title: Tinker源码分析(四):加载资源补丁流程
date: 2019-03-05 23:54:26
categories: Android Blog
tags: [Android,开源框架,源码解析,Tinker,热修复]
---
本系列 Tinker 源码解析基于 Tinker v1.9.12

加载资源补丁流程
=============
将到资源补丁的加载，首先还要回过头来先看资源补丁的校验和检查。

我们回到 TinkerLoader.tryLoadPatchFilesInternal 方法中来看。

tryLoadPatchFilesInternal
----------
``` java
//check resource
final boolean isEnabledForResource = ShareTinkerInternals.isTinkerEnabledForResource(tinkerFlag);
Log.w(TAG, "tryLoadPatchFiles:isEnabledForResource:" + isEnabledForResource);
if (isEnabledForResource) {
    boolean resourceCheck = TinkerResourceLoader.checkComplete(app, patchVersionDirectory, securityCheck, resultIntent);
    if (!resourceCheck) {
        //file not found, do not load patch
        Log.w(TAG, "tryLoadPatchFiles:resource check fail");
        return;
    }
}
```

具体的校验是在 TinkerResourceLoader.checkComplete 中完成的。这里为了校验的速度，所以只会校验资源补丁存不存在。

checkComplete
-------------
checkComplete 方法我们分段来看吧

``` java
// 读取 assets/res_meta.txt 
String meta = securityCheck.getMetaContentMap().get(RESOURCE_META_FILE);
//not found resource
if (meta == null) {
    return true;
}
//only parse first line for faster
ShareResPatchInfo.parseResPatchInfoFirstLine(meta, resPatchInfo);
```

为了校验的速度，只读取了 assets/res_meta.txt 的第一行，并存入到 resPatchInfo 中

res_meta.txt 的第一行主要是资源的 crc 值和 md5 值 ，在后面会做校验。

``` java
if (resPatchInfo.resArscMd5 == null) {
    return true;
}
if (!ShareResPatchInfo.checkResPatchInfo(resPatchInfo)) {
    intentResult.putExtra(ShareIntentUtil.INTENT_PATCH_PACKAGE_PATCH_CHECK, ShareConstants.ERROR_PACKAGE_CHECK_RESOURCE_META_CORRUPTED);
    ShareIntentUtil.setIntentReturnCode(intentResult, ShareConstants.ERROR_LOAD_PATCH_PACKAGE_CHECK_FAIL);
    return false;
}
```

校验上面读取到的 md5 值是否为空以及 md5 值长度是否是 32 位

``` java
String resourcePath = directory + "/" + RESOURCE_PATH + "/";

File resourceDir = new File(resourcePath);

if (!resourceDir.exists() || !resourceDir.isDirectory()) {
    ShareIntentUtil.setIntentReturnCode(intentResult, ShareConstants.ERROR_LOAD_PATCH_VERSION_RESOURCE_DIRECTORY_NOT_EXIST);
    return false;
}
```

校验资源补丁文件夹是否存在。

``` java
File resourceFile = new File(resourcePath + RESOURCE_FILE);
if (!SharePatchFileUtil.isLegalFile(resourceFile)) {
    ShareIntentUtil.setIntentReturnCode(intentResult, ShareConstants.ERROR_LOAD_PATCH_VERSION_RESOURCE_FILE_NOT_EXIST);
    return false;
}
```

校验资源补丁文件是否存在及合法性。

``` java
try {
    TinkerResourcePatcher.isResourceCanPatch(context);
} catch (Throwable e) {
    Log.e(TAG, "resource hook check failed.", e);
    intentResult.putExtra(ShareIntentUtil.INTENT_PATCH_EXCEPTION, e);
    ShareIntentUtil.setIntentReturnCode(intentResult, ShareConstants.ERROR_LOAD_PATCH_VERSION_RESOURCE_LOAD_EXCEPTION);
    return false;
}
return true;
```

通过 context 来检查当前环境是否支持加载资源补丁。方法里面做的事就是通过反射来获取各种系统的属性和方法。简单地举例以下几种：

* ActivityThread : 当前的 ActivityThread 实例，app主线程的入口。利用 ActivityThread 可以获取到 LoadedApk 对象；
* LoadedApk : 通过 LoadedApk 可以获取 mResDir 属性；
* mResDir : 这个值很关键，就是资源文件的路径。在后面会被 hook 成资源补丁的路径；
* addAssetPath : 通过 addAssetPath 方法将资源补丁文件加载进新的 AssetManager 中；
* mActiveResources : ResourcesManager 的 Resources 容器。里面会存储着每个 apk 对应的 Resources 对象。mActiveResources 是 ArrayMap 类型的，不同的 apk 都有一个不同的 key 来获取对应的 apk 的 Resource 对象；
* mAssets : 即 Resources 类中的 mAssets 属性，其实就是一个 AssetManager 对象。在资源打补丁的时候，Resources 中原来的 mAssets 对象会被替换成新的 AssetManager 对象。

这里就不详细讲了，总结起来就一句话：获取 Android 系统中与资源有关的一些属性和方法，为接下来的加载资源补丁做准备。如果在 isResourceCanPatch 方法中报出异常了，就认为当前环境不能加载资源补丁了。

tryLoadPatchFilesInternal
-------------------------
然后我们再在 tryLoadPatchFilesInternal 中往下看。会看到资源补丁加载代码的入口，即 TinkerResourceLoader.loadTinkerResources 方法

``` java
//now we can load patch resource
if (isEnabledForResource) {
    boolean loadTinkerResources = TinkerResourceLoader.loadTinkerResources(app, patchVersionDirectory, resultIntent);
    if (!loadTinkerResources) {
        Log.w(TAG, "tryLoadPatchFiles:onPatchLoadResourcesFail");
        return;
    }
}
```

loadTinkerResources
-------------------
loadTinkerResources 方法我们分段来看。

``` java
	//  检查 res_meta.txt 中读取出来的 md5 值，如果 resPatchInfo 或者 md5 是空的，就说明补丁包中没有资源补丁，不需要加载
	if (resPatchInfo == null || resPatchInfo.resArscMd5 == null) {
	    return true;
	}
	String resourceString = directory + "/" + RESOURCE_PATH +  "/" + RESOURCE_FILE;
	File resourceFile = new File(resourceString);
	long start = System.currentTimeMillis();
	// 如果校验设置为 true ，就去校验资源补丁包 resources.apk 的 md5 值
	if (application.isTinkerLoadVerifyFlag()) {
	    if (!SharePatchFileUtil.checkResourceArscMd5(resourceFile, resPatchInfo.resArscMd5)) {
	        Log.e(TAG, "Failed to load resource file, path: " + resourceFile.getPath() + ", expect md5: " + resPatchInfo.resArscMd5);
	        ShareIntentUtil.setIntentReturnCode(intentResult, ShareConstants.ERROR_LOAD_PATCH_VERSION_RESOURCE_MD5_MISMATCH);
	        return false;
	    }
	    Log.i(TAG, "verify resource file:" + resourceFile.getPath() + " md5, use time: " + (System.currentTimeMillis() - start));
	}
```

然后就是加载资源补丁了，如果加载失败了，会把 dex 补丁卸载了。防止 dex 补丁代码中会引用到资源补丁中的资源文件，导致程序崩溃或报错。

``` java
try {
    // 加载资源
    TinkerResourcePatcher.monkeyPatchExistingResources(application, resourceString);
    Log.i(TAG, "monkeyPatchExistingResources resource file:" + resourceString + ", use time: " + (System.currentTimeMillis() - start));
} catch (Throwable e) {
    Log.e(TAG, "install resources failed");
    //remove patch dex if resource is installed failed
    // 如果资源补丁加载失败的话，会移除 dex 补丁
    // 因为如果dex补丁代码中有引用到资源的话，会报错
    try {
        SystemClassLoaderAdder.uninstallPatchDex(application.getClassLoader());
    } catch (Throwable throwable) {
        Log.e(TAG, "uninstallPatchDex failed", e);
    }
    intentResult.putExtra(ShareIntentUtil.INTENT_PATCH_EXCEPTION, e);
    ShareIntentUtil.setIntentReturnCode(intentResult, ShareConstants.ERROR_LOAD_PATCH_VERSION_RESOURCE_LOAD_EXCEPTION);
    return false;
}
```

monkeyPatchExistingResources
----------------------------
monkeyPatchExistingResources 方法也一段一段来看

``` java
// 检查资源补丁apk是否为空
if (externalResourceFile == null) {
    return;
}

final ApplicationInfo appInfo = context.getApplicationInfo();

final Field[] packagesFields;
// 准备之前反射好的 packagesFiled 和 resourcePackagesFiled 字段
// 利用 packagesFiled 和 resourcePackagesFiled 可以获取 LoadedApk 对象
if (Build.VERSION.SDK_INT < 27) {
    packagesFields = new Field[]{packagesFiled, resourcePackagesFiled};
} else {
    packagesFields = new Field[]{packagesFiled};
}
// 遍历 packagesFields ，获取对应的值
for (Field field : packagesFields) {
    // 获取 ActivityThread 中 packagesFiled 或 resourcePackagesFiled
    // value 其实为 Map<String, WeakReference<LoadedApk>> 类型
    final Object value = field.get(currentActivityThread);
    // 再对 value 进行遍历，获取 LoadedApk 对象
    for (Map.Entry<String, WeakReference<?>> entry
            : ((Map<String, WeakReference<?>>) value).entrySet()) {
        final Object loadedApk = entry.getValue().get();
        if (loadedApk == null) {
            continue;
        }
        // 从 LoadedApk 对象中获取 mResDir 属性，这个属性的意义在上面已经讲过了
        final String resDirPath = (String) resDir.get(loadedApk);
        // 将 mResDir 的值 hook 成资源补丁 apk 的路径
        if (appInfo.sourceDir.equals(resDirPath)) {
            resDir.set(loadedApk, externalResourceFile);
        }
    }
}
```

上面这段代码基本上都有注释了，接着往下看

``` java
// Create a new AssetManager instance and point it to the resources installed under
// 创建一个新的 AssetManager 实例，并把资源补丁apk加载进 AssetManager 中
if (((Integer) addAssetPathMethod.invoke(newAssetManager, externalResourceFile)) == 0) {
    throw new IllegalStateException("Could not create new AssetManager");
}

// Kitkat needs this method call, Lollipop doesn't. However, it doesn't seem to cause any harm
// in L, so we do it unconditionally.
// 创建出 AssetManager 后，调用 ensureStringBlocks 来确保资源的字符串索引创建出来
if (stringBlocksField != null && ensureStringBlocksMethod != null) {
    stringBlocksField.set(newAssetManager, null);
    ensureStringBlocksMethod.invoke(newAssetManager);
}
```

在创建出新的 AssetManager 之后，最后要做的事就是用新的 AssetManager 来替换旧的。下面代码中的 references 就是上面提到的 mActiveResources 的 value 集合。也就是每个 apk 的 Resources 资源集合。

``` java
for (WeakReference<Resources> wr : references) {
    final Resources resources = wr.get();
    if (resources == null) {
        continue;
    }
    // Set the AssetManager of the Resources instance to our brand new one
    try {
        //pre-N
        // Android N 之前的方案
        // 把原来 resources 的 mAssets 属性替换成新的 AssetManager 对象
        assetsFiled.set(resources, newAssetManager);
    } catch (Throwable ignore) {
        // N
        // Android N 之后， mAssets 属性被放在了 ResourcesImpl 中
        // 所以需要先获取 ResourcesImpl 对象再进行替换
        final Object resourceImpl = resourcesImplFiled.get(resources);
        // for Huawei HwResourcesImpl
        final Field implAssets = findField(resourceImpl, "mAssets");
        implAssets.set(resourceImpl, newAssetManager);
    }
    // 在 Resource 中会维护一个 mTypedArrayPool 资源池
    // 来减少频繁访问 AssetManager ，所以需要去释放这个资源池，否则取到的都是缓存
    clearPreloadTypedArrayIssue(resources);
    // 最后调用 updateConfiguration 方法来确保资源更新了
    resources.updateConfiguration(resources.getConfiguration(), resources.getDisplayMetrics());
}

// Handle issues caused by WebView on Android N.
// Issue: On Android N, if an activity contains a webview, when screen rotates
// our resource patch may lost effects.
// for 5.x/6.x, we found Couldn't expand RemoteView for StatusBarNotification Exception
if (Build.VERSION.SDK_INT >= 24) {
    try {
        if (publicSourceDirField != null) {
            publicSourceDirField.set(context.getApplicationInfo(), externalResourceFile);
        }
    } catch (Throwable ignore) {
    }
}
```

最后，就是来确认一下资源补丁是否已经加载成功了。具体的方法就是在资源补丁Apk的 assets 中有一个 Tinker 的测试资源，名字叫 only_use_to_test_tinker_resource.txt ，如果可以正确读取到并且没报错的话，就证明资源补丁加载成功了。否则就抛出异常，会执行 dex 补丁卸载的流程。

``` java
if (!checkResUpdate(context)) {
    throw new TinkerRuntimeException(ShareConstants.CHECK_RES_INSTALL_FAIL);
}
```

到这里，资源补丁的加载流程就讲完了。



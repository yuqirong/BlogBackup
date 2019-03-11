title: Tinker源码分析(五):加载so补丁流程
date: 2019-03-10 22:50:22
categories: Android Blog
tags: [Android,开源框架,源码解析,Tinker,热修复]
---
本系列 Tinker 源码解析基于 Tinker v1.9.12

校验so补丁流程
============
与加载资源补丁类似，加载so补丁也要先从校验开始看起。

其实总体来说，Tinker 中加载 so 补丁文件的关键代码就一句：

System.load(String filePath)

tryLoadPatchFilesInternal
-------------------------
``` java
final boolean isEnabledForNativeLib = ShareTinkerInternals.isTinkerEnabledForNativeLib(tinkerFlag);

if (isEnabledForNativeLib) {
    //tinker/patch.info/patch-641e634c/lib
    boolean libCheck = TinkerSoLoader.checkComplete(patchVersionDirectory, securityCheck, resultIntent);
    if (!libCheck) {
        //file not found, do not load patch
        Log.w(TAG, "tryLoadPatchFiles:native lib check fail");
        return;
    }
}
```

checkComplete
-------------
从 assets/so_meta.txt 中读取 so 补丁信息，每一条 so 补丁信息都会被封装成一个 ShareBsDiffPatchInfo 对象，然后放入 libraryList 中。

``` java
String meta = securityCheck.getMetaContentMap().get(SO_MEAT_FILE);
//not found lib
if (meta == null) {
    return true;
}
ArrayList<ShareBsDiffPatchInfo> libraryList = new ArrayList<>();
ShareBsDiffPatchInfo.parseDiffPatchInfo(meta, libraryList);

if (libraryList.isEmpty()) {
    return true;
}
```

然后遍历 libraryList ，去校验里面的 ShareBsDiffPatchInfo 对象中 md5 和 name 值是否合法。合法的 ShareBsDiffPatchInfo 对象再放入 libs 中。

``` java
//tinker//patch-641e634c/lib
String libraryPath = directory + "/" + SO_PATH + "/";

HashMap<String, String> libs = new HashMap<>();

for (ShareBsDiffPatchInfo info : libraryList) {
    if (!ShareBsDiffPatchInfo.checkDiffPatchInfo(info)) {
        intentResult.putExtra(ShareIntentUtil.INTENT_PATCH_PACKAGE_PATCH_CHECK, ShareConstants.ERROR_PACKAGE_CHECK_LIB_META_CORRUPTED);
        ShareIntentUtil.setIntentReturnCode(intentResult, ShareConstants.ERROR_LOAD_PATCH_PACKAGE_CHECK_FAIL);
        return false;
    }
    String middle = info.path + "/" + info.name;

    //unlike dex, keep the original structure
    libs.put(middle, info.md5);
}
```

接着会校验 so 补丁文件夹是否存在

``` java
File libraryDir = new File(libraryPath);

if (!libraryDir.exists() || !libraryDir.isDirectory()) {
    ShareIntentUtil.setIntentReturnCode(intentResult, ShareConstants.ERROR_LOAD_PATCH_VERSION_LIB_DIRECTORY_NOT_EXIST);
    return false;
}
```

再校验上面的从 so_meta.txt 中获取到的 so 补丁文件路径是否真的存在并且 so 文件是可读的

``` java
//fast check whether there is any dex files missing
for (String relative : libs.keySet()) {
    File libFile = new File(libraryPath + relative);
    if (!SharePatchFileUtil.isLegalFile(libFile)) {
        ShareIntentUtil.setIntentReturnCode(intentResult, ShareConstants.ERROR_LOAD_PATCH_VERSION_LIB_FILE_NOT_EXIST);
        intentResult.putExtra(ShareIntentUtil.INTENT_PATCH_MISSING_LIB_PATH, libFile.getAbsolutePath());
        return false;
    }
}
```

都没问题的话，就通过校验

``` java
//if is ok, add to result intent
intentResult.putExtra(ShareIntentUtil.INTENT_PATCH_LIBS_PATH, libs);
return true;
```

加载so补丁流程
============
加载so补丁的入口：TinkerLoadLibrary.loadArmLibrary / TinkerLoadLibrary.loadArmV7Library ，区别就是前者用来加载 armeabi 平台的，后者是用来加载 armeabi-v7a 平台的。我们就来看 loadArmLibrary 方法吧。

loadArmLibrary
--------------
``` java
public static void loadArmLibrary(Context context, String libName) {
    // 校验 libName 和 context 参数
    if (libName == null || libName.isEmpty() || context == null) {
        throw new TinkerRuntimeException("libName or context is null!");
    }

    // 这里需要保证已经调用了 Tinker.install 方法 不然 Tinker 没安装的话会抛出异常
    Tinker tinker = Tinker.with(context);

    // 如果 tinker 支持 so 补丁，就加载外部的 so 文件
    if (tinker.isEnabledForNativeLib()) {
        if (TinkerLoadLibrary.loadLibraryFromTinker(context, "lib/armeabi", libName)) {
            return;
        }
    }
    // 如果 tinker 不支持 so 补丁，就调用系统的加载方法
    System.loadLibrary(libName);
}
```

loadLibraryFromTinker
---------------------
loadLibraryFromTinker 一开始校验了 libName 的名字是否是 lib 开头、 .so 结尾的。

``` java
    final Tinker tinker = Tinker.with(context);

    libName = libName.startsWith("lib") ? libName : "lib" + libName;
    libName = libName.endsWith(".so") ? libName : libName + ".so";
    String relativeLibPath = relativePath + "/" + libName;
``` 

然后就是用 relativeLibPath 去和之前 so 校验得到的 libs 去一一匹配。

如果匹配上了，就说明要加载的就是这个 so 文件，调用 System.load ，传入文件路径即可。

``` java
//TODO we should add cpu abi, and the real path later
// tinker 支持 so 补丁并且 tinker 完成加载补丁
if (tinker.isEnabledForNativeLib() && tinker.isTinkerLoaded()) {
    TinkerLoadResult loadResult = tinker.getTinkerLoadResultIfPresent();
    // 获取上面校验得到的 libs
    if (loadResult.libs != null) {
        for (String name : loadResult.libs.keySet()) {
            // 如果名字对上了，就说明要加载的就是这个外部的so补丁
            if (name.equals(relativeLibPath)) {
                String patchLibraryPath = loadResult.libraryDirectory + "/" + name;
                File library = new File(patchLibraryPath);
                // 确认 so 补丁文件存在
                if (library.exists()) {
                    //whether we check md5 when load
                    boolean verifyMd5 = tinker.isTinkerLoadVerify();
                    // 如果需要校验md5值但是校验失败了，就回调 onLoadFileMd5Mismatch 方法
                    if (verifyMd5 && !SharePatchFileUtil.verifyFileMd5(library, loadResult.libs.get(name))) {
                        tinker.getLoadReporter().onLoadFileMd5Mismatch(library, ShareConstants.TYPE_LIBRARY);
                    } else {
                        // 否则就调用 System.load 方法，传入 so 补丁文件的路径即可
                        System.load(patchLibraryPath);
                        TinkerLog.i(TAG, "loadLibraryFromTinker success:" + patchLibraryPath);
                        return true;
                    }
                }
            }
        }
    }
}
```

到这里，Tinker 中关于 so 补丁加载的流程就讲完了。


番外
===
大家有没有发现，一个个单独去调用 TinkerLoadLibrary.loadArmLibrary 会很麻烦，因为如果我的 so 补丁文件有很多个，就需要调用很多次。所以从 Tinker v1.7.7 之后，提供了一键反射的方案来加载 so 补丁文件。

具体方法 TinkerLoadLibrary.installNavitveLibraryABI

installNavitveLibraryABI
-------------------------

来看一下具体的代码：

``` java
public static boolean installNavitveLibraryABI(Context context, String currentABI) {
    // 检查 tinker 有没有安装
    Tinker tinker = Tinker.with(context);
    if (!tinker.isTinkerLoaded()) {
        TinkerLog.i(TAG, "tinker is not loaded, just return");
        return false;
    }
    // 检查 tinker 加载的结果
    TinkerLoadResult loadResult = tinker.getTinkerLoadResultIfPresent();
    if (loadResult.libs == null) {
        TinkerLog.i(TAG, "tinker libs is null, just return");
        return false;
    }
    // 检查当前 ABI 的 so 文件夹是否存在
    File soDir = new File(loadResult.libraryDirectory, "lib/" + currentABI);
    if (!soDir.exists()) {
        TinkerLog.e(TAG, "current libraryABI folder is not exist, path: %s", soDir.getPath());
        return false;
    }
    // 获取 classloader
    ClassLoader classLoader = context.getClassLoader();
    if (classLoader == null) {
        TinkerLog.e(TAG, "classloader is null");
        return false;
    }
    TinkerLog.i(TAG, "before hack classloader:" + classLoader.toString());
    // 加载当前 ABI 的所有 so 补丁文件
    try {
        installNativeLibraryPath(classLoader, soDir);
        return true;
    } catch (Throwable throwable) {
        TinkerLog.e(TAG, "installNativeLibraryPath fail:" + throwable);
        return false;
    } finally {
        TinkerLog.i(TAG, "after hack classloader:" + classLoader.toString());
    }
}
```

在做了一堆的检查之后，具体 so 文件加载是在 installNativeLibraryPath 方法中。

installNativeLibraryPath
-----------------------
installNativeLibraryPath 中做的事情主要有两点：

* 如果 classloader 中没有注入 so 补丁文件夹的路径的话，就执行注入；
* 如果 classloader 中已经有 so 补丁文件夹的路径了，就先删除，再进行注入；

具体 hook 的代码根据 SDK 版本而定，这里就不展开讲了。

``` java
private static void installNativeLibraryPath(ClassLoader classLoader, File folder)
    throws Throwable {
    if (folder == null || !folder.exists()) {
        TinkerLog.e(TAG, "installNativeLibraryPath, folder %s is illegal", folder);
        return;
    }
    // android o sdk_int 26
    // for android o preview sdk_int 25
    if ((Build.VERSION.SDK_INT == 25 && Build.VERSION.PREVIEW_SDK_INT != 0)
        || Build.VERSION.SDK_INT > 25) {
        try {
            V25.install(classLoader, folder);
        } catch (Throwable throwable) {
            // install fail, try to treat it as v23
            // some preview N version may go here
            TinkerLog.e(TAG, "installNativeLibraryPath, v25 fail, sdk: %d, error: %s, try to fallback to V23",
                    Build.VERSION.SDK_INT, throwable.getMessage());
            V23.install(classLoader, folder);
        }
    } else if (Build.VERSION.SDK_INT >= 23) {
        try {
            V23.install(classLoader, folder);
        } catch (Throwable throwable) {
            // install fail, try to treat it as v14
            TinkerLog.e(TAG, "installNativeLibraryPath, v23 fail, sdk: %d, error: %s, try to fallback to V14",
                Build.VERSION.SDK_INT, throwable.getMessage());

            V14.install(classLoader, folder);
        }
    } else if (Build.VERSION.SDK_INT >= 14) {
        V14.install(classLoader, folder);
    } else {
        V4.install(classLoader, folder);
    }
}
```

综上所述，有了 TinkerLoadLibrary.installNavitveLibraryABI 之后，你就只需要传入当前手机系统的 ABI ，就无需再对 so 的加载做任何的介入。


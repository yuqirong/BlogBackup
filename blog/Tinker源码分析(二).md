title: Tinker源码分析(二):加载补丁
date: 2019-02-27 21:43:16
categories: Android Blog
tags: [Android,开源框架,源码解析,Tinker,热修复]
---
前一篇讲到了利用反射执行的是 TinkerLoader.tryLoad 方法

tryLoad
--------

``` java
	@Override
	public Intent tryLoad(TinkerApplication app) {
	    Intent resultIntent = new Intent();
	
	    long begin = SystemClock.elapsedRealtime();
	    tryLoadPatchFilesInternal(app, resultIntent);
	    long cost = SystemClock.elapsedRealtime() - begin;
	    ShareIntentUtil.setIntentPatchCostTime(resultIntent, cost);
	    return resultIntent;
	}
```

加载的流程主要在 tryLoadPatchFilesInternal 里面。tryLoadPatchFilesInternal 方法很长，我们需要分段来看。

tryLoadPatchFilesInternal
-------------------------
一开始是各种校验

检查 tinker 是否开启

``` java
	final int tinkerFlag = app.getTinkerFlags();
	// 检查 tinker 是否开启
	if (!ShareTinkerInternals.isTinkerEnabled(tinkerFlag)) {
	    Log.w(TAG, "tryLoadPatchFiles: tinker is disable, just return");
	    ShareIntentUtil.setIntentReturnCode(resultIntent, ShareConstants.ERROR_LOAD_DISABLE);
	    return;
	}
```
	
检查当前的进程

``` java
	// 检查当前的进程，确保不是 :patch 进程
	if (ShareTinkerInternals.isInPatchProcess(app)) {
	    Log.w(TAG, "tryLoadPatchFiles: we don't load patch with :patch process itself, just return");
	    ShareIntentUtil.setIntentReturnCode(resultIntent, ShareConstants.ERROR_LOAD_DISABLE);
	    return;
	}
```
	
获取 tinker 目录，检查目录是否存在

``` java
	// tinker 获取 tinker 目录，/data/data/tinker.sample.android/tinker
	File patchDirectoryFile = SharePatchFileUtil.getPatchDirectory(app);
	if (patchDirectoryFile == null) {
	    Log.w(TAG, "tryLoadPatchFiles:getPatchDirectory == null");
	    //treat as not exist
	    ShareIntentUtil.setIntentReturnCode(resultIntent, ShareConstants.ERROR_LOAD_PATCH_DIRECTORY_NOT_EXIST);
	    return;
	}
	String patchDirectoryPath = patchDirectoryFile.getAbsolutePath();
	// 检查 tinker 目录是否存在
	//check patch directory whether exist
	if (!patchDirectoryFile.exists()) {
	    Log.w(TAG, "tryLoadPatchFiles:patch dir not exist:" + patchDirectoryPath);
	    ShareIntentUtil.setIntentReturnCode(resultIntent, ShareConstants.ERROR_LOAD_PATCH_DIRECTORY_NOT_EXIST);
	    return;
	}
```
	
检查 patch.info 文件是否存在
	
``` java
	// 文件目录 /data/data/tinker.sample.android/tinker/patch.info
	File patchInfoFile = SharePatchFileUtil.getPatchInfoFile(patchDirectoryPath);
	
	// 检查 patch.info 补丁信息文件是否存在
	//check patch info file whether exist
	if (!patchInfoFile.exists()) {
	    Log.w(TAG, "tryLoadPatchFiles:patch info not exist:" + patchInfoFile.getAbsolutePath());
	    ShareIntentUtil.setIntentReturnCode(resultIntent, ShareConstants.ERROR_LOAD_PATCH_INFO_NOT_EXIST);
	    return;
	}
```
	
读取 patch.info 文件，并包装成一个 SharePatchInfo ，并检查 patchInfo 是否为空 (先加锁再解锁)
	
``` java
	//old = 641e634c5b8f1649c75caf73794acbdf
	//new = 2c150d8560334966952678930ba67fa8
	// tinker/info.lock
	File patchInfoLockFile = SharePatchFileUtil.getPatchInfoLockFile(patchDirectoryPath);
	
	// 检查 patch info 文件中的补丁版本信息
	patchInfo = SharePatchInfo.readAndCheckPropertyWithLock(patchInfoFile, patchInfoLockFile);
	if (patchInfo == null) {
	    ShareIntentUtil.setIntentReturnCode(resultIntent, ShareConstants.ERROR_LOAD_PATCH_INFO_CORRUPTED);
	    return;
	}
```

检查读取出来的 patchInfo 补丁版本信息

``` java
	String oldVersion = patchInfo.oldVersion;
	String newVersion = patchInfo.newVersion;
	String oatDex = patchInfo.oatDir;
	
	if (oldVersion == null || newVersion == null || oatDex == null) {
	    //it is nice to clean patch
	    Log.w(TAG, "tryLoadPatchFiles:onPatchInfoCorrupted");
	    ShareIntentUtil.setIntentReturnCode(resultIntent, ShareConstants.ERROR_LOAD_PATCH_INFO_CORRUPTED);
	    return;
	}
```
	
如果发现 patchInfo 中的 isRemoveNewVersion 为 true 并且在主进程中运行的话，就代表需要清理新的补丁文件夹了
	
``` java
	boolean mainProcess = ShareTinkerInternals.isInMainProcess(app);
	boolean isRemoveNewVersion = patchInfo.isRemoveNewVersion;

	// So far new version is not loaded in main process and other processes.
	// We can remove new version directory safely.
	if (mainProcess && isRemoveNewVersion) {
	    Log.w(TAG, "found clean patch mark and we are in main process, delete patch file now.");
	    // 获取新的补丁文件夹，例如 patch-2c150d85
	    String patchName = SharePatchFileUtil.getPatchVersionDirectory(newVersion);
	    if (patchName != null) {
		     // 删除新的补丁文件夹   
	        String patchVersionDirFullPath = patchDirectoryPath + "/" + patchName;
	        SharePatchFileUtil.deleteDir(patchVersionDirFullPath);
	        // 如果旧版本和新版本一致，就把 oldVersion 和 newVersion 设置为空来清除补丁
	        if (oldVersion.equals(newVersion)) {
	            // !oldVersion.equals(newVersion) means new patch is applied, just fall back to old one in that case.
	            // Or we will set oldVersion and newVersion to empty string to clean patch.
	            oldVersion = "";
	        }
	        // 如果 !oldVersion.equals(newVersion) 意味着新补丁已经应用了，需要回退到原来的旧版本
	        newVersion = oldVersion;
	        patchInfo.oldVersion = oldVersion;
	        patchInfo.newVersion = newVersion;
	        // 把数据重新写入 patchInfo 文件中
	        SharePatchInfo.rewritePatchInfoFileWithLock(patchInfoFile, patchInfo, patchInfoLockFile);
	        // 杀掉主进程以外的所有进程
	        ShareTinkerInternals.killProcessExceptMain(app);
	    }
	}
	
	resultIntent.putExtra(ShareIntentUtil.INTENT_PATCH_OLD_VERSION, oldVersion);
	resultIntent.putExtra(ShareIntentUtil.INTENT_PATCH_NEW_VERSION, newVersion);
```

根据版本变化和是否是主进程的条件决定是否允许加载最新的补丁

``` java
	boolean versionChanged = !(oldVersion.equals(newVersion));
	boolean oatModeChanged = oatDex.equals(ShareConstants.CHANING_DEX_OPTIMIZE_PATH);
	oatDex = ShareTinkerInternals.getCurrentOatMode(app, oatDex);
	resultIntent.putExtra(ShareIntentUtil.INTENT_PATCH_OAT_DIR, oatDex);
	
	String version = oldVersion;
	
	// 根据版本变化和是否是主进程的条件决定是否允许加载最新的补丁
	if (versionChanged && mainProcess) {
	    version = newVersion;
	}
	if (ShareTinkerInternals.isNullOrNil(version)) {
	    Log.w(TAG, "tryLoadPatchFiles:version is blank, wait main process to restart");
	    ShareIntentUtil.setIntentReturnCode(resultIntent, ShareConstants.ERROR_LOAD_PATCH_INFO_BLANK);
	    return;
	}
```

检查当前版本补丁文件夹是否存在

``` java
	//patch-641e634c
	String patchName = SharePatchFileUtil.getPatchVersionDirectory(version);
	if (patchName == null) {
	    Log.w(TAG, "tryLoadPatchFiles:patchName is null");
	    //we may delete patch info file
	    ShareIntentUtil.setIntentReturnCode(resultIntent, ShareConstants.ERROR_LOAD_PATCH_VERSION_DIRECTORY_NOT_EXIST);
	    return;
	}
	//tinker/patch.info/patch-641e634c
	String patchVersionDirectory = patchDirectoryPath + "/" + patchName;
	
	File patchVersionDirectoryFile = new File(patchVersionDirectory);
	
	if (!patchVersionDirectoryFile.exists()) {
	    Log.w(TAG, "tryLoadPatchFiles:onPatchVersionDirectoryNotFound");
	    //we may delete patch info file
	    ShareIntentUtil.setIntentReturnCode(resultIntent, ShareConstants.ERROR_LOAD_PATCH_VERSION_DIRECTORY_NOT_EXIST);
	    return;
	}
```

检查补丁文件是否存在并且可读

``` java
	//tinker/patch.info/patch-641e634c/patch-641e634c.apk
	final String patchVersionFileRelPath = SharePatchFileUtil.getPatchVersionFile(version);
	File patchVersionFile = (patchVersionFileRelPath != null ? new File(patchVersionDirectoryFile.getAbsolutePath(), patchVersionFileRelPath) : null);
	
	if (!SharePatchFileUtil.isLegalFile(patchVersionFile)) {
	    Log.w(TAG, "tryLoadPatchFiles:onPatchVersionFileNotFound");
	    //we may delete patch info file
	    ShareIntentUtil.setIntentReturnCode(resultIntent, ShareConstants.ERROR_LOAD_PATCH_VERSION_FILE_NOT_EXIST);
	    return;
	}
```

检查补丁文件签名以及补丁文件中的 tinker id 和基准包的 tinker id 是否一致

``` java
	ShareSecurityCheck securityCheck = new ShareSecurityCheck(app);
	
	int returnCode = ShareTinkerInternals.checkTinkerPackage(app, tinkerFlag, patchVersionFile, securityCheck);
	if (returnCode != ShareConstants.ERROR_PACKAGE_CHECK_OK) {
	    Log.w(TAG, "tryLoadPatchFiles:checkTinkerPackage");
	    resultIntent.putExtra(ShareIntentUtil.INTENT_PATCH_PACKAGE_PATCH_CHECK, returnCode);
	    ShareIntentUtil.setIntentReturnCode(resultIntent, ShareConstants.ERROR_LOAD_PATCH_PACKAGE_CHECK_FAIL);
	    return;
	}

	resultIntent.putExtra(ShareIntentUtil.INTENT_PATCH_PACKAGE_CONFIG, securityCheck.getPackagePropertiesIfPresent());


	public static int checkTinkerPackage(Context context, int tinkerFlag, File patchFile, ShareSecurityCheck securityCheck) {
	    // 检查补丁文件签名和 tinker id 是否一致
	    // 这里为了快速校验,就只检验补丁包内部以meta.txt结尾的文件的签名，而其他的文件的合法性则通过meta.txt文件内部记录的补丁文件Md5值来校验
	    int returnCode = checkSignatureAndTinkerID(context, patchFile, securityCheck);
	    if (returnCode == ShareConstants.ERROR_PACKAGE_CHECK_OK) {
	        // 检查配置的 tinker flag 和 meta.txt 是否匹配
	        // 如果不匹配的话，中断接下来的流程
	        returnCode = checkPackageAndTinkerFlag(securityCheck, tinkerFlag);
	    }
	    return returnCode;
	}
```
	
根据不同的情况,最多有四个文件是以meta.txt结尾的:

* package_meta.txt 补丁包的基本信息
* dex_meta.txt 所有dex文件的信息
* so_meta.txt 所有so文件的信息
* res_meta.txt 所有资源文件的信息


如果开启了支持 dex 热修复，检查 dex_meta.txt 文件中记录的dex文件信息对应的dex文件是否存在

``` java
	final boolean isEnabledForDex = ShareTinkerInternals.isTinkerEnabledForDex(tinkerFlag);
	
	if (isEnabledForDex) {
	    //tinker/patch.info/patch-641e634c/dex
	    // 检查下发的meta文件中记录的dex信息中对应的dex文件是否存在
	    boolean dexCheck = TinkerDexLoader.checkComplete(patchVersionDirectory, securityCheck, oatDex, resultIntent);
	    if (!dexCheck) {
	        //file not found, do not load patch
	        Log.w(TAG, "tryLoadPatchFiles:dex check fail");
	        return;
	    }
	}
```
	
如果开启了支持 so 热修复，检查 so_meta.txt 文件中记录的so文件信息对应的so文件是否存在

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
	
如果开启了支持 res 热修复，检查 res_meta.txt 文件中记录的res文件信息对应的res文件是否存在
	
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
	
符合条件的话就更新版本信息,并将最新的patch info更新入文件.在v1.7.5的版本开始有了isSystemOTA判断,只要用户是ART环境并且做了OTA升级则在加载dex补丁的时候就会先把最近一次的补丁全部DexFile.loadDex一遍重新生成odex.再加载dex补丁
	
``` java
	//only work for art platform oat，because of interpret, refuse 4.4 art oat
	//android o use quicken default, we don't need to use interpret mode
	boolean isSystemOTA = ShareTinkerInternals.isVmArt()
	    && ShareTinkerInternals.isSystemOTA(patchInfo.fingerPrint)
	    && Build.VERSION.SDK_INT >= 21 && !ShareTinkerInternals.isAfterAndroidO();
	
	resultIntent.putExtra(ShareIntentUtil.INTENT_PATCH_SYSTEM_OTA, isSystemOTA);
	
	//we should first try rewrite patch info file, if there is a error, we can't load jar
	if (mainProcess && (versionChanged || oatModeChanged)) {
	    patchInfo.oldVersion = version;
	    patchInfo.oatDir = oatDex;
	
	    //update old version to new
	    if (!SharePatchInfo.rewritePatchInfoFileWithLock(patchInfoFile, patchInfo, patchInfoLockFile)) {
	        ShareIntentUtil.setIntentReturnCode(resultIntent, ShareConstants.ERROR_LOAD_PATCH_REWRITE_PATCH_INFO_FAIL);
	        Log.w(TAG, "tryLoadPatchFiles:onReWritePatchInfoCorrupted");
	        return;
	    }
	
	    Log.i(TAG, "tryLoadPatchFiles:success to rewrite patch info, kill other process.");
	    ShareTinkerInternals.killProcessExceptMain(app);
	
	    if (oatModeChanged) {
	        // delete interpret odex
	        // for android o, directory change. Fortunately, we don't need to support android o interpret mode any more
	        Log.i(TAG, "tryLoadPatchFiles:oatModeChanged, try to delete interpret optimize files");
	        SharePatchFileUtil.deleteDir(patchVersionDirectory + "/" + ShareConstants.INTERPRET_DEX_OPTIMIZE_PATH);
	    }
	}
```

加载补丁的安全次数最多三次

``` java
	if (!checkSafeModeCount(app)) {
	    resultIntent.putExtra(ShareIntentUtil.INTENT_PATCH_EXCEPTION, new TinkerRuntimeException("checkSafeModeCount fail"));
	    ShareIntentUtil.setIntentReturnCode(resultIntent, ShareConstants.ERROR_LOAD_PATCH_UNCAUGHT_EXCEPTION);
	    Log.w(TAG, "tryLoadPatchFiles:checkSafeModeCount fail");
	    return;
	}
```

加载补丁 jar

``` java
	//now we can load patch jar
	if (isEnabledForDex) {
	    boolean loadTinkerJars = TinkerDexLoader.loadTinkerJars(app, patchVersionDirectory, oatDex, resultIntent, isSystemOTA);
	
	    if (isSystemOTA) {
	        // update fingerprint after load success
	        patchInfo.fingerPrint = Build.FINGERPRINT;
	        patchInfo.oatDir = loadTinkerJars ? ShareConstants.INTERPRET_DEX_OPTIMIZE_PATH : ShareConstants.DEFAULT_DEX_OPTIMIZE_PATH;
	        // reset to false
	        oatModeChanged = false;
	
	        if (!SharePatchInfo.rewritePatchInfoFileWithLock(patchInfoFile, patchInfo, patchInfoLockFile)) {
	            ShareIntentUtil.setIntentReturnCode(resultIntent, ShareConstants.ERROR_LOAD_PATCH_REWRITE_PATCH_INFO_FAIL);
	            Log.w(TAG, "tryLoadPatchFiles:onReWritePatchInfoCorrupted");
	            return;
	        }
	        // update oat dir
	        resultIntent.putExtra(ShareIntentUtil.INTENT_PATCH_OAT_DIR, patchInfo.oatDir);
	    }
	    if (!loadTinkerJars) {
	        Log.w(TAG, "tryLoadPatchFiles:onPatchLoadDexesFail");
	        return;
	    }
	}
```
	
加载补丁资源

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

结束

``` java
	// Init component hotplug support.
	if (isEnabledForDex && isEnabledForResource) {
	    ComponentHotplug.install(app, securityCheck);
	}
	
	//all is ok!
	ShareIntentUtil.setIntentReturnCode(resultIntent, ShareConstants.ERROR_LOAD_OK);
	Log.i(TAG, "tryLoadPatchFiles: load end, ok!");
	return;
```


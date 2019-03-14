title: Tinker源码分析(六):补丁合成流程
date: 2019-03-14 23:05:56
categories: Android Blog
tags: [Android,开源框架,源码解析,Tinker,热修复]
---
本系列 Tinker 源码解析基于 Tinker v1.9.12

补丁合成流程
==========
下发的补丁包其实并不能直接加载，因为补丁包只是差异包，需要和本地的 dex 、资源等进行合成后，得到全量的 dex 才能被完整地使用。这样也就避免了热修复中 dex 的 pre-verify 问题，也减少了补丁包的体积，方便用户下载。

补丁合成的入口在 TinkerInstaller.onReceiveUpgradePatch 方法

TinkerInstaller.onReceiveUpgradePatch
====
``` java
public static void onReceiveUpgradePatch(Context context, String patchLocation) {
    Tinker.with(context).getPatchListener().onPatchReceived(patchLocation);
}
```

这里的 PatchListener 有默认实现类，即 DefaultPatchListener 。

DefaultPatchListener.onPatchReceived
====
``` java
@Override
public int onPatchReceived(String path) {
    File patchFile = new File(path);
    // 对补丁进行校验
    int returnCode = patchCheck(path, SharePatchFileUtil.getMD5(patchFile));

    if (returnCode == ShareConstants.ERROR_PATCH_OK) {
        // 通过的话就启动 :process 进程进行补丁合成    
        TinkerPatchService.runPatchService(context, path);
    } else {
        // 校验失败就回调 onLoadPatchListenerReceiveFail
        Tinker.with(context).getLoadReporter().onLoadPatchListenerReceiveFail(new File(path), returnCode);
    }
    return returnCode;
}
```

我们直接来看 TinkerPatchService.runPatchService 方法

TinkerPatchService.runPatchService
====
``` java
public static void runPatchService(final Context context, final String path) {
    TinkerLog.i(TAG, "run patch service...");
    Intent intent = new Intent(context, TinkerPatchService.class);
    // path 就是补丁的路径
    intent.putExtra(PATCH_PATH_EXTRA, path);
    // RESULT_CLASS_EXTRA 一般默认就是 DefaultTinkerResultService
    intent.putExtra(RESULT_CLASS_EXTRA, resultServiceClass.getName());
    try {
        enqueueWork(context, TinkerPatchService.class, JOB_ID, intent);
    } catch (Throwable thr) {
        TinkerLog.e(TAG, "run patch service fail, exception:" + thr);
    }
}
```

在 runPatchService 中去启动了 TinkerPatchService 。TinkerPatchService 是跑在 :patch
 进程中的。

TinkerPatchService 主要做的事情都在 onHandleWork 中

``` java
@Override
protected void onHandleWork(Intent intent) {
    // 提高优先级
    increasingPriority();
    // 合成补丁
    doApplyPatch(this, intent);
}
```

首先是 increasingPriority 方法，目的就是提高 service 的优先级，具体的方案就是设置为前台服务

``` java
private void increasingPriority() {
    if (Build.VERSION.SDK_INT >= 26) {
        TinkerLog.i(TAG, "for system version >= Android O, we just ignore increasingPriority "
                + "job to avoid crash or toasts.");
        return;
    }

    if ("ZUK".equals(Build.MANUFACTURER)) {
        TinkerLog.i(TAG, "for ZUK device, we just ignore increasingPriority "
                + "job to avoid crash.");
        return;
    }

    TinkerLog.i(TAG, "try to increase patch process priority");
    // 设置为前台服务，提高优先级
    try {
        Notification notification = new Notification();
        if (Build.VERSION.SDK_INT < 18) {
            startForeground(notificationId, notification);
        } else {
            startForeground(notificationId, notification);
            // start InnerService
            startService(new Intent(this, InnerService.class));
        }
    } catch (Throwable e) {
        TinkerLog.i(TAG, "try to increase patch process priority error:" + e);
    }
}
```

接着是 doApplyPatch 方法，在这里做补丁合成的事

``` java
private static void doApplyPatch(Context context, Intent intent) {
    // Since we may retry with IntentService, we should prevent
    // racing here again.
    if (!sIsPatchApplying.compareAndSet(false, true)) {
        TinkerLog.w(TAG, "TinkerPatchService doApplyPatch is running by another runner.");
        return;
    }

    Tinker tinker = Tinker.with(context);
    tinker.getPatchReporter().onPatchServiceStart(intent);

    if (intent == null) {
        TinkerLog.e(TAG, "TinkerPatchService received a null intent, ignoring.");
        return;
    }
    // 获取补丁文件的路径
    String path = getPatchPathExtra(intent);
    if (path == null) {
        TinkerLog.e(TAG, "TinkerPatchService can't get the path extra, ignoring.");
        return;
    }
    File patchFile = new File(path);

    long begin = SystemClock.elapsedRealtime();
    boolean result;
    long cost;
    Throwable e = null;

    PatchResult patchResult = new PatchResult();
    try {
        if (upgradePatchProcessor == null) {
            throw new TinkerRuntimeException("upgradePatchProcessor is null.");
        }
        // 处理补丁合成
        result = upgradePatchProcessor.tryPatch(context, path, patchResult);
    } catch (Throwable throwable) {
        e = throwable;
        result = false;
        tinker.getPatchReporter().onPatchException(patchFile, e);
    }

    cost = SystemClock.elapsedRealtime() - begin;
    tinker.getPatchReporter().
        onPatchResult(patchFile, result, cost);

    patchResult.isSuccess = result;
    patchResult.rawPatchFilePath = path;
    patchResult.costTime = cost;
    patchResult.e = e;
    // 补丁合成的结果回调给 DefaultResultService
    AbstractResultService.runResultService(context, patchResult, getPatchResultExtra(intent));

    sIsPatchApplying.set(false);
}
```

upgradePatchProcessor 是一个接口，具体的实现类是 UpgradePatch 。

UpgradePatch.tryPatch
===
那么来看看 UpgradePatch.tryPatch ，方法比较长，分段来看吧。

首先是对 Tinker 自身开关的校验，然后对补丁文件的合法性进行校验。

``` java
@Override
public boolean tryPatch(Context context, String tempPatchPath, PatchResult patchResult) {
    Tinker manager = Tinker.with(context);

    final File patchFile = new File(tempPatchPath);

    if (!manager.isTinkerEnabled() || !ShareTinkerInternals.isTinkerEnableWithSharedPreferences(context)) {
        TinkerLog.e(TAG, "UpgradePatch tryPatch:patch is disabled, just return");
        return false;
    }

    if (!SharePatchFileUtil.isLegalFile(patchFile)) {
        TinkerLog.e(TAG, "UpgradePatch tryPatch:patch file is not found, just return");
        return false;
    }
```

然后检查补丁包的签名以及 tinkerId , 这里的操作和加载补丁是一样的。

然后就是获取补丁文件的 md5 值

``` java
//check the signature, we should create a new checker
ShareSecurityCheck signatureCheck = new ShareSecurityCheck(context);

int returnCode = ShareTinkerInternals.checkTinkerPackage(context, manager.getTinkerFlags(), patchFile, signatureCheck);
if (returnCode != ShareConstants.ERROR_PACKAGE_CHECK_OK) {
    TinkerLog.e(TAG, "UpgradePatch tryPatch:onPatchPackageCheckFail");
    manager.getPatchReporter().onPatchPackageCheckFail(patchFile, returnCode);
    return false;
}

// 获取补丁文件的 md5
String patchMd5 = SharePatchFileUtil.getMD5(patchFile);
if (patchMd5 == null) {
    TinkerLog.e(TAG, "UpgradePatch tryPatch:patch md5 is null, just return");
    return false;
}
//use md5 as version
// 用 md5 做版本号
patchResult.patchVersion = patchMd5;

TinkerLog.i(TAG, "UpgradePatch tryPatch:patchMd5:%s", patchMd5);
```

接着，校验完成后，我们就来构造一个新的 patch.info 文件了。

``` java
//check ok, we can real recover a new patch
final String patchDirectory = manager.getPatchDirectory().getAbsolutePath();

// info.lock 文件
File patchInfoLockFile = SharePatchFileUtil.getPatchInfoLockFile(patchDirectory);
// patch.info 文件
File patchInfoFile = SharePatchFileUtil.getPatchInfoFile(patchDirectory);

// 读取出老的 patch.info 文件，可能存在 可能不存在
SharePatchInfo oldInfo = SharePatchInfo.readAndCheckPropertyWithLock(patchInfoFile, patchInfoLockFile);

//it is a new patch, so we should not find a exist
SharePatchInfo newInfo;

//如果有老的 patch.info 文件
if (oldInfo != null) {
    if (oldInfo.oldVersion == null || oldInfo.newVersion == null || oldInfo.oatDir == null) {
        TinkerLog.e(TAG, "UpgradePatch tryPatch:onPatchInfoCorrupted");
        manager.getPatchReporter().onPatchInfoCorrupted(patchFile, oldInfo.oldVersion, oldInfo.newVersion);
        return false;
    }

    if (!SharePatchFileUtil.checkIfMd5Valid(patchMd5)) {
        TinkerLog.e(TAG, "UpgradePatch tryPatch:onPatchVersionCheckFail md5 %s is valid", patchMd5);
        manager.getPatchReporter().onPatchVersionCheckFail(patchFile, oldInfo, patchMd5);
        return false;
    }
    // if it is interpret now, use changing flag to wait main process
    final String finalOatDir = oldInfo.oatDir.equals(ShareConstants.INTERPRET_DEX_OPTIMIZE_PATH)
        ? ShareConstants.CHANING_DEX_OPTIMIZE_PATH : oldInfo.oatDir;
    // 构造新的 patch.info 
    newInfo = new SharePatchInfo(oldInfo.oldVersion, patchMd5, false, Build.FINGERPRINT, finalOatDir);
} else {
    // 构造新的 patch.info 
    newInfo = new SharePatchInfo("", patchMd5, false, Build.FINGERPRINT, ShareConstants.DEFAULT_DEX_OPTIMIZE_PATH);
}
```

再接下来，就是把补丁包复制到私有目录中

具体的路径也就是之前在加载补丁中遇到的 /data/data/应用包名/tinker/patch-xxxxxx/patch-xxxxxx.apk

``` java
//it is a new patch, we first delete if there is any files
//don't delete dir for faster retry
//        SharePatchFileUtil.deleteDir(patchVersionDirectory);
final String patchName = SharePatchFileUtil.getPatchVersionDirectory(patchMd5);

final String patchVersionDirectory = patchDirectory + "/" + patchName;

TinkerLog.i(TAG, "UpgradePatch tryPatch:patchVersionDirectory:%s", patchVersionDirectory);

//copy file
File destPatchFile = new File(patchVersionDirectory + "/" + SharePatchFileUtil.getPatchVersionFile(patchMd5));

try {
  // check md5 first
  if (!patchMd5.equals(SharePatchFileUtil.getMD5(destPatchFile))) {
      // 复制补丁包到 /data/data/ 中
      SharePatchFileUtil.copyFileUsingStream(patchFile, destPatchFile);
      TinkerLog.w(TAG, "UpgradePatch copy patch file, src file: %s size: %d, dest file: %s size:%d", patchFile.getAbsolutePath(), patchFile.length(),
          destPatchFile.getAbsolutePath(), destPatchFile.length());
  }
} catch (IOException e) {
//            e.printStackTrace();
  TinkerLog.e(TAG, "UpgradePatch tryPatch:copy patch file fail from %s to %s", patchFile.getPath(), destPatchFile.getPath());
  manager.getPatchReporter().onPatchTypeExtractFail(patchFile, destPatchFile, patchFile.getName(), ShareConstants.TYPE_PATCH_FILE);
  return false;
}
```

复制好之后，就是把补丁包和基准包进行整合了

``` java
//we use destPatchFile instead of patchFile, because patchFile may be deleted during the patch process
// 合成 dex
if (!DexDiffPatchInternal.tryRecoverDexFiles(manager, signatureCheck, context, patchVersionDirectory, destPatchFile)) {
    TinkerLog.e(TAG, "UpgradePatch tryPatch:new patch recover, try patch dex failed");
    return false;
}
// 合成 so 文件
if (!BsDiffPatchInternal.tryRecoverLibraryFiles(manager, signatureCheck, context, patchVersionDirectory, destPatchFile)) {
    TinkerLog.e(TAG, "UpgradePatch tryPatch:new patch recover, try patch library failed");
    return false;
}
// 合成资源文件
if (!ResDiffPatchInternal.tryRecoverResourceFiles(manager, signatureCheck, context, patchVersionDirectory, destPatchFile)) {
    TinkerLog.e(TAG, "UpgradePatch tryPatch:new patch recover, try patch resource failed");
    return false;
}
```

这里面的三个合成代码我们到后面的章节再分析，这里先跳过了。

合成完后，还要对 dex 进行opt优化

``` java
// check dex opt file at last, some phone such as VIVO/OPPO like to change dex2oat to interpreted
if (!DexDiffPatchInternal.waitAndCheckDexOptFile(patchFile, manager)) {
    TinkerLog.e(TAG, "UpgradePatch tryPatch:new patch recover, check dex opt file failed");
    return false;
}
```

最后，就是把结果重新写入到 patch.info ，这样在加载补丁的流程中就能加载新补丁了。

``` java
if (!SharePatchInfo.rewritePatchInfoFileWithLock(patchInfoFile, newInfo, patchInfoLockFile)) {
    TinkerLog.e(TAG, "UpgradePatch tryPatch:new patch recover, rewrite patch info failed");
    manager.getPatchReporter().onPatchInfoCorrupted(patchFile, newInfo.oldVersion, newInfo.newVersion);
    return false;
}

TinkerLog.w(TAG, "UpgradePatch tryPatch: done, it is ok");
return true;
```

over ，整个合成补丁的流程讲完了，这里还留了三个坑：

* dex 文件的合成
* so 文件的合成
* 资源文件的合成

到后面再讲吧。


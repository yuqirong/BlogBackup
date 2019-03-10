title: Tinker源码分析(三):加载dex补丁流程
date: 2019-02-28 19:23:19
categories: Android Blog
tags: [Android,开源框架,源码解析,Tinker,热修复]
---
本系列 Tinker 源码解析基于 Tinker v1.9.12

加载dex补丁流程
=============

TinkerDexLoader.loadTinkerJars
------------------------------
判断一下 dexList 和 classLoader

``` java
	if (loadDexList.isEmpty() && classNDexInfo.isEmpty()) {
	    Log.w(TAG, "there is no dex to load");
	    return true;
	}
	
	PathClassLoader classLoader = (PathClassLoader) TinkerDexLoader.class.getClassLoader();
	if (classLoader != null) {
	    Log.i(TAG, "classloader: " + classLoader.toString());
	} else {
	    Log.e(TAG, "classloader is null");
	    ShareIntentUtil.setIntentReturnCode(intentResult, ShareConstants.ERROR_LOAD_PATCH_VERSION_DEX_CLASSLOADER_NULL);
	    return false;
	}
```

如果 TinkerLoadVerifyFlag 为 true 的话，会对每个 dex 进行 md5 校验

``` java
	String dexPath = directory + "/" + DEX_PATH + "/";
	
	ArrayList<File> legalFiles = new ArrayList<>();
	
	for (ShareDexDiffPatchInfo info : loadDexList) {
	    //for dalvik, ignore art support dex
	    // 对于 dalvik 虚拟机，忽略 art support dex
	    if (isJustArtSupportDex(info)) {
	        continue;
	    }
	
	    String path = dexPath + info.realName;
	    File file = new File(path);
	
	    if (application.isTinkerLoadVerifyFlag()) {
	        long start = System.currentTimeMillis();
	        String checkMd5 = getInfoMd5(info);
	        // 校验dex文件的 md5 值
	        if (!SharePatchFileUtil.verifyDexFileMd5(file, checkMd5)) {
	            //it is good to delete the mismatch file
	            ShareIntentUtil.setIntentReturnCode(intentResult, ShareConstants.ERROR_LOAD_PATCH_VERSION_DEX_MD5_MISMATCH);
	            intentResult.putExtra(ShareIntentUtil.INTENT_PATCH_MISMATCH_DEX_PATH,
	                file.getAbsolutePath());
	            return false;
	        }
	        Log.i(TAG, "verify dex file:" + file.getPath() + " md5, use time: " + (System.currentTimeMillis() - start));
	    }
	    // dex文件都通过的话，加入到 legalFiles 集合中 
	    legalFiles.add(file);
	}
```
	
如果是 art 虚拟机并且是 Android N 及以上的环境，会另外加上 tinker_classN.apk
	
``` java
	// verify merge classN.apk
	if (isVmArt && !classNDexInfo.isEmpty()) {
	    File classNFile = new File(dexPath + ShareConstants.CLASS_N_APK_NAME);
	    long start = System.currentTimeMillis();
	
	    if (application.isTinkerLoadVerifyFlag()) {
	        for (ShareDexDiffPatchInfo info : classNDexInfo) {
	            if (!SharePatchFileUtil.verifyDexFileMd5(classNFile, info.rawName, info.destMd5InArt)) {
	                ShareIntentUtil.setIntentReturnCode(intentResult, ShareConstants.ERROR_LOAD_PATCH_VERSION_DEX_MD5_MISMATCH);
	                intentResult.putExtra(ShareIntentUtil.INTENT_PATCH_MISMATCH_DEX_PATH,
	                    classNFile.getAbsolutePath());
	                return false;
	            }
	        }
	    }
	    Log.i(TAG, "verify dex file:" + classNFile.getPath() + " md5, use time: " + (System.currentTimeMillis() - start));
	
	    legalFiles.add(classNFile);
	}
```

如果用户是ART虚拟机并且做了OTA升级，那么在加载dex补丁的时候就会先把最近一次的补丁全部DexFile.loadDex一遍.这么做的原因是有些场景做了OTA后,oat的规则可能发生变化,在这种情况下去加载上个系统版本oat过的dex就会出现问题.

``` java
	File optimizeDir = new File(directory + "/" + oatDir);
	
	        if (isSystemOTA) {
	            final boolean[] parallelOTAResult = {true};
	            final Throwable[] parallelOTAThrowable = new Throwable[1];
	            String targetISA;
	            try {
	                targetISA = ShareTinkerInternals.getCurrentInstructionSet();
	            } catch (Throwable throwable) {
	                Log.i(TAG, "getCurrentInstructionSet fail:" + throwable);
	//                try {
	//                    targetISA = ShareOatUtil.getOatFileInstructionSet(testOptDexFile);
	//                } catch (Throwable throwable) {
	                // don't ota on the front
	                deleteOutOfDateOATFile(directory);
	
	                intentResult.putExtra(ShareIntentUtil.INTENT_PATCH_INTERPRET_EXCEPTION, throwable);
	                ShareIntentUtil.setIntentReturnCode(intentResult, ShareConstants.ERROR_LOAD_PATCH_GET_OTA_INSTRUCTION_SET_EXCEPTION);
	                return false;
	//                }
	            }
	
	            deleteOutOfDateOATFile(directory);
	
	            Log.w(TAG, "systemOTA, try parallel oat dexes, targetISA:" + targetISA);
	            // change dir
	            optimizeDir = new File(directory + "/" + INTERPRET_DEX_OPTIMIZE_PATH);
	
	            // 对 dex 文件作 odex 处理
	            TinkerDexOptimizer.optimizeAll(
	                legalFiles, optimizeDir, true, targetISA,
	                new TinkerDexOptimizer.ResultCallback() {
	                    long start;
	
	                    @Override
	                    public void onStart(File dexFile, File optimizedDir) {
	                        start = System.currentTimeMillis();
	                        Log.i(TAG, "start to optimize dex:" + dexFile.getPath());
	                    }
	
	                    @Override
	                    public void onSuccess(File dexFile, File optimizedDir, File optimizedFile) {
	                        // Do nothing.
	                        Log.i(TAG, "success to optimize dex " + dexFile.getPath() + ", use time " + (System.currentTimeMillis() - start));
	                    }
	
	                    @Override
	                    public void onFailed(File dexFile, File optimizedDir, Throwable thr) {
	                        parallelOTAResult[0] = false;
	                        parallelOTAThrowable[0] = thr;
	                        Log.i(TAG, "fail to optimize dex " + dexFile.getPath() + ", use time " + (System.currentTimeMillis() - start));
	                    }
	                }
	            );
	
	
	            if (!parallelOTAResult[0]) {
	                Log.e(TAG, "parallel oat dexes failed");
	                intentResult.putExtra(ShareIntentUtil.INTENT_PATCH_INTERPRET_EXCEPTION, parallelOTAThrowable[0]);
	                ShareIntentUtil.setIntentReturnCode(intentResult, ShareConstants.ERROR_LOAD_PATCH_OTA_INTERPRET_ONLY_EXCEPTION);
	                return false;
	            }
	        }
```

加载dex
-------

``` java
	try {
	       SystemClassLoaderAdder.installDexes(application, classLoader, optimizeDir, legalFiles);
	   } catch (Throwable e) {
	       Log.e(TAG, "install dexes failed");
	//            e.printStackTrace();
	       intentResult.putExtra(ShareIntentUtil.INTENT_PATCH_EXCEPTION, e);
	       ShareIntentUtil.setIntentReturnCode(intentResult, ShareConstants.ERROR_LOAD_PATCH_VERSION_DEX_LOAD_EXCEPTION);
	       return false;
	   }
		
	   return true;
```

SystemClassLoaderAdder.installDexes
-----------------------------------
``` java
	public static void installDexes(Application application, PathClassLoader loader, File dexOptDir, List<File> files)
	    throws Throwable {
	    Log.i(TAG, "installDexes dexOptDir: " + dexOptDir.getAbsolutePath() + ", dex size:" + files.size());
	
	    if (!files.isEmpty()) {
	        files = createSortedAdditionalPathEntries(files);
	        ClassLoader classLoader = loader;
	        if (Build.VERSION.SDK_INT >= 24 && !checkIsProtectedApp(files)) {
	            classLoader = AndroidNClassLoader.inject(loader, application);
	        }
	        //because in dalvik, if inner class is not the same classloader with it wrapper class.
	        //it won't fail at dex2opt
	        if (Build.VERSION.SDK_INT >= 23) {
	            V23.install(classLoader, files, dexOptDir);
	        } else if (Build.VERSION.SDK_INT >= 19) {
	            V19.install(classLoader, files, dexOptDir);
	        } else if (Build.VERSION.SDK_INT >= 14) {
	            V14.install(classLoader, files, dexOptDir);
	        } else {
	            V4.install(classLoader, files, dexOptDir);
	        }
	        //install done
	        sPatchDexCount = files.size();
	        Log.i(TAG, "after loaded classloader: " + classLoader + ", dex size:" + sPatchDexCount);
	
	        if (!checkDexInstall(classLoader)) {
	            //reset patch dex
	            SystemClassLoaderAdder.uninstallPatchDex(classLoader);
	            throw new TinkerRuntimeException(ShareConstants.CHECK_DEX_INSTALL_FAIL);
	        }
	    }
	}
```

可以看到，在加载 dex 的时候，分别分成了四个版本：

* v4
* v14
* v19
* v23

其中如果是 SDK 24 及以上，需要改造一下 classloder

针对每个版本不同的源码，进行 dex 插入

我们就来看其中一个版本 v19

``` java
	private static final class V19 {
	
	    private static void install(ClassLoader loader, List<File> additionalClassPathEntries,
	                                File optimizedDirectory)
	        throws IllegalArgumentException, IllegalAccessException,
	        NoSuchFieldException, InvocationTargetException, NoSuchMethodException, IOException {
	        /* The patched class loader is expected to be a descendant of
	         * dalvik.system.BaseDexClassLoader. We modify its
	         * dalvik.system.DexPathList pathList field to append additional DEX
	         * file entries.
	         */
	        Field pathListField = ShareReflectUtil.findField(loader, "pathList");
	        Object dexPathList = pathListField.get(loader);
	        ArrayList<IOException> suppressedExceptions = new ArrayList<IOException>();
	        ShareReflectUtil.expandFieldArray(dexPathList, "dexElements", makeDexElements(dexPathList,
	            new ArrayList<File>(additionalClassPathEntries), optimizedDirectory,
	            suppressedExceptions));
	        if (suppressedExceptions.size() > 0) {
	            for (IOException e : suppressedExceptions) {
	                Log.w(TAG, "Exception in makeDexElement", e);
	                throw e;
	            }
	        }
	    }
	
	    /**
	     * A wrapper around
	     * {@code private static final dalvik.system.DexPathList#makeDexElements}.
	     */
	    private static Object[] makeDexElements(
	        Object dexPathList, ArrayList<File> files, File optimizedDirectory,
	        ArrayList<IOException> suppressedExceptions)
	        throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
	
	        Method makeDexElements = null;
	        try {
	            makeDexElements = ShareReflectUtil.findMethod(dexPathList, "makeDexElements", ArrayList.class, File.class,
	                ArrayList.class);
	        } catch (NoSuchMethodException e) {
	            Log.e(TAG, "NoSuchMethodException: makeDexElements(ArrayList,File,ArrayList) failure");
	            // 对不同的 rom 做兼容，有的 rom 的 makeDexElements 方法参数类型是 List
	            try {
	                makeDexElements = ShareReflectUtil.findMethod(dexPathList, "makeDexElements", List.class, File.class, List.class);
	            } catch (NoSuchMethodException e1) {
	                Log.e(TAG, "NoSuchMethodException: makeDexElements(List,File,List) failure");
	                throw e1;
	            }
	        }
	
	        return (Object[]) makeDexElements.invoke(dexPathList, files, optimizedDirectory, suppressedExceptions);
	    }
	}
```

首先反射拿到反射得到 PathClassLoader 中的 pathList 对象,再将补丁文件通过反射调用makeDexElements 得到补丁文件的 Element[] ,再将补丁包的 Element[] 数组插入到 dexElements 中

另外，需要对 Android 7.0 及以上单独处理一下，具体看 [Android N混合编译与对热补丁影响解析](https://mp.weixin.qq.com/s?__biz=MzAwNDY1ODY2OQ==&mid=2649286341&idx=1&sn=054d595af6e824cbe4edd79427fc2706&scene=0)

加载补丁操作做好之后，最后还要检查一下，如果没加载成功就会执行卸载：

``` java
	if (!checkDexInstall(classLoader)) {
	    //reset patch dex
	    SystemClassLoaderAdder.uninstallPatchDex(classLoader);
	    throw new TinkerRuntimeException(ShareConstants.CHECK_DEX_INSTALL_FAIL);
	}
```

具体验证补丁是否加载成功的方法就是判断 TinkerTestDexLoad.isPatch 的值。

在没有补丁加载的情况下都是返回 false 的, 在补丁中修改 isPatch 属性为 true 。所以只要反射拿到isPatch 的属性为 true 就说明补丁已经成功加载进来了。否则就调用 SystemClassLoaderAdder.uninstallPatchDex 执行卸载

``` java
	public static void uninstallPatchDex(ClassLoader classLoader) throws Throwable {
	    if (sPatchDexCount <= 0) {
	        return;
	    }
	    if (Build.VERSION.SDK_INT >= 14) {
	        Field pathListField = ShareReflectUtil.findField(classLoader, "pathList");
	        Object dexPathList = pathListField.get(classLoader);
	        ShareReflectUtil.reduceFieldArray(dexPathList, "dexElements", sPatchDexCount);
	    } else {
	        ShareReflectUtil.reduceFieldArray(classLoader, "mPaths", sPatchDexCount);
	        ShareReflectUtil.reduceFieldArray(classLoader, "mFiles", sPatchDexCount);
	        ShareReflectUtil.reduceFieldArray(classLoader, "mZips", sPatchDexCount);
	        try {
	            ShareReflectUtil.reduceFieldArray(classLoader, "mDexs", sPatchDexCount);
	        } catch (Exception e) {
	        }
	    }
	}
```

卸载补丁可以说是加载补丁的逆向操作，具体操作可以分成 v4 和 v14 两个版本

具体的内容就是把 dexElements 中的头部 element 去除了。

到这，dex 补丁加载的流程结束了。



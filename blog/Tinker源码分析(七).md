title: Tinker源码分析(七):dex合成流程
date: 2019-03-20 23:36:31
categories: Android Blog
tags: [Android,开源框架,源码解析,Tinker,热修复]
---
本系列 Tinker 源码解析基于 Tinker v1.9.12

前面讲到了 Tinker 安装补丁的流程，现在就详细地来看下 dex 合成的代码。代码入口就在 DexDiffPatchInternal.tryRecoverDexFiles 中。

UpgradePatch
============
``` java
//we use destPatchFile instead of patchFile, because patchFile may be deleted during the patch process
if (!DexDiffPatchInternal.tryRecoverDexFiles(manager, signatureCheck, context, patchVersionDirectory, destPatchFile)) {
    TinkerLog.e(TAG, "UpgradePatch tryPatch:new patch recover, try patch dex failed");
    return false;
}
```

直接调用了 DexDiffPatchInternal.tryRecoverDexFiles 方法。

tryRecoverDexFiles
=========
``` java
protected static boolean tryRecoverDexFiles(Tinker manager, ShareSecurityCheck checker, Context context,
                                            String patchVersionDirectory, File patchFile) {
    // 检查是否开启支持dex补丁开关                                        
    if (!manager.isEnabledForDex()) {
        TinkerLog.w(TAG, "patch recover, dex is not enabled");
        return true;
    }
    // 检查补丁包中的 dex_meta.txt 是否存在
    String dexMeta = checker.getMetaContentMap().get(DEX_META_FILE);

    if (dexMeta == null) {
        TinkerLog.w(TAG, "patch recover, dex is not contained");
        return true;
    }

    long begin = SystemClock.elapsedRealtime();
    // 到这个方法中执行具体的操作
    boolean result = patchDexExtractViaDexDiff(context, patchVersionDirectory, dexMeta, patchFile);
    long cost = SystemClock.elapsedRealtime() - begin;
    TinkerLog.i(TAG, "recover dex result:%b, cost:%d", result, cost);
    return result;
}
```

tryRecoverDexFiles 方法开头做了些校验，最后又到 patchDexExtractViaDexDiff 中。

patchDexExtractViaDexDiff
=====
``` java
private static boolean patchDexExtractViaDexDiff(Context context, String patchVersionDirectory, String meta, final File patchFile) {
    // dex补丁合成的路径
    String dir = patchVersionDirectory + "/" + DEX_PATH + "/";
    // extractDexDiffInternals 这个方法是重点！！！
    if (!extractDexDiffInternals(context, dir, meta, patchFile, TYPE_DEX)) {
        TinkerLog.w(TAG, "patch recover, extractDiffInternals fail");
        return false;
    }

    // 把 tinker/patch-xxxxx/dex/ 下面的文件校验下，看看是否是合法的dex文件
    File dexFiles = new File(dir);
    File[] files = dexFiles.listFiles();
    List<File> legalFiles = new ArrayList<>();
    // may have directory in android o
    if (files != null) {
        for (File file : files) {
            final String fileName = file.getName();
            if (file.isFile()
                &&  (fileName.endsWith(ShareConstants.DEX_SUFFIX)
                  || fileName.endsWith(ShareConstants.JAR_SUFFIX)
                  || fileName.endsWith(ShareConstants.PATCH_SUFFIX))
            ) {
                legalFiles.add(file);
            }
        }
    }

    TinkerLog.i(TAG, "legal files to do dexopt: " + legalFiles);
    // 对 dex 做 opt 优化
    final String optimizeDexDirectory = patchVersionDirectory + "/" + DEX_OPTIMIZE_PATH + "/";
    return dexOptimizeDexFiles(context, legalFiles, optimizeDexDirectory, patchFile);

}
```

在 patchDexExtractViaDexDiff 中可以看到， dex 文件合成之后，会对其做 opt 优化。而合成的代码就在 extractDexDiffInternals 里面。

extractDexDiffInternals 方法有点长。按照老规矩，我们分段来看。

extractDexDiffInternals
=====
``` java
private static boolean extractDexDiffInternals(Context context, String dir, String meta, File patchFile, int type) {
    
    // 读取 dex_meta.txt 中的信息
    patchList.clear();
    ShareDexDiffPatchInfo.parseDexDiffPatchInfo(meta, patchList);

    if (patchList.isEmpty()) {
        TinkerLog.w(TAG, "extract patch list is empty! type:%s:", ShareTinkerInternals.getTypeString(type));
        return true;
    }
    
```

首先读取 dex_meta.txt 中的信息，用“,”分割，保存到 patchList 中。

下面贴出一份 dex_meta.txt 的示例：

```
	classes.dex,,1a6e6d6a40eff95aa33ab06e07acd413,1a6e6d6a40eff95aa33ab06e07acd413,d865f383455abd6e3f70096109543644,2999635299,712828526,jar
	test.dex,,56900442eb5b7e1de45449d0685e6e00,56900442eb5b7e1de45449d0685e6e00,0,0,0,jar
```

dex_meta.txt 记录着

* name ：补丁 dex 名字
* path ：补丁 dex 路径
* destMd5InDvm ：合成新 dex 在 dvm 中的 md5 值
* destMd5InArt ：合成新 dex 在 art 中的 md5 值
* dexDiffMd5 ：补丁包 dex 文件的 md5 值
* oldDexCrc ：基准包中对应 dex 的 crc 值
* newDexCrc ：合成新 dex 的 crc 值
* dexMode ：dex 类型，为 jar 类型


接着往下看。

``` java
	File directory = new File(dir);
	if (!directory.exists()) {
	   directory.mkdirs();
	}
	//I think it is better to extract the raw files from apk
	Tinker manager = Tinker.with(context);
	ZipFile apk = null;
	ZipFile patch = null;
	try {
	   ApplicationInfo applicationInfo = context.getApplicationInfo();
	   if (applicationInfo == null) {
	       // Looks like running on a test Context, so just return without patching.
	       TinkerLog.w(TAG, "applicationInfo == null!!!!");
	       return false;
	   }
	   // 获取到基准包apk的路径
	   String apkPath = applicationInfo.sourceDir;
	   // 基准包文件
	   apk = new ZipFile(apkPath);
	   // 补丁包文件
	   patch = new ZipFile(patchFile);
	   if (checkClassNDexFiles(dir)) {
	       TinkerLog.w(TAG, "class n dex file %s is already exist, and md5 match, just continue", ShareConstants.CLASS_N_APK_NAME);
	       return true;
	   }
```

然后获取基本包和补丁包的路径，为下面合成做准备。
   
``` java     
// 遍历 ShareDexDiffPatchInfo
for (ShareDexDiffPatchInfo info : patchList) {
  long start = System.currentTimeMillis();

	// 补丁dex文件路径
  final String infoPath = info.path;
  String patchRealPath;
  if (infoPath.equals("")) {
      patchRealPath = info.rawName;
  } else {
      patchRealPath = info.path + "/" + info.rawName;
  }

  String dexDiffMd5 = info.dexDiffMd5;
  String oldDexCrc = info.oldDexCrC;

	// 如果是 dvm 虚拟机环境，但是补丁dex是art环境的，就跳过
  if (!isVmArt && info.destMd5InDvm.equals("0")) {
      TinkerLog.w(TAG, "patch dex %s is only for art, just continue", patchRealPath);
      continue;
  }
  String extractedFileMd5 = isVmArt ? info.destMd5InArt : info.destMd5InDvm;
  // 检查 md5 值
  if (!SharePatchFileUtil.checkIfMd5Valid(extractedFileMd5)) {
      TinkerLog.w(TAG, "meta file md5 invalid, type:%s, name: %s, md5: %s", ShareTinkerInternals.getTypeString(type), info.rawName, extractedFileMd5);
      manager.getPatchReporter().onPatchPackageCheckFail(patchFile, BasePatchInternal.getMetaCorruptedCode(type));
      return false;
  }

  File extractedFile = new File(dir + info.realName);

  // 如果合成的dex文件已经存在了
  if (extractedFile.exists()) {
      // 就校验合成的 dex 文件md5值，如果通过就跳过
      if (SharePatchFileUtil.verifyDexFileMd5(extractedFile, extractedFileMd5)) {
          //it is ok, just continue
          TinkerLog.w(TAG, "dex file %s is already exist, and md5 match, just continue", extractedFile.getPath());
          continue;
      } else {
          TinkerLog.w(TAG, "have a mismatch corrupted dex " + extractedFile.getPath());
          // 否则删除文件
          extractedFile.delete();
      }
  } else {
      extractedFile.getParentFile().mkdirs();
  }
```

从这里开始，就是遍历 patchList 中的记录，进行一个个 dex 文件合成了。一开头会去校验合成的文件是否存在，存在的话就跳过，进行下一个。

``` java
  ZipEntry patchFileEntry = patch.getEntry(patchRealPath);
  ZipEntry rawApkFileEntry = apk.getEntry(patchRealPath);

  if (oldDexCrc.equals("0")) {
      if (patchFileEntry == null) {
          TinkerLog.w(TAG, "patch entry is null. path:" + patchRealPath);
          manager.getPatchReporter().onPatchTypeExtractFail(patchFile, extractedFile, info.rawName, type);
          return false;
      }

      //it is a new file, but maybe we need to repack the dex file
      if (!extractDexFile(patch, patchFileEntry, extractedFile, info)) {
          TinkerLog.w(TAG, "Failed to extract raw patch file " + extractedFile.getPath());
          manager.getPatchReporter().onPatchTypeExtractFail(patchFile, extractedFile, info.rawName, type);
          return false;
      }
  } 
```

如果 oldDexCrc 为0，就说明基准包中对应的 oldDex 文件不存在，直接按照 patch 信息重新打包 dex 即可。
     
``` java 
// 如果 dexDiffMd5 为 0， 就说明补丁包中没有这个dex，但是基准包中存在
  else if (dexDiffMd5.equals("0")) {
      // skip process old dex for real dalvik vm
      // 如果是 dvm 环境的无须做处理
      if (!isVmArt) {
          continue;
      }

      // 检查基准包中的 dex 是否为空
      if (rawApkFileEntry == null) {
          TinkerLog.w(TAG, "apk entry is null. path:" + patchRealPath);
          manager.getPatchReporter().onPatchTypeExtractFail(patchFile, extractedFile, info.rawName, type);
          return false;
      }

      //check source crc instead of md5 for faster
      // 检查基准包中的 dex 的 crc 值和 dex_meta.txt 中是否一致
      String rawEntryCrc = String.valueOf(rawApkFileEntry.getCrc());
      if (!rawEntryCrc.equals(oldDexCrc)) {
          TinkerLog.e(TAG, "apk entry %s crc is not equal, expect crc: %s, got crc: %s", patchRealPath, oldDexCrc, rawEntryCrc);
          manager.getPatchReporter().onPatchTypeExtractFail(patchFile, extractedFile, info.rawName, type);
          return false;
      }

      // Small patched dex generating strategy was disabled, we copy full original dex directly now.
      //patchDexFile(apk, patch, rawApkFileEntry, null, info, smallPatchInfoFile, extractedFile);
      // 直接复制 ：copy full original dex directly now.
      extractDexFile(apk, rawApkFileEntry, extractedFile, info);

      // 复制完后校验一下md5值是否一致
      if (!SharePatchFileUtil.verifyDexFileMd5(extractedFile, extractedFileMd5)) {
          TinkerLog.w(TAG, "Failed to recover dex file when verify patched dex: " + extractedFile.getPath());
          manager.getPatchReporter().onPatchTypeExtractFail(patchFile, extractedFile, info.rawName, type);
          SharePatchFileUtil.safeDeleteFile(extractedFile);
          return false;
      }
  } 
```
            
上面这段代码用来处理基准包中有 oldDex ，但是补丁包中没有 dex 的情况。

如果是 dvm 环境就跳过不处理即可，如果是 art 环境就把 oldDex 复制过去。
   
``` java       
            else {
                // 检查补丁包中 dex 是否存在
                if (patchFileEntry == null) {
                    TinkerLog.w(TAG, "patch entry is null. path:" + patchRealPath);
                    manager.getPatchReporter().onPatchTypeExtractFail(patchFile, extractedFile, info.rawName, type);
                    return false;
                }
                // 检查补丁包中的 dex md5值是否合法
                if (!SharePatchFileUtil.checkIfMd5Valid(dexDiffMd5)) {
                    TinkerLog.w(TAG, "meta file md5 invalid, type:%s, name: %s, md5: %s", ShareTinkerInternals.getTypeString(type), info.rawName, dexDiffMd5);
                    manager.getPatchReporter().onPatchPackageCheckFail(patchFile, BasePatchInternal.getMetaCorruptedCode(type));
                    return false;
                }
                // 检查基准包中的 dex 是否存在
                if (rawApkFileEntry == null) {
                    TinkerLog.w(TAG, "apk entry is null. path:" + patchRealPath);
                    manager.getPatchReporter().onPatchTypeExtractFail(patchFile, extractedFile, info.rawName, type);
                    return false;
                }
                // 检查基准包中的 dex 的 crc 值是否一致
                String rawEntryCrc = String.valueOf(rawApkFileEntry.getCrc());
                if (!rawEntryCrc.equals(oldDexCrc)) {
                    TinkerLog.e(TAG, "apk entry %s crc is not equal, expect crc: %s, got crc: %s", patchRealPath, oldDexCrc, rawEntryCrc);
                    manager.getPatchReporter().onPatchTypeExtractFail(patchFile, extractedFile, info.rawName, type);
                    return false;
                }
                // 执行合成操作
                patchDexFile(apk, patch, rawApkFileEntry, patchFileEntry, info, extractedFile);
                // 检查合成出来的dex的 md5 值是否一致
                if (!SharePatchFileUtil.verifyDexFileMd5(extractedFile, extractedFileMd5)) {
                    TinkerLog.w(TAG, "Failed to recover dex file when verify patched dex: " + extractedFile.getPath());
                    manager.getPatchReporter().onPatchTypeExtractFail(patchFile, extractedFile, info.rawName, type);
                    SharePatchFileUtil.safeDeleteFile(extractedFile);
                    return false;
                }

                TinkerLog.w(TAG, "success recover dex file: %s, size: %d, use time: %d",
                    extractedFile.getPath(), extractedFile.length(), (System.currentTimeMillis() - start));
            }
        }
        if (!mergeClassNDexFiles(context, patchFile, dir)) {
            return false;
        }
    } catch (Throwable e) {
        throw new TinkerRuntimeException("patch " + ShareTinkerInternals.getTypeString(type) + " extract failed (" + e.getMessage() + ").", e);
    } finally {
        SharePatchFileUtil.closeZip(apk);
        SharePatchFileUtil.closeZip(patch);
    }
    return true;
}
```

最后，就是基准包和补丁包中都存在对应 dex 的情况了。

代码一开始就是一堆的各种校验，都通过后，调用 patchDexFile 执行合成操作。合成完后再对合成的 dex 进行md5校验。

patchDexFile
======
``` java
private static void patchDexFile(
    ZipFile baseApk, ZipFile patchPkg, ZipEntry oldDexEntry, ZipEntry patchFileEntry,
    ShareDexDiffPatchInfo patchInfo, File patchedDexFile) throws IOException {
    InputStream oldDexStream = null;
    InputStream patchFileStream = null;
    try {
        // 基准包 dex 文件输入流
        oldDexStream = new BufferedInputStream(baseApk.getInputStream(oldDexEntry));
        // 补丁包 dex 文件输入流
        patchFileStream = (patchFileEntry != null ? new BufferedInputStream(patchPkg.getInputStream(patchFileEntry)) : null);

        final boolean isRawDexFile = SharePatchFileUtil.isRawDexFile(patchInfo.rawName);
        if (!isRawDexFile || patchInfo.isJarMode) {
            ZipOutputStream zos = null;
            try {
                // 合成 dex 文件的输出流
                zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(patchedDexFile)));
                zos.putNextEntry(new ZipEntry(ShareConstants.DEX_IN_JAR));
                // Old dex is not a raw dex file.
                if (!isRawDexFile) {
                    ZipInputStream zis = null;
                    try {
                        zis = new ZipInputStream(oldDexStream);
                        ZipEntry entry;
                        while ((entry = zis.getNextEntry()) != null) {
                            if (ShareConstants.DEX_IN_JAR.equals(entry.getName())) break;
                        }
                        if (entry == null) {
                            throw new TinkerRuntimeException("can't recognize zip dex format file:" + patchedDexFile.getAbsolutePath());
                        }
                        new DexPatchApplier(zis, patchFileStream).executeAndSaveTo(zos);
                    } finally {
                        StreamUtil.closeQuietly(zis);
                    }
                } else {
                    new DexPatchApplier(oldDexStream, patchFileStream).executeAndSaveTo(zos);
                }
                zos.closeEntry();
            } finally {
                StreamUtil.closeQuietly(zos);
            }
        } else {
            new DexPatchApplier(oldDexStream, patchFileStream).executeAndSaveTo(patchedDexFile);
        }
    } finally {
        StreamUtil.closeQuietly(oldDexStream);
        StreamUtil.closeQuietly(patchFileStream);
    }
}
```

在 patchDexFile 中，拿到基准包 dex 文件的 InputStream 和补丁包 dex 文件的 InputStream ，然后利用 DexPatchApplier 把这两个流合成一个 dex 文件。

``` java
public void executeAndSaveTo(OutputStream out) throws IOException {
    // Before executing, we should check if this patch can be applied to
    // old dex we passed in.
    byte[] oldDexSign = this.oldDex.computeSignature(false);
    if (oldDexSign == null) {
        throw new IOException("failed to compute old dex's signature.");
    }
    if (this.patchFile == null) {
        throw new IllegalArgumentException("patch file is null.");
    }
    byte[] oldDexSignInPatchFile = this.patchFile.getOldDexSignature();
    if (CompareUtils.uArrCompare(oldDexSign, oldDexSignInPatchFile) != 0) {
        throw new IOException(
                String.format(
                        "old dex signature mismatch! expected: %s, actual: %s",
                        Arrays.toString(oldDexSign),
                        Arrays.toString(oldDexSignInPatchFile)
                )
        );
    }

    // Firstly, set sections' offset after patched, sort according to their offset so that
    // the dex lib of aosp can calculate section size.
    TableOfContents patchedToc = this.patchedDex.getTableOfContents();

    patchedToc.header.off = 0;
    patchedToc.header.size = 1;
    patchedToc.mapList.size = 1;

    patchedToc.stringIds.off
            = this.patchFile.getPatchedStringIdSectionOffset();
    patchedToc.typeIds.off
            = this.patchFile.getPatchedTypeIdSectionOffset();
    patchedToc.typeLists.off
            = this.patchFile.getPatchedTypeListSectionOffset();
    patchedToc.protoIds.off
            = this.patchFile.getPatchedProtoIdSectionOffset();
    patchedToc.fieldIds.off
            = this.patchFile.getPatchedFieldIdSectionOffset();
    patchedToc.methodIds.off
            = this.patchFile.getPatchedMethodIdSectionOffset();
    patchedToc.classDefs.off
            = this.patchFile.getPatchedClassDefSectionOffset();
    patchedToc.mapList.off
            = this.patchFile.getPatchedMapListSectionOffset();
    patchedToc.stringDatas.off
            = this.patchFile.getPatchedStringDataSectionOffset();
    patchedToc.annotations.off
            = this.patchFile.getPatchedAnnotationSectionOffset();
    patchedToc.annotationSets.off
            = this.patchFile.getPatchedAnnotationSetSectionOffset();
    patchedToc.annotationSetRefLists.off
            = this.patchFile.getPatchedAnnotationSetRefListSectionOffset();
    patchedToc.annotationsDirectories.off
            = this.patchFile.getPatchedAnnotationsDirectorySectionOffset();
    patchedToc.encodedArrays.off
            = this.patchFile.getPatchedEncodedArraySectionOffset();
    patchedToc.debugInfos.off
            = this.patchFile.getPatchedDebugInfoSectionOffset();
    patchedToc.codes.off
            = this.patchFile.getPatchedCodeSectionOffset();
    patchedToc.classDatas.off
            = this.patchFile.getPatchedClassDataSectionOffset();
    patchedToc.fileSize
            = this.patchFile.getPatchedDexSize();

    Arrays.sort(patchedToc.sections);

    patchedToc.computeSizesFromOffsets();

    // Secondly, run patch algorithms according to sections' dependencies.
    this.stringDataSectionPatchAlg = new StringDataSectionPatchAlgorithm(
            patchFile, oldDex, patchedDex, oldToPatchedIndexMap
    );
    this.typeIdSectionPatchAlg = new TypeIdSectionPatchAlgorithm(
            patchFile, oldDex, patchedDex, oldToPatchedIndexMap
    );
    this.protoIdSectionPatchAlg = new ProtoIdSectionPatchAlgorithm(
            patchFile, oldDex, patchedDex, oldToPatchedIndexMap
    );
    this.fieldIdSectionPatchAlg = new FieldIdSectionPatchAlgorithm(
            patchFile, oldDex, patchedDex, oldToPatchedIndexMap
    );
    this.methodIdSectionPatchAlg = new MethodIdSectionPatchAlgorithm(
            patchFile, oldDex, patchedDex, oldToPatchedIndexMap
    );
    this.classDefSectionPatchAlg = new ClassDefSectionPatchAlgorithm(
            patchFile, oldDex, patchedDex, oldToPatchedIndexMap
    );
    this.typeListSectionPatchAlg = new TypeListSectionPatchAlgorithm(
            patchFile, oldDex, patchedDex, oldToPatchedIndexMap
    );
    this.annotationSetRefListSectionPatchAlg = new AnnotationSetRefListSectionPatchAlgorithm(
            patchFile, oldDex, patchedDex, oldToPatchedIndexMap
    );
    this.annotationSetSectionPatchAlg = new AnnotationSetSectionPatchAlgorithm(
            patchFile, oldDex, patchedDex, oldToPatchedIndexMap
    );
    this.classDataSectionPatchAlg = new ClassDataSectionPatchAlgorithm(
            patchFile, oldDex, patchedDex, oldToPatchedIndexMap
    );
    this.codeSectionPatchAlg = new CodeSectionPatchAlgorithm(
            patchFile, oldDex, patchedDex, oldToPatchedIndexMap
    );
    this.debugInfoSectionPatchAlg = new DebugInfoItemSectionPatchAlgorithm(
            patchFile, oldDex, patchedDex, oldToPatchedIndexMap
    );
    this.annotationSectionPatchAlg = new AnnotationSectionPatchAlgorithm(
            patchFile, oldDex, patchedDex, oldToPatchedIndexMap
    );
    this.encodedArraySectionPatchAlg = new StaticValueSectionPatchAlgorithm(
            patchFile, oldDex, patchedDex, oldToPatchedIndexMap
    );
    this.annotationsDirectorySectionPatchAlg = new AnnotationsDirectorySectionPatchAlgorithm(
            patchFile, oldDex, patchedDex, oldToPatchedIndexMap
    );

    this.stringDataSectionPatchAlg.execute();
    this.typeIdSectionPatchAlg.execute();
    this.typeListSectionPatchAlg.execute();
    this.protoIdSectionPatchAlg.execute();
    this.fieldIdSectionPatchAlg.execute();
    this.methodIdSectionPatchAlg.execute();
    this.annotationSectionPatchAlg.execute();
    this.annotationSetSectionPatchAlg.execute();
    this.annotationSetRefListSectionPatchAlg.execute();
    this.annotationsDirectorySectionPatchAlg.execute();
    this.debugInfoSectionPatchAlg.execute();
    this.codeSectionPatchAlg.execute();
    this.classDataSectionPatchAlg.execute();
    this.encodedArraySectionPatchAlg.execute();
    this.classDefSectionPatchAlg.execute();

    // Thirdly, write header, mapList. Calculate and write patched dex's sign and checksum.
    Dex.Section headerOut = this.patchedDex.openSection(patchedToc.header.off);
    patchedToc.writeHeader(headerOut);

    Dex.Section mapListOut = this.patchedDex.openSection(patchedToc.mapList.off);
    patchedToc.writeMap(mapListOut);

    this.patchedDex.writeHashes();

    // Finally, write patched dex to file.
    this.patchedDex.writeTo(out);
}
```

而 DexPatchApplier 里面合流操作的代码是需要根据 Tinker 的 DexDiff 算法来的。大致就是把两个 Dex 文件的每个分区做 merge 操作。

这里先留一个坑。等以后把 DexDiff 算法看明白了再补上。

另外，dodola 写了一篇 [Tinker Dexdiff算法解析](https://www.zybuluo.com/dodola/note/554061)，有需要的同学可以看下。

那么 dex 合成的流程就到这吧。


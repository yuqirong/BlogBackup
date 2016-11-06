title: 热修复框架HotFix源码解析
date: 2016-11-06 13:15:59
categories: Android Blog
tags: [Android,热修复,开源框架,源码解析]
---
0x00
============
讲起 Android 的热修复，相信大家对其都略知一二。热修复可以说是继插件化之后，又一项新的技术。目前的 Android 热修复框架主要分为了两类：

* 基于 Native Hook：使用 JNI 动态改变方法指针，比如有 [Dexposed](https://github.com/alibaba/dexposed) 、[AndFix](https://github.com/alibaba/AndFix) 等；
* 基于 Java Dex 分包：改变 dex 加载顺序，比如有 [HotFix](https://github.com/dodola/HotFix) 、[Nuwa](https://github.com/jasonross/Nuwa) 、[Amigo](https://github.com/eleme/Amigo) 等；

Native Hook 方案有一定的兼容性问题，并且其热修复是基于方法的；而 Java Dex 分包的方案具有很好的兼容性，被大众所接受。其实早在去年年末，[HotFix](https://github.com/dodola/HotFix) 、 [Nuwa](https://github.com/jasonross/Nuwa) 就已经出现了，并且它们的原理是相同的，都是基于 QQ 空间终端开发团队发布的[《安卓App热补丁动态修复技术介绍》][url]文中介绍的思路来实现的。如果没有看过这篇文章的童鞋，强烈建议先阅读一遍。

[url]: http://mp.weixin.qq.com/s?__biz=MzI1MTA1MzM2Nw==&mid=400118620&idx=1&sn=b4fdd5055731290eef12ad0d17f39d4a&scene=1&srcid=1031x2ljgSF4xJGlH1xMCJxO&uin=MjAyNzY1NTU%3D&key=04dce534b3b035ef58d8714d714d36bcc6cc7e136bbd64850522b491d143aafceb62c46421c5965e18876433791d16ec&devicetype=iMac+MacBookPro12%2C1+OSX+OSX+10.10.5+build(14F27)&version=11020201&lang=zh_CN&pass_ticket=7O%2FVfztuLjqu23ED2WEkvy1SJstQD4eLRqX%2B%2BbCY3uE%3D

虽然现在 [HotFix](https://github.com/dodola/HotFix) 框架已经被作者 [dodola](https://github.com/dodola) 标注了 Deprecated ，但是这并不妨碍我们解析其源码。那么下面我们就开始进入正题。

0x01
=====
首先来看一下 [HotFix](https://github.com/dodola/HotFix) 项目的结构：

![HotFix项目结构](/uploads/20161106/20161106151939.png)

可以看到项目中主要分为四个 module ：

* app : 里面有一个 HotFix 用法的 Demo ；
* buildSrc : 用于编译打包时代码注入的 Gradle 的 Task ；
* hackDex : 只有一个 AntilazyLoad 类，独立打成一个 hack.dex ，防止出现 CLASS_ISPREVERIFIED 相关的问题；
* hotfixlib : 热修复框架的 lib ；

我们就先从 app 入手吧，先来看看 HotfixApplication :

``` java
public class HotfixApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        File dexPath = new File(getDir("dex", Context.MODE_PRIVATE), "hackdex_dex.jar");
		// 把 assets 中的 hackdex_dex.jar 复制给 dexPath
        Utils.prepareDex(this.getApplicationContext(), dexPath, "hackdex_dex.jar");
        HotFix.patch(this, dexPath.getAbsolutePath(), "dodola.hackdex.AntilazyLoad");
        try {
            this.getClassLoader().loadClass("dodola.hackdex.AntilazyLoad");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

    }
}
```

在 `onCreate()` 方法中，代码量很少。一开始使用 `Utils.prepareDex` 把 assets 中的 hackdex_dex.jar 复制到内部存储中：

``` java
/**
 * 把 assets 中的 hack_dex 复制到内部存储中
 * @param context
 * @param dexInternalStoragePath
 * @param dex_file
 * @return
 */
public static boolean prepareDex(Context context, File dexInternalStoragePath, String dex_file) {
    BufferedInputStream bis = null;
    OutputStream dexWriter = null;

    try {
        bis = new BufferedInputStream(context.getAssets().open(dex_file));
        dexWriter = new BufferedOutputStream(new FileOutputStream(dexInternalStoragePath));
        byte[] buf = new byte[BUF_SIZE];
        int len;
        while ((len = bis.read(buf, 0, BUF_SIZE)) > 0) {
            dexWriter.write(buf, 0, len);
        }
        dexWriter.close();
        bis.close();
        return true;
    } catch (IOException e) {
        if (dexWriter != null) {
            try {
                dexWriter.close();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
        if (bis != null) {
            try {
                bis.close();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
        return false;
    }
}
```

复制完后调用了 `HotFix.patch` ：

``` java
public static void patch(Context context, String patchDexFile, String patchClassName) {
    if (patchDexFile != null && new File(patchDexFile).exists()) {
        try {
            if (hasLexClassLoader()) {
                injectInAliyunOs(context, patchDexFile, patchClassName);
            } else if (hasDexClassLoader()) {
                injectAboveEqualApiLevel14(context, patchDexFile, patchClassName);
            } else {
                injectBelowApiLevel14(context, patchDexFile, patchClassName);
            }
        } catch (Throwable th) {
        }
    }
}
```

在 `patch` 方法中，分为了三种情况：

1. 阿里云系统；
2. Android 系统 API Level >= 14 的；
3. Android 系统 API Level < 14 的；

其实阿里云的热修复和 Android系统 API < 14 的代码是差不多的，就是把 .dex 修改为了 .lex 。在这里就不分析，主要来看看 Android 系统 API >= 14 和 Android 系统 API < 14 两种情况。

Android 系统 API Level >= 14
---------------
先来分析 `injectAboveEqualApiLevel14` 方法：

``` java
private static void injectAboveEqualApiLevel14(Context context, String str, String str2)
    throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
    PathClassLoader pathClassLoader = (PathClassLoader) context.getClassLoader();
    // 合并 DexElements[] 数组
    Object a = combineArray(getDexElements(getPathList(pathClassLoader)),
        getDexElements(getPathList(
            new DexClassLoader(str, context.getDir("dex", 0).getAbsolutePath(), str, context.getClassLoader()))));
    // 得到当前 pathClassLoader 中的 pathList
   Object a2 = getPathList(pathClassLoader);
    // 把合并后的 DexElements[] 数组设置给 PathList 中的 dexElements
    setField(a2, a2.getClass(), "dexElements", a);
    pathClassLoader.loadClass(str2);
}
```

得到当前 `context` 内部的 `pathClassLoader` ，然后调用 `combineArray(getDexElements(getPathList(pathClassLoader)), getDexElements(getPathList(new DexClassLoader(str, context.getDir("dex", 0).getAbsolutePath(), str, context.getClassLoader()))))` 。这个 `combineArray` 方法中嵌套了很多层方法，我们一个一个来看。首先是 `getPathList` 方法：

``` java
private static Object getPathList(Object obj) throws ClassNotFoundException, NoSuchFieldException,
    IllegalAccessException {
	// 得到当前 PathClassLoader 中的 pathList 属性
    return getField(obj, Class.forName("dalvik.system.BaseDexClassLoader"), "pathList");
}

private static Object getField(Object obj, Class cls, String str)
    throws NoSuchFieldException, IllegalAccessException {
    Field declaredField = cls.getDeclaredField(str);
    declaredField.setAccessible(true);
    return declaredField.get(obj);
}
```

从上面的源码中知道，其实 getPathList 就是获取 BaseDexClassLoader 类的对象中的 pathList 属性。

![BaseDexClassLoader源码](/uploads/20161106/20161106163934.png)

PathClassLoader 类继承自 BaseDexClassLoader：

![PathClassLoader源码](/uploads/20161106/20161106163627.png)

得到了 pathList 之后，调用了 `getDexElements` 。顾名思义，就是获得了 pathList 中的 dexElements 属性。

``` java
private static Object getDexElements(Object obj) throws NoSuchFieldException, IllegalAccessException {
	// 得到当前 pathList 中的 dexElements 属性
    return getField(obj, obj.getClass(), "dexElements");
}
```

![DexPathList源码](/uploads/20161106/20161106164537.png)

所以在 `combineArray` 方法中传入的参数都是 Elements[] 。一个是当前应用程序中的 dexElements，另一个是 hackdex_dex.jar 中的 dexElements 。

下面来看看 `combineArray` 中的源码：

``` java
private static Object combineArray(Object obj, Object obj2) {
    // 得到 DexElements[] 数组的 class
    Class componentType = obj2.getClass().getComponentType();
    // 得到补丁包中 DexElements[] 数组的长度
    int length = Array.getLength(obj2);
    // 全长
    int length2 = Array.getLength(obj) + length;
    Object newInstance = Array.newInstance(componentType, length2);
    for (int i = 0; i < length2; i++) {
        if (i < length) {
			// obj2 中的 Element 顺序在 obj 前面
            Array.set(newInstance, i, Array.get(obj2, i));
        } else {
            Array.set(newInstance, i, Array.get(obj, i - length));
        }
    }
    return newInstance;
}
```

主要干的事情就是把传入的两个 dexElements 合并成一个 dexElements 。但是要注意的是第二个 obj2 中的 dex 要排在 obj 前面，这样才能达到热修复的效果。

最后我们回过头来看看 `injectAboveEqualApiLevel14` 方法中剩下的代码：

    // 得到当前 pathClassLoader 中的 pathList
    Object a2 = getPathList(pathClassLoader);
    // 把合并后的 DexElements[] 数组设置给 PathList
    setField(a2, a2.getClass(), "dexElements", a);
	// 先加载 dodola.hackdex.AntilazyLoad.class
    pathClassLoader.loadClass(str2);

这几行代码相信大家都能看懂了。这样 `injectAboveEqualApiLevel14` 整个流程就走完了。剩下，我们就看看 `injectBelowApiLevel14` 吧。

Android 系统 API Level < 14
-------
`injectBelowApiLevel14` 方法代码：

``` java
@TargetApi(14)
private static void injectBelowApiLevel14(Context context, String str, String str2)
    throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
    PathClassLoader obj = (PathClassLoader) context.getClassLoader();
    DexClassLoader dexClassLoader =
        new DexClassLoader(str, context.getDir("dex", 0).getAbsolutePath(), str, context.getClassLoader());
    // why load class str2
    dexClassLoader.loadClass(str2);
    setField(obj, PathClassLoader.class, "mPaths",
        appendArray(getField(obj, PathClassLoader.class, "mPaths"), getField(dexClassLoader, DexClassLoader.class,
                "mRawDexPath")
        ));
    setField(obj, PathClassLoader.class, "mFiles",
        combineArray(getField(obj, PathClassLoader.class, "mFiles"), getField(dexClassLoader, DexClassLoader.class,
                "mFiles")
        ));
    setField(obj, PathClassLoader.class, "mZips",
        combineArray(getField(obj, PathClassLoader.class, "mZips"), getField(dexClassLoader, DexClassLoader.class,
            "mZips")));
    setField(obj, PathClassLoader.class, "mDexs",
        combineArray(getField(obj, PathClassLoader.class, "mDexs"), getField(dexClassLoader, DexClassLoader.class,
            "mDexs")));
    obj.loadClass(str2);
}
```

我们发现在 API Level < 14 中，流程还是那一套流程，和 API Level >= 14 的一致，只不过要合并的属性变多了。主要因为 ClassLoader 源代码有变更，所以要分版本作出兼容。在这里就不分析了，相信看完 `injectAboveEqualApiLevel14` 之后对 `injectBelowApiLevel14` 也一定理解了。

0x02
=====
在 MainActivity 中，进行了热修复，相关代码：

``` java
//准备补丁,从assert里拷贝到dex里
File dexPath = new File(getDir("dex", Context.MODE_PRIVATE), "path_dex.jar");
Utils.prepareDex(this.getApplicationContext(), dexPath, "path_dex.jar");
//                DexInjector.inject(dexPath.getAbsolutePath(), defaultDexOptPath, "dodola.hotfix
// .BugClass");

HotFix.patch(this, dexPath.getAbsolutePath(), "dodola.hotfix.BugClass");
```

惊奇地发现 MainActivity 中热修复的代码和上面 HotfixApplication 中加载 hackdex_dex.jar 的代码是一模一样的。没错，都是用的同一套流程，所以同样的道理就很容易理解了。

0x03
===
[HotFix](https://github.com/dodola/HotFix) 整个逻辑就是上面这样了。但是我们还有一个问题要去解决，那就是我们怎样把 AntilazyLoad 动态引入到构造方法中。[HotFix](https://github.com/dodola/HotFix) 使用 javassist 来做到代码动态注入。具体的代码就是在 buildSrc 中：

``` java
/**
 * 植入代码
 * @param buildDir 是项目的build class目录,就是我们需要注入的class所在地
 * @param lib 这个是hackdex的目录,就是AntilazyLoad类的class文件所在地
 */
public static void process(String buildDir, String lib) {

    println(lib)
    ClassPool classes = ClassPool.getDefault()
    classes.appendClassPath(buildDir)
    classes.appendClassPath(lib)

    //下面的操作比较容易理解,在将需要关联的类的构造方法中插入引用代码
    CtClass c = classes.getCtClass("dodola.hotfix.BugClass")
    if (c.isFrozen()) {
        c.defrost()
    }
    println("====添加构造方法====")
    def constructor = c.getConstructors()[0];
    constructor.insertBefore("System.out.println(dodola.hackdex.AntilazyLoad.class);")
    c.writeFile(buildDir)



    CtClass c1 = classes.getCtClass("dodola.hotfix.LoadBugClass")
    if (c1.isFrozen()) {
        c1.defrost()
    }
    println("====添加构造方法====")
    def constructor1 = c1.getConstructors()[0];
    constructor1.insertBefore("System.out.println(dodola.hackdex.AntilazyLoad.class);")
    c1.writeFile(buildDir)

}
```

0x04
====
[HotFix](https://github.com/dodola/HotFix) 框架总体就是这样的了，还是比较简单的。现在作者重新写了一个 [RocooFix](https://github.com/dodola/RocooFix) 框架，主要解决了 Gradle 1.4 以上无法打包的问题。如果有兴趣的童鞋可以关注一下。

那么今天就到这里了，bye bye ！

0x05
=====
References

* [安卓App热补丁动态修复技术介绍][url]

[url]: http://mp.weixin.qq.com/s?__biz=MzI1MTA1MzM2Nw==&mid=400118620&idx=1&sn=b4fdd5055731290eef12ad0d17f39d4a&scene=1&srcid=1031x2ljgSF4xJGlH1xMCJxO&uin=MjAyNzY1NTU%3D&key=04dce534b3b035ef58d8714d714d36bcc6cc7e136bbd64850522b491d143aafceb62c46421c5965e18876433791d16ec&devicetype=iMac+MacBookPro12%2C1+OSX+OSX+10.10.5+build(14F27)&version=11020201&lang=zh_CN&pass_ticket=7O%2FVfztuLjqu23ED2WEkvy1SJstQD4eLRqX%2B%2BbCY3uE%3D

* [Android 热补丁动态修复框架小结](http://blog.csdn.net/lmj623565791/article/details/49883661)
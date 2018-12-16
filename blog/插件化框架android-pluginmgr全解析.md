title: 插件化框架android-pluginmgr全解析
date: 2016-10-02 10:03:34
categories: Android Blog
tags: [Android,插件化,开源框架,源码解析]
---
0x00 前言：插件化的介绍
====
阅读须知：阅读本文的童鞋最好是有过插件化框架使用经历或者对插件化框架有过了解的。前方高能，大牛绕道。

最近一直在关注 Android 插件化方面，所以今天的主题就确定是 Android 中比较热门的“插件化”了。所谓的插件化就是下载 apk 到指定目录，不需要安装该 apk ，就能利用某个已安装的 apk （即“宿主”）调用起该未安装 apk 中的 Activity 、Service 等组件（即“插件”）。

Android 插件化的发展到目前为止也有一段时间了，从一开始任主席的 [dynamic-load-apk](https://github.com/singwhatiwanna/dynamic-load-apk) 到今天要分析的 [android-pluginmgr](https://github.com/houkx/android-pluginmgr) 再到360的 [DroidPlugin](https://github.com/DroidPluginTeam/DroidPlugin) ，也代表着插件化的思想从顶部的应用层向下到 Framework 层渗入。最早插件化的思想是 [dynamic-load-apk](https://github.com/singwhatiwanna/dynamic-load-apk) 实现的， [dynamic-load-apk](https://github.com/singwhatiwanna/dynamic-load-apk) 在“宿主” ProxyActivity 的生命周期中利用接口回调了“插件” PluginActivity 的“生命周期”，以此来间接实现 PluginActivity 的“生命周期”。也就是说，其实插件中的 “PluginActivity” 并不具有真正 Activity 的性质，实质就是一个普通类，只是利用接口回调了类中的生命周期方法而已。比接口回调更好的方案就是利用 ActivityThread 、Instrumentation 等去动态地 Hook 即将创建的 ProxyActivity ，也就是说表面上创建的是 ProxyActivity ，其实实际上是创建了 PluginActivity 。这种思想相比于 [dynamic-load-apk](https://github.com/singwhatiwanna/dynamic-load-apk) 而言，插件中 Activity 已经是实质上的 Activity ，具备了生命周期方法。今天我们要解析的 android-pluginmgr 插件化框架就是基于这种思想的。最后就是像 [DroidPlugin](https://github.com/DroidPluginTeam/DroidPlugin) 这种插件化框架，改动了 ActivityManagerService 、 PackageManagerService 等 Android 源码，以此来实现插件化。总之，并没有哪种插件化框架是最好的，一切都是要根据自身实际情况而决定的。

熟悉插件化的童鞋都知道，插件化要解决的有三个基本难题：

1. 插件中 ClassLoader 的问题；
2. 插件中的资源文件访问问题；
3. 插件中 Activity 组件的生命周期问题。

基本上，解决了上面三个问题，就可以算是一个合格的插件化框架了。但是要注意的是，插件化远远不止这三个问题，比如还有插件中 .so 文件加载，支持 Service 插件化等问题。

好了，讲了这么多废话，接下来我们就来分析 [android-pluginmgr](https://github.com/houkx/android-pluginmgr) 的源码吧。

0x01 PluginManager.init
======
注：本文分析的 [android-pluginmgr](https://github.com/houkx/android-pluginmgr) 为 master 分支，版本为0.2.2；

android-pluginmgr的简单用法
---------------
我们先简单地来看一下 [android-pluginmgr](https://github.com/houkx/android-pluginmgr) 框架的用法（来自于 [android-pluginmgr](https://github.com/houkx/android-pluginmgr) 的 [README.md](https://github.com/houkx/android-pluginmgr/blob/master/README.md) ）：

1. declare permission in your `AndroidManifest.xml`: 

		<uses-permission android:name="android.permission.MOUNT_UNMOUNT_FILESYSTEMS" />
		<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />

2. regist an activity:

		<activity android:name="androidx.pluginmgr.DynamicActivity" />

3. init PluginMgr in your application:

		  @Override
		  public void onCreate(){
		     PluginManager.init(this);
		     //...
		  }

4. load plugin from plug apk:

		  PluginManager pluginMgr = PluginManager.getSingleton();
		  File myPlug = new File("/mnt/sdcard/Download/myplug.apk");
		  PlugInfo plug = pluginMgr.loadPlugin(myPlug).iterator().next();

5. start activity:

		  mgr.startMainActivity(context, plug);

基本的用法就像以上这五步，另外需要注意的是，“插件”中所需要的权限都要在“宿主”的 AndroidManifest.xml 中进行申明。

PluginManager.init(this)源码
---------------
下面我们来分析下 `PluginManager.init(this);` 的源码：

``` java
/**
 * 初始化插件管理器,请不要传入易变的Context,那将造成内存泄露!
 *
 * @param context Application上下文
 */
public static void init(Context context) {
    if (SINGLETON != null) {
        Trace.store("PluginManager have been initialized, YOU needn't initialize it again!");
        return;
    }
    Trace.store("init PluginManager...");
    SINGLETON = new PluginManager(context);
}
```

可以看到在 `init(Context context)` 中主要创建了一个 `SINGLETON` 单例，所以我们就要追踪 `PluginManager` 构造器的源码了：

```
/**
 * 插件管理器私有构造器
 *
 * @param context Application上下文
 */
private PluginManager(Context context) {
    if (!isMainThread()) {
        throw new IllegalThreadStateException("PluginManager must init in UI Thread!");
    }
    this.context = context;
    File optimizedDexPath = context.getDir(Globals.PRIVATE_PLUGIN_OUTPUT_DIR_NAME, Context.MODE_PRIVATE);
    dexOutputPath = optimizedDexPath.getAbsolutePath();
    dexInternalStoragePath = context.getDir(
            Globals.PRIVATE_PLUGIN_ODEX_OUTPUT_DIR_NAME, Context.MODE_PRIVATE
    );
    DelegateActivityThread delegateActivityThread = DelegateActivityThread.getSingleton();
    Instrumentation originInstrumentation = delegateActivityThread.getInstrumentation();
    if (!(originInstrumentation instanceof PluginInstrumentation)) {
        PluginInstrumentation pluginInstrumentation = new PluginInstrumentation(originInstrumentation);
        delegateActivityThread.setInstrumentation(pluginInstrumentation);
    }
}
```

在构造器中做的事情有点多，我们一步步来看下。一开始得到插件 dex opt 输出路径 `dexOutputPath` 和私有目录中存储插件的路径 `dexInternalStoragePath` 。这些路径都是在 `Global` 类中事先定义好的：

``` java
/**
 * 私有目录中保存插件文件的文件夹名
 */
public static final String PRIVATE_PLUGIN_OUTPUT_DIR_NAME = "plugins-file";

/**
 * 私有目录中保存插件odex的文件夹名
 */
public static final String PRIVATE_PLUGIN_ODEX_OUTPUT_DIR_NAME = "plugins-opt";
```

但是根据常量定义的名称来看，总感觉作者在 `context.getDir()` 时把这两个路径搞反了 \\(╯-╰)/。

之后在构造器中创建了 `DelegateActivityThread` 类的单例：

``` java
public final class DelegateActivityThread {

    private static DelegateActivityThread SINGLETON = new DelegateActivityThread();

    private Reflect activityThreadReflect;

    public DelegateActivityThread() {
        activityThreadReflect = Reflect.on(ActivityThread.currentActivityThread());
    }

    public static DelegateActivityThread getSingleton() {
        return SINGLETON;
    }

    public Application getInitialApplication() {
        return activityThreadReflect.get("mInitialApplication");
    }

    public Instrumentation getInstrumentation() {
        return activityThreadReflect.get("mInstrumentation");
    }

    public void setInstrumentation(Instrumentation newInstrumentation) {
        activityThreadReflect.set("mInstrumentation", newInstrumentation);
    }

}
```

DelegateActivityThread 类的主要作用就是使用反射包装了当前的 ActivityThread ，并且一开始在 DelegateActivityThread 中使用 PluginInstrumentation 替换原始的 Instrumentation 。其实 Activity 的生命周期调用都是通过 Instrumentation 来完成的。我们来看看 PluginInstrumentation 的构造器相关代码：

``` java
public class PluginInstrumentation extends DelegateInstrumentation
{

    /**
     * 当前正在运行的插件
     */
    private PlugInfo currentPlugin;

    /**
     * @param mBase 真正的Instrumentation
     */
    public PluginInstrumentation(Instrumentation mBase) {
        super(mBase);
    }

	...

}
```

可以看到 PluginInstrumentation 是继承自 DelegateInstrumentation 类的，而 DelegateInstrumentation 本质上就是 Instrumentation 。 DelegateInstrumentation 类中的方法都是直接调用 Instrumentation 类的:

``` java
public class DelegateInstrumentation extends Instrumentation {

    private Instrumentation mBase;

    /**
     * @param mBase 真正的Instrumentation
     */
    public DelegateInstrumentation(Instrumentation mBase) {
        this.mBase = mBase;
    }

    @Override
    public void onCreate(Bundle arguments) {
        mBase.onCreate(arguments);
    }

    @Override
    public void start() {
        mBase.start();
    }

    @Override
    public void onStart() {
        mBase.onStart();
    }

	...
}
```

好了，在 `PluginManager.init()` 方法中大概做的就是这些逻辑了。

0x02 PluginManager.loadPlugin
=====
看完了上面的 `PluginManager.init()` 之后，下一步就是调用 `pluginManager.loadPlugin` 去加载插件。一起来看看相关源码：

``` java
/**
 * 加载指定插件或指定目录下的所有插件
 * <p>
 * 都使用文件名作为Id
 *
 * @param pluginSrcDirFile - apk或apk目录
 * @return 插件集合
 * @throws Exception
 */
public Collection<PlugInfo> loadPlugin(final File pluginSrcDirFile)
        throws Exception {
    if (pluginSrcDirFile == null || !pluginSrcDirFile.exists()) {
        Trace.store("invalidate plugin file or Directory :"
                + pluginSrcDirFile);
        return null;
    }
    if (pluginSrcDirFile.isFile()) {
        PlugInfo one = buildPlugInfo(pluginSrcDirFile, null, null);
        if (one != null) {
            savePluginToMap(one);
        }
        return Collections.singletonList(one);
    }
//        synchronized (this) {
//            pluginPkgToInfoMap.clear();
//        }
    File[] pluginApkFiles = pluginSrcDirFile.listFiles(this);
    if (pluginApkFiles == null || pluginApkFiles.length == 0) {
        throw new FileNotFoundException("could not find plugins in:"
                + pluginSrcDirFile);
    }
    for (File pluginApk : pluginApkFiles) {
        try {
            PlugInfo plugInfo = buildPlugInfo(pluginApk, null, null);
            if (plugInfo != null) {
                savePluginToMap(plugInfo);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
    return pluginPkgToInfoMap.values();
}
```

在 `loadPlugin` 代码的注释中，我们可以知道加载的插件可以是一个也可以是一个文件夹下的多个。因为会根据传入的 `pluginSrcDirFile` 参数去判断是文件还是文件夹，其实道理都是一样的，无非就是多了一个 for 循环而已。在这里要注意一下，PluginManager 是实现了 FileFilter 接口的，因此在加载多个插件时，调用 `listFiles(this)` 会过滤当前文件夹下非 apk 文件：

``` java
@Override
public boolean accept(File pathname) {
    return !pathname.isDirectory() && pathname.getName().endsWith(".apk");
}
```

好了，我们在 `loadPlugin()` 的代码中会注意到，无论是加载单个插件还是多个插件都会调用 `buildPlugInfo()` 方法。顾名思义，就是根据传入的插件文件去加载：

``` java
private PlugInfo buildPlugInfo(File pluginApk, String pluginId,
                               String targetFileName) throws Exception {
    PlugInfo info = new PlugInfo();
    info.setId(pluginId == null ? pluginApk.getName() : pluginId);

    File privateFile = new File(dexInternalStoragePath,
            targetFileName == null ? pluginApk.getName() : targetFileName);

    info.setFilePath(privateFile.getAbsolutePath());
    //Copy Plugin to Private Dir
    if (!pluginApk.getAbsolutePath().equals(privateFile.getAbsolutePath())) {
        copyApkToPrivatePath(pluginApk, privateFile);
    }
    String dexPath = privateFile.getAbsolutePath();
    //Load Plugin Manifest
    PluginManifestUtil.setManifestInfo(context, dexPath, info);
    //Load Plugin Res
    try {
        AssetManager am = AssetManager.class.newInstance();
        am.getClass().getMethod("addAssetPath", String.class)
                .invoke(am, dexPath);
        info.setAssetManager(am);
        Resources hotRes = context.getResources();
        Resources res = new Resources(am, hotRes.getDisplayMetrics(),
                hotRes.getConfiguration());
        info.setResources(res);
    } catch (Exception e) {
        throw new RuntimeException("Unable to create Resources&Assets for "
                + info.getPackageName() + " : " + e.getMessage());
    }
    //Load  classLoader for Plugin
    PluginClassLoader pluginClassLoader = new PluginClassLoader(info, dexPath, dexOutputPath
            , getPluginLibPath(info).getAbsolutePath(), pluginParentClassLoader);
    info.setClassLoader(pluginClassLoader);
    ApplicationInfo appInfo = info.getPackageInfo().applicationInfo;
    Application app = makeApplication(info, appInfo);
    attachBaseContext(info, app);
    info.setApplication(app);
    Trace.store("Build pluginInfo => " + info);
    return info;
}
```

从上面的代码中看到， `buildPlugInfo()` 方法中做的大致有四步：

1. 复制插件 apk 到指定目录；
2. 加载插件 apk 的 AndroidManifest.xml 文件；
3. 加载插件 apk 中的资源文件；
4. 为插件 apk 设置 ClassLoader。

复制插件 apk 到指定目录
------
下面我们慢慢来分析，第一步，会把传入的插件 apk 复制到 `dexInternalStoragePath` 路径下，也就是之前在 PluginManager 的构造器中所指定的目录。这部分的代码很简单，就省略了。

加载插件 apk 的 AndroidManifest.xml 文件
----------
第二步，根据代码可知，会使用 `PluginManifestUtil.setManifestInfo()` 去加载 AndroidManifest 里的信息，那就去看下相关的代码实现：

``` java
public static void setManifestInfo(Context context, String apkPath, PlugInfo info)
		throws XmlPullParserException, IOException {
	// 得到AndroidManifest文件
	ZipFile zipFile = new ZipFile(new File(apkPath), ZipFile.OPEN_READ);
	ZipEntry manifestXmlEntry = zipFile.getEntry(XmlManifestReader.DEFAULT_XML);
	// 解析AndroidManifest文件
	String manifestXML = XmlManifestReader.getManifestXMLFromAPK(zipFile,
			manifestXmlEntry);
	// 创建相应的packageInfo
	PackageInfo pkgInfo = context.getPackageManager()
			.getPackageArchiveInfo(
					apkPath,
					PackageManager.GET_ACTIVITIES
							| PackageManager.GET_RECEIVERS//
							| PackageManager.GET_PROVIDERS//
							| PackageManager.GET_META_DATA//
							| PackageManager.GET_SHARED_LIBRARY_FILES//
							| PackageManager.GET_SERVICES//
			// | PackageManager.GET_SIGNATURES//
			);
    if (pkgInfo == null || pkgInfo.activities == null) {
        throw new XmlPullParserException("No any activity in " + apkPath);
    }
    pkgInfo.applicationInfo.publicSourceDir = apkPath;
    pkgInfo.applicationInfo.sourceDir = apkPath;
	// 得到libDir，加载.so文件
	File libDir = PluginManager.getSingleton().getPluginLibPath(info);
	try {
		if (extractLibFile(zipFile, libDir)) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
				pkgInfo.applicationInfo.nativeLibraryDir = libDir.getAbsolutePath();
			}
		}
	} finally {
		zipFile.close();
	}
    info.setPackageInfo(pkgInfo);
    setAttrs(info, manifestXML);
}
```

在代码中，一开始会通过 apk 得到 AndroidManifest.xml 文件。然后使用 `XmlManifestReader` 去读取 AndroidManifest 中的信息。在 `XmlManifestReader` 中会使用 `XmlPullParser` 去解析 xml ， `XmlManifestReader` 相关的源码就不贴出来了，想要进一步了解的童鞋可以自己去看，[点击这里查看 XmlManifestReader 源码](https://github.com/houkx/android-pluginmgr/blob/master/android-pluginmgr/src/main/java/androidx/pluginmgr/utils/XmlManifestReader.java)。接下来根据 `apkPath` 得到相应的 `pkgInfo` ，并且若有 libDir 会去加载相应的 .so 文件。最后会调用 `setAttrs(info, manifestXML)` 这个方法：

``` java
private static void setAttrs(PlugInfo info, String manifestXML)
		throws XmlPullParserException, IOException {
	XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
	factory.setNamespaceAware(true);
	XmlPullParser parser = factory.newPullParser();
	parser.setInput(new StringReader(manifestXML));
	int eventType = parser.getEventType();
	String namespaceAndroid = null;
	do {
		switch (eventType) {
		case XmlPullParser.START_DOCUMENT: {
			break;
		}
		case XmlPullParser.START_TAG: {
			String tag = parser.getName();
			if (tag.equals("manifest")) {
				namespaceAndroid = parser.getNamespace("android");
			} else if ("activity".equals(parser.getName())) {
				addActivity(info, namespaceAndroid, parser);
			} else if ("receiver".equals(parser.getName())) {
				addReceiver(info, namespaceAndroid, parser);
			} else if ("service".equals(parser.getName())) {
				addService(info, namespaceAndroid, parser);
			}else if("application".equals(parser.getName())){
				parseApplicationInfo(info, namespaceAndroid, parser);
			}
			break;
		}
		case XmlPullParser.END_TAG: {
			break;
		}
		}
		eventType = parser.next();
	} while (eventType != XmlPullParser.END_DOCUMENT);
}
```

在 `setAttrs(PlugInfo info, String manifestXML)` 方法中，使用了 pull 方式去解析 manifest ，并且根据 activity 、 recevicer 、 service 等调用不同的 `addXxxx()` 方法。这些方法其实本质上是一样的，我们就挑 `addActivity()` 方法来看一下:

``` java
private static void addActivity(PlugInfo info, String namespace,
		XmlPullParser parser) throws XmlPullParserException, IOException {
	int eventType = parser.getEventType();
	String activityName = parser.getAttributeValue(namespace, "name");
	String packageName = info.getPackageInfo().packageName;
	activityName = getName(activityName, packageName);
	ResolveInfo act = new ResolveInfo();
	act.activityInfo = info.findActivityByClassNameFromPkg(activityName);
	do {
		switch (eventType) {
		case XmlPullParser.START_TAG: {
			String tag = parser.getName();
			if ("intent-filter".equals(tag)) {
				if (act.filter == null) {
					act.filter = new IntentFilter();
				}
			} else if ("action".equals(tag)) {
				String actionName = parser.getAttributeValue(namespace,
						"name");
				act.filter.addAction(actionName);
			} else if ("category".equals(tag)) {
				String category = parser.getAttributeValue(namespace,
						"name");
				act.filter.addCategory(category);
			} else if ("data".equals(tag)) {
				// TODO parse data
			}
			break;
		}
		}
		eventType = parser.next();
	} while (!"activity".equals(parser.getName()));
	//
	info.addActivity(act);
}
```

 `addActivity()` 代码中的逻辑比较简单，就是创建一个 `ResolveInfo` 类的对象 `act` ，把 Activity 相关的信息全部装进去，比如有 ActivityInfo 、 intent-filter 等。最后把 `act` 添加到 `info` 中。其他的 `addReceiver` 和 `addService` 也是同一个逻辑。而 `parseApplicationInfo` 也是把 Application 的相关信息封装到 `info` 中。感兴趣的同学可以看一下相关的源码，[点击这里查看](https://github.com/houkx/android-pluginmgr/blob/master/android-pluginmgr/src/main/java/androidx/pluginmgr/utils/PluginManifestUtil.java)。到这里，就把加载插件中 AndroidManifest.xml 的代码分析完了。

加载插件 apk 中的资源文件
-----------
再回到 `buildPlugInfo()` 的代码中去，接下来就是第三步，加载插件中的资源文件了。

为了方便，我们把相关的代码复制到这里来：

``` java
try {
    AssetManager am = AssetManager.class.newInstance();
    am.getClass().getMethod("addAssetPath", String.class)
            .invoke(am, dexPath);
    info.setAssetManager(am);
    Resources hotRes = context.getResources();
    Resources res = new Resources(am, hotRes.getDisplayMetrics(),
            hotRes.getConfiguration());
    info.setResources(res);
} catch (Exception e) {
    throw new RuntimeException("Unable to create Resources&Assets for "
            + info.getPackageName() + " : " + e.getMessage());
}
```

首先通过反射得到 `AssetManager` 的对象 `am`，然后通过反射其 `addAssetPath` 方法传入 `dexPath` 参数来加载插件的资源文件，接下来就得到相应插件的 `Resource` 对象 `res` 了。这样就实现了访问插件中的资源文件了。那么到底  `addAssetPath` 这个方法有什么魔力呢？我们查看一下 Android 相关的源代码（[android/content/res/AssetManager.java](https://android.googlesource.com/platform/frameworks/base/+/56a2301/core/java/android/content/res/AssetManager.java)）：

``` java
/**
 * Add an additional set of assets to the asset manager.  This can be
 * either a directory or ZIP file.  Not for use by applications.  Returns
 * the cookie of the added asset, or 0 on failure.
 * {@hide}
 */
public final int addAssetPath(String path) {
    synchronized (this) {
        int res = addAssetPathNative(path);
        makeStringBlocks(mStringBlocks);
        return res;
    }
}
```

查看方法的注释我们知道，这个 `addAssetPath()` 方法就是用来添加额外的资源文件到 AssetManager 中去的，但是已经被 hide 了。所以我们只能通过反射的方式来执行了。这样就解决了加载插件中的资源文件的问题了。

其实，大多数插件化框架都是通过反射 `addAssetPath()` 的方式来解决加载插件资源问题，基本上已经成为了标准方案了。

为插件 apk 设置 ClassLoader
--------
终于到了最后一个步骤了，如何为插件设置 ClassLoader 呢？其实解决的方案就是通过 `DexClassLoader` 。我们先来看 `buildPlugInfo()` 中的代码：

``` java
PluginClassLoader pluginClassLoader = new PluginClassLoader(info, dexPath, dexOutputPath
        , getPluginLibPath(info).getAbsolutePath(), pluginParentClassLoader);
info.setClassLoader(pluginClassLoader);
ApplicationInfo appInfo = info.getPackageInfo().applicationInfo;
Application app = makeApplication(info, appInfo);
attachBaseContext(info, app);
info.setApplication(app);
Trace.store("Build pluginInfo => " + info);
```

在代码中创建了 `pluginClassLoader` 对象，而 `PluginClassLoader` 正是继承自 `DexClassLoader` 的，将 `dexPath` 、 `dexOutputPath` 等参数传入后，就可以去加载插件中的类了。 基本上所有的插件化框架都是通过 `DexClassLoder` 来作为插件 apk 的 ClassLoader 的。

之后在 `makeApplication(info, appInfo)` 就使用 `PluginClassLoader` 利用反射去创建插件的 Application 了：

``` java
/**
 * 构造插件的Application
 *
 * @param plugInfo 插件信息
 * @param appInfo 插件ApplicationInfo
 * @return 插件App
 */
private Application makeApplication(PlugInfo plugInfo, ApplicationInfo appInfo) {
    String appClassName = appInfo.className;
    if (appClassName == null) {
        //Default Application
        appClassName = Application.class.getName();
    }
        try {
            return (Application) plugInfo.getClassLoader().loadClass(appClassName).newInstance();
        } catch (Throwable e) {
            throw new RuntimeException("Unable to create Application for "
                    + plugInfo.getPackageName() + ": "
                    + e.getMessage());
        }
}
```

创建完插件的 Application 之后， 再调用 `attachBaseContext(info, app)` 方法把 Application 的 mBase 属性替换成 `PluginContext` 对象，`PluginContext` 类继承自 [LayoutInflaterProxyContext](https://github.com/houkx/android-pluginmgr/blob/master/android-pluginmgr/src/main/java/androidx/pluginmgr/delegate/LayoutInflaterProxyContext.java) ，里面封装了一些插件的信息，比如有插件资源、插件 ClassLoader 等。值得一提的是，在插件中 PluginContext 可以得到“宿主”的 Context ，也就是所谓的“破壳”。具体可查看[ PluginContext 的源码](https://github.com/houkx/android-pluginmgr/blob/master/android-pluginmgr/src/main/java/androidx/pluginmgr/environment/PluginContext.java)。

``` java
private void attachBaseContext(PlugInfo info, Application app) {
    try {
        Field mBase = ContextWrapper.class.getDeclaredField("mBase");
        mBase.setAccessible(true);
        mBase.set(app, new PluginContext(context.getApplicationContext(), info));
    } catch (Throwable e) {
        e.printStackTrace();
    }
}
```

讲到这里基本上把 `buildPlugInfo()` 中的逻辑讲完了， `pluginManager.loadPlugin` 剩下的代码都比较简单，相信大家一看就懂了。 

0x03 PluginManager.startActivity
======
startActivity
---------------
在加载好插件 apk 之后，就可以使用插件了。和平常无异，我们使用 `PluginManager.startActivity` 来启动插件中的 Activity 。其实 `PluginManager` 有很多 startActivity 的方法：

![startActivity截图](/uploads/20161002/201610041161253.jpg)

但是终于都会调用 `startActivity(Context from, PlugInfo plugInfo, ActivityInfo activityInfo, Intent intent)` 这个方法：

``` java
private DynamicActivitySelector activitySelector = DefaultActivitySelector.getDefault();

...

/**
 * 启动插件的指定Activity
 *
 * @param from         fromContext
 * @param plugInfo     插件信息
 * @param activityInfo 要启动的插件activity信息
 * @param intent       通过此Intent可以向插件传参, 可以为null
 */
public void startActivity(Context from, PlugInfo plugInfo, ActivityInfo activityInfo, Intent intent) {
    if (activityInfo == null) {
        throw new ActivityNotFoundException("Cannot find ActivityInfo from plugin, could you declare this Activity in plugin?");
    }
    if (intent == null) {
        intent = new Intent();
    }
    CreateActivityData createActivityData = new CreateActivityData(activityInfo.name, plugInfo.getPackageName());
    intent.setClass(from, activitySelector.selectDynamicActivity(activityInfo));
    intent.putExtra(Globals.FLAG_ACTIVITY_FROM_PLUGIN, createActivityData);
    from.startActivity(intent);
}
```

我们先来看代码， `CreateActivityData` 类是用来存储一个将要创建的插件 Activity 的数据，实现了 `Serializable` 接口，因此可以被序列化。总之， `CreateActivityData` 会存储将要创建的插件 Activity 的类名和包名，再把它放入 `intent` 中。之后， `intent` 设置要创建的 Activity 为 `activitySelector.selectDynamicActivity(activityInfo)` ，`activitySelector` 是 `DefaultActivitySelector` 类的对象，那么这 `DefaultActivitySelector` 到底是什么东西呢？一起来看看 `DefaultActivitySelector` 的源码：

``` java
public class DefaultActivitySelector implements DynamicActivitySelector {

    private static DynamicActivitySelector DEFAULT = new DefaultActivitySelector();

    @Override
    public Class<? extends Activity> selectDynamicActivity(ActivityInfo pluginActivityInfo) {
        return DynamicActivity.class;
    }

    public static DynamicActivitySelector getDefault() {
        return DEFAULT;
    }
}
```

其实很简单，不管传入的 `pluginActivityInfo` 参数是什么，返回的都是 `DynamicActivity.class` 。也就是我们在介绍[ android-pluginmgr 简单用法](/2016/10/02/%E6%8F%92%E4%BB%B6%E5%8C%96%E6%A1%86%E6%9E%B6android-pluginmgr%E5%85%A8%E8%A7%A3%E6%9E%90/#android-pluginmgr_u7684_u7B80_u5355_u7528_u6CD5)时，第二步在 AndroidManifest 中注册的那个 `DynamicActivity` 。
看到这里的代码，我们一定可以猜到什么。因为这里的 `intent` 中设置即将启动的 Activity 仍然为 `DynamicActivity` ，所以在后面的代码中肯定会去动态地替换掉 `DynamicActivity`。

动态Hook
---------
之前在 [PluginManager.init(this) 源码](/2016/10/02/%E6%8F%92%E4%BB%B6%E5%8C%96%E6%A1%86%E6%9E%B6android-pluginmgr%E5%85%A8%E8%A7%A3%E6%9E%90/#PluginManager-init_28this_29_u6E90_u7801)这一小节中介绍了，当前 `ActivityThread` 的 `Instrumentation` 已经被替换成了 `PluginInstrumentation`。所以在创建 Activity 的时候会去调用 `PluginInstrumentation` 里面的方法。这样就可以在里面“做手脚”，实现了动态去替换 Activity 的思路。我们先来看一下 `PluginInstrumentation` 中部分方法的源码：

``` java
private void replaceIntentTargetIfNeed(Context from, Intent intent)
{
    if (!intent.hasExtra(Globals.FLAG_ACTIVITY_FROM_PLUGIN) && currentPlugin != null)
	{
        ComponentName componentName = intent.getComponent();
        if (componentName != null)
		{
            String pkgName = componentName.getPackageName();
            String activityName = componentName.getClassName();
            if (pkgName != null)
			{
                CreateActivityData createActivityData = new CreateActivityData(activityName, currentPlugin.getPackageName());
                ActivityInfo activityInfo = currentPlugin.findActivityByClassName(activityName);
                if (activityInfo != null) {
                    intent.setClass(from, PluginManager.getSingleton().getActivitySelector().selectDynamicActivity(activityInfo));
                    intent.putExtra(Globals.FLAG_ACTIVITY_FROM_PLUGIN, createActivityData);
                    intent.setExtrasClassLoader(currentPlugin.getClassLoader());
                }
            }
        }
    }
}


@Override
public ActivityResult execStartActivity(Context who, IBinder contextThread, IBinder token, Fragment fragment, Intent intent, int requestCode)
{
    replaceIntentTargetIfNeed(who, intent);
    return super.execStartActivity(who, contextThread, token, fragment, intent, requestCode);
}


@Override
public ActivityResult execStartActivity(Context who, IBinder contextThread, IBinder token, Fragment fragment, Intent intent, int requestCode, Bundle options)
{
    replaceIntentTargetIfNeed(who, intent);
    return super.execStartActivity(who, contextThread, token, fragment, intent, requestCode, options);
}

@Override
public ActivityResult execStartActivity(Context who, IBinder contextThread, IBinder token, Activity target, Intent intent, int requestCode)
{
    replaceIntentTargetIfNeed(who, intent);
    return super.execStartActivity(who, contextThread, token, target, intent, requestCode);
}

@Override
public ActivityResult execStartActivity(Context who, IBinder contextThread, IBinder token, Activity target, Intent intent, int requestCode, Bundle options)
{
    replaceIntentTargetIfNeed(who, intent);
    return super.execStartActivity(who, contextThread, token, target, intent, requestCode, options);
}
```

我们发现，在所有的 `execStartActivity()` 方法执行前，都加上了 `replaceIntentTargetIfNeed(Context from, Intent intent)` 这个方法，在方法里面 `intent.setClass` 中设置的还是 `DynamicActivity.class` ，把插件信息都检查了一遍。

在这之后，会去执行 `PluginInstrumentation.newActivity` 方法来创建即将要启动的Activity 。也正是在这里，对之前的 `DynamicActivity` 进行 Hook ，达到启动插件 Activity 的目的。

``` java
@Override
public Activity newActivity(ClassLoader cl, String className, Intent intent) throws InstantiationException, IllegalAccessException, ClassNotFoundException
{
    CreateActivityData activityData = (CreateActivityData) intent.getSerializableExtra(Globals.FLAG_ACTIVITY_FROM_PLUGIN);
    //如果activityData存在,那么说明将要创建的是插件Activity
    if (activityData != null && PluginManager.getSingleton().getPlugins().size() > 0) {
        //这里找不到插件信息就会抛异常的,不用担心空指针
        PlugInfo plugInfo;
        try
		{
            Log.d(getClass().getSimpleName(), "+++ Start Plugin Activity => " + activityData.pluginPkg + " / " + activityData.activityName);
            // 得到插件信息类
            plugInfo = PluginManager.getSingleton().tryGetPluginInfo(activityData.pluginPkg);
            // 在该方法中会调用插件的Application.onCreate()
            plugInfo.ensureApplicationCreated();
        }
		catch (PluginNotFoundException e)
		{
            PluginManager.getSingleton().dump();
            throw new IllegalAccessException("Cannot get plugin Info : " + activityData.pluginPkg);
        }
        if (activityData.activityName != null)
		{
            // 在这里替换了className，变成了插件Activity的className
            className = activityData.activityName;
            // 替换classloader
            cl = plugInfo.getClassLoader();
        }
    }
    return super.newActivity(cl, className, intent);
}
```

在 `newActivity()` 方法中，先拿到了插件信息 `plugInfo` ，然后会确保插件的 `Application` 已经创建。然后在第25行会去替换掉 `className` 和 `cl` 。这样，原本要创建的是 `DynamicActivity` 就变成了插件的 `Activity` 了，从而实现了创建插件 Activity 的目的，并且这个 Activity 是真实的 Activity 组件，具备生命周期的。

也许有童鞋会有疑问，如果直接在 `startActivity` 中设置要启动的 Activity 为插件 Activity ，这样不行吗？答案是肯定的，因为这样就会抛出一个异常：`ActivityNotFoundException:...have you declared this activity in your AndroidManifest.xml?`我相信这个异常大家很熟悉的吧，在刚开始学习 Android 时，大家都会犯的一个错误。所以，我想我们也明白了为什么要花这么大的一个功夫去动态地替换要创建的 Activity ，就是为了绕过这个 `ActivityNotFoundException` 异常，达到去“欺骗” Android 系统的效果。

既然创建好了，那么就来看看 `PluginInstrumentation` 里调用相关生命周期的方法：

``` java
@Override
public void callActivityOnCreate(Activity activity, Bundle icicle) {
    lookupActivityInPlugin(activity);
    if (currentPlugin != null) {
        //初始化插件Activity
        Context baseContext = activity.getBaseContext();
        PluginContext pluginContext = new PluginContext(baseContext, currentPlugin);
        try {
            try {
                //在许多设备上，Activity自身hold资源
                Reflect.on(activity).set("mResources", pluginContext.getResources());

            } catch (Throwable ignored) {
            }

            Field field = ContextWrapper.class.getDeclaredField("mBase");
            field.setAccessible(true);
            field.set(activity, pluginContext);
            try {
                Reflect.on(activity).set("mApplication", currentPlugin.getApplication());
            } catch (ReflectException e) {
                Trace.store("Application not inject success into : " + activity);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }

        ActivityInfo activityInfo = currentPlugin.findActivityByClassName(activity.getClass().getName());
        if (activityInfo != null) {
            //根据AndroidManifest.xml中的参数设置Theme
            int resTheme = activityInfo.getThemeResource();
            if (resTheme != 0) {
                boolean hasNotSetTheme = true;
                try {
                    Field mTheme = ContextThemeWrapper.class
                            .getDeclaredField("mTheme");
                    mTheme.setAccessible(true);
                    hasNotSetTheme = mTheme.get(activity) == null;
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (hasNotSetTheme) {
                    changeActivityInfo(activityInfo, activity);
                    activity.setTheme(resTheme);
                }
            }

        }

        // 如果是三星手机，则使用包装的LayoutInflater替换原LayoutInflater
        // 这款手机在解析内置的布局文件时有各种错误
        if (android.os.Build.MODEL.startsWith("GT")) {
            Window window = activity.getWindow();
            Reflect windowRef = Reflect.on(window);
            try {
                LayoutInflater originInflater = window.getLayoutInflater();
                if (!(originInflater instanceof LayoutInflaterWrapper)) {
                    windowRef.set("mLayoutInflater", new LayoutInflaterWrapper(originInflater));
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }
    super.callActivityOnCreate(activity, icicle);
}

/**
 * 检查跳转目标是不是来自插件
 *
 * @param activity Activity
 */
private void lookupActivityInPlugin(Activity activity) {
    ClassLoader classLoader = activity.getClass().getClassLoader();
    if (classLoader instanceof PluginClassLoader) {
        currentPlugin = ((PluginClassLoader) classLoader).getPlugInfo();
    } else {
        currentPlugin = null;
    }
}
```

在 `callActivityOnCreate()` 中先去检查了创建的 Activity 是否来自于插件。如果是，那么会给 Activity 设置 Context 、 设置主题等；如果不是，则直接执行父类方法。在 `super.callActivityOnCreate(activity, icicle)` 中会去调用 `Activity.onCreate() `方法。其他的生命周期方法作者没有特殊处理，这里就不讲了。

分析到这，我们终于把 [android-pluginmgr](https://github.com/houkx/android-pluginmgr) 插件化实现的方案完整地梳理了一遍。当然，不同的插件化框架会有不同的实现方案，具体的仍然需要自己专心研究。另外我们发现该框架还没有实现启动插件 Service 的功能，如果想要了解，可以参考下其他插件化框架。

0x04 总结
=========
上面乱七八糟的流程讲了一遍，可能还有一些童鞋不太懂，所以在这里给出一张 [android-pluginmgr](https://github.com/houkx/android-pluginmgr) 的流程图。不懂的童鞋可以根据这张图再好好看一下源码，相信你会恍然大悟的。

![android-pluginmgr流程图](/uploads/20161002/20161005183404.png)

最后，如果对本文哪里有疑问的童鞋，欢迎留言，一起交流。

0x05 References
=====
* [包建强：为什么我说Android插件化从入门到放弃？](https://mp.weixin.qq.com/s?__biz=MjM5MDE0Mjc4MA==&mid=2650993300&idx=1&sn=797fa87ef528cff3a50e77806cf9f675&scene=1&srcid=07125lNtkiWhjbu4dp8GhoAf#rd)
title: Dynamic-Load-Apk源码解析
date: 2016-10-29 21:57:12
categories: Android Blog
tags: [Android,插件化,开源框架,源码解析]
---
0x00
======
趁着今天是周末，无所事事，来讲讲 [Dynamic-Load-Apk](https://github.com/singwhatiwanna/dynamic-load-apk) 框架。[Dynamic-Load-Apk](https://github.com/singwhatiwanna/dynamic-load-apk) 是任主席主导开发的一款插件化框架，其中心思想主要就是两个字——**代理**。和我之前分析的 [android-pluginmgr](https://github.com/houkx/android-pluginmgr) 插件化框架不同的是，[Dynamic-Load-Apk](https://github.com/singwhatiwanna/dynamic-load-apk) 框架完全基于在应用层上实现，并不依靠 ActivityThread 、Instrumentation 等。另外，[Dynamic-Load-Apk](https://github.com/singwhatiwanna/dynamic-load-apk) 框架在插件化发展历程中诞生较早，对后来不断涌现的插件化框架具有深刻的指导意义。

0x01
======
注：本文分析的 [Dynamic-Load-Apk](https://github.com/singwhatiwanna/dynamic-load-apk) 为 master 分支，版本为1.0.0；

其实 [Dynamic-Load-Apk](https://github.com/singwhatiwanna/dynamic-load-apk) 框架的思想很巧妙，大致的思路如下：在宿主中首先申明了一些 ProxyActivity 以及 ProxyService ，插件中的 PluginActivity 要继承指定的 DLBasePluginActivity 。然后启动插件中的 Activity 时，实际上启动的是 ProxyActivity , 之后利用接口回调调用了 PluginActivity 中的生命周期方法。也就是说，PluginActivity 并不是实质上的 Activity ，其实只是一个普通的 Java 类。

在分析源码之前，先在这里简单地说一下 [Dynamic-Load-Apk](https://github.com/singwhatiwanna/dynamic-load-apk) 框架的结构：

![dynamic-load-apk的类结构](/uploads/20161029/20161029223619.png)

从上面的图中大致可以看出来，整个框架中的 Java 类基本可以分为五种类型：

* **DLPluginManager** ：顾名思义，是整个插件的管理类。主要作用就是加载以及管理插件，启动插件中的Activity、Service等；
* **DLProxyActivity 、DLProxyFragmentActivity 、DLProxyService** ：代理组件。可以看到有 Activity 、Service 等，在启动插件时实质上启动的是这些代理组件，之后在代理组件中利用接口回调插件的相关“生命周期”；
* **DLProxyImpl 、DLServiceProxyImpl** ：属于启动插件过程中一些公共逻辑代码。在代理组件连接插件组件时，把一些公共的方法抽取出来放入了这些类中；
* **DLBasePluginActivity 、DLBasePluginFragmentActivity 、DLBasePluginService** ：插件的基类。用户使用的插件需要继承自这些基类，之后接口才会回调插件的“生命周期”。
* **DLPlugin 、DLServicePlugin** ：插件“生命周期”定义的接口。在这两个类中定义了 Activity 、Service 相关的生命周期方法。

那么接下来我们就一一来解析源码吧。

DLPluginManager.loadApk
------------------
DLPluginManager 是个单例类，我们先来看看它的初始化方法 `DLPluginManager.getInstance(Context context)` ：

``` java
private DLPluginManager(Context context) {
    mContext = context.getApplicationContext();
    mNativeLibDir = mContext.getDir("pluginlib", Context.MODE_PRIVATE).getAbsolutePath();
}

public static DLPluginManager getInstance(Context context) {
    if (sInstance == null) {
        synchronized (DLPluginManager.class) {
            if (sInstance == null) {
                sInstance = new DLPluginManager(context);
            }
        }
    }

    return sInstance;
}
```

可以看到在构造函数中设置了 .so 文件存储的目录。初始化完成后，通过 `loadApk(final String dexPath, boolean hasSoLib)` 方法来加载插件：

``` java
public DLPluginPackage loadApk(final String dexPath, boolean hasSoLib) {
    mFrom = DLConstants.FROM_EXTERNAL;

    PackageInfo packageInfo = mContext.getPackageManager().getPackageArchiveInfo(dexPath,
            PackageManager.GET_ACTIVITIES | PackageManager.GET_SERVICES);
    if (packageInfo == null) {
        return null;
    }
	// 得到插件信息封装类
    DLPluginPackage pluginPackage = preparePluginEnv(packageInfo, dexPath);
	// 如果有 .so 文件，则复制到 mNativeLibDir 目录
    if (hasSoLib) {
        copySoLib(dexPath);
    }

    return pluginPackage;
}
```

在 `loadApk(final String dexPath, boolean hasSoLib)` 主要做了两件事：

1. 在 `preparePluginEnv(PackageInfo packageInfo, String dexPath)` 方法中把插件 packageInfo 封装成 pluginPackage ；
2. 复制 .so 文件到 mNativeLibDir 目录，主要流程就是在 SoLibManager 中利用 I/O 流复制文件。在这里就不讲了，代码比较简单，有兴趣的童鞋可以自己回去看源码；

那么我们就跟着主流程来看看 `preparePluginEnv(PackageInfo packageInfo, String dexPath)` 方法：

``` java
private DLPluginPackage preparePluginEnv(PackageInfo packageInfo, String dexPath) {
    // 先查看缓存中有没有该 pluginPackage
    DLPluginPackage pluginPackage = mPackagesHolder.get(packageInfo.packageName);
    if (pluginPackage != null) {
        return pluginPackage;
    }
    // 创建 ClassLoader
    DexClassLoader dexClassLoader = createDexClassLoader(dexPath); 
    AssetManager assetManager = createAssetManager(dexPath);
    // 得到插件 res 资源
    Resources resources = createResources(assetManager);
    // create pluginPackage
    pluginPackage = new DLPluginPackage(dexClassLoader, resources, packageInfo);
    mPackagesHolder.put(packageInfo.packageName, pluginPackage);
    return pluginPackage;
}
```

根据代码可知，在方法中创建了插件的 ClassLoader ，插件的 res 资源。如果你看过我另一篇插件化框架分析的文章[《插件化框架android-pluginmgr全解析》](/2016/10/02/插件化框架android-pluginmgr全解析)，那么想必对这其中的原理已经熟知了：

1. 插件的类加载器是 DexClassLoader 或其子类，可以指定加载 dex 的目录。对应着上面的 `createDexClassLoader(dexPath)` 方法；
2. 插件的 res 资源访问主要通过 AssetManager 的 `addAssetPath` 方法来获取。需要注意的是，`addAssetPath` 方法是 @hide 的，需要反射来执行。对应着 `createAssetManager(dexPath)` 方法；

createXxxx 方法具体的代码就不在这里贴出来了，想了解的可以查看源码。通过这些 createXxxx 方法，就把插件的 ClassLoader 和 res 资源问题解决了。最后封装成一个 pluginPackage 对象，方便之后使用。

DLPluginManager.startPluginActivityForResult
------------------
加载完插件之后，我们就要着手于如何启动插件了。想要启动插件，就要调用 `startPluginActivity(Context context, DLIntent dlIntent)` 方法，而 `startPluginActivity(Context context, DLIntent dlIntent)` 方法内部又是调用 `startPluginActivityForResult(Context context, DLIntent dlIntent, int requestCode)` 方法的，所以我们直接查看 `startPluginActivityForResult(Context context, DLIntent dlIntent, int requestCode)` 的源码：

``` java
public int startPluginActivityForResult(Context context, DLIntent dlIntent, int requestCode) {
    // 是否宿主内部调用
    if (mFrom == DLConstants.FROM_INTERNAL) {
        dlIntent.setClassName(context, dlIntent.getPluginClass());
        performStartActivityForResult(context, dlIntent, requestCode);
        return DLPluginManager.START_RESULT_SUCCESS;
    }

    String packageName = dlIntent.getPluginPackage();
    if (TextUtils.isEmpty(packageName)) {
        throw new NullPointerException("disallow null packageName.");
    }
    // 得到插件信息
    DLPluginPackage pluginPackage = mPackagesHolder.get(packageName);
    if (pluginPackage == null) {
        return START_RESULT_NO_PKG;
    }
    // 得到插件 Activity 的全类名
    final String className = getPluginActivityFullPath(dlIntent, pluginPackage);
    // 得到对应的 class
    Class<?> clazz = loadPluginClass(pluginPackage.classLoader, className);
    if (clazz == null) {
        return START_RESULT_NO_CLASS;
    }

    // 根据插件 class 继承的是哪个基类，分别得到对应的代理类
    // 若继承的是 DLBasePluginActivity ，得到的就是 DLProxyActivity 代理类
    // 若继承的是 DLBasePluginFragmentActivity ，得到的就是 DLProxyFragmentActivity 代理类
    Class<? extends Activity> activityClass = getProxyActivityClass(clazz);
    if (activityClass == null) {
        return START_RESULT_TYPE_ERROR;
    }

    // 把插件信息传入 Intent 中
    dlIntent.putExtra(DLConstants.EXTRA_CLASS, className);
    dlIntent.putExtra(DLConstants.EXTRA_PACKAGE, packageName);
    // 这里启动的是上面得到的代理类 Activity
    dlIntent.setClass(mContext, activityClass);
    // 启动 Activity
    performStartActivityForResult(context, dlIntent, requestCode);
    return START_RESULT_SUCCESS;
}
```

`startPluginActivityForResult(Context context, DLIntent dlIntent, int requestCode)` 方法里基本上都有注释，要明白的是，intent 启动的是代理的 Activity ，并不是我们插件的 Activity 。另外，在 DLPluginManager 里还有启动插件 Service 的相关代码，不过具体的流程和启动插件 Activity 是相似的。如果有想要进一步了解的童鞋可以自行看源码。

DLProxyActivity
---------------------
经过上一步之后，我们就启动了代理类 Activity 。代理类 Activity 有两种：DLProxyActivity 和 DLProxyFragmentActivity 。但是其中的逻辑都是一样的。在这里我们只分析 DLProxyActivity 了。

``` java
public class DLProxyActivity extends Activity implements DLAttachable {

    protected DLPlugin mRemoteActivity;
    private DLProxyImpl impl = new DLProxyImpl(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        impl.onCreate(getIntent());
    }

	...

}
```

从类的结构上看到，DLProxyActivity 实现了 DLAttachable 接口。那么 DLAttachable 接口的作用是什么呢：

``` java
public interface DLAttachable {
    /**
     * when the proxy impl ( {@see DLProxyImpl#launchTargetActivity()} ) launch
     * the plugin activity , dl will call this method to attach the proxy activity
     * and pluginManager to the plugin activity. the proxy activity will load
     * the plugin's resource, so the proxy activity is a resource delegate for
     * plugin activity.
     * 
     * @param proxyActivity a instance of DLPlugin, {@see DLBasePluginActivity}
     *            and {@see DLBasePluginFragmentActivity}
     * @param pluginManager DLPluginManager instance, manager the plugins
     */
    public void attach(DLPlugin proxyActivity, DLPluginManager pluginManager);
}
```

根据注释的意思是，`attach(DLPlugin proxyActivity, DLPluginManager pluginManager)` 方法可以在 ProxyImpl 调用 `launchTargetActivity()` 时把 PluginActivity 和 ProxyActivity 绑定在一起。那样就达到了可以在 ProxyActivity 中使用 PluginActivity 的效果。那么到底在什么时候调用 `proxyImpl.launchTargetActivity()` 方法呢？我们回到上面的 DLProxyActivity 类中来，看到了 DLProxyActivity 中有一个 `impl` 成员变量。在 `onCreate(Bundle savedInstanceState)` 中调用了 `impl.onCreate(getIntent())` ，我们猜想在 `impl.onCreate(getIntent())` 的方法里一定会去调用 `launchTargetActivity()` 方法。下面我们就来看看源码：

``` java
public void onCreate(Intent intent) {

    // set the extra's class loader
    intent.setExtrasClassLoader(DLConfigs.sPluginClassloader);
    // 得到传过来的插件 Activity 包名和全类名
    mPackageName = intent.getStringExtra(DLConstants.EXTRA_PACKAGE);
    mClass = intent.getStringExtra(DLConstants.EXTRA_CLASS);
    Log.d(TAG, "mClass=" + mClass + " mPackageName=" + mPackageName);
    // 得到插件相关的信息
    mPluginManager = DLPluginManager.getInstance(mProxyActivity);
    mPluginPackage = mPluginManager.getPackage(mPackageName);
    mAssetManager = mPluginPackage.assetManager;
    mResources = mPluginPackage.resources;
    // 得到要启动插件的 activityInfo，设置插件 Activity 的主题
    initializeActivityInfo();
    // 把 DLProxyActivity 的主题设置为插件 Activity 的主题
    handleActivityInfo();
    launchTargetActivity();
}
```

在 `onCreate(Intent intent)` 中得到了之前插件 Activity 相关的信息。然后把 DLProxyActivity 的主题设置为 PluginActivity 的主题。最后调用了  `launchTargetActivity()` ，说明我们的猜想是正确的。来看看在 `launchTargetActivity` 方法中到底干了什么：

``` java
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
protected void launchTargetActivity() {
    try {
		// 得到插件 Activity 的 class
        Class<?> localClass = getClassLoader().loadClass(mClass);
        Constructor<?> localConstructor = localClass.getConstructor(new Class[] {});
		// 创建插件 Activity 的对象
        Object instance = localConstructor.newInstance(new Object[] {});
        mPluginActivity = (DLPlugin) instance;
		// 调用 attach 方法，把插件和代理绑定起来
        ((DLAttachable) mProxyActivity).attach(mPluginActivity, mPluginManager);
        Log.d(TAG, "instance = " + instance);
        // attach the proxy activity and plugin package to the mPluginActivity
		// 手动调用插件的 attach 方法
        mPluginActivity.attach(mProxyActivity, mPluginPackage);

        Bundle bundle = new Bundle();
        bundle.putInt(DLConstants.FROM, DLConstants.FROM_EXTERNAL);
		// 手动调用插件的 onCreate 方法
        mPluginActivity.onCreate(bundle);
    } catch (Exception e) {
        e.printStackTrace();
    }
}
```

我们发现在方法中使用反射创建了插件 Activity 的对象，又因为插件 Activity 必须继承指定的基类，这些基类是实现了 DLPlugin 接口的。所以插件 Activity 可以强转为 DLPlugin 。DLPlugin 接口定义了一系列的 Activity 生命周期方法，之后手动回调了 `attach` 和 `onCreate` 方法。

现在我们再回过头来看看 DLProxyActivity 里的其他生命周期方法，发现都有一句 ` mRemoteActivity.onXxxxx()` 。其中的 mRemoteActivity 就是通过 DLAttachable 接口绑定的插件 Activity 对象。所以每当代理 ProxyActivity 回调生命周期方法时，都调用了 DLPlugin 接口一致的生命周期方法，这样就实现了插件 Activity 也有“生命周期”方法。

DLBasePluginActivity
--------------------
讲解了 DLProxyActivity 之后，再来看看 DLBasePluginActivity 就发现轻松多了。

``` java
public class DLBasePluginActivity extends Activity implements DLPlugin {

	....

	@Override
    public void setContentView(int layoutResID) {
        if (mFrom == DLConstants.FROM_INTERNAL) {
            super.setContentView(layoutResID);
        } else {
            mProxyActivity.setContentView(layoutResID);
        }
    }

	@Override
    public void onResume() {
        if (mFrom == DLConstants.FROM_INTERNAL) {
            super.onResume();
        }
    }

    @Override
    public void onPause() {
        if (mFrom == DLConstants.FROM_INTERNAL) {
            super.onPause();
        }
    }	

}
```

DLBasePluginActivity 实现了 DLPlugin 接口，就有了 `onCreate()` 、`onResume()` 这些“生命周期”方法。另外在重写的方法中会判断当前否被代理，以此来确定直接走父类逻辑还是代理 Activity 或是空逻辑。

0x02
=============
讲到这里，整个启动插件 Activity 的流程就走完了。除此之外，还有启动插件 Service 其实也是相似的流程。现在的 [Dynamic-Load-Apk](https://github.com/singwhatiwanna/dynamic-load-apk) 框架如果实际使用起来可能有比较多的问题，作者也基本上很早就停止更新了。但是这并不妨碍我们分析源码，学习其中的精髓。我想大部分人看完源码都会体会到 [Dynamic-Load-Apk](https://github.com/singwhatiwanna/dynamic-load-apk) 的核心思想——代理，这也正是和其他插件化框架不同的地方。在这里感谢那些为 [Dynamic-Load-Apk](https://github.com/singwhatiwanna/dynamic-load-apk) 作出贡献的人。

如果有问题可以留言，Goodbye !

0x03
=====
Reference：

* [DynamicLoadApk 源码解析](http://a.codekk.com/detail/Android/FFish/DynamicLoadApk%20%E6%BA%90%E7%A0%81%E8%A7%A3%E6%9E%90)

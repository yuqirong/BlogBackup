title: VirtualAPK源码解析
date: 2017-11-12 22:03:58
categories: Android Blog
tags: [Android,开源框架,源码解析,插件化]
---
Header
======
VirtualAPK 是滴滴开源的一款 Android 插件化的框架。

现在市面上，成熟的插件化框架已经挺多了，那为什么还要重新开发一款轮子呢？

1. 大部分开源框架所支持的功能还不够全面
2. 兼容性问题严重，大部分开源方案不够健壮
3. 已有的开源方案不适合滴滴的业务场景

在加载耦合插件方面，VirtualAPK是开源方案的首选。

以上是滴滴给出的官方解释。

对于我们开发者来说，这种当然是好事。第一，我们选择插件化框架的余地变多了；第二，我们也可以多学习学习框架内部实现的原理，一举两得。

那就不说废话了，一起来看。

使用方法
=======
使用方法直接抄 GitHub 上的，就将就着看吧。

* 第一步： 初始化插件引擎

	``` java
	@Override
	protected void attachBaseContext(Context base) {
	    super.attachBaseContext(base);
	    PluginManager.getInstance(base).init();
	}
	```

* 第二步：加载插件

	``` java
	public class PluginManager {
	    public void loadPlugin(File apk);
	}
	```

	当插件入口被调用后，插件的后续逻辑均不需要宿主干预，均走原生的Android流程。 比如，在插件内部，如下代码将正确执行：

	``` java
	@Override
	protected void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    setContentView(R.layout.activity_book_manager);
	    LinearLayout holder = (LinearLayout)findViewById(R.id.holder);
	    TextView imei = (TextView)findViewById(R.id.imei);
	    imei.setText(IDUtil.getUUID(this));
	     
	    // bind service in plugin
	    Intent service = new Intent(this, BookManagerService.class);
	    bindService(service, mConnection, Context.BIND_AUTO_CREATE);
	    
	    // start activity in plugin
	    Intent intent = new Intent(this, TCPClientActivity.class);
	    startActivity(intent);
	}
	```

源码解析
=======
使用方法很简单，下面就顺着上面的代码一步步去探究实现原理。

初始化插件框架
------------
第一步就是初始化框架：`PluginManager.getInstance(base).init();`

### PluginManager.getInstance(Context base)

``` java
    public static PluginManager getInstance(Context base) {
        if (sInstance == null) {
            synchronized (PluginManager.class) {
                if (sInstance == null)
                    sInstance = new PluginManager(base);
            }
        }

        return sInstance;
    }

    private PluginManager(Context context) {
        Context app = context.getApplicationContext();
        if (app == null) {
            this.mContext = context;
        } else {
            this.mContext = ((Application)app).getBaseContext();
        }
        prepare();
    }
```

PluginManager 设计为了单例模式，负责管理插件的一些操作。

在初始化的时候，得到全局 Context ，以防出现内存泄漏的情况。

之后调用了 `prepare()` 方法，做一些预备操作。

### prepare()

``` java
    private void prepare() {
		// host base context
        Systems.sHostContext = getHostContext();
		// hook instrumentation
        this.hookInstrumentationAndHandler();
        this.hookSystemServices();
    }
```

在 `prepare()` 中，将宿主 Context 赋值给了 Systems.sHostContext 。这样，在之后的代码中可以方便地访问到宿主 Context 。

然后分为了两步操作，hookInstrumentationAndHandler 和 hookSystemServices 。为运行插件做一些必要的 hook 操作。

### hookInstrumentationAndHandler()

``` java
    private void hookInstrumentationAndHandler() {
        try {
            Instrumentation baseInstrumentation = ReflectUtil.getInstrumentation(this.mContext);
            if (baseInstrumentation.getClass().getName().contains("lbe")) {
                // reject executing in paralell space, for example, lbe.
                System.exit(0);
            }
            // 创建插件的 instrumentation
            final VAInstrumentation instrumentation = new VAInstrumentation(this, baseInstrumentation);
            Object activityThread = ReflectUtil.getActivityThread(this.mContext);
            // 将插件的 instrumentation 设置到当前的 activityThread 中
            ReflectUtil.setInstrumentation(activityThread, instrumentation);
            // 给 mainThreadHandler 设置 callback
            ReflectUtil.setHandlerCallback(this.mContext, instrumentation);
            this.mInstrumentation = instrumentation;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
```

hookSystemServices()
--------------------

    private void hookSystemServices() {
        try {
            // 得到当前系统中的 ActivityManager 单例
            Singleton<IActivityManager> defaultSingleton = (Singleton<IActivityManager>) ReflectUtil.getField(ActivityManagerNative.class, null, "gDefault");
            // 创建 ActivityManager 的动态代理 
            IActivityManager activityManagerProxy = ActivityManagerProxy.newInstance(this, defaultSingleton.get());

            // Hook IActivityManager from ActivityManagerNative  利用反射将系统的 ActivityManager 单例替换为 activityManagerProxy 
            ReflectUtil.setField(defaultSingleton.getClass().getSuperclass(), defaultSingleton, "mInstance", activityManagerProxy);

            if (defaultSingleton.get() == activityManagerProxy) {
                this.mActivityManager = activityManagerProxy;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

init()
-------

    public void init() {
        // ComponentsHandler 感觉像是插件中四大组件的管理者
        mComponentsHandler = new ComponentsHandler(this);
        // 用的是 asynctask 的线程池
        RunUtil.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                // 空方法，给之后的东西，万一有什么需要初始化的
                doInWorkThread();
            }
        });
    }

    private void doInWorkThread() {
    }


加载插件
=======
load plugin apk ：

	PluginManager.getInstance(base).loadPlugin(apk);

loadPlugin(File apk)
--------------------

    public void loadPlugin(File apk) throws Exception {
        if (null == apk) {
            throw new IllegalArgumentException("error : apk is null.");
        }

        if (!apk.exists()) {
            throw new FileNotFoundException(apk.getAbsolutePath());
        }

        LoadedPlugin plugin = LoadedPlugin.create(this, this.mContext, apk);
        if (null != plugin) {
            this.mPlugins.put(plugin.getPackageName(), plugin);
            // 创建插件中的 application
            plugin.invokeApplication();
        } else {
            throw  new RuntimeException("Can't load plugin which is invalid: " + apk.getAbsolutePath());
        }
    }

LoadedPlugin
------------

### PackageParserCompat.parsePackage

    public static final PackageParser.Package parsePackage(final Context context, final File apk, final int flags) throws PackageParser.PackageParserException {
        if (Build.VERSION.SDK_INT >= 24) {
            return PackageParserV24.parsePackage(context, apk, flags);
        } else if (Build.VERSION.SDK_INT >= 21) {
            return PackageParserLollipop.parsePackage(context, apk, flags);
        } else {
            return PackageParserLegacy.parsePackage(context, apk, flags);
        }
    }

### new PackageInfo()

	this.mPackageInfo = new PackageInfo();
    this.mPackageInfo.applicationInfo = this.mPackage.applicationInfo;
    this.mPackageInfo.applicationInfo.sourceDir = apk.getAbsolutePath();
    this.mPackageInfo.signatures = this.mPackage.mSignatures;
    this.mPackageInfo.packageName = this.mPackage.packageName;
    if (pluginManager.getLoadedPlugin(mPackageInfo.packageName) != null) {
        throw new RuntimeException("plugin has already been loaded : " + mPackageInfo.packageName);
    }
    this.mPackageInfo.versionCode = this.mPackage.mVersionCode;
    this.mPackageInfo.versionName = this.mPackage.mVersionName;
    this.mPackageInfo.permissions = new PermissionInfo[0];

### createResources(context, apk)

    @WorkerThread
    private static Resources createResources(Context context, File apk) {
        if (Constants.COMBINE_RESOURCES) {
            //如果插件资源合并到宿主里面去的情况，插件可以访问宿主的资源
            Resources resources = new ResourcesManager().createResources(context, apk.getAbsolutePath());
			// hook resource
            ResourcesManager.hookResources(context, resources);
            return resources;
        } else {
            //插件使用独立的Resources，不与宿主有关系，无法访问到宿主的资源
            Resources hostResources = context.getResources();
            // 利用 addAssetPath 来创建 assetManager
            AssetManager assetManager = createAssetManager(context, apk);
            // 创建 Resources
            return new Resources(assetManager, hostResources.getDisplayMetrics(), hostResources.getConfiguration());
        }
    }

createResources(Context hostContext, String apk)
------------------------------------------------
    public static synchronized Resources createResources(Context hostContext, String apk) {
        Resources hostResources = hostContext.getResources();
        Resources newResources = null;
        AssetManager assetManager;
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                // 在Android 5.0 之前，addAssetPath 只是把 apk 路径加入到资源路径列表里
                // 但是资源的解析其实是在很早的时候就已经执行完了
                // 所以重新构造一个新的 AssetManager，再 hook resource 替换系统的 mResource
                assetManager = AssetManager.class.newInstance();
                ReflectUtil.invoke(AssetManager.class, assetManager, "addAssetPath", hostContext.getApplicationInfo().sourceDir);
            } else {
                // Android 5.0 资源做过分区，直接获取宿主的 assetManager
                assetManager = hostResources.getAssets();
            }
            // 将当前插件的资源路径加入到 assetManager 中
            ReflectUtil.invoke(AssetManager.class, assetManager, "addAssetPath", apk);
            // 获取之前加载完毕的插件，得到它们的 apk 路径再重新添加到 assetManager 中
            // 是否在 Android 5.0 及以上不需要此步骤？
            List<LoadedPlugin> pluginList = PluginManager.getInstance(hostContext).getAllLoadedPlugins();
            for (LoadedPlugin plugin : pluginList) {
                ReflectUtil.invoke(AssetManager.class, assetManager, "addAssetPath", plugin.getLocation());
            }
            // 对一些安卓厂商做兼容性处理，Resources 的类名各不同
            // 创建 resource
            if (isMiUi(hostResources)) {
                newResources = MiUiResourcesCompat.createResources(hostResources, assetManager);
            } else if (isVivo(hostResources)) {
                newResources = VivoResourcesCompat.createResources(hostContext, hostResources, assetManager);
            } else if (isNubia(hostResources)) {
                newResources = NubiaResourcesCompat.createResources(hostResources, assetManager);
            } else if (isNotRawResources(hostResources)) {
                newResources = AdaptationResourcesCompat.createResources(hostResources, assetManager);
            } else {
                // is raw android resources
                newResources = new Resources(assetManager, hostResources.getDisplayMetrics(), hostResources.getConfiguration());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return newResources;
    }

hookResources(Context base, Resources resources)
------------------------------------------------

    public static void hookResources(Context base, Resources resources) {
        if (Build.VERSION.SDK_INT >= 24) {
            return;
        }

        try {
            // 替换 hostContext 中的 mResources
            ReflectUtil.setField(base.getClass(), base, "mResources", resources);
            // 替换 packageInfo 中的 mResources
            Object loadedApk = ReflectUtil.getPackageInfo(base);
            ReflectUtil.setField(loadedApk.getClass(), loadedApk, "mResources", resources);
            // 将 resource 放入 mActiveResources 中
            Object activityThread = ReflectUtil.getActivityThread(base);
            Object resManager = ReflectUtil.getField(activityThread.getClass(), activityThread, "mResourcesManager");
            Map<Object, WeakReference<Resources>> map = (Map<Object, WeakReference<Resources>>) ReflectUtil.getField(resManager.getClass(), resManager, "mActiveResources");
            Object key = map.keySet().iterator().next();
            map.put(key, new WeakReference<>(resources));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


### createClassLoader(Context context, File apk, File libsDir, ClassLoader parent)

    private static ClassLoader createClassLoader(Context context, File apk, File libsDir, ClassLoader parent) {
        // create classloader
        File dexOutputDir = context.getDir(Constants.OPTIMIZE_DIR, Context.MODE_PRIVATE);
        String dexOutputPath = dexOutputDir.getAbsolutePath();
        DexClassLoader loader = new DexClassLoader(apk.getAbsolutePath(), dexOutputPath, libsDir.getAbsolutePath(), parent);
        // 如果合并，就会把宿主的 classloader 中的 dex 加入到插件中的 classloader
        if (Constants.COMBINE_CLASSLOADER) {
            try {
                DexUtil.insertDex(loader);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return loader;
    }

### tryToCopyNativeLib(apk);

复制 .so 文件

### Cache instrumentations

    Map<ComponentName, InstrumentationInfo> instrumentations = new HashMap<ComponentName, InstrumentationInfo>();
    for (PackageParser.Instrumentation instrumentation : this.mPackage.instrumentation) {
        instrumentations.put(instrumentation.getComponentName(), instrumentation.info);
    }
    this.mInstrumentationInfos = Collections.unmodifiableMap(instrumentations);
    this.mPackageInfo.instrumentation = instrumentations.values().toArray(new InstrumentationInfo[instrumentations.size()]);

### Cache activities

    Map<ComponentName, ActivityInfo> activityInfos = new HashMap<ComponentName, ActivityInfo>();
    for (PackageParser.Activity activity : this.mPackage.activities) {
        activityInfos.put(activity.getComponentName(), activity.info);
    }
    this.mActivityInfos = Collections.unmodifiableMap(activityInfos);
    this.mPackageInfo.activities = activityInfos.values().toArray(new ActivityInfo[activityInfos.size()]);

### Cache providers

    Map<String, ProviderInfo> providers = new HashMap<String, ProviderInfo>();
    Map<ComponentName, ProviderInfo> providerInfos = new HashMap<ComponentName, ProviderInfo>();
    for (PackageParser.Provider provider : this.mPackage.providers) {
        providers.put(provider.info.authority, provider.info);
        providerInfos.put(provider.getComponentName(), provider.info);
    }
    this.mProviders = Collections.unmodifiableMap(providers);
    this.mProviderInfos = Collections.unmodifiableMap(providerInfos);
    this.mPackageInfo.providers = providerInfos.values().toArray(new ProviderInfo[providerInfos.size()]);

### Register broadcast receivers dynamically
        
    Map<ComponentName, ActivityInfo> receivers = new HashMap<ComponentName, ActivityInfo>();
    for (PackageParser.Activity receiver : this.mPackage.receivers) {
        receivers.put(receiver.getComponentName(), receiver.info);

        try {
            // 得到插件中的 BroadcastReceiver 的实例
            BroadcastReceiver br = BroadcastReceiver.class.cast(getClassLoader().loadClass(receiver.getComponentName().getClassName()).newInstance());
            // 注册广播
            for (PackageParser.ActivityIntentInfo aii : receiver.intents) {
                this.mHostContext.registerReceiver(br, aii);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    this.mReceiverInfos = Collections.unmodifiableMap(receivers);
    this.mPackageInfo.receivers = receivers.values().toArray(new ActivityInfo[receivers.size()]);


插件Activity加载过程
==================

VAInstrumentation
=================
execStartActivity
-------------------
    public ActivityResult execStartActivity(
            Context who, IBinder contextThread, IBinder token, Activity target,
            Intent intent, int requestCode, Bundle options) {
        // 这个主要是当component为null时，根据启动Activity时，配置的action，data,category等去已加载的plugin中匹配到确定的Activity的。
        mPluginManager.getComponentsHandler().transformIntentToExplicitAsNeeded(intent);
        // null component is an implicitly intent
        if (intent.getComponent() != null) {
            Log.i(TAG, String.format("execStartActivity[%s : %s]", intent.getComponent().getPackageName(),
                    intent.getComponent().getClassName()));
            // 先将 intent 中的 packagename 和 classname 替换为 Stub Activity
            this.mPluginManager.getComponentsHandler().markIntentIfNeeded(intent);
        }
        // 利用反射去执行原来的 execStartActivity 方法
        ActivityResult result = realExecStartActivity(who, contextThread, token, target,
                    intent, requestCode, options);

        return result;

    }

ComponentsHandler
-----------------
transformIntentToExplicitAsNeeded
---------------------------------
	public Intent transformIntentToExplicitAsNeeded(Intent intent) {
        ComponentName component = intent.getComponent();
        if (component == null) {
            ResolveInfo info = mPluginManager.resolveActivity(intent);
            if (info != null && info.activityInfo != null) {
                component = new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
                intent.setComponent(component);
            }
        }

        return intent;
    }

markIntentIfNeeded
-------------------
    public void markIntentIfNeeded(Intent intent) {
        if (intent.getComponent() == null) {
            return;
        }
        String targetPackageName = intent.getComponent().getPackageName();
        String targetClassName = intent.getComponent().getClassName();
        // search map and return specific launchmode stub activity
        // 先将目标的 packagename 和 classname 保存好，之后以便恢复的
        if (!targetPackageName.equals(mContext.getPackageName()) && mPluginManager.getLoadedPlugin(targetPackageName) != null) {
            intent.putExtra(Constants.KEY_IS_PLUGIN, true);
            intent.putExtra(Constants.KEY_TARGET_PACKAGE, targetPackageName);
            intent.putExtra(Constants.KEY_TARGET_ACTIVITY, targetClassName);
            // 根据 launchMode 、theme 查找对应类型的 stub activity
            // 并将 intent 中的目标 activity 替换为 stub activity
            dispatchStubActivity(intent);
        }
    }


VAInstrumentation
=================
newActivity
------------

    @Override
    public Activity newActivity(ClassLoader cl, String className, Intent intent) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        try {
            cl.loadClass(className);
        } catch (ClassNotFoundException e) {
            // 若出错则说明要启动的是插件里面的classname
            LoadedPlugin plugin = this.mPluginManager.getLoadedPlugin(intent);
            String targetClassName = PluginUtil.getTargetActivity(intent);

            Log.i(TAG, String.format("newActivity[%s : %s]", className, targetClassName));

            if (targetClassName != null) {
                Activity activity = mBase.newActivity(plugin.getClassLoader(), targetClassName, intent);
                activity.setIntent(intent);

                try {
                    // for 4.1+  设置好 resource
                    ReflectUtil.setField(ContextThemeWrapper.class, activity, "mResources", plugin.getResources());
                } catch (Exception ignored) {
                    // ignored.
                }

                return activity;
            }
        }
        // 启动的不是插件中的activity就使用原来的
        return mBase.newActivity(cl, className, intent);
    }

callActivityOnCreate
--------------------

    @Override
    public void callActivityOnCreate(Activity activity, Bundle icicle) {
        final Intent intent = activity.getIntent();
        // 根据 KEY_IS_PLUGIN 来判断是否是启动插件 activity
        if (PluginUtil.isIntentFromPlugin(intent)) {
            Context base = activity.getBaseContext();
            try {
                // 将创建出来的插件 activity 中 mResources mBase mApplication 进行替换
                LoadedPlugin plugin = this.mPluginManager.getLoadedPlugin(intent);
                ReflectUtil.setField(base.getClass(), base, "mResources", plugin.getResources());
                ReflectUtil.setField(ContextWrapper.class, activity, "mBase", plugin.getPluginContext());
                ReflectUtil.setField(Activity.class, activity, "mApplication", plugin.getApplication());
                ReflectUtil.setFieldNoException(ContextThemeWrapper.class, activity, "mBase", plugin.getPluginContext());

                // set screenOrientation 设置屏幕方向
                ActivityInfo activityInfo = plugin.getActivityInfo(PluginUtil.getComponent(intent));
                if (activityInfo.screenOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
                    activity.setRequestedOrientation(activityInfo.screenOrientation);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }

        mBase.callActivityOnCreate(activity, icicle);
    }

插件Service加载过程
==================

ActivityManagerProxy
====================
invoke
------

	@Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if ("startService".equals(method.getName())) {
            try {
                return startService(proxy, method, args);
            } catch (Throwable e) {
                Log.e(TAG, "Start service error", e);
            }
        } 
		...
	}

startService
------------
    private Object startService(Object proxy, Method method, Object[] args) throws Throwable {
        IApplicationThread appThread = (IApplicationThread) args[0];
        Intent target = (Intent) args[1];
        ResolveInfo resolveInfo = this.mPluginManager.resolveService(target, 0);
        if (null == resolveInfo || null == resolveInfo.serviceInfo) {
            // 如果为空的话，不是启动的插件service
            return method.invoke(this.mActivityManager, args);
        }
        // 
        return startDelegateServiceForTarget(target, resolveInfo.serviceInfo, null, RemoteService.EXTRA_COMMAND_START_SERVICE);
    }

startDelegateServiceForTarget
-----------------------------
    private ComponentName startDelegateServiceForTarget(Intent target, ServiceInfo serviceInfo, Bundle extras, int command) {
        Intent wrapperIntent = wrapperTargetIntent(target, serviceInfo, extras, command);
        return mPluginManager.getHostContext().startService(wrapperIntent);
    }

wrapperTargetIntent
-------------------
    private Intent wrapperTargetIntent(Intent target, ServiceInfo serviceInfo, Bundle extras, int command) {
        // fill in service with ComponentName
        target.setComponent(new ComponentName(serviceInfo.packageName, serviceInfo.name));
        String pluginLocation = mPluginManager.getLoadedPlugin(target.getComponent()).getLocation();

        // start delegate service to run plugin service inside
        // 检查插件 service 是否是本地还是远程，再选择相对应的 delegate service
        boolean local = PluginUtil.isLocalService(serviceInfo);
        Class<? extends Service> delegate = local ? LocalService.class : RemoteService.class;
        // 创建新的 intent ，用来启动 delegate service。intent 中会保存插件 service 的相关信息
        Intent intent = new Intent();
        // 设置 class 为对应的 delegate service classname
        intent.setClass(mPluginManager.getHostContext(), delegate);
        intent.putExtra(RemoteService.EXTRA_TARGET, target);
        intent.putExtra(RemoteService.EXTRA_COMMAND, command);
        intent.putExtra(RemoteService.EXTRA_PLUGIN_LOCATION, pluginLocation);
        if (extras != null) {
            intent.putExtras(extras);
        }

        return intent;
    }

LocalService
============
onStartCommand
---------------

	@Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (null == intent || !intent.hasExtra(EXTRA_TARGET) || !intent.hasExtra(EXTRA_COMMAND)) {
            return START_STICKY;
        }

        Intent target = intent.getParcelableExtra(EXTRA_TARGET);
        int command = intent.getIntExtra(EXTRA_COMMAND, 0);
        if (null == target || command <= 0) {
            return START_STICKY;
        }

        ComponentName component = target.getComponent();
        LoadedPlugin plugin = mPluginManager.getLoadedPlugin(component);

        switch (command) {
            case EXTRA_COMMAND_START_SERVICE: {
                ActivityThread mainThread = (ActivityThread) ReflectUtil.getActivityThread(getBaseContext());
                IApplicationThread appThread = mainThread.getApplicationThread();
                Service service;
                // 判断该插件的 service 是否已经创建过
                if (this.mPluginManager.getComponentsHandler().isServiceAvailable(component)) {
                    // 直接从已启动服务 map 中取出该插件的 service
                    service = this.mPluginManager.getComponentsHandler().getService(component);
                } else {
                    try {
                        // 创建插件 service 对象
                        service = (Service) plugin.getClassLoader().loadClass(component.getClassName()).newInstance();

                        // 需要调用 attach
                        Application app = plugin.getApplication();
                        IBinder token = appThread.asBinder();
                        Method attach = service.getClass().getMethod("attach", Context.class, ActivityThread.class, String.class, IBinder.class, Application.class, Object.class);
                        IActivityManager am = mPluginManager.getActivityManager();
                        attach.invoke(service, plugin.getPluginContext(), mainThread, component.getClassName(), token, app, am);
                        // oncreate
                        service.onCreate();
                        // 放入已启动服务 map 中
                        this.mPluginManager.getComponentsHandler().rememberService(component, service);
                    } catch (Throwable t) {
                        return START_STICKY;
                    }
                }
                // 调用 onStartCommand
                service.onStartCommand(target, 0, this.mPluginManager.getComponentsHandler().getServiceCounter(service).getAndIncrement());
                break;
            }
		...
	}


插件BroadcastReceiver加载过程
============================

LoadedPlugin
============
    Map<ComponentName, ActivityInfo> receivers = new HashMap<ComponentName, ActivityInfo>();
    for (PackageParser.Activity receiver : this.mPackage.receivers) {
        receivers.put(receiver.getComponentName(), receiver.info);

        try {
            // 得到插件中的 BroadcastReceiver 的实例
            BroadcastReceiver br = BroadcastReceiver.class.cast(getClassLoader().loadClass(receiver.getComponentName().getClassName()).newInstance());
            // 注册广播
            for (PackageParser.ActivityIntentInfo aii : receiver.intents) {
                this.mHostContext.registerReceiver(br, aii);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

插件ContentProvider加载过程
==========================
PluginContext
=============

    @Override
    public ContentResolver getContentResolver() {
        return new PluginContentResolver(getHostContext());
    }

PluginContentResolver
======================

    public PluginContentResolver(Context context) {
        super(context);
        mBase = context.getContentResolver();
        mPluginManager = PluginManager.getInstance(context);
    }

acquireProvider
--------------
    protected IContentProvider acquireProvider(Context context, String auth) {
        try {
            if (mPluginManager.resolveContentProvider(auth, 0) != null) {
                return mPluginManager.getIContentProvider();
            }

            return (IContentProvider) sAcquireProvider.invoke(mBase, context, auth);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

PluginManager
=============
getIContentProvider
---------------------
    public synchronized IContentProvider getIContentProvider() {
        if (mIContentProvider == null) {
            hookIContentProviderAsNeeded();
        }

        return mIContentProvider;
    }

hookIContentProviderAsNeeded
------------------------------

    private void hookIContentProviderAsNeeded() {
        Uri uri = Uri.parse(PluginContentResolver.getUri(mContext));
        mContext.getContentResolver().call(uri, "wakeup", null, null);
        try {
            Field authority = null;
            Field mProvider = null;
            ActivityThread activityThread = (ActivityThread) ReflectUtil.getActivityThread(mContext);
            Map mProviderMap = (Map) ReflectUtil.getField(activityThread.getClass(), activityThread, "mProviderMap");
            Iterator iter = mProviderMap.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry entry = (Map.Entry) iter.next();
                Object key = entry.getKey();
                Object val = entry.getValue();
                String auth;
                if (key instanceof String) {
                    auth = (String) key;
                } else {
                    if (authority == null) {
                        authority = key.getClass().getDeclaredField("authority");
                        authority.setAccessible(true);
                    }
                    auth = (String) authority.get(key);
                }
                if (auth.equals(PluginContentResolver.getAuthority(mContext))) {
                    if (mProvider == null) {
                        mProvider = val.getClass().getDeclaredField("mProvider");
                        mProvider.setAccessible(true);
                    }
                    IContentProvider rawProvider = (IContentProvider) mProvider.get(val);
                    IContentProvider proxy = IContentProviderProxy.newInstance(mContext, rawProvider);
                    mIContentProvider = proxy;
                    Log.d(TAG, "hookIContentProvider succeed : " + mIContentProvider);
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
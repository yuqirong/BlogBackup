title: ARouter源码解析（一）
date: 2018-12-24 21:13:20
categories: Android Blog
tags: [Android,开源框架,源码解析]
---
arouter-api version : 1.4.1

前言
======
之前对 ActivityRouter 的源码做了一次分析，相信大家对路由框架已经有一个大概的理解了。

而今天给大家分析一下 ARouter 。大家在项目组件化的过程中，可能绝大多数的开发者都会使用 ARouter 来作为项目的路由框架。毕竟 ARouter 是阿里出品，优点自然不必多说了。

所以在平常使用的过程中，不仅仅要做到会用，还要深入了解一下 ARouter 的内部原理。

本次 ARouter 的解析分为三部分：

1. 对 IRouteRoot 页面跳转进行源码解析；
2. 对 IInterceptorGroup 拦截器进行源码解析；
3. 对 @Autowired 自动注入进行源码解析；

本篇是 ARouter 系列的第一篇，下面就对 IRouteRoot 页面跳转进行详细解析。

ARouter 源码
===========
使用 ARouter 的时候，都需要初始化

``` java
if (isDebug()) {          
    ARouter.openLog();
    ARouter.openDebug();
}
ARouter.init(mApplication);
```

源码分析的入口，就在 ARouter.init 里

``` java
public static void init(Application application) {
    if (!hasInit) {
        logger = _ARouter.logger;
        _ARouter.logger.info(Consts.TAG, "ARouter init start.");
        hasInit = _ARouter.init(application);

        if (hasInit) {
            _ARouter.afterInit();
        }

        _ARouter.logger.info(Consts.TAG, "ARouter init over.");
    }
}
```

源码上可以看到，ARouter 的内部其实是 _ARouter 在起作用，ARouter 只是把 _ARouter 再做了一层包装。那么我们就跟进 _ARouter 的 init 方法。

``` java
protected static synchronized boolean init(Application application) {
    mContext = application;
    LogisticsCenter.init(mContext, executor);
    logger.info(Consts.TAG, "ARouter init success!");
    hasInit = true;
    mHandler = new Handler(Looper.getMainLooper());

    return true;
}
```

最重要的一句代码还是 `LogisticsCenter.init(mContext, executor)` ，其中 executor 是线程池。

那么问题来了， LogisticsCenter 是干什么的呢？

	* LogisticsCenter contains all of the map.
	* 
	* 1. Creates instance when it is first used.
	* 2. Handler Multi-Module relationship map(*)
	* 3. Complex logic to solve duplicate group definition

根据官方的注释，LogisticsCenter 是包含了所有的映射，处理跨模块的映射关系以及匹配路由等。

所以根据之前 ActivityRouter 的经验猜测得到，LogisticsCenter 的 init 方法里面，肯定会去加载路由，并建立关系。

``` java
public synchronized static void init(Context context, ThreadPoolExecutor tpe) throws HandlerException {
    mContext = context;
    executor = tpe;

    try {
        long startInit = System.currentTimeMillis();
        //billy.qi modified at 2017-12-06
        //load by plugin first
        loadRouterMap();
        if (registerByPlugin) {
            logger.info(TAG, "Load router map by arouter-auto-register plugin.");
        } else {
            Set<String> routerMap;

            // 如果是debug或者新版本的话，会去重新加载路由映射
            if (ARouter.debuggable() || PackageUtils.isNewVersion(context)) {
                logger.info(TAG, "Run with debug mode or new install, rebuild router map.");
                // 加载路由映射
                routerMap = ClassUtils.getFileNameByPackageName(mContext, ROUTE_ROOT_PAKCAGE);
                // 保存所有的路由映射到 SharedPreferences
                if (!routerMap.isEmpty()) {
                    context.getSharedPreferences(AROUTER_SP_CACHE_KEY, Context.MODE_PRIVATE).edit().putStringSet(AROUTER_SP_KEY_MAP, routerMap).apply();
                }
                // 保存新版本号到 sharedpreference
                PackageUtils.updateVersion(context);    // Save new version name when router map update finishes.
            } else {
                // 否则就从 SharedPreferences 中读取之前保存的所有路由映射
                logger.info(TAG, "Load router map from cache.");
                routerMap = new HashSet<>(context.getSharedPreferences(AROUTER_SP_CACHE_KEY, Context.MODE_PRIVATE).getStringSet(AROUTER_SP_KEY_MAP, new HashSet<String>()));
            }

            logger.info(TAG, "Find router map finished, map size = " + routerMap.size() + ", cost " + (System.currentTimeMillis() - startInit) + " ms.");
            startInit = System.currentTimeMillis();

            // 把上面加载得到的路由映射根据ClassName分为三种，分别进行注册
            // IRouteRoot 页面跳转
            // IInterceptorGroup 拦截器
            // IProviderGroup 服务组件
            for (String className : routerMap) {
                if (className.startsWith(ROUTE_ROOT_PAKCAGE + DOT + SDK_NAME + SEPARATOR + SUFFIX_ROOT)) {
                    // This one of root elements, load root.
                    ((IRouteRoot) (Class.forName(className).getConstructor().newInstance())).loadInto(Warehouse.groupsIndex);
                } else if (className.startsWith(ROUTE_ROOT_PAKCAGE + DOT + SDK_NAME + SEPARATOR + SUFFIX_INTERCEPTORS)) {
                    // Load interceptorMeta
                    ((IInterceptorGroup) (Class.forName(className).getConstructor().newInstance())).loadInto(Warehouse.interceptorsIndex);
                } else if (className.startsWith(ROUTE_ROOT_PAKCAGE + DOT + SDK_NAME + SEPARATOR + SUFFIX_PROVIDERS)) {
                    // Load providerIndex
                    ((IProviderGroup) (Class.forName(className).getConstructor().newInstance())).loadInto(Warehouse.providersIndex);
                }
            }
        }

        ...
        
    } catch (Exception e) {
        throw new HandlerException(TAG + "ARouter init logistics center exception! [" + e.getMessage() + "]");
    }
}
```

LogisticsCenter 的 init 方法的代码基本上都可以看得懂，其中 `ClassUtils.getFileNameByPackageName` 是我们值得探究的地方。这句代码主要做的事情就是从 dex 中遍历 class 找到 arouter-compiler 生成的类集合。具体的分析我们到最后面再讲，这里先埋个伏笔。

接着往下看，我们知道，routerMap 中的 className 都是 arouter-compiler 在编译期生成的，那我们先来看看生成的类长什么样

``` java
/**
 * DO NOT EDIT THIS FILE!!! IT WAS GENERATED BY AROUTER. */
public class ARouter$$Root$$app implements IRouteRoot {
  @Override
  public void loadInto(Map<String, Class<? extends IRouteGroup>> routes) {
    routes.put("test", ARouter$$Group$$test.class);
    routes.put("yourservicegroupname", ARouter$$Group$$yourservicegroupname.class);
  }
}
```

ARouter 的路由会分组加载，比如当前有 /test/abc 和 /test/def 两个路由，那他们同属于 /test 这个组。所以在 Warehouse.groupsIndex 中存放的 key 是路由组名，value 是对应组路由类。查找路由的时候也是根据组名 key ，再找到组路由类 value 中查找匹配的路由。

``` java
/**
 * DO NOT EDIT THIS FILE!!! IT WAS GENERATED BY AROUTER. */
public class ARouter$$Group$$test implements IRouteGroup {
  @Override
  public void loadInto(Map<String, RouteMeta> atlas) {
    atlas.put("/test/activity1", RouteMeta.build(RouteType.ACTIVITY, Test1Activity.class, "/test/activity1", "test", new java.util.HashMap<String, Integer>(){{put("ser", 9); put("ch", 5); put("fl", 6); put("dou", 7); put("boy", 0); put("url", 8); put("pac", 10); put("obj", 11); put("name", 8); put("objList", 11); put("map", 11); put("age", 3); put("height", 3); }}, -1, -2147483648));
    atlas.put("/test/activity2", RouteMeta.build(RouteType.ACTIVITY, Test2Activity.class, "/test/activity2", "test", new java.util.HashMap<String, Integer>(){{put("key1", 8); }}, -1, -2147483648));
    atlas.put("/test/activity3", RouteMeta.build(RouteType.ACTIVITY, Test3Activity.class, "/test/activity3", "test", new java.util.HashMap<String, Integer>(){{put("name", 8); put("boy", 0); put("age", 3); }}, -1, -2147483648));
    atlas.put("/test/activity4", RouteMeta.build(RouteType.ACTIVITY, Test4Activity.class, "/test/activity4", "test", null, -1, -2147483648));
    atlas.put("/test/fragment", RouteMeta.build(RouteType.FRAGMENT, BlankFragment.class, "/test/fragment", "test", null, -1, -2147483648));
    atlas.put("/test/webview", RouteMeta.build(RouteType.ACTIVITY, TestWebview.class, "/test/webview", "test", null, -1, -2147483648));
  }
}
```

可以看到路由相关的参数配置被构造成了一个 RouteMeta 对象。RouteMeta 类如下所示：

``` java
public class RouteMeta {
    private RouteType type;         // Type of route
    private Element rawType;        // Raw type of route
    private Class<?> destination;   // Destination
    private String path;            // Path of route
    private String group;           // Group of route
    private int priority = -1;      // The smaller the number, the higher the priority
    private int extra;              // Extra data
    private Map<String, Integer> paramsType;  // Param type
    private String name;

    private Map<String, Autowired> injectConfig;  // Cache inject config.

    ...
}
```

到这里，加载路由的部分就完成了，剩下的就是跳转路由了。

跳转路由的通常操作：

	ARouter.getInstance().build("/test/abc").navigation();

那先看一下 ARouter 的 build 方法

``` java
 public Postcard build(String path) {
    return _ARouter.getInstance().build(path);
}
```

里面调用的是 _ARouter 的 build 方法。

``` java
protected Postcard build(String path) {
    if (TextUtils.isEmpty(path)) {
        throw new HandlerException(Consts.TAG + "Parameter is invalid!");
    } else {
        // 获取 PathReplaceService 实例，如果不为空，就处理 path
        PathReplaceService pService = ARouter.getInstance().navigation(PathReplaceService.class);
        if (null != pService) {
            path = pService.forString(path);
        }
        return build(path, extractGroup(path));
    }
}

/**
 * 截取跳转路径中的第一段作为分组名
 */
private String extractGroup(String path) {
    if (TextUtils.isEmpty(path) || !path.startsWith("/")) {
        throw new HandlerException(Consts.TAG + "Extract the default group failed, the path must be start with '/' and contain more than 2 '/'!");
    }

    try {
        String defaultGroup = path.substring(1, path.indexOf("/", 1));
        if (TextUtils.isEmpty(defaultGroup)) {
            throw new HandlerException(Consts.TAG + "Extract the default group failed! There's nothing between 2 '/'!");
        } else {
            return defaultGroup;
        }
    } catch (Exception e) {
        logger.warning(Consts.TAG, "Failed to extract default group! " + e.getMessage());
        return null;
    }
}
```

PathReplaceService 是官方给我们预留的口子，用来对 path 做预处理。如果你有需求来对 path 做统一的预处理，那么直接实现 PathReplaceService 即可。

我们接着跟进，看下 `_ARouter.build(String path, String group)` 方法

``` java
protected Postcard build(String path, String group) {
    if (TextUtils.isEmpty(path) || TextUtils.isEmpty(group)) {
        throw new HandlerException(Consts.TAG + "Parameter is invalid!");
    } else {
        PathReplaceService pService = ARouter.getInstance().navigation(PathReplaceService.class);
        if (null != pService) {
            path = pService.forString(path);
        }
        return new Postcard(path, group);
    }
}
```

发现在 `build(String path, String group)` 中直接创建了一个 Postcard 对象并返回。Postcard 类是继承了 RouteMeta ，额外添加了一些其他的信息。

有了 Postcard 之后，直接调用 navigation 进行跳转。

``` java
public Object navigation(Context context, NavigationCallback callback) {
    return ARouter.getInstance().navigation(context, this, -1, callback);
}

public void navigation(Activity mContext, int requestCode, NavigationCallback callback) {
    ARouter.getInstance().navigation(mContext, this, requestCode, callback);
}
```

Postcard 的所有 navigation 方法最后都会调用 ARouter 的 navigation 这个方法。

``` java
public Object navigation(Context mContext, Postcard postcard, int requestCode, NavigationCallback callback) {
    return _ARouter.getInstance().navigation(mContext, postcard, requestCode, callback);
}
```

最后还是调用了 _ARouter.navigation 

``` java
protected Object navigation(final Context context, final Postcard postcard, final int requestCode, final NavigationCallback callback) {
    try {
        // 将 postcard 与路由表中进行匹配，并且填充 postcard 的数据
        LogisticsCenter.completion(postcard);
    } catch (NoRouteFoundException ex) {
        logger.warning(Consts.TAG, ex.getMessage());
        // 如果 debug ，就显示匹配错误
        if (debuggable()) {
            // Show friendly tips for user.
            runInMainThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(mContext, "There's no route matched!\n" +
                            " Path = [" + postcard.getPath() + "]\n" +
                            " Group = [" + postcard.getGroup() + "]", Toast.LENGTH_LONG).show();
                }
            });
        }
        // 回调路由匹配失败
        if (null != callback) {
            callback.onLost(postcard);
        } else {    
            // No callback for this invoke, then we use the global degrade service.
            // 如果没有回调，就调用全局降级的策略
            DegradeService degradeService = ARouter.getInstance().navigation(DegradeService.class);
            if (null != degradeService) {
                degradeService.onLost(context, postcard);
            }
        }

        return null;
    }

    // 回调路由匹配成功
    if (null != callback) {
        callback.onFound(postcard);
    }
    // 如果不是绿色通道，就调用拦截器，拦截器这部分后面单独出来讲，这里就不讲了
    if (!postcard.isGreenChannel()) {   // It must be run in async thread, maybe interceptor cost too mush time made ANR.
        interceptorService.doInterceptions(postcard, new InterceptorCallback() {
            /**
             * Continue process
             *
             * @param postcard route meta
             */
            @Override
            public void onContinue(Postcard postcard) {
                _navigation(context, postcard, requestCode, callback);
            }

            /**
             * Interrupt process, pipeline will be destory when this method called.
             *
             * @param exception Reson of interrupt.
             */
            @Override
            public void onInterrupt(Throwable exception) {
                if (null != callback) {
                    callback.onInterrupt(postcard);
                }

                logger.info(Consts.TAG, "Navigation failed, termination by interceptor : " + exception.getMessage());
            }
        });
    } else {
        // 否则调用 _navigation 进行跳转
        return _navigation(context, postcard, requestCode, callback);
    }

    return null;
}

private Object _navigation(final Context context, final Postcard postcard, final int requestCode, final NavigationCallback callback) {
    final Context currentContext = null == context ? mContext : context;

    switch (postcard.getType()) {
        case ACTIVITY: // 如果是 activity 的，执行跳转
            // Build intent
            final Intent intent = new Intent(currentContext, postcard.getDestination());
            intent.putExtras(postcard.getExtras());

            // Set flags.
            int flags = postcard.getFlags();
            if (-1 != flags) {
                intent.setFlags(flags);
            } else if (!(currentContext instanceof Activity)) {    // Non activity, need less one flag.
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }

            // Set Actions
            String action = postcard.getAction();
            if (!TextUtils.isEmpty(action)) {
                intent.setAction(action);
            }

            // Navigation in main looper.
            runInMainThread(new Runnable() {
                @Override
                public void run() {
                    startActivity(requestCode, currentContext, intent, postcard, callback);
                }
            });

            break;
        case PROVIDER: // 如果是服务组件，那么直接返回该组件
            return postcard.getProvider();
        case BOARDCAST:
        case CONTENT_PROVIDER:
        case FRAGMENT: // 如果是 fragment 的话，返回该 fragment 的实例
            Class fragmentMeta = postcard.getDestination();
            try {
                Object instance = fragmentMeta.getConstructor().newInstance();
                if (instance instanceof Fragment) {
                    ((Fragment) instance).setArguments(postcard.getExtras());
                } else if (instance instanceof android.support.v4.app.Fragment) {
                    ((android.support.v4.app.Fragment) instance).setArguments(postcard.getExtras());
                }

                return instance;
            } catch (Exception ex) {
                logger.error(Consts.TAG, "Fetch fragment instance error, " + TextUtils.formatStackTrace(ex.getStackTrace()));
            }
        case METHOD:
        case SERVICE:
        default:
            return null;
    }

    return null;
}

```

上面的代码基本上都写了注释，大家应该都能看懂。

我们重点来关注下 `LogisticsCenter.completion(postcard);` 

``` java
public synchronized static void completion(Postcard postcard) {
    if (null == postcard) {
        throw new NoRouteFoundException(TAG + "No postcard!");
    }
    // 先根据 path 去获取 RouteMeta
    RouteMeta routeMeta = Warehouse.routes.get(postcard.getPath());
    if (null == routeMeta) {    // 如果 routeMeta 为空，可能是不存在或者是未加载
        Class<? extends IRouteGroup> groupMeta = Warehouse.groupsIndex.get(postcard.getGroup());  // 加载分组下的路由映射
        // 如果不存在，就报错
        if (null == groupMeta) {
            throw new NoRouteFoundException(TAG + "There is no route match the path [" + postcard.getPath() + "], in group [" + postcard.getGroup() + "]");
        } else {
            // Load route and cache it into memory, then delete from metas.
            try {
                if (ARouter.debuggable()) {
                    logger.debug(TAG, String.format(Locale.getDefault(), "The group [%s] starts loading, trigger by [%s]", postcard.getGroup(), postcard.getPath()));
                }
                // 实现按需加载
                IRouteGroup iGroupInstance = groupMeta.getConstructor().newInstance();
                iGroupInstance.loadInto(Warehouse.routes);
                // 移除 groupsIndex , 否则会造成死循环
                Warehouse.groupsIndex.remove(postcard.getGroup());

                if (ARouter.debuggable()) {
                    logger.debug(TAG, String.format(Locale.getDefault(), "The group [%s] has already been loaded, trigger by [%s]", postcard.getGroup(), postcard.getPath()));
                }
            } catch (Exception e) {
                throw new HandlerException(TAG + "Fatal exception when loading group meta. [" + e.getMessage() + "]");
            }
            // 重新加载一遍
            completion(postcard);   // Reload
        }
    } else {
        // 找到对应的 routeMeta， 填充 postcard 数据
        postcard.setDestination(routeMeta.getDestination());
        postcard.setType(routeMeta.getType());
        postcard.setPriority(routeMeta.getPriority());
        postcard.setExtra(routeMeta.getExtra());

        Uri rawUri = postcard.getUri();
        // 如果 rawUri 不为空，则是 uri 跳转。就解析 rawUri 中的参数，放入 bundle 中
        if (null != rawUri) {   // Try to set params into bundle.
            Map<String, String> resultMap = TextUtils.splitQueryParameters(rawUri);
            Map<String, Integer> paramsType = routeMeta.getParamsType();

            if (MapUtils.isNotEmpty(paramsType)) {
                // Set value by its type, just for params which annotation by @Param
                for (Map.Entry<String, Integer> params : paramsType.entrySet()) {
                    setValue(postcard,
                            params.getValue(),
                            params.getKey(),
                            resultMap.get(params.getKey()));
                }

                // Save params name which need auto inject.
                postcard.getExtras().putStringArray(ARouter.AUTO_INJECT, paramsType.keySet().toArray(new String[]{}));
            }

            // Save raw uri
            postcard.withString(ARouter.RAW_URI, rawUri.toString());
        }
        // 如果是 PROVIDER 和 FRAGMENT 类型的，开启绿色通道
        switch (routeMeta.getType()) {
            case PROVIDER:  // if the route is provider, should find its instance
                // Its provider, so it must implement IProvider
                Class<? extends IProvider> providerMeta = (Class<? extends IProvider>) routeMeta.getDestination();
                IProvider instance = Warehouse.providers.get(providerMeta);
                if (null == instance) { // There's no instance of this provider
                    IProvider provider;
                    try {
                        provider = providerMeta.getConstructor().newInstance();
                        provider.init(mContext);
                        Warehouse.providers.put(providerMeta, provider);
                        instance = provider;
                    } catch (Exception e) {
                        throw new HandlerException("Init provider failed! " + e.getMessage());
                    }
                }
                postcard.setProvider(instance);
                postcard.greenChannel();    // Provider should skip all of interceptors
                break;
            case FRAGMENT:
                postcard.greenChannel();    // Fragment needn't interceptors
            default:
                break;
        }
    }
}
```

至此，整个路由跳转的流程就讲完了，大致的流程可以分为

1. 加载路由映射
2. 根据 path 构造出 Postcard 对象
3. 区分 Postcard 的 type 来实现跳转

番外
===
前面说过，ARouter 会在 dex 中寻找 arouter-compiler 生成的类。那我们最后来看看是怎么实现的。

``` java
public static Set<String> getFileNameByPackageName(Context context, final String packageName) throws PackageManager.NameNotFoundException, IOException, InterruptedException {
    final Set<String> classNames = new HashSet<>();
    // 获取 dex 文件存放的路径
    List<String> paths = getSourcePaths(context);
    final CountDownLatch parserCtl = new CountDownLatch(paths.size());
    // 遍历所有 dex 文件的路径
    for (final String path : paths) {
        DefaultPoolExecutor.getInstance().execute(new Runnable() {
            @Override
            public void run() {
                DexFile dexfile = null;

                try {
                    // 根据路径加载 dex 文件
                    if (path.endsWith(EXTRACTED_SUFFIX)) {
                        //NOT use new DexFile(path), because it will throw "permission error in /data/dalvik-cache"
                        dexfile = DexFile.loadDex(path, path + ".tmp", 0);
                    } else {
                        dexfile = new DexFile(path);
                    }
                    // 遍历 dexfile 的中所有 className 如果是 arouter 报名开头的，就加入到 classNames 中
                    Enumeration<String> dexEntries = dexfile.entries();
                    while (dexEntries.hasMoreElements()) {
                        String className = dexEntries.nextElement();
                        if (className.startsWith(packageName)) {
                            classNames.add(className);
                        }
                    }
                } catch (Throwable ignore) {
                    Log.e("ARouter", "Scan map file in dex files made error.", ignore);
                } finally {
                    if (null != dexfile) {
                        try {
                            dexfile.close();
                        } catch (Throwable ignore) {
                        }
                    }

                    parserCtl.countDown();
                }
            }
        });
    }

    parserCtl.await();

    Log.d(Consts.TAG, "Filter " + classNames.size() + " classes by packageName <" + packageName + ">");
    return classNames;
}
```

我们再来看下 getSourcePaths 方法，看看它是怎么找 dex 文件路径的

``` java
    public static List<String> getSourcePaths(Context context) throws PackageManager.NameNotFoundException, IOException {
        ApplicationInfo applicationInfo = context.getPackageManager().getApplicationInfo(context.getPackageName(), 0);
        File sourceApk = new File(applicationInfo.sourceDir);

        List<String> sourcePaths = new ArrayList<>();
        sourcePaths.add(applicationInfo.sourceDir); //add the default apk path

        //the prefix of extracted file, ie: test.classes
        String extractedFilePrefix = sourceApk.getName() + EXTRACTED_NAME_EXT;

//        如果VM已经支持了MultiDex，就不要去Secondary Folder加载 Classesx.zip了，那里已经么有了
//        通过是否存在sp中的multidex.version是不准确的，因为从低版本升级上来的用户，是包含这个sp配置的
        if (!isVMMultidexCapable()) {
            //the total dex numbers
            int totalDexNumber = getMultiDexPreferences(context).getInt(KEY_DEX_NUMBER, 1);
            File dexDir = new File(applicationInfo.dataDir, SECONDARY_FOLDER_NAME);

            for (int secondaryNumber = 2; secondaryNumber <= totalDexNumber; secondaryNumber++) {
                //for each dex file, ie: test.classes2.zip, test.classes3.zip...
                String fileName = extractedFilePrefix + secondaryNumber + EXTRACTED_SUFFIX;
                File extractedFile = new File(dexDir, fileName);
                if (extractedFile.isFile()) {
                    sourcePaths.add(extractedFile.getAbsolutePath());
                    //we ignore the verify zip part
                } else {
                    throw new IOException("Missing extracted secondary dex file '" + extractedFile.getPath() + "'");
                }
            }
        }

        // 如果是debug的，那么额外去加载下 instant run 中的dex文件路径
        if (ARouter.debuggable()) { // Search instant run support only debuggable
            sourcePaths.addAll(tryLoadInstantRunDexFile(applicationInfo));
        }
        return sourcePaths;
    }
```
	
更多的细节有兴趣的同学可以自己回去看，这里因为篇幅的原因就不过多讲这些了。

那么今天就到这里结束了，关于 ARouter 系列的更多源码解析，可以看接下来的两篇博客。

bye



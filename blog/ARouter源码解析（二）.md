title: ARouter源码解析（二）
date: 2018-12-24 21:13:20
categories: Android Blog

tags: [Android,开源框架,源码解析]
---
arouter-api version : 1.4.1

前言
======

前几天对 ARouter 的页面跳转源码进行了分析，趁着今天有空，就讲讲 ARouter 里面的拦截器吧。

ARouter 拦截器的使用方法在这就不多说了，不了解的同学可以去 GitHub 上看看。那就直接进入正题了。

拦截器解析
========
把视线转移回 ARouter 的 init 方法

``` java
public static void init(Application application) {
    if (!hasInit) {
        logger = _ARouter.logger;
        _ARouter.logger.info(Consts.TAG, "ARouter init start.");
        hasInit = _ARouter.init(application);
        // 如果初始化完成了
        if (hasInit) {
            _ARouter.afterInit();
        }

        _ARouter.logger.info(Consts.TAG, "ARouter init over.");
    }
}
```

在 init 中，判断了初始化完成后，调用了 `_ARouter.afterInit()` 来初始化拦截器，跟进代码去看看。

```
static void afterInit() {
    // Trigger interceptor init, use byName.
    interceptorService = (InterceptorService) ARouter.getInstance().build("/arouter/service/interceptor").navigation();
}
```

发现有个 InterceptorService ，InterceptorService 就是用来控制拦截的服务组件，来看看它的接口是怎么定义的

``` java
public interface InterceptorService extends IProvider {

    /**
     * Do interceptions
     */
    void doInterceptions(Postcard postcard, InterceptorCallback callback);
}
```

之前我们分析过，IProvider 也是可以用 `ARouter.getInstance().build("xxx").navigation()` 的形式获取的。关键的代码在 LogisticsCenter 的 completion 方法中

``` java
/**
 * Completion the postcard by route metas
 *
 * @param postcard Incomplete postcard, should complete by this method.
 */
public synchronized static void completion(Postcard postcard) {
    if (null == postcard) {
        throw new NoRouteFoundException(TAG + "No postcard!");
    }

    RouteMeta routeMeta = Warehouse.routes.get(postcard.getPath());
    if (null == routeMeta) {
        // 省略一大串代码
        ...
    } else {
        // 省略一大串代码
        ...

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

可以看到，如果是 PROVIDER 类型的，就会反射出一个单例对象，并且设置为绿色通道（即不受拦截器的影响）。更详细的代码就不过多介绍了，不理解的同学可以结合着上一篇博客私下回去再看。

所以其实在 afterInit 方法中，只是获取到了 InterceptorService 的实例对象，我们根据上面的 “/arouter/service/interceptor” 可以很轻松的查到，InterceptorService 接口的实现类就是 InterceptorServiceImpl 

``` java
@Route(path = "/arouter/service/interceptor")
public class InterceptorServiceImpl implements InterceptorService {
    private static boolean interceptorHasInit;
    private static final Object interceptorInitLock = new Object();

    ...

    @Override
    public void init(final Context context) {
        LogisticsCenter.executor.execute(new Runnable() {
            @Override
            public void run() {
                if (MapUtils.isNotEmpty(Warehouse.interceptorsIndex)) {
                    for (Map.Entry<Integer, Class<? extends IInterceptor>> entry : Warehouse.interceptorsIndex.entrySet()) {
                        Class<? extends IInterceptor> interceptorClass = entry.getValue();
                        try {
                            IInterceptor iInterceptor = interceptorClass.getConstructor().newInstance();
                            iInterceptor.init(context);
                            Warehouse.interceptors.add(iInterceptor);
                        } catch (Exception ex) {
                            throw new HandlerException(TAG + "ARouter init interceptor error! name = [" + interceptorClass.getName() + "], reason = [" + ex.getMessage() + "]");
                        }
                    }

                    interceptorHasInit = true;

                    logger.info(TAG, "ARouter interceptors init over.");

                    synchronized (interceptorInitLock) {
                        interceptorInitLock.notifyAll();
                    }
                }
            }
        });
    }

}
```

我们先来看 InterceptorServiceImpl 的 init 方法。

在 init 方法中，做的主要事情就是遍历所有 IInterceptor class 并创建出对象，调用其 init 方法，完成初始化操作。

初始化完成之后，InterceptorService又是在哪里被使用的呢？

我们在 _ARouter 的 navigation 方法里可以看到它的踪迹：

``` java
protected Object navigation(final Context context, final Postcard postcard, final int requestCode, final NavigationCallback callback) {

    ...

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
        return _navigation(context, postcard, requestCode, callback);
    }

    return null;
}
```

如果不是绿色通道的话，就会启动拦截器去进行拦截。

``` java
@Override
public void doInterceptions(final Postcard postcard, final InterceptorCallback callback) {
   if (null != Warehouse.interceptors && Warehouse.interceptors.size() > 0) {

       checkInterceptorsInitStatus();
       // 如果拦截器还没有初始化好
       if (!interceptorHasInit) {
           callback.onInterrupt(new HandlerException("Interceptors initialization takes too much time."));
           return;
       }

       LogisticsCenter.executor.execute(new Runnable() {
           @Override
           public void run() {
               CancelableCountDownLatch interceptorCounter = new CancelableCountDownLatch(Warehouse.interceptors.size());
               try {
                   _excute(0, interceptorCounter, postcard);
                   // 设置超时时间 默认300s
                   interceptorCounter.await(postcard.getTimeout(), TimeUnit.SECONDS);
                   if (interceptorCounter.getCount() > 0) {    // 如果 count 大于 0 说明是拦截器超时
                       callback.onInterrupt(new HandlerException("The interceptor processing timed out."));
                   } else if (null != postcard.getTag()) {    // 说明是某个拦截器中断了，导致整个流程中断
                       callback.onInterrupt(new HandlerException(postcard.getTag().toString()));
                   } else { // 否则就通过
                       callback.onContinue(postcard);
                   }
               } catch (Exception e) {
                   callback.onInterrupt(e);
               }
           }
       });
   } else { // 如果没有拦截器 就通过
       callback.onContinue(postcard);
   }
}

/**
* Excute interceptor
*
* @param index    current interceptor index
* @param counter  interceptor counter
* @param postcard routeMeta
*/
private static void _excute(final int index, final CancelableCountDownLatch counter, final Postcard postcard) {
   if (index < Warehouse.interceptors.size()) {
       IInterceptor iInterceptor = Warehouse.interceptors.get(index);
       iInterceptor.process(postcard, new InterceptorCallback() {
           @Override
           public void onContinue(Postcard postcard) {
               // Last interceptor excute over with no exception.
               counter.countDown();
               // 一个拦截器执行好后，执行下一个
               _excute(index + 1, counter, postcard);  // When counter is down, it will be execute continue ,but index bigger than interceptors size, then U know.
           }

           @Override
           public void onInterrupt(Throwable exception) {
               // Last interceptor excute over with fatal exception.

               postcard.setTag(null == exception ? new HandlerException("No message.") : exception.getMessage());    // save the exception message for backup.
               // 如果其中一个拦截器中断的话，就中断整个流程
               counter.cancel();
               // Be attention, maybe the thread in callback has been changed,
               // then the catch block(L207) will be invalid.
               // The worst is the thread changed to main thread, then the app will be crash, if you throw this exception!
//                    if (!Looper.getMainLooper().equals(Looper.myLooper())) {    // You shouldn't throw the exception if the thread is main thread.
//                        throw new HandlerException(exception.getMessage());
//                    }
           }
       });
   }
}
```

上面的代码基本上都加了注释了，这里就不再多讲了。

到这里整个 ARouter 拦截器的流程就差不多讲完了，如果还有哪里不懂的地方可以在评论区留言。

再见👋


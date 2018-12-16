title: EventBus源码解析
date: 2016-12-20 23:46:27
categories: Android Blog
tags: [Android,开源框架,源码解析]
---
0001B
=====
时近年末，但是也没闲着。最近正好在看 [EventBus](https://github.com/greenrobot/EventBus) 的源码。那就正好今天来说说 [EventBus](https://github.com/greenrobot/EventBus) 的那些事儿。

EventBus 是什么呢（相信地球人都知道→_→）？

EventBus is a publish/subscribe event bus optimized for Android.

这是官方给的介绍，简洁、明了、霸气。翻译过来就是：[EventBus](https://github.com/greenrobot/EventBus) 是一种为 Android 而优化设计的发布/订阅事件总线。这官方的套词可能有些人看了还是不懂。。。

![???](/uploads/20161226/20161226232951.jpg)

简单地举了栗子，[EventBus](https://github.com/greenrobot/EventBus) 就好像一辆公交车（快上车，老司机要飙车 乀(ˉεˉ乀) ）。相对应的，发布事件就可以类比为乘客，订阅事件就好似接站服务的人。乘客想要到达指定目的地就必须上车乘坐该公交车，公交车会做统一配置管理每位乘客（发布事件流程）。达到目的地后，打开下车门，把乘客交任给接站服务的人做相应的处理（订阅事件流程）。不知道这个栗子你们懂不懂，反正我是懂了(￣ε ￣)。

![快上车](/uploads/20161226/20170107005159.jpg)

所以总的来说，对于一个事件，你只要关心发送和接收就行了，而其中的收集、分发等都交给 [EventBus](https://github.com/greenrobot/EventBus) 来处理，你不需要做任何事。不得不说这太方便了，能让代码更见简洁，大大降低了模块之间的耦合性。

0002B 使用方法
=============
现在，来看一下 [EventBus](https://github.com/greenrobot/EventBus) 的使用方法，直接复制粘贴 [GitHub](https://github.com/greenrobot/EventBus) 中的例子：

1. 第一步，定义一个事件类 `MessageEvent` :

		public static class MessageEvent { 
			/* Additional fields if needed */ 
		}

2. 定义一个订阅方法，可以使用 `@Subscribe` 注解来指定订阅方法所在的线程：

		@Subscribe(threadMode = ThreadMode.MAIN)  
		public void onMessageEvent(MessageEvent event) {
			/* Do something */
		};

	注册和反注册你的订阅方法。比如在 Android 中，Activity 和 Fragment 通常在如下的生命周期中进行注册和反注册：

		@Override
		public void onStart() {
		    super.onStart();
		    EventBus.getDefault().register(this);
		}
		
		@Override
		public void onStop() {
		    super.onStop();
		    EventBus.getDefault().unregister(this);
		}

3.发送事件：

		EventBus.getDefault().post(new MessageEvent());

可以看出 [EventBus](https://github.com/greenrobot/EventBus) 使用起来很简单，就这么几行代码解决了许多我们备受困扰的问题。那么接下来我们就深入 [EventBus](https://github.com/greenrobot/EventBus) 的源码内部，一探究竟。

0003B EventBus
==============
在 [GitHub](https://github.com/greenrobot/EventBus) 上对于 [EventBus](https://github.com/greenrobot/EventBus) 整体有一张示意图，很明确地画出了整个框架的设计原理：

![EventBus示意图](/uploads/20161226/20170102003651.png)

那么依据这张图，我们先从 “Publisher” 开始讲起吧。PS : 本文分析的 [EventBus](https://github.com/greenrobot/EventBus) 源码版本为 3.0.0 。

EventBus.getDefault()
---------------------
来看一下 `EventBus.getDefault()` 的源码（文件路径：org/greenrobot/eventbus/EventBus.java）：

``` java
private static final EventBusBuilder DEFAULT_BUILDER = new EventBusBuilder();
private final Map<Class<?>, CopyOnWriteArrayList<Subscription>> subscriptionsByEventType;
private final Map<Object, List<Class<?>>> typesBySubscriber;
private final Map<Class<?>, Object> stickyEvents;


public static EventBus getDefault() {
    if (defaultInstance == null) {
        synchronized (EventBus.class) {
            if (defaultInstance == null) {
                defaultInstance = new EventBus();
            }
        }
    }
    return defaultInstance;
}

/**
 * Creates a new EventBus instance; each instance is a separate scope in which events are delivered. To use a
 * central bus, consider {@link #getDefault()}.
 */
public EventBus() {
    this(DEFAULT_BUILDER);
}

EventBus(EventBusBuilder builder) {
    // key 为事件的类型，value 为所有订阅该事件类型的订阅者集合
    subscriptionsByEventType = new HashMap<>();
    // key 为某个订阅者，value 为该订阅者所有的事件类型
    typesBySubscriber = new HashMap<>();
    // 粘性事件的集合，key 为事件的类型，value 为该事件的对象
    stickyEvents = new ConcurrentHashMap<>();
    // 主线程事件发送者
    mainThreadPoster = new HandlerPoster(this, Looper.getMainLooper(), 10);
    // 子线程事件发送者
    backgroundPoster = new BackgroundPoster(this);
    // 异步线程事件发送者
    asyncPoster = new AsyncPoster(this);
    // 索引类的数量
    indexCount = builder.subscriberInfoIndexes != null ? builder.subscriberInfoIndexes.size() : 0;
    // 订阅方法查找者
    subscriberMethodFinder = new SubscriberMethodFinder(builder.subscriberInfoIndexes,
            builder.strictMethodVerification, builder.ignoreGeneratedIndex);
    // 是否打印订阅者异常的日志，默认为 true
    logSubscriberExceptions = builder.logSubscriberExceptions;
    // 是否打印没有订阅者的异常日志，默认为 true
    logNoSubscriberMessages = builder.logNoSubscriberMessages;
    // 是否允许发送 SubscriberExceptionEvent ，默认为 true
    sendSubscriberExceptionEvent = builder.sendSubscriberExceptionEvent;
    // 是否允许发送 sendNoSubscriberEvent ，默认为 true
    sendNoSubscriberEvent = builder.sendNoSubscriberEvent;
    // 是否允许抛出订阅者的异常，默认是 false
    throwSubscriberException = builder.throwSubscriberException;
    // 是否支持事件继承，默认是 true
    eventInheritance = builder.eventInheritance;
    // 创建线程池
    executorService = builder.executorService;
}
```

从上面的源码中可以看出，平时的我们经常调用的 `EventBus.getDefault()` 代码，其实是获取了 `EventBus` 类的单例。若该单例未实例化，那么会根据 `DEFAULT_BUILDER` 采用构造者模式去实例化该单例。在 `EventBus` 构造器中初始化了一堆的成员变量，这些都会在下面中使用到。

register(Object subscriber)
---------------------------
事件订阅者必须调用 `register(Object subscriber)` 方法来进行注册，一起来看看在 `register(Object subscriber)` 中到底做了一些什么：

``` java
public void register(Object subscriber) {
    // 得到订阅者的类 class
    Class<?> subscriberClass = subscriber.getClass();
    // 找到该 class 下所有的订阅方法
    List<SubscriberMethod> subscriberMethods = subscriberMethodFinder.findSubscriberMethods(subscriberClass);
    synchronized (this) {
        for (SubscriberMethod subscriberMethod : subscriberMethods) {		
            subscribe(subscriber, subscriberMethod);
        }
    }
}
```

在 `register(Object subscriber)` 中，利用 `subscriberMethodFinder.findSubscriberMethods` 方法找到订阅者 class 下所有的订阅方法，然后用 `for` 循环建立订阅关系。其中 `subscriberMethodFinder.findSubscriberMethods` 方法我们暂时先不看了，跳过。在这里只要知道作用是找到该订阅者所有的订阅方法就好了。具体 `SubscriberMethodFinder` 的代码会在后面的章节中详细分析。

而 `SubscriberMethod` 其实就是订阅方法的包装类：

``` java
public class SubscriberMethod {
    // 订阅的方法
    final Method method;
    // 订阅所在的线程
    final ThreadMode threadMode;
    // 订阅事件的类型
    final Class<?> eventType;
    // 优先级
    final int priority;
    // 订阅是否是粘性的
    final boolean sticky;
    // 特定字符串，用来比较两个 SubscriberMethod 是否为同一个
    String methodString;
    ...

}
```

然后就是轮到了 `subscribe(subscriber, subscriberMethod)` 方法：

``` java
private void subscribe(Object subscriber, SubscriberMethod subscriberMethod) {
    // 得到订阅方法的事件类型
    Class<?> eventType = subscriberMethod.eventType;

    Subscription newSubscription = new Subscription(subscriber, subscriberMethod);
    // 根据订阅方法的事件类型得到所有订阅该事件类型的订阅者集合
    CopyOnWriteArrayList<Subscription> subscriptions = subscriptionsByEventType.get(eventType);
    if (subscriptions == null) {
        subscriptions = new CopyOnWriteArrayList<>();
        subscriptionsByEventType.put(eventType, subscriptions);
    } else {
        // 如果 subscriptions 已经包含了，抛出异常
        if (subscriptions.contains(newSubscription)) {
            throw new EventBusException("Subscriber " + subscriber.getClass() + " already registered to event "
                    + eventType);
        }
    }
    // 根据该 subscriberMethod 优先级插入到 subscriptions 中
    int size = subscriptions.size();
    for (int i = 0; i <= size; i++) {
        if (i == size || subscriberMethod.priority > subscriptions.get(i).subscriberMethod.priority) {
            subscriptions.add(i, newSubscription);
            break;
        }
    }
    // 放入 subscribedEvents 中，key：订阅者  value：该订阅者的所有订阅事件的类型
    List<Class<?>> subscribedEvents = typesBySubscriber.get(subscriber);
    if (subscribedEvents == null) {
        subscribedEvents = new ArrayList<>();
        typesBySubscriber.put(subscriber, subscribedEvents);
    }
    subscribedEvents.add(eventType);
    // 如果订阅的方法支持 sticky
    if (subscriberMethod.sticky) {
        // 如果支持事件继承
        if (eventInheritance) {
            // Existing sticky events of all subclasses of eventType have to be considered.
            // Note: Iterating over all events may be inefficient with lots of sticky events,
            // thus data structure should be changed to allow a more efficient lookup
            // (e.g. an additional map storing sub classes of super classes: Class -> List<Class>).
            Set<Map.Entry<Class<?>, Object>> entries = stickyEvents.entrySet();
            // 遍历 stickyEvents
            for (Map.Entry<Class<?>, Object> entry : entries) {
                Class<?> candidateEventType = entry.getKey();
                // 判断 eventType 类型是否是 candidateEventType 的父类
                if (eventType.isAssignableFrom(candidateEventType)) {
                    // 得到对应 eventType 的子类事件，类型为 candidateEventType
                    Object stickyEvent = entry.getValue();
                    // 发送粘性事件给 newSubscription
                    checkPostStickyEventToSubscription(newSubscription, stickyEvent);
                }
            }
        } else {
            // 拿到之前 sticky 的事件，然后发送给 newSubscription
            Object stickyEvent = stickyEvents.get(eventType);
            // 发送粘性事件给 newSubscription
            checkPostStickyEventToSubscription(newSubscription, stickyEvent);
        }
    }
}
```

其实 `subscribe(subscriber, subscriberMethod)` 方法主要就做了三件事：

1. 得到 `subscriptions` ，然后根据优先级把 `subscriberMethod` 插入到 `subscriptions` 中；
2. 将 `eventType` 放入到 `subscribedEvents` 中；
3. 如果订阅方法支持 `sticky` ，那么发送相关的粘性事件。

粘性事件发送调用了 `checkPostStickyEventToSubscription(newSubscription, stickyEvent);` 。从方法的命名上来看，知道应该是事件发送到订阅者相关的代码。那么继续跟进代码：

``` java
private void checkPostStickyEventToSubscription(Subscription newSubscription, Object stickyEvent) {
    if (stickyEvent != null) {
        // If the subscriber is trying to abort the event, it will fail (event is not tracked in posting state)
        // --> Strange corner case, which we don't take care of here.
        postToSubscription(newSubscription, stickyEvent, Looper.getMainLooper() == Looper.myLooper());
    }
}

private void postToSubscription(Subscription subscription, Object event, boolean isMainThread) {
    // 根据不同的线程模式执行对应
    switch (subscription.subscriberMethod.threadMode) {
        case POSTING: // 和发送事件处于同一个线程
            invokeSubscriber(subscription, event);
            break;
        case MAIN: // 主线程
            if (isMainThread) {
                invokeSubscriber(subscription, event);
            } else {
                mainThreadPoster.enqueue(subscription, event);
            }
            break;
        case BACKGROUND: // 子线程
            if (isMainThread) {
                backgroundPoster.enqueue(subscription, event);
            } else {
                invokeSubscriber(subscription, event);
            }
            break;
        case ASYNC: // 和发送事件处于不同的线程
            asyncPoster.enqueue(subscription, event);
            break;
        default: // 抛出异常
            throw new IllegalStateException("Unknown thread mode: " + subscription.subscriberMethod.threadMode);
    }
}

void invokeSubscriber(Subscription subscription, Object event) {
    try {
        // 通过反射执行订阅方法
        subscription.subscriberMethod.method.invoke(subscription.subscriber, event);
    } catch (InvocationTargetException e) {
        handleSubscriberException(subscription, event, e.getCause());
    } catch (IllegalAccessException e) {
        throw new IllegalStateException("Unexpected exception", e);
    }
}
```

在 `checkPostStickyEventToSubscription(Subscription newSubscription, Object stickyEvent)` 方法的内部调用了 `postToSubscription(Subscription subscription, Object event, boolean isMainThread)` 。主要的操作都在 `postToSubscription` 中。根据 `threadMode` 共分为四种：

1. 同一个线程：表示订阅方法所处的线程和发布事件的线程是同一个线程；
2. 主线程：如果发布事件的线程是主线程，那么直接执行订阅方法；否则利用 Handler 回调主线程来执行；
3. 子线程：如果发布事件的线程是主线程，那么调用线程池中的子线程来执行订阅方法；否则直接执行；
4. 异步线程：无论发布事件执行在主线程还是子线程，都利用一个异步线程来执行订阅方法。

这四种线程模式其实最后都会调用 `invokeSubscriber(Subscription subscription, Object event)` 方法通过反射来执行。至此，关于粘性事件的发送就告一段落了。

另外，在这里因篇幅原因就不对 `mainThreadPoster` 和 `backgroundPoster` 等细说了，可以自行回去看相关源码，比较简单。

unregister(Object subscriber)
-----------------------------
看完 `register(Object subscriber)` ，接下来顺便看看 `unregister(Object subscriber)` 的源码：

``` java
public synchronized void unregister(Object subscriber) {
    // 通过 subscriber 来找到 subscribedTypes
    List<Class<?>> subscribedTypes = typesBySubscriber.get(subscriber);
    if (subscribedTypes != null) {
        for (Class<?> eventType : subscribedTypes) {
            // 解除每个订阅的事件类型
            unsubscribeByEventType(subscriber, eventType);
        }
        // 从 typesBySubscriber 中移除
        typesBySubscriber.remove(subscriber);
    } else {
        Log.w(TAG, "Subscriber to unregister was not registered before: " + subscriber.getClass());
    }
}

private void unsubscribeByEventType(Object subscriber, Class<?> eventType) {
    List<Subscription> subscriptions = subscriptionsByEventType.get(eventType);
    if (subscriptions != null) {
        int size = subscriptions.size();
        for (int i = 0; i < size; i++) {
            Subscription subscription = subscriptions.get(i);
            if (subscription.subscriber == subscriber) {
                subscription.active = false;
                subscriptions.remove(i);
                i--;
                size--;
            }
        }
    }
}		
```

瞟了一眼 `unregister(Object subscriber)` 方法，我们基本上就已经知道其中做了什么。在之前 `register(Object subscriber)` 中 `subscriptionsByEventType` 和 `typesBySubscriber` 会对 `subscriber` 间接进行绑定。而在 `unregister(Object subscriber)` 会对其解绑，这样就防止了造成内存泄露的危险。

post(Object event)
------------------
最后，我们来分析下发送事件 `post(Object event)` 的源码：

``` java
private final ThreadLocal<PostingThreadState> currentPostingThreadState = new ThreadLocal<PostingThreadState>() {
    @Override
    protected PostingThreadState initialValue() {
        return new PostingThreadState();
    }
};

public void post(Object event) {
    // 得到当前线程的 postingState
    PostingThreadState postingState = currentPostingThreadState.get();
    // 加入到队列中
    List<Object> eventQueue = postingState.eventQueue;
    eventQueue.add(event);
    // 如果没有持续在发送事件，那么开始发送事件并一直保持发送ing
    if (!postingState.isPosting) {
        postingState.isMainThread = Looper.getMainLooper() == Looper.myLooper();
        postingState.isPosting = true;
        if (postingState.canceled) {
            throw new EventBusException("Internal error. Abort state was not reset");
        }
        try {
            while (!eventQueue.isEmpty()) {
                // 发送单个事件
                postSingleEvent(eventQueue.remove(0), postingState);
            }
        } finally {
            postingState.isPosting = false;
            postingState.isMainThread = false;
        }
    }
}
```

在 `post(Object event)` 中，首先根据 `currentPostingThreadState` 获取当前线程状态 `postingState` 。`currentPostingThreadState` 其实就是一个 `ThreadLocal` 类的对象，不同的线程根据自己独有的索引值可以得到相应属于自己的 `postingState` 数据。

然后把事件 `event` 加入到 `eventQueue` 队列中排队。只要 `eventQueue` 不为空，就不间断地发送事件。而发送单个事件的代码在 `postSingleEvent(Object event, PostingThreadState postingState)` 中，我们跟进去看：

``` java
private void postSingleEvent(Object event, PostingThreadState postingState) throws Error {
    // 得到事件的类型
    Class<?> eventClass = event.getClass();
    // 是否找到订阅者
    boolean subscriptionFound = false;
    // 如果支持事件继承
    if (eventInheritance) {
        // 查找 eventClass 的所有父类和接口
        List<Class<?>> eventTypes = lookupAllEventTypes(eventClass);
        int countTypes = eventTypes.size();
        for (int h = 0; h < countTypes; h++) {
            Class<?> clazz = eventTypes.get(h);
            // 依次向订阅方法类型为 eventClass 的父类或接口的发送事件
            // 只要其中有一个 postSingleEventForEventType 返回 true ，那么 subscriptionFound 就为 true
            subscriptionFound |= postSingleEventForEventType(event, postingState, clazz);
        }
    } else {
        // 发送事件
        subscriptionFound = postSingleEventForEventType(event, postingState, eventClass);
    }
    // 如果没有订阅者
    if (!subscriptionFound) {
        if (logNoSubscriberMessages) {
            Log.d(TAG, "No subscribers registered for event " + eventClass);
        }
        if (sendNoSubscriberEvent && eventClass != NoSubscriberEvent.class &&
                eventClass != SubscriberExceptionEvent.class) {
            // 发送 NoSubscriberEvent 事件，可以自定义接收
            post(new NoSubscriberEvent(this, event));
        }
    }
}
```

`postSingleEvent(Object event, PostingThreadState postingState)` 中的代码逻辑还是比较清晰的，会根据 `eventInheritance` 分成两种：

1. 支持事件继承：得到 `eventClass` 的所有父类和接口，然后循环依次发送事件；
2. 不支持事件继承：直接发送事件。

另外，若找不到订阅者，在默认配置下还会发送 `NoSubscriberEvent` 事件。需要开发者自定义订阅方法接收这个事件。

关于发送的具体操作还是要到 `postSingleEventForEventType(Object event, PostingThreadState postingState, Class<?> eventClass)` 中去看：

``` java
private boolean postSingleEventForEventType(Object event, PostingThreadState postingState, Class<?> eventClass) {
    CopyOnWriteArrayList<Subscription> subscriptions;
    synchronized (this) {
        // 得到订阅者
        subscriptions = subscriptionsByEventType.get(eventClass);
    }
    if (subscriptions != null && !subscriptions.isEmpty()) {
        // 依次遍历订阅者
        for (Subscription subscription : subscriptions) {
            postingState.event = event;
            postingState.subscription = subscription;
            boolean aborted = false;
            try {
                // 发送事件
                postToSubscription(subscription, event, postingState.isMainThread);
                // 是否被取消了
                aborted = postingState.canceled;
            } finally {
                postingState.event = null;
                postingState.subscription = null;
                postingState.canceled = false;
            }
            // 如果被取消，则跳出循环
            if (aborted) {
                break;
            }
        }
        return true;
    }
    return false;
}
```

仔细看上面的代码，我们应该能发现一个重要的线索—— `postToSubscription` 。没错，就是上面讲解发送粘性事件中的 `postToSubscription` 方法。神奇地绕了一圈又绕回来了。

而 `postSingleEventForEventType` 方法做的事情只不过是遍历了订阅者，然后一个个依次调用 `postToSubscription` 方法，之后就是进入 `switch` 四种线程模式（`POSTING` 、`MAIN` 、`BACKGROUND` 和 `ASYNC`）并执行订阅者的订阅方法的逻辑了。这里就不重复讲了，具体可以查看上面发送粘性事件中的分析。

至此，整个 [EventBus](https://github.com/greenrobot/EventBus) 发布/订阅的原理就讲完了。[EventBus](https://github.com/greenrobot/EventBus) 是一款典型的运行观察者模式的开源框架，设计巧妙，代码也通俗易懂，值得我们学习。

别以为到这里就本文结束了，可不要忘了，在前面我们还留下一个坑没填—— `SubscriberMethodFinder` 。想不想知道 `SubscriberMethodFinder` 到底是如何工作的呢？那还等什么，我们赶快进入下一章节。

0004B SubscriberMethodFinder
============================
`SubscriberMethodFinder` 的作用说白了其实就是寻找订阅者的订阅方法。正如在上面的代码中提到的那样， `findSubscriberMethods` 方法可以返回指定订阅者中的所有订阅方法。

findSubscriberMethods(Class<?> subscriberClass)
-----------------------------------------------
我们看下内部的源码（文件路径：org/greenrobot/eventbus/SubscriberMethodFinder.java）：

``` java
List<SubscriberMethod> findSubscriberMethods(Class<?> subscriberClass) {
    // 先从缓存中获取
    List<SubscriberMethod> subscriberMethods = METHOD_CACHE.get(subscriberClass);
    if (subscriberMethods != null) {
        return subscriberMethods;
    }

    if (ignoreGeneratedIndex) {
        // 如果忽略索引，就根据反射来获取
        subscriberMethods = findUsingReflection(subscriberClass);
    } else {
        // 否则使用索引
        subscriberMethods = findUsingInfo(subscriberClass);
    }
    if (subscriberMethods.isEmpty()) {
        throw new EventBusException("Subscriber " + subscriberClass
                + " and its super classes have no public methods with the @Subscribe annotation");
    } else {
        // 放入缓存中
        METHOD_CACHE.put(subscriberClass, subscriberMethods);
        return subscriberMethods;
    }
}
```

内部有两种途径获取：`findUsingReflection(Class<?> subscriberClass)` 和 `findUsingInfo(Class<?> subscriberClass)` 。另外，还有缓存可以提高索引效率。

findUsingReflection(Class<?> subscriberClass)
---------------------------------------------
那么我们先来看看 `findUsingReflection(Class<?> subscriberClass)` 方法：

``` java
private List<SubscriberMethod> findUsingReflection(Class<?> subscriberClass) {
    FindState findState = prepareFindState();
    // 做初始化操作
    findState.initForSubscriber(subscriberClass);
    while (findState.clazz != null) {
        // 通过反射查找订阅方法
        findUsingReflectionInSingleClass(findState);
        // 查找 clazz 的父类
        findState.moveToSuperclass();
    }
    // 返回 findState 中的 subscriberMethods
    return getMethodsAndRelease(findState);
}
```

这里出现一个新的类 `FindState` ，而 `FindState` 的作用可以对订阅方法做一些校验，以及查找到的所有订阅方法也是封装在 `FindState.subscriberMethods` 中的。另外，在 `SubscriberMethodFinder` 类内部还维持着一个 `FIND_STATE_POOL` ，可以循环利用，节省内存。

接着往下看，就发现了一个关键的方法： `findUsingReflectionInSingleClass(FindState findState)` 。根据这方法名可以知道反射获取订阅方法的操作就在这儿：

``` java
private void findUsingReflectionInSingleClass(FindState findState) {
    Method[] methods;
    try {
        // This is faster than getMethods, especially when subscribers are fat classes like Activities
        methods = findState.clazz.getDeclaredMethods();
    } catch (Throwable th) {
        // Workaround for java.lang.NoClassDefFoundError, see https://github.com/greenrobot/EventBus/issues/149
        methods = findState.clazz.getMethods();
        findState.skipSuperClasses = true;
    }
    for (Method method : methods) {
        int modifiers = method.getModifiers();
        // 方法的修饰符只能为 public 并且不能是 static 和 abstract
        if ((modifiers & Modifier.PUBLIC) != 0 && (modifiers & MODIFIERS_IGNORE) == 0) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            // 订阅方法的参数只能有一个
            if (parameterTypes.length == 1) {
                // 得到 @Subscribe 注解，如果注解不为空那就认为是订阅方法
                Subscribe subscribeAnnotation = method.getAnnotation(Subscribe.class);
                if (subscribeAnnotation != null) {
                    Class<?> eventType = parameterTypes[0];
                    // 将该 method 做校验
                    if (findState.checkAdd(method, eventType)) {
                        // 解析 @Subscribe 注解中的 threadMode
                        ThreadMode threadMode = subscribeAnnotation.threadMode();
                        // 加入到 findState.subscriberMethods 中
                        findState.subscriberMethods.add(new SubscriberMethod(method, eventType, threadMode,
                                subscribeAnnotation.priority(), subscribeAnnotation.sticky()));
                    }
                }
            } else if (strictMethodVerification && method.isAnnotationPresent(Subscribe.class)) {
                String methodName = method.getDeclaringClass().getName() + "." + method.getName();
                throw new EventBusException("@Subscribe method " + methodName +
                        "must have exactly 1 parameter but has " + parameterTypes.length);
            }
        } else if (strictMethodVerification && method.isAnnotationPresent(Subscribe.class)) {
            String methodName = method.getDeclaringClass().getName() + "." + method.getName();
            throw new EventBusException(methodName +
                    " is a illegal @Subscribe method: must be public, non-static, and non-abstract");
        }
    }
}
```

通过一个个循环订阅者中的方法，筛选得到其中的订阅方法后，保存在 `findState.subscriberMethods` 中。最后在 `getMethodsAndRelease(FindState findState)` 方法中把 `findState.subscriberMethods` 返回。（这里就不对 `getMethodsAndRelease(FindState findState)` 做解析了，可以下去自己看代码，比较简单 \*^ο^\* ）

findUsingInfo(Class<?> subscriberClass)
---------------------------------------
最后，剩下另外一种获取订阅方法的途径还没讲。

``` java
private List<SubscriberMethod> findUsingInfo(Class<?> subscriberClass) {
    FindState findState = prepareFindState();
    findState.initForSubscriber(subscriberClass);
    while (findState.clazz != null) {
        findState.subscriberInfo = getSubscriberInfo(findState);
        if (findState.subscriberInfo != null) {
            // 直接获取 subscriberInfo 中的 SubscriberMethods
            SubscriberMethod[] array = findState.subscriberInfo.getSubscriberMethods();
            for (SubscriberMethod subscriberMethod : array) {
                if (findState.checkAdd(subscriberMethod.method, subscriberMethod.eventType)) {
                    findState.subscriberMethods.add(subscriberMethod);
                }
            }
        } else {
            // 如果 subscriberInfo 没有，就通过反射的方式
            findUsingReflectionInSingleClass(findState);
        }
        findState.moveToSuperclass();
    }
    return getMethodsAndRelease(findState);
}

private SubscriberInfo getSubscriberInfo(FindState findState) {
    if (findState.subscriberInfo != null && findState.subscriberInfo.getSuperSubscriberInfo() != null) {
        SubscriberInfo superclassInfo = findState.subscriberInfo.getSuperSubscriberInfo();
        if (findState.clazz == superclassInfo.getSubscriberClass()) {
            return superclassInfo;
        }
    }
    if (subscriberInfoIndexes != null) {
        // 使用 SubscriberInfoIndex 来获取 SubscriberInfo
        for (SubscriberInfoIndex index : subscriberInfoIndexes) {
            SubscriberInfo info = index.getSubscriberInfo(findState.clazz);
            if (info != null) {
                return info;
            }
        }
    }
    return null;
}
```

我们发现在 `findUsingInfo(Class<?> subscriberClass)` 中是通过 `SubscriberInfo` 类来获取订阅方法的；如果没有 `SubscriberInfo` ，就直接通过反射的形式来获取。那么 `SubscriberInfo` 又是如何得到的呢？还要继续跟踪到 `getSubscriberInfo(FindState findState)` 方法中。然后又有一个新的类蹦出来—— `SubscriberInfoIndex` 。那么 `SubscriberInfoIndex` 又是什么东东啊（文件路径：org/greenrobot/eventbus/meta/SubscriberInfoIndex.java）？

``` java
public interface SubscriberInfoIndex {
    SubscriberInfo getSubscriberInfo(Class<?> subscriberClass);
}
```

点进去后发现 `SubscriberInfoIndex` 只是一个接口而已，是不是感到莫名其妙。What the hell is it!

我们把这个疑问先放在心里，到 `EventBusPerformance` 这个 module 中，进入 build/generated/source/apt/debug/org/greenrobot/eventbusperf 目录下，发现有一个类叫 `MyEventBusIndex` ：

``` java
/** This class is generated by EventBus, do not edit. */
public class MyEventBusIndex implements SubscriberInfoIndex {
    private static final Map<Class<?>, SubscriberInfo> SUBSCRIBER_INDEX;

    static {
        SUBSCRIBER_INDEX = new HashMap<Class<?>, SubscriberInfo>();

        putIndex(new SimpleSubscriberInfo(org.greenrobot.eventbusperf.testsubject.SubscribeClassEventBusDefault.class,
                true, new SubscriberMethodInfo[] {
            new SubscriberMethodInfo("onEvent", TestEvent.class),
        }));

        putIndex(new SimpleSubscriberInfo(TestRunnerActivity.class, true, new SubscriberMethodInfo[] {
            new SubscriberMethodInfo("onEventMainThread", TestFinishedEvent.class, ThreadMode.MAIN),
        }));

        putIndex(new SimpleSubscriberInfo(org.greenrobot.eventbusperf.testsubject.PerfTestEventBus.SubscriberClassEventBusAsync.class,
                true, new SubscriberMethodInfo[] {
            new SubscriberMethodInfo("onEventAsync", TestEvent.class, ThreadMode.ASYNC),
        }));

        putIndex(new SimpleSubscriberInfo(org.greenrobot.eventbusperf.testsubject.PerfTestEventBus.SubscribeClassEventBusBackground.class,
                true, new SubscriberMethodInfo[] {
            new SubscriberMethodInfo("onEventBackgroundThread", TestEvent.class, ThreadMode.BACKGROUND),
        }));

        putIndex(new SimpleSubscriberInfo(org.greenrobot.eventbusperf.testsubject.PerfTestEventBus.SubscribeClassEventBusMain.class,
                true, new SubscriberMethodInfo[] {
            new SubscriberMethodInfo("onEventMainThread", TestEvent.class, ThreadMode.MAIN),
        }));

    }

    private static void putIndex(SubscriberInfo info) {
        SUBSCRIBER_INDEX.put(info.getSubscriberClass(), info);
    }

    @Override
    public SubscriberInfo getSubscriberInfo(Class<?> subscriberClass) {
        SubscriberInfo info = SUBSCRIBER_INDEX.get(subscriberClass);
        if (info != null) {
            return info;
        } else {
            return null;
        }
    }
}
```

从代码中可知，`MyEventBusIndex` 其实是 `SubscriberInfoIndex` 的实现类，并且是 [EventBus](https://github.com/greenrobot/EventBus) 自动生成的（根据注释可知这点）。而 `getSubscriberInfo(Class<?> subscriberClass)` 方法已经实现了，内部维持着一个 `SUBSCRIBER_INDEX` 的 `HashMap` ，用来保存订阅类的相关信息 `info` 。然后在需要的时候可以通过 `info` 快速返回 `SubscriberMethod` 。这样就达到了不用反射获取订阅方法的目的，提高了执行效率。

到了这里我们明白了上面关于 `SubscriberInfoIndex` 的疑问，但是又有一个新的疑问产生了：`MyEventBusIndex` 到底是如何生成的？想要解开这个疑问，我们就要去 `EventBusAnnotationProcessor` 类中寻找答案了。

0005B EventBusAnnotationProcessor
=================================
一看到 `EventBusAnnotationProcessor` ，菊花一紧，料想肯定逃不了注解。我们可以猜出个大概： [EventBus](https://github.com/greenrobot/EventBus) 在编译时通过 `EventBusAnnotationProcessor` 寻找到所有标有 `@Subscribe` 注解的订阅方法，然后依据这些订阅方法自动生成像 `MyEventBusIndex` 一样的索引类代码，以此提高索引效率。

总体来说，这种注解的思路和 [Dagger](https://github.com/square/dagger) 、[ButterKnife](https://github.com/JakeWharton/butterknife) 等框架类似。想要了更多，可以阅读我的上一篇博客[《ButterKnife源码分析》](/2016/12/18/ButterKnife源码解析/)。

在这里由于篇幅的原因只能简单粗略地解析 `EventBusAnnotationProcessor` 的源码了，还请多多谅解。

process(Set<?extendsTypeElement> annotations, RoundEnvironment env)
---------------------------------------------------------------------
我们简单地来分析一下 `process(Set<? extends TypeElement> annotations, RoundEnvironment env)` ：

``` java
@Override
public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment env) {
    Messager messager = processingEnv.getMessager();
    try {

        ... // 省略一堆代码
        // 根据 @Subscribe 的注解得到所有订阅方法
        collectSubscribers(annotations, env, messager);
        // 校验这些订阅方法，过滤掉不符合的
        checkForSubscribersToSkip(messager, indexPackage);

        if (!methodsByClass.isEmpty()) {
            // 生成索引类，比如 MyEventBusIndex
            createInfoIndexFile(index);
        } else {
            messager.printMessage(Diagnostic.Kind.WARNING, "No @Subscribe annotations found");
        }
        writerRoundDone = true;
    } catch (RuntimeException e) {
        // IntelliJ does not handle exceptions nicely, so log and print a message
        e.printStackTrace();
        messager.printMessage(Diagnostic.Kind.ERROR, "Unexpected error in EventBusAnnotationProcessor: " + e);
    }
    return true;
}
```

其实在 `process(Set<? extends TypeElement> annotations, RoundEnvironment env)` 方法中重要的代码就这么几行，其他不重要的代码都省略了。那现在我们顺着一个一个方法来看。

collectSubscribers(Set<?extendsTypeElement> annotations, RoundEnvironment env, Messager messager)
---------------------------------------------------------------------------
我们先从 `collectSubscribers(annotations, env, messager);` 开始入手：

``` java
private void collectSubscribers(Set<? extends TypeElement> annotations, RoundEnvironment env, Messager messager) {
    for (TypeElement annotation : annotations) {
        // 根据注解去获得 elements
        Set<? extends Element> elements = env.getElementsAnnotatedWith(annotation);
        for (Element element : elements) {
            if (element instanceof ExecutableElement) {
                ExecutableElement method = (ExecutableElement) element;
                if (checkHasNoErrors(method, messager)) {
                    TypeElement classElement = (TypeElement) method.getEnclosingElement();
                    // 添加该订阅方法
                    methodsByClass.putElement(classElement, method);
                }
            } else {
                messager.printMessage(Diagnostic.Kind.ERROR, "@Subscribe is only valid for methods", element);
            }
        }
    }
}

private boolean checkHasNoErrors(ExecutableElement element, Messager messager) {
    // 方法不能是 static 的
    if (element.getModifiers().contains(Modifier.STATIC)) {
        messager.printMessage(Diagnostic.Kind.ERROR, "Subscriber method must not be static", element);
        return false;
    }
    // 方法要是 public 的
    if (!element.getModifiers().contains(Modifier.PUBLIC)) {
        messager.printMessage(Diagnostic.Kind.ERROR, "Subscriber method must be public", element);
        return false;
    }
    // 参数只能有一个
    List<? extends VariableElement> parameters = ((ExecutableElement) element).getParameters();
    if (parameters.size() != 1) {
        messager.printMessage(Diagnostic.Kind.ERROR, "Subscriber method must have exactly 1 parameter", element);
        return false;
    }
    return true;
}
```

上面代码做的事情就是根据注解获取了对应的方法，然后初步筛选了一些方法，放入 `methodsByClass` 中。

checkForSubscribersToSkip(Messager messager, String myPackage)
--------------------------------------------------------------
得到这些初选的订阅方法后，就要进入 `checkForSubscribersToSkip(Messager messager, String myPackage)` 环节：

``` java
private void checkForSubscribersToSkip(Messager messager, String myPackage) {
    for (TypeElement skipCandidate : methodsByClass.keySet()) {
        TypeElement subscriberClass = skipCandidate;
        while (subscriberClass != null) {
            // 如果该订阅类是 public 的，可以通过
            // 如果该订阅类是 private 或者 protected 的，会被加入到 classesToSkip 中
            // 如果该订阅类是默认修饰符，但是订阅类的包和索引类的包不是同一个包，会被加入到 classesToSkip 中
            if (!isVisible(myPackage, subscriberClass)) {
                boolean added = classesToSkip.add(skipCandidate);
                if (added) {
                    String msg;
                    if (subscriberClass.equals(skipCandidate)) {
                        msg = "Falling back to reflection because class is not public";
                    } else {
                        msg = "Falling back to reflection because " + skipCandidate +
                                " has a non-public super class";
                    }
                    messager.printMessage(Diagnostic.Kind.NOTE, msg, subscriberClass);
                }
                break;
            }
            List<ExecutableElement> methods = methodsByClass.get(subscriberClass);
            if (methods != null) {
                // 校验订阅方法是否合格
                for (ExecutableElement method : methods) {
                    String skipReason = null;
                    VariableElement param = method.getParameters().get(0);
                    TypeMirror typeMirror = getParamTypeMirror(param, messager);
                    if (!(typeMirror instanceof DeclaredType) ||
                            !(((DeclaredType) typeMirror).asElement() instanceof TypeElement)) {
                        skipReason = "event type cannot be processed";
                    }
                    if (skipReason == null) {
                        TypeElement eventTypeElement = (TypeElement) ((DeclaredType) typeMirror).asElement();
                        if (!isVisible(myPackage, eventTypeElement)) {
                            skipReason = "event type is not public";
                        }
                    }
                    if (skipReason != null) {
                        boolean added = classesToSkip.add(skipCandidate);
                        if (added) {
                            String msg = "Falling back to reflection because " + skipReason;
                            if (!subscriberClass.equals(skipCandidate)) {
                                msg += " (found in super class for " + skipCandidate + ")";
                            }
                            messager.printMessage(Diagnostic.Kind.NOTE, msg, param);
                        }
                        break;
                    }
                }
            }
            // 查找父类
            subscriberClass = getSuperclass(subscriberClass);
        }
    }
}
```

用一句话来概括，`checkForSubscribersToSkip(Messager messager, String myPackage)` 做的事情就是如果这些订阅类中牵扯到不可见状态，那么就会被加入到 `classesToSkip` 中，导致后面生成索引类中跳过这些订阅类。

createInfoIndexFile(String index)
---------------------------------
经过筛选后，`EventBusAnnotationProcessor` 最终要生成一个索引类，具体的代码就在 `createInfoIndexFile(String index)` 中：


``` java
private void createInfoIndexFile(String index) {
    BufferedWriter writer = null;
    try {
        JavaFileObject sourceFile = processingEnv.getFiler().createSourceFile(index);
        int period = index.lastIndexOf('.');
        String myPackage = period > 0 ? index.substring(0, period) : null;
        String clazz = index.substring(period + 1);
        writer = new BufferedWriter(sourceFile.openWriter());
        // 下面都是自动生成的代码
        if (myPackage != null) {
            writer.write("package " + myPackage + ";\n\n");
        }
        writer.write("import org.greenrobot.eventbus.meta.SimpleSubscriberInfo;\n");
        writer.write("import org.greenrobot.eventbus.meta.SubscriberMethodInfo;\n");
        writer.write("import org.greenrobot.eventbus.meta.SubscriberInfo;\n");
        writer.write("import org.greenrobot.eventbus.meta.SubscriberInfoIndex;\n\n");
        writer.write("import org.greenrobot.eventbus.ThreadMode;\n\n");
        writer.write("import java.util.HashMap;\n");
        writer.write("import java.util.Map;\n\n");
        writer.write("/** This class is generated by EventBus, do not edit. */\n");
        writer.write("public class " + clazz + " implements SubscriberInfoIndex {\n");
        writer.write("    private static final Map<Class<?>, SubscriberInfo> SUBSCRIBER_INDEX;\n\n");
        writer.write("    static {\n");
        writer.write("        SUBSCRIBER_INDEX = new HashMap<Class<?>, SubscriberInfo>();\n\n");
        writeIndexLines(writer, myPackage);
        writer.write("    }\n\n");
        writer.write("    private static void putIndex(SubscriberInfo info) {\n");
        writer.write("        SUBSCRIBER_INDEX.put(info.getSubscriberClass(), info);\n");
        writer.write("    }\n\n");
        writer.write("    @Override\n");
        writer.write("    public SubscriberInfo getSubscriberInfo(Class<?> subscriberClass) {\n");
        writer.write("        SubscriberInfo info = SUBSCRIBER_INDEX.get(subscriberClass);\n");
        writer.write("        if (info != null) {\n");
        writer.write("            return info;\n");
        writer.write("        } else {\n");
        writer.write("            return null;\n");
        writer.write("        }\n");
        writer.write("    }\n");
        writer.write("}\n");
    } catch (IOException e) {
        throw new RuntimeException("Could not write source for " + index, e);
    } finally {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                //Silent
            }
        }
    }
}

private void writeIndexLines(BufferedWriter writer, String myPackage) throws IOException {
    for (TypeElement subscriberTypeElement : methodsByClass.keySet()) {
        // 如果是被包含在 classesToSkip 中的，就跳过
        if (classesToSkip.contains(subscriberTypeElement)) {
            continue;
        }
        // 生成对应的 index
        String subscriberClass = getClassString(subscriberTypeElement, myPackage);
        if (isVisible(myPackage, subscriberTypeElement)) {
            writeLine(writer, 2,
                    "putIndex(new SimpleSubscriberInfo(" + subscriberClass + ".class,",
                    "true,", "new SubscriberMethodInfo[] {");
            List<ExecutableElement> methods = methodsByClass.get(subscriberTypeElement);
            writeCreateSubscriberMethods(writer, methods, "new SubscriberMethodInfo", myPackage);
            writer.write("        }));\n\n");
        } else {
            writer.write("        // Subscriber not visible to index: " + subscriberClass + "\n");
        }
    }
}
```

上面的这几行代码应该很眼熟吧，`MyEventBusIndex` 就是从这个模子里“刻”出来的，都是写死的代码。不同的是在 `writeIndexLines(BufferedWriter writer, String myPackage)` 中会把之前包含在 `classesToSkip` 里的跳过，其他的都自动生成 index 。最后就能得到一个像 `MyEventBusIndex` 一样的索引类了。

另外补充一句，如果你想使用像 `MyEventBusIndex` 一样的索引类，需要在初始化 `EventBus` 时通过 `EventBus.builder().addIndex(new MyEventBusIndex()).build();` 形式来将索引类配置进去。

话已至此，整个 `EventBusAnnotationProcessor` 我们大致地分析了一遍。利用编译时注解的特性来生成索引类是一种很好的解决途径，避免了程序在运行时利用反射去获取订阅方法，提高了运行效率的同时又提高了逼格。

0006B 总结
==========
从头到尾分析下来，发现 EventBus 真的是一款不错的开源框架，完美诠释了观察者模式。从之前的 2.0 版本到现在的 3.0 版本，加入了注解的同时也减少了反射，提高了性能，为此增添了不少的色彩。

与 [EventBus](https://github.com/greenrobot/EventBus) 相似的还有 [Otto](https://github.com/square/otto) 框架，当然现在业内也有不少使用 [RxJava](https://github.com/ReactiveX/RxJava) 来实现具备发布/订阅功能的 “RxBus” 。对此我的看法是，如果是小型项目，可以使用 RxBus 来代替 [EventBus](https://github.com/greenrobot/EventBus) ，但是一旦项目成熟起来，涉及到模块之前通信和解耦，那么还是使用更加专业的 [EventBus](https://github.com/greenrobot/EventBus) 吧。毕竟若是新手想上手 [RxJava](https://github.com/ReactiveX/RxJava) 还是需要一段时间的。

今天就到这了，对 [EventBus](https://github.com/greenrobot/EventBus) 有问题的同学可以留言，bye bye ！

0007B References
================
* [EventBus 3.0 源码分析](http://www.jianshu.com/p/f057c460c77e)
* [老司机教你 “飙” EventBus 3](https://segmentfault.com/a/1190000005089229?utm_source=tuicool&utm_medium=referral#articleHeader11)
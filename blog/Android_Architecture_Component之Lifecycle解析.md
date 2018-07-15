title: Android Architecture Component之Lifecycle解析
date: 2018-07-15 00:29:11
categories: Android Blog
tags: [Android, 源码解析, 开源框架, Android Architecture Component]
---
Header
======
终于到了最后的关头，Android Architecture Component 系列的最后一节内容。今天给大家带来的就是 Lifecycle 的解析。

至于 Lifecycle 的作用就不过多介绍，简单的来说就是让你自己定义的东西可以感知生命周期。比如你想设计了一个 GPS 位置监听器，打算在 Activity 可交互状态下发送地址位置，那么就可以利用 Lifecycle 来做这件事，这样和 Activity 的耦合性就减少了很多。

废话不多说了，就来看看 Lifecycle 内部的实现原理吧。

Lifecycle
=========

Part 1
======
LifecycleOwner
--------------
先来看 LifecycleOwner 接口，这个接口定义就说明了某样东西是具有生命周期的。getLifecycle() 方法返回生命周期。

``` java
public interface LifecycleOwner {
    /**
     * Returns the Lifecycle of the provider.
     *
     * @return The lifecycle of the provider.
     */
    @NonNull
    Lifecycle getLifecycle();
}
```

官方建议除了 Activity 和 Fragment 之外，其他的代码都不应该实现 LifecycleOwner 这个接口。

目前 SupportActivity 和 Fragment 都实现了该接口。

Lifecycle
---------
在上面我们看到 LifecycleOwner 接口的 getLifecycle() 方法返回了 Lifecycle 。Lifecycle 代表着生命周期，那么来看看 Lifecycle 是怎么定义的。

``` java
public abstract class Lifecycle {

    @MainThread
    public abstract void addObserver(@NonNull LifecycleObserver observer);

    @MainThread
    public abstract void removeObserver(@NonNull LifecycleObserver observer);

    @MainThread
    @NonNull
    public abstract State getCurrentState();

    @SuppressWarnings("WeakerAccess")
    public enum Event {
        /**
         * Constant for onCreate event of the {@link LifecycleOwner}.
         */
        ON_CREATE,
        /**
         * Constant for onStart event of the {@link LifecycleOwner}.
         */
        ON_START,
        /**
         * Constant for onResume event of the {@link LifecycleOwner}.
         */
        ON_RESUME,
        /**
         * Constant for onPause event of the {@link LifecycleOwner}.
         */
        ON_PAUSE,
        /**
         * Constant for onStop event of the {@link LifecycleOwner}.
         */
        ON_STOP,
        /**
         * Constant for onDestroy event of the {@link LifecycleOwner}.
         */
        ON_DESTROY,
        /**
         * An {@link Event Event} constant that can be used to match all events.
         */
        ON_ANY
    }

    @SuppressWarnings("WeakerAccess")
    public enum State {
        /**
         * Destroyed state for a LifecycleOwner. After this event, this Lifecycle will not dispatch
         * any more events. For instance, for an {@link android.app.Activity}, this state is reached
         * <b>right before</b> Activity's {@link android.app.Activity#onDestroy() onDestroy} call.
         */
        DESTROYED,

        /**
         * Initialized state for a LifecycleOwner. For an {@link android.app.Activity}, this is
         * the state when it is constructed but has not received
         * {@link android.app.Activity#onCreate(android.os.Bundle) onCreate} yet.
         */
        INITIALIZED,

        /**
         * Created state for a LifecycleOwner. For an {@link android.app.Activity}, this state
         * is reached in two cases:
         * <ul>
         *     <li>after {@link android.app.Activity#onCreate(android.os.Bundle) onCreate} call;
         *     <li><b>right before</b> {@link android.app.Activity#onStop() onStop} call.
         * </ul>
         */
        CREATED,

        /**
         * Started state for a LifecycleOwner. For an {@link android.app.Activity}, this state
         * is reached in two cases:
         * <ul>
         *     <li>after {@link android.app.Activity#onStart() onStart} call;
         *     <li><b>right before</b> {@link android.app.Activity#onPause() onPause} call.
         * </ul>
         */
        STARTED,

        /**
         * Resumed state for a LifecycleOwner. For an {@link android.app.Activity}, this state
         * is reached after {@link android.app.Activity#onResume() onResume} is called.
         */
        RESUMED;

        /**
         * Compares if this State is greater or equal to the given {@code state}.
         *
         * @param state State to compare with
         * @return true if this State is greater or equal to the given {@code state}
         */
        public boolean isAtLeast(@NonNull State state) {
            return compareTo(state) >= 0;
        }
    }
}
```

Lifecycle 是个抽象类，其中定义了：

* addObserver ：增加观察者，观察者可以观察到该生命周期的变化，具体的观察者就是 LifecycleObserver ；
* removeObserver ：移除观察者 LifecycleObserver ；
* getCurrentState ：返回当前生命周期的状态；
* Event ：生命周期事件；
* State ：生命周期状态；

至于 Event 和 State 的关系我们等到了下面再讲。

到这，我们来看看 SupportActivity 和 Fragment 在 getLifecycle 方法中返回了什么：

``` java
@Override
public Lifecycle getLifecycle() {
    return mLifecycleRegistry;
}
```

发现返回的是 LifecycleRegistry 的一个对象，而 LifecycleRegistry 就是 Lifecycle 的实现类。

我们先把对 LifecycleRegistry 的解析放一放，先来看看生命周期观察者 LifecycleObserver 。

LifecycleObserver
-----------------
``` java
@SuppressWarnings("WeakerAccess")
public interface LifecycleObserver {

}
```

LifecycleObserver 是个空接口，里面什么都没有。那我们自己定义一个类 MyLifecycleObserver 来实现 LifecycleObserver 接口，以达到观察生命周期的目的。

``` java
public class MyLifecycleObserver implements LifecycleObserver {

	@OnLifecycleEvent(Lifecycle.Event.ON_ANY)
	void onAny(LifecycleOwner owner, Lifecycle.Event event) {
	    System.out.println("onAny:" + event.name());
	}

	@OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
	void onCreate() {
	    System.out.println("onCreate");
	}

	@OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
	void onDestroy() {
	    System.out.println("onDestroy");
	}

}
```

然后在 MainActivity 里面添加我们的 MyLifecycleObserver 观察者。

``` java
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    getLifecycle().addObserver(new MyLifecycleObserver());
}
```

通过之前分析的代码我们可以观察到，getLifecycle() 返回的就是 LifecycleRegistry 对象。所以其实调用的就是 LifecycleRegistry 的 addObserver 方法来添加观察者的。

LifecycleRegistry
-----------------
``` java
    @Override
    public void addObserver(@NonNull LifecycleObserver observer) {
        State initialState = mState == DESTROYED ? DESTROYED : INITIALIZED;
        ObserverWithState statefulObserver = new ObserverWithState(observer, initialState);
        ObserverWithState previous = mObserverMap.putIfAbsent(observer, statefulObserver);

        if (previous != null) {
            return;
        }
        LifecycleOwner lifecycleOwner = mLifecycleOwner.get();
        if (lifecycleOwner == null) {
            // it is null we should be destroyed. Fallback quickly
            return;
        }

        boolean isReentrance = mAddingObserverCounter != 0 || mHandlingEvent;
        State targetState = calculateTargetState(observer);
        mAddingObserverCounter++;
        while ((statefulObserver.mState.compareTo(targetState) < 0
                && mObserverMap.contains(observer))) {
            pushParentState(statefulObserver.mState);
            statefulObserver.dispatchEvent(lifecycleOwner, upEvent(statefulObserver.mState));
            popParentState();
            // mState / subling may have been changed recalculate
            targetState = calculateTargetState(observer);
        }

        if (!isReentrance) {
            // we do sync only on the top level.
            sync();
        }
        mAddingObserverCounter--;
    }
```

一开始，针对每个 LifecycleObserver 对象设置了一个初始状态 initialState ，然后结合初始状态 initialState 和 observer ，把它俩包装成一个 ObserverWithState 对象。并保存到 mObserverMap 中。 mObserverMap 缓存了所有的生命周期观察者。

我们来看看 ObserverWithState 里面的操作。

ObserverWithState
-----------------
ObserverWithState 是 LifecycleRegistry 的静态内部类。

``` java
static class ObserverWithState {
    State mState;
    GenericLifecycleObserver mLifecycleObserver;

    ObserverWithState(LifecycleObserver observer, State initialState) {
        mLifecycleObserver = Lifecycling.getCallback(observer);
        mState = initialState;
    }

    void dispatchEvent(LifecycleOwner owner, Event event) {
        State newState = getStateAfter(event);
        mState = min(mState, newState);
        mLifecycleObserver.onStateChanged(owner, event);
        mState = newState;
    }
}
```

在 ObserverWithState 中，我们有点蹊跷，mLifecycleObserver 的类型是 GenericLifecycleObserver ，但是我们传入的是 LifecycleObserver 类型。所以在 Lifecycling.getCallback(observer) 这句代码中做的事情就是把 LifecycleObserver 转化成 GenericLifecycleObserver ，我们深入了解下。

Lifecycling
-----------
``` java
    @NonNull
    static GenericLifecycleObserver getCallback(Object object) {
        if (object instanceof FullLifecycleObserver) {
            return new FullLifecycleObserverAdapter((FullLifecycleObserver) object);
        }

        if (object instanceof GenericLifecycleObserver) {
            return (GenericLifecycleObserver) object;
        }

        final Class<?> klass = object.getClass();
        int type = getObserverConstructorType(klass);
        if (type == GENERATED_CALLBACK) {
            List<Constructor<? extends GeneratedAdapter>> constructors =
                    sClassToAdapters.get(klass);
            if (constructors.size() == 1) {
                GeneratedAdapter generatedAdapter = createGeneratedAdapter(
                        constructors.get(0), object);
                return new SingleGeneratedAdapterObserver(generatedAdapter);
            }
            GeneratedAdapter[] adapters = new GeneratedAdapter[constructors.size()];
            for (int i = 0; i < constructors.size(); i++) {
                adapters[i] = createGeneratedAdapter(constructors.get(i), object);
            }
            return new CompositeGeneratedAdaptersObserver(adapters);
        }
        return new ReflectiveGenericLifecycleObserver(object);
    }
```

根据代码可以大概知道，在 getCallback 中主要做的事情就是利用适配器 Adapter 把 LifeObserver 转化成 GenericLifecycleObserver 。

之前我们定义的 MyLifecycleObserver 是直接实现 LifecycleObserver 接口的，所以它不属于 FullLifecycleObserver 或者 FullLifecycleObserver ，因此它会去执行 getObserverConstructorType(klass) 方法。

``` java
    private static int getObserverConstructorType(Class<?> klass) {
        // 如果之前解析过了，直接返回缓存
        if (sCallbackCache.containsKey(klass)) {
            return sCallbackCache.get(klass);
        }
        // 否则调用 resolveObserverCallbackType 进行解析类型
        int type = resolveObserverCallbackType(klass);
        sCallbackCache.put(klass, type);
        return type;
    }
```

在 getObserverConstructorType 中，主要还是要看 resolveObserverCallbackType 方法。

``` java
    private static int resolveObserverCallbackType(Class<?> klass) {
        // anonymous class bug:35073837
        if (klass.getCanonicalName() == null) {
            return REFLECTIVE_CALLBACK;
        }

        // 注意这里调用了 generatedConstructor 来生成了 GeneratedAdapter 的构造器
        Constructor<? extends GeneratedAdapter> constructor = generatedConstructor(klass);
        if (constructor != null) {

            // 得到构造器后进行缓存
            sClassToAdapters.put(klass, Collections
                    .<Constructor<? extends GeneratedAdapter>>singletonList(constructor));
            return GENERATED_CALLBACK;
        }

        boolean hasLifecycleMethods = ClassesInfoCache.sInstance.hasLifecycleMethods(klass);
        if (hasLifecycleMethods) {
            return REFLECTIVE_CALLBACK;
        }

        Class<?> superclass = klass.getSuperclass();
        List<Constructor<? extends GeneratedAdapter>> adapterConstructors = null;
        if (isLifecycleParent(superclass)) {
            if (getObserverConstructorType(superclass) == REFLECTIVE_CALLBACK) {
                return REFLECTIVE_CALLBACK;
            }
            adapterConstructors = new ArrayList<>(sClassToAdapters.get(superclass));
        }

        for (Class<?> intrface : klass.getInterfaces()) {
            if (!isLifecycleParent(intrface)) {
                continue;
            }
            if (getObserverConstructorType(intrface) == REFLECTIVE_CALLBACK) {
                return REFLECTIVE_CALLBACK;
            }
            if (adapterConstructors == null) {
                adapterConstructors = new ArrayList<>();
            }
            adapterConstructors.addAll(sClassToAdapters.get(intrface));
        }
        if (adapterConstructors != null) {
            sClassToAdapters.put(klass, adapterConstructors);
            return GENERATED_CALLBACK;
        }

        return REFLECTIVE_CALLBACK;
    }
```

resolveObserverCallbackType 方法中调用 generatedConstructor 来生成 MyLifecycleObserver 的 GeneratedAdapter 构造器。看到这里可能很多人会懵逼，什么是 GeneratedAdapter ？

GeneratedAdapter
----------------
其实 GeneratedAdapter 可以理解为系统为我们的 MyLifecycleObserver 而设计适配器。

比如，我们在 MyLifecycleObserver 里设计了 onCreate 方法在生命周期的创建状态来回调，但是系统并不知道这个 onCreate 方法。所以需要设计出一套适配器来适配我们的 MyLifecycleObserver 。

那么这个适配器的代码也需要我们来写吗？不需要，在编译期时 apt 自动帮我们生成好了。我们可以在 build/generated/source/apt 目录下找到自动生成的 GeneratedAdapter 。

``` java
     public class MyLifecycleObserver_LifecycleAdapter implements GeneratedAdapter {
      final MyLifecycleObserver mReceiver;

      MyLifecycleObserver_LifecycleAdapter(MyLifecycleObserver receiver) {
        this.mReceiver = receiver;
      }

      @Override
      public void callMethods(LifecycleOwner owner, Lifecycle.Event event, boolean onAny,
          MethodCallsLogger logger) {
        boolean hasLogger = logger != null;
        if (onAny) {
          if (!hasLogger || logger.approveCall("onAny", 4)) {
            mReceiver.onAny(owner,event);
          }
          return;
        }
        if (event == Lifecycle.Event.ON_CREATE) {
          if (!hasLogger || logger.approveCall("onCreate", 1)) {
            mReceiver.onCreate();
          }
          return;
        }
        if (event == Lifecycle.Event.ON_DESTROY) {
          if (!hasLogger || logger.approveCall("onDestroy", 1)) {
            mReceiver.onDestroy();
          }
          return;
        }
      }
    }
```

到这里就真相大白了吧，所以在 generatedConstructor 方法中生成的就是 MyLifecycleObserver_LifecycleAdapter 的构造器。

具体代码：

``` java
    @Nullable
    private static Constructor<? extends GeneratedAdapter> generatedConstructor(Class<?> klass) {
        try {
            Package aPackage = klass.getPackage();
            String name = klass.getCanonicalName();
            final String fullPackage = aPackage != null ? aPackage.getName() : "";
            // 获取apt自动生成的GeneratedAdapter的类名，在这里就是 MyLifecycleObserver_LifecycleAdapter
            final String adapterName = getAdapterName(fullPackage.isEmpty() ? name :
                    name.substring(fullPackage.length() + 1));

            @SuppressWarnings("unchecked") final Class<? extends GeneratedAdapter> aClass =
                    (Class<? extends GeneratedAdapter>) Class.forName(
                            fullPackage.isEmpty() ? adapterName : fullPackage + "." + adapterName);
            Constructor<? extends GeneratedAdapter> constructor =
                    aClass.getDeclaredConstructor(klass);
            if (!constructor.isAccessible()) {
                constructor.setAccessible(true);
            }
            return constructor;
        } catch (ClassNotFoundException e) {
            return null;
        } catch (NoSuchMethodException e) {
            // this should not happen
            throw new RuntimeException(e);
        }
    }
```

我们再回到 resolveObserverCallbackType 方法，获取到 MyLifecycleObserver_LifecycleAdapter 构造器后，直接返回了 GENERATED_CALLBACK 。

``` java
        Constructor<? extends GeneratedAdapter> constructor = generatedConstructor(klass);
        if (constructor != null) {
            sClassToAdapters.put(klass, Collections
                    .<Constructor<? extends GeneratedAdapter>>singletonList(constructor));
            return GENERATED_CALLBACK;
        }
```

然后在 getCallback 方法中会执行：

``` java
        if (type == GENERATED_CALLBACK) {
            List<Constructor<? extends GeneratedAdapter>> constructors =
                    sClassToAdapters.get(klass);
            // MyLifecycleObserver_LifecycleAdapter 的构造器只有一个，所以适配创建出来的是 SingleGeneratedAdapterObserver
            if (constructors.size() == 1) {
                // 这里的 generatedAdapter 就是 MyLifecycleObserver_LifecycleAdapter
                GeneratedAdapter generatedAdapter = createGeneratedAdapter(
                        constructors.get(0), object);

                // 单个构造器
                return new SingleGeneratedAdapterObserver(generatedAdapter);
            }
            // 至于什么时候 MyLifecycleObserver_LifecycleAdapter 会有多个构造器目前我还不清楚，如果有大神知道的话请告知我下
            GeneratedAdapter[] adapters = new GeneratedAdapter[constructors.size()];
            for (int i = 0; i < constructors.size(); i++) {
                adapters[i] = createGeneratedAdapter(constructors.get(i), object);
            }

            // 多个构造器
            return new CompositeGeneratedAdaptersObserver(adapters);
        }
```

因为 MyLifecycleObserver_LifecycleAdapter 的构造器就只有一个，所以 LifecycleObserver 转化成了 SingleGeneratedAdapterObserver 。

SingleGeneratedAdapterObserver
------------------------------
``` java
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class SingleGeneratedAdapterObserver implements GenericLifecycleObserver {

    private final GeneratedAdapter mGeneratedAdapter;

    SingleGeneratedAdapterObserver(GeneratedAdapter generatedAdapter) {
        mGeneratedAdapter = generatedAdapter;
    }

    @Override
    public void onStateChanged(LifecycleOwner source, Lifecycle.Event event) {
        mGeneratedAdapter.callMethods(source, event, false, null);
        mGeneratedAdapter.callMethods(source, event, true, null);
    }
}
```

SingleGeneratedAdapterObserver 是实现了 GenericLifecycleObserver 这个接口的。经过上面的一系列操作，我们的 MyLifecycleObserver 就被适配成了 SingleGeneratedAdapterObserver 。

ObserverWithState
-----------------
其实在 ObserverWithState 还有一个方法 ： dispatchEvent 。

``` java
        void dispatchEvent(LifecycleOwner owner, Event event) {
            State newState = getStateAfter(event);
            mState = min(mState, newState);
            // mLifecycleObserver 就是上面的 SingleGeneratedAdapterObserver
            mLifecycleObserver.onStateChanged(owner, event);
            mState = newState;
        }
```

dispatchEvent 会在生命周期发生改变时，然后通知观察者的时候调用。

所以我们可以理一理调用链：

生命周期发生改变 -> ObserverWithState.dispatchEvent -> SingleGeneratedAdapterObserver.onStateChanged -> MyLifecycleObserver_LifecycleAdapter.callMethods -> MyLifecycleObserver.onCreate/onAny/onDestroy

看完有没有一种原来如此、恍然大悟的感觉？

Part 2
======
那么什么时候会去调用 ObserverWithState.dispatchEvent 的方法呢？

答案就是在 LifecycleRegistry.handleLifecycleEvent 。 handleLifecycleEvent 方法就是被设计为设置生命周期状态并通知观察者的。

LifecycleRegistry
-----------------
``` java
    public void handleLifecycleEvent(@NonNull Lifecycle.Event event) {
        // 根据 event 来得到下一个生命周期的状态值
        State next = getStateAfter(event);
        // 将当前生命周期状态值改成 next ，并通知观察者
        moveToState(next);
    }
```

在这里正好把 event 和 state 的关系捋一捋，这是官方给出的参考图，简明扼要。

![event and state](/uploads/20180715/20180715050357.png)

下面就来看看 moveToState 方法。

``` java
    private void moveToState(State next) {
        if (mState == next) {
            return;
        }
        mState = next;
        if (mHandlingEvent || mAddingObserverCounter != 0) {
            mNewEventOccurred = true;
            // we will figure out what to do on upper level.
            return;
        }
        mHandlingEvent = true;
        sync();
        mHandlingEvent = false;
    }
```

如果当前生命周期的状态已经同步完成了，就直接 return 掉。否则就会同步并调用 sync 方法。

``` java
    private void sync() {
        LifecycleOwner lifecycleOwner = mLifecycleOwner.get();
        if (lifecycleOwner == null) {
            Log.w(LOG_TAG, "LifecycleOwner is garbage collected, you shouldn't try dispatch "
                    + "new events from it.");
            return;
        }
        while (!isSynced()) {
            mNewEventOccurred = false;
            // no need to check eldest for nullability, because isSynced does it for us.
            if (mState.compareTo(mObserverMap.eldest().getValue().mState) < 0) {
                backwardPass(lifecycleOwner);
            }
            Entry<LifecycleObserver, ObserverWithState> newest = mObserverMap.newest();
            if (!mNewEventOccurred && newest != null
                    && mState.compareTo(newest.getValue().mState) > 0) {
                forwardPass(lifecycleOwner);
            }
        }
        mNewEventOccurred = false;
    }
```

主要做的事情就是比较当前生命周期的状态和我们存放在 mObserverMap 中最早或最新放入的观察者的状态，通过上面的分析，我们知道是 ObserverWithState 里面一开始有我们添加观察者时的初始状态。

假如生命周期当前状态 mState 是 STARTED ,而观察者的状态是 CREATED，那么我们需要通过 forwardPass() 通知所有的观察者当前生命周期的状态改变到了 STARTED ，请同步。

``` java
    private void forwardPass(LifecycleOwner lifecycleOwner) {
        Iterator<Entry<LifecycleObserver, ObserverWithState>> ascendingIterator =
                mObserverMap.iteratorWithAdditions();
        while (ascendingIterator.hasNext() && !mNewEventOccurred) {
            Entry<LifecycleObserver, ObserverWithState> entry = ascendingIterator.next();
            ObserverWithState observer = entry.getValue();
            while ((observer.mState.compareTo(mState) < 0 && !mNewEventOccurred
                    && mObserverMap.contains(entry.getKey()))) {
                pushParentState(observer.mState);
                observer.dispatchEvent(lifecycleOwner, upEvent(observer.mState));
                popParentState();
            }
        }
    }
```

首先循坏遍历存储了所有观察者的 mObserverMap ，第二个 while 是要分发处理各个状态经过的 event 。

比如当前状态 mState 是 RESUMED ，而 ObserverWithState 中的 state 是 INITIALIZED 。那么调用 ObserverWithState 的 dispatchEvent 方法就要分发 ON_CREATE ，ON_START ，ON_RESUME 了。

Part 3
======
问题又来了，到底是谁调用了 handleLifecycleEvent 呢？

我们可以在最终 merge 好的 AndroidManifest 中去寻找答案。

我们发现了这货 ：

``` xml
<provider
    android:name="android.arch.lifecycle.ProcessLifecycleOwnerInitializer"
    android:authorities="com.yuqirong.multiscrolllayout.lifecycle-trojan"
    android:exported="false"
    android:multiprocess="true" />
```

进 ProcessLifecycleOwnerInitializer 里看看。

``` java
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class ProcessLifecycleOwnerInitializer extends ContentProvider {
    @Override
    public boolean onCreate() {
        LifecycleDispatcher.init(getContext());
        ProcessLifecycleOwner.init(getContext());
        return true;
    }

	...

}
```

里面有个 LifecycleDispatcher ，一听名字上就猜到它做的是生命周期分发的工作。

``` java
class LifecycleDispatcher {

    private static final String REPORT_FRAGMENT_TAG = "android.arch.lifecycle"
            + ".LifecycleDispatcher.report_fragment_tag";

    private static AtomicBoolean sInitialized = new AtomicBoolean(false);

    static void init(Context context) {
        if (sInitialized.getAndSet(true)) {
            return;
        }
        // 注册了ActivityLifecycleCallbacks
        ((Application) context.getApplicationContext())
                .registerActivityLifecycleCallbacks(new DispatcherActivityCallback());
    }

    @SuppressWarnings("WeakerAccess")
    @VisibleForTesting
    static class DispatcherActivityCallback extends EmptyActivityLifecycleCallbacks {
        private final FragmentCallback mFragmentCallback;

        DispatcherActivityCallback() {
            mFragmentCallback = new FragmentCallback();
        }

        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            // 注册了一个FragmentLifecycleCallbacks，这个是监控fragment的生命周期回调
            if (activity instanceof FragmentActivity) {
                ((FragmentActivity) activity).getSupportFragmentManager()
                        .registerFragmentLifecycleCallbacks(mFragmentCallback, true);
            }
            // 这句代码很关键 
            ReportFragment.injectIfNeededIn(activity);
        }

        @Override
        public void onActivityStopped(Activity activity) {
            if (activity instanceof FragmentActivity) {
                markState((FragmentActivity) activity, CREATED);
            }
        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
            if (activity instanceof FragmentActivity) {
                markState((FragmentActivity) activity, CREATED);
            }
        }
    }

    ...
}
```

发现有一个 ReportFragment.injectIfNeededIn(activity); 进这里面看看。

``` java
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class ReportFragment extends Fragment {
    private static final String REPORT_FRAGMENT_TAG = "android.arch.lifecycle"
            + ".LifecycleDispatcher.report_fragment_tag";

    public static void injectIfNeededIn(Activity activity) {
        // ProcessLifecycleOwner should always correctly work and some activities may not extend
        // FragmentActivity from support lib, so we use framework fragments for activities
        android.app.FragmentManager manager = activity.getFragmentManager();
        if (manager.findFragmentByTag(REPORT_FRAGMENT_TAG) == null) {
            manager.beginTransaction().add(new ReportFragment(), REPORT_FRAGMENT_TAG).commit();
            // Hopefully, we are the first to make a transaction.
            manager.executePendingTransactions();
        }
    }

    static ReportFragment get(Activity activity) {
        return (ReportFragment) activity.getFragmentManager().findFragmentByTag(
                REPORT_FRAGMENT_TAG);
    }

    private ActivityInitializationListener mProcessListener;

    private void dispatchCreate(ActivityInitializationListener listener) {
        if (listener != null) {
            listener.onCreate();
        }
    }

    private void dispatchStart(ActivityInitializationListener listener) {
        if (listener != null) {
            listener.onStart();
        }
    }

    private void dispatchResume(ActivityInitializationListener listener) {
        if (listener != null) {
            listener.onResume();
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        dispatchCreate(mProcessListener);
        dispatch(Lifecycle.Event.ON_CREATE);
    }

    @Override
    public void onStart() {
        super.onStart();
        dispatchStart(mProcessListener);
        dispatch(Lifecycle.Event.ON_START);
    }

    @Override
    public void onResume() {
        super.onResume();
        dispatchResume(mProcessListener);
        dispatch(Lifecycle.Event.ON_RESUME);
    }

    @Override
    public void onPause() {
        super.onPause();
        dispatch(Lifecycle.Event.ON_PAUSE);
    }

    @Override
    public void onStop() {
        super.onStop();
        dispatch(Lifecycle.Event.ON_STOP);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        dispatch(Lifecycle.Event.ON_DESTROY);
        // just want to be sure that we won't leak reference to an activity
        mProcessListener = null;
    }

    private void dispatch(Lifecycle.Event event) {
        Activity activity = getActivity();
        if (activity instanceof LifecycleRegistryOwner) {
            ((LifecycleRegistryOwner) activity).getLifecycle().handleLifecycleEvent(event);
            return;
        }

        if (activity instanceof LifecycleOwner) {
            Lifecycle lifecycle = ((LifecycleOwner) activity).getLifecycle();
            if (lifecycle instanceof LifecycleRegistry) {
                ((LifecycleRegistry) lifecycle).handleLifecycleEvent(event);
            }
        }
    }

    void setProcessListener(ActivityInitializationListener processListener) {
        mProcessListener = processListener;
    }

    interface ActivityInitializationListener {
        void onCreate();

        void onStart();

        void onResume();
    }
}
```
把 ReportFragment 加入到 Activity 中,然后在其各个生命周期中都会调用 dispatch() 方法。而 dispatch 方法最后调用了 LifecycleRegistry.RehandleLifecycleEvent 。

至此，Lifecycle 的整个流程都梳理完成了。

Footer
======
我们终于完成了对 Android Architecture Component 的整体源码解析，其中涉及到了 LiveData 、 ViewModel 和 Lifecycle 。当然出此之外还有 Room 和 Paging Library 等也是不错的选择，暂时就告一段落了。至于 Room 等有兴趣的同学可以下去自己研究下，拜拜！

bye ~~
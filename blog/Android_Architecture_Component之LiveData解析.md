title: Android Architecture Component之LiveData解析
date: 2018-06-20 22:07:08
categories: Android Blog
tags: [Android, 源码解析]
---
Header
======
Android Architecture Component 是 Google 在 2017 年推出的一套帮助开发者解决 Android 架构设计的方案。里面有众多吸引人的亮点，比如 Lifecycle、ViewModel 和 LiveData 等组件的设计，确实是一款牛逼的架构。

相信很多同学都用过这个架构了，在这就不多介绍了。今天就给大家来解析一下其中的 LiveData 是如何工作的。

LiveData 表示的是动态的数据，比如我们从网络上获取的数据，或者从数据库中获取的数据等，都可以用 LiveData 来概括。其中 setValue 方法是需要运行在主线程中的，而 postValue 方法是可以在子线程运行的。

LiveData
========
Observer
--------
LiveData 应用的主要是观察者模式，因为数据是多变的，所以肯定需要观察者来观察。而观察者和数据源建立连接就是通过 observe 方法来实现的。

``` java
private SafeIterableMap<Observer<T>, ObserverWrapper> mObservers = new SafeIterableMap<>();
```

这个 LiveData 的所有观察者 Observer 都会被保存在 mObservers 这个 map 里面。那么对应的 value 值 ObserverWrapper 又是什么东西呢？

``` java
private abstract class ObserverWrapper {
        final Observer<T> mObserver;
        boolean mActive;
        int mLastVersion = START_VERSION;

        ObserverWrapper(Observer<T> observer) {
            mObserver = observer;
        }
		
        ...

        void activeStateChanged(boolean newActive) {
            if (newActive == mActive) {
                return;
            }
            // immediately set active state, so we'd never dispatch anything to inactive
            // owner
            mActive = newActive;
            boolean wasInactive = LiveData.this.mActiveCount == 0;
            LiveData.this.mActiveCount += mActive ? 1 : -1;
            // 如果现在第一次新增活跃的观察者，那么回调 onActive ，onActive 是个空方法
            if (wasInactive && mActive) {
                onActive();
            }
            // 如果现在没有活跃的观察者了，那么回调 onInactive ，onInactive 是个空方法
            if (LiveData.this.mActiveCount == 0 && !mActive) {
                onInactive();
            }
            // 向观察者发送 LiveData 的值
            if (mActive) {
                dispatchingValue(this);
            }
        }
}
```

ObserverWrapper 是 Observer 的包装类，在 Observer 的基础上增加了 mActive 和 mLastVersion 。mActive 用来标识观察者是否是活跃，也就是说是否是在可用的生命周期内。

但是 ObserverWrapper 是个抽象类啊，到底是谁来实现它的呢？答案有两个。

* LifecycleBoundObserver
* AlwaysActiveObserver

我们重点来讲讲 LifecycleBoundObserver 。

``` java
    class LifecycleBoundObserver extends ObserverWrapper implements GenericLifecycleObserver {
        @NonNull final LifecycleOwner mOwner;

        LifecycleBoundObserver(@NonNull LifecycleOwner owner, Observer<T> observer) {
            super(observer);
            mOwner = owner;
        }

        @Override
        boolean shouldBeActive() {
            return mOwner.getLifecycle().getCurrentState().isAtLeast(STARTED);
        }

        @Override
        public void onStateChanged(LifecycleOwner source, Lifecycle.Event event) {
            if (mOwner.getLifecycle().getCurrentState() == DESTROYED) {
                // 移除观察者，在这个方法中会移除生命周期监听并回调activeStateChanged 方法
                removeObserver(mObserver);
                return;
            }
            activeStateChanged(shouldBeActive());
        }

        @Override
        boolean isAttachedTo(LifecycleOwner owner) {
            return mOwner == owner;
        }

        @Override
        void detachObserver() {
            mOwner.getLifecycle().removeObserver(this);
        }
    }
```

可以看出，LifecycleBoundObserver 是把 ObserverWrapper 和 Lifecycle 相结合了。这样，在 LiveData 里就可以获取到观察者的生命周期了。当观察者的生命周期可用时，LiveData 会把数据发送给观察者，而当观察者生命周期不可用的时候，即 `mOwner.getLifecycle().getCurrentState() == DESTROYED` ，LiveData 就会选择不发送，并且自动解绑，防止造成内存泄漏等问题。

最后补充一下，LiveData 认为观察者生命周期可用的依据就是在 onStart 调用之后，在 onPause 调用之前。

平时使用 observe 的就是直接利用的是 LifecycleBoundObserver ，而另一个 AlwaysActiveObserver 顾名思义就是一直是活跃的，和观察者的生命周期无关了。我们调用 observeForever 方法内部使用的就是 AlwaysActiveObserver 。

observe
-------
顺便，我们把 observe 方法也一起看了。

``` java
    @MainThread
    public void observe(@NonNull LifecycleOwner owner, @NonNull Observer<T> observer) {
        if (owner.getLifecycle().getCurrentState() == DESTROYED) {
            // ignore
            return;
        }
        LifecycleBoundObserver wrapper = new LifecycleBoundObserver(owner, observer);
        ObserverWrapper existing = mObservers.putIfAbsent(observer, wrapper);
        if (existing != null && !existing.isAttachedTo(owner)) {
            throw new IllegalArgumentException("Cannot add the same observer"
                    + " with different lifecycles");
        }
        if (existing != null) {
            return;
        }
        owner.getLifecycle().addObserver(wrapper);
    }
```

代码比较简单，就是利用了之前我们分析的 LifecycleBoundObserver ，再把它保存到 map 中。
最后，将 LifecycleBoundObserver 的生命周期监听注册好，OK，万事具备。

还有，另外一个 observeForever 方法就不看了，和 observe 方法差不多。

setData or postData
-------------------
setData 或者 postData 是当数据改变后向观察者传递值的。postData 最后也会调用 setData ，所以在这我们就只看 setData 了。

``` java
    @MainThread
    protected void setValue(T value) {
        assertMainThread("setValue");
        mVersion++;
        // mData 保存的就是改变后的数据
        mData = value;
        dispatchingValue(null);
    }
```

发现这个 setData 的代码中判断了是否是主线程，所以这个方法只能在主线程中调用了。另外，调用后相应的版本也会自增。最后就是调用 dispatchingValue 方法去分发这个数据 mData 了。

``` java
    private void dispatchingValue(@Nullable ObserverWrapper initiator) {
        if (mDispatchingValue) {
            mDispatchInvalidated = true;
            return;
        }
        mDispatchingValue = true;
        do {
            mDispatchInvalidated = false;
            if (initiator != null) {
                considerNotify(initiator);
                initiator = null;
            } else {
                for (Iterator<Map.Entry<Observer<T>, ObserverWrapper>> iterator =
                        mObservers.iteratorWithAdditions(); iterator.hasNext(); ) {
                    considerNotify(iterator.next().getValue());
                    if (mDispatchInvalidated) {
                        break;
                    }
                }
            }
        } while (mDispatchInvalidated);
        mDispatchingValue = false;
    }
```

在 dispatchingValue 就是循环遍历 mObservers 这个 map ，向每一个观察者都发送新的数据。具体的代码在 considerNotify 方法中。

``` java
    private void considerNotify(ObserverWrapper observer) {
        if (!observer.mActive) {
            return;
        }
        // Check latest state b4 dispatch. Maybe it changed state but we didn't get the event yet.
        //
        // we still first check observer.active to keep it as the entrance for events. So even if
        // the observer moved to an active state, if we've not received that event, we better not
        // notify for a more predictable notification order.
        if (!observer.shouldBeActive()) {
            observer.activeStateChanged(false);
            return;
        }
        if (observer.mLastVersion >= mVersion) {
            return;
        }
        observer.mLastVersion = mVersion;
        //noinspection unchecked
        // 调用 Observer 的 onChanged 方法实现回调
        observer.mObserver.onChanged((T) mData);
    }
```

好啦，到这里就把 LiveData 整个流程讲的差不多了。当然还有一些细节没讲到，感兴趣的同学就自己回去看看源码吧。

Footer
======
LiveData 讲完了，再说一点，我们在实际的使用中用的都是 LiveData 的实现类 MutableLiveData 。

剩下的就不多说了，那么就静静等待解析 ViewModel 和 Lifecycle 吧。

bye ~~
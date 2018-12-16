title: Android Architecture Component之ViewModel解析
date: 2018-07-09 22:52:33
categories: Android Blog
tags: [Android, 源码解析, 开源框架]
---
Header
======
之前给大家分析过了 LiveData ，今天就来看看 ViewModel 。

ViewModel 的作用就相当于 MVP 中的 Presenter ，是用来衔接 Model 和 View 的。通常把一些与 View 无关的业务逻辑写在 ViewModel 里面。ViewModel 内部创建出 LiveData 对象，利用 LiveData 对象来传递数据给 View 。

ViewModel 相对于 Presenter 而言，有以下几个好处：

1. ViewModel 并不直接持有 View ，所以在 ViewModel 销毁时不需要像 Presenter 一样地去手动解除 View 的绑定，也就不会造成持有 View 导致的内存泄漏；
2. 比如 Activity 配置改变的情况下，ViewModel 会保存不会丢失数据；
3. ViewModel 可以做到在同一个 Activity 的情况下，多个 Fragment 共享数据；

下面是官方给出的 ViewModel 生命周期图，大家随意感受一下：

![ViewModel Lifecycle](/uploads/20180709/20180709221953.png)

那么就开始进入正题吧。

本次解析的 ViewModel 源码基于 `android.arch.lifecycle:extensions:1.1.1`

ViewModel
=========
先来看看 ViewModel 是怎么被创建出来的：

``` java
    XXXViewModel xxxViewModel = ViewModelProviders.of(activity).get(XXXViewModel.class)
```

可以看到 ViewModel 并不是简单地 new 出来的，这其中的逻辑要需要我们一步一步慢慢揭开。

那么 ViewModel 是怎样被定义的呢？

ViewModel
--------
``` java
public abstract class ViewModel {
    /**
     * This method will be called when this ViewModel is no longer used and will be destroyed.
     * <p>
     * It is useful when ViewModel observes some data and you need to clear this subscription to
     * prevent a leak of this ViewModel.
     */
    @SuppressWarnings("WeakerAccess")
    protected void onCleared() {
    }
}
```

原来 ViewModel 是个抽象类，里面只有一个 onCleared() 方法。 onCleared() 会在 ViewModel 被销毁时回调，所以可以在 onCleared() 里面做一些释放资源、清理内存的操作。

另外，ViewModel 还有一个子类： AndroidViewModel 。AndroidViewModel 在 ViewModel 的基础上内部包含了 application 。

ViewModelProviders
------------------
我们就来抽丝剥茧了，先从 ViewModelProviders 入手。创建 ViewModel 时在 ViewModelProviders 中调用了 of 方法。

### of
``` java
    @NonNull
    @MainThread
    public static ViewModelProvider of(@NonNull FragmentActivity activity) {
        return of(activity, null);
    }

    @NonNull
    @MainThread
    public static ViewModelProvider of(@NonNull Fragment fragment) {
        return of(fragment, null);
    }

    @NonNull
    @MainThread
    public static ViewModelProvider of(@NonNull Fragment fragment, @Nullable Factory factory) {
        Application application = checkApplication(checkActivity(fragment));
        if (factory == null) {
            factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application);
        }
        return new ViewModelProvider(ViewModelStores.of(fragment), factory);
    }

    @NonNull
    @MainThread
    public static ViewModelProvider of(@NonNull FragmentActivity activity,
            @Nullable Factory factory) {
        Application application = checkApplication(activity);
        if (factory == null) {
            factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application);
        }
        return new ViewModelProvider(ViewModelStores.of(activity), factory);
    }
```

of 方法可以分为两个入口，分别对应着 Fragment 和 Activity 。这也说明了 ViewModel 的作用域其实是分为两个维度的。但是这两个方法内部的代码很像，逻辑基本都是：

1. 先去获取 application ；
2. 创建 factory ；
3. 创建 ViewModelProvider ，ViewModelProvider 顾名思义就是提供 ViewModel 的；

第一步就不用说了，直接进入第二步吧。

Factory
-------
Factory 是什么东东呢，说白了就是 ViewModel 的制造工厂。所有的 ViewModel 都是由 Factory 来创建出来的。

``` java
    public interface Factory {
        /**
         * Creates a new instance of the given {@code Class}.
         * <p>
         *
         * @param modelClass a {@code Class} whose instance is requested
         * @param <T>        The type parameter for the ViewModel.
         * @return a newly created ViewModel
         */
        @NonNull
        <T extends ViewModel> T create(@NonNull Class<T> modelClass);
    }
```
Factory 是个接口，里面定义了 create 方法来创建 ViewModel 。来看看它的实现类 NewInstanceFactory 。


### NewInstanceFactory

``` java
    /**
     * Simple factory, which calls empty constructor on the give class.
     */
    public static class NewInstanceFactory implements Factory {

        @SuppressWarnings("ClassNewInstance")
        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            //noinspection TryWithIdenticalCatches
            try {
                return modelClass.newInstance();
            } catch (InstantiationException e) {
                throw new RuntimeException("Cannot create an instance of " + modelClass, e);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Cannot create an instance of " + modelClass, e);
            }
        }
    }
```

其实没啥好说的，就是利用反射来创建实例了，是一个很简单的实现类。NewInstanceFactory 其实是创建普通 ViewModel 的工厂，而如果想创建 AndroidViewModel 的话，工厂就要选择 AndroidViewModelFactory 了。

### AndroidViewModelFactory

``` java
    /**
     * {@link Factory} which may create {@link AndroidViewModel} and
     * {@link ViewModel}, which have an empty constructor.
     */
    public static class AndroidViewModelFactory extends ViewModelProvider.NewInstanceFactory {

        private static AndroidViewModelFactory sInstance;

        /**
         * Retrieve a singleton instance of AndroidViewModelFactory.
         *
         * @param application an application to pass in {@link AndroidViewModel}
         * @return A valid {@link AndroidViewModelFactory}
         */
        @NonNull
        public static AndroidViewModelFactory getInstance(@NonNull Application application) {
            if (sInstance == null) {
                sInstance = new AndroidViewModelFactory(application);
            }
            return sInstance;
        }

        private Application mApplication;

        /**
         * Creates a {@code AndroidViewModelFactory}
         *
         * @param application an application to pass in {@link AndroidViewModel}
         */
        public AndroidViewModelFactory(@NonNull Application application) {
            mApplication = application;
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            if (AndroidViewModel.class.isAssignableFrom(modelClass)) {
                //noinspection TryWithIdenticalCatches
                try {
                    return modelClass.getConstructor(Application.class).newInstance(mApplication);
                } catch (NoSuchMethodException e) {
                    throw new RuntimeException("Cannot create an instance of " + modelClass, e);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("Cannot create an instance of " + modelClass, e);
                } catch (InstantiationException e) {
                    throw new RuntimeException("Cannot create an instance of " + modelClass, e);
                } catch (InvocationTargetException e) {
                    throw new RuntimeException("Cannot create an instance of " + modelClass, e);
                }
            }
            return super.create(modelClass);
        }
    }
```

发现在 AndroidViewModelFactory 的 create 方法中，对创建 ViewModel 的方案做了兼容，所以 AndroidViewModelFactory 是同时适用于创建 ViewModel 和 AndroidViewModel 的。并且 AndroidViewModelFactory 是单例工厂，防止多次创建浪费内存。

额外补充一点，在 ViewModelProviders 中有一个内部类 DefaultFactory ，现在已经被打上废弃的标签了，可以猜出这个 DefaultFactory 应该是早期版本的默认工厂类，现在已经被 AndroidViewModelFactory 代替了。

ViewModelStores
---------------
到这里 Factory 就有了，那么就重点来看看 `ViewModelStores.of(activity)` 这段代码了。ViewModelStores 是根据作用域用来提供 ViewModelStore 的，而 ViewModelStore 的作用就是存储 ViewModel ，内部是利用 key/value 将 ViewModel 保存在 HashMap 中，方便读写，这里就不展示 ViewModelStore 的源码了，大家可以把 ViewModelStore 当作 HashMap 就行。

``` java
	/**
	 * Factory methods for {@link ViewModelStore} class.
	 */
	@SuppressWarnings("WeakerAccess")
	public class ViewModelStores {
	
	    private ViewModelStores() {
	    }
	
	    /**
	     * Returns the {@link ViewModelStore} of the given activity.
	     *
	     * @param activity an activity whose {@code ViewModelStore} is requested
	     * @return a {@code ViewModelStore}
	     */
	    @NonNull
	    @MainThread
	    public static ViewModelStore of(@NonNull FragmentActivity activity) {
	        if (activity instanceof ViewModelStoreOwner) {
	            return ((ViewModelStoreOwner) activity).getViewModelStore();
	        }
	        return holderFragmentFor(activity).getViewModelStore();
	    }
	
	    /**
	     * Returns the {@link ViewModelStore} of the given fragment.
	     *
	     * @param fragment a fragment whose {@code ViewModelStore} is requested
	     * @return a {@code ViewModelStore}
	     */
	    @NonNull
	    @MainThread
	    public static ViewModelStore of(@NonNull Fragment fragment) {
	        if (fragment instanceof ViewModelStoreOwner) {
	            return ((ViewModelStoreOwner) fragment).getViewModelStore();
	        }
	        return holderFragmentFor(fragment).getViewModelStore();
	    }
	}
```

根据 ViewModelProviders 的思路，ViewModelStores 也是分为了两个方法，对应着 Fragment 和 Activity 。

1. 如果 Activity 和 Fragment 实现了 ViewModelStoreOwner 的接口，那么直接返回内部的 ViewModelStore 就行了；
2. 如果是之前老早版本的 Activity 或者 Fragment ，那么它们肯定是没有实现 ViewModelStoreOwner 接口的，那该怎么办呢？很简单，新创建一个 Fragment 来关联 ViewModelStoreOwner 就好了啊！

所以就有了 holderFragmentFor(activity) 和 holderFragmentFor(fragment) 这段了。

HolderFragment
--------------
HolderFragment 实现了 ViewModelStoreOwner 接口，所以 HolderFragment 的作用就是代替了那些之前没有实现 ViewModelStoreOwner 接口的 Activity/Fragment 。这样，Activity/Fragment 也间接地拥有了 ViewModelStore 。

HolderFragment 的代码我们就只看 holderFragmentFor(activity) 这一段吧，holderFragmentFor(fragment) 也是类似的。

``` java
    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static HolderFragment holderFragmentFor(FragmentActivity activity) {
        return sHolderFragmentManager.holderFragmentFor(activity);
    }

    static class HolderFragmentManager {

        ...
	
        HolderFragment holderFragmentFor(FragmentActivity activity) {
            FragmentManager fm = activity.getSupportFragmentManager();
            HolderFragment holder = findHolderFragment(fm);
            if (holder != null) {
                return holder;
            }
            holder = mNotCommittedActivityHolders.get(activity);
            if (holder != null) {
                return holder;
            }

            if (!mActivityCallbacksIsAdded) {
                mActivityCallbacksIsAdded = true;
                activity.getApplication().registerActivityLifecycleCallbacks(mActivityCallbacks);
            }
            holder = createHolderFragment(fm);
            mNotCommittedActivityHolders.put(activity, holder);
            return holder;
        }
    } 
```

其实就是把 HolderFragment 添加进 Activity 里面，这样 HolderFragment 就和 Activity 的生命周期关联在一起了。实际上获取的就是 HolderFragment 里面的 ViewModelStore 。每个 Activity 里面只有一个 HolderFragment 。

Fragment 也是同理，利用 getChildFragmentManager() 来往里添加 HolderFragment 。这里就不讲了，有兴趣的同学可以自己回去看看源码。

至此，用来创建 ViewModelProvider 的两个入参 ViewModelStore 和 Factory 都讲完了。

ViewModelProvider
-----------------
创建出 ViewModelProvider 后，最后一步就是调用它的 get 方法返回 ViewModel 了。

``` java
    @NonNull
    @MainThread
    public <T extends ViewModel> T get(@NonNull Class<T> modelClass) {
        String canonicalName = modelClass.getCanonicalName();
        if (canonicalName == null) {
            throw new IllegalArgumentException("Local and anonymous classes can not be ViewModels");
        }
        return get(DEFAULT_KEY + ":" + canonicalName, modelClass);
    }

    @NonNull
    @MainThread
    public <T extends ViewModel> T get(@NonNull String key, @NonNull Class<T> modelClass) {
        ViewModel viewModel = mViewModelStore.get(key);

        if (modelClass.isInstance(viewModel)) {
            //noinspection unchecked
            return (T) viewModel;
        } else {
            //noinspection StatementWithEmptyBody
            if (viewModel != null) {
                // TODO: log a warning.
            }
        }

        viewModel = mFactory.create(modelClass);
        mViewModelStore.put(key, viewModel);
        //noinspection unchecked
        return (T) viewModel;
    }
```

get 方法很 easy ，就是利用 class 的 canonicalName 生成一个唯一的 key ，然后利用 key 去 mViewModelStore 中获取。如果有值就返回，否则就利用 factory 创建新的 ViewModel ，然后保存到 mViewModelStore 中并返回。

整个 ViewModel 的源码流程基本上就讲完了，其实并不复杂。回去多多体会，总能明白其中的奥秘。

下面，额外给大家补充几个小点，加个鸡腿。

Tip
===
ViewModel的onCleared什么时候回调
--------------------------------
之前说过，ViewModel 是保存在 ViewModelStore 里面的，所以 ViewModel 的销毁一定是在 ViewModelStore 里面操作的。

### ViewModelStore

``` java
    /**
     *  Clears internal storage and notifies ViewModels that they are no longer used.
     */
    public final void clear() {
        for (ViewModel vm : mMap.values()) {
            vm.onCleared();
        }
        mMap.clear();
    }
```

可以看到 ViewModelStore 的 clear() 方法内部调用 ViewModel 的 onCleared() 方法。那么哪里调用了 ViewModelStore 的 clear() 方法呢？


### Fragment

``` java
    /**
     * Called when the fragment is no longer in use.  This is called
     * after {@link #onStop()} and before {@link #onDetach()}.
     */
    @CallSuper
    public void onDestroy() {
        mCalled = true;
        // Use mStateSaved instead of isStateSaved() since we're past onStop()
        if (mViewModelStore != null && !mHost.mFragmentManager.mStateSaved) {
            mViewModelStore.clear();
        }
    }
```

可以从代码上看到，Fragment 的销毁操作调用是在 onDestroy() 中。

另外，如果状态保存标记值 mStateSaved 为 true 的情况下，是不会去清除 ViewModel 的，这也是为什么上面中讲的配置改变的情况下，数据得以保持住的原因。

### FragmentActivity

``` java
    /**
     * Destroy all fragments.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();

        doReallyStop(false);

        if (mViewModelStore != null && !mRetaining) {
            mViewModelStore.clear();
        }

        mFragments.dispatchDestroy();
    }
```

同理， Activity 的销毁操作也是在 onDestroy() 完成的。

Footer
======
终于把 LiveData 和 ViewModel 都分析了一遍，现在还差一个 Lifecycle 。

那么等有时间再写吧，bye bye！
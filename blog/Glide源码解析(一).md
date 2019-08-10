title: Glide源码解析(一)
date: 2019-08-04 16:13:51
categories: Android Blog
tags: [Android,开源框架,源码解析,Glide]
---
前言
===
Glide是一个快速高效的Android图片加载库，注重于平滑的滚动。Glide提供了易用的API，高性能、可扩展的图片解码管道（decode pipeline），以及自动的资源池技术。

Glide 充分考虑了Android图片加载性能的两个关键方面：

*	图片解码速度
*	解码图片带来的资源压力

为了让用户拥有良好的App使用体验，图片不仅要快速加载，而且还不能因为过多的主线程I/O或频繁的垃圾回收导致页面的闪烁和抖动现象。

Glide使用了多个步骤来确保在Android上加载图片尽可能的快速和平滑：

*	自动、智能地下采样(downsampling)和缓存(caching)，以最小化存储开销和解码次数；
*	积极的资源重用，例如字节数组和Bitmap，以最小化昂贵的垃圾回收和堆碎片影响；
*	深度的生命周期集成，以确保仅优先处理活跃的Fragment和Activity的请求，并有利于应用在必要时释放资源以避免在后台时被杀掉。

目前，在 Android 开发中 Glide 算得上是图片加载框架中的佼佼者了。其巧妙的设计和卓越的性能令人赞叹不已。

那么本系列给大家带来的就是解析 Glide 的源码，看看背后的 Glide 是什么样子的？

Glide : https://github.com/bumptech/glide

version : v4.9.0

Glide使用方法
===
Glide 的 API 有很多，但是我们这里就挑最简单的讲：

	Glide.with(fragment)
	    .load(url)
	    .into(imageView);

可以看到基本上分为三个步骤：

1. with
2. load
3. into

在本篇文章，我们就讲讲第一步：Glide.with

源码解析
===
Glide
----
``` java
@NonNull
public static RequestManager with(@NonNull Context context) {
  return getRetriever(context).get(context);
}

@NonNull
public static RequestManager with(@NonNull Activity activity) {
  return getRetriever(activity).get(activity);
}

@NonNull
public static RequestManager with(@NonNull FragmentActivity activity) {
  return getRetriever(activity).get(activity);
}

@NonNull
public static RequestManager with(@NonNull Fragment fragment) {
  return getRetriever(fragment.getContext()).get(fragment);
}

@SuppressWarnings("deprecation")
@Deprecated
@NonNull
public static RequestManager with(@NonNull android.app.Fragment fragment) {
  return getRetriever(fragment.getActivity()).get(fragment);
}

@NonNull
public static RequestManager with(@NonNull View view) {
  return getRetriever(view.getContext()).get(view);
}
```

可以看到，with 重载的方法非常多，但是代码内部的逻辑基本上都是一致的。都是先调用了 getRetriever 方法。

``` java
@NonNull
private static RequestManagerRetriever getRetriever(@Nullable Context context) {
  // Context could be null for other reasons (ie the user passes in null), but in practice it will
  // only occur due to errors with the Fragment lifecycle.
  Preconditions.checkNotNull(
      context,
      "You cannot start a load on a not yet attached View or a Fragment where getActivity() "
          + "returns null (which usually occurs when getActivity() is called before the Fragment "
          + "is attached or after the Fragment is destroyed).");
  return Glide.get(context).getRequestManagerRetriever();
}
```

`Glide.get(context)` 就是获取了 Glide 单例，然后再 `getRequestManagerRetriever()` 。其实 Glide 创建单例的代码中有一堆变量初始化的代码，在这里就不详细展示出来了，后面有用到再讲。所以接下来的逻辑就到 RequestManagerRetriever 中去了。

RequestManagerRetriever
----
通过上面一堆 with 重载的方法可以看出，get 方法是和 with 一样也有一堆重载的，并且和 with 是一一对应的。

在这里，就主要顺着 `get(@NonNull FragmentActivity activity)` 来讲吧，其他的 get 方法里的逻辑也是类似的。

``` java
@NonNull
public RequestManager get(@NonNull FragmentActivity activity) {
  if (Util.isOnBackgroundThread()) {
    return get(activity.getApplicationContext());
  } else {
    assertNotDestroyed(activity);
    FragmentManager fm = activity.getSupportFragmentManager();
    return supportFragmentGet(activity, fm, /*parentHint=*/ null, isActivityVisible(activity));
  }
}
```

get 方法主要逻辑分为两块：

1. 如果不是主线程，就调用 get(Context context) 
2. 否则调用 supportFragmentGet()

我们先来看第一块逻辑吧

``` java
@NonNull
public RequestManager get(@NonNull Context context) {
  if (context == null) {
    throw new IllegalArgumentException("You cannot start a load on a null Context");
  } else if (Util.isOnMainThread() && !(context instanceof Application)) {
    if (context instanceof FragmentActivity) {
      return get((FragmentActivity) context);
    } else if (context instanceof Activity) {
      return get((Activity) context);
    } else if (context instanceof ContextWrapper
        // Only unwrap a ContextWrapper if the baseContext has a non-null application context.
        // Context#createPackageContext may return a Context without an Application instance,
        // in which case a ContextWrapper may be used to attach one.
        && ((ContextWrapper) context).getBaseContext().getApplicationContext() != null) {
      return get(((ContextWrapper) context).getBaseContext());
    }
  }

  return getApplicationManager(context);
}
```

前面会判断 Context ，然后再走不同的 get ，这些我们都跳过，来看最后的 getApplicationManager 。

``` java
@NonNull
private RequestManager getApplicationManager(@NonNull Context context) {
  // Either an application context or we're on a background thread.
  if (applicationManager == null) {
    synchronized (this) {
      if (applicationManager == null) {
        // Normally pause/resume is taken care of by the fragment we add to the fragment or
        // activity. However, in this case since the manager attached to the application will not
        // receive lifecycle events, we must force the manager to start resumed using
        // ApplicationLifecycle.

        // TODO(b/27524013): Factor out this Glide.get() call.
        Glide glide = Glide.get(context.getApplicationContext());
        applicationManager =
            factory.build(
                glide,
                new ApplicationLifecycle(),
                new EmptyRequestManagerTreeNode(),
                context.getApplicationContext());
      }
    }
  }

  return applicationManager;
}
```

在 getApplicationManager 会获取到一个 applicationManager 对象。需要注意的是，这个 applicationManager 的生命周期是和 Application 保持一致的。

接下来，来看看第二块逻辑 supportFragmentGet 。

``` java
@NonNull
private RequestManager supportFragmentGet(
    @NonNull Context context,
    @NonNull FragmentManager fm,
    @Nullable Fragment parentHint,
    boolean isParentVisible) {
  SupportRequestManagerFragment current =
      getSupportRequestManagerFragment(fm, parentHint, isParentVisible);
  RequestManager requestManager = current.getRequestManager();
  if (requestManager == null) {
    // TODO(b/27524013): Factor out this Glide.get() call.
    Glide glide = Glide.get(context);
    requestManager =
        factory.build(
            glide, current.getGlideLifecycle(), current.getRequestManagerTreeNode(), context);
    current.setRequestManager(requestManager);
  }
  return requestManager;
}
```

在 supportFragmentGet 中新建了一个没有 UI 的 SupportRequestManagerFragment ，然后把 fragment 添加到 Activity 中，这样 fragment 就和 Activity 的生命周期同步了。而在 fragment 中，有着 onStart() onStop() 的生命周期监听。因此，Glide 就实现了在 Fragment 和 Activity 的图片加载请求的生命周期管理。

我们可以看出，supportFragmentGet 中返回的 requestManager 是和当前 fragment 生命周期绑定在一起的。

结束语
===
综上所述，Glide.with 中，主要做的事情有两件：

* Glide 单例的初始化过程
* Glide 请求的生命周期管理

如果传入的是 ApplicationContext ，得到的就是 applicationManager ，生命周期和 Application 一致；否则得到的 requestManager 生命周期就是和 Activity/Fragment 一致了。





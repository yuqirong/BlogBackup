title: Glide源码解析(二)
date: 2019-08-06 21:56:46
categories: Android Blog
tags: [Android,开源框架,源码解析,Glide]
---
之前已经讲过 Glide.with 了，那么今天就来讲讲 load 方法。

Glide : https://github.com/bumptech/glide

version : v4.9.0

源码解析
===
load 重载的方法有很多，这里就挑一个看了。来看看 load(String string) 内部的代码。

RequestManager
----
``` java
@NonNull
@CheckResult
@Override
public RequestBuilder<Drawable> load(@Nullable String string) {
  return asDrawable().load(string);
}
```

内部先调用了 asDrawable() 。

``` java
@NonNull
@CheckResult
public RequestBuilder<Drawable> asDrawable() {
  return as(Drawable.class);
}
```

在 as 方法中，回去新建一个 RequestBuilder 对象，资源类型为 Drawable 。

一眼就可以看出，这是使用构造者模式来创建 Request 。

``` java
@NonNull
@CheckResult
public <ResourceType> RequestBuilder<ResourceType> as(
    @NonNull Class<ResourceType> resourceClass) {
  return new RequestBuilder<>(glide, this, resourceClass, context);
}
```

去 RequestBuilder 的构造方法中看看。

RequestBuilder
----
``` java
@SuppressLint("CheckResult")
@SuppressWarnings("PMD.ConstructorCallsOverridableMethod")
protected RequestBuilder(
    @NonNull Glide glide,
    RequestManager requestManager,
    Class<TranscodeType> transcodeClass,
    Context context) {
  this.glide = glide;
  this.requestManager = requestManager;
  this.transcodeClass = transcodeClass;
  this.context = context;
  this.transitionOptions = requestManager.getDefaultTransitionOptions(transcodeClass);
  this.glideContext = glide.getGlideContext();

  initRequestListeners(requestManager.getDefaultRequestListeners());
  apply(requestManager.getDefaultRequestOptions());
}
```

注意一下，这里调用了 apply 方法。apply 方法是用来对当前请求应用配置选项的。这里传入的是默认的配置选项。

``` java
@NonNull
@CheckResult
@Override
public RequestBuilder<TranscodeType> apply(@NonNull BaseRequestOptions<?> requestOptions) {
  Preconditions.checkNotNull(requestOptions);
  return super.apply(requestOptions);
}
```

RequestBuilder.apply 方法内部会去调用父类的 apply 方法。

BaseRequestOptions
---
``` java
@NonNull
@CheckResult
public T apply(@NonNull BaseRequestOptions<?> o) {
  if (isAutoCloneEnabled) {
    return clone().apply(o);
  }
  BaseRequestOptions<?> other = o;

  if (isSet(other.fields, SIZE_MULTIPLIER)) {
    sizeMultiplier = other.sizeMultiplier;
  }
  if (isSet(other.fields, USE_UNLIMITED_SOURCE_GENERATORS_POOL)) {
    useUnlimitedSourceGeneratorsPool = other.useUnlimitedSourceGeneratorsPool;
  }
  if (isSet(other.fields, USE_ANIMATION_POOL)) {
    useAnimationPool = other.useAnimationPool;
  }
  if (isSet(other.fields, DISK_CACHE_STRATEGY)) {
    diskCacheStrategy = other.diskCacheStrategy;
  }
  if (isSet(other.fields, PRIORITY)) {
    priority = other.priority;
  }
  if (isSet(other.fields, ERROR_PLACEHOLDER)) {
    errorPlaceholder = other.errorPlaceholder;
    errorId = 0;
    fields &= ~ERROR_ID;
  }
  if (isSet(other.fields, ERROR_ID)) {
    errorId = other.errorId;
    errorPlaceholder = null;
    fields &= ~ERROR_PLACEHOLDER;
  }
  if (isSet(other.fields, PLACEHOLDER)) {
    placeholderDrawable = other.placeholderDrawable;
    placeholderId = 0;
    fields &= ~PLACEHOLDER_ID;
  }
  if (isSet(other.fields, PLACEHOLDER_ID)) {
    placeholderId = other.placeholderId;
    placeholderDrawable = null;
    fields &= ~PLACEHOLDER;
  }
  if (isSet(other.fields, IS_CACHEABLE)) {
    isCacheable = other.isCacheable;
  }
  if (isSet(other.fields, OVERRIDE)) {
    overrideWidth = other.overrideWidth;
    overrideHeight = other.overrideHeight;
  }
  if (isSet(other.fields, SIGNATURE)) {
    signature = other.signature;
  }
  if (isSet(other.fields, RESOURCE_CLASS)) {
    resourceClass = other.resourceClass;
  }
  if (isSet(other.fields, FALLBACK)) {
    fallbackDrawable = other.fallbackDrawable;
    fallbackId = 0;
    fields &= ~FALLBACK_ID;
  }
  if (isSet(other.fields, FALLBACK_ID)) {
    fallbackId = other.fallbackId;
    fallbackDrawable = null;
    fields &= ~FALLBACK;
  }
  if (isSet(other.fields, THEME)) {
    theme = other.theme;
  }
  if (isSet(other.fields, TRANSFORMATION_ALLOWED)) {
    isTransformationAllowed = other.isTransformationAllowed;
  }
  if (isSet(other.fields, TRANSFORMATION_REQUIRED)) {
    isTransformationRequired = other.isTransformationRequired;
  }
  if (isSet(other.fields, TRANSFORMATION)) {
    transformations.putAll(other.transformations);
    isScaleOnlyOrNoTransform = other.isScaleOnlyOrNoTransform;
  }
  if (isSet(other.fields, ONLY_RETRIEVE_FROM_CACHE)) {
    onlyRetrieveFromCache = other.onlyRetrieveFromCache;
  }

  // Applying options with dontTransform() is expected to clear our transformations.
  if (!isTransformationAllowed) {
    transformations.clear();
    fields &= ~TRANSFORMATION;
    isTransformationRequired = false;
    fields &= ~TRANSFORMATION_REQUIRED;
    isScaleOnlyOrNoTransform = true;
  }

  fields |= other.fields;
  options.putAll(other.options);

  return selfOrThrowIfLocked();
}
```

可以看到，配置选项有一大堆。其中不乏有我们很常用的：

* diskCacheStrategy 磁盘缓存策略
* errorPlaceholder 出错时的占位图
* placeholderDrawable 加载时候的占位图
* overrideWidth、overrideHeight 加载图片固定宽高

等等，都是在 apply 中应用的。

看完了 asDrawable() 方法，接下来就回过头来看看 load 方法。

RequestManager
----
``` java
@NonNull
@Override
@CheckResult
public RequestBuilder<TranscodeType> load(@Nullable String string) {
  return loadGeneric(string);
}
```

所有的 load 方法内部都是去调用 loadGeneric 。

``` java
@NonNull
private RequestBuilder<TranscodeType> loadGeneric(@Nullable Object model) {
  this.model = model;
  isModelSet = true;
  return this;
}
```

在 loadGeneric 中，把 model 赋值给 this.model 全局变量。然后把 isModelSet 设置为 true ，标记已经调用过 load 方法了。并且返回了当前 RequestBuilder 对象。

以上就是 load 方法内部所有的逻辑了，其实 load 方法内部并没有什么复杂的东西。真正复杂的是接下来的 into 方法。关于 into 方法在下一篇中会给大家讲解。


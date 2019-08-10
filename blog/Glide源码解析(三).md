title: Glide源码解析(三)
date: 2019-08-07 17:12:29
categories: Android Blog
tags: [Android,开源框架,源码解析,Glide]
---
本篇是 Glide 系列的最后一篇，主要讲一下 into 方法里面的逻辑。into 的逻辑也是最多最复杂的，可能需要反复阅读源码才能搞清楚。

Glide : https://github.com/bumptech/glide

version : v4.9.0

RequestBuilder
----
``` java
@NonNull
public <Y extends Target<TranscodeType>> Y into(@NonNull Y target) {
  return into(target, /*targetListener=*/ null, Executors.mainThreadExecutor());
}

@NonNull
@Synthetic
<Y extends Target<TranscodeType>> Y into(
    @NonNull Y target,
    @Nullable RequestListener<TranscodeType> targetListener,
    Executor callbackExecutor) {
  return into(target, targetListener, /*options=*/ this, callbackExecutor);
}
```

into 方法最后都会调用 `into(@NonNull Y target, @Nullable RequestListener<TranscodeType> targetListener, BaseRequestOptions<?> options,  Executor callbackExecutor)`

``` java
private <Y extends Target<TranscodeType>> Y into(
    @NonNull Y target,
    @Nullable RequestListener<TranscodeType> targetListener,
    BaseRequestOptions<?> options,
    Executor callbackExecutor) {
  Preconditions.checkNotNull(target);
  // 判断有没有调用过 load 方法
  if (!isModelSet) {
    throw new IllegalArgumentException("You must call #load() before calling #into()");
  }
  // 这里根据一堆参数会去构造图片请求 SingleRequest
  Request request = buildRequest(target, targetListener, options, callbackExecutor);

  Request previous = target.getRequest();
  if (request.isEquivalentTo(previous)
      && !isSkipMemoryCacheWithCompletePreviousRequest(options, previous)) {
    request.recycle();
    // If the request is completed, beginning again will ensure the result is re-delivered,
    // triggering RequestListeners and Targets. If the request is failed, beginning again will
    // restart the request, giving it another chance to complete. If the request is already
    // running, we can let it continue running without interruption.
    if (!Preconditions.checkNotNull(previous).isRunning()) {
      // Use the previous request rather than the new one to allow for optimizations like skipping
      // setting placeholders, tracking and un-tracking Targets, and obtaining View dimensions
      // that are done in the individual Request.
      previous.begin();
    }
    return target;
  }

  requestManager.clear(target);
  target.setRequest(request);
  requestManager.track(target, request);

  return target;
}
```

构造出请求 request 后，重点关注下 requestManager.track 方法，由 requestManager 来执行这个请求。

RequestManager
----
``` java
synchronized void track(@NonNull Target<?> target, @NonNull Request request) {
  targetTracker.track(target);
  requestTracker.runRequest(request);
}
```

track 方法中调用了两个方法：
	
1. TargetTracker.track() 方法会对当前 Target 的生命周期进行管理;
2.	RequestTracker.runRequest() 方法对当前请求进行管理;

RequestTracker
----
``` java
public void runRequest(@NonNull Request request) {
  requests.add(request);
  if (!isPaused) {
    request.begin();
  } else {
    request.clear();
    if (Log.isLoggable(TAG, Log.VERBOSE)) {
      Log.v(TAG, "Paused, delaying request");
    }
    pendingRequests.add(request);
  }
}
```

当 Glide 未处于暂停状态的时候，会直接使用 Request.begin() 方法开启请求。

SingleRequest
----
``` java
@Override
public synchronized void begin() {
  assertNotCallingCallbacks();
  stateVerifier.throwIfRecycled();
  startTime = LogTime.getLogTime();
  if (model == null) {
    if (Util.isValidDimensions(overrideWidth, overrideHeight)) {
      width = overrideWidth;
      height = overrideHeight;
    }
    // Only log at more verbose log levels if the user has set a fallback drawable, because
    // fallback Drawables indicate the user expects null models occasionally.
    int logLevel = getFallbackDrawable() == null ? Log.WARN : Log.DEBUG;
    onLoadFailed(new GlideException("Received null model"), logLevel);
    return;
  }

  if (status == Status.RUNNING) {
    throw new IllegalArgumentException("Cannot restart a running request");
  }

  // If we're restarted after we're complete (usually via something like a notifyDataSetChanged
  // that starts an identical request into the same Target or View), we can simply use the
  // resource and size we retrieved the last time around and skip obtaining a new size, starting a
  // new load etc. This does mean that users who want to restart a load because they expect that
  // the view size has changed will need to explicitly clear the View or Target before starting
  // the new load.
  if (status == Status.COMPLETE) {
    onResourceReady(resource, DataSource.MEMORY_CACHE);
    return;
  }

  // Restarts for requests that are neither complete nor running can be treated as new requests
  // and can run again from the beginning.
  
  // 主要来看这里，之前的都是一些对请求的状态判断
  status = Status.WAITING_FOR_SIZE;
  if (Util.isValidDimensions(overrideWidth, overrideHeight)) {
    onSizeReady(overrideWidth, overrideHeight);
  } else {
    target.getSize(this);
  }

  if ((status == Status.RUNNING || status == Status.WAITING_FOR_SIZE)
      && canNotifyStatusChanged()) {
    target.onLoadStarted(getPlaceholderDrawable());
  }
  if (IS_VERBOSE_LOGGABLE) {
    logV("finished run method in " + LogTime.getElapsedMillis(startTime));
  }
}
```

在 begin 方法中，重点关注下 

	  if (Util.isValidDimensions(overrideWidth, overrideHeight)) {
	    onSizeReady(overrideWidth, overrideHeight);
	  } else {
	    target.getSize(this);
	  }
	  
这里会根据有没有强制设置图片宽度来分为两部分：

* 设置了，直接调用 onSizeReady
* 没设置，会去调用 target.getSize 。做的事情就是给 view 添加 addOnPreDrawListener 。这样的话，在绘制之前获取到了 view 的宽高，然后再回调 onSizeReady

所以，说到底，最后都是会调用 onSizeReady 的。

``` java
@Override
public synchronized void onSizeReady(int width, int height) {
  stateVerifier.throwIfRecycled();
  if (IS_VERBOSE_LOGGABLE) {
    logV("Got onSizeReady in " + LogTime.getElapsedMillis(startTime));
  }
  if (status != Status.WAITING_FOR_SIZE) {
    return;
  }
  status = Status.RUNNING;

  float sizeMultiplier = requestOptions.getSizeMultiplier();
  this.width = maybeApplySizeMultiplier(width, sizeMultiplier);
  this.height = maybeApplySizeMultiplier(height, sizeMultiplier);

  if (IS_VERBOSE_LOGGABLE) {
    logV("finished setup for calling load in " + LogTime.getElapsedMillis(startTime));
  }
  loadStatus =
      engine.load(
          glideContext,
          model,
          requestOptions.getSignature(),
          this.width,
          this.height,
          requestOptions.getResourceClass(),
          transcodeClass,
          priority,
          requestOptions.getDiskCacheStrategy(),
          requestOptions.getTransformations(),
          requestOptions.isTransformationRequired(),
          requestOptions.isScaleOnlyOrNoTransform(),
          requestOptions.getOptions(),
          requestOptions.isMemoryCacheable(),
          requestOptions.getUseUnlimitedSourceGeneratorsPool(),
          requestOptions.getUseAnimationPool(),
          requestOptions.getOnlyRetrieveFromCache(),
          this,
          callbackExecutor);

  // This is a hack that's only useful for testing right now where loads complete synchronously
  // even though under any executor running on any thread but the main thread, the load would
  // have completed asynchronously.
  if (status != Status.RUNNING) {
    loadStatus = null;
  }
  if (IS_VERBOSE_LOGGABLE) {
    logV("finished onSizeReady in " + LogTime.getElapsedMillis(startTime));
  }
}
```

在 onSizeReady 中将状态更改为 Status.RUNNING ，并调用 engine 的 load() 方法。

Engine
----
``` java
public <R> LoadStatus load(
    GlideContext glideContext,
    Object model,
    Key signature,
    int width,
    int height,
    Class<?> resourceClass,
    Class<R> transcodeClass,
    Priority priority,
    DiskCacheStrategy diskCacheStrategy,
    Map<Class<?>, Transformation<?>> transformations,
    boolean isTransformationRequired,
    boolean isScaleOnlyOrNoTransform,
    Options options,
    boolean isMemoryCacheable,
    boolean useUnlimitedSourceExecutorPool,
    boolean useAnimationPool,
    boolean onlyRetrieveFromCache,
    ResourceCallback cb,
    Executor callbackExecutor) {
  long startTime = VERBOSE_IS_LOGGABLE ? LogTime.getLogTime() : 0;
  // 构造此图片的缓存 key
  EngineKey key =
      keyFactory.buildKey(
          model,
          signature,
          width,
          height,
          transformations,
          resourceClass,
          transcodeClass,
          options);

  EngineResource<?> memoryResource;
  synchronized (this) {
    memoryResource = loadFromMemory(key, isMemoryCacheable, startTime);
    // 查看有没有图片缓存，如果没有的话就开启新的任务加载
    if (memoryResource == null) {
      return waitForExistingOrStartNewJob(
          glideContext,
          model,
          signature,
          width,
          height,
          resourceClass,
          transcodeClass,
          priority,
          diskCacheStrategy,
          transformations,
          isTransformationRequired,
          isScaleOnlyOrNoTransform,
          options,
          isMemoryCacheable,
          useUnlimitedSourceExecutorPool,
          useAnimationPool,
          onlyRetrieveFromCache,
          cb,
          callbackExecutor,
          key,
          startTime);
    }
  }

  // Avoid calling back while holding the engine lock, doing so makes it easier for callers to
  // deadlock.
  // 如果有的话，直接回调 SingleRequest 的 onResourceReady 方法
  cb.onResourceReady(memoryResource, DataSource.MEMORY_CACHE);
  return null;
}
```

在 load 中，Glide 会先去取缓存，当缓存中不存在的时候就准备使用新创建一个任务来加载图片。我们这里就当作是第一次加载图片了，所以跟进 waitForExistingOrStartNewJob 方法中看看。

``` java
private <R> LoadStatus waitForExistingOrStartNewJob(
    GlideContext glideContext,
    Object model,
    Key signature,
    int width,
    int height,
    Class<?> resourceClass,
    Class<R> transcodeClass,
    Priority priority,
    DiskCacheStrategy diskCacheStrategy,
    Map<Class<?>, Transformation<?>> transformations,
    boolean isTransformationRequired,
    boolean isScaleOnlyOrNoTransform,
    Options options,
    boolean isMemoryCacheable,
    boolean useUnlimitedSourceExecutorPool,
    boolean useAnimationPool,
    boolean onlyRetrieveFromCache,
    ResourceCallback cb,
    Executor callbackExecutor,
    EngineKey key,
    long startTime) {

  EngineJob<?> current = jobs.get(key, onlyRetrieveFromCache);
  if (current != null) {
    current.addCallback(cb, callbackExecutor);
    if (VERBOSE_IS_LOGGABLE) {
      logWithTimeAndKey("Added to existing load", startTime, key);
    }
    return new LoadStatus(cb, current);
  }

  EngineJob<R> engineJob =
      engineJobFactory.build(
          key,
          isMemoryCacheable,
          useUnlimitedSourceExecutorPool,
          useAnimationPool,
          onlyRetrieveFromCache);

  DecodeJob<R> decodeJob =
      decodeJobFactory.build(
          glideContext,
          model,
          key,
          signature,
          width,
          height,
          resourceClass,
          transcodeClass,
          priority,
          diskCacheStrategy,
          transformations,
          isTransformationRequired,
          isScaleOnlyOrNoTransform,
          onlyRetrieveFromCache,
          options,
          engineJob);

  jobs.put(key, engineJob);

  engineJob.addCallback(cb, callbackExecutor);
  engineJob.start(decodeJob);

  if (VERBOSE_IS_LOGGABLE) {
    logWithTimeAndKey("Started new load", startTime, key);
  }
  return new LoadStatus(cb, engineJob);
}
```

上面方法中创建了两个对象，一个是 DecodeJob、一个是 EngineJob。它们之间的关系是，EngineJob 内部维护了线程池，用来管理资源加载，已经当资源加载完毕的时候通知回调。 DecodeJob 继承了 Runnable，是线程池当中的一个任务。就像上面那样，我们通过调用 engineJob.start(decodeJob) 来开始执行图片加载的任务。

EngineJob
----
``` java
public synchronized void start(DecodeJob<R> decodeJob) {
  this.decodeJob = decodeJob;
  GlideExecutor executor =
      decodeJob.willDecodeFromCache() ? diskCacheExecutor : getActiveSourceExecutor();
  executor.execute(decodeJob);
}
``` 

执行 decodeJob 。所以直接看 DecodeJob 的 run 方法。

DecodeJob
----
``` java
@Override
public void run() {
  // This should be much more fine grained, but since Java's thread pool implementation silently
  // swallows all otherwise fatal exceptions, this will at least make it obvious to developers
  // that something is failing.
  GlideTrace.beginSectionFormat("DecodeJob#run(model=%s)", model);
  // Methods in the try statement can invalidate currentFetcher, so set a local variable here to
  // ensure that the fetcher is cleaned up either way.
  DataFetcher<?> localFetcher = currentFetcher;
  try {
    if (isCancelled) {
      notifyFailed();
      return;
    }
    runWrapped();
  } catch (CallbackException e) {
    // If a callback not controlled by Glide throws an exception, we should avoid the Glide
    // specific debug logic below.
    throw e;
  } catch (Throwable t) {
    // Catch Throwable and not Exception to handle OOMs. Throwables are swallowed by our
    // usage of .submit() in GlideExecutor so we're not silently hiding crashes by doing this. We
    // are however ensuring that our callbacks are always notified when a load fails. Without this
    // notification, uncaught throwables never notify the corresponding callbacks, which can cause
    // loads to silently hang forever, a case that's especially bad for users using Futures on
    // background threads.
    if (Log.isLoggable(TAG, Log.DEBUG)) {
      Log.d(
          TAG,
          "DecodeJob threw unexpectedly" + ", isCancelled: " + isCancelled + ", stage: " + stage,
          t);
    }
    // When we're encoding we've already notified our callback and it isn't safe to do so again.
    if (stage != Stage.ENCODE) {
      throwables.add(t);
      notifyFailed();
    }
    if (!isCancelled) {
      throw t;
    }
    throw t;
  } finally {
    // Keeping track of the fetcher here and calling cleanup is excessively paranoid, we call
    // close in all cases anyway.
    if (localFetcher != null) {
      localFetcher.cleanup();
    }
    GlideTrace.endSection();
  }
}
```

在 run 方法中，当前任务没有被取消的话，会进入到 runWrapped() 方法。

``` java
private void runWrapped() {
  switch (runReason) {
    case INITIALIZE: // 一进来是 INITIALIZE 状态的
      stage = getNextStage(Stage.INITIALIZE); // 获取 Stage.INITIALIZE 的下一步
      currentGenerator = getNextGenerator(); // 根据获取到的 stage 来选择不同的 Generator
      runGenerators(); // 运行 Generator
      break;
    case SWITCH_TO_SOURCE_SERVICE:
      runGenerators();
      break;
    case DECODE_DATA:
      decodeFromRetrievedData();
      break;
    default:
      throw new IllegalStateException("Unrecognized run reason: " + runReason);
  }
}
``` 

这里一开始进入的时候，runReason 是 INITIALIZE 状态的。在 getNextStage 中，因为没有图片缓存所以得到是的 Stage.SOURCE 。那么就接着到了 getNextGenerator 。

``` java
private DataFetcherGenerator getNextGenerator() {
  switch (stage) {
    case RESOURCE_CACHE:
      return new ResourceCacheGenerator(decodeHelper, this);
    case DATA_CACHE:
      return new DataCacheGenerator(decodeHelper, this);
    case SOURCE:
      return new SourceGenerator(decodeHelper, this);
    case FINISHED:
      return null;
    default:
      throw new IllegalStateException("Unrecognized stage: " + stage);
  }
}
```

stage 是 SOURCE ，所以获取到的就是 SourceGenerator 。接着就调用 runGenerators() 。

``` java
private void runGenerators() {
  currentThread = Thread.currentThread();
  startFetchTime = LogTime.getLogTime();
  boolean isStarted = false;
  while (!isCancelled
      && currentGenerator != null
      && !(isStarted = currentGenerator.startNext())) { // 这里执行 currentGenerator.startNext
    stage = getNextStage(stage);
    currentGenerator = getNextGenerator();

    if (stage == Stage.SOURCE) {
      reschedule();
      return;
    }
  }
  // We've run out of stages and generators, give up.
  if ((stage == Stage.FINISHED || isCancelled) && !isStarted) {
    notifyFailed();
  }

  // Otherwise a generator started a new load and we expect to be called back in
  // onDataFetcherReady.
}
```

会先去执行 currentGenerator.startNext() 。所以接着就跳转到 SourceGenerator.startNext() 。

SourceGenerator
----
``` java
@Override
public boolean startNext() {
  // 一开始肯定没有数据来缓存的，所以往下走
  if (dataToCache != null) {
    Object data = dataToCache;
    dataToCache = null;
    cacheData(data);
  }

  if (sourceCacheGenerator != null && sourceCacheGenerator.startNext()) {
    return true;
  }
  sourceCacheGenerator = null;

  loadData = null;
  boolean started = false;
  while (!started && hasNextModelLoader()) {
    // 使用 DecodeHelper 的 getLoadData() 方法从注册的映射表中找出当前的图片类型对应的 ModelLoader；
    loadData = helper.getLoadData().get(loadDataListIndex++);
    if (loadData != null
        && (helper.getDiskCacheStrategy().isDataCacheable(loadData.fetcher.getDataSource())
            || helper.hasLoadPath(loadData.fetcher.getDataClass()))) {
      started = true;
      // 这里调用 ModelLoader 中的 fetcher 去加载数据
      loadData.fetcher.loadData(helper.getPriority(), this);
    }
  }
  return started;
}
```

因为之前我们的想法是加载网络上的 url 图片，所以这里的 loadData 就对应着 HttpGlideUrlLoader
， fetcher 就是 HttpUrlFetcher 。

HttpUrlFetcher
----
``` java
@Override
public void loadData(
    @NonNull Priority priority, @NonNull DataCallback<? super InputStream> callback) {
  long startTime = LogTime.getLogTime();
  try {
    // 在这里获取网络上的图片数据
    InputStream result = loadDataWithRedirects(glideUrl.toURL(), 0, null, glideUrl.getHeaders());
    // 回调 onDataReady
    callback.onDataReady(result);
  } catch (IOException e) {
    if (Log.isLoggable(TAG, Log.DEBUG)) {
      Log.d(TAG, "Failed to load data for url", e);
    }
    callback.onLoadFailed(e);
  } finally {
    if (Log.isLoggable(TAG, Log.VERBOSE)) {
      Log.v(TAG, "Finished http url fetcher fetch in " + LogTime.getElapsedMillis(startTime));
    }
  }
}
```

loadDataWithRedirects 中会去调用 HttpURLConnection 加载网络上的图片数据。

加载完之后，会回调 onDataReady 方法。这个回调一直从 HttpUrlFetcher 中一直回调到 SourceGenerator 中。所以下面就来看看 SourceGenerator.onDataReady

SourceGenerator
----
``` java
@Override
public void onDataReady(Object data) {
  DiskCacheStrategy diskCacheStrategy = helper.getDiskCacheStrategy();
  if (data != null && diskCacheStrategy.isDataCacheable(loadData.fetcher.getDataSource())) {
    dataToCache = data;
    // We might be being called back on someone else's thread. Before doing anything, we should
    // reschedule to get back onto Glide's thread.
    cb.reschedule();
  } else {
    cb.onDataFetcherReady(
        loadData.sourceKey,
        data,
        loadData.fetcher,
        loadData.fetcher.getDataSource(),
        originalKey);
  }
}
```

在 onDataReady 中，会去判断如果 data 不为空并且磁盘缓存可以缓存的情况下，会调用 cb.reschedule(); 。这其实是调用了 DecodeJob 的 reschedule 方法。

DecodeJob
----
``` java
@Override
public void reschedule() {
  runReason = RunReason.SWITCH_TO_SOURCE_SERVICE;
  callback.reschedule(this);
}
```

在这里，设置 runReason 为 RunReason.SWITCH_TO_SOURCE_SERVICE ，这很关键，在下面代码中会用到。然后再调用 callback.reschedule(this) 。其实就是调用了 EngineJob 的 reschedule 方法。

EngineJob
----
``` java
@Override
public void reschedule(DecodeJob<?> job) {
  // Even if the job is cancelled here, it still needs to be scheduled so that it can clean itself
  // up.
  getActiveSourceExecutor().execute(job);
}
```

reschedule 方法摆明了就是让 DecodeJob 把 run 方法再跑一遍。之前说过，DecodeJob 的 run 方法里面大部分的逻辑其实是在 runWrapped 中的。

DecodeJob
----
``` java
private void runWrapped() {
  switch (runReason) {
    case INITIALIZE:
      stage = getNextStage(Stage.INITIALIZE);
      currentGenerator = getNextGenerator();
      runGenerators();
      break;
    case SWITCH_TO_SOURCE_SERVICE:
      runGenerators();
      break;
    case DECODE_DATA:
      decodeFromRetrievedData();
      break;
    default:
      throw new IllegalStateException("Unrecognized run reason: " + runReason);
  }
}
```

这代码很熟悉，之前我们的流程到过这里。不同的是，之前的 runReason 是 INITIALIZE 。而现在的 runReason 是 SWITCH_TO_SOURCE_SERVICE 。

接着 SWITCH_TO_SOURCE_SERVICE 的逻辑是直接调用 runGenerators 方法。

``` java
private void runGenerators() {
  currentThread = Thread.currentThread();
  startFetchTime = LogTime.getLogTime();
  boolean isStarted = false;
  while (!isCancelled
      && currentGenerator != null
      && !(isStarted = currentGenerator.startNext())) {
    stage = getNextStage(stage);
    currentGenerator = getNextGenerator();

    if (stage == Stage.SOURCE) {
      reschedule();
      return;
    }
  }
  // We've run out of stages and generators, give up.
  if ((stage == Stage.FINISHED || isCancelled) && !isStarted) {
    notifyFailed();
  }

  // Otherwise a generator started a new load and we expect to be called back in
  // onDataFetcherReady.
}
```

这里的 currentGenerator 还是之前的 SourceGenerator ，所以还是调用 SourceGenerator.startNext 。

SourceGenerator
----
``` java
@Override
public boolean startNext() {
  // 不同的是，这里的 dataToCache 不再是空的了，而是之前从网络上下载获取到的 InputStream
  // 所以这里会去走创建缓存的逻辑
  if (dataToCache != null) {
    Object data = dataToCache;
    dataToCache = null;
    cacheData(data);
  }

  if (sourceCacheGenerator != null && sourceCacheGenerator.startNext()) {
    return true;
  }
  sourceCacheGenerator = null;

  loadData = null;
  boolean started = false;
  while (!started && hasNextModelLoader()) {
    loadData = helper.getLoadData().get(loadDataListIndex++);
    if (loadData != null
        && (helper.getDiskCacheStrategy().isDataCacheable(loadData.fetcher.getDataSource())
            || helper.hasLoadPath(loadData.fetcher.getDataClass()))) {
      started = true;
      loadData.fetcher.loadData(helper.getPriority(), this);
    }
  }
  return started;
}
```

这次调用 SourceGenerator.startNext 其实是建立磁盘缓存，直接来看 cacheData 方法。

``` java
private void cacheData(Object dataToCache) {
  long startTime = LogTime.getLogTime();
  try {
    // 建立缓存
    Encoder<Object> encoder = helper.getSourceEncoder(dataToCache);
    DataCacheWriter<Object> writer =
        new DataCacheWriter<>(encoder, dataToCache, helper.getOptions());
    originalKey = new DataCacheKey(loadData.sourceKey, helper.getSignature());
    helper.getDiskCache().put(originalKey, writer);
    if (Log.isLoggable(TAG, Log.VERBOSE)) {
      Log.v(
          TAG,
          "Finished encoding source to cache"
              + ", key: "
              + originalKey
              + ", data: "
              + dataToCache
              + ", encoder: "
              + encoder
              + ", duration: "
              + LogTime.getElapsedMillis(startTime));
    }
  } finally {
    loadData.fetcher.cleanup();
  }
  // 注意，这里 sourceCacheGenerator 创建一个对象，所以 sourceCacheGenerator 不再是 null 
  sourceCacheGenerator =
      new DataCacheGenerator(Collections.singletonList(loadData.sourceKey), helper, this);
}
```

这里的主要逻辑是构建一个用于将数据缓存到磁盘上面的 DataCacheGenerator。DataCacheGenerator 的流程基本与 SourceGenerator 一致，也就是根据资源文件的类型找到 ModelLoader，然后使用 DataFetcher 加载缓存的资源。与之前不同的是，这次是用 DataFecher 来加载 File 类型的资源。也就是说，当我们从网络中拿到了数据之后 Glide 会先将其缓存到磁盘上面，然后再从磁盘上面读取图片并将其显示到控件上面。所以，当从网络打开了输入流之后 SourceGenerator 的任务基本结束了，而后的显示的任务都由 DataCacheGenerator 来完成。

再回过头来看看 SourceGenerator.startNext 方法，在 cacheData 后面会对 sourceCacheGenerator 进行判断。由于上面已经把 sourceCacheGenerator 对象 new 出来了。所以接着就直接走 DataCacheGenerator 的 startNext 方法了。所以上面这段话就很好理解了。

	  if (sourceCacheGenerator != null && sourceCacheGenerator.startNext()) {
	    return true;
	  }

那么，这里我们对 DataCacheGenerator 的逻辑就省略了。DataCacheGenerator 中的流程最终会走到 
ByteBufferFetcher 。

之前的 SourceGenerator 对应着 HttpUrlFetcher ，而 DataCacheGenerator 对应着 ByteBufferFetcher 。

ByteBufferFetcher
----
``` java
@Override
public void loadData(
    @NonNull Priority priority, @NonNull DataCallback<? super ByteBuffer> callback) {
  ByteBuffer result;
  try {
    // 把图片的字节流再从磁盘缓存中读取出来
    result = ByteBufferUtil.fromFile(file);
  } catch (IOException e) {
    if (Log.isLoggable(TAG, Log.DEBUG)) {
      Log.d(TAG, "Failed to obtain ByteBuffer for file", e);
    }
    callback.onLoadFailed(e);
    return;
  }
  // 回调给 DataCacheGenerator 的 onDataReady 方法
  callback.onDataReady(result);
}
```

磁盘缓存好之后，再从文件中读取图片的字节流。回调给 DataCacheGenerator

DataCacheGenerator
----
``` java
@Override
public void onDataReady(Object data) {
  cb.onDataFetcherReady(sourceKey, data, loadData.fetcher, DataSource.DATA_DISK_CACHE, sourceKey);
}
```

DataCacheGenerator 会回调 DecodeJob 的 onDataFetcherReady 方法。

DecodeJob
----
``` java
@Override
public void onDataFetcherReady(
    Key sourceKey, Object data, DataFetcher<?> fetcher, DataSource dataSource, Key attemptedKey) {
  this.currentSourceKey = sourceKey;
  this.currentData = data;
  this.currentFetcher = fetcher;
  this.currentDataSource = dataSource;
  this.currentAttemptingKey = attemptedKey;
  if (Thread.currentThread() != currentThread) {
    runReason = RunReason.DECODE_DATA;
    callback.reschedule(this);
  } else {
    GlideTrace.beginSection("DecodeJob.decodeFromRetrievedData");
    try {
      decodeFromRetrievedData();
    } finally {
      GlideTrace.endSection();
    }
  }
}
``` 

runReason 已经改成 RunReason.DECODE_DATA ，说明已经进行到解码图片数据的环节了。

接着调用 decodeFromRetrievedData 。

``` java
private void decodeFromRetrievedData() {
  if (Log.isLoggable(TAG, Log.VERBOSE)) {
    logWithTimeAndKey(
        "Retrieved data",
        startFetchTime,
        "data: "
            + currentData
            + ", cache key: "
            + currentSourceKey
            + ", fetcher: "
            + currentFetcher);
  }
  Resource<R> resource = null;
  try {
    resource = decodeFromData(currentFetcher, currentData, currentDataSource);
  } catch (GlideException e) {
    e.setLoggingDetails(currentAttemptingKey, currentDataSource);
    throwables.add(e);
  }
  // 图片资源获取到后，通知已经任务完成
  if (resource != null) {
    notifyEncodeAndRelease(resource, currentDataSource);
  } else {
    runGenerators();
  }
}
```

这里我们可以看下，当 resource 最终获取到后，是通过 notifyEncodeAndRelease 来通知任务完成的。这在后面的代码解析中会讲到。

现在，我们就来看看关键的逻辑，接着调用 decodeFromData 。

``` java
private <Data> Resource<R> decodeFromData(
    DataFetcher<?> fetcher, Data data, DataSource dataSource) throws GlideException {
  try {
    if (data == null) {
      return null;
    }
    long startTime = LogTime.getLogTime();
    Resource<R> result = decodeFromFetcher(data, dataSource);
    if (Log.isLoggable(TAG, Log.VERBOSE)) {
      logWithTimeAndKey("Decoded result " + result, startTime);
    }
    return result;
  } finally {
    fetcher.cleanup();
  }
}
```

调用 decodeFromFetcher 方法。
	
``` java
@SuppressWarnings("unchecked")
private <Data> Resource<R> decodeFromFetcher(Data data, DataSource dataSource)
    throws GlideException {
  LoadPath<Data, ?, R> path = decodeHelper.getLoadPath((Class<Data>) data.getClass());
  return runLoadPath(data, dataSource, path);
}
```

调用了 runLoadPath 。

``` java
private <Data, ResourceType> Resource<R> runLoadPath(
    Data data, DataSource dataSource, LoadPath<Data, ResourceType, R> path)
    throws GlideException {
  Options options = getOptionsWithHardwareConfig(dataSource);
  DataRewinder<Data> rewinder = glideContext.getRegistry().getRewinder(data);
  try {
    // ResourceType in DecodeCallback below is required for compilation to work with gradle.
    return path.load(
        rewinder, options, width, height, new DecodeCallback<ResourceType>(dataSource));
  } finally {
    rewinder.cleanup();
  }
}
``` 

调用 path.load 方法。

LoadPath
----
``` java
public Resource<Transcode> load(
    DataRewinder<Data> rewinder,
    @NonNull Options options,
    int width,
    int height,
    DecodePath.DecodeCallback<ResourceType> decodeCallback)
    throws GlideException {
  List<Throwable> throwables = Preconditions.checkNotNull(listPool.acquire());
  try {
    return loadWithExceptionList(rewinder, options, width, height, decodeCallback, throwables);
  } finally {
    listPool.release(throwables);
  }
}
``` 

直接调用 loadWithExceptionList 方法。

``` java
private Resource<Transcode> loadWithExceptionList(
    DataRewinder<Data> rewinder,
    @NonNull Options options,
    int width,
    int height,
    DecodePath.DecodeCallback<ResourceType> decodeCallback,
    List<Throwable> exceptions)
    throws GlideException {
  Resource<Transcode> result = null;
  //noinspection ForLoopReplaceableByForEach to improve perf
  for (int i = 0, size = decodePaths.size(); i < size; i++) {
    DecodePath<Data, ResourceType, Transcode> path = decodePaths.get(i);
    try {
      result = path.decode(rewinder, width, height, options, decodeCallback);
    } catch (GlideException e) {
      exceptions.add(e);
    }
    if (result != null) {
      break;
    }
  }

  if (result == null) {
    throw new GlideException(failureMessage, new ArrayList<>(exceptions));
  }

  return result;
}
```

然后会调用 DecodePath 的 decode 方法。

DecodePath
----
``` java
public Resource<Transcode> decode(
    DataRewinder<DataType> rewinder,
    int width,
    int height,
    @NonNull Options options,
    DecodeCallback<ResourceType> callback)
    throws GlideException {
  Resource<ResourceType> decoded = decodeResource(rewinder, width, height, options);
  Resource<ResourceType> transformed = callback.onResourceDecoded(decoded);
  return transcoder.transcode(transformed, options);
}
```

在 decode 中做了三件事：

1. decodeResource 将原始数据转换成我们原始图片的过程;
2. callback.onResourceDecoded 是当得到了原始图片之后对图片继续处理过程;
3. transcoder.transcode 会使用 BitmapDrawableTranscoder 包装一层，即对 Drawable 进行延迟初始化处理。

那么我们接着跟进 decodeResource 方法。

``` java
@NonNull
private Resource<ResourceType> decodeResource(
    DataRewinder<DataType> rewinder, int width, int height, @NonNull Options options)
    throws GlideException {
  List<Throwable> exceptions = Preconditions.checkNotNull(listPool.acquire());
  try {
    return decodeResourceWithList(rewinder, width, height, options, exceptions);
  } finally {
    listPool.release(exceptions);
  }
}
```

主要逻辑在 decodeResourceWithList 中。
	
``` java
@NonNull
private Resource<ResourceType> decodeResourceWithList(
    DataRewinder<DataType> rewinder,
    int width,
    int height,
    @NonNull Options options,
    List<Throwable> exceptions)
    throws GlideException {
  Resource<ResourceType> result = null;
  //noinspection ForLoopReplaceableByForEach to improve perf
  for (int i = 0, size = decoders.size(); i < size; i++) {
    ResourceDecoder<DataType, ResourceType> decoder = decoders.get(i);
    try {
      DataType data = rewinder.rewindAndGet();
      if (decoder.handles(data, options)) {
        data = rewinder.rewindAndGet();
        result = decoder.decode(data, width, height, options);
      }
      // Some decoders throw unexpectedly. If they do, we shouldn't fail the entire load path, but
      // instead log and continue. See #2406 for an example.
    } catch (IOException | RuntimeException | OutOfMemoryError e) {
      if (Log.isLoggable(TAG, Log.VERBOSE)) {
        Log.v(TAG, "Failed to decode data for " + decoder, e);
      }
      exceptions.add(e);
    }

    if (result != null) {
      break;
    }
  }

  if (result == null) {
    throw new GlideException(failureMessage, new ArrayList<>(exceptions));
  }
  return result;
}
```

ResourceDecoder 具有多个实现类，比如 BitmapDrawableDecoder、ByteBufferBitmapDecoder等。从名字也可以看出来是用来将一个类型转换成另一个类型的。

在这里会使用 ByteBufferBitmapDecoder 来将 ByteBuffer 专成 Bitmap 。

ByteBufferBitmapDecoder
----
``` java
@Override
public Resource<Bitmap> decode(
    @NonNull ByteBuffer source, int width, int height, @NonNull Options options)
    throws IOException {
  InputStream is = ByteBufferUtil.toStream(source);
  return downsampler.decode(is, width, height, options);
}
```

它最终会在 Downsampler 的 decodeStream() 方法中调用 BitmapFactory 的 decodeStream() 方法来从输入流中得到 Bitmap。

在 Downsampler 内部还会维持一个 BitmapPool ，用来复用 Bitmap 。有兴趣的同学可以看下这一块的代码，这里就不过多展示了。

接下来，就来看看上面 callback.onResourceDecoded 的逻辑。callback.onResourceDecoded 会调用 DecodeJob.onResourceDecoded 方法。

DecodeJob
----
``` java
@Synthetic
@NonNull
<Z> Resource<Z> onResourceDecoded(DataSource dataSource, @NonNull Resource<Z> decoded) {
  @SuppressWarnings("unchecked")
  Class<Z> resourceSubClass = (Class<Z>) decoded.get().getClass();
  Transformation<Z> appliedTransformation = null;
  Resource<Z> transformed = decoded;
  if (dataSource != DataSource.RESOURCE_DISK_CACHE) {
    appliedTransformation = decodeHelper.getTransformation(resourceSubClass);
    transformed = appliedTransformation.transform(glideContext, decoded, width, height);
  }
  // TODO: Make this the responsibility of the Transformation.
  if (!decoded.equals(transformed)) {
    decoded.recycle();
  }

  final EncodeStrategy encodeStrategy;
  final ResourceEncoder<Z> encoder;
  if (decodeHelper.isResourceEncoderAvailable(transformed)) {
    encoder = decodeHelper.getResultEncoder(transformed);
    encodeStrategy = encoder.getEncodeStrategy(options);
  } else {
    encoder = null;
    encodeStrategy = EncodeStrategy.NONE;
  }

  Resource<Z> result = transformed;
  boolean isFromAlternateCacheKey = !decodeHelper.isSourceKey(currentSourceKey);
  if (diskCacheStrategy.isResourceCacheable(
      isFromAlternateCacheKey, dataSource, encodeStrategy)) {
    if (encoder == null) {
      throw new Registry.NoResultEncoderAvailableException(transformed.get().getClass());
    }
    final Key key;
    switch (encodeStrategy) {
      case SOURCE:
        key = new DataCacheKey(currentSourceKey, signature);
        break;
      case TRANSFORMED:
        key =
            new ResourceCacheKey(
                decodeHelper.getArrayPool(),
                currentSourceKey,
                signature,
                width,
                height,
                appliedTransformation,
                resourceSubClass,
                options);
        break;
      default:
        throw new IllegalArgumentException("Unknown strategy: " + encodeStrategy);
    }

    LockedResource<Z> lockedResult = LockedResource.obtain(transformed);
    deferredEncodeManager.init(key, encoder, lockedResult);
    result = lockedResult;
  }
  return result;
}
```

主要的逻辑是根据我们设置的参数进行变化。也就是说，如果我们使用了 centerCrop 等参数，那么这里将会对其进行处理。这里的 Transformation 是一个接口，它的一系列的实现都是对应于 scaleType 等参数的。

到了这里， Glide 所有加载图片、处理图片的逻辑都讲完了。剩下的，就是将图片显示到 ImageView 上面了。

我们再回过头来看之前讲到 DecodeJob.notifyEncodeAndRelease 方法

DecodeJob
----
``` java
private void notifyEncodeAndRelease(Resource<R> resource, DataSource dataSource) {
  if (resource instanceof Initializable) {
    ((Initializable) resource).initialize();
  }

  Resource<R> result = resource;
  LockedResource<R> lockedResource = null;
  if (deferredEncodeManager.hasResourceToEncode()) {
    lockedResource = LockedResource.obtain(resource);
    result = lockedResource;
  }

  notifyComplete(result, dataSource);

  stage = Stage.ENCODE;
  try {
    if (deferredEncodeManager.hasResourceToEncode()) {
      deferredEncodeManager.encode(diskCacheProvider, options);
    }
  } finally {
    if (lockedResource != null) {
      lockedResource.unlock();
    }
  }
  // Call onEncodeComplete outside the finally block so that it's not called if the encode process
  // throws.
  onEncodeComplete();
}
```

来看   notifyComplete(result, dataSource); 方法

``` java
private void notifyComplete(Resource<R> resource, DataSource dataSource) {
  setNotifiedOrThrow();
  callback.onResourceReady(resource, dataSource);
}
```

回调 EngineJob 的 onResourceReady 方法。

EngineJob
----
``` java
@Override
public void onResourceReady(Resource<R> resource, DataSource dataSource) {
  synchronized (this) {
    this.resource = resource;
    this.dataSource = dataSource;
  }
  notifyCallbacksOfResult();
}
```

关键在 notifyCallbacksOfResult 中。

``` java
@Synthetic
void notifyCallbacksOfResult() {
  ResourceCallbacksAndExecutors copy;
  Key localKey;
  EngineResource<?> localResource;
  synchronized (this) {
    stateVerifier.throwIfRecycled();
    if (isCancelled) {
      // TODO: Seems like we might as well put this in the memory cache instead of just recycling
      // it since we've gotten this far...
      resource.recycle();
      release();
      return;
    } else if (cbs.isEmpty()) {
      throw new IllegalStateException("Received a resource without any callbacks to notify");
    } else if (hasResource) {
      throw new IllegalStateException("Already have resource");
    }
    engineResource = engineResourceFactory.build(resource, isCacheable, key, resourceListener);
    // Hold on to resource for duration of our callbacks below so we don't recycle it in the
    // middle of notifying if it synchronously released by one of the callbacks. Acquire it under
    // a lock here so that any newly added callback that executes before the next locked section
    // below can't recycle the resource before we call the callbacks.
    hasResource = true;
    copy = cbs.copy();
    incrementPendingCallbacks(copy.size() + 1);

    localKey = key;
    localResource = engineResource;
  }

  engineJobListener.onEngineJobComplete(this, localKey, localResource);

  for (final ResourceCallbackAndExecutor entry : copy) {
    entry.executor.execute(new CallResourceReady(entry.cb));
  }
  decrementPendingCallbacks();
}
```

在代码的最后，

	 	for (final ResourceCallbackAndExecutor entry : copy) {
	    entry.executor.execute(new CallResourceReady(entry.cb));
	  }

可以看到这里会执行 CallResourceReady 。

CallResourceReady
----
``` java
private class CallResourceReady implements Runnable {

  private final ResourceCallback cb;

  CallResourceReady(ResourceCallback cb) {
    this.cb = cb;
  }

  @Override
  public void run() {
    // Make sure we always acquire the request lock, then the EngineJob lock to avoid deadlock
    // (b/136032534).
    synchronized (cb) {
      synchronized (EngineJob.this) {
        if (cbs.contains(cb)) {
          // Acquire for this particular callback.
          engineResource.acquire();
          callCallbackOnResourceReady(cb);
          removeCallback(cb);
        }
        decrementPendingCallbacks();
      }
    }
  }
}


@Synthetic
@GuardedBy("this")
void callCallbackOnResourceReady(ResourceCallback cb) {
  try {
    // This is overly broad, some Glide code is actually called here, but it's much
    // simpler to encapsulate here than to do so at the actual call point in the
    // Request implementation.
    cb.onResourceReady(engineResource, dataSource);
  } catch (Throwable t) {
    throw new CallbackException(t);
  }
}
```

最后还是用回调调用了 SingleRequest 的 onResourceReady 方法。

SingleRequest
----
``` java
@Override
public synchronized void onResourceReady(Resource<?> resource, DataSource dataSource) {
  stateVerifier.throwIfRecycled();
  loadStatus = null;
  if (resource == null) {
    GlideException exception =
        new GlideException(
            "Expected to receive a Resource<R> with an "
                + "object of "
                + transcodeClass
                + " inside, but instead got null.");
    onLoadFailed(exception);
    return;
  }

  Object received = resource.get();
  if (received == null || !transcodeClass.isAssignableFrom(received.getClass())) {
    releaseResource(resource);
    GlideException exception =
        new GlideException(
            "Expected to receive an object of "
                + transcodeClass
                + " but instead"
                + " got "
                + (received != null ? received.getClass() : "")
                + "{"
                + received
                + "} inside"
                + " "
                + "Resource{"
                + resource
                + "}."
                + (received != null
                    ? ""
                    : " "
                        + "To indicate failure return a null Resource "
                        + "object, rather than a Resource object containing null data."));
    onLoadFailed(exception);
    return;
  }

  if (!canSetResource()) {
    releaseResource(resource);
    // We can't put the status to complete before asking canSetResource().
    status = Status.COMPLETE;
    return;
  }

  onResourceReady((Resource<R>) resource, (R) received, dataSource);
}
```

最后调用 onResourceReady((Resource<R>) resource, (R) received, dataSource);

``` java
private synchronized void onResourceReady(Resource<R> resource, R result, DataSource dataSource) {
  // We must call isFirstReadyResource before setting status.
  boolean isFirstResource = isFirstReadyResource();
  status = Status.COMPLETE;
  this.resource = resource;

  if (glideContext.getLogLevel() <= Log.DEBUG) {
    Log.d(
        GLIDE_TAG,
        "Finished loading "
            + result.getClass().getSimpleName()
            + " from "
            + dataSource
            + " for "
            + model
            + " with size ["
            + width
            + "x"
            + height
            + "] in "
            + LogTime.getElapsedMillis(startTime)
            + " ms");
  }

  isCallingCallbacks = true;
  try {
    boolean anyListenerHandledUpdatingTarget = false;
    if (requestListeners != null) {
      for (RequestListener<R> listener : requestListeners) {
        anyListenerHandledUpdatingTarget |=
            listener.onResourceReady(result, model, target, dataSource, isFirstResource);
      }
    }
    anyListenerHandledUpdatingTarget |=
        targetListener != null
            && targetListener.onResourceReady(result, model, target, dataSource, isFirstResource);
    // 重点在这里！！！！
    if (!anyListenerHandledUpdatingTarget) {
      Transition<? super R> animation = animationFactory.build(dataSource, isFirstResource);
      target.onResourceReady(result, animation);
    }
  } finally {
    isCallingCallbacks = false;
  }

  notifyLoadSuccess();
}
```

发现上面的代码调用了 target.onResourceReady(result, animation);

这里的 target 一般都是 ImageViewTarget 。ImageViewTarget 是个抽象类。

ImageViewTarget
----
``` java
@Override
public void onResourceReady(@NonNull Z resource, @Nullable Transition<? super Z> transition) {
  if (transition == null || !transition.transition(resource, this)) {
    setResourceInternal(resource);
  } else {
    maybeUpdateAnimatable(resource);
  }
}

private void setResourceInternal(@Nullable Z resource) {
  // Order matters here. Set the resource first to make sure that the Drawable has a valid and
  // non-null Callback before starting it.
  setResource(resource);
  maybeUpdateAnimatable(resource);
}
```

setResource(resource) 是抽象方法，我们到子类中看看。我们挑 DrawableImageViewTarget 来看看吧。

DrawableImageViewTarget
----
``` java
@Override
protected void setResource(@Nullable Drawable resource) {
  view.setImageDrawable(resource);
}
```

终于，我们看到了 ImageView.setImageDrawable 来显示图片了，不容易啊。

总结
===
真的没想到，短短的一句 Glide.with(context).load("http://www.xxxx.com/xx.jpg").into(imageView) 代码内部竟然隐藏着如此庞大的逻辑。相信你看完这一系列的文章，对 Glide 会刮目相看吧。

当然，本系列还有很多 Glide 中没讲到的知识点，比如缓存具体的应用等，如果想了解的同学可以自行去阅读下源码。



title: Volley框架源码解析
date: 2016-11-19 21:19:17
categories: Android Blog
tags: [Android,开源框架,源码解析]
---
0001B
=====
在 2013 年的 Google I/O 大会上，Volley 网络通信框架正式发布。Volley 框架被设计为适用于网络请求非常频繁但是数据量并不是特别大的情景，正如它的名字一样。Volley 相比其他网络框架而言，采用了在 Android 2.3 以下使用 HttpClient ，而 Android 2.3 及以上使用 HttpUrlConnection 的方案。这是因为在 Android 2.3 以下时，HttpUrlConnection 并不完善，有很多 bug 存在。因此在 Android 2.3 以下最好使用 HttpClient 来进行网络通信；而在 Android 2.3 及以上，HttpUrlConnection 比起 HttpClient 来说更加简单易用，修复了之前的 bug 。所以在 Android 2.3 及以上我们使用 HttpUrlConnection 来进行网络通信。

除此之外，Volley 框架还具有优先级处理、可扩展性强等特点。虽然现在有 Retrofit 、OkHttp 等十分优秀的网络通信框架，但是深入理解 Volley 框架内部的思想可以大大提高我们自身的技术水平，毕竟仅仅停留在只会使用的阶段是不行的哦。那么，下面就进入我们今天的正题吧！（ ps ：本文篇幅过长，可能会引起不适，请在家长的陪同下观看）

0010B
=====
Volley 使用方法
--------------
在长篇大论地解析 Volley 框架源码之前，我们先来看看平时是怎样使用 Volley 的。（大牛可直接跳过 -_- ）

``` java
RequestQueue mQueue = Volley.newRequestQueue(context);
JsonObjectRequest request = new JsonObjectRequest(url, null,
        new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject jsonObject) {
            	// TODO 
            }
        }, new Response.ErrorListener() {
    @Override
    public void onErrorResponse(VolleyError volleyError) {
		// TODO 
    }
});
mQueue.add(request);
```

我们通过 `Volley.newRequestQueue(context)` 来得到一个请求队列的对象 `mQueue`，在队列中暂存了我们所有 add 进去的 request ，之后一个个取出 request 来进行网络通信。一般来说，在一个应用程序中，只保持一个请求队列的对象。

之后创建了 JsonObjectRequest 对象用来请求 JSON 数据，并把它加入 `mQueue` 的队列中。Volley 框架的使用方法非常简单，并且有多种 request 请求方式可以选择，使用方法都是和上面类似的。

0011B
=====
在这先把 Volley 框架中几个重要的类的作用讲一下，以便看源码时能够更加明白：

* RequestQueue ：这个大家一看都明白，用来缓存 request 的请求队列，根据优先级高低排列；
* Request ：表示网络请求，本身是一个抽象类，子类有 StringRequest 、JsonRequest 、ImageRequest 等；
* Response ：表示网络请求后的响应，也是一个抽象类。内部定义了 Listener 、ErrorListener 接口；
* NetworkResponse ：对返回的 HttpResponse 内容进行了封装，虽然类名和 Response 差不多，但是不是 Response 的子类；
* CacheDispatcher ：一个处理请求缓存的线程。不断从 RequestQueue 中取出 Request ，然后取得该 Request 对应的缓存，若缓存存在就调用 ResponseDelivery 做后续分发处理；如果没有缓存或者缓存失效需要进入 NetworkDispatcher 中从网络上获取结果；
* NetworkDispatcher ：一个处理网络请求的线程。和 CacheDispatcher 类似，从网络上得到响应后调用 ResponseDelivery 做后续分发处理。而且根据需求判断是否需要做缓存处理；
* ResponseDelivery ：用作分发处理。利用 Handler 把结果回调到主线程中，即 Listener 、ErrorListener 接口。主要实现类为 ExecutorDelivery ；
* HttpStack ：主要作用就是发起 Http 请求。子类分为 HurlStack 和 HttpClientStack ，分别对应着 HttpUrlConnection 和 HttpClient ；
* Network ：处理 Stack 发起的 Http 请求，把 Request 转换为 Response ，主要实现类为 BasicNetwork ；
* RetryPolicy ：请求重试策略。主要实现类为 DefaultRetryPolicy ；
* Cache ：网络请求的缓存。在 CacheDispatcher 中获取 Cache ，在 NetworkDispatcher 中判断是否保存 Cache 。主要实现类为 DiskBasedCache ，缓存在磁盘中。

Volley
------
看完了之后，我们就要开始源码解析。我们入手点就是 `Volley.newRequestQueue(context)` 了。

``` java
public class Volley {

    /** 默认的磁盘缓存目录名 */
    private static final String DEFAULT_CACHE_DIR = "volley";

    public static RequestQueue newRequestQueue(Context context, HttpStack stack) {
        File cacheDir = new File(context.getCacheDir(), DEFAULT_CACHE_DIR);
        // 设置 UA
        String userAgent = "volley/0";
        try {
            String packageName = context.getPackageName();
            PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
            userAgent = packageName + "/" + info.versionCode;
        } catch (NameNotFoundException e) {
        }
        // 根据 Android SDK 版本设置 HttpStack ，分为 HurlStack 和 HttpClientStack
        // 分别对应着 HttpUrlConnection 和 HttpClient
        if (stack == null) {
            if (Build.VERSION.SDK_INT >= 9) {
                stack = new HurlStack();
            } else {
                // Prior to Gingerbread, HttpUrlConnection was unreliable.
                // See: http://android-developers.blogspot.com/2011/09/androids-http-clients.html
                stack = new HttpClientStack(AndroidHttpClient.newInstance(userAgent));
            }
        }
        // 得到 network
        Network network = new BasicNetwork(stack);
        // 创建 RequestQueue
        RequestQueue queue = new RequestQueue(new DiskBasedCache(cacheDir), network);
        queue.start();

        return queue;
    }

    public static RequestQueue newRequestQueue(Context context) {
        return newRequestQueue(context, null);
    }
}
```

从上面 Volley 类的源码中可知，Volley 类主要就是用来创建 RequestQueue 的。我们之前使用的 `newRequestQueue(Context context)` 方法最终会调用 `newRequestQueue(Context context, HttpStack stack)` 。Volley 允许我们使用自定义的 HttpStack ，从这也可以看出 Volley 具有很强的扩展性。

RequestQueue
------------
接下来继续跟踪 RequestQueue 构造方法的代码。

``` java
// 默认线程池数量为 4
private static final int DEFAULT_NETWORK_THREAD_POOL_SIZE = 4;

public RequestQueue(Cache cache, Network network) {
    this(cache, network, DEFAULT_NETWORK_THREAD_POOL_SIZE);
}

public RequestQueue(Cache cache, Network network, int threadPoolSize) {
    this(cache, network, threadPoolSize,
            new ExecutorDelivery(new Handler(Looper.getMainLooper())));
}

public RequestQueue(Cache cache, Network network, int threadPoolSize,
        ResponseDelivery delivery) {
    mCache = cache;
    mNetwork = network;
    mDispatchers = new NetworkDispatcher[threadPoolSize];
    mDelivery = delivery;
}
```

在构造方法中创建了 ExecutorDelivery 对象，ExecutorDelivery 中传入的 Handler 为主线程的，方便得到 Response 后回调；NetworkDispatcher[] 数组对象，默认数组的长度为 4 ，也就意味着默认处理请求的线程最多为 4 个。

在 `Volley.newRequestQueue(Context context, HttpStack stack)` 中创建完 RequestQueue 对象 `queue` 之后，还调用了 `queue.start()` 方法。主要用于启动 `queue` 中的 `mCacheDispatcher` 和 `mDispatchers` 。

``` java
/** 请求缓存队列 */
private final PriorityBlockingQueue<Request<?>> mCacheQueue =
    new PriorityBlockingQueue<Request<?>>();

/** 网络请求队列 */
private final PriorityBlockingQueue<Request<?>> mNetworkQueue =
    new PriorityBlockingQueue<Request<?>>();

public void start() {
    stop();  // 确保当前 RequestQueue 中的 mCacheDispatcher 和 mDispatchers[] 是关闭的
    // 创建 mCacheDispatcher ，并且开启
    mCacheDispatcher = new CacheDispatcher(mCacheQueue, mNetworkQueue, mCache, mDelivery);
    mCacheDispatcher.start();

    // 根据 mDispatchers[] 数组的长度创建 networkDispatcher ，并且开启
    for (int i = 0; i < mDispatchers.length; i++) {
        NetworkDispatcher networkDispatcher = new NetworkDispatcher(mNetworkQueue, mNetwork,
                mCache, mDelivery);
        mDispatchers[i] = networkDispatcher;
        networkDispatcher.start();
    }
}

// 关闭当前的 mCacheDispatcher 和 mDispatchers[]
public void stop() {
    if (mCacheDispatcher != null) {
        mCacheDispatcher.quit();
    }
    for (int i = 0; i < mDispatchers.length; i++) {
        if (mDispatchers[i] != null) {
            mDispatchers[i].quit();
        }
    }
}

void finish(Request<?> request) {
    // Remove from the set of requests currently being processed.
    synchronized (mCurrentRequests) {
        // 从 mCurrentRequests 中移除该 request
        mCurrentRequests.remove(request);
    }
    // 如果 request 是可以被缓存的，那么从 mWaitingRequests 中移除，加入到 mCacheQueue 中 	
    if (request.shouldCache()) {
        synchronized (mWaitingRequests) {
            String cacheKey = request.getCacheKey();
            Queue<Request<?>> waitingRequests = mWaitingRequests.remove(cacheKey);
            if (waitingRequests != null) {
                if (VolleyLog.DEBUG) {
                    VolleyLog.v("Releasing %d waiting requests for cacheKey=%s.",
                            waitingRequests.size(), cacheKey);
                }
                // Process all queued up requests. They won't be considered as in flight, but
                // that's not a problem as the cache has been primed by 'request'.
                mCacheQueue.addAll(waitingRequests);
            }
        }
    }
}
```

那么看到这里我们意识到有必要看一下 CacheDispatcher 和 NetworkDispatcher 的代码。我们先暂且放一下，来看看 RequestQueue 的 `add` 方法。`add` 方法就是把 Request 加入到 RequestQueue 中了：

``` java
private final Map<String, Queue<Request<?>>> mWaitingRequests =
        new HashMap<String, Queue<Request<?>>>();

// 当前正在请求的 Set 集合
private final Set<Request<?>> mCurrentRequests = new HashSet<Request<?>>();

public <T> Request<T> add(Request<T> request) {
    // Tag the request as belonging to this queue and add it to the set of current requests.
    request.setRequestQueue(this);
    synchronized (mCurrentRequests) {
        //加入到当前请求队列中
        mCurrentRequests.add(request);
    }

    // 设置序列号，该序列号为 AtomInteger 自增的值
    request.setSequence(getSequenceNumber());
    request.addMarker("add-to-queue");

    // 如果该 request 不该缓存，则直接加入 mNetworkQueue ，跳过下面的步骤
    if (!request.shouldCache()) {
        mNetworkQueue.add(request);
        return request;
    }
    
    synchronized (mWaitingRequests) {
        // 其实 cacheKey 就是 request 的 url
        String cacheKey = request.getCacheKey();
        // 如果该 mWaitingRequests 已经包含了有该 cacheKey
        if (mWaitingRequests.containsKey(cacheKey)) {
            // 得到该 cacheKey 对应的 Queue
            Queue<Request<?>> stagedRequests = mWaitingRequests.get(cacheKey);
            if (stagedRequests == null) {
                stagedRequests = new LinkedList<Request<?>>();
            }
            stagedRequests.add(request);
            // 把该 request 加入到 mWaitingRequests
            mWaitingRequests.put(cacheKey, stagedRequests);
            if (VolleyLog.DEBUG) {
                VolleyLog.v("Request for cacheKey=%s is in flight, putting on hold.", cacheKey);
            }
        } else {
            // 如果没有，那么将该 request 加入到 mCacheQueue 中
            mWaitingRequests.put(cacheKey, null);
            mCacheQueue.add(request);
        }
        return request;
    }
}
```

在 `add(Request<T> request)` 方法中，额外使用了两个集合来维护 Request ，其中 

* mCurrentRequests ：用来维护正在做请求操作的 Request；
* mWaitingRequests ：主要作用是如果当前有一个 Request 正在请求并且是可以缓存的，那么 Volley 会去 mWaitingRequests 中根据该 cacheKey 查询之前有没有一样的 Request 被加入到 mWaitingRequests 中。若有，那么该 Request 就不需要再被缓存了；若没有就加入到 mCacheQueue 中进行后续操作。

现在我们来看看 CacheDispatcher 和 NetworkDispatcher 类的源码。

CacheDispatcher
---------------
首先是 CacheDispatcher 的：

``` java
public class CacheDispatcher extends Thread {

	...... // 省略部分源码

    // 结束当前线程
    public void quit() {
        mQuit = true;
        interrupt();
    }

    @Override
    public void run() {
        if (DEBUG) VolleyLog.v("start new dispatcher");
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

        // 初始化 mCache ，读取磁盘中的缓存文件，加载到 mCache 中的 map 中
        // 会造成线程阻塞，要在子线程中调用
        mCache.initialize();

        while (true) {
            try {
                // 从缓存队列中取出 request ，若没有则会阻塞
                final Request<?> request = mCacheQueue.take();
                request.addMarker("cache-queue-take");

                // 如果该 request 被标记为取消，则跳过该 request ，不分发
                if (request.isCanceled()) {
                    request.finish("cache-discard-canceled");
                    continue;
                }

                // 根据 request 的 url 去获得缓存
                Cache.Entry entry = mCache.get(request.getCacheKey());
                if (entry == null) {
                    request.addMarker("cache-miss");
                    // 没有缓存，把 Request 放入网络请求队列中 
                    mNetworkQueue.put(request);
                    continue;
                }

                // 若缓存失效，也放入网络请求队列中
                if (entry.isExpired()) {
                    request.addMarker("cache-hit-expired");
                    request.setCacheEntry(entry);
                    mNetworkQueue.put(request);
                    continue;
                }

                // 缓存存在，把缓存转换为 Response
                request.addMarker("cache-hit");
                Response<?> response = request.parseNetworkResponse(
                        new NetworkResponse(entry.data, entry.responseHeaders));
                request.addMarker("cache-hit-parsed");
                // 判断缓存是否需要刷新
                if (!entry.refreshNeeded()) {
                    // 不需要刷新就直接让 mDelivery 分发
                    mDelivery.postResponse(request, response);
                } else {
                    // 需要刷新缓存
                    request.addMarker("cache-hit-refresh-needed");
                    request.setCacheEntry(entry);

                    // 先设置一个标志，表明该缓存可以先分发，之后需要重新刷新
                    response.intermediate = true;

                    // 利用 mDelivery 先把 response 分发下去，之后还要把该 request 加入到 mNetworkQueue 重新请求一遍
                    mDelivery.postResponse(request, response, new Runnable() {
                        @Override
                        public void run() {
                            try {
                                mNetworkQueue.put(request);
                            } catch (InterruptedException e) {
                                // Not much we can do about this.
                            }
                        }
                    });
                }

            } catch (InterruptedException e) {
                // We may have been interrupted because it was time to quit.
                if (mQuit) {
                    return;
                }
                continue;
            }
        }
    }

}
```

CacheDispatcher 类主要的代码就如上面所示了，在主要的 `run()` 方法中都添加了注释，阅读起来应该比较简单。那么在这里就贡献一张 CacheDispatcher 类的流程图：

![CacheDispatcher 类的流程图](/uploads/20161119/20161122231354.png)

NetworkDispatcher
-----------------
然后是 NetworkDispatcher 的代码：

``` java
public class NetworkDispatcher extends Thread {

	...... // 省略部分源码

    public void quit() {
        mQuit = true;
        interrupt();
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private void addTrafficStatsTag(Request<?> request) {
        // Tag the request (if API >= 14)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            TrafficStats.setThreadStatsTag(request.getTrafficStatsTag());
        }
    }

    @Override
    public void run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
        Request<?> request;
        while (true) {
            try {
                // 取出 request
                request = mQueue.take();
            } catch (InterruptedException e) {
                // We may have been interrupted because it was time to quit.
                if (mQuit) {
                    return;
                }
                continue;
            }

            try {
                request.addMarker("network-queue-take");

                // 判断是否被取消，和 CacheDispatcher 中的步骤一致
                if (request.isCanceled()) {
                    request.finish("network-discard-cancelled");
                    continue;
                }
                // 设置线程标识
                addTrafficStatsTag(request);

                // 处理网络请求
                NetworkResponse networkResponse = mNetwork.performRequest(request);
                request.addMarker("network-http-complete");
                
                // 如果服务端返回 304 并且已经分发了一个响应，那么不再进行二次分发
                if (networkResponse.notModified && request.hasHadResponseDelivered()) {
                    request.finish("not-modified");
                    continue;
                }

                // 得到 response
                Response<?> response = request.parseNetworkResponse(networkResponse);
                request.addMarker("network-parse-complete");

                // 如果该 request 需要进行缓存，那么保存缓存
                // TODO: Only update cache metadata instead of entire record for 304s.
                if (request.shouldCache() && response.cacheEntry != null) {
                    mCache.put(request.getCacheKey(), response.cacheEntry);
                    request.addMarker("network-cache-written");
                }

                // 标记该 request 对应的 response 正在分发中
                request.markDelivered();
                // 分发该 request 对应的 response
                mDelivery.postResponse(request, response);
            } catch (VolleyError volleyError) {
                // 分发该 request 对应的 error
                parseAndDeliverNetworkError(request, volleyError);
            } catch (Exception e) {
                VolleyLog.e(e, "Unhandled exception %s", e.toString());
                // 分发该 request 对应的 error
                mDelivery.postError(request, new VolleyError(e));
            }
        }
    }

    private void parseAndDeliverNetworkError(Request<?> request, VolleyError error) {
        error = request.parseNetworkError(error);
        mDelivery.postError(request, error);
    }

}
```

同样的，根据 NetworkDispatcher 我们也可以梳理出一张流程图：

![NetworkDispatcher 类的流程图](/uploads/20161119/20161123222635.png)

Request
-------
到这里，我们把目光转向 Request 。Request 是一个抽象类：

``` java
public abstract class Request<T> implements Comparable<Request<T>> {

	...// 省略绝大部分源码-_- !
	
	abstract protected Response<T> parseNetworkResponse(NetworkResponse response);
	
	abstract protected void deliverResponse(T response);
	
	protected VolleyError parseNetworkError(VolleyError volleyError) {
	    return volleyError;
	}
	
	public void deliverError(VolleyError error) {
	    if (mErrorListener != null) {
	        mErrorListener.onErrorResponse(error);
	    }
	}
	
	// request 完成响应分发后调用 
	void finish(final String tag) {
	    if (mRequestQueue != null) {
	        // 跳转到 RequestQueue.finish 方法
	        mRequestQueue.finish(this);
	    }
	    if (MarkerLog.ENABLED) {
	        final long threadId = Thread.currentThread().getId();
	        if (Looper.myLooper() != Looper.getMainLooper()) {
	            // If we finish marking off of the main thread, we need to
	            // actually do it on the main thread to ensure correct ordering.
	            Handler mainThread = new Handler(Looper.getMainLooper());
	            mainThread.post(new Runnable() {
	                @Override
	                public void run() {
	                    mEventLog.add(tag, threadId);
	                    mEventLog.finish(this.toString());
	                }
	            });
	            return;
	        }
	
	        mEventLog.add(tag, threadId);
	        mEventLog.finish(this.toString());
	    } else {
	        long requestTime = SystemClock.elapsedRealtime() - mRequestBirthTime;
	        if (requestTime >= SLOW_REQUEST_THRESHOLD_MS) {
	            VolleyLog.d("%d ms: %s", requestTime, this.toString());
	        }
	    }
	}
	
	@Override
	public int compareTo(Request<T> other) {
	    Priority left = this.getPriority();
	    Priority right = other.getPriority();
	
	    // request 优先级高的比低的排在队列前面，优先被请求
	    // 如果优先级一样，按照 FIFO 的原则排列
	    return left == right ?
	            this.mSequence - other.mSequence :
	            right.ordinal() - left.ordinal();
	}

}
```

Request 实现了 Comparable 接口，这是因为 Request 是有优先级的，优先级高比优先级低的要先响应，排列在前。默认有四种优先级：

``` java
public enum Priority {
    LOW,
    NORMAL,
    HIGH,
    IMMEDIATE
}
```

另外，子类继承 Request 还要实现两个抽象方法：

* parseNetworkResponse ：把 NetworkResponse 转换为合适类型的 Response；
* deliverResponse ：把解析出来的类型分发给监听器回调。

另外，Request 还支持八种请求方式：

``` java
/**
 * Supported request methods.
 */
public interface Method {
    int DEPRECATED_GET_OR_POST = -1;
    int GET = 0;
    int POST = 1;
    int PUT = 2;
    int DELETE = 3;
    int HEAD = 4;
    int OPTIONS = 5;
    int TRACE = 6;
    int PATCH = 7;
}
```

在 Volley 中，Request 的子类众多，有 StringRequest 、JsonObjectRequest(继承自  JsonRequest ) 、JsonArrayRequest(继承自  JsonRequest ) 和 ImageRequest 等。当然这些子类并不能满足全部的场景要求，而这就需要我们开发者自己动手去扩展了。

下面我就分析一下 StringRequest 的源码，其他子类的源码都是类似的，可以回去自行研究。

``` java
public class StringRequest extends Request<String> {

    private final Listener<String> mListener;

    public StringRequest(int method, String url, Listener<String> listener,
            ErrorListener errorListener) {
        super(method, url, errorListener);
        mListener = listener;
    }

    public StringRequest(String url, Listener<String> listener, ErrorListener errorListener) {
        this(Method.GET, url, listener, errorListener);
    }

    @Override
    protected void deliverResponse(String response) {
        // 得到相应的 response 后，回调 Listener 的接口
        mListener.onResponse(response);
    }

    @Override
    protected Response<String> parseNetworkResponse(NetworkResponse response) {
        String parsed;
        try {
            // 把字节数组转化为字符串
            parsed = new String(response.data, HttpHeaderParser.parseCharset(response.headers));(response.headers));
        } catch (UnsupportedEncodingException e) {
            parsed = new String(response.data);
        }
        return Response.success(parsed, HttpHeaderParser.parseCacheHeaders(response));
    }

}
```

我们发现 StringRequest 的源码十分简洁。在 `parseNetworkResponse` 方法中主要把 response 中的 data 转化为对应的 String 类型。然后回调 `Response.success` 即可。

看完了 Request 之后，我们来分析一下 Network 。

Network
-------
Network 是一个接口，里面就一个方法 `performRequest(Request<?> request)` :

``` java
public interface Network {

    public NetworkResponse performRequest(Request<?> request) throws VolleyError;

}
```

光看这个方法的定义就知道这个方法是用来干什么了！就是根据传入的 Request 执行，转换为对应的 NetworkResponse 的，并且该 NetworkResponse 不为空。我们就跳到它的实现类中看看该方法具体是怎么样的。

``` java
public class BasicNetwork implements Network {
    protected static final boolean DEBUG = VolleyLog.DEBUG;

    private static int SLOW_REQUEST_THRESHOLD_MS = 3000;

    private static int DEFAULT_POOL_SIZE = 4096;

    protected final HttpStack mHttpStack;

    protected final ByteArrayPool mPool;

    public BasicNetwork(HttpStack httpStack) {
        // 使用 ByteArrayPool 可以实现复用，节约内存
        this(httpStack, new ByteArrayPool(DEFAULT_POOL_SIZE));
    }

    public BasicNetwork(HttpStack httpStack, ByteArrayPool pool) {
        mHttpStack = httpStack;
        mPool = pool;
    }

    @Override
    public NetworkResponse performRequest(Request<?> request) throws VolleyError {
        long requestStart = SystemClock.elapsedRealtime();
        while (true) {
            HttpResponse httpResponse = null;
            byte[] responseContents = null;
            Map<String, String> responseHeaders = new HashMap<String, String>();
            try {
                // 得到请求头
                Map<String, String> headers = new HashMap<String, String>();
                // 添加缓存头部信息
                addCacheHeaders(headers, request.getCacheEntry());
                httpResponse = mHttpStack.performRequest(request, headers);
                // 得到响应行
                StatusLine statusLine = httpResponse.getStatusLine();
                int statusCode = statusLine.getStatusCode();
                // 转化得到响应头
                responseHeaders = convertHeaders(httpResponse.getAllHeaders());
                // 如果返回的状态码是304（即：HttpStatus.SC_NOT_MODIFIED）
                // 那么说明服务端的数据没有变化，就直接从之前的缓存中取
                if (statusCode == HttpStatus.SC_NOT_MODIFIED) {
                    return new NetworkResponse(HttpStatus.SC_NOT_MODIFIED,
                            request.getCacheEntry() == null ? null : request.getCacheEntry().data,
                            responseHeaders, true);
                }

                // Some responses such as 204s do not have content.  We must check.
                // 有一些响应可能没有内容，比如，所以要判断一下
                if (httpResponse.getEntity() != null) {
                    // 把 entiity 转为 byte[]
                  responseContents = entityToBytes(httpResponse.getEntity());
                } else {
                  // Add 0 byte response as a way of honestly representing a
                  // no-content request.
                  responseContents = new byte[0];
                }

                // 如果该请求的响应时间超过 SLOW_REQUEST_THRESHOLD_MS(即 3000ms) ，会打印相应的日志
                long requestLifetime = SystemClock.elapsedRealtime() - requestStart;
                logSlowRequests(requestLifetime, request, responseContents, statusLine);
                // 响应码不是在 200-299 之间就抛异常
                if (statusCode < 200 || statusCode > 299) {
                    throw new IOException();
                }
                return new NetworkResponse(statusCode, responseContents, responseHeaders, false);
            } catch (SocketTimeoutException e) {
                // 启动重试策略, 超时错误
                attemptRetryOnException("socket", request, new TimeoutError());
            } catch (ConnectTimeoutException e) {
                // 启动重试策略, 超时错误
                attemptRetryOnException("connection", request, new TimeoutError());
            } catch (MalformedURLException e) {
                throw new RuntimeException("Bad URL " + request.getUrl(), e);
            } catch (IOException e) {
                int statusCode = 0;
                NetworkResponse networkResponse = null;
                if (httpResponse != null) {
                    statusCode = httpResponse.getStatusLine().getStatusCode();
                } else {
                    throw new NoConnectionError(e);
                }
                VolleyLog.e("Unexpected response code %d for %s", statusCode, request.getUrl());
                if (responseContents != null) {
                    networkResponse = new NetworkResponse(statusCode, responseContents,
                            responseHeaders, false);
                    if (statusCode == HttpStatus.SC_UNAUTHORIZED ||
                            statusCode == HttpStatus.SC_FORBIDDEN) {
                        // 启动重试策略, 认证错误
                        attemptRetryOnException("auth",
                                request, new AuthFailureError(networkResponse));
                    } else {
                        // TODO: Only throw ServerError for 5xx status codes.
                        throw new ServerError(networkResponse);
                    }
                } else {
                    throw new NetworkError(networkResponse);
                }
            }
        }
    }

    /**
     * 如果请求的响应时间超过 SLOW_REQUEST_THRESHOLD_MS ，就打印相应的日志
     */
    private void logSlowRequests(long requestLifetime, Request<?> request,
            byte[] responseContents, StatusLine statusLine) {
        if (DEBUG || requestLifetime > SLOW_REQUEST_THRESHOLD_MS) {
            VolleyLog.d("HTTP response for request=<%s> [lifetime=%d], [size=%s], " +
                    "[rc=%d], [retryCount=%s]", request, requestLifetime,
                    responseContents != null ? responseContents.length : "null",
                    statusLine.getStatusCode(), request.getRetryPolicy().getCurrentRetryCount());
        }
    }

    /**
     * 进行重试策略，如果不满足重试的条件会抛出异常
     */
    private static void attemptRetryOnException(String logPrefix, Request<?> request,
            VolleyError exception) throws VolleyError {
        RetryPolicy retryPolicy = request.getRetryPolicy();
        int oldTimeout = request.getTimeoutMs();

        try {
            retryPolicy.retry(exception);
        } catch (VolleyError e) {
            request.addMarker(
                    String.format("%s-timeout-giveup [timeout=%s]", logPrefix, oldTimeout));
            throw e;
        }
        request.addMarker(String.format("%s-retry [timeout=%s]", logPrefix, oldTimeout));
    }

    // 在请求行中添加缓存相关
    private void addCacheHeaders(Map<String, String> headers, Cache.Entry entry) {
        // If there's no cache entry, we're done.
        if (entry == null) {
            return;
        }

        if (entry.etag != null) {
            headers.put("If-None-Match", entry.etag);
        }

        if (entry.serverDate > 0) {
            Date refTime = new Date(entry.serverDate);
            headers.put("If-Modified-Since", DateUtils.formatDate(refTime));
        }
    }

    protected void logError(String what, String url, long start) {
        long now = SystemClock.elapsedRealtime();
        VolleyLog.v("HTTP ERROR(%s) %d ms to fetch %s", what, (now - start), url);
    }

    /**把 HttpEntity 的内容读取到 byte[] 中. */
    private byte[] entityToBytes(HttpEntity entity) throws IOException, ServerError {
        PoolingByteArrayOutputStream bytes =
                new PoolingByteArrayOutputStream(mPool, (int) entity.getContentLength());
        byte[] buffer = null;
        try {
            InputStream in = entity.getContent();
            if (in == null) {
                throw new ServerError();
            }
            buffer = mPool.getBuf(1024);
            int count;
            while ((count = in.read(buffer)) != -1) {
                bytes.write(buffer, 0, count);
            }
            return bytes.toByteArray();
        } finally {
            try {
                // Close the InputStream and release the resources by "consuming the content".
                entity.consumeContent();
            } catch (IOException e) {
                // This can happen if there was an exception above that left the entity in
                // an invalid state.
                VolleyLog.v("Error occured when calling consumingContent");
            }
            mPool.returnBuf(buffer);
            bytes.close();
        }
    }

    /**
     * 把 Headers[] 转换为 Map<String, String>.
     */
    private static Map<String, String> convertHeaders(Header[] headers) {
        Map<String, String> result = new HashMap<String, String>();
        for (int i = 0; i < headers.length; i++) {
            result.put(headers[i].getName(), headers[i].getValue());
        }
        return result;
    }
}
```

我们把 BasicNetwork 的源码全部看下来，发现 BasicNetwork 干的事情就如下：

* 利用 HttpStack 执行请求，把响应 HttpResponse 封装为 NetworkResponse ;
* 如果在这过程中出错，会有重试策略。

至于 NetworkResponse 的源码在这里就不分析了，主要是一个相对于 HttpResponse 的封装类，可以自己去看其源码。

得到 NetworkResponse 之后，在 NetworkDispatcher 中经过 Request 的 `parseNetworkResponse` 方法把 NetworkResponse 转化为了 Response 。(具体可参考上面分析的 NetworkDispatcher 和 StringRequest 源码)

那么接下来就把目光转向 Response 吧。

Response
--------
Response 类的源码比较简单，一起来看看：

``` java
public class Response<T> {

    /** 分发响应结果的接口 */
    public interface Listener<T> {
        /** Called when a response is received. */
        public void onResponse(T response);
    }

    /** 分发响应错误的接口 */
    public interface ErrorListener {
        /**
         * Callback method that an error has been occurred with the
         * provided error code and optional user-readable message.
         */
        public void onErrorResponse(VolleyError error);
    }

    /** 通过这个静态方法构造 Response */
    public static <T> Response<T> success(T result, Cache.Entry cacheEntry) {
        return new Response<T>(result, cacheEntry);
    }

    /**
     * 通过这个静态方法构造错误的 Response
     */
    public static <T> Response<T> error(VolleyError error) {
        return new Response<T>(error);
    }

    /** Parsed response, or null in the case of error. */
    public final T result;

    /** Cache metadata for this response, or null in the case of error. */
    public final Cache.Entry cacheEntry;

    /** Detailed error information if <code>errorCode != OK</code>. */
    public final VolleyError error;

    /** True if this response was a soft-expired one and a second one MAY be coming. */
    public boolean intermediate = false;

    /**
     * 是否成功
     */
    public boolean isSuccess() {
        return error == null;
    }


    private Response(T result, Cache.Entry cacheEntry) {
        this.result = result;
        this.cacheEntry = cacheEntry;
        this.error = null;
    }

    private Response(VolleyError error) {
        this.result = null;
        this.cacheEntry = null;
        this.error = error;
    }
}
```

Response 类主要通过 `success` 和 `error` 两个方法分别来构造正确的响应结果和错误的响应结果。另外，在 Response 类中还有 Listener 和 ErrorListener 两个接口。在最终的回调中会使用到它们。

在得到了 Response 之后，就要使用 ResponseDelivery 来分发了。那下面就轮到 ResponseDelivery 了，go on !!

ResponseDelivery
----------------

``` java
public interface ResponseDelivery {
    /**
     * 分发该 request 的 response
     */
    public void postResponse(Request<?> request, Response<?> response);

    /**
     * 分发该 request 的response ，runnable 会在分发之后执行
     */
    public void postResponse(Request<?> request, Response<?> response, Runnable runnable);

    /**
     * 分发该 request 错误的 response
     */
    public void postError(Request<?> request, VolleyError error);
}
```

ResponseDelivery 的接口就定义了三个方法，我们需要在其实现类中看看具体的实现：

``` java
public class ExecutorDelivery implements ResponseDelivery {
    /** 用来分发 Response , 一般都是在主线程中*/
    private final Executor mResponsePoster;

    /**
     * 传入的 Handler 为主线程的
     */
    public ExecutorDelivery(final Handler handler) {
        // Make an Executor that just wraps the handler.
        mResponsePoster = new Executor() {
            @Override
            public void execute(Runnable command) {
                handler.post(command);
            }
        };
    }

    /**
     * Creates a new response delivery interface, mockable version
     * for testing.
     * @param executor For running delivery tasks
     */
    public ExecutorDelivery(Executor executor) {
        mResponsePoster = executor;
    }

    @Override
    public void postResponse(Request<?> request, Response<?> response) {
        postResponse(request, response, null);
    }

    @Override
    public void postResponse(Request<?> request, Response<?> response, Runnable runnable) {
        request.markDelivered();
        request.addMarker("post-response");
        mResponsePoster.execute(new ResponseDeliveryRunnable(request, response, runnable));
    }

    @Override
    public void postError(Request<?> request, VolleyError error) {
        request.addMarker("post-error");
        Response<?> response = Response.error(error);
        mResponsePoster.execute(new ResponseDeliveryRunnable(request, response, null));
    }

    /**
     * A Runnable 用来分发 response 到主线程的回调接口中
     */
    @SuppressWarnings("rawtypes")
    private class ResponseDeliveryRunnable implements Runnable {
        private final Request mRequest;
        private final Response mResponse;
        private final Runnable mRunnable;

        public ResponseDeliveryRunnable(Request request, Response response, Runnable runnable) {
            mRequest = request;
            mResponse = response;
            mRunnable = runnable;
        }

        @SuppressWarnings("unchecked")
        @Override
        public void run() {
            // 如果 request 被标记为取消，那么不用分发
            if (mRequest.isCanceled()) {
                mRequest.finish("canceled-at-delivery");
                return;
            }

            // 根据 mResponse 是否成功来分发到不同的方法
            if (mResponse.isSuccess()) {
                mRequest.deliverResponse(mResponse.result);
            } else {
                mRequest.deliverError(mResponse.error);
            }

            // If this is an intermediate response, add a marker, otherwise we're done
            // and the request can be finished. 
            if (mResponse.intermediate) {
                mRequest.addMarker("intermediate-response");
            } else {
                mRequest.finish("done");
            }

            // 运行 mRunnable
            if (mRunnable != null) {
                mRunnable.run();
            }
       }
    }
}
```

ResponseDelivery 将根据 mResponse 是否成功来调用不同的方法 `mRequest.deliverResponse` 和 `mRequest.deliverError` 。在 `mRequest.deliverResponse` 中会回调 Listener 的 `onResponse` 方法；而在 `mRequest.deliverError` 中会回调 ErrorListener 的 `onErrorResponse` 方法。至此，一个完整的网络请求及响应流程走完了。

HttpStack
---------
现在回过头来看看 Volley 框架中是如何发起网络请求的。在本文的开头中说过，Volley 是会根据 Android 的版本来选择对应的 HttpStack。那么下面我们来深入看一下 HttpStack 的源码。

``` java
public interface HttpStack {
    /**
     * 通过所给的参数执行请求
     */
    public HttpResponse performRequest(Request<?> request, Map<String, String> additionalHeaders)
        throws IOException, AuthFailureError;

}
```

HttpStack 接口中定义的方法就只有一个。我们要分别来看看 HurlStack 和 HttpClientStack 各自的实现。

HurlStack ：

``` java
@Override
public HttpResponse performRequest(Request<?> request, Map<String, String> additionalHeaders)
        throws IOException, AuthFailureError {
    String url = request.getUrl();
    HashMap<String, String> map = new HashMap<String, String>();
    // 把请求头放入 map 中
    map.putAll(request.getHeaders());
    map.putAll(additionalHeaders);
    if (mUrlRewriter != null) {
        String rewritten = mUrlRewriter.rewriteUrl(url);
        if (rewritten == null) {
            throw new IOException("URL blocked by rewriter: " + url);
        }
        url = rewritten;
    }
    URL parsedUrl = new URL(url);
    // 使用 HttpURLConnection 来发起请求
    HttpURLConnection connection = openConnection(parsedUrl, request);
    // 设置请求头
    for (String headerName : map.keySet()) {
        connection.addRequestProperty(headerName, map.get(headerName));
    }
    // 设置请求方法
    setConnectionParametersForRequest(connection, request);
    // Initialize HttpResponse with data from the HttpURLConnection.
    ProtocolVersion protocolVersion = new ProtocolVersion("HTTP", 1, 1);
    int responseCode = connection.getResponseCode();
    if (responseCode == -1) {
        // -1 is returned by getResponseCode() if the response code could not be retrieved.
        // Signal to the caller that something was wrong with the connection.
        throw new IOException("Could not retrieve response code from HttpUrlConnection.");
    }
    // 把响应封装进 response 中
    StatusLine responseStatus = new BasicStatusLine(protocolVersion,
            connection.getResponseCode(), connection.getResponseMessage());
    BasicHttpResponse response = new BasicHttpResponse(responseStatus);
    response.setEntity(entityFromConnection(connection));
    for (Entry<String, List<String>> header : connection.getHeaderFields().entrySet()) {
        if (header.getKey() != null) {
            Header h = new BasicHeader(header.getKey(), header.getValue().get(0));
            response.addHeader(h);
        }
    }
    return response;
}
```

HttpClientStack ：

``` java
@Override
public HttpResponse performRequest(Request<?> request, Map<String, String> additionalHeaders)
        throws IOException, AuthFailureError {
    // 根据请求方法生成对应的 HttpUriRequest
    HttpUriRequest httpRequest = createHttpRequest(request, additionalHeaders);
    // 添加请求头
    addHeaders(httpRequest, additionalHeaders);
    addHeaders(httpRequest, request.getHeaders());
    onPrepareRequest(httpRequest);
    HttpParams httpParams = httpRequest.getParams();
    int timeoutMs = request.getTimeoutMs();
    // TODO: Reevaluate this connection timeout based on more wide-scale
    // data collection and possibly different for wifi vs. 3G.
    HttpConnectionParams.setConnectionTimeout(httpParams, 5000);
    HttpConnectionParams.setSoTimeout(httpParams, timeoutMs);
    return mClient.execute(httpRequest);
}
```

在这里只给出 HurlStack 和 HttpClientStack 的 `performRequest` 方法。我们可以看到 HurlStack 和 HttpClientStack 已经把 HttpUrlConnection 和 HttpClient 封装得很彻底了，以后哪里有需要的地方可以直接使用。

RetryPolicy
-----------
RetryPolicy 接口主要的作用就是定制重试策略，我们从下面的源码可以看出该接口有三个抽象方法：

* getCurrentTimeout ：得到当前超时时间；
* getCurrentRetryCount ：得到当前重试的次数；
* retry ：是否进行重试，其中的 `error` 参数为异常的信息。若在 `retry` 方法中跑出 `error` 异常，那 Volley 就会停止重试。

``` java
public interface RetryPolicy {

    /**
     * Returns the current timeout (used for logging).
     */
    public int getCurrentTimeout();

    /**
     * Returns the current retry count (used for logging).
     */
    public int getCurrentRetryCount();

    /**
     * Prepares for the next retry by applying a backoff to the timeout.
     * @param error The error code of the last attempt.
     * @throws VolleyError In the event that the retry could not be performed (for example if we
     * ran out of attempts), the passed in error is thrown.
     */
    public void retry(VolleyError error) throws VolleyError;
}
```

RetryPolicy 接口有一个默认的实现类 DefaultRetryPolicy ，DefaultRetryPolicy 的构造方法有两个：

``` java
/** The default socket timeout in milliseconds */
public static final int DEFAULT_TIMEOUT_MS = 2500;

/** The default number of retries */
public static final int DEFAULT_MAX_RETRIES = 1;

/** The default backoff multiplier */
public static final float DEFAULT_BACKOFF_MULT = 1f;

public DefaultRetryPolicy() {
    this(DEFAULT_TIMEOUT_MS, DEFAULT_MAX_RETRIES, DEFAULT_BACKOFF_MULT);
}

/**
 * Constructs a new retry policy.
 * @param initialTimeoutMs The initial timeout for the policy.
 * @param maxNumRetries The maximum number of retries.
 * @param backoffMultiplier Backoff multiplier for the policy.
 */
public DefaultRetryPolicy(int initialTimeoutMs, int maxNumRetries, float backoffMultiplier) {
    mCurrentTimeoutMs = initialTimeoutMs;
    mMaxNumRetries = maxNumRetries;
    mBackoffMultiplier = backoffMultiplier;
}
```

从上面可以看到，在 Volley 内部已经有一套默认的参数配置了。当然，你也可以通过自定义的形式来设置重试策略。

``` java
@Override
public void retry(VolleyError error) throws VolleyError {
    // 重试次数自增
    mCurrentRetryCount++;
    // 超时时间自增，mBackoffMultiplier 为超时时间的因子
    mCurrentTimeoutMs += (mCurrentTimeoutMs * mBackoffMultiplier);
    // 如果超过最大次数，抛出异常
    if (!hasAttemptRemaining()) {
        throw error;
    }
}

/**
 * Returns true if this policy has attempts remaining, false otherwise.
 */
protected boolean hasAttemptRemaining() {
    return mCurrentRetryCount <= mMaxNumRetries;
}
```

Cache
-----
分析完了前面这么多的类，终于轮到了最后的 Cache 。Cache 接口中定义了一个内部类 Entry ，还有定义了几个方法：

* get(String key) ：根据传入的 `key` 来获取 entry ；
* put(String key, Entry entry) ：增加或者替换缓存；
* initialize() ：初始化，是耗时的操作，在子线程中调用；
* invalidate(String key, boolean fullExpire) ：根据 `key` 使之对应的缓存失效；
* remove(String key) ：根据 `key` 移除某个缓存；
* clear() ：清空缓存。

``` java
public interface Cache {
    /**
     * Retrieves an entry from the cache.
     * @param key Cache key
     * @return An {@link Entry} or null in the event of a cache miss
     */
    public Entry get(String key);

    /**
     * Adds or replaces an entry to the cache.
     * @param key Cache key
     * @param entry Data to store and metadata for cache coherency, TTL, etc.
     */
    public void put(String key, Entry entry);

    /**
     * Performs any potentially long-running actions needed to initialize the cache;
     * will be called from a worker thread.
     */
    public void initialize();

    /**
     * Invalidates an entry in the cache.
     * @param key Cache key
     * @param fullExpire True to fully expire the entry, false to soft expire
     */
    public void invalidate(String key, boolean fullExpire);

    /**
     * Removes an entry from the cache.
     * @param key Cache key
     */
    public void remove(String key);

    /**
     * Empties the cache.
     */
    public void clear();

}
```

内部类 Entry ，Entry 中有一个属性为 etag ，上面的源码中也有 etag 的身影。如果你对 ETag 不熟悉，可以查看这篇文章[《Etag与HTTP缓存机制》](http://blog.csdn.net/kikikind/article/details/6266101)：

``` java
public static class Entry {
    /** 缓存中数据，即响应中的 body */
    public byte[] data;

    /** HTTP头部的一个定义，允许客户端进行缓存协商 */
    public String etag;

    /** 服务端响应的时间 */
    public long serverDate;

    /** 缓存过期的时间，若小于当前时间则过期 */
    public long ttl;

    /** 缓存的有效时间，若小于当前时间则可以进行刷新操作 */
    public long softTtl;

    /** 响应的头部信息 */
    public Map<String, String> responseHeaders = Collections.emptyMap();

    /** 判断缓存是否有效，若返回 true 则缓存失效. */
    public boolean isExpired() {
        return this.ttl < System.currentTimeMillis();
    }

    /** 判断缓存是否需要刷新 */
    public boolean refreshNeeded() {
        return this.softTtl < System.currentTimeMillis();
    }
}
```

看完了 Cache 接口之后，我们来看一下实现类 DiskBasedCache 。首先是它的构造方法：

``` java
private static final int DEFAULT_DISK_USAGE_BYTES = 5 * 1024 * 1024;

public DiskBasedCache(File rootDirectory, int maxCacheSizeInBytes) {
    mRootDirectory = rootDirectory;
    mMaxCacheSizeInBytes = maxCacheSizeInBytes;
}

public DiskBasedCache(File rootDirectory) {
    this(rootDirectory, DEFAULT_DISK_USAGE_BYTES);
}
```

从构造方法中传入的参数可知，Volley 默认最大磁盘缓存为 5M 。

DiskBasedCache 的 `get(String key)` 方法：

``` java
@Override
public synchronized Entry get(String key) {
    // 得到对应的缓存摘要信息
    CacheHeader entry = mEntries.get(key);
    // if the entry does not exist, return.
    if (entry == null) {
        return null;
    }
    // 得到缓存文件
    File file = getFileForKey(key);
    // CountingInputStream 为自定义的 IO 流，继承自 FilterInputStream
    // 具有记忆已读取的字节数的功能
    CountingInputStream cis = null;
    try {
        cis = new CountingInputStream(new FileInputStream(file));
        CacheHeader.readHeader(cis); // eat header
        // 得到缓存中的数据 data[] 
        byte[] data = streamToBytes(cis, (int) (file.length() - cis.bytesRead));
        return entry.toCacheEntry(data);
    } catch (IOException e) {
        VolleyLog.d("%s: %s", file.getAbsolutePath(), e.toString());
        // 若出错则移除缓存
        remove(key);
        return null;
    } finally {
        if (cis != null) {
            try {
                cis.close();
            } catch (IOException ioe) {
                return null;
            }
        }
    }
}

// 把 url 分成两半，分别得到对应的 hashcode ，拼接后得到对应的文件名
private String getFilenameForKey(String key) {
    int firstHalfLength = key.length() / 2;
    String localFilename = String.valueOf(key.substring(0, firstHalfLength).hashCode());
    localFilename += String.valueOf(key.substring(firstHalfLength).hashCode());
    return localFilename;
}

/**
 * Returns a file object for the given cache key.
 */
public File getFileForKey(String key) {
    return new File(mRootDirectory, getFilenameForKey(key));
}
```

DiskBasedCache 的 `putEntry(String key, CacheHeader entry)` 方法：

``` java
@Override
public synchronized void put(String key, Entry entry) {
    // 检查磁盘空间是否足够，若不够会删除一些缓存文件
    pruneIfNeeded(entry.data.length);
    File file = getFileForKey(key);
    try {
        FileOutputStream fos = new FileOutputStream(file);
        CacheHeader e = new CacheHeader(key, entry);
        e.writeHeader(fos);
        fos.write(entry.data);
        fos.close();
        putEntry(key, e);
        return;
    } catch (IOException e) {
    }
    boolean deleted = file.delete();
    if (!deleted) {
        VolleyLog.d("Could not clean up file %s", file.getAbsolutePath());
    }
}

private void putEntry(String key, CacheHeader entry) {
    // 计算总缓存的大小
    if (!mEntries.containsKey(key)) {
        mTotalSize += entry.size;
    } else {
        CacheHeader oldEntry = mEntries.get(key);
        mTotalSize += (entry.size - oldEntry.size);
    }
    // 增加或者替换缓存
    mEntries.put(key, entry);
}
```

`initialize()` 方法：

``` java
@Override
public synchronized void initialize() {
    if (!mRootDirectory.exists()) {
        if (!mRootDirectory.mkdirs()) {
            VolleyLog.e("Unable to create cache dir %s", mRootDirectory.getAbsolutePath());
        }
        return;
    }

    File[] files = mRootDirectory.listFiles();
    if (files == null) {
        return;
    }
    // 读取缓存文件
    for (File file : files) {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
            // 得到缓存文件的摘要信息
            CacheHeader entry = CacheHeader.readHeader(fis);
            entry.size = file.length();
            // 放入 map 中
            putEntry(entry.key, entry);
        } catch (IOException e) {
            if (file != null) {
               file.delete();
            }
        } finally {
            try {
                if (fis != null) {
                    fis.close();
                }
            } catch (IOException ignored) { }
        }
    }
}
```

`invalidate(String key, boolean fullExpire)` 方法：

``` java
@Override
public synchronized void invalidate(String key, boolean fullExpire) {
    Entry entry = get(key);
    if (entry != null) {
        // 有效时间置零
        entry.softTtl = 0;
        if (fullExpire) {
            entry.ttl = 0;
        }
        put(key, entry);
    }

}
```

`remove(String key)` 和 `clear()` 方法比较简单，就不需要注释了：

```
@Override
public synchronized void remove(String key) {
    boolean deleted = getFileForKey(key).delete();
    removeEntry(key);
    if (!deleted) {
        VolleyLog.d("Could not delete cache entry for key=%s, filename=%s",
                key, getFilenameForKey(key));
    }
}

@Override
public synchronized void clear() {
    File[] files = mRootDirectory.listFiles();
    if (files != null) {
        for (File file : files) {
            file.delete();
        }
    }
    mEntries.clear();
    mTotalSize = 0;
    VolleyLog.d("Cache cleared.");
}
```

0100B
=====
至此，Volley 源码解析差不多已经结束了。基本上在整个 Volley 框架中至关重要的类都讲到了。当然，还有一些 NetworkImageView 、ImageLoader 等源码还没解析。由于本篇文章内容太长了(有史以来写过最长的一篇─=≡Σ((( つ•̀ω•́)つ)，只能等到下次有机会再给大家补上了。

在这还给出了一张整个 Volley 框架大致的网络通信流程图，对上面源码没看懂的童鞋可以参考这张图再看一遍：

![Volley网络通信流程图](/uploads/20161119/20161130214351.png)

最后，只剩下总结了。从头到尾分析了一遍，发现 Volley 真的是一款很优秀的框架，面向接口编程在其中发挥到极致。其中有不少值得我们借鉴的地方，但是 Volley 并不是没有缺点的，对于大文件传输 Volley 就很不擅长，搞不好会 OOM 。另外，在源码中还有不少可以继续优化的地方，有兴趣的同学可以自定义一个属于自己的 Volley 。

好了，如果你对本文哪里有问题或者不懂的地方，欢迎留言一起交流。

0101B
=====
References

* [Volley 源码解析](http://a.codekk.com/detail/Android/grumoon/Volley%20%E6%BA%90%E7%A0%81%E8%A7%A3%E6%9E%90)
* [volley 框架剖析(四） 之HTTPCache设计](http://blog.csdn.net/zoudifei/article/details/45623121)
* [Android Volley完全解析(四)，带你从源码的角度理解Volley  
 ](http://blog.csdn.net/sinyu890807/article/details/17656437)
* [Etag与HTTP缓存机制](http://blog.csdn.net/kikikind/article/details/6266101)
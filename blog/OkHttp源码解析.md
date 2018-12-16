title: OkHttp源码解析
date: 2017-07-25 20:54:57
categories: Android Blog
tags: [Android,开源框架,源码解析]
---
Header
======
注：本文 OkHttp 源码解析基于 v3.8.1 。

OkHttp in GitHub：[https://github.com/square/okhttp](https://github.com/square/okhttp)

现如今，在 Android 开发领域大多数都是选择以 OkHttp 作为网络框架。

然而，简单地会使用 OkHttp 并不能让我们得到满足。更深层次的，我们需要阅读框架的源码，才能用起来得心应手，融会贯通。

An HTTP & HTTP/2 client for Android and Java applications.

这是官网上对于 OkHttp 的介绍，简单明了。同时，也印证了那句经典的话：

Talk is cheap, show me the code.

OkHttp的简单使用方法
==================
OkHttp 使用方法，直接抄官网的 \\(╯-╰)/ 。

GET 请求：

``` java
OkHttpClient client = new OkHttpClient();

String run(String url) throws IOException {
  Request request = new Request.Builder()
      .url(url)
      .build();

  Response response = client.newCall(request).execute();
  return response.body().string();
}
```

POST 请求：

``` java
public static final MediaType JSON
    = MediaType.parse("application/json; charset=utf-8");

OkHttpClient client = new OkHttpClient();

String post(String url, String json) throws IOException {
  RequestBody body = RequestBody.create(JSON, json);
  Request request = new Request.Builder()
      .url(url)
      .post(body)
      .build();
  Response response = client.newCall(request).execute();
  return response.body().string();
}
```

深入源码
=======
在这里，先分析下同步请求的源码，之后再回过头来看异步请求的源码。

Let's go !

同步请求
=======
OkHttpClient
------------
首先创建一个 `OkHttpClient` 对象，那我们看看在构造器中做了什么：

``` java
  public OkHttpClient() {
    this(new Builder());
  }

  OkHttpClient(Builder builder) {
    this.dispatcher = builder.dispatcher; // 分发器
    this.proxy = builder.proxy; // 代理
    this.protocols = builder.protocols; // 协议
    this.connectionSpecs = builder.connectionSpecs;
    this.interceptors = Util.immutableList(builder.interceptors); // 拦截器
    this.networkInterceptors = Util.immutableList(builder.networkInterceptors); // 网络拦截器
    this.eventListenerFactory = builder.eventListenerFactory;
    this.proxySelector = builder.proxySelector; // 代理选择
    this.cookieJar = builder.cookieJar; // cookie
    this.cache = builder.cache; // 缓存
    this.internalCache = builder.internalCache;
    this.socketFactory = builder.socketFactory;

    boolean isTLS = false;
    for (ConnectionSpec spec : connectionSpecs) {
      isTLS = isTLS || spec.isTls();
    }

    if (builder.sslSocketFactory != null || !isTLS) {
      this.sslSocketFactory = builder.sslSocketFactory;
      this.certificateChainCleaner = builder.certificateChainCleaner;
    } else {
      X509TrustManager trustManager = systemDefaultTrustManager();
      this.sslSocketFactory = systemDefaultSslSocketFactory(trustManager);
      this.certificateChainCleaner = CertificateChainCleaner.get(trustManager);
    }

    this.hostnameVerifier = builder.hostnameVerifier;
    this.certificatePinner = builder.certificatePinner.withCertificateChainCleaner(
        certificateChainCleaner);
    this.proxyAuthenticator = builder.proxyAuthenticator;
    this.authenticator = builder.authenticator;
    this.connectionPool = builder.connectionPool; // 连接复用池
    this.dns = builder.dns;
    this.followSslRedirects = builder.followSslRedirects;
    this.followRedirects = builder.followRedirects;
    this.retryOnConnectionFailure = builder.retryOnConnectionFailure;
    this.connectTimeout = builder.connectTimeout; // 连接超时时间
    this.readTimeout = builder.readTimeout; // 读取超时时间
    this.writeTimeout = builder.writeTimeout; // 写入超时时间
    this.pingInterval = builder.pingInterval;
  }
```

在构造器中利用建造者模式来构建 `OkHttpClient` 的对象。当然，如果你想自定义 `OkHttpClient` 配置的话，就要 new 一个 `OkHttpClient.Builder` 来配置自己的参数了。相信大家都干过这种事情了(∩_∩)。

`OkHttpClient` 的构造器中主要是扎堆扎堆的配置，没别的。

之后再调用 `newCall(Request request)` ：

``` java
@Override
public Call newCall(Request request) {
    return RealCall.newRealCall(this, request, false /* for web socket */);
}
```

在方法里面其实是创建了一个 `RealCall` 的对象，那么我们就进入 `RealCall` 中去看看吧。

RealCall
--------
在 `RealCall` 的构造器中只是给一些变量赋值或初始化而已，没什么：

``` java
static RealCall newRealCall(OkHttpClient client, Request originalRequest, boolean forWebSocket) {
    // Safely publish the Call instance to the EventListener.
    RealCall call = new RealCall(client, originalRequest, forWebSocket);
    call.eventListener = client.eventListenerFactory().create(call);
    return call;
}

private RealCall(OkHttpClient client, Request originalRequest, boolean forWebSocket) {
    this.client = client;
    this.originalRequest = originalRequest;
    this.forWebSocket = forWebSocket;
    this.retryAndFollowUpInterceptor = new RetryAndFollowUpInterceptor(client, forWebSocket);
}
```

然后再把目光转向 `RealCall` 中的 `execute()` 方法：

``` java
@Override
public Response execute() throws IOException {
    synchronized (this) {
        // 如果该 call 已经被执行过了，就设置 executed 为 true
        if (executed) throw new IllegalStateException("Already Executed");
        executed = true;
    }
    captureCallStackTrace();
    try {
        // 加入 runningSyncCalls 队列中
        client.dispatcher().executed(this);
        // 得到响应 result
        Response result = getResponseWithInterceptorChain();
        if (result == null) throw new IOException("Canceled");
        return result;
    } finally {
        // 从 runningSyncCalls 队列中移除
        client.dispatcher().finished(this);
    }
}
```

`execute()` 方法为执行该 `RealCall`，在方法里面一开始检查了该 call 时候被执行。

然后又加入了 `Dispatcher` 的 `runningSyncCalls` 中。`runningSyncCalls` 队列只是用来记录正在同步请求中的 call ，在 call 完成请求后又会从 `runningSyncCalls` 中移除。

可见，在同步请求中 `Dispatcher` 参与的部分很少。但是在异步请求中， `Dispatcher` 可谓是大展身手。

最重要的方法，那就是 `getResponseWithInterceptorChain()` 。我们可以看到这方法是直接返回 `Response` 对象的，所以，在这个方法中一定做了很多很多的事情。

那就继续深入吧：

``` java
Response getResponseWithInterceptorChain() throws IOException {
    // Build a full stack of interceptors.
    List<Interceptor> interceptors = new ArrayList<>();
    interceptors.addAll(client.interceptors()); // 加入用户自定义的拦截器
    interceptors.add(retryAndFollowUpInterceptor); // 重试和重定向拦截器
    interceptors.add(new BridgeInterceptor(client.cookieJar())); // 加入转化请求响应的拦截器
    interceptors.add(new CacheInterceptor(client.internalCache())); // 加入缓存拦截器
    interceptors.add(new ConnectInterceptor(client)); // 加入连接拦截器
    if (!forWebSocket) {
        interceptors.addAll(client.networkInterceptors()); // 加入用户自定义的网络拦截器
    }
    interceptors.add(new CallServerInterceptor(forWebSocket)); // 加入发出请求和读取响应的拦截器
    
    Interceptor.Chain chain = new RealInterceptorChain(interceptors, null, null, null, 0,
            originalRequest, this, eventListener, client.readTimeoutMillis());
    // 利用 chain 来链式调用拦截器，最后的返回结果就是 Response 对象
    return chain.proceed(originalRequest);
}
```

在 `getResponseWithInterceptorChain()` 方法中有一堆的拦截器！！！

关于拦截器，之前在 [一起来写OKHttp的拦截器](http://yuqirong.me/2017/06/25/%E4%B8%80%E8%B5%B7%E6%9D%A5%E5%86%99OKHttp%E7%9A%84%E6%8B%A6%E6%88%AA%E5%99%A8/) 这篇博客中有讲过，若不了解的同学可以先看下。

我们都知道，拦截器是 OkHttp 的精髓。

1. `client.interceptors()` ，首先加入 `interceptors` 的是用户自定义的拦截器，比如修改请求头的拦截器等；
2. `RetryAndFollowUpInterceptor` 是用来重试和重定向的拦截器，在下面我们会讲到；
3. `BridgeInterceptor` 是用来将用户友好的请求转化为向服务器的请求，之后又把服务器的响应转化为对用户友好的响应；
4. `CacheInterceptor` 是缓存拦截器，若存在缓存并且可用就直接返回该缓存，否则会向服务器请求；
5. `ConnectInterceptor` 用来建立连接的拦截器；
6. `client.networkInterceptors()` 加入用户自定义的 `networkInterceptors` ;
7. `CallServerInterceptor` 是真正向服务器发出请求且得到响应的拦截器；

最后在聚合了这些拦截器后，利用 `RealInterceptorChain` 来链式调用这些拦截器，利用的就是责任链模式。

RealInterceptorChain
--------------------
`RealInterceptorChain` 可以说是真正把这些拦截器串起来的一个角色。一个个拦截器就像一颗颗珠子，而 `RealInterceptorChain` 就是把这些珠子串连起来的那根绳子。

进入 `RealInterceptorChain` ，主要是 `proceed(Request request, StreamAllocation streamAllocation, HttpCodec httpCodec, RealConnection connection)` 这个方法：

``` java
public Response proceed(Request request, StreamAllocation streamAllocation, HttpCodec httpCodec,
                        RealConnection connection) throws IOException {
    if (index >= interceptors.size()) throw new AssertionError();

    calls++;

    // If we already have a stream, confirm that the incoming request will use it.
    if (this.httpCodec != null && !this.connection.supportsUrl(request.url())) {
        throw new IllegalStateException("network interceptor " + interceptors.get(index - 1)
                + " must retain the same host and port");
    }

    // If we already have a stream, confirm that this is the only call to chain.proceed().
    if (this.httpCodec != null && calls > 1) {
        throw new IllegalStateException("network interceptor " + interceptors.get(index - 1)
                + " must call proceed() exactly once");
    }

    // 得到下一次对应的 RealInterceptorChain
    RealInterceptorChain next = new RealInterceptorChain(interceptors, streamAllocation, httpCodec,
            connection, index + 1, request, call, eventListener, readTimeout);
    // 当前次数的 interceptor
    Interceptor interceptor = interceptors.get(index);
    // 进行拦截处理，并且在 interceptor 链式调用 next 的 proceed 方法
    Response response = interceptor.intercept(next);

    // 确认下一次的 interceptor 调用过 chain.proceed()
    if (httpCodec != null && index + 1 < interceptors.size() && next.calls != 1) {
        throw new IllegalStateException("network interceptor " + interceptor
                + " must call proceed() exactly once");
    }

    // Confirm that the intercepted response isn't null.
    if (response == null) {
        throw new NullPointerException("interceptor " + interceptor + " returned null");
    }

    if (response.body() == null) {
        throw new IllegalStateException(
                "interceptor " + interceptor + " returned a response with no body");
    }

    return response;
}
```

在代码中是一次次链式调用拦截器，可能有些同学还是看不懂。那么，我就捉急地画了一张示意图：

![interceptors](/uploads/20170725/20170722185657.png)

有了这张图就好懂多了，如果还没懂的话就只能自己慢慢体会了。

下面就要进入分析拦截器的步骤了，至于用户自定义的拦截器在这就略过了。还有，拦截器只分析主要的 `intercept(Chain chain)` 代码。

RetryAndFollowUpInterceptor
---------------------------
``` java
@Override
public Response intercept(Chain chain) throws IOException {
    Request request = chain.request();
    RealInterceptorChain realChain = (RealInterceptorChain) chain;
    Call call = realChain.call();
    EventListener eventListener = realChain.eventListener();

    streamAllocation = new StreamAllocation(client.connectionPool(), createAddress(request.url()),
            call, eventListener, callStackTrace);

    int followUpCount = 0;
    Response priorResponse = null;
    // 如果取消，就释放资源
    while (true) {
        if (canceled) {
            streamAllocation.release();
            throw new IOException("Canceled");
        }

        Response response = null;
        boolean releaseConnection = true;
        try {
            // 调用下一个拦截器
            response = realChain.proceed(request, streamAllocation, null, null);
            releaseConnection = false;
        } catch (RouteException e) {
            // The attempt to connect via a route failed. The request will not have been sent.
            // 路由连接失败，请求将不会被发送
            if (!recover(e.getLastConnectException(), false, request)) {
                throw e.getLastConnectException();
            }
            releaseConnection = false;
            continue;
        } catch (IOException e) {
            // An attempt to communicate with a server failed. The request may have been sent.
            // 服务器连接失败，请求可能已被发送
            boolean requestSendStarted = !(e instanceof ConnectionShutdownException);
            if (!recover(e, requestSendStarted, request)) throw e;
            releaseConnection = false;
            continue;
        } finally {
            // We're throwing an unchecked exception. Release any resources.
            // 抛出未检查的异常，释放资源
            if (releaseConnection) {
                streamAllocation.streamFailed(null);
                streamAllocation.release();
            }
        }

        // Attach the prior response if it exists. Such responses never have a body.
        if (priorResponse != null) {
            response = response.newBuilder()
                    .priorResponse(priorResponse.newBuilder()
                            .body(null)
                            .build())
                    .build();
        }
        // 如果不需要重定向，那么 followUp 为空，会根据响应码判断
        Request followUp = followUpRequest(response);
        // 释放资源，返回 response
        if (followUp == null) {
            if (!forWebSocket) {
                streamAllocation.release();
            }
            return response;
        }
        // 关闭 response 的 body
        closeQuietly(response.body());

        if (++followUpCount > MAX_FOLLOW_UPS) {
            streamAllocation.release();
            throw new ProtocolException("Too many follow-up requests: " + followUpCount);
        }

        if (followUp.body() instanceof UnrepeatableRequestBody) {
            streamAllocation.release();
            throw new HttpRetryException("Cannot retry streamed HTTP body", response.code());
        }
        // response 和 followUp 比较是否为同一个连接
        // 若为重定向就销毁旧连接，创建新连接
        if (!sameConnection(response, followUp.url())) {
            streamAllocation.release();
            streamAllocation = new StreamAllocation(client.connectionPool(),
                    createAddress(followUp.url()), call, eventListener, callStackTrace);
        } else if (streamAllocation.codec() != null) {
            throw new IllegalStateException("Closing the body of " + response
                    + " didn't close its backing stream. Bad interceptor?");
        }
        // 将重定向操作得到的新请求设置给 request
        request = followUp;
        priorResponse = response;
    }
}
```

总体来说，`RetryAndFollowUpInterceptor` 是用来失败重试以及重定向的拦截器。

BridgeInterceptor
-----------------
``` java
@Override
public Response intercept(Chain chain) throws IOException {
    Request userRequest = chain.request();
    Request.Builder requestBuilder = userRequest.newBuilder();
    // 将用户友好的 request 构造为发送给服务器的 request
    RequestBody body = userRequest.body();
    // 若有请求体，则构造
    if (body != null) {
        MediaType contentType = body.contentType();
        if (contentType != null) {
            requestBuilder.header("Content-Type", contentType.toString());
        }

        long contentLength = body.contentLength();
        if (contentLength != -1) {
            requestBuilder.header("Content-Length", Long.toString(contentLength));
            requestBuilder.removeHeader("Transfer-Encoding");
        } else {
            requestBuilder.header("Transfer-Encoding", "chunked");
            requestBuilder.removeHeader("Content-Length");
        }
    }

    if (userRequest.header("Host") == null) {
        requestBuilder.header("Host", hostHeader(userRequest.url(), false));
    }
    // Keep Alive
    if (userRequest.header("Connection") == null) {
        requestBuilder.header("Connection", "Keep-Alive");
    }

    // If we add an "Accept-Encoding: gzip" header field we're responsible for also decompressing
    // the transfer stream.
    // 使用 gzip 压缩
    boolean transparentGzip = false;
    if (userRequest.header("Accept-Encoding") == null && userRequest.header("Range") == null) {
        transparentGzip = true;
        requestBuilder.header("Accept-Encoding", "gzip");
    }
    // 设置 cookie
    List<Cookie> cookies = cookieJar.loadForRequest(userRequest.url());
    if (!cookies.isEmpty()) {
        requestBuilder.header("Cookie", cookieHeader(cookies));
    }
    // UA
    if (userRequest.header("User-Agent") == null) {
        requestBuilder.header("User-Agent", Version.userAgent());
    }
    // 构造完后，将 request 交给下一个拦截器去处理。最后又得到服务端响应 networkResponse
    Response networkResponse = chain.proceed(requestBuilder.build());
    // 保存 networkResponse 的 cookie
    HttpHeaders.receiveHeaders(cookieJar, userRequest.url(), networkResponse.headers());
    // 将 networkResponse 构造为对用户友好的 response
    Response.Builder responseBuilder = networkResponse.newBuilder()
            .request(userRequest);
    // 如果 networkResponse 使用 gzip 并且有响应体的话，给用户友好的 response 设置响应体
    if (transparentGzip
            && "gzip".equalsIgnoreCase(networkResponse.header("Content-Encoding"))
            && HttpHeaders.hasBody(networkResponse)) {
        GzipSource responseBody = new GzipSource(networkResponse.body().source());
        Headers strippedHeaders = networkResponse.headers().newBuilder()
                .removeAll("Content-Encoding")
                .removeAll("Content-Length")
                .build();
        responseBuilder.headers(strippedHeaders);
        responseBuilder.body(new RealResponseBody(strippedHeaders, Okio.buffer(responseBody)));
    }

    return responseBuilder.build();
}
```

在 `BridgeInterceptor` 这一步，先把用户友好的请求进行重新构造，变成了向服务器发送的请求。

之后调用 `chain.proceed(requestBuilder.build())` 进行下一个拦截器的处理。

等到后面的拦截器都处理完毕，得到响应。再把 `networkResponse` 转化成对用户友好的 `response` 。

CacheInterceptor
----------------
``` java
@Override
public Response intercept(Chain chain) throws IOException {
    // 得到 request 对应缓存中的 response
    Response cacheCandidate = cache != null
            ? cache.get(chain.request())
            : null;
    // 获取当前时间，会和之前缓存的时间进行比较
    long now = System.currentTimeMillis();
    // 得到缓存策略
    CacheStrategy strategy = new CacheStrategy.Factory(now, chain.request(), cacheCandidate).get();
    Request networkRequest = strategy.networkRequest;
    Response cacheResponse = strategy.cacheResponse;
    // 追踪缓存，其实就是计数
    if (cache != null) {
        cache.trackResponse(strategy);
    }
    // 缓存不适用，关闭
    if (cacheCandidate != null && cacheResponse == null) {
        closeQuietly(cacheCandidate.body()); // The cache candidate wasn't applicable. Close it.
    }

    // If we're forbidden from using the network and the cache is insufficient, fail.
    // 禁止网络并且没有缓存的话，返回失败
    if (networkRequest == null && cacheResponse == null) {
        return new Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(504)
                .message("Unsatisfiable Request (only-if-cached)")
                .body(Util.EMPTY_RESPONSE)
                .sentRequestAtMillis(-1L)
                .receivedResponseAtMillis(System.currentTimeMillis())
                .build();
    }

    // If we don't need the network, we're done.
    // 不用网络请求，返回缓存
    if (networkRequest == null) {
        return cacheResponse.newBuilder()
                .cacheResponse(stripBody(cacheResponse))
                .build();
    }

    Response networkResponse = null;
    try {
        // 交给下一个拦截器，返回 networkResponse
        networkResponse = chain.proceed(networkRequest);
    } finally {
        // If we're crashing on I/O or otherwise, don't leak the cache body.
        if (networkResponse == null && cacheCandidate != null) {
            closeQuietly(cacheCandidate.body());
        }
    }

    // 如果我们同时有缓存和 networkResponse ，根据情况使用
    if (cacheResponse != null) {
        if (networkResponse.code() == HTTP_NOT_MODIFIED) {
            Response response = cacheResponse.newBuilder()
                    .headers(combine(cacheResponse.headers(), networkResponse.headers()))
                    .sentRequestAtMillis(networkResponse.sentRequestAtMillis())
                    .receivedResponseAtMillis(networkResponse.receivedResponseAtMillis())
                    .cacheResponse(stripBody(cacheResponse))
                    .networkResponse(stripBody(networkResponse))
                    .build();
            networkResponse.body().close();
            // 更新原来的缓存至最新
            // Update the cache after combining headers but before stripping the
            // Content-Encoding header (as performed by initContentStream()).
            cache.trackConditionalCacheHit();
            cache.update(cacheResponse, response);
            return response;
        } else {
            closeQuietly(cacheResponse.body());
        }
    }

    Response response = networkResponse.newBuilder()
            .cacheResponse(stripBody(cacheResponse))
            .networkResponse(stripBody(networkResponse))
            .build();
    // 保存之前未缓存的缓存
    if (cache != null) {
        if (HttpHeaders.hasBody(response) && CacheStrategy.isCacheable(response, networkRequest)) {
            // Offer this request to the cache.
            CacheRequest cacheRequest = cache.put(response);
            return cacheWritingResponse(cacheRequest, response);
        }

        if (HttpMethod.invalidatesCache(networkRequest.method())) {
            try {
                cache.remove(networkRequest);
            } catch (IOException ignored) {
                // The cache cannot be written.
            }
        }
    }

    return response;
}
```

`CacheInterceptor` 做的事情就是根据请求拿到缓存，若没有缓存或者缓存失效，就进入网络请求阶段，否则会返回缓存。

ConnectInterceptor
------------------
``` java
@Override
public Response intercept(Chain chain) throws IOException {
    RealInterceptorChain realChain = (RealInterceptorChain) chain;
    Request request = realChain.request();
    StreamAllocation streamAllocation = realChain.streamAllocation();

    // We need the network to satisfy this request. Possibly for validating a conditional GET.
    boolean doExtensiveHealthChecks = !request.method().equals("GET");
    // 创建 httpCodec （抽象类），分别对应着 http1.1 和 http 2
    HttpCodec httpCodec = streamAllocation.newStream(client, doExtensiveHealthChecks);
    RealConnection connection = streamAllocation.connection();

    return realChain.proceed(request, streamAllocation, httpCodec, connection);
}
```

这里调用了 `streamAllocation.newStream` 创建了一个 `HttpCodec` 的对象。

而 `HttpCodec` 是一个抽象类，其实现类分别是 `Http1Codec` 和 `Http2Codec` 。相对应的就是 HTTP/1.1 和 HTTP/2.0 。

我们来看下 `streamAllocation.newStream` 的代码：

``` java
public HttpCodec newStream(OkHttpClient client, boolean doExtensiveHealthChecks) {
    int connectTimeout = client.connectTimeoutMillis();
    int readTimeout = client.readTimeoutMillis();
    int writeTimeout = client.writeTimeoutMillis();
    boolean connectionRetryEnabled = client.retryOnConnectionFailure();

    try {
        // 在连接池中找到一个可用的连接，然后创建出 HttpCodec 对象
        RealConnection resultConnection = findHealthyConnection(connectTimeout, readTimeout,
                writeTimeout, connectionRetryEnabled, doExtensiveHealthChecks);
        HttpCodec resultCodec = resultConnection.newCodec(client, this);

        synchronized (connectionPool) {
            codec = resultCodec;
            return resultCodec;
        }
    } catch (IOException e) {
        throw new RouteException(e);
    }
}
```

在 `newStream(OkHttpClient client, boolean doExtensiveHealthChecks)` 中先在连接池中找到可用的连接 `resultConnection` ，再结合 `sink` 和 `source` 创建出 `HttpCodec` 的对象。

CallServerInterceptor
---------------------
``` java
@Override
public Response intercept(Chain chain) throws IOException {
    RealInterceptorChain realChain = (RealInterceptorChain) chain;
    HttpCodec httpCodec = realChain.httpStream();
    StreamAllocation streamAllocation = realChain.streamAllocation();
    RealConnection connection = (RealConnection) realChain.connection();
    Request request = realChain.request();    

    long sentRequestMillis = System.currentTimeMillis();
    // 整理请求头并写入
    httpCodec.writeRequestHeaders(request);

    Response.Builder responseBuilder = null;
    // 检查是否为有 body 的请求方法
    if (HttpMethod.permitsRequestBody(request.method()) && request.body() != null) {
        // If there's a "Expect: 100-continue" header on the request, wait for a "HTTP/1.1 100
        // Continue" response before transmitting the request body. If we don't get that, return what
        // we did get (such as a 4xx response) without ever transmitting the request body.
        // 如果有 Expect: 100-continue 在请求头中，那么要等服务器的响应
        if ("100-continue".equalsIgnoreCase(request.header("Expect"))) {
            httpCodec.flushRequest();
            responseBuilder = httpCodec.readResponseHeaders(true);
        }

        if (responseBuilder == null) {
            // Write the request body if the "Expect: 100-continue" expectation was met.
            // 写入请求体
            Sink requestBodyOut = httpCodec.createRequestBody(request, request.body().contentLength());
            BufferedSink bufferedRequestBody = Okio.buffer(requestBodyOut);
            request.body().writeTo(bufferedRequestBody);
            bufferedRequestBody.close();
        } else if (!connection.isMultiplexed()) {
            // If the "Expect: 100-continue" expectation wasn't met, prevent the HTTP/1 connection from
            // being reused. Otherwise we're still obligated to transmit the request body to leave the
            // connection in a consistent state.
            streamAllocation.noNewStreams();
        }
    }

    httpCodec.finishRequest();
    // 得到响应头
    if (responseBuilder == null) {
        responseBuilder = httpCodec.readResponseHeaders(false);
    }
    // 构造 response
    Response response = responseBuilder
            .request(request)
            .handshake(streamAllocation.connection().handshake())
            .sentRequestAtMillis(sentRequestMillis)
            .receivedResponseAtMillis(System.currentTimeMillis())
            .build();

    int code = response.code();
    // 如果为 web socket 且状态码是 101 ，那么 body 为空
    if (forWebSocket && code == 101) {
        // Connection is upgrading, but we need to ensure interceptors see a non-null response body.
        response = response.newBuilder()
                .body(Util.EMPTY_RESPONSE)
                .build();
    } else {
        // 读取 body
        response = response.newBuilder()
                .body(httpCodec.openResponseBody(response))
                .build();
    }
    // 如果请求头中有 close 那么断开连接
    if ("close".equalsIgnoreCase(response.request().header("Connection"))
            || "close".equalsIgnoreCase(response.header("Connection"))) {
        streamAllocation.noNewStreams();
    }
    // 抛出协议异常
    if ((code == 204 || code == 205) && response.body().contentLength() > 0) {
        throw new ProtocolException(
                "HTTP " + code + " had non-zero Content-Length: " + response.body().contentLength());
    }

    return response;
}
```

在 `CallServerInterceptor` 中可见，关于请求和响应部分都是通过 `HttpCodec` 来实现的。而在 `HttpCodec` 内部又是通过 `sink` 和 `source` 来实现的。所以说到底还是 IO 流在起作用。

小结
----
到这里，我们也完全明白了 OkHttp 中的分层思想，每一个 interceptor 只处理自己的事，而剩余的就交给其他的 interceptor 。这种思想可以简化一些繁琐复杂的流程，从而达到逻辑清晰、互不干扰的效果。

异步请求
=======
与同步请求直接调用 `execute()` 不同的是，异步请求是调用了 `enqueue(Callback responseCallback)` 这个方法。那么我们对异步请求探究的入口就是 `enqueue(Callback responseCallback)` 了。

RealCall
---------
``` java
@Override
public void enqueue(Callback responseCallback) {
    synchronized (this) {
        if (executed) throw new IllegalStateException("Already Executed");
        executed = true;
    }
    captureCallStackTrace();
    // 加入到 dispatcher 中，这里包装成了 AsyncCall
    client.dispatcher().enqueue(new AsyncCall(responseCallback));
}
```

主要的方法就是调用了 `Dispatcher` 的 `enqueue(AsyncCall call)` 方法。这里需要注意的是，传入的是 `AsyncCall` 对象，而不是同步中的 `RealCall` 。

那么我们就跟进到 `Dispatcher` 的源码中吧，至于 `AsyncCall` 我们会在下面详细讲到。

Dispatcher
-----------
``` java
synchronized void enqueue(AsyncCall call) {
    // 如果当前正在运行的异步 call 数 < 64 && 队列中请求同一个 host 的异步 call 数 < 5
    // maxRequests = 64，maxRequestsPerHost = 5
    if (runningAsyncCalls.size() < maxRequests && runningCallsForHost(call) < maxRequestsPerHost) {
        // 加入正在运行异步队列
        runningAsyncCalls.add(call);
        // 加入到线程池中
        executorService().execute(call);
    } else {
        // 加入预备异步队列
        readyAsyncCalls.add(call);
    }
}

// 创建线程池
public synchronized ExecutorService executorService() {
    if (executorService == null) {
        executorService = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60, TimeUnit.SECONDS,
                new SynchronousQueue<Runnable>(), Util.threadFactory("OkHttp Dispatcher", false));
    }
    return executorService;
}
```

从 `enqueue(AsyncCall call)` 中可以知道，OkHttp 在运行中的异步请求数最多为 63 ，而同一个 host 的异步请求数最多为 4 。否则会加入到 `readyAsyncCalls` 中。

在加入到 `runningAsyncCalls` 后，就会进入线程池中被执行。到了这里，我们就要到 `AsyncCall` 中一探究竟了。

AsyncCall
---------
``` java
final class AsyncCall extends NamedRunnable {
    private final Callback responseCallback;

    AsyncCall(Callback responseCallback) {
        super("OkHttp %s", redactedUrl());
        this.responseCallback = responseCallback;
    }

    String host() {
        return originalRequest.url().host();
    }

    Request request() {
        return originalRequest;
    }

    RealCall get() {
        return RealCall.this;
    }

    @Override
    protected void execute() {
        boolean signalledCallback = false;
        try {
            // 调用一连串的拦截器，得到响应
            Response response = getResponseWithInterceptorChain();
            if (retryAndFollowUpInterceptor.isCanceled()) {
                // 回调失败
                signalledCallback = true;
                responseCallback.onFailure(RealCall.this, new IOException("Canceled"));
            } else {
                // 回调结果
                signalledCallback = true;
                responseCallback.onResponse(RealCall.this, response);
            }
        } catch (IOException e) {
            if (signalledCallback) {
                // Do not signal the callback twice!
                Platform.get().log(INFO, "Callback failure for " + toLoggableString(), e);
            } else {
                // 回调失败
                responseCallback.onFailure(RealCall.this, e);
            }
        } finally {
            // 在 runningAsyncCalls 中移除，并作推进其他 call 的工作
            client.dispatcher().finished(this);
        }
    }
}
```

在 `AsyncCall` 的 `execute()` 方法中，也是调用了 `getResponseWithInterceptorChain()` 方法来得到 `Response` 对象。从这里开始，就和同步请求的流程是一样的，就没必要讲了。

在得到 `Response` 后，进行结果的回调。

最后，调用了 `Dispatcher` 的 `finished` 方法：

``` java
void finished(AsyncCall call) {
    finished(runningAsyncCalls, call, true);
}

private <T> void finished(Deque<T> calls, T call, boolean promoteCalls) {
    int runningCallsCount;
    Runnable idleCallback;
    synchronized (this) {
        // 移除该 call
        if (!calls.remove(call)) throw new AssertionError("Call wasn't in-flight!");
        // 将 readyAsyncCalls 中的 call 移动到 runningAsyncCalls 中，并加入到线程池中
        if (promoteCalls) promoteCalls();
        runningCallsCount = runningCallsCount();
        idleCallback = this.idleCallback;
    }

    if (runningCallsCount == 0 && idleCallback != null) {
        idleCallback.run();
    }
}
```

在 `finished(Deque<T> calls, T call, boolean promoteCalls)` 中对该 call 移除。

若在 `readyAsyncCalls` 中其他的 call ，就移动到 `runningAsyncCalls` 中并加入线程池中。

这样，完整的流程就循环起来了。

Footer
======
基本上 OkHttp 的请求响应的流程就讲完了，篇幅有点长长长啊。

不过还有很多点没有涉及到的，比如连接池、缓存策略等等，都是值得我们去深究的。也是需要花很大的功夫才能了解透彻。

好了，那就到这里吧，有问题的同学可以留言。

Goodbye !



References
==========
* [OKHttp源码解析](http://www.jianshu.com/p/27c1554b7fee)
* [拆轮子系列：拆 OkHttp](https://blog.piasy.com/2016/07/11/Understand-OkHttp/)
* [OkHttp框架的RetryAndFollowUpInterceptor请求重定向源码解析](http://blog.csdn.net/qq_15274383/article/details/73729801)
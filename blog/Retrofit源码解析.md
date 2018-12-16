title: Retrofit源码解析
date: 2017-08-03 23:19:17
categories: Android Blog
tags: [Android,开源框架,源码解析]
---
Header
======
之前对 OkHttp 进行过源码分析了，那么今天就来讲讲 Retrofit 。

Retrofit 其实是对 OkHttp 进行了一层封装，让开发者对网络操作更加方便快捷。

相信绝大多数的 Android 开发者都有使用过的经历。其 restful 风格的编程俘获了众多人的心。

废话就不多讲了，下面就要对 Retrofit 进行源码解析。

本文解析的 Retrofit 基于 v2.3.0 ，GitHub 地址：[https://github.com/square/retrofit](https://github.com/square/retrofit)

Retrofit 使用方法
================
直接抄官网的：

第一步，声明 API 接口：

``` java
public interface GitHubService {
  @GET("users/{user}/repos")
  Call<List<Repo>> listRepos(@Path("user") String user);
}
```

第二步，构造出 `Retrofit` 对象：

``` java
Retrofit retrofit = new Retrofit.Builder()
    .baseUrl("https://api.github.com/")
    .addConverterFactory(GsonConverterFactory.create())
    .build();
```

第三步，得到 API 接口，直接调用：

``` java
GitHubService service = retrofit.create(GitHubService.class);
Call<List<Repo>> repos = service.listRepos("octocat");
```

最后，就是调用 `repos` 执行 Call ：

``` java
// sync
repos.execute();
// async
repos.enqueue(...);
```

请求源码解析
==========
我们先来看看发出网络请求部分的源码。

Retrofit.Builder
----------------
首先切入点就是 `Retrofit.Builder` 。

在 `Retrofit.Builder` 中有以下的方法：

* client ： 设置 http client，默认是 OkHttpClient，会调用 `callFactory` 方法
* callFactory ： 设置网络请求 call 的工厂，默认就是上面的 OkHttpClient
* baseUrl ： api 的 base url
* addConverterFactory ： 添加数据转换器工厂
* addCallAdapterFactory　：　添加网络请求适配器工厂
* callbackExecutor ： 回调方法执行器
* validateEagerly ： 是否提前解析接口方法

这些都是用来配置 `Builder` 的。

那么我们来看下 `Builder` 的构造方法：

``` java
public Builder() {
  // 确定平台，有 Android Java8 默认Platform 三种
  this(Platform.get());
}

Builder(Platform platform) {
  this.platform = platform;
  // Add the built-in converter factory first. This prevents overriding its behavior but also
  // ensures correct behavior when using converters that consume all types.
  // 默认内置的数据转换器 BuiltInConverters
  converterFactories.add(new BuiltInConverters());
}
```

来个小插曲，我们来看下 Retrofit 是如何确定平台的：

### Platform

``` java
private static final Platform PLATFORM = findPlatform();

static Platform get() {
  return PLATFORM;
}

private static Platform findPlatform() {
  try {
    Class.forName("android.os.Build");
    if (Build.VERSION.SDK_INT != 0) {
      return new Android();
    }
  } catch (ClassNotFoundException ignored) {
  }
  try {
    Class.forName("java.util.Optional");
    return new Java8();
  } catch (ClassNotFoundException ignored) {
  }
  return new Platform();
}
```

从上面的代码中可以看到，是通过反射判断有没有该类来实现的。若以后在开发的过程中有需要判断平台的需求，我们可以直接将该段代码 copy 过来。

接着，在创建 `Builder` 对象并进行自定义配置后，我们就要调用 `build()` 方法来构造出 `Retrofit` 对象了。那么，我们来看下 `build()` 方法里干了什么：

``` java
public Retrofit build() {
  if (baseUrl == null) {
    throw new IllegalStateException("Base URL required.");
  }
  // 默认为 OkHttpClient
  okhttp3.Call.Factory callFactory = this.callFactory;
  if (callFactory == null) {
    callFactory = new OkHttpClient();
  }
  // Android 平台下默认为 MainThreadExecutor
  Executor callbackExecutor = this.callbackExecutor;
  if (callbackExecutor == null) {
    callbackExecutor = platform.defaultCallbackExecutor();
  }
  //
  // Make a defensive copy of the adapters and add the default Call adapter.
  List<CallAdapter.Factory> adapterFactories = new ArrayList<>(this.adapterFactories);
  // 添加 ExecutorCallAdapterFactory
  adapterFactories.add(platform.defaultCallAdapterFactory(callbackExecutor));

  // Make a defensive copy of the converters. 默认有 BuiltInConverters
  List<Converter.Factory> converterFactories = new ArrayList<>(this.converterFactories);

  return new Retrofit(callFactory, baseUrl, converterFactories, adapterFactories,
      callbackExecutor, validateEagerly);
}
```

在 `build()` 中，做的事情有：检查配置、设置默认配置、创建 `Retrofit` 对象。

关于上面种种奇怪的类，我们先不关心，因为之后我们遇到了再分析。我们先把目光聚焦在 `Retrofit` 类上。

Retrofit
--------
`Retrofit` 类的构造方法没什么好看的，在这就不讲了。

得到 `Retrofit` 对象后就是调用 `create(final Class<T> service)` 方法来创建我们 API 接口的实例。

所以我们需要跟进 `create(final Class<T> service)` 中来看下：

``` java
  public <T> T create(final Class<T> service) {
    // 校验是否为接口，且不能继承其他接口
    Utils.validateServiceInterface(service);
    // 是否需要提前解析接口方法
    if (validateEagerly) {
      eagerlyValidateMethods(service);
    }
    // 动态代理模式
    return (T) Proxy.newProxyInstance(service.getClassLoader(), new Class<?>[] { service },
        new InvocationHandler() {
          private final Platform platform = Platform.get();

          @Override public Object invoke(Object proxy, Method method, Object... args)
              throws Throwable {
            // If the method is a method from Object then defer to normal invocation.
            if (method.getDeclaringClass() == Object.class) {
              return method.invoke(this, args);
            }
            if (platform.isDefaultMethod(method)) {
              return platform.invokeDefaultMethod(method, service, proxy, args);
            }
            // 将接口中的方法构造为 ServiceMethod
            ServiceMethod serviceMethod = loadServiceMethod(method);
            OkHttpCall okHttpCall = new OkHttpCall<>(serviceMethod, args);
            return serviceMethod.callAdapter.adapt(okHttpCall);
          }
        });
  }
```

在上面的代码中，最关键的就是动态代理。实际上，进行网络操作的都是通过代理类来完成的。如果对动态代理不太懂的同学请自行百度了，这里就不多讲了。

重点就是

``` java
// 将接口中的方法构造为 ServiceMethod
ServiceMethod serviceMethod = loadServiceMethod(method);
OkHttpCall okHttpCall = new OkHttpCall<>(serviceMethod, args);
return serviceMethod.callAdapter.adapt(okHttpCall);
```

这三句代码，下面我们着重来看。


在代理中，会根据参数中传入的具体接口方法来构造出对应的 `serviceMethod` 。`ServiceMethod` 类的作用就是把接口的方法适配为对应的 HTTP call 。

``` java
  ServiceMethod loadServiceMethod(Method method) {
    ServiceMethod result;
    synchronized (serviceMethodCache) {
	  // 先从缓存中取，若没有就去创建对应的 ServiceMethod
      result = serviceMethodCache.get(method);
      if (result == null) {
        // 没有缓存就创建，之后再放入缓存中
        result = new ServiceMethod.Builder(this, method).build();
        serviceMethodCache.put(method, result);
      }
    }
    return result;
  }
```

可以看到在内部还维护了一个 `serviceMethodCache` 来缓存 `ServiceMethod` ，以便提高效率。我们就直接来看 `ServiceMethod` 是如何被创建的吧。

ServiceMethod.Builder
---------------------
发现 `ServiceMethod` 也是通过建造者模式来创建对象的。那就进入对应构造方法：

``` java
public Builder(Retrofit retrofit, Method method) {
  this.retrofit = retrofit;
  this.method = method;
  // 接口方法的注解
  this.methodAnnotations = method.getAnnotations();
  // 接口方法的参数类型
  this.parameterTypes = method.getGenericParameterTypes();
  // 接口方法参数的注解
  this.parameterAnnotationsArray = method.getParameterAnnotations();
}
```

在构造方法中没有什么大的动作，那么就单刀直入 `build()` 方法：

``` java
public ServiceMethod build() {
  // 根据接口方法的注解和返回类型创建 callAdapter
  // 如果没有添加 CallAdapter 那么默认会用 ExecutorCallAdapterFactory
  callAdapter = createCallAdapter();
  // calladapter 的响应类型中的泛型，比如 Call<User> 中的 User
  responseType = callAdapter.responseType();
  if (responseType == Response.class || responseType == okhttp3.Response.class) {
    throw methodError("'"
        + Utils.getRawType(responseType).getName()
        + "' is not a valid response body type. Did you mean ResponseBody?");
  }

  // 根据之前泛型中的类型以及接口方法的注解创建 ResponseConverter
  responseConverter = createResponseConverter();

  // 根据接口方法的注解构造请求方法，比如 @GET @POST @DELETE 等
  // 另外还有添加请求头，检查url中有无带?，转化 path 中的参数
  for (Annotation annotation : methodAnnotations) {
    parseMethodAnnotation(annotation);
  }

  if (httpMethod == null) {
    throw methodError("HTTP method annotation is required (e.g., @GET, @POST, etc.).");
  }

  // 若无 body 则不能有 isMultipart 和 isFormEncoded
  if (!hasBody) {
    if (isMultipart) {
      throw methodError(
          "Multipart can only be specified on HTTP methods with request body (e.g., @POST).");
    }
    if (isFormEncoded) {
      throw methodError("FormUrlEncoded can only be specified on HTTP methods with "
          + "request body (e.g., @POST).");
    }
  }

  // 下面的代码主要用来解析接口方法参数中的注解，比如 @Path @Query @QueryMap @Field 等等
  // 相应的，每个方法的参数都创建了一个 ParameterHandler<?> 对象
  int parameterCount = parameterAnnotationsArray.length;
  parameterHandlers = new ParameterHandler<?>[parameterCount];
  for (int p = 0; p < parameterCount; p++) {
    Type parameterType = parameterTypes[p];
    if (Utils.hasUnresolvableType(parameterType)) {
      throw parameterError(p, "Parameter type must not include a type variable or wildcard: %s",
          parameterType);
    }

    Annotation[] parameterAnnotations = parameterAnnotationsArray[p];
    if (parameterAnnotations == null) {
      throw parameterError(p, "No Retrofit annotation found.");
    }

    parameterHandlers[p] = parseParameter(p, parameterType, parameterAnnotations);
  }

  // 检查构造出的请求有没有不对的地方？
  if (relativeUrl == null && !gotUrl) {
    throw methodError("Missing either @%s URL or @Url parameter.", httpMethod);
  }
  if (!isFormEncoded && !isMultipart && !hasBody && gotBody) {
    throw methodError("Non-body HTTP method cannot contain @Body.");
  }
  if (isFormEncoded && !gotField) {
    throw methodError("Form-encoded method must contain at least one @Field.");
  }
  if (isMultipart && !gotPart) {
    throw methodError("Multipart method must contain at least one @Part.");
  }

  return new ServiceMethod<>(this);
}
```

在 `build()` 中代码挺长的，总结起来就一句话：就是将 API 接口中的方法进行解析，构造成 `ServiceMethod` ，交给下面的 `OkHttpCall` 使用。

基本上做的事情就是：

1. 创建 CallAdapter ；
2. 创建 ResponseConverter；
3. 根据 API 接口方法的注解构造网络请求方法；
4. 根据 API 接口方法参数中的注解构造网络请求的参数；
5. 检查有无异常；

代码中都是注释，在这里就不详细多讲了。

`ServiceMethod serviceMethod = loadServiceMethod(method);` 这句代码我们看完了，那么看接下来的 `OkHttpCall okHttpCall = new OkHttpCall<>(serviceMethod, args);` 。

OkHttpCall
----------
在 `OkHttpCall` 的构造器中没什么大动作，搞不了大事情的：

``` java
  OkHttpCall(ServiceMethod<T> serviceMethod, Object[] args) {
    this.serviceMethod = serviceMethod;
    this.args = args;
  }
```

而真正搞事情的是 `serviceMethod.callAdapter.adapt(okHttpCall);` 这句代码。

ExecutorCallAdapterFactory
--------------------------
在 Retrofit 中默认的 callAdapterFactory 是 `ExecutorCallAdapterFactory` 。我们就进入它的 `get(Type returnType, Annotation[] annotations, Retrofit retrofit)` 看看吧，返回了一个匿名类 `CallAdapter<Object, Call<?>>` ，在其中有 `adapt(Call<Object> call)` 方法：

``` java
  @Override
  public CallAdapter<?, ?> get(Type returnType, Annotation[] annotations, Retrofit retrofit) {
    if (getRawType(returnType) != Call.class) {
      return null;
    }
    final Type responseType = Utils.getCallResponseType(returnType);
    return new CallAdapter<Object, Call<?>>() {
      @Override public Type responseType() {
        return responseType;
      }

      @Override public Call<Object> adapt(Call<Object> call) {
        return new ExecutorCallbackCall<>(callbackExecutor, call);
      }
    };
  }
```

可以看到它 `return new ExecutorCallbackCall<>(callbackExecutor, call);` 。`ExecutorCallbackCall` 是实现了 `retrofit2.Call` ，这里注意下，是 Retrofit 中的 Call 而不是 OkHttp 中的 Call 。使用了装饰者模式把 `retrofit2.Call` 又包装了一层。

在得到了 `ExecutorCallbackCall` ，我们可以调用同步方法 `execute()` 或异步方法 `enqueue(Callback<T> callback)` 来执行该 call 。

ExecutorCallAdapterFactory.ExecutorCallbackCall
-----------------------------------------------
那我们就跟进同步方法 `execute()` 吧，异步的 `enqueue(Callback<T> callback)` 就不看了。了解过 OkHttp 的同学应该都知道这两个方法的区别，就是多了异步执行和回调的步骤而已。

``` java
@Override 
public Response<T> execute() throws IOException {
  // delegate 就是构造器中传进来的 OkHttpCall
  return delegate.execute();
}
```

所以，其实就是调用了 `OkHttpCall` 的 `execute()` 方法。

所以我们又要回到 `OkHttpCall` 中了。

OkHttpCall
----------
``` java
  @Override public Response<T> execute() throws IOException {
    okhttp3.Call call;

    synchronized (this) {
      if (executed) throw new IllegalStateException("Already executed.");
      executed = true;

      if (creationFailure != null) {
        if (creationFailure instanceof IOException) {
          throw (IOException) creationFailure;
        } else {
          throw (RuntimeException) creationFailure;
        }
      }

      call = rawCall;
      if (call == null) {
        try {
		  // 根据 serviceMethod 中的众多数据创建出 Okhttp 中的 Request 对象
		  // 注意的一点，会调用上面的 ParameterHandler.apply 方法来填充网络请求参数
		  // 然后再根据 OkhttpClient 创建出 Okhttp 中的 Call
		  // 这一步也说明了在 Retrofit 中的 OkHttpCall 内部请求最后会转换为 OkHttp 的 Call
          call = rawCall = createRawCall();
        } catch (IOException | RuntimeException e) {
          creationFailure = e;
          throw e;
        }
      }
    }
    // 检查 call 是否取消
    if (canceled) {
      call.cancel();
    }
	// 执行 call 并转换响应的 response
    return parseResponse(call.execute());
  }
```

在 `execute()` 做的就是将 Retrofit 中的 call 转化为 OkHttp 中的 call 。

最后让 OkHttp 的 call 去执行。

至此，Retrofit 的网络请求部分源码已经全部解析一遍了。

剩下的就是响应部分了，趁热打铁。

响应源码解析
==========
我们可以看到 `OkHttpCall.execute()` 中的最后一句：`parseResponse(call.execute())`。

所以对响应的处理就是 `parseResponse(okhttp3.Response rawResponse)` 这个方法了。

OkHttpCall
----------

``` java
  Response<T> parseResponse(okhttp3.Response rawResponse) throws IOException {
    ResponseBody rawBody = rawResponse.body();

    // Remove the body's source (the only stateful object) so we can pass the response along.
    rawResponse = rawResponse.newBuilder()
        .body(new NoContentResponseBody(rawBody.contentType(), rawBody.contentLength()))
        .build();
    // 如果返回的响应码不是成功的话，返回错误 Response
    int code = rawResponse.code();
    if (code < 200 || code >= 300) {
      try {
        // Buffer the entire body to avoid future I/O.
        ResponseBody bufferedBody = Utils.buffer(rawBody);
        return Response.error(bufferedBody, rawResponse);
      } finally {
        rawBody.close();
      }
    }
    // 如果返回的响应码是204或者205，返回没有 body 的成功 Response
    if (code == 204 || code == 205) {
      return Response.success(null, rawResponse);
    }

    ExceptionCatchingRequestBody catchingBody = new ExceptionCatchingRequestBody(rawBody);
    try {
      // 将 body 转换为对应的泛型，然后返回成功 Response
      T body = serviceMethod.toResponse(catchingBody);
      return Response.success(body, rawResponse);
    } catch (RuntimeException e) {
      // If the underlying source threw an exception, propagate that rather than indicating it was
      // a runtime exception.
      catchingBody.throwIfCaught();
      throw e;
    }
  }
```

在 `parseResponse(okhttp3.Response rawResponse)` 中主要是这句代码：

`T body = serviceMethod.toResponse(catchingBody);` 

将 `ResponseBody` 直接转化为了泛型，可以猜到这也是 Converter 的功劳。

ServiceMethod
-------------
``` java
  T toResponse(ResponseBody body) throws IOException {
    return responseConverter.convert(body);
  }
```

果然没错，内部是调用了 `responseConverter` 的。

BuiltInConverters
-----------------
`BuiltInConverters` 中有好几种内置的 Converter 。并且只支持返回 `ResponseBody` 。我们来看下它们的实现：

``` java
static final class StreamingResponseBodyConverter
        implements Converter<ResponseBody, ResponseBody> {
    static final StreamingResponseBodyConverter INSTANCE = new StreamingResponseBodyConverter();

    @Override
    public ResponseBody convert(ResponseBody value) throws IOException {
        return value;
    }
}

static final class BufferingResponseBodyConverter
        implements Converter<ResponseBody, ResponseBody> {
    static final BufferingResponseBodyConverter INSTANCE = new BufferingResponseBodyConverter();

    @Override
    public ResponseBody convert(ResponseBody value) throws IOException {
        try {
            // Buffer the entire body to avoid future I/O.
            return Utils.buffer(value);
        } finally {
            value.close();
        }
    }
}
```

其实说白了就是将 `ResponseBody` 转化为对应的数据类型了。比如在 `GsonConverterFactory` 中就是把 `ResponseBody` 用 gson 转化为对应的类型，有兴趣的同学可以自己看下。这里也没什么神秘的，相信大家都懂的。

到这里就把 Retrofit 响应部分的源码解析完毕了。

大家自行消化一下吧。

我自己也写得头晕了。。。笑 cry

Footer
======
最后，相信大家已经了解了 Retrofit 到底是怎么一回事了。

Retrofit 内部访问网络仍然是通过 OkHttp ，而只是把构造请求和响应封装了一下，更加简单易用了。

还有，看过框架源码的都知道在源码中有很多设计模式的体现，比如建造者模式、装饰者模式以及 OkHttp 中的责任链模式等。这些也正是值得我们学习的地方。

好啦，今天结束了。如果有问题的同学可以留言咯。

Goodbye

References
==========
* [Android：手把手带你深入剖析 Retrofit 2.0 源码](http://www.jianshu.com/p/0c055ad46b6c)
* [Retrofit2 完全解析 探索与okhttp之间的关系](http://blog.csdn.net/lmj623565791/article/details/51304204)
title: 一起来写OkHttp的拦截器
date: 2017-06-25 12:37:02
categories: Android Blog
tags: [Android,开源框架]
---
00:00
======
一开始就不多说废话了，主要因为工作时遇到了一些使用 OkHttp 拦截器的问题，所以在此特写这篇以作记录。

现如今，做 Android 开发在选择网络框架时，大多数都会首推 Retrofit 。Retrofit 以其简洁优雅的代码俘获了大多数开发者的心。

然而 Retrofit 内部请求也是基于 OkHttp 的，所以在做一些自定义修改 HTTP 请求时，需要对 OkHttp 拦截器具有一定了解。相信熟悉 OkHttp 的同学都知道，OkHttp 内部是使用拦截器来完成请求和响应的，利用的是责任链设计模式。所以可以说，拦截器是 OkHttp 的精髓所在。

那么接下来，我们就通过一些例子来学习怎样编写 OkHttp 的拦截器吧，其实这些例子也正是之前我遇到的情景。

00:01
=====
添加请求 Header
--------------
假设现在后台要求我们在请求 API 接口时，都在每一个接口的请求头上添加对应的 token 。使用 Retrofit 比较多的同学肯定会条件反射出以下代码：

``` java
@FormUrlEncoded
@POST("/mobile/login.htm")
Call<ResponseBody> login(@Header("token") String token, @Field("mobile") String phoneNumber, @Field("smsCode") String smsCode);
```

这样的写法自然可以，无非就是每次调用 login API 接口时都把 token 传进去而已。但是需要注意的是，假如现在有十多个 API 接口，每一个都需要传入 token ，难道我们去重复一遍又一遍吗？

相信有良知的程序员都会拒绝，因为这会导致代码的冗余。

那么有没有好的办法可以一劳永逸呢？答案是肯定的，那就要用到拦截器了。

代码很简单：

``` java
public class TokenHeaderInterceptor implements Interceptor {

    @Override
    public Response intercept(Chain chain) throws IOException {
        // get token
        String token = AppService.getToken();
        Request originalRequest = chain.request();
        // get new request, add request header
        Request updateRequest = originalRequest.newBuilder()
                .header("token", token)
                .build();
        return chain.proceed(updateRequest);
    }

}
```

我们先拦截得到 originalRequest ，然后利用 originalRequest 生成新的 updateRequest ，再交给 chain 处理进行下一环。

最后，在 OkHttpClient 中使用：

``` java
OkHttpClient client = new OkHttpClient.Builder()
                .addNetworkInterceptor(new TokenHeaderInterceptor())
                .build();

Retrofit retrofit = new Retrofit.Builder().baseUrl(BuildConfig.BASE_URL)
                .client(client).addConverterFactory(GsonConverterFactory.create()).build();
```

改变请求体
---------
除了增加请求头之外，拦截器还可以改变请求体。

假设现在我们有如下需求：在上面的 login 接口基础上，后台要求我们传过去的请求参数是要按照一定规则经过加密的。

规则如下：

* 请求参数名统一为content；
* content值：JSON 格式的字符串经过 AES 加密后的内容；

举个例子，根据上面的 login 接口，现有

```
{"mobile":"157xxxxxxxx", "smsCode":"xxxxxx"}
```

JSON 字符串，然后再将其加密。最后以 content=[加密后的 JSON 字符串] 方式发送给后台。

看完了上面的 `TokenHeaderInterceptor` 之后，这需求对于我们来说可以算是信手拈来：

``` java
public class RequestEncryptInterceptor implements Interceptor {

    private static final String FORM_NAME = "content";
    private static final String CHARSET = "UTF-8";

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        RequestBody body = request.body();
        if (body instanceof FormBody) {
            FormBody formBody = (FormBody) body;
            Map<String, String> formMap = new HashMap<>();
            // 从 formBody 中拿到请求参数，放入 formMap 中
            for (int i = 0; i < formBody.size(); i++) {
                formMap.put(formBody.name(i), formBody.value(i));
            }
            // 将 formMap 转化为 json 然后 AES 加密
            Gson gson = new Gson();
            String jsonParams = gson.toJson(formMap);
            String encryptParams = AESCryptUtils.encrypt(jsonParams.getBytes(CHARSET), AppConstant.getAESKey());
            // 重新修改 body 的内容
            body = new FormBody.Builder().add(FORM_NAME, encryptParams).build();
        }
        if (body != null) {
            request = request.newBuilder()
                    .post(body)
                    .build();
        }
        return chain.proceed(request);
    }
}
```

代码中已经添加了关键的注释，相信我已经不需要多解释什么了。

经过了这两种拦截器，相信同学们已经充分体会到了 OkHttp 的优点和与众不同。

最后，自定义拦截器的使用情景通常是对所有网络请求作统一处理。如果下次你也碰到这种类似的需求，别忘记使用自定义拦截器哦！

00:02
=====
呃呃呃，按道理来讲应该要结束了。

但是，我在这里开启一个番外篇吧，不过目标不是针对拦截器而是 ConverterFactory 。

还是后台需求，login 接口返回的数据也是经过 AES 加密的。所以需要我们针对所有响应体都做解密处理。

另外，还有很重要的一点，就是数据正常和异常时返回的 JSON 格式不一致。

在业务数据正常的时候（即 code 等于 200 时）：

```
{
    "code":200,
    "msg":"请求成功",
    "data":{
        "nickName":"Hello",
        "userId": "1234567890"
    }
}
```

业务数据异常时（即 code 不等于 200 时）：

```
{
    "code":7008,
    "msg":"用户名或密码错误",
    "data":"用户名或密码错误"
}
```

而这会在使用 Retrofit 自动从 JSON 转化为 bean 类时报错。因为 data 中的正常数据中是 JSON ，而另一个异常数据中是字符串。

那么，如何解决上述的两个问题呢？

利用 **自定义 ConverterFactory** ！！

我们先创建包名 `retrofit2.converter.gson` ，为什么要创建这个包名呢？

因为自定义的 ConverterFactory 需要继承 Converter.Factory ，而 Converter.Factory 类默认是包修饰符。

代码如下：

``` java
public final class CustomConverterFactory extends Converter.Factory {

    private final Gson gson;

    public static CustomConverterFactory create() {
        return create(new Gson());
    }

    @SuppressWarnings("ConstantConditions") // Guarding public API nullability.
    public static CustomConverterFactory create(Gson gson) {
        if (gson == null) throw new NullPointerException("gson == null");
        return new CustomConverterFactory(gson);
    }
   
    private CustomConverterFactory(Gson gson) {
        this.gson = gson;
    }

    @Override
    public Converter<ResponseBody, ?> responseBodyConverter(Type type, Annotation[] annotations, Retrofit retrofit) {
        TypeAdapter<?> adapter = gson.getAdapter(TypeToken.get(type));
        // attention here!
        return new CustomResponseConverter<>(gson, adapter);
    }

    @Override
    public Converter<?, RequestBody> requestBodyConverter(Type type, Annotation[] parameterAnnotations, Annotation[] methodAnnotations, Retrofit retrofit) {
        TypeAdapter<?> adapter = gson.getAdapter(TypeToken.get(type));
        return new GsonRequestBodyConverter<>(gson, adapter);
    }

}
```

从代码中可知，`CustomConverterFactory` 内部是根据 `CustomResponseConverter` 来转化 JSON 的，这才是我们的重点。

``` java
class CustomResponseConverter<T> implements Converter<ResponseBody, T> {

    private final Gson gson;
    private final TypeAdapter<T> adapter;
    private static final String CODE = "code";
    private static final String DATA = "data";

    CustomResponseConverter(Gson gson, TypeAdapter<T> adapter) {
        this.gson = gson;
        this.adapter = adapter;
    }

    @Override
    public T convert(ResponseBody value) throws IOException {
        try {
            String originalBody = value.string();
            // 先 AES 解密
            String body = AESCryptUtils.decrypt(originalBody, AppConstant.getAESKey());
            // 再获取 code 
            JSONObject json = new JSONObject(body);
            int code = json.optInt(CODE);
            // 当 code 不为 200 时，设置 data 为 null，这样转化就不会出错了
            if (code != 200) {
                Map<String, String> map = gson.fromJson(body, new TypeToken<Map<String, String>>() {
                }.getType());
                map.put(DATA, null);
                body = gson.toJson(map);
            }
            return adapter.fromJson(body);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        } finally {
            value.close();
        }
    }
}
```

代码也是很简单的，相信也不需要解释了。o(∩_∩)o

最后就是使用了 `CustomConverterFactory` ：

``` java
OkHttpClient client = new OkHttpClient.Builder()
                .addNetworkInterceptor(new TokenHeaderInterceptor())
                .addInterceptor(new RequestEncryptInterceptor())
                .build();
Retrofit retrofit = new Retrofit.Builder().baseUrl(BuildConfig.BASE_URL)
                .client(client).addConverterFactory(CustomConverterFactory.create()).build();
```

好了，这下真的把该讲的都讲完了，大家可以散了。

完结了。

再见！

再见！

再见！

重要的说三遍！！！

再说最后一遍，再见！！！

00:03
=====
**References**

* [如何使用Retrofit请求非Restful API](http://www.jianshu.com/p/2263242fa02d)
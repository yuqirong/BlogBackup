title: 实现WebView中JS和App之间的交互
date: 2016-03-07 19:53:12
categories: Android Tips
tags: [Android,WebView]
---
Native App 和 JS 交互
======
今天被问到了一个问题：在 WebView 中加载了一个网页，点击网页中的按钮，如何跳转到指定 Activity ？当时听到后脸上就写了大大的“懵逼”两个字，一时词穷，没回答上来。之前对 WebView 也没有更深入地了解，只是简单地用来加载网页而已。

![这里写图片的描述](http://ofyt9w4c2.bkt.clouddn.com/20160307/20160307200816.png)

之后在脑海中回想到 WebView 中的 JS 可以和 app 产生交互，于是搜索了一下，果然网上有类似的实现效果。看了一下，在这里就做一个简单的笔记了以便之后查看。

在 WebView 中想要 JS 和 app 产生交互，就不得不提一个方法，那就是`addJavascriptInterface(Object object, String name)`：

* 第一个参数：绑定到 JavaScript 的类实例。
* 第二个参数：用来显示 JavaScript 中的实例的名称。

这里只是给出了参数的解释，如果你没看懂，那接下来就告诉你答案。

那就开始吧，在创建新的 project 之前，我们先把要加载的 test.html 写好，放在 assets 目录下：

``` html
<html>
	<head>
	    <title>WebView Test</title>
	    <script type="text/javascript">
		function btnShowToast(){
		    window.testJS.showToast();
		}
	
		function btnGoActivity(){
		    window.testJS.goActivity();
		}
	    </script>
	</head>
	<body>
		<p>This is a website</p>
		<br>
		<button onclick='btnShowToast();'>show Toast</button>
		<button onclick='btnGoActivity();'>go Activity</button>
	</body>
</html>
```

上面的 html 很简单，相信有点基础的同学都能看得懂。要注意的是在JS函数中的 testJS 是要和 WebView 约定好的，这里就取名叫 testJS 吧，在下面会用到。还有`showToast()`和`goActivity()`也是约定好的函数名。我们预期的效果是点击 show Toast 按钮会显示Toast，而点击 go Activity 按钮会跳转到另外一个 Activity 上。

下面创建了一个 project ，名叫 WebViewDemo ，工程中 MainActivity 的 layout.xml 就只有一个 WebView 了：

``` xml
<?xml version="1.0" encoding="utf-8"?>
<RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    tools:context="com.yuqirong.webviewdemo.MainActivity">

    <WebView
        android:id="@+id/mWebView"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />
    
</RelativeLayout>
```

MainActivity 的代码很短，就直接贴出来了：

``` java
public class MainActivity extends AppCompatActivity {

    private WebView mWebView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mWebView = (WebView) findViewById(R.id.mWebView);
        // 设置支持JS
        mWebView.getSettings().setJavaScriptEnabled(true);
        // 增加JS交互的接口
        mWebView.addJavascriptInterface(new AndroidJSInterface(this), "testJS");
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false;
            }
        });
        String url = "file:///android_asset/test.html";
        mWebView.loadUrl(url);
    }
}
```

我们可以看到，如果想要和 JS 交互，那么`mWebView.getSettings().setJavaScriptEnabled(true);`这句是必不可少的，再看到下面一行代码：`mWebView.addJavascriptInterface(new AndroidJSInterface(this), "testJS");`，这里注意一下第二个参数，没错，就是在 html 中的 testJS ！

再看回第一个参数，发现 new 了一个 AndroidJSInterface 类，下面就是 AndroidJSInterface 的代码：

``` java
public class AndroidJSInterface {

    private Context context;

    public AndroidJSInterface(Context context) {
        this.context = context;
    }

    @JavascriptInterface
    public void goActivity() {
        context.startActivity(new Intent(context, SecondActivity.class));
    }

    @JavascriptInterface
    public void showToast() {
        Toast.makeText(context, "hello js", Toast.LENGTH_SHORT).show();
    }

}
```
我们可以看到上面的 `showToast()` 和 `goActivity()` 方法名和 html 里面的一定要一样，不然无法触发了。然后在方法的内部实现你想要的逻辑。这里需要注意一下，JS 回调方法是在子线程中运行的，所以不能有 UI 操作。

经过上面的步骤，就可以实现和JS交互了，一起来看看效果吧：

![这里写图片的描述](http://ofyt9w4c2.bkt.clouddn.com/20160307/20160307225743.gif)

JS 交互封装
======
假如现在的情景是有很多个 JSInterface 需要和 App 交互，那么我们应该怎么样去设计 JSInterface 呢？难道要一个一个去写 `@JavascriptInterface` 吗。

聪明的程序猿就会想到把 JSInterface 封装一下，封装成一个易于拓展的工具类。接下来就带大家去改变一下 JSInterface 。

首先创建一个 BaseJavaScriptInterface 基类，之后所有的 JSInterface 都要继承于它：

``` java
public abstract class BaseJavaScriptInterface {

    public abstract void onActionEvent(WebView webView, String message);

    public void onMessageBack(final WebView webview, final String methodName, final Object obj) {
        if (webview != null) {
            webview.post(new Runnable() {
                @Override
                public void run() {
                    webview.loadUrl("javascript:" + methodName + "('" + obj + "')");
                }
            });
        }
    }

}
```

可以看到，上面有两个方法：

* `onActionEvent(WebView webView, String message)` ：主要用来 JS 调用 Native App 。`webView` 为当前回调发生的 WebView ，`message` 为 JS 传过来的参数；
* `onMessageBack(final WebView webview, final String methodName, final Object obj)` ：Native App 把参数回传给 JS 。`methodName` 为 JS 方法名，`obj` 为 JS 方法参数。

比如我们创建了 `TestJavaScriptInterface` ，就如下所示。这样做的好处就是针对每个不同 JSInterface 的逻辑都在自己的 `onActionEvent(WebView webView, String message)` 去实现中，简洁易懂。

``` java
public class TestJavaScriptInterface extends BaseJavaScriptInterface {

    @Override
    public void onActionEvent(WebView webView, String message) {
        Toast.makeText(webView.getContext(),message,Toast.LENGTH_SHORT).show();
        onMessageBack(webView,"callback","hello javascript");
    }

}
```

只有上面这样当然是不够的，我们把主要的逻辑放在了 `WebViewJSBridge` 中。顾名思义， `WebViewJSBridge` 就是 WebView 和 JS 交互的桥梁：

``` java
public class WebViewJSBridge {

    private static final String TAG = "WebViewJSBridge";
    // js交互接口的缓存
    private static HashMap<String, BaseJavaScriptInterface> cacheJSInterfaces = new HashMap<>();

    // 包括了所有的javascriptinterface
    private static HashMap<String,String> jsInterfaceMap = new HashMap<>();

    static {
        jsInterfaceMap.put("test","com.yuqirong.daggerdemo.webview.TestJavaScriptInterface");
    }

    private WeakReference<WebView> mWebViewReference;
    
    public WebViewJSBridge(WebView webView) {
        mWebViewReference = new WeakReference<>(webView);
    }

    /**
     * 给WebView添加JS交互接口
     *
     * @param webview
     * @param methodName
     */
    @SuppressLint("JavascriptInterface")
    public void addJavascriptInterface(WebView webview, String methodName) {
        if (webview != null && methodName != null) {
            WebSettings settings = webview.getSettings();
            settings.setJavaScriptEnabled(true);
            webview.addJavascriptInterface(this, methodName);
        } else {
            Log.e(TAG, "the method name of javascript can not be null");
        }
    }

    /**
     * native客户端从JS得到对应数据
     *
     * @param json JS传给客户端的JSON数据，格式为{"key":"xxx", "message":"xxx"}
     */
    @JavascriptInterface
    public void onMessageRecevice(String json) {
        try {
            JSONObject jsonObejct = new JSONObject(json);
            String key = jsonObejct.getString("key");
            String msg = jsonObejct.getString("message");
            BaseJavaScriptInterface javaScriptInterface = getJavaScriptInterface(key);
            if (javaScriptInterface != null) {
                // 执行 JSInterface 的 onActionEvent
                javaScriptInterface.onActionEvent(mWebViewReference.get(), msg);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }


    /**
     * 通过传入的key，得到相应的JSInterface
     *
     * @param key
     * @return
     */
    private BaseJavaScriptInterface getJavaScriptInterface(String key) {
        if (TextUtils.isEmpty(key)) {
            return null;
        }
        // 先从缓存中查找
        BaseJavaScriptInterface javaScriptInterface = cacheJSInterfaces.get(key);
        if (javaScriptInterface != null) {
            return javaScriptInterface;
        }
        // 若没有缓存，通过反射创建，放入缓存中
        String interfaceName = jsInterfaceMap.get(key);
        if (!TextUtils.isEmpty(interfaceName)) {
            try {
                Class clazz = Class.forName(interfaceName);
                Object o = clazz.newInstance();
                if (o instanceof BaseJavaScriptInterface) {
                    BaseJavaScriptInterface newInterface = (BaseJavaScriptInterface) o;
                    cacheJSInterfaces.put(key, newInterface);
                    return newInterface;
                }
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public void onDestroy(){
        cacheJSInterfaces.clear();
    }

}
```

在 `WebViewJSBridge` 中，我们约定俗成，JS 想要和 Native App 交互，就必须通过 `window.android.onMessageRecevice(data);` 的方法。也就是会回调 `WebViewJSBridge` 中的 `onMessageRecevice` 方法。而 JS 传过来 JSON 参数的格式必须是 `{"key":"xxx", "message":"xxx"}` 。因为 `WebViewJSBridge` 会通过 key 来得到真正调用的 JSInterface 。之后我们新创建的 JSInterface 都要放入到 `jsInterfaceMap` 中，就好像上面的 `TestJavaScriptInterface` 一样。

啰里啰唆讲了这么多，那我们到底如何使用 `WebViewJSBridge` 呢？

``` java
webview = (WebView) findViewById(R.id.webview);
WebViewJSBridge bridge = new WebViewJSBridge(webview);
bridge.addJavascriptInterface(webview,"android");
```

没有看错，只要这三行代码就可以了。简单明了，只要在你想要实现与 JS 交互的 WebView 中添加这三行代码，其他的都交给 `WebViewJSBridge` 去做吧！

当然上面封装的 `WebViewJSBridge` 缺乏了自定义性。需要各位根据自身情况来修改 JSON 具体数据的格式，还有 JS 交互的方法名可以和前端工程师约定一下，剩下的基本上都现成提供了。

好了，差不多讲完了，那么今天就这样了。看完了本篇文章后可千万不要说不会 JS 和 Native App 交互哦。

源码下载：

[WebViewDemo.rar](http://ofytl4mzu.bkt.clouddn.com/20160307/WebViewDemo.rar)
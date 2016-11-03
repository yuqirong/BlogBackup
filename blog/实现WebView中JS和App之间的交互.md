title: 实现WebView中JS和App之间的交互
date: 2016-03-07 19:53:12
categories: Android Tips
tags: [Android,WebView]
---
今天被问到了一个问题：在 WebView 中加载了一个网页，点击网页中的按钮，如何跳转到指定 Activity ？当时听到后脸上就写了大大的“懵逼”两个字，一时词穷，没回答上来。之前对 WebView 也没有更深入地了解，只是简单地用来加载网页而已。

![这里写图片的描述](/uploads/20160307/20160307200816.png)

之后在脑海中回想到 WebView 中的JS可以和app产生交互，于是搜索了一下，果然网上有类似的实现效果。看了一下，在这里就做一个简单的笔记了以便之后查看。

在 WebView 中想要JS和app产生交互，就不得不提一个方法，那就是`addJavascriptInterface(Object object, String name)`：

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
我们可以看到，如果想要和JS交互，那么`mWebView.getSettings().setJavaScriptEnabled(true);`这句是必不可少的，再看到下面一行代码：`mWebView.addJavascriptInterface(new AndroidJSInterface(this), "testJS");`，这里注意一下第二个参数，没错，就是在 html 中的 testJS ！

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
我们可以看到上面的`showToast()`和`goActivity()`方法名和 html 里面的一定要一样，不然无法触发了。然后在方法的内部实现你想要的逻辑。

经过上面的步骤，就可以实现和JS交互了，一起来看看效果吧：

![这里写图片的描述](/uploads/20160307/20160307225743.gif)

源码下载：

[WebViewDemo.rar](/uploads/20160307/WebViewDemo.rar)
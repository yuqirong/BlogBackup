title: 轻松实现app中的导航Tab栏悬浮功能
date: 2016-01-12 21:59:29
categories: Android Blog
tags: [Android]
---
又到了更博的时间了，今天给大家带来的就是“导航Tab栏悬浮功能”了。通常大家在玩手机的过程中应该会注意到很多的app都有这种功能，比如说外卖达人常用的“饿了么”。下面就给出了“饿了么”导航Tab栏悬浮的效果图。

![这里填写图片的描述](/uploads/20160112/20160112210653.gif)

可以看到上图中的“分类”、“排序”、“筛选”会悬浮在app的顶部，状态随着ScrollView(也可能不是ScrollView，在这里姑且把这滑动的UI控件当作ScrollView吧)的滚动而变化。像这种导航Tab栏悬浮的作用相信大家都能体会到，Tab栏不会随着ScrollView等的滚动而被滑出屏幕外，增加了与用户之间的交互性和方便性。

看到上面的效果，相信大家都跃跃欲试了，那就让我们开始吧。

首先大家要明白一点：Tab栏的状态变化是要监听ScrollView滑动距离的。至于如何得到ScrollView的滑动距离？可以看看我的一篇Tip：[《给你的ScrollView设置滑动距离监听器》](/2015/10/19/给你的ScrollView设置滑动距离监听器/)，这里就不过多叙述了。

好了，根据上面的就得到了对ScrollView滑动的监听了。接下来要思考的问题就是如何让Tab栏实现悬浮的效果呢？这里给出的方法有两种，第一种就是使用WindowManager来动态地添加一个View悬浮在顶部；第二种就是随着ScrollView的滑动不断重新设置Tab栏的布局位置。

我们先来看看第一种实现方法，首先是xml布局了。

Activity的布局，activity_main.xml：

``` xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <RelativeLayout
        android:id="@+id/rl_title"
        android:layout_width="match_parent"
        android:layout_height="50dp"
        android:background="@color/colorPrimary">

        <ImageView
            android:id="@+id/iv_back"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_centerVertical="true"
            android:layout_marginLeft="10dp"
            android:src="@drawable/new_img_back" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_centerInParent="true"
            android:text="@string/app_name"
            android:textColor="@android:color/white"
            android:textSize="18sp" />

    </RelativeLayout>

    <com.yuqirong.tabsuspenddemo.view.MyScrollView
        android:id="@+id/mScrollView"
        android:layout_width="match_parent"
        android:layout_height="match_parent">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:background="#cccccc"
            android:orientation="vertical">

            <ImageView
                android:id="@+id/iv_pic"
                android:layout_width="match_parent"
                android:layout_height="180dp"
                android:scaleType="centerCrop"
                android:src="@drawable/ic_bg_personal_page" />

            <include layout="@layout/tab_layout" />

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="90dp"
                android:layout_marginBottom="5dp"
                android:layout_marginLeft="15dp"
                android:layout_marginRight="15dp"
                android:layout_marginTop="15dp"
                android:background="@android:color/white"
                android:orientation="horizontal">

            </LinearLayout>

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="90dp"
                android:layout_marginBottom="5dp"
                android:layout_marginLeft="15dp"
                android:layout_marginRight="15dp"
                android:layout_marginTop="15dp"
                android:background="@android:color/white"
                android:orientation="horizontal">

            </LinearLayout>

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="90dp"
                android:layout_marginBottom="5dp"
                android:layout_marginLeft="15dp"
                android:layout_marginRight="15dp"
                android:layout_marginTop="15dp"
                android:background="@android:color/white"
                android:orientation="horizontal">

            </LinearLayout>

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="90dp"
                android:layout_marginBottom="5dp"
                android:layout_marginLeft="15dp"
                android:layout_marginRight="15dp"
                android:layout_marginTop="15dp"
                android:background="@android:color/white"
                android:orientation="horizontal">

            </LinearLayout>

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="90dp"
                android:layout_marginBottom="5dp"
                android:layout_marginLeft="15dp"
                android:layout_marginRight="15dp"
                android:layout_marginTop="15dp"
                android:background="@android:color/white"
                android:orientation="horizontal">

            </LinearLayout>


            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="90dp"
                android:layout_marginBottom="5dp"
                android:layout_marginLeft="15dp"
                android:layout_marginRight="15dp"
                android:layout_marginTop="15dp"
                android:background="@android:color/white"
                android:orientation="horizontal">

            </LinearLayout>

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="90dp"
                android:layout_marginBottom="5dp"
                android:layout_marginLeft="15dp"
                android:layout_marginRight="15dp"
                android:layout_marginTop="15dp"
                android:background="@android:color/white"
                android:orientation="horizontal">

            </LinearLayout>
            
        </LinearLayout>
    </com.yuqirong.tabsuspenddemo.view.MyScrollView>
</LinearLayout>
```

Tab栏的布局，tab_layout.xml：

``` xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/ll_tab"
    android:layout_width="match_parent"
    android:layout_height="40dp"
    android:background="@color/colorPrimary"
    android:orientation="horizontal">

    <TextView
        android:layout_width="0dp"
        android:layout_height="match_parent"
        android:layout_weight="1"
        android:gravity="center"
        android:text="分类"
        android:textColor="@android:color/white"
        android:textSize="18sp" />

    <TextView
        android:layout_width="0dp"
        android:layout_height="match_parent"
        android:layout_weight="1"
        android:gravity="center"
        android:text="排序"
        android:textColor="@android:color/white"
        android:textSize="18sp" />

    <TextView
        android:layout_width="0dp"
        android:layout_height="match_parent"
        android:layout_weight="1"
        android:gravity="center"
        android:text="筛选"
        android:textColor="@android:color/white"
        android:textSize="18sp" />

</LinearLayout>
```

上面布局中的很多空白LinearLayout主要是拉长ScrollView，效果图就是这样的：

![这里填写图片的描述](/uploads/20160112/20160112201753.png)

然后我们来看看`onCreate(Bundle savedInstanceState)`：

``` java
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getSupportActionBar().hide();
    setContentView(R.layout.activity_main);
    mScrollView = (MyScrollView) findViewById(R.id.mScrollView);
    mScrollView.setOnScrollListener(this);
    ll_tab = (LinearLayout) findViewById(R.id.ll_tab);
    windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
}
```

我们先在`onCreate(Bundle savedInstanceState)`中给ScrollView添加了滑动距离监听器以及得到了一个windowManager的对象。还有一点需要注意的是：我们调用了`getSupportActionBar().hide();`去掉了标题栏(MainActivity继承了AppCompatActivity)。这是因为标题栏的存在导致了在计算悬浮窗y轴的值时要额外加上标题栏的高度(当然你也可以保留标题栏，然后计算时再加上标题栏的高度^_^!)。

然后在onWindowFocusChanged(boolean hasFocus)得到Tab栏的高度、getTop()值等，以便下面备用。如果你对getLeft()、getTop()、getRight()和getBottom()还不了解的话，可以看看我的另一篇Tip： [《对view的getLeft()、getTop()等的笔记》][url]。

[url]: /2016/01/05/%E5%AF%B9view%E7%9A%84getLeft()%E3%80%81getTop()%E7%AD%89%E7%9A%84%E7%AC%94%E8%AE%B0/

``` java
@Override
public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    if (hasFocus) {
        tabHeight = ll_tab.getHeight();
        tabTop = ll_tab.getTop();
        scrollTop = mScrollView.getTop();
    }
}
```

之后在滑动监听器的回调方法`onScroll(int scrollY)`中来控制显示还是隐藏悬浮窗。

``` java
@Override
public void onScroll(int scrollY) {
    Log.i(TAG, "scrollY = " + scrollY + ", tabTop = " + tabTop);
    if (scrollY > tabTop) {
		// 如果没显示
        if (!isShowWindow) {
            showWindow();
        }
    } else {
		// 如果显示了
        if (isShowWindow) {
            removeWindow();
        }
    }
}
```

上面的代码比较简单，不用我过多叙述了。下面是removeWindow()、showWindow()两个方法：

``` java
// 显示window
private void removeWindow() {
    if (ll_tab_temp != null)
        windowManager.removeView(ll_tab_temp);
    isShowWindow = false;
}

// 移除window
private void showWindow() {
    if (ll_tab_temp == null) {
        ll_tab_temp = LayoutInflater.from(this).inflate(R.layout.tab_layout, null);
    }
    if (layoutParams == null) {
        layoutParams = new WindowManager.LayoutParams();
        layoutParams.type = WindowManager.LayoutParams.TYPE_PHONE; //悬浮窗的类型，一般设为2002，表示在所有应用程序之上，但在状态栏之下
        layoutParams.format = PixelFormat.RGBA_8888;
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;  //悬浮窗的行为，比如说不可聚焦，非模态对话框等等
        layoutParams.gravity = Gravity.TOP;  //悬浮窗的对齐方式
        layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        layoutParams.height = tabHeight;
        layoutParams.x = 0;  //悬浮窗X的位置
        layoutParams.y = scrollTop;  //悬浮窗Y的位置
    }
    windowManager.addView(ll_tab_temp, layoutParams);
    isShowWindow = true;
}
```

这两个方法也很简单，而且有注释，相信大家可以看懂。

最后，不要忘了在AndroidManifest.xml里申请显示悬浮窗的权限：

	<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />

到这里，整体的代码就这些了。一起来看看效果吧：

![这里填写图片的描述](/uploads/20160112/20160112204356.gif)

但是用这种方法来实现Tab栏悬浮功能有一个缺点，那就是如果该app没有被赋予显示悬浮窗的权限，那么该功能就变成鸡肋了。当然还有第二种方法来实现，不过只能等到下一篇博文再讲了。

本Demo源码下载：

[TabSuspendDemo.rar](/uploads/20160112/TabSuspendDemo.rar)

have a nice day~

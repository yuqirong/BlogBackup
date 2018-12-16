title: 实现导航Tab栏悬浮功能之改进版
date: 2016-01-14 20:34:25
categories: Android Blog
tags: [Android]
---
在上一篇博文中，我们用WindowManager的方法实现了Tab栏的悬浮功能。如果你没有看过上篇博文，请点击[《轻松实现app中的导航Tab栏悬浮功能》][url]。
[url]: /2016/01/12/%E8%BD%BB%E6%9D%BE%E5%AE%9E%E7%8E%B0app%E4%B8%AD%E7%9A%84%E5%AF%BC%E8%88%AATab%E6%A0%8F%E6%82%AC%E6%B5%AE%E5%8A%9F%E8%83%BD/

当然，用WindowManager来实现由一个缺点就是当没有显示悬浮窗的权限时，该功能就无法体现出来。而在本篇博文中，我们用第二种方法，也就是不断地重新设置Tab栏的布局位置来实现悬浮功能，弥补了第一种方法的缺点。效果图这里就不放了，相信大家都看过啦。

不废话了，直接上代码。

activity_main.xml：
``` xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/ll_main"
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

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="90dp"
                android:layout_marginBottom="5dp"
                android:layout_marginLeft="15dp"
                android:layout_marginRight="15dp"
                android:layout_marginTop="55dp"
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

            <include layout="@layout/tab_layout" />

        </LinearLayout>
    </com.yuqirong.tabsuspenddemo.view.MyScrollView>
</LinearLayout>
```
tab_layout.xml：
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
我们发现在activity_main.xml里Tab栏悬浮窗的布局放在了最后，这是因为当悬浮窗悬浮在顶部时，应该在所有的UI控件上方，所以在xml里放在了最后。

接下来看看MainActivity：
``` java
public class MainActivity extends AppCompatActivity implements MyScrollView.OnScrollListener {

    private static final String TAG = "MainActivity";
    private MyScrollView mScrollView;
    private LinearLayout ll_tab;
    private ImageView iv_pic;
    private int picBottom;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getSupportActionBar().hide();
        mScrollView = (MyScrollView) findViewById(R.id.mScrollView);
        mScrollView.setOnScrollListener(this);
        ll_tab = (LinearLayout) findViewById(R.id.ll_tab);
        iv_pic = (ImageView) findViewById(R.id.iv_pic);
        findViewById(R.id.ll_main).getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                onScroll(mScrollView.getScrollY());
            }
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            picBottom = iv_pic.getBottom();
        }
    }

    @Override
    public void onScroll(int scrollY) {
        int top = Math.max(scrollY, picBottom);
        ll_tab.layout(0, top, ll_tab.getWidth(), top + ll_tab.getHeight());
    }

}
```
我们惊奇地发现在Activity里的代码竟然这么短！但是这是这么短，实现了一模一样的功能。

首先在父布局中添加了OnGlobalLayoutListener，以便当布局的状态或者控件的可见性改变时去重新设置Tab栏的布局。之后在`onWindowFocusChanged(boolean hasFocus)`里得到`iv_pic.getBottom()`的值，也就是`iv_pic`的高度。也就是说你一开始想把`ll_tab`布局在`iv_pic`的下面。因此可以当作Tab栏距离ScrollView顶部的距离。

最后在`onScroll(int scrollY)`中比较scrollY，picBottom的最大值。当`scrollY<picBottom`时，`ll_tab`会跟随ScrollView的滑动而滑动；当`scrollY>picBottom`时，`ll_tab`布局的顶部的坐标始终是ScrollView的滑动距离，这样就造成了`ll_tab`悬浮在顶部的“假象”。

好了，一起来看看效果吧：

![这里填写图片的描述](/uploads/20160114/20160114223247.gif)

是不是和第一种方法的效果一样呢，相信大家都学会了。如果有问题可以在下面留言。

最后，放出源码：

[TabSuspendDemo.rar](/uploads/20160114/TabSuspendDemo.rar)

~have fun!~
title: 对view的getLeft()、getTop()等的笔记
date: 2016-01-05 20:01:48
categories: Android Tips
tags: [Android]
---
在今天的开发中，遇到了一个之前没有关注过的细节。那就是我用`view.getTop()`来获取view距离屏幕顶部高度，结果发现得到的数值和理论不一致。我们来举个例子吧，比如我们有如下的布局：

``` xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">


    <LinearLayout
        android:id="@+id/ll_01"
        android:layout_width="match_parent"
        android:layout_height="60dp"
        android:orientation="vertical"
        android:background="@android:color/holo_green_light">

        <TextView
            android:id="@+id/tv_01"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="我是第一行文字" />
    </LinearLayout>

    <LinearLayout
        android:id="@+id/ll_02"
        android:layout_width="match_parent"
        android:layout_height="60dp"
        android:orientation="vertical"
        android:background="@android:color/holo_blue_light">

        <TextView
            android:id="@+id/tv_02"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="我是第二行文字" />
    </LinearLayout>

</LinearLayout>
```

上面是一个很简单的布局，UI效果图如下：

![这里填写图片的描述](/uploads/20160105/20160105202531.png)

当我们用`tv_01.getTop()`的时候，得到的返回值是0，符合我的想象。但是用`tv_02.getTop()`，得到的值也为0。而我原以为`tv_02.getTop()`的值为`ll_01`的高度，也就是`tv_02`距离屏幕顶部的长度。但是结果和我的想象不一致。

后来我才知道原来`getTop()`方法返回的是该view距离**父容器顶部**的距离，所以理所应当`tv_02.getTop()`距离`ll_02`顶部的距离也为0了，同样的`getLeft()`、`getBottom()`、`getRight()`也是一个道理，以此类推。

那么问题来了，如何按我之前的想法一样，得到`tv_02`距离**屏幕顶部**的值呢？很简单，我们只要`tv_02.getTop() + ll_02.getTop()`就好了。相信聪明的你已经懂了。

看来在开发中还有不少的细节需要我们注意，特此一记。

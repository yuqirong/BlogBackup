title: 初探MD式转场动画
date: 2016-03-23 16:44:22
categories: Android Tips
tags: [Android,Material Design]
---
最近在做MD设计风格的APP，所以在转场动画上当然也得符合MD了。下面就是效果图：

![这里写图片描述](/uploads/20160323/20160323165423.gif)

一开始并未了解过这种转场动画，原来是Google在SDK中已经给我们提供了。`ActivityOptions`是 Android 5.0 及以上使用的，但是也提供了`ActivityOptionsCompat`向下兼容。

下面我们就来看看吧：

layout_item.xml(ListView的item布局)：

``` xml
<?xml version="1.0" encoding="utf-8"?>
<RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="@android:color/white"
    android:padding="10dp">

    <ImageView
        android:id="@+id/iv_img"
        android:layout_width="90dip"
        android:layout_height="65dip"
        android:transitionName="photos"
        android:padding="1dp" />

    <TextView
        android:id="@+id/tv_title"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginLeft="8dp"
        android:layout_marginTop="2dp"
        android:layout_toRightOf="@id/iv_img"
        android:singleLine="true"
        android:textAppearance="@style/TextAppearance.AppCompat.Subhead"
        android:textColor="?android:attr/textColorPrimary"
        tools:text="标题" />

    <TextView
        android:id="@+id/tv_content"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_below="@id/tv_title"
        android:layout_marginLeft="8dp"
        android:layout_marginTop="8dp"
        android:layout_toRightOf="@id/iv_img"
        android:maxLines="2"
        android:textAppearance="@style/TextAppearance.AppCompat.Small"
        android:textColor="?android:attr/textColorPrimary"
        tools:text="标题标题标题标题标题标题标题标题标题标题标题标题标题标题标题标题" />

    <TextView
        android:id="@+id/tv_time"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_alignParentRight="true"
        android:layout_below="@+id/tv_content"
        android:maxLines="1"
        android:text="2016-02-25 11:22:23"
        android:textAppearance="@style/TextAppearance.AppCompat.Small"
        android:textColor="?android:attr/textColorSecondary"
        android:textSize="12sp" />

</RelativeLayout>
```

我们会注意到在ImageView里有`android:transitionName="photos"`，这正是后面需要用到的。在这里的`photos`可以任意取名。也就是说你想让哪个View在转场时表现出动画，就在哪个View的xml中添加`android:transitionName`。

之后就是我们点击Item时应该跳转到另一个Activity中(这里就跳转到NewsDetailActivity了)，这其中的逻辑如下：
	
```java
// Android 5.0 使用转场动画
if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
    ActivityOptions options = ActivityOptions
            .makeSceneTransitionAnimation(getActivity(),
                    itemView.findViewById(R.id.iv_img), "photos");
    startActivity(NewsDetailActivity.class, bundle, options.toBundle());
} else {
    //让新的Activity从一个小的范围扩大到全屏
    ActivityOptionsCompat options = ActivityOptionsCompat
            .makeScaleUpAnimation(itemView, itemView.getWidth() / 2,
                    itemView.getHeight() / 2, 0, 0);
    startActivity(NewsDetailActivity.class, bundle, options.toBundle());
}
```

可以看到在Android 5.0时使用的`makeSceneTransitionAnimation()`方法中的第三个参数正是上面的`"photos"`。当然在5.0版本以下我们只能使用兼容的`ActivityOptionsCompat`了。

最后在要跳转的Activity的布局中也添加`android:transitionName="photos"`，这样就形成了一个MD式转场动画了。

以下是NewsDetailActivity的布局xml(只截取了部分)：

``` xml
	<ImageView
	    android:id="@+id/iv_album"
	    android:layout_width="match_parent"
	    android:layout_height="256dp"
	    android:scaleType="centerCrop"
	    android:src="@drawable/thumbnail_default"
	    android:transitionName="photos"
	    app:layout_collapseMode="parallax"
	    app:layout_collapseParallaxMultiplier="0.7" />
```

好了，这样就完成了，如果你需要在NewsDetailActivity执行finish时也出现转场动画，你只需要这样做(这里只给出了`onBackPressed()`的样例)：

``` java
@Override
public void onBackPressed() {
    super.onBackPressed();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        finishAfterTransition();
    }
}
```

其实关于`ActivityOptions`和`ActivityOptionsCompat`转场动画还有更多选择，可以深入研究一下。

Reference
============
[你所不知道的Activity转场动画——ActivityOptions](http://www.lxway.com/895445426.htm)
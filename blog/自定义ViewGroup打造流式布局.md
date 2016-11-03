title: 自定义ViewGroup打造流式布局
date: 2016-01-22 20:31:37
categories: Android Blog
tags: [Android,自定义View]
---
前几天看了[鸿洋_](http://my.csdn.net/lmj623565791)的[《Android 自定义ViewGroup 实战篇 -> 实现FlowLayout》](http://blog.csdn.net/lmj623565791/article/details/38352503)，觉得文中的FlowLayout很多地方都可以用到。于是自己按照思路实现了一遍，这就是本片博文诞生的原因了。

首先流式布局相信大家都见到过，比如说下图中的京东热搜就是流式布局的应用。还有更多应用的地方在这里就不一一举例了。

![这里写图片描述](/uploads/20160122/20160122205447.png)

下面我们就来看看是如何实现的。首先新建一个class，继承自ViewGroup。在`generateLayoutParams(AttributeSet attrs)`里直接返回MarginLayoutParams就行了。
``` java
@Override
public LayoutParams generateLayoutParams(AttributeSet attrs) {
    return new MarginLayoutParams(getContext(), attrs);
}
```
然后就是`onLayout(boolean changed, int l, int t, int r, int b)`了，大部分的代码都添加了注释，相信大家都能看懂。
``` java
@Override
protected void onLayout(boolean changed, int l, int t, int r, int b) {
    int count = getChildCount();
    int cWidth;
    int cHeight;
    MarginLayoutParams params;
	// 用来统计总宽度，初始值设为paddingLeft
    int lastWidth = getPaddingLeft();
    int lastHeight = getPaddingTop();

    for (int i = 0; i < count; i++) {
		// 得到当前View
        View childView = getChildAt(i);
		// 测量得到当前View的宽度
        cWidth = childView.getMeasuredWidth();
		// 测量得到当前View的高度
        cHeight = childView.getMeasuredHeight();
        params = (MarginLayoutParams) childView.getLayoutParams();
		// 宽度加上margin
        int width = cWidth + params.leftMargin + params.rightMargin;
		// 高度加上margin
        int height = cHeight + params.topMargin + params.bottomMargin;
		// 判断流式布局里的item总长度是否超过FlowLayout的宽度，如果是则需要换行  
        if (width + lastWidth > r - getPaddingRight()) {
			// 如果超过，重置lastWidth
            lastWidth = getPaddingLeft();
			// lastHeight加上一个item的高度
            lastHeight += height;
        }
		// 给View布局
        childView.layout(lastWidth, lastHeight, lastWidth + cWidth, lastHeight + cHeight);
		//累加总宽度
        lastWidth += width;
    }
}
```
之后就是`onMeasure(int widthMeasureSpec, int heightMeasureSpec)`。
``` java
@Override
protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int widthSize = MeasureSpec.getSize(widthMeasureSpec);
    int widthMode = MeasureSpec.getMode(widthMeasureSpec);

    int heightSize = MeasureSpec.getSize(heightMeasureSpec);
    int heightMode = MeasureSpec.getMode(heightMeasureSpec);

    int totalWidth = getPaddingLeft() + getPaddingRight();
    int maxWidth = getPaddingLeft() + getPaddingRight();
    int maxHeight = getPaddingBottom() + getPaddingTop();
    int count = getChildCount();
	// 测量子view
    measureChildren(widthMeasureSpec, heightMeasureSpec);

    int cWidth;
    int cHeight;
    MarginLayoutParams params;
    int width;
    int height;
    for (int i = 0; i < count; i++) {
        View childView = getChildAt(i);
        cWidth = childView.getMeasuredWidth();
        cHeight = childView.getMeasuredHeight();
        params = (MarginLayoutParams) childView.getLayoutParams();

        width = cWidth + params.leftMargin + params.rightMargin;
        height = cHeight + params.topMargin + params.bottomMargin;

        if (i == 0) {
			// 第一行时最大高度设为height
            maxHeight += height;
        }

		// 如果需要换行
        if (width + totalWidth > widthSize) {
			// 得到最大的值作为setMeasuredDimension()的宽度
            maxWidth = Math.max(maxWidth, totalWidth);
            totalWidth = getPaddingLeft() + getPaddingRight();
            totalWidth += width;
			// 高度就是累加到最后
            maxHeight += height;
        } else {
			// 不换行就总长度累加
            totalWidth += width;
        }
        Log.i(TAG, "i = " + i + ", width = " + width + ", totalWidth = " + totalWidth + ", widthSize = " + widthSize + ((TextView) childView).getText());
        Log.i(TAG, "height = " + height);
        Log.i(TAG, "i = " + i + ", maxHeight = " + maxHeight);

    }
	// 设置宽高度
    setMeasuredDimension(widthMode == MeasureSpec.EXACTLY ? widthSize : maxWidth,
            heightMode == MeasureSpec.EXACTLY ? heightSize : maxHeight);

}
```
在`onMeasure(int widthMeasureSpec, int heightMeasureSpec)`中，如果测量模式是MeasureSpec.EXACTLY，则直接设置测量出来的宽和高；否则需要测量每个子View，根据item的行数来得到宽和高。

这样，FlowLayout就写好了，那就让我们来看看效果吧。当`android:layout_width="wrap_content"`时
``` xml
<?xml version="1.0" encoding="utf-8"?>
<com.yuqirong.viewgroup.view.FlowLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:background="#ddd"
    android:padding="20dp">

    <TextView
        style="@style/text_flag_01"
        android:text="杭州" />

    <TextView
        style="@style/text_flag_01"
        android:text="宁波" />

    <TextView
        style="@style/text_flag_01"
        android:text="上海" />

    <TextView
        style="@style/text_flag_01"
        android:text="北京" />

    <TextView
        style="@style/text_flag_01"
        android:text="重庆" />

    <TextView
        style="@style/text_flag_01"
        android:text="南昌" />

    <TextView
        style="@style/text_flag_01"
        android:text="苏州" />

</com.yuqirong.viewgroup.view.FlowLayout>
```
运行的效果图：

![这里写图片描述](/uploads/20160122/20160122220305.png)

当`android:layout_width="300dp"`时：
``` xml
<?xml version="1.0" encoding="utf-8"?>
<com.yuqirong.viewgroup.view.FlowLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="300dp"
    android:layout_height="wrap_content"
    android:background="#ddd"
    android:padding="20dp">

    <TextView
        style="@style/text_flag_01"
        android:text="杭州" />

    <TextView
        style="@style/text_flag_01"
        android:text="哈尔滨" />

    <TextView
        style="@style/text_flag_01"
        android:text="宁波" />

    <TextView
        style="@style/text_flag_01"
        android:text="呼和浩特" />

    <TextView
        style="@style/text_flag_01"
        android:text="上海" />

    <TextView
        style="@style/text_flag_01"
        android:text="北京" />

    <TextView
        style="@style/text_flag_01"
        android:text="重庆" />

    <TextView
        style="@style/text_flag_01"
        android:text="南昌" />

    <TextView
        style="@style/text_flag_01"
        android:text="苏州" />

</com.yuqirong.viewgroup.view.FlowLayout>
```
运行的效果图：

![这里写图片描述](/uploads/20160122/20160122220718.png)

最后，提供源码下载：

[FlowLayout.rar](/uploads/20160122/FlowLayout.rar)
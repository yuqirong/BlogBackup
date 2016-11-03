title: 给你的ScrollView设置滑动距离监听器
date: 2015-10-19 19:44:21
categories: Android Tips
tags: [Android,ScrollView]
---
ScrollView是我们经常使用的一个UI控件，也许你在使用ScrollView的过程中会发现，当你想监听ScrollView滑动的距离时却没有合适的监听器！当然在API 23中有`setOnScrollChangeListener(View.OnScrollChangeListener l)`可以使用，但是并不兼容低版本的API。那怎么办呢？只好重写ScrollView来实现对滑动距离的监听了。

话不多说，直接上代码：

``` java
public class MyScrollView extends ScrollView {

    private OnScrollListener listener;

	/**
	 * 设置滑动距离监听器
	 */
    public void setOnScrollListener(OnScrollListener listener) {
        this.listener = listener;
    }

    public MyScrollView(Context context) {
        super(context);
    }

    public MyScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MyScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

	// 滑动距离监听器
    public interface OnScrollListener{

		/**
		 * 在滑动的时候调用，scrollY为已滑动的距离
		 */
        void onScroll(int scrollY);
    }

    @Override
    public void computeScroll() {
        super.computeScroll();
        if(listener!=null){
            listener.onScroll(getScrollY());
        }
    }
}
```

上面重写的MyScrollView是在`computeScroll()`实现监听，因为ScrollView内部是通过Scroller来实现的，当滑动的时候会去调用`computeScroll()`方法，从而达到监听的效果。

当然还有另一种方法，就是在`onScrollChanged(int l, int t, int oldl, int oldt)`去监听，最后的效果是一样的：

``` java
public class MyScrollView extends ScrollView {

    private OnScrollListener listener;

    public void setOnScrollListener(OnScrollListener listener) {
        this.listener = listener;
    }

    public MyScrollView(Context context) {
        super(context);
    }

    public MyScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MyScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public interface OnScrollListener{
        void onScroll(int scrollY);
    }

     @Override  
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {  
        super.onScrollChanged(l, t, oldl, oldt);  
        if(listener != null){  
            listener.onScroll(t);  
        }  
    }  
}
```

下面提供MyScrollView的源码下载：

[MyScrollView.java(通过computeScroll监听)](/uploads/20151019/MyScrollView.java)

[MyScrollView.java(通过onScrollChanged监听)](/uploads/20151019/MyScrollView2.java)
title: 快速打造带有视差效果的ListView
date: 2016-04-19 13:35:32
categories: Android Blog
tags: [Android,自定义View]
---
在上一篇博文中，我们实现了仿美团的下拉刷新。而今天的主题还是与 ListView 有关，这次是来实现具有视差效果的 ListView 。

那么到底什么是视差效果呢？一起来看效果图就知道了：

![这里写图片描述](/uploads/20160419/20160419141952.gif)

我们可以看到 ListView 的 HeaderView 会跟随 ListView 的滑动而变大，HeaderView里的图片会有缩放效果。这些可以使用属性动画来实现。接下来我们就来动手吧！

首先自定义几个属性，在之后可以用到：

``` xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <declare-styleable name="ZoomListView">
		<!-- headerView的高度 -->
        <attr name="header_height" format="dimension|reference"></attr>
        <!-- headerView的最大高度 -->
		<attr name="header_max_height" format="dimension|reference"></attr>
		<!-- headerView里面的图片最大的伸缩量 -->
        <attr name="header_max_scale" format="float"></attr>
    </declare-styleable>
</resources>
```

之后创建 ZoomListView 类，继承自 ListView ：

``` java
public class ZoomListView extends ListView {
	
	// 最大的伸缩量
    private final float defaultHeaderMaxScale = 1.2f;
    // 头部最大的高度
    private float headerMaxHeight;
    // 头部初始高度
    private float headerHeight;
    // 头部默认初始高度
    private float defaultHeaderHeight;
    // 头部默认最大的高度
    private float defaultHeaderMaxHeight;
    private ImageView headerView;
    private ViewGroup.LayoutParams layoutParams;
    private LinearLayout linearLayout;
    // 最大的缩放值
    private float headerMaxScale;

    public ZoomListView(Context context) {
        this(context, null);
    }

    public ZoomListView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ZoomListView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        defaultHeaderHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 160, context.getResources().getDisplayMetrics());
        defaultHeaderMaxHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 240, context.getResources().getDisplayMetrics());
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ZoomListView);
        headerHeight = a.getDimension(R.styleable.ZoomListView_header_height, defaultHeaderHeight);
        headerMaxHeight = a.getDimension(R.styleable.ZoomListView_header_max_height, defaultHeaderMaxHeight);
        headerMaxScale = a.getFloat(R.styleable.ZoomListView_header_max_scale, defaultHeaderMaxScale);
        a.recycle();
        initView();
    }
	...
}
```

到这里都是按部就班式的，设置好自定义属性的初始值，之后调用 `initView()` ，那就来看看 `initView()` 方法：

``` java
private void initView() {
    headerView = new ImageView(getContext());
    headerView.setScaleType(ImageView.ScaleType.CENTER_CROP);
    linearLayout = new LinearLayout(getContext());
    linearLayout.addView(headerView);
    layoutParams = headerView.getLayoutParams();
    if (layoutParams == null) {
        layoutParams = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) headerHeight);
    } else {
        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        layoutParams.height = (int) headerHeight;
    }
    headerView.setLayoutParams(layoutParams);
    addHeaderView(linearLayout);
}

public void setDrawableId(int id) {
    headerView.setImageResource(id);
}
```

可以看出在 `initView()` 里我们创建了 headerView ，并添加到了ListView的头部。而 `setDrawableId(int id)` 就是给 headerView 设置相关图片的。

下面就是视差效果的主要实现代码了：

``` java
@Override
protected boolean overScrollBy(int deltaX, int deltaY, int scrollX, int scrollY, int scrollRangeX, int scrollRangeY, int maxOverScrollX, int maxOverScrollY, boolean isTouchEvent) {
    if (deltaY < 0 && isTouchEvent) {
        if (headerView.getHeight() < headerMaxHeight) {
            int newHeight = headerView.getHeight()
                    + Math.abs(deltaY / 3);
            headerView.getLayoutParams().height = newHeight;
            headerView.requestLayout();
            float temp = 1 + (headerMaxScale - 1f) * (headerView.getHeight() - headerHeight) / (headerMaxHeight - headerHeight);
            headerView.animate().scaleX(temp)
                    .scaleY(temp).setDuration(0).start();
        }
    }
    return super.overScrollBy(deltaX, deltaY, scrollX, scrollY, scrollRangeX, scrollRangeY, maxOverScrollX, maxOverScrollY, isTouchEvent);
}
```

我们重写了 `overScrollBy()` 方法，当 deltaY 小于0时(即 ListView 已经到顶端，但是用户手势还是向下拉)，去动态地设置 headerView 的高度以及 headerView 的 scale 值。这样就可以产生 headerView 变高以及图片放大的效果了。

接下来要考虑的问题就是当用户松开手指时，要恢复回原来的样子。所以我们应该在 `onTouchEvent(MotionEvent ev)` 里去实现相关操作：

``` java
@Override
public boolean onTouchEvent(MotionEvent ev) {
    switch (ev.getAction()) {
        case MotionEvent.ACTION_UP:
                startAnim();
            break;
    }
    return super.onTouchEvent(ev);
}

// 开始执行动画
private void startAnim() {
    ValueAnimator animator = ValueAnimator.ofFloat(headerView.getHeight(), headerHeight);
    animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            float fraction = (float) animation.getAnimatedValue();
            headerView.getLayoutParams().height = (int) fraction;
            headerView.requestLayout();
        }
    });
    animator.setDuration(500);
    animator.setInterpolator(new LinearInterpolator());

    ValueAnimator animator2 = ValueAnimator.ofFloat(headerView.getScaleX(), 1f);
    animator2.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            float fraction = (float) animation.getAnimatedValue();
            headerView.setScaleX(fraction);
            headerView.setScaleY(fraction);
        }
    });
    animator2.setDuration(500);
    animator2.setInterpolator(new LinearInterpolator());
    animator.start();
    animator2.start();
}
```

上面的代码简单点来说，就是在 ACTION_UP 时，去开始两个属性动画，一个属性动画是将 headerView 的高度恢复成原来的值，另一个属性动画就是把 headerView 的 scale 重新恢复为1f。相信大家都可以看懂的。

ZoomListView 整体的代码就这些了，很简短。下面附上下载的链接：

[ZoomListView.rar](/uploads/20160419/ZoomListView.rar)

good luck ! ~~
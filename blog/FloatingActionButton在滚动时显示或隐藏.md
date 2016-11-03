title: FloatingActionButton在滚动时显示或隐藏
date: 2016-04-10 19:51:22
categories: Android Tips
tags: [Android,Material Design]
---
在Material Design中，FloatingActionButton(即FAB)是一个很重要的元素。而通常在列表向下滚动的时候，FAB应该会隐藏；而在向上滚动时，FAB应该会显示出来。本篇就记录其中一种实现FAB显示或隐藏的方案，主要应用了属性动画。

其实关于FAB的显示和隐藏，Google官方就提供了其中一种方案：`fab.hidden()`和`fab.show()`。但是自带的是FAB缩放的效果。并不是上下移动的效果。

那么我们就来看看如何实现FAB上下移动的效果吧！

首先在你想要滑动的View(比如说RecyclerView等)的布局上加上：

	app:layout_behavior="@string/appbar_scrolling_view_behavior"

然后再附上FAB的xml：
``` xml
 <android.support.design.widget.FloatingActionButton
    android:id="@+id/fab"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_gravity="bottom|end"
    android:layout_margin="@dimen/fab_margin"
    app:layout_behavior="com.yuqirong.rxnews.ui.view.ScrollAwareFABBehavior"
    android:src="@android:drawable/ic_dialog_email" />
```

注意其中的layout_behavior，是我们自己实现的一个类：

``` java
public class ScrollAwareFABBehavior extends FloatingActionButton.Behavior {

    private static final Interpolator INTERPOLATOR = new FastOutSlowInInterpolator();
    private boolean mIsAnimatingOut = false;

    public ScrollAwareFABBehavior(Context context, AttributeSet attrs) {
        super();
    }

    @Override
    public boolean onStartNestedScroll(final CoordinatorLayout coordinatorLayout, final FloatingActionButton child,
                                       final View directTargetChild, final View target, final int nestedScrollAxes) {
        // Ensure we react to vertical scrolling
        return nestedScrollAxes == ViewCompat.SCROLL_AXIS_VERTICAL
                || super.onStartNestedScroll(coordinatorLayout, child, directTargetChild, target, nestedScrollAxes);
    }

    @Override
    public void onNestedScroll(final CoordinatorLayout coordinatorLayout, final FloatingActionButton child,
                               final View target, final int dxConsumed, final int dyConsumed,
                               final int dxUnconsumed, final int dyUnconsumed) {
        super.onNestedScroll(coordinatorLayout, child, target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed);
        if (dyConsumed > 0 && !this.mIsAnimatingOut && child.getVisibility() == View.VISIBLE) {
            // User scrolled down and the FAB is currently visible -> hide the FAB
            animateOut(child);
        } else if (dyConsumed < 0 && child.getVisibility() != View.VISIBLE) {
            // User scrolled up and the FAB is currently not visible -> show the FAB
            animateIn(child);
        }
    }

    // Same animation that FloatingActionButton.Behavior uses to hide the FAB when the AppBarLayout exits
    private void animateOut(final FloatingActionButton button) {
        if (Build.VERSION.SDK_INT >= 14) {
            ViewCompat.animate(button).translationY(button.getHeight() + getMarginBottom(button)).setInterpolator(INTERPOLATOR).withLayer()
                    .setListener(new ViewPropertyAnimatorListener() {
                        public void onAnimationStart(View view) {
                            ScrollAwareFABBehavior.this.mIsAnimatingOut = true;
                        }

                        public void onAnimationCancel(View view) {
                            ScrollAwareFABBehavior.this.mIsAnimatingOut = false;
                        }

                        public void onAnimationEnd(View view) {
                            ScrollAwareFABBehavior.this.mIsAnimatingOut = false;
                            view.setVisibility(View.GONE);
                        }
                    }).start();
        } else {
            button.hide();
        }
    }

    // Same animation that FloatingActionButton.Behavior uses to show the FAB when the AppBarLayout enters
    private void animateIn(FloatingActionButton button) {
        button.setVisibility(View.VISIBLE);
        if (Build.VERSION.SDK_INT >= 14) {
            ViewCompat.animate(button).translationY(0)
                    .setInterpolator(INTERPOLATOR).withLayer().setListener(null)
                    .start();
        } else {
            button.show();
        }
    }

    private int getMarginBottom(View v) {
        int marginBottom = 0;
        final ViewGroup.LayoutParams layoutParams = v.getLayoutParams();
        if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
            marginBottom = ((ViewGroup.MarginLayoutParams) layoutParams).bottomMargin;
        }
        return marginBottom;
    }

}
```

我们主要看`onNestedScroll()`这个方法，在方法里主要判断了一下是向上滑还是向下滑。再分别去调用`animateOut()`和`animateIn()`。那我们就来看看`animateOut()`。(`animateIn()`和`animateOut()`的原理一样的，我们只看`animateOut()`吧)

在`animateOut()`根据SDK的版本判断，若大于或等于14使用属性动画；不然就是使用了自带的`hide()`方法。代码还是比较简单的，相信大家都能看得懂。当然如下想在SDK 14以下使用上下移动的效果，那就要用NineOldAndroids这个库了。

效果就是如下所示了：

![这里写图片描述](/uploads/20160413/20160413202356.gif)

好了，今天就到这了。bye！
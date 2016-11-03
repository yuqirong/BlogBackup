title: 深入解析Scroller滚动原理
date: 2016-04-05 20:21:10
categories: Android Blog
tags: [Android,源码解析]
---
最近在看《Android开发艺术探索》这本书，不得不赞一句主席写得真好，受益匪浅。在书中的相关章节有介绍用Scroller来实现平滑滚动的效果。而我们今天就来探究一下为什么Scroller能够实现平滑滚动。

首先我们先来看一下Scroller的用法，基本可概括为“三部曲”：

1. 创建一个Scroller对象，一般在View的构造器中创建：
``` java
public ScrollViewGroup(Context context) {
    this(context, null);
}

public ScrollViewGroup(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
}

public ScrollViewGroup(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    mScroller = new Scroller(context);
}
```

2. 重写View的computeScroll()方法，下面的代码基本是不会变化的：
``` java
@Override
public void computeScroll() {
    super.computeScroll();
    if (mScroller.computeScrollOffset()) {
        scrollTo(mScroller.getCurrX(), mScroller.getCurrY());
        postInvalidate();
    }
}
```

3. 调用startScroll()方法，startX和startY为开始滚动的坐标点，dx和dy为对应的偏移量：
``` java
mScroller.startScroll (int startX, int startY, int dx, int dy);
invalidate();
```

上面的三步就是Scroller的基本用法了。那接下来的任务就是解析Scroller的滚动原理了。

而在这之前，我们还有一件事要办，那就是搞清楚scrollTo()和scrollBy()的原理。scrollTo()和scrollBy()的区别我这里就不重复叙述了，不懂的可以自行google或百度。下面贴出scrollTo()的源码：
``` java
public void scrollTo(int x, int y) {
    if (mScrollX != x || mScrollY != y) {
        int oldX = mScrollX;
        int oldY = mScrollY;
        mScrollX = x;
        mScrollY = y;
        invalidateParentCaches();
        onScrollChanged(mScrollX, mScrollY, oldX, oldY);
        if (!awakenScrollBars()) {
            postInvalidateOnAnimation();
        }
    }
}
```

设置好mScrollX和mScrollY之后，调用了`onScrollChanged(mScrollX, mScrollY, oldX, oldY);`，View就会被重新绘制。这样就达到了滑动的效果。

下面我们再来看看scrollBy()：
``` java
public void scrollBy(int x, int y) {
    scrollTo(mScrollX + x, mScrollY + y);
}
```

这样简短的代码相信大家都懂了，原来scrollBy()内部是调用了scrollTo()的。但是scrollTo()/scrollBy()的滚动都是瞬间完成的，怎么样才能实现平滑滚动呢。

不知道大家有没有这样一种想法：如果我们把要滚动的偏移量分成若干份小的偏移量，当然这份量要大。然后用scrollTo()/scrollBy()每次都滚动小份的偏移量。在一定的时间内，不就成了平滑滚动了吗？没错，Scroller正是借助这一原理来实现平滑滚动的。下面我们就来看看源码吧！

根据“三部曲”中第一部，先来看看Scroller的构造器：
``` java
public Scroller(Context context, Interpolator interpolator, boolean flywheel) {
    mFinished = true;
    if (interpolator == null) {
        mInterpolator = new ViscousFluidInterpolator();
    } else {
        mInterpolator = interpolator;
    }
    mPpi = context.getResources().getDisplayMetrics().density * 160.0f;
    mDeceleration = computeDeceleration(ViewConfiguration.getScrollFriction());
    mFlywheel = flywheel;

    mPhysicalCoeff = computeDeceleration(0.84f); // look and feel tuning
}
```

在构造器中做的主要就是指定了插补器，如果没有指定插补器，那么就用默认的ViscousFluidInterpolator。

我们再来看看Scroller的startScroll()：
``` java
public void startScroll(int startX, int startY, int dx, int dy, int duration) {
    mMode = SCROLL_MODE;
    mFinished = false;
    mDuration = duration;
    mStartTime = AnimationUtils.currentAnimationTimeMillis();
    mStartX = startX;
    mStartY = startY;
    mFinalX = startX + dx;
    mFinalY = startY + dy;
    mDeltaX = dx;
    mDeltaY = dy;
    mDurationReciprocal = 1.0f / (float) mDuration;
}
```

我们发现，在startScroll()里面并没有开始滚动，而是设置了一堆变量的初始值，那么到底是什么让View开始滚动的？我们应该把目标集中在startScroll()的下一句`invalidate();`身上。我们可以这样理解：首先在startScroll()设置好了一堆初始值，之后调用了`invalidate();`让View重新绘制，这里又有一个很重要的点，在draw()中会调用`computeScroll()`这个方法！

源码太长了，在这里就不贴出来了。想看的童鞋在View类里面搜`boolean draw(Canvas canvas, ViewGroup parent, long drawingTime)`这个方法就能看到了。通过ViewGroup.drawChild()方法就会调用子View的draw()方法。而在View类里面的`computeScroll()`是一个空的方法，需要我们去实现：

``` java
/**
 * Called by a parent to request that a child update its values for mScrollX
 * and mScrollY if necessary. This will typically be done if the child is
 * animating a scroll using a {@link android.widget.Scroller Scroller}
 * object.
 */
public void computeScroll() {
}
```

而在上面“三部曲”的第二部中，我们就已经实现了`computeScroll()`。首先判断了`computeScrollOffset()`，我们来看看相关源码：
``` java
/**
 * Call this when you want to know the new location.  If it returns true,
 * the animation is not yet finished.
 */ 
public boolean computeScrollOffset() {
    if (mFinished) {
        return false;
    }

    int timePassed = (int)(AnimationUtils.currentAnimationTimeMillis() - mStartTime);

    if (timePassed < mDuration) {
        switch (mMode) {
        case SCROLL_MODE:
            final float x = mInterpolator.getInterpolation(timePassed * mDurationReciprocal);
            mCurrX = mStartX + Math.round(x * mDeltaX);
            mCurrY = mStartY + Math.round(x * mDeltaY);
            break;
        case FLING_MODE:
            final float t = (float) timePassed / mDuration;
            final int index = (int) (NB_SAMPLES * t);
            float distanceCoef = 1.f;
            float velocityCoef = 0.f;
            if (index < NB_SAMPLES) {
                final float t_inf = (float) index / NB_SAMPLES;
                final float t_sup = (float) (index + 1) / NB_SAMPLES;
                final float d_inf = SPLINE_POSITION[index];
                final float d_sup = SPLINE_POSITION[index + 1];
                velocityCoef = (d_sup - d_inf) / (t_sup - t_inf);
                distanceCoef = d_inf + (t - t_inf) * velocityCoef;
            }

            mCurrVelocity = velocityCoef * mDistance / mDuration * 1000.0f;
            
            mCurrX = mStartX + Math.round(distanceCoef * (mFinalX - mStartX));
            // Pin to mMinX <= mCurrX <= mMaxX
            mCurrX = Math.min(mCurrX, mMaxX);
            mCurrX = Math.max(mCurrX, mMinX);
            
            mCurrY = mStartY + Math.round(distanceCoef * (mFinalY - mStartY));
            // Pin to mMinY <= mCurrY <= mMaxY
            mCurrY = Math.min(mCurrY, mMaxY);
            mCurrY = Math.max(mCurrY, mMinY);

            if (mCurrX == mFinalX && mCurrY == mFinalY) {
                mFinished = true;
            }

            break;
        }
    }
    else {
        mCurrX = mFinalX;
        mCurrY = mFinalY;
        mFinished = true;
    }
    return true;
}
```

这个方法的返回值有讲究，若返回true则说明Scroller的滑动没有结束；若返回false说明Scroller的滑动结束了。再来看看内部的代码：先是计算出了已经滑动的时间，若已经滑动的时间小于总滑动的时间，则说明滑动没有结束；不然就说明滑动结束了，设置标记`mFinished = true;`。而在滑动未结束里面又分为了两个mode，不过这两个mode都干了差不多的事，大致就是根据刚才的时间`timePassed`和插补器来计算出该时间点滚动的距离`mCurrX`和`mCurrY`。也就是上面“三部曲”中第二部的mScroller.getCurrX(), mScroller.getCurrY()的值。

然后在第二部曲中调用scrollTo()方法滚动到指定点(即上面的`mCurrX`, `mCurrY`)。之后又调用了`postInvalidate();`，让View重绘并重新调用`computeScroll()`以此循环下去，一直到View滚动到指定位置为止，至此Scroller滚动结束。

其实Scroller的原理还是比较通俗易懂的。我们再来理清一下思路，以一张图的形式来终结今天的Scroller解析：

![这里写图片描述](/uploads/20160405/20160405235023.png)

好了，如果有什么问题可以在下面留言。

Goodbye!
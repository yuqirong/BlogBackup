title: View的工作原理
date: 2017-09-18 22:35:00
categories: Android Blog
tags: [Android,源码解析]
---
注：本文分析的源码基于 Android API 25

View绘制的起点
=============
WindowManagerGlobal
--------------------
### addView(View view, ViewGroup.LayoutParams params, Display display, Window parentWindow)
在 `WindowManagerGlobal` 的 `addView(View view, ViewGroup.LayoutParams params, Display display, Window parentWindow)` 方法中，创建了 `ViewRootImpl` 对象，将 `ViewRootImpl` 和 `DecorView` 相关联：

``` java
	root = new ViewRootImpl(view.getContext(), display);
	...
	// view 是 PhoneWindow 的 DecorView
	root.setView(view, wparams, panelParentView);
```

创建好了 `root` 之后，调用了 `ViewRootImpl` 的 `setView(View view, WindowManager.LayoutParams attrs, View panelParentView)` 方法。

将 DecorView 和 ViewRootImpl 相关联。

ViewRootImpl
-------------
### setView(View view, WindowManager.LayoutParams attrs, View panelParentView)

``` java
	public void setView(View view, WindowManager.LayoutParams attrs, View panelParentView) {
        synchronized (this) {
            if (mView == null) {
				// 将 decorView 设置给全局的 mView
                mView = view;
				...
				// 标记已经添加了 decorView
				mAdded = true;
				...
				// 第一次发起布局，在添加到 WindowManager 之前
				// 确保在接收其他系统事件之前完成重新布局
				requestLayout();
				...
				// 利用 mWindowSession 以跨进程的方式向 WMS 发起一个调用，从而将DecorView 最终添加到 Window 上
			  	try {
			  	    mOrigWindowType = mWindowAttributes.type;
			  	    mAttachInfo.mRecomputeGlobalAttributes = true;
			  	    collectViewAttributes();
			  	    res = mWindowSession.addToDisplay(mWindow, mSeq, mWindowAttributes, getHostVisibility(), mDisplay.getDisplayId(), mAttachInfo.mContentInsets, mAttachInfo.mStableInsets, mAttachInfo.mOutsets, mInputChannel);
                } 
				...
			}
		}
	}
```

在 `setView(View view, WindowManager.LayoutParams attrs, View panelParentView)` 方法中，主要做的事情有：

1. 保存 DecorView
2. 第一次调用 `requestLayout()` ，发起整个 View 的绘制流程
3. 将 View 添加到 Window 上去

而在这，我们重点关注 `requestLayout()` 方法，因为恰恰这句代码引发了整个 View 的绘制。

### requestLayout()

``` java
    @Override
    public void requestLayout() {
        if (!mHandlingLayoutInLayoutRequest) {
			// 检查当前线程
            checkThread();
            mLayoutRequested = true;
			// 调用绘制
            scheduleTraversals();
        }
    }
```

在 `requestLayout()` 中先检查了线程，若 OK 后调用 `scheduleTraversals()` 。

### scheduleTraversals()

``` java
    void scheduleTraversals() {
        if (!mTraversalScheduled) {
            mTraversalScheduled = true;
            mTraversalBarrier = mHandler.getLooper().getQueue().postSyncBarrier();
			// 发送消息，调用 mTraversalRunnable
            mChoreographer.postCallback(
                    Choreographer.CALLBACK_TRAVERSAL, mTraversalRunnable, null);
            if (!mUnbufferedInputDispatch) {
                scheduleConsumeBatchedInput();
            }
            notifyRendererOfFramePending();
            pokeDrawLockIfNeeded();
        }
    }

	final TraversalRunnable mTraversalRunnable = new TraversalRunnable();

    final class TraversalRunnable implements Runnable {
        @Override
        public void run() {
			// 内部调用了 performTraversals()
            doTraversal();
        }
    }
```

在 `scheduleTraversals()` 中，其实是这样的：

scheduleTraversals() -> 调用 mTraversalRunnable -> doTraversal() -> performTraversals()
    
所以最后还是要看 `performTraversals()` 。

### performTraversals()

``` java
	private void performTraversals() {
	
		// 计算 Activity 中 window 的宽高等等
		...

		if (!mStopped || mReportNextDraw) {
                boolean focusChangedDueToTouchMode = ensureTouchModeLocally(
                        (relayoutResult&WindowManagerGlobal.RELAYOUT_RES_IN_TOUCH_MODE) != 0);
                if (focusChangedDueToTouchMode || mWidth != host.getMeasuredWidth()
                        || mHeight != host.getMeasuredHeight() || contentInsetsChanged ||
                        updatedConfiguration) {
                    // 得到 view 宽高的规格
                    // mWidth 和 mHeight 即用来描述 Activity 窗口宽度和高度
                    // lp.width 和 lp.height 就是 DecorView 的宽高
                    int childWidthMeasureSpec = getRootMeasureSpec(mWidth, lp.width);
                    int childHeightMeasureSpec = getRootMeasureSpec(mHeight, lp.height);

                    if (DEBUG_LAYOUT) Log.v(mTag, "Ooops, something changed!  mWidth="
                            + mWidth + " measuredWidth=" + host.getMeasuredWidth()
                            + " mHeight=" + mHeight
                            + " measuredHeight=" + host.getMeasuredHeight()
                            + " coveredInsetsChanged=" + contentInsetsChanged);

                     // Ask host how big it wants to be
                     // 开始执行测量工作，测量是从这里发起的
                    performMeasure(childWidthMeasureSpec, childHeightMeasureSpec);

                    // Implementation of weights from WindowManager.LayoutParams
                    // We just grow the dimensions as needed and re-measure if
                    // needs be
                    int width = host.getMeasuredWidth();
                    int height = host.getMeasuredHeight();
                    boolean measureAgain = false;

                    // 检查是否需要重新测量
                    if (lp.horizontalWeight > 0.0f) {
                        width += (int) ((mWidth - width) * lp.horizontalWeight);
                        childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(width,
                                MeasureSpec.EXACTLY);
                        measureAgain = true;
                    }
                    if (lp.verticalWeight > 0.0f) {
                        height += (int) ((mHeight - height) * lp.verticalWeight);
                        childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(height,
                                MeasureSpec.EXACTLY);
                        measureAgain = true;
                    }

                    // 需要再次测量的话，就再执行一遍 performMeasure
                    if (measureAgain) {
                        if (DEBUG_LAYOUT) Log.v(mTag,
                                "And hey let's measure once more: width=" + width
                                + " height=" + height);
                        performMeasure(childWidthMeasureSpec, childHeightMeasureSpec);
                    }

                    layoutRequested = true;
                }
            }

		...

        final boolean didLayout = layoutRequested && (!mStopped || mReportNextDraw);
        boolean triggerGlobalLayoutListener = didLayout
                || mAttachInfo.mRecomputeGlobalAttributes;
        if (didLayout) {
            // 执行布局工作，布局是从这里发起的
            performLayout(lp, mWidth, mHeight);

		...

		if (!cancelDraw && !newSurface) {
            if (mPendingTransitions != null && mPendingTransitions.size() > 0) {
                for (int i = 0; i < mPendingTransitions.size(); ++i) {
                    mPendingTransitions.get(i).startChangingAnimations();
                }
                mPendingTransitions.clear();
            }
            // 执行绘制工作，绘制是从这里发起的
            performDraw();
        } 

		...

	}
```

`performTraversals()` 方法的代码很长很长，但是我们关注点就可以放在三大流程上。其他的代码因为自己能力欠缺，并不能一一说出这些代码的作用。所以我们接下来就把重点放在：

1. getRootMeasureSpec
2. performMeasure
3. performLayout
4. performDraw



三大流程
=======
ViewRootImpl
-------------
### measureHierarchy(final View host, final WindowManager.LayoutParams lp, final Resources res, final int desiredWindowWidth, final int desiredWindowHeight)

其实在 `performTraversals()` 中有一句代码

``` java
// Ask host how big it wants to be
windowSizeMayChange |= measureHierarchy(host, lp, res,
        desiredWindowWidth, desiredWindowHeight);
```

在 `measureHierarchy` 方法中已经调用了 `performMeasure` 来进行测量。不过作用不同，只是为了确定 window 的大小而做的测量辅助。所以可以说，并不算上在三大流程中。

在 `measureHierarchy` 中，确定了 DecorView 的 `MeasureSpec` 。其中 `childWidthMeasureSpec` 和 `childHeightMeasureSpec` 即为 DecorView 对应的 `MeasureSpec` 。

``` java
// desiredWindowWidth 和 desiredWindowHeight 是屏幕的宽高
childWidthMeasureSpec = getRootMeasureSpec(desiredWindowWidth, lp.width);
childHeightMeasureSpec = getRootMeasureSpec(desiredWindowHeight, lp.height);
performMeasure(childWidthMeasureSpec, childHeightMeasureSpec);
```

### getRootMeasureSpec(int windowSize, int rootDimension)

那么就来看看 `getRootMeasureSpec` 咯。

``` java
    private static int getRootMeasureSpec(int windowSize, int rootDimension) {
        int measureSpec;
        switch (rootDimension) {

        case ViewGroup.LayoutParams.MATCH_PARENT:
            // Window can't resize. Force root view to be windowSize.
            measureSpec = MeasureSpec.makeMeasureSpec(windowSize, MeasureSpec.EXACTLY);
            break;
        case ViewGroup.LayoutParams.WRAP_CONTENT:
            // Window can resize. Set max size for root view.
            measureSpec = MeasureSpec.makeMeasureSpec(windowSize, MeasureSpec.AT_MOST);
            break;
        default:
            // Window wants to be an exact size. Force root view to be that size.
            measureSpec = MeasureSpec.makeMeasureSpec(rootDimension, MeasureSpec.EXACTLY);
            break;
        }
        return measureSpec;
    }
```

代码很简洁，也很易懂。

1. 如果是 MATCH_PARENT ，那么对应的就是窗口大小；
2. 如果是 WRAP_CONTENT ，那么不能超过窗口大小；
3. 固定大小，那么就是大小就是传入的 lp.width/lp.height 了。

ViewGroup
----------
### getChildMeasureSpec(int spec, int padding, int childDimension)

顺便，我们把平时自定义 ViewGroup 计算子 View 测量规格的 `getChildMeasureSpec` 方法也一起来看看：

``` java
    public static int getChildMeasureSpec(int spec, int padding, int childDimension) {
        // 父容器的 mode
        int specMode = MeasureSpec.getMode(spec);
        // 父容器的 size
        int specSize = MeasureSpec.getSize(spec);
        // 子 view 可以使用空间，即父容器的 size - padding
        int size = Math.max(0, specSize - padding);

        int resultSize = 0;
        int resultMode = 0;

        switch (specMode) {
        // Parent has imposed an exact size on us
        case MeasureSpec.EXACTLY:
            if (childDimension >= 0) {
                resultSize = childDimension;
                resultMode = MeasureSpec.EXACTLY;
            } else if (childDimension == LayoutParams.MATCH_PARENT) {
                // Child wants to be our size. So be it.
                resultSize = size;
                resultMode = MeasureSpec.EXACTLY;
            } else if (childDimension == LayoutParams.WRAP_CONTENT) {
                // Child wants to determine its own size. It can't be
                // bigger than us.
                resultSize = size;
                resultMode = MeasureSpec.AT_MOST;
            }
            break;

        // Parent has imposed a maximum size on us
        case MeasureSpec.AT_MOST:
            if (childDimension >= 0) {
                // Child wants a specific size... so be it
                resultSize = childDimension;
                resultMode = MeasureSpec.EXACTLY;
            } else if (childDimension == LayoutParams.MATCH_PARENT) {
                // Child wants to be our size, but our size is not fixed.
                // Constrain child to not be bigger than us.
                resultSize = size;
                resultMode = MeasureSpec.AT_MOST;
            } else if (childDimension == LayoutParams.WRAP_CONTENT) {
                // Child wants to determine its own size. It can't be
                // bigger than us.
                resultSize = size;
                resultMode = MeasureSpec.AT_MOST;
            }
            break;

        // Parent asked to see how big we want to be
        case MeasureSpec.UNSPECIFIED:
            if (childDimension >= 0) {
                // Child wants a specific size... let him have it
                resultSize = childDimension;
                resultMode = MeasureSpec.EXACTLY;
            } else if (childDimension == LayoutParams.MATCH_PARENT) {
                // Child wants to be our size... find out how big it should
                // be
                resultSize = View.sUseZeroUnspecifiedMeasureSpec ? 0 : size;
                resultMode = MeasureSpec.UNSPECIFIED;
            } else if (childDimension == LayoutParams.WRAP_CONTENT) {
                // Child wants to determine its own size.... find out how
                // big it should be
                resultSize = View.sUseZeroUnspecifiedMeasureSpec ? 0 : size;
                resultMode = MeasureSpec.UNSPECIFIED;
            }
            break;
        }
        //noinspection ResourceType
        return MeasureSpec.makeMeasureSpec(resultSize, resultMode);
    }
```

上面的 switch/case 代码比较简单，而且容易理解。我们可以整理为一张表格（该表格来自于《Android开发艺术探索》）：

![measurespec](/uploads/20170821/20170821150637571.png)

在这里，我们小结一下。对于 DecorView 来说，其 `MeasureSpec` 是由窗口的大小和自身的 `LayoutParams` 来共同决定的；而对于普通的 View 来说，其 `MeasureSpec` 是由父容器的 `MeasureSpec` 和自身的 `LayoutParams` 共同决定的。

measure过程
===========
ViewRootImpl
--------------
### performMeasure(int childWidthMeasureSpec, int childHeightMeasureSpec)

分析 measure 过程，我们的起点就是在 `ViewRootImpl` 的 `performMeasure(int childWidthMeasureSpec, int childHeightMeasureSpec)` 方法中：

``` java
    private void performMeasure(int childWidthMeasureSpec, int childHeightMeasureSpec) {
        Trace.traceBegin(Trace.TRACE_TAG_VIEW, "measure");
        try {
            // 进行测量
            mView.measure(childWidthMeasureSpec, childHeightMeasureSpec);
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_VIEW);
        }
    }
```

在 `performMeasure` 中调用了 `measure` 方法。说到底，DecorView 只是一个所以我们又要进入 `View` 类中去看下。

View
-----
### measure(int widthMeasureSpec, int heightMeasureSpec)

``` java
	public final void measure(int widthMeasureSpec, int heightMeasureSpec) {
        ...

        if (forceLayout || needsLayout) {
            ...
            if (cacheIndex < 0 || sIgnoreMeasureCache) {
                // measure ourselves, this should set the measured dimension flag back
                // 调用 onMeasure
                onMeasure(widthMeasureSpec, heightMeasureSpec);
                mPrivateFlags3 &= ~PFLAG3_MEASURE_NEEDED_BEFORE_LAYOUT;
            } else {
                long value = mMeasureCache.valueAt(cacheIndex);
                // Casting a long to int drops the high 32 bits, no mask needed
                setMeasuredDimensionRaw((int) (value >> 32), (int) value);
                mPrivateFlags3 |= PFLAG3_MEASURE_NEEDED_BEFORE_LAYOUT;
            }

            ...
    }
```

`View` 的 `measure` 方法内部是调用了 `onMeasure` 。所以我们还要接着跟进到 `onMeasure` 中才行。另外， `measure` 方法是用 final 修饰的，所以子类是无法进行重写的。

FrameLayout
-----------
### onMeasure(int widthMeasureSpec, int heightMeasureSpec)

这里小提一下，我们都知道 DecorView 其实是一个 `FrameLayout` ，所以 `onMeasure` 应该在 `FrameLayout` 中去看：

``` java
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int count = getChildCount();
        // 判断当前 framelayout 布局的宽高是否至少一个是 match_parent 或者精确值 ，如果是则置 measureMatchParent 为 false .
        final boolean measureMatchParentChildren =
                MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.EXACTLY ||
                MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY;
        mMatchParentChildren.clear();

        int maxHeight = 0;
        int maxWidth = 0;
        int childState = 0;

        // 遍历不为 GONE 的子 view
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (mMeasureAllChildren || child.getVisibility() != GONE) {
                // 对每一个子 View 进行测量
                measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                final LayoutParams lp = (LayoutParams) child.getLayoutParams();
                // 寻找子 View 中宽高的最大者，因为如果 FrameLayout 是 wrap_content 属性
                // 那么它的宽高取决于子 View 中的宽高最大者
                maxWidth = Math.max(maxWidth,
                        child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin);
                maxHeight = Math.max(maxHeight,
                        child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin);
                childState = combineMeasuredStates(childState, child.getMeasuredState());
                // 如果 FrameLayout 为 wrap_content 且 子 view 的宽或高为 match_parent ，那么就添加到 mMatchParentChildren 中
                if (measureMatchParentChildren) {
                    if (lp.width == LayoutParams.MATCH_PARENT ||
                            lp.height == LayoutParams.MATCH_PARENT) {
                        mMatchParentChildren.add(child);
                    }
                }
            }
        }

        // Account for padding too
        maxWidth += getPaddingLeftWithForeground() + getPaddingRightWithForeground();
        maxHeight += getPaddingTopWithForeground() + getPaddingBottomWithForeground();

        // Check against our minimum height and width
        maxHeight = Math.max(maxHeight, getSuggestedMinimumHeight());
        maxWidth = Math.max(maxWidth, getSuggestedMinimumWidth());

        // Check against our foreground's minimum height and width
        final Drawable drawable = getForeground();
        if (drawable != null) {
            maxHeight = Math.max(maxHeight, drawable.getMinimumHeight());
            maxWidth = Math.max(maxWidth, drawable.getMinimumWidth());
        }
        //设置测量结果
        setMeasuredDimension(resolveSizeAndState(maxWidth, widthMeasureSpec, childState),
                resolveSizeAndState(maxHeight, heightMeasureSpec,
                        childState << MEASURED_HEIGHT_STATE_SHIFT));

        // 子View中设置为match_parent的个数
        count = mMatchParentChildren.size();
        // 若 FrameLayout 为 wrap_content 且 count > 1
        if (count > 1) {
            for (int i = 0; i < count; i++) {
                final View child = mMatchParentChildren.get(i);
                final MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();

                // 如果子 View 的宽度是 match_parent 属性，那么对 childWidthMeasureSpec 修改：
                // 把 widthMeasureSpec 的宽度修改为:framelayout总宽度 - padding - margin，模式设置为 EXACTLY
                final int childWidthMeasureSpec;
                if (lp.width == LayoutParams.MATCH_PARENT) {
                    final int width = Math.max(0, getMeasuredWidth()
                            - getPaddingLeftWithForeground() - getPaddingRightWithForeground()
                            - lp.leftMargin - lp.rightMargin);
                    childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(
                            width, MeasureSpec.EXACTLY);
                } else {
                    // 否则就按照正常的来就行了
                    childWidthMeasureSpec = getChildMeasureSpec(widthMeasureSpec,
                            getPaddingLeftWithForeground() + getPaddingRightWithForeground() +
                            lp.leftMargin + lp.rightMargin,
                            lp.width);
                }

                // 高度同理
                final int childHeightMeasureSpec;
                if (lp.height == LayoutParams.MATCH_PARENT) {
                    final int height = Math.max(0, getMeasuredHeight()
                            - getPaddingTopWithForeground() - getPaddingBottomWithForeground()
                            - lp.topMargin - lp.bottomMargin);
                    childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(
                            height, MeasureSpec.EXACTLY);
                } else {
                    childHeightMeasureSpec = getChildMeasureSpec(heightMeasureSpec,
                            getPaddingTopWithForeground() + getPaddingBottomWithForeground() +
                            lp.topMargin + lp.bottomMargin,
                            lp.height);
                }
                //对于这部分的子 View 需要重新进行 measure 过程
                child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
            }
        }
    }
```

如果上面 `FrameLayout` 的 `onMeasure` 流程没看懂的话也没关系。其实总的来说重要的就只有遍历 `child.measure(childWidthMeasureSpec, childHeightMeasureSpec)` 这个方法，这是将父容器的 measure 过程传递到子 View 中。

ViewGroup
-----------
### measureChildWithMargins(View child, int parentWidthMeasureSpec, int widthUsed, int parentHeightMeasureSpec, int heightUsed)

可能有些人也有疑问，在上面 `measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0)` 后也没看到有 `child.measure` 的方法啊，这是因为在 `measureChildWithMargins` 中内部调用了 `child.measure` ：


``` java
    protected void measureChildWithMargins(View child,
            int parentWidthMeasureSpec, int widthUsed,
            int parentHeightMeasureSpec, int heightUsed) {
        final MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
        // getChildMeasureSpec 我们上面分析过了
        final int childWidthMeasureSpec = getChildMeasureSpec(parentWidthMeasureSpec,
                mPaddingLeft + mPaddingRight + lp.leftMargin + lp.rightMargin
                        + widthUsed, lp.width);
        final int childHeightMeasureSpec = getChildMeasureSpec(parentHeightMeasureSpec,
                mPaddingTop + mPaddingBottom + lp.topMargin + lp.bottomMargin
                        + heightUsed, lp.height);
        // measure 传递给子 View
        child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
    }
```

这下明白了吧？父容器就是遍历调用了 `child.measure` 这个方法将 measure 过程传递给每一个子 View 的。虽然不同的父容器 `onMeasure` 方法都不一样，但是相同的是，他们都会遍历调用 `child.measure` 。

View
----

### onMeasure(int widthMeasureSpec, int heightMeasureSpec)

上面我们也讲过，`measure` 方法内部其实是调用了 `onMeasure` ，所以子 View 被父容器调用了 `measure` 后，也会调用属于自己的 `onMeasure` 方法。那么我们就直接看向 `View` 的 `onMeasure` 方法：

``` java
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec),
                getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec));
    }
```

`onMeasure` 方法只有一句代码，所以重点就是 `getDefaultSize(int size, int measureSpec)` 咯。

`getSuggestedMinimumWidth()` 内部逻辑：

1. 若没有设置背景，就是 `android:minWidth` 的值；
2. 若有设置背景，就是 max(android:minWidth, 背景 Drawable 的原始宽度)

`getSuggestedMinimumHeight()` 也是同理。

### getDefaultSize(int size, int measureSpec)

``` java
    public static int getDefaultSize(int size, int measureSpec) {
        int result = size;
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);

        switch (specMode) {
        case MeasureSpec.UNSPECIFIED:
            result = size;
            break;
        // 直接返回 specSize
        case MeasureSpec.AT_MOST:
        case MeasureSpec.EXACTLY:
            result = specSize;
            break;
        }
        return result;
    }
```

从上面我们可以看到：

* 若是 UNSPECIFIED ，则直接返回的就是 `getSuggestedMinimumWidth/getSuggestedMinimumHeight` 的值；
* 若是 AT\_MOST/EXACTLY ，直接用的就是 specSize 。

而根据我们之前总结出来的表可知，只要 view 不指定固定大小，那么无论是 AT\_MOST 还是 EXACTLY ，都是按照 parentSize 来的。

这也是为什么我们在自定义 View 时，如果不重写 `onMeasure(int widthMeasureSpec, int heightMeasureSpec)` ，wrap\_content 和 match\_parent 效果一样的原因。

小结
----
我们把 measure 过程的代码流程理一下：

ViewRootImpl.performTraversals -> ViewRootImpl.performMeasure -> DecorView.measure -> DecorView.onMeasure -> DecorView.measureChildWithMargins -> ViewGroup.measure -> ViewGroup.onMeasure -> ViewGroup.measureChildWithMargins -> ... -> View.measure -> View.onMeasure

注：DecorView 其实就是 FrameLayout

layout过程
==========
ViewRootImpl
------------
### performLayout(WindowManager.LayoutParams lp, int desiredWindowWidth, int desiredWindowHeight)

在上面分析过，layout 过程是从 `ViewRootImpl` 中的 `performLayout(WindowManager.LayoutParams lp, int desiredWindowWidth, int desiredWindowHeight)` 开始的。

``` java
    private void performLayout(WindowManager.LayoutParams lp, int desiredWindowWidth,
            int desiredWindowHeight) {
        mLayoutRequested = false;
        mScrollMayChange = true;
        mInLayout = true;

        final View host = mView;
        if (DEBUG_ORIENTATION || DEBUG_LAYOUT) {
            Log.v(mTag, "Laying out " + host + " to (" +
                    host.getMeasuredWidth() + ", " + host.getMeasuredHeight() + ")");
        }

        Trace.traceBegin(Trace.TRACE_TAG_VIEW, "layout");
        try {

            // host 就是 DecorView，调用了 layout 方法开始布局
            host.layout(0, 0, host.getMeasuredWidth(), host.getMeasuredHeight());

            mInLayout = false;
            // mLayoutRequesters 为需要重新请求布局的 view 集合数
            int numViewsRequestingLayout = mLayoutRequesters.size();

            // 下面的代码主要用于若有请求重新布局的 view ，那么再进行重新布局
            if (numViewsRequestingLayout > 0) {
                // requestLayout() was called during layout.
                // If no layout-request flags are set on the requesting views, there is no problem.
                // If some requests are still pending, then we need to clear those flags and do
                // a full request/measure/layout pass to handle this situation.
                ArrayList<View> validLayoutRequesters = getValidLayoutRequesters(mLayoutRequesters,
                        false);
                if (validLayoutRequesters != null) {
                    // Set this flag to indicate that any further requests are happening during
                    // the second pass, which may result in posting those requests to the next
                    // frame instead
                    mHandlingLayoutInLayoutRequest = true;

                    // view 请求布局，进行重新测量和布局
                    int numValidRequests = validLayoutRequesters.size();
                    for (int i = 0; i < numValidRequests; ++i) {
                        final View view = validLayoutRequesters.get(i);
                        Log.w("View", "requestLayout() improperly called by " + view +
                                " during layout: running second layout pass");
                        view.requestLayout();
                    }

                    // 对整个View树进行重新测量
                    measureHierarchy(host, lp, mView.getContext().getResources(),
                            desiredWindowWidth, desiredWindowHeight);
                    mInLayout = true;

                    // 进行第二次布局
                    host.layout(0, 0, host.getMeasuredWidth(), host.getMeasuredHeight());

                    mHandlingLayoutInLayoutRequest = false;

                    // Check the valid requests again, this time without checking/clearing the
                    // layout flags, since requests happening during the second pass get noop'd
                    validLayoutRequesters = getValidLayoutRequesters(mLayoutRequesters, true);
                    if (validLayoutRequesters != null) {
                        final ArrayList<View> finalRequesters = validLayoutRequesters;
                        // Post second-pass requests to the next frame
                        getRunQueue().post(new Runnable() {
                            @Override
                            public void run() {
                                int numValidRequests = finalRequesters.size();
                                for (int i = 0; i < numValidRequests; ++i) {
                                    final View view = finalRequesters.get(i);
                                    Log.w("View", "requestLayout() improperly called by " + view +
                                            " during second layout pass: posting in next frame");
                                    view.requestLayout();
                                }
                            }
                        });
                    }
                }

            }
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_VIEW);
        }
        mInLayout = false;
    }
```

基本可知，`performLayout` 是通过调用 DecorView 的 `layout` 方法来向下传递布局的。所以我们应该继续追踪 `FrameLayout` 的 `layout` 方法，其实就是 `ViewGroup` 的 `layout` 方法。

ViewGroup
---------
### layout(int l, int t, int r, int b)

`FrameLayout` 的 `layout` 是父类 `ViewGroup` 实现的，添加了 final 修饰符，无法被重写：

``` java
@Override
public final void layout(int l, int t, int r, int b) {
    if (!mSuppressLayout && (mTransition == null || !mTransition.isChangingLayout())) {
        if (mTransition != null) {
            mTransition.layoutChange(this);
        }
        // 调用 view 的 layout 方法
        super.layout(l, t, r, b);
    } else {  
        // record the fact that we noop'd it; request layout when transition finishes
        mLayoutCalledWhileSuppressed = true;
    }
}
```

在 `ViewGroup` 的 `layout` 方法中又调用了父类的方法 `super.layout(l, t, r, b)` 。所以我们又要到 `View` 类中去看。

View
----
### layout(int l, int t, int r, int b)

``` java
    @SuppressWarnings({"unchecked"})
    public void layout(int l, int t, int r, int b) {
        if ((mPrivateFlags3 & PFLAG3_MEASURE_NEEDED_BEFORE_LAYOUT) != 0) {
            onMeasure(mOldWidthMeasureSpec, mOldHeightMeasureSpec);
            mPrivateFlags3 &= ~PFLAG3_MEASURE_NEEDED_BEFORE_LAYOUT;
        }

        // 当前布局的四个顶点
        int oldL = mLeft;
        int oldT = mTop;
        int oldB = mBottom;
        int oldR = mRight;

        // 计算四个顶点的值，判断布局位置是否改变
        boolean changed = isLayoutModeOptical(mParent) ?
                setOpticalFrame(l, t, r, b) : setFrame(l, t, r, b);

        // 如果视图的大小和位置发生变化，会调用onLayout()
        if (changed || (mPrivateFlags & PFLAG_LAYOUT_REQUIRED) == PFLAG_LAYOUT_REQUIRED) {

            // 空方法
            onLayout(changed, l, t, r, b);

            if (shouldDrawRoundScrollbar()) {
                if(mRoundScrollbarRenderer == null) {
                    mRoundScrollbarRenderer = new RoundScrollbarRenderer(this);
                }
            } else {
                mRoundScrollbarRenderer = null;
            }

            mPrivateFlags &= ~PFLAG_LAYOUT_REQUIRED;

            // 调用布局位置改变监听器
            ListenerInfo li = mListenerInfo;
            if (li != null && li.mOnLayoutChangeListeners != null) {
                ArrayList<OnLayoutChangeListener> listenersCopy =
                        (ArrayList<OnLayoutChangeListener>)li.mOnLayoutChangeListeners.clone();
                int numListeners = listenersCopy.size();
                for (int i = 0; i < numListeners; ++i) {
                    listenersCopy.get(i).onLayoutChange(this, l, t, r, b, oldL, oldT, oldR, oldB);
                }
            }
        }

        mPrivateFlags &= ~PFLAG_FORCE_LAYOUT;
        mPrivateFlags3 |= PFLAG3_IS_LAID_OUT;
    }
```

上面的代码中做了这几件事：

1. 设置当前布局中的四个顶点；
2. 调用 `setFrame` 来设置新的顶点位置；
3. 调用 `onLayout` 方法；
4. 回调布局位置改变监听器；

### setOpticalFrame(int left, int top, int right, int bottom)

我们先来看 `setOpticalFrame` 方法：

``` java
private boolean setOpticalFrame(int left, int top, int right, int bottom) {
    Insets parentInsets = mParent instanceof View ?
            ((View) mParent).getOpticalInsets() : Insets.NONE;
    Insets childInsets = getOpticalInsets();
    // 调用 setFrame 方法
    return setFrame(
            left   + parentInsets.left - childInsets.left,
            top    + parentInsets.top  - childInsets.top,
            right  + parentInsets.left + childInsets.right,
            bottom + parentInsets.top  + childInsets.bottom);
}
```

其实在 `setOpticalFrame` 的内部也是调用 `setFrame` 方法的。

### setFrame(int left, int top, int right, int bottom)

``` java
    protected boolean setFrame(int left, int top, int right, int bottom) {
        boolean changed = false;

        if (DBG) {
            Log.d("View", this + " View.setFrame(" + left + "," + top + ","
                    + right + "," + bottom + ")");
        }

        // 如果新值和旧值不相等，那就是布局位置改变了
        if (mLeft != left || mRight != right || mTop != top || mBottom != bottom) {
            changed = true;

            // Remember our drawn bit
            int drawn = mPrivateFlags & PFLAG_DRAWN;

            // 计算新的宽高和旧的宽高
            int oldWidth = mRight - mLeft;
            int oldHeight = mBottom - mTop;
            int newWidth = right - left;
            int newHeight = bottom - top;
            // 判断大小是否改变
            boolean sizeChanged = (newWidth != oldWidth) || (newHeight != oldHeight);

            // Invalidate our old position
            invalidate(sizeChanged);

            // 设置 view 的上下左右，赋予最新的值
            mLeft = left;
            mTop = top;
            mRight = right;
            mBottom = bottom;
            mRenderNode.setLeftTopRightBottom(mLeft, mTop, mRight, mBottom);

            mPrivateFlags |= PFLAG_HAS_BOUNDS;

            // 回调大小改变的方法
            if (sizeChanged) {
                sizeChange(newWidth, newHeight, oldWidth, oldHeight);
            }

            if ((mViewFlags & VISIBILITY_MASK) == VISIBLE || mGhostView != null) {
                // If we are visible, force the DRAWN bit to on so that
                // this invalidate will go through (at least to our parent).
                // This is because someone may have invalidated this view
                // before this call to setFrame came in, thereby clearing
                // the DRAWN bit.
                mPrivateFlags |= PFLAG_DRAWN;
                invalidate(sizeChanged);
                // parent display list may need to be recreated based on a change in the bounds
                // of any child
                invalidateParentCaches();
            }

            // Reset drawn bit to original value (invalidate turns it off)
            mPrivateFlags |= drawn;

            mBackgroundSizeChanged = true;
            if (mForegroundInfo != null) {
                mForegroundInfo.mBoundsChanged = true;
            }

            // Android无障碍辅助功能通知
            notifySubtreeAccessibilityStateChangedIfNeeded();
        }
        return changed;
    }
```

先回根据新旧的宽高进行比较，来确定是不是大小被改变了。如果是，会回调 `sizeChange(newWidth, newHeight, oldWidth, oldHeight)` 方法，这个方法是不是很眼熟呢？

之后还会把这消息通知给 `AccessibilityService` 无障碍服务。

最后返回布局是否改变的 boolean 值。

FrameLayout
-----------
### onLayout(boolean changed, int left, int top, int right, int bottom)

接着，根据布局改变值 `changed` 会调用 `onLayout` 方法。

`onLayout` 方法在 View/ViewGroup 都是空的，是需要子类来实现的。所以我们还是要看 `FrameLayout` 中的 `onLayout` ：

``` java
	@Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        layoutChildren(left, top, right, bottom, false /* no force left gravity */);
    }
```

在 `onLayout` 中调用了 `layoutChildren` 方法。

### layoutChildren(int left, int top, int right, int bottom, boolean forceLeftGravity)

``` java
	void layoutChildren(int left, int top, int right, int bottom, boolean forceLeftGravity) {
        final int count = getChildCount();

        final int parentLeft = getPaddingLeftWithForeground();
        final int parentRight = right - left - getPaddingRightWithForeground();

        final int parentTop = getPaddingTopWithForeground();
        final int parentBottom = bottom - top - getPaddingBottomWithForeground();

        // 遍历子 view
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                final LayoutParams lp = (LayoutParams) child.getLayoutParams();

                // 子 view 的宽高
                final int width = child.getMeasuredWidth();
                final int height = child.getMeasuredHeight();

                int childLeft;
                int childTop;

                // 得到子 view 的 gravity
                int gravity = lp.gravity;
                if (gravity == -1) {
                    gravity = DEFAULT_CHILD_GRAVITY;
                }

                final int layoutDirection = getLayoutDirection();
                final int absoluteGravity = Gravity.getAbsoluteGravity(gravity, layoutDirection);
                final int verticalGravity = gravity & Gravity.VERTICAL_GRAVITY_MASK;

                // 根据不同的 gravity 来计算 childLeft
                switch (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
                    case Gravity.CENTER_HORIZONTAL:
                        childLeft = parentLeft + (parentRight - parentLeft - width) / 2 +
                        lp.leftMargin - lp.rightMargin;
                        break;
                    case Gravity.RIGHT:
                        if (!forceLeftGravity) {
                            childLeft = parentRight - width - lp.rightMargin;
                            break;
                        }
                    case Gravity.LEFT:
                    default:
                        childLeft = parentLeft + lp.leftMargin;
                }


                // 根据不同的 gravity 来计算 childTop
                switch (verticalGravity) {
                    case Gravity.TOP:
                        childTop = parentTop + lp.topMargin;
                        break;
                    case Gravity.CENTER_VERTICAL:
                        childTop = parentTop + (parentBottom - parentTop - height) / 2 +
                        lp.topMargin - lp.bottomMargin;
                        break;
                    case Gravity.BOTTOM:
                        childTop = parentBottom - height - lp.bottomMargin;
                        break;
                    default:
                        childTop = parentTop + lp.topMargin;
                }
                // 调用子 view 的 layout 方法
                child.layout(childLeft, childTop, childLeft + width, childTop + height);
            }
        }
    }
```

简单来说，在 `layoutChildren` 中，遍历所有可见的子 View ，然后得到它们的宽高。

再根据不同的 gravity 来计算 childLeft 和 childTop ，最后调用 child.layout 来向子 View 传递下去。

小结
---
我们把 layout 过程的代码流程理一下：

ViewRootImpl.performTraversals -> ViewRootImpl.performLayout -> DecorView(ViewGroup).layout -> View.layout -> DecorView(FrameLayout).onLayout ->  DecorView(FrameLayout).layoutChildren -> ViewGroup.layout -> View.layout -> ViewGroup.onLayout -> ... -> View.layout -> View.onLayout

注： 

* ViewGroup.onLayout 是抽象方法，根据不同的 ViewGroup 都有不同的实现方式。但是相同的是，都会遍历调用 child.layout 方法；
* View.onLayout 是空方法；

draw过程
=======
最后一个，draw 过程。 draw 过程应该来说是比较简单的。

ViewRootImpl
-------------
### performDraw()

首先起点是 `performDraw()` 方法。

``` java
    private void performDraw() {
        if (mAttachInfo.mDisplayState == Display.STATE_OFF && !mReportNextDraw) {
            return;
        }

        final boolean fullRedrawNeeded = mFullRedrawNeeded;
        mFullRedrawNeeded = false;

        mIsDrawing = true;
        Trace.traceBegin(Trace.TRACE_TAG_VIEW, "draw");
        try {
            // 调用 draw 方法，fullRedrawNeeded 为是否重新绘制全部视图
            draw(fullRedrawNeeded);
        } finally {
            mIsDrawing = false;
            Trace.traceEnd(Trace.TRACE_TAG_VIEW);
        }

        ...

    }
```

如果是第一次绘制视图，那么显然应该绘制所有的视图，`fullRedrawNeeded` 参数就为 true ；反之如果由于某些原因，导致了视图重绘，那么就没有必要绘制所有视图，即为 false 。

### draw(boolean fullRedrawNeeded)

`performDraw()` 内部又调用了私有方法 `draw(boolean fullRedrawNeeded)` :

``` java
    private void draw(boolean fullRedrawNeeded) {
        
        ...

        // dirty 表示需要绘制的区域
        final Rect dirty = mDirty;
        if (mSurfaceHolder != null) {
            // The app owns the surface, we won't draw.
            dirty.setEmpty();
            if (animating && mScroller != null) {
                mScroller.abortAnimation();
            }
            return;
        }

        // 如果需要全部绘制，那么 dirty 就是整个屏幕了
        if (fullRedrawNeeded) {
            mAttachInfo.mIgnoreDirtyState = true;
            dirty.set(0, 0, (int) (mWidth * appScale + 0.5f), (int) (mHeight * appScale + 0.5f));
        }

        ...

        // 调用 drawSoftware ，把绘制区域 dirty 传入
        if (!drawSoftware(surface, mAttachInfo, xOffset, yOffset, scalingRequired, dirty)) {
            return;
        }
            
        ...

    }
```

在确定了绘制的区域 `dirty` 之后，调用了 `drawSoftware(Surface surface, AttachInfo attachInfo, int xoff, int yoff, boolean scalingRequired, Rect dirty)` 。

### drawSoftware(Surface surface, AttachInfo attachInfo, int xoff, int yoff, boolean scalingRequired, Rect dirty)

``` java
    private boolean drawSoftware(Surface surface, AttachInfo attachInfo, int xoff, int yoff,
            boolean scalingRequired, Rect dirty) {

        // Draw with software renderer.
        final Canvas canvas;
        try {
            final int left = dirty.left;
            final int top = dirty.top;
            final int right = dirty.right;
            final int bottom = dirty.bottom;

            //锁定画布，由 dirty 区域决定
            canvas = mSurface.lockCanvas(dirty);

            // The dirty rectangle can be modified by Surface.lockCanvas()
            //noinspection ConstantConditions
            if (left != dirty.left || top != dirty.top || right != dirty.right
                    || bottom != dirty.bottom) {
                attachInfo.mIgnoreDirtyState = true;
            }

            // TODO: Do this in native
            canvas.setDensity(mDensity);
        } catch (Surface.OutOfResourcesException e) {
            handleOutOfResourcesException(e);
            return false;
        } catch (IllegalArgumentException e) {
            Log.e(mTag, "Could not lock surface", e);
            // Don't assume this is due to out of memory, it could be
            // something else, and if it is something else then we could
            // kill stuff (or ourself) for no reason.
            mLayoutRequested = true;    // ask wm for a new surface next time.
            return false;
        }

        try {
            if (DEBUG_ORIENTATION || DEBUG_DRAW) {
                Log.v(mTag, "Surface " + surface + " drawing to bitmap w="
                        + canvas.getWidth() + ", h=" + canvas.getHeight());
                //canvas.drawARGB(255, 255, 0, 0);
            }

            // If this bitmap's format includes an alpha channel, we
            // need to clear it before drawing so that the child will
            // properly re-composite its drawing on a transparent
            // background. This automatically respects the clip/dirty region
            // or
            // If we are applying an offset, we need to clear the area
            // where the offset doesn't appear to avoid having garbage
            // left in the blank areas.
            if (!canvas.isOpaque() || yoff != 0 || xoff != 0) {
                canvas.drawColor(0, PorterDuff.Mode.CLEAR);
            }

            dirty.setEmpty();
            mIsAnimating = false;
            mView.mPrivateFlags |= View.PFLAG_DRAWN;

            if (DEBUG_DRAW) {
                Context cxt = mView.getContext();
                Log.i(mTag, "Drawing: package:" + cxt.getPackageName() +
                        ", metrics=" + cxt.getResources().getDisplayMetrics() +
                        ", compatibilityInfo=" + cxt.getResources().getCompatibilityInfo());
            }
            try {
                canvas.translate(-xoff, -yoff);
                if (mTranslator != null) {
                    mTranslator.translateCanvas(canvas);
                }
                canvas.setScreenDensity(scalingRequired ? mNoncompatDensity : 0);
                attachInfo.mSetIgnoreDirtyState = false;

                // 调用 View 的 draw 方法
                mView.draw(canvas);

                drawAccessibilityFocusedDrawableIfNeeded(canvas);
            } finally {
                if (!attachInfo.mSetIgnoreDirtyState) {
                    // Only clear the flag if it was not set during the mView.draw() call
                    attachInfo.mIgnoreDirtyState = false;
                }
            }
        } finally {
            try {
                surface.unlockCanvasAndPost(canvas);
            } catch (IllegalArgumentException e) {
                Log.e(mTag, "Could not unlock surface", e);
                mLayoutRequested = true;    // ask wm for a new surface next time.
                //noinspection ReturnInsideFinallyBlock
                return false;
            }

            if (LOCAL_LOGV) {
                Log.v(mTag, "Surface " + surface + " unlockCanvasAndPost");
            }
        }
        return true;
    }
```

View
-----
### draw(Canvas canvas)

之后调用了 `View` 的 `draw(Canvas canvas)` ：

``` java
    public void draw(Canvas canvas) {
        final int privateFlags = mPrivateFlags;
        final boolean dirtyOpaque = (privateFlags & PFLAG_DIRTY_MASK) == PFLAG_DIRTY_OPAQUE &&
                (mAttachInfo == null || !mAttachInfo.mIgnoreDirtyState);
        mPrivateFlags = (privateFlags & ~PFLAG_DIRTY_MASK) | PFLAG_DRAWN;

        /*
         * Draw traversal performs several drawing steps which must be executed
         * in the appropriate order:
         *
         *      1. Draw the background
         *      2. If necessary, save the canvas' layers to prepare for fading
         *      3. Draw view's content
         *      4. Draw children
         *      5. If necessary, draw the fading edges and restore layers
         *      6. Draw decorations (scrollbars for instance)
         */

        // 第一步，画背景
        int saveCount;

        if (!dirtyOpaque) {
            drawBackground(canvas);
        }

        // skip step 2 & 5 if possible (common case)
        // 可能的话，跳过第二步和第五步
        final int viewFlags = mViewFlags;
        boolean horizontalEdges = (viewFlags & FADING_EDGE_HORIZONTAL) != 0;
        boolean verticalEdges = (viewFlags & FADING_EDGE_VERTICAL) != 0;
        if (!verticalEdges && !horizontalEdges) {
            // 第三步，画自己的内容
            if (!dirtyOpaque) onDraw(canvas);

            // 第四步，画自己子 view 的内容
            dispatchDraw(canvas);

            // Overlay is part of the content and draws beneath Foreground
            if (mOverlay != null && !mOverlay.isEmpty()) {
                mOverlay.getOverlayView().dispatchDraw(canvas);
            }

            // 第六步，绘制View的装饰，比如 scrollbar 等 (foreground, scrollbars)
            onDrawForeground(canvas);

            // 做完了，直接返回 we're done...
            return;
        }

        /*
         * Here we do the full fledged routine...
         * (this is an uncommon case where speed matters less,
         * this is why we repeat some of the tests that have been
         * done above)
         */

        boolean drawTop = false;
        boolean drawBottom = false;
        boolean drawLeft = false;
        boolean drawRight = false;

        float topFadeStrength = 0.0f;
        float bottomFadeStrength = 0.0f;
        float leftFadeStrength = 0.0f;
        float rightFadeStrength = 0.0f;

        // 第二步，保存 canvas 图层
        int paddingLeft = mPaddingLeft;

        final boolean offsetRequired = isPaddingOffsetRequired();
        if (offsetRequired) {
            paddingLeft += getLeftPaddingOffset();
        }

        int left = mScrollX + paddingLeft;
        int right = left + mRight - mLeft - mPaddingRight - paddingLeft;
        int top = mScrollY + getFadeTop(offsetRequired);
        int bottom = top + getFadeHeight(offsetRequired);

        if (offsetRequired) {
            right += getRightPaddingOffset();
            bottom += getBottomPaddingOffset();
        }

        final ScrollabilityCache scrollabilityCache = mScrollCache;
        final float fadeHeight = scrollabilityCache.fadingEdgeLength;
        int length = (int) fadeHeight;

        // clip the fade length if top and bottom fades overlap
        // overlapping fades produce odd-looking artifacts
        if (verticalEdges && (top + length > bottom - length)) {
            length = (bottom - top) / 2;
        }

        // also clip horizontal fades if necessary
        if (horizontalEdges && (left + length > right - length)) {
            length = (right - left) / 2;
        }

        if (verticalEdges) {
            topFadeStrength = Math.max(0.0f, Math.min(1.0f, getTopFadingEdgeStrength()));
            drawTop = topFadeStrength * fadeHeight > 1.0f;
            bottomFadeStrength = Math.max(0.0f, Math.min(1.0f, getBottomFadingEdgeStrength()));
            drawBottom = bottomFadeStrength * fadeHeight > 1.0f;
        }

        if (horizontalEdges) {
            leftFadeStrength = Math.max(0.0f, Math.min(1.0f, getLeftFadingEdgeStrength()));
            drawLeft = leftFadeStrength * fadeHeight > 1.0f;
            rightFadeStrength = Math.max(0.0f, Math.min(1.0f, getRightFadingEdgeStrength()));
            drawRight = rightFadeStrength * fadeHeight > 1.0f;
        }

        saveCount = canvas.getSaveCount();

        int solidColor = getSolidColor();
        if (solidColor == 0) {
            final int flags = Canvas.HAS_ALPHA_LAYER_SAVE_FLAG;

            if (drawTop) {
                canvas.saveLayer(left, top, right, top + length, null, flags);
            }

            if (drawBottom) {
                canvas.saveLayer(left, bottom - length, right, bottom, null, flags);
            }

            if (drawLeft) {
                canvas.saveLayer(left, top, left + length, bottom, null, flags);
            }

            if (drawRight) {
                canvas.saveLayer(right - length, top, right, bottom, null, flags);
            }
        } else {
            scrollabilityCache.setFadeColor(solidColor);
        }

        // Step 3, draw the content
        if (!dirtyOpaque) onDraw(canvas);

        // Step 4, draw the children
        dispatchDraw(canvas);

        // 第五步，绘制边缘效果和恢复图层
        final Paint p = scrollabilityCache.paint;
        final Matrix matrix = scrollabilityCache.matrix;
        final Shader fade = scrollabilityCache.shader;

        if (drawTop) {
            matrix.setScale(1, fadeHeight * topFadeStrength);
            matrix.postTranslate(left, top);
            fade.setLocalMatrix(matrix);
            p.setShader(fade);
            canvas.drawRect(left, top, right, top + length, p);
        }

        if (drawBottom) {
            matrix.setScale(1, fadeHeight * bottomFadeStrength);
            matrix.postRotate(180);
            matrix.postTranslate(left, bottom);
            fade.setLocalMatrix(matrix);
            p.setShader(fade);
            canvas.drawRect(left, bottom - length, right, bottom, p);
        }

        if (drawLeft) {
            matrix.setScale(1, fadeHeight * leftFadeStrength);
            matrix.postRotate(-90);
            matrix.postTranslate(left, top);
            fade.setLocalMatrix(matrix);
            p.setShader(fade);
            canvas.drawRect(left, top, left + length, bottom, p);
        }

        if (drawRight) {
            matrix.setScale(1, fadeHeight * rightFadeStrength);
            matrix.postRotate(90);
            matrix.postTranslate(right, top);
            fade.setLocalMatrix(matrix);
            p.setShader(fade);
            canvas.drawRect(right - length, top, right, bottom, p);
        }

        canvas.restoreToCount(saveCount);

        // Overlay is part of the content and draws beneath Foreground
        if (mOverlay != null && !mOverlay.isEmpty()) {
            mOverlay.getOverlayView().dispatchDraw(canvas);
        }

        // Step 6, draw decorations (foreground, scrollbars)
        onDrawForeground(canvas);
    }
```

draw 过程大概有下面几步：

1. 绘制背景：`background.draw(canvas)` ；
2. 保存当前的图层信息（一般来说跳过）；
3. 绘制自己：`onDraw(canvas)` ；
4. 绘制children：`dispatchDraw(canvas)` ；
5. 绘制边缘效果，恢复图层（一般来说跳过）；
6. 绘制前景装饰：`onDrawForeground(canvas)` 。

在这里，我们继续看一下 `dispatchDraw(Canvas canvas)` 方法，这个方法是向子 View 分发绘制流程的。

因为 View 没有子 View ，所以 `dispatchDraw(Canvas canvas)` 方法是空的，所以我们要到 ViewGroup 中去看看。

ViewGroup
---------
### dispatchDraw(Canvas canvas)

``` java
    @Override
    protected void dispatchDraw(Canvas canvas) {
        boolean usingRenderNodeProperties = canvas.isRecordingFor(mRenderNode);
        final int childrenCount = mChildrenCount;
        final View[] children = mChildren;
        int flags = mGroupFlags;

        if ((flags & FLAG_RUN_ANIMATION) != 0 && canAnimate()) {
            final boolean buildCache = !isHardwareAccelerated();

            // 遍历子 view 
            for (int i = 0; i < childrenCount; i++) {
                final View child = children[i];
                if ((child.mViewFlags & VISIBILITY_MASK) == VISIBLE) {
                    final LayoutParams params = child.getLayoutParams();
                    attachLayoutAnimationParameters(child, params, i, childrenCount);
                    bindLayoutAnimation(child);
                }
            }

            final LayoutAnimationController controller = mLayoutAnimationController;
            if (controller.willOverlap()) {
                mGroupFlags |= FLAG_OPTIMIZE_INVALIDATE;
            }

            controller.start();

            mGroupFlags &= ~FLAG_RUN_ANIMATION;
            mGroupFlags &= ~FLAG_ANIMATION_DONE;

            if (mAnimationListener != null) {
                mAnimationListener.onAnimationStart(controller.getAnimation());
            }
        }

        int clipSaveCount = 0;
        final boolean clipToPadding = (flags & CLIP_TO_PADDING_MASK) == CLIP_TO_PADDING_MASK;
        if (clipToPadding) {
            clipSaveCount = canvas.save();
            canvas.clipRect(mScrollX + mPaddingLeft, mScrollY + mPaddingTop,
                    mScrollX + mRight - mLeft - mPaddingRight,
                    mScrollY + mBottom - mTop - mPaddingBottom);
        }

        // We will draw our child's animation, let's reset the flag
        mPrivateFlags &= ~PFLAG_DRAW_ANIMATION;
        mGroupFlags &= ~FLAG_INVALIDATE_REQUIRED;

        boolean more = false;
        final long drawingTime = getDrawingTime();

        if (usingRenderNodeProperties) canvas.insertReorderBarrier();
        final int transientCount = mTransientIndices == null ? 0 : mTransientIndices.size();
        int transientIndex = transientCount != 0 ? 0 : -1;
        // Only use the preordered list if not HW accelerated, since the HW pipeline will do the
        // draw reordering internally
        final ArrayList<View> preorderedList = usingRenderNodeProperties
                ? null : buildOrderedChildList();
        final boolean customOrder = preorderedList == null
                && isChildrenDrawingOrderEnabled();
        for (int i = 0; i < childrenCount; i++) {
            while (transientIndex >= 0 && mTransientIndices.get(transientIndex) == i) {
                final View transientChild = mTransientViews.get(transientIndex);
                if ((transientChild.mViewFlags & VISIBILITY_MASK) == VISIBLE ||
                        transientChild.getAnimation() != null) {
                    more |= drawChild(canvas, transientChild, drawingTime);
                }
                transientIndex++;
                if (transientIndex >= transientCount) {
                    transientIndex = -1;
                }
            }

            final int childIndex = getAndVerifyPreorderedIndex(childrenCount, i, customOrder);
            final View child = getAndVerifyPreorderedView(preorderedList, children, childIndex);
            if ((child.mViewFlags & VISIBILITY_MASK) == VISIBLE || child.getAnimation() != null) {
                // 调用 drawChild 来绘制子 view
                more |= drawChild(canvas, child, drawingTime);
            }
        }
        ...
    }
```

在 `dispatchDraw(Canvas canvas)` 中，遍历子 View ，然后调用 `drawChild(Canvas canvas, View child, long drawingTime)` 方法来执行子 View 的绘制流程。

### drawChild(Canvas canvas, View child, long drawingTime)

``` java
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        return child.draw(canvas, this, drawingTime);
    }
```

发现在 `drawChild(Canvas canvas, View child, long drawingTime)` 中还是调用了 `draw(Canvas canvas, ViewGroup parent, long drawingTime)` 方法。但是这个 `draw(Canvas canvas, ViewGroup parent, long drawingTime)` 和上面的 `draw(Canvas canvas)` 参数不同，所以不是同一个方法。

View
----
### draw(Canvas canvas, ViewGroup parent, long drawingTime)

``` java
	boolean draw(Canvas canvas, ViewGroup parent, long drawingTime) {

        ...

		// 如果没有绘制缓存
        if (!drawingWithDrawingCache) {
            if (drawingWithRenderNode) {
                mPrivateFlags &= ~PFLAG_DIRTY_MASK;
                ((DisplayListCanvas) canvas).drawRenderNode(renderNode);
            } else {
                // Fast path for layouts with no backgrounds
                // 如果设置了 willNotDraw 为 true ，那么不会绘制自己，直接跳过，优化绘制性能
                // View 默认是 false ，ViewGroup 默认是 true ，直接让自己的子 View 进入绘制
                if ((mPrivateFlags & PFLAG_SKIP_DRAW) == PFLAG_SKIP_DRAW) {
                    mPrivateFlags &= ~PFLAG_DIRTY_MASK;
                    dispatchDraw(canvas);
                } else {
                    // 调用 draw 方法
                    draw(canvas);
                }
            }
        } else if (cache != null) {
            // 有缓存就用缓存绘制
            mPrivateFlags &= ~PFLAG_DIRTY_MASK;
            if (layerType == LAYER_TYPE_NONE || mLayerPaint == null) {
                // no layer paint, use temporary paint to draw bitmap
                Paint cachePaint = parent.mCachePaint;
                if (cachePaint == null) {
                    cachePaint = new Paint();
                    cachePaint.setDither(false);
                    parent.mCachePaint = cachePaint;
                }
                cachePaint.setAlpha((int) (alpha * 255));
                canvas.drawBitmap(cache, 0.0f, 0.0f, cachePaint);
            } else {
                // use layer paint to draw the bitmap, merging the two alphas, but also restore
                int layerPaintAlpha = mLayerPaint.getAlpha();
                if (alpha < 1) {
                    mLayerPaint.setAlpha((int) (alpha * layerPaintAlpha));
                }
                canvas.drawBitmap(cache, 0.0f, 0.0f, mLayerPaint);
                if (alpha < 1) {
                    mLayerPaint.setAlpha(layerPaintAlpha);
                }
            }
        }

        ...

	}
```

在 `draw(Canvas canvas, ViewGroup parent, long drawingTime)` 中，若没有缓存的话：

* 若 `willNotDraw` 设置为 false 的话，那么调用 `draw(canvas)` ；
* 否则直接调用 `dispatchDraw(canvas)` 分发给子 View ，一般适用于 ViewGroup ；

`willNotDraw` 代表一个 View 不需要绘制任何内容的话，那么系统会跳过，进行性能上的优化。

到这里，就调用了子 View 的 `draw(Canvas canvas)` 方法，从而实现了绘制过程的向下传递。

小结
---
我们把 draw 过程的代码流程理一下：

ViewRootImpl.performTraversals -> ViewRootImpl.performDraw -> ViewRootImpl.draw(boolean fullRedrawNeeded) -> ViewRootImpl.drawSoftware -> DecorView(View).draw(Canvas canvas) -> DecorView(ViewGroup).dispatchDraw -> DecorView(ViewGroup).drawChild -> ViewGroup(View).draw(Canvas canvas, ViewGroup parent, long drawingTime) -> ViewGroup.dispatchDraw -> ViewGroup.drawChild -> ViewGroup.draw(Canvas canvas, ViewGroup parent, long drawingTime) -> ... -> View.draw(Canvas canvas) -> View.onDraw -> View.dispatchDraw

注：

* 其中 `View.dispatchDraw` 为空实现；
* DecorView 在 `draw(Canvas canvas)` 的方法内不会调用 `onDraw` 方法；
* ViewGroup 不会调用 `draw(Canvas canvas)` 方法；

最后
===
总体来说，三个流程中主要还是 measure 过程较复杂。其他的两个流程整体上来说还是比较清晰简单的。

可以说 View 工作的三大流程是每一位 Android 开发者都必须掌握的。之前虽然也了解，但是没有写成博客好好捋一下，现在终于完成了，篇幅真的太长了。 ^_^

另外，除了需要了解这三大流程外，还需要知道 `requestLayout` 和 `invalidate` 等方法的原理。这些东西等有空了我理一理再写出来给大家吧。

今天就这样了，如果有不懂的地方可以在下面留言。

References
==========
* [View绘制流程及源码解析(一)——performTraversals()源码分析](http://www.jianshu.com/p/a65861e946cb)
* [从ViewRootImpl类分析View绘制的流程](http://blog.csdn.net/feiduclear_up/article/details/46772477)
* [Android View 测量流程(Measure)完全解析](http://blog.csdn.net/a553181867/article/details/51494058)
* [Android学习笔记---深入理解View#04](http://www.jianshu.com/p/6d66ea4998de)
* [Android View 绘制流程(Draw) 完全解析](http://blog.csdn.net/a553181867/article/details/51570854)
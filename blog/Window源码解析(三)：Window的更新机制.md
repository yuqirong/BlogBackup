title: Window源码解析(三)：Window的更新机制
date: 2017-10-10 20:53:03
categories: Android Blog
tags: [Android,Window,源码解析]
---
注：本文解析的源码基于 API 25，部分内容来自于《Android开发艺术探索》。

第一篇：[《Window源码解析(一)：与DecorView的那些事》][url]

第二篇：[《Window源码解析(二)：Window的添加机制》][url2]

[url]: /2017/09/28/Window源码解析(一)：与DecorView的那些事/
[url2]: /2017/10/08/Window源码解析(二)：Window的添加机制/

Header
======
在上一篇中，介绍了 Window 添加机制的实现。

那么今天就好好探究探究 Window 更新机制。其实 Window 的更新内部流程和添加 Window 并无什么差异，所以本篇可能会讲得比较简略。

但是还是值得我们去了解的，那么老死机开车了。

Window的更新机制
===============
我们更新 Window 的代码：

`WindowManager.updateViewLayout`

WindowManagerImpl
-----------------
### updateViewLayout(@NonNull View view, @NonNull ViewGroup.LayoutParams params)

所以我们的入口就是 WindowManagerImpl 实现类的，先看代码：

``` java
    @Override
    public void updateViewLayout(@NonNull View view, @NonNull ViewGroup.LayoutParams params) {
        applyDefaultToken(params);
        mGlobal.updateViewLayout(view, params);
    }
```

果然，内部还是交给了 WindowManagerGlobal 来处理了，而且这代码和 `addView` 的极其类似。

WindowManagerGlobal
-------------------
### updateViewLayout(View view, ViewGroup.LayoutParams params)

``` java
    public void updateViewLayout(View view, ViewGroup.LayoutParams params) {
        if (view == null) {
            throw new IllegalArgumentException("view must not be null");
        }
        if (!(params instanceof WindowManager.LayoutParams)) {
            throw new IllegalArgumentException("Params must be WindowManager.LayoutParams");
        }

        final WindowManager.LayoutParams wparams = (WindowManager.LayoutParams)params;

        view.setLayoutParams(wparams);

        synchronized (mLock) {
            // 找到该 view 的索引
            int index = findViewLocked(view, true);
            ViewRootImpl root = mRoots.get(index);
            // 替换 params
            mParams.remove(index);
            mParams.add(index, wparams);
            root.setLayoutParams(wparams, false);
        }
    }
```

这代码也基本上一看就懂的。因为是更新 Window ，所以肯定是要替换 params 了。

之后就是调用 `ViewRootImpl.setLayoutParams` 来设置新的 params 。

ViewRootImpl
------------
### setLayoutParams(WindowManager.LayoutParams attrs, boolean newView)

``` java
    void setLayoutParams(WindowManager.LayoutParams attrs, boolean newView) {
        synchronized (this) {

            ...

            applyKeepScreenOnFlag(mWindowAttributes);

            // 传入的 newView 是 false ，不执行这些代码
            if (newView) {
                mSoftInputMode = attrs.softInputMode;
                requestLayout();
            }

            // Don't lose the mode we last auto-computed.
            if ((attrs.softInputMode & WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST)
                    == WindowManager.LayoutParams.SOFT_INPUT_ADJUST_UNSPECIFIED) {
                mWindowAttributes.softInputMode = (mWindowAttributes.softInputMode
                        & ~WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST)
                        | (oldSoftInputMode & WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST);
            }

            mWindowAttributesChanged = true;
            // 使 view 重走三大流程
            scheduleTraversals();
        }
    }
```

在 `setLayoutParams` 中，调用了 `scheduleTraversals()` 方法。

在之前讲 View 工作原理的时候，我们都看过 `scheduleTraversals()` 最后会调用 `performTraversals()` 来开始 View 的测量、布局和绘制。所以在这，也就触发了 View 重新去调整自己。

### performTraversals()

``` java
    private void performTraversals() {
		...
		relayoutResult = relayoutWindow(params, viewVisibility, insetsPending);
		...
	}
```

`performTraversals()` 方法太长了，其他的都不看了，我们只注意这一句代码。

接着，在内部又调用了 `relayoutWindow(params, viewVisibility, insetsPending)` 方法。一看这方法名就知道这方法都干什么了。

### relayoutWindow()

``` java
    private int relayoutWindow(WindowManager.LayoutParams params, int viewVisibility,
            boolean insetsPending) throws RemoteException {

        ...

        // 看得出来，这里又是调用 session 来走 IPC 流程，然后得到更新 window 的结果 relayoutResult
        int relayoutResult = mWindowSession.relayout(
                mWindow, mSeq, params,
                (int) (mView.getMeasuredWidth() * appScale + 0.5f),
                (int) (mView.getMeasuredHeight() * appScale + 0.5f),
                viewVisibility, insetsPending ? WindowManagerGlobal.RELAYOUT_INSETS_PENDING : 0,
                mWinFrame, mPendingOverscanInsets, mPendingContentInsets, mPendingVisibleInsets,
                mPendingStableInsets, mPendingOutsets, mPendingBackDropFrame, mPendingConfiguration,
                mSurface);

        ...

        return relayoutResult;
    }
```

到了这一步，我们再次相遇熟悉的 `mWindowSession` 。

也知道了其实这是走了一个 IPC 的调用过程，在它内部肯定会利用 WindowManagerService 来完成 Window 的更新。

而 relayoutResult 就是这 IPC 最后返回的结果，也就是 Window 更新的结果。

虽然套路都懂了，但是有时候我们还是要吃。那么就去 Session 类中看看。

Session
---------
### relayout(IWindow window, int seq, WindowManager.LayoutParams attrs ... )

``` java
    public int relayout(IWindow window, int seq, WindowManager.LayoutParams attrs,
            int requestedWidth, int requestedHeight, int viewFlags,
            int flags, Rect outFrame, Rect outOverscanInsets, Rect outContentInsets,
            Rect outVisibleInsets, Rect outStableInsets, Rect outsets, Rect outBackdropFrame,
            Configuration outConfig, Surface outSurface) {
        if (false) Slog.d(TAG_WM, ">>>>>> ENTERED relayout from "
                + Binder.getCallingPid());
        int res = mService.relayoutWindow(this, window, seq, attrs,
                requestedWidth, requestedHeight, viewFlags, flags,
                outFrame, outOverscanInsets, outContentInsets, outVisibleInsets,
                outStableInsets, outsets, outBackdropFrame, outConfig, outSurface);
        if (false) Slog.d(TAG_WM, "<<<<<< EXITING relayout to "
                + Binder.getCallingPid());
        return res;
    }
```

和我们预想的一样，内部是调用了 mService ，也就是 WindowManagerService 。

WindowManagerService
---------------------
### relayoutWindow(Session session, IWindow client, int seq, WindowManager.LayoutParams attrs ... )

``` java
    public int relayoutWindow(Session session, IWindow client, int seq,
            WindowManager.LayoutParams attrs, int requestedWidth,
            int requestedHeight, int viewVisibility, int flags,
            Rect outFrame, Rect outOverscanInsets, Rect outContentInsets,
            Rect outVisibleInsets, Rect outStableInsets, Rect outOutsets, Rect outBackdropFrame,
            Configuration outConfig, Surface outSurface) {
        int result = 0;
        boolean configChanged;
        boolean hasStatusBarPermission =
                mContext.checkCallingOrSelfPermission(android.Manifest.permission.STATUS_BAR)
                        == PackageManager.PERMISSION_GRANTED;

        long origId = Binder.clearCallingIdentity();
        synchronized(mWindowMap) {
			// 根据 session 和client 得到 windowState 对象
            WindowState win = windowForClientLocked(session, client, false);
            if (win == null) {
                return 0;
            }

			// 将属性进行相应的转换后保存到 WindowState
			...

			// 去更新  window
			if (viewVisibility == View.VISIBLE &&
                (win.mAppToken == null || !win.mAppToken.clientHidden)) {
	            result = relayoutVisibleWindow(outConfig, result, win, winAnimator, attrChanges,
	                    oldVisibility);
	            try {
	                result = createSurfaceControl(outSurface, result, win, winAnimator);
	            } catch (Exception e) {
	                mInputMonitor.updateInputWindowsLw(true /*force*/);
	
	                Slog.w(TAG_WM, "Exception thrown when creating surface for client "
	                         + client + " (" + win.mAttrs.getTitle() + ")",
	                         e);
	                Binder.restoreCallingIdentity(origId);
	                return 0;
	            }
			}

			...			

            boolean toBeDisplayed = (result & WindowManagerGlobal.RELAYOUT_RES_FIRST_TIME) != 0;
            if (imMayMove && (moveInputMethodWindowsIfNeededLocked(false) || toBeDisplayed)) {
                // Little hack here -- we -should- be able to rely on the
                // function to return true if the IME has moved and needs
                // its layer recomputed.  However, if the IME was hidden
                // and isn't actually moved in the list, its layer may be
                // out of data so we make sure to recompute it.
                // 如果窗口排序有改动，那么为 DisplayContent 的所有窗口分配最终的显示次序
                mLayersController.assignLayersLocked(win.getWindowList());
            }
			...
			// 更新 window 后设置一些变量

	}
```

WMS 的 `relayoutWindow` 方法中，先得到了需要更新的 WindowState 对象，接着去执行更新。如果 Window 的显示次序变化了的话，需要重新分配次序。最后就是设置一些 Window 更新完成后的一些变量了。

而其他的代码太复杂了，学艺不精，不能全部分析出来。

Footer
======
总之，Window 更新也和添加一样，都是通过 session 来调用 IPC 过程完成的。并且最终实现都是在 WindowManagerService 里。

至此，还有一篇 Window 删除还没分析。不用猜也知道，这流程肯定也是差不多的。但是我们还是要深入其中一探究竟。

今天就完结了，bye !

References
==========
* [Android源码分析之WindowManager.LayoutParams属性更新过程](http://blog.csdn.net/amwihihc/article/details/7992329)
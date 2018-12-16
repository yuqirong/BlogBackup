title: Window源码解析(四)：Window的删除机制
date: 2017-10-23 21:46:03
categories: Android Blog
tags: [Android,Window,源码解析]
---
注：本文解析的源码基于 API 25，部分内容来自于《Android开发艺术探索》。

第一篇：[《Window源码解析(一)：与DecorView的那些事》][url]

第二篇：[《Window源码解析(二)：Window的添加机制》][url2]

第二篇：[《Window源码解析(三)：Window的更新机制》][url3]

[url]: /2017/09/28/Window源码解析(一)：与DecorView的那些事/
[url2]: /2017/10/08/Window源码解析(二)：Window的添加机制/
[url3]: /2017/10/10/Window源码解析(三)：Window的更新机制/

Header
======
这篇将是 Window 系列的最后一篇了，主要来讲讲 Window 删除的机制原理。

其实相对于 Window 的添加和更新来说，删除也是换汤不换药的。也是通过 WindowSession 和 WindowManagerService 来完成这个步骤的。

Window的删除机制
===============
我们删除 Window 的代码：

`WindowManager.removeView`

WindowManagerImpl
------------------
### removeView(View view)

``` java
    @Override
    public void removeView(View view) {
        mGlobal.removeView(view, false);
    }
```

WindowManager 是一个接口，具体实现是 WindowManagerImpl 类。不用说，WindowManagerImpl 内部肯定是 WindowManagerGlobal 在“作祟”咯。

WindowManagerGlobal
--------------------
### removeView(View view, boolean immediate)

``` java
    public void removeView(View view, boolean immediate) {
        if (view == null) {
            throw new IllegalArgumentException("view must not be null");
        }

        synchronized (mLock) {
            // 得到当前 view 的索引
            int index = findViewLocked(view, true);
            View curView = mRoots.get(index).getView();
            // 主要执行删除 view 的操作
            removeViewLocked(index, immediate);
            // 如果要删除的 view 不是 viewrootimpl 中的 view ，那么会抛出异常
            if (curView == view) {
                return;
            }

            throw new IllegalStateException("Calling with view " + view
                    + " but the ViewAncestor is attached to " + curView);
        }
    }
```

在 `removeView(View view, boolean immediate)` 先找到了打算删除的 View 的索引。然后根据索引去执行删除操作。

若 `immediate` 参数传入的是 true ，那么就执行了同步删除操作；否则就是异步删除操作了。大多使用的都是异步删除操作，避免出错，即 `immediate` 为 false；

其实这个方法的重点都放在了 `removeViewLocked(index, immediate)` 中了。

### removeViewLocked(int index, boolean immediate)

``` java
    private void removeViewLocked(int index, boolean immediate) {
        ViewRootImpl root = mRoots.get(index);
        View view = root.getView();
		
        // 关闭输入法
        if (view != null) {
            InputMethodManager imm = InputMethodManager.getInstance();
            if (imm != null) {
                imm.windowDismissed(mViews.get(index).getWindowToken());
            }
        }
        // 调用 die 方法，将 immediate 传入，即是否为同步删除
        boolean deferred = root.die(immediate);
        if (view != null) {
            view.assignParent(null);
            if (deferred) {
				// 添加到马上移除的集合中
                mDyingViews.add(view);
            }
        }
    }
```

在 `removeViewLocked(int index, boolean immediate)` 中，调用了 ViewRootImpl 的 die 方法。大多数的默认情况下，`immediate` 都为 false 。

之后又将 view 添加到 mDyingViews 中。mDyingViews 维持着都是即将要删除的 View 。


ViewRootImpl
------------
### die(boolean immediate)
``` java
    boolean die(boolean immediate) {
        // Make sure we do execute immediately if we are in the middle of a traversal or the damage
        // done by dispatchDetachedFromWindow will cause havoc on return.

        // 如果是同步移除，则马上执行 doDie
        if (immediate && !mIsInTraversal) {
            doDie();
            return false;
        }

        if (!mIsDrawing) {
            destroyHardwareRenderer();
        } else {
            Log.e(mTag, "Attempting to destroy the window while drawing!\n" +
                    "  window=" + this + ", title=" + mWindowAttributes.getTitle());
        }
        // 异步的话就利用 handler 发一个 messaage , 接收到 message 后也是执行 doDie 方法
        mHandler.sendEmptyMessage(MSG_DIE);
        return true;
    }
```

在 `die(boolean immediate)` 方法中，不管同步还是异步，都是执行 `doDie()` 方法。不同的就是同步是马上执行，而异步是利用 Handler 去发消息，接收到消息后在执行。

### doDie()

``` java
    void doDie() {
        // 先检查线程，要在主线程中进行
        checkThread();
        if (LOCAL_LOGV) Log.v(mTag, "DIE in " + this + " of " + mSurface);
        synchronized (this) {
            if (mRemoved) {
                return;
            }
            mRemoved = true;
            if (mAdded) {
                // 如果是已经添加到 Window 上的，执行删除操作
                dispatchDetachedFromWindow();
            }

            if (mAdded && !mFirst) {
                destroyHardwareRenderer();

                if (mView != null) {
                    int viewVisibility = mView.getVisibility();
                    boolean viewVisibilityChanged = mViewVisibility != viewVisibility;
                    if (mWindowAttributesChanged || viewVisibilityChanged) {
                        // If layout params have been changed, first give them
                        // to the window manager to make sure it has the correct
                        // animation info.
                        try {
                            if ((relayoutWindow(mWindowAttributes, viewVisibility, false)
                                    & WindowManagerGlobal.RELAYOUT_RES_FIRST_TIME) != 0) {
                                mWindowSession.finishDrawing(mWindow);
                            }
                        } catch (RemoteException e) {
                        }
                    }

                    mSurface.release();
                }
            }
            // 将 mAdded 设置为 false
            mAdded = false;
        }

        // 从对应的 mRoots mParams mDyingViews 中移除该 view 的引用
        WindowManagerGlobal.getInstance().doRemoveView(this);
    }
```

`doDie()` 方法中主要看两点：

1. dispatchDetachedFromWindow() 是去执行删除 window 的方法；
2. WindowManagerGlobal.getInstance().doRemoveView(this) 把 mRoot 、mParams 和 mDyingViews 中关于当前 Window 的参数都移除了。

所以我们接下来，还是要看下 dispatchDetachedFromWindow() 方法。

``` java
    void dispatchDetachedFromWindow() {
        // 在这里调用 view 的 dispatchDetachedFromWindow 方法
        if (mView != null && mView.mAttachInfo != null) {
            mAttachInfo.mTreeObserver.dispatchOnWindowAttachedChange(false);
            mView.dispatchDetachedFromWindow();
        }

        // 辅助功能相关的操作
        mAccessibilityInteractionConnectionManager.ensureNoConnection();
        mAccessibilityManager.removeAccessibilityStateChangeListener(
                mAccessibilityInteractionConnectionManager);
        mAccessibilityManager.removeHighTextContrastStateChangeListener(
                mHighContrastTextManager);
        removeSendWindowContentChangedCallback();
        // 垃圾回收的工作
        destroyHardwareRenderer();

        setAccessibilityFocus(null, null);

        mView.assignParent(null);
        mView = null;
        mAttachInfo.mRootView = null;

        mSurface.release();

        if (mInputQueueCallback != null && mInputQueue != null) {
            mInputQueueCallback.onInputQueueDestroyed(mInputQueue);
            mInputQueue.dispose();
            mInputQueueCallback = null;
            mInputQueue = null;
        }
        if (mInputEventReceiver != null) {
            mInputEventReceiver.dispose();
            mInputEventReceiver = null;
        }
        // 重点来了，调用 session 来做 window 移除操作
        try {
            mWindowSession.remove(mWindow);
        } catch (RemoteException e) {
        }

        // Dispose the input channel after removing the window so the Window Manager
        // doesn't interpret the input channel being closed as an abnormal termination.
        if (mInputChannel != null) {
            mInputChannel.dispose();
            mInputChannel = null;
        }

        mDisplayManager.unregisterDisplayListener(mDisplayListener);
        // 解除 view 绘制之类的操作
        unscheduleTraversals();
    }
```

在方法一开头，先回调了 View 的 dispatchDetachedFromWindow 方法，该方法表示 View 马上要从 Window 上删除了。在这个方法内，可以做一些资源回收的工作。

之后做的就是一些垃圾回收的工作，比如清楚数据和消息，移除回调等。

再然后要看的就是 `mWindowSession.remove(mWindow)` ，这步才是真正调用了 Session 来移除 Window 的操作，是 IPC 的过程。具体的我们深入去看了。

Session
-------
``` java
    public void remove(IWindow window) {
        mService.removeWindow(this, window);
    }
```

在 Session 中直接调用了 WindowManagerService 的 `removeWindow(Session session, IWindow client)` 方法。

WindowManagerService
--------------------
``` java
    public void removeWindow(Session session, IWindow client) {
        synchronized(mWindowMap) {
            // 得到 windowstate 对象
            WindowState win = windowForClientLocked(session, client, false);
            if (win == null) {
                return;
            }
            // 进行移除 window 操作
            removeWindowLocked(win);
        }
    }
```

先得到 WindowState 对象，再调用 removeWindowLocked 去移除该 WindowState 。而具体的 removeWindowLocked 代码我们在这就不深入了，可以自行研究。

至此，整个 Window 移除机制就分析完毕了。

Footer
======
终于终于终于把 Window 的相关内容都重新梳理完毕了，也花了将近一个月的时间。

之前有一些似懂非懂的点也明朗了，但是还是有一些地方没有深入去涉及。比如 WindowManagerService 内部的操作。

以后的路还很长，期待自己再深入下去。

References
==========
* [《深入理解Android 卷III》第四章 深入理解WindowManagerService](http://blog.csdn.net/innost/article/details/47660193)
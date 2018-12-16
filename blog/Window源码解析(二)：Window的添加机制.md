title: Window源码解析(二)：Window的添加机制
date: 2017-10-08 15:34:03
categories: Android Blog
tags: [Android,Window,源码解析]
---
注：本文解析的源码基于 API 25，部分内容来自于《Android开发艺术探索》。

第一篇：[《Window源码解析(一)：与DecorView的那些事》][url]

[url]: /2017/09/28/Window源码解析(一)：与DecorView的那些事/

Header
======
在上一篇中，我们讲了 Window 和 DecorView 的那些事，如果没有看过的同学请点击这里：[《Window源码解析(一)：与DecorView的那些事》][url]。

[url]: /2017/09/28/Window源码解析(一)：与DecorView的那些事/

而今天就要来详细了解 Window 的添加机制了，到底在 WindowManager.addView 中做了什么事情？我们一起来看看吧！！

Window的添加机制
===============
上面我们看到了在 `makeVisible()` 中调用了 `wm.addView(mDecor, getWindow().getAttributes())` 将 DecorView 视图添加到 Window 上。

那么调用这句代码之后究竟发生了什么呢，这就需要我们一步一步慢慢去揭开了。

WindowManager
-------------
WindowManager 是一个接口，继承了 ViewManager 。

``` java
	public interface ViewManager
	{
	    public void addView(View view, ViewGroup.LayoutParams params);
	    public void updateViewLayout(View view, ViewGroup.LayoutParams params);
	    public void removeView(View view);
	}
```

可以看到，ViewManager 中定义的方法非常熟悉，也是平时我们经常使用的，就是对 View 的增删改。

对 WindowManager 具体的实现就是 WindowManagerImpl 这个类了。在后面我们会接触到它的。

那么，我们就开始吧。

WindowManagerImpl
-----------------

### addView(@NonNull View view, @NonNull ViewGroup.LayoutParams params)

``` java
    @Override
    public void addView(@NonNull View view, @NonNull ViewGroup.LayoutParams params) {
        // 检查是否需要使用默认的 token ，token 就是一个 binder 对象
        // 如果没有父 window ，那么我们需要使用默认的 token
        applyDefaultToken(params);
        // 调用 WindowManagerGlobal 来实现添加 view
        mGlobal.addView(view, params, mContext.getDisplay(), mParentWindow);
    }
```

发现在 WindowManagerImpl 也没有直接实现 View 的添加，而是转交给了 WindowManagerGlobal 类来做这件事。其实除了 `addView` 之外，`updateViewLayout` 和 `removeView` 也都是通过 WindowManagerGlobal 来实现的，这是桥接模式的体现。

那么我们继续跟下去。

WindowManagerGlobal
-------------------
### addView(View view, ViewGroup.LayoutParams params, Display display, Window parentWindow)

``` java
    public void addView(View view, ViewGroup.LayoutParams params,
            Display display, Window parentWindow) {
        // 检查参数有无错误，如果是子 window 的话要调整一些参数
        if (view == null) {
            throw new IllegalArgumentException("view must not be null");
        }
        if (display == null) {
            throw new IllegalArgumentException("display must not be null");
        }
        if (!(params instanceof WindowManager.LayoutParams)) {
            throw new IllegalArgumentException("Params must be WindowManager.LayoutParams");
        }

        final WindowManager.LayoutParams wparams = (WindowManager.LayoutParams) params;
        if (parentWindow != null) {
            parentWindow.adjustLayoutParamsForSubWindow(wparams);
        } else {
            // If there's no parent, then hardware acceleration for this view is
            // set from the application's hardware acceleration setting.
            final Context context = view.getContext();
            if (context != null
                    && (context.getApplicationInfo().flags
                            & ApplicationInfo.FLAG_HARDWARE_ACCELERATED) != 0) {
                wparams.flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
            }
        }

        ViewRootImpl root;
        View panelParentView = null;

        synchronized (mLock) {
            // Start watching for system property changes.
            if (mSystemPropertyUpdater == null) {
                mSystemPropertyUpdater = new Runnable() {
                    @Override public void run() {
                        synchronized (mLock) {
                            for (int i = mRoots.size() - 1; i >= 0; --i) {
                                mRoots.get(i).loadSystemProperties();
                            }
                        }
                    }
                };
                SystemProperties.addChangeCallback(mSystemPropertyUpdater);
            }

            int index = findViewLocked(view, false);
            if (index >= 0) {
                if (mDyingViews.contains(view)) {
                    // Don't wait for MSG_DIE to make it's way through root's queue.
                    mRoots.get(index).doDie();
                } else {
                    throw new IllegalStateException("View " + view
                            + " has already been added to the window manager.");
                }
                // The previous removeView() had not completed executing. Now it has.
            }

            // If this is a panel window, then find the window it is being
            // attached to for future reference.
            if (wparams.type >= WindowManager.LayoutParams.FIRST_SUB_WINDOW &&
                    wparams.type <= WindowManager.LayoutParams.LAST_SUB_WINDOW) {
                final int count = mViews.size();
                for (int i = 0; i < count; i++) {
                    if (mRoots.get(i).mWindow.asBinder() == wparams.token) {
                        panelParentView = mViews.get(i);
                    }
                }
            }
            // 创建新的 viewrootimpl
            root = new ViewRootImpl(view.getContext(), display);

            view.setLayoutParams(wparams);
            // 保存当前界面这些参数
            // mViews 存储所有 window 所对应的 view
            mViews.add(view);
            // mRoots 存储所有 window 所对应的 ViewRootImpl
            mRoots.add(root);
            // mParams 存储所有 window 所对应的布局参数
            mParams.add(wparams);
        }

        // do this last because it fires off messages to start doing things
        try {
            // 调用 setview 来开始 view 的测量 布局 绘制流程，完成 window 的添加
            root.setView(view, wparams, panelParentView);
        } catch (RuntimeException e) {
            // BadTokenException or InvalidDisplayException, clean up.
            synchronized (mLock) {
                final int index = findViewLocked(view, false);
                if (index >= 0) {
                    removeViewLocked(index, true);
                }
            }
            throw e;
        }
    }
```

在 `addView(View view, ViewGroup.LayoutParams params, Display display, Window parentWindow)` 中，我们捋一捋它干了什么事：

1. 检查了参数，如果是子 Window 的话，还要调整参数；
2. 创建 ViewRootImpl ，然后将当前界面的参数保存起来；
3. 调用 ViewRootImpl 的 setView 来更新界面并完成 Window 的添加；

可以看出，Window 的添加还需要我们到 `ViewRootImpl.setView` 中去看，同时也即将开启 View 三大工作流程。

ViewRootImpl
------------
### setView(View view, WindowManager.LayoutParams attrs, View panelParentView)

``` java
	public void setView(View view, WindowManager.LayoutParams attrs, View panelParentView) {

            // 开始了 view 的三大工作流程
            ...

            try {
                mOrigWindowType = mWindowAttributes.type;
                mAttachInfo.mRecomputeGlobalAttributes = true;
                collectViewAttributes();
                // 利用 mWindowSession 来添加 window ，是一个 IPC 的过程
                res = mWindowSession.addToDisplay(mWindow, mSeq, mWindowAttributes,
                        getHostVisibility(), mDisplay.getDisplayId(),
                        mAttachInfo.mContentInsets, mAttachInfo.mStableInsets,
                        mAttachInfo.mOutsets, mInputChannel);
            } catch (RemoteException e) {
                mAdded = false;
                mView = null;
                mAttachInfo.mRootView = null;
                mInputChannel = null;
                mFallbackEventHandler.setView(null);
                unscheduleTraversals();
                setAccessibilityFocus(null, null);
                throw new RuntimeException("Adding window failed", e);
            } finally {
                if (restore) {
                    attrs.restore();
                }
            }

			...

		// 检查 IPC 的结果，若不是 ADD_OKAY ，就说明添加 window 失败
		if (res < WindowManagerGlobal.ADD_OKAY) {
                mAttachInfo.mRootView = null;
                mAdded = false;
                mFallbackEventHandler.setView(null);
                unscheduleTraversals();
                setAccessibilityFocus(null, null);
                switch (res) {
                    case WindowManagerGlobal.ADD_BAD_APP_TOKEN:
                    case WindowManagerGlobal.ADD_BAD_SUBWINDOW_TOKEN:
                        throw new WindowManager.BadTokenException(
                                "Unable to add window -- token " + attrs.token
                                + " is not valid; is your activity running?");
                    case WindowManagerGlobal.ADD_NOT_APP_TOKEN:
                        throw new WindowManager.BadTokenException(
                                "Unable to add window -- token " + attrs.token
                                + " is not for an application");
                    case WindowManagerGlobal.ADD_APP_EXITING:
                        throw new WindowManager.BadTokenException(
                                "Unable to add window -- app for token " + attrs.token
                                + " is exiting");
                    case WindowManagerGlobal.ADD_DUPLICATE_ADD:
                        throw new WindowManager.BadTokenException(
                                "Unable to add window -- window " + mWindow
                                + " has already been added");
                    case WindowManagerGlobal.ADD_STARTING_NOT_NEEDED:
                        // Silently ignore -- we would have just removed it
                        // right away, anyway.
                        return;
                    case WindowManagerGlobal.ADD_MULTIPLE_SINGLETON:
                        throw new WindowManager.BadTokenException("Unable to add window "
                                + mWindow + " -- another window of type "
                                + mWindowAttributes.type + " already exists");
                    case WindowManagerGlobal.ADD_PERMISSION_DENIED:
                        throw new WindowManager.BadTokenException("Unable to add window "
                                + mWindow + " -- permission denied for window type "
                                + mWindowAttributes.type);
                    case WindowManagerGlobal.ADD_INVALID_DISPLAY:
                        throw new WindowManager.InvalidDisplayException("Unable to add window "
                                + mWindow + " -- the specified display can not be found");
                    case WindowManagerGlobal.ADD_INVALID_TYPE:
                        throw new WindowManager.InvalidDisplayException("Unable to add window "
                                + mWindow + " -- the specified window type "
                                + mWindowAttributes.type + " is not valid");
                }
                throw new RuntimeException(
                        "Unable to add window -- unknown error code " + res);
            }

			...

	}
```

在这里，View 也开始了测量、布局、绘制的三大流程。

之后，利用 `mWindowSession` 来添加 window ，`mWindowSession` 的类型是 IWindowSession ，它是一个 Binder 对象，其真正的实现类是 Session 。所以这是一个 IPC 的过程。这步具体的实现我们下面再看。

在添加完成后，根据返回值 res 来判断添加 window 是否成功。若不是 WindowManagerGlobal.ADD_OKAY 则说明添加失败了，抛出对应的异常。


Session
--------
### addToDisplay(IWindow window, int seq, WindowManager.LayoutParams attrs, int viewVisibility, int displayId, Rect outContentInsets, Rect outStableInsets, Rect outOutsets, InputChannel outInputChannel)

``` java
    @Override
    public int addToDisplay(IWindow window, int seq, WindowManager.LayoutParams attrs,
            int viewVisibility, int displayId, Rect outContentInsets, Rect outStableInsets,
            Rect outOutsets, InputChannel outInputChannel) {
        return mService.addWindow(this, window, seq, attrs, viewVisibility, displayId,
                outContentInsets, outStableInsets, outOutsets, outInputChannel);
    }
```

在 Session 中，发现添加 Window 的操作交给了 mService ，而 mService 其实就是 WindowManagerService 。终于来到了最终 boss 这里了，那我们直击要害吧！

WindowManagerService
--------------------
### addWindow(Session session, IWindow client, int seq, WindowManager.LayoutParams attrs, int viewVisibility, int displayId, Rect outContentInsets, Rect outStableInsets, Rect outOutsets, InputChannel outInputChannel)

``` java
    public int addWindow(Session session, IWindow client, int seq,
            WindowManager.LayoutParams attrs, int viewVisibility, int displayId,
            Rect outContentInsets, Rect outStableInsets, Rect outOutsets,
            InputChannel outInputChannel) {
        int[] appOp = new int[1];
        // 校验 window 的权限，如果不是 ADD_OKAY 就不通过
        int res = mPolicy.checkAddPermission(attrs, appOp);
        if (res != WindowManagerGlobal.ADD_OKAY) {
            return res;
        }
			
        // 初步校验一些参数，不通过就会返回错误的 res 值 
        // 比如检查子窗口，就要求父窗口必须已经存在等
        ...

        boolean addToken = false;
        // 拿到 layoutparams.token ，进行校验
        WindowToken token = mTokenMap.get(attrs.token);
        AppWindowToken atoken = null;
        boolean addToastWindowRequiresToken = false;

        // 校验 token 有效性， 如果 token 为空或不正确的话，那么直接返回 ADD_BAD_APP_TOKEN 等异常
        if (token == null) {
            ...
        }else if (...) {
            ...
        } else if (token.appWindowToken != null) {
            Slog.w(TAG_WM, "Non-null appWindowToken for system window of type=" + type);
            // It is not valid to use an app token with other system types; we will
            // instead make a new token for it (as if null had been passed in for the token).
            attrs.token = null;
            token = new WindowToken(this, null, -1, false);
            addToken = true;
        }

        // 为新窗口创建了新的 WindowState 对象
        WindowState win = new WindowState(this, session, client, token,
                    attachedWindow, appOp[0], seq, attrs, viewVisibility, displayContent);

        res = mPolicy.prepareAddWindowLw(win, attrs);
        if (res != WindowManagerGlobal.ADD_OKAY) {
            return res;
        }

        ...

        if (type == TYPE_INPUT_METHOD) {
            win.mGivenInsetsPending = true;
            mInputMethodWindow = win;
            addInputMethodWindowToListLocked(win);
            imMayMove = false;
        } else if (type == TYPE_INPUT_METHOD_DIALOG) {
            mInputMethodDialogs.add(win);
            // 将新的 WindowState 按显示次序插入到当前 DisplayContent 的 mWindows 列表中
            addWindowToListInOrderLocked(win, true);
            moveInputMethodDialogsLocked(findDesiredInputMethodWindowIndexLocked(true));
            imMayMove = false;
        } else {
            // 将新的 WindowState 按显示次序插入到当前 DisplayContent 的 mWindows 列表中
            addWindowToListInOrderLocked(win, true);
            if (type == TYPE_WALLPAPER) {
                mWallpaperControllerLocked.clearLastWallpaperTimeoutTime();
                displayContent.pendingLayoutChanges |= FINISH_LAYOUT_REDO_WALLPAPER;
            } else if ((attrs.flags&FLAG_SHOW_WALLPAPER) != 0) {
                displayContent.pendingLayoutChanges |= FINISH_LAYOUT_REDO_WALLPAPER;
            } else if (mWallpaperControllerLocked.isBelowWallpaperTarget(win)) {
                // If there is currently a wallpaper being shown, and
                // the base layer of the new window is below the current
                // layer of the target window, then adjust the wallpaper.
                // This is to avoid a new window being placed between the
                // wallpaper and its target.
                displayContent.pendingLayoutChanges |= FINISH_LAYOUT_REDO_WALLPAPER;
            }
        }

        // 根据窗口的排序结果，为 DisplayContent 的所有窗口分配最终的显示次序
        mLayersController.assignLayersLocked(displayContent.getWindowList());

        ...

        // 返回添加窗口的结果
        return res;
    }
```

在 WindowManagerService 中做的事情有很多，一开始利用 `mPolicy.checkAddPermission` 检查了权限，这里面可大有文章，利用 `type = WindowManager.LayoutParams.TYPE_TOAST` 来跳过权限显示悬浮窗的故事就来自于这里。想详细了解的同学请看[《Android 悬浮窗权限各机型各系统适配大全》](http://blog.csdn.net/self_study/article/details/52859790)。

然后就是校验了一些参数，比如 token 。token 是用来表示窗口的一个令牌，其实是一个 Binder 对象。只有符合条件的 token 才能被 WindowManagerService 通过并添加到应用上。

再然后就是创建了一个 WindowState 对象，利用这个对象按照显示次序插入 mWindows 列表中，最后就是依据排序来确定窗口的最终显示次序。并返回了 Window 添加的结果 res 。

到这，整个添加 Window 的过程就结束了。

Footer
======
Window 添加其实就是一个 IPC 的过程，而更新和删除 Window 也是如此，基本上步骤都是相似的。

接下来就顺便把 Window 更新和删除的流程都梳理一遍吧。

静静等待此系列第三篇出炉！

References
==========
* [《深入理解Android 卷III》第四章 深入理解WindowManagerService](http://blog.csdn.net/innost/article/details/47660193)
* [Android 悬浮窗权限各机型各系统适配大全](http://blog.csdn.net/self_study/article/details/52859790)
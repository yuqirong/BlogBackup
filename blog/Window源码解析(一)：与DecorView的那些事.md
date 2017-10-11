title: Window源码解析(一)：与DecorView的那些事
date: 2017-09-28 14:39:03
categories: Android Blog
tags: [Android,Window,源码解析]
---
注：本文解析的源码基于 API 25，部分内容来自于《Android开发艺术探索》。

Header
======
今天我们来讲讲 Window ，Window 代表着一个窗口。

比如在 Activity 中，我们可以设置自定义的视图 View ，其实 View 并不是直接附着在 Activity 上，而是 View 附着在 Window 上，Activity 又持有一个 Window 对象。可见，Window 是一个重要的角色，主要用来负责管理 View 的。而 Window 和 View 又是通过 ViewRootImpl 来建立联系的，这在之前的[《View的工作原理》](/2017/09/18/View%E7%9A%84%E5%B7%A5%E4%BD%9C%E5%8E%9F%E7%90%86/)中介绍过。

所以一个 Window 就对应着一个 View 和一个 ViewRootImpl 。

同理，Dialog 和 Toast 等的视图也都是附着在 Window 上。

除此之外，相信看过《Android开发艺术探索》的同学都知道。Window 有三种类型，分别对应着：

1. 应用 Window ，即 Activity 的 Window 。对应的 type 为1~99；
2. 子 Window ，比如 Dialog 的 Window ，子 Window 并不能单独存在，需要有父 Window 的支持。对应的 type 为1000~1999；
3. 系统 Window ，需要权限声明才可以创建，比如常用的 Toast 和状态栏等都是系统级别的 Window。对应的 type 为2000~2999；

这三种 Window 的区分方法就是依靠 WindowManager.LayoutParams 中的 type 来决定的。type 越大，Window 就越显示在层级顶部。

粗看有这么多知识点，所以我们确实有必要对 Window 好好深入了解一下。在这，我们先详细介绍一下 Window 和 Activity 的那些“纠葛”，然后再深入 Window 的内部机制。

初见Window
=========
Activity
--------
### attach(Context context, ActivityThread aThread, ...)

Window 第一次出现在 Activity 的视野中，是在 Activity 的 `attach` 方法中，具体代码如下：

``` java
    final void attach(Context context, ActivityThread aThread,
            Instrumentation instr, IBinder token, int ident,
            Application application, Intent intent, ActivityInfo info,
            CharSequence title, Activity parent, String id,
            NonConfigurationInstances lastNonConfigurationInstances,
            Configuration config, String referrer, IVoiceInteractor voiceInteractor,
            Window window) {
        attachBaseContext(context);

        mFragments.attachHost(null /*parent*/);

        // 创建 window 对象
        mWindow = new PhoneWindow(this, window);
        mWindow.setWindowControllerCallback(this);
        // 设置回调，用来回调接收触摸、按键等事件
        mWindow.setCallback(this);
        mWindow.setOnWindowDismissedCallback(this);
        mWindow.getLayoutInflater().setPrivateFactory(this);
        if (info.softInputMode != WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED) {
            mWindow.setSoftInputMode(info.softInputMode);
        }
        if (info.uiOptions != 0) {
            mWindow.setUiOptions(info.uiOptions);
        }
        
        ...

        // 设置窗口管理器，其实是创建了 WindowManagerImpl 对象
        // WindowManager 是接口，而 WindowManagerImpl 是 WindowManger 的实现类
        mWindow.setWindowManager(
                (WindowManager)context.getSystemService(Context.WINDOW_SERVICE),
                mToken, mComponent.flattenToString(),
                (info.flags & ActivityInfo.FLAG_HARDWARE_ACCELERATED) != 0);
        if (mParent != null) {
            mWindow.setContainer(mParent.getWindow());
        }
        mWindowManager = mWindow.getWindowManager();
        mCurrentConfig = config;
    }
```

在方法中创建了一个 PhoneWindow 对象，而 PhoneWindow 其实就是 Window 的具体实现类，Window 只是一个接口而已。之后设置了回调，这样当 Window 接收到触摸或者按键等事件后，会回调给 Activity 。

另外还给 Window 对象设置了窗口管理器，也就是我们经常用到的 WindowManager 。

WindowManager 是外界接触 Window 的入口，也就是说，想要对 Window 进行一些操作需要用过 WindowManager 来完成。

与DecorView的那些事
==================
在开头中说到，Window 是用来负责管理 View 的。

现在 Window 已经创建完毕了，那么到底什么时候与 View 发生了交集了呢？

我们需要深入到 `onCreate()` 中一个熟悉的方法： `setContentView(R.layout.activity_main)` 。

Activity
--------
### setContentView(@LayoutRes int layoutResID)

``` java
    public void setContentView(@LayoutRes int layoutResID) {
        // 这里 getWindow 得到的正是上面创建的 PhoneWindow 对象
        getWindow().setContentView(layoutResID);
        initWindowDecorActionBar();
    }
```

发现它调用的是 Window 中的同名方法。

接着到 PhoneWindow 中跟进，查看具体实现的逻辑。

PhoneWindow
------------
### setContentView(int layoutResID)

``` java
    @Override
    public void setContentView(int layoutResID) {
        // Note: FEATURE_CONTENT_TRANSITIONS may be set in the process of installing the window
        // decor, when theme attributes and the like are crystalized. Do not check the feature
        // before this happens.
		
		// mContentParent 是放置窗口内容的父 viewgroup ，可能是 decorView 本身，也有可能是它的子 viewgroup
		// 如果 mContentParent 是空的，那么就说明 decorView 是空的
        if (mContentParent == null) {
			// 创建 decorview
            installDecor();
        } else if (!hasFeature(FEATURE_CONTENT_TRANSITIONS)) {
            mContentParent.removeAllViews();
        }

        if (hasFeature(FEATURE_CONTENT_TRANSITIONS)) {
            final Scene newScene = Scene.getSceneForLayout(mContentParent, layoutResID,
                    getContext());
            transitionTo(newScene);
        } else {
			// 将 layout 布局加入到 mContentParent 中并去解析 layout xml 文件
            mLayoutInflater.inflate(layoutResID, mContentParent);
        }
        mContentParent.requestApplyInsets();
		// 通知 activity 窗口内容已经发生变化了
        final Callback cb = getCallback();
        if (cb != null && !isDestroyed()) {
            cb.onContentChanged();
        }
        mContentParentExplicitlySet = true;
    }
```

在 `setContentView(int layoutResID)` 中，一开始判断了 mContentParent 。mContentParent 其实就是我们设置的 contentView 的父视图。

关于 mContentParent ，在 PhoneWindow 中有注释：

	// This is the view in which the window contents are placed. It is either
    // mDecor itself, or a child of mDecor where the contents go.

意思就是说，当我们不需要 titlebar 的时候，mContentParent 其实就和 DecorView 一样了；有 titlebar 的时候，DecorView 的内容就分为了 titlebar 和 mContentParent 。

所以如果 mContentParent 为空，那么可以说明还没有创建过 DecorView 。

我们总结一下，在 `setContentView(int layoutResID)` 中主要就是这三件事：

1. 创建 DecorView 视图对象；
2. 将自定义的视图 layout_main.xml 进行解析并添加到 mContentParent 中；
3. 去通知 activity 窗口视图已经改变了，进行相关操作；

我们去 `installDecor()` 中看看究竟怎么创建 DecorView 的。

### installDecor()

``` java
    private void installDecor() {
        mForceDecorInstall = false;
        if (mDecor == null) {
            // 如果 decorview 为空，调用 generateDecor 来创建 decorview
            mDecor = generateDecor(-1);
            mDecor.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            mDecor.setIsRootNamespace(true);
            if (!mInvalidatePanelMenuPosted && mInvalidatePanelMenuFeatures != 0) {
                mDecor.postOnAnimation(mInvalidatePanelMenuRunnable);
            }
        } else {
            mDecor.setWindow(this);
        }
        if (mContentParent == null) {
            // 创建 mContentParent ，也就是 contentView 的父视图
            mContentParent = generateLayout(mDecor);

            // Set up decor part of UI to ignore fitsSystemWindows if appropriate.
            mDecor.makeOptionalFitsSystemWindows();

            final DecorContentParent decorContentParent = (DecorContentParent) mDecor.findViewById(
                    R.id.decor_content_parent);

            ...

            }
        }
    }
```

在 `installDecor()` 中，调用了 `generateDecor()` 方法来创建 DecorView；

之后又调用 `generateLayout(mDecor)` 来创建 mContentParent 。

### generateDecor(int featureId)

``` java
    protected DecorView generateDecor(int featureId) {
        // System process doesn't have application context and in that case we need to directly use
        // the context we have. Otherwise we want the application context, so we don't cling to the
        // activity.

        // 得到 context 上下文
        Context context;
        if (mUseDecorContext) {
            Context applicationContext = getContext().getApplicationContext();
            if (applicationContext == null) {
                context = getContext();
            } else {
                context = new DecorContext(applicationContext, getContext().getResources());
                if (mTheme != -1) {
                    context.setTheme(mTheme);
                }
            }
        } else {
            context = getContext();
        }
        // 创建 DecorView 对象
        return new DecorView(context, featureId, this, getAttributes());
    }
```

`generateDecor(int featureId)` 方法比较简单，之前初始化了一下 context ，然后直接 new 了一个 DecorView 完事！

### generateLayout(DecorView decor)

``` java
    protected ViewGroup generateLayout(DecorView decor) {
        // 应用当前的主题，比如设置一些 window 属性等
        ... 

        // 根据主题设置去选择 layoutResource
        // 这个 layoutResource 也就是 DecorView 的子 View 的布局
        int layoutResource;
        int features = getLocalFeatures();

        ...

        else if ((features & (1 << FEATURE_NO_TITLE)) == 0) {
            // If no other features and not embedded, only need a title.
            // If the window is floating, we need a dialog layout
            if (mIsFloating) {
                TypedValue res = new TypedValue();
                getContext().getTheme().resolveAttribute(
                        R.attr.dialogTitleDecorLayout, res, true);
                layoutResource = res.resourceId;
            } else if ((features & (1 << FEATURE_ACTION_BAR)) != 0) {
                layoutResource = a.getResourceId(
                        R.styleable.Window_windowActionBarFullscreenDecorLayout,
                        R.layout.screen_action_bar);
            } else {
                // 比较常见的就是这种布局
                layoutResource = R.layout.screen_title;
            }
            // System.out.println("Title!");
        } else if ((features & (1 << FEATURE_ACTION_MODE_OVERLAY)) != 0) {
            layoutResource = R.layout.screen_simple_overlay_action_mode;
        } else {
            // Embedded, so no decoration is needed.
            layoutResource = R.layout.screen_simple;
            // System.out.println("Simple!");
        }

        mDecor.startChanging();
        // 这个方法里将上面 layoutResource 的布局转换并添加到 DecorVew 中
        mDecor.onResourcesLoaded(mLayoutInflater, layoutResource);
        // 得到 contentParent（id = android.R.id.content）, 也就是我们 setContentView 的父视图
        ViewGroup contentParent = (ViewGroup)findViewById(ID_ANDROID_CONTENT);
        if (contentParent == null) {
            throw new RuntimeException("Window couldn't find content container view");
        }

        if ((features & (1 << FEATURE_INDETERMINATE_PROGRESS)) != 0) {
            ProgressBar progress = getCircularProgressBar(false);
            if (progress != null) {
                progress.setIndeterminate(true);
            }
        }

        if ((features & (1 << FEATURE_SWIPE_TO_DISMISS)) != 0) {
            registerSwipeCallbacks();
        }

        // Remaining setup -- of background and title -- that only applies
        // to top-level windows.
        // 背景设置和标题设置
        if (getContainer() == null) {
            final Drawable background;
            if (mBackgroundResource != 0) {
                background = getContext().getDrawable(mBackgroundResource);
            } else {
                background = mBackgroundDrawable;
            }
            mDecor.setWindowBackground(background);

            final Drawable frame;
            if (mFrameResource != 0) {
                frame = getContext().getDrawable(mFrameResource);
            } else {
                frame = null;
            }
            mDecor.setWindowFrame(frame);

            mDecor.setElevation(mElevation);
            mDecor.setClipToOutline(mClipToOutline);

            if (mTitle != null) {
                setTitle(mTitle);
            }

            if (mTitleColor == 0) {
                mTitleColor = mTextColor;
            }
            setTitleColor(mTitleColor);
        }

        mDecor.finishChanging();
        
        return contentParent;
    }
```

这个方法中大致的逻辑就是，根据主题的设置情况来选择 DecorView 子 View 的 layoutResource 。在这，我们就看看最常用的一种布局 R.layout.screen_title (位于 /frameworks/base/core/res/res/layout/screen_title.xml ):

``` xml
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:orientation="vertical"
    android:fitsSystemWindows="true">
    <!-- Popout bar for action modes -->
    <ViewStub android:id="@+id/action_mode_bar_stub"
              android:inflatedId="@+id/action_mode_bar"
              android:layout="@layout/action_mode_bar"
              android:layout_width="match_parent"
              android:layout_height="wrap_content"
              android:theme="?attr/actionBarTheme" />
    <FrameLayout
        android:layout_width="match_parent" 
        android:layout_height="?android:attr/windowTitleSize"
        style="?android:attr/windowTitleBackgroundStyle">
        <TextView android:id="@android:id/title" 
            style="?android:attr/windowTitleStyle"
            android:background="@null"
            android:fadingEdge="horizontal"
            android:gravity="center_vertical"
            android:layout_width="match_parent"
            android:layout_height="match_parent" />
    </FrameLayout>
    <FrameLayout android:id="@android:id/content"
        android:layout_width="match_parent" 
        android:layout_height="0dip"
        android:layout_weight="1"
        android:foregroundGravity="fill_horizontal|top"
        android:foreground="?android:attr/windowContentOverlay" />
</LinearLayout>
```

我们可以看到，DecorView 的子 View 其实是一个 LinearLayout ，而 LinearLayout 中有分为 titlebar 和 id 为 android:id/content 的 FrameLayout（其实就是 mContentParent）。

之后将这个视图创建出来并添加到 DecorView 中。

具体的代码可以深入 DecorView 的 `onResourcesLoaded(LayoutInflater inflater, int layoutResource)` 中去看：

``` java
    void onResourcesLoaded(LayoutInflater inflater, int layoutResource) {
        mStackId = getStackId();

        if (mBackdropFrameRenderer != null) {
            loadBackgroundDrawablesIfNeeded();
            mBackdropFrameRenderer.onResourcesLoaded(
                    this, mResizingBackgroundDrawable, mCaptionBackgroundDrawable,
                    mUserCaptionBackgroundDrawable, getCurrentColor(mStatusColorViewState),
                    getCurrentColor(mNavigationColorViewState));
        }

        mDecorCaptionView = createDecorCaptionView(inflater);
        // 解析之前选择出来的 layoutResource ，该 root 也就是 DecorView 的直接子 View
        final View root = inflater.inflate(layoutResource, null);
        if (mDecorCaptionView != null) {
            if (mDecorCaptionView.getParent() == null) {
                addView(mDecorCaptionView,
                        new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));
            }
            mDecorCaptionView.addView(root,
                    new ViewGroup.MarginLayoutParams(MATCH_PARENT, MATCH_PARENT));
        } else {
            // Put it below the color views.
            // 将 root 视图添加到 DecorView 中
            addView(root, 0, new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));
        }
        // 可以看出成员变量 mContentRoot 就是 DecorView 的直接子 View
        // 也就是 mContentParent 的父视图
        mContentRoot = (ViewGroup) root;
        initializeElevation();
    }
```

看到这，我们可以画一张图出来了，把 PhoneWindow 、DecorView 和 mContentParent 都理清楚：

![View层级](/uploads/20170928/20170928102356.jpg) 

然后进行标题设置之类的工作。最后得到并返回 mContentParent 。

到了这里，基本上把 Window 、DecorView 和 Activity 三者之间的关系整理清楚了，但是事情并没有结束。这时候的 DecorView 并没有真正添加到 Window 上去，只是创建出对象了并解析了视图而已。DecorView 还没有被 WindowManager 识别，Window 也还无法接受外界的输入信息。

那么，到底 DecorView 是什么时候附着到 Window 上去的？

这个答案需要我们到 ActivityThread 的 `handleResumeActivity()` 中找找了。回调  Activity 的 `onResume()` 生命周期后，又调用了 Activity 的 `makeVisible()` 方法。

Activity
---------
### makeVisible()

``` java
    void makeVisible() {
        if (!mWindowAdded) {
            // WindowManager 是 ViewManager 的实现类
            ViewManager wm = getWindowManager();
            // 将 decorview 添加到 window 中
            wm.addView(mDecor, getWindow().getAttributes());
            mWindowAdded = true;
        }
        // 设置 decorview 可见
        mDecor.setVisibility(View.VISIBLE);
    }
```

走完这步，DecorView 才完成添加和显示出来，Activity 的视图才能被用户看到。

整个 Window 创建的流程也结束了。

Footer
======
Window 和 Decor 的“爱恨情仇”到这里就告一段落了，但是 Window 的内部机制我们还可以好好叙一叙。

注意到上面 WindowManager 的 `addView` 方法了吧？

Window 是怎么添加上去的，究竟在这里面发生了什么事呢？

只能留到下一篇再详细讲讲了。

bye bye !

References
==========
* [结合源码，探索Android中的Window与DecorView](https://mp.weixin.qq.com/s/NZ1GFkEn4UGNljYPVpdfhw)
title: 让你的ListView更炫酷，实现侧滑删除效果
date: 2015-12-13 23:54:48
categories: Android Blog
tags: [Android,自定义View]
---
又到了更新博客的时间了，今天给大家带来的是ListView侧滑出现删除等按钮的效果。相信大家在平时玩app的时候都接触过这种效果吧。比如说QQ聊天列表侧滑就会出现“置顶”、“标为已读”、“删除”等按钮。这篇博文将用ViewDragHelper这个神器来实现侧滑效果。友情链接一下之前写的博文使用ViewDragHelper来实现侧滑菜单的，[点击此处跳转](/2015/11/04/史上最简单粗暴实现侧滑菜单/)。如果你对ViewDragHelper不熟悉，你可以去看看[鸿洋_](http://my.csdn.net/lmj623565791)的[《Android ViewDragHelper完全解析 自定义ViewGroup神器》](http://blog.csdn.net/lmj623565791/article/details/46858663)。

好了，话说的那么多，先来看看我们实现的效果图吧：

![这里填写图片描述](/uploads/20151213/20151213140251.gif)

可以看出来，我们实现的和QQ的效果相差无几。下面就是源码时间了。

先来看一下ListView的item的`slip_item_layout.xml`：

``` xml
<?xml version="1.0" encoding="utf-8"?>
<com.yuqirong.swipelistview.view.SwipeListLayout xmlns:android="http://schemas.android.com/apk/res/android"
android:id="@+id/sll_main"
android:layout_width="match_parent"
android:layout_height="wrap_content" >

    <LinearLayout
        android:layout_width="wrap_content"
        android:layout_height="match_parent"
        android:layout_gravity="center_vertical"
        android:orientation="horizontal" >

        <TextView
            android:id="@+id/tv_top"
            android:layout_width="80dp"
            android:layout_height="match_parent"
            android:background="#66ff0000"
            android:gravity="center"
            android:text="置顶" />

        <TextView
            android:id="@+id/tv_delete"
            android:layout_width="80dp"
            android:layout_height="match_parent"
            android:background="#330000ff"
            android:gravity="center"
            android:text="删除" />
    </LinearLayout>

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:layout_gravity="center_vertical"
        android:background="#66ffffff"
        android:orientation="horizontal" >

        <ImageView
            android:layout_width="wrap_content"
            android:layout_height="match_parent"
            android:layout_margin="10dp"
            android:src="@drawable/head_1" />

        <TextView
            android:id="@+id/tv_name"
            android:layout_width="wrap_content"
            android:layout_height="match_parent"
            android:layout_marginLeft="10dp"
            android:gravity="center_vertical"
            android:text="hello" />
    </LinearLayout>

</com.yuqirong.swipelistview.view.SwipeListLayout>
```

我们可以看出，要先把侧滑出的按钮布局放在SwipeListLayout的第一层，而item的布局放在第二层。还有一点要注意的是，侧滑出的按钮如果有两个或两个以上，那么必须用ViewGroup作为父布局。要整体保持SwipeListLayout的直接子View为2个。

而activity的布局文件里就是一个ListView，这里就不再给出了。

下面我们直接来看看SwipeListLayout的内容：

``` java
public SwipeListLayout(Context context) {
	this(context, null);
}

public SwipeListLayout(Context context, AttributeSet attrs) {
	super(context, attrs);
	mDragHelper = ViewDragHelper.create(this, callback);
}

// ViewDragHelper的回调
Callback callback = new Callback() {

	@Override
	public boolean tryCaptureView(View view, int arg1) {
		return view == itemView;
	}

	@Override
	public int clampViewPositionHorizontal(View child, int left, int dx) {
		if (child == itemView) {
			if (left > 0) {
				return 0;
			} else {
				left = Math.max(left, -hiddenViewWidth);
				return left;
			}
		}
		return 0;
	}

	@Override
	public int getViewHorizontalDragRange(View child) {
		return hiddenViewWidth;
	}

	@Override
	public void onViewPositionChanged(View changedView, int left, int top,
			int dx, int dy) {
		if (itemView == changedView) {
			hiddenView.offsetLeftAndRight(dx);
		}
		// 有时候滑动很快的话 会出现隐藏按钮的linearlayout没有绘制的问题
		// 为了确保绘制成功 调用 invalidate
		invalidate();
	}

	public void onViewReleased(View releasedChild, float xvel, float yvel) {
		// 向右滑xvel为正 向左滑xvel为负
		if (releasedChild == itemView) {
			if (xvel == 0
					&& Math.abs(itemView.getLeft()) > hiddenViewWidth / 2.0f) {
				open(smooth);
			} else if (xvel < 0) {
				open(smooth);
			} else {
				close(smooth);
			}
		}
	}

};
```

我们主要来看callback，首先在`tryCaptureView(View view, int arg1)`里设置了只有是itemView的时候才能被捕获，也就是说当你去滑动“删除”、“置顶”等按钮的时候，侧滑按钮是不会被关闭的，因为根本就没捕获。(当然你也可以设置都捕获，那样的话下面的逻辑要调整了)，剩余的几个函数中的逻辑较为简单，在`onView
Released(View releasedChild, float xvel, float yvel)`也是判断了当手指抬起时itemView所处的位置。如果向左滑或者停止滑动时按钮已经显示出1/2的宽度，则打开；其余情况下都将关闭按钮。

以下分别是close()和open()的方法：

``` java
/**
 * 侧滑关闭
 * 
 * @param smooth
 *            为true则有平滑的过渡动画
 */
private void close(boolean smooth) {
	preStatus = status;
	status = Status.Close;
	if (smooth) {
		if (mDragHelper.smoothSlideViewTo(itemView, 0, 0)) {
			if (listener != null) {
				Log.i(TAG, "start close animation");
				listener.onStartCloseAnimation();
			}
			ViewCompat.postInvalidateOnAnimation(this);
		}
	} else {
		layout(status);
	}
	if (listener != null && preStatus == Status.Open) {
		Log.i(TAG, "close");
		listener.onStatusChanged(status);
	}
}

/**
 * 侧滑打开
 * 
 * @param smooth
 *            为true则有平滑的过渡动画
 */
private void open(boolean smooth) {
	preStatus = status;
	status = Status.Open;
	if (smooth) {
		if (mDragHelper.smoothSlideViewTo(itemView, -hiddenViewWidth, 0)) {
			if (listener != null) {
				Log.i(TAG, "start open animation");
				listener.onStartOpenAnimation();
			}
			ViewCompat.postInvalidateOnAnimation(this);
		}
	} else {
		layout(status);
	}
	if (listener != null && preStatus == Status.Close) {
		Log.i(TAG, "open");
		listener.onStatusChanged(status);
	}
}
```

SwipeListLayout大致的代码就这些，相信对于熟悉ViewDragHelper的同学们来说应该是不成问题的。其实整体的逻辑和之前用ViewDragHelper来实现侧滑菜单大同小异。

顺便下面贴出SwipeListLayout的全部代码：

``` java
/**
 * 侧滑Layout
 */
public class SwipeListLayout extends FrameLayout {

	private View hiddenView;
	private View itemView;
	private int hiddenViewWidth;
	private ViewDragHelper mDragHelper;
	private int hiddenViewHeight;
	private int itemWidth;
	private int itemHeight;
	private OnSwipeStatusListener listener;
	private Status status = Status.Close;
	private boolean smooth = true;

	public static final String TAG = "SlipListLayout";

	// 状态
	public enum Status {
		Open, Close
	}

	/**
	 * 设置侧滑状态
	 * 
	 * @param status
	 *            状态 Open or Close
	 * @param smooth
	 *            若为true则有过渡动画，否则没有
	 */
	public void setStatus(Status status, boolean smooth) {
		this.status = status;
		if (status == Status.Open) {
			open(smooth);
		} else {
			close(smooth);
		}
	}

	public void setOnSwipeStatusListener(OnSwipeStatusListener listener) {
		this.listener = listener;
	}

	/**
	 * 是否设置过渡动画
	 * 
	 * @param smooth
	 */
	public void setSmooth(boolean smooth) {
		this.smooth = smooth;
	}

	public interface OnSwipeStatusListener {

		/**
		 * 当状态改变时回调
		 * 
		 * @param status
		 */
		void onStatusChanged(Status status);

		/**
		 * 开始执行Open动画
		 */
		void onStartCloseAnimation();

		/**
		 * 开始执行Close动画
		 */
		void onStartOpenAnimation();

	}

	public SwipeListLayout(Context context) {
		this(context, null);
	}

	public SwipeListLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
		mDragHelper = ViewDragHelper.create(this, callback);
	}

	// ViewDragHelper的回调
	Callback callback = new Callback() {

		@Override
		public boolean tryCaptureView(View view, int arg1) {
			return view == itemView;
		}

		@Override
		public int clampViewPositionHorizontal(View child, int left, int dx) {
			if (child == itemView) {
				if (left > 0) {
					return 0;
				} else {
					left = Math.max(left, -hiddenViewWidth);
					return left;
				}
			}
			return 0;
		}

		@Override
		public int getViewHorizontalDragRange(View child) {
			return hiddenViewWidth;
		}

		@Override
		public void onViewPositionChanged(View changedView, int left, int top,
				int dx, int dy) {
			if (itemView == changedView) {
				hiddenView.offsetLeftAndRight(dx);
			}
			// 有时候滑动很快的话 会出现隐藏按钮的linearlayout没有绘制的问题
			// 为了确保绘制成功 调用 invalidate
			invalidate();
		}

		public void onViewReleased(View releasedChild, float xvel, float yvel) {
			// 向右滑xvel为正 向左滑xvel为负
			if (releasedChild == itemView) {
				if (xvel == 0
						&& Math.abs(itemView.getLeft()) > hiddenViewWidth / 2.0f) {
					open(smooth);
				} else if (xvel < 0) {
					open(smooth);
				} else {
					close(smooth);
				}
			}
		}

	};
	private Status preStatus = Status.Close;

	/**
	 * 侧滑关闭
	 * 
	 * @param smooth
	 *            为true则有平滑的过渡动画
	 */
	private void close(boolean smooth) {
		preStatus = status;
		status = Status.Close;
		if (smooth) {
			if (mDragHelper.smoothSlideViewTo(itemView, 0, 0)) {
				if (listener != null) {
					Log.i(TAG, "start close animation");
					listener.onStartCloseAnimation();
				}
				ViewCompat.postInvalidateOnAnimation(this);
			}
		} else {
			layout(status);
		}
		if (listener != null && preStatus == Status.Open) {
			Log.i(TAG, "close");
			listener.onStatusChanged(status);
		}
	}

	/**
	 * 
	 * @param status
	 */
	private void layout(Status status) {
		if (status == Status.Close) {
			hiddenView.layout(itemWidth, 0, itemWidth + hiddenViewWidth,
					itemHeight);
			itemView.layout(0, 0, itemWidth, itemHeight);
		} else {
			hiddenView.layout(itemWidth - hiddenViewWidth, 0, itemWidth,
					itemHeight);
			itemView.layout(-hiddenViewWidth, 0, itemWidth - hiddenViewWidth,
					itemHeight);
		}
	}

	/**
	 * 侧滑打开
	 * 
	 * @param smooth
	 *            为true则有平滑的过渡动画
	 */
	private void open(boolean smooth) {
		preStatus = status;
		status = Status.Open;
		if (smooth) {
			if (mDragHelper.smoothSlideViewTo(itemView, -hiddenViewWidth, 0)) {
				if (listener != null) {
					Log.i(TAG, "start open animation");
					listener.onStartOpenAnimation();
				}
				ViewCompat.postInvalidateOnAnimation(this);
			}
		} else {
			layout(status);
		}
		if (listener != null && preStatus == Status.Close) {
			Log.i(TAG, "open");
			listener.onStatusChanged(status);
		}
	}

	@Override
	public void computeScroll() {
		super.computeScroll();
		// 开始执行动画
		if (mDragHelper.continueSettling(true)) {
			ViewCompat.postInvalidateOnAnimation(this);
		}
	}

	// 让ViewDragHelper来处理触摸事件
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		final int action = ev.getAction();
		if (action == MotionEvent.ACTION_CANCEL) {
			mDragHelper.cancel();
			return false;
		}
		return mDragHelper.shouldInterceptTouchEvent(ev);
	}

	// 让ViewDragHelper来处理触摸事件
	public boolean onTouchEvent(MotionEvent event) {
		mDragHelper.processTouchEvent(event);
		return true;
	};

	@Override
	protected void onFinishInflate() {
		super.onFinishInflate();
		hiddenView = getChildAt(0); // 得到隐藏按钮的linearlayout
		itemView = getChildAt(1); // 得到最上层的linearlayout
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		// 测量子View的长和宽
		itemWidth = itemView.getMeasuredWidth();
		itemHeight = itemView.getMeasuredHeight();
		hiddenViewWidth = hiddenView.getMeasuredWidth();
		hiddenViewHeight = hiddenView.getMeasuredHeight();
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right,
			int bottom) {
		layout(Status.Close);
	}

}
```

最后，提供SwipeListLayout的源码下载：

[SwipeListView.rar](/uploads/20151213/SwipeListView.rar)

GitHub：

[SwipeListView](https://github.com/yuqirong/SwipeListView)

~have fun!~
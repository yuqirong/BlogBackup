title: 《Android群英传》笔记(上)
date: 2016-02-28 20:20:04
categories: Book Note
tags: [Android,《Android群英传》]
---
第三章：Android控件架构与自定义控件详解
=======

3.1 Android控件架构
--------

控件分为两类：View和ViewGroup，通过ViewGroup整个界面形成一个树形结构，并且ViewGroup负责对子View的测量与绘制以及传递交互事件。通常在Activity中使用的findViewById()方法，就是在控件树中以树的深度优先遍历来查找对应元素。在每颗控件树的顶部，都有一个ViewParent对象，这就是整棵树的控制核心，所有的交互管理事件都由它来统一调度和分配。

![这里写图片描述](/uploads/20160228/20160228230641.png)

如上图所示，每个Activity都包含一个Window对象，在Android中Window对象通常由PhoneWindow来实现。PhoneWindow对象又将一个DecorView设置为整个应用的根View。DecorView作为了窗口界面的顶层视图，封装了一些窗口操作的通用方法。可以说，DecorView将要显示的具体内容呈现在了PhoneWindow上，这里所有View的监听事件，都通过WindowManagerService来接收，并通过Activity对象来回调onClickListener。DecorView在显示上分为TitleView和ContentView两部分。ContentView是一个ID为content的FrameLayout，activity_main.xml就是设置在这样一个FrameLayout里。可以通过如下代码获得ContentView：

	FrameLayout content = (FrameLayout)findViewById(android.R.id.content);

![这里写图片描述](/uploads/20160228/20160228232837.png)

而在代码中，当程序在onCreate()方法中调用setContentView()方法后，ActivityManagerService会回调onResume()方法，此时系统才会把整个DecorView添加到PhoneWindow中，并让其显示出来，从而最终完成界面的绘制。

3.2 View的测量
--------

View的测量在onMeasure中进行，系统提供了MeasureSpec类，是一个32位的int值，其高2位为测量模式，低30位为测量的大小。测量模式有以下三种：

* EXACTLY：精确模式，当控件指定精确值（例如android:layout\_width="50dp"）或者指定为match\_parent属性时系统使用该模式。

* AT_MOST：最大值模式，指定wrap\_content时系统使用该属性。控件大小一般随着控件的子空间或内容的变化而变化，此时控件的尺寸只要不超过父控件允许的最大尺寸即可。View类默认只支持EXACTLY，如果想使用wrap\_content需自己在onMeasure中实现。

* UNSPECIFIED：自定义模式，View想多大就多大，通常在绘制自定义View的时候才使用。

下面是onMeasure的示例代码：

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
	    int widthMode = MeasureSpec.getMode(widthMeasureSpec);// 获取宽度模式
	    int widthSize = MeasureSpec.getSize(widthMeasureSpec);// 获取宽度值
	    int width = 0;
	    if (widthMode == MeasureSpec.EXACTLY) {
	        width = widthSize;
	    } else {
	        width = 200;// 自定义的默认wrap_content值
	        if (widthMode == MeasureSpec.AT_MOST) {
	                width = Math.min(widthSize, width);
	        }
	
	    }
	    int heightMode = MeasureSpec.getMode(heightMeasureSpec);// 获取高度模式
	    int heightSize = MeasureSpec.getSize(heightMeasureSpec);// 获取高度值
	    int height = 0;
	    if (heightMode == MeasureSpec.EXACTLY) {
	        height = heightSize;
	    } else {
	        height = 200;// 自定义的默认wrap_content值
	        if (heightMode == MeasureSpec.AT_MOST) {
	            height = Math.min(heightSize, height);
	        }
	    }
	    setMeasuredDimension(width, height);// 最终将测量的值传入该方法完成测量
	}

3.3 View的绘制
--------

View的绘制是通过onDraw()方法实现的，具体是通过对onDraw()方法中Canvas参数操作执行绘图。在其他地方，则需要自己创建Canvas对象，创建时需传入一个bitmap对象，这个过程我们称之为装载画布。bitmap是用来存储所有绘制在Canvas上的像素信息，当你通过这种方式创建了Canvas对象后，后面调用所有的Canvas.drawXXX方法都发生在这个bitmap上。

3.4 ViewGroup的测量
--------

当ViewGroup的大小为wrap\_content时，它就会遍历所有子View，以便获得所有子View的大小，从而来决定自身的大小，而在其他模式下则通过指定值来设置自身的大小。

然后当子View测量完毕以后，ViewGroup会执行它的Layout方法，同样是遍历子View并调用其Layout方法来确定布局位置。

在自定义ViewGroup时，通常会重写onLayout()方法来控制子View显示位置，若需支持wrap_content还需重写onMeasure()方法，这点与View是相同的。

3.5 ViewGroup的绘制
--------

ViewGroup通常情况下不需要绘制，如果不是指定了ViewGroup的背景颜色，那么ViewGroup的onDraw()方法都不会被调用。但是ViewGroup会调用dispatchDraw()方法来绘制其子View，过程同样是遍历子View，并调用子View的绘制方法来完成绘制工作。

3.6 自定义View
--------

自定义View时有一些比较重要的回调方法如下：

* onFinishInflate();//从xml加载组件后回调
* onSizeChanged();//组件大小改变时回调
* onMeasure();//回调该方法进行测量
* onLayout();//回调该方法来确定显示的位置
* onTouchEvent();//监听到触摸事件回调

通常情况下，有以下三种方法来实现自定义的控件：

* 对现有控件进行拓展
* 通过组合来实现新的控件
* 重写View来实现全新的控件

PS ： LinearGradient也称作线性渲染，LinearGradient的作用是实现某一区域内颜色的线性渐变效果。构造函数有两个，分别如下：

`public LinearGradient(float x0, float y0, float x1, float y1, int color0, int color1, Shader.TileMode tile)`
 
其中，参数x0表示渐变的起始点x坐标；参数y0表示渐变的起始点y坐标；参数x1表示渐变的终点x坐标；参数y1表示渐变的终点y坐标　；color0表示渐变开始颜色；color1表示渐变结束颜色；参数tile表示平铺方式。
 
Shader.TileMode有3种参数可供选择，分别为CLAMP、REPEAT和MIRROR：
 
* CLAMP的作用是如果渲染器超出原始边界范围，则会复制边缘颜色对超出范围的区域进行着色
 
* REPEAT的作用是在横向和纵向上以平铺的形式重复渲染位图
 
* MIRROR的作用是在横向和纵向上以镜像的方式重复渲染位图

`public LinearGradient (float x0, float y0, float x1, float y1, int[] colors, float[] positions, Shader.TileMode tile)`
 
其中，参数x0表示渐变的起始点x坐标；参数y0表示渐变的起始点y坐标；参数x1表示渐变的终点x坐标；参数y1表示渐变的终点y坐标；参数colors表示渐变的颜色数组；参数positions用来定义每个颜色处于的渐变相对位置；参数tile表示平铺方式。通常，参数positions设为null，表示颜色数组按顺序均匀的分布。

3.7 自定义ViewGroup
-----------

自定义ViewGroup通常需要重写onMeasure()方法来对子View进行测量，重写onLayout()方法来确定子View的位置，重写onTouchEvent()方法增加响应事件。

`onMeasure(int widthMeasureSpec, int heightMeasureSpec)`方法：

	@Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            measureChild(getChildAt(i), widthMeasureSpec, heightMeasureSpec);
        }
    }


3.8 事件拦截机制分析
-----------

本章较为浅显的分析了下事件传递的机制。当ViewGroup接收到事件，通过调用dispatchTouchEvent()，由这个方法再调用onInterceptTouchEvent()方法来判断是否要拦截事件，如果返回true则拦截将事件交给自己的onTouchEvent处理，返回false则继续向下传递。当View在接受到事件时，通过调用dispatchTouchEvent()，由此方法再调用onTouchEvent方法，如果返回true则拦截事件自己处理，如果返回false则将事件向上传递回ViewGroup并且调用其onTouchEvent方法继续做判断。

第四章：ListView使用技巧
=======

4.1 ListView常用优化技巧
--------

* 使用ViewHolder模式提高效率
* 设置项目间分隔线：
	
		android:divider="@android:color/darker_gray"
		android:dividerHeight="10dp"

     特殊情况下，以下代码可以设置分割线为透明：
	
		android:divider="@null"

* 隐藏ListView的滚动条：
		
		android:scrollbars="none"

* 取消ListView的点击效果：

		android:listSelector="#00000000"

	也可以用Android自带的透明色来实现这个效果：

		android:listSelector="@android:color/transparent"

* 设置ListView需要显示在第几项：

		listView.setSelection(N);

	其中N就是需要显示的第N个Item。

	除此之外，还可以使用如下代码来实现平滑移动：

		mListView.smoothScrollBy(distance,duration);
		mListView.smoothScrollByOffset(offset);
		mListView.smoothScrollToPosition(index);

* 动态修改ListView：

		mAdapter.notifyDataSetChanged();

* 遍历ListView中的所有Item：

		for(int i=0;i<mListView.getChildCount();i++){
			View view = mListView.getChildAt(i);
		}

* 处理空ListView：

		<RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
		    android:layout_width="match_parent"
		    android:layout_height="match_parent">
			
			<ListView
	        android:id="@+id/listView"
	        android:layout_width="match_parent"
	        android:layout_height="match_parent" />
	
		    <ImageView
		        android:id="@+id/imageView"
		        android:layout_width="match_parent"
		        android:layout_height="match_parent"
		        android:src="@drawable/empty_view" />

		</RelativeLayout>

	在代码中，我们通过以下方式给ListView设置空数据时要显示的布局，代码如下：

		ListView listView = (ListView)findViewById(R.id.listView);
		listView.setEmptyView(findViewById(R.id.imageView));

* ListView滑动监听：一种是通过OnTouchListener来实现监听，另外一种是使用OnScrollListener来实现监听。

	OnScrollListener中有两个回调方法——onScrollStateChanged()和onScroll()

	 	@Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {
            switch(scrollState){
	            case SCROLL_STATE_FLING:
	          	// TODO    
	            break;
	            case SCROLL_STATE_IDLE:
	            // TODO 
	            break;
	            case SCROLL_STATE_TOUCH_SCROLL:
	            // TODO 
	            break;
            }
        }

	scrollState有以下三种模式：

	* OnScrollListener.SCROLL\_STATE\_IDLE ： 滚动停止时；
	* OnScrollListener.SCROLL\_STATE\_TOUCH\_SCROLL ： 正在滚动时；
	* OnScrollListener.SCROLL\_STATE\_FLING ： 手指抛动时，即手指用力滑动，在离开后ListView由于惯性继续滑动的状态；
	
	当手指没有做手指抛动的状态时，这个方法只会回调2次，否则会回调3次。

			@Override
	        public void onScroll(AbsListView view, int firstVisibleItem,
	                int visibleItemCount, int totalItemCount) {
	            // TODO Auto-generated method stub
	        }

	* firstVisibleItem ： 当前能看见的第一个Item的ID（从0开始）
	
	* visibleItemCount ： 当前能看见的Item的总数
	
	* totalItemCount ： 整个ListView的Item总数

	判断是否滚动到最后一行：

			if(firstVisibleItem + visibleItemCount == totalItemCount && totalItemCount > 0){
				// 滚动到最后一行
			} 

	再比如，可以通过如下代码来判断滚动的方向：

			if(firstVisibleItem > lastVisibleItemPosition){
				// 上滑
			}else if(firstVisibleItem < lastVisibleItemPosition){
				// 下滑
			}
			lastVisibleItemPosition = firstVisibleItem;

	当然，ListView也给我们提供了一些封装的方法来获得当前可视的Item的位置等信息：

			// 获取可视区域内最后一个Item的id
			mListView.getLastVisiblePosition()；
			// 获取可视区域内第一个Item的id
			mListView。getFirstVisiblePosition();

4.2 ListView常用拓展
--------
1. 具有弹性的ListView：

		@Override
	    protected boolean overScrollBy(int deltaX, int deltaY, int scrollX, int scrollY, int scrollRangeX, int scrollRangeY, int maxOverScrollX, int maxOverScrollY, boolean isTouchEvent) {
	        return super.overScrollBy(deltaX, deltaY, scrollX, scrollY, scrollRangeX, scrollRangeY, maxOverScrollX, maxOverScrollY, isTouchEvent);
	    }

	其中的maxOverScrollY，默认值为0。所以只要修改它的值，就可以让ListView具有弹性了。

第五章：Android Scroll分析
=======

5.1 滑动效果是如何产生的
--------

滑动一个View，本质上来说就是移动一个View。改变其当前所处的位置，它的原理与动画效果的实现非常相似，都是通过不断地改变View的坐标来实现这一效果。所以，要实现View的滑动，就必须监听用户触摸的事件，并根据事件传入的坐标，动态且不断地改变View的坐标，从而实现View跟随用户触摸的滑动而滑动。

**Android坐标系**

Android的坐标系是以屏幕最左上角为顶点，向右为x轴正方向，向下是y轴正方向。在触控事件中通过`getRawX()`和`getRawY()`获取Android坐标系中的坐标。在View中通过`getLocationOnScreen(int[] location)`获取。

**视图坐标系**

描述的是子视图在父视图中的位置关系，原点为父视图的左上角，x、y轴方向与Android坐标系一致。触控事件中通过`getX()`,`getY()`获取视图坐标系的坐标。

**触控事件——MotionEvent**

	// 单点触摸按下动作
	public static final int MotionEvent.ACTION_DOWN = 0;
	// 单点触摸离开动作
	public static final int MotionEvent.ACTION_UP = 1;
	// 触摸点移动动作
	public static final int MotionEvent.ACTION_MOVE = 2;
	// 触摸动作取消
	public static final int MotionEvent.ACTION_CANCEL = 3;
	// 触摸动作超出边界
	public static final int MotionEvent.ACTION_OUTSIDE = 4;
	// 多点触摸按下动作
	public static final int MotionEvent.ACTION_POINTER_DOWN = 5;
	// 多点离开动作
	public static final int MotionEvent.ACTION_POINTER_UP = 6;

**View提供的获取坐标方法**

* getTop()：获取到的是View自身的顶边到其父布局顶边的距离
* getLeft()：获取到的是View自身的左边到其父布局左边的距离
* getRight()：获取到的是View自身的右边到其父布局右边的距离
* getBottom()：获取到的是View自身的底边到其父布局底边的距离

**MotionEvent提供的方法**

* getX()：获取点击事件距离控件左边的距离，即视图坐标
* getY()：获取点击事件距离控件顶边的距离，即视图坐标
* getRawX()：获取点击事件距离整个屏幕左边的距离，即绝对坐标
* getRawY()：获取点击事件距离整个屏幕顶边的距离，即绝对坐标

5.2 实现滑动的七种方法
--------

* layout()方法

		private int lastX;
		private int lastY;
		private int offsetX;
		private int offsetY;
	
		@Override
		public boolean onTouchEvent(MotionEvent event) {
		  int x = (int) event.getRawX();
		  int y = (int) event.getRawY();
		
		  switch (event.getAction()) {
		  case MotionEvent.ACTION_DOWN:
		      lastX = x;
		      lastY = y;
		      break;
		  case MotionEvent.ACTION_MOVE:
		      offsetX = x - lastX;
		      offsetY = y - lastY;
		
		      layout(getLeft() + offsetX, getTop() + offsetY, getRight() + offsetX, getBottom() + offsetY);
		
		      lastX = x;
		      lastY = y;
		      break;
		  }
		  return true;
		}

* offsetLeftAndRight()与offsetTopAndBottom()

		// 同时对left和right进行偏移
		offsetLeftAndRight(offsetX);
		// 同时对top和bottom进行偏移
		offsetTopAndBottom(offsetY);

* LayoutParams

		LinearLayout.MarginLayoutParams layoutParams=(LinearLayout.LayoutParams)getLayoutParams();
        layoutParams.leftMargin=getLeft()+offsetX;
        layoutParams.topMargin=getTop()+offsetY;
        setLayoutParams(layoutParams);

	或者

		ViewGroup.MarginLayoutParams layoutParams=(MarginLayoutParams) getLayoutParams();
		layoutParams.leftMargin=getLeft()+offsetX;
		layoutParams.topMargin=getTop()+offsetY;
		setLayoutParams(layoutParams);

* scrollTo()和scrollBy()

		//scrollTo和scrollBy移动的是view的内容而不是view本身
		//如果在viewgroup中使用就是移动所有子view。
		View view=(View) getParent();
		//scrollTo和scrollBy参考的坐标系正好与视图坐标系相反，所以offset需为负
		view.scrollBy(-offsetX, -offsetY);

* Scroller
	
	scrollTo()和scrollBy()都是使View的平移瞬间发生的，这样的效果会让人感觉很突兀，而Scroller可以实现平滑移动的效果，而不是瞬间完成的移动。

	使用Scroller主要有三个步骤：

	1. 初始化Scroller对象，一般在view初始化的时候同时初始化scroller，代码如下：
	`mScroller=new Scroller(context);`
	
	2. 重写view的computeScroll()方法，实现模拟滑动。computeScroll()的模版代码如下：
		
			@Override
			public void computeScroll() {
			  super.computeScroll();
			  // 判断Scroller是否执行完毕
			  if (mScroller.computeScrollOffset()) {
			      ((View) getParent()).scrollTo(mScroller.getCurrX(), mScroller.getCurrY());
				  // 通过重绘来不断调用computeScroll
			      invalidate();
			  }
			}

		Scroller类提供了computeScrollOffset()方法来判断是否完成了整个滑动，同时也提供了getCurrX()和getCurrY()方法来获得当前的滑动坐标。computeScroll()方法是不会自动调用的，只能通过invalidate()->draw()->computeScroll()来间接调用，实现循环获取scrollX和scrollY的目的，当移动过程结束之后，Scroller.computeScrollOffset方法会返回false，从而中断循环,完成整个平滑移动过程；
	
	3. startScroll开启模拟过程。调用Scroller.startScroll()方法，将起始位置、偏移量以及移动时间(可选)作为参数传递给startScroll()方法。在获取坐标时，通常可以使用getScrollX()和getScrollY()方法来获取父视图中content所滑动到的点的坐标，不过要注意的是这个值的正负，它与在scrollBy()、scrollTo()中讲解是一样的。另外，在startScroll()之后，还要invalidate()方法来通知View进行重绘，从而来调用computeScroll()的模拟过程。当然，可以给startScroll()方法增加一个duration的参数来设置滑动的持续时长。
	

* 属性动画

* ViewDragHelper

	ViewDragHelper基本可以实现各种不同滑动需求，但使用稍微复杂。
	
	示例代码：

		public class DragViewGroup extends FrameLayout {
		
		  private ViewDragHelper mViewDragHelper;
		  private View mMenuView, mMainView;
		  private int mWidth;
		
		  public DragViewGroup(Context context) {
		      super(context);
		      initView();
		  }
		
		  public DragViewGroup(Context context, AttributeSet attrs) {
		      super(context, attrs);
		      initView();
		  }
		
		  public DragViewGroup(Context context, AttributeSet attrs, int defStyleAttr) {
		      super(context, attrs, defStyleAttr);
		      initView();
		  }
		
		  @Override
		  protected void onFinishInflate() {
		      super.onFinishInflate();
		      mMenuView = getChildAt(0);
		      mMainView = getChildAt(1);
		  }
		
		  @Override
		  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		      super.onSizeChanged(w, h, oldw, oldh);
		      mWidth = mMenuView.getMeasuredWidth();
		  }
		
		  @Override
		  public boolean onInterceptTouchEvent(MotionEvent ev) {
		      return mViewDragHelper.shouldInterceptTouchEvent(ev);
		  }
		
		  @Override
		  public boolean onTouchEvent(MotionEvent event) {
		      //将触摸事件传递给ViewDragHelper,此操作必不可少
		      mViewDragHelper.processTouchEvent(event);
		      return true;
		  }
		
		  private void initView() {
		      mViewDragHelper = ViewDragHelper.create(this, callback);
		  }
		
		  private ViewDragHelper.Callback callback = new ViewDragHelper.Callback() {
		
              // 何时开始检测触摸事件
              @Override
              public boolean tryCaptureView(View child, int pointerId) {
                  //如果当前触摸的child是mMainView时开始检测
                  return mMainView == child;
              }

              // 触摸到View后回调
              @Override
              public void onViewCaptured(View capturedChild, int activePointerId) {
                  super.onViewCaptured(capturedChild, activePointerId);
              }

              // 当拖拽状态改变，比如idle，dragging
              @Override
              public void onViewDragStateChanged(int state) {
                  super.onViewDragStateChanged(state);
              }

              // 当位置改变的时候调用,常用与滑动时更改scale等
              @Override
              public void onViewPositionChanged(View changedView, int left, int top, int dx, int dy) {
                  super.onViewPositionChanged(changedView, left, top, dx, dy);
              }

              // 处理垂直滑动
              @Override
              public int clampViewPositionVertical(View child, int top, int dy) {
                  return 0;
              }

              // 处理水平滑动
              @Override
              public int clampViewPositionHorizontal(View child, int left, int dx) {
                  return left;
              }

              // 拖动结束后调用
              @Override
              public void onViewReleased(View releasedChild, float xvel, float yvel) {
                  super.onViewReleased(releasedChild, xvel, yvel);
                  //手指抬起后缓慢移动到指定位置
                  if (mMainView.getLeft() < 500) {
                      //关闭菜单，相当于Scroller的startScroll方法
                      mViewDragHelper.smoothSlideViewTo(mMainView, 0, 0);
                      ViewCompat.postInvalidateOnAnimation(DragViewGroup.this);
                  } else {
                      //打开菜单
                      mViewDragHelper.smoothSlideViewTo(mMainView, 300, 0);
                      ViewCompat.postInvalidateOnAnimation(DragViewGroup.this);
                  }
              }
          };
		
		  @Override
		  public void computeScroll() {
		      if (mViewDragHelper.continueSettling(true)) {
		          ViewCompat.postInvalidateOnAnimation(this);
		      }
		  }
		}
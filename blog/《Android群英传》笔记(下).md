title: 《Android群英传》笔记(下)
date: 2016-03-08 20:22:27
categories: Book Note
tags: [Android,《Android群英传》]
---
第六章：Android绘图机制与处理技巧
=======
6.1 屏幕的尺寸信息
-----
系统屏幕密度如下

* ldpi---120---240X320分辨率
* mdpi---160---320X480分辨率
* hdpi---240---480X800分辨率
* xhdpi---320---720X1280分辨率
* xxhdpi---480---1080X1920分辨率

Android系统使用mdpi即密度值为160的屏幕作为标准，在这屏幕上1px = 1dp。

所以各个分辨率直接的换算比例，即ldpi:mdpi:hdpi:xhdpi:xxhdpi=3:4:6:8:12

下面给出单位转换的源码：

	public class DisplayUtil {

	    /**
	     * 把px值转换为dip或dp值
	     *
	     * @param context
	     * @param pxValue
	     * @return
	     */
	    public static int px2dip(Context context, float pxValue) {
	        final float scale = context.getResources().getDisplayMetrics().density;
	        return (int) (pxValue / scale + 0.5f);
	    }
	
	    /**
	     * 把dip值或dp值转换为px值
	     *
	     * @param context
	     * @param dipValue
	     * @return
	     */
	    public static int dip2px(Context context, float dipValue) {
	        final float scale = context.getResources().getDisplayMetrics().density;
	        return (int) (dipValue * scale + 0.5f);
	    }
	
	    /**
	     * 将px值转换为sp值
	     *
	     * @param context
	     * @param pxValue
	     * @return
	     */
	    public static int px2sp(Context context, float pxValue) {
	        final float fontScale = context.getResources().getDisplayMetrics().scaledDensity;
	        return (int) (pxValue / fontScale + 0.5f);
	    }
	
	    /**
	     * 将sp值转换为px值
	     * @param context
	     * @param spValue
	     * @return
	     */
	    public static int sp2px(Context context, float spValue) {
	        final float fontScale = context.getResources().getDisplayMetrics().scaledDensity;
	        return (int) (spValue * fontScale + 0.5f);
	    }
	
	}

6.2 2D绘图基础
-----
Paint类的一些属性和对应的功能：

* setAntiAlias(); //设置画笔的锯齿效果
* setColor(); //设置画笔的颜色
* setARGB(); //设置画笔的A,R,G,B的值
* setAlpha(); //设置画笔的Alpha值
* setTextSize(); //设置字体的尺寸
* setStyle(); //设置画笔的风格（空心或者实心）
* setStrokeWidth(); //设置空心边框的宽度

Canvas类主要的绘画功能：

* canvas.drawPoint(x,y,paint); //绘制点
* canvas.drawLine(startX,startY,endX,endY,paint); //绘制直线
* canvas.drawRect(left,top,right,bottom,paint); //绘制矩形
* canvas.drawRoundRect(left,top,right,bottom,radiusX,radiusY,paint); //绘制圆角矩形
* canvas.drawCircle(circleX,circleY,radius,paint); //绘制圆
* canvas.drawOval(left,top,right,bottom,paint); //通过椭圆的外接矩形来绘制椭圆
* canvas.drawText(text,startX,startY,paint); //绘制文字
* canvas.drawPosText(text,new float[]{x1,y1,...,xn,yn},paint); //指定位置绘制文本

6.3 Android XML绘图
-----
* Bitmap：
 
		<bitmap xmlns:android="http://schemas.android.com/apk/res/android"
		  android:src="@drawable/ic_launcher"/>

	这样就能直接将图片转成bitmap在程序中使用了。

* Shape:

		<shape xmlns:android="http://schemas.android.com/apk/res/android"
		  android:shape="line|oval|ring|rectangle">
		  <!--默认为rectangle-->
		  <corners
		      android:bottomLeftRadius="integer"
		      android:bottomRightRadius="integer"
		      android:radius="integer"
		      android:topLeftRadius="integer"
		      android:topRightRadius="integer" />
		  <!--当shape为rectangle时才有，radius默认为1dp-->
		
		  <gradient
		      android:angle="integer"
		      android:centerColor="color"
		      android:centerX="integer"
		      android:centerY="integer"
		      android:endColor="color"
		      android:gradientRadius="integer"
		      android:startColor="color"
		      android:type="linear|radial|sweep"
		      android:useLevel="boolean" />
		
		  <padding
		      android:bottom="integer"
		      android:left="integer"
		      android:right="integer"
		      android:top="integer" />
		
		  <size
		      android:width="integer"
		      android:height="integer" />
		  <!--指定大小，一般用在imageview配合scaleType使用-->
		
		  <solid android:color="color" />
		  <!--填充颜色-->
		  <stroke
		      android:width="integer"
		      android:color="color"
		      android:dashGap="integer"
		      android:dashWidth="integer" />
		  <!--边框,dashGap为虚线间隔宽度，dashWidth为虚线宽度-->
		</shape>

* Layer:实现类似Photoshop中图层的概念。

		<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
		  <item android:drawable="@mipmap/ic_launcher" />
		  <item
		      android:drawable="@mipmap/ic_launcher"
		      android:left="10dp"
		      android:top="10dp" />
		  <item
		      android:drawable="@mipmap/ic_launcher"
		      android:left="20dp"
		      android:top="20dp" />
		</layer-list>

* Selector：通常用于view的触摸反馈。

		<selector xmlns:android="http://schemas.android.com/apk/res/android">
		  <item android:state_pressed="true">
		      <shape android:shape="rectangle">
		          <solid android:color="#334444" />
		      </shape>
		  </item>
		  <item android:state_pressed="false">
		      <shape android:shape="rectangle">
		          <solid android:color="#444444" />
		      </shape>
		  </item>
		</selector>

6.4 Android绘图技巧
--------

* Canvas.save():保存画布。它的作用就是将之前的所有已绘制图像保存起来，让后续的操作就好像在一个新的图层上操作一样。

* Canvas.restore():合并图层操作。它的作用就是将我们在save()之后绘制的所有图像与save()之前的图像进行合并。

* Canvas.translate():画布平移，可理解为坐标系的平移。如在之前绘制的坐标系原点在(0,0)。在translate(x,y)之后，坐标原点在(x,y)。要注意的是，并不是移动至(x,y)点，而是在原先的基础上加上x和y。比如原先canvas位于(100,200)，translate(x,y)后，canvas位于(100+x,200+y)

* Canvas.rotate():画布翻转，可理解为坐标系的翻转。canvas.rotate(30);为按照坐标系的原点顺时针旋转30度。canvas.rotate(30,x,y);为按照坐标系的(x,y)点顺时针旋转30度。

* Canvas.saveLayer()、Canvas.saveLayerAlpha():将一个图层入栈。

* Canvas.restore()、Canvas.restoreToCount():将一个图层出栈。

6.5 Android图像处理之色彩特效处理
-------
* 色调：`setRotate(int axis,float degree)`设置颜色的色调。第一个参数，系统分别使用0、1、2来代表Red、Green、Blue三种颜色的处理。而第二个参数就是需要处理的值。

		ColorMatrix hueMatrix = new ColorMatrix();
        hueMatrix.setRotate(0, hue0);
        hueMatrix.setRotate(1, hue1);
        hueMatrix.setRotate(2, hue2);

通过上面的方法，可以为RGB三种颜色分量分别重新设置了不同的色调值。

* 饱和度：`setSaturation(float sat)`方法来设置颜色的饱和度，参数即代表设置颜色饱和度的值，代码如下所示。当饱和度为0时，图像就变成灰色图像了。

		ColorMatrix saturationMatrix = new ColorMatrix();
        saturationMatrix.setSaturation(saturation);

* 亮度：当三原色以相同的比例进行混合的时候，就会显示出白色。系统也正是使用这个原理来改变一个图像的亮度的，代码如下所示。当亮度为0时，图像就变成全黑了。

		ColorMatrix lumMatrix = new ColorMatrix();
        lumMatrix.setScale(lum,lum,lum,1);

* `postConcat()`方法将矩阵的作用效果混合，从而叠加处理效果，代码如下：

		ColorMatrix imageMatrix = new ColorMatrix();
    	imageMatrix.postConcat(hueMatrix);
        imageMatrix.postConcat(saturationMatrix);
        imageMatrix.postConcat(lumMatrix);

6.6 Android图像处理之图形特效处理
-------------------
* matrix.setRotate()——旋转变换
* matrix.setTranslate()——平移变换
* matrix.setScale()——缩放变换
* matrix.setSkew()——错切变换
* pre()和post()——提供矩阵的前乘和后乘运算

6.7 Android图像处理之画笔特效处理
--------------------
	mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
可以实现圆形ImageView。

// TODO

**Shader**

Shader又称之为着色器、渲染器，它用来实现一系列的渐变、渲染效果。在Android中的Shader包括以下几种：

* BitmapShader 位图Shader
* LinearGradient 线性Shader
* RadialGradient 光束Shader
* SweepGradient 梯度Shader
* ComposeShader 混合Shader

除第一个Shader以外 其他Shader都实现了名副其实的渐变。BitmapShader产生的是一个位图，它的作用就是通过Paint对画布进行指定Bitmap的填充，填充有以下几种模式可以选择：

* CLAMP拉伸——拉伸的是图片最后一个像素 不断重复
* REPEAT重复——横向纵向不断重复
* MIRROR镜像——横向不断翻转重复，纵向不断翻转重复

**PathEffect**

PathEffect是指用各种笔触效果来绘制一个路径。

* ConrnerPathEffect 就是将拐角变得圆滑，具体圆滑的程度，则由参数决定
* DiscretePathEffect 使用这个效果后，线段上就会产生许多杂点。
* DashPathEffect 这个效果可以用来绘制虚线，用一个数组来设置各个点之间的间隔。另一个参数phase则用来控制绘制时数组的一个偏移量。通常可以通过设置值来实现路径的动态效果。
* PathDashPathEffect 与前面的DashPathEffect类似，只不过它的功能更加强大，可以设置显示点的图形，即方形点的虚线，圆形点的虚线。
* ComposePathEffect 组合PathEffect，将任意两种路径特性组合起来形成一种新的效果。

6.8 View之孪生兄弟——SurfaceView
-------------------
SurfaceView与view的区别：  

* View主要适用于主动更新的情况下，而SurfaceView 主要适用于被动更新，例如频繁的刷新。
* View在主线程中对画面进行刷新，而SurfaceView通常会通过一个子线程来进行页面刷新。 
* View在绘图时没有使用双缓冲机制，而SurfaceView在底层实现机制中就已经实现了双缓冲。

总结成一句话就是，如果你的自定义View需要频繁刷新，或者刷新时数据处理量比较大，那你就可以考虑使用SurfaceView来取代View了。

SurfaceView模版代码：

	public class MySurfaceView extends SurfaceView implements Runnable, SurfaceHolder.Callback {
	  //SurfaceHolder
	  private SurfaceHolder mSurfaceHolder;
	  //用于绘图的Canvas
	  private Canvas mCanvas;
	  //子线程标志位
	  private boolean mIsDrawing;
	
	  public MySurfaceView(Context context) {
	      super(context);
	      init();
	  }
	  public MySurfaceView(Context context, AttributeSet attrs) {
	      super(context, attrs);
	      init();
	  }
	  public MySurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
	      super(context, attrs, defStyleAttr);
	      init();
	  }
	  private void init() {
	      mSurfaceHolder = getHolder();
	      mSurfaceHolder.addCallback(this);
	      setFocusable(true);
	      setFocusableInTouchMode(true);
	      this.setKeepScreenOn(true);
		  //mSurfaceHolder.setFormat(PixelFormat.OPAQUE);
	  }
	  @Override
	  public void surfaceCreated(SurfaceHolder holder) {
	      mIsDrawing = true;
	      new Thread(this).start();
	  }
	  @Override
	  public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
	  }
	  @Override
	  public void surfaceDestroyed(SurfaceHolder holder) {
	      mIsDrawing = false;
	  }
	  @Override
	  public void run() {
	      while (mIsDrawing) {
	          draw();
	      }
	  }
	  private void draw() {
	      try {
	          //每次获得的canvas对象都是上次的 因此上次的绘画操作都会保存
	          mCanvas = mSurfaceHolder.lockCanvas();
	          //draw here
	      } catch (Exception e) {
	
	      } finally {
	          if (mCanvas != null) {
				  // 对画布内容进行提交
	              mSurfaceHolder.unlockCanvasAndPost(mCanvas);
	          }
	      }
	
	  }
	}

第七章：Android动画机制与使用技巧
=======
7.1 Android View动画框架
--------------------
Animation框架定义了透明度、旋转、缩放和位移几种常见的动画，而且控制的是整个View，实现的原理是绘制视图时 View 所在的 ViewGroup 中的 drawChild 函数获取该View的Animation的Transformation值，然后调用canvas.concat(transformToApply.getMatrix()),通过矩阵运算完成动画帧。如果动画没有完成，就继续调用invalidate()函数，启动下次绘制来驱动动画，从而完成整个动画的绘制。

* 透明度动画：AlphaAnimation
* 旋转动画：RotateAnimation
* 位移动画：TranslateAnimation
* 缩放动画：ScaleAnimation

动画集合：AnimationSet

动画监听器：setAnimationListener(new Animation.AnimationListener(){...})

7.2 Android属性动画分析
--------------------
__ObjectAnimator__：属性动画框架中最重要的实行类。

用法：

	ObjectAnimator animator = ObjectAnimator.ofFloat(view,"translationX",300);
    animator.setDuration(300);
    animator.start();

注意：操纵的属性(即上面的“translationX”)必须具有get、set方法，不然ObjectAnimator就无法起效。因为内部会通过Java反射机制来调用set函数修改对象属性值。

常用属性值：

* translationX和translationY
* rotation、rotationX和rotationY
* scaleX和scaleY
* pivotX和pivotY
* x和y
* alpha

__PropertyValuesHolder__：类似于视图动画中的AnimationSet。在属性动画中，如果针对同一个对象的多个属性，要同时作用多种动画，可以使用PropertyValuesHolder来实现。

用法：
	
	PropertyValuesHolder pvh1 = PropertyValuesHolder.ofFloat("translationX",300f);
    PropertyValuesHolder pvh2 = PropertyValuesHolder.ofFloat("scaleX",1f,0,1f);
    PropertyValuesHolder pvh3 = PropertyValuesHolder.ofFloat("scaleY",1f,0,1f);
    ObjectAnimator.ofPropertyValuesHolder(view,pvh1,pvh2,pvh3).setDuration(1000).start();

__ValueAnimator__：ValueAnimator本身不提供任何动画效果，它更像一个数值发生器，用来产生具有一定规律的数字，从而让调用者来控制动画的实现过程。

用法：

	ValueAnimator animator = ValueAnimator.ofFloat(0,100);
    animator.setTarget(view);
    animator.setDuration(1000).start();
    animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            Float value = (Float) animation.getAnimatedValue();
            // TODO use the value
        }
    });

在ValueAnimator的AnimatorUpdateListener中监听数值的变换，从而完成动画的变换。

__AnimatorListener和AnimatorListenerAdapter__：动画事件的监听。

AnimatorListener用法：

	ObjectAnimator animator = ObjectAnimator.ofFloat(view,"translationX",300);
	animator.addListener(new Animator.AnimatorListener() {
        @Override
        public void onAnimationStart(Animator animation) {
            
        }

        @Override
        public void onAnimationEnd(Animator animation) {

        }

        @Override
        public void onAnimationCancel(Animator animation) {

        }

        @Override
        public void onAnimationRepeat(Animator animation) {

        }
    });

AnimatorListenerAdapter用法：

	ObjectAnimator animator = ObjectAnimator.ofFloat(view,"translationX",300);
	animator.addListener(new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd(Animator animation) {
            super.onAnimationEnd(animation);
        }
    });

__AnimatorSet__：AnimatorSet不仅能实现一个属性同时作用多个属性动画效果，同时也能实现更为精确的顺序控制。

用法(实现上面使用PropertyValuesHolder的动画效果)：

	ObjectAnimator animator1 = ObjectAnimator.ofFloat(view,"translationX",300);
        ObjectAnimator animator2 = ObjectAnimator.ofFloat(view,"scaleX",1f,0f,1f);
        ObjectAnimator animator3 = ObjectAnimator.ofFloat(view,"scaleY",1f,0f,1f);
        AnimatorSet set = new AnimatorSet();
        set.setDuration(1000);
        set.playTogether(animator1,animator2,animator3);
        set.start();

还有playTogether()、playSequentially()、animSet.play()、with()、before()、after()来协同工作。

__在XML中使用属性动画__：在res里新建文件夹animator。

anim_scalex.xml：

	<?xml version="1.0" encoding="utf-8"?>
	<objectAnimator xmlns:android="http://schemas.android.com/apk/res/android"
	    android:duration="1000"
	    android:propertyName="scaleX"
	    android:valueFrom="1.0"
	    android:valueTo="2.0"
	    android:valueType="floatType">
	</objectAnimator> 

在代码中加载xml属性动画：

	public void scaleX(View view) {
        Animator anim = AnimatorInflater.loadAnimator(this, R.animator.anim_scalex);  
        anim.setTarget(view);  
        anim.start();
    }

另附 XML文件中定义两个objectAnimator：

	<?xml version="1.0" encoding="utf-8"?>  
	<set xmlns:android="http://schemas.android.com/apk/res/android"  
	    android:ordering="together" >  
	  
	    <objectAnimator  
	        android:duration="1000"  
	        android:propertyName="scaleX"  
	        android:valueFrom="1"  
	        android:valueTo="0.5" >  
	    </objectAnimator>  
	    <objectAnimator  
	        android:duration="1000"  
	        android:propertyName="scaleY"  
	        android:valueFrom="1"  
	        android:valueTo="0.5" >  
	    </objectAnimator>  
	  
	</set> 

__View的animate方法__：可以认为是属性动画的一种简写方式。

用法：

	view.animate()
        .alpha(0)
        .y(300)
        .setDuration(300)
        .withStartAction(new Runnable() {
            @Override
            public void run() {

            }
        }).withEndAction(new Runnable() {
            @Override
            public void run() {
				runOnUiThread(new Runnable() {
	                @Override
	                public void run() {
	                    
	                }
	            });
            }
        }).start();

7.3 Android布局动画
--------------------
最简单的布局动画是在ViewGroup的XML中，使用以下打开布局动画。
`android:animateLayoutChanges="true"`
通过上面的代码，当ViewGroup添加View时，子View会呈现逐渐显示的过渡效果，是Android默认的显示的过渡效果。

__LayoutAnimationController__

	LinearLayout ll = (LinearLayout)findViewById(R.id.ll);
	// 设置过渡动画
    ScaleAnimation scaleAnimation = new ScaleAnimation(0,1,0,1);
    scaleAnimation.setDuration(2000);
	// 设置布局动画的显示属性
    LayoutAnimationController lac = new LayoutAnimationController(scaleAnimation,0.5f);
    lac.setOrder(LayoutAnimationController.ORDER_NORMAL);
    ll.setLayoutAnimation(lac);

LayoutAnimationController的第一个参数是作用的动画，第二个参数是每个View显示的delay时间。当delay时间不为0时，可以设置子View显示的顺序，如下所示。

* LayoutAnimationController.ORDER_NORMAL  顺序
* LayoutAnimationController.ORDER_RANDOM  随机
* LayoutAnimationController.ORDER_REVERSE 反序

7.4 Interpolators(插值器)
--------------------


7.5 自定义动画
--------------------
首先继承Animation类，实现`applyTransformation(float interpolatedTime, Transformation t)`的逻辑。不过通常情况下，还要覆盖父类的`initialize(int width, int height, int parentWidth, int parentHeight)`实现一些初始化的工作。

`applyTransformation(float interpolatedTime, Transformation t)`第一个参数是插值器的时间因子，这个因子由动画当前完成的百分比和当前时间所对应的插值所计算得来的，取值为0到1.0。第二个参数是矩阵的封装类，一般使用这个类来获取当前的矩阵对象，代码如下：

	final Matrix matrix = t.getMatrix();

下面给出电视机关闭效果的动画：

	@Override
    protected void applyTransformation(float interpolatedTime, Transformation t) {
        final Matrix matrix = t.getMatrix();
        matrix.preScale(1, 1 - interpolatedTime, mCenterWidth, mCenterHeight);
    }

//TODO

7.6 Android 5.X SVG 矢量动画机制
--------------------
Google在Android 5.X中提供了下面两个新的API来帮助支持SVG：

* VectorDrawable
* AnimatedVectorDrawable

下面给出SVG图形：

	<?xml version="1.0" encoding="utf-8"?>
	<vector xmlns:android="http://schemas.android.com/apk/res/android"
	    android:width="200dp"
	    android:height="200dp"
	    android:viewportHeight="100"
	    android:viewportWidth="100">
	
	    <group
	        android:name="test"
	        android:rotation="0">
	        <path
	            android:fillColor="@android:color/holo_blue_light"
	            android:pathData="M 25 50
	            a 25,25 0 1,0 50,0" />
	    </group>
	
	</vector>

 //TODO

第八章：Activity与Activity调用栈分析
=======
8.1 Activity
--------------------
Activity的形态：

* Active/Running : 这时候Activity处于Activity栈的最顶层，可见，并与用户进行交互。

* Paused : 当Activity失去焦点，被一个新的非全屏的Activity或者一个透明的Activity放置在栈顶时，Activity就转化为Paused形态。但它只是失去了与用户交互的能力，所有状态信息、成员变量都还保持着，只有在系统内存极低的情况下，才会被系统回收掉。

* Stopped : 如果一个Activity被另一个Activity完全覆盖，那么Activity就会进入Stopped形态，此时，它不再可见，但却依然保持了所有状态信息和成员变量。

* Killed : 当Activity被系统回收掉或者Activity从来没有创建过，Activity就处于Killed状态。

Activity启动与销毁过程：

* onCreate() ： 创建基本的UI元素。

* onPause()和onStop() ： 清除Activity的资源，避免浪费。

* onDestory() ： 因为引用会在Activity销毁的时候销毁，而线程不会，所以清除开启的线程。

Activity的暂停与恢复过程：

* onPause() ： 释放系统资源，如Camera、sensor、receivers。

* onResume() ： 需要重新初始化在onPause()中释放资源。

Activity的停止过程：

* 由部分不可见到完全不可见 ： onPause() -> onStop()

* 由部分不可见到可见： onPause() -> onStop() -> onRestart() -> onStart() -> onResume()

Activity的重新创建过程：

如果用户finish()方法结束了Activity，则不会调用onSaveInstanceState()。

8.2 Android任务栈简介
--------------------
一个Task中的Activity可以来自不同的App，同一个App的Activity也可能不在一个Task中。

8.3 AndroidMainifest启动模式
--------------------

* standard ： 默认的启动模式，每次都会创建新的实例

* singleTop ：   通常适用于接收到消息后显示的界面

* singleTask ： 通常可以用来退出整个应用：将主Activity设为singleTask模式，然后在要退出的Activity中转到主Activity，从而将主Activity之上的Activity都清除，然后重写主Activity的onNewIntent()方法，在方法中加上一句finish()，将最后一个Activity结束掉。

* singleInstance ： 申明为singleInstance的Activity会出现在一个新的任务栈中，而且该任务栈中只存在这一个Activity。举个例子，如果应用A的任务栈中创建了MainActivity的实例，且启动模式为singleInstance，如果应用B的也要激活MainActivity，则不需要创建，两个应用共享该Activity实例。这种启动模式常用于需要与程序分离的界面。

关于singleTop和singleInstance这两种启动模式还有一点需要特殊说明：如果在一个singleTop或者singleInstance的Activity A中通过startActivityForResult()方法来启动另一个Activity B，那么直接返回Activity.RESULT_CANCELED而不会再去等待返回。这是由于系统在Framework层做了对这两种启动模式的限制，因为Android开发者认为，不同Task之间，默认是不能传递数据的，如果一定要传递，那么只能通过Intent来绑定数据。

8.4 Intent Flag启动模式
--------------------

* Intent.FLAG\_ACTIVITY\_NEW\_TASK：使用一个新的Task来启动一个Activity，但启动的每个Activity都将在一个新的Task中。该Flag通常使用在从Service中启动Activity的场景，由于在Service中并不存在Activity栈，所以使用该Flag来创建一个新的Activity栈，并创建新的Activity实例。

* Intent.FLAG\_ACTIVITY\_SINGLE\_TOP：使用singleTop模式来启动一个Activity，与指定android:launchMode="singleTop"效果相同。

* Intent.FLAG\_ACTIVITY\_CLEAR\_TOP：使用singleTask模式来启动一个Activity，与指定android:launchMode="singleTask"效果相同。

* Intent.FLAG\_ACTIVITY\_NO\_HISTORY：使用这种模式启动Activity，当该Activity启动其他Activity后，该Activity就消失了，不会保留在Activity栈中。例如A-B，B中以这种模式启动C，C再启动D，则当前Activity栈为ABD。

8.5 清空任务栈
--------------------

* clearTaskOnLaunch：每次返回该Activity时，都将该Activity之上的所有Activity都清除。通过这个属性，可以让这个Task每次在初始化的时候，都只有这一个Activity。

* finishOnTaskLaunch：finishOnTaskLaunch属性与clearTaskOnLaunch属性类似，只不过clearTaskOnLaunch作用在别人身上，而finishOnTaskLaunch作用在自己身上。通过这个属性，当离开这个Activity所处的Task，那么用户再返回时，该Activity就会被finish掉。

* alwaysRetainTaskState：如果将Activity的这个属性设置为True，那么该Activity所在的Task将不接受任何清理命令，一直保持当前Task状态。

第九章：Android系统信息与安全机制
=======

9.1 Android系统信息获取
--------------------

要获取系统的配置信息，通常可以从以下两个方面获取：

* android.os.Build
* SystemProperty

下面列举了android.os.Build一些常用的信息：

* Build.BOARD // 主板
* Build.BRAND // Android系统定制商
* Build.SUPPORTED_ABIS // CPU指令集
* Build.DEVICE // 设备参数
* Build.DISPLAY // 显示屏参数
* Build.FINGERPRINT // 唯一编号
* Build.SERIAL // 硬件序列号
* Build.ID // 修订版本列表
* Build.MANUFACTURER // 硬件制造商
* Build.MODEL // 版本
* Build.HARDWARE // 硬件名
* Build.PRODUCT // 手机产品名
* Build.TAGS // 描述Build的标签
* Build.TYPE // Builder类型
* Build.VERSION.CODENAME // 当前开发代号
* Build.VERSION.INCREMENTAL // 源码控制版本号
* Build.VERSION.RELEASE // 版本字符串
* Build.VERSION.SDK_INT // 版本号
* Build.HOST // Host值
* Build.USER // User名
* Build.TIME // 编译时间

下面列举了SystemProperty常用的信息：

* os.version // OS版本
* os.name // OS名称
* os.arch // OS架构
* user.home // Home属性
* user.name // Name属性
* user.dir //Dir属性
* user.timezone // 时区
* path.separator // 路径分隔符
* line.separator // 行分隔符
* file.separator // 文件分隔符
* java.vendor.url // Java Vendor URL 属性
* java.class.path // Java Class 路径
* java.class.version Java Class 版本
* java.vendor // Java Vendor 属性
* java.version // Java 版本
* java.home // Java Home 属性

我们可以访问到系统的属性值，代码如下所示：

	String board = Build.BOARD;
	String brand = Build.BRAND;

	String os_version = System.getProperty("os.version");
	String os_name = System.getProperty("os.name");

9.2 Android Apk应用信息获取之PackageManager
--------------------

Android系统提供了PackageManager来负责管理所有已安装的App。其中封装的信息如下：

* ActivityInfo：Mainfest文件中<activity\></activity\>和<receiver\></receiver\>之间的所有信息，包括name、icon、label、launchmode等。
* ServiceInfo：封装了<service\></service\>之间的所有信息。
* ApplicationInfo：封装了<application\></application\>之间的信息，不过特别的是，Application包含很多Flag，FLAG\_SYSTEM表示为系统应用，FLAG\_EXTERNAL\_STORAGE表示为安装在SDCard上的应用等，通过这些Flag，可以很方便的判断应用类型。
* PackageInfo：PackageInfo与前面三个Info类似，都是用于封装Mainfest文件的相关节点信息，而它包含了所以Activity、Service等信息。
* ResolveInfo：封装的是包含<intent\>信息的上一级信息，所以它可以返回ActivityInfo，ServiceInfo等包含<intent\>的信息，它经常用来帮助我们找到那些包含特定Intent条件的信息，如带分享功能、播放功能的应用。

PackageManager常用方法如下：

* getPackageManager：通过调用这个方法返回一个PackageManager对象。
* getApplicationInfo：以ApplicationInfo的形式返回指定包名的ApplicationInfo。
* getApplicationIcon：返回指定包名的Icon。
* getInstallApplication：以ApplicationInfo的形式返回安装的应用。
* getInstalledPackages：以PackageInfo的形式返回安装的应用。
* queryIntentActivities：返回指定intent的ResolveInfo对象、Activity集合。
* queryIntentServices：返回指定intent的ResolveInfo对象、Service集合。
* resolveActivity：返回指定Intent的Activity。
* resolveService：返回指定Intent的Service。

判断App类型的依据，就是利用ApplicationInfo中的FLAG_SYSTEM来进行判断，代码如下所示：

	app.flags & ApplicationInfo.FLAG_SYSTEM

* 如果当前应用的`flags & ApplicationInfo.FLAG_SYSTEM != 0`则为系统应用；
* 如果当前应用的`flags & ApplicationInfo.FLAG_SYSTEM <= 0`则为第三方应用；
* 特殊的，当系统应用经过升级后，也将成为第三方应用：`flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0`；
* 如果当前应用的`flags & ApplicationInfo.FLAG_EXTERNAL_STORAGE != 0`则为安装在SDCard上的应用。

9.3 Android Apk应用信息获取之ActivityManager
--------------------

ActivityManager可以获得在运行的应用程序信息。其中封装的信息如下：

* ActivityManager.MemoryInfo：MemoryInfo有几个非常重要的字段，availMem--系统可用内存，totalMem--总内存，threshold--低内存的阈值，即区分是否低内存的临界值，lowMemory--是否处于低内存。
* Debug.MemoryInfo：ActivityManager.MemoryInfo用于统计全局的内存信息，而Debug.MemoryInfo用于统计进程下的内存信息。
* RunningAppProcessInfo：进程相关的信息，processName--进程名，pid--进程pid，uid--进程uid，pkgList--该进程下的所有包。
* RunningServiceInfo：用于封装运行的服务信息，在它里面包含一些服务进程的信息，同时还有一些其他信息。activeSince--第一次被激活的时间、方式，foreground--服务是否在后台执行。

9.4 解析Packages.xml获取系统信息
--------------------

packages.xml在 data/system/目录下。

9.5 Android安全机制
--------------------

反编译：

* apktool(反编译XML) ： `java -jar apktool_2.1.0.jar d test.apk`
* apktool(重新打包) ： `java -jar apktool_2.1.0.jar b test`
* Dex2jar、jd-gui ：`d2j-dex2jar.bat classes.dex`

Android Apk 加密：

打开build.gradle(Module:app)文件，minifyEnabled为true则说明打开混淆功能，proguard-rules.pro为项目自定义的混淆文件，在项目app文件夹下找到这个文件，在这个文件里可以定义引入的第三方依赖包的混淆规则。

	buildTypes {
        release {
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'
        }
    }

第十章：Android性能优化
=======

10.1 布局优化
--------------------

* Android UI渲染机制

	在Android中，系统通过VSYNC信号出发对UI的渲染、重绘，其间隔时间是16ms。这个16ms其实就是1000ms中显示60帧画面的单位时间（玩游戏的就该知道，大于等于60帧就感觉不到卡顿）。Android系统提供了检测UI渲染时间的工具，打开“开发者选项”，选择“Profile GPU Rendering”（我的手机是“GPU呈现模式分析”），选中“On screen as bars”（我的为“在屏幕上显示为条形图”）。每一条柱状线都包括三部分，蓝色代表测量绘制Display List的时间，红色代表OpenGL渲染Display List所需要的时间，黄色代表CPU等待GPU处理的时间，中间绿色横线代表VSYNC时间16ms，需要尽量将所有条形图都控制在这条绿线之下。

* 避免Overdraw

	过渡绘制会浪费很多CPU、GPU资源，例如系统默认会绘制Activity的背景，而如果再给布局绘制了重叠的背景，那么默认Activity的背景就属于无效的过渡绘制。Android系统在开发者选项中提供了这样一个检测工具--“Enable GPU Overdraw”。借助它可以判断Overdraw的次数。尽量增大蓝色的区域，减少红色的区域。

* 优化布局层级

	在Android中系统对View的测量、布局和绘制都是通过遍历View树来进行的，如果View树太高，就会影响其速度，Google也建议View树的高度不宜超过10层。

* 避免嵌套过多无用布局

	* 使用<include\>标签重用Layout
	* 使用<ViewStub\>实现View的延迟加载
	
	<ViewStub\>是个非常轻量级的组件，不仅不可视而且大小为0。这个布局在初始化时不需要显示，只有在某些情况下才显示出来。下面是实例代码：

			<RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
				xmlns:tools="http://schemas.android.com/tools"
				android:layout_width="match_parent"
				android:layout_height="match_parent"
				tools:context=".MainActivity">
				<TextView
				  android:id="@+id/tv"
				  android:layout_width="wrap_content"
				  android:layout_height="wrap_content"
				  android:text="not often use" />
			</RelativeLayout>

	使用<ViewStub\>：

			<?xml version="1.0" encoding="utf-8"?>
			<RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
				xmlns:tools="http://schemas.android.com/tools"
				android:layout_width="match_parent"
				android:layout_height="match_parent"
				tools:context=".MainActivity">
				<ViewStub
				  android:id="@+id/view_stub"
				  android:layout_width="match_parent"
				  android:layout_height="wrap_content"
				  android:layout_centerInParent="true"
				  android:layout="@layout/test" />
			</RelativeLayout>

	在`onCreate(Bundle savedInstanceState)`中：

			ViewStub viewStub= (ViewStub) findViewById(R.id.view_stub);
			//下面两个方法都是用来实现延迟加载的，区别是inflate()方法会返回引用的布局。
			viewStub.setVisibility(View.VISIBLE); // 第一种方法
			
			View view=viewStub.inflate(); // 第二种方法
			TextView tv= (TextView) view.findViewById(R.id.tv);

	<ViewStub\>标签与设置View.GONE这种方式的区别在于<ViewStub\>标签只在显示时渲染整个布局，而设置View.GONE这种方式在初始化布局树时就已经添加在布局树上了，所以相比之下<ViewStub>更有效率。

* Hierarchy Viewer

这是个用来测试布局冗余的工具。[可点击此处](http://blog.csdn.net/xyz_lmn/article/details/14222975)

10.2 内存优化
--------------------

* 什么是内存

	* 寄存器：速度最快的存储场所，因为寄存器位于处理器内部，在程序中无法控制。
	* 栈：存放基本类型的数据和对象的引用，但对象本身不存放在栈中，而是存放在堆中。
	* 堆：堆内存用来存放由new创建的对象和数组，在堆中分配的内存，由Java虚拟机的自动垃圾回收器(GC)来管理。
	* 静态存储区域：是指在固定的位置存放应用程序运行时一直存在的数据，Java在内存中专门划分了一个静态存储区域来管理一些特殊的数据变量如静态的数据变量。
	* 常量池：就是该类型所用到常量的一个有序集合，包括直接常量（基本类型，String）和对其他类型、字段和方法的符号引用。

	在程序中，可以使用如下所示的代码来获得堆的大小，所谓的内存分析，正是分析Heap中的内存状态

			ActivityManager manager = (ActivityManager)getSystemService(Context.ACTIVITY_SERVICE)；
			int heapSize = manager.getLargeMemoryClass();

* 获取Android系统内存信息

	* 进程状态
	
 			adb shell dumpsys procstats

	* 内存信息
			
			adb shell dumpsys meminfo

* 内存优化实例

	* Bitmap优化
	
	Bitmap是造成内存占用过高甚至是OOM的最大威胁，可以通过以下技巧进行优化
	
	① 使用适当分辨率和大小的图片：例如在图片列表界面可以使用图片的缩略图thumbnails，而在显示详细图片的时候再显示原图；或者在对图像要求不高的地方，尽量降低图片的精度。
	
	② 及时回收内存：一旦使用完Bitmap后，一定要及时使用bitmap.recycle()方法释放内存资源。自Android3.0后，由于Bitmap被放到了堆中，其内存由GC管理，就不需要释放了。

	③ 通过内存缓存LruCache和DiskLruCache可以更好地使用Bitmap。
	
	* 代码优化

	任何Java类都将占用大约500字节的内存空间，创建一个类的实例会消耗大约15字节内存。从代码的实现上，也可以对内存进行优化。

	① 对常量使用static修饰符。

	② 使用静态方法，静态方法会比普通方法提高15%左右的访问速度。

	③ 减少不必要的成员变量，这点在Android Lint工具上已经集成检测了。

	④ 减少不必要对象，使用基础类型会比使用对象更加节省资源，同时更应该避免频繁创建短作用域的变量。

	⑤ 尽量不要使用枚举、少用迭代器。

	⑥ 对Cursor、Receiver、Sensor、File等对象，要非常注意对它们的创建、回收和注册、反注册。

	⑦ 避免使用IOC框架，IOC通常使用注解、反射来进行实现，大量使用反射会带来性能的下降。
	
	⑧ 使用RenderScript、OpenGL来进行非常复杂的绘图操作。

	⑨ 使用SurfaceView来代替View进行大量、频繁的绘图操作。

	⑩ 尽量使用视图缓存，而不是每次都执行inflate()解析视图。

10.3 Lint工具
--------------------

10.4 使用Android Studio的Memory Monitor工具
--------------------

10.5 使用TraceView工具优化App性能
--------------------

10.6 使用MAT工具分析App内存状态
--------------------

10.7 使用Dumpsys命令分析系统状态
--------------------


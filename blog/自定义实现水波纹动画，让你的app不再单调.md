title: 自定义实现水波纹动画，让你的app不再单调
date: 2015-12-27 23:05:17
categories: Android Blog
tags: [Android,自定义View]
---
在开发Android应用的过程中，动画是一个很重要的点。好的动画可以给用户一种耳目一新的感觉。比如说京东app里下拉刷新中的动画是一个奔跑的快递员，这样用户会有一种耳目一新的感觉。所以我们何尝不提供一种新的动画方式呢？而今天给大家带来的就是水波纹动画。

至于效果怎样，我们一起来看看：

![这里填写图片描述](/uploads/20151227/20151227231546.gif)

是不是觉得有新意多了呢？那就一起来看看吧，先简单讲述一下思路：首先波浪的形状主要是根据三角函数决定的。三角函数相信大家在中学的课程中学习过吧。通用公式就是f(x)=Asin(ωx+φ) + b。其中A就是波浪的振幅，ω与时间周期有关，x就是屏幕宽度的像素点，φ是初相，可以让波浪产生偏移，最后的b就是水位的高度了。最后根据这公式算出y坐标，用`canvas.drawLine(startX, startY, stopX, stopY, paint);`来画出竖直的线条，这样就形成了波浪。

整体的思路就如下面示意图所示，当红色的线条间距越来越小，密度越来越大时就形成了波浪：

![这里填写图片描述](/uploads/20151227/20151227230103.png)

讲完了思路，那下面我们就来分析一下代码吧。

首先看一下“自定义View三部曲”中的第一部，自定义属性：
``` xml
<?xml version="1.0" encoding="utf-8"?>
<resources>

    <declare-styleable name="WaveView">
        <attr name="waveColor" format="color|reference"></attr>
        <attr name="secondWaveColor" format="color|reference"></attr>
        <attr name="waveHeight" format="dimension|reference"></attr>
    </declare-styleable>
    
</resources>
```
我们先定义了三个属性，分别是前波浪颜色、后波浪颜色以及波浪的振幅高度。

然后就是在构造器中初始化自定义的属性。
``` java
public WaveView(Context context) {
    this(context, null);
}

public WaveView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
}

public WaveView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.WaveView);
    defaultWaveColor = getResources().getColor(R.color.indigo_color);
    waveColor = a.getColor(R.styleable.WaveView_waveColor, defaultWaveColor);
    defaultSecondWaveColor = getResources().getColor(R.color.second_indigo_color);
    secondWaveColor = a.getColor(R.styleable.WaveView_secondWaveColor, defaultSecondWaveColor);
    defaultWaveHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics());
    waveHeight = a.getDimension(R.styleable.WaveView_waveHeight, defaultWaveHeight);
    a.recycle();
    mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
}
```
前面的代码很简单，接下来要重写`onSizeChanged(int w, int h, int oldw, int oldh)`:
``` java
@Override
protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    this.w = (float) (Math.PI * 2 / w);
}
```
这里的w就是上面f(x)=Asin(ωx+φ) + b公式中的ω，而ω=2π/T。也就是说周期就是屏幕宽度。所以在一个屏幕内正好可以显示出正弦函数的一个周期。

下面就是三部曲的第二部：重写`onMeasure(int widthMeasureSpec, int heightMeasureSpec)`：
``` java
@Override
protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    int widthSize = MeasureSpec.getSize(widthMeasureSpec);
    int widthMode = MeasureSpec.getMode(widthMeasureSpec);
    if (widthMode == MeasureSpec.EXACTLY) {
        mWidth = widthSize;
    } else {
        mWidth = 200;
    }

    int heightSize = MeasureSpec.getSize(heightMeasureSpec);
    int heightMode = MeasureSpec.getMode(heightMeasureSpec);
    if (heightMode == MeasureSpec.EXACTLY) {
        mHeight = heightSize;
    } else {
        mHeight = 200;
    }
    setMeasuredDimension(mWidth, mHeight);
}
```
onMeasure()中就是测量了View的宽高度，如果不是MeasureSpec.EXACTLY的模式就直接赋值200(这里没有把200px转化为200dp,偷懒了ㄟ(▔ ,▔)ㄏ)。相信大家都会了。

最后就是`onDraw(Canvas canvas)`，也就是三部曲中的最后一部：
``` java
@Override
protected void onDraw(Canvas canvas) {
	// 水位的高度
    waterHeight = waterHeight + 10;
	// 正弦函数y的坐标
    float startY;
    canvas.save();

    if (System.currentTimeMillis() - startTime < 100) {
        try {
            Thread.sleep(100 + startTime - System.currentTimeMillis());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    double temp = Math.toDegrees(speed);
    speed++;

	// 先绘制第二条波浪
    mPaint.setColor(secondWaveColor);
	// 遍历每个像素点，并在竖直上画线
    for (int i = 0; i < mWidth; i++) {
		// 和第一条波浪相比产生的偏移量为8,至于偏移量大小可以自己决定
        startY = (float) (waveHeight * Math.sin(w * i + temp + 8) + mHeight - waveHeight - waterHeight);
        canvas.drawLine(i, startY, i, mHeight, mPaint);
    }

	// 再绘制第一条波浪
    mPaint.setColor(waveColor);
    for (int i = 0; i < mWidth; i++) {
        startY = (float) (waveHeight * Math.sin(w * i + temp) + mHeight - waveHeight - waterHeight);
        canvas.drawLine(i, startY, i, mHeight, mPaint);
    }
    Log.i(TAG, "waterHeight : " + waterHeight);
    canvas.restore();
	// 不断重绘
    invalidate();
    startTime = System.currentTimeMillis();
}
```
在`onDraw(Canvas canvas)`的一开始waterHeight不断自增，以此来实现水位不断上涨的效果，然后就是线程的休眠来控制绘制的频率。之后在绘制第二条波浪时初相加上一个偏移量，这样就可以与第一条波浪形成交错的效果。整体代码并不复杂，主要是坐标上的计算。

到这里基本就讲得差不多了，以下是本案例的源码：

[WaveView.rar](/uploads/20151227/WaveView.rar)

最后，预祝大家元旦快乐！
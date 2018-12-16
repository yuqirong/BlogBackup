title: 自定义View之实现文字百分比进度条
date: 2015-10-23 23:26:56
categories: Android Blog
tags: [Android,自定义View]
---
之前在学习自定义View的时候看到[鸿洋_](http://my.csdn.net/lmj623565791)的 [《Android 打造形形色色的进度条 实现可以如此简单》](http://blog.csdn.net/lmj623565791/article/details/43371299) 中自带百分比的进度条，于是照着例子自己实现了一下。下面是View的样子：  
![这里写图片描述](/uploads/20151023/20151023211913.gif)

大家都知道自定义View的主要步骤：  
1. 自定义View的一些属性  
2. 在构造器中初始化属性  
3. 重写onMeasure()方法  
4. 重写onDraw()方法  

下面就来实现第一步：  
先在values文件夹中新建attrs.xml：
``` xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
	<declare-styleable name="PercentProgressView">
		<!-- 进度条当前进度的颜色 -->
		<attr name="progress_color" format="color|reference"></attr>
		<!-- 进度条的当前进度 -->
		<attr name="progress" format="integer|float"></attr>
		<!-- 进度条的总进度 -->
		<attr name="total" format="integer|float"></attr>
		<!-- 进度条总进度的颜色 -->
		<attr name="total_color" format="color|reference"></attr>
		<!-- 百分比字体的大小 -->
		<attr name="text_size" format="dimension"></attr>
		<!-- 进度条的宽度 -->
		<attr name="progress_height" format="dimension"></attr>
		<!-- 百分比字体的偏移量 -->
		<attr name="text_offset" format="dimension"></attr>
	</declare-styleable>
</resources>
```
好了我差不多就定义以上几种属性，有需要的可以在后面再添加。这样我们的第一步就完成了。下面我们就来看看第二步吧。
``` java
// 画笔
private Paint mPaint;
// 当前进度颜色
private int mProgressColor;
// 总进度颜色
private int mTotalColor;
// view的宽度
private int mWidth;
// view的高度
private int mHeight;
// 当前进度
private float mProgress;
// 总进度
private float mTotal;
// 字体大小
private float mTextSize;
// 进度文字偏移
private float mTextOffset;
// 进度条高度
private float mProgressHeight;
// 真实宽度
private int realWidth;
// 文字默认宽度
private static final float TEXT_SIZE = 20;
// 文字偏移
private static final float TEXT_OFFSET = 10;
// 默认进度条高度
private static final float DEFAULT_PROGRESS_HEIGHT = 4;

public PercentProgressView(Context context) {
    this(context, null);
}

public PercentProgressView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
}

public PercentProgressView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.PercentProgressView);
    // 当前进度颜色
    mProgressColor = a.getColor(R.styleable.PercentProgressView_progress_color, Color.RED);
    // 总进度颜色
    mTotalColor = a.getColor(R.styleable.PercentProgressView_total_color, Color.GRAY);
    // 当前的进度
    mProgress = a.getFloat(R.styleable.PercentProgressView_progress, 0f);
    // 总量
    mTotal = a.getFloat(R.styleable.PercentProgressView_total, 100f);
    // 字体大小
    float defaultTextSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, TEXT_SIZE, getResources()
                    .getDisplayMetrics());
    mTextSize = a.getDimension(R.styleable.PercentProgressView_text_size, defaultTextSize);
    // 进度条高度
    float defaultProgressHeight = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, DEFAULT_PROGRESS_HEIGHT, getResources()
                    .getDisplayMetrics());
    mProgressHeight = a.getDimension(R.styleable.PercentProgressView_progress_height, defaultProgressHeight);
    // 进度文字偏移
    float defaultTextOffset = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, TEXT_OFFSET, getResources()
                    .getDisplayMetrics());
    mTextOffset = a.getDimension(R.styleable.PercentProgressView_text_offset, defaultTextOffset);
	// 回收TypedArray
	a.recycle();
    mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    mPaint.setTextSize(mTextSize);
    mPaint.setStrokeWidth(mProgressHeight);
    mPaint.setColor(mProgressColor);
}
```
在第二步中我们主要做的就是把上一步定义的属性在构造器中初始化，设置一些默认值以及创建一个新的Paint对象。其实并没什么难度，都是一些重复性的东西。

接下来要做的就是重写onMeasure()方法来测量View。宽度我们可以设置为match_parent，高度为可自定义，所以我们要测量一下高度。使用MeasureSpec.getMode(heightMeasureSpec)来判断用户设置的模式，如果是 MeasureSpec.EXACTLY 则不直接返回 MeasureSpec.getSize(heightMeasureSpec) 就可以了，不然的话要比较文字和进度条的高度，取两者的最大值。最后调用setMeasuredDimension(width,height)。
``` java
@Override
protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    int mode = MeasureSpec.getMode(heightMeasureSpec);
    int size = MeasureSpec.getSize(heightMeasureSpec);
    mWidth = MeasureSpec.getSize(widthMeasureSpec);
    mHeight = measureHeight(mode, size);
    setMeasuredDimension(mWidth, mHeight);
    realWidth = getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
}

//测量高度
private int measureHeight(int mode, int size) {
    int result;
    if (mode == MeasureSpec.EXACTLY) {
		//精确的情况下
        result = size;
    } else {
        int h = (int) (getPaddingBottom() + getPaddingTop() +
                Math.max(mProgressHeight, Math.abs(mPaint.descent() - mPaint.ascent())));
        result = h;
        if (mode == MeasureSpec.AT_MOST) {
            result = Math.min(h, size);
        }
    }
    return result;
}
```
上面三步完成之后就到了最后的重点onDraw()方法了。根据思路我们应该先画出已完成进度的矩形，再画出百分比文字，最后画出未完成的进度。需要注意的是绘制文字的时候Y轴起点为文字的baseline，而不是文字的顶部。下面给出了绘制时大概的思路图：  
![这里写图片描述](/uploads/20151023/20151023214750.png)
``` java
@Override
protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    canvas.save();
    // 设置画笔颜色为已完成的颜色
    mPaint.setColor(mProgressColor);
    float p = getPercent();
    // 得到百分比
    String s = (int) (p * 100) + "%";
    // 测量文字的宽度
    float textWidth = mPaint.measureText(s);
    
    canvas.drawRect(getPaddingLeft(), (mHeight - mProgressHeight) / 2,
            getPaddingLeft() + (realWidth - textWidth - mTextOffset) * p, (mHeight + mProgressHeight) / 2, mPaint);
	// 测量文字的高度
    float textHeight = Math.abs((mPaint.descent() + mPaint.ascent()) / 2);
			
    canvas.drawText(s, getPaddingLeft() + (realWidth - textWidth - mTextOffset) * p + mTextOffset / 2,
            textHeight + mHeight / 2, mPaint);
	// 设置画笔颜色为未完成的颜色
    mPaint.setColor(mTotalColor);

    canvas.drawRect(getPaddingLeft() + (realWidth - textWidth - mTextOffset) * p + mTextOffset + textWidth, (mHeight - mProgressHeight) / 2,
            mWidth - getPaddingRight(), (mHeight + mProgressHeight) / 2, mPaint);
    canvas.restore();
}
```
到了这里整体的View差不多已经写完了，其实总体并没有什么难点。只要搞清思路，相信大家都能定义出自己想要的View。  

以下是完整代码下载地址：  
[PercentProgressView.rar](/uploads/20151023/PercentProgressView.rar)
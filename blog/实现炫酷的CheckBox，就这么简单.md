title: 实现炫酷的CheckBox，就这么简单
date: 2015-12-05 23:10:32
categories: Android Blog
tags: [Android,自定义View]
---
今天给大家带来的是一款全新的CheckBox，是不是对系统自带的CheckBox产生乏味感了呢，那就来看看下面的CheckBox吧！

之前在逛GitHub的时候看到一款比较新颖的CheckBox：[SmoothCheckBox](https://github.com/andyxialm/SmoothCheckBox)，它的效果预览触动到我了，于是趁着今天有空就试着自己写一写。尽管效果可能不如SmoothCheckBox那样动感，但是基本的效果还是实现了。按照惯例，下面就贴出我写的CheckBox的gif： 

![这里写图片描述](/uploads/20151205/20151205234652.gif)

gif的效果可能有点过快，在真机上运行的效果会更好一些。我们主要的思路就是利用属性动画来动态地画出选中状态以及对勾的绘制过程。看到上面的效果图，相信大家都迫不及待地要跃跃欲试了，那就让我们开始吧。

自定义View的第一步：自定义属性。
``` xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
	<declare-styleable name="SmoothCheckBox">
		<!-- 动画持续时间 -->
		<attr name="duration" format="integer"></attr>
		<!-- 边框宽度 -->
		<attr name="strikeWidth" format="dimension|reference"></attr>
		<!-- 边框颜色 -->
		<attr name="borderColor" format="color|reference"></attr>
		<!-- 选中状态的颜色 -->
		<attr name="trimColor" format="color|reference"></attr>
		<!-- 对勾颜色 -->
		<attr name="tickColor" format="color|reference"></attr>
		<!-- 对勾宽度 -->
		<attr name="tickWidth" format="dimension|reference"></attr>
	</declare-styleable>
</resources>
```
我们把CheckBox取名为SmoothCheckBox(没办法(⊙﹏⊙)，这名字挺好听的)，定义了几个等等要用到的属性。这一步很简单，相信大家都熟练了。

接下来看一看`onMeasure(int widthMeasureSpec, int heightMeasureSpec)`:
``` java
@Override
protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    int widthSize = MeasureSpec.getSize(widthMeasureSpec);
    int widthMode = MeasureSpec.getMode(widthMeasureSpec);
    if (widthMode == MeasureSpec.EXACTLY) {
        mWidth = widthSize;
    } else {
        mWidth = 40;
    }

    int heightSize = MeasureSpec.getSize(heightMeasureSpec);
    int heightMode = MeasureSpec.getMode(heightMeasureSpec);
    if (heightMode == MeasureSpec.EXACTLY) {
        mHeight = heightSize;
    } else {
        mHeight = 40;
    }
    setMeasuredDimension(mWidth, mHeight);
    int size = Math.min(mWidth, mHeight);
    center = size / 2;
    mRadius = (int) ((size - mStrokeWidth) / 2 / 1.2f);
    startPoint.set(center * 14 / 30, center * 28 / 30);
    breakPoint.set(center * 26 / 30, center * 40 / 30);
    endPoint.set(center * 44 / 30, center * 20 / 30);

    downLength = (float) Math.sqrt(Math.pow(startPoint.x - breakPoint.x, 2f) + Math.pow(startPoint.y - breakPoint.y, 2f));
    upLength = (float) Math.sqrt(Math.pow(endPoint.x - breakPoint.x, 2f) + Math.pow(endPoint.y - breakPoint.y, 2f));
    totalLength = downLength + upLength;
}
```
一开始是测量了SmoothCheckBox的宽、高度，默认的宽高度随便定义了一个，当然你们可以自己去修改和完善它。然后就是设置半径之类的，最后的startPoint、breakPoint、endPoint分别对应着选中时对勾的三个点(至于为何是这几个数字，那完全是经验值);downLength就是startPoint和breakPoint的距离，而相对应的upLength就是breakPoint和endPoint的距离。即以下图示：

![这里写图片描述](/uploads/20151205/20151205000130.png)

在看`onDraw(Canvas canvas)`之前我们先来看两组动画，分别是选中状态时的动画以及未选中状态的动画：
``` java
// 由未选中到选中的动画
private void checkedAnimation() {
    animatedValue = 0f;
    tickValue = 0f;
	// 选中时底色的动画
    mValueAnimator = ValueAnimator.ofFloat(0f, 1.2f, 1f).setDuration(2 * duration / 5);
    mValueAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
	// 对勾的动画
    mTickValueAnimator = ValueAnimator.ofFloat(0f, 1f).setDuration(3 * duration / 5);
    mTickValueAnimator.setInterpolator(new LinearInterpolator());
    mTickValueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator valueAnimator) {
			// 得到动画执行进度
            tickValue = (float) valueAnimator.getAnimatedValue();
            postInvalidate();
        }
    });
    mValueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator valueAnimator) {
			// 得到动画执行进度
            animatedValue = (float) valueAnimator.getAnimatedValue();
            postInvalidate();
        }
    });
    mValueAnimator.addListener(new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd(Animator animation) {
			//当底色的动画完成后再开始对勾的动画
            mTickValueAnimator.start();
            Log.i(TAG," mTickValueAnimator.start();");
        }
    });
    mValueAnimator.start();
}

// 由选中到未选中的动画
private void uncheckedAnimation() {
    animatedValue = 0f;
    mValueAnimator = ValueAnimator.ofFloat(1f, 0f).setDuration(2 * duration / 5);
    mValueAnimator.setInterpolator(new AccelerateInterpolator());
    mValueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator valueAnimator) {
            animatedValue = (float) valueAnimator.getAnimatedValue();
            postInvalidate();
        }
    });
    mValueAnimator.start();
}
```
这两组动画在点击SmoothCheckBox的时候会调用。相似的，都是在动画执行中得到动画执行的进度，再来调用`postInvalidate();`让SmoothCheckBox重绘。看完这个之后就是终极大招`onDraw(Canvas canvas)`了:
``` java
@Override
protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    canvas.save();
    drawBorder(canvas);
    drawTrim(canvas);
    if (isChecked) {
        drawTick(canvas);
    }
    canvas.restore();
}

// 画对勾
private void drawTick(Canvas canvas) {
	// 得到画对勾的进度
    float temp = tickValue * totalLength;
    Log.i(TAG, "temp:" + temp + "downlength :" + downLength);
	//判断是否是刚开始画对勾的时候,即等于startPoint
    if (Float.compare(tickValue, 0f) == 0) {
        Log.i(TAG, "startPoint : " + startPoint.x + ", " + startPoint.y);
        path.reset();
        path.moveTo(startPoint.x, startPoint.y);
    }
	// 如果画对勾的进度已经超过breakPoint的时候,即(breakPoint,endPoint]
    if (temp > downLength) {
        path.moveTo(startPoint.x, startPoint.y);
        path.lineTo(breakPoint.x, breakPoint.y);
        Log.i(TAG, "endPoint : " + endPoint.x + ", " + endPoint.y);
        path.lineTo((endPoint.x - breakPoint.x) * (temp - downLength) / upLength + breakPoint.x, (endPoint.y - breakPoint.y) * (temp - downLength) / upLength + breakPoint.y);
    } else {
		//画对勾的进度介于startPoinit和breakPoint之间，即(startPoint,breakPoint]
        Log.i(TAG, "down x : " + (breakPoint.x - startPoint.x) * temp / downLength + ",down y: " + (breakPoint.y - startPoint.y) * temp / downLength);
        path.lineTo((breakPoint.x - startPoint.x) * temp / downLength + startPoint.x, (breakPoint.y - startPoint.y) * temp / downLength + startPoint.y);
    }
    canvas.drawPath(path, tickPaint);
}

// 画边框
private void drawBorder(Canvas canvas) {
    float temp;
	// 通过animatedValue让边框产生一个“OverShooting”的动画
    if (animatedValue > 1f) {
        temp = animatedValue * mRadius;
    } else {
        temp = mRadius;
    }
    canvas.drawCircle(center, center, temp, borderPaint);
}

// 画checkbox内部
private void drawTrim(Canvas canvas) {
    canvas.drawCircle(center, center, (mRadius - mStrokeWidth) * animatedValue, trimPaint);
}
```
`onDraw(Canvas canvas)`代码中的逻辑基本都加了注释，主要就是原理搞懂了就比较简单了。在绘制对勾时要区分当前处于绘制对勾的哪种状态，然后对应做处理画出线条，剩下的就简单了。关于SmoothCheckBox的讲解到这里就差不多了。

下面就贴出SmoothCheckBox的完整代码：
``` java
public class SmoothCheckBox extends View implements View.OnClickListener {

    // 动画持续时间
    private long duration;
    // 边框宽度
    private float mStrokeWidth;
    // 对勾宽度
    private float mTickWidth;
    // 内饰画笔
    private Paint trimPaint;
    // 边框画笔
    private Paint borderPaint;
    // 对勾画笔
    private Paint tickPaint;
    // 默认边框宽度
    private float defaultStrikeWidth;
    // 默认对勾宽度
    private float defaultTickWidth;
    // 宽度
    private int mWidth;
    // 高度
    private int mHeight;
    // 边框颜色
    private int borderColor;
    // 内饰颜色
    private int trimColor;
    // 对勾颜色
    private int tickColor;
    // 半径
    private int mRadius;
    // 中心点
    private int center;
    // 是否是选中
    private boolean isChecked;
    //对勾向下的长度
    private float downLength;
    //对勾向上的长度
    private float upLength;
    // 对勾的总长度
    private float totalLength;
    // 监听器
    private OnCheckedChangeListener listener;

    private ValueAnimator mValueAnimator;

    private ValueAnimator mTickValueAnimator;

    private float animatedValue;

    private float tickValue;
    // 对勾开始点
    private Point startPoint = new Point();
    // 对勾转折点
    private Point breakPoint = new Point();
    // 对勾结束点
    private Point endPoint = new Point();

    private static final String TAG = "SmoothCheckBox";

    private static final String KEY_INSTANCE_STATE = "InstanceState";

    private Path path = new Path();

    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
        this.listener = listener;
    }

    public SmoothCheckBox(Context context) {
        this(context, null);
    }

    public SmoothCheckBox(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SmoothCheckBox(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.SmoothCheckBox);
        duration = a.getInt(R.styleable.SmoothCheckBox_duration, 600);

        defaultStrikeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, getResources().getDisplayMetrics());
        mStrokeWidth = a.getDimension(R.styleable.SmoothCheckBox_strikeWidth, defaultStrikeWidth);
        defaultTickWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, getResources().getDisplayMetrics());
        mTickWidth = a.getDimension(R.styleable.SmoothCheckBox_tickWidth, defaultTickWidth);
        borderColor = a.getColor(R.styleable.SmoothCheckBox_borderColor, getResources().getColor(android.R.color.darker_gray));
        trimColor = a.getColor(R.styleable.SmoothCheckBox_trimColor, getResources().getColor(android.R.color.holo_green_light));
        tickColor = a.getColor(R.styleable.SmoothCheckBox_tickColor, getResources().getColor(android.R.color.white));
        a.recycle();

        trimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trimPaint.setStyle(Paint.Style.FILL);
        trimPaint.setColor(trimColor);

        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStrokeWidth(mStrokeWidth);
        borderPaint.setColor(borderColor);
        borderPaint.setStyle(Paint.Style.STROKE);

        tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tickPaint.setColor(tickColor);
        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setStrokeCap(Paint.Cap.ROUND);
        tickPaint.setStrokeWidth(mTickWidth);

        setOnClickListener(this);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        if (widthMode == MeasureSpec.EXACTLY) {
            mWidth = widthSize;
        } else {
            mWidth = 40;
        }

        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        if (heightMode == MeasureSpec.EXACTLY) {
            mHeight = heightSize;
        } else {
            mHeight = 40;
        }
        setMeasuredDimension(mWidth, mHeight);
        int size = Math.min(mWidth, mHeight);
        center = size / 2;
        mRadius = (int) ((size - mStrokeWidth) / 2 / 1.2f);
        startPoint.set(center * 14 / 30, center * 28 / 30);
        breakPoint.set(center * 26 / 30, center * 40 / 30);
        endPoint.set(center * 44 / 30, center * 20 / 30);

        downLength = (float) Math.sqrt(Math.pow(startPoint.x - breakPoint.x, 2f) + Math.pow(startPoint.y - breakPoint.y, 2f));
        upLength = (float) Math.sqrt(Math.pow(endPoint.x - breakPoint.x, 2f) + Math.pow(endPoint.y - breakPoint.y, 2f));
        totalLength = downLength + upLength;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        drawBorder(canvas);
        drawTrim(canvas);
        if (isChecked) {
            drawTick(canvas);
        }
        canvas.restore();
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Bundle bundle = new Bundle();
        bundle.putParcelable(KEY_INSTANCE_STATE, super.onSaveInstanceState());
        bundle.putBoolean(KEY_INSTANCE_STATE, isChecked);
        return bundle;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state instanceof Bundle) {
            Bundle bundle = (Bundle) state;
            boolean isChecked = bundle.getBoolean(KEY_INSTANCE_STATE);
            setChecked(isChecked);
            super.onRestoreInstanceState(bundle.getParcelable(KEY_INSTANCE_STATE));
            return;
        }
        super.onRestoreInstanceState(state);
    }

    // 切换状态
    private void toggle() {
        isChecked = !isChecked;
        if (listener != null) {
            listener.onCheckedChanged(this, isChecked);
        }
        if (isChecked) {
            checkedAnimation();
        } else {
            uncheckedAnimation();
        }
    }

    // 由未选中到选中的动画
    private void checkedAnimation() {
        animatedValue = 0f;
        tickValue = 0f;
        mValueAnimator = ValueAnimator.ofFloat(0f, 1.2f, 1f).setDuration(2 * duration / 5);
        mValueAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        mTickValueAnimator = ValueAnimator.ofFloat(0f, 1f).setDuration(3 * duration / 5);
        mTickValueAnimator.setInterpolator(new LinearInterpolator());
        mTickValueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                tickValue = (float) valueAnimator.getAnimatedValue();
                postInvalidate();
            }
        });
        mValueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                animatedValue = (float) valueAnimator.getAnimatedValue();
                postInvalidate();
            }
        });
        mValueAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mTickValueAnimator.start();
                Log.i(TAG," mTickValueAnimator.start();");
            }
        });
        mValueAnimator.start();
    }

    // 由选中到未选中的动画
    private void uncheckedAnimation() {
        animatedValue = 0f;
        mValueAnimator = ValueAnimator.ofFloat(1f, 0f).setDuration(2 * duration / 5);
        mValueAnimator.setInterpolator(new AccelerateInterpolator());
        mValueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                animatedValue = (float) valueAnimator.getAnimatedValue();
                postInvalidate();
            }
        });
        mValueAnimator.start();
    }

    // 画对勾
    private void drawTick(Canvas canvas) {
        float temp = tickValue * totalLength;
        Log.i(TAG, "temp:" + temp + "downlength :" + downLength);
        if (Float.compare(tickValue, 0f) == 0) {
            Log.i(TAG, "startPoint : " + startPoint.x + ", " + startPoint.y);
            path.reset();
            path.moveTo(startPoint.x, startPoint.y);
        }
        if (temp > downLength) {
            path.moveTo(startPoint.x, startPoint.y);
            path.lineTo(breakPoint.x, breakPoint.y);
            Log.i(TAG, "endPoint : " + endPoint.x + ", " + endPoint.y);
            path.lineTo((endPoint.x - breakPoint.x) * (temp - downLength) / upLength + breakPoint.x, (endPoint.y - breakPoint.y) * (temp - downLength) / upLength + breakPoint.y);
        } else {
            Log.i(TAG, "down x : " + (breakPoint.x - startPoint.x) * temp / downLength + ",down y: " + (breakPoint.y - startPoint.y) * temp / downLength);
            path.lineTo((breakPoint.x - startPoint.x) * temp / downLength + startPoint.x, (breakPoint.y - startPoint.y) * temp / downLength + startPoint.y);
        }
        canvas.drawPath(path, tickPaint);
    }

    // 画边框
    private void drawBorder(Canvas canvas) {
        float temp;
        if (animatedValue > 1f) {
            temp = animatedValue * mRadius;
        } else {
            temp = mRadius;
        }
        canvas.drawCircle(center, center, temp, borderPaint);
    }

    // 画checkbox内部
    private void drawTrim(Canvas canvas) {
        canvas.drawCircle(center, center, (mRadius - mStrokeWidth) * animatedValue, trimPaint);
    }

    @Override
    public void onClick(View view) {
        toggle();
    }

    /**
     * 判断checkbox是否选中状态
     *
     * @return
     */
    public boolean isChecked() {
        return isChecked;
    }

    /**
     * 设置checkbox的状态
     *
     * @param isChecked 是否选中
     */
    public void setChecked(boolean isChecked) {
        this.setChecked(isChecked, false);
    }

    /**
     * 设置checkbox的状态
     *
     * @param isChecked   是否选中
     * @param isAnimation 切换时是否有动画
     */
    public void setChecked(boolean isChecked, boolean isAnimation) {
        this.isChecked = isChecked;
        if (isAnimation) {
            if (isChecked) {
                checkedAnimation();
            } else {
                uncheckedAnimation();
            }
        } else {
            animatedValue = isChecked ? 1f : 0f;
            tickValue = 1f;
            invalidate();
        }
        if (listener != null) {
            listener.onCheckedChanged(this, isChecked);
        }
    }

    public interface OnCheckedChangeListener {
        void onCheckedChanged(SmoothCheckBox smoothCheckBox, boolean isChecked);
    }
}
```
下面是SmoothCheckBox的源码下载，如果有问题可以在下面留言来交流：

[SmoothCheckBox.rar](/uploads/20151205/SmoothCheckBox.rar)

GitHub:

[SmoothCheckBox](https://github.com/yuqirong/SmoothCheckBox)

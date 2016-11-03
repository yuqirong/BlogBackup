title: 快速打造仿Android联系人界面
date: 2016-03-22 15:53:24
categories: Android Blog
tags: [Android,自定义View]
---
有段时间没写博客了，趁今天有空就写了一篇。今天的主题就是仿联系人界面。相信大家在平时都见过，就是可以实现快速索引的侧边栏。比如在美团中选择城市的界面：

![这里写图片描述](/uploads/20160322/20160322200035.png)

我们可以看到在右侧有一个支持快速索引的栏。接下来，我们就要实现这种索引栏。

首先是`attrs.xml`，定义了三个自定义属性：

``` xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <declare-styleable name="QuickIndexBar">
        // 字体的颜色
        <attr name="font_color" format="color|reference"></attr>
        // 选中时字体的颜色
        <attr name="selected_font_color" format="color|reference"></attr>
        // 字体的大小
        <attr name="font_size" format="dimension|reference"></attr>
    </declare-styleable>
</resources>
```

之后我们创建一个类继承自`View`，类名就叫`QuickIndexBar`：

``` java
// 默认字体颜色
private int defaultFontColor = Color.WHITE;
// 默认选中字体颜色
private int defaultSelectedFontColor = Color.GRAY;
// 字体颜色
private int fontColor;
// 选中字体颜色
private int selectedFontColor;
// 字体大小
private float fontSize;
// 默认字体大小
private float defaultfontSize = 12;
// 上次触摸的字母单元格
int lastSelected = -1;
// 这次触摸的字母单元格
int selected = -1;

public QuickIndexBar(Context context) {
    this(context, null);
}

public QuickIndexBar(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
}

public QuickIndexBar(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);

    TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.QuickIndexBar);
    fontColor = a.getColor(R.styleable.QuickIndexBar_font_color, defaultFontColor);
    selectedFontColor = a.getColor(R.styleable.QuickIndexBar_selected_font_color, defaultSelectedFontColor);
    fontSize = a.getDimension(R.styleable.QuickIndexBar_font_size,
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, defaultfontSize,
                    getContext().getResources().getDisplayMetrics()));
    a.recycle();
    mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    mPaint.setColor(fontColor);
    mPaint.setTypeface(Typeface.DEFAULT_BOLD);
    mPaint.setTextSize(fontSize);

}
```

上面的代码就是在构造器中初始化了自定义属性，大家应该都能看懂。

``` java
// 快速索引的字母
public static final String[] INDEX_ARRAYS = new String[]{"#", "A", "B",
        "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O",
        "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"};
// 控件的宽度
private int width;
// 控件的高度
private int height;
// 字母单元格的宽度
private float cellHeight;

/**
 * 得到控件的大小
 */
@Override
protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    width = getMeasuredWidth();
    height = getMeasuredHeight();
    //  得到字母单元格的高度
    cellHeight = height * 1.0f / INDEX_ARRAYS.length;
}
```

然后在`onSizeChanged(int w, int h, int oldw, int oldh)`中获取`width`和`height`。还要计算`cellHeight`,也就是`INDEX_ARRAYS`中每个字符串所占用的高度，以便在`onDraw(Canvas canvas)`中使用。

我们来看看`onDraw(Canvas canvas)`：

``` java
@Override
protected void onDraw(Canvas canvas) {
    // 遍历画出index
    for (int i = 0; i < INDEX_ARRAYS.length; i++) {
        // 测出字体的宽度
        float x = width / 2 - mPaint.measureText(INDEX_ARRAYS[i]) / 2;
        // 得到字体的高度
        Paint.FontMetrics fm = mPaint.getFontMetrics();
        double fontHeight = Math.ceil(fm.descent - fm.ascent);

        float y = (float) ((i + 1) * cellHeight - cellHeight / 2 + fontHeight / 2);
        if (i == selected) {
            mPaint.setColor(lastSelected == -1 ? fontColor : selectedFontColor);
        } else {
            mPaint.setColor(fontColor);
        }
        // 绘制索引的字母 (x,y)为字母左下角的坐标
        canvas.drawText(INDEX_ARRAYS[i], x, y, mPaint);
    }

}
```

在代码中去遍历`INDEX_ARRAYS`，测量出字母的宽度和高度。这里要注意的是，`canvas.drawText(String text, float x, float y, Paint paint)`中的 x,y 指的是字母左下角的坐标，并不是“原点”。

别忘了我们还要对`QuickIndexBar`的触摸事件作出处理。所以我们要重写onTouchEvent(MotionEvent event)：

``` java
/**
 * 设置当索引改变的监听器
 */
public interface OnIndexChangeListener {
    /**
     * 当索引改变
     *
     * @param selectIndex 索引值
     */
    void onIndexChange(int selectIndex);

    /**
     * 当手指抬起
     */
    void onActionUp();
}

public void setOnIndexChangeListener(OnIndexChangeListener listener) {
    this.listener = listener;
}

@Override
public boolean onTouchEvent(MotionEvent event) {
    float y;
    switch (event.getAction()) {
        case MotionEvent.ACTION_DOWN:
        case MotionEvent.ACTION_MOVE:
            y = event.getY();
            // 计算出触摸的是哪个字母单元格
            selected = (int) (y / cellHeight);
            if (selected >= 0 && selected < INDEX_ARRAYS.length) {
                if (selected != lastSelected) {
                    if (listener != null) {
                        listener.onIndexChange(selected); // 回调监听器的方法
                    }
                    Log.i(TAG, INDEX_ARRAYS[selected]);
                }
                lastSelected = selected;
            }
            break;
        case MotionEvent.ACTION_UP:
            // 把上次的字母单元格重置
            lastSelected = -1;
            listener.onActionUp();
            break;
    }
    invalidate(); // 重绘视图
    return true;
}
```

在`ACTION_DOWN`和`ACTION_MOVE`计算出了触摸的y值对应的是索引中的哪个字母，然后回调了监听器；而在`ACTION_UP`中重置了`lastSelected`，回调了监听器。

这样，我们就把`QuickIndexBar`写好了，关于`QuickIndexBar`使用的代码就不贴出来了，太长了。如果有需要，可以下载下面的Demo，里面都有注释。Demo的效果图如下：

![这里写图片描述](/uploads/20160322/20160322211942.gif)

好了，今天就到这里了。have fun!

源码下载：

[ContactPicker.rar](/uploads/20160322/ContactPicker.rar)

GitHub：

[ContactPicker](https://github.com/yuqirong/ContactPicker)
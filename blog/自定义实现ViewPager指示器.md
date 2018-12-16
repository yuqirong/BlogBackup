title: 自定义实现ViewPager指示器
date: 2016-04-27 20:09:19
categories: Android Blog
tags: [Android,自定义View]
---
今天来更新一发“自定义实现 ViewPager 指示器”。 ViewPager 指示器相信大家都用过吧，从一开始 JW 大神的 [ViewPagerIndicator](https://github.com/JakeWharton/ViewPagerIndicator) ，到现在 Material Design 中的 TabLayout 。GitHub 上还有其他形形色色的指示器。那么肯定有人会问：既然有了这么多的指示器可以用，那为什么还要自己自定义呢？其实，我们学习了自定义指示器之后，可以知道 ViewPager 指示器的原理，还可以提高我们代码的水平哦！那还等什么，一起来学习吧。

首先放上一张效果图，亮亮眼：

![这里写图片描述](/uploads/20160427/20160427152924.gif)

接下来我们来大致地分析一下思路： ViewPager 指示器我们可以看作是一个横向的 LinearLayout ，相对应的 Tab 可以直接使用 TextView 来实现。而 LinearLayout 中有许多个 TextView ，当我们点击其中的 TextView 时， ViewPager 就切换到对应的 item 上。而当我们手动滑动 ViewPager 时，根据 OnPageChangeListener 来动态地改变指示器。好了，基本上思路就是这样了，下面就来看看代码了。

自定义的属性 attrs.xml ：

``` xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <declare-styleable name="ViewPagerIndicator">
	<!-- tab可见的数量 -->
	<attr name="visible_tab_num" format="integer"></attr>
	<!-- tab选中时的颜色 -->
	<attr name="selected_color" format="color|reference"></attr>
	<!-- tab未选中时的颜色 -->
	<attr name="unselected_color" format="color|reference"></attr>
	<!-- tab中字体的大小 -->
	<attr name="text_size" format="dimension|reference"></attr>
	<!-- tab选中时横线的高度 -->
	<attr name="indicator_height" format="dimension|reference"></attr>
    </declare-styleable>
</resources>
```

自定义的属性基本上就以上几种，如果自己有其他的需求，可以另外添加。

之后我们就创建一个类，名字就叫 ViewPagerIndicator 了：

``` java
public class ViewPagerIndicator extends LinearLayout {

    // tab可见数
    private int visibleTabNum;
    // 选中的颜色
    private int selectedColor;
    // 未选中的颜色
    private int unselectedColor;
    // 屏幕宽度
    private int screenWidth;
    // tab的宽度
    private int tabWidth;
    // 横线的偏移
    private float offset;
    // 画笔
    private Paint mPaint;
    // 高度
    private int height;
    // 横线的高度
    private float indicatorHeight;
    // 默认横线的高度
    private float defaultIndicatorHeight;
    // viewpager当前页数
    private int mCurrentItem;
    // 字体大小
    private float textSize;
    // 默认字体大小
    private float defaultTextSize;

    private ViewPager mViewPager;
    // 滑动的最小距离
    private int touchSlop;
    // 上次触摸的x轴坐标
    private float lastX;

    private static final String TAG = "ViewPagerIndicator";

    public ViewPagerIndicator(Context context) {
        this(context, null);
    }

    public ViewPagerIndicator(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ViewPagerIndicator(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        // 设置横向
        setOrientation(LinearLayout.HORIZONTAL);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ViewPagerIndicator);
        selectedColor = a.getColor(R.styleable.ViewPagerIndicator_selected_color, Color.BLUE);
        unselectedColor = a.getColor(R.styleable.ViewPagerIndicator_unselected_color, Color.WHITE);
        visibleTabNum = a.getInt(R.styleable.ViewPagerIndicator_visible_tab_num, 4);
        // 默认字体大小
        defaultTextSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10, context.getResources().getDisplayMetrics());
        textSize = a.getDimension(R.styleable.ViewPagerIndicator_text_size, defaultTextSize);
        // 默认下划横线高度
        defaultIndicatorHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, context.getResources().getDisplayMetrics());
        indicatorHeight = a.getDimension(R.styleable.ViewPagerIndicator_indicator_height, defaultIndicatorHeight);
        a.recycle();
        screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        tabWidth = screenWidth / visibleTabNum;
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setColor(selectedColor);
        mPaint.setStrokeWidth(indicatorHeight);
        mPaint.setStyle(Paint.Style.FILL);
        // 得到touchSlop
        touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
    }
	...
}
```

上面的代码主要是初始化了自定义属性，还有得到了 tabWidth 以便后面使用。

当然，如果用户旋转了屏幕，那么 tabWidth 是会改变的。所以我们应该在 `onSizeChanged` 里重新赋值：

``` java
@Override
protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    // 当大小改变时，得到一个tab的宽度
    tabWidth = w / visibleTabNum;
    height = h;
}
```
这样，无论是横屏还是竖屏，在屏幕上可见的 Tab 数量永远是固定的(即 visibleTabNum 的值)。之后，我们先来“画”出 Tab 被选中时底下的那条横线。

``` java
@Override
protected void dispatchDraw(Canvas canvas) {
    if (mViewPager != null) {
        canvas.save();
        // 绘制横线
        canvas.drawLine(offset, height - indicatorHeight, offset + tabWidth, height - indicatorHeight, mPaint);
        canvas.restore();
    }
    super.dispatchDraw(canvas);
}

// 设置offset
private void setOffset(float offset) {
    this.offset = offset;
    invalidate();
}
```
mViewPager 的赋值是在`setViewPager(ViewPager viewPager)`方法中完成的，这个方法放在下面去讲。而其中的 offset 是偏移量。当用户滑动切换 ViewPager 时，Tab 底下的横线应该也要做相应的位移，而这就是由 offset 来完成的。调用 `setOffset(float offset)` 方法，可以引起视图重绘。另外横线的高度 indicatorHeight 可以由用户自定义的，这里的代码还是比较简单的，相信大家都可以看懂的。

到这就来讲讲 setViewPager 方法了。当我们想要把 ViewPager 和 ViewPagerIndicator 关联起来时，可以给外部设置一个 `setViewPager(ViewPager viewPager)` 方法，那下面就是该方法的源码了：

``` java
/**
 * 设置ViewPager， 请确保在设置了adapter之后调用该方法
 *
 * @param viewPager
 */
public void setViewPager(ViewPager viewPager) {
    if (viewPager == null) {
        return;
    }
    this.mViewPager = viewPager;
    // 得到适配器
    PagerAdapter adapter = viewPager.getAdapter();
    // adapter不能为空
    if (adapter == null) {
        throw new IllegalArgumentException("the adapter of viewpager must be not null..");
    }
    // 先移除所有的子view
    this.removeAllViews();
    // 添加Textview
    for (int i = 0; i < adapter.getCount(); i++) {
        createTextView(adapter.getPageTitle(i).toString(), i);
    }

    mCurrentItem = viewPager.getCurrentItem();
    ((TextView) getChildAt(mCurrentItem)).setTextColor(selectedColor);
    viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            // 设置了横线的偏移，并引起重绘
            setOffset((position + positionOffset) * tabWidth);
            // tab也要进行相应的移动，若当前的tab是倒数第二个，则不移动。
            if (position + positionOffset + 1 > visibleTabNum - 1 && position + positionOffset + 1 <= getChildCount() - 1) {
                scrollTo((int) ((position + positionOffset - visibleTabNum + 2) * tabWidth), 0);
            }
        }

        @Override
        public void onPageSelected(int position) {
            // 字体颜色改变
            ((TextView) getChildAt(mCurrentItem)).setTextColor(unselectedColor);
            ((TextView) getChildAt(position)).setTextColor(selectedColor);
            mCurrentItem = position;
        }

        @Override
        public void onPageScrollStateChanged(int state) {

        }
    });
}
```

从方法内部可以看出，我们要得到 ViewPager 的 adapter 。如果 adapter 为空则抛出异常。之后根据 adapter 的 count 数量去创建相对应的 TextView 作为 Tab 。下面为 `createTextView` 方法：

``` java
// 添加textview到ViewPagerIndicator中
private void createTextView(String title, int i) {
    TextView tv = new TextView(getContext());
    LinearLayout.LayoutParams params = new LayoutParams(tabWidth, LayoutParams.MATCH_PARENT);
    tv.setLayoutParams(params);
    tv.setText(title);
    tv.setGravity(Gravity.CENTER);
    tv.setTextColor(unselectedColor);
    tv.setTag(i);
    tv.setTextSize(textSize);
    tv.setOnClickListener(tvClickListener);
    this.addView(tv);
}

// textview的点击监听器
OnClickListener tvClickListener = new OnClickListener() {
    @Override
    public void onClick(View v) {
        int i = (int) v.getTag();
        if (mViewPager != null) {
            mViewPager.setCurrentItem(i, true);
        }
    }
};
```
在 `createTextView` 方法中，使用了addView 来动态地添加 Tab 。这里有一处比较巧妙的地方：我们把当前 TextView 的索引 i 存储到了 Tag 中。而当用户点击 Tab 时，在监听器中我们取出那个 Tag 值，这样就知道了用户点击的是哪个 Tab 了，并且让 ViewPager 切换到那个页面下。

好了，我们再回过头继续看之前的 `setViewPager(ViewPager viewPager)` 方法，我们看到给 viewPager 设置了 OnPageChangeListener 。在 OnPageChangeListener 的 onPageScrolled 方法中，根据当前的 position 和 positionOffset 就可以完成选中时那条横线的移动。并且为了选中的 Tab 出现在屏幕中，ViewPagerIndicator 也要用 scrollTo 方法来做相应地移动。而在 onPageSelected 方法中，我们把选中的 Tab 中的字体颜色更改为已选中的颜色，之前选中的改成未选中颜色。

到这里，整体完成得差不多了。但是如果我们想让 ViewPagerIndicator 可以滑动的话，还要重写 `onInterceptTouchEvent(MotionEvent ev)` 和 `onTouchEvent(MotionEvent event)` 两个方法。

``` java
@Override
public boolean onInterceptTouchEvent(MotionEvent ev) {
    boolean result = false;
    switch (ev.getAction()) {
        case MotionEvent.ACTION_DOWN:
            lastX = ev.getX();
            break;
        case MotionEvent.ACTION_MOVE:
            float offsetX = ev.getX() - lastX;
            // 当移动大于touchSlop时，拦截该触摸事件
            if (Math.abs(offsetX) >= touchSlop) {
                result = true;
            } else {
                result = false;
            }
            break;
        case MotionEvent.ACTION_UP:
            lastX = 0f;
            break;
    }
    Log.i(TAG, "onInterceptTouchEvent result = " + result);
    return result;
}

@Override
public boolean onTouchEvent(MotionEvent event) {
    float x = event.getX();
    switch (event.getAction()) {
        case MotionEvent.ACTION_DOWN:
            lastX = x;
            break;
        case MotionEvent.ACTION_MOVE:
            float offsetX = x - lastX;
            // 滑动相应的距离
            scrollBy(-(int) offsetX, 0);
            // 最左边界值检查
            if (getScrollX() < 0) {
                scrollTo(0, 0);
            }
            // 最右边界值检查
            if (getScrollX() > tabWidth * (getChildCount() - visibleTabNum)) {
                scrollTo(tabWidth * (getChildCount() - visibleTabNum), 0);
            }
            lastX = x;
            break;
        case MotionEvent.ACTION_UP:
            lastX = 0f;
            break;
    }
    return true;
}
```
在 `onInterceptTouchEvent(MotionEvent ev)` 中，若滑动的距离超过 touchSlop ，则拦截该触摸事件自己处理，否则传递给子View。而在 `onTouchEvent(MotionEvent event)` 中，使用了 scrollBy 来处理滑动，并且设置了边界值的检查。

在这里，整体代码讲解完成了。其实 ViewPagerIndicator 本质就是使用了 OnPageChangeListener 以及当用户点击时切换 ViewPager 到指定页面，并没有太难的地方。以后我们自己也可以实现各种炫酷的 ViewPagerIndicator 了！

下面提供源码的下载链接：

[ViewPagerIndicator.rar](/uploads/20160427/ViewPagerIndicator.rar)

have a nice day !~~
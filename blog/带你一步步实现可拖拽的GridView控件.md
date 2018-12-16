title: 带你一步步实现可拖拽的GridView控件
date: 2016-02-15 20:46:17
categories: Android Blog
tags: [Android,自定义View]
---
经常使用网易新闻的童鞋都知道在网易新闻中有一个新闻栏目管理，其中GridView的item是可以拖拽的，效果十分炫酷。具体效果如下图：

![这里写图片描述](/uploads/20160215/20160215203853.gif)

是不是也想自己也想实现出相同的效果呢？那就一起来往下看吧。

首先我们来梳理一下思路：

1. 当用户长按选择一个item时，将该item隐藏，然后用WindowManager添加一个新的window，该window与所选择item一模一样，并且跟随用户手指滑动而不断改变位置。
2. 当window的位置坐标在GridView里面时，使用`pointToPosition (int x, int y)`方法来判断对应的应该是哪个item，在adapter中作出数据集相应的变化，然后做出平移的动画。
3. 当用户手指抬起时，把window移除，使用`notifyDataSetChanged()`做出GridView更新。

讲完了思路后，我们就来实践一下吧，把这个控件取名为DragGridView。

``` java
public DragGridView(Context context) {
    this(context, null);
}

public DragGridView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
}

public DragGridView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    setOnItemLongClickListener(this);
}
```

手指在Item上长按时
=============
首先在构造器中得到WindowManager对象以及设置长按监听器，所以只有长按item才能拖拽。

``` java
@Override
public boolean onInterceptTouchEvent(MotionEvent ev) {
    switch (ev.getAction()) {
        case MotionEvent.ACTION_DOWN:
            mWindowX = ev.getRawX();
            mWindowY = ev.getRawY();
            break;
        case MotionEvent.ACTION_MOVE:
            break;
        case MotionEvent.ACTION_UP:
            break;
    }
    return super.onInterceptTouchEvent(ev);
}
```

然后在`onInterceptTouchEvent(MotionEvent ev)`中得到手指下落时的`ev.getRawX()`和`ev.getRawY()`，以备后面的计算使用。至于`getRawX()`和`getX()`的区别这里就不再讲述了，如果有不懂的可以自行百度。

下面就是`onItemLongClick(AdapterView<?> parent, View view, int position, long id)`方法了，我们在DragGridView中定义了两种模式：`MODE_DRAG`和`MODE_NORMAL`，分别对应着item拖拽和item不拖拽：

``` java
@Override
public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
    if (mode == MODE_DRAG) {
        return false;
    }
    this.view = view;
    this.position = position;
    this.tempPosition = position;
    mX = mWindowX - view.getLeft() - this.getLeft();
    mY = mWindowY - view.getTop() - this.getTop();
    initWindow();
    return true;
}
```

在onItemLongClick()中先判断了一下模式，只有在`MODE_NORMAL`的情况下才会添加window。然后计算出mX和mY。可能有些童鞋在mX和mY的计算上看不懂，我给出了一个图示：

![这里写图片描述](/uploads/20160215/20160215210800.png)

其中红点是手指按下的坐标，也就是(mWindowX,mWindowY)这个点；绿边框为DragGridView，因为DragGridView有可能会有margin值；所以this.getLeft()就是绿边框到屏幕的距离，而view.getLeft()就是长按的Item的左边到绿边框的距离。这几个值相减就得到了mX。同理，mY也是这样得到的。

然后来看看`initWindow();`这个方法：

``` java
/**
 * 初始化window
 */
private void initWindow() {
    if (dragView == null) {
        dragView = View.inflate(getContext(), R.layout.drag_item, null);
        TextView tv_text = (TextView) dragView.findViewById(R.id.tv_text);
        tv_text.setText(((TextView) view.findViewById(R.id.tv_text)).getText());
    }
    if (layoutParams == null) {
        layoutParams = new WindowManager.LayoutParams();
        layoutParams.type = WindowManager.LayoutParams.TYPE_PHONE;
        layoutParams.format = PixelFormat.RGBA_8888;
        layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;  //悬浮窗的行为，比如说不可聚焦，非模态对话框等等
        layoutParams.width = view.getWidth();
        layoutParams.height = view.getHeight();
        layoutParams.x = view.getLeft() + this.getLeft();  //悬浮窗X的位置
        layoutParams.y = view.getTop() + this.getTop();  //悬浮窗Y的位置
        view.setVisibility(INVISIBLE);
    }

    mWindowManager.addView(dragView, layoutParams);
    mode = MODE_DRAG;
}
```

在`initWindow()`中，我们先创建了一个dragView，而dragView里面的内容与长按的Item的内容完全一致。然后创建`WindowManager.LayoutParams`的对象，把dragView添加到window上去。同时，也要把长按的Item隐藏了。在这里别忘了需要申请显示悬浮窗的权限：

	<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>

手指滑动时
=============
在`initWindow()`之后，我们就要考虑当手指滑动时window也要跟着动了，我们重写`onTouchEvent(MotionEvent ev)`来监听滑动事件，可以看到下面的`updateWindow(ev)`方法。

``` java
@Override
public boolean onTouchEvent(MotionEvent ev) {
    switch (ev.getAction()) {
        case MotionEvent.ACTION_DOWN:
            break;
        case MotionEvent.ACTION_MOVE:
            if (mode == MODE_DRAG) {
                updateWindow(ev);
            }
            break;
        case MotionEvent.ACTION_UP:
            if (mode == MODE_DRAG) {
                closeWindow(ev.getX(), ev.getY());
            }
            break;
    }
    return super.onTouchEvent(ev);
}
```

这里贴出`updateWindow(ev)`方法：

``` java
/**
 * 触摸移动时，window更新
 *
 * @param ev
 */
private void updateWindow(MotionEvent ev) {
    if (mode == MODE_DRAG) {
        float x = ev.getRawX() - mX;
        float y = ev.getRawY() - mY;
        if (layoutParams != null) {
            layoutParams.x = (int) x;
            layoutParams.y = (int) y;
            mWindowManager.updateViewLayout(dragView, layoutParams);
        }
        float mx = ev.getX();
        float my = ev.getY();
        int dropPosition = pointToPosition((int) mx, (int) my);
        Log.i(TAG, "dropPosition : " + dropPosition + " , tempPosition : " + tempPosition);
        if (dropPosition == tempPosition || dropPosition == GridView.INVALID_POSITION) {
            return;
        }
        itemMove(dropPosition);
    }
}
```

在这里，mX和mY就派上用场了。根据`ev.getRawX()`和`ev.getRawY()`分别减去`mX`和`mY`就得到了移动中layoutParams.x和layoutParams.y。再调用`updateViewLayout (View view, ViewGroup.LayoutParams params)`就出现了window跟随手指滑动而滑动的效果。最后根据 `pointToPosition(int x, int y)`返回的值来执行`itemMove(dropPosition);`。

``` java
/**
 * 判断item移动，作出移动动画
 *
 * @param dropPosition
 */
private void itemMove(int dropPosition) {
    TranslateAnimation translateAnimation;
	// 移动的位置在原位置前面时
    if (dropPosition < tempPosition) {
        for (int i = dropPosition; i < tempPosition; i++) {
            View view = getChildAt(i);
            View nextView = getChildAt(i + 1);
            float xValue = (nextView.getLeft() - view.getLeft()) * 1f / view.getWidth();
            float yValue = (nextView.getTop() - view.getTop()) * 1f / view.getHeight();
            translateAnimation =
                    new TranslateAnimation(Animation.RELATIVE_TO_SELF, 0f, Animation.RELATIVE_TO_SELF, xValue, Animation.RELATIVE_TO_SELF, 0f, Animation.RELATIVE_TO_SELF, yValue);
            translateAnimation.setInterpolator(new LinearInterpolator());
            translateAnimation.setFillAfter(true);
            translateAnimation.setDuration(300);
            if (i == tempPosition - 1) {
                translateAnimation.setAnimationListener(animationListener);
            }
            view.startAnimation(translateAnimation);
        }
    } else {
		// 移动的位置在原位置后面时
        for (int i = tempPosition + 1; i <= dropPosition; i++) {
            View view = getChildAt(i);
            View prevView = getChildAt(i - 1);
            float xValue = (prevView.getLeft() - view.getLeft()) * 1f / view.getWidth();
            float yValue = (prevView.getTop() - view.getTop()) * 1f / view.getHeight();
            translateAnimation =
                    new TranslateAnimation(Animation.RELATIVE_TO_SELF, 0f, Animation.RELATIVE_TO_SELF, xValue, Animation.RELATIVE_TO_SELF, 0f, Animation.RELATIVE_TO_SELF, yValue);
            translateAnimation.setInterpolator(new LinearInterpolator());
            translateAnimation.setFillAfter(true);
            translateAnimation.setDuration(300);
            if (i == dropPosition) {
                translateAnimation.setAnimationListener(animationListener);
            }
            view.startAnimation(translateAnimation);
        }
    }
    tempPosition = dropPosition;
}

/**
 * 动画监听器
 */
Animation.AnimationListener animationListener = new Animation.AnimationListener() {
    @Override
    public void onAnimationStart(Animation animation) {

    }

    @Override
    public void onAnimationEnd(Animation animation) {
        // 在动画完成时将adapter里的数据交换位置
        ListAdapter adapter = getAdapter();
        if (adapter != null && adapter instanceof DragGridAdapter) {
            ((DragGridAdapter) adapter).exchangePosition(position, tempPosition, true);
        }
        position = tempPosition;
    }

    @Override
    public void onAnimationRepeat(Animation animation) {

    }
};
```

上面的代码主要是根据dropPosition使要改变位置的Item来做出平移动画，当最后一个要改变位置的Item平移动画完成之后，在adapter中完成数据集的交换。

``` java
/**
 * 给item交换位置
 *
 * @param originalPosition item原先位置
 * @param nowPosition      item现在位置
 */
public void exchangePosition(int originalPosition, int nowPosition, boolean isMove) {
    T t = list.get(originalPosition);
    list.remove(originalPosition);
    list.add(nowPosition, t);
    movePosition = nowPosition;
    this.isMove = isMove;
    notifyDataSetChanged();
}

@Override
public View getView(int position, View convertView, ViewGroup parent) {
    Log.i(TAG, "-------------------------------");
    for (T t : list){
        Log.i(TAG, t.toString());
    }
    View view = getItemView(position, convertView, parent);
    if (position == movePosition && isMove) {
        view.setVisibility(View.INVISIBLE);
    }
    return view;
}
```

手指抬起时
=============
在上面`onTouchEvent(MotionEvent ev)`方法中，可以看到手指抬起时调用了`closeWindow(ev.getX(), ev.getY());`，那就一起来看看：

``` java
/**
 * 关闭window
 *
 * @param x
 * @param y
 */
private void closeWindow(float x, float y) {
    if (dragView != null) {
        mWindowManager.removeView(dragView);
        dragView = null;
        layoutParams = null;
    }
    itemDrop();
    mode = MODE_NORMAL;
}

/**
 * 手指抬起时，item下落
 */
private void itemDrop() {
    if (tempPosition == position || tempPosition == GridView.INVALID_POSITION) {
        getChildAt(position).setVisibility(VISIBLE);
    } else {
        ListAdapter adapter = getAdapter();
        if (adapter != null && adapter instanceof DragGridAdapter) {
            ((DragGridAdapter) adapter).exchangePosition(position, tempPosition, false);
        }
    }
}
```

可以看出主要做的事情就是移除了window，并且也是调用了`exchangePosition(int originalPosition, int nowPosition, boolean isMove)`，不同的是第三个参数isMove传入了false，这样所有的Item都显示出来了。

讲了这么多，来看看最后的效果吧：

![这里写图片描述](/uploads/20160215/20160215212234.gif)

和网易新闻的效果不相上下吧，完整的源码太长就不贴出了，下面提供源码下载：

[DragGridView.rar](/uploads/20160215/DragGridView-master.rar)

GitHub：

[DragGridView](https://github.com/yuqirong/DragGridView)
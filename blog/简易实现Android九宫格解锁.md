title: 简易实现Android九宫格解锁
date: 2016-05-10 20:09:19
categories: Android Blog
tags: [Android,自定义View]
---
前言
===
在平常使用手机的过程中，九宫格解锁是我们经常接触到的。常见的比如有锁屏中的九宫格，还有支付宝中的九宫格等。因为九宫格可以保护用户的隐私，所以它的应用面很广泛。那么今天我们就来自定义一个属于自己的九宫格吧！

首先我们来分析一下实现九宫格解锁的思路：当用户的手指触摸到某一个点时，先判断该点是否在九宫格的某一格范围之内，若在范围内，则该格变成选中的状态；之后用户手指滑动的时候，以该格的圆心为中心，用户手指为终点，两点连线。最后当用户手指抬起时，判断划过的九宫格密码是否和原先的密码匹配。

大致的思路流程就是上面这样的了，下面我们可以来实践一下。

Point 类
===
我们先来创建一个 `Point` 类，用来表示九宫格锁的九个格子。除了坐标 `x` ，`y` 之外，还有三种模式：正常模式、按下模式和错误模式。根据模式不同该格子的颜色会有所不同，这会在下面中说明。

``` java
public class Point {

    private float x;
    private float y;
    // 正常模式
    public static final int NORMAL_MODE = 1;
    // 按下模式
    public static final int PRESSED_MODE = 2;
    // 错误模式
    public static final int ERROR_MODE = 3;
    private int state = NORMAL_MODE;
    // 表示该格的密码，比如“1”、“2”等
    private String mark;

    public String getMark() {
        return mark;
    }

    public void setMark(String mark) {
        this.mark = mark;
    }

    public Point(float x, float y, String mark) {
        this.x = x;
        this.y = y;
        this.mark = mark;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public float getX() {
        return x;
    }

    public void setX(float x) {
        this.x = x;
    }

    public float getY() {
        return y;
    }

    public void setY(float y) {
        this.y = y;
    }

}
```

RotateDegrees类
======
有了上面的 `Point` 类之后，我们还要创建一个 `RotateDegrees` 类，主要作用是计算两个 `Point` 坐标之间的角度：

```
public class RotateDegrees {

    /**
     * 根据传入的point计算出它们之间的角度
     * @param a
     * @param b
     * @return
     */
    public static float getDegrees(Point a, Point b) {
        float degrees = 0;
        float aX = a.getX();
        float aY = a.getY();
        float bX = b.getX();
        float bY = b.getY();

        if (aX == bX) {
            if (aY < bY) {
                degrees = 90;
            } else {
                degrees = 270;
            }
        } else if (bY == aY) {
            if (aX < bX) {
                degrees = 0;
            } else {
                degrees = 180;
            }
        } else {
            if (aX > bX) {
                if (aY > bY) { // 第三象限
                    degrees = 180 + (float) (Math.atan2(aY - bY, aX - bX) * 180 / Math.PI);
                } else { // 第二象限
                    degrees = 180 - (float) (Math.atan2(bY - aY, aX - bX) * 180 / Math.PI);
                }
            } else {
                if (aY > bY) { // 第四象限
                    degrees = 360 - (float) (Math.atan2(aY - bY, bX - aX) * 180 / Math.PI);
                } else { // 第一象限
                    degrees = (float) (Math.atan2(bY - aY, bX - aX) * 180 / Math.PI);
                }
            }
        }
        return degrees;
    }

    /**
     * 根据point和(x,y)计算出它们之间的角度
     * @param a
     * @param bX
     * @param bY
     * @return
     */
    public static float getDegrees(Point a, float bX, float bY) {
        Point b = new Point(bX, bY, null);
        return getDegrees(a, b);
    }

}
```

ScreenLockView 类
=====
然后我们要先准备好关于九宫格的几张图片，比如在九宫格的格子中，`NORMAL_MODE` 模式下是蓝色的，被手指按住时九宫格的格子是绿色的，也就是对应着上面 Point 类的中 `PRESSED_MODE` 模式，还有 `ERROR_MODE` 模式下是红色的。另外还有圆点之间的连线，也是根据模式不同颜色也会不同。在这里我就不把图片贴出来了，想要的童鞋可以下载源码从中获取。

有了图片资源之后，我们要做的就是先在构造器中加载图片：

``` java
public class ScreenLockView extends View {

    private static final String TAG = "ScreenLockView";
    // 错误格子的图片
    private Bitmap errorBitmap;
    // 正常格子的图片
    private Bitmap normalBitmap;
    // 手指按下时格子的图片
    private Bitmap pressedBitmap;
    // 错误时连线的图片
    private Bitmap lineErrorBitmap;
    // 手指按住时连线的图片
    private Bitmap linePressedBitmap;
    // 偏移量，使九宫格在屏幕中央
    private int offset;
    // 九宫格的九个格子是否已经初始化
    private boolean init;
    // 格子的半径
    private int radius;
    // 密码
    private String password = "123456";
    // 九个格子
    private Point[][] points = new Point[3][3];
    private int width;
    private int height;
    private Matrix matrix = new Matrix();
    private float moveX = -1;
    private float moveY = -1;
    // 是否手指在移动
    private boolean isMove;
    // 是否可以触摸，当用户抬起手指，划出九宫格的密码不正确时为不可触摸
    private boolean isTouch = true;
    // 用来存储记录被按下的点
    private List<Point> pressedPoint = new ArrayList<>();
    // 屏幕解锁监听器
    private OnScreenLockListener listener;

    public ScreenLockView(Context context) {
        this(context, null);
    }

    public ScreenLockView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ScreenLockView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        errorBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.bitmap_error);
        normalBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.bitmap_normal);
        pressedBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.bitmap_pressed);
        lineErrorBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.line_error);
        linePressedBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.line_pressed);
        radius = normalBitmap.getWidth() / 2;
    }
	...
}
```

在构造器中我们主要就是把图片加载完成，并且得到了格子的半径，即图片宽度的一半。

之后我们来看看 `onMeasure(int widthMeasureSpec, int heightMeasureSpec)` 方法：

``` java
@Override
protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int widthSize = MeasureSpec.getSize(widthMeasureSpec);
    int widthMode = MeasureSpec.getMode(widthMeasureSpec);
    int heightSize = MeasureSpec.getSize(heightMeasureSpec);
    int heightMode = MeasureSpec.getMode(heightMeasureSpec);
    if (widthSize > heightSize) {
        offset = (widthSize - heightSize) / 2;
    } else {
        offset = (heightSize - widthSize) / 2;
    }
    setMeasuredDimension(widthSize, heightSize);
}
```

在 `onMeasure(int widthMeasureSpec, int heightMeasureSpec)` 方法中，主要得到对应的偏移量，以便在下面的 `onDraw(Canvas canvas)` 把九宫格绘制在屏幕中央。

下面就是 `onDraw(Canvas canvas)` 方法：

``` java
@Override
protected void onDraw(Canvas canvas) {
    if (!init) {
        width = getWidth();
        height = getHeight();
        initPoint();
        init = true;
    }
    // 画九宫格的格子
    drawPoint(canvas);

    if (moveX != -1 && moveY != -1) {
        // 画直线
        drawLine(canvas);
    }
}
```

首先判断了是否为第一次调用 `onDraw(Canvas canvas)` 方法，若为第一次则对 points 进行初始化：

``` java
// 初始化点
private void initPoint() {
    points[0][0] = new Point(width / 4, offset + width / 4, "0");
    points[0][1] = new Point(width / 2, offset + width / 4, "1");
    points[0][2] = new Point(width * 3 / 4, offset + width / 4, "2");

    points[1][0] = new Point(width / 4, offset + width / 2, "3");
    points[1][1] = new Point(width / 2, offset + width / 2, "4");
    points[1][2] = new Point(width * 3 / 4, offset + width / 2, "5");

    points[2][0] = new Point(width / 4, offset + width * 3 / 4, "6");
    points[2][1] = new Point(width / 2, offset + width * 3 / 4, "7");
    points[2][2] = new Point(width * 3 / 4, offset + width * 3 / 4, "8");
}
```

在 `initPoint()` 方法中主要创建了九个格子，并设置了相应的位置和密码。初始化完成之后把 init 置为 false ,下次不会再调用。

回过头再看看 `onDraw(Canvas canvas)` 中其他的逻辑，接下来调用了 `drawPoint(canvas)` 来绘制格子：

``` java
// 画九宫格的格子
private void drawPoint(Canvas canvas) {
    for (int i = 0; i < points.length; i++) {
        for (int j = 0; j < points[i].length; j++) {
            int state = points[i][j].getState();
            if (state == Point.NORMAL_MODE) {
                canvas.drawBitmap(normalBitmap, points[i][j].getX() - radius, points[i][j].getY() - radius, null);
            } else if (state == Point.PRESSED_MODE) {
                canvas.drawBitmap(pressedBitmap, points[i][j].getX() - radius, points[i][j].getY() - radius, null);
            } else {
                canvas.drawBitmap(errorBitmap, points[i][j].getX() - radius, points[i][j].getY() - radius, null);
            }
        }
    }
}
```

在绘制格子还是很简单的，主要分为了三种：普通模式下的格子、按下模式下的格子以及错误模式下的格子。

onTouchEvent
=====
在绘制好了格子之后，我们先不看最后的 `drawLine(canvas)` 方法，因为绘制直线是和用户手指的触摸事件息息相关的，所以我们先把目光转向 `onTouchEvent(MotionEvent event)` 方法：

``` java
@Override
public boolean onTouchEvent(MotionEvent event) {
    if (isTouch) {
        float x = event.getX();
        float y = event.getY();
        Point point;
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            // 判断用户触摸的点是否在九宫格的任意一个格子之内
                point = isPoint(x, y);
                if (point != null) {
                    point.setState(Point.PRESSED_MODE);  // 切换为按下模式
                    pressedPoint.add(point);  
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (pressedPoint.size() > 0) {
                    point = isPoint(x, y);
                    if (point != null) {
                        if (!crossPoint(point)) {
                            point.setState(Point.PRESSED_MODE);
                            pressedPoint.add(point);
                        }
                    }
                    moveX = x;
                    moveY = y;
                    isMove = true;
                }
                break;
            case MotionEvent.ACTION_UP:
                isMove = false;
                String tempPwd = "";
                for (Point p : pressedPoint) {
                    tempPwd += p.getMark();
                }
                if (listener != null) {
                    listener.getStringPassword(tempPwd);
                }

                if (tempPwd.equals(password)) {
                    if (listener != null) {
                        listener.isPassword(true);
                    }
                } else {
                    for (Point p : pressedPoint) {
                        p.setState(Point.ERROR_MODE);
                    }
                    isTouch = false;
                    this.postDelayed(runnable, 1000);
                    if (listener != null) {
                        listener.isPassword(false);
                    }
                }
                break;
        }
        invalidate();
    }
    return true;
}

public interface OnScreenLockListener {
    public void getStringPassword(String password);
    public void isPassword(boolean flag);
}

public void setOnScreenLockListener(OnScreenLockListener listener) {
    this.listener = listener;
}
```

在 `MotionEvent.ACTION_DOWN` 中，先在 `isPoint(float x, float y)` 方法内判断了用户触摸事件的坐标点是否在九宫格的任意一格之内。如果是，则需要把该九宫格的格子添加到 `pressedPoint` 中：

``` java
// 该触摸点是否为格子
private Point isPoint(float x, float y) {
    Point point;
    for (int i = 0; i < points.length; i++) {
        for (int j = 0; j < points[i].length; j++) {
            point = points[i][j];
            if (isContain(point, x, y)) {
                return point;
            }
        }
    }
    return null;
}

// 该点(x，y)是否被包含
private boolean isContain(Point point, float x, float y) {
    // 该点的(x,y)与格子圆心的距离若小于半径就是被包含了
    return Math.sqrt(Math.pow(x - point.getX(), 2f) + Math.pow(y - point.getY(), 2f)) <= radius;
}
```

接下来就是要看 `MotionEvent.ACTION_MOVE` 的逻辑了。一开始判断了用户触摸的点是否为九宫格的某个格子。但是比 `MotionEvent.ACTION_DOWN` 还多了一个步骤：若用户触摸了某个格子，还要判断该格子是否已经被包含在 `pressedPoint` 里面了。

``` java
// 是否该格子已经被包含在pressedPoint里面了
private boolean crossPoint(Point point) {
    if (pressedPoint.contains(point)) {
        return true;
    }
    return false;
}
```

最后来看看 `MotionEvent.ACTION_UP` ，把 `pressedPoint` 里保存的格子遍历后得到用户划出的密码，再和预先设置的密码比较，若相同则回调 `OnScreenLockListener` 监听器；不相同则把 `pressedPoint` 中的所有格子的模式设置为错误模式，并在 `runnable` 中调用 `reset()` 清空 `pressedPoint` ，重绘视图，再回调监听器。

``` java
private Runnable runnable = new Runnable() {
    @Override
    public void run() {
        isTouch = true;
        reset();
        invalidate();
    }
};

// 重置格子
private void reset(){
    for (int i = 0; i < points.length; i++) {
        for (int j = 0; j < points[i].length; j++) {
            points[i][j].setState(Point.NORMAL_MODE);
        }
    }
    pressedPoint.clear();
}
```

现在我们回过头来看看之前在 `onDraw(Canvas canvas)` 里面的 `drawLine(Canvas canvas)` 方法：

``` java
// 画直线
private void drawLine(Canvas canvas) {

    // 将pressedPoint中的所有格子依次遍历，互相连线
    for (int i = 0; i < pressedPoint.size() - 1; i++) {
        // 得到当前格子
        Point point = pressedPoint.get(i);
        // 得到下一个格子
        Point nextPoint = pressedPoint.get(i + 1);
        // 旋转画布
        canvas.rotate(RotateDegrees.getDegrees(point, nextPoint), point.getX(), point.getY());

        matrix.reset();
        // 根据距离设置拉伸的长度
        matrix.setScale(getDistance(point, nextPoint) / linePressedBitmap.getWidth(), 1f);
        // 进行平移
        matrix.postTranslate(point.getX(), point.getY() - linePressedBitmap.getWidth() / 2);


        if (point.getState() == Point.PRESSED_MODE) {
            canvas.drawBitmap(linePressedBitmap, matrix, null);
        } else {
            canvas.drawBitmap(lineErrorBitmap, matrix, null);
        }
        // 把画布旋转回来
        canvas.rotate(-RotateDegrees.getDegrees(point, nextPoint), point.getX(), point.getY());
    }

    // 如果是手指在移动的情况
    if (isMove) {
        Point lastPoint = pressedPoint.get(pressedPoint.size() - 1);
        canvas.rotate(RotateDegrees.getDegrees(lastPoint, moveX, moveY), lastPoint.getX(), lastPoint.getY());

        matrix.reset();
        Log.i(TAG, "the distance : " + getDistance(lastPoint, moveX, moveY) / linePressedBitmap.getWidth());
        matrix.setScale(getDistance(lastPoint, moveX, moveY) / linePressedBitmap.getWidth(), 1f);
        matrix.postTranslate(lastPoint.getX(), lastPoint.getY() - linePressedBitmap.getWidth() / 2);
        canvas.drawBitmap(linePressedBitmap, matrix, null);

        canvas.rotate(-RotateDegrees.getDegrees(lastPoint, moveX, moveY), lastPoint.getX(), lastPoint.getY());
    }
}

// 根据point和坐标点计算出之间的距离
private float getDistance(Point point, float moveX, float moveY) {
    Point b = new Point(moveX,moveY,null);
    return getDistance(point,b);
}

// 根据两个point计算出之间的距离
private float getDistance(Point point, Point nextPoint) {
    return (float) Math.sqrt(Math.pow(nextPoint.getX() - point.getX(), 2f) + Math.pow(nextPoint.getY() - point.getY(), 2f));
}
```

`drawLine(Canvas canvas)` 整体的逻辑并不复杂，首先将 `pressedPoint` 中的所有格子依次遍历，将它们连线。之后若是用户的手指还有滑动的话，把最后一个格子和用户手指触摸的点连线。

文末
====
`ScreenLockView` 中的代码差不多就是这些了，既然讲解完了那就一起来看看效果吧：

![ScreenShot](/uploads/20160510/20160510151253.gif)

效果还算不错吧，当然你也可以自己设置喜欢的九宫格图片，只要替换一下就可以了。如果对本篇文章有问题，可以留言。

老规矩，附上源码下载链接：

[ScreenLockView.rar](/uploads/20160510/ScreenLockView.rar)

Goodbye ~~
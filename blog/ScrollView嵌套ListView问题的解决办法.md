title: ScrollView嵌套ListView问题的解决办法
date: 2015-10-17 19:16:03
categories: Android Tips
tags: [Android,ScrollView]
---
在平常的Android开发中我们经常会碰到ScrollView嵌套ListView或者是GridView的情况，若按照一般的流程我们会发现在ScrollView中的ListView显示不全的问题，其实我们可以重写ListView的`onMeasure(int widthMeasureSpec, int heightMeasureSpec)`方法来解决。

以下是重写ListView的代码：

``` java
/**
 * 重写ListView，解决与ScrollView的冲突
 */
public class MyListView extends ListView {
	
    public MyListView(Context context) {
        super(context);
    }

    public MyListView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MyListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int expandSpec = MeasureSpec.makeMeasureSpec(Integer.MAX_VALUE >> 2,
                MeasureSpec.AT_MOST);
        super.onMeasure(widthMeasureSpec, expandSpec);
    }
}
```

同样的，GridView也可以通过重写来解决：

``` java
/**
 * 重写GridView，解决与ScrollView的冲突
 */
public class MyGridView extends GridView {

    public MyGridView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MyGridView(Context context) {
        super(context);
    }

    public MyGridView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

        int expandSpec = MeasureSpec.makeMeasureSpec(Integer.MAX_VALUE >> 2,
                MeasureSpec.AT_MOST);
        super.onMeasure(widthMeasureSpec, expandSpec);
    }

}
```

下面提供MyListView、MyGridView的源码下载：

[MyListView.java](/uploads/20151017/MyListView.java)

[MyGridView.java](/uploads/20151017/MyGridView.java)
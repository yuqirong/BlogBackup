title: 使用RecyclerView实现仿喵街效果
date: 2016-02-26 19:50:18
categories: Android Blog
tags: [Android,自定义View]
---
又到更新博客的时间了，本次给大家带来的是在RecyclerView的基础上实现喵街的效果。那么喵街是什么效果呢？下面就贴出效果图：

![这里写图片描述](/uploads/20160226/20160226205314.gif)

值得一提的是，这是旧版本的特效，新版本的喵街已经去掉了这种效果。

看完了效果，接下来就是动手的时间了。

我们先来分析一下思路：我们先给RecyclerView添加一个OnScrollListener，然后分别去获得firstVisiblePosition和firstCompletelyVisiblePosition。这里要注意一下，firstVisiblePosition是第一个在屏幕中**可见**的itemView对应的position，而firstCompletelyVisiblePosition是是第一个在屏幕中**完全可见**的itemView对应的position。之后在滚动中去动态地设置itemView的高度。整体的思路就这样了，下面我们直接来看代码。

创建几个自定义的属性，以便后面备用：
``` xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <declare-styleable name="ExpandRecyclerView">
		<!-- item最大的高度 -->
        <attr name="max_item_height" format="dimension"></attr>
        <!-- item普通的高度 -->
        <attr name="normal_item_height" format="dimension"></attr>
    </declare-styleable>
</resources>
```
之后我们新建一个类继承自RecyclerView，类名就叫ExpandRecyclerView。
``` java
//最大item的高度
private float maxItemHeight;
//普通item的高度
private float normalItemHeight;
// 默认最大的item高度
private float defaultMaxItemHeight;
// 默认普通的item高度
private float defaultNormalItemHeight;

public ExpandRecyclerView(Context context) {
    this(context, null);
}

public ExpandRecyclerView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
}

public ExpandRecyclerView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ExpandRecyclerView);
    defaultMaxItemHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 256, context.getResources().getDisplayMetrics());
    defaultNormalItemHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 120, context.getResources().getDisplayMetrics());
    maxItemHeight = a.getDimension(R.styleable.ExpandRecyclerView_max_item_height, defaultMaxItemHeight);
    normalItemHeight = a.getDimension(R.styleable.ExpandRecyclerView_normal_item_height, defaultNormalItemHeight);
    a.recycle();

    setHasFixedSize(true);
    setLayoutManager(new LinearLayoutManager(context));
    setItemAnimator(new DefaultItemAnimator());
    this.addOnScrollListener(listener);
}
```
在构造器中我们得到了`maxItemHeight`和`normalItemHeight`，之后设置了OnScrollListener。
``` java
OnScrollListener listener = new RecyclerView.OnScrollListener() {

    @Override
    public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
        super.onScrolled(recyclerView, dx, dy);
        Log.i(TAG,"dy : " + dy);
        LinearLayoutManager mLinearLayoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
        // 在屏幕中第一个可见的position
        int firstVisiblePosition = mLinearLayoutManager.findFirstVisibleItemPosition();
        // 得到第一个可见的ViewHolder
        RecyclerView.ViewHolder firstVisibleViewHolder =
                recyclerView.findViewHolderForLayoutPosition(firstVisiblePosition);
        // 在屏幕中第一个完全可见的position
        int firstCompletelyVisiblePosition = mLinearLayoutManager.findFirstCompletelyVisibleItemPosition();
        // 得到第一个完全可见的ViewHolder
        RecyclerView.ViewHolder firstCompletelyVisibleViewHolder =
                recyclerView.findViewHolderForLayoutPosition(firstCompletelyVisiblePosition);

        Log.i(TAG, "firstVisiblePosition : " + firstVisiblePosition + " , firstCompletelyVisiblePosition : " + firstCompletelyVisiblePosition);
        // 当firstVisibleViewHolder被滑出屏幕时
        if (firstVisibleViewHolder.itemView.getLayoutParams().height - dy < maxItemHeight
                && firstVisibleViewHolder.itemView.getLayoutParams().height - dy >= normalItemHeight) {
            // 高度减小
            firstVisibleViewHolder.itemView.getLayoutParams().height -= dy;
            firstVisibleViewHolder.itemView.setLayoutParams(firstVisibleViewHolder.itemView.getLayoutParams());
        }
        // 当firstCompletelyVisibleViewHolder慢慢滑到屏幕顶部时
        if (firstCompletelyVisibleViewHolder.itemView.getLayoutParams().height + dy <= maxItemHeight
                && firstCompletelyVisibleViewHolder.itemView.getLayoutParams().height + dy >= normalItemHeight) {
            // 高度增加
            firstCompletelyVisibleViewHolder.itemView.getLayoutParams().height += dy;
            firstCompletelyVisibleViewHolder.itemView.setLayoutParams(firstCompletelyVisibleViewHolder.itemView.getLayoutParams());
        }

    }

};
```
在`onScrolled(RecyclerView recyclerView, int dx, int dy)`里大部分的代码都加上注释了，就是根据`dy`去动态地改变了`firstVisibleViewHolder`和`firstCompletelyVisibleViewHolder`的高度。

上面的搞定了之后，别忘了要在Adapter里去初始化设置Item的高度。
``` java
/**
 * 设置适配器
 *
 * @param adapter
 */
@Override
public void setAdapter(Adapter adapter) {
    super.setAdapter(adapter);
    if (adapter instanceof ExpandRecyclerViewAdapter) {
        ExpandRecyclerViewAdapter mAdapter = (ExpandRecyclerViewAdapter) adapter;
        //设置最大的item高度
        mAdapter.setMaxItemHeight(maxItemHeight);
        //设置普通的item高度
        mAdapter.setNormalItemHeight(normalItemHeight);
    }
}
```
ExpandRecyclerViewAdapter的代码，重写`onBindViewHolder(RecyclerView.ViewHolder holder, int position)`：
``` java
@Override
public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        if(position == 0){
            holder.itemView.setLayoutParams(new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) maxItemHeight));
        }else{
            holder.itemView.setLayoutParams(new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) normalItemHeight));
        }
        bindCustomViewHolder(holder, position);
}

public abstract void bindCustomViewHolder(RecyclerView.ViewHolder holder, int position);
```
好了，整体的代码就这些了，下面贴出运行效果：

![这里写图片描述](/uploads/20160226/20160226210235.gif)

源码下载：

[ExpandRecyclerView.rar](/uploads/20160226/ExpandRecyclerView.rar)

Reference
=====
[android版高仿喵街主页滑动效果](http://www.jianshu.com/p/a2c3c21e3b99)

Thanks
===
* [miaojiedemo](https://github.com/dongjunkun/miaojiedemo) created by [dongjunkun](https://github.com/dongjunkun)


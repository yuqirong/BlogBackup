title: RecyclerView实现拖拽排序和侧滑删除
date: 2017-02-03 15:23:59
categories: Android Blog
tags: [Android,RecyclerView]
---
在平时开发应用的时候，经常会遇到列表排序、滑动删除的需求。如果列表效果采用的是 `ListView` 的话，需要经过自定义 View 才能实现效果；但是如果采用的是 `RecyclerView` 的话，系统 API 就已经为我们提供了相应的功能。

接下来，我们就来看一下怎么用系统 API 来实现排序和删除的效果。

创建 ItemTouchHelper
--------------------
创建一个 `ItemTouchHelper` 对象，然后其调用 `attachToRecyclerView` 方法：

``` java
RecyclerView recyclerView = (RecyclerView) findViewById(R.id.recyclerView);
recyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
RecyclerViewAdapter adapter = new RecyclerViewAdapter();
ItemTouchHelper helper = new ItemTouchHelper(new MyItemTouchCallback(adapter));
helper.attachToRecyclerView(recyclerView);
```

在创建 `ItemTouchHelper` 对象时候，需要我们传入一个实现了 `ItemTouchHelper.Callback` 接口的对象。而排序和删除的逻辑都封装在了这个 `ItemTouchHelper.Callback` 的对象里面了。

实现 ItemTouchHelper.Callback 接口
---------------------------------
创建 `MyItemTouchCallback` 类，实现 `ItemTouchHelper.Callback` 接口：

``` java
public class MyItemTouchCallback extends ItemTouchHelper.Callback {

    private final RecyclerViewAdapter adapter;

    public MyItemTouchCallback(RecyclerViewAdapter adapter) {
        this.adapter = adapter;
    }

    @Override
    public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
        return 0;
    }

    @Override
    public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
        return false;
    }

    @Override
    public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
        
    }

}
```

实现 `ItemTouchHelper.Callback` 接口后有三个方法需要重写：

1. `getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder)` ：设置滑动类型的标记。需要设置两种类型的 flag ，即 `dragFlags` 和 `swipeFlags` ，分别代表着拖拽标记和滑动标记。最后需要调用 `makeMovementFlags(dragFlags, swipeFlags)` 方法来合成返回。
2. `onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target)` ：当用户拖拽列表某个 item 时会回调。很明显，拖拽排序的代码应该在这个方法中实现。
3.  `onSwiped(RecyclerView.ViewHolder viewHolder, int direction)` ：当用户滑动列表某个 item 时会回调。所以侧滑删除的代码应该在这个方法中实现。

重写方法
-------
我们先来看看 `getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder)` 方法：

``` java
@Override
public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
    int dragFlag;
    int swipeFlag;
    RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
    if (layoutManager instanceof GridLayoutManager) {
        dragFlag = ItemTouchHelper.DOWN | ItemTouchHelper.UP
                | ItemTouchHelper.RIGHT | ItemTouchHelper.LEFT;
        swipeFlag = 0;
    } else {
        dragFlag = ItemTouchHelper.DOWN | ItemTouchHelper.UP;
        swipeFlag = ItemTouchHelper.END;
    }
    return makeMovementFlags(dragFlag, swipeFlag);
}
```

代码中根据 `layoutManager` 分为了两种情况：

1. 如果是 `GridLayoutManager` ，那么拖拽排序就可以细分为上下左右四个方向了，而且 `GridLayoutManager` 没有侧滑删除的功能；
2. 若是其他的 `LayoutManager` ，比如说 `LinearLayoutManager` ，那么拖拽排序就只有上下两个方向了，并且设置 `swipeFlag` 为 `ItemTouchHelper.END` 类型；
3. 对于其他自定义类型的 `LayoutManager` 可以自己根据自身情况补充。

下面就是 `onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target)` 方法：

``` java
@Override
public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
    int fromPosition = viewHolder.getAdapterPosition();
    int toPosition = target.getAdapterPosition();
    if (fromPosition < toPosition) {
        for (int i = fromPosition; i < toPosition; i++) {
            Collections.swap(adapter.getDataList(), i, i + 1);
        }
    } else {
        for (int i = fromPosition; i > toPosition; i--) {
            Collections.swap(adapter.getDataList(), i, i - 1);
        }
    }
    recyclerView.getAdapter().notifyItemMoved(fromPosition, toPosition);
    return true;
}
```

之前说过了，`onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target)` 方法是用户在拖拽 item 的时候会回调。所以关于列表排序的代码应该写在这里。方法参数中的 `viewHolder` 代表的是用户当前拖拽的 item ，而 `target` 代表的是被用户拖拽所覆盖的那个 item 。所以在 `onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target)` 方法中的逻辑就是把 `fromPosition` 至 `toPosition` 为止改变它们的位置。

最后就是 `onSwiped(RecyclerView.ViewHolder viewHolder, int direction)` 方法了：

```
@Override
public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
    int position = viewHolder.getAdapterPosition();
    if (direction == ItemTouchHelper.END) {
        adapter.getDataList().remove(position);
        adapter.notifyItemRemoved(position);
    }
}
```

这个方法在用户进行侧滑删除操作的时候会回调，其中的逻辑就是得到当前用户进行侧滑删除操作的 item ，然后将其删除。

到了这里，大功告成了。那么来看看效果吧：

![效果图](/uploads/20170206/20170206222813.gif)

改善用户体验
-----------
我们发现还有一些不完美的地方：比如当用户在拖拽排序的时候，可以改变当前拖拽 item 的透明度，这样就可以和其他 item 区分开来了。那么，我们需要去重写 `onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState)` 方法：

``` java
@Override
public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
    super.onSelectedChanged(viewHolder, actionState);
    if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
        viewHolder.itemView.setBackgroundColor(Color.BLUE);
    }
}
```

相对应地，当用户手指从拖拽 item 中抬起的时候，我们需要把 item 的透明度复原。需要我们重写 `clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder)` 方法：

``` java
@Override
public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
    super.clearView(recyclerView, viewHolder);
    viewHolder.itemView.setBackgroundColor(0);
}
```

好了，来看看改进之后的效果：

![改进效果图](/uploads/20170206/20170203223341.gif)

今天就这样吧，拜拜啦！！

源码下载：[TestRV.rar](/uploads/20170206/TestRV.rar)
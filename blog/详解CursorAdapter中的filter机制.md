title: 详解CursorAdapter中的filter机制
date: 2016-07-03 20:35:51
categories: Android Blog
tags: [Android,源码解析]
---
前言
==========
目前在公司中仍处于认知状态，因此没有什么时间写博客了，趁今天是周末就来更新一发。

关于今天为什么讲 CursorAdapter 的原因，是因为之前在工作的时候有遇到 CursorAdapter 中 filter 的相关问题，于是就想把 CursorAdapter 中的 filter 机制流程好好梳理一下。出于这样的目的，本篇博文就诞生了。

在阅读本文之前，最好已经有写过 CursorAdapter 中 filter 相关代码的经历，这样可以帮助你更好地理解其中的原理。如果你准备好了，那么接下来就一起来看看吧。

CursorAdapter 类
==========
首先我们来看一下 CursorAdapter 的继承以及实现关系：

``` java
public abstract class CursorAdapter extends BaseAdapter implements Filterable, CursorFilter.CursorFilterClient {

}
```

CursorAdapter 继承自 BaseAdapter ，相信大家都可以理解。之后又实现了 Filterable 和 CursorFilter.CursorFilterClient 接口。

Filterable 的接口很简单，只有一个 `getFilter()` 方法，用来返回 filter 。

```java
public interface Filterable {
    /**
     * <p>Returns a filter that can be used to constrain data with a filtering
     * pattern.</p>
     *
     * <p>This method is usually implemented by {@link android.widget.Adapter}
     * classes.</p>
     *
     * @return a filter used to constrain data
     */
    Filter getFilter();
}
```

而 CursorFilter.CursorFilterClient 的接口是定义在 CursorFilter 类里面的。而 CursorFilter 类是默认修饰符，也就是说我们在外部无法访问到它。

``` java
interface CursorFilterClient {
    CharSequence convertToString(Cursor cursor);
    Cursor runQueryOnBackgroundThread(CharSequence constraint);
    Cursor getCursor();
    void changeCursor(Cursor cursor);
}
```

我们来看看 CursorFilterClient 接口中的抽象方法。根据方法名我们大概都能猜出该方法需要做的事情。 `convertToString(Cursor cursor)` 方法主要的功能就是根据传入的 cursor 参数返回某个字段；`runQueryOnBackgroundThread(CharSequence constraint)` 方法的意思就是根据传入的 constraint 字符序列去搜索得到 cursor；而 `getCursor()`就是返回 cursor；`changeCursor(Cursor cursor)` 就是根据传入的新的 cursor 去替换旧的 cursor 。

filter 的用法
==========
好了，我们来想想平时我们是怎么样使用 CursorAdapter 中的 filter ？

第一步，我们会使用自定义的 adapter 继承自 CursorAdapter ，并且实现 FilterQueryProvider 和 FilterListener 接口。最后别忘了调用 `setFilterQueryProvider(FilterQueryProvider filterQueryProvider)` 方法。

然后，第二步我们会使用CursorAdapter的 `getFilter()` 方法来得到 filter 。对，没错，就是实现 Filterable 接口的那个 `getFilter` 方法。

``` java
public Filter getFilter() {
    if (mCursorFilter == null) {
        mCursorFilter = new CursorFilter(this);
    }
    return mCursorFilter;
}
```

在 CursorAdapter 的源码中，判断了 mCursorFilter 是否为空。若为空，则创建一个新的 CursorFilter 对象。否则直接返回 mCursorFilter 。在这里要说明一下 CursorFilter 是 Filter 的子类。
 
而在 CursorFilter 的构造器中，主要是设置了 client (CursorAdapter 实现了 CursorFilterClient 接口)。

``` java
CursorFilter(CursorFilterClient client) {
    mClient = client;
}
```

在第二步得到了 filter 之后，第三步就可以使用 `filter.filter(CharSequence constraint)` 或者 `filter.filter(CharSequence constraint, FilterListener listener)` 方法了。constraint 参数就是要过滤的关键词；而 FilterListener 是一个 Filter 类的内部接口，会在过滤完成之后回调其中的 `onFilterComplete(int count)` 方法。

filter 的原理
==========

大致使用 filter 的步骤就是像上面这样的了。下面我们就来揭开这其中神秘的面纱吧！

我们的入手点就是 Filter 的 filter 方法了。其中的 `filter.filter(CharSequence constraint)` 方法内部会调用 `filter.filter(CharSequence constraint, FilterListener listener)` 方法。所以我们只需要看下`filter.filter(CharSequence constraint, FilterListener listener)` 的源码：

``` java
/**
 * <p>Starts an asynchronous filtering operation. Calling this method
 * cancels all previous non-executed filtering requests and posts a new
 * filtering request that will be executed later.</p>
 *
 * <p>Upon completion, the listener is notified.</p>
 *
 * @param constraint the constraint used to filter the data
 * @param listener a listener notified upon completion of the operation
 *
 * @see #filter(CharSequence)
 * @see #performFiltering(CharSequence)
 * @see #publishResults(CharSequence, android.widget.Filter.FilterResults)
 */
public final void filter(CharSequence constraint, FilterListener listener) {
    synchronized (mLock) {
        if (mThreadHandler == null) {
            HandlerThread thread = new HandlerThread(
                    THREAD_NAME, android.os.Process.THREAD_PRIORITY_BACKGROUND);
            thread.start();
            mThreadHandler = new RequestHandler(thread.getLooper());
        }

        final long delay = (mDelayer == null) ? 0 : mDelayer.getPostingDelay(constraint);
        
        Message message = mThreadHandler.obtainMessage(FILTER_TOKEN);

        RequestArguments args = new RequestArguments();
        // make sure we use an immutable copy of the constraint, so that
        // it doesn't change while the filter operation is in progress
        args.constraint = constraint != null ? constraint.toString() : null;
        args.listener = listener;
        message.obj = args;

        mThreadHandler.removeMessages(FILTER_TOKEN);
        mThreadHandler.removeMessages(FINISH_TOKEN);
        mThreadHandler.sendMessageDelayed(message, delay);
    }
}
```

从源码中我们可以看到，主要做的就是在一开始创建一个 HandlerThread 线程，并且创建了一个 RequestHandler 的对象 mThreadHandler 。之后创建了一个 RequestArguments 的对象 args，然后把 constraint 和 listener 传到 args 中去，而 RequestArguments 类还有一个成员变量就是 results ，主要用于存储 filter 过滤之后的结果，这会在下面的代码中用到。然后用 mThreadHandler 将该消息发送出去。

那么我们接下来就要来看看 RequestHandler 的源码：

``` java
/**
 * <p>Worker thread handler. When a new filtering request is posted from
 * {@link android.widget.Filter#filter(CharSequence, android.widget.Filter.FilterListener)},
 * it is sent to this handler.</p>
 */
private class RequestHandler extends Handler {
    public RequestHandler(Looper looper) {
        super(looper);
    }
    
    /**
     * <p>Handles filtering requests by calling
     * {@link Filter#performFiltering} and then sending a message
     * with the results to the results handler.</p>
     *
     * @param msg the filtering request
     */
    public void handleMessage(Message msg) {
        int what = msg.what;
        Message message;
        switch (what) {
            case FILTER_TOKEN:
                RequestArguments args = (RequestArguments) msg.obj;
                try {
                    args.results = performFiltering(args.constraint);
                } catch (Exception e) {
                    args.results = new FilterResults();
                    Log.w(LOG_TAG, "An exception occured during performFiltering()!", e);
                } finally {
                    message = mResultHandler.obtainMessage(what);
                    message.obj = args;
                    message.sendToTarget();
                }

                synchronized (mLock) {
                    if (mThreadHandler != null) {
                        Message finishMessage = mThreadHandler.obtainMessage(FINISH_TOKEN);
                        mThreadHandler.sendMessageDelayed(finishMessage, 3000);
                    }
                }
                break;
            case FINISH_TOKEN:
                synchronized (mLock) {
                    if (mThreadHandler != null) {
                        mThreadHandler.getLooper().quit();
                        mThreadHandler = null;
                    }
                }
                break;
        }
    }
}
```

在 case FILTER_TOKEN 中我们可以看到，会先去调用 `performFiltering(CharSequence constraint)` 方法。而该方法在 Filter 类中是抽象方法，需要在子类中去实现。那么我们就来看看 CursorFilter 的 `performFiltering(CharSequence constraint)` 方法吧：

``` java
@Override
protected FilterResults performFiltering(CharSequence constraint) {
    Cursor cursor = mClient.runQueryOnBackgroundThread(constraint);

    FilterResults results = new FilterResults();
    if (cursor != null) {
        results.count = cursor.getCount();
        results.values = cursor;
    } else {
        results.count = 0;
        results.values = null;
    }
    return results;
}
```

在 `performFiltering(CharSequence constraint)` 方法中又会去调用  mClient 的 `runQueryOnBackgroundThread(CharSequence constraint)` 方法，而 mClient 就是之前的 CursorAdapter ，所以我们又要跳到 CursorAdapter 类去看相关的代码：

``` java
/**
 * Runs a query with the specified constraint. This query is requested
 * by the filter attached to this adapter.
 *
 * The query is provided by a
 * {@link android.widget.FilterQueryProvider}.
 * If no provider is specified, the current cursor is not filtered and returned.
 *
 * After this method returns the resulting cursor is passed to {@link #changeCursor(Cursor)}
 * and the previous cursor is closed.
 *
 * This method is always executed on a background thread, not on the
 * application's main thread (or UI thread.)
 * 
 * Contract: when constraint is null or empty, the original results,
 * prior to any filtering, must be returned.
 *
 * @param constraint the constraint with which the query must be filtered
 *
 * @return a Cursor representing the results of the new query
 *
 * @see #getFilter()
 * @see #getFilterQueryProvider()
 * @see #setFilterQueryProvider(android.widget.FilterQueryProvider)
 */
public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
    if (mFilterQueryProvider != null) {
        return mFilterQueryProvider.runQuery(constraint);
    }

    return mCursor;
}
```

我们可以看到会去调用 mFilterQueryProvider 的 `runQuery(CharSequence constraint)` 方法。 FilterQueryProvider 其实就是一个接口而已，当我们需要使用 filter 时就要实现该接口。在上面的 filter 用法中已经提到过了。其中的 `runQuery(CharSequence constraint)` 方法就是需要我们自己去实现的。当然，这里还有另外一种方法，就是不用实现 FilterQueryProvider 接口。而是在子类中去重写 `runQueryOnBackgroundThread(CharSequence constraint)` 方法，也是达到了一样的效果。

假定我们已经在 `runQuery(CharSequence constraint)` 实现了相关的操作，并且返回了查询出来的 cursor 。那样我们又要跳回到 RequestHandler 的源码中了(这里只截取部分代码，完整代码请查看上面)：

``` java
try {
    args.results = performFiltering(args.constraint);
} catch (Exception e) {
    args.results = new FilterResults();
    Log.w(LOG_TAG, "An exception occured during performFiltering()!", e);
} finally {
    message = mResultHandler.obtainMessage(what);
    message.obj = args;
    message.sendToTarget();
}
```

可以看到，这里把返回的 cursor 传给了 args.results 。并且又使用了 mResultHandler 发送了消息。这样我们又要来看一下 ResultHandler 的源码了：

``` java
/**
 * <p>Handles the results of a filtering operation. The results are
 * handled in the UI thread.</p>
 */
private class ResultsHandler extends Handler {
    /**
     * <p>Messages received from the request handler are processed in the
     * UI thread. The processing involves calling
     * {@link Filter#publishResults(CharSequence,
     * android.widget.Filter.FilterResults)}
     * to post the results back in the UI and then notifying the listener,
     * if any.</p> 
     *
     * @param msg the filtering results
     */
    @Override
    public void handleMessage(Message msg) {
        RequestArguments args = (RequestArguments) msg.obj;

        publishResults(args.constraint, args.results);
        if (args.listener != null) {
            int count = args.results != null ? args.results.count : -1;
            args.listener.onFilterComplete(count);
        }
    }
}
```

在 `handleMessage(Message msg)` 中，调用了 `publishResults(CharSequence constraint, FilterResults results)` 方法。在 Filter 类中 `publishResults(CharSequence constraint, FilterResults results)` 又是抽象的，所以还得去 CursorFilter 类中查看相关的源码：

``` java
@Override
protected void publishResults(CharSequence constraint, FilterResults results) {
    Cursor oldCursor = mClient.getCursor();
    
    if (results.values != null && results.values != oldCursor) {
        mClient.changeCursor((Cursor) results.values);
    }
}
```

源码里表示了会去调用 CursorAdapter 的 `changeCursor(Cursor cursor)` :

``` java
/**
 * Change the underlying cursor to a new cursor. If there is an existing cursor it will be
 * closed.
 * 
 * @param cursor The new cursor to be used
 */
public void changeCursor(Cursor cursor) {
    Cursor old = swapCursor(cursor);
    if (old != null) {
        old.close();
    }
}
```

在 `changeCursor(Cursor cursor)` 中，又调用了 `swapCursor(Cursor newCursor)` :

``` java
/**
 * Swap in a new Cursor, returning the old Cursor.  Unlike
 * {@link #changeCursor(Cursor)}, the returned old Cursor is <em>not</em>
 * closed.
 *
 * @param newCursor The new cursor to be used.
 * @return Returns the previously set Cursor, or null if there wasa not one.
 * If the given new Cursor is the same instance is the previously set
 * Cursor, null is also returned.
 */
public Cursor swapCursor(Cursor newCursor) {
    if (newCursor == mCursor) {
        return null;
    }
    Cursor oldCursor = mCursor;
    if (oldCursor != null) {
        if (mChangeObserver != null) oldCursor.unregisterContentObserver(mChangeObserver);
        if (mDataSetObserver != null) oldCursor.unregisterDataSetObserver(mDataSetObserver);
    }
    mCursor = newCursor;
    if (newCursor != null) {
        if (mChangeObserver != null) newCursor.registerContentObserver(mChangeObserver);
        if (mDataSetObserver != null) newCursor.registerDataSetObserver(mDataSetObserver);
        mRowIDColumn = newCursor.getColumnIndexOrThrow("_id");
        mDataValid = true;
        // notify the observers about the new cursor
        notifyDataSetChanged();
    } else {
        mRowIDColumn = -1;
        mDataValid = false;
        // notify the observers about the lack of a data set
        notifyDataSetInvalidated();
    }
    return oldCursor;
}
```

在 `swapCursor(Cursor newCursor)` 中主要的工作就是把 oldCursor 替换成 newCursor ，并且调用了 `notifyDataSetChanged();` 来更新 ListView 。从上面的源码中还可以看到， `swapCursor(Cursor newCursor)` 方法中返回的 oldCursor 是没有关闭的。

完成了替换 Cursor 的工作后，我们还要回过头来看看 ResultsHandler 剩余部分的代码(只截取了部分代码)：

``` java
if (args.listener != null) {
    int count = args.results != null ? args.results.count : -1;
    args.listener.onFilterComplete(count);
}
```

可以看到，在最后回调了 FilterListener 的 `onFilterComplete(int count)` 方法。其中的 count 参数是查询出来结果的总数。

至此，一个完整的 filter 流程终于走完了。这其中虽然看似很绕，其实原理还是比较简单的。

尾语
====
看完上面分析，相信大家对 CursorAdapter 的 filter 机制已经有了一个大致的了解了吧。主要原理基本上还是 Handler 异步消息机制以及各个接口回调等。从中可以发现其实源码并不难，只要有耐心慢慢分析，一定会有所突破的。如果对这整个流程有问题的童鞋可以在下面留言。

那么，今天就到这了。Goodbye！
title: 深入解析AsyncTask的原理
date: 2015-10-13 19:20:37
categories: Android Blog
tags: [Android,源码解析]
---
前言
=====
在初学 Android 的时候，AsyncTask 应该是大家都比较熟悉的。我们都知道 AsyncTask 可以在后台开启一个异步的任务，当任务完成后可以更新在 UI 上。而在 AsyncTask 中，比较常用的方法有： onPreExecute 、 doInBackground 、 onPostExecute 和 onProgressUpdate 等。而上述的方法中除了 doInBackground 运行在子线程中，其他的都是运行在主线程的，相信大家对这几个方法也了如指掌了。

在这里先剧透一下， AsnycTask 原理就是“线程池 + Handler”的组合。如果你对Handler消息传递的机制不清楚，那么可以查看我上一篇的博文：[《探究Android异步消息的处理之Handler详解》](/2015/09/29/%E6%8E%A2%E7%A9%B6Android%E5%BC%82%E6%AD%A5%E6%B6%88%E6%81%AF%E7%9A%84%E5%A4%84%E7%90%86%E4%B9%8BHandler%E8%AF%A6%E8%A7%A3/)，里面会有详细的介绍。那么接下来，就一起来看看 AsyncTask 实现的原理吧！

class
=====
``` java
public abstract class AsyncTask<Params, Progress, Result> {
	...
}
```
AsyncTask 为抽象类，其中有三个泛型。这三个泛型相信大家都很熟悉了，第一个是传递的参数类型，第二个是任务执行的进度，第三个是任务执行的结果类型了。如果其中某个不需要可以传入Void。

线程池
======
那么我们先来看看 AsyncTask 里的线程池：

``` java
private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
private static final int CORE_POOL_SIZE = CPU_COUNT + 1;
private static final int MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1;
private static final int KEEP_ALIVE = 1;

private static final ThreadFactory sThreadFactory = new ThreadFactory() {
    private final AtomicInteger mCount = new AtomicInteger(1);

    public Thread newThread(Runnable r) {
        return new Thread(r, "AsyncTask #" + mCount.getAndIncrement());
    }
};

private static final BlockingQueue<Runnable> sPoolWorkQueue =
        new LinkedBlockingQueue<Runnable>(128);

/**
 * An {@link Executor} that can be used to execute tasks in parallel.
 */
public static final Executor THREAD_POOL_EXECUTOR
        = new ThreadPoolExecutor(CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE,
                TimeUnit.SECONDS, sPoolWorkQueue, sThreadFactory);
```
从上面我们可以看到，该线程池(即 THREAD\_POOL\_EXECUTOR)的核心线程数为 CPU 的核心数量 + 1，最大线程数为 CPU 的核心数量 * 2 + 1，过剩线程的存活时间为1s。这里要注意的是 sPoolWorkQueue 是静态阻塞式的队列，意味着所有的 AsyncTask 用的都是同一个 sPoolWorkQueue ，也就是说最大的容量为128个任务，若超过了会抛出异常。最后一个参数就是线程工厂了，用来制造线程。

我们再来看看 AsyncTask 内部的任务执行器 SERIAL\_EXECUTOR ，该执行器用来把任务传递给上面的 THREAD\_POOL\_EXECUTOR 线程池。在 AsyncTask 的设计中，SERIAL\_EXECUTOR 是默认的任务执行器，并且是串行的，也就导致了在 AsyncTask 中任务都是串行地执行。当然，AsyncTask 也是支持任务并行执行的，这个点我们在下面再讲。

``` java
/**
 * An {@link Executor} that executes tasks one at a time in serial
 * order.  This serialization is global to a particular process.
 */
public static final Executor SERIAL_EXECUTOR = new SerialExecutor();

private static volatile Executor sDefaultExecutor = SERIAL_EXECUTOR;

private static class SerialExecutor implements Executor {
    final ArrayDeque<Runnable> mTasks = new ArrayDeque<Runnable>();
    Runnable mActive;

    public synchronized void execute(final Runnable r) {
        mTasks.offer(new Runnable() {
            public void run() {
                try {
                    r.run();
                } finally {
                    scheduleNext();
                }
            }
        });
        if (mActive == null) {
            scheduleNext();
        }
    }

    protected synchronized void scheduleNext() {
        if ((mActive = mTasks.poll()) != null) {
            THREAD_POOL_EXECUTOR.execute(mActive);
        }
    }
}
```
可以从 SerialExecutor 的内部看到，是循环地取出 mActive ，并且把 mActive 放置到上面的 THREAD\_POOL\_EXECUTOR 中去执行。这样就导致了任务是串行地执行的。

Handler
=========
讲完了线程池，那么剩下的就是 Handler 了。下面是 AsyncTask 内部实现的一个 Handler :

``` java
private static InternalHandler sHandler;

private static final int MESSAGE_POST_RESULT = 0x1;
private static final int MESSAGE_POST_PROGRESS = 0x2;

private static class InternalHandler extends Handler {
    public InternalHandler() {
        super(Looper.getMainLooper());
    }

    @SuppressWarnings({"unchecked", "RawUseOfParameterizedType"})
    @Override
    public void handleMessage(Message msg) {
        AsyncTaskResult<?> result = (AsyncTaskResult<?>) msg.obj;
        switch (msg.what) {
            case MESSAGE_POST_RESULT:
                // There is only one result
                result.mTask.finish(result.mData[0]);
                break;
            case MESSAGE_POST_PROGRESS:
                result.mTask.onProgressUpdate(result.mData);
                break;
        }
    }
}

private void finish(Result result) {
    if (isCancelled()) {
        onCancelled(result);
    } else {
        onPostExecute(result);
    }
    mStatus = Status.FINISHED;
}

@SuppressWarnings({"RawUseOfParameterizedType"})
private static class AsyncTaskResult<Data> {
    final AsyncTask mTask;
    final Data[] mData;

    AsyncTaskResult(AsyncTask task, Data... data) {
        mTask = task;
        mData = data;
    }
}
```
在源码中，有一个静态的 sHandler ，还有定义了两条消息的类型。一条表示传送结果，另一条表示传送进度。再来分析一下 InternalHandler 的源码：在 InternalHandler 的 handleMessage 方法中，根据消息类型分别有不同的处理。其中的 result.mTask 可以从 AsyncTaskResult 类中看到，就是 AsyncTask 本身。而后边的方法有一些眼熟啊。其中 finish 应该是在任务结束时回调的，若任务完成会回调 onPostExecute 方法，否则会回调 onCancelled 方法；而消息类型为 MESSAGE\_POST\_PROGRESS 中的 onProgressUpdate 方法不就是我们在使用 AsyncTask 时重写的那个方法么。在这里我们已经可以看出点端倪了。但是我们先卖个关子，继续向下看。

构造器
=====
再来看看 AsyncTask 的构造器：

``` java
private final WorkerRunnable<Params, Result> mWorker;
private final FutureTask<Result> mFuture;
	
/**
 * Creates a new asynchronous task. This constructor must be invoked on the UI thread.
 */
public AsyncTask() {
    mWorker = new WorkerRunnable<Params, Result>() {
        public Result call() throws Exception {
            mTaskInvoked.set(true);

            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            //noinspection unchecked
            Result result = doInBackground(mParams);
            Binder.flushPendingCommands();
            return postResult(result);
        }
    };

    mFuture = new FutureTask<Result>(mWorker) {
        @Override
        protected void done() {
            try {
                postResultIfNotInvoked(get());
            } catch (InterruptedException e) {
                android.util.Log.w(LOG_TAG, e);
            } catch (ExecutionException e) {
                throw new RuntimeException("An error occurred while executing doInBackground()",
                        e.getCause());
            } catch (CancellationException e) {
                postResultIfNotInvoked(null);
            }
        }
    };
}

@WorkerThread
protected abstract Result doInBackground(Params... params);

@WorkerThread
protected final void publishProgress(Progress... values) {
    if (!isCancelled()) {
        getHandler().obtainMessage(MESSAGE_POST_PROGRESS,
                new AsyncTaskResult<Progress>(this, values)).sendToTarget();
    }
}

private void postResultIfNotInvoked(Result result) {
    final boolean wasTaskInvoked = mTaskInvoked.get();
    if (!wasTaskInvoked) {
        postResult(result);
    }
}

private Result postResult(Result result) {
    @SuppressWarnings("unchecked")
    Message message = getHandler().obtainMessage(MESSAGE_POST_RESULT,
            new AsyncTaskResult<Result>(this, result));
    message.sendToTarget();
    return result;
}

private static Handler getHandler() {
    synchronized (AsyncTask.class) {
        if (sHandler == null) {
            sHandler = new InternalHandler();
        }
        return sHandler;
    }
}
```
在构造器中创建了 WorkerRunnable 和 FutureTask 的对象，而在 WorkerRunnable 内部的 call 方法中会去执行需要我们重写的 doInBackground 方法。而如果在 doInBackground 方法中调用 publishProgress 方法，就会使用发送消息到 sHandler 的 handleMessage 方法，之后就调用了 onProgressUpdate 方法了，具体可见上面的 InternalHandler 中的代码。最后如果任务结束了在 postResult 中发送消息给 sHandler ，就是要在 handleMessage 中拿到消息并且执行上面分析过的 finish 方法了。

execute
=====
最后我们来讲讲 execute 方法，以下是源码：
``` java
@MainThread
public final AsyncTask<Params, Progress, Result> execute(Params... params) {
    return executeOnExecutor(sDefaultExecutor, params);
}

@MainThread
public final AsyncTask<Params, Progress, Result> executeOnExecutor(Executor exec, Params... params) {
    if (mStatus != Status.PENDING) {
        switch (mStatus) {
            case RUNNING:
                throw new IllegalStateException("Cannot execute task:"
                        + " the task is already running.");
            case FINISHED:
                throw new IllegalStateException("Cannot execute task:"
                        + " the task has already been executed "
                        + "(a task can be executed only once)");
        }
    }

    mStatus = Status.RUNNING;

    onPreExecute();

    mWorker.mParams = params;
    exec.execute(mFuture);

    return this;
}

@MainThread
protected void onPreExecute() {
}

```
在上面中可以看到我们平常使用的 execute 方法会去调用 executeOnExecutor 方法。而在 executeOnExecutor 方法内又会去调用 onPreExecute 方法。这也就是为什么 onPreExecute 方法是在任务开始运行之前调用的原因了。这里要注意的是，如果想让 AsyncTask 并行地去执行任务，那么可以在 executeOnExecutor 方法中传入一个并行的任务执行器，这样就达到了并行的效果。

总结
====
好了，AsyncTask 的源码大致就这些了，也分析地差不多了。我们总共分成了 class 、线程池、Handler 、构造器和 execute 五部分来分析。这样可能会给人比较散乱的感觉，但是连起来看就会对 AsyncTask 的原理更加了解了。那么，下面我们就来总结一下吧：

* AsyncTask 的线程池的线程数量是和 CPU 的核心数相关的。而线程池的队列是阻塞式的并且是有限的，最大容量为128。这也意味着 AsyncTask 不适合于做一些大型的、长期在后台运行的任务。因为这样可能导致着队列的溢出，会抛出异常。所以 AsyncTask 适合于一些小型的任务。

* onProgressUpdate、 onCancelled 和 onPostExecute 等都是通过 handler 的消息传递机制来调用的。所以 AsyncTask 可以理解为“线程池 + Handler”的组合。

* 在 execute 方法中，会先去调用 onPreExecute 方法，之后再在线程池中执行 mFuture 。这时会调用 doInBackground 方法开始进行任务操作。 mWorker 和 mFuture 都是在构造器中初始化完成的。

* AsyncTask 支持多线程进行任务操作，默认为单线程进行任务操作。

今天就到这里了，下次再见！

Goodbye ~~
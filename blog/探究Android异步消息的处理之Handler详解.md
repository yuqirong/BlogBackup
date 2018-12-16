title: 探究Android异步消息的处理之Handler详解
date: 2015-09-29 13:19:12
categories: Android Blog
tags: [Android,源码解析]
---
在学习Android的路上，大家肯定会遇到异步消息处理，Android提供给我们一个类来处理相关的问题，那就是Handler。相信大家大多都用过Handler了，下面我们就来看看Handler最简单的用法：

``` java
public class FirstActivity extends AppCompatActivity {

    public static final String TAG = "FirstActivity";
    private static Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0:
                    Log.i(TAG, "handler receive msg.what = " + msg.what);
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_first);
        new Thread(new Runnable() {
            @Override
            public void run() {
                //这里做相关操作
                handler.sendEmptyMessage(0);
            }
        }).start();
    }

}
```
上面代码实现了在子线程中发出一个消息，然后在主线程中接收消息。Handler其他类似的用法在这就不过多叙述了。下面我们来看看Handler到底是怎么实现异步消息处理的吧！

先来看看我们new一个Handler的对象到底发生了什么（只截取了关键源码）：

``` java
public Handler() {
        this(null, false);
    }
    
public Handler(Callback callback, boolean async) {
        if (FIND_POTENTIAL_LEAKS) {
            final Class<? extends Handler> klass = getClass();
            if ((klass.isAnonymousClass() || klass.isMemberClass() || klass.isLocalClass()) &&
                    (klass.getModifiers() & Modifier.STATIC) == 0) {
                Log.w(TAG, "The following Handler class should be static or leaks might occur: " +
                    klass.getCanonicalName());
            }
        }

        mLooper = Looper.myLooper();
        if (mLooper == null) {
            throw new RuntimeException(
                "Can't create handler inside thread that has not called Looper.prepare()");
        }
        mQueue = mLooper.mQueue;
        mCallback = callback;
        mAsynchronous = async;
    }
```
可以看到我们平常写的 new Handler()；其实是调用了另外一个构造方法，并且判断了mLooper是不是为空，为空则抛出一个异常**"Can't create handler inside thread that has not called Looper.prepare()"**，mLooper其实是一个Looper类的成员变量，官方文档上对Looper类的解释是 **Class used to run a message loop for a thread.**也就是说Looper用于在一个线程中传递message的。  然后我们根据异常的提示知道要在new一个Handler的对象之前必须
先调用Looper.prepare()。那接下来就只能先去看看Looper.prepare()方法了：

``` java
static final ThreadLocal<Looper> sThreadLocal = new ThreadLocal<Looper>();
    private static Looper sMainLooper;  // guarded by Looper.class

    final MessageQueue mQueue;
    final Thread mThread;

    private Printer mLogging;

     /** Initialize the current thread as a looper.
      * This gives you a chance to create handlers that then reference
      * this looper, before actually starting the loop. Be sure to call
      * {@link #loop()} after calling this method, and end it by calling
      * {@link #quit()}.
      */
    public static void prepare() {
        prepare(true);
    }

    private static void prepare(boolean quitAllowed) {
        if (sThreadLocal.get() != null) {
            throw new RuntimeException("Only one Looper may be created per thread");
        }
        sThreadLocal.set(new Looper(quitAllowed));
    }

private Looper(boolean quitAllowed) {
        mQueue = new MessageQueue(quitAllowed);
        mThread = Thread.currentThread();
    }

    /**
     * Run the message queue in this thread. Be sure to call
     * {@link #quit()} to end the loop.
     */
    public static void loop() {
        final Looper me = myLooper();
        if (me == null) {
            throw new RuntimeException("No Looper; Looper.prepare() wasn't called on this thread.");
        }
        final MessageQueue queue = me.mQueue;

        // Make sure the identity of this thread is that of the local process,
        // and keep track of what that identity token actually is.
        Binder.clearCallingIdentity();
        final long ident = Binder.clearCallingIdentity();

        for (;;) {
            Message msg = queue.next(); // might block
            if (msg == null) {
                // No message indicates that the message queue is quitting.
                return;
            }

            // This must be in a local variable, in case a UI event sets the logger
            Printer logging = me.mLogging;
            if (logging != null) {
                logging.println(">>>>> Dispatching to " + msg.target + " " +
                        msg.callback + ": " + msg.what);
            }

            msg.target.dispatchMessage(msg);

            if (logging != null) {
                logging.println("<<<<< Finished to " + msg.target + " " + msg.callback);
            }

            // Make sure that during the course of dispatching the
            // identity of the thread wasn't corrupted.
            final long newIdent = Binder.clearCallingIdentity();
            if (ident != newIdent) {
                Log.wtf(TAG, "Thread identity changed from 0x"
                        + Long.toHexString(ident) + " to 0x"
                        + Long.toHexString(newIdent) + " while dispatching to "
                        + msg.target.getClass().getName() + " "
                        + msg.callback + " what=" + msg.what);
            }

            msg.recycleUnchecked();
        }
    }

    /**
     * Return the Looper object associated with the current thread.  Returns
     * null if the calling thread is not associated with a Looper.
     */
    public static @Nullable Looper myLooper() {
        return sThreadLocal.get();
    }

```
prepare()方法就是将一个sThreadLocal和新建的Looper对象相绑定，同时mQueue成员变量也创建了新的MessageQueue对象，MessageQueue这个类就是用于存储Message的队列。在prepare()方法的注释上写着在调用prepare()方法之后还要调用loop()方法，我们再看loop方法，可以看到方法里写了一个for的死循环，主要用于在MessageQueue里不断地去取Message，如果msg为空，则阻塞；不然会调用msg.target.dispatchMessage(msg)这个方法。dispatchMessage()这个方法我会在后面讲解，先暂时放一边不管。

好了，捋一捋思路，当你在新建一个Handler对象时，要先确保调用了Looper.prepare()方法，然后调用Looper.loop()方法让MessageQueue这个队列“动”起来。这样你就成功地创建了一个Handler的对象。然后我们再使用Handler的sendMessage系列方法来发送一个消息。下面我们就来看看sendMessage系列方法里到底干了什么：

``` java
public boolean sendMessageAtTime(Message msg, long uptimeMillis) {
        MessageQueue queue = mQueue;
        if (queue == null) {
            RuntimeException e = new RuntimeException(
                    this + " sendMessageAtTime() called with no mQueue");
            Log.w("Looper", e.getMessage(), e);
            return false;
        }
        return enqueueMessage(queue, msg, uptimeMillis);
    }

private boolean enqueueMessage(MessageQueue queue, Message msg, long uptimeMillis) {
        msg.target = this;
        if (mAsynchronous) {
            msg.setAsynchronous(true);
        }
        return queue.enqueueMessage(msg, uptimeMillis);
    }
```
为什么我就贴出一个方法呢？这是因为Handler一系列的sendMessage方法基本上最后都是调用了sendMessageAtTime这个方法。从源码中我们看到主要就是干了把Message加入队列这个事,并把当前的Handler对象赋给了msg的target。再联系上面的Looper.loop方法，我们大概就懂了。好了，我们回过头来看看上面的msg.target.dispatchMessage(msg)主要的功能。其实就是调用了Handler的dispatchMessage方法：

``` java
 public void dispatchMessage(Message msg) {
        if (msg.callback != null) {
            handleCallback(msg);
        } else {
            if (mCallback != null) {
                if (mCallback.handleMessage(msg)) {
                    return;
                }
            }
            handleMessage(msg);
        }
    }
```
我们看到了一行熟悉的代码：handleMessage(msg)，这不正是我们再创建Handler对象时重写的那个方法么！好了，这一切的逻辑我们似乎已经搞清了：首先调用Looper.prepare()创建一个Looper对象，然后handler发送消息后把消息加入到MessageQueue里，因为之前调用了Looper.loop(),所以MessageQueue在不断地做出队的操作，然后再根据message的target变量分发消息，回到handler的handleMessage()方法。

也许有人会有疑问了，为什么在主线程中创建Handler对象可以直接使用而不需要调用Looper.prepare()和Looper.loop()两个方法呢？这是因为在ActivityThread里面已经调用了，下面附上ActivityThread的源码：

``` java
/** 
 * This manages the execution of the main thread in an 
 * application process, scheduling and executing activities, 
 * broadcasts, and other operations on it as the activity 
 * manager requests. 
 * 
 * {@hide} 
 */  
public final class ActivityThread {  
  
    static ContextImpl mSystemContext = null;  
  
    static IPackageManager sPackageManager;  
      
    // 创建ApplicationThread实例，以接收AMS指令并执行  
    final ApplicationThread mAppThread = new ApplicationThread();  
  
    final Looper mLooper = Looper.myLooper();  
  
    final H mH = new H();  
  
    final HashMap<IBinder, ActivityClientRecord> mActivities  
            = new HashMap<IBinder, ActivityClientRecord>();  
      
    // List of new activities (via ActivityRecord.nextIdle) that should  
    // be reported when next we idle.  
    ActivityClientRecord mNewActivities = null;  
      
    // Number of activities that are currently visible on-screen.  
    int mNumVisibleActivities = 0;  
      
    final HashMap<IBinder, Service> mServices  
            = new HashMap<IBinder, Service>();  
      
    Application mInitialApplication;  
  
    final ArrayList<Application> mAllApplications  
            = new ArrayList<Application>();  
  
    static final ThreadLocal<ActivityThread> sThreadLocal = new ThreadLocal<ActivityThread>();  
    Instrumentation mInstrumentation;  
  
    static Handler sMainThreadHandler;  // set once in main()  
  
    static final class ActivityClientRecord {  
        IBinder token;  
        int ident;  
        Intent intent;  
        Bundle state;  
        Activity activity;  
        Window window;  
        Activity parent;  
        String embeddedID;  
        Activity.NonConfigurationInstances lastNonConfigurationInstances;  
        boolean paused;  
        boolean stopped;  
        boolean hideForNow;  
        Configuration newConfig;  
        Configuration createdConfig;  
        ActivityClientRecord nextIdle;  
  
        String profileFile;  
        ParcelFileDescriptor profileFd;  
        boolean autoStopProfiler;  
  
        ActivityInfo activityInfo;  
        CompatibilityInfo compatInfo;  
        LoadedApk packageInfo; //包信息，通过调用ActivityThread.getPapckageInfo而获得  
  
        List<ResultInfo> pendingResults;  
        List<Intent> pendingIntents;  
  
        boolean startsNotResumed;  
        boolean isForward;  
        int pendingConfigChanges;  
        boolean onlyLocalRequest;  
  
        View mPendingRemoveWindow;  
        WindowManager mPendingRemoveWindowManager;  
  
        ...  
    }  
  
  
    private class ApplicationThread extends ApplicationThreadNative {  
  
        private void updatePendingConfiguration(Configuration config) {  
            synchronized (mPackages) {  
                if (mPendingConfiguration == null ||  
                        mPendingConfiguration.isOtherSeqNewer(config)) {  
                    mPendingConfiguration = config;  
                }  
            }  
        }  
  
        public final void schedulePauseActivity(IBinder token, boolean finished,  
                boolean userLeaving, int configChanges) {  
            queueOrSendMessage(  
                    finished ? H.PAUSE_ACTIVITY_FINISHING : H.PAUSE_ACTIVITY,  
                    token,  
                    (userLeaving ? 1 : 0),  
                    configChanges);  
        }  
  
        // we use token to identify this activity without having to send the  
        // activity itself back to the activity manager. (matters more with ipc)  
        public final void scheduleLaunchActivity(Intent intent, IBinder token, int ident,  
                ActivityInfo info, Configuration curConfig, CompatibilityInfo compatInfo,  
                Bundle state, List<ResultInfo> pendingResults,  
                List<Intent> pendingNewIntents, boolean notResumed, boolean isForward,  
                String profileName, ParcelFileDescriptor profileFd, boolean autoStopProfiler) {  
            ActivityClientRecord r = new ActivityClientRecord();  
  
            r.token = token;  
            r.ident = ident;  
            r.intent = intent;  
            r.activityInfo = info;  
            r.compatInfo = compatInfo;  
            r.state = state;  
  
            r.pendingResults = pendingResults;  
            r.pendingIntents = pendingNewIntents;  
  
            r.startsNotResumed = notResumed;  
            r.isForward = isForward;  
  
            r.profileFile = profileName;  
            r.profileFd = profileFd;  
            r.autoStopProfiler = autoStopProfiler;  
  
            updatePendingConfiguration(curConfig);  
  
            queueOrSendMessage(H.LAUNCH_ACTIVITY, r);  
        }  
  
        ...  
    }  
  
    private class H extends Handler {  
  
        public void handleMessage(Message msg) {  
            if (DEBUG_MESSAGES) Slog.v(TAG, ">>> handling: " + codeToString(msg.what));  
            switch (msg.what) {  
                case LAUNCH_ACTIVITY: {  
                    Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, "activityStart");  
                    ActivityClientRecord r = (ActivityClientRecord)msg.obj;  
  
                    r.packageInfo = getPackageInfoNoCheck(  
                            r.activityInfo.applicationInfo, r.compatInfo);  
                    handleLaunchActivity(r, null);  
                    Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);  
                } break;  
                ...  
            }  
            if (DEBUG_MESSAGES) Slog.v(TAG, "<<< done: " + codeToString(msg.what));  
        }  
         
        ...  
    }  
  
    public static ActivityThread currentActivityThread() {  
        return sThreadLocal.get();  
    }  
  
   
    public static void main(String[] args) {  
        SamplingProfilerIntegration.start();  
  
        // CloseGuard defaults to true and can be quite spammy.  We  
        // disable it here, but selectively enable it later (via  
        // StrictMode) on debug builds, but using DropBox, not logs.  
        CloseGuard.setEnabled(false);  
  
        Environment.initForCurrentUser();  
  
        // Set the reporter for event logging in libcore  
        EventLogger.setReporter(new EventLoggingReporter());  
  
        Process.setArgV0("<pre-initialized>");  
  
        Looper.prepareMainLooper();  
  
        // 创建ActivityThread实例  
        ActivityThread thread = new ActivityThread();  
        thread.attach(false);  
  
        if (sMainThreadHandler == null) {  
            sMainThreadHandler = thread.getHandler();  
        }  
  
        AsyncTask.init();  
  
        if (false) {  
            Looper.myLooper().setMessageLogging(new  
                    LogPrinter(Log.DEBUG, "ActivityThread"));  
        }  
  
        Looper.loop();  
  
        throw new RuntimeException("Main thread loop unexpectedly exited");  
    }  
}  
```
可以看到上面的main方法里的181行和198行已经调用了prepare和loop的方法。因此在主线程中使用Handler不需要再调用prepare和loop方法了。

好了，今天该讲的差不多了，就到这吧。

由于第一次写讲解源码的博客，不便之处请大家多多包涵。有问题的可以在下面评论。
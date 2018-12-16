title: 《Android开发艺术探索》笔记(下)
date: 2016-08-06 00:12:02
categories: Book Note
tags: [Android,《Android艺术开发探索》]
---

第八章：理解Window和WindowManager
=======

8.1 Window和WindowManager
----
Window是抽象类，具体实现是PhoneWindow，通过WindowManager就可以创建Window。WindowManager是外界访问Window的入口，但是Window的具体实现是在WindowManagerService中，WindowManager和WindowManagerService的交互是一个IPC过程。所有的视图例如Activity、Dialog、Toast都是附加在Window上的。因此，Window是实际上View的直接管理者。

WindowManager.LayoutParams中的flags参数解析：

* FLAG\_NOT\_FOCUSABLE：表示window不需要获取焦点，也不需要接收各种输入事件。此标记会同时启用FLAG_NOT_TOUCH_MODAL，最终事件会直接传递给下层的具有焦点的window；
* FLAG\_NOT\_TOUCH_MODAL：在此模式下，系统会将window区域外的单击事件传递给底层的window，当前window区域内的单击事件则自己处理，一般都需要开启这个标记；
* FLAG\_SHOW\_WHEN\_LOCKED：开启此模式可以让Window显示在锁屏的界面上。

type参数表示window的类型，window共有三种类型：应用window，子window和系统window。应用window对应着一个Activity，子window不能独立存在，需要附属在特定的父window之上，比如Dialog就是子window。系统window是需要声明权限才能创建的window，比如Toast和系统状态栏这些都是系统window，需要声明的权限是`<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />`。

window是分层的，每个window都对应着z-ordered，层级大的会覆盖在层级小的上面，应用window的层级范围是1~99，子window的层级范围是1000~1999，系统window的层级范围是2000~2999。

WindowManager继承自ViewManager，常用的只有三个方法：addView、updateView和removeView。

8.2 Window的内部机制
----
Window是一个抽象的概念，不是实际存在的，它也是以View的形式存在。在实际使用中无法直接访问Window，只能通过WindowManager才能访问Window。每个Window都对应着一个View和一个ViewRootImpl，Window和View通过ViewRootImpl来建立联系。

Window的添加、删除和更新过程都是IPC过程。WindowManager的实现类(即：WindowManagerImpl)对于addView、updateView和removeView方法都是委托给WindowManagerGlobal类，该类保存了很多数据列表，例如所有window对应的view集合mViews、所有window对应的ViewRootImpl的集合mRoots，其他的还有mParams和mDyingViews等。

**Window的添加过程**：

1. WindowManagerGlobal中的addView；
2. 检查参数是否合法，如果子Window还需要调节布局参数；
3. 创建ViewRootImpl并将View添加到列表中；
4. 通过ViewRootImpl的setView来更新界面并完成Window的添加过程。在setView内部会调用requestLayout来完成异步刷新请求。在requestLayout中的scheduleTraversals是View绘制的入口，最终通过WindowSession来完成Window的添加过程，注意其实这里是个IPC过程，最终会通过WindowManagerService的addWindow方法来实现Window的添加。 

**Window的删除过程**：

1. WinodwManagerGlobal中的removeView；
2. findViewLocked来查找待删除待View的索引，再调用removeViewLocked来做进一步删除；
3. removeViewLocked通过ViewRootImpl的die方法来完成删除操作，包括同步和异步两种方式，同步方式可能会导致意外的错误，不推荐，一般使用异步的方式，其实就是通过handler发送了一个删除请求，将View添加到mDyingViews中；
4. die方法本质调用了doDie方法，真正删除View的逻辑在该方法的dispatchDetachedFromWindow方法中，主要做了四件事：垃圾回收，通过Session的remove方法删除Window，调用View的dispatchDetachedFromWindow方法同时会回调View的onDetachedFromWindow以及onDetachedFromWindowInternal，调用WindowManagerGlobal的doRemoveView刷新数据。 

**Window的更新过程**：

1. WindowManagerGlobal的updateViewLayout；
2. 更新View的LayoutParams；
3. 更新ViewImple的LayoutParams，实现对View的重新测量，布局，重绘；
4. 通过WindowSession更新Window的视图，WindowManagerService.relayoutWindow()。

8.3 Window的创建过程
------
**Activity的window创建过程**：

1. Activity的启动过程很复杂，最终会由ActivityThread中的performLaunchActivity来完成整个启动过程，在这个方法内部会通过类加载器创建Activity的实例对象，并调用它的attach方法为其关联运行过程中所依赖的一系列上下文环境变量；
2. Activity实现了Window的Callback接口，当window接收到外界的状态变化时就会回调Activity的方法，例如onAttachedToWindow、onDetachedFromWindow、dispatchTouchEvent等；
3. Activity的Window是由PolicyManager来创建的，它的真正实现是Policy类，它会新建一个PhoneWindow对象，Activity的setContentView的实现是由PhoneWindow来实现的；
4. Activity的顶级View是DecorView，它本质上是一个FrameLayout。如果没有DecorView，那么PhoneWindow会先创建一个DecorView，然后加载具体的布局文件并将view添加到DecorView的mContentParent中，最后就是回调Activity的onContentChanged通知Activity视图已经发生了变化；
5. 还有一个步骤是让WindowManager能够识别DecorView，在ActivityThread调用handleResumeActivity方法时，首先会调用Activity的onResume方法，然后会调用makeVisible方法，这个方法中DecorView真正地完成了添加和显示过程。

``` java
ViewManager vm = getWindowManager();
vm.addView(mDecor, getWindow().getAttributes());
mWindowAdded = true;
```

**Dialog的Window创建过程**：

过程与Activity的Window创建过程类似，普通的Dialog的有一个特别之处，即它必须采用Activity的Context，如果采用Application的Context会报错。原因是Application没有应用token，应用token一般是Activity拥有的。解决的方案就是通过`dialog.getWindow.setType`方法设置成系统级别的type，记得在manifest中设置权限。

**Toast的Window创建过程**：

1. Toast内部有两类IPC：Toast访问NotificationManagerService；NotificationManagerService（下文简称NMS）访问Toast的TN接口；
2. Toast属于系统Window，内部视图mNextView一种为系统默认样式，另一种通过setView方法来指定一个自定义View。
3. TN是一个Binder类，NMS处理Toast的显示隐藏请求时会跨进程回调TN中的方法，所以TN运行在Binder线程池中，所以需要handler切换到当前发送Toast请求的线程中，也就是说没有Looper的线程是无法弹出Toast的。
4. Toast的show方法调用了NMS的enqueueToast方法，该方法先将Toast请求封装成ToastRecord并丢入mToastQueue队列中（非系统应用最多塞50个）。
5. NMS通过showNextToastLocked方法来显示当前View，Toast显示由ToastRecord的callback方法中的show方法完成，callback其实就是TN对象的远程Binder，所以最终调用的是TN中的方法，并运行在发起Toast请求应用的Binder线程池中。
6. 显示以后，NMS通过scheduleTimeoutLocked方法发送延时消息，延时后NMS通过cancelToastLocked方法来隐藏Toast并从队列中移除，隐藏依然通过ToastRecord的callback中的hide方法实现。
7. callback回调TN的show和hide方法后，会通过handler发送两个Runnable，里面的handleShow和handleHide方法是真正完成显示和隐藏Toast的地方。handleShow方法中将Toast的视图添加到Window中，handleHide方法将Toast视图从Window中移除。


第十章 Android的消息机制
==================
10.1 Android消息机制概述
--------------
Android的消息机制主要是指Handler的运行机制，其底层需要MessageQueue和Looper的支撑。MessageQueue是以单链表的数据结构存储消息列表但是以队列的形式对外提供插入和删除消息操作的消息队列。MessageQueue只是消息的存储单元，而Looper则是以无限循环的形式去查找是否有新消息，如果有的话就去处理消息，否则就一直等待着。

Handler的主要作用是将一个任务切换到某个指定的线程中去执行。

* 为什么要提供这个功能呢？

Android规定UI操作只能在主线程中进行，ViewRootImpl的checkThread方法会验证当前线程是否可以进行UI操作。

* 为什么不允许子线程访问UI呢？

这是因为UI组件不是线程安全的，如果在多线程中并发访问可能会导致UI组件处于不可预期的状态。另外，如果对UI组件的访问进行加锁机制的话又会降低UI访问的效率，所以还是采用单线程模型来处理UI事件。

Handler的创建会采用当前线程的Looper来构建内部的消息循环系统，如果当前线程中不存在Looper的话就会报错。Handler可以用post方法将一个Runnable投递到消息队列中，也可以用send方法发送一个消息投递到消息队列中，其实post最终还是调用了send方法。

10.2 Android的消息机制分析
--------
(1).ThreadLocal的工作原理

1. ThreadLocal是一个线程内部的数据存储类，通过它可以在指定的线程中存储数据，数据存储以后，只有在指定线程中可以获取到存储的数据，对于其他线程来说则无法获取到数据。一般来说，当某些数据是以线程为作用域并且不同线程具有不同的数据副本的时候，可以考虑使用ThreadLocal。 对于Handler来说，它需要获取当前线程的Looper，而Looper的作用域就是线程并且不同线程具有不同的Looper，这个时候通过ThreadLocal就可以实现Looper在线程中的存取了。
2. ThreadLocal的原理：不同线程访问同一个ThreadLocal的get方法时，ThreadLocal内部会从各自的线程中取出一个数组，然后再从数组中根据当前ThreadLocal的索引去查找出对应的value值，不同线程中的数组是不同的，这就是为什么通过ThreadLocal可以在不同线程中维护一套数据的副本并且彼此互不干扰。
3. ThreadLocal是一个泛型类public class ThreadLocal<T>，下面是它的set方法

		public void set(T value) {
		    Thread currentThread = Thread.currentThread();
		    Values values = values(currentThread);
		    if (values == null) {
		        values = initializeValues(currentThread);
		    }
		    values.put(this, value);
		}

	Values是Thread类内部专门用来存储线程的ThreadLocal数据的，它内部有一个数组private Object[] table，ThreadLocal的值就存在这个table数组中。如果values的值为null，那么就需要对其进行初始化然后再将ThreadLocal的值进行存储。
	ThreadLocal数据的存储规则：ThreadLocal的值在table数组中的存储位置总是ThreadLocal的索引+1的位置。

(2).MessageQueue的工作原理

1. MessageQueue其实是通过单链表来维护消息列表的，它包含两个主要操作enqueueMessage和next，前者是插入消息，后者是取出一条消息并移除。
2. next方法是一个无限循环的方法，如果消息队列中没有消息，那么next方法会一直阻塞在这里。当有新消息到来时，next方法会返回这条消息并将它从链表中移除。

(3).Looper的工作原理
1. 为一个线程创建Looper的方法，代码如下所示

		new Thread("test"){
		    @Override
		    public void run() {
		        Looper.prepare();//创建looper
		        Handler handler = new Handler();//可以创建handler了
		        Looper.loop();//开始looper循环
		    }
		}.start();

2. Looper的prepareMainLooper方法主要是给主线程也就是ActivityThread创建Looper使用的，本质也是调用了prepare方法。
3. Looper的quit和quitSafely方法的区别是：前者会直接退出Looper，后者只是设定一个退出标记，然后把消息队列中的已有消息处理完毕后才安全地退出。Looper退出之后，通过Handler发送的消息就会失败，这个时候Handler的send方法会返回false。
在子线程中，如果手动为其创建了Looper，那么在所有的事情完成以后应该调用quit方法来终止消息循环，否则这个子线程就会一直处于等待的状态，而如果退出Looper以后，这个线程就会立刻终止，因此建议不需要的时候终止Looper。
4. 当Looper的quit方法被调用时，Looper就会调用MessageQueue的quit或者quitSafely方法来通知消息队列退出，当消息队列被标记为退出状态时，它的next方法就会返回null。也就是说，Looper必须退出，否则loop方法就会无限循环下去。Looper的loop方法会调用MessageQueue的next方法来获取新消息，而next是一个阻塞操作，当没有消息时，next方法会一直阻塞着在那里，这也导致了loop方法一直阻塞在那里。如果MessageQueue的next方法返回了新消息，Looper就会处理这条消息：msg.target.dispatchMessage(msg)，其中的msg.target就是发送这条消息的Handler对象。

(4).Handler的工作原理

1. Handler就是处理消息的发送和接收之后的处理；
2. Handler处理消息的过程

		public void dispatchMessage(Message msg) {
		    if (msg.callback != null) {
		        handleCallback(msg);//当message是runnable的情况，也就是Handler的post方法传递的参数，这种情况下直接执行runnable的run方法
		    } else {
		        if (mCallback != null) {//如果创建Handler的时候是给Handler设置了Callback接口的实现，那么此时调用该实现的handleMessage方法
		            if (mCallback.handleMessage(msg)) {
		                return;
		            }
		        }
		        handleMessage(msg);//如果是派生Handler的子类，就要重写handleMessage方法，那么此时就是调用子类实现的handleMessage方法
		    }
		}
		
		private static void handleCallback(Message message) {
		        message.callback.run();
		}
		
		/**
		 * Subclasses must implement this to receive messages.
		 */
		public void handleMessage(Message msg) {
		}

3. Handler还有一个特殊的构造方法，它可以通过特定的Looper来创建Handler。

		public Handler(Looper looper){
		  this(looper, null, false);
		}

10.3 主线程的消息循环
-------------
Android的主线程就是ActivityThread，主线程的入口方法就是main，其中调用了Looper.prepareMainLooper()来创建主线程的Looper以及MessageQueue，并通过Looper.loop()方法来开启主线程的消息循环。主线程内有一个Handler，即ActivityThread.H，它定义了一组消息类型，主要包含了四大组件的启动和停止等过程，例如LAUNCH_ACTIVITY等。

ActivityThread通过ApplicationThread和AMS进行进程间通信，AMS以进程间通信的方法完成ActivityThread的请求后会回调ApplicationThread中的Binder方法，然后ApplicationThread会向H发送消息，H收到消息后会将ApplicationThread中的逻辑切换到ActivityThread中去执行，即切换到主线程中去执行，这个过程就是主线程的消息循环模型。

第十一章 Android的线程和线程池
======
11.1 主线程和子线程
-------
1. 在Java中默认情况下一个进程只有一个线程，也就是主线程，其他线程都是子线程，也叫工作线程。Android中的主线程主要处理和界面相关的事情，而子线程则往往用于执行耗时操作。线程的创建和销毁的开销较大，所以如果一个进程要频繁地创建和销毁线程的话，都会采用线程池的方式。
2. 在Android中除了Thread，还有HandlerThread、AsyncTask以及IntentService等也都扮演着线程的角色，只是它们具有不同的特性和使用场景。AsyncTask封装了线程池和Handler，它主要是为了方便开发者在子线程中更新UI。HandlerThread是一种具有消息循环的线程，在它的内部可以使用Handler。IntentService是一个服务，它内部采用HandlerThread来执行任务，当任务执行完毕后就会自动退出。因为它是服务的缘故，所以和后台线程相比，它比较不容易被系统杀死。
3. 从Android 3.0开始，系统要求网络访问必须在子线程中进行，否则网络访问将会失败并抛出NetworkOnMainThreadException这个异常，这样做是为了避免主线程由于被耗时操作所阻塞从而出现ANR现象。
4. AsyncTask是一个抽象泛型类，它提供了Params、Progress、Result三个泛型参数，如果task确实不需要传递具体的参数，那么都可以设置为Void。下面是它的四个核心方法，其中doInBackground不是在主线程执行的。
onPreExecute、doInBackground、onProgressUpdate、onPostResult

11.2 Android中的线程形态
---------
1. AsyncTask的使用过程中的条件限制：

	(1).AsyncTask的类必须在主线程中加载，这个过程在Android 4.1及以上版本中已经被系统自动完成。

	(2).AsyncTask对象必须在主线程中创建，execute方法必须在UI线程中调用。

	(3).一个AsyncTask对象只能执行一次，即只能调用一次execute方法，否则会报运行时异常。

	(4).在Android 1.6之前，AsyncTask是串行执行任务的，Android 1.6的时候AsyncTask开始采用线程池并行处理任务，但是从Android 3.0开始，为了避免AsyncTask带来的并发错误，AsyncTask又采用一个线程来串行执行任务。尽管如此，在Android 3.0以及后续版本中，我们可以使用AsyncTask的executeOnExecutor方法来并行执行任务。但是这个方法是Android 3.0新添加的方法，并不能在低版本上使用。

2. AsyncTask的原理：

	(1).AsyncTask中有两个线程池：SerialExecutor和THREAD_POOL_EXECUTOR。前者是用于任务的排队，默认是串行的线程池；后者用于真正执行任务。AsyncTask中还有一个Handler，即InternalHandler，用于将执行环境从线程池切换到主线程。AsyncTask内部就是通过InternalHandler来发送任务执行的进度以及执行结束等消息。
	
	(2).AsyncTask排队执行过程：系统先把参数Params封装为FutureTask对象，它相当于Runnable；接着将FutureTask交给SerialExecutor的execute方法，它先把FutureTask插入到任务队列tasks中，如果这个时候没有正在活动的AsyncTask任务，那么就会执行下一个AsyncTask任务，同时当一个AsyncTask任务执行完毕之后，AsyncTask会继续执行其他任务直到所有任务都被执行为止。

3. HandlerThread就是一种可以使用Handler的Thread，它的实现就是在run方法中通过Looper.prepare()来创建消息队列，并通过Looper.loop()来开启消息循环，这样在实际的使用中就允许在HandlerThread中创建Handler了，外界可以通过Handler的消息方式通知HandlerThread执行一个具体的任务。HandlerThread的run方法是一个无限循环，因此当明确不需要再使用HandlerThread的时候，可以通过它的quit或者quitSafely方法来终止线程的执行。HandlerThread的最主要的应用场景就是用在IntentService中。

4. IntentService是一个继承自Service的抽象类，要使用它就要创建它的子类。IntentService适合执行一些高优先级的后台任务，这样不容易被系统杀死。IntentService的onCreate方法中会创建HandlerThread，并使用HandlerThread的Looper来构造一个Handler对象ServiceHandler，这样通过ServiceHandler对象发送的消息最终都会在HandlerThread中执行。IntentService会将Intent封装到Message中，通过ServiceHandler发送出去，在ServiceHandler的handleMessage方法中会调用IntentService的抽象方法onHandleIntent，所以IntentService的子类都要是实现这个方法。

11.3 Android中的线程池
-------------------
**使用线程池的好处**：

1. 重用线程，避免线程的创建和销毁带来的性能开销；
2. 能有效控制线程池的最大并发数，避免大量的线程之间因互相抢占系统资源而导致的阻塞现象；
3. 能够对线程进行简单的管理，并提供定时执行以及指定间隔循环执行等功能。

Executor只是一个接口，真正的线程池是ThreadPoolExecutor。ThreadPoolExecutor提供了一系列参数来配置线程池，通过不同的参数可以创建不同的线程池，Android的线程池都是通过Executors提供的工厂方法得到的。

**ThreadPoolExecutor的构造参数**：

1. corePoolSize：核心线程数，默认情况下，核心线程会在线程中一直存活；
2. maximumPoolSize：最大线程数，当活动线程数达到这个数值后，后续的任务将会被阻塞；
3. keepAliveTime：非核心线程闲置时的超时时长，超过这个时长，闲置的非核心线程就会被回收；
4. unit：用于指定keepAliveTime参数的时间单位，有TimeUnit.MILLISECONDS、TimeUnit.SECONDS、TimeUnit.MINUTES等；
5. workQueue：任务队列，通过线程池的execute方法提交的Runnable对象会存储在这个参数中；
6. threadFactory：线程工厂，为线程池提供创建新线程的功能。它是一个接口，它只有一个方法Thread newThread(Runnable r)；
7. RejectedExecutionHandler：当线程池无法执行新任务时，可能是由于任务队列已满或者是无法成功执行任务，这个时候就会调用这个Handler的rejectedExecution方法来通知调用者，默认情况下，rejectedExecution会直接抛出一个rejectedExecutionException。

**ThreadPoolExecutor执行任务的规则**：

1. 如果线程池中的线程数未达到核心线程的数量，那么会直接启动一个核心线程来执行任务；
2. 如果线程池中的线程数量已经达到或者超过核心线程的数量，那么任务会被插入到任务队列中排队等待执行；
3. 如果在步骤2中无法将任务插入到的任务队列中，可能是任务队列已满，这个时候如果线程数量没有达到规定的最大值，那么会立刻启动非核心线程来执行这个任务；
4. 如果步骤3中线程数量已经达到线程池规定的最大值，那么就拒绝执行此任务，ThreadPoolExecutor会调用RejectedExecutionHandler的rejectedExecution方法来通知调用者。

**AsyncTask的THREAD\_POOL\_EXECUTOR线程池的配置**：

1. corePoolSize=CPU核心数+1；
2. maximumPoolSize=2倍的CPU核心数+1；
3. 核心线程无超时机制，非核心线程在闲置时间的超时时间为1s；
4. 任务队列的容量为128。

**Android中常见的4类具有不同功能特性的线程池**：

1. FixedThreadPool：线程数量固定的线程池，它只有核心线程；
2. CachedThreadPool：线程数量不固定的线程池，它只有非核心线程；
3. ScheduledThreadPool：核心线程数量固定，非核心线程数量没有限制的线程池，主要用于执行定时任务和具有固定周期的任务；
4. SingleThreadPool：只有一个核心线程的线程池，确保了所有的任务都在同一个线程中按顺序执行。

第十二章 Bitmap的加载和Cache
==============
12.1 Bitmap的高速加载
-----------------------
1. Bitmap是如何加载的？

	BitmapFactory类提供了四类方法：decodeFile、decodeResource、decodeStream和decodeByteArray从不同来源加载出一个Bitmap对象，最终的实现是在底层实现的。

2. 如何高效加载Bitmap？

	采用BitmapFactory.Options按照一定的采样率来加载所需尺寸的图片，因为imageview所需的图片大小往往小于图片的原始尺寸。

3. BitmapFactory.Options的inSampleSize参数，即采样率
官方文档指出采样率的取值应该是2的指数，例如k，那么采样后的图片宽高均为原图片大小的 1/k。
如何获取采样率？

	下面是常用的获取采样率的代码片段：

		public Bitmap decodeSampledBitmapFromResource(Resources res, int resId, int reqWidth, int reqHeight) {
		    // First decode with inJustDecodeBounds=true to check dimensions
		    final BitmapFactory.Options options = new BitmapFactory.Options();
		    options.inJustDecodeBounds = true;
		    BitmapFactory.decodeResource(res, resId, options);
		
		    // Calculate inSampleSize
		    options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
		
		    // Decode bitmap with inSampleSize set
		    options.inJustDecodeBounds = false;
		    return BitmapFactory.decodeResource(res, resId, options);
		}
		
		public int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
		    if (reqWidth == 0 || reqHeight == 0) {
		        return 1;
		    }
		
		    // Raw height and width of image
		    final int height = options.outHeight;
		    final int width = options.outWidth;
		    Log.d(TAG, "origin, w= " + width + " h=" + height);
		    int inSampleSize = 1;
		
		    if (height > reqHeight || width > reqWidth) {
		        final int halfHeight = height / 2;
		        final int halfWidth = width / 2;
		
		        // Calculate the largest inSampleSize value that is a power of 2 and
		        // keeps both height and width larger than the requested height and width.
		        while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
		            inSampleSize *= 2;
		        }
		    }
		
		    Log.d(TAG, "sampleSize:" + inSampleSize);
		    return inSampleSize;
		}

	将inJustDecodeBounds设置为true的时候，BitmapFactory只会解析图片的原始宽高信息，并不会真正的加载图片，所以这个操作是轻量级的。需要注意的是，这个时候BitmapFactory获取的图片宽高信息和图片的位置以及程序运行的设备有关，这都会导致BitmapFactory获取到不同的结果。

12.2 Android中的缓存策略
---------------
1. 最常用的缓存算法是LRU，核心是当缓存满时，会优先淘汰那些近期最少使用的缓存对象，系统中采用LRU算法的缓存有两种：LruCache(内存缓存)和DiskLruCache(磁盘缓存)。
2. LruCache是Android 3.1才有的，通过support-v4兼容包可以兼容到早期的Android版本。LruCache类是一个线程安全的泛型类，它内部采用一个LinkedHashMap以强引用的方式存储外界的缓存对象，其提供了get和put方法来完成缓存的获取和添加操作，当缓存满时，LruCache会移除较早使用的缓存对象，然后再添加新的缓存对象。
3. DiskLruCache磁盘缓存，它不属于Android sdk的一部分，[它的源码可以在这里下载](/uploads/20160826/DiskLruCache.java)
DiskLruCache的创建、缓存查找和缓存添加操作
4. ImageLoader的实现 具体内容看源码，[点击下载](/uploads/20160826/ImageLoader.java)
功能：图片的同步/异步加载，图片压缩，内存缓存，磁盘缓存，网络拉取

12.3 ImageLoader的使用
-----------------
避免发生列表item错位的解决方法：给显示图片的imageview添加tag属性，值为要加载的图片的目标url，显示的时候判断一下url是否匹配。
优化列表的卡顿现象

1. 不要在getView中执行耗时操作，不要在getView中直接加载图片，否则肯定会导致卡顿；
2. 控制异步任务的执行频率：在列表滑动的时候停止加载图片，等列表停下来以后再加载图片；
3. 使用硬件加速来解决莫名的卡顿问题，给Activity添加配置`android:hardwareAccelerated="true"`。

第十三章：综合技术
=======
13.1 使用CrashHandler来获取应用的Crash信息
-------------------
应用发生Crash在所难免，但是如何采集crash信息以供后续开发处理这类问题呢？利用Thread类的setDefaultUncaughtExceptionHandler方法！defaultUncaughtHandler是Thread类的静态成员变量，所以如果我们将自定义的UncaughtExceptionHandler设置给Thread的话，那么当前进程内的所有线程都能使用这个UncaughtExceptionHandler来处理异常了。

``` java
public static void setDefaultUncaughtExceptionHandler(UncaughtExceptionHandler handler) {
    Thread.defaultUncaughtHandler = handler;
}
```

这里实现了一个简易版本的UncaughtExceptionHandler类的子类[CrashHandler，点击下载](/uploads/20160826/CrashHandler.java)。

CrashHandler的使用方式就是在Application的onCreate方法中设置一下即可

``` java
//在这里为应用设置异常处理程序，然后我们的程序才能捕获未处理的异常
CrashHandler crashHandler = CrashHandler.getInstance();
crashHandler.init(this);
```

13.2 使用multidex来解决方法数越界
------
在Android中单个dex文件所能够包含的最大方法数是65536，这包含Android Framework、依赖的jar以及应用本身的代码中的所有方法。如果方法数超过了最大值，那么编译会报错`DexIndexOverflowException: method ID not in [0, 0xffff]:65536`。

有时方法数没有超过最大值，但是安装在低版本手机上时应用异常终止了，报错：

```
E/dalvikvm : Optimization failed
E/installed : dexopt failed on '/data/dalvik-cache/data@app@com.ryg.multidextest-2.apk@classes.dex' res=65280
```

这是因为应用在安装的时候，系统会通过dexopt程序来优化dex文件，在优化的过程中dexopt采用一个固定大小的缓冲区来存储应用中所有方法的信息，这个缓冲区就是LinearAlloc。LinearAlloc缓冲区在新版本的Android系统中大小是8MB或者16MB，但是在Android 2.2和2.3中却只有5MB，当待安装的应用的方法数比较多的时候，尽管它还没有达到最大方法数，但是它的存储空间仍然有可能超过5MB，这种情况下dexopt就会报错导致安装失败。

如何解决方法数越界的问题呢？ Google在2014年提出了简单方便的multidex的解决方案。
在Android 5.0之前使用multidex需要引入android-support-multidex.jar包，从Android 5.0开始，系统默认支持了multidex，它可以从apk中加载多个dex。这里Multidex方案主要针对AndroidStudio和Gradle编译环境。

使用Multidex的步骤：

1. 在build.gradle文件中添加multiDexEnabled true

		android {
		    ...
		
		    defaultConfig {
		        ...
		
		        multiDexEnabled true // [添加的配置] enable multidex support
		    }
		    ...
		}

2. 添加对multidex的依赖

		compile 'com.android.support:multidex:1.0.0'

3. 在代码中添加对multidex的支持，这里有三种方案：

	① 在AndroidManifest文件中指定Application为MultiDexApplication；

		<application android:name="android.support.multidex.MultiDexApplication"
		...
		</application>

	② 让应用的Application继承自MultiDexApplication；

	③ 重写Application的attachBaseContext方法，这个方法要先于onCreate方法执行；

		public class TestApplication extends Application {
		
		    @Override
		    protected void attachBaseContext(Context base) {
		        super.attachBaseContext(base);
		        MultiDex.install(this);
		    }
		}

采用上面的配置之后，如果应用的方法数没有越界，那么Gradle并不会生成多个dex文件；如果方法数越界后，Gradle就会在apk中打包2个或者多个dex文件，具体会打包多少个dex文件要看当前项目的代码规模。在有些情况下，可能需要指定主dex文件中所要包含的类，这个可以通过--main-dex-list选项来实现这个功能。

build.gradle：

```
afterEvaluate {
    println "afterEvaluate"
    tasks.matching {
        it.name.startsWith('dex')
    }.each { dx ->
        def listFile = project.rootDir.absolutePath + '/app/maindexlist.txt'
        println "root dir:" + project.rootDir.absolutePath
        println "dex task found: " + dx.name
        if (dx.additionalParameters == null) {
            dx.additionalParameters = []
        }
        dx.additionalParameters += '--multi-dex'
        dx.additionalParameters += '--main-dex-list=' + listFile
        dx.additionalParameters += '--minimal-main-dex'
    }
}
```

maindexlist.txt：

	com/ryg/multidextest/TestApplication.class
	com/ryg/multidextest/MainActivity.class
	
	// multidex
	android/support/multidex/MultiDex.class
	android/support/multidex/MultiDexApplication.class
	android/support/multidex/MultiDexExtractor.class
	android/support/multidex/MultiDexExtractor$1.class
	android/support/multidex/MultiDex$V4.class
	android/support/multidex/MultiDex$V14.class
	android/support/multidex/MultiDex$V19.class
	android/support/multidex/ZipUtil.class
	android/support/multidex/ZipUtil$CentralDirectory.class

--multi-dex表明当方法数越界时生成多个dex文件，--main-dex-list指定了要在主dex中打包的类的列表，--minimal-main-dex表明只有--main-dex-list所指定的类才能打包到主dex中。multidex的jar包中的9个类必须要打包到主dex中，其次不能在Application中成员以及代码块中访问其他dex中的类，否个程序会因为无法加载对应的类而中止执行。

Multidex方案可能带来的问题：
1. 应用启动速度会降低，因为应用启动的时候会加载额外的dex文件，甚至可能出现ANR现象。所以要避免生成较大的dex文件；
2. 需要做大量的兼容性测试，因为Dalvik LinearAlloc的bug，可能导致使用multidex的应用无法在Android 4.0以前的手机上运行。同时由于Dalvik linearAlloc的bug，有可能会出现应用在运行中由于采用了multidex方案从而产生大量的内存消耗的情况，这会导致奔溃。

在实际项目中，1.中的现象是客观存在的，但是2.中的现象目前极少遇到。

13.3 Android的动态加载技术
------
动态加载技术又称插件化技术，将应用插件化可以减轻应用的内存和CPU占用，还可以在不发布新版本的情况下更新某些模块。不同的插件化方案各有特色，但是都需要解决三个基础性问题：资源访问，Activity生命周期管理和插件ClassLoader的管理。

宿主和插件：宿主是指普通的apk，插件是经过处理的dex或者apk。在主流的插件化框架中多采用特殊处理的apk作为插件，处理方式往往和编译以及打包环节有关，另外很多插件化框架都需要用到代理Activity的概念，插件Activity的启动大多数是借助一个代理Activity来实现的。

三个基础性问题的解决方案：

1. 资源访问：宿主程序调起未安装的插件apk，插件中凡是R开头的资源都不能访问了，因为宿主程序中并没有插件的资源，通过R来访问插件的资源是行不通的。
Activity的资源访问是通过ContextImpl来完成的，它有两个方法getAssets()和getResources()方法是用来加载资源的。
具体实现方式是通过反射，调用AssetManager的addAssetPath方法添加插件的路径，然后将插件apk中的资源加载到Resources对象中即可。
2. Activity生命周期管理：有两种常见的方式，反射方式和接口方式。反射方式就是通过反射去获取Activity的各个生命周期方法，然后在代理Activity中去调用插件Activity对应的生命周期方法即可。
反射方式代码繁琐，性能开销大。接口方式将Activity的生命周期方法提取出来作为一个接口，然后通过代理Activity去调用插件Activity的生命周期方法，这样就完成了插件Activity的生命周期管理。
3. 插件ClassLoader的管理：为了更好地对多插件进行支持，需要合理地去管理各个插件的DexClassLoader，这样同一个插件就可以采用同一个ClassLoader去加载类，从而避免了多个ClassLoader加载同一个类时所引起的类型转换错误。

其他详细信息看作者插件化框架 [singwhatiwanna/dynamic-load-apk](https://github.com/singwhatiwanna/dynamic-load-apk)

13.4 反编译初步
-----------
反编译可查看[Android安全机制之反编译](/2016/04/03/Android%E5%AE%89%E5%85%A8%E6%9C%BA%E5%88%B6%E4%B9%8B%E5%8F%8D%E7%BC%96%E8%AF%91/)

二次打包：

	apktool.bat b [解包后文件所在的位置] [二次打包之后的文件名]

签名：
	
	java -jar signapk.jar testkey.x509.pem testkey.pk8 [未签名apk] [已签名apk]

第十五章 Android性能优化
=====

2015年Google关于Android性能优化典范的专题视频：

[Youtube视频地址](https://www.youtube.com/playlist?list=PLWz5rJ2EKKc9CBxr3BVjPTPoDPLdPIFCE)

15.1 Android的性能优化方法
-----
1. 布局优化

	(1).删除布局中无用的组件和层级，有选择地使用性能较低的ViewGroup；

	(2).使用<include>、<merge>、<viewstub>等标签：<include>标签主要用于布局重用，<merge>标签一般和<include>配合使用，它可以减少布局中的层级；<viewstub>标签则提供了按需加载的功能，当需要的时候才会将ViewStub中的布局加载到内存，提供了程序的初始化效率。

	(3).<include>标签只支持android:layout_开头的属性，android:id属性例外。

	(4).ViewStub继承自View，它非常轻量级且宽高都为0，它本身不参与任何的布局和绘制过程。实际开发中，很多布局文件在正常情况下不会显示，例如网络异常时的界面，这个时候就没有必要在整个界面初始化的时候加载进行，通过ViewStub可以做到在需要的时候再加载。
	如下面示例，android:id是ViewStub的id，而android:inflatedId是布局的根元素的id。
	
		<ViewStub android:id="@+id/xxx"
		  android:inflatedId="@+id/yyy"
		  android:layout="@layout/zzz"
		  ...
		</ViewStub>

2. 绘制优化

	(1).在onDraw中不要创建新的布局对象，因为onDraw会被频繁调用；
	(2).onDraw方法中不要指定耗时任务，也不能执行成千上万次的循环操作。

3. 内存泄露优化

	可能导致内存泄露的场景很多，例如静态变量、单例模式、属性动画、AsyncTask、Handler等等

4. 响应速度优化和ANR日志分析

	(1).ANR出现的情况：Activity如果5s内没有响应屏幕触摸事件或者键盘输入事件就会ANR，而BroadcastReceiver如果10s内没有执行完操作也会出现ANR。
	
	(2).当一个进程发生了ANR之后，系统会在/data/anr目录下创建一个文件traces.txt，通过分析这个文件就能定位ANR的原因。

5. ListView和Bitmap优化

	(1).ListView优化：采用ViewHolder并避免在getView方法中执行耗时操作；根据列表的滑动状态来绘制任务的执行频率；可以尝试开启硬件加速来使ListView的滑动更加流畅。

	(2).Bitmap优化：根据需要对图片进行采样，详情请看第12章 Bitmap的加载和Cache。

6. 线程优化

	采用线程池，详情请看第11章 Android的线程和线程池。

7. 一些性能优化建议

	(1).不要过多使用枚举，枚举占用的内存空间要比整型大；

	(2).常量请使用static final来修饰；

	(3).使用一些Android特有的数据结构，比如SparseArray和Pair等，他们都具有更好的性能；

	(4).适当使用软引用和弱引用；

	(5).采用内存缓存和磁盘缓存；

	(6).尽量采用静态内部类，这样可以避免潜在的由于内部类而导致的内存泄露。
	
15.2 内存泄露分析之MAT工具
-------
MAT是功能强大的内存分析工具，主要有Histograms和Dominator Tree等功能。

详细的可以查看 [内存泄露从入门到精通三部曲](http://gold.xitu.io/entry/563b341e60b20bd506b55592)。

15.3 提高程序的可维护性
------

1. 命名规范。比如私有成员变量以m开头，静态成员变量以s开头，常量全部以大写字母表示；
2. 代码的排版上需要留出合理的空白来区分不同的代码块；
3. 仅为非常关键的代码添加注释，其他地方不用注释。
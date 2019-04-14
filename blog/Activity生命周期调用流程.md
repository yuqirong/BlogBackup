title: Activity生命周期调用流程
date: 2019-04-09 22:26:55
categories: Android Blog
tags: [Android,Activity]
---
注：源码分析基于 Android SDK API 28 

在前一篇中，我们分析了 startActivity 的整个流程，并且也讲到了何时调用了 onCreate() 。

那么就会有一个疑问，其他的生命周期方法是在哪里被调用的呢？今天就来揭开这个谜底。

我们知道，Activity A 启动 Activity B ，其生命周期方法调用如下：

1. Activity A onPause()
2. Activity B onCreate()
3. Activity B onStart()
4. Activity B onResume()
5. Activity A onStop()

那首先我们来看看 Activity A 的 onPause() 是什么地方调用的？

onPause()
=========
在前一篇文章中讲到，startActivity 的流程中有一步是 resumeTopActivityInnerLocked 。

我们来看一下其中的源码片段：

``` java
boolean pausing = mStackSupervisor.pauseBackStacks(userLeaving, next, false);
if (mResumedActivity != null) {
    if (DEBUG_STATES) Slog.d(TAG_STATES,
            "resumeTopActivityLocked: Pausing " + mResumedActivity);
    pausing |= startPausingLocked(userLeaving, false, next, false);
}
```

从 startPausingLocked 方法的名字上来看，这就是去调用 onPause 方法的入口。

``` java
final boolean startPausingLocked(boolean userLeaving, boolean uiSleeping,
        ActivityRecord resuming, boolean pauseImmediately) {
    if (mPausingActivity != null) {
        Slog.wtf(TAG, "Going to pause when pause is already pending for " + mPausingActivity
                + " state=" + mPausingActivity.getState());
        if (!shouldSleepActivities()) {
            // Avoid recursion among check for sleep and complete pause during sleeping.
            // Because activity will be paused immediately after resume, just let pause
            // be completed by the order of activity paused from clients.
            completePauseLocked(false, resuming);
        }
    }
    ActivityRecord prev = mResumedActivity;

    if (prev == null) {
        if (resuming == null) {
            Slog.wtf(TAG, "Trying to pause when nothing is resumed");
            mStackSupervisor.resumeFocusedStackTopActivityLocked();
        }
        return false;
    }

    if (prev == resuming) {
        Slog.wtf(TAG, "Trying to pause activity that is in process of being resumed");
        return false;
    }

    if (DEBUG_STATES) Slog.v(TAG_STATES, "Moving to PAUSING: " + prev);
    else if (DEBUG_PAUSE) Slog.v(TAG_PAUSE, "Start pausing: " + prev);
    mPausingActivity = prev;
    mLastPausedActivity = prev;
    mLastNoHistoryActivity = (prev.intent.getFlags() & Intent.FLAG_ACTIVITY_NO_HISTORY) != 0
            || (prev.info.flags & ActivityInfo.FLAG_NO_HISTORY) != 0 ? prev : null;
    prev.setState(PAUSING, "startPausingLocked");
    prev.getTask().touchActiveTime();
    clearLaunchTime(prev);

    mStackSupervisor.getLaunchTimeTracker().stopFullyDrawnTraceIfNeeded(getWindowingMode());

    mService.updateCpuStats();
    // 这里开始调用 onPause
    if (prev.app != null && prev.app.thread != null) {
        if (DEBUG_PAUSE) Slog.v(TAG_PAUSE, "Enqueueing pending pause: " + prev);
        try {
            EventLogTags.writeAmPauseActivity(prev.userId, System.identityHashCode(prev),
                    prev.shortComponentName, "userLeaving=" + userLeaving);
            mService.updateUsageStats(prev, false);

            mService.getLifecycleManager().scheduleTransaction(prev.app.thread, prev.appToken,
                    PauseActivityItem.obtain(prev.finishing, userLeaving,
                            prev.configChangeFlags, pauseImmediately));
        } catch (Exception e) {
            // Ignore exception, if process died other code will cleanup.
            Slog.w(TAG, "Exception thrown during pause", e);
            mPausingActivity = null;
            mLastPausedActivity = null;
            mLastNoHistoryActivity = null;
        }
    } else {
        mPausingActivity = null;
        mLastPausedActivity = null;
        mLastNoHistoryActivity = null;
    }

    // If we are not going to sleep, we want to ensure the device is
    // awake until the next activity is started.
    if (!uiSleeping && !mService.isSleepingOrShuttingDownLocked()) {
        mStackSupervisor.acquireLaunchWakelock();
    }

    if (mPausingActivity != null) {
        // Have the window manager pause its key dispatching until the new
        // activity has started.  If we're pausing the activity just because
        // the screen is being turned off and the UI is sleeping, don't interrupt
        // key dispatch; the same activity will pick it up again on wakeup.
        if (!uiSleeping) {
            prev.pauseKeyDispatchingLocked();
        } else if (DEBUG_PAUSE) {
             Slog.v(TAG_PAUSE, "Key dispatch not paused for screen off");
        }

        if (pauseImmediately) {
            // If the caller said they don't want to wait for the pause, then complete
            // the pause now.
            completePauseLocked(false, resuming);
            return false;

        } else {
            schedulePauseTimeout(prev);
            return true;
        }

    } else {
        // This activity failed to schedule the
        // pause, so just treat it as being paused now.
        if (DEBUG_PAUSE) Slog.v(TAG_PAUSE, "Activity not running, resuming next.");
        if (resuming == null) {
            mStackSupervisor.resumeFocusedStackTopActivityLocked();
        }
        return false;
    }
}
```

和调用 onCreate 一样，onPause 也是利用 Transaction 来完成的。不过这里的是 PauseActivityItem 。

追踪到 PauseActivityItem 的 execute 方法

``` java
@Override
public void execute(ClientTransactionHandler client, IBinder token,
        PendingTransactionActions pendingActions) {
    Trace.traceBegin(TRACE_TAG_ACTIVITY_MANAGER, "activityPause");
    // 调用 ActivityThread 的 handlePauseActivity 方法
    client.handlePauseActivity(token, mFinished, mUserLeaving, mConfigChanges, pendingActions,
            "PAUSE_ACTIVITY_ITEM");
    Trace.traceEnd(TRACE_TAG_ACTIVITY_MANAGER);
}
```

发现还是一个套路，最终还是要去 ActivityThread 中找答案。

``` java
@Override
public void handlePauseActivity(IBinder token, boolean finished, boolean userLeaving,
        int configChanges, PendingTransactionActions pendingActions, String reason) {
    ActivityClientRecord r = mActivities.get(token);
    if (r != null) {
        if (userLeaving) {
            performUserLeavingActivity(r);
        }

        r.activity.mConfigChangeFlags |= configChanges;
        performPauseActivity(r, finished, reason, pendingActions);

        // Make sure any pending writes are now committed.
        if (r.isPreHoneycomb()) {
            QueuedWork.waitToFinish();
        }
        mSomeActivitiesChanged = true;
    }
}
```

重点关注 performPauseActivity

``` java
private Bundle performPauseActivity(ActivityClientRecord r, boolean finished, String reason,
        PendingTransactionActions pendingActions) {
    if (r.paused) {
        if (r.activity.mFinished) {
            // If we are finishing, we won't call onResume() in certain cases.
            // So here we likewise don't want to call onPause() if the activity
            // isn't resumed.
            return null;
        }
        RuntimeException e = new RuntimeException(
                "Performing pause of activity that is not resumed: "
                + r.intent.getComponent().toShortString());
        Slog.e(TAG, e.getMessage(), e);
    }
    if (finished) {
        r.activity.mFinished = true;
    }

    // Pre-Honeycomb apps always save their state before pausing
    // 调用 Activity 的 OnSaveInstanceState 方法
    final boolean shouldSaveState = !r.activity.mFinished && r.isPreHoneycomb();
    if (shouldSaveState) {
        callActivityOnSaveInstanceState(r);
    }
    // 调用 onPause
    performPauseActivityIfNeeded(r, reason);

    // Notify any outstanding on paused listeners
    ArrayList<OnActivityPausedListener> listeners;
    synchronized (mOnPauseListeners) {
        listeners = mOnPauseListeners.remove(r.activity);
    }
    int size = (listeners != null ? listeners.size() : 0);
    for (int i = 0; i < size; i++) {
        listeners.get(i).onPaused(r.activity);
    }

    final Bundle oldState = pendingActions != null ? pendingActions.getOldState() : null;
    if (oldState != null) {
        // We need to keep around the original state, in case we need to be created again.
        // But we only do this for pre-Honeycomb apps, which always save their state when
        // pausing, so we can not have them save their state when restarting from a paused
        // state. For HC and later, we want to (and can) let the state be saved as the
        // normal part of stopping the activity.
        if (r.isPreHoneycomb()) {
            r.state = oldState;
        }
    }

    return shouldSaveState ? r.state : null;
}

private void performPauseActivityIfNeeded(ActivityClientRecord r, String reason) {
    if (r.paused) {
        // You are already paused silly...
        return;
    }

    try {
        r.activity.mCalled = false;
        // 调用 Activity.onPause
        mInstrumentation.callActivityOnPause(r.activity);
        if (!r.activity.mCalled) {
            throw new SuperNotCalledException("Activity " + safeToComponentShortString(r.intent)
                    + " did not call through to super.onPause()");
        }
    } catch (SuperNotCalledException e) {
        throw e;
    } catch (Exception e) {
        if (!mInstrumentation.onException(r.activity, e)) {
            throw new RuntimeException("Unable to pause activity "
                    + safeToComponentShortString(r.intent) + ": " + e.toString(), e);
        }
    }
    r.setState(ON_PAUSE);
}
```

最终由 mInstrumentation 内部调用 Activity.performPause 。而 performPause 方法内部又调用了 onPause 。

``` java
public void callActivityOnPause(Activity activity) {
    activity.performPause();
}

// Activity.performPause
final void performPause() {
    mDoReportFullyDrawn = false;
    mFragments.dispatchPause();
    mCalled = false;
    onPause();
    writeEventLog(LOG_AM_ON_PAUSE_CALLED, "performPause");
    mResumed = false;
    if (!mCalled && getApplicationInfo().targetSdkVersion
            >= android.os.Build.VERSION_CODES.GINGERBREAD) {
        throw new SuperNotCalledException(
                "Activity " + mComponent.toShortString() +
                " did not call through to super.onPause()");
    }
}
```

onCreate()
==========
onCreate 的生命周期调用在前一篇中已经分析过了，所以在这里就不讲了。

如果有需要的话可以看前一篇博客。

onResume()
==========
在前一篇中讲到，`resumeTopActivityInnerLocked(ActivityRecord prev, ActivityOptions options)` 方法中有一段代码

```
transaction.setLifecycleStateRequest(
                        ResumeActivityItem.obtain(next.app.repProcState,
                                mService.isNextTransitionForward()));
mService.getLifecycleManager().scheduleTransaction(transaction);
```

可以看到 transaction 将最后的生命周期状态设置为了 resume 。

根据前一篇博客的分析，代码最后会执行 ResumeActivityItem.execute

``` java
@Override
public void execute(ClientTransactionHandler client, IBinder token,
        PendingTransactionActions pendingActions) {
    Trace.traceBegin(TRACE_TAG_ACTIVITY_MANAGER, "activityResume");
    client.handleResumeActivity(token, true /* finalStateRequest */, mIsForward,
            "RESUME_ACTIVITY");
    Trace.traceEnd(TRACE_TAG_ACTIVITY_MANAGER);
}
```

可以看到在 execute 中调用了 ActivityThread 的 handleResumeActivity 方法。

``` java
@Override
public void handleResumeActivity(IBinder token, boolean finalStateRequest, boolean isForward,
        String reason) {
    // If we are getting ready to gc after going to the background, well
    // we are back active so skip it.
    unscheduleGcIdler();
    mSomeActivitiesChanged = true;

    // TODO Push resumeArgs into the activity for consideration
    // 请关注这里
    final ActivityClientRecord r = performResumeActivity(token, finalStateRequest, reason);
    if (r == null) {
        // We didn't actually resume the activity, so skipping any follow-up actions.
        return;
    }

    final Activity a = r.activity;

    if (localLOGV) {
        Slog.v(TAG, "Resume " + r + " started activity: " + a.mStartedActivity
                + ", hideForNow: " + r.hideForNow + ", finished: " + a.mFinished);
    }

    final int forwardBit = isForward
            ? WindowManager.LayoutParams.SOFT_INPUT_IS_FORWARD_NAVIGATION : 0;

    // If the window hasn't yet been added to the window manager,
    // and this guy didn't finish itself or start another activity,
    // then go ahead and add the window.
    boolean willBeVisible = !a.mStartedActivity;
    if (!willBeVisible) {
        try {
            willBeVisible = ActivityManager.getService().willActivityBeVisible(
                    a.getActivityToken());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }
    if (r.window == null && !a.mFinished && willBeVisible) {
        r.window = r.activity.getWindow();
        View decor = r.window.getDecorView();
        decor.setVisibility(View.INVISIBLE);
        ViewManager wm = a.getWindowManager();
        WindowManager.LayoutParams l = r.window.getAttributes();
        a.mDecor = decor;
        l.type = WindowManager.LayoutParams.TYPE_BASE_APPLICATION;
        l.softInputMode |= forwardBit;
        if (r.mPreserveWindow) {
            a.mWindowAdded = true;
            r.mPreserveWindow = false;
            // Normally the ViewRoot sets up callbacks with the Activity
            // in addView->ViewRootImpl#setView. If we are instead reusing
            // the decor view we have to notify the view root that the
            // callbacks may have changed.
            ViewRootImpl impl = decor.getViewRootImpl();
            if (impl != null) {
                impl.notifyChildRebuilt();
            }
        }
        if (a.mVisibleFromClient) {
            if (!a.mWindowAdded) {
                a.mWindowAdded = true;
                wm.addView(decor, l);
            } else {
                // The activity will get a callback for this {@link LayoutParams} change
                // earlier. However, at that time the decor will not be set (this is set
                // in this method), so no action will be taken. This call ensures the
                // callback occurs with the decor set.
                a.onWindowAttributesChanged(l);
            }
        }

        // If the window has already been added, but during resume
        // we started another activity, then don't yet make the
        // window visible.
    } else if (!willBeVisible) {
        if (localLOGV) Slog.v(TAG, "Launch " + r + " mStartedActivity set");
        r.hideForNow = true;
    }

    // Get rid of anything left hanging around.
    cleanUpPendingRemoveWindows(r, false /* force */);

    // The window is now visible if it has been added, we are not
    // simply finishing, and we are not starting another activity.
    if (!r.activity.mFinished && willBeVisible && r.activity.mDecor != null && !r.hideForNow) {
        if (r.newConfig != null) {
            performConfigurationChangedForActivity(r, r.newConfig);
            if (DEBUG_CONFIGURATION) {
                Slog.v(TAG, "Resuming activity " + r.activityInfo.name + " with newConfig "
                        + r.activity.mCurrentConfig);
            }
            r.newConfig = null;
        }
        if (localLOGV) Slog.v(TAG, "Resuming " + r + " with isForward=" + isForward);
        WindowManager.LayoutParams l = r.window.getAttributes();
        if ((l.softInputMode
                & WindowManager.LayoutParams.SOFT_INPUT_IS_FORWARD_NAVIGATION)
                != forwardBit) {
            l.softInputMode = (l.softInputMode
                    & (~WindowManager.LayoutParams.SOFT_INPUT_IS_FORWARD_NAVIGATION))
                    | forwardBit;
            if (r.activity.mVisibleFromClient) {
                ViewManager wm = a.getWindowManager();
                View decor = r.window.getDecorView();
                wm.updateViewLayout(decor, l);
            }
        }

        r.activity.mVisibleFromServer = true;
        mNumVisibleActivities++;
        if (r.activity.mVisibleFromClient) {
            r.activity.makeVisible();
        }
    }

    r.nextIdle = mNewActivities;
    mNewActivities = r;
    if (localLOGV) Slog.v(TAG, "Scheduling idle handler for " + r);
    Looper.myQueue().addIdleHandler(new Idler());
}
```

在 handleResumeActivity 中调用了 performResumeActivity 来完成 Activity 的 resume 操作。

``` java
@VisibleForTesting
public ActivityClientRecord performResumeActivity(IBinder token, boolean finalStateRequest,
        String reason) {
    final ActivityClientRecord r = mActivities.get(token);
    if (localLOGV) {
        Slog.v(TAG, "Performing resume of " + r + " finished=" + r.activity.mFinished);
    }
    if (r == null || r.activity.mFinished) {
        return null;
    }
    if (r.getLifecycleState() == ON_RESUME) {
        if (!finalStateRequest) {
            final RuntimeException e = new IllegalStateException(
                    "Trying to resume activity which is already resumed");
            Slog.e(TAG, e.getMessage(), e);
            Slog.e(TAG, r.getStateString());
            // TODO(lifecycler): A double resume request is possible when an activity
            // receives two consequent transactions with relaunch requests and "resumed"
            // final state requests and the second relaunch is omitted. We still try to
            // handle two resume requests for the final state. For cases other than this
            // one, we don't expect it to happen.
        }
        return null;
    }
    if (finalStateRequest) {
        r.hideForNow = false;
        r.activity.mStartedActivity = false;
    }
    try {
        r.activity.onStateNotSaved();
        r.activity.mFragments.noteStateNotSaved();
        checkAndBlockForNetworkAccess();
        if (r.pendingIntents != null) {
            deliverNewIntents(r, r.pendingIntents);
            r.pendingIntents = null;
        }
        if (r.pendingResults != null) {
            deliverResults(r, r.pendingResults, reason);
            r.pendingResults = null;
        }
        // 调用 Activity 的 performResume 方法
        r.activity.performResume(r.startsNotResumed, reason);

        r.state = null;
        r.persistentState = null;
        r.setState(ON_RESUME);
    } catch (Exception e) {
        if (!mInstrumentation.onException(r.activity, e)) {
            throw new RuntimeException("Unable to resume activity "
                    + r.intent.getComponent().toShortString() + ": " + e.toString(), e);
        }
    }
    return r;
}
```

activity.performResume 的内部将 onResume 回调的操作交给了 mInstrumentation 来处理。

``` java
final void performResume(boolean followedByPause, String reason) {
    performRestart(true /* start */, reason);

    mFragments.execPendingActions();

    mLastNonConfigurationInstances = null;

    if (mAutoFillResetNeeded) {
        // When Activity is destroyed in paused state, and relaunch activity, there will be
        // extra onResume and onPause event,  ignore the first onResume and onPause.
        // see ActivityThread.handleRelaunchActivity()
        mAutoFillIgnoreFirstResumePause = followedByPause;
        if (mAutoFillIgnoreFirstResumePause && DEBUG_LIFECYCLE) {
            Slog.v(TAG, "autofill will ignore first pause when relaunching " + this);
        }
    }

    mCalled = false;
    // mResumed is set by the instrumentation
    mInstrumentation.callActivityOnResume(this);
    writeEventLog(LOG_AM_ON_RESUME_CALLED, reason);
    if (!mCalled) {
        throw new SuperNotCalledException(
            "Activity " + mComponent.toShortString() +
            " did not call through to super.onResume()");
    }

    // invisible activities must be finished before onResume() completes
    if (!mVisibleFromClient && !mFinished) {
        Log.w(TAG, "An activity without a UI must call finish() before onResume() completes");
        if (getApplicationInfo().targetSdkVersion
                > android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
            throw new IllegalStateException(
                    "Activity " + mComponent.toShortString() +
                    " did not call finish() prior to onResume() completing");
        }
    }

    // Now really resume, and install the current status bar and menu.
    mCalled = false;

    mFragments.dispatchResume();
    mFragments.execPendingActions();

    onPostResume();
    if (!mCalled) {
        throw new SuperNotCalledException(
            "Activity " + mComponent.toShortString() +
            " did not call through to super.onPostResume()");
    }
}
```

在 mInstrumentation.callActivityOnResume 内部调用了 Activity 的 onResume 方法。

``` java
public void callActivityOnResume(Activity activity) {
    activity.mResumed = true;
    activity.onResume();
    
    if (mActivityMonitors != null) {
        synchronized (mSync) {
            final int N = mActivityMonitors.size();
            for (int i=0; i<N; i++) {
                final ActivityMonitor am = mActivityMonitors.get(i);
                am.match(activity, activity, activity.getIntent());
            }
        }
    }
}
```

onStart()
=========
可能有些同学会觉得奇怪，怎么从 onCreate 直接跳到 onResume 了？不是应该还有一个 onStart 么？

那接下来，我们就来看看 onStart 是哪里被调用的。

话还要从 transaction 开始说起。

一开始 transaction 设置了 LaunchActivityItem ，然后又设置了生命周期状态 ResumeActivityItem 。

所以可以简单地看出，onCreate -> onResume ，中间并没有加 onStart 。那么 onStart 是哪里在调用呢？

我们来看下 TransactionExecutor.execute 方法。

``` java
public void execute(ClientTransaction transaction) {
    final IBinder token = transaction.getActivityToken();
    log("Start resolving transaction for client: " + mTransactionHandler + ", token: " + token);

    executeCallbacks(transaction);

    executeLifecycleState(transaction);
    mPendingActions.clear();
    log("End resolving transaction");
}
```

经过上一篇中分析，我们知道执行 `executeCallbacks(transaction);` 之后，Activity 就完成了 onCreate 的调用，所以此时 Activity 的状态应该是 ON_CREATE 。

然后来看看 executeLifecycleState 方法。

``` java
private void executeLifecycleState(ClientTransaction transaction) {
    final ActivityLifecycleItem lifecycleItem = transaction.getLifecycleStateRequest();
    if (lifecycleItem == null) {
        // No lifecycle request, return early.
        return;
    }
    log("Resolving lifecycle state: " + lifecycleItem);

    final IBinder token = transaction.getActivityToken();
    final ActivityClientRecord r = mTransactionHandler.getActivityClient(token);

    if (r == null) {
        // Ignore requests for non-existent client records for now.
        return;
    }

    // Cycle to the state right before the final requested state.
    // 这里的 lifecycleItem.getTargetState() 正是 ResumeActivityItem.ON_RESUME
    cycleToPath(r, lifecycleItem.getTargetState(), true /* excludeLastState */);

    // Execute the final transition with proper parameters.
    lifecycleItem.execute(mTransactionHandler, token, mPendingActions);
    lifecycleItem.postExecute(mTransactionHandler, token, mPendingActions);
}
```

重点来关注下 cycleToPath 方法。

``` java
private void cycleToPath(ActivityClientRecord r, int finish,
        boolean excludeLastState) {
    final int start = r.getLifecycleState();
    log("Cycle from: " + start + " to: " + finish + " excludeLastState:" + excludeLastState);
    // 这里需要我们重点来关注
    final IntArray path = mHelper.getLifecyclePath(start, finish, excludeLastState);
    // 去执行 path 中的生命周期
    performLifecycleSequence(r, path);
}

// 调用 ActivityThread 中对应的生命周期方法
private void performLifecycleSequence(ActivityClientRecord r, IntArray path) {
    final int size = path.size();
    for (int i = 0, state; i < size; i++) {
        state = path.get(i);
        log("Transitioning to state: " + state);
        switch (state) {
            case ON_CREATE:
                mTransactionHandler.handleLaunchActivity(r, mPendingActions,
                        null /* customIntent */);
                break;
            case ON_START:
                mTransactionHandler.handleStartActivity(r, mPendingActions);
                break;
            case ON_RESUME:
                mTransactionHandler.handleResumeActivity(r.token, false /* finalStateRequest */,
                        r.isForward, "LIFECYCLER_RESUME_ACTIVITY");
                break;
            case ON_PAUSE:
                mTransactionHandler.handlePauseActivity(r.token, false /* finished */,
                        false /* userLeaving */, 0 /* configChanges */, mPendingActions,
                        "LIFECYCLER_PAUSE_ACTIVITY");
                break;
            case ON_STOP:
                mTransactionHandler.handleStopActivity(r.token, false /* show */,
                        0 /* configChanges */, mPendingActions, false /* finalStateRequest */,
                        "LIFECYCLER_STOP_ACTIVITY");
                break;
            case ON_DESTROY:
                mTransactionHandler.handleDestroyActivity(r.token, false /* finishing */,
                        0 /* configChanges */, false /* getNonConfigInstance */,
                        "performLifecycleSequence. cycling to:" + path.get(size - 1));
                break;
            case ON_RESTART:
                mTransactionHandler.performRestartActivity(r.token, false /* start */);
                break;
            default:
                throw new IllegalArgumentException("Unexpected lifecycle state: " + state);
        }
    }
}
```

我们发现 mHelper.getLifecyclePath 返回的 path 直接传入到 performLifecycleSequence 方法中。

而 performLifecycleSequence 方法里面一堆 switch case 正是去调用生命周期的，可以看到有 ON_START 的身影。我们的可以猜想到，在 mHelper.getLifecyclePath 方法中应该会返回 ON_START 。这样在 performLifecycleSequence 中就会去调用 `mTransactionHandler.handleStartActivity(r, mPendingActions)` 了。

那么我们来看看 mHelper.getLifecyclePath 中的方法。

``` java
@VisibleForTesting
public IntArray getLifecyclePath(int start, int finish, boolean excludeLastState) {
    if (start == UNDEFINED || finish == UNDEFINED) {
        throw new IllegalArgumentException("Can't resolve lifecycle path for undefined state");
    }
    if (start == ON_RESTART || finish == ON_RESTART) {
        throw new IllegalArgumentException(
                "Can't start or finish in intermittent RESTART state");
    }
    if (finish == PRE_ON_CREATE && start != finish) {
        throw new IllegalArgumentException("Can only start in pre-onCreate state");
    }

    mLifecycleSequence.clear();
    if (finish >= start) {
        // just go there
        for (int i = start + 1; i <= finish; i++) {
            mLifecycleSequence.add(i);
        }
    } else { // finish < start, can't just cycle down
        if (start == ON_PAUSE && finish == ON_RESUME) {
            // Special case when we can just directly go to resumed state.
            mLifecycleSequence.add(ON_RESUME);
        } else if (start <= ON_STOP && finish >= ON_START) {
            // Restart and go to required state.

            // Go to stopped state first.
            for (int i = start + 1; i <= ON_STOP; i++) {
                mLifecycleSequence.add(i);
            }
            // Restart
            mLifecycleSequence.add(ON_RESTART);
            // Go to required state
            for (int i = ON_START; i <= finish; i++) {
                mLifecycleSequence.add(i);
            }
        } else {
            // Relaunch and go to required state

            // Go to destroyed state first.
            for (int i = start + 1; i <= ON_DESTROY; i++) {
                mLifecycleSequence.add(i);
            }
            // Go to required state
            for (int i = ON_CREATE; i <= finish; i++) {
                mLifecycleSequence.add(i);
            }
        }
    }

    // Remove last transition in case we want to perform it with some specific params.
    if (excludeLastState && mLifecycleSequence.size() != 0) {
        mLifecycleSequence.remove(mLifecycleSequence.size() - 1);
    }

    return mLifecycleSequence;
}
```

看完上面这一段代码，相信你已经大致的明白了吧。上面这段代码中主要做的就是把“中间路径”给计算出来。

比如起点是 ON_CREATE , 终点是 ON_RESUME 。所以“中间路径”就是 [ON_START, ON_RESUME] 。但是之前传入的 excludeLastState 参数是 true 。所以还要减掉最后一个终点，因为“中间路径”就是 [ON_START] 了。

这样一连贯起来，我们就明白了 onStart 是怎么调用了的吧！

那么接着看吧。有了 ON_START 后，会调用 ActivityThread.handleStartActivity

``` java

@Override
public void handleStartActivity(ActivityClientRecord r,
        PendingTransactionActions pendingActions) {
    final Activity activity = r.activity;
    if (r.activity == null) {
        // TODO(lifecycler): What do we do in this case?
        return;
    }
    if (!r.stopped) {
        throw new IllegalStateException("Can't start activity that is not stopped.");
    }
    if (r.activity.mFinished) {
        // TODO(lifecycler): How can this happen?
        return;
    }

    // Start
    // 调用 performStart
    activity.performStart("handleStartActivity");
    r.setState(ON_START);

    if (pendingActions == null) {
        // No more work to do.
        return;
    }

    // Restore instance state
    // 调用 OnRestoreInstanceState
    if (pendingActions.shouldRestoreInstanceState()) {
        if (r.isPersistable()) {
            if (r.state != null || r.persistentState != null) {
                mInstrumentation.callActivityOnRestoreInstanceState(activity, r.state,
                        r.persistentState);
            }
        } else if (r.state != null) {
            mInstrumentation.callActivityOnRestoreInstanceState(activity, r.state);
        }
    }

    // Call postOnCreate()
    if (pendingActions.shouldCallOnPostCreate()) {
        activity.mCalled = false;
        if (r.isPersistable()) {
            mInstrumentation.callActivityOnPostCreate(activity, r.state,
                    r.persistentState);
        } else {
            mInstrumentation.callActivityOnPostCreate(activity, r.state);
        }
        if (!activity.mCalled) {
            throw new SuperNotCalledException(
                    "Activity " + r.intent.getComponent().toShortString()
                            + " did not call through to super.onPostCreate()");
        }
    }
}
```

在 handleStartActivity 里面调用了 Activity.performStart

``` java
final void performStart(String reason) {
    mActivityTransitionState.setEnterActivityOptions(this, getActivityOptions());
    mFragments.noteStateNotSaved();
    mCalled = false;
    mFragments.execPendingActions();
    // 调用 Instrumentation 的 callActivityOnStart
    mInstrumentation.callActivityOnStart(this);
    writeEventLog(LOG_AM_ON_START_CALLED, reason);

    if (!mCalled) {
        throw new SuperNotCalledException(
            "Activity " + mComponent.toShortString() +
            " did not call through to super.onStart()");
    }
    mFragments.dispatchStart();
    mFragments.reportLoaderStart();

    boolean isAppDebuggable =
            (mApplication.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;

    // This property is set for all non-user builds except final release
    boolean isDlwarningEnabled = SystemProperties.getInt("ro.bionic.ld.warning", 0) == 1;

    if (isAppDebuggable || isDlwarningEnabled) {
        String dlwarning = getDlWarning();
        if (dlwarning != null) {
            String appName = getApplicationInfo().loadLabel(getPackageManager())
                    .toString();
            String warning = "Detected problems with app native libraries\n" +
                             "(please consult log for detail):\n" + dlwarning;
            if (isAppDebuggable) {
                  new AlertDialog.Builder(this).
                      setTitle(appName).
                      setMessage(warning).
                      setPositiveButton(android.R.string.ok, null).
                      setCancelable(false).
                      show();
            } else {
                Toast.makeText(this, appName + "\n" + warning, Toast.LENGTH_LONG).show();
            }
        }
    }

    // This property is set for all non-user builds except final release
    boolean isApiWarningEnabled = SystemProperties.getInt("ro.art.hiddenapi.warning", 0) == 1;

    if (isAppDebuggable || isApiWarningEnabled) {
        if (!mMainThread.mHiddenApiWarningShown && VMRuntime.getRuntime().hasUsedHiddenApi()) {
            // Only show the warning once per process.
            mMainThread.mHiddenApiWarningShown = true;

            String appName = getApplicationInfo().loadLabel(getPackageManager())
                    .toString();
            String warning = "Detected problems with API compatibility\n"
                             + "(visit g.co/dev/appcompat for more info)";
            if (isAppDebuggable) {
                new AlertDialog.Builder(this)
                    .setTitle(appName)
                    .setMessage(warning)
                    .setPositiveButton(android.R.string.ok, null)
                    .setCancelable(false)
                    .show();
            } else {
                Toast.makeText(this, appName + "\n" + warning, Toast.LENGTH_LONG).show();
            }
        }
    }

    mActivityTransitionState.enterReady(this);
}
```

不出所料，performStart 中又调用了 mInstrumentation.callActivityOnStart(this)

在 callActivityOnStart 中直接调用 activity.onStart

``` java
public void callActivityOnStart(Activity activity) {
    activity.onStart();
}
```

onStop()
========
最后，我们来看一下 onStop 。

在 ActivityThread 的 handleResumeActivity 方法中，末尾有一段代码

``` java
r.nextIdle = mNewActivities;
mNewActivities = r;
if (localLOGV) Slog.v(TAG, "Scheduling idle handler for " + r);
Looper.myQueue().addIdleHandler(new Idler());
```

重点来关注下 Idler 。

``` java
private class Idler implements MessageQueue.IdleHandler {
    @Override
    public final boolean queueIdle() {
        ActivityClientRecord a = mNewActivities;
        boolean stopProfiling = false;
        if (mBoundApplication != null && mProfiler.profileFd != null
                && mProfiler.autoStopProfiler) {
            stopProfiling = true;
        }
        if (a != null) {
            mNewActivities = null;
            IActivityManager am = ActivityManager.getService();
            ActivityClientRecord prev;
            do {
                if (localLOGV) Slog.v(
                    TAG, "Reporting idle of " + a +
                    " finished=" +
                    (a.activity != null && a.activity.mFinished));
                if (a.activity != null && !a.activity.mFinished) {
                    try {
                    	// 调用 AMS 来处理 activity 的 onStop
                        am.activityIdle(a.token, a.createdConfig, stopProfiling);
                        a.createdConfig = null;
                    } catch (RemoteException ex) {
                        throw ex.rethrowFromSystemServer();
                    }
                }
                prev = a;
                a = a.nextIdle;
                prev.nextIdle = null;
            } while (a != null);
        }
        if (stopProfiling) {
            mProfiler.stopProfiling();
        }
        ensureJitEnabled();
        return false;
    }
}
```

可以看到，其中有一句 `am.activityIdle(a.token, a.createdConfig, stopProfiling);` 。

而 am 就是 AMS 了，所以我们需要去 AMS 里面看看。

``` java
@Override
public final void activityIdle(IBinder token, Configuration config, boolean stopProfiling) {
    final long origId = Binder.clearCallingIdentity();
    synchronized (this) {
        ActivityStack stack = ActivityRecord.getStackLocked(token);
        if (stack != null) {
            ActivityRecord r =
                    mStackSupervisor.activityIdleInternalLocked(token, false /* fromTimeout */,
                            false /* processPausingActivities */, config);
            if (stopProfiling) {
                if ((mProfileProc == r.app) && mProfilerInfo != null) {
                    clearProfilerLocked();
                }
            }
        }
    }
    Binder.restoreCallingIdentity(origId);
}
```

用 mStackSupervisor 来处理 Activity 任务栈的操作。

``` java
@GuardedBy("mService")
final ActivityRecord activityIdleInternalLocked(final IBinder token, boolean fromTimeout,
        boolean processPausingActivities, Configuration config) {
    if (DEBUG_ALL) Slog.v(TAG, "Activity idle: " + token);

    ArrayList<ActivityRecord> finishes = null;
    ArrayList<UserState> startingUsers = null;
    int NS = 0;
    int NF = 0;
    boolean booting = false;
    boolean activityRemoved = false;

    ActivityRecord r = ActivityRecord.forTokenLocked(token);
    if (r != null) {
        if (DEBUG_IDLE) Slog.d(TAG_IDLE, "activityIdleInternalLocked: Callers="
                + Debug.getCallers(4));
        mHandler.removeMessages(IDLE_TIMEOUT_MSG, r);
        r.finishLaunchTickingLocked();
        if (fromTimeout) {
            reportActivityLaunchedLocked(fromTimeout, r, -1, -1);
        }

        // This is a hack to semi-deal with a race condition
        // in the client where it can be constructed with a
        // newer configuration from when we asked it to launch.
        // We'll update with whatever configuration it now says
        // it used to launch.
        if (config != null) {
            r.setLastReportedGlobalConfiguration(config);
        }

        // We are now idle.  If someone is waiting for a thumbnail from
        // us, we can now deliver.
        r.idle = true;

        //Slog.i(TAG, "IDLE: mBooted=" + mBooted + ", fromTimeout=" + fromTimeout);
        if (isFocusedStack(r.getStack()) || fromTimeout) {
            booting = checkFinishBootingLocked();
        }
    }

    if (allResumedActivitiesIdle()) {
        if (r != null) {
            mService.scheduleAppGcsLocked();
        }

        if (mLaunchingActivity.isHeld()) {
            mHandler.removeMessages(LAUNCH_TIMEOUT_MSG);
            if (VALIDATE_WAKE_LOCK_CALLER &&
                    Binder.getCallingUid() != Process.myUid()) {
                throw new IllegalStateException("Calling must be system uid");
            }
            mLaunchingActivity.release();
        }
        ensureActivitiesVisibleLocked(null, 0, !PRESERVE_WINDOWS);
    }

    // Atomically retrieve all of the other things to do.
    final ArrayList<ActivityRecord> stops = processStoppingActivitiesLocked(r,
            true /* remove */, processPausingActivities);
    NS = stops != null ? stops.size() : 0;
    if ((NF = mFinishingActivities.size()) > 0) {
        finishes = new ArrayList<>(mFinishingActivities);
        mFinishingActivities.clear();
    }

    if (mStartingUsers.size() > 0) {
        startingUsers = new ArrayList<>(mStartingUsers);
        mStartingUsers.clear();
    }

    // Stop any activities that are scheduled to do so but have been
    // waiting for the next one to start.
    for (int i = 0; i < NS; i++) {
        r = stops.get(i);
        final ActivityStack stack = r.getStack();
        if (stack != null) {
            if (r.finishing) {
                stack.finishCurrentActivityLocked(r, ActivityStack.FINISH_IMMEDIATELY, false,
                        "activityIdleInternalLocked");
            } else {
                stack.stopActivityLocked(r);
            }
        }
    }

    // Finish any activities that are scheduled to do so but have been
    // waiting for the next one to start.
    for (int i = 0; i < NF; i++) {
        r = finishes.get(i);
        final ActivityStack stack = r.getStack();
        if (stack != null) {
            activityRemoved |= stack.destroyActivityLocked(r, true, "finish-idle");
        }
    }

    if (!booting) {
        // Complete user switch
        if (startingUsers != null) {
            for (int i = 0; i < startingUsers.size(); i++) {
                mService.mUserController.finishUserSwitch(startingUsers.get(i));
            }
        }
    }

    mService.trimApplications();
    //dump();
    //mWindowManager.dump();

    if (activityRemoved) {
        resumeFocusedStackTopActivityLocked();
    }

    return r;
}
```

上面这段代码有点长，其实我们只要关注以下这段代码就好了

	// Stop any activities that are scheduled to do so but have been
	// waiting for the next one to start.
	for (int i = 0; i < NS; i++) {
	   r = stops.get(i);
	   final ActivityStack stack = r.getStack();
	   if (stack != null) {
	       if (r.finishing) {
	           stack.finishCurrentActivityLocked(r, ActivityStack.FINISH_IMMEDIATELY, false,
	                   "activityIdleInternalLocked");
	       } else {
	           stack.stopActivityLocked(r);
	       }
	   }
	}

发现如果 ActivityRecord 没有 finish 的话，就会调用 `stack.stopActivityLocked(r);`

那我们去 ActivityStack 中看看

``` java
final void stopActivityLocked(ActivityRecord r) {
    if (DEBUG_SWITCH) Slog.d(TAG_SWITCH, "Stopping: " + r);
    if ((r.intent.getFlags()&Intent.FLAG_ACTIVITY_NO_HISTORY) != 0
            || (r.info.flags&ActivityInfo.FLAG_NO_HISTORY) != 0) {
        if (!r.finishing) {
            if (!shouldSleepActivities()) {
                if (DEBUG_STATES) Slog.d(TAG_STATES, "no-history finish of " + r);
                if (requestFinishActivityLocked(r.appToken, Activity.RESULT_CANCELED, null,
                        "stop-no-history", false)) {
                    // If {@link requestFinishActivityLocked} returns {@code true},
                    // {@link adjustFocusedActivityStack} would have been already called.
                    r.resumeKeyDispatchingLocked();
                    return;
                }
            } else {
                if (DEBUG_STATES) Slog.d(TAG_STATES, "Not finishing noHistory " + r
                        + " on stop because we're just sleeping");
            }
        }
    }

    if (r.app != null && r.app.thread != null) {
        adjustFocusedActivityStack(r, "stopActivity");
        r.resumeKeyDispatchingLocked();
        try {
            r.stopped = false;
            if (DEBUG_STATES) Slog.v(TAG_STATES,
                    "Moving to STOPPING: " + r + " (stop requested)");
            r.setState(STOPPING, "stopActivityLocked");
            if (DEBUG_VISIBILITY) Slog.v(TAG_VISIBILITY,
                    "Stopping visible=" + r.visible + " for " + r);
            if (!r.visible) {
                r.setVisible(false);
            }
            EventLogTags.writeAmStopActivity(
                    r.userId, System.identityHashCode(r), r.shortComponentName);
            mService.getLifecycleManager().scheduleTransaction(r.app.thread, r.appToken,
                    StopActivityItem.obtain(r.visible, r.configChangeFlags));
            if (shouldSleepOrShutDownActivities()) {
                r.setSleeping(true);
            }
            Message msg = mHandler.obtainMessage(STOP_TIMEOUT_MSG, r);
            mHandler.sendMessageDelayed(msg, STOP_TIMEOUT);
        } catch (Exception e) {
            // Maybe just ignore exceptions here...  if the process
            // has crashed, our death notification will clean things
            // up.
            Slog.w(TAG, "Exception thrown during pause", e);
            // Just in case, assume it to be stopped.
            r.stopped = true;
            if (DEBUG_STATES) Slog.v(TAG_STATES, "Stop failed; moving to STOPPED: " + r);
            r.setState(STOPPED, "stopActivityLocked");
            if (r.deferRelaunchUntilPaused) {
                destroyActivityLocked(r, true, "stop-except");
            }
        }
    }
}
```

一眼就看到了 onStop 调用的入口啦：

	mService.getLifecycleManager().scheduleTransaction(r.app.thread, r.appToken,
        StopActivityItem.obtain(r.visible, r.configChangeFlags));

经过上面这么多的分析，相信已经不用说这句代码意味着什么了吧！

我们直接看 StopActivityItem 的 execute 方法

``` java
@Override
public void execute(ClientTransactionHandler client, IBinder token,
        PendingTransactionActions pendingActions) {
    Trace.traceBegin(TRACE_TAG_ACTIVITY_MANAGER, "activityStop");
    client.handleStopActivity(token, mShowWindow, mConfigChanges, pendingActions,
            true /* finalStateRequest */, "STOP_ACTIVITY_ITEM");
    Trace.traceEnd(TRACE_TAG_ACTIVITY_MANAGER);
}
```

和其他的一样，也是调用了 ActivityThread 的 handleStopActivity 方法。

``` java
@Override
public void handleStopActivity(IBinder token, boolean show, int configChanges,
        PendingTransactionActions pendingActions, boolean finalStateRequest, String reason) {
    final ActivityClientRecord r = mActivities.get(token);
    r.activity.mConfigChangeFlags |= configChanges;

    final StopInfo stopInfo = new StopInfo();
    performStopActivityInner(r, stopInfo, show, true /* saveState */, finalStateRequest,
            reason);

    if (localLOGV) Slog.v(
        TAG, "Finishing stop of " + r + ": show=" + show
        + " win=" + r.window);

    updateVisibility(r, show);

    // Make sure any pending writes are now committed.
    if (!r.isPreHoneycomb()) {
        QueuedWork.waitToFinish();
    }

    stopInfo.setActivity(r);
    stopInfo.setState(r.state);
    stopInfo.setPersistentState(r.persistentState);
    pendingActions.setStopInfo(stopInfo);
    mSomeActivitiesChanged = true;
}
```

关键代码 performStopActivityInner

``` java
private void performStopActivityInner(ActivityClientRecord r, StopInfo info, boolean keepShown,
        boolean saveState, boolean finalStateRequest, String reason) {
    if (localLOGV) Slog.v(TAG, "Performing stop of " + r);
    if (r != null) {
        if (!keepShown && r.stopped) {
            if (r.activity.mFinished) {
                // If we are finishing, we won't call onResume() in certain
                // cases.  So here we likewise don't want to call onStop()
                // if the activity isn't resumed.
                return;
            }
            if (!finalStateRequest) {
                final RuntimeException e = new RuntimeException(
                        "Performing stop of activity that is already stopped: "
                                + r.intent.getComponent().toShortString());
                Slog.e(TAG, e.getMessage(), e);
                Slog.e(TAG, r.getStateString());
            }
        }

        // One must first be paused before stopped...
        performPauseActivityIfNeeded(r, reason);

        if (info != null) {
            try {
                // First create a thumbnail for the activity...
                // For now, don't create the thumbnail here; we are
                // doing that by doing a screen snapshot.
                info.setDescription(r.activity.onCreateDescription());
            } catch (Exception e) {
                if (!mInstrumentation.onException(r.activity, e)) {
                    throw new RuntimeException(
                            "Unable to save state of activity "
                            + r.intent.getComponent().toShortString()
                            + ": " + e.toString(), e);
                }
            }
        }

        if (!keepShown) {
            callActivityOnStop(r, saveState, reason);
        }
    }
}
```

内部会调用 performPauseActivityIfNeeded

``` java
private void performPauseActivityIfNeeded(ActivityClientRecord r, String reason) {
    if (r.paused) {
        // You are already paused silly...
        return;
    }

    try {
        r.activity.mCalled = false;
        mInstrumentation.callActivityOnPause(r.activity);
        if (!r.activity.mCalled) {
            throw new SuperNotCalledException("Activity " + safeToComponentShortString(r.intent)
                    + " did not call through to super.onPause()");
        }
    } catch (SuperNotCalledException e) {
        throw e;
    } catch (Exception e) {
        if (!mInstrumentation.onException(r.activity, e)) {
            throw new RuntimeException("Unable to pause activity "
                    + safeToComponentShortString(r.intent) + ": " + e.toString(), e);
        }
    }
    r.setState(ON_PAUSE);
}
```

我们看到，还是利用 mInstrumentation 来调用 onStop

``` java
public void callActivityOnPause(Activity activity) {
    activity.performPause();
}

// Activity.performPause
final void performPause() {
    mDoReportFullyDrawn = false;
    mFragments.dispatchPause();
    mCalled = false;
    onPause();
    writeEventLog(LOG_AM_ON_PAUSE_CALLED, "performPause");
    mResumed = false;
    if (!mCalled && getApplicationInfo().targetSdkVersion
            >= android.os.Build.VERSION_CODES.GINGERBREAD) {
        throw new SuperNotCalledException(
                "Activity " + mComponent.toShortString() +
                " did not call through to super.onPause()");
    }
}
```

好了，到这里就把整个 Activity 启动的生命周期回调流程都走了一遍，回去好好理解下吧。



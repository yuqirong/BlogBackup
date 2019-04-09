title: startActivity启动流程
date: 2019-04-03 21:56:50
categories: Android Blog
tags: [Android,Activity]
---
注：源码分析基于 Android SDK API 28

对于 Activity 大家都已经很熟悉很亲切了吧，在这就不过多介绍了。

直接进入正题，走起！

一般我们启动 Activity 的入口都是 startActivity ，所以这也成为了我们分析整个流程的切入口。

Activity
====
startActivity(Intent intent)
----
``` java
@Override
public void startActivity(Intent intent) {
    this.startActivity(intent, null);
}

@Override
public void startActivity(Intent intent, @Nullable Bundle options) {
    if (options != null) {
        startActivityForResult(intent, -1, options);
    } else {
        // Note we want to go through this call for compatibility with
        // applications that may have overridden the method.
        startActivityForResult(intent, -1);
    }
}

public void startActivityForResult(@RequiresPermission Intent intent, int requestCode) {
    startActivityForResult(intent, requestCode, null);
}
```

其实最后都是调用 startActivityForResult 这个方法。

startActivityForResult(@RequiresPermission Intent intent, int requestCode, @Nullable Bundle options)
----
``` java
public void startActivityForResult(@RequiresPermission Intent intent, int requestCode,
        @Nullable Bundle options) {
    if (mParent == null) {
        options = transferSpringboardActivityOptions(options);
        Instrumentation.ActivityResult ar =
            mInstrumentation.execStartActivity(
                this, mMainThread.getApplicationThread(), mToken, this,
                intent, requestCode, options);
        if (ar != null) {
            mMainThread.sendActivityResult(
                mToken, mEmbeddedID, requestCode, ar.getResultCode(),
                ar.getResultData());
        }
        if (requestCode >= 0) {
            // If this start is requesting a result, we can avoid making
            // the activity visible until the result is received.  Setting
            // this code during onCreate(Bundle savedInstanceState) or onResume() will keep the
            // activity hidden during this time, to avoid flickering.
            // This can only be done when a result is requested because
            // that guarantees we will get information back when the
            // activity is finished, no matter what happens to it.
            mStartedActivity = true;
        }

        cancelInputsAndStartExitTransition(options);
        // TODO Consider clearing/flushing other event sources and events for child windows.
    } else {
        if (options != null) {
            mParent.startActivityFromChild(this, intent, requestCode, options);
        } else {
            // Note we want to go through this method for compatibility with
            // existing applications that may have overridden it.
            mParent.startActivityFromChild(this, intent, requestCode);
        }
    }
}
```

在 mParent 为空的情况下，直接调用 mInstrumentation.execStartActivity 来启动 Activity 。 

这里的 mParent 其实是指 ActivityGroup ，用来嵌套多个 Activity ，是早已废弃的 API 了。

所以一般情况下，mParent 都是为空的。那我们跟进到 Instrumentation 中的 execStartActivity 方法去看。

Instrumentation
====
execStartActivity
----
``` java
public ActivityResult execStartActivity(
        Context who, IBinder contextThread, IBinder token, Activity target,
        Intent intent, int requestCode, Bundle options) {
    IApplicationThread whoThread = (IApplicationThread) contextThread;
    Uri referrer = target != null ? target.onProvideReferrer() : null;
    if (referrer != null) {
        intent.putExtra(Intent.EXTRA_REFERRER, referrer);
    }
    if (mActivityMonitors != null) {
        synchronized (mSync) {
            final int N = mActivityMonitors.size();
            for (int i=0; i<N; i++) {
                final ActivityMonitor am = mActivityMonitors.get(i);
                ActivityResult result = null;
                if (am.ignoreMatchingSpecificIntents()) {
                    result = am.onStartActivity(intent);
                }
                if (result != null) {
                    am.mHits++;
                    return result;
                } else if (am.match(who, null, intent)) {
                    am.mHits++;
                    if (am.isBlocking()) {
                        return requestCode >= 0 ? am.getResult() : null;
                    }
                    break;
                }
            }
        }
    }
    try {
        intent.migrateExtraStreamToClipData();
        intent.prepareToLeaveProcess(who);
        // 调用 ActivityManagerService 启动 Activity
        int result = ActivityManager.getService()
            .startActivity(whoThread, who.getBasePackageName(), intent,
                    intent.resolveTypeIfNeeded(who.getContentResolver()),
                    token, target != null ? target.mEmbeddedID : null,
                    requestCode, 0, null, options); 
        // 在 checkStartActivityResult 中会检查 Activity 启动的结果
        // 比如没有在 AndroidManifest.xml 中注册的异常就是在这里抛出来的
        checkStartActivityResult(result, intent);
    } catch (RemoteException e) {
        throw new RuntimeException("Failure from system", e);
    }
    return null;
}

/** @hide */
public static void checkStartActivityResult(int res, Object intent) {
    if (!ActivityManager.isStartResultFatalError(res)) {
        return;
    }

    switch (res) {
        case ActivityManager.START_INTENT_NOT_RESOLVED:
        case ActivityManager.START_CLASS_NOT_FOUND:
            if (intent instanceof Intent && ((Intent)intent).getComponent() != null)
                throw new ActivityNotFoundException(
                        "Unable to find explicit activity class "
                        + ((Intent)intent).getComponent().toShortString()
                        + "; have you declared this activity in your AndroidManifest.xml?");
            throw new ActivityNotFoundException(
                    "No Activity found to handle " + intent);
        case ActivityManager.START_PERMISSION_DENIED:
            throw new SecurityException("Not allowed to start activity "
                    + intent);
        case ActivityManager.START_FORWARD_AND_REQUEST_CONFLICT:
            throw new AndroidRuntimeException(
                    "FORWARD_RESULT_FLAG used while also requesting a result");
        case ActivityManager.START_NOT_ACTIVITY:
            throw new IllegalArgumentException(
                    "PendingIntent is not an activity");
        case ActivityManager.START_NOT_VOICE_COMPATIBLE:
            throw new SecurityException(
                    "Starting under voice control not allowed for: " + intent);
        case ActivityManager.START_VOICE_NOT_ACTIVE_SESSION:
            throw new IllegalStateException(
                    "Session calling startVoiceActivity does not match active session");
        case ActivityManager.START_VOICE_HIDDEN_SESSION:
            throw new IllegalStateException(
                    "Cannot start voice activity on a hidden session");
        case ActivityManager.START_ASSISTANT_NOT_ACTIVE_SESSION:
            throw new IllegalStateException(
                    "Session calling startAssistantActivity does not match active session");
        case ActivityManager.START_ASSISTANT_HIDDEN_SESSION:
            throw new IllegalStateException(
                    "Cannot start assistant activity on a hidden session");
        case ActivityManager.START_CANCELED:
            throw new AndroidRuntimeException("Activity could not be started for "
                    + intent);
        default:
            throw new AndroidRuntimeException("Unknown error code "
                    + res + " when starting " + intent);
    }
}
```

在上面的代码中，调用了 ActivityManager.getService() 。其实实质上就是获取了 ActivityManagerService 。之后就会在 AMS 中去执行 Activity 的启动流程。

另外，返回的结果 result 会在 checkStartActivityResult 中去检查。比如 Activity 没有在 AndroidManifest.xml 中注册，就会抛出异常。

那我们来看看 AMS 中的实现。

ActivityManager
====
getService()
----
``` java
public static IActivityManager getService() {
    return IActivityManagerSingleton.get();
}

private static final Singleton<IActivityManager> IActivityManagerSingleton =
        new Singleton<IActivityManager>() {
            @Override
            protected IActivityManager create() {
                final IBinder b = ServiceManager.getService(Context.ACTIVITY_SERVICE);
                final IActivityManager am = IActivityManager.Stub.asInterface(b);
                return am;
            }
        };
```

可以看到 ActivityManager.getService() 获取的就是 AMS 对象。那么接着来看 AMS 的 startActivity 方法。

ActivityManagerService
====
startActivity(IApplicationThread caller, String callingPackage, Intent intent, String resolvedType, IBinder resultTo, String resultWho, int requestCode, int startFlags, ProfilerInfo profilerInfo, Bundle bOptions)
----
``` java
@Override
public final int startActivity(IApplicationThread caller, String callingPackage,
        Intent intent, String resolvedType, IBinder resultTo, String resultWho, int requestCode,
        int startFlags, ProfilerInfo profilerInfo, Bundle bOptions) {
    return startActivityAsUser(caller, callingPackage, intent, resolvedType, resultTo,
            resultWho, requestCode, startFlags, profilerInfo, bOptions,
            UserHandle.getCallingUserId());
}
```

直接调用的是 startActivityAsUser 方法。

startActivityAsUser(IApplicationThread caller, String callingPackage, Intent intent, String resolvedType, IBinder resultTo, String resultWho, int requestCode, int startFlags, ProfilerInfo profilerInfo, Bundle bOptions, int userId, boolean validateIncomingUser)
-----
``` java
public final int startActivityAsUser(IApplicationThread caller, String callingPackage,
        Intent intent, String resolvedType, IBinder resultTo, String resultWho, int requestCode,
        int startFlags, ProfilerInfo profilerInfo, Bundle bOptions, int userId,
        boolean validateIncomingUser) {
    enforceNotIsolatedCaller("startActivity");

    userId = mActivityStartController.checkTargetUser(userId, validateIncomingUser,
            Binder.getCallingPid(), Binder.getCallingUid(), "startActivityAsUser");

    // TODO: Switch to user app stacks here.
    return mActivityStartController.obtainStarter(intent, "startActivityAsUser")
            .setCaller(caller)
            .setCallingPackage(callingPackage)
            .setResolvedType(resolvedType)
            .setResultTo(resultTo)
            .setResultWho(resultWho)
            .setRequestCode(requestCode)
            .setStartFlags(startFlags)
            .setProfilerInfo(profilerInfo)
            .setActivityOptions(bOptions)
            .setMayWait(userId)
            .execute();

}
```

这里的 mActivityStartController.obtainStarter 就是创建了一个 ActivityStarter 对象。

ActivityStarter 是什么东西呢？

	This class collects all the logic for determining how an intent and flags should be turned into an activity and associated task and stack.

官方的解释是把 activity 启动过程中处理 intent 和 flags 的逻辑以及任务栈等操作都被封装在 ActivityStarter 中了。

而 startActivityAsUser 干的事就是把参数传进去，用 builder 模式构造出一个 ActivityStarter 对象。然后调用他的 execute 方法去执行。

所以我们直接来看 execute 方法。

ActivityStarter
====
execute()
----
``` java
int execute() {
    try {
        // TODO(b/64750076): Look into passing request directly to these methods to allow
        // for transactional diffs and preprocessing.
        if (mRequest.mayWait) {
            return startActivityMayWait(mRequest.caller, mRequest.callingUid,
                    mRequest.callingPackage, mRequest.intent, mRequest.resolvedType,
                    mRequest.voiceSession, mRequest.voiceInteractor, mRequest.resultTo,
                    mRequest.resultWho, mRequest.requestCode, mRequest.startFlags,
                    mRequest.profilerInfo, mRequest.waitResult, mRequest.globalConfig,
                    mRequest.activityOptions, mRequest.ignoreTargetSecurity, mRequest.userId,
                    mRequest.inTask, mRequest.reason,
                    mRequest.allowPendingRemoteAnimationRegistryLookup);
        } else {
            return startActivity(mRequest.caller, mRequest.intent, mRequest.ephemeralIntent,
                    mRequest.resolvedType, mRequest.activityInfo, mRequest.resolveInfo,
                    mRequest.voiceSession, mRequest.voiceInteractor, mRequest.resultTo,
                    mRequest.resultWho, mRequest.requestCode, mRequest.callingPid,
                    mRequest.callingUid, mRequest.callingPackage, mRequest.realCallingPid,
                    mRequest.realCallingUid, mRequest.startFlags, mRequest.activityOptions,
                    mRequest.ignoreTargetSecurity, mRequest.componentSpecified,
                    mRequest.outActivity, mRequest.inTask, mRequest.reason,
                    mRequest.allowPendingRemoteAnimationRegistryLookup);
        }
    } finally {
        onExecutionComplete();
    }
}
```

在上一步构造 ActivityStarter 的过程中，有一句 setMayWait(userId) 代码。这句代码内部会将 mayWait 设置为 true 。所以我们要深入到 startActivityMayWait 方法中去。

startActivityMayWait(IApplicationThread caller, int callingUid, String callingPackage, Intent intent, String resolvedType, IVoiceInteractionSession voiceSession, IVoiceInteractor voiceInteractor, IBinder resultTo, String resultWho, int requestCode, int startFlags, ProfilerInfo profilerInfo, WaitResult outResult, Configuration globalConfig, SafeActivityOptions options, boolean ignoreTargetSecurity, int userId, TaskRecord inTask, String reason, boolean allowPendingRemoteAnimationRegistryLookup)
----
``` java
private int startActivityMayWait(IApplicationThread caller, int callingUid,
        String callingPackage, Intent intent, String resolvedType,
        IVoiceInteractionSession voiceSession, IVoiceInteractor voiceInteractor,
        IBinder resultTo, String resultWho, int requestCode, int startFlags,
        ProfilerInfo profilerInfo, WaitResult outResult,
        Configuration globalConfig, SafeActivityOptions options, boolean ignoreTargetSecurity,
        int userId, TaskRecord inTask, String reason,
        boolean allowPendingRemoteAnimationRegistryLookup) {
    // Refuse possible leaked file descriptors
    if (intent != null && intent.hasFileDescriptors()) {
        throw new IllegalArgumentException("File descriptors passed in Intent");
    }
    mSupervisor.getActivityMetricsLogger().notifyActivityLaunching();
    boolean componentSpecified = intent.getComponent() != null;

    final int realCallingPid = Binder.getCallingPid();
    final int realCallingUid = Binder.getCallingUid();

    int callingPid;
    if (callingUid >= 0) {
        callingPid = -1;
    } else if (caller == null) {
        callingPid = realCallingPid;
        callingUid = realCallingUid;
    } else {
        callingPid = callingUid = -1;
    }

    // Save a copy in case ephemeral needs it
    final Intent ephemeralIntent = new Intent(intent);
    // Don't modify the client's object!
    intent = new Intent(intent);
    if (componentSpecified
            && !(Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() == null)
            && !Intent.ACTION_INSTALL_INSTANT_APP_PACKAGE.equals(intent.getAction())
            && !Intent.ACTION_RESOLVE_INSTANT_APP_PACKAGE.equals(intent.getAction())
            && mService.getPackageManagerInternalLocked()
                    .isInstantAppInstallerComponent(intent.getComponent())) {
        // intercept intents targeted directly to the ephemeral installer the
        // ephemeral installer should never be started with a raw Intent; instead
        // adjust the intent so it looks like a "normal" instant app launch
        intent.setComponent(null /*component*/);
        componentSpecified = false;
    }

    ResolveInfo rInfo = mSupervisor.resolveIntent(intent, resolvedType, userId,
            0 /* matchFlags */,
                    computeResolveFilterUid(
                            callingUid, realCallingUid, mRequest.filterCallingUid));
    if (rInfo == null) {
        UserInfo userInfo = mSupervisor.getUserInfo(userId);
        if (userInfo != null && userInfo.isManagedProfile()) {
            // Special case for managed profiles, if attempting to launch non-cryto aware
            // app in a locked managed profile from an unlocked parent allow it to resolve
            // as user will be sent via confirm credentials to unlock the profile.
            UserManager userManager = UserManager.get(mService.mContext);
            boolean profileLockedAndParentUnlockingOrUnlocked = false;
            long token = Binder.clearCallingIdentity();
            try {
                UserInfo parent = userManager.getProfileParent(userId);
                profileLockedAndParentUnlockingOrUnlocked = (parent != null)
                        && userManager.isUserUnlockingOrUnlocked(parent.id)
                        && !userManager.isUserUnlockingOrUnlocked(userId);
            } finally {
                Binder.restoreCallingIdentity(token);
            }
            if (profileLockedAndParentUnlockingOrUnlocked) {
                rInfo = mSupervisor.resolveIntent(intent, resolvedType, userId,
                        PackageManager.MATCH_DIRECT_BOOT_AWARE
                                | PackageManager.MATCH_DIRECT_BOOT_UNAWARE,
                        computeResolveFilterUid(
                                callingUid, realCallingUid, mRequest.filterCallingUid));
            }
        }
    }
    // Collect information about the target of the Intent.
    ActivityInfo aInfo = mSupervisor.resolveActivity(intent, rInfo, startFlags, profilerInfo);

    synchronized (mService) {
        final ActivityStack stack = mSupervisor.mFocusedStack;
        stack.mConfigWillChange = globalConfig != null
                && mService.getGlobalConfiguration().diff(globalConfig) != 0;
        if (DEBUG_CONFIGURATION) Slog.v(TAG_CONFIGURATION,
                "Starting activity when config will change = " + stack.mConfigWillChange);

        final long origId = Binder.clearCallingIdentity();

        if (aInfo != null &&
                (aInfo.applicationInfo.privateFlags
                        & ApplicationInfo.PRIVATE_FLAG_CANT_SAVE_STATE) != 0 &&
                mService.mHasHeavyWeightFeature) {
            // This may be a heavy-weight process!  Check to see if we already
            // have another, different heavy-weight process running.
            if (aInfo.processName.equals(aInfo.applicationInfo.packageName)) {
                final ProcessRecord heavy = mService.mHeavyWeightProcess;
                if (heavy != null && (heavy.info.uid != aInfo.applicationInfo.uid
                        || !heavy.processName.equals(aInfo.processName))) {
                    int appCallingUid = callingUid;
                    if (caller != null) {
                        ProcessRecord callerApp = mService.getRecordForAppLocked(caller);
                        if (callerApp != null) {
                            appCallingUid = callerApp.info.uid;
                        } else {
                            Slog.w(TAG, "Unable to find app for caller " + caller
                                    + " (pid=" + callingPid + ") when starting: "
                                    + intent.toString());
                            SafeActivityOptions.abort(options);
                            return ActivityManager.START_PERMISSION_DENIED;
                        }
                    }

                    IIntentSender target = mService.getIntentSenderLocked(
                            ActivityManager.INTENT_SENDER_ACTIVITY, "android",
                            appCallingUid, userId, null, null, 0, new Intent[] { intent },
                            new String[] { resolvedType }, PendingIntent.FLAG_CANCEL_CURRENT
                                    | PendingIntent.FLAG_ONE_SHOT, null);

                    Intent newIntent = new Intent();
                    if (requestCode >= 0) {
                        // Caller is requesting a result.
                        newIntent.putExtra(HeavyWeightSwitcherActivity.KEY_HAS_RESULT, true);
                    }
                    newIntent.putExtra(HeavyWeightSwitcherActivity.KEY_INTENT,
                            new IntentSender(target));
                    if (heavy.activities.size() > 0) {
                        ActivityRecord hist = heavy.activities.get(0);
                        newIntent.putExtra(HeavyWeightSwitcherActivity.KEY_CUR_APP,
                                hist.packageName);
                        newIntent.putExtra(HeavyWeightSwitcherActivity.KEY_CUR_TASK,
                                hist.getTask().taskId);
                    }
                    newIntent.putExtra(HeavyWeightSwitcherActivity.KEY_NEW_APP,
                            aInfo.packageName);
                    newIntent.setFlags(intent.getFlags());
                    newIntent.setClassName("android",
                            HeavyWeightSwitcherActivity.class.getName());
                    intent = newIntent;
                    resolvedType = null;
                    caller = null;
                    callingUid = Binder.getCallingUid();
                    callingPid = Binder.getCallingPid();
                    componentSpecified = true;
                    rInfo = mSupervisor.resolveIntent(intent, null /*resolvedType*/, userId,
                            0 /* matchFlags */, computeResolveFilterUid(
                                    callingUid, realCallingUid, mRequest.filterCallingUid));
                    aInfo = rInfo != null ? rInfo.activityInfo : null;
                    if (aInfo != null) {
                        aInfo = mService.getActivityInfoForUser(aInfo, userId);
                    }
                }
            }
        }

        final ActivityRecord[] outRecord = new ActivityRecord[1];
        int res = startActivity(caller, intent, ephemeralIntent, resolvedType, aInfo, rInfo,
                voiceSession, voiceInteractor, resultTo, resultWho, requestCode, callingPid,
                callingUid, callingPackage, realCallingPid, realCallingUid, startFlags, options,
                ignoreTargetSecurity, componentSpecified, outRecord, inTask, reason,
                allowPendingRemoteAnimationRegistryLookup);

        Binder.restoreCallingIdentity(origId);

        if (stack.mConfigWillChange) {
            // If the caller also wants to switch to a new configuration,
            // do so now.  This allows a clean switch, as we are waiting
            // for the current activity to pause (so we will not destroy
            // it), and have not yet started the next activity.
            mService.enforceCallingPermission(android.Manifest.permission.CHANGE_CONFIGURATION,
                    "updateConfiguration()");
            stack.mConfigWillChange = false;
            if (DEBUG_CONFIGURATION) Slog.v(TAG_CONFIGURATION,
                    "Updating to new configuration after starting activity.");
            mService.updateConfigurationLocked(globalConfig, null, false);
        }

        if (outResult != null) {
            outResult.result = res;

            final ActivityRecord r = outRecord[0];

            switch(res) {
                case START_SUCCESS: {
                    mSupervisor.mWaitingActivityLaunched.add(outResult);
                    do {
                        try {
                            mService.wait();
                        } catch (InterruptedException e) {
                        }
                    } while (outResult.result != START_TASK_TO_FRONT
                            && !outResult.timeout && outResult.who == null);
                    if (outResult.result == START_TASK_TO_FRONT) {
                        res = START_TASK_TO_FRONT;
                    }
                    break;
                }
                case START_DELIVERED_TO_TOP: {
                    outResult.timeout = false;
                    outResult.who = r.realActivity;
                    outResult.totalTime = 0;
                    outResult.thisTime = 0;
                    break;
                }
                case START_TASK_TO_FRONT: {
                    // ActivityRecord may represent a different activity, but it should not be
                    // in the resumed state.
                    if (r.nowVisible && r.isState(RESUMED)) {
                        outResult.timeout = false;
                        outResult.who = r.realActivity;
                        outResult.totalTime = 0;
                        outResult.thisTime = 0;
                    } else {
                        outResult.thisTime = SystemClock.uptimeMillis();
                        mSupervisor.waitActivityVisible(r.realActivity, outResult);
                        // Note: the timeout variable is not currently not ever set.
                        do {
                            try {
                                mService.wait();
                            } catch (InterruptedException e) {
                            }
                        } while (!outResult.timeout && outResult.who == null);
                    }
                    break;
                }
            }
        }

        mSupervisor.getActivityMetricsLogger().notifyActivityLaunched(res, outRecord[0]);
        return res;
    }
}
```

在这个方法中，获取 Activity 的 intent 信息，解析得到 ResolveInfo 和 ActivityInfo ，另外获取 callingPid 和 callingUid 。

这个方法的代码很多，看得头疼。但是我们就关心其中的关键代码：

	final ActivityRecord[] outRecord = new ActivityRecord[1];
	int res = startActivity(caller, intent, ephemeralIntent, resolvedType, aInfo, rInfo,
                voiceSession, voiceInteractor, resultTo, resultWho, requestCode, callingPid,
                callingUid, callingPackage, realCallingPid, realCallingUid, startFlags, options,
                ignoreTargetSecurity, componentSpecified, outRecord, inTask, reason,
                allowPendingRemoteAnimationRegistryLookup);

startActivityMayWait 内部调用 startActivity 来启动 Activity 。所以我们还是要追踪到 startActivity 方法中。

startActivity(IApplicationThread caller, Intent intent, Intent ephemeralIntent, String resolvedType, ActivityInfo aInfo, ResolveInfo rInfo, IVoiceInteractionSession voiceSession, IVoiceInteractor voiceInteractor, IBinder resultTo, String resultWho, int requestCode, int callingPid, int callingUid, String callingPackage, int realCallingPid, int realCallingUid, int startFlags,  SafeActivityOptions options, boolean ignoreTargetSecurity, boolean componentSpecified, ActivityRecord[] outActivity, TaskRecord inTask, String reason, boolean allowPendingRemoteAnimationRegistryLookup)
----
``` java
private int startActivity(IApplicationThread caller, Intent intent, Intent ephemeralIntent,
        String resolvedType, ActivityInfo aInfo, ResolveInfo rInfo,
        IVoiceInteractionSession voiceSession, IVoiceInteractor voiceInteractor,
        IBinder resultTo, String resultWho, int requestCode, int callingPid, int callingUid,
        String callingPackage, int realCallingPid, int realCallingUid, int startFlags,
        SafeActivityOptions options, boolean ignoreTargetSecurity, boolean componentSpecified,
        ActivityRecord[] outActivity, TaskRecord inTask, String reason,
        boolean allowPendingRemoteAnimationRegistryLookup) {

    if (TextUtils.isEmpty(reason)) {
        throw new IllegalArgumentException("Need to specify a reason.");
    }
    mLastStartReason = reason;
    mLastStartActivityTimeMs = System.currentTimeMillis();
    mLastStartActivityRecord[0] = null;

    mLastStartActivityResult = startActivity(caller, intent, ephemeralIntent, resolvedType,
            aInfo, rInfo, voiceSession, voiceInteractor, resultTo, resultWho, requestCode,
            callingPid, callingUid, callingPackage, realCallingPid, realCallingUid, startFlags,
            options, ignoreTargetSecurity, componentSpecified, mLastStartActivityRecord,
            inTask, allowPendingRemoteAnimationRegistryLookup);

    if (outActivity != null) {
        // mLastStartActivityRecord[0] is set in the call to startActivity above.
        outActivity[0] = mLastStartActivityRecord[0];
    }

    return getExternalResult(mLastStartActivityResult);
}
```

内部又调用了另一个 startActivity 的重载方法。

startActivity(IApplicationThread caller, Intent intent, Intent ephemeralIntent, String resolvedType, ActivityInfo aInfo, ResolveInfo rInfo, IVoiceInteractionSession voiceSession, IVoiceInteractor voiceInteractor, IBinder resultTo, String resultWho, int requestCode, int callingPid, int callingUid, String callingPackage, int realCallingPid, int realCallingUid, int startFlags, SafeActivityOptions options, boolean ignoreTargetSecurity, boolean componentSpecified, ActivityRecord[] outActivity, TaskRecord inTask, boolean allowPendingRemoteAnimationRegistryLookup)
----
``` java
private int startActivity(IApplicationThread caller, Intent intent, Intent ephemeralIntent,
        String resolvedType, ActivityInfo aInfo, ResolveInfo rInfo,
        IVoiceInteractionSession voiceSession, IVoiceInteractor voiceInteractor,
        IBinder resultTo, String resultWho, int requestCode, int callingPid, int callingUid,
        String callingPackage, int realCallingPid, int realCallingUid, int startFlags,
        SafeActivityOptions options,
        boolean ignoreTargetSecurity, boolean componentSpecified, ActivityRecord[] outActivity,
        TaskRecord inTask, boolean allowPendingRemoteAnimationRegistryLookup) {
    int err = ActivityManager.START_SUCCESS;
    // Pull the optional Ephemeral Installer-only bundle out of the options early.
    final Bundle verificationBundle
            = options != null ? options.popAppVerificationBundle() : null;

    ProcessRecord callerApp = null;
    if (caller != null) {
        callerApp = mService.getRecordForAppLocked(caller);
        if (callerApp != null) {
            callingPid = callerApp.pid;
            callingUid = callerApp.info.uid;
        } else {
            Slog.w(TAG, "Unable to find app for caller " + caller
                    + " (pid=" + callingPid + ") when starting: "
                    + intent.toString());
            err = ActivityManager.START_PERMISSION_DENIED;
        }
    }

    final int userId = aInfo != null && aInfo.applicationInfo != null
            ? UserHandle.getUserId(aInfo.applicationInfo.uid) : 0;

    if (err == ActivityManager.START_SUCCESS) {
        Slog.i(TAG, "START u" + userId + " {" + intent.toShortString(true, true, true, false)
                + "} from uid " + callingUid);
    }

    ActivityRecord sourceRecord = null;
    ActivityRecord resultRecord = null;
    if (resultTo != null) {
        sourceRecord = mSupervisor.isInAnyStackLocked(resultTo);
        if (DEBUG_RESULTS) Slog.v(TAG_RESULTS,
                "Will send result to " + resultTo + " " + sourceRecord);
        if (sourceRecord != null) {
            if (requestCode >= 0 && !sourceRecord.finishing) {
                resultRecord = sourceRecord;
            }
        }
    }

    final int launchFlags = intent.getFlags();

    if ((launchFlags & Intent.FLAG_ACTIVITY_FORWARD_RESULT) != 0 && sourceRecord != null) {
        // Transfer the result target from the source activity to the new
        // one being started, including any failures.
        if (requestCode >= 0) {
            SafeActivityOptions.abort(options);
            return ActivityManager.START_FORWARD_AND_REQUEST_CONFLICT;
        }
        resultRecord = sourceRecord.resultTo;
        if (resultRecord != null && !resultRecord.isInStackLocked()) {
            resultRecord = null;
        }
        resultWho = sourceRecord.resultWho;
        requestCode = sourceRecord.requestCode;
        sourceRecord.resultTo = null;
        if (resultRecord != null) {
            resultRecord.removeResultsLocked(sourceRecord, resultWho, requestCode);
        }
        if (sourceRecord.launchedFromUid == callingUid) {
            // The new activity is being launched from the same uid as the previous
            // activity in the flow, and asking to forward its result back to the
            // previous.  In this case the activity is serving as a trampoline between
            // the two, so we also want to update its launchedFromPackage to be the
            // same as the previous activity.  Note that this is safe, since we know
            // these two packages come from the same uid; the caller could just as
            // well have supplied that same package name itself.  This specifially
            // deals with the case of an intent picker/chooser being launched in the app
            // flow to redirect to an activity picked by the user, where we want the final
            // activity to consider it to have been launched by the previous app activity.
            callingPackage = sourceRecord.launchedFromPackage;
        }
    }

    if (err == ActivityManager.START_SUCCESS && intent.getComponent() == null) {
        // We couldn't find a class that can handle the given Intent.
        // That's the end of that!
        err = ActivityManager.START_INTENT_NOT_RESOLVED;
    }

    if (err == ActivityManager.START_SUCCESS && aInfo == null) {
        // We couldn't find the specific class specified in the Intent.
        // Also the end of the line.
        err = ActivityManager.START_CLASS_NOT_FOUND;
    }

    if (err == ActivityManager.START_SUCCESS && sourceRecord != null
            && sourceRecord.getTask().voiceSession != null) {
        // If this activity is being launched as part of a voice session, we need
        // to ensure that it is safe to do so.  If the upcoming activity will also
        // be part of the voice session, we can only launch it if it has explicitly
        // said it supports the VOICE category, or it is a part of the calling app.
        if ((launchFlags & FLAG_ACTIVITY_NEW_TASK) == 0
                && sourceRecord.info.applicationInfo.uid != aInfo.applicationInfo.uid) {
            try {
                intent.addCategory(Intent.CATEGORY_VOICE);
                if (!mService.getPackageManager().activitySupportsIntent(
                        intent.getComponent(), intent, resolvedType)) {
                    Slog.w(TAG,
                            "Activity being started in current voice task does not support voice: "
                                    + intent);
                    err = ActivityManager.START_NOT_VOICE_COMPATIBLE;
                }
            } catch (RemoteException e) {
                Slog.w(TAG, "Failure checking voice capabilities", e);
                err = ActivityManager.START_NOT_VOICE_COMPATIBLE;
            }
        }
    }

    if (err == ActivityManager.START_SUCCESS && voiceSession != null) {
        // If the caller is starting a new voice session, just make sure the target
        // is actually allowing it to run this way.
        try {
            if (!mService.getPackageManager().activitySupportsIntent(intent.getComponent(),
                    intent, resolvedType)) {
                Slog.w(TAG,
                        "Activity being started in new voice task does not support: "
                                + intent);
                err = ActivityManager.START_NOT_VOICE_COMPATIBLE;
            }
        } catch (RemoteException e) {
            Slog.w(TAG, "Failure checking voice capabilities", e);
            err = ActivityManager.START_NOT_VOICE_COMPATIBLE;
        }
    }

    final ActivityStack resultStack = resultRecord == null ? null : resultRecord.getStack();

    if (err != START_SUCCESS) {
        if (resultRecord != null) {
            resultStack.sendActivityResultLocked(
                    -1, resultRecord, resultWho, requestCode, RESULT_CANCELED, null);
        }
        SafeActivityOptions.abort(options);
        return err;
    }

    boolean abort = !mSupervisor.checkStartAnyActivityPermission(intent, aInfo, resultWho,
            requestCode, callingPid, callingUid, callingPackage, ignoreTargetSecurity,
            inTask != null, callerApp, resultRecord, resultStack);
    abort |= !mService.mIntentFirewall.checkStartActivity(intent, callingUid,
            callingPid, resolvedType, aInfo.applicationInfo);

    // Merge the two options bundles, while realCallerOptions takes precedence.
    ActivityOptions checkedOptions = options != null
            ? options.getOptions(intent, aInfo, callerApp, mSupervisor)
            : null;
    if (allowPendingRemoteAnimationRegistryLookup) {
        checkedOptions = mService.getActivityStartController()
                .getPendingRemoteAnimationRegistry()
                .overrideOptionsIfNeeded(callingPackage, checkedOptions);
    }
    if (mService.mController != null) {
        try {
            // The Intent we give to the watcher has the extra data
            // stripped off, since it can contain private information.
            Intent watchIntent = intent.cloneFilter();
            abort |= !mService.mController.activityStarting(watchIntent,
                    aInfo.applicationInfo.packageName);
        } catch (RemoteException e) {
            mService.mController = null;
        }
    }

    mInterceptor.setStates(userId, realCallingPid, realCallingUid, startFlags, callingPackage);
    if (mInterceptor.intercept(intent, rInfo, aInfo, resolvedType, inTask, callingPid,
            callingUid, checkedOptions)) {
        // activity start was intercepted, e.g. because the target user is currently in quiet
        // mode (turn off work) or the target application is suspended
        intent = mInterceptor.mIntent;
        rInfo = mInterceptor.mRInfo;
        aInfo = mInterceptor.mAInfo;
        resolvedType = mInterceptor.mResolvedType;
        inTask = mInterceptor.mInTask;
        callingPid = mInterceptor.mCallingPid;
        callingUid = mInterceptor.mCallingUid;
        checkedOptions = mInterceptor.mActivityOptions;
    }

    if (abort) {
        if (resultRecord != null) {
            resultStack.sendActivityResultLocked(-1, resultRecord, resultWho, requestCode,
                    RESULT_CANCELED, null);
        }
        // We pretend to the caller that it was really started, but
        // they will just get a cancel result.
        ActivityOptions.abort(checkedOptions);
        return START_ABORTED;
    }

    // If permissions need a review before any of the app components can run, we
    // launch the review activity and pass a pending intent to start the activity
    // we are to launching now after the review is completed.
    if (mService.mPermissionReviewRequired && aInfo != null) {
        if (mService.getPackageManagerInternalLocked().isPermissionsReviewRequired(
                aInfo.packageName, userId)) {
            IIntentSender target = mService.getIntentSenderLocked(
                    ActivityManager.INTENT_SENDER_ACTIVITY, callingPackage,
                    callingUid, userId, null, null, 0, new Intent[]{intent},
                    new String[]{resolvedType}, PendingIntent.FLAG_CANCEL_CURRENT
                            | PendingIntent.FLAG_ONE_SHOT, null);

            final int flags = intent.getFlags();
            Intent newIntent = new Intent(Intent.ACTION_REVIEW_PERMISSIONS);
            newIntent.setFlags(flags
                    | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            newIntent.putExtra(Intent.EXTRA_PACKAGE_NAME, aInfo.packageName);
            newIntent.putExtra(Intent.EXTRA_INTENT, new IntentSender(target));
            if (resultRecord != null) {
                newIntent.putExtra(Intent.EXTRA_RESULT_NEEDED, true);
            }
            intent = newIntent;

            resolvedType = null;
            callingUid = realCallingUid;
            callingPid = realCallingPid;

            rInfo = mSupervisor.resolveIntent(intent, resolvedType, userId, 0,
                    computeResolveFilterUid(
                            callingUid, realCallingUid, mRequest.filterCallingUid));
            aInfo = mSupervisor.resolveActivity(intent, rInfo, startFlags,
                    null /*profilerInfo*/);

            if (DEBUG_PERMISSIONS_REVIEW) {
                Slog.i(TAG, "START u" + userId + " {" + intent.toShortString(true, true,
                        true, false) + "} from uid " + callingUid + " on display "
                        + (mSupervisor.mFocusedStack == null
                        ? DEFAULT_DISPLAY : mSupervisor.mFocusedStack.mDisplayId));
            }
        }
    }

    // If we have an ephemeral app, abort the process of launching the resolved intent.
    // Instead, launch the ephemeral installer. Once the installer is finished, it
    // starts either the intent we resolved here [on install error] or the ephemeral
    // app [on install success].
    if (rInfo != null && rInfo.auxiliaryInfo != null) {
        intent = createLaunchIntent(rInfo.auxiliaryInfo, ephemeralIntent,
                callingPackage, verificationBundle, resolvedType, userId);
        resolvedType = null;
        callingUid = realCallingUid;
        callingPid = realCallingPid;

        aInfo = mSupervisor.resolveActivity(intent, rInfo, startFlags, null /*profilerInfo*/);
    }

    ActivityRecord r = new ActivityRecord(mService, callerApp, callingPid, callingUid,
            callingPackage, intent, resolvedType, aInfo, mService.getGlobalConfiguration(),
            resultRecord, resultWho, requestCode, componentSpecified, voiceSession != null,
            mSupervisor, checkedOptions, sourceRecord);
    if (outActivity != null) {
        outActivity[0] = r;
    }

    if (r.appTimeTracker == null && sourceRecord != null) {
        // If the caller didn't specify an explicit time tracker, we want to continue
        // tracking under any it has.
        r.appTimeTracker = sourceRecord.appTimeTracker;
    }

    final ActivityStack stack = mSupervisor.mFocusedStack;

    // If we are starting an activity that is not from the same uid as the currently resumed
    // one, check whether app switches are allowed.
    if (voiceSession == null && (stack.getResumedActivity() == null
            || stack.getResumedActivity().info.applicationInfo.uid != realCallingUid)) {
        if (!mService.checkAppSwitchAllowedLocked(callingPid, callingUid,
                realCallingPid, realCallingUid, "Activity start")) {
            mController.addPendingActivityLaunch(new PendingActivityLaunch(r,
                    sourceRecord, startFlags, stack, callerApp));
            ActivityOptions.abort(checkedOptions);
            return ActivityManager.START_SWITCHES_CANCELED;
        }
    }

    if (mService.mDidAppSwitch) {
        // This is the second allowed switch since we stopped switches,
        // so now just generally allow switches.  Use case: user presses
        // home (switches disabled, switch to home, mDidAppSwitch now true);
        // user taps a home icon (coming from home so allowed, we hit here
        // and now allow anyone to switch again).
        mService.mAppSwitchesAllowedTime = 0;
    } else {
        mService.mDidAppSwitch = true;
    }

    mController.doPendingActivityLaunches(false);

    return startActivity(r, sourceRecord, voiceSession, voiceInteractor, startFlags,
            true /* doResume */, checkedOptions, inTask, outActivity);
}
```

在这个方法中，主要创建一个 ActivityRecord 。重点关注最后一句代码：

	return startActivity(r, sourceRecord, voiceSession, voiceInteractor, startFlags, true /* doResume */, checkedOptions, inTask, outActivity);

所以又又又跳转到另外一个 startActivity 的重载方法中了。。

startActivity(final ActivityRecord r, ActivityRecord sourceRecord, IVoiceInteractionSession voiceSession, IVoiceInteractor voiceInteractor, int startFlags, boolean doResume, ActivityOptions options, TaskRecord inTask, ActivityRecord[] outActivity)
----
``` java
private int startActivity(final ActivityRecord r, ActivityRecord sourceRecord,
            IVoiceInteractionSession voiceSession, IVoiceInteractor voiceInteractor,
            int startFlags, boolean doResume, ActivityOptions options, TaskRecord inTask,
            ActivityRecord[] outActivity) {
    int result = START_CANCELED;
    try {
        mService.mWindowManager.deferSurfaceLayout();
        // 重点看这里
        result = startActivityUnchecked(r, sourceRecord, voiceSession, voiceInteractor,
                startFlags, doResume, options, inTask, outActivity);
    } finally {
        // If we are not able to proceed, disassociate the activity from the task. Leaving an
        // activity in an incomplete state can lead to issues, such as performing operations
        // without a window container.
        final ActivityStack stack = mStartActivity.getStack();
        if (!ActivityManager.isStartResultSuccessful(result) && stack != null) {
            stack.finishActivityLocked(mStartActivity, RESULT_CANCELED,
                    null /* intentResultData */, "startActivity", true /* oomAdj */);
        }
        mService.mWindowManager.continueSurfaceLayout();
    }

    postStartActivityProcessing(r, result, mTargetStack);

    return result;
}
```

在方法内部，把操作交给了 startActivityUnchecked 方法。

startActivityUnchecked(final ActivityRecord r, ActivityRecord sourceRecord, IVoiceInteractionSession voiceSession, IVoiceInteractor voiceInteractor, int startFlags, boolean doResume, ActivityOptions options, TaskRecord inTask, ActivityRecord[] outActivity)
----
``` java
private int startActivityUnchecked(final ActivityRecord r, ActivityRecord sourceRecord,
        IVoiceInteractionSession voiceSession, IVoiceInteractor voiceInteractor,
        int startFlags, boolean doResume, ActivityOptions options, TaskRecord inTask,
        ActivityRecord[] outActivity) {

    setInitialState(r, options, inTask, doResume, startFlags, sourceRecord, voiceSession,
            voiceInteractor);

    computeLaunchingTaskFlags();

    computeSourceStack();

    mIntent.setFlags(mLaunchFlags);

    ActivityRecord reusedActivity = getReusableIntentActivity();

    int preferredWindowingMode = WINDOWING_MODE_UNDEFINED;
    int preferredLaunchDisplayId = DEFAULT_DISPLAY;
    if (mOptions != null) {
        preferredWindowingMode = mOptions.getLaunchWindowingMode();
        preferredLaunchDisplayId = mOptions.getLaunchDisplayId();
    }

    // windowing mode and preferred launch display values from {@link LaunchParams} take
    // priority over those specified in {@link ActivityOptions}.
    if (!mLaunchParams.isEmpty()) {
        if (mLaunchParams.hasPreferredDisplay()) {
            preferredLaunchDisplayId = mLaunchParams.mPreferredDisplayId;
        }

        if (mLaunchParams.hasWindowingMode()) {
            preferredWindowingMode = mLaunchParams.mWindowingMode;
        }
    }

    if (reusedActivity != null) {
        // When the flags NEW_TASK and CLEAR_TASK are set, then the task gets reused but
        // still needs to be a lock task mode violation since the task gets cleared out and
        // the device would otherwise leave the locked task.
        if (mService.getLockTaskController().isLockTaskModeViolation(reusedActivity.getTask(),
                (mLaunchFlags & (FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK))
                        == (FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK))) {
            Slog.e(TAG, "startActivityUnchecked: Attempt to violate Lock Task Mode");
            return START_RETURN_LOCK_TASK_MODE_VIOLATION;
        }

        // True if we are clearing top and resetting of a standard (default) launch mode
        // ({@code LAUNCH_MULTIPLE}) activity. The existing activity will be finished.
        final boolean clearTopAndResetStandardLaunchMode =
                (mLaunchFlags & (FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
                        == (FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                && mLaunchMode == LAUNCH_MULTIPLE;

        // If mStartActivity does not have a task associated with it, associate it with the
        // reused activity's task. Do not do so if we're clearing top and resetting for a
        // standard launchMode activity.
        if (mStartActivity.getTask() == null && !clearTopAndResetStandardLaunchMode) {
            mStartActivity.setTask(reusedActivity.getTask());
        }

        if (reusedActivity.getTask().intent == null) {
            // This task was started because of movement of the activity based on affinity...
            // Now that we are actually launching it, we can assign the base intent.
            reusedActivity.getTask().setIntent(mStartActivity);
        }

        // This code path leads to delivering a new intent, we want to make sure we schedule it
        // as the first operation, in case the activity will be resumed as a result of later
        // operations.
        if ((mLaunchFlags & FLAG_ACTIVITY_CLEAR_TOP) != 0
                || isDocumentLaunchesIntoExisting(mLaunchFlags)
                || isLaunchModeOneOf(LAUNCH_SINGLE_INSTANCE, LAUNCH_SINGLE_TASK)) {
            final TaskRecord task = reusedActivity.getTask();

            // In this situation we want to remove all activities from the task up to the one
            // being started. In most cases this means we are resetting the task to its initial
            // state.
            final ActivityRecord top = task.performClearTaskForReuseLocked(mStartActivity,
                    mLaunchFlags);

            // The above code can remove {@code reusedActivity} from the task, leading to the
            // the {@code ActivityRecord} removing its reference to the {@code TaskRecord}. The
            // task reference is needed in the call below to
            // {@link setTargetStackAndMoveToFrontIfNeeded}.
            if (reusedActivity.getTask() == null) {
                reusedActivity.setTask(task);
            }

            if (top != null) {
                if (top.frontOfTask) {
                    // Activity aliases may mean we use different intents for the top activity,
                    // so make sure the task now has the identity of the new intent.
                    top.getTask().setIntent(mStartActivity);
                }
                deliverNewIntent(top);
            }
        }

        mSupervisor.sendPowerHintForLaunchStartIfNeeded(false /* forceSend */, reusedActivity);

        reusedActivity = setTargetStackAndMoveToFrontIfNeeded(reusedActivity);

        final ActivityRecord outResult =
                outActivity != null && outActivity.length > 0 ? outActivity[0] : null;

        // When there is a reused activity and the current result is a trampoline activity,
        // set the reused activity as the result.
        if (outResult != null && (outResult.finishing || outResult.noDisplay)) {
            outActivity[0] = reusedActivity;
        }

        if ((mStartFlags & START_FLAG_ONLY_IF_NEEDED) != 0) {
            // We don't need to start a new activity, and the client said not to do anything
            // if that is the case, so this is it!  And for paranoia, make sure we have
            // correctly resumed the top activity.
            resumeTargetStackIfNeeded();
            return START_RETURN_INTENT_TO_CALLER;
        }

        if (reusedActivity != null) {
            setTaskFromIntentActivity(reusedActivity);

            if (!mAddingToTask && mReuseTask == null) {
                // We didn't do anything...  but it was needed (a.k.a., client don't use that
                // intent!)  And for paranoia, make sure we have correctly resumed the top activity.

                resumeTargetStackIfNeeded();
                if (outActivity != null && outActivity.length > 0) {
                    outActivity[0] = reusedActivity;
                }

                return mMovedToFront ? START_TASK_TO_FRONT : START_DELIVERED_TO_TOP;
            }
        }
    }

    if (mStartActivity.packageName == null) {
        final ActivityStack sourceStack = mStartActivity.resultTo != null
                ? mStartActivity.resultTo.getStack() : null;
        if (sourceStack != null) {
            sourceStack.sendActivityResultLocked(-1 /* callingUid */, mStartActivity.resultTo,
                    mStartActivity.resultWho, mStartActivity.requestCode, RESULT_CANCELED,
                    null /* data */);
        }
        ActivityOptions.abort(mOptions);
        return START_CLASS_NOT_FOUND;
    }

    // If the activity being launched is the same as the one currently at the top, then
    // we need to check if it should only be launched once.
    final ActivityStack topStack = mSupervisor.mFocusedStack;
    final ActivityRecord topFocused = topStack.getTopActivity();
    final ActivityRecord top = topStack.topRunningNonDelayedActivityLocked(mNotTop);
    final boolean dontStart = top != null && mStartActivity.resultTo == null
            && top.realActivity.equals(mStartActivity.realActivity)
            && top.userId == mStartActivity.userId
            && top.app != null && top.app.thread != null
            && ((mLaunchFlags & FLAG_ACTIVITY_SINGLE_TOP) != 0
            || isLaunchModeOneOf(LAUNCH_SINGLE_TOP, LAUNCH_SINGLE_TASK));
    if (dontStart) {
        // For paranoia, make sure we have correctly resumed the top activity.
        topStack.mLastPausedActivity = null;
        if (mDoResume) {
            mSupervisor.resumeFocusedStackTopActivityLocked();
        }
        ActivityOptions.abort(mOptions);
        if ((mStartFlags & START_FLAG_ONLY_IF_NEEDED) != 0) {
            // We don't need to start a new activity, and the client said not to do
            // anything if that is the case, so this is it!
            return START_RETURN_INTENT_TO_CALLER;
        }

        deliverNewIntent(top);

        // Don't use mStartActivity.task to show the toast. We're not starting a new activity
        // but reusing 'top'. Fields in mStartActivity may not be fully initialized.
        mSupervisor.handleNonResizableTaskIfNeeded(top.getTask(), preferredWindowingMode,
                preferredLaunchDisplayId, topStack);

        return START_DELIVERED_TO_TOP;
    }

    boolean newTask = false;
    final TaskRecord taskToAffiliate = (mLaunchTaskBehind && mSourceRecord != null)
            ? mSourceRecord.getTask() : null;

    // Should this be considered a new task?
    int result = START_SUCCESS;
    if (mStartActivity.resultTo == null && mInTask == null && !mAddingToTask
            && (mLaunchFlags & FLAG_ACTIVITY_NEW_TASK) != 0) {
        newTask = true;
        result = setTaskFromReuseOrCreateNewTask(taskToAffiliate, topStack);
    } else if (mSourceRecord != null) {
        result = setTaskFromSourceRecord();
    } else if (mInTask != null) {
        result = setTaskFromInTask();
    } else {
        // This not being started from an existing activity, and not part of a new task...
        // just put it in the top task, though these days this case should never happen.
        setTaskToCurrentTopOrCreateNewTask();
    }
    if (result != START_SUCCESS) {
        return result;
    }

    mService.grantUriPermissionFromIntentLocked(mCallingUid, mStartActivity.packageName,
            mIntent, mStartActivity.getUriPermissionsLocked(), mStartActivity.userId);
    mService.grantEphemeralAccessLocked(mStartActivity.userId, mIntent,
            mStartActivity.appInfo.uid, UserHandle.getAppId(mCallingUid));
    if (newTask) {
        EventLog.writeEvent(EventLogTags.AM_CREATE_TASK, mStartActivity.userId,
                mStartActivity.getTask().taskId);
    }
    ActivityStack.logStartActivity(
            EventLogTags.AM_CREATE_ACTIVITY, mStartActivity, mStartActivity.getTask());
    mTargetStack.mLastPausedActivity = null;

    mSupervisor.sendPowerHintForLaunchStartIfNeeded(false /* forceSend */, mStartActivity);

    mTargetStack.startActivityLocked(mStartActivity, topFocused, newTask, mKeepCurTransition,
            mOptions);
    if (mDoResume) {
        final ActivityRecord topTaskActivity =
                mStartActivity.getTask().topRunningActivityLocked();
        if (!mTargetStack.isFocusable()
                || (topTaskActivity != null && topTaskActivity.mTaskOverlay
                && mStartActivity != topTaskActivity)) {
            // If the activity is not focusable, we can't resume it, but still would like to
            // make sure it becomes visible as it starts (this will also trigger entry
            // animation). An example of this are PIP activities.
            // Also, we don't want to resume activities in a task that currently has an overlay
            // as the starting activity just needs to be in the visible paused state until the
            // over is removed.
            mTargetStack.ensureActivitiesVisibleLocked(null, 0, !PRESERVE_WINDOWS);
            // Go ahead and tell window manager to execute app transition for this activity
            // since the app transition will not be triggered through the resume channel.
            mService.mWindowManager.executeAppTransition();
        } else {
            // If the target stack was not previously focusable (previous top running activity
            // on that stack was not visible) then any prior calls to move the stack to the
            // will not update the focused stack.  If starting the new activity now allows the
            // task stack to be focusable, then ensure that we now update the focused stack
            // accordingly.
            if (mTargetStack.isFocusable() && !mSupervisor.isFocusedStack(mTargetStack)) {
                mTargetStack.moveToFront("startActivityUnchecked");
            }
            mSupervisor.resumeFocusedStackTopActivityLocked(mTargetStack, mStartActivity,
                    mOptions);
        }
    } else if (mStartActivity != null) {
        mSupervisor.mRecentTasks.add(mStartActivity.getTask());
    }
    mSupervisor.updateUserStackLocked(mStartActivity.userId, mTargetStack);

    mSupervisor.handleNonResizableTaskIfNeeded(mStartActivity.getTask(), preferredWindowingMode,
            preferredLaunchDisplayId, mTargetStack);

    return START_SUCCESS;
}
```

在 startActivityUnchecked 中，给 Activity 设置了 TaskRecord , 完成后执行ActivityStackSupervisor.resumeFocusedStackTopActivityLocked 方法。

ActivityStackSupervisor
====
resumeFocusedStackTopActivityLocked(ActivityStack targetStack, ActivityRecord target, ActivityOptions targetOptions)
----
``` java
boolean resumeFocusedStackTopActivityLocked(
        ActivityStack targetStack, ActivityRecord target, ActivityOptions targetOptions) {

    if (!readyToResume()) {
        return false;
    }

    if (targetStack != null && isFocusedStack(targetStack)) {
        return targetStack.resumeTopActivityUncheckedLocked(target, targetOptions);
    }

    final ActivityRecord r = mFocusedStack.topRunningActivityLocked();
    if (r == null || !r.isState(RESUMED)) {
        mFocusedStack.resumeTopActivityUncheckedLocked(null, null);
    } else if (r.isState(RESUMED)) {
        // Kick off any lingering app transitions form the MoveTaskToFront operation.
        mFocusedStack.executeAppTransition(targetOptions);
    }

    return false;
}
```

在 resumeFocusedStackTopActivityLocked 中，跳转到 ActivityStack 的 resumeTopActivityUncheckedLocked 方法中去执行了。

ActivityStack
====
resumeTopActivityUncheckedLocked(ActivityRecord prev, ActivityOptions options)
----
``` java
@GuardedBy("mService")
boolean resumeTopActivityUncheckedLocked(ActivityRecord prev, ActivityOptions options) {
    if (mStackSupervisor.inResumeTopActivity) {
        // Don't even start recursing.
        return false;
    }

    boolean result = false;
    try {
        // Protect against recursion.
        mStackSupervisor.inResumeTopActivity = true;
        result = resumeTopActivityInnerLocked(prev, options);

        // When resuming the top activity, it may be necessary to pause the top activity (for
        // example, returning to the lock screen. We suppress the normal pause logic in
        // {@link #resumeTopActivityUncheckedLocked}, since the top activity is resumed at the
        // end. We call the {@link ActivityStackSupervisor#checkReadyForSleepLocked} again here
        // to ensure any necessary pause logic occurs. In the case where the Activity will be
        // shown regardless of the lock screen, the call to
        // {@link ActivityStackSupervisor#checkReadyForSleepLocked} is skipped.
        final ActivityRecord next = topRunningActivityLocked(true /* focusableOnly */);
        if (next == null || !next.canTurnScreenOn()) {
            checkReadyForSleep();
        }
    } finally {
        mStackSupervisor.inResumeTopActivity = false;
    }

    return result;
}
```

可以看到代码中的 result 是 resumeTopActivityInnerLocked 方法的返回值。所以还要跳到 resumeTopActivityInnerLocked 方法中去看。

resumeTopActivityInnerLocked(ActivityRecord prev, ActivityOptions options)
----
``` java
@GuardedBy("mService")
private boolean resumeTopActivityInnerLocked(ActivityRecord prev, ActivityOptions options) {
    if (!mService.mBooting && !mService.mBooted) {
        // Not ready yet!
        return false;
    }

    // Find the next top-most activity to resume in this stack that is not finishing and is
    // focusable. If it is not focusable, we will fall into the case below to resume the
    // top activity in the next focusable task.
    final ActivityRecord next = topRunningActivityLocked(true /* focusableOnly */);

    final boolean hasRunningActivity = next != null;

    // TODO: Maybe this entire condition can get removed?
    if (hasRunningActivity && !isAttached()) {
        return false;
    }

    mStackSupervisor.cancelInitializingActivities();

    // Remember how we'll process this pause/resume situation, and ensure
    // that the state is reset however we wind up proceeding.
    boolean userLeaving = mStackSupervisor.mUserLeaving;
    mStackSupervisor.mUserLeaving = false;

    if (!hasRunningActivity) {
        // There are no activities left in the stack, let's look somewhere else.
        return resumeTopActivityInNextFocusableStack(prev, options, "noMoreActivities");
    }

    next.delayedResume = false;

    // If the top activity is the resumed one, nothing to do.
    if (mResumedActivity == next && next.isState(RESUMED)
            && mStackSupervisor.allResumedActivitiesComplete()) {
        // Make sure we have executed any pending transitions, since there
        // should be nothing left to do at this point.
        executeAppTransition(options);
        if (DEBUG_STATES) Slog.d(TAG_STATES,
                "resumeTopActivityLocked: Top activity resumed " + next);
        if (DEBUG_STACK) mStackSupervisor.validateTopActivitiesLocked();
        return false;
    }

    // If we are sleeping, and there is no resumed activity, and the top
    // activity is paused, well that is the state we want.
    if (shouldSleepOrShutDownActivities()
            && mLastPausedActivity == next
            && mStackSupervisor.allPausedActivitiesComplete()) {
        // Make sure we have executed any pending transitions, since there
        // should be nothing left to do at this point.
        executeAppTransition(options);
        if (DEBUG_STATES) Slog.d(TAG_STATES,
                "resumeTopActivityLocked: Going to sleep and all paused");
        if (DEBUG_STACK) mStackSupervisor.validateTopActivitiesLocked();
        return false;
    }

    // Make sure that the user who owns this activity is started.  If not,
    // we will just leave it as is because someone should be bringing
    // another user's activities to the top of the stack.
    if (!mService.mUserController.hasStartedUserState(next.userId)) {
        Slog.w(TAG, "Skipping resume of top activity " + next
                + ": user " + next.userId + " is stopped");
        if (DEBUG_STACK) mStackSupervisor.validateTopActivitiesLocked();
        return false;
    }

    // The activity may be waiting for stop, but that is no longer
    // appropriate for it.
    mStackSupervisor.mStoppingActivities.remove(next);
    mStackSupervisor.mGoingToSleepActivities.remove(next);
    next.sleeping = false;
    mStackSupervisor.mActivitiesWaitingForVisibleActivity.remove(next);

    if (DEBUG_SWITCH) Slog.v(TAG_SWITCH, "Resuming " + next);

    // If we are currently pausing an activity, then don't do anything until that is done.
    if (!mStackSupervisor.allPausedActivitiesComplete()) {
        if (DEBUG_SWITCH || DEBUG_PAUSE || DEBUG_STATES) Slog.v(TAG_PAUSE,
                "resumeTopActivityLocked: Skip resume: some activity pausing.");
        if (DEBUG_STACK) mStackSupervisor.validateTopActivitiesLocked();
        return false;
    }

    mStackSupervisor.setLaunchSource(next.info.applicationInfo.uid);

    boolean lastResumedCanPip = false;
    ActivityRecord lastResumed = null;
    final ActivityStack lastFocusedStack = mStackSupervisor.getLastStack();
    if (lastFocusedStack != null && lastFocusedStack != this) {
        // So, why aren't we using prev here??? See the param comment on the method. prev doesn't
        // represent the last resumed activity. However, the last focus stack does if it isn't null.
        lastResumed = lastFocusedStack.mResumedActivity;
        if (userLeaving && inMultiWindowMode() && lastFocusedStack.shouldBeVisible(next)) {
            // The user isn't leaving if this stack is the multi-window mode and the last
            // focused stack should still be visible.
            if(DEBUG_USER_LEAVING) Slog.i(TAG_USER_LEAVING, "Overriding userLeaving to false"
                    + " next=" + next + " lastResumed=" + lastResumed);
            userLeaving = false;
        }
        lastResumedCanPip = lastResumed != null && lastResumed.checkEnterPictureInPictureState(
                "resumeTopActivity", userLeaving /* beforeStopping */);
    }
    // If the flag RESUME_WHILE_PAUSING is set, then continue to schedule the previous activity
    // to be paused, while at the same time resuming the new resume activity only if the
    // previous activity can't go into Pip since we want to give Pip activities a chance to
    // enter Pip before resuming the next activity.
    final boolean resumeWhilePausing = (next.info.flags & FLAG_RESUME_WHILE_PAUSING) != 0
            && !lastResumedCanPip;

    boolean pausing = mStackSupervisor.pauseBackStacks(userLeaving, next, false);
    if (mResumedActivity != null) {
        if (DEBUG_STATES) Slog.d(TAG_STATES,
                "resumeTopActivityLocked: Pausing " + mResumedActivity);
        pausing |= startPausingLocked(userLeaving, false, next, false);
    }
    if (pausing && !resumeWhilePausing) {
        if (DEBUG_SWITCH || DEBUG_STATES) Slog.v(TAG_STATES,
                "resumeTopActivityLocked: Skip resume: need to start pausing");
        // At this point we want to put the upcoming activity's process
        // at the top of the LRU list, since we know we will be needing it
        // very soon and it would be a waste to let it get killed if it
        // happens to be sitting towards the end.
        if (next.app != null && next.app.thread != null) {
            mService.updateLruProcessLocked(next.app, true, null);
        }
        if (DEBUG_STACK) mStackSupervisor.validateTopActivitiesLocked();
        if (lastResumed != null) {
            lastResumed.setWillCloseOrEnterPip(true);
        }
        return true;
    } else if (mResumedActivity == next && next.isState(RESUMED)
            && mStackSupervisor.allResumedActivitiesComplete()) {
        // It is possible for the activity to be resumed when we paused back stacks above if the
        // next activity doesn't have to wait for pause to complete.
        // So, nothing else to-do except:
        // Make sure we have executed any pending transitions, since there
        // should be nothing left to do at this point.
        executeAppTransition(options);
        if (DEBUG_STATES) Slog.d(TAG_STATES,
                "resumeTopActivityLocked: Top activity resumed (dontWaitForPause) " + next);
        if (DEBUG_STACK) mStackSupervisor.validateTopActivitiesLocked();
        return true;
    }

    // If the most recent activity was noHistory but was only stopped rather
    // than stopped+finished because the device went to sleep, we need to make
    // sure to finish it as we're making a new activity topmost.
    if (shouldSleepActivities() && mLastNoHistoryActivity != null &&
            !mLastNoHistoryActivity.finishing) {
        if (DEBUG_STATES) Slog.d(TAG_STATES,
                "no-history finish of " + mLastNoHistoryActivity + " on new resume");
        requestFinishActivityLocked(mLastNoHistoryActivity.appToken, Activity.RESULT_CANCELED,
                null, "resume-no-history", false);
        mLastNoHistoryActivity = null;
    }

    if (prev != null && prev != next) {
        if (!mStackSupervisor.mActivitiesWaitingForVisibleActivity.contains(prev)
                && next != null && !next.nowVisible) {
            mStackSupervisor.mActivitiesWaitingForVisibleActivity.add(prev);
            if (DEBUG_SWITCH) Slog.v(TAG_SWITCH,
                    "Resuming top, waiting visible to hide: " + prev);
        } else {
            // The next activity is already visible, so hide the previous
            // activity's windows right now so we can show the new one ASAP.
            // We only do this if the previous is finishing, which should mean
            // it is on top of the one being resumed so hiding it quickly
            // is good.  Otherwise, we want to do the normal route of allowing
            // the resumed activity to be shown so we can decide if the
            // previous should actually be hidden depending on whether the
            // new one is found to be full-screen or not.
            if (prev.finishing) {
                prev.setVisibility(false);
                if (DEBUG_SWITCH) Slog.v(TAG_SWITCH,
                        "Not waiting for visible to hide: " + prev + ", waitingVisible="
                        + mStackSupervisor.mActivitiesWaitingForVisibleActivity.contains(prev)
                        + ", nowVisible=" + next.nowVisible);
            } else {
                if (DEBUG_SWITCH) Slog.v(TAG_SWITCH,
                        "Previous already visible but still waiting to hide: " + prev
                        + ", waitingVisible="
                        + mStackSupervisor.mActivitiesWaitingForVisibleActivity.contains(prev)
                        + ", nowVisible=" + next.nowVisible);
            }
        }
    }

    // Launching this app's activity, make sure the app is no longer
    // considered stopped.
    try {
        AppGlobals.getPackageManager().setPackageStoppedState(
                next.packageName, false, next.userId); /* TODO: Verify if correct userid */
    } catch (RemoteException e1) {
    } catch (IllegalArgumentException e) {
        Slog.w(TAG, "Failed trying to unstop package "
                + next.packageName + ": " + e);
    }

    // We are starting up the next activity, so tell the window manager
    // that the previous one will be hidden soon.  This way it can know
    // to ignore it when computing the desired screen orientation.
    boolean anim = true;
    if (prev != null) {
        if (prev.finishing) {
            if (DEBUG_TRANSITION) Slog.v(TAG_TRANSITION,
                    "Prepare close transition: prev=" + prev);
            if (mStackSupervisor.mNoAnimActivities.contains(prev)) {
                anim = false;
                mWindowManager.prepareAppTransition(TRANSIT_NONE, false);
            } else {
                mWindowManager.prepareAppTransition(prev.getTask() == next.getTask()
                        ? TRANSIT_ACTIVITY_CLOSE
                        : TRANSIT_TASK_CLOSE, false);
            }
            prev.setVisibility(false);
        } else {
            if (DEBUG_TRANSITION) Slog.v(TAG_TRANSITION,
                    "Prepare open transition: prev=" + prev);
            if (mStackSupervisor.mNoAnimActivities.contains(next)) {
                anim = false;
                mWindowManager.prepareAppTransition(TRANSIT_NONE, false);
            } else {
                mWindowManager.prepareAppTransition(prev.getTask() == next.getTask()
                        ? TRANSIT_ACTIVITY_OPEN
                        : next.mLaunchTaskBehind
                                ? TRANSIT_TASK_OPEN_BEHIND
                                : TRANSIT_TASK_OPEN, false);
            }
        }
    } else {
        if (DEBUG_TRANSITION) Slog.v(TAG_TRANSITION, "Prepare open transition: no previous");
        if (mStackSupervisor.mNoAnimActivities.contains(next)) {
            anim = false;
            mWindowManager.prepareAppTransition(TRANSIT_NONE, false);
        } else {
            mWindowManager.prepareAppTransition(TRANSIT_ACTIVITY_OPEN, false);
        }
    }

    if (anim) {
        next.applyOptionsLocked();
    } else {
        next.clearOptionsLocked();
    }

    mStackSupervisor.mNoAnimActivities.clear();

    ActivityStack lastStack = mStackSupervisor.getLastStack();
    if (next.app != null && next.app.thread != null) {
        if (DEBUG_SWITCH) Slog.v(TAG_SWITCH, "Resume running: " + next
                + " stopped=" + next.stopped + " visible=" + next.visible);

        // If the previous activity is translucent, force a visibility update of
        // the next activity, so that it's added to WM's opening app list, and
        // transition animation can be set up properly.
        // For example, pressing Home button with a translucent activity in focus.
        // Launcher is already visible in this case. If we don't add it to opening
        // apps, maybeUpdateTransitToWallpaper() will fail to identify this as a
        // TRANSIT_WALLPAPER_OPEN animation, and run some funny animation.
        final boolean lastActivityTranslucent = lastStack != null
                && (lastStack.inMultiWindowMode()
                || (lastStack.mLastPausedActivity != null
                && !lastStack.mLastPausedActivity.fullscreen));

        // The contained logic must be synchronized, since we are both changing the visibility
        // and updating the {@link Configuration}. {@link ActivityRecord#setVisibility} will
        // ultimately cause the client code to schedule a layout. Since layouts retrieve the
        // current {@link Configuration}, we must ensure that the below code updates it before
        // the layout can occur.
        synchronized(mWindowManager.getWindowManagerLock()) {
            // This activity is now becoming visible.
            if (!next.visible || next.stopped || lastActivityTranslucent) {
                next.setVisibility(true);
            }

            // schedule launch ticks to collect information about slow apps.
            next.startLaunchTickingLocked();

            ActivityRecord lastResumedActivity =
                    lastStack == null ? null :lastStack.mResumedActivity;
            final ActivityState lastState = next.getState();

            mService.updateCpuStats();

            if (DEBUG_STATES) Slog.v(TAG_STATES, "Moving to RESUMED: " + next
                    + " (in existing)");

            next.setState(RESUMED, "resumeTopActivityInnerLocked");

            mService.updateLruProcessLocked(next.app, true, null);
            updateLRUListLocked(next);
            mService.updateOomAdjLocked();

            // Have the window manager re-evaluate the orientation of
            // the screen based on the new activity order.
            boolean notUpdated = true;

            if (mStackSupervisor.isFocusedStack(this)) {
                // We have special rotation behavior when here is some active activity that
                // requests specific orientation or Keyguard is locked. Make sure all activity
                // visibilities are set correctly as well as the transition is updated if needed
                // to get the correct rotation behavior. Otherwise the following call to update
                // the orientation may cause incorrect configurations delivered to client as a
                // result of invisible window resize.
                // TODO: Remove this once visibilities are set correctly immediately when
                // starting an activity.
                notUpdated = !mStackSupervisor.ensureVisibilityAndConfig(next, mDisplayId,
                        true /* markFrozenIfConfigChanged */, false /* deferResume */);
            }

            if (notUpdated) {
                // The configuration update wasn't able to keep the existing
                // instance of the activity, and instead started a new one.
                // We should be all done, but let's just make sure our activity
                // is still at the top and schedule another run if something
                // weird happened.
                ActivityRecord nextNext = topRunningActivityLocked();
                if (DEBUG_SWITCH || DEBUG_STATES) Slog.i(TAG_STATES,
                        "Activity config changed during resume: " + next
                                + ", new next: " + nextNext);
                if (nextNext != next) {
                    // Do over!
                    mStackSupervisor.scheduleResumeTopActivities();
                }
                if (!next.visible || next.stopped) {
                    next.setVisibility(true);
                }
                next.completeResumeLocked();
                if (DEBUG_STACK) mStackSupervisor.validateTopActivitiesLocked();
                return true;
            }

            try {
                final ClientTransaction transaction = ClientTransaction.obtain(next.app.thread,
                        next.appToken);
                // Deliver all pending results.
                ArrayList<ResultInfo> a = next.results;
                if (a != null) {
                    final int N = a.size();
                    if (!next.finishing && N > 0) {
                        if (DEBUG_RESULTS) Slog.v(TAG_RESULTS,
                                "Delivering results to " + next + ": " + a);
                        transaction.addCallback(ActivityResultItem.obtain(a));
                    }
                }

                if (next.newIntents != null) {
                    transaction.addCallback(NewIntentItem.obtain(next.newIntents,
                            false /* andPause */));
                }

                // Well the app will no longer be stopped.
                // Clear app token stopped state in window manager if needed.
                next.notifyAppResumed(next.stopped);

                EventLog.writeEvent(EventLogTags.AM_RESUME_ACTIVITY, next.userId,
                        System.identityHashCode(next), next.getTask().taskId,
                        next.shortComponentName);

                next.sleeping = false;
                mService.getAppWarningsLocked().onResumeActivity(next);
                mService.showAskCompatModeDialogLocked(next);
                next.app.pendingUiClean = true;
                next.app.forceProcessStateUpTo(mService.mTopProcessState);
                next.clearOptionsLocked();
                transaction.setLifecycleStateRequest(
                        ResumeActivityItem.obtain(next.app.repProcState,
                                mService.isNextTransitionForward()));
                mService.getLifecycleManager().scheduleTransaction(transaction);

                if (DEBUG_STATES) Slog.d(TAG_STATES, "resumeTopActivityLocked: Resumed "
                        + next);
            } catch (Exception e) {
                // Whoops, need to restart this activity!
                if (DEBUG_STATES) Slog.v(TAG_STATES, "Resume failed; resetting state to "
                        + lastState + ": " + next);
                next.setState(lastState, "resumeTopActivityInnerLocked");

                // lastResumedActivity being non-null implies there is a lastStack present.
                if (lastResumedActivity != null) {
                    lastResumedActivity.setState(RESUMED, "resumeTopActivityInnerLocked");
                }

                Slog.i(TAG, "Restarting because process died: " + next);
                if (!next.hasBeenLaunched) {
                    next.hasBeenLaunched = true;
                } else  if (SHOW_APP_STARTING_PREVIEW && lastStack != null
                        && lastStack.isTopStackOnDisplay()) {
                    next.showStartingWindow(null /* prev */, false /* newTask */,
                            false /* taskSwitch */);
                }
                mStackSupervisor.startSpecificActivityLocked(next, true, false);
                if (DEBUG_STACK) mStackSupervisor.validateTopActivitiesLocked();
                return true;
            }
        }

        // From this point on, if something goes wrong there is no way
        // to recover the activity.
        try {
            next.completeResumeLocked();
        } catch (Exception e) {
            // If any exception gets thrown, toss away this
            // activity and try the next one.
            Slog.w(TAG, "Exception thrown during resume of " + next, e);
            requestFinishActivityLocked(next.appToken, Activity.RESULT_CANCELED, null,
                    "resume-exception", true);
            if (DEBUG_STACK) mStackSupervisor.validateTopActivitiesLocked();
            return true;
        }
    } else {
        // Whoops, need to restart this activity!
        if (!next.hasBeenLaunched) {
            next.hasBeenLaunched = true;
        } else {
            if (SHOW_APP_STARTING_PREVIEW) {
                next.showStartingWindow(null /* prev */, false /* newTask */,
                        false /* taskSwich */);
            }
            if (DEBUG_SWITCH) Slog.v(TAG_SWITCH, "Restarting: " + next);
        }
        if (DEBUG_STATES) Slog.d(TAG_STATES, "resumeTopActivityLocked: Restarting " + next);
        mStackSupervisor.startSpecificActivityLocked(next, true, true);
    }

    if (DEBUG_STACK) mStackSupervisor.validateTopActivitiesLocked();
    return true;
}
```

方法中的代码很长，可以看到最后都是调用了 mStackSupervisor.startSpecificActivityLocked

所以还是要到 ActivityStackSupervisor 的 startSpecificActivityLocked 方法中去看。

ActivityStackSupervisor
====
startSpecificActivityLocked(ActivityRecord r, boolean andResume, boolean checkConfig)
-----
``` java
void startSpecificActivityLocked(ActivityRecord r,
        boolean andResume, boolean checkConfig) {
    // Is this activity's application already running?
    ProcessRecord app = mService.getProcessRecordLocked(r.processName,
            r.info.applicationInfo.uid, true);

    getLaunchTimeTracker().setLaunchTime(r);

    if (app != null && app.thread != null) {
        try {
            if ((r.info.flags&ActivityInfo.FLAG_MULTIPROCESS) == 0
                    || !"android".equals(r.info.packageName)) {
                // Don't add this if it is a platform component that is marked
                // to run in multiple processes, because this is actually
                // part of the framework so doesn't make sense to track as a
                // separate apk in the process.
                app.addPackage(r.info.packageName, r.info.applicationInfo.longVersionCode,
                        mService.mProcessStats);
            }
            realStartActivityLocked(r, app, andResume, checkConfig);
            return;
        } catch (RemoteException e) {
            Slog.w(TAG, "Exception when starting activity "
                    + r.intent.getComponent().flattenToShortString(), e);
        }

        // If a dead object exception was thrown -- fall through to
        // restart the application.
    }

    mService.startProcessLocked(r.processName, r.info.applicationInfo, true, 0,
            "activity", r.intent.getComponent(), false, false, true);
}
```

在 startSpecificActivityLocked 中会判断进程是否存在，因为我们分析的是 startActivity 的逻辑，所以肯定是已经存在了进程的，所以会调用 realStartActivityLocked()方法。

realStartActivityLocked(ActivityRecord r, ProcessRecord app, boolean andResume, boolean checkConfig)
----
``` java
final boolean realStartActivityLocked(ActivityRecord r, ProcessRecord app,
        boolean andResume, boolean checkConfig) throws RemoteException {

    if (!allPausedActivitiesComplete()) {
        // While there are activities pausing we skipping starting any new activities until
        // pauses are complete. NOTE: that we also do this for activities that are starting in
        // the paused state because they will first be resumed then paused on the client side.
        if (DEBUG_SWITCH || DEBUG_PAUSE || DEBUG_STATES) Slog.v(TAG_PAUSE,
                "realStartActivityLocked: Skipping start of r=" + r
                + " some activities pausing...");
        return false;
    }

    final TaskRecord task = r.getTask();
    final ActivityStack stack = task.getStack();

    beginDeferResume();

    try {
        r.startFreezingScreenLocked(app, 0);

        // schedule launch ticks to collect information about slow apps.
        r.startLaunchTickingLocked();

        r.setProcess(app);

        if (getKeyguardController().isKeyguardLocked()) {
            r.notifyUnknownVisibilityLaunched();
        }

        // Have the window manager re-evaluate the orientation of the screen based on the new
        // activity order.  Note that as a result of this, it can call back into the activity
        // manager with a new orientation.  We don't care about that, because the activity is
        // not currently running so we are just restarting it anyway.
        if (checkConfig) {
            // Deferring resume here because we're going to launch new activity shortly.
            // We don't want to perform a redundant launch of the same record while ensuring
            // configurations and trying to resume top activity of focused stack.
            ensureVisibilityAndConfig(r, r.getDisplayId(),
                    false /* markFrozenIfConfigChanged */, true /* deferResume */);
        }

        if (r.getStack().checkKeyguardVisibility(r, true /* shouldBeVisible */,
                true /* isTop */)) {
            // We only set the visibility to true if the activity is allowed to be visible
            // based on
            // keyguard state. This avoids setting this into motion in window manager that is
            // later cancelled due to later calls to ensure visible activities that set
            // visibility back to false.
            r.setVisibility(true);
        }

        final int applicationInfoUid =
                (r.info.applicationInfo != null) ? r.info.applicationInfo.uid : -1;
        if ((r.userId != app.userId) || (r.appInfo.uid != applicationInfoUid)) {
            Slog.wtf(TAG,
                    "User ID for activity changing for " + r
                            + " appInfo.uid=" + r.appInfo.uid
                            + " info.ai.uid=" + applicationInfoUid
                            + " old=" + r.app + " new=" + app);
        }

        app.waitingToKill = null;
        r.launchCount++;
        r.lastLaunchTime = SystemClock.uptimeMillis();

        if (DEBUG_ALL) Slog.v(TAG, "Launching: " + r);

        int idx = app.activities.indexOf(r);
        if (idx < 0) {
            app.activities.add(r);
        }
        mService.updateLruProcessLocked(app, true, null);
        mService.updateOomAdjLocked();

        final LockTaskController lockTaskController = mService.getLockTaskController();
        if (task.mLockTaskAuth == LOCK_TASK_AUTH_LAUNCHABLE
                || task.mLockTaskAuth == LOCK_TASK_AUTH_LAUNCHABLE_PRIV
                || (task.mLockTaskAuth == LOCK_TASK_AUTH_WHITELISTED
                        && lockTaskController.getLockTaskModeState()
                                == LOCK_TASK_MODE_LOCKED)) {
            lockTaskController.startLockTaskMode(task, false, 0 /* blank UID */);
        }

        try {
            if (app.thread == null) {
                throw new RemoteException();
            }
            List<ResultInfo> results = null;
            List<ReferrerIntent> newIntents = null;
            if (andResume) {
                // We don't need to deliver new intents and/or set results if activity is going
                // to pause immediately after launch.
                results = r.results;
                newIntents = r.newIntents;
            }
            if (DEBUG_SWITCH) Slog.v(TAG_SWITCH,
                    "Launching: " + r + " icicle=" + r.icicle + " with results=" + results
                            + " newIntents=" + newIntents + " andResume=" + andResume);
            EventLog.writeEvent(EventLogTags.AM_RESTART_ACTIVITY, r.userId,
                    System.identityHashCode(r), task.taskId, r.shortComponentName);
            if (r.isActivityTypeHome()) {
                // Home process is the root process of the task.
                mService.mHomeProcess = task.mActivities.get(0).app;
            }
            mService.notifyPackageUse(r.intent.getComponent().getPackageName(),
                    PackageManager.NOTIFY_PACKAGE_USE_ACTIVITY);
            r.sleeping = false;
            r.forceNewConfig = false;
            mService.getAppWarningsLocked().onStartActivity(r);
            mService.showAskCompatModeDialogLocked(r);
            r.compat = mService.compatibilityInfoForPackageLocked(r.info.applicationInfo);
            ProfilerInfo profilerInfo = null;
            if (mService.mProfileApp != null && mService.mProfileApp.equals(app.processName)) {
                if (mService.mProfileProc == null || mService.mProfileProc == app) {
                    mService.mProfileProc = app;
                    ProfilerInfo profilerInfoSvc = mService.mProfilerInfo;
                    if (profilerInfoSvc != null && profilerInfoSvc.profileFile != null) {
                        if (profilerInfoSvc.profileFd != null) {
                            try {
                                profilerInfoSvc.profileFd = profilerInfoSvc.profileFd.dup();
                            } catch (IOException e) {
                                profilerInfoSvc.closeFd();
                            }
                        }

                        profilerInfo = new ProfilerInfo(profilerInfoSvc);
                    }
                }
            }

            app.hasShownUi = true;
            app.pendingUiClean = true;
            app.forceProcessStateUpTo(mService.mTopProcessState);
            // Because we could be starting an Activity in the system process this may not go
            // across a Binder interface which would create a new Configuration. Consequently
            // we have to always create a new Configuration here.

            final MergedConfiguration mergedConfiguration = new MergedConfiguration(
                    mService.getGlobalConfiguration(), r.getMergedOverrideConfiguration());
            r.setLastReportedConfiguration(mergedConfiguration);

            logIfTransactionTooLarge(r.intent, r.icicle);


            // Create activity launch transaction.
            final ClientTransaction clientTransaction = ClientTransaction.obtain(app.thread,
                    r.appToken);
            clientTransaction.addCallback(LaunchActivityItem.obtain(new Intent(r.intent),
                    System.identityHashCode(r), r.info,
                    // TODO: Have this take the merged configuration instead of separate global
                    // and override configs.
                    mergedConfiguration.getGlobalConfiguration(),
                    mergedConfiguration.getOverrideConfiguration(), r.compat,
                    r.launchedFromPackage, task.voiceInteractor, app.repProcState, r.icicle,
                    r.persistentState, results, newIntents, mService.isNextTransitionForward(),
                    profilerInfo));

            // Set desired final state.
            final ActivityLifecycleItem lifecycleItem;
            if (andResume) {
                lifecycleItem = ResumeActivityItem.obtain(mService.isNextTransitionForward());
            } else {
                lifecycleItem = PauseActivityItem.obtain();
            }
            clientTransaction.setLifecycleStateRequest(lifecycleItem);

            // Schedule transaction.
            mService.getLifecycleManager().scheduleTransaction(clientTransaction);


            if ((app.info.privateFlags & ApplicationInfo.PRIVATE_FLAG_CANT_SAVE_STATE) != 0
                    && mService.mHasHeavyWeightFeature) {
                // This may be a heavy-weight process!  Note that the package
                // manager will ensure that only activity can run in the main
                // process of the .apk, which is the only thing that will be
                // considered heavy-weight.
                if (app.processName.equals(app.info.packageName)) {
                    if (mService.mHeavyWeightProcess != null
                            && mService.mHeavyWeightProcess != app) {
                        Slog.w(TAG, "Starting new heavy weight process " + app
                                + " when already running "
                                + mService.mHeavyWeightProcess);
                    }
                    mService.mHeavyWeightProcess = app;
                    Message msg = mService.mHandler.obtainMessage(
                            ActivityManagerService.POST_HEAVY_NOTIFICATION_MSG);
                    msg.obj = r;
                    mService.mHandler.sendMessage(msg);
                }
            }

        } catch (RemoteException e) {
            if (r.launchFailed) {
                // This is the second time we failed -- finish activity
                // and give up.
                Slog.e(TAG, "Second failure launching "
                        + r.intent.getComponent().flattenToShortString()
                        + ", giving up", e);
                mService.appDiedLocked(app);
                stack.requestFinishActivityLocked(r.appToken, Activity.RESULT_CANCELED, null,
                        "2nd-crash", false);
                return false;
            }

            // This is the first time we failed -- restart process and
            // retry.
            r.launchFailed = true;
            app.activities.remove(r);
            throw e;
        }
    } finally {
        endDeferResume();
    }

    r.launchFailed = false;
    if (stack.updateLRUListLocked(r)) {
        Slog.w(TAG, "Activity " + r + " being launched, but already in LRU list");
    }

    // TODO(lifecycler): Resume or pause requests are done as part of launch transaction,
    // so updating the state should be done accordingly.
    if (andResume && readyToResume()) {
        // As part of the process of launching, ActivityThread also performs
        // a resume.
        stack.minimalResumeActivityLocked(r);
    } else {
        // This activity is not starting in the resumed state... which should look like we asked
        // it to pause+stop (but remain visible), and it has done so and reported back the
        // current icicle and other state.
        if (DEBUG_STATES) Slog.v(TAG_STATES,
                "Moving to PAUSED: " + r + " (starting in paused state)");
        r.setState(PAUSED, "realStartActivityLocked");
    }

    // Launch the new version setup screen if needed.  We do this -after-
    // launching the initial activity (that is, home), so that it can have
    // a chance to initialize itself while in the background, making the
    // switch back to it faster and look better.
    if (isFocusedStack(stack)) {
        mService.getActivityStartController().startSetupActivity();
    }

    // Update any services we are bound to that might care about whether
    // their client may have activities.
    if (r.app != null) {
        mService.mServices.updateServiceConnectionActivitiesLocked(r.app);
    }

    return true;
}
```

上面又是一段贼长的代码，看的费劲。我们就直接来关注重点：

	// Create activity launch transaction.
	final ClientTransaction clientTransaction = ClientTransaction.obtain(app.thread,
	        r.appToken);
	clientTransaction.addCallback(LaunchActivityItem.obtain(new Intent(r.intent),
	        System.identityHashCode(r), r.info,
	        // TODO: Have this take the merged configuration instead of separate global
	        // and override configs.
	        mergedConfiguration.getGlobalConfiguration(),
	        mergedConfiguration.getOverrideConfiguration(), r.compat,
	        r.launchedFromPackage, task.voiceInteractor, app.repProcState, r.icicle,
	        r.persistentState, results, newIntents, mService.isNextTransitionForward(),
	        profilerInfo));
	
	// Set desired final state.
	final ActivityLifecycleItem lifecycleItem;
	if (andResume) {
	    lifecycleItem = ResumeActivityItem.obtain(mService.isNextTransitionForward());
	} else {
	    lifecycleItem = PauseActivityItem.obtain();
	}
	clientTransaction.setLifecycleStateRequest(lifecycleItem);
	
	// Schedule transaction.
	mService.getLifecycleManager().scheduleTransaction(clientTransaction);
	
1. 首先创建了一个 ClientTransaction ，那什么是 ClientTransaction 呢？ClientTransaction 可以理解为内部包含了一系列的 Activity 生命周期状态的事务，这些事务会最终交给 ActiivtyThread 来处理，然后 ActiivtyThread 再回调相应的 Activity 生命周期。
2. ClientTransaction 添加了 LaunchActivityItem ，LaunchActivityItem 简单的说就是用来启动 Activity 的。
3. 设置了最终的生命周期状态，这里是添加了 ResumeActivityItem 。即 Activity 最终生命周期的状态是 resumed 。
4. 调用 LifecycleManager 来启动 clientTransaction 。mService.getLifecycleManager() 其实是一个 ClientLifecycleManager 对象。

那么就来看看 ClientLifecycleManager.scheduleTransaction 方法

ClientLifecycleManager
====
scheduleTransaction(ClientTransaction transaction)
----
``` java
void scheduleTransaction(ClientTransaction transaction) throws RemoteException {
    final IApplicationThread client = transaction.getClient();
    transaction.schedule();
    if (!(client instanceof Binder)) {
        // If client is not an instance of Binder - it's a remote call and at this point it is
        // safe to recycle the object. All objects used for local calls will be recycled after
        // the transaction is executed on client in ActivityThread.
        transaction.recycle();
    }
}
```
transaction.getClient() 方法主要获取了需要启动 Activity 进程的代理对象 IApplicationThread 

然后内部直接调用了 ClientTransaction 的 schedule 方法。

ClientTransaction
=====
schedule()
----
``` java
public void schedule() throws RemoteException {
    mClient.scheduleTransaction(this);
}
```

schedule 又调用了 mClient.scheduleTransaction 。mClient 就是与 ActivityThread 通讯的代理对象 IApplicationThread ，所以这里其实是调用 ActivityThread 类中 ApplicationThread 内部类	

ApplicationThread
====
scheduleTransaction(ClientTransaction transaction)
----
``` java
@Override
public void scheduleTransaction(ClientTransaction transaction) throws RemoteException {
    ActivityThread.this.scheduleTransaction(transaction);
}
```

直接调用的是 ActivityThread 的 scheduleTransaction 方法。

有些同学可能会纳闷，ActivityThread 里面没有找到 scheduleTransaction 这个方法啊？别着急，我们来看 ActivityThread 是继承 ClientTransactionHandler 的。所以 scheduleTransaction 这个方法在 ClientTransactionHandler 里面。

ClientTransactionHandler
====
scheduleTransaction(ClientTransaction transaction)
----
``` java
void scheduleTransaction(ClientTransaction transaction) {
    transaction.preExecute(this);
    sendMessage(ActivityThread.H.EXECUTE_TRANSACTION, transaction);
}
```

调用了 ActivityThread 的 sendMessage() 方法。

ActivityThread
====
handleMessage(Message msg)
----
``` java
void sendMessage(int what, Object obj) {
    sendMessage(what, obj, 0, 0, false);
}

private void sendMessage(int what, Object obj, int arg1) {
    sendMessage(what, obj, arg1, 0, false);
}

private void sendMessage(int what, Object obj, int arg1, int arg2) {
    sendMessage(what, obj, arg1, arg2, false);
}

private void sendMessage(int what, Object obj, int arg1, int arg2, boolean async) {
    if (DEBUG_MESSAGES) Slog.v(
        TAG, "SCHEDULE " + what + " " + mH.codeToString(what)
        + ": " + arg1 + " / " + obj);
    Message msg = Message.obtain();
    msg.what = what;
    msg.obj = obj;
    msg.arg1 = arg1;
    msg.arg2 = arg2;
    if (async) {
        msg.setAsynchronous(true);
    }
    mH.sendMessage(msg);
}
```

sendMessage 方法其实就是将 ClientTransaction 参数通过 Handler 机制切换至主线程进行处理。这里的 Handler 就是 H 。相应的，sendMessage 之后，我们就要追踪到 H 的 handleMessage 方法中看了。

H
====
handleMessage(Message msg)
----
``` java
public void handleMessage(Message msg) {
    if (DEBUG_MESSAGES) Slog.v(TAG, ">>> handling: " + codeToString(msg.what));
    switch (msg.what) {
    	...
		case EXECUTE_TRANSACTION:
		    final ClientTransaction transaction = (ClientTransaction) msg.obj;
		    mTransactionExecutor.execute(transaction);
		    if (isSystem()) {
		        // Client transactions inside system process are recycled on the client side
		        // instead of ClientLifecycleManager to avoid being cleared before this
		        // message is handled.
		        transaction.recycle();
		    }
		    // TODO(lifecycler): Recycle locally scheduled transactions.
		    break;
    }
    ...
}
```

handleMessage 的代码有点长，我们只关注 EXECUTE_TRANSACTION 部分。

发现利用了 mTransactionExecutor 来处理 transaction 。mTransactionExecutor 其实是 TransactionExecutor 的一个对象，所以要到 TransactionExecutor 中去追踪。

TransactionExecutor
====
execute(ClientTransaction transaction)
----
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

这里主要看这两行代码：

1. executeCallbacks(transaction) 在这里面会去执行 LaunchActivityItem 。也就是会去创建 Activity 并回调 onCreate 生命周期方法；
2. executeLifecycleState(transaction) 在这里会执行 ResumeActivityItem 。之前创建出来的 Activity 的也会回调 onStart 、onResume 生命周期方法。

那我们慢慢看吧，先来看 executeCallbacks(transaction)

executeCallbacks(ClientTransaction transaction)
----
``` java
@VisibleForTesting
public void executeCallbacks(ClientTransaction transaction) {
    final List<ClientTransactionItem> callbacks = transaction.getCallbacks();
    if (callbacks == null) {
        // No callbacks to execute, return early.
        return;
    }
    log("Resolving callbacks");

    final IBinder token = transaction.getActivityToken();
    ActivityClientRecord r = mTransactionHandler.getActivityClient(token);

    // In case when post-execution state of the last callback matches the final state requested
    // for the activity in this transaction, we won't do the last transition here and do it when
    // moving to final state instead (because it may contain additional parameters from server).
    final ActivityLifecycleItem finalStateRequest = transaction.getLifecycleStateRequest();
    final int finalState = finalStateRequest != null ? finalStateRequest.getTargetState()
            : UNDEFINED;
    // Index of the last callback that requests some post-execution state.
    final int lastCallbackRequestingState = lastCallbackRequestingState(transaction);

    final int size = callbacks.size();
    for (int i = 0; i < size; ++i) {
        final ClientTransactionItem item = callbacks.get(i);
        log("Resolving callback: " + item);
        final int postExecutionState = item.getPostExecutionState();
        final int closestPreExecutionState = mHelper.getClosestPreExecutionState(r,
                item.getPostExecutionState());
        if (closestPreExecutionState != UNDEFINED) {
            cycleToPath(r, closestPreExecutionState);
        }

        item.execute(mTransactionHandler, token, mPendingActions);
        item.postExecute(mTransactionHandler, token, mPendingActions);
        if (r == null) {
            // Launch activity request will create an activity record.
            r = mTransactionHandler.getActivityClient(token);
        }

        if (postExecutionState != UNDEFINED && r != null) {
            // Skip the very last transition and perform it by explicit state request instead.
            final boolean shouldExcludeLastTransition =
                    i == lastCallbackRequestingState && finalState == postExecutionState;
            cycleToPath(r, postExecutionState, shouldExcludeLastTransition);
        }
    }
}
```

上面代码主要干的事情就是先获取 ClientTransaction 中的 ClientTransactionItem 对象。然后调用 ClientTransactionItem.execute 。

别看上面代码一大串的，其实最重要的就是 `item.execute(mTransactionHandler, token, mPendingActions);` 这句代码了。

说明了 TransactionExecutor 内部是交给每个 item 来处理对应的操作的。而上面分析可以知道，这个 item 就是 LaunchActivityItem 。

LaunchActivityItem
====
execute(ClientTransactionHandler client, IBinder token, PendingTransactionActions pendingActions)
----
``` java
@Override
public void execute(ClientTransactionHandler client, IBinder token,
        PendingTransactionActions pendingActions) {
    Trace.traceBegin(TRACE_TAG_ACTIVITY_MANAGER, "activityStart");
    ActivityClientRecord r = new ActivityClientRecord(token, mIntent, mIdent, mInfo,
            mOverrideConfig, mCompatInfo, mReferrer, mVoiceInteractor, mState, mPersistentState,
            mPendingResults, mPendingNewIntents, mIsForward,
            mProfilerInfo, client);
    client.handleLaunchActivity(r, pendingActions, null /* customIntent */);
    Trace.traceEnd(TRACE_TAG_ACTIVITY_MANAGER);
}
```

在 LaunchActivityItem 的 execute 内部，调用了 client.handleLaunchActivity 来执行。而 client 就是 ActivityThread 。所以我们要继续追踪到 ActivityThread.handleLaunchActivity 。

ActivityThread
====
handleLaunchActivity(ActivityClientRecord r, PendingTransactionActions pendingActions, Intent customIntent)
----
``` java
@Override
public Activity handleLaunchActivity(ActivityClientRecord r,
        PendingTransactionActions pendingActions, Intent customIntent) {
    // If we are getting ready to gc after going to the background, well
    // we are back active so skip it.
    unscheduleGcIdler();
    mSomeActivitiesChanged = true;

    if (r.profilerInfo != null) {
        mProfiler.setProfiler(r.profilerInfo);
        mProfiler.startProfiling();
    }

    // Make sure we are running with the most recent config.
    handleConfigurationChanged(null, null);

    if (localLOGV) Slog.v(
        TAG, "Handling launch of " + r);

    // Initialize before creating the activity
    if (!ThreadedRenderer.sRendererDisabled) {
        GraphicsEnvironment.earlyInitEGL();
    }
    WindowManagerGlobal.initialize();

    final Activity a = performLaunchActivity(r, customIntent);

    if (a != null) {
        r.createdConfig = new Configuration(mConfiguration);
        reportSizeConfigurations(r);
        if (!r.activity.mFinished && pendingActions != null) {
            pendingActions.setOldState(r.state);
            pendingActions.setRestoreInstanceState(true);
            pendingActions.setCallOnPostCreate(true);
        }
    } else {
        // If there was an error, for any reason, tell the activity manager to stop us.
        try {
            ActivityManager.getService()
                    .finishActivity(r.token, Activity.RESULT_CANCELED, null,
                            Activity.DONT_FINISH_TASK_WITH_ACTIVITY);
        } catch (RemoteException ex) {
            throw ex.rethrowFromSystemServer();
        }
    }

    return a;
}
```

看来一句关键的代码

    final Activity a = performLaunchActivity(r, customIntent);

最终可以看到，Activity 就是 performLaunchActivity 方法创建出来的。

performLaunchActivity(ActivityClientRecord r, Intent customIntent) 
----
``` java
/**  Core implementation of activity launch. */
private Activity performLaunchActivity(ActivityClientRecord r, Intent customIntent) {
    ActivityInfo aInfo = r.activityInfo;
    if (r.packageInfo == null) {
        r.packageInfo = getPackageInfo(aInfo.applicationInfo, r.compatInfo,
                Context.CONTEXT_INCLUDE_CODE);
    }

    ComponentName component = r.intent.getComponent();
    if (component == null) {
        component = r.intent.resolveActivity(
            mInitialApplication.getPackageManager());
        r.intent.setComponent(component);
    }

    if (r.activityInfo.targetActivity != null) {
        component = new ComponentName(r.activityInfo.packageName,
                r.activityInfo.targetActivity);
    }

    ContextImpl appContext = createBaseContextForActivity(r);
    Activity activity = null;
    try {
        java.lang.ClassLoader cl = appContext.getClassLoader();
        activity = mInstrumentation.newActivity(
                cl, component.getClassName(), r.intent);
        StrictMode.incrementExpectedActivityCount(activity.getClass());
        r.intent.setExtrasClassLoader(cl);
        r.intent.prepareToEnterProcess();
        if (r.state != null) {
            r.state.setClassLoader(cl);
        }
    } catch (Exception e) {
        if (!mInstrumentation.onException(activity, e)) {
            throw new RuntimeException(
                "Unable to instantiate activity " + component
                + ": " + e.toString(), e);
        }
    }

    try {
        Application app = r.packageInfo.makeApplication(false, mInstrumentation);

        if (localLOGV) Slog.v(TAG, "Performing launch of " + r);
        if (localLOGV) Slog.v(
                TAG, r + ": app=" + app
                + ", appName=" + app.getPackageName()
                + ", pkg=" + r.packageInfo.getPackageName()
                + ", comp=" + r.intent.getComponent().toShortString()
                + ", dir=" + r.packageInfo.getAppDir());

        if (activity != null) {
            CharSequence title = r.activityInfo.loadLabel(appContext.getPackageManager());
            Configuration config = new Configuration(mCompatConfiguration);
            if (r.overrideConfig != null) {
                config.updateFrom(r.overrideConfig);
            }
            if (DEBUG_CONFIGURATION) Slog.v(TAG, "Launching activity "
                    + r.activityInfo.name + " with config " + config);
            Window window = null;
            if (r.mPendingRemoveWindow != null && r.mPreserveWindow) {
                window = r.mPendingRemoveWindow;
                r.mPendingRemoveWindow = null;
                r.mPendingRemoveWindowManager = null;
            }
            appContext.setOuterContext(activity);
            activity.attach(appContext, this, getInstrumentation(), r.token,
                    r.ident, app, r.intent, r.activityInfo, title, r.parent,
                    r.embeddedID, r.lastNonConfigurationInstances, config,
                    r.referrer, r.voiceInteractor, window, r.configCallback);

            if (customIntent != null) {
                activity.mIntent = customIntent;
            }
            r.lastNonConfigurationInstances = null;
            checkAndBlockForNetworkAccess();
            activity.mStartedActivity = false;
            int theme = r.activityInfo.getThemeResource();
            if (theme != 0) {
                activity.setTheme(theme);
            }

            activity.mCalled = false;
            if (r.isPersistable()) {
                mInstrumentation.callActivityOnCreate(activity, r.state, r.persistentState);
            } else {
                mInstrumentation.callActivityOnCreate(activity, r.state);
            }
            if (!activity.mCalled) {
                throw new SuperNotCalledException(
                    "Activity " + r.intent.getComponent().toShortString() +
                    " did not call through to super.onCreate()");
            }
            r.activity = activity;
        }
        r.setState(ON_CREATE);

        mActivities.put(r.token, r);

    } catch (SuperNotCalledException e) {
        throw e;

    } catch (Exception e) {
        if (!mInstrumentation.onException(activity, e)) {
            throw new RuntimeException(
                "Unable to start activity " + component
                + ": " + e.toString(), e);
        }
    }

    return activity;
}
```

我们把上面这段代码整理一下（以下总结来自《Android开发艺术探索》）：

1. 从 ActivityClientRecord 中获取待启动的 Activity 的组件信息；
2. 通过 Instrumentation 的 newActivity 方法使用类加载器创建 Activity 对象；
3. 通过 LoadedApk 的 makeApplication 方法来尝试创建 Application 对象；
4. 创建 ContextImpl 对象并通过 Activity 的 attach 方法来完成一些重要数据的初始化；
5. 调用 Activity 的 onCreate 方法

在这里，1-4点详细的分析就不展开了，我们重点来看一下第5小点。

即

	if (r.isPersistable()) {
	 	mInstrumentation.callActivityOnCreate(activity, r.state, r.persistentState);
	} else {
	 	mInstrumentation.callActivityOnCreate(activity, r.state);
	}

Instrumentation
====
callActivityOnCreate
----
``` java
public void callActivityOnCreate(Activity activity, Bundle icicle) {
    prePerformCreate(activity);
    activity.performCreate(icicle);
    postPerformCreate(activity);
}
```

到这，有一种豁然开朗的感觉。原来 Activity 的生命周期调用都是通过 Instrumentation 调用来完成的。

Activity
====
performCreate(Bundle icicle)
----
``` java
final void performCreate(Bundle icicle) {
    performCreate(icicle, null);
}

final void performCreate(Bundle icicle, PersistableBundle persistentState) {
    mCanEnterPictureInPicture = true;
    restoreHasCurrentPermissionRequest(icicle);
    // 回调 activity 的 onCreate 生命周期
    if (persistentState != null) {
        onCreate(icicle, persistentState);
    } else {
        onCreate(icicle);
    }
    writeEventLog(LOG_AM_ON_CREATE_CALLED, "performCreate");
    mActivityTransitionState.readState(icicle);

    mVisibleFromClient = !mWindow.getWindowStyle().getBoolean(
            com.android.internal.R.styleable.Window_windowNoDisplay, false);
    mFragments.dispatchActivityCreated();
    mActivityTransitionState.setEnterActivityOptions(this, getActivityOptions());
}
```

源码分析到这里，基本上把 Activity 创建和回调 onCreate 生命周期讲完了。

前面还留了一个坑，就是 Activity 是怎么回调 onStart 和 onResume 的呢？我们只能到下一篇再讲了。

bye bye


title: View事件分发机制源码解析
date: 2017-10-31 21:06:30
categories: Android Blog
tags: [Android,事件分发,源码解析]
---
注：本文解析的源码基于 API 25，部分内容来自于《Android开发艺术探索》。

Header
======
Android View 事件分发的机制可以说是 Android 开发者必知点之一，一般在面试的过程中肯定也有涉及。之前重新梳理了一下 View 事件的分发，所以为了有所记录，下定决心要写一篇关于 View 事件分发的博客。

虽然很早之前也写了一篇关于事件分发的博客[《Android onTouch事件传递机制解析》](http://yuqirong.me/2015/10/29/Android%20onTouch%E4%BA%8B%E4%BB%B6%E4%BC%A0%E9%80%92%E6%9C%BA%E5%88%B6%E8%A7%A3%E6%9E%90/)，但是在这篇中分析不够全面，Activity 和 ViewGroup 没有涉及到。那么就来“再续前缘”吧。

事件分发可以说分为三个部分，

* 一个是 Activity 
* 然后是 ViewGroup
* 最后是 View

我们在分析事件分发时，也会依次按照这三个部分来入手。

因为最后的 View 部分在之前已经分析过了（也就是[《Android onTouch事件传递机制解析》](http://yuqirong.me/2015/10/29/Android%20onTouch%E4%BA%8B%E4%BB%B6%E4%BC%A0%E9%80%92%E6%9C%BA%E5%88%B6%E8%A7%A3%E6%9E%90/)），所以今天的内容里关于 View 部分的就不再讲了，大家可以自己去这篇博客中接着看下去。

好咯，下面就是我们的 show time ！

Activity
========
先入手第一部分：Activity 。

Activity
--------
### dispatchTouchEvent(MotionEvent ev)

在 Activity 的扎堆代码中，我们先从 `dispatchTouchEvent(MotionEvent ev)` 看起。

``` java
    public boolean dispatchTouchEvent(MotionEvent ev) {
        // 如果是 down 事件，回调 onUserInteraction()
        // onUserInteraction 是空方法，可以用来判断用户是否正在和设备交互
        // 当用户触摸屏幕或是点击按键都会回调此方法
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            onUserInteraction();
        }
        if (getWindow().superDispatchTouchEvent(ev)) {
            return true;
        }
        // 如果 getWindow().superDispatchTouchEvent 返回 false 的话就交给 onTouchEvent 处理
        return onTouchEvent(ev);
    }
```

Activity 的 `dispatchTouchEvent(MotionEvent ev)` 方法代码很短，我们先跟着 `getWindow().superDispatchTouchEvent(ev)` 去走。如果 `getWindow().superDispatchTouchEvent(ev)` 返回了 true ，那就代表着事件有 View 去响应处理了；否则返回 false 的话，就说明没有 View 处理，那么就转回来交给了 Activity 来处理，也就是 `onTouchEvent(ev)` 方法。

### onTouchEvent(MotionEvent event)

在 Activity 的 `onTouchEvent(ev)` 方法中会去判断该触摸事件的坐标是否在 Window 范围之外，如果在范围之外就关闭该 Activity （注意：Window 设置了 mCloseOnTouchOutside 为 true 的情况下）并且返回 true；否则返回 false 。具体代码如下：

``` java
    public boolean onTouchEvent(MotionEvent event) {
        if (mWindow.shouldCloseOnTouch(this, event)) {
            finish();
            return true;
        }

        return false;
    }
```

好了，现在回过头来讲讲有 View 去处理事件的情形。

我们都知道 getWindow() 实际上是得到了当前 Activity 的 Window 对象，而 Window 的具体实现是 PhoneWindow 。所以我们接着要去看 PhoneWindow 的 `superDispatchTouchEvent(ev)` 方法。

PhoneWindow
-----------
### superDispatchTouchEvent(MotionEvent event)

``` java
    @Override
    public boolean superDispatchTouchEvent(MotionEvent event) {
        return mDecor.superDispatchTouchEvent(event);
    }
```

在 PhoneWindow 中直接把事件交给了 mDecor 来处理，而 mDecor 正是 Window 中持有的 DecorView 对象。在这里，也代表着事件从 Activity 传给了 ViewGroup 。

接着跟下去。


DecorView
---------
### superDispatchTouchEvent(MotionEvent event)

``` java
    public boolean superDispatchTouchEvent(MotionEvent event) {
        return super.dispatchTouchEvent(event);
    }
```

DecorView 的 `superDispatchTouchEvent(MotionEvent event)` 方法代码也很短了[/笑哭]，直接调用了父类的 `dispatchTouchEvent(event)` 方法。DecorView 是继承了 FrameLayout 的。而在 FrameLayout 中并没有去重写 `dispatchTouchEvent(event)` 。所以我们要去看 ViewGroup 的 `dispatchTouchEvent(event)` 方法了。

至此为止，我们第一部分关于 Activity 的事件分发已经讲完了。接下去的就是第二部分关于 ViewGroup 的了。

ViewGroup
=========
第二部分，ViewGroup 。

ViewGroup
---------
上面讲到了我们要去 ViewGroup 中看 `dispatchTouchEvent(event)` 方法。

### dispatchTouchEvent(event)

`dispatchTouchEvent(event)` 方法挺长的，在这里我们就把它分段进行分析，这样也更加容易理解。

``` java
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (mInputEventConsistencyVerifier != null) {
            mInputEventConsistencyVerifier.onTouchEvent(ev, 1);
        }
                             
        // If the event targets the accessibility focused view and this is it, start
        // normal event dispatch. Maybe a descendant is what will handle the click.
        if (ev.isTargetAccessibilityFocus() && isAccessibilityFocusedViewOrHost()) {
            ev.setTargetAccessibilityFocus(false);
        }
        
        // 返回值，代表着该View是否处理事件
        boolean handled = false;
        // 判断当前 window 是否有被遮挡，若返回 false 则丢弃这个事件
        if (onFilterTouchEventForSecurity(ev)) {
            final int action = ev.getAction();
            final int actionMasked = action & MotionEvent.ACTION_MASK;

            // 如果是 ACTION_DOWN 事件，那么需要恢复初始状态以及 mFirstTouchTarget 置空等
            if (actionMasked == MotionEvent.ACTION_DOWN) {
                // Throw away all previous state when starting a new touch gesture.
                // The framework may have dropped the up or cancel event for the previous gesture
                // due to an app switch, ANR, or some other state change.
                cancelAndClearTouchTargets(ev);
                // 在 resetTouchState 会对 FLAG_DISALLOW_INTERCEPT 重置
                resetTouchState();
            }

         ...

    }
```

在 `dispatchTouchEvent(event)` 方法的一开始，首先以 Window 是否被遮挡来过滤掉一些不必要的事件。之后若是手指按下的 ACTION\_DOWN 事件的话，做一些状态清除等工作，比如 mFirstTouchTarget = null 。 当 ViewGroup 的子元素成功处理了事件后，mFirstTouchTarget 就会被赋值并指向了子元素。因此当触摸事件为 ACTION\_DOWN 时，说明这是一轮新的事件，还不知道哪个 View 可以处理该事件，所以 mFirstTouchTarget 会被置为 null 了。

这一小段代码理解后，我们再接着往下看。

``` java
            // ----------第 1 小点----------
            // 检查是否需要拦截事件
            final boolean intercepted;
            if (actionMasked == MotionEvent.ACTION_DOWN
                    || mFirstTouchTarget != null) {

                // ----------第 2 小点----------
                // disallowIntercept 代表着子View是否禁止让父ViewGroup拦截事件
                final boolean disallowIntercept = (mGroupFlags & FLAG_DISALLOW_INTERCEPT) != 0;
                // 如果不禁止，就调用 onInterceptTouchEvent 
                if (!disallowIntercept) {
                    intercepted = onInterceptTouchEvent(ev);
                    ev.setAction(action); // restore action in case it was changed
                } else {
                    intercepted = false;
                }
            } else {
                // There are no touch targets and this action is not an initial down
                // so this view group continues to intercept touches.

                // ----------第 3 小点----------
                // 如果 mFirstTouchTarget 等于 null ，则说明子 view 中都不处理事件，并且不是 ACTION_DOWN 事件，当前 viewgroup 默认拦截该事件
                intercepted = true;
            }

            if (intercepted || mFirstTouchTarget != null) {
                ev.setTargetAccessibilityFocus(false);
            }
```

这里判断了 ViewGroup 是否要去拦截事件。根据判断 `actionMasked == MotionEvent.ACTION_DOWN || mFirstTouchTarget != null` ，我们可以知道一点，当 `mFirstTouchTarget` 为 null 时，就说明了子 View 中都不会处理这轮事件了，那么就应该把该事件直接交回给 ViewGroup （此时的事件肯定是 ACTION\_MOVE 或者 ACTION\_UP 了）。所以直接设置了 `intercepted = true` ，也就是上面代码中的第 3 小点。

反之，若为 ACTION_DOWN 事件，那么说明是一轮全新的事件。是需要去问问子 View 到底要不要处理事件的。同理，mFirstTouchTarget != null 的话肯定是找到子 View 来处理事件了，所以也不能马上判断是否拦截，需要继续深入。也就是上面的第 1 小点。

剩下最后一个第 2 小点。

之后先得到 disallowIntercept ，disallowIntercept 与 FLAG\_DISALLOW\_INTERCEPT 有关。而 FLAG\_DISALLOW\_INTERCEPT 又是通过 `requestDisallowInterceptTouchEvent` 方法来设置的。如果 disallowIntercept 是 true ，说明子 View 禁止让父 ViewGroup 拦截事件。那就直接设置 `intercepted = false` 。这里要注意下，当为 ACTION\_DOWN 事件时，会重置 FLAG\_DISALLOW\_INTERCEPT 标记位，所以 ACTION\_DOWN 事件在 ViewGroup 的 `onInterceptTouchEvent` 方法中一定会询问自己是否需要拦截，而 ACTION\_MOVE 和 ACTION\_UP 则不一定。

反之，若 disallowIntercept 为 false 的话，那么 ViewGroup 会调用 `onInterceptTouchEvent(ev)` 方法来判断自己是否要去拦截，开发者可以去重写这个方法来达到一些拦截的目的，该方法默认返回 false ，也就是不拦截。

趁热，接着撸。

``` java
            // 检查下是否是 ACTION_CANCEL 事件
            final boolean canceled = resetCancelNextUpFlag(this)
                    || actionMasked == MotionEvent.ACTION_CANCEL;

            // 是否分发给多个子 View ，默认是 false
            final boolean split = (mGroupFlags & FLAG_SPLIT_MOTION_EVENTS) != 0;
            TouchTarget newTouchTarget = null;
            boolean alreadyDispatchedToNewTouchTarget = false;
            // 不被 ViewGroup 拦截并且不是 ACTION_CANCEL 事件
            if (!canceled && !intercepted) {

                View childWithAccessibilityFocus = ev.isTargetAccessibilityFocus()
                        ? findChildWithAccessibilityFocus() : null;

                // 当为 ACTION_DOWN 等事件时，要去寻找可以处理的子View，然后下发
                if (actionMasked == MotionEvent.ACTION_DOWN
                        || (split && actionMasked == MotionEvent.ACTION_POINTER_DOWN)
                        || actionMasked == MotionEvent.ACTION_HOVER_MOVE) {
                    final int actionIndex = ev.getActionIndex(); // always 0 for down
                    final int idBitsToAssign = split ? 1 << ev.getPointerId(actionIndex)
                            : TouchTarget.ALL_POINTER_IDS;

                    // Clean up earlier touch targets for this pointer id in case they
                    // have become out of sync.
                    removePointersFromTouchTargets(idBitsToAssign);

                    final int childrenCount = mChildrenCount;
                    if (newTouchTarget == null && childrenCount != 0) {
                        final float x = ev.getX(actionIndex);
                        final float y = ev.getY(actionIndex);

                        // 获取 z 轴上从大到小排序的子 view 顺序
                        final ArrayList<View> preorderedList = buildTouchDispatchChildList();
                        final boolean customOrder = preorderedList == null
                                && isChildrenDrawingOrderEnabled();
                        final View[] children = mChildren;
                        // 开始遍历子 view
                        for (int i = childrenCount - 1; i >= 0; i--) {
                            // 确认子 View 的下标
                            final int childIndex = getAndVerifyPreorderedIndex(
                                    childrenCount, i, customOrder);
                            // 根据下标，得到子View
                            final View child = getAndVerifyPreorderedView(
                                    preorderedList, children, childIndex);

                            // 如果当前 view 没有焦点，那么跳过
                            if (childWithAccessibilityFocus != null) {
                                if (childWithAccessibilityFocus != child) {
                                    continue;
                                }
                                childWithAccessibilityFocus = null;
                                i = childrenCount - 1;
                            }
                            
                            // 点击是否落在子view范围内和子view是否正在动画
                            if (!canViewReceivePointerEvents(child)
                                    || !isTransformedTouchPointInView(x, y, child, null)) {
                                ev.setTargetAccessibilityFocus(false);
                                continue;
                            }

                            newTouchTarget = getTouchTarget(child);
                            if (newTouchTarget != null) {
                                // Child is already receiving touch within its bounds.
                                // Give it the new pointer in addition to the ones it is handling.
                                newTouchTarget.pointerIdBits |= idBitsToAssign;
                                break;
                            }

                            resetCancelNextUpFlag(child);
                            // 分发给该子 View 的 dispatchTouchEvent 方法
                            if (dispatchTransformedTouchEvent(ev, false, child, idBitsToAssign)) {
                                // Child wants to receive touch within its bounds.
                                mLastTouchDownTime = ev.getDownTime();
                                if (preorderedList != null) {
                                    // childIndex points into presorted list, find original index
                                    for (int j = 0; j < childrenCount; j++) {
                                        if (children[childIndex] == mChildren[j]) {
                                            mLastTouchDownIndex = j;
                                            break;
                                        }
                                    }
                                } else {
                                    mLastTouchDownIndex = childIndex;
                                }
                                mLastTouchDownX = ev.getX();
                                mLastTouchDownY = ev.getY();
                                //给 mFirstTouchTarget 赋值，该事件已经交给子 View 处理了
                                newTouchTarget = addTouchTarget(child, idBitsToAssign);
                                alreadyDispatchedToNewTouchTarget = true;
                                break;
                            }

                            // The accessibility focus didn't handle the event, so clear
                            // the flag and do a normal dispatch to all children.
                            ev.setTargetAccessibilityFocus(false);
                        }
                        if (preorderedList != null) preorderedList.clear();
                    }

                    if (newTouchTarget == null && mFirstTouchTarget != null) {
                        // Did not find a child to receive the event.
                        // Assign the pointer to the least recently added target.
                        newTouchTarget = mFirstTouchTarget;
                        while (newTouchTarget.next != null) {
                            newTouchTarget = newTouchTarget.next;
                        }
                        newTouchTarget.pointerIdBits |= idBitsToAssign;
                    }
                }
            }
```

这段代码较长，基本上的逻辑就是为 ACTION_DOWN 等事件找一个可以处理的子 View 。

先遍历了所有的子 View ，会根据点击坐标是否落在子view范围内以及子view是否正在动画来判断是否接收事件。

如果找到了一个子 View 可以接收事件，那么就会调用它的 `dispatchTouchEvent` 方法。若 `dispatchTouchEvent` 方法返回 true 的话，说明该 View 确认处理该事件了，那么之后给 mFirstTouchTarget 赋值；否则就继续遍历重复之前的流程了。

三言两语就概括了这段代码的逻辑。

再来看最后一段代码。

``` java
            // Dispatch to touch targets.
            // 如果 mFirstTouchTarget 为空，那么有可能没有子 View 或者所有的子 View 都不处理该事件了
            if (mFirstTouchTarget == null) {
                // No touch targets so treat this as an ordinary view.
                // 交给自己处理，调用 ViewGroup 的 super.dispatchTouchEvent 方法
                handled = dispatchTransformedTouchEvent(ev, canceled, null,
                        TouchTarget.ALL_POINTER_IDS);
            } else {
                //处理有 mFirstTouchTarget 并且除了 ACTION_DOWN 以外的事件
                // Dispatch to touch targets, excluding the new touch target if we already
                // dispatched to it.  Cancel touch targets if necessary.
                TouchTarget predecessor = null;
                TouchTarget target = mFirstTouchTarget;
                while (target != null) {
                    final TouchTarget next = target.next;
                    // alreadyDispatchedToNewTouchTarget 为 true 就说明了 mFirstTouchTarget 被赋值了
                    // 所以事件已经交给子 View 处理了，这里就返回 true 
                    if (alreadyDispatchedToNewTouchTarget && target == newTouchTarget) {
                        handled = true;
                    } else {
                        // cancelChild 为 true 的话就说明接下来的事件被 ViewGroup 拦截了，需要传递 ACTION_CANCEL 事件
                        final boolean cancelChild = resetCancelNextUpFlag(target.child)
                                || intercepted;
                        // 传递 ACTION_CANCEL 事件给子 View
                        if (dispatchTransformedTouchEvent(ev, cancelChild,
                                target.child, target.pointerIdBits)) {
                            handled = true;
                        }
                        // 释放 mFirstTouchTarget ，之后事件就交给了 ViewGroup 自己处理了
                        if (cancelChild) {
                            if (predecessor == null) {
                                mFirstTouchTarget = next;
                            } else {
                                predecessor.next = next;
                            }
                            target.recycle();
                            target = next;
                            continue;
                        }
                    }
                    predecessor = target;
                    target = next;
                }
            }

            // Update list of touch targets for pointer up or cancel, if needed.
            // 当为 ACTION_CANCEL 和 ACTION_UP 等事件的一些重置状态
            if (canceled
                    || actionMasked == MotionEvent.ACTION_UP
                    || actionMasked == MotionEvent.ACTION_HOVER_MOVE) {
                resetTouchState();
            } else if (split && actionMasked == MotionEvent.ACTION_POINTER_UP) {
                final int actionIndex = ev.getActionIndex();
                final int idBitsToRemove = 1 << ev.getPointerId(actionIndex);
                removePointersFromTouchTargets(idBitsToRemove);
            }
        }

        if (!handled && mInputEventConsistencyVerifier != null) {
            mInputEventConsistencyVerifier.onUnhandledEvent(ev, 1);
        }
        // 返回是否处理的 boolean 值
        return handled;
    }
```

一开头，判断了 `mFirstTouchTarget == null` 。如果是空的话，就代表着可能 ViewGroup 中没有子 View ，或者所有的子 View 都不打算处理这轮的事件。那么只能交给 ViewGroup 自己处理了。之后调用了 `dispatchTransformedTouchEvent(ev, canceled, null, TouchTarget.ALL_POINTER_IDS)` 方法。

细心的同学已经发现，这个事件分发给子 View 调用的是同一个方法。不同的是，分发给子 View 的是 `dispatchTransformedTouchEvent(ev, canceled, child, TouchTarget.ALL_POINTER_IDS)` 。也就是说，第三个参数一个是 null ，而另一个是 child 。其实，在 `dispatchTransformedTouchEvent` 内部的逻辑大概是这样的，省略了其他代码：

``` java
    if (child == null) {
        handled = super.dispatchTouchEvent(event);
    } else {
        handled = child.dispatchTouchEvent(event);
    }
```

所以，当传入是 null 的话，调用的直接是 `super.dispatchTouchEvent(event)` ，也就是 View 类的 `dispatchTouchEvent` 方法了。

再来看 `mFirstTouchTarget != null` 的情况。

mFirstTouchTarget 不为空的话，代码里处理的都是除了 ACTION\_DOWN 的事件，也就是 ACTION\_MOVE 和 ACTION\_UP 事件。若 alreadyDispatchedToNewTouchTarget 为 true ，那么这正是上面给 mFirstTouchTarget 赋值时留下来的“锅”，直接返回 handled = true 即可。

否则就直接将事件分发给子 View 了。这里注意下，若 cancelChild 为 true 的话，就代表着事件被 ViewGroup 拦截了，所以分发给子 View 的将是 ACTION\_CANCEL 事件，之后把 mFirstTouchTarget 置空了。那么之后事件再过来，调用的就是 ViewGroup 的 `super.dispatchTouchEvent(event)` ，就完成了把事件分发给 ViewGroup 了。

这样，以后的事件就完全移交给 ViewGroup 了，没子 View 什么事了。

最后就是对 ACTION\_CANCEL 和 ACTION\_UP 事件的一些状态重置。

在这，基本上把 ViewGroup 这部分讲完了。

View
====
View 部分的事件分发就参考一下[《Android onTouch事件传递机制解析》](http://yuqirong.me/2015/10/29/Android%20onTouch%E4%BA%8B%E4%BB%B6%E4%BC%A0%E9%80%92%E6%9C%BA%E5%88%B6%E8%A7%A3%E6%9E%90/)，这里面讲的还是挺清楚的，很早以前写的，不多讲了。

Footer
======
今天的内容都讲的差不多了，也把事件分发的机制又整理了一遍。当然也有一些不完善的地方，比如事件是怎样传递给 Activity 的在本文中没有涉及到，想了解的同学可以看下这篇[《Android中MotionEvent的来源和ViewRootImpl》](http://blog.csdn.net/singwhatiwanna/article/details/50775201)，任大神的作品。

好了，要说再见了。如果有问题的同学可以在下面留言。

Goodbye ...

References
==========
* [Android中MotionEvent的来源和ViewRootImpl](http://blog.csdn.net/singwhatiwanna/article/details/50775201)
* [Android 事件分发机制源码攻略（二） —— ViewGroup篇](http://blog.csdn.net/u013927241/article/details/77919424)
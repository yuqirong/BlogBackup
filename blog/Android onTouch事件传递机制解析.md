title: Android onTouch事件传递机制解析
date: 2015-10-29 22:56:09
categories: Android Blog
tags: [Android,源码解析]
---
记得刚开始学习Android的时候，对于onTouch相关的事件一头雾水。分不清onTouch()，onTouchEvent()和OnClick()之间的关系和先后顺序，觉得有必要搞清onTouch事件传递的原理。经过一段时间的琢磨以及网上相关博客的介绍，总算是了解了触摸事件传递的机制了，顺便写一篇博客来记录一下。下面就让我们来看看吧。

大家都知道一般我们使用的UI控件都是继承自共同的父类——View。所以View这个类应该掌管着onTouch事件的相关处理。那就让我们去看看：在View中寻找Touch相关的方法，其中一个很容易地引起了我们的注意：dispatchTouchEvent(MotionEvent event)。根据方法名的意思应该是负责分发触摸事件的，下面给出了源码：

``` java	
/**
 * Pass the touch screen motion event down to the target view, or this
 * view if it is the target.
 *
 * @param event The motion event to be dispatched.
 * @return True if the event was handled by the view, false otherwise.
 */
 public boolean dispatchTouchEvent(MotionEvent event) {
    // If the event should be handled by accessibility focus first.
    if (event.isTargetAccessibilityFocus()) {
        // We don't have focus or no virtual descendant has it, do not handle the event.
        if (!isAccessibilityFocusedViewOrHost()) {
            return false;
        }
        // We have focus and got the event, then use normal event dispatch.
        event.setTargetAccessibilityFocus(false);
    }

    boolean result = false;

    if (mInputEventConsistencyVerifier != null) {
        mInputEventConsistencyVerifier.onTouchEvent(event, 0);
    }

    final int actionMasked = event.getActionMasked();
    if (actionMasked == MotionEvent.ACTION_DOWN) {
        // Defensive cleanup for new gesture
        stopNestedScroll();
    }

    if (onFilterTouchEventForSecurity(event)) {
        //noinspection SimplifiableIfStatement
        ListenerInfo li = mListenerInfo;
        if (li != null && li.mOnTouchListener != null
                && (mViewFlags & ENABLED_MASK) == ENABLED
                && li.mOnTouchListener.onTouch(this, event)) {
            result = true;
        }

        if (!result && onTouchEvent(event)) {
            result = true;
        }
    }

    if (!result && mInputEventConsistencyVerifier != null) {
        mInputEventConsistencyVerifier.onUnhandledEvent(event, 0);
    }

    // Clean up after nested scrolls if this is the end of a gesture;
    // also cancel it if we tried an ACTION_DOWN but we didn't want the rest
    // of the gesture.
    if (actionMasked == MotionEvent.ACTION_UP ||
            actionMasked == MotionEvent.ACTION_CANCEL ||
            (actionMasked == MotionEvent.ACTION_DOWN && !result)) {
        stopNestedScroll();
    }

    return result;
}
```

源码有点长，但我们不必每一行都看。首先注意到dispatchTouchEvent的返回值是boolean类型的，注释上的解释：`@return True if the event was handled by the view, false otherwise.`也就是说如果该触摸事件被这个View消费了就返回true，否则返回false。在方法中首先判断了该event是否是否得到了焦点，如果没有得到焦点直接返回false。然后让我们把目光转向`if (li != null && li.mOnTouchListener != null&& (mViewFlags & ENABLED_MASK) == ENABLED&& li.mOnTouchListener.onTouch(this, event))`这个片段，看到这里有一个名为li的局部变量，属于 ListenerInfo 类，经 mListenerInfo 赋值得到。ListenerInfo只是一个包装类，里面封装了大量的监听器。再在 View 类中去寻找 mListenerInfo ，可以看到下面的代码：
	
``` java
ListenerInfo getListenerInfo() {
    if (mListenerInfo != null) {
        return mListenerInfo;
    }
    mListenerInfo = new ListenerInfo();
    return mListenerInfo;
}
```

因此我们可以知道mListenerInfo是不为空的，所以li也不是空，第一个判断为true，然后看到li.mOnTouchListener，前面说过ListenerInfo是一个监听器的封装类，所以我们同样去追踪mOnTouchListener：

``` java
/**
 * Register a callback to be invoked when a touch event is sent to this view.
 * @param l the touch listener to attach to this view
 */
public void setOnTouchListener(OnTouchListener l) {
    getListenerInfo().mOnTouchListener = l;
}
```

正是通过上面的方法来设置 mOnTouchListener 的，我想上面的方法大家肯定都很熟悉吧，正是我们平时经常用的 xxx.setOnTouchListener ，好了我们从中得知如果设置了OnTouchListener则第二个判断也为true，第三个判断为如果该View是否为enable，默认都是enable的，所以同样为true。还剩最后一个：`li.mOnTouchListener.onTouch(this, event)`，显然是回调了第二个判断中监听器的onTouch()方法，如果onTouch()方法返回true,则上面四个判断全部为true,dispatchTouchEvent()方法会返回true，并且不会执行` if (!result && onTouchEvent(event))`这个判断；而在这个判断中我们又看到了一个熟悉的方法：onTouchEvent()。所以想要执行onTouchEvent，则在上面的四个判断中必须至少有一个false。

那就假定我们在onTouch()方法中返回的是false，这样就顺利地执行了onTouchEvent，那就看看onTouchEvent的源码吧：

``` java
/**
 * Implement this method to handle touch screen motion events.
 * <p>
 * If this method is used to detect click actions, it is recommended that
 * the actions be performed by implementing and calling
 * {@link #performClick()}. This will ensure consistent system behavior,
 * including:
 * <ul>
 * <li>obeying click sound preferences
 * <li>dispatching OnClickListener calls
 * <li>handling {@link AccessibilityNodeInfo#ACTION_CLICK ACTION_CLICK} when
 * accessibility features are enabled
 * </ul>
 *
 * @param event The motion event.
 * @return True if the event was handled, false otherwise.
 */
public boolean onTouchEvent(MotionEvent event) {
    final float x = event.getX();
    final float y = event.getY();
    final int viewFlags = mViewFlags;
    final int action = event.getAction();

    if ((viewFlags & ENABLED_MASK) == DISABLED) {
        if (action == MotionEvent.ACTION_UP && (mPrivateFlags & PFLAG_PRESSED) != 0) {
            setPressed(false);
        }
        // A disabled view that is clickable still consumes the touch
        // events, it just doesn't respond to them.
        return (((viewFlags & CLICKABLE) == CLICKABLE
                || (viewFlags & LONG_CLICKABLE) == LONG_CLICKABLE)
                || (viewFlags & CONTEXT_CLICKABLE) == CONTEXT_CLICKABLE);
    }

    if (mTouchDelegate != null) {
        if (mTouchDelegate.onTouchEvent(event)) {
            return true;
        }
    }

    if (((viewFlags & CLICKABLE) == CLICKABLE ||
            (viewFlags & LONG_CLICKABLE) == LONG_CLICKABLE) ||
            (viewFlags & CONTEXT_CLICKABLE) == CONTEXT_CLICKABLE) {
        switch (action) {
            case MotionEvent.ACTION_UP:
                boolean prepressed = (mPrivateFlags & PFLAG_PREPRESSED) != 0;
                if ((mPrivateFlags & PFLAG_PRESSED) != 0 || prepressed) {
                    // take focus if we don't have it already and we should in
                    // touch mode.
                    boolean focusTaken = false;
                    if (isFocusable() && isFocusableInTouchMode() && !isFocused()) {
                        focusTaken = requestFocus();
                    }

                    if (prepressed) {
                        // The button is being released before we actually
                        // showed it as pressed.  Make it show the pressed
                        // state now (before scheduling the click) to ensure
                        // the user sees it.
                        setPressed(true, x, y);
                   }

                    if (!mHasPerformedLongPress && !mIgnoreNextUpEvent) {
                        // This is a tap, so remove the longpress check
                        removeLongPressCallback();

                        // Only perform take click actions if we were in the pressed state
                        if (!focusTaken) {
                            // Use a Runnable and post this rather than calling
                            // performClick directly. This lets other visual state
                            // of the view update before click actions start.
                            if (mPerformClick == null) {
                                mPerformClick = new PerformClick();
                            }
                            if (!post(mPerformClick)) {
                                performClick();
                            }
                        }
                    }

                    if (mUnsetPressedState == null) {
                        mUnsetPressedState = new UnsetPressedState();
                    }

                    if (prepressed) {
                        postDelayed(mUnsetPressedState,
                                ViewConfiguration.getPressedStateDuration());
                    } else if (!post(mUnsetPressedState)) {
                        // If the post failed, unpress right now
                        mUnsetPressedState.run();
                    }

                    removeTapCallback();
                }
                mIgnoreNextUpEvent = false;
                break;

            case MotionEvent.ACTION_DOWN:
                mHasPerformedLongPress = false;

                if (performButtonActionOnTouchDown(event)) {
                    break;
                }

                // Walk up the hierarchy to determine if we're inside a scrolling container.
                boolean isInScrollingContainer = isInScrollingContainer();

                // For views inside a scrolling container, delay the pressed feedback for
                // a short period in case this is a scroll.
                if (isInScrollingContainer) {
                    mPrivateFlags |= PFLAG_PREPRESSED;
                    if (mPendingCheckForTap == null) {
                        mPendingCheckForTap = new CheckForTap();
                    }
                    mPendingCheckForTap.x = event.getX();
                    mPendingCheckForTap.y = event.getY();
                    postDelayed(mPendingCheckForTap, ViewConfiguration.getTapTimeout());
                } else {
                    // Not inside a scrolling container, so show the feedback right away
                    setPressed(true, x, y);
                    checkForLongClick(0);
                }
                break;

            case MotionEvent.ACTION_CANCEL:
                setPressed(false);
                removeTapCallback();
                removeLongPressCallback();
                mInContextButtonPress = false;
                mHasPerformedLongPress = false;
                mIgnoreNextUpEvent = false;
                break;

            case MotionEvent.ACTION_MOVE:
                drawableHotspotChanged(x, y);

                // Be lenient about moving outside of buttons
                if (!pointInView(x, y, mTouchSlop)) {
                    // Outside button
                    removeTapCallback();
                    if ((mPrivateFlags & PFLAG_PRESSED) != 0) {
                        // Remove any future long press/tap checks
                        removeLongPressCallback();

                        setPressed(false);
                    }
                }
                break;
        }

        return true;
    }

    return false;
}
```

这段源码比 dispatchTouchEvent 的还要长，不过同样我们挑重点的看：
`if (((viewFlags & CLICKABLE) == CLICKABLE || (viewFlags & LONG_CLICKABLE) == LONG_CLICKABLE) || (viewFlags & CONTEXT_CLICKABLE) == CONTEXT_CLICKABLE)`
看到这句话就大概知道了主要是判断该view是否是可点击的，如果可以点击则接着执行，否则直接返回false。可以看到if里面用switch来判断是哪种触摸事件，但在最后都是返回true的。还有一点要注意：在 ACTION_UP 中会执行 performClick() 方法：

``` java
public boolean performClick() {
    final boolean result;
    final ListenerInfo li = mListenerInfo;
    if (li != null && li.mOnClickListener != null) {
        playSoundEffect(SoundEffectConstants.CLICK);
        li.mOnClickListener.onClick(this);
        result = true;
    } else {
        result = false;
    }

    sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED);
    return result;
}
```

可以看到上面的`li.mOnClickListener.onClick(this);`，没错，我们好像又有了新的发现。根据上面的经验，这句代码会去回调我们设置好的点击事件监听器。也就是我们平常用的xxx.setOnClickListener(listener);

``` java
/**
 * Register a callback to be invoked when this view is clicked. If this view is not
 * clickable, it becomes clickable.
 *
 * @param l The callback that will run
 *
 * @see #setClickable(boolean)
 */
public void setOnClickListener(@Nullable OnClickListener l) {
    if (!isClickable()) {
        setClickable(true);
    }
    getListenerInfo().mOnClickListener = l;
}
```

我们可以看到上面方法设置正是mListenerInfo的点击监听器，验证了上面的猜想。到了这里onTouch事件的传递机制基本已经分析完成了,也算是告一段落了。

好了，这下我们可以解决开头的问题了，顺便我们再来小结一下：在dispatchTouchEvent中，如果设置了OnTouchListener并且View是enable的，那么首先被执行的是OnTouchListener中的`onTouch(View v, MotionEvent event)`。若onTouch返回true,则dispatchTouchEvent不再往下执行并且返回true；不然会执行onTouchEvent，在onTouchEvent中若View是可点击的，则返回true，不然为false。还有在onTouchEvent中若View是可点击以及当前触摸事件为ACTION_UP，会执行performClick()，回调OnClickListener的onClick方法。下面是我画的一张草图：  
![这里写图片描述](/uploads/20151029/20151029230937.png)

还有一点值得注意的地方是：假如当前事件是ACTION\_DOWN，只有dispatchTouchEvent返回true了之后该View才会接收到接下来的ACTION\_MOVE,ACTION\_UP事件，也就是说只有事件被消费了才能接收接下来的事件。

好了，今天就到这里了，如果有什么问题可以在下面留言。
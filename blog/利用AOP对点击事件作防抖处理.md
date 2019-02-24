title: 利用AOP对点击事件作防抖处理
date: 2019-02-23 22:11:21
categories: Android Blog
tags: [Android,AOP]
---
Header
======
最近项目中有一个需求，需要对重复的点击事件作过滤处理。

可能第一个想到的方法是在 OnClickListener.onClick 中根据时间间隔来判断，这也是比较传统的方案。但是缺点同样也很明显，就是对现有代码的侵入性太强了。因为点击事件回调的代码我们早已写好了，现在再去改动会很痛苦，并且改动的范围也很广。

那么有没有一种方法是不需要改动源代码，就可以实现对点击事件去重的呢？当然有，我们可以利用 AOP 来实现一套方案。接下来就来讲讲这套方案就具体实现。

Body
====
在写代码之前，需要先设置 AOP 的配置，AOP 一般采用的是 AspectJ 。而在 Android 中一般直接使用 [hugo](https://github.com/JakeWharton/hugo) 或者 [gradle_plugin_android_aspectjx](https://github.com/HujiangTechnology/gradle_plugin_android_aspectjx) 插件，这样就省去了配置 AspectJ 的麻烦。在这里我使用的就是 gradle_plugin_android_aspectjx 插件，gradle_plugin_android_aspectjx 具体的配置就不详细展开了，可以自行去了解。

配置好之后，我们设计一下具体的方案，如果有不需要点击过滤的，我们就配置一个 @Except 注解。

``` java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface Except {
}
```

之后我们就来写一下 AOP 处理的代码。首先我们来创建一个 SingleClickAspect 类，在这里面写 AOP 的代码（添加 @Aspect 注解来表明这是一个切面，需要 AspectJ 处理）

``` java
@Aspect
public class SingleClickAspect {
    ...
}
```

然后我们来定义一下 AOP 的连接点（Join Point）。因为我们是打算在 onClick 中处理事件去重的，所以连接点显而易见是 method execution 。

接着是切点（Pointcuts）。最基本的切点就是 View.OnClickListener.onClick 方法了。所以可以得出第一个切点表达式：

	execution(* android.view.View.OnClickListener.onClick(..))

另外，如果是在布局 xml 中直接使用 android:onclick="xxx" 指定点击事件的话，我们也需要进行防重处理。如果有看过这一块源码的同学可能会知道，其实 android:onclick="xxx" 最后调用的是 DeclaredOnClickListener 这个类来完成点击方法包装的。

所以我们的切点就是 DeclaredOnClickListener.onClick 方法了。可以得出第二个切点表达式：

	execution(* android.support.v7.app.AppCompatViewInflater.DeclaredOnClickListener.onClick(..))

最后，还有不少同学会使用 ButterKnife 来完成 view 的初始化操作，所以对 ButterKnife 也要囊括进来。（其实 ButterKnife 内部已经对点击事件进行去重了，具体可以看下 DebouncingOnClickListener，但是我们这里还是写一下吧）

	execution(@butterknife.OnClick * *(..))

没错，ButterKnife 的切点表达式很简单，就是对 @OnClick 注解的地方处理一下即可。

定义完切点表达式后，我们就要来写点击事件去重的代码了。这里根据需求我们可以得出通知（Advice
）使用 @Around 类型。

``` java
// normal onClick
private static final String ON_CLICK_POINTCUTS = "execution(* android.view.View.OnClickListener.onClick(..))";
// 如果 onClick 是写在 xml 里面的
private static final String ON_CLICK_IN_XML_POINTCUTS = "execution(* android.support.v7.app.AppCompatViewInflater.DeclaredOnClickListener.onClick(..))";
// butterknife on click
private static final String ON_CLICK_IN_BUTTER_KNIFE_POINTCUTS = "execution(@butterknife.OnClick * *(..))";
// view tag unique key, must be one of resource id
private static final int SINGLE_CLICK_KEY = R.string.me_yuqirong_singleclick_tag_key;

@Pointcut(ON_CLICK_POINTCUTS)
public void onClickPointcuts() {
}

@Pointcut(ON_CLICK_IN_XML_POINTCUTS)
public void onClickInXmlPointcuts() {
}

@Pointcut(ON_CLICK_IN_BUTTER_KNIFE_POINTCUTS)
public void onClickInButterKnifePointcuts() {
}

@Around("onClickPointcuts() || onClickInXmlPointcuts() || onClickInButterKnifePointcuts()")
public void throttleClick(ProceedingJoinPoint joinPoint) throws Throwable {
    try {
        // check for Except annotation
        Signature signature = joinPoint.getSignature();
        if (signature instanceof MethodSignature) {
            MethodSignature methodSignature = (MethodSignature) signature;
            Method method = methodSignature.getMethod();
            // 如果有 Except 注解，就不需要做点击防抖处理
            boolean isExcept = method != null && method.isAnnotationPresent(Except.class);
            if (isExcept) {
                Log.d(TAG, "the click method is except, so proceed it");
                joinPoint.proceed();
                return;
            }
        }
        Object[] args = joinPoint.getArgs();
        View view = getViewFromArgs(args);
        // unknown click type, so skip it
        if (view == null) {
            Log.d(TAG, "unknown type method, so proceed it");
            joinPoint.proceed();
            return;
        }
        Long lastClickTime = (Long) view.getTag(SINGLE_CLICK_KEY);
        // if lastClickTime is null, means click first time
        if (lastClickTime == null) {
            Log.d(TAG, "the click event is first time, so proceed it");
            view.setTag(SINGLE_CLICK_KEY, SystemClock.elapsedRealtime());
            joinPoint.proceed();
            return;
        }
        if (canClick(lastClickTime)) {
            Log.d(TAG, "the click event time interval is legal, so proceed it");
            view.setTag(SINGLE_CLICK_KEY, SystemClock.elapsedRealtime());
            joinPoint.proceed();
            return;
        }
        Log.d(TAG, "throttle the click event, view id = " + view.getId());
    } catch (Throwable e) {
        e.printStackTrace();
        Log.d(TAG, e.getMessage());
        joinPoint.proceed();
    }
}

/**
 * 获取 view 参数
 *
 * @param args
 * @return
 */
private View getViewFromArgs(Object[] args) {
    if (args != null && args.length > 0) {
        Object arg = args[0];
        if (arg instanceof View) {
            return (View) arg;
        }
    }
    return null;
}

/**
 * 判断是否达到可以点击的时间间隔，这里间隔就设置为500L
 *
 * @param lastClickTime
 * @return
 */
private boolean canClick(long lastClickTime) {
    return SystemClock.elapsedRealtime() - lastClickTime
            >= 500L;
}

```

代码的逻辑很简单，先判断一下是否有 @Except 注解，如果有的话就直接通过了。

然后得到 onClick 方法的参数 view 。判断 view.getTag 有没有值。如果没有值，就说明是第一次点击，那么放行通过。否则就判断是否两次点击时间间隔有没有大于规定的时间间隔，从而实现点击事件的去重。

到这里，基本就完事了，整下来代码其实也就没多少量。

Footer
======
以后在做其他需求的时候，也可以思考一下是否使用 AOP 可以达成目标，可能代码量会更少，侵入性也会更低。

另外 AOP 的使用范围还是比较广泛的，比如打印日志、埋点统计等。如果看完这篇博客有想法的同学，可以自己去试试！



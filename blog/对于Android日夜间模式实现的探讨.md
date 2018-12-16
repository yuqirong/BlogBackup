title: 对于Android日夜间模式实现的探讨
date: 2016-09-08 18:32:11
categories: Android Blog
tags: [Android]
---
0x0001
======

关于 Android 的日间/夜间模式切换相信大家在平时使用 APP 的过程中都遇到过，比如知乎、简书中就有相关的模式切换。实现日间/夜间模式切换的方案也有许多种，趁着今天有空来讲一下日间/夜间模式切换的几种实现方案，也可以做一个横向的对比来看看哪种方案最好。

在本篇文章中给出了三种实现日间/夜间模式切换的方案：

1. 使用 setTheme 的方法让 Activity 重新设置主题；
2. 设置 Android Support Library 中的 UiMode 来支持日间/夜间模式的切换；
3. 通过资源 id 映射，回调自定义 ThemeChangeListener 接口来处理日间/夜间模式的切换。

三种方案综合起来可能导致文章的篇幅过长，请耐心阅读。

0x0002
======

使用 setTheme 方法
------

我们先来看看使用 setTheme 方法来实现日间/夜间模式切换的方案。这种方案的思路很简单，就是在用户选择夜间模式时，Activity 设置成夜间模式的主题，之后再让 Activity 调用 recreate() 方法重新创建一遍就行了。

那就动手吧，在 colors.xml 中定义两组颜色，分别表示日间和夜间的主题色：

``` xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="colorPrimary">#3F51B5</color>
    <color name="colorPrimaryDark">#303F9F</color>
    <color name="colorAccent">#FF4081</color>

    <color name="nightColorPrimary">#3b3b3b</color>
    <color name="nightColorPrimaryDark">#383838</color>
    <color name="nightColorAccent">#a72b55</color>
</resources>
```

之后在 styles.xml 中定义两组主题，也就是日间主题和夜间主题：

``` xml
<resources>

    <!-- Base application theme. -->
    <style name="AppTheme" parent="Theme.AppCompat.Light.DarkActionBar">
        <!-- Customize your theme here. -->
        <item name="colorPrimary">@color/colorPrimary</item>
        <item name="colorPrimaryDark">@color/colorPrimaryDark</item>
        <item name="colorAccent">@color/colorAccent</item>
        <item name="android:textColor">@android:color/black</item>
        <item name="mainBackground">@android:color/white</item>
    </style>

    <style name="NightAppTheme" parent="Theme.AppCompat.Light.DarkActionBar">
        <!-- Customize your theme here. -->
        <item name="colorPrimary">@color/nightColorPrimary</item>
        <item name="colorPrimaryDark">@color/nightColorPrimaryDark</item>
        <item name="colorAccent">@color/nightColorAccent</item>
        <item name="android:textColor">@android:color/white</item>
        <item name="mainBackground">@color/nightColorPrimaryDark</item>
    </style>

</resources>
```

在主题中的 `mainBackground` 属性是我们自定义的属性，用来表示背景色：

``` xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <attr name="mainBackground" format="color|reference"></attr>
</resources>
```

接下来就是看一下布局 activity_main.xml：

``` xml
<?xml version="1.0" encoding="utf-8"?>
<RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="?attr/mainBackground"
    android:paddingBottom="@dimen/activity_vertical_margin"
    android:paddingLeft="@dimen/activity_horizontal_margin"
    android:paddingRight="@dimen/activity_horizontal_margin"
    android:paddingTop="@dimen/activity_vertical_margin"
    tools:context="com.yuqirong.themedemo.MainActivity">

    <Button
        android:id="@+id/btn_theme"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="切换日/夜间模式" />

    <TextView
        android:id="@+id/tv"
        android:layout_below="@id/btn_theme"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center_horizontal"
        android:text="通过setTheme()的方法" />

</RelativeLayout>
```

在 `<RelativeLayout>` 的 `android:background` 属性中，我们使用 `"?attr/mainBackground"` 来表示，这样就代表着 `RelativeLayout` 的背景色会去引用在主题中事先定义好的 `mainBackground` 属性的值。这样就实现了日间/夜间模式切换的换色了。

最后就是 MainActivity 的代码：

``` java
public class MainActivity extends AppCompatActivity {

	// 默认是日间模式
    private int theme = R.style.AppTheme;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
		// 判断是否有主题存储
        if(savedInstanceState != null){
            theme = savedInstanceState.getInt("theme");
            setTheme(theme);
        }
        setContentView(R.layout.activity_main);

        Button btn_theme = (Button) findViewById(R.id.btn_theme);
        btn_theme.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                theme = (theme == R.style.AppTheme) ? R.style.NightAppTheme : R.style.AppTheme;
                MainActivity.this.recreate();
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("theme", theme);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        theme = savedInstanceState.getInt("theme");
    }
}
```

在 MainActivity 中有几点要注意一下：

1. 调用 `recreate()` 方法后 Activity 的生命周期会调用 `onSaveInstanceState(Bundle outState)` 来备份相关的数据，之后也会调用 `onRestoreInstanceState(Bundle savedInstanceState)` 来还原相关的数据，因此我们把 `theme` 的值保存进去，以便 Activity 重新创建后使用。

2. 我们在 `onCreate(Bundle savedInstanceState)` 方法中还原得到了 `theme` 值后，`setTheme()` 方法一定要在 `setContentView()` 方法之前调用，否则的话就看不到效果了。

3. `recreate()` 方法是在 API 11 中添加进来的，所以在 Android 2.X 中使用会抛异常。

贴完上面的代码之后，我们来看一下该方案实现的效果图：

![setTheme()效果图gif](/uploads/20160908/20160909103512.gif)

使用 Android Support Library 中的 UiMode 方法
-------

使用 UiMode 的方法也很简单，我们需要把 colors.xml 定义为日间/夜间两种。之后根据不同的模式会去选择不同的 colors.xml 。在 Activity 调用 recreate() 之后，就实现了切换日/夜间模式的功能。

说了这么多，直接上代码。下面是 values/colors.xml ：

``` xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="colorPrimary">#3F51B5</color>
    <color name="colorPrimaryDark">#303F9F</color>
    <color name="colorAccent">#FF4081</color>
    <color name="textColor">#FF000000</color>
    <color name="backgroundColor">#FFFFFF</color>
</resources>
```

除了 values/colors.xml 之外，我们还要创建一个 values-night/colors.xml 文件，用来设置夜间模式的颜色，其中 `<color>` 的 name 必须要和 values/colors.xml 中的相对应：

``` xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="colorPrimary">#3b3b3b</color>
    <color name="colorPrimaryDark">#383838</color>
    <color name="colorAccent">#a72b55</color>
    <color name="textColor">#FFFFFF</color>
    <color name="backgroundColor">#3b3b3b</color>
</resources>
```

在 styles.xml 中去引用我们在 colors.xml 中定义好的颜色：

``` xml
<resources>

    <!-- Base application theme. -->
    <style name="AppTheme" parent="Theme.AppCompat.Light.DarkActionBar">
        <!-- Customize your theme here. -->
        <item name="colorPrimary">@color/colorPrimary</item>
        <item name="colorPrimaryDark">@color/colorPrimaryDark</item>
        <item name="colorAccent">@color/colorAccent</item>
        <item name="android:textColor">@color/textColor</item>
        <item name="mainBackground">@color/backgroundColor</item>
    </style>

</resources>
```

activity_main.xml 布局的内容和上面 setTheme() 方法中的相差无几，这里就不贴出来了。之后的事情就变得很简单了，在 MyApplication 中先选择一个默认的 Mode ：

``` java
public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // 默认设置为日间模式
        AppCompatDelegate.setDefaultNightMode(
                AppCompatDelegate.MODE_NIGHT_NO);
    }

}
```

要注意的是，这里的 Mode 有四种类型可以选择：

* MODE\_NIGHT\_NO： 使用亮色(light)主题，不使用夜间模式；
* MODE\_NIGHT\_YES：使用暗色(dark)主题，使用夜间模式；
* MODE\_NIGHT\_AUTO：根据当前时间自动切换 亮色(light)/暗色(dark)主题；
* MODE\_NIGHT\_FOLLOW\_SYSTEM(默认选项)：设置为跟随系统，通常为 MODE\_NIGHT\_NO

当用户点击按钮切换日/夜间时，重新去设置相应的 Mode ：

``` java
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btn_theme = (Button) findViewById(R.id.btn_theme);
        btn_theme.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                getDelegate().setLocalNightMode(currentNightMode == Configuration.UI_MODE_NIGHT_NO
                        ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
                // 同样需要调用recreate方法使之生效
                recreate();
            }
        });
    }

}
```



我们来看一下 UiMode 方案实现的效果图：

![UiMode的效果图gif](/uploads/20160908/20160910011353.gif)

就前两种方法而言，配置比较简单，最后的实现效果也都基本上是一样的。但是缺点就是需要调用 `recreate()` 使之生效。而让 Activity 重新创建就必须涉及到一些状态的保存。这就增加了一些难度。所以，我们一起来看看第三种解决方法。

通过资源 id 映射，回调接口
---------

第三种方法的思路就是根据设置的主题去动态地获取资源 id 的映射，然后使用回调接口的方式让 UI 去设置相关的属性值。我们在这里先规定一下：夜间模式的资源在命名上都要加上后缀 “_night” ，比如日间模式的背景色命名为 color\_background ，那么相对应的夜间模式的背景资源就要命名为 color\_background\_night 。好了，下面就是我们的 Demo 所需要用到的 colors.xml ：

``` xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    
    <color name="colorPrimary">#3F51B5</color>
    <color name="colorPrimary_night">#3b3b3b</color>
    <color name="colorPrimaryDark">#303F9F</color>
    <color name="colorPrimaryDark_night">#383838</color>
    <color name="colorAccent">#FF4081</color>
    <color name="colorAccent_night">#a72b55</color>
    <color name="textColor">#FF000000</color>
    <color name="textColor_night">#FFFFFF</color>
    <color name="backgroundColor">#FFFFFF</color>
    <color name="backgroundColor_night">#3b3b3b</color>
    
</resources>
```

可以看到每一项 color 都会有对应的 “_night” 与之匹配。

看到这里，肯定有人会问，为什么要设置对应的 “_night” ？到底是通过什么方式来设置日/夜间模式的呢？下面就由 ThemeManager 来为你解答：

``` java
public class ThemeManager {

    // 默认是日间模式
    private static ThemeMode mThemeMode = ThemeMode.DAY;
    // 主题模式监听器
    private static List<OnThemeChangeListener> mThemeChangeListenerList = new LinkedList<>();
    // 夜间资源的缓存，key : 资源类型, 值<key:资源名称, value:int值>
    private static HashMap<String, HashMap<String, Integer>> sCachedNightResrouces = new HashMap<>();
    // 夜间模式资源的后缀，比如日件模式资源名为：R.color.activity_bg, 那么夜间模式就为 ：R.color.activity_bg_night
    private static final String RESOURCE_SUFFIX = "_night";

    /**
     * 主题模式，分为日间模式和夜间模式
     */
    public enum ThemeMode {
        DAY, NIGHT
    }

    /**
     * 设置主题模式
     *
     * @param themeMode
     */
    public static void setThemeMode(ThemeMode themeMode) {
        if (mThemeMode != themeMode) {
            mThemeMode = themeMode;
            if (mThemeChangeListenerList.size() > 0) {
                for (OnThemeChangeListener listener : mThemeChangeListenerList) {
                    listener.onThemeChanged();
                }
            }
        }
    }

    /**
     * 根据传入的日间模式的resId得到相应主题的resId，注意：必须是日间模式的resId
     *
     * @param dayResId 日间模式的resId
     * @return 相应主题的resId，若为日间模式，则得到dayResId；反之夜间模式得到nightResId
     */
    public static int getCurrentThemeRes(Context context, int dayResId) {
        if (getThemeMode() == ThemeMode.DAY) {
            return dayResId;
        }
        // 资源名
        String entryName = context.getResources().getResourceEntryName(dayResId);
        // 资源类型
        String typeName = context.getResources().getResourceTypeName(dayResId);
        HashMap<String, Integer> cachedRes = sCachedNightResrouces.get(typeName);
        // 先从缓存中去取，如果有直接返回该id
        if (cachedRes == null) {
            cachedRes = new HashMap<>();
        }
        Integer resId = cachedRes.get(entryName + RESOURCE_SUFFIX);
        if (resId != null && resId != 0) {
            return resId;
        } else {
            //如果缓存中没有再根据资源id去动态获取
            try {
                // 通过资源名，资源类型，包名得到资源int值
                int nightResId = context.getResources().getIdentifier(entryName + RESOURCE_SUFFIX, typeName, context.getPackageName());
                // 放入缓存中
                cachedRes.put(entryName + RESOURCE_SUFFIX, nightResId);
                sCachedNightResrouces.put(typeName, cachedRes);
                return nightResId;
            } catch (Resources.NotFoundException e) {
                e.printStackTrace();
            }
        }
        return 0;
    }

    /**
     * 注册ThemeChangeListener
     *
     * @param listener
     */
    public static void registerThemeChangeListener(OnThemeChangeListener listener) {
        if (!mThemeChangeListenerList.contains(listener)) {
            mThemeChangeListenerList.add(listener);
        }
    }

    /**
     * 反注册ThemeChangeListener
     *
     * @param listener
     */
    public static void unregisterThemeChangeListener(OnThemeChangeListener listener) {
        if (mThemeChangeListenerList.contains(listener)) {
            mThemeChangeListenerList.remove(listener);
        }
    }

    /**
     * 得到主题模式
     *
     * @return
     */
    public static ThemeMode getThemeMode() {
        return mThemeMode;
    }

    /**
     * 主题模式切换监听器
     */
    public interface OnThemeChangeListener {
        /**
         * 主题切换时回调
         */
        void onThemeChanged();
    }
}
```

上面 ThemeManager 的代码基本上都有注释，想要看懂并不困难。其中最核心的就是 `getCurrentThemeRes` 方法了。在这里解释一下 `getCurrentThemeRes` 的逻辑。参数中的 dayResId 是日间模式的资源id，如果当前主题是日间模式的话，就直接返回 dayResId 。反之当前主题为夜间模式的话，先根据 dayResId 得到资源名称和资源类型。比如现在有一个资源为 R.color.colorPrimary ，那么资源名称就是 colorPrimary ，资源类型就是 color 。然后根据资源类型和资源名称去获取缓存。如果没有缓存，那么就要动态获取资源了。这里使用方法的是

	context.getResources().getIdentifier(String name, String defType, String defPackage)

* `name` 参数就是资源名称，不过要注意的是这里的资源名称还要加上后缀 “_night” ，也就是上面在 colors.xml 中定义的名称；
* `defType` 参数就是资源的类型了。比如 color，drawable等；
* `defPackage` 就是资源文件的包名，也就是当前 APP 的包名。

有了上面的这个方法，就可以通过 R.color.colorPrimary 资源找到对应的 R.color.colorPrimary_night 资源了。最后还要把找到的夜间模式资源加入到缓存中。这样的话以后就直接去缓存中读取，而不用再次去动态查找资源 id 了。

ThemeManager 中剩下的代码应该都是比较简单的，相信大家都可以看得懂了。

现在我们来看看 MainActivity 的代码：

``` java
public class MainActivity extends AppCompatActivity implements ThemeManager.OnThemeChangeListener {

    private TextView tv;
    private Button btn_theme;
    private RelativeLayout relativeLayout;
    private ActionBar supportActionBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ThemeManager.registerThemeChangeListener(this);
        supportActionBar = getSupportActionBar();
        btn_theme = (Button) findViewById(R.id.btn_theme);
        relativeLayout = (RelativeLayout) findViewById(R.id.relativeLayout);
        tv = (TextView) findViewById(R.id.tv);
        btn_theme.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ThemeManager.setThemeMode(ThemeManager.getThemeMode() == ThemeManager.ThemeMode.DAY
                        ? ThemeManager.ThemeMode.NIGHT : ThemeManager.ThemeMode.DAY);
            }
        });
    }

    public void initTheme() {
        tv.setTextColor(getResources().getColor(ThemeManager.getCurrentThemeRes(MainActivity.this, R.color.textColor)));
        btn_theme.setTextColor(getResources().getColor(ThemeManager.getCurrentThemeRes(MainActivity.this, R.color.textColor)));
        relativeLayout.setBackgroundColor(getResources().getColor(ThemeManager.getCurrentThemeRes(MainActivity.this, R.color.backgroundColor)));
        // 设置标题栏颜色
        if(supportActionBar != null){
            supportActionBar.setBackgroundDrawable(new ColorDrawable(getResources().getColor(ThemeManager.getCurrentThemeRes(MainActivity.this, R.color.colorPrimary))));
        }
        // 设置状态栏颜色
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.setStatusBarColor(getResources().getColor(ThemeManager.getCurrentThemeRes(MainActivity.this, R.color.colorPrimary)));
        }
    }

    @Override
    public void onThemeChanged() {
        initTheme();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ThemeManager.unregisterThemeChangeListener(this);
    }

}
```

在 MainActivity 中实现了 OnThemeChangeListener 接口，这样就可以在主题改变的时候执行回调方法。然后在 `initTheme()` 中去重新设置 UI 的相关颜色属性值。还有别忘了要在 `onDestroy()` 中移除 ThemeChangeListener 。

最后就来看看第三种方法的效果吧：

![动态获取资源id的效果图gif](/uploads/20160908/20160910114556.gif)

也许有人会说和前两种方法的效果没什么差异啊，但是仔细看就会发现前面两种方法在切换模式的瞬间会有短暂黑屏现象存在，而第三种方法没有。这是因为前两种方法都要调用 `recreate()` 。而第三种方法不需要 Activity 重新创建，使用回调的方法来实现。

0x0003
=====

到了这里，按照套路应该是要总结的时候了。那么就根据上面给的三种方法来一个简单的对比吧：

1. setTheme 方法：可以配置多套主题，比较容易上手。除了日/夜间模式之外，还可以有其他五颜六色的主题。但是需要调用 recreate() ，切换瞬间会有黑屏闪现的现象；

2. UiMode 方法：优点就是 Android Support Library 中已经支持，简单规范。但是也需要调用 recreate() ，存在黑屏闪现的现象；

3. 动态获取资源 id ，回调接口：该方法使用起来比前两个方法复杂，另外在回调的方法中需要设置每一项 UI 相关的属性值。但是不需要调用 recreate() ，没有黑屏闪现的现象。

三种方法整体的对比就如上所示了。当然除了上面的三种方法实现日/夜间模式切换之外，还有比如动态换肤等也都可以实现。方法有很多种，重要的是要根据自身情况选择合适的方法去实现。在下面我会给出其他几种实现日/夜间模式切换方法的链接，可以参考一下。

好了，到了说再见的时候了。

Goodbye !

[setTheme方法的Demo下载](/uploads/20160908/ThemeDemo_setTheme.rar)

[UiMode方法的Demo下载](/uploads/20160908/ThemeDemo_UiMode.rar)

[动态获取资源id方法的Demo下载](/uploads/20160908/ThemeDemo_thememanager.rar)

0x0004
=======

[android 实现【夜晚模式】的另外一种思路](https://segmentfault.com/a/1190000005736047?f=tt&hmsr=toutiao.io&utm_medium=toutiao.io&utm_source=toutiao.io)

[知乎和简书的夜间模式实现套路](http://www.diycode.cc/topics/269)


title: 关于Activity生命周期的小结
date: 2015-08-26 22:13:31
categories: Android Blog
tags: [Android,Activity]
---
开头先说一下写这篇博客的初衷，由于博主在找实习的过程中面试经常被问到Activity生命周期有关的问题，所以特此写一篇博客来记一下。 

Activity作为四大组件之一，几乎是每个人开始学习Android最先接触到的。常见的生命周期方法大家肯定都是非常熟悉的，所以Activity生命周期的顺序在这就不必过多叙述了。今天讲一下由FirstActivity启动SecondActivity而调用生命周期方法的顺序问题。

首先我们创建一个如下图的FirstActivity:
![这里写图片描述](/uploads/20150826/20150826213244536.jpg)
很简单，LinearLayout里只有一个Button，用于启动SecondActivity。

以下为FirstActivity的布局 activity_first.xml:

``` xml
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <Button
        android:id="@+id/second"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="start SecondActivity" />

</LinearLayout>
```

FirstActivity的代码如下：

``` java
public class FirstActivity extends AppCompatActivity {

    public static final String TAG = "Activity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_first);
        Log.i(TAG, "FirstActivity onCreate");

        Button button = (Button) findViewById(R.id.second);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(FirstActivity.this,SecondActivity.class));
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.i(TAG,"FirstActivity onStart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "FirstActivity onResume");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "FirstActivity onPause");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i(TAG, "FirstActivity onStop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "FirstActivity onDestroy");
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.i(TAG, "FirstActivity onRestart");
    }

    @Override
    public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {
        super.onSaveInstanceState(outState, outPersistentState);
        Log.i(TAG, "FirstActivity onSaveInstanceState");
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        Log.i(TAG, "FirstActivity onRestoreInstanceState");
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.i(TAG, "FirstActivity onConfigurationChanged");
    }
}
```

主要是在生命周期方法中设置了Log打印。

SecondActivity的代码与FirstActivity并无差异，主要将Log中的FirstActivity替换成了SecondActivity。

接下来我们就启动FirstActivity，可以看到Logcat中打印了如下的日志：
![这里写图片描述](/uploads/20150826/20150826212633880.jpg)
一切如我们想象的一样，然后我们点击按钮用于启动SecondActivity，可以看到打印出来的日志：
![这里写图片描述](/uploads/20150826/20150826214443682.jpg)
可以看到FirstActivity和SecondActivity的生命周期方法是交叉着的，并不是先让FirstActivity执行完然后再执行SecondActivity的方法，这正是我们需要注意的。

然后我们点击Back键，返回FirstActivity:
![这里写图片描述](/uploads/20150826/20150826214330231.jpg)
FirstActivity调用的是onRestart方法，因为先前FirstActivity已经创建，所以并不会重新调用onCreate方法。最后再次点击Back键，退出Activity：
![这里写图片描述](/uploads/20150826/20150826214929167.jpg)
写到这里本篇博客的要讲内容已经差不多了，下面再补充一下关于切换横竖屏时Activity的生命周期调用，先前在网上看的一些博文叙述的都已经过时了，大都是在Android 2.2 或者 2.3 时写的，已经不适用于Android 4.0以上的版本了。所以在这里重新写一下：

测试机型：红米2
Android版本：5.1.0

1. 不设置android:configChanges时，无论是切横屏还是切竖屏都会重新调用各个生命周期，**但都是调用一次**（原先Android 2.X 的说法是切横屏时会执行一次,切竖屏时会执行两次，只适用于Android 2.X 版本）
2.  设置android:configChages=”orientation”时，结果和不设置一样，仍然是重新调用生命周期方法，而且横竖屏都是一次（Android2.X版本：设置Activity的android:configChanges=”orientation”时,切屏还是会重新调用各个生命周期,切横、竖屏时只会执行一次）。
3.  设置为android:configChanges=”orientation|keyboardHidden”时，Android 4.0以上和不设置一样，仍然是重新调用生命周期方法，而且横竖屏都是一次；**Android2.X版本切屏不会重新调用各个生命周期,只会执行onConfigurationChanged方法**.
4.  Android4.0版本只有设置Activity的android:configChanges="orientation|keyboardHidden|screenSize"时，才不重新创建Activity，但会调用onConfigurationChanged方法.

好了，今天就到这里吧。
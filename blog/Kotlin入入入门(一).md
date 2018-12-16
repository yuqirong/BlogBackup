title: Kotlin入入入门(一)
date: 2017-06-07 20:10:22
categories: Android Blog
tags: [Android,Kotlin]
---
Android Studio 配置
===================
Android Studio 3.0 版本已经默认添加了对 Kotlin 的支持，所以以下 Android Studio 配置是针对于 3.0 版本以下的。

1. 安装 Kotlin 插件

	![Kotlin Plugin](/uploads/20170607/20170607210549.png)

2. 将 Java 代码转化为 Kotlin 代码

	![Converting Java code to Kotlin](/uploads/20170607/20170607221409.png)

	之后代码就变成了如下：

	``` kotlin
	class MainActivity : AppCompatActivity() {

	    override fun onCreate(savedInstanceState: Bundle?) {
	        super.onCreate(savedInstanceState)
	        setContentView(R.layout.activity_main)
	    }
	}
	```

3. 将文件编辑之后，会跳出一个配置 Kotlin 的提示：

	![Kotlin Configure](/uploads/20170607/20170607222519.png)

	点击配置后，出现如下弹窗，点击 OK 即可

	![Kotlin Configure Dialog](/uploads/20170607/20170607222730.png)

4. 配置完成后，可以看到项目的 build.gradle 多了一些：

		buildscript {
		    ext.kotlin_version = '1.1.2-4'
		    repositories {
		        jcenter()
		    }
		    dependencies {
		        classpath 'com.android.tools.build:gradle:2.2.2'
		        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version"
		    }
		}

	app/build.gradle 的配置：

		apply plugin: 'com.android.application'
		apply plugin: 'kotlin-android'
		
		...

		dependencies {
			...
		    compile "org.jetbrains.kotlin:kotlin-stdlib:$kotlin_version"
		}

Hello World
===========
学习一门编程语言的首要任务就是写出 “Hello World” ，那就让我们迈下第一步吧。

activity_main.xml :

``` xml
<?xml version="1.0" encoding="utf-8"?>
<RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/activity_main"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:paddingBottom="@dimen/activity_vertical_margin"
    android:paddingLeft="@dimen/activity_horizontal_margin"
    android:paddingRight="@dimen/activity_horizontal_margin"
    android:paddingTop="@dimen/activity_vertical_margin"
    tools:context="me.yuqirong.kotlindemo.MainActivity">

    <TextView
        android:id="@+id/textView"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content" />
</RelativeLayout>
```

然后在 `onCreate(savedInstanceState: Bundle?)` 去得到 `textView` 。这里就体现出 Kotlin 的好处了，不再需要 `findViewById` 。而是先需要在 app/build.gradle 中添加如下配置：

	apply plugin: 'com.android.application'
	apply plugin: 'kotlin-android'
	// 添加以下这行
	apply plugin: 'kotlin-android-extensions'

	...

配置好后，在代码中就可以直接使用了，是不是很方便呢！

``` kotlin
package me.yuqirong.kotlindemo

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        textView.text = "Hello World"
    }
}
```

运行一下，demo的效果图就是这样滴，不加特效！

![Demo效果图](/uploads/20170607/20170607231545.png)

Goodbye ~ ~

Demo下载：[KotlinDemo](/uploads/20170607/KotlinDemo.rar)
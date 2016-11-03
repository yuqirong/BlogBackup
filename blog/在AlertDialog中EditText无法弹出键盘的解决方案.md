title: 在AlertDialog中EditText无法弹出键盘的解决方案
date: 2016-02-06 14:54:11
categories: Android Tips
tags: [Android]
---
之前在做项目的过程中，有一个需求就是在AlertDialog中有EditText，可以在EditText中输入内容。但是在实际开发的过程中却发现，点击EditText却始终无法弹出键盘。因为之前在使用AlertDialog的时候，布局中并没有EditText，因此没有发现这个问题。这次算是填了一个隐藏的坑。

例如下面给出了一个例子，首先贴上AlertDialog的`layout.xml`：
``` xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="300dp"
    android:layout_height="200dp"
    android:background="@android:color/white"
    android:orientation="vertical">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Hello friend!"/>

    <EditText
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="input content" />

    <Button
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="submit"/>

</LinearLayout>
```
AlertDialog的效果图是这样的：

![这里填写图片的描述](/uploads/20160206/20160206160310.png)

我们会发现无论怎么点击EditText也无法弹出键盘，其实我们只要加上`alertDialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);`这一句，就可以让键盘弹出了。

![这里填写图片的描述](/uploads/20160206/20160206161311.png)

源码下载：

[AlertDialogDemo.rar](/uploads/20160206/AlertDialogDemo.rar)

StackOverFlow：

[Android: EditText in Dialog doesn't pull up soft keyboard](http://stackoverflow.com/questions/9102074/android-edittext-in-dialog-doesnt-pull-up-soft-keyboard)
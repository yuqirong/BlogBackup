title: Android Data Binding入门
date: 2018-05-30 22:16:27
categories: Android Blog
tags: [Android]
---
配置
------
新建一个 Project，确保项目 build.gradle 中的 Gradle 插件版本不低于 1.5.0-alpha1，比如我的 Demo 是 3.1.2 版本的：

```
buildscript {
    
    repositories {
        google()
        jcenter()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:3.1.2'
        

        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle files
    }
}
```

然后修改对应 app 模块的 build.gradle ：

```
android {
  ...
  dataBinding {
      enabled true
  }
}
```

User
-------
先定义一个 User 类，代表用户。这也是我们项目中的 Model 。
```
public class User {
    
    private String username;
    private String password;
    private String nickName;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }
}
```

layout
--------
定义好 User 类之后，我们要在 layout 布局文件中将 View 和Model 进行绑定

``` xml
<?xml version="1.0" encoding="utf-8"?>
<layout xmlns:android="http://schemas.android.com/apk/res/android">

    <data>
        <variable
            name="user"
            type="me.yuqirong.myapplication.User" />
    </data>
    <!--原先的根节点（Root Element）-->
    <LinearLayout xmlns:app="http://schemas.android.com/apk/res-auto"
        xmlns:tools="http://schemas.android.com/tools"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical"
        tools:context=".MainActivity">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@{user.username}" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@{user.password}" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@{user.nickName}" />

    </LinearLayout>
</layout>
```

在data内描述了一个名为user的变量属性，使其可以在这个layout中使用：

``` xml
<variable name="user" type="me.yuqirong.myapplication.User"/>
```

在layout的属性表达式写作 @{xxx.xxxx} ，下面是一个TextView的text设置为user的 username 属性：

``` xml
	<TextView 
      android:layout_width="wrap_content"
      android:layout_height="wrap_content"
      android:text="@{user.username}"/>
```

MainActivity
---------------
单单在 layout 布局文件中将 view 和 model 绑定还不够，我们需要知道要绑定的是哪个 user 类的对象。所以我们还要在 MainActivity 中写代码。

``` java
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityMainBinding dataBinding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        User user = new User();
        user.setNickName("tom");
        user.setUsername("tom123");
        user.setPassword("abc123456");
        dataBinding.setUser(user);
    }

}
```

这样，就完成了一个简单的 Data Binding Demo 了。

Data Binding 的小技巧
--------------------

* 获取 Activity 的 View

	``` java
	ActivityMainBinding dataBinding = DataBindingUtil.setContentView(this, R.layout.activity_main);
	View view = dataBinding.getRoot();//获取对应的View
	```

* 使用某个子 View，其中 tvName 对应着 android:id="@+id/tv_name" 的 TextView 

	``` java
	dataBinding.tvName.setText("Hello World");
	```
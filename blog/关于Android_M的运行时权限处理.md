title: 关于Android M的运行时权限处理
date: 2016-06-08 22:12:20
categories: Android Blog
tags: [Android]
---
之前有人在 Android 6.0 的机型上运行了 [DragGridView](https://github.com/yuqirong/DragGridView) 结果出异常奔溃了。想必问题的原因大家都知道，是 Android 6.0 新引入了在运行时权限申请(Runtime Permissions)的功能。那么这所谓的运行时申请权限究竟是怎么一回事呢，一起来看看吧！

在 Android 6.0 中，app 如果想要获得某些权限，会在应用中弹出一个对话框，让用户确认是否授予该权限。具体的截图如下：

![Runtime Permissions screenshot](/uploads/20160608/20160608161439.png)

这要做的好处就是运行一个 app 时可以拒绝其中的某些权限，防止 app 触及到你的隐私(比如说通讯录、短信之类的)。而在 Android 6.0 之前，若同意安装 app ，就意味着该 app 可以获取权限列表中的所有权限。(注：这里所指的都是原生 Android 系统，比如 MIUI 之类的第三方 ROM 很早就具备了这种功能。)

接下来就来看看相关的 API 吧，首先我们来看看 `Context.checkSelfPermission(String permission)` 方法，该方法主要用于检测该 app 是否已经被赋予了某权限，传入的参数有。如果已被赋予，则返回 `PERMISSION_GRANTED` ，否则返回 `PERMISSION_DENIED` 。

若返回了 `PERMISSION_DENIED` ，那么我们就要去申请该权限了。这时就要用到  `Activity.requestPermissions(String[] permissions, int requestCode)` 这个方法了。顾名思义，该方法的作用就是申请某些权限了。第一个参数就是要申请的权限，可以看到参数形式是一个数组，也就是说可以一次申请多个权限。而第二个参数就是申请权限的代号，主要用于在之后的回调中选择。

当用户在权限申请的对话框中作出选择后，就会回调 `onRequestPermissionsResult (int requestCode, String[] permissions, int[] grantResults)` 方法。第一个参数就是上面的权限代号；第二个参数是申请的权限数组；第三个参数就是权限申请的结果。

结合上面的几个方法，可以写出如下所示的权限申请代码模版：

``` java
public static final int READ_CONTACTS_REQUEST_CODE = 101;

// 如果权限没有被授予
if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CONTACTS) !=
        PackageManager.PERMISSION_GRANTED) {
    // 申请权限
    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_CONTACTS}, READ_CONTACTS_REQUEST_CODE);
} else {
    // TODO 权限已经被授予

}

@Override
public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    switch (requestCode) {
        case READ_CONTACTS_REQUEST_CODE:
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // TODO 用户已经授予了权限

            } else {
                // TODO 用户拒绝授予权限

            }
            break;
    }

}
```

在这里，还有一个方法需要注意下，那就是 `shouldShowRequestPermissionRationale (Activity activity, String permission)` 方法。这个方法的作用就是当用户拒绝了某个权限之后，下一次就会显示出需要该权限的说明。

关于运行时申请权限基本就这样了，值得提醒的是，并不是所有的权限都需要运行时申请，只有“危险”的权限才通过运行时来申请。比如说读取联系人、获取位置信息、读写SD卡等等都为“危险权限”，而比如振动、联网、蓝牙等就是普通权限了，就不需要运行时申请了。

说完了运行时申请权限后，另外还有一点需要注意的是，在 Android 6.0 显示悬浮窗也有一个“坑”。如果调用平常的显示悬浮窗的方法，会抛出 “permission denied for this window type” 异常。解决的方案就是在显示悬浮窗之前，需要调用一下 `Settings.canDrawOverlays(context)` 这个方法。若该方法返回 true ，则说明用户同意创建悬浮窗；否则可以跳转到相关的设置页面。具体的代码模版如下：

``` java
if (Build.VERSION.SDK_INT >= 23) {
    if (Settings.canDrawOverlays(context)) {
        // 显示悬浮窗
    } else {
	// 跳转到相关的设置页面
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
        startActivity(intent);
    }
} else {
    // 版本低于Android 6.0，直接显示悬浮窗
}
```

好了，就到这里吧。

GoodBye！

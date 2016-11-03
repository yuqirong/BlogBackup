title: Android安全机制之反编译
date: 2016-04-03 00:59:11
categories: Android Blog
tags: [Android]
---
今天我们就来探讨一下反编译，其实反编译在我一开始学习Android的时候就听说过，但是一直没有去尝试。初次接触应该就是那次“蜻蜓FM v5.0.1 apk”事件了( 此处应有掌声(¯ □ ¯) )。那时根据网上的教程第一次反编译了“蜻蜓FM”的apk，看到了传说中的“普罗米修斯方法”以及“宙斯类”(不得不感慨开发小哥的智商)。之后就是在阅读《Android群英传》时也有相关反编译的内容，觉得有必要记录一下。所以这就是本片写博文的起源了。

首先关于反编译，我们先要准备几个工具：

* apktool：aoktool主要是用来反编译资源文件的，也就是XML了。
* Dex2jar：Dex2jar就是反编译源代码的，会把源代码反编译成一个jar包。
* jd-gui ：在上面Dex2jar反编译出来的jar包，放入jd-gui中，就可以查看源代码了。

关于上面的三个工具，我会在文末放出下载链接，大家可以去下载。

好了，那接下来我们就开始反编译之旅吧！

至于要反编译的apk，我只能选择自己的[Koku](https://github.com/yuqirong/Koku)了，[点击此处下载](http://www.wandoujia.com/apps/com.yuqirong.koku/download)。

我们把上面下载下来的apk用winrar打开(当然你也可以用其他的解压工具)，我们可以看到里面的文件内容如下图所示：

![这里写图片描述](/uploads/20160403/20160403112646.png)

我们发现classes.dex这个文件，其实classes.dex反编译出来就是源代码。然后我们把Dex2jar解压出来，发现里面有d2j-dex2jar.bat，这就是主角了。

![这里写图片描述](/uploads/20160403/20160403124449.png)

在Dex2jar解压出来的目录下，打开命令提示符输入：

	d2j-dex2jar.bat classes.dex所在的路径

比如：

![这里写图片描述](/uploads/20160403/20160403124911.png)

运行后，我们发现在Dex2jar解压出来的目录下多了一个classes-dex2jar.jar。

![这里写图片描述](/uploads/20160403/20160403125136.png)

然后我们把下载下来的jd-gui.zip解压，里面会有jd-gui.exe。相信大家都懂吧。用jd-gui.exe打开上面的classes-dex2jar.jar，你会惊喜地发现源代码就在你眼前！

![这里写图片描述](/uploads/20160403/20160403125619.png)

看上面的代码截图，我们会发现比如说`setContentView()`里面是一串数字。不过别怕，我们都知道R文件是用来关联资源文件的，把上面的那串数字复制下来，再打开R.class，查找一下：

![这里写图片描述](/uploads/20160403/20160403130002.png)

原来那串数字就代表了activity\_my\_favorite.xml这个layout。那么问题来了，我们如何反编译XML文件呢？那就要用到上面的apktool了。

打开apktool的所在目录，把koku.apk移动到apktool的同一目录下，输入命令符：

	java -jar apktool_2.1.0.jar d koku.apk

如果你配置了Java环境变量，则可以直接输入：

	apktool_2.1.0.jar d koku.apk

运行完成之后，我们可以发现在目录下多了一个名字叫koku的文件夹，而这就是我们反编译出来的XML文件了。

![这里写图片描述](/uploads/20160403/20160403131154.png)

我们打开里面的AndroidManifest.xml：

![这里写图片描述](/uploads/20160403/20160403131326.png)

里面真的有<uses-permission\>、<activity\>等信息！然后我们打开res里面的layout文件夹，会发现里面有我们上面提到的activity\_my\_favorite.xml：

![这里写图片描述](/uploads/20160403/20160403131658.png)

里面的布局一目了然。到这里，这样一个apk的基本的源代码我们都可以看得到。当然，反编译别人的apk应该是以学习为主，而不是恶意地二次打包以及破坏。

在这里额外多说一句，如果要反编译的apk经过了代码混淆，那么反编译出来的就变成了a.class、b.class、c.class等等，所以代码混淆可以有效地阻止apk反编译。

而如果你想要将代码混淆，只要打开项目中的build.gradle(Module:app)文件，minifyEnabled为true则说明打开混淆功能。proguard-rules.pro为项目自定义的混淆文件，在项目app文件夹下找到这个文件，在这个文件里可以定义引入的第三方依赖包的混淆规则。

	buildTypes {
        release {
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'
        }
    }

好了，差不多该讲的都讲完了，今天就到这里了。

下面给出反编译工具的下载链接：

[apktool_2.1.0.jar](/uploads/20160403/apktool_2.1.0.jar)

[dex2jar-2.0.zip](/uploads/20160403/dex2jar-2.0.zip)

[jd-gui-0.3.5.windows.zip](/uploads/20160403/jd-gui-0.3.5.windows.zip)

~have a nice day~
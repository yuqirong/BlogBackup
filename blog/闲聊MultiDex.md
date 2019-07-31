title: 闲聊MultiDex
date: 2019-07-09 00:14:20
categories: Android Blog
tags: [Android,multiDex]
---
MultiDex 是什么？
======
当Android系统安装一个应用的时候，有一步是对Dex进行优化，这个过程有一个专门的工具来处理，叫DexOpt。DexOpt的执行过程是在第一次加载Dex文件的时候执行的。这个过程会生成一个ODEX文件，即Optimised Dex。执行ODex的效率会比直接执行Dex文件的效率要高很多。

但是在早期的Android系统中，DexOpt有一个问题，DexOpt会把每一个类的方法id检索起来，存在一个链表结构里面。但是这个链表的长度是用一个short类型来保存的，导致了方法id的数目不能够超过65536个。当一个项目足够大的时候，显然这个方法数的上限是不够的。尽管在新版本的Android系统中，DexOpt修复了这个问题，但是我们仍然需要对低版本的Android系统做兼容。

在Android 5.0之前，为了解决这个问题，Google官方推出了这个类似于补丁一样的 support-library, MultiDex。而在 Android 5.0及以后，使用了 ART 虚拟机，原生支持从 APK 文件加载多个 dex 文件。

使用方法
=======
以下是在 minSdkVersion < 21 的使用方法。

1. 配置build.gradle

		android {
		
		    compileSdkVersion 21
		    buildToolsVersion "21.1.0"
		
		    defaultConfig {
		        ...
		
		        // Enabling multidex support.
		        multiDexEnabled true
		    }
		    ...
		}
	
		dependencies {
		  compile 'com.android.support:multidex:1.0.3'
		}

2. 继承 MultiDexApplication

		public class MyApplication extends MultiDexApplication {
		  // ...........
		}

3. 如果不想继承 MultiDexApplication ，可以重写 attachBaseContext 方法

		@Override
		protected void attachBaseContext(Context base) {
		   super.attachBaseContext(base);
		   MultiDex.install(this);
		}

反之，如果 minSdkVersion >= 21 ，只需要以下配置即可。 

		android {
		    defaultConfig {
		        ...
		        minSdkVersion 21 
		        targetSdkVersion 28
		        multiDexEnabled true
		    }
		    ...
		}


MultiDex 原理
======
* [类加载机制系列3——MultiDex原理解析](https://juejin.im/entry/5a3a21fcf265da430d58294e)
* [Android使用Multidex突破64K方法数限制原理解析](https://www.jianshu.com/p/33968db4b08d)

简单地来说，MultiDex 做的事情就是：

1. 解压得到 dex 并进行 dexOpt ;
2. 把主dex文件除外的 dex 文件都追加到 PathClassLoader 中 DexPathListde Element[] 数组中

熟悉组件化、热修复的同学肯定对这些已经了如指掌了吧。  

MultiDex 的局限性
=======
Dalvik 可执行文件分包支持库具有一些已知的局限性，将其纳入您的应用构建配置之中时，您应该注意这些局限性并进行针对性的测试：

•	启动期间在设备数据分区中安装 DEX 文件的过程相当复杂，如果辅助 DEX 文件较大，可能会导致应用无响应 (ANR) 错误。在此情况下，您应该通过 ProGuard 应用代码压缩以尽量减小 DEX 文件的大小，并移除未使用的那部分代码。
	
•	由于存在 Dalvik linearAlloc 错误（问题 22586），使用 Dalvik 可执行文件分包的应用可能无法在运行的平台版本早于 Android 4.0（API 级别 14）的设备上启动。如果您的目标 API 级别低于 14，请务必针对这些版本的平台进行测试，因为您的应用可能会在启动时或加载特定类群时出现问题。代码压缩可以减少甚至有可能消除这些潜在问题。
	
•	由于存在 Dalvik linearAlloc 限制（问题 78035），因此，如果使用 Dalvik 可执行文件分包配置的应用发出非常庞大的内存分配请求，则可能会在运行期间发生崩溃。尽管 Android 4.0（API 级别 14）提高了分配限制，但在 Android 5.0（API 级别 21）之前的 Android 版本上，应用仍有可能遭遇这一限制。

**第一个问题的解决方法**

Facebook 提出一种加载方案：将 MultiDex.install() 操作放在另外一个经常进行的。

让 Launcher Activity 在另外一个进程启动，但是 Multidex.install 还是在 Main Process 中开启，虽然逻辑上已经不承担 dexopt 的任务。
 这个 Launcher Activity 就是用来异步触发 dexopt 的 ，load 完成就启动 Main Activity；如果已经loaded，则直接启动Main Process。
 Multidex.install所引发的合并耗时操作，是在前台进程的异步任务中执行的，所以没有 ANR 的风险。

在 Facebook 的这个方案基础上，[其实你不知道MultiDex到底有多坑](https://www.jianshu.com/p/a5353748159f) 给出了一个优化后的方案。

![优化后的方案流程图](/uploads/20190709/20190709001043.png)

另外，还有美团、微信的解决方案，详见 [Android Dex分包最全总结：含Facebook解决方案](https://blog.csdn.net/xJ032w2j4cCjhOW8s8/article/details/89880046)

**第二个问题的解决方法**

现在开发的应用 minSdkVersion 一般都设置为 Android 4.1 。所以 API 低于 14 的不需要考虑了。

现在应该不存在哪个应用丧心病狂地向下兼容适配到 Android 2.X 了吧？ 

Reference
====
* [配置方法数超过 64K 的应用](https://developer.android.com/studio/build/multidex?hl=zh-cn)
* [类加载机制系列3——MultiDex原理解析](https://juejin.im/entry/5a3a21fcf265da430d58294e)
* [Android使用Multidex突破64K方法数限制原理解析](https://www.jianshu.com/p/33968db4b08d)
* [其实你不知道MultiDex到底有多坑](https://www.jianshu.com/p/a5353748159f)
* [Android Dex分包最全总结：含Facebook解决方案](https://blog.csdn.net/xJ032w2j4cCjhOW8s8/article/details/89880046)



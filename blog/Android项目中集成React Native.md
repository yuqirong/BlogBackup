title: Android项目中集成React Native
date: 2016-12-04 21:04:35
categories: React Native Blog
tags: [Android,React Native]
---

集成React Native的步骤
=====================
1. 运行以下命令 ：
 
		$ npm init

	生成 `package.json` ，下面给出一份 Demo ：

		{
		  "name": "HelloWorld",
		  "version": "0.0.1",
		  "private": true,
		  "main": "index.android.js",
		  "scripts": {
		    "start": "node node_modules/react-native/local-cli/cli.js start",
		    "test": "jest"
		  },
		  "dependencies": {
		    "react": "^15.4.1",
		    "react-native": "^0.39.0"
		  }
		}

2. 运行以下命令安装 React Native , Android 项目根目录就生成了 `node_modules/` 文件夹：

		$ npm install --save react react-native

	在 `.gitignore` 中添加：

		# node.js
		node_modules/
		npm-debug.log

	执行 `react-native upgrade` 可以更新已有组件。

3. 运行以下命令生成 `.flowconfig` 文件：

		$ curl -o .flowconfig https://raw.githubusercontent.com/facebook/react-native/master/.flowconfig

4. 修改 `package.json` 中 `scripts` 的 `start` 部分：

		"start": "node node_modules/react-native/local-cli/cli.js start"

5. 在 Android 项目的根目录下新建 `index.android.js` ：

		'use strict';
		
		import React from 'react';
		import {
		  AppRegistry,
		  StyleSheet,
		  Text,
		  View
		} from 'react-native';
		
		class HelloWorld extends React.Component {
		  render() {
		    return (
		      <View>
		        <Text>Hello, World</Text>
		        <Text>Hello, React Native</Text>
		      </View>
		    )
		  }
		}
		
		AppRegistry.registerComponent('HelloWorld', () => HelloWorld);

6. 在 `app/build.gradle` 中添加：

		defaultConfig {
	        ...
	        ndk {
	            abiFilters "armeabi-v7a", "x86"
	        }
    	}
		
		dependencies {
	    	...
	    	compile "com.facebook.react:react-native:0.39.0" // From node_modules.
		}

	react-native 依赖的版本和 `package.json` 中保持一致。

7. 在 Android 项目根目录下的 `build.gradle` 文件添加如下内容：

		allprojects {
		    repositories {
		        maven {
		            // All of React Native (JS, Android binaries) is installed from npm
		            url "$rootDir/node_modules/react-native/android"
		        }
		    }
		}

8. 新建一个 `MyApplication` 继承自 `Application` ，在 `AndroidManifest.xml` 中修改成相应的 `MyApplication` ：

		public class MyApplication extends Application implements ReactApplication {
		
		    private final ReactNativeHost mReactNativeHost = new ReactNativeHost(this) {
		        @Override
		        protected boolean getUseDeveloperSupport() {
		            return BuildConfig.DEBUG;
		        }
		
		        @Override
		        protected List<ReactPackage> getPackages() {
		            return Arrays.<ReactPackage>asList(
		                    new MainReactPackage()
		            );
		        }
		
		    };
		
		    @Override
		    public ReactNativeHost getReactNativeHost() {
		        return mReactNativeHost;
		    }
		
		    @Override
		    public void onCreate() {
		        super.onCreate();
		        SoLoader.init(this, /* native exopackage */ false);
		    }
		
		}

9. 新建一个 `ReactNativeActivity` ，用来展示 React Native 的页面：

		public class ReactNativeActivity extends ReactActivity {
		
		    @Override
		    protected String getMainComponentName() {
		        return "HelloWorld";
		    }
		
		}

	其中 `getMainComponentName()` 返回的字符串要和 index.android.js 中的 `AppRegistry.registerComponent` 中保持一致。

10. 在 `AndroidManifest.xml` 里需要添加相关内容

		<uses-permission android:name="android.permission.INTERNET" />
	    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />

		<activity android:name=".ReactNativeActivity" />
        <activity android:name="com.facebook.react.devsupport.DevSettingsActivity" />

11. 启动服务。debug 模式下需要在 `package.json` 所在目录下执行 

		$ npm start

	之后按照正常的 Android 程序调试即可。

12. 发布正式包

	JS Bundle 打包：

	在 `app/src/main/` 下创建 `assets/` 文件夹，执行以下命令将 JS Bundle 保存到资源目录下

		$ react-native bundle --platform android --dev false --entry-file index.android.js --bundle-output app/src/main/assets/index.android.bundle --assets-dest app/src/main/res/

	在 `app/src/main/assets/` 下就会生成 `index.android.bundle` 文件。图片会生成在 `app/sec/main/res/drawable-mdpi/` 目录下。之后按照 Android 项目正常打包即可，但别忘了添加 React Native 的混淆：

		-dontobfuscate
		
		# React Native
		
		# Keep our interfaces so they can be used by other ProGuard rules.
		# See http://sourceforge.net/p/proguard/bugs/466/
		-keep,allowobfuscation @interface com.facebook.proguard.annotations.DoNotStrip
		-keep,allowobfuscation @interface com.facebook.proguard.annotations.KeepGettersAndSetters
		-keep,allowobfuscation @interface com.facebook.common.internal.DoNotStrip
		
		# Do not strip any method/class that is annotated with @DoNotStrip
		-keep @com.facebook.proguard.annotations.DoNotStrip class *
		-keep @com.facebook.common.internal.DoNotStrip class *
		-keepclassmembers class * {
		    @com.facebook.proguard.annotations.DoNotStrip *;
		    @com.facebook.common.internal.DoNotStrip *;
		}
		
		-keepclassmembers @com.facebook.proguard.annotations.KeepGettersAndSetters class * {
		  void set*(***);
		  *** get*();
		}
		
		-keep class * extends com.facebook.react.bridge.JavaScriptModule { *; }
		-keep class * extends com.facebook.react.bridge.NativeModule { *; }
		-keepclassmembers,includedescriptorclasses class * { native <methods>; }
		-keepclassmembers class *  { @com.facebook.react.uimanager.UIProp <fields>; }
		-keepclassmembers class *  { @com.facebook.react.uimanager.annotations.ReactProp <methods>; }
		-keepclassmembers class *  { @com.facebook.react.uimanager.annotations.ReactPropGroup <methods>; }
		
		-dontwarn com.facebook.react.**
		
		# okhttp
		
		-keepattributes Signature
		-keepattributes *Annotation*
		-keep class okhttp3.** { *; }
		-keep interface okhttp3.** { *; }
		-dontwarn okhttp3.**
		
		# okio
		
		-keep class sun.misc.Unsafe { *; }
		-dontwarn java.nio.file.*
		-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
		-dontwarn okio.**

在集成React Native中遇到的问题
============================
Warning:Conflict with dependency 'com.google.code.findbugs:jsr305'. Resolved versions for app (3.0.0) and test app (2.0.1) differ. See http://g.co/androidstudio/app-test-app-conflict for details.
-------------------------------------------------
在 app/build.gradle 中添加如下：

	android {
		...
	    configurations.all {
	        resolutionStrategy.force 'com.google.code.findbugs:jsr305:3.0.0'
	    }
	}



Cannot find module 'invariant'
------------------------------
在调用 `react-native init Test` 初始化某个项目时，抛出如下异常：

	$ react-native init Test
	This may take some time...
	This will walk you through creating a new React Native project in C:\Users\Administrator\Desktop\Test
	Installing react-native package from npm...
	module.js:327
	    throw err;
	    ^
	
	Error: Cannot find module 'invariant'
	    at Function.Module._resolveFilename (module.js:325:15)
	    at Function.Module._load (module.js:276:25)                
	    at Module.require (module.js:353:17)
	    at require (internal/module.js:12:17)
	    at Object.<anonymous> (C:/Users/Administrator/Desktop/Test/node_modules/react-native/packager/react-packager/src/node-haste/Module.js:18:19)
	    at Module._compile (module.js:409:26)
	    at loader (C:\Users\Administrator\Desktop\Test\node_modules\react-native\node_modules\babel-register\lib\node.js:144:5)
	    at Object.require.extensions.(anonymous function) [as .js] (C:\Users\Administrator\Desktop\Test\node_modules\react-native\node_modules\babel-register\lib\node.js:154:7)
	    at Module.load (module.js:343:32)
	    at Function.Module._load (module.js:300:12)

解决方案：调用 `npm i --save-dev invariant` 命令，详见 [Cannot find module 'invariant' - react-native-cli](https://github.com/facebook/react-native/issues/11327)

java.lang.IllegalAccessError: Method 'void android.support.v4.net.ConnectivityManagerCompat.<init\>()' is inaccessible to class 'com.facebook.react.modules.netinfo.NetInfoModule'
---------------------------
在 React Native 程序运行时，报错：
 
	E/AndroidRuntime: FATAL EXCEPTION: AsyncTask #1
	    Process: com.fanwei.reactnativeupdate, PID: 3139
	    java.lang.RuntimeException: An error occured while executing doInBackground()
	        at android.os.AsyncTask$3.done(AsyncTask.java:304)
	        at java.util.concurrent.FutureTask.finishCompletion(FutureTask.java:355)
	        at java.util.concurrent.FutureTask.setException(FutureTask.java:222)
	        at java.util.concurrent.FutureTask.run(FutureTask.java:242)
	        at android.os.AsyncTask$SerialExecutor$1.run(AsyncTask.java:231)
	        at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1112)
	        at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:587)
	        at java.lang.Thread.run(Thread.java:818)
	     Caused by: java.lang.IllegalAccessError: Method 'void android.support.v4.net.ConnectivityManagerCompat.<init>()' is inaccessible to class 'com.facebook.react.modules.netinfo.NetInfoModule' (declaration of 'com.facebook.react.modules.netinfo.NetInfoModule' appears in /data/data/com.fanwei.reactnativeupdate/files/instant-run/dex/slice-com.facebook.react-react-native-0.20.1_3b7c8d9b91d5c989075fbd631ac74b192cee741b-classes.dex)
	        at com.facebook.react.modules.netinfo.NetInfoModule.<init>(NetInfoModule.java:55)
	        at com.facebook.react.shell.MainReactPackage.createNativeModules(MainReactPackage.java:67)
	        at com.facebook.react.ReactInstanceManagerImpl.processPackage(ReactInstanceManagerImpl.java:793)
	        at com.facebook.react.ReactInstanceManagerImpl.createReactContext(ReactInstanceManagerImpl.java:730)
	        at com.facebook.react.ReactInstanceManagerImpl.access$600(ReactInstanceManagerImpl.java:91)
	        at com.facebook.react.ReactInstanceManagerImpl$ReactContextInitAsyncTask.doInBackground(ReactInstanceManagerImpl.java:184)
	        at com.facebook.react.ReactInstanceManagerImpl$ReactContextInitAsyncTask.doInBackground(ReactInstanceManagerImpl.java:169)
	        at android.os.AsyncTask$2.call(AsyncTask.java:292)
	        at java.util.concurrent.FutureTask.run(FutureTask.java:237)
	        at android.os.AsyncTask$SerialExecutor$1.run(AsyncTask.java:231) 
	        at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1112) 
	        at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:587) 
	        at java.lang.Thread.run(Thread.java:818) 

解决方案：修改依赖为 `com.android.support:appcompat-v7:23.0.1` ，详见 [Android java.lang.IllegalAccessError Method void android.support.v4.net.ConnectivityManagerCompat](https://github.com/facebook/react-native/issues/6152)

ERROR  EPERM: operation not permitted, lstat 'C:\Users\Administrator\Desktop\ReactNativeUpdate\app\build\generated\assets\shaders\debug'
{"errno":-4048,"code":"EPERM","syscall":"lstat","path":"C:\\Users\\Administrator\\Desktop\\ReactNativeUpdate\\app\\build\\generated\\assets\\shaders\\debug"}
Error: EPERM: operation not permitted, lstat 'C:\Users\Administrator\Desktop\ReactNativeUpdate\app\build\generated\assets\shaders\debug' at Error (native)
---------------------------------------------------------------
在调用命令 `npm start` 时，出现以下错误：

	ERROR  EPERM: operation not permitted, lstat 'C:\Users\Administrator\Desktop\ReactNativeUpdate\app\build\generated\assets\shaders\debug'
	{"errno":-4048,"code":"EPERM","syscall":"lstat","path":"C:\\Users\\Administrator\\Desktop\\ReactNativeUpdate\\app\\build\\generated\\assets\\shaders\\debug"}
	Error: EPERM: operation not permitted, lstat 'C:\Users\Administrator\Desktop\ReactNativeUpdate\app\build\generated\assets\shaders\debug'
	    at Error (native)

解决方案：打开项目中 `node_modules/react-native/local-cli/server/server.js` 找到  `process.on('uncaughtException', error => {` 这个方法，把最后一句 `process.exit(11);` 注释掉。

References
==========
* [Integration With Existing Apps](http://facebook.github.io/react-native/docs/integration-with-existing-apps.html)
* [React Native移植原生Android项目-已更新版本](http://www.lcode.org/react-native%e7%a7%bb%e6%a4%8d%e5%8e%9f%e7%94%9fandroid%e9%a1%b9%e7%9b%ae-%e5%b7%b2%e6%9b%b4%e6%96%b0%e7%89%88%e6%9c%ac/)
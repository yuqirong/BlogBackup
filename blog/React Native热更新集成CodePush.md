title: React Native热更新集成CodePush
date: 2016-11-03 09:30:59
categories: Android Blog
tags: [Android,React Native]
---

Code Push GitHub 地址: https://github.com/Microsoft/react-native-code-push

基本安装
========
1. 安装 code push cli

		npm install -g code-push-cli

2. 注册，成功后得到 key 后输入

		code-push register

3. 在 Code Push 服务器注册 app

		code-push app add your_app_name 

	查找密钥，有 deployment key 和 staging key 两种

		code-push deployment ls your_app_name -k

4. 在项目根目录执行以下命令安装react-native-code-push模块

		npm install –save react-native-code-push

5. 在 Anroid project 中安装插件，CodePush提供了两种方式：RNPM 和 Manual，这里所使用的是RNPM

		npm i -g rnpm

6. 添加codepush配置，要求输入 deployment key ，可以 ignore

		react-native link react-native-code-push

	或者**手动配置**，引入项目, 在setting.gradle文件中设置：

		include ':react-native-code-push'
		project(':react-native-code-push').projectDir = new File(rootProject.projectDir, '../node_modules/react-native-code-push/android/app')

	在app/build.gradle文件中设置

		dependencies {
		    ...
		    compile project(':react-native-code-push')
		}

	在app/build.gradle文件添加项目依赖：

		apply from: "../../node_modules/react-native/react.gradle"
		apply from: "../../node_modules/react-native-code-push/android/codepush.gradle"

	在 MainApplication 中添加如下代码：

		import com.microsoft.codepush.react.CodePush;
	
		...
		
		@Override
		    
		protected String getJSBundleFile() {
		
			return CodePush.getJSBundleFile();
		}
	
		
		@Override
		    
		protected List<ReactPackage> getPackages() {
		      
			return Arrays.<ReactPackage>asList(
		          
				new MainReactPackage(),
				// 把 deployment-key-here 替换成你 app 的 key
				new CodePush("deployment-key-here", this, BuildConfig.DEBUG)
		      
			);
		}

JS代码
========

		import CodePush from 'react-native-code-push';

		CodePush.sync();

修改versionName
============
在 android/app/build.gradle 中有个 android.defaultConfig.versionName 属性，我们需要把 应用版本改成 1.0.0（默认是1.0，但是codepush需要三位数）。

	android{
	    defaultConfig{
	        versionName "1.0.0"
	    }
	}

打包 JS Bundle
============
1. 在工程目录里面新增 bundles 文件：

		mkdir bundles

2. 运行命令打包 

		react-native bundle --platform 平台 --entry-file 启动文件 --bundle-output 打包js输出文件 --assets-dest 资源输出目录 --dev 是否调试

	例如:

		react-native bundle --platform android --entry-file index.android.js --bundle-output ./bundles/index.android.bundle --dev false

发布更新
======
打包bundle结束后，就可以通过CodePush发布更新了。在终端输入

	code-push release <应用名称> <Bundles所在目录> <对应的应用版本> --deploymentName <更新环境> --description <更新描述>  --mandatory <是否强制更新>

例如:

	code-push release AwesomeProject ./bundles/index.android.bundle 1.0.0 --deploymentName Staging --description "1.测试更新" --mandatory false

**注意**：

1. Code Push 默认是更新 staging 环境的，如果是 staging ，则不需要填写 deploymentName 。

2. 如果有 mandatory 则 Code Push 会根据 mandatory 是 true 或 false 来控制应用是否强制更新。默认情况下 mandatory 为 false 即不强制更新。

3. 对应的应用版本（targetBinaryVersion）是指当前 app 的版本(对应build.gradle中设置的versionName "1.0.6")，也就是说此次更新的 js/images 对应的是 app 的那个版本。不要将其理解为这次 js 更新的版本。

	如客户端版本是 1.0.6，那么我们对1.0.6的客户端更新js/images，targetBinaryVersion填的就是1.0.6。

3. 对于对某个应用版本进行多次更新的情况，Code Push 会检查每次上传的 bundle ，如果在该版本下如 1.0.6 已经存在与这次上传完全一样的 bundle (对应一个版本有两个 bundle 的 md5 完全一样)，那么 CodePush 会拒绝此次更新。

4. 在终端输入 `code-push deployment history your_app_name Staging` 可以看到 staging 版本更新的时间、描述等等属性。

相关问题
=========
进行编译时报错，具体的原因未知。

错误一：

	Execution:failed for task ':app:packageDebug'
	outDexFolder must be a folder

错误二：

	Execution:failed for task ':app:packageDebug'
	Failed to create \projectName\android\app\buildintermediates\debug\merging


React Native 获取应用版本号
=================

1. npm install react-native-device-info --save

2. react-native link react-native-device-info

3. import com.learnium.RNDeviceInfo.RNDeviceInfo;

4. 获取应用版本号：DeviceInfo.getVersion()

参考链接
=====
* [Code Push GitHub](https://github.com/Microsoft/react-native-code-push)
* [React Native热更新部署/热更新-CodePush最新集成总结](http://www.jianshu.com/p/9e3b4a133bcc)

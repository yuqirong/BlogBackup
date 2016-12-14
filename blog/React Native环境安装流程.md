title: React Native环境安装流程
date: 2016-10-15 08:52:40
categories: React Native Blog
tags: [React Native]
---

React Native 环境安装
=======================
1. 安装 Java 、 Android SDK 。这个应该不用讲了，不会的直接自己百度吧。

2. 安装 Node

	React Native 需要使用 Node JS 来做服务器，可以去 Node JS 的官网下载安装：
	
	下载地址：https://nodejs.org/en/
	
	使用 `node -v` 可以查看 Node JS 安装的版本。

3. 安装 Git
	
	下载地址：https://git-scm.com/downloads

	配置好环境变量后从 GitHub 把 React Native 仓库 clone 下来。

	React Native GitHub 地址：https://github.com/facebook/react-native.git
	

4. 安装 React Native 命令行工具 react-native-cli
	
	打开 clone 下来的 React Native 仓库，进入 /react-native-cli 目录，输入命令 `npm install -g` 安装

5. 创建 React Native 项目

	进入你希望创建项目的目录后，输入 `react-native init [项目名]` 创建新项目，比如 `react-native init AwesomeProject`，等待一段时间项目会创建完成。

6. 运行打包的 Node JS 服务器

	在命令行中进入项目目录，输入 `react-native start` ，等待一段时间即可。

7. 运行项目

	第六步的服务端不要关闭，重新启动一个新的命令行，进入项目目录，输入 `react-native run-android` 运行 Android 项目。同理，输入 `react-native run-ios` 运行 iOS 项目。

References
==========
* [史上最详细Windows版本搭建安装React Native环境配置](http://www.lcode.org/%e5%8f%b2%e4%b8%8a%e6%9c%80%e8%af%a6%e7%bb%86windows%e7%89%88%e6%9c%ac%e6%90%ad%e5%bb%ba%e5%ae%89%e8%a3%85react-native%e7%8e%af%e5%a2%83%e9%85%8d%e7%bd%ae/)
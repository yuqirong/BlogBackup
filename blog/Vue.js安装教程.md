title: Vue.js安装教程
date: 2017-12-01 00:29:14
categories: Vue.js
tags: Vue.js
---
安装步骤
=======
1. 安装 node.js (网址：[https://nodejs.org/en/](https://nodejs.org/en/))。

2. 基于 node.js ,利用淘宝 npm 镜像安装相关依赖。在 cmd 里直接输入：`npm install -g cnpm –registry=https://registry.npm.taobao.org`，回车，等待安装。

3. 安装全局 vue-cli 脚手架,用于帮助搭建所需的模板框架，在 cmd 里 
	
	1. 输入：`cnpm install -g vue-cli`，回车，等待安装；
	
	2. 输入： vue ，回车，若出现 vue 信息说明表示成功。

4. 创建项目，在 cmd 里输入：`vue init webpack vue_test(项目文件夹名)` ，回车，等待一小会儿，依次出现下列选项，输入后创建成功。

	![create vue project](/uploads/20171201/20171201223625.jpg)

5. 安装依赖，在 cmd 里 
 
	1. 输入：`cd vue_test` ，回车，进入到具体项目文件夹

    2. 输入：`npm install`，回车，等待一小会儿，安装依赖。

6. 测试环境是否搭建成功

	1. 在 cmd 里输入：`npm run dev`

	2. 在浏览里输入：localhost:8080(默认端口为8080)

	运行起来后的效果如下图所示：

	![Vue running](/uploads/20171201/20171201223752.jpg)


安装中遇到的问题
==============
vue init webpack vue_test
-----
	C:\Users\h\Desktop>vue init webpack vue_test
	C:\Users\h\AppData\Roaming\npm\node_modules\vue-cli\bin\vue-init:60
	let template = program.args[0]
	^^^
	
	SyntaxError: Block-scoped declarations (let, const, function, class) not yet sup
	ported outside strict mode
	    at exports.runInThisContext (vm.js:54:16)
	    at Module._compile (module.js:375:25)
	    at Object.Module._extensions..js (module.js:406:10)
	    at Module.load (module.js:345:32)
	    at Function.Module._load (module.js:302:12)
	    at Function.Module.runMain (module.js:431:10)
	    at startup (node.js:141:18)
	    at node.js:977:3

nodejs版本太低，去官网更新即可。

npm install
-----------
	C:\Users\h\Desktop\vue_test>npm install
	
	> chromedriver@2.33.2 install C:\Users\h\Desktop\vue_test\node_modules\chromedri
	ver
	> node install.js
	
	Downloading https://chromedriver.storage.googleapis.com/2.33/chromedriver_win32.
	zip
	Saving to C:\Users\h\AppData\Local\Temp\chromedriver\chromedriver_win32.zip
	ChromeDriver installation failed Error with http(s) request: Error: connect ETIM
	EDOUT 172.217.160.112:443
	npm WARN optional SKIPPING OPTIONAL DEPENDENCY: fsevents@1.1.3 (node_modules\fse
	vents):
	npm WARN notsup SKIPPING OPTIONAL DEPENDENCY: Unsupported platform for fsevents@
	1.1.3: wanted {"os":"darwin","arch":"any"} (current: {"os":"win32","arch":"x64"}
	)
	
	npm ERR! code ELIFECYCLE
	npm ERR! errno 1
	npm ERR! chromedriver@2.33.2 install: `node install.js`
	npm ERR! Exit status 1
	npm ERR!
	npm ERR! Failed at the chromedriver@2.33.2 install script.
	npm ERR! This is probably not a problem with npm. There is likely additional log
	ging output above.
	
	npm ERR! A complete log of this run can be found in:
	npm ERR!     C:\Users\h\AppData\Roaming\npm-cache\_logs\2017-11-25T07_25_19_228Z
	-debug.log

因为 chromedriver 被墙了，所以需要输入命令 `cnpm install chromedriver` ，安装 chromedriver 。
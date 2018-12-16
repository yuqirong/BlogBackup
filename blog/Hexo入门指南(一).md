title: Hexo入门指南(一)
date: 2015-07-12 20:37:29
categories: Hexo
tags: [Hexo]
---
前言
=========

对于一个程序员而言，我想GitHub和个人博客应该程序员的“标配”吧，在个人博客上可以记载一路上的酸甜苦辣，回过头来看看，何尝不是一种享受呢。想不想拥有一个属于自己的博客？接下来本篇博文就来教你如何搭建一款属于自己的博客。

在开讲之前，需要我们自己有一个GitHub的账号(相信大多数的程序员都有)，然后在GitHub上创建一个新的Repo,名字叫xxx.github.io(xxx为自己GitHub的用户名，如我的GitHub的用户名叫yuqirong,则要创建repo名就是[yuqirong.github.io](https://github.com/yuqirong/yuqirong.github.io)),搞定了这个我们就做好了第一步。

经过上面步骤，我们已经拥有了一个初步域名：`http://xxx.github.io`,而服务器就是用的是GitHub的。所以下面我们就要精心地装扮一下我们的博客了。这里介绍一下[Hexo](https://hexo.io/zh-cn/docs/index.html)，[Hexo](https://hexo.io/zh-cn/docs/index.html) 是一个快速、简洁且高效的博客框架。[Hexo](https://hexo.io/zh-cn/docs/index.html) 使用 Markdown（或其他渲染引擎）解析文章，在几秒内，即可利用靓丽的主题生成静态网页。也就是说你不需要去写一大堆的html，js，css等，Hexo会帮你实现所有。听完这句话是不是很激动呢，那下面就开始安装Hexo。

安装前提
=========

安装 Hexo 相当简单。但是Hexo是基于Node.js的，所以在安装前，必须安装Node.js：

[Node.js](http://nodejs.org/)

装完了Node.js还有一个必须也要有，那就是Git(相信大多数程序员的电脑都都有安装)：

[Git](http://git-scm.com/)

装完了上面两个就可以安装Hexo了：

	$ npm install -g hexo-cli

恭喜你，Hexo成功地安装在你的电脑上了。

建立网站
=========

先把之前创建的xxx.github.io的仓库clone到你的本地，然后执行：

	$ hexo init <folder>
	$ cd <folder>
	$ npm install

注意：上面的<folder>就是你clone下来的xxx.github.io

执行后可以去xxx.github.io里看到有许多的文件夹生成：

	├──	.deploy_git
	├── node_modules
	├── _config.yml
	├── package.json
	├── scaffolds
	├── source
	└── themes

在这里介绍一下这些文件及文件夹的作用：

* .deploy_git：这个可以不管，主要是网站部署到GitHub上时生成的文件。
* node_modules：主要是Hexo的文件。
* _config.yml：整个网站的配置信息，之后会在这里配置大部分的参数。
* package.json：应用程序的信息。没什么卵用。
* scaffolds：模版文件夹。当您新建文章时，Hexo会根据scaffold来建立文件。(来自官方的解释)
* source：是存放用户资源的文件夹，之后我们写的博客文章和一些图片等都存放在此文件夹下。
* themes：下载下来的博客主题都存放在这里，初始时会有一个landscape主题。

配置
=========

打开xxx.github.io/_config.yml，你可以看到开头有title、subtitle、description和author等。那接下来就来配置网站吧！

	# Site
	title	    网站标题
	subtitle	网站副标题
	description	网站描述
	author	    您的名字
	language	网站使用的语言
	timezone	网站时区。Hexo 默认使用您电脑的时区。时区列表。比如说：America/New_York, Japan, 和 UTC 。


	# URL
	## If your site is put in a subdirectory, set url as 'http://yoursite.com/child' and root as '/child/'
	url			网址	 如http://xxx.github.io
	root		网站根目录	
	permalink	文章的永久链接格式：year/:month/:day/:title/
	permalink_default	永久链接中各部分的默认值	

经过了上面的配置，你就有了一个自定义的网站了。有点小激动吧。离网站建成还差最后一步，那就 是部署了。

部署
=========

我们发现在网站中竟然没有自己的博文，那马上来写一篇吧：

	$ hexo new page

在xxx.github.io/source/_posts/目录下可以看到有一个page.md文件，那就是我们刚刚新创建的page，而写博文需要用MarkDown语法来写。如果你还不了解MarkDown，那就[点击这里](https://github.com/younghz/Markdown)吧。

经过上面的步骤，相信我们已经有一篇原创的博文了，那就让Hexo生成静态文件：

	$ hexo generate

我们打开xxx.github.io/目录会发现已经有了一个public的文件夹，没错，整个网站的静态文件都在public文件夹里面。也就是说，把publish文件夹里面的东西push到xxx.github.io中，那整个网站就搭建完成了。那就赶快执行吧！

在执行部署命令之前，还有一项配置没做：  
打开xxx.github.io/_config.yml，拉到底部，会看到#Deployment，那就是我们要配置的部署选项

	# Deployment
	## Docs: http://hexo.io/docs/deployment.html
	deploy:
	  type: git
	  repo: https://github.com/xxx/xxx.github.io
	  branch: master
	  message: Site updated

上面repo中的xxx就是你的GitHub账号的用户名，branch设置成master。message就是你每次push到GitHub时的备注。

到了这里，就可以真正地部署了：

	$ hexo deploy

执行完后你再打开`http://xxx.github.io`，你会惊喜地发现一个完全属于你自己的个人网站诞生了！

Hexo的初步入门就差不多到这里了，后面还将会有关于Hexo的博文更新，敬请关注。

Reference
=========

* [Hexo官方文档](https://hexo.io/zh-cn/docs/)
* [如何在一天之内搭建以你自己名字为域名的很 cool 的个人博客](http://gold.xitu.io/entry/56657fe160b202595a6f8ef6)
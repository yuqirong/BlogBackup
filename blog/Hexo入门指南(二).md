title: Hexo入门指南(二)
date: 2015-07-18 19:09:24
categories: Hexo
tags: [Hexo]
---
在[Hexo入门指南(一)][url]中我们已经初步地搭建了我们的博客，但是我们发现Hexo另一大特点我们还没有尝试过——主题。下面我们就来试试更换[Hexo的主题](https://hexo.io/themes/)吧。

[url]: /2015/07/12/Hexo入门指南(一)/

[Hexo的主题](https://hexo.io/themes/)非常多，有各式各样的，基本满足了大家的审美需求。其中我们就以[NexT主题](http://theme-next.iissnan.com/)为例吧。[NexT主题](http://theme-next.iissnan.com/)很简约，但有非常多的人用。

下载 NexT 主题
=============
在终端窗口下，定位到 Hexo 站点目录下

	$ cd your-hexo-site
	$ git clone https://github.com/iissnan/hexo-theme-next themes/next

启用 NexT 主题
============
克隆/下载 完成后，打开站点配置文件(也就是xxx.github.io/_config.yml)，找到 theme 字段，并将其值更改为 next。

验证主题是否启用
=============
运行 hexo s --debug，并访问 http://localhost:4000，确保站点正确运行。

关于其它[主题设定](http://theme-next.iissnan.com/five-minutes-setup.html)等，这里就不过多叙述了，官方文档讲得很详细。可以参考[NexT官方文档](http://theme-next.iissnan.com/)。

增加留言板
=========
该功能的实现的前提必须是Next已安装了第三方评论系统，如多说等。如你的网站并未安装第三方评论，请[点击此处](http://theme-next.iissnan.com/third-party-services.html)。然后执行以下命令：

	$ hexo new page guestbook

我们会发现在xxx.github.io/source/里生成了一个名叫guestbook的文件夹，那就是我们想要的留言板。打开guestbook/index.md，设置`comments: true`，如下图所示：

![这里写图片描述](/uploads/20150718/20150718193734.png)

然后找到你NexT主题中的`_config.yml`(即xxx.github.io/themes/next/_config.yml)，在menu中添加guestbook，即：

	menu:
	  home: /
	  categories: /categories
	  archives: /archives
	  tags: /tags
	  guestbook: /guestbook
	  about: /about

再找到你NexT主题zh-Hans.yml文件（如果你的网站是其它语言的，请选择相对应的语言文件），文件路径xxx.github.io/themes/next/languages/zh-Hans.yml，添加`guestbook: 留言板`，即：

	menu:
	  home: 首页
	  archives: 归档
	  categories: 分类
	  tags: 标签
	  about: 关于
	  search: 搜索
	  guestbook: 留言板
	  commonweal: 公益404

重新部署网站，你会发现在menu中多了一项“留言板”功能，这样就可以在留言板上留言了。

修改底栏
================
关于修改底栏增加站长统计及访客记录等可参考此处 [Hexo 3.1.1 静态博客搭建指南](http://lovenight.github.io/2015/11/10/Hexo-3-1-1-%E9%9D%99%E6%80%81%E5%8D%9A%E5%AE%A2%E6%90%AD%E5%BB%BA%E6%8C%87%E5%8D%97/) 。

References
===========
* [NexT官方文档](http://theme-nekxt.iissnan.com/)
* [动动手指，NexT主题与Hexo更搭哦（基础篇）](http://www.arao.me/2015/hexo-next-theme-optimize-base/)
* [Hexo 3.1.1 静态博客搭建指南](http://lovenight.github.io/2015/11/10/Hexo-3-1-1-%E9%9D%99%E6%80%81%E5%8D%9A%E5%AE%A2%E6%90%AD%E5%BB%BA%E6%8C%87%E5%8D%97/)
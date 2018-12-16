title: 从SVN迁移到GitLab
date: 2018-11-15 22:43:46
categories: Android Blog
tags: [Git,GitLab] 
---
之前公司代码版本管理用的都是 SVN ，最近搭了 GitLab 。所以想把代码从 SVN 迁移到 GitLab 上。但是 SVN 的提交记录又不能丢，也要跟着一起迁移，所以本篇记录一下迁移的方法。

	yum install -y git-svn

安装 git-svn ，可以帮助你很轻松的从 SVN 转到 GitLab 上。

然后 cd 到要迁移到 SVN 项目的根目录下

	svn log --xml | grep author | sort -u | perl -pe 's/.>(.?)<./$1 = /'
	
这条命令会输出 SVN 所有提交过的人的名字，比如

<author>xiaoming</author>
<author>xiaowang</author>
<author>xiaohong</author>

然后新建一个文件，用于保存该记录

	touch svn-history.txt
	
再然后我们就要对这个记录做一些处理，能让 Git 识别这些代码提交者

	vi svn-history.txt
	
把内容改成如下：

xiaoming = xiaoming  <xiaoming@163.com>
xiaowang = xiaowang  <xiaowang@qq.com>
xiaohong = xiaohong  <xiaohong@qq.com>

保存好后，输入命令

	git svn clone  svn://svn.yoursvnaddress.com/XXXX/  --no-metadata  --authors-file=svn-history.txt
	
这条命令会在当前目录下新建一个 XXXX 项目，这个 XXXX 项目是用 Git 的。

	cd XXXX
	git remote add origin git@yougitaddress:xxx/XXXX.git
	git push origin --all

这样就完成了从 SVN 到 GitLab 的迁移，并且是包含了 SVN 提交记录的。




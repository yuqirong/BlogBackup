title: 《Pro Git》笔记
date: 2016-06-18 00:29:14
categories: Book Note
tags: Git
---
第一章 起步
========

1.5 初次运行 Git 前的配置
--------

* Git 配置用户名：

	git config --global user.name "yuqirong"

* Git 配置电子邮箱：

	git config --global user.email "yuqirong@myhexin.com"

* 查看 Git 配置情况：

	git config --list

* 设置默认使用的文本编辑器：

	git config --global core.editor emacs

* 设置默认使用的差异分析工具

	git config --global merge.tool vimdiff

第二章 Git 基础
========

2.1 取得项目的 Git 仓库
------------

* 对某个项目进行 Git 管理：

	git init

* 对某个仓库进行克隆：

	git clone "https://github.com/yuqirong/yuqirong.github.io.git"

* 或者对克隆下来的仓库进行改名：

	git clone "https://github.com/yuqirong/yuqirong.github.io.git" yuqirong

* Git 添加某个文件：

	git add src/com/yuqirong/Test/a.java

2.2 记录每次更新到仓库
---------------

* 检查当前文件状态：

	git status

* 查看已暂存的更新：

	git diff --cached

* 查看未暂存的更新：

	git diff

* 提交更新：

	git commit
	或
	git commit -m "add new file"

* 跳过使用暂存区域的提交更新：

	git commit -a -m "add new file"

* 移除文件：

	git rm abc.txt

* 强制移除文件：

	git rm -f abc.txt

* 文件保存在当前目录中但从跟踪清单中移除：

	git rm --cached abc.txt

* 移动文件：

	git mv file\_from file\_to

2.3 查看提交历史
--------------

* 查看提交历史：

	git log

* 查看提交历史中每次提交的内容差异：

	git log -patch

* 查看提交历史但仅显示简要的增改行数统计：

	git log --stat

* 查看提交历史并限制输出长度：

	git log -2
	或
	git log --since=2.weeks

2.4 撤消操作
--------------

* 修改最后一次提交：

	git commit --amend

* 取消已经暂存的文件：

	git reset HEAD abc.txt

* 取消对文件的修改：

	git checkout -- abc.txt (ps:该命令对已经add的文件无效)

2.5 远程仓库的使用
--------------

* 查看当前的远程库：

	git remote
	
* 查看当前远程库对应的克隆地址：

	git remote -v

* 添加远程仓库：

	git remote add [short-name] [url]

* 从远程仓库抓取数据：

	git fetch [remote-name] (fetch 命令只是将远端的数据拉到本地仓库，并不自动合并到当前工作分支，只有当你确实准备好了，才能手工合并。)

* 从远程仓库抓取数据，自动合并到本地仓库中：

	git pull [remote-name]

* 推送数据到远程仓库中：

	git push [remote-name] [branch-name]

* 查看远程仓库信息:

	git remote show [remote-name]

* 远程仓库的重命名:

	git remote rename [old-name] [new-name]

* 远程仓库的删除:

	git remote rm [remote-name]

2.6 打标签
---------------

* 列出现有的标签：

	git tag

* 新建标签：

	git tag -a v1.0 -m "qirong yu's blog"

* 查看相应标签的版本信息：

	git show v1.0

* 签署标签：

	git tag -s v1.0 -m "qirong yu's blog"

* 轻量级标签：

	git tag v1.0

* 验证标签：

	git tag -v [tag-name]

* 分享标签:

	git push origin v1.0

* 推送所有的标签：

	git push origin --tags

2.7 技巧和窍门
--------------

* 设置 Git 命令别名：

	git config --global alias.co checkout

第三章 起步
========

3.1 何谓分支
-------------
* 创建一个新的分支：

	git branch [branch-name]

* 克隆一个远程服务器上的分支：

	git clone -b [branch-name] [remote-url]

* 切换分支：

	git checkout [branch-name]

3.2 基本的分支与合并
-----------
* 新建一个分支并切换到该分支上：
	
	git checkout -b [branch-name]

* 合并分支：

	git merge [branch-name]

* 删除分支：

	git branch -d [branch-name]

* 调用可视化的合并工具来解决冲突：

	git mergetool

3.3 分支管理
----------------

* 列出所有分支：

	git branch

* 查看分支最后一次 commit 信息：

	git branch -v

* 查看哪些分支已经被并入：

	git branch --merged

* 查看哪些分支没有被并入：

	git branch --no-merged

3.5 远程分支
--------------

* 跟踪分支

	git checkout -b [local-branch-name] [remote-name]/[remote-branch-name]

* 删除远程分支：

	git push [remote-name] :[remote-branch-name]

3.6 衍合
-----------

* 衍合分支：

	git rebase [branch-name]

第4章 服务器上的 Git 
========

第5章 分布式 Git
========



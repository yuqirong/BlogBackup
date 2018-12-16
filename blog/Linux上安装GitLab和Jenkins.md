title: Linux上安装GitLab和Jenkins
date: 2018-11-13 22:17:39
categories: Android Blog
tags: [Android]
---
之前在公司的服务器上搭建了 GitLab 和 Jenkins ，所以打算把这过程记录下，以便下次有需要时可以复用。

Git
===

在搭建 GitLab 之前，肯定要先安装 Git 。

在 https://github.com/git/git/releases 中选择最新版本的 Git，然后
		
	wget https://github.com/git/git/archive/v2.19.1.tar.gz

下载下来后，我们进行解压

	tar -zxvf v2.19.1.tar.gz
	
进入解压后的文件夹

	cd git-2.19.1
	
之后我们需要编译 Git 的源码，在这之前我们先安装编译需要的依赖，这里可能提示需要 su 权限才能安装

	yum install curl-devel expat-devel gettext-devel openssl-devel zlib-devel gcc perl-ExtUtils-MakeMaker
	
安装好后我们进行编译

	make prefix=/usr/local/git all
	
之后我们安装 Git 到 /usr/local/git 路径

	make prefix=/usr/local/git install

安装完成后 Git 会自动将配置添加到环境变量 PATH 中，如果没有的话需要手动添加，可以自行百度

最后输入

	git --version
	
查看 Git 是否安装成功。

GitLab
======

安装依赖


	//配置系统防火墙,把HTTP和SSH端口开放.
	sudo yum install curl openssh-server postfix cronie
	sudo service postfix start
	sudo lokkit -s http -s ssh
	sudo chkconfig postfix on
	
如果提示无法找到 lokkit 命令，那么需要运行以下命令安装

	yum install lokkit

这里需要注意的是 lokkit 会把 iptables 打开，如果不想要 iptables 的话，可以进行关闭

	service iptables stop

第二步，就是下载 GitLab 安装包。下载地址：https://mirrors.tuna.tsinghua.edu.cn/gitlab-ce/yum/el7

	wget https://mirrors.tuna.tsinghua.edu.cn/gitlab-ce/yum/el7/gitlab-ce-11.4.5-ce.0.el7.x86_64.rpm
	
下载好后，进行安装

	rpm -Uvh gitlab-ce-11.4.5-ce.0.el7.x86_64.rpm
	
修改 GitLab 配置文件指定服务器ip和自定义端口

	vim  /etc/gitlab/gitlab.rb
	
指定访问ip及端口用号

external-url 'http://www.xxx.com'

保存并退出，执行以下命令更新配置。

	sudo gitlab-ctl reconfigure

最后，根据上面配置的 external-url 就可以访问 GitLab 了。

Jenkins
=======

安装 Jenkins 是需要 Java 环境的，这里就不讲 Linux 系统安装 Java 了，有需要的可以自行百度。

Jenkins 安装教程：https://wiki.jenkins.io/display/JENKINS/Installing+Jenkins+on+Red+Hat+distributions#InstallingJenkinson

选择最新版 ，使用 yum 方式下载安装

	sudo wget -O /etc/yum.repos.d/jenkins.repo http://pkg.jenkins-ci.org/redhat/jenkins.repo
	sudo rpm --import https://jenkins-ci.org/redhat/jenkins-ci.org.key
	sudo yum install jenkins

接下来配置 Jenkins 端口

	vi /etc/sysconfig/jenkins

查找/JENKINS_PORT，修改JENKINS_PORT="8080"，默认为“8080”，我修改为了9090。/JENKINS_LISTEN_ADDRESS 是对应 Jenkins 的 ip ，默认是 0.0.0.0 。

启动 Jenkins
	
	service jenkins restart
	
在浏览器中输入 Jenkins 的网址，就可以使用了。


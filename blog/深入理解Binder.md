title: 深入理解Binder
date: 2019-05-21 23:26:43
categories: Android Blog
tags: [Android,IPC,Binder]
---
之前一直对 Binder 理解不够透彻，仅仅知道一些皮毛，所以最近抽空深入理解一下，并在这里做个小结。

Binder是什么
===========
Binder 是 Android 系统中实现 IPC (进程间通信)的一种机制。Binder 原意是“胶水、粘合剂”，所以可以想象它的用途就是像胶水一样把两个进程紧紧“粘”在一起，从而可以方便地实现 IPC 。

进程通信
=======
那么为什么会有进程通信呢？这是因为在 Linux 中进程之间是隔离的，也就是说 A 进程不知道有 B 进程的存在，相应的 B 进程也不知道 A 进程的存在。A 、B 两进程的内存是不共享的，所以 A 进程的数据想要传给 B 进程就需要用到 IPC 。

在这里再科普一下进程空间的知识点：进程空间可以分为用户空间和内核空间。简单的说，用户空间是用户程序运行的空间，而内核空间就是内核运行的空间了。因为像内核这么底层、至关重要的东西肯定是不会简单地让用户程序随便调用的，所以需要把内核保护起来，就创造了内核空间，让内核运行在内核空间中，这样就不会被用户空间随便干扰到了。两个进程之间的用户空间是不共享的，但是内核空间是共享的。

所以到这里，有些同学会有个大胆的想法，两个进程间的通信可以利用内核空间来实现啊，因为它们的内核空间是共享的，这样数据不就传过去了嘛。但是接着又来了一个问题：为了保证安全性，用户空间和内核空间也是隔离的。那么如何把数据从发送方的用户空间传到内核空间呢？

针对这个问题提供了**系统调用**来解决，可以让用户程序调用内核资源。系统调用是用户空间访问内核空间的唯一方式，保证了所有的资源访问都是在内核的控制下进行的，避免了用户程序对系统资源的越权访问，提升了系统安全性和稳定性(这段话来自[《写给 Android 应用工程师的 Binder 原理剖析》](https://juejin.im/post/5acccf845188255c3201100f))。我们平时的网络、I/O操作其实都是通过系统调用在内核空间中运行的（也就是**内核态**）。

至此，关于 IPC 我们有了一个大概的实现方案：A 进程的数据通过系统调用把数据传输到内核空间（即copy_from_user），内核空间再利用系统调用把数据传输到 B 进程（即 copy_to_user）。这也正是目前 Linux 中传统 IPC 通信的实现原理，可以看到这其中会有两次数据拷贝。

![IPC原理](/uploads/20190521/20190521235434.jpg)

(图片来自于[《写给 Android 应用工程师的 Binder 原理剖析》](https://juejin.im/post/5acccf845188255c3201100f))

Linux 中的一些 IPC 方式：

1. 管道（Pipe）
2. 信号（Signal）
3. 报文（Message）队列（消息队列）
4. 共享内存
5. 信号量（semaphore）
6. 套接字（Socket）

Binder IPC 原理
==============
通过上面的讲解我们可以知道，IPC 是需要内核空间来支持的。Linux 中的管道、socket 等都是在内核中的。但是在 Linux 系统里面是没有 Binder 的。那么 Android 中是如何利用 Binder 来实现 IPC 的呢？

这就要讲到 Linux 中的**动态内核可加载模块**。动态内核可加载模块是具有独立功能的程序，它可以被单独编译，但是不能独立运行。它在运行时被链接到内核作为内核的一部分运行。这样，Android 系统就可以通过动态添加一个内核模块运行在内核空间，用户进程之间通过这个内核模块作为桥梁来实现通信。（这段话来自[《写给 Android 应用工程师的 Binder 原理剖析》](https://juejin.im/post/5acccf845188255c3201100f)）在 Android 中，这个内核模块也就是 Binder 驱动。

另外，Binder IPC 原理相比较上面传统的 Linux IPC 而言，只需要一次数据拷贝就可以完成了。那么究竟是怎么做到的呢？

其实 Binder 是借助于 mmap （内存映射）来实现的。mmap 用于文件或者其它对象映射进内存，通常是用在有物理介质的文件系统上的。mmap 简单的来说就是可以把用户空间的内存区域和内核空间的内存区域之间建立映射关系，这样就减少了数据拷贝的次数，任何一方的对内存区域的改动都将被反应给另一方。

所以，Binder 的做法就是建立一个虚拟设备（设备驱动是/dev/binder），然后在内核空间创建一块数据接收的缓存区，这个缓存区会和内存缓存区以及接收数据进程的用户空间建立映射，这样发送数据进程把数据发送到内存缓存区，该数据就会被间接映射到接收进程的用户空间中，减少了一次数据拷贝。具体可以看下图理解

![Binder IPC原理](/uploads/20190521/20190522105623.jpg)

(图片来自于[《写给 Android 应用工程师的 Binder 原理剖析》](https://juejin.im/post/5acccf845188255c3201100f))

为什么选择Binder
==============
Binder 的优点

1. **效率高，性能好**：传统的 Linux 下 IPC 通信都需要两次数据拷贝，即一次 copy_from_user 和一次 copy_to_user ，而 binder 只需要一次拷贝；
2. **安全性高**：Binder 可以做安全校验，如果没有相应权限可以拒绝提供连接。在底层为每个 app 添加UID/PID，鉴别进程身份；
3. **稳定性高**：Binder 是 C/S 架构，Client 端和 Server 端分工明确，互不干扰。并且 Client 端可以设置死亡通知，及时监听 Server 端的存活情况；
4. **使用简单，对开发者友好**：Binder 封装了底层 IPC 通信，让开发者无需关心底层细节，也无需关心 Server 端的实现细节。只需要面向 binder 对象就可以完成 IPC 通信，简单无脑。

Binder通信过程
=============
在整个 Binder 通信过程中，可以分为四个部分：

* Client : 即客户端进程；
* Server : 即服务端进程；
* Binder 驱动 : 驱动负责进程之间 Binder 通信的建立，Binder 在进程之间的传递，Binder 引用计数管理，数据包在进程之间的传递和交互等一系列底层支持；(来自[《Android Binder 设计与实现》](https://blog.csdn.net/universus/article/details/6211589))
* ServiceManager : 作用是将字符形式的 Binder 名字转化成 Client 中对该 Binder 的引用，使得 Client 能够通过 Binder 的名字获得对 Binder 实体的引用。(来自[《Android Binder 设计与实现》](https://blog.csdn.net/universus/article/details/6211589))

其中 Client 和 Server 是应用层实现的，而 Binder 驱动和 ServiceManager 是 Android 系统底层实现的。

具体流程如下：

1. 首先由一个进程使用 BINDER_SET_CONTEXT_MGR 命令通过 Binder 驱动将自己注册为 ServiceManager 。
2. Server 进程向 Binder 驱动发起 Binder 注册的请求，驱动为这个 Binder 创建位于内核中的实体节点以及 ServiceManager 对实体的引用，将名字以及新建的引用打包传给 ServiceManager，ServiceManger 将其填入查找表。
3. Client 通过服务名称，在 Binder 驱动的帮助下从 ServiceManager 中获取到对 Binder 实体的引用，通过这个引用就能实现和 Server 进程的通信。
4. 然后 Binder 驱动为跨进程通信做准备，Binder 驱动在内核中创建接收缓存区，并将接收缓存区与内核缓存区、接收进程的用户空间做内存映射。
5. Client 进程调用 copy_from_user 将数据发送到内核缓存区（Client 进程中当前的线程将被挂起），因为之前做了内存映射，所以这就相当于把数据间接发送到了 Server 端。然后 Binder 驱动通知 Server 解包；
6. 收到 Binder 驱动的通知后，Server 进程从线程池中取出线程，进行数据解包并调用相关的目标方法，最后将方法执行的返回值写入到内存中；
7. 又因为之前做了内存映射，所以方法的返回值就间接地发送到了内核缓存区中，最后 Binder 驱动通知 Client 进程获取方法的返回值（此时 Client 进程被唤醒），然后 Client 进程调用 copy_to_user 将返回值发送到自己的用户空间中。

![Binder通信过程](/uploads/20190521/20190530122134.jpg)

(Binder通信过程示意图来自于[《写给 Android 应用工程师的 Binder 原理剖析》](https://juejin.im/post/5acccf845188255c3201100f))

Binder原理详解
=============
* [图文详解 Android Binder跨进程通信的原理](https://www.jianshu.com/p/4ee3fd07da14)
* [一篇文章了解相见恨晚的 Android Binder 进程间通讯机制](https://blog.csdn.net/freekiteyu/article/details/70082302)

参考
====
* [写给 Android 应用工程师的 Binder 原理剖析](https://juejin.im/post/5acccf845188255c3201100f)
* [图文详解 Android Binder跨进程通信的原理](https://www.jianshu.com/p/4ee3fd07da14)
* [Android Binder设计与实现 - 设计篇](https://blog.csdn.net/universus/article/details/6211589)
* [Binder学习指南](https://www.jianshu.com/p/af2993526daf)
* [一篇文章了解相见恨晚的 Android Binder 进程间通讯机制](https://blog.csdn.net/freekiteyu/article/details/70082302)



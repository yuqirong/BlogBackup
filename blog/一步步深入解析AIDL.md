title: 一步步深入解析AIDL
date: 2016-07-28 22:55:37
categories: Android Blog
tags: [Android,IPC]
---
前言
======
在 Android 系统中，进程间通信 (IPC) 是一种很重要的机制。IPC 产生的原因是某些情况下需要在两个进程之间进行一些数据的交换。而在深入学习 Android 的过程中难免会遇到 IPC 的相关问题，比如常见的有在自己的应用程序中读取手机联系人的信息，这就涉及到 IPC 了。因为自己的应用程序是一个进程，通讯录也是一个进程，只不过获取通讯录的数据信息是通过 Content Provider 的方式来实现的。

对于初学者来说，在一开始接触 IPC 时可能会摸不着头脑，因为网上很多博客在讲 Android IPC 时通常都是长篇大论，没有从例子着手。基于以上种种原因以及希望对 AIDL 有一个更深入的理解，本篇博文就诞生了。在 Android 系统中，IPC 的方式有很多种，比如有 Messenger 、AIDL 和 ContentProvider 等。我们今天就来讲讲其中的 AIDL ，AIDL 也是比较常见和经常使用的一种 IPC 方式。希望读者在看完本篇之后对于 AIDL 有一个比较深入的理解。

什么是 AIDL
====
首先我们对于新的事物都会有一个疑问，那就是什么是 AIDL？

AIDL 的全称是 Android Interface Definition Language(即 Android 接口定义语言)。通常对于 AIDL 的使用有三步流程：

1. 定义 AIDL 接口；
2. 在 Service 中创建对应的 Stub 对象；
3. 将该服务暴露给其他进程调用；

讲完了流程，我们就又有一个疑问了，Android系统中实现 IPC 有这么多方式，到底应该在什么情况下使用 AIDL 呢？

Android 官方文档给出的答案是：

Note: Using AIDL is necessary only if you allow clients from different applications to access your service for IPC and want to handle multithreading in your service. If you do not need to perform concurrent IPC across different applications, you should create your interface by implementing a Binder or, if you want to perform IPC, but do not need to handle multithreading, implement your interface using a Messenger. Regardless, be sure that you understand Bound Services before implementing an AIDL.

使用AIDL只有在你允许来自不同应用的客户端跨进程通信访问你的Service，并且想要在你的Service种处理多线程的时候才是必要的。 简单地来说，就是多个客户端，多个线程并发的情况下要使用 AIDL 。官方文档还指出，如果你的 IPC 不需要适用于多个客户端的，那就使用 Binder ；如果你的想要 IPC ，但是不需要多线程，那就选择 Messenger 。

相信大家到这里对于 AIDL 有一个初步的概念了，那么下面我们就来举个例子讲解一下 AIDL 。

AIDL的使用方法
====
我们来模拟一下需要进行 IPC 的情况，现在有客户端和服务端，客户端通过 AIDL 来和服务端进行 IPC 。我们假定现在客户端需要传一个 Person 类的对象给服务端，之后服务端回传给客户端一个 Person 类的集合。

先来看看服务端的相关代码，以下 Person.aidl 文件：

``` java
// Person.aidl
package com.yuqirong.aidldemo;

// Declare any non-default types here with import statements

parcelable Person;
```

注意在 IPC 机制中传递的自定义对象需要序列化，所以要实现 Parcelable 接口。在 AIDL 文件中使用 `parcelable` 关键字声明。有了 Person.aidl 之后，我们就要创建 AIDL 接口了。

``` java
// IMyAidlInterface.aidl
package com.yuqirong.aidldemo;

// Declare any non-default types here with import statements
import com.yuqirong.aidldemo.Person;

interface IMyAidlInterface {
    /**
     * Demonstrates some basic types that you can use as parameters
     * and return values in AIDL.
     */
    List<Person> addPerson(in Person person);
}

```

在 IMyAidlInterface.aidl 里，主要声明一个用于添加 Person 对象的抽象方法。另外，需要注意以下几点：

1. Person 类需要手动去 import ，在 AIDL 文件中不能自动导包；
2. 在 `addPerson` 方法里需要声明参数是 in 的，用来表示该参数是传入的。除了 in 之外，还有 out 和 inout ；

下面我们要创建一个 Service 用于和客户端进行 IPC 。这里还要把该 Service 运行在一个新的进程里。我们只要在 AndroidManifest.xml 中声明 `android:process=":remote"` 就行了。

``` java
public class MyService extends Service {

    private static final String TAG = "MyService";

    private List<Person> persons = new ArrayList<>();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG,"bind success");
        return mBinder;
    }

    private IBinder mBinder = new IMyAidlInterface.Stub() {

        @Override
        public List<Person> addPerson(Person person) throws RemoteException {
            // 需要同步
            synchronized (persons){
                persons.add(person);
                return persons;
            }
        }
    };
    
}
```

在上面的代码中我们可以看到在 `onBind(Intent intent)` 方法中返回了 mBinder ，而客户端正是通过这个 mBinder 来和服务端进行 IPC 的。mBinder 是 IMyAidlInterface.Stub 匿名类的对象，IMyAidlInterface.Stub 其实是一个抽象类，继承自 Binder ，实现 `addPerson` 方法。这里要注意以下，在 `addPerson` 的方法中需要将 persons 同步，这是因为在服务端 AIDL 是运行在 Binder 线程池中的，有可能会有多个客户端同时连接，这时候就需要同步以防止数据出错。

服务端的代码差不多就这些，下面我们来看看客户端的，客户端也是需要 AIDL 文件的，可以从服务端中复制过来。需要注意的是包名和 AIDL 文件都要和服务端保持一致，否则在客户端反序列化的时候会出错。以下只截取了客户端部分关键代码。

``` java
// 客户端用来和服务端IPC的接口
private IMyAidlInterface aidlInterface;

// 启动服务端的服务，并进行绑定
Intent intent = new Intent();
intent.setComponent(new ComponentName("com.yuqirong.aidldemo", "com.yuqirong.aidldemo.MyService"));
bindService(intent, conn, Context.BIND_AUTO_CREATE);

private ServiceConnection conn = new ServiceConnection() {
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        // 在这里得到了和服务端进行通信的接口
        aidlInterface = IMyAidlInterface.Stub.asInterface(service);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        aidlInterface = null;
    }
};
```

客户端通过 Intent 启动并绑定服务端的 Service ，在 `onServiceConnected` 中通过 binder 对象得到了 aidlInterface 。之后客户端就可以使用 aidlInterface 了：

``` java
new Thread(new Runnable() {
    @Override
    public void run() {
        try {
            List<Person> list = aidlInterface.addPerson(new Person("yuqirong", 21, "13567891023"));
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
}).start();
```

我们注意到，在客户端调用 AIDL 接口方法时是新创建了一个子线程去执行的，这是因为在服务端在处理 AIDL 时有可能是很耗时的。如果在主线程中去执行，那么就有可能出现 ANR 的问题。所以为了避免 ANR ，在客户端调用 AIDL 的代码最好在子线程去执行。

整套 AIDL 的流程基本上就是这样的。通过这个简单的例子，相信对于 AIDL 有了一个初步的了解。下面我们就要去揭开 AIDL 是如何实现 IPC 的神秘面纱。

解析AIDL
====
现在我们终于要来看看 AIDL 是如何工作的？我们可以在工程中的 gen 目录下找到对应 AIDL 编译后的文件：

``` java
package com.yuqirong.aidldemo;
public interface IMyAidlInterface extends android.os.IInterface
{
    /** Local-side IPC implementation stub class. */
    public static abstract class Stub extends android.os.Binder implements com.yuqirong.aidldemo.IMyAidlInterface
    {
        private static final java.lang.String DESCRIPTOR = "com.yuqirong.aidldemo.IMyAidlInterface";
        /** Construct the stub at attach it to the interface. */
        public Stub()
        {
            this.attachInterface(this, DESCRIPTOR);
        }
        /**
         * Cast an IBinder object into an com.yuqirong.aidldemo.IMyAidlInterface interface,
         * generating a proxy if needed.
         */
        public static com.yuqirong.aidldemo.IMyAidlInterface asInterface(android.os.IBinder obj)
        {
            if ((obj==null)) {
                return null;
            }
            android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (((iin!=null)&&(iin instanceof com.yuqirong.aidldemo.IMyAidlInterface))) {
                return ((com.yuqirong.aidldemo.IMyAidlInterface)iin);
            }
            return new com.yuqirong.aidldemo.IMyAidlInterface.Stub.Proxy(obj);
        }
        @Override public android.os.IBinder asBinder()
        {
            return this;
        }
        @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
        {
            switch (code)
            {
                case INTERFACE_TRANSACTION:
                {
                    reply.writeString(DESCRIPTOR);
                    return true;
                }
                case TRANSACTION_addPerson:
                {
                    data.enforceInterface(DESCRIPTOR);
                    com.yuqirong.aidldemo.Person _arg0;
                    if ((0!=data.readInt())) {
                        _arg0 = com.yuqirong.aidldemo.Person.CREATOR.createFromParcel(data);
                    }
                    else {
                        _arg0 = null;
                    }
                    java.util.List<com.yuqirong.aidldemo.Person> _result = this.addPerson(_arg0);
                    reply.writeNoException();
                    reply.writeTypedList(_result);
                    return true;
                }
            }
            return super.onTransact(code, data, reply, flags);
        }
        private static class Proxy implements com.yuqirong.aidldemo.IMyAidlInterface
        {
            private android.os.IBinder mRemote;
            Proxy(android.os.IBinder remote)
            {
                mRemote = remote;
            }
            @Override public android.os.IBinder asBinder()
            {
                return mRemote;
            }
            public java.lang.String getInterfaceDescriptor()
            {
                return DESCRIPTOR;
            }
            /**
             * Demonstrates some basic types that you can use as parameters
             * and return values in AIDL.
             */
            @Override public java.util.List<com.yuqirong.aidldemo.Person> addPerson(com.yuqirong.aidldemo.Person person) throws android.os.RemoteException
            {
                android.os.Parcel _data = android.os.Parcel.obtain();
                android.os.Parcel _reply = android.os.Parcel.obtain();
                java.util.List<com.yuqirong.aidldemo.Person> _result;
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    if ((person!=null)) {
                        _data.writeInt(1);
                        person.writeToParcel(_data, 0);
                    }
                    else {
                        _data.writeInt(0);
                    }
                    mRemote.transact(Stub.TRANSACTION_addPerson, _data, _reply, 0);
                    _reply.readException();
                    _result = _reply.createTypedArrayList(com.yuqirong.aidldemo.Person.CREATOR);
                }
                finally {
                    _reply.recycle();
                    _data.recycle();
                }
                return _result;
            }
        }
        static final int TRANSACTION_addPerson = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    }
    /**
     * Demonstrates some basic types that you can use as parameters
     * and return values in AIDL.
     */
    public java.util.List<com.yuqirong.aidldemo.Person> addPerson(com.yuqirong.aidldemo.Person person) throws android.os.RemoteException;
}
```

可以看到编译后的 IMyAidlInterface.aidl 变成了一个接口，继承自 IInterface 。在 IMyAidlInterface 接口中我们发现主要分成两部分结构：抽象类 Stub 和原来 aidl 中声明的 `addPerson` 方法。

重点在于 Stub 类，下面我们来分析一下。从 Stub 类中我们可以看到是继承自 Binder 并且实现了 IMyAidlInterface 接口。 Stub 类的基本结构如下：

*  `asInterface(android.os.IBinder obj)` 方法；
*  `asBinder()` 方法；
*  `onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags)` 方法；
*  静态类 `Proxy`，主要方法是 `addPerson(com.yuqirong.aidldemo.Person person)` ；
*  静态常量 `TRANSACTION_addPerson` ；

**asInterface(android.os.IBinder obj)**

我们先从 `asInterface(android.os.IBinder obj)` 方法入手，在上面的代码中可以看到，主要的作用就是根据传入的Binder对象转换成客户端需要的 IMyAidlInterface 接口。如果服务端和客户端处于同一个进程，那么该方法得到的就是服务端 Stub 对象本身，也就是上面 AIDL 例子 MyService 中的 mBinder 对象；否则返回的是系统封装后的 Stub.Proxy ，也就是一个代理类，在这个代理中实现跨进程通信。

**asBinder()**

该方法就是返回当前的 Binder 对象。

**onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags)**

在 `onTransact` 方法中，根据传入的 code 值会去执行服务端相对应的方法。其中静态变量 `TRANSACTION_addPerson` 就是其中的 code 值之一(在 AIDL 文件中声明的方法有多少个就有多少个对应的 code )。其中 data 就是服务端方法中所需要的参数，执行完后，最后把方法的返回结果放入 reply 中传递给客户端。若该方法返回 false ，那么客户端请求失败。

**Proxy中的addPerson(com.yuqirong.aidldemo.Person person)**

Proxy 类是实现了 IMyAidlInterface 接口，把其中的 `addPerson` 方法进行了重写。在方法中一开始创建了两个 Parcel 对象，其中一个用来把方法的参数装入，然后调用 `transact` 方法执行服务端的代码，执行完后把返回的结果装入另外一个 Parcel 对象中返回。

看完上面方法的介绍，我们回过头来看看 AIDL 例子中实现的流程。在客户端中通过 Intent 去绑定一个服务端的 Service 。在 `onServiceConnected(ComponentName name, IBinder service)` 方法中通过返回的 service 可以得到对应的 AIDL 接口的实例。这是调用了 `asInterface(android.os.IBinder obj)` 方法来完成的。

客户端在 `onServiceConnected(ComponentName name, IBinder service)` 中得到的 service 正是服务端中的 mBinder 。当客户端调用 AIDL 接口时，AIDL 通过 Proxy 类中的 `addPerson` 来调用 `transact` 方法，`transact` 方法又会去调用服务端的 `onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags)` 方法。 `onTransact` 方法是运行在服务端的 Binder 线程池中的。在 `onTransact` 中根据 code 执行相关 AIDL 接口的方法，方法的参数从 data 中获取。执行完毕之后把结果装入 reply 中返回给客户端。 AIDL 的流程基本上就是这样子了。

到了这里，大家就会发现 AIDL 底层的实现就是依靠 Binder 来完成的。为了方便大家的理解，这里给出一张 AIDL 机制的原理图( PS :该图来自于《Android开发艺术探索》，感谢任主席)：

![AIDL机制原理图](/uploads/20160728/20160728201234.png)

结尾
====
写到这里本篇博文就临近末尾了。 AIDL 在 Android IPC 机制中算得上是很重要的一部分，AIDL 主要是通过 Binder 来实现进程通信的。其实另一种 IPC 的方式 Messenger 底层也是通过 AIDL 来实现的。所以 AIDL 的重要性就不言而喻了。如果有兴趣的同学可以在理解 AIDL 的基础上去看看 Messenger 的源码。当然在上面的 AIDL 例子中的代码是很简单的，没有涉及到死亡代理、权限验证等功能，童鞋们可以自己去把这些相关的去学习下。

好了，最后附上 AIDL 例子的源码：

[AIDLDemo.rar](/uploads/20160728/AIDLDemo.rar)

Goodbye !

References
====
[《Android开发艺术探索》笔记(上) —— 第二章：IPC机制][url]

[url]: /2016/03/31/《Android开发艺术探索》笔记(上)/
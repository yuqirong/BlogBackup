title: android-architecture之todo-mvp源码分析
date: 2017-02-22 21:08:02
categories: Android Blog
tags: [Android,源码解析,Android架构]
---
Android 架构一直都是热门话题，从一开始的 MVC ，到目前火爆的 MVP ，再到方兴未艾的 MVVM 。并不能说哪一种架构最好，因为这些架构都顺应了当时开发的趋势。在这里就不对这三个架构一一解释了，如果想要了解更多的同学可以自行搜索。

自从 2015 下半年来，MVP 渐渐崛起成为了现在普遍流行的架构模式。但是各种不同实现方式的 MVP 架构层出不穷，也让新手不知所措。而 Google 作为“老大哥”，针对此现象为 Android 架构做出了“规范示例”：[android-architecture](https://github.com/googlesamples/android-architecture) 。 

目前已有的架构示例如下图所示：

![stable sample](/uploads/20170222/20170222230352.png)

而今天给大家带来的就是分析 [todo-mvp](https://github.com/googlesamples/android-architecture/tree/todo-mvp/) 项目的架构。那就快进入正题吧！

todo-mvp
========
先来看看项目包的目录结构：

![目录结构](/uploads/20170222/20170227212108.png)

基本上目录结构可以分为四种：

1. addedittask、statistics、taskdetail、tasks ：可以看出在 [todo-mvp](https://github.com/googlesamples/android-architecture/tree/todo-mvp/) 项目中是按功能来分包的，这些包中的结构都是一致的，待会我们只需要分析其中一个包即可；
2. data ：该分包下主要是数据层的代码，即 MVP 中的 Model 层；
3. util ：工具类包，在这里就不展开细讲了；
4. BaseView、BasePresenter ：MVP 中 View 和 Presenter 的基类。

然后是官方给出的 [todo-mvp](https://github.com/googlesamples/android-architecture/tree/todo-mvp/) 架构图：

![MVP](/uploads/20170222/20170228224356.png)

BaseView 和 BasePresenter
-------------------------
这里就先看一下 BaseView 的代码：

``` java
public interface BaseView<T> {

    void setPresenter(T presenter);

}
```

BaseView 是一个泛型接口，里面只有一个抽象方法 `setPresenter(T presenter)` ，用来设置 Presenter 。

然后是 BasePresenter 的代码：

``` java
public interface BasePresenter {

    void start();

}
```

BasePresenter 接口也只有一个抽象方法 `start()` ，用于在 Activity/Fragment 的 `onResume()` 方法中调用。

addedittask、statistics、taskdetail、tasks
----------------------------------------
这四个分包从结构上来讲都是一样的，那么在这里我们就分析 tasks 这个分包吧。下面是该分包下的源码文件：

![task分包的结构](/uploads/20170222/20170227215628.png)

我们以 `TasksContract` 为切入点：

``` java
public interface TasksContract {

    interface View extends BaseView<Presenter> {

        void setLoadingIndicator(boolean active);

        void showTasks(List<Task> tasks);

        void showAddTask();

        void showTaskDetailsUi(String taskId);

        ...
    }

    interface Presenter extends BasePresenter {

        void result(int requestCode, int resultCode);

        void loadTasks(boolean forceUpdate);

        void addNewTask();

        ...
    }
}
```

原来 `TasksContract` 接口其实就是用来定义 `View` 和 `Presenter` 的。 `View` 和 `Presenter` 继承了 `BaseView` 和 `BasePresenter` 。再回头看看上面的 `TaskPresenter` ，想必大家都猜到了，肯定是继承了 `Presenter` ：

``` java
public class TasksPresenter implements TasksContract.Presenter {

    private final TasksRepository mTasksRepository;

    private final TasksContract.View mTasksView;

    public TasksPresenter(@NonNull TasksRepository tasksRepository, @NonNull TasksContract.View tasksView) {
        mTasksRepository = checkNotNull(tasksRepository, "tasksRepository cannot be null");
        mTasksView = checkNotNull(tasksView, "tasksView cannot be null!");

        mTasksView.setPresenter(this);
    }

    @Override
    public void start() {
        loadTasks(false);
    }

	...
}
```

在 `TasksPresenter` 的构造方法中把 `tasksRepository` 和 `tasksView` 传入，并且把 `TasksPresenter` 对象设置给了 `mTasksView` 。这样，Presenter 就实现了 Model 和 View 的解耦。

data
----
data 代表了 MVP 中的 Model 。我们根据上面出现过的 `TasksRepository` 来分析。

``` java
public class TasksRepository implements TasksDataSource {

    private static TasksRepository INSTANCE = null;

    private final TasksDataSource mTasksRemoteDataSource;

    private final TasksDataSource mTasksLocalDataSource;

    private TasksRepository(@NonNull TasksDataSource tasksRemoteDataSource,
                            @NonNull TasksDataSource tasksLocalDataSource) {
        mTasksRemoteDataSource = checkNotNull(tasksRemoteDataSource);
        mTasksLocalDataSource = checkNotNull(tasksLocalDataSource);
    }

    public static TasksRepository getInstance(TasksDataSource tasksRemoteDataSource,
                                              TasksDataSource tasksLocalDataSource) {
        if (INSTANCE == null) {
            INSTANCE = new TasksRepository(tasksRemoteDataSource, tasksLocalDataSource);
        }
        return INSTANCE;
    }

	...
}
```

`TasksRepository` 实现了 `TasksDataSource` 接口，`TasksDataSource` 接口定义了一些对 `Task` 的增删改查操作。在 `TasksRepository` 的构造方法中传入两个 `TasksDataSource` 对象，其实是模拟了本地数据存储和网络数据存储两种方式。至于其他的就不详细展开了，无非就是对数据读写之类的操作。

End
===
到这里，基本上把 [todo-mvp](https://github.com/googlesamples/android-architecture/tree/todo-mvp/) 架构的代码大致地讲了一遍。本篇博客就不对其他的代码展开分析了，因为我们注重的是该项目中的 MVP 架构实现方式。另外，todo 系列还有其他几种 MVP 实现的方式，只能下次有空再讲了。

就到这吧，Goodbye !
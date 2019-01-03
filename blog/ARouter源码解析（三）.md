title: ARouter源码解析（三）
date: 2019-01-03 21:46:43
categories: Android Blog
tags: [Android,开源框架,源码解析]
---
arouter-api version : 1.4.1

前言
======
到现在为止，ARouter 还有最后的依赖注入还没有解析过，那么今天就来深入探究一下其实现原理。

PS : 因为依赖注入的原理还比较简单，所以本篇篇幅会较短。

@Autowired解析
==============
想要用 ARouter 实现依赖注入，需要在 Activity/Fragment 中加上

	ARouter.getInstance().inject(this);

那么我们这个代码就成为了我们分析的入口了。

``` java
public void inject(Object thiz) {
    _ARouter.inject(thiz);
}
```

ARouter 内部还是调用了 _ARouter 的 inject 方法。

``` java
static void inject(Object thiz) {
    AutowiredService autowiredService = ((AutowiredService) ARouter.getInstance().build("/arouter/service/autowired").navigation());
    // 如果 autowiredService 不为空，完成依赖注入
    if (null != autowiredService) {
        autowiredService.autowire(thiz);
    }
}
```

发现依赖注入和拦截器很相似，都是利用服务组件来完成的。依赖注入的服务组件叫 AutowiredService ，跟踪可以发现，它的实现类是 AutowiredServiceImpl 。

``` java
@Route(path = "/arouter/service/autowired")
public class AutowiredServiceImpl implements AutowiredService {
    private LruCache<String, ISyringe> classCache;
    private List<String> blackList;

    @Override
    public void init(Context context) {
        classCache = new LruCache<>(66);
        blackList = new ArrayList<>();
    }

    @Override
    public void autowire(Object instance) {
        String className = instance.getClass().getName();
        try {
            // 如果 instance 这个类进入黑名单了，就不会完成依赖注入
            if (!blackList.contains(className)) {
                // 先从缓存中取
                ISyringe autowiredHelper = classCache.get(className);
                // 没有缓存就创建对象
                if (null == autowiredHelper) {  // No cache.
                    autowiredHelper = (ISyringe) Class.forName(instance.getClass().getName() + SUFFIX_AUTOWIRED).getConstructor().newInstance();
                }
                // 完成依赖注入
                autowiredHelper.inject(instance);
                // 放入缓存中
                classCache.put(className, autowiredHelper);
            }
        } catch (Exception ex) {
            // 出错就加入黑名单中
            blackList.add(className);    // This instance need not autowired.
        }
    }
}
```

其中 ISyringe 就是依赖注入抽取出来的接口，

```
public interface ISyringe {
    void inject(Object target);
}
```

那么 ISyringe 的实现类又是谁呢？答案就是在编译期自动生成的类 `XXXX$$ARouter$$Autowired` ，我们找 demo 中生成的 `Test1Activity$$ARouter$$Autowired` 来看看

``` java
public class Test1Activity$$ARouter$$Autowired implements ISyringe {
  private SerializationService serializationService;

  @Override
  public void inject(Object target) {
    serializationService = ARouter.getInstance().navigation(SerializationService.class);
    Test1Activity substitute = (Test1Activity)target;
    substitute.name = substitute.getIntent().getStringExtra("name");
    substitute.age = substitute.getIntent().getIntExtra("age", substitute.age);
    substitute.height = substitute.getIntent().getIntExtra("height", substitute.height);
    substitute.girl = substitute.getIntent().getBooleanExtra("boy", substitute.girl);
    substitute.ch = substitute.getIntent().getCharExtra("ch", substitute.ch);
    substitute.fl = substitute.getIntent().getFloatExtra("fl", substitute.fl);
    substitute.dou = substitute.getIntent().getDoubleExtra("dou", substitute.dou);
    substitute.ser = (com.alibaba.android.arouter.demo.testinject.TestSerializable) substitute.getIntent().getSerializableExtra("ser");
    substitute.pac = substitute.getIntent().getParcelableExtra("pac");
    if (null != serializationService) {
      substitute.obj = serializationService.parseObject(substitute.getIntent().getStringExtra("obj"), new com.alibaba.android.arouter.facade.model.TypeWrapper<TestObj>(){}.getType());
    } else {
      Log.e("ARouter::", "You want automatic inject the field 'obj' in class 'Test1Activity' , then you should implement 'SerializationService' to support object auto inject!");
    }
    if (null != serializationService) {
      substitute.objList = serializationService.parseObject(substitute.getIntent().getStringExtra("objList"), new com.alibaba.android.arouter.facade.model.TypeWrapper<List<TestObj>>(){}.getType());
    } else {
      Log.e("ARouter::", "You want automatic inject the field 'objList' in class 'Test1Activity' , then you should implement 'SerializationService' to support object auto inject!");
    }
    if (null != serializationService) {
      substitute.map = serializationService.parseObject(substitute.getIntent().getStringExtra("map"), new com.alibaba.android.arouter.facade.model.TypeWrapper<Map<String, List<TestObj>>>(){}.getType());
    } else {
      Log.e("ARouter::", "You want automatic inject the field 'map' in class 'Test1Activity' , then you should implement 'SerializationService' to support object auto inject!");
    }
    substitute.url = substitute.getIntent().getStringExtra("url");
    substitute.helloService = ARouter.getInstance().navigation(HelloService.class);
  }
}
```

从上面自动生成的代码中看出来，依赖注入实际上内部还是使用 `getIntent.getXxxExtra` 的形式来赋值的（同理，Fragment 用的是`getArguments().getXxx()` ）。需要注意的是，@Autowired 修饰的字段不能是 private 的，不然在自动生成代码的时候会报错。

另外，上面的代码中有一个 SerializationService 是用来干什么的？其实 SerializationService 是 json 序列化用的。在 demo 中官方给出了一个实现类 JsonServiceImpl ，内部用的是阿里的 fastjson 。如果有需要自定义的童鞋，可以参照着 JsonServiceImpl 自己去实现。

结束
===
看到这，基本上 ARouter 依赖注入的东西就讲完了。

这一系列下来，ARouter 代码层面的流程都讲的差不多。剩下就是 gradle-plugin 和 compiler 这两个部分还没解析过，等时间了再给大家讲。

bye bye



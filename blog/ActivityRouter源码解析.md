title: ActivityRouter源码解析
date: 2018-07-22 22:32:15
categories: Android Blog
tags: [Android,源码解析]
---
ActivityRouter ：https://github.com/mzule/ActivityRouter

Header
======
在如今的 Android 组件化开发中，一款好的路由框架是不可或缺的。比如目前阿里的 ARouter 、美团的 WMRouter 等。路由框架可以降低 Activity 之间的耦合，从而在不需要关心目标 Activity 的具体实现类， 利用协议完成跳转。

ActivityRouter使用方法
=====================
在AndroidManifest.xml配置

	<activity
	    android:name="com.github.mzule.activityrouter.router.RouterActivity"
	    android:theme="@android:style/Theme.NoDisplay">
	    <intent-filter>
	        <action android:name="android.intent.action.VIEW" />
	        <category android:name="android.intent.category.DEFAULT" />
	        <category android:name="android.intent.category.BROWSABLE" />
	        <data android:scheme="mzule" /><!--改成自己的scheme-->
	    </intent-filter>
	</activity>


在需要配置的Activity上添加注解

	@Router("main")
	public class MainActivity extends Activity {
		...
	}
	
想要跳转到 MainActivity ，只要调用以下代码即可

	Routers.open(context, "mzule://main")
	
如果想用 @Router 来调用方法

	@Router("logout")
	public static void logout(Context context, Bundle bundle) {
	    Toast.makeText(context, "logout", Toast.LENGTH_SHORT).show();
	}

源码解析
======
ActivityRouter 工程的结构如下

![ActivityRouter](/uploads/20180722/20181216160032.png)

* activityrouter: 路由跳转的具体实现代码
* annotaition: 路由注解
* app: 路由 demo
* app_module: 路由 demo module
* compiler: 注解处理
* stub: 壳 module

annotation
----------
先来看看 Router 的注解

``` java
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.CLASS)
public @interface Router {

    String[] value();

    String[] stringParams() default "";

    String[] intParams() default "";

    String[] longParams() default "";

    String[] booleanParams() default "";

    String[] shortParams() default "";

    String[] floatParams() default "";

    String[] doubleParams() default "";

    String[] byteParams() default "";

    String[] charParams() default "";

    String[] transfer() default "";
}
```

@Router 定义了该 Activity 路由的名字以及一些参数，这里可以注意到 @Retention 是 CLASS ，所以后面肯定在编译期间利用 Processor 来解析 @Router 生成路由表的。

另外，看到 @Target 是 ElementType.TYPE 和 ElementType.METHOD ，其实 @Router 除了跳转 Activity 之外，还有一个功能就是可以执行方法，只要在方法加上 @Router 即可。

路由表的生成源码我们到后面再讲，先来看看有了协议之后，Routers 是如何实现跳转 Activity 的。

activityrouter
--------------
``` java
public class Routers {

	...

	public static boolean open(Context context, String url) {
	    return open(context, Uri.parse(url));
	}
	
	public static boolean open(Context context, String url, RouterCallback callback) {
	    return open(context, Uri.parse(url), callback);
	}
	
	public static boolean open(Context context, Uri uri) {
	    return open(context, uri, getGlobalCallback(context));
	}
	
	public static boolean open(Context context, Uri uri, RouterCallback callback) {
	    return open(context, uri, -1, callback);
	}
	
	public static boolean openForResult(Activity activity, String url, int requestCode) {
	    return openForResult(activity, Uri.parse(url), requestCode);
	}
	
	public static boolean openForResult(Activity activity, String url, int requestCode, RouterCallback callback) {
	    return openForResult(activity, Uri.parse(url), requestCode, callback);
	}
	
	public static boolean openForResult(Activity activity, Uri uri, int requestCode) {
	    return openForResult(activity, uri, requestCode, getGlobalCallback(activity));
	}
	
	public static boolean openForResult(Activity activity, Uri uri, int requestCode, RouterCallback callback) {
	    return open(activity, uri, requestCode, callback);
	}
	
	...

}
```

可以看到不同的 open openForResult 方法重载，最后都是调用了 `open(Context context, Uri uri, int requestCode, RouterCallback callback)` 。那么接着跟踪：

``` java
private static boolean open(Context context, Uri uri, int requestCode, RouterCallback callback) {
    boolean success = false;
    // 如果有 callback 在跳转前回调 
    if (callback != null) {
        if (callback.beforeOpen(context, uri)) {
            return false;
        }
    }
    // 执行路由跳转
    try {
        success = doOpen(context, uri, requestCode);
    } catch (Throwable e) {
        e.printStackTrace();
        if (callback != null) {
            // 错误回调
            callback.error(context, uri, e);
        }
    }
    // 成功或失败回调
    if (callback != null) {
        if (success) {
            callback.afterOpen(context, uri);
        } else {
            callback.notFound(context, uri);
        }
    }
    return success;
}
```

open 方法中有很多都是不同状态下 callback 的回调，真正跳转的逻辑放在了 doOpen 方法中。

``` java
private static boolean doOpen(Context context, Uri uri, int requestCode) {
    // 如果没有初始化的话，调用 Router.init 进行初始化路由表
    initIfNeed();
    // 解析 uri 得到对应的 path
    Path path = Path.create(uri);
    // 根据 path 去查找与之对应匹配的 mapping ，然后实现跳转
    for (Mapping mapping : mappings) {
        if (mapping.match(path)) {
            // 如果 activity 是空的，就说明是执行方法的
            if (mapping.getActivity() == null) {
                mapping.getMethod().invoke(context, mapping.parseExtras(uri));
                return true;
            }
            // 否则就是利用 intent 来跳转 activity
            Intent intent = new Intent(context, mapping.getActivity());
            intent.putExtras(mapping.parseExtras(uri));
            intent.putExtra(KEY_RAW_URL, uri.toString());
            if (!(context instanceof Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            if (requestCode >= 0) {
                if (context instanceof Activity) {
                    ((Activity) context).startActivityForResult(intent, requestCode);
                } else {
                    throw new RuntimeException("can not startActivityForResult context " + context);
                }
            } else {
                context.startActivity(intent);
            }
            return true;
        }
    }
    return false;
}
```

我们一步步来分析 doOpen 中的具体步骤。先从 `Path path = Path.create(uri);` 开始看。

``` java
public static Path create(Uri uri) {
    Path path = new Path(uri.getScheme().concat("://"));
    String urlPath = uri.getPath();
    if (urlPath == null) {
        urlPath = "";
    }
    if (urlPath.endsWith("/")) {
        urlPath = urlPath.substring(0, urlPath.length() - 1);
    }
    parse(path, uri.getHost() + urlPath);
    return path;
}

private static void parse(Path scheme, String s) {
    String[] components = s.split("/");
    Path curPath = scheme;
    for (String component : components) {
        Path temp = new Path(component);
        curPath.next = temp;
        curPath = temp;
    }
}
```

上面的代码看完可能会让有些同学感觉很绕，简单地解释下。上面这段代码主要做的事情就是把传入的 uri 解析，生成了一个 Path 对象。该 Path 对象主要包含了 uri 中的 scheme 、host 、path 这三部分，利用单链表的特点把这三部分串连起来。这个 Path 也就是后面用来匹配路由表用的。

可能还有一些同学对 uri 的 scheme 、 host 等不了解，在这里就简单地普及下。

比如现在有一个 uri 

	mzule://main/home/login?username=tom
	
这个 uri 就可以分解为

scheme ：mzule ，就是 “://” 前面的字符串
host ：main ，“://” 后面的字符串
path ：home 和 login 都属于 path，就是 “/” 与 “/” 之间的字符串
query ：参数，可以理解成键值对，多个之间用 & 连接。获取 username 这个参数，对应的值就是 tom

生成好了 Path 之后，就是遍历路由表进行匹配了。

所谓的路由表其实就是一个 List 

	private static List<Mapping> mappings = new ArrayList<>();

在调用 RouterInit.init 时候会把路由数据添加到 List 中。准确的说， RouterInit.init 中调用了 Router.map 方法来实现添加的。

``` java
static void map(String format, Class<? extends Activity> activity, MethodInvoker method, ExtraTypes extraTypes) {
    mappings.add(new Mapping(format, activity, method, extraTypes));
}
```

那么，我们来看下 Mapping 的结构

``` java
public class Mapping {
    private final String format;
    private final Class<? extends Activity> activity;
    private final MethodInvoker method;
    private final ExtraTypes extraTypes;
    private Path formatPath;
    
    ...
    
}
```

* format 就是我们传入的 uri
* activity 就是路由对应的 activity
* method 表示是否是执行方法
* extraTypes 是所携带的参数类型
* formatPath 就是 uri 对应的 Path

具体的 Mapping 初始化是在 Processor 生成的代码中完成的，我们到后面再讲。

在回过头来看 doOpen 方法，在 mapping.match(path) 方法中用来判断该 path 有没有匹配路由表中的路由

``` java
public boolean match(Path fullLink) {
    if (formatPath.isHttp()) {
        return Path.match(formatPath, fullLink);
    } else {
        // fullLink without host
        boolean match = Path.match(formatPath.next(), fullLink.next());
        if (!match && fullLink.next() != null) {
            // fullLink with host
            match = Path.match(formatPath.next(), fullLink.next().next());
        }
        return match;
    }
}
```
 
Mapping 的 match 方法就是把自身的 formatPath 和 fullLink 进行比较，最终调用的还是 Path.match 方法，本质就是把 Path 链表中的每一项进行比较，来判断两个 Path 是否相等。这里就不展示具体源码了，有兴趣的同学可以自己回去看。

再后面的就是判断 activity ，如果是空的，就认为是执行方法，否则就构造 Intent 来实现跳转，再利用 requestCode 来判断是 startActivity 还是 startActivityForResult 。其中执行方法主要调用了 MethodInvoker.invoke 方法

``` java
public interface MethodInvoker {
    void invoke(Context context, Bundle bundle);
}
```


再重点关注下 mapping.parseExtras(uri) 这句代码。这里主要做的事情就是构造 Bundle 传入 uri 的参数。

``` java
public Bundle parseExtras(Uri uri) {
    Bundle bundle = new Bundle();
    // path segments // ignore scheme
    Path p = formatPath.next();
    Path y = Path.create(uri).next();
    while (p != null) {
    		// 是否是 path 中传递参数
        if (p.isArgument()) {
            put(bundle, p.argument(), y.value());
        }
        p = p.next();
        y = y.next();
    }
    // 解析 uri 中的参数，放入 bundle 中
    Set<String> names = UriCompact.getQueryParameterNames(uri);
    for (String name : names) {
        String value = uri.getQueryParameter(name);
        put(bundle, name, value);
    }
    return bundle;
}

// 本方法主要做的事情就是根据参数名来判断参数类型
private void put(Bundle bundle, String name, String value) {
    int type = extraTypes.getType(name);
    name = extraTypes.transfer(name);
    if (type == ExtraTypes.STRING) {
        type = extraTypes.getType(name);
    }
    switch (type) {
        case ExtraTypes.INT:
            bundle.putInt(name, Integer.parseInt(value));
            break;
        case ExtraTypes.LONG:
            bundle.putLong(name, Long.parseLong(value));
            break;
        case ExtraTypes.BOOL:
            bundle.putBoolean(name, Boolean.parseBoolean(value));
            break;
        case ExtraTypes.SHORT:
            bundle.putShort(name, Short.parseShort(value));
            break;
        case ExtraTypes.FLOAT:
            bundle.putFloat(name, Float.parseFloat(value));
            break;
        case ExtraTypes.DOUBLE:
            bundle.putDouble(name, Double.parseDouble(value));
            break;
        case ExtraTypes.BYTE:
            bundle.putByte(name, Byte.parseByte(value));
            break;
        case ExtraTypes.CHAR:
            bundle.putChar(name, value.charAt(0));
            break;
        default:
            bundle.putString(name, value);
            break;
    }
}
```

这代码很简单，基本上都加了注释，相信大家都看得懂，就不讲咯。

到这里，整个 ActivityRouter 的流程就讲完啦。

剩下的，就是 Processor 解析注解生成代码了。

compiler
--------
先告诉处理器支持的注解

``` java
@Override
public Set<String> getSupportedAnnotationTypes() {
    Set<String> ret = new HashSet<>();
    ret.add(Modules.class.getCanonicalName());
    ret.add(Module.class.getCanonicalName());
    ret.add(Router.class.getCanonicalName());
    return ret;
}
```

剩下主要看 RouterProcessor 的 process 方法。

方法的代码如下：

``` java
@Override
public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    debug("process apt with " + annotations.toString());
    if (annotations.isEmpty()) {
        return false;
    }
    boolean hasModule = false;
    boolean hasModules = false;
    // module
    String moduleName = "RouterMapping";
    Set<? extends Element> moduleList = roundEnv.getElementsAnnotatedWith(Module.class);
    if (moduleList != null && moduleList.size() > 0) {
        Module annotation = moduleList.iterator().next().getAnnotation(Module.class);
        moduleName = moduleName + "_" + annotation.value();
        hasModule = true;
    }
    // modules
    String[] moduleNames = null;
    Set<? extends Element> modulesList = roundEnv.getElementsAnnotatedWith(Modules.class);
    if (modulesList != null && modulesList.size() > 0) {
        Element modules = modulesList.iterator().next();
        moduleNames = modules.getAnnotation(Modules.class).value();
        hasModules = true;
    }
    // RouterInit
    if (hasModules) {
        debug("generate modules RouterInit");
        generateModulesRouterInit(moduleNames);
    } else if (!hasModule) {
        debug("generate default RouterInit");
        generateDefaultRouterInit();
    }
    // RouterMapping
    return handleRouter(moduleName, roundEnv);
}
```

process 方法中的逻辑可以分为三部分：

* 判断是否有 @module 和 @modules ，即是否是组件化开发的
* 生成 RouterInit
* 生成 RouterMapping

那我们慢慢分析，先来看第一部分

``` java
    // module
    String moduleName = "RouterMapping";
    Set<? extends Element> moduleList = roundEnv.getElementsAnnotatedWith(Module.class);
    if (moduleList != null && moduleList.size() > 0) {
        Module annotation = moduleList.iterator().next().getAnnotation(Module.class);
        // 如果是多 module 组件化开发的话，每个 module 需要标注 @module ，这样每个module都会生成一个属于自己的 RouterMapping ，防止重复
        // 比如 @Module("abc") moduleName 就是 RouterMapping_abc
        moduleName = moduleName + "_" + annotation.value();
        hasModule = true;
    }
    // @Modules 的作用就是把上面生成的各个 RouterMapping 给汇总起来，统一到 RouterInit 里面，这样只要调用 RouterInit.init 方法就完成了各模块的路由初始化
    String[] moduleNames = null;
    Set<? extends Element> modulesList = roundEnv.getElementsAnnotatedWith(Modules.class);
    if (modulesList != null && modulesList.size() > 0) {
        Element modules = modulesList.iterator().next();
        // 比如@Modules("abc","def") , moduleNames 就是 [“abc”, "def"]
        moduleNames = modules.getAnnotation(Modules.class).value();
        hasModules = true;
    }
```

接下来就是生成 RouterInit 类

```
if (hasModules) {
    debug("generate modules RouterInit");
    generateModulesRouterInit(moduleNames);
} else if (!hasModule) {
    debug("generate default RouterInit");
    generateDefaultRouterInit();
}
```

如果是多 module 组件化开发，最终会调用 generateModulesRouterInit ，否则调用的就是默认的 generateDefaultRouterInit 。

这里我们就看 generateModulesRouterInit 的代码吧。

``` java
private void generateModulesRouterInit(String[] moduleNames) {
    MethodSpec.Builder initMethod = MethodSpec.methodBuilder("init")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL, Modifier.STATIC);
    for (String module : moduleNames) {
        initMethod.addStatement("RouterMapping_" + module + ".map()");
    }
    TypeSpec routerInit = TypeSpec.classBuilder("RouterInit")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addMethod(initMethod.build())
            .build();
    try {
        JavaFile.builder("com.github.mzule.activityrouter.router", routerInit)
                .build()
                .writeTo(filer);
    } catch (Exception e) {
        e.printStackTrace();
    }
}
```

可以看到，利用了 javapoet 来生成 java 代码，这代码很简单，就不用多讲啦，直接来看下最后生成 RouterInit 类的代码吧

``` java
package com.github.mzule.activityrouter.router;

public final class RouterInit {
  public static final void init() {
    RouterMapping_app.map();
    RouterMapping_sdk.map();
  }
}
```

RouterInit 生成好之后，最后的工作就是生成对应的 RouterMapping_app 和 RouterMapping_sdk 这两个类了。

生成的入口就是 handleRouter(moduleName, roundEnv) 方法。

```  java
private boolean handleRouter(String genClassName, RoundEnvironment roundEnv) {
    Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(Router.class);

    // 定义方法 public static final void map()
    MethodSpec.Builder mapMethod = MethodSpec.methodBuilder("map")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL, Modifier.STATIC)
            .addStatement("java.util.Map<String,String> transfer = null")
            .addStatement("com.github.mzule.activityrouter.router.ExtraTypes extraTypes")
            .addCode("\n");

    // 遍历 @Router 修饰的 element
    for (Element element : elements) {
        Router router = element.getAnnotation(Router.class);
        // 判断 @Router 中有没有 transfer
        String[] transfer = router.transfer();
        if (transfer.length > 0 && !"".equals(transfer[0])) {
            mapMethod.addStatement("transfer = new java.util.HashMap<String, String>()");
            for (String s : transfer) {
                String[] components = s.split("=>");
                if (components.length != 2) {
                    error("transfer `" + s + "` not match a=>b format");
                    break;
                }
                mapMethod.addStatement("transfer.put($S, $S)", components[0], components[1]);
            }
        } else {
            mapMethod.addStatement("transfer = null");
        }

        // 解析路由参数类型
        mapMethod.addStatement("extraTypes = new com.github.mzule.activityrouter.router.ExtraTypes()");
        mapMethod.addStatement("extraTypes.setTransfer(transfer)");

        addStatement(mapMethod, int.class, router.intParams());
        addStatement(mapMethod, long.class, router.longParams());
        addStatement(mapMethod, boolean.class, router.booleanParams());
        addStatement(mapMethod, short.class, router.shortParams());
        addStatement(mapMethod, float.class, router.floatParams());
        addStatement(mapMethod, double.class, router.doubleParams());
        addStatement(mapMethod, byte.class, router.byteParams());
        addStatement(mapMethod, char.class, router.charParams());

        // 遍历 @Router 生成所有路由的解析代码
        for (String format : router.value()) {
            ClassName className;
            Name methodName = null;
            if (element.getKind() == ElementKind.CLASS) {
                className = ClassName.get((TypeElement) element);
            } else if (element.getKind() == ElementKind.METHOD) {
                className = ClassName.get((TypeElement) element.getEnclosingElement());
                methodName = element.getSimpleName();
            } else {
                throw new IllegalArgumentException("unknow type");
            }
            if (format.startsWith("/")) {
                error("Router#value can not start with '/'. at [" + className + "]@Router(\"" + format + "\")");
                return false;
            }
            if (format.endsWith("/")) {
                error("Router#value can not end with '/'. at [" + className + "]@Router(\"" + format + "\")");
                return false;
            }
            // 如果 @Router 是修饰类的 就是路由跳转的
            if (element.getKind() == ElementKind.CLASS) {
                mapMethod.addStatement("com.github.mzule.activityrouter.router.Routers.map($S, $T.class, null, extraTypes)", format, className);
            } else {
                // 否则就是路由调用方法的，第三个参数传入 MethodInvoker 对象
                mapMethod.addStatement("com.github.mzule.activityrouter.router.Routers.map($S, null, " +
                        "new MethodInvoker() {\n" +
                        "   public void invoke(android.content.Context context, android.os.Bundle bundle) {\n" +
                        "       $T.$N(context, bundle);\n" +
                        "   }\n" +
                        "}, " +
                        "extraTypes)", format, className, methodName);
            }
        }
        mapMethod.addCode("\n");
    }
    TypeSpec routerMapping = TypeSpec.classBuilder(genClassName)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addMethod(mapMethod.build())
            .build();
    // 生成 RouterMapping_xxx 类
    try {
        JavaFile.builder("com.github.mzule.activityrouter.router", routerMapping)
                .build()
                .writeTo(filer);
    } catch (Throwable e) {
        e.printStackTrace();
    }
    return true;
}

// 生成 extraTypes 参数类型设置代码
// 比如 
// extraTypes.setLongExtra("id,updateTime".split(","));
// extraTypes.setBooleanExtra("web".split(","));
private void addStatement(MethodSpec.Builder mapMethod, Class typeClz, String[] args) {
    String extras = join(args);
    if (extras.length() > 0) {
        String typeName = typeClz.getSimpleName();
        String s = typeName.substring(0, 1).toUpperCase() + typeName.replaceFirst("\\w", "");

        mapMethod.addStatement("extraTypes.set" + s + "Extra($S.split(\",\"))", extras);
    }
}

```

来看一下最后生成的 RouterMapping_xxx 的代码：

``` java
public final class RouterMapping_app {
  public static final void map() {
    java.util.Map<String,String> transfer = null;
    com.github.mzule.activityrouter.router.ExtraTypes extraTypes;

    transfer = null;
    extraTypes = new com.github.mzule.activityrouter.router.ExtraTypes();
    extraTypes.setTransfer(transfer);
    com.github.mzule.activityrouter.router.Routers.map("user/:userId", UserActivity.class, null, extraTypes);
    com.github.mzule.activityrouter.router.Routers.map("user/:nickname/city/:city/gender/:gender/age/:age", UserActivity.class, null, extraTypes);

    transfer = new java.util.HashMap<String, String>();
    transfer.put("web", "fromWeb");
    extraTypes = new com.github.mzule.activityrouter.router.ExtraTypes();
    extraTypes.setTransfer(transfer);
    extraTypes.setLongExtra("id,updateTime".split(","));
    extraTypes.setBooleanExtra("web".split(","));
    com.github.mzule.activityrouter.router.Routers.map("http://mzule.com/main", MainActivity.class, null, extraTypes);
    com.github.mzule.activityrouter.router.Routers.map("main", MainActivity.class, null, extraTypes);
    com.github.mzule.activityrouter.router.Routers.map("home", MainActivity.class, null, extraTypes);

    transfer = null;
    extraTypes = new com.github.mzule.activityrouter.router.ExtraTypes();
    extraTypes.setTransfer(transfer);
    com.github.mzule.activityrouter.router.Routers.map("with_host", HostActivity.class, null, extraTypes);

    transfer = null;
    extraTypes = new com.github.mzule.activityrouter.router.ExtraTypes();
    extraTypes.setTransfer(transfer);
    com.github.mzule.activityrouter.router.Routers.map("home/:homeName", HomeActivity.class, null, extraTypes);

    transfer = null;
    extraTypes = new com.github.mzule.activityrouter.router.ExtraTypes();
    extraTypes.setTransfer(transfer);
    com.github.mzule.activityrouter.router.Routers.map("logout", null, new MethodInvoker() {
           public void invoke(android.content.Context context, android.os.Bundle bundle) {
               NonUIActions.logout(context, bundle);
           }
        }, extraTypes);

    transfer = null;
    extraTypes = new com.github.mzule.activityrouter.router.ExtraTypes();
    extraTypes.setTransfer(transfer);
    com.github.mzule.activityrouter.router.Routers.map("upload", null, new MethodInvoker() {
           public void invoke(android.content.Context context, android.os.Bundle bundle) {
               NonUIActions.uploadLog(context, bundle);
           }
        }, extraTypes);

    transfer = null;
    extraTypes = new com.github.mzule.activityrouter.router.ExtraTypes();
    extraTypes.setTransfer(transfer);
    com.github.mzule.activityrouter.router.Routers.map("user/collection", UserCollectionActivity.class, null, extraTypes);

  }
}
```

至此，ActivityRouter 所有的流程都已经讲完啦！！！

RouterActivity
--------------
对啦，还有一点，ActivityRouter 支持从外部唤起 Activity 。

在 AndroidManifest.xml 中声明 RouterActivity ，填写对应 scheme 和 host 。

	<activity
	    android:name="com.github.mzule.activityrouter.router.RouterActivity"
	    android:theme="@android:style/Theme.NoDisplay">
	    ...
	    <intent-filter>
	    	<action android:name="android.intent.action.VIEW" />
	    	<category android:name="android.intent.category.DEFAULT" />
	    	<category android:name="android.intent.category.BROWSABLE" />
	    	<data android:scheme="http" android:host="mzule.com" />
		</intent-filter>
	</activity>
	
其实先唤起的是 RouterActivity ，然后在 RouterActivity 中根据 uri 再跳转到对应的 Activity ，这点可以从 RouterActivity 的代码中印证。

``` java
public class RouterActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        RouterCallback callback = getRouterCallback();

        Uri uri = getIntent().getData();
        if (uri != null) {
            Routers.open(this, uri, callback);
        }
        finish();
    }

    private RouterCallback getRouterCallback() {
        if (getApplication() instanceof RouterCallbackProvider) {
            return ((RouterCallbackProvider) getApplication()).provideRouterCallback();
        }
        return null;
    }
}
```

这下真的是讲完啦

讲完啦

完啦

啦

Footer
======
其实现在市面的路由框架基本上都是这种套路，了解其中的奥义可以更好地使用它。

感兴趣的同学可以再去看下 ARouter 之类的源码，相信收获会更大！

再见👋



title: Tinker源码分析(一):TinkerApplication
date: 2019-02-24 22:52:16
categories: Android Blog
tags: [Android,开源框架,源码解析,Tinker,热修复]
---
本系列 Tinker 源码解析基于 Tinker v1.9.12

自动生成TinkerApplication
========================
接入 Tinker 第一步就是改造 Application 。官方推荐是利用 @DefaultLifeCycle 动态生成 Application

``` java
	@DefaultLifeCycle(application = "tinker.sample.android.app.SampleApplication",
	                  flags = ShareConstants.TINKER_ENABLE_ALL,
	                  loadVerifyFlag = false)
	public class SampleApplicationLike extends DefaultApplicationLike {
	}
```

那我们来解析一下 Tinker 是如何生成 Application 以及在 Application 中做了什么事？

看到 @DefaultLifeCycle 注解，我们可想而知应该是经过 processor 处理后动态生成了 Application 。

查看 Tinker 工程可以发现在 tinker-android-anno 下面有一个 AnnotationProcessor 

``` java
	@Override
	public Set<String> getSupportedAnnotationTypes() {
	    final Set<String> supportedAnnotationTypes = new LinkedHashSet<>();
	
	    supportedAnnotationTypes.add(DefaultLifeCycle.class.getName());
	
	    return supportedAnnotationTypes;
	}
	
	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
	    processDefaultLifeCycle(roundEnv.getElementsAnnotatedWith(DefaultLifeCycle.class));
	    return true;
	}
```

发现它正是处理 @DefaultLifeCycle 的。

下面重要看 processDefaultLifeCycle 方法。

``` java
private void processDefaultLifeCycle(Set<? extends Element> elements) {
    // DefaultLifeCycle
    for (Element e : elements) {
        DefaultLifeCycle ca = e.getAnnotation(DefaultLifeCycle.class);

        String lifeCycleClassName = ((TypeElement) e).getQualifiedName().toString();
        String lifeCyclePackageName = lifeCycleClassName.substring(0, lifeCycleClassName.lastIndexOf('.'));
        lifeCycleClassName = lifeCycleClassName.substring(lifeCycleClassName.lastIndexOf('.') + 1);

        String applicationClassName = ca.application();
        if (applicationClassName.startsWith(".")) {
            applicationClassName = lifeCyclePackageName + applicationClassName;
        }
        String applicationPackageName = applicationClassName.substring(0, applicationClassName.lastIndexOf('.'));
        applicationClassName = applicationClassName.substring(applicationClassName.lastIndexOf('.') + 1);

        String loaderClassName = ca.loaderClass();
        if (loaderClassName.startsWith(".")) {
            loaderClassName = lifeCyclePackageName + loaderClassName;
        }

        System.out.println("*");

        final InputStream is = AnnotationProcessor.class.getResourceAsStream(APPLICATION_TEMPLATE_PATH);
        final Scanner scanner = new Scanner(is);
        final String template = scanner.useDelimiter("\\A").next();
        final String fileContent = template
            .replaceAll("%PACKAGE%", applicationPackageName)
            .replaceAll("%APPLICATION%", applicationClassName)
            .replaceAll("%APPLICATION_LIFE_CYCLE%", lifeCyclePackageName + "." + lifeCycleClassName)
            .replaceAll("%TINKER_FLAGS%", "" + ca.flags())
            .replaceAll("%TINKER_LOADER_CLASS%", "" + loaderClassName)
            .replaceAll("%TINKER_LOAD_VERIFY_FLAG%", "" + ca.loadVerifyFlag());

        try {
            JavaFileObject fileObject = processingEnv.getFiler().createSourceFile(applicationPackageName + "." + applicationClassName);
            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "Creating " + fileObject.toUri());
            Writer writer = fileObject.openWriter();
            try {
                PrintWriter pw = new PrintWriter(writer);
                pw.print(fileContent);
                pw.flush();

            } finally {
                writer.close();
            }
        } catch (IOException x) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, x.toString());
        }
    }
}
```

整个 processDefaultLifeCycle 方法看下来，其实主要在做的就是去读取一份模版，然后用注解中设置的值替换里面的一些占位符。这个模版就是 resouces/TinkerAnnoApplication.tmpl 


	package %PACKAGE%;

	import com.tencent.tinker.loader.app.TinkerApplication;
	
	/**
	 *
	 * Generated application for tinker life cycle
	 *
	 */
	public class %APPLICATION% extends TinkerApplication {
	
	    public %APPLICATION%() {
	        super(%TINKER_FLAGS%, "%APPLICATION_LIFE_CYCLE%", "%TINKER_LOADER_CLASS%", %TINKER_LOAD_VERIFY_FLAG%);
	    }
	
	}
	
最终生成的 SampleApplication 效果：
	
	/**
	 *
	 * Generated application for tinker life cycle
	 *
	 */
	public class SampleApplication extends TinkerApplication {
	
	    public SampleApplication() {
	        super(7, "tinker.sample.android.app.SampleApplicationLike", "com.tencent.tinker.loader.TinkerLoader", false);
	    }
	
	}
	
解析 TinkerApplication
======================
想要知道 TinkerApplication 里面干了什么？

一起看看 TinkerApplication.onCreate 

``` java
@Override
public void onCreate() {
    super.onCreate();
    try {
        ensureDelegate();
        try {
            ComponentHotplug.ensureComponentHotplugInstalled(this);
        } catch (UnsupportedEnvironmentException e) {
            throw new TinkerRuntimeException("failed to make sure that ComponentHotplug logic is fine.", e);
        }
        invokeAppLikeOnCreate(applicationLike);
    } catch (TinkerRuntimeException e) {
        throw e;
    } catch (Throwable thr) {
        throw new TinkerRuntimeException(thr.getMessage(), thr);
    }
}
```

第一步，调用 ensureDelegate 创建 application 代理，即 applicationLike

``` java
private synchronized void ensureDelegate() {
    if (applicationLike == null) {
        applicationLike = createDelegate();
    }
}

private Object createDelegate() {
    try {
        // Use reflection to create the delegate so it doesn't need to go into the primary dex.
        // And we can also patch it
        Class<?> delegateClass = Class.forName(delegateClassName, false, getClassLoader());
        Constructor<?> constructor = delegateClass.getConstructor(Application.class, int.class, boolean.class,
            long.class, long.class, Intent.class);
        return constructor.newInstance(this, tinkerFlags, tinkerLoadVerifyFlag,
            applicationStartElapsedTime, applicationStartMillisTime, tinkerResultIntent);
    } catch (Throwable e) {
        throw new TinkerRuntimeException("createDelegate failed", e);
    }
}
```

然后调用 invokeAppLikeOnCreate(applicationLike) 去回调 applicationLike 的 onCreate 方法。这样，applicationLike 和 application 的生命周期方法就做到同步了。另外，其余的生命周期方法也是如此来实现同步的，这里就不详细讲解了。

那么 Tinker 是什么时候加载的呢？

答案就在 attachBaseContext 中

``` java
@Override
protected void attachBaseContext(Context base) {
    super.attachBaseContext(base);
    Thread.setDefaultUncaughtExceptionHandler(new TinkerUncaughtHandler(this));
    onBaseContextAttached(base);
}

private void onBaseContextAttached(Context base) {
    try {
        applicationStartElapsedTime = SystemClock.elapsedRealtime();
        applicationStartMillisTime = System.currentTimeMillis();
        loadTinker();
        ensureDelegate();
        invokeAppLikeOnBaseContextAttached(applicationLike, base);
        //reset save mode
        if (useSafeMode) {
            ShareTinkerInternals.setSafeModeCount(this, 0);
        }
    } catch (TinkerRuntimeException e) {
        throw e;
    } catch (Throwable thr) {
        throw new TinkerRuntimeException(thr.getMessage(), thr);
    }
}

```

可以看到调用了 loadTinker 方法。

``` java
private void loadTinker() {
    try {
        //reflect tinker loader, because loaderClass may be define by user!
        Class<?> tinkerLoadClass = Class.forName(loaderClassName, false, getClassLoader());
        Method loadMethod = tinkerLoadClass.getMethod(TINKER_LOADER_METHOD, TinkerApplication.class);
        Constructor<?> constructor = tinkerLoadClass.getConstructor();
        tinkerResultIntent = (Intent) loadMethod.invoke(constructor.newInstance(), this);
    } catch (Throwable e) {
        //has exception, put exception error code
        tinkerResultIntent = new Intent();
        ShareIntentUtil.setIntentReturnCode(tinkerResultIntent, ShareConstants.ERROR_LOAD_PATCH_UNKNOWN_EXCEPTION);
        tinkerResultIntent.putExtra(INTENT_PATCH_EXCEPTION, e);
    }
}
```

这里的 loaderClassName 就是上面 @DefaultLifeCycle 中定义的 loaderClass 。默认的是 com.tencent.tinker.loader.TinkerLoader ，也支持用户自定义 TinkerLoader 。

所以 loadTinker 中干的事就是利用反射执行了 TinkerLoader.tryLoad 方法。

至于在 tryLoad 方法中到底做了什么事，我们等到下一篇再讲吧。



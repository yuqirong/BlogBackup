title: ARouter源码解析（五）
date: 2019-01-10 21:42:23
categories: Android Blog
tags: [Android,开源框架,源码解析]
---
arouter-gradle-plugin version : 1.0.2

AutoRegister : https://github.com/luckybilly/AutoRegister

前言
====
在本系列的第一篇中讲过，ARouter 可以通过扫描 dex 文件中 class 的全类名，来加载 compiler 生成的路由类。但这种方式影响性能，并且效率也不高。所以在 ARouter v1.3.0 之后的版本中，加入了自动注册的方式进行路由表的加载，自动注册可以缩短初始化时间，解决应用加固导致无法直接访问 dex 文件从而初始化失败的问题。

那么自动注册到底是什么东东，为什么有这么强大的能力呢？

那么接下来，我们就来分析分析。

预先需要了解的知识点：

* 自定义 gradle plugin
* gradle transform api
* 使用 asm 实现字节码插桩

arouter-register
================
arouter-register 的入口就在 PluginLaunch

``` java
public class PluginLaunch implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        def isApp = project.plugins.hasPlugin(AppPlugin)
        //only application module needs this plugin to generate register code
        if (isApp) {
            Logger.make(project)

            Logger.i('Project enable arouter-register plugin')

            def android = project.extensions.getByType(AppExtension)
            def transformImpl = new RegisterTransform(project)

            //init arouter-auto-register settings
            ArrayList<ScanSetting> list = new ArrayList<>(3)
            list.add(new ScanSetting('IRouteRoot'))
            list.add(new ScanSetting('IInterceptorGroup'))
            list.add(new ScanSetting('IProviderGroup'))
            RegisterTransform.registerList = list
            //register this plugin
            android.registerTransform(transformImpl)
        }
    }

}
```

从上面的代码可知：

* 只在 application module （一般都是 app module）生成自动注册的代码；
* 初始化了自动注册的设置，这样自动注册就知道需要注册 IRouteRoot IInterceptorGroup IProviderGroup 这三者；
* 注册 RegisterTransform ，字节码插桩将在 RegisterTransform 中完成；

可以看出，重点就在 RegisterTransform 里面。那我们重点就关注下 RegisterTransform 的代码，这里就贴出 transform 方法的源码了。（关于 Transform 的 InputTypes 和 Scopes 知识点在这就不讲了，如有需要了解的同学可以看 [Android 热修复使用Gradle Plugin1.5改造Nuwa插件](https://blog.csdn.net/sbsujjbcy/article/details/50839263)）

``` java
class RegisterTransform extends Transform {


	@Override
	void transform(Context context, Collection<TransformInput> inputs
	               , Collection<TransformInput> referencedInputs
	               , TransformOutputProvider outputProvider
	               , boolean isIncremental) throws IOException, TransformException, InterruptedException {
	
	    Logger.i('Start scan register info in jar file.')
	
	    long startTime = System.currentTimeMillis()
	    boolean leftSlash = File.separator == '/'
	
	    inputs.each { TransformInput input ->
	
	        // 扫描所有的 jar 文件
	        input.jarInputs.each { JarInput jarInput ->
	            String destName = jarInput.name
	            // rename jar files
	            def hexName = DigestUtils.md5Hex(jarInput.file.absolutePath)
	            if (destName.endsWith(".jar")) {
	                destName = destName.substring(0, destName.length() - 4)
	            }
	            // 输入的 jar 文件
	            File src = jarInput.file
	            // 输出的 jar 文件
	            File dest = outputProvider.getContentLocation(destName + "_" + hexName, jarInput.contentTypes, jarInput.scopes, Format.JAR)
	
	            // 扫描 jar 文件，查找实现 IRouteRoot IInterceptorGroup IProviderGroup 接口的类，并且找到 LogisticsCenter 在哪个 jar 文件中
	            // 不扫描 com.android.support 开头的 jar
	            if (ScanUtil.shouldProcessPreDexJar(src.absolutePath)) {
	                // ScanUtil.scanJar 的代码就不详细展开了，感兴趣的同学可以自己去看下
	                ScanUtil.scanJar(src, dest)
	            }
	            FileUtils.copyFile(src, dest)
	
	        }
	        // 扫描所有的 class 文件，查找实现 IRouteRoot IInterceptorGroup IProviderGroup 接口的类
	        // 和扫描 jar 做差不多类似的工作。不同的点就是不用再去找 LogisticsCenter 类
	        input.directoryInputs.each { DirectoryInput directoryInput ->
	            File dest = outputProvider.getContentLocation(directoryInput.name, directoryInput.contentTypes, directoryInput.scopes, Format.DIRECTORY)
	            String root = directoryInput.file.absolutePath
	            if (!root.endsWith(File.separator))
	                root += File.separator
	            directoryInput.file.eachFileRecurse { File file ->
	                def path = file.absolutePath.replace(root, '')
	                if (!leftSlash) {
	                    path = path.replaceAll("\\\\", "/")
	                }
	                // 只处理 com/alibaba/android/arouter/routes/ 开头的 class
	                if(file.isFile() && ScanUtil.shouldProcessClass(path)){
	                    ScanUtil.scanClass(file)
	                }
	            }
	
	            // copy to dest
	            FileUtils.copyDirectory(directoryInput.file, dest)
	        }
	    }
	
	    Logger.i('Scan finish, current cost time ' + (System.currentTimeMillis() - startTime) + "ms")
	    
	    // 这里开始字节码插桩操作
	    if (fileContainsInitClass) {
	        // 遍历之前找的 IRouteRoot IInterceptorGroup IProviderGroup
	        registerList.each { ext ->
	            Logger.i('Insert register code to file ' + fileContainsInitClass.absolutePath)
	
	            if (ext.classList.isEmpty()) {
	                Logger.e("No class implements found for interface:" + ext.interfaceName)
	            } else {
	                ext.classList.each {
	                    Logger.i(it)
	                }
	                // 对 LogisticsCenter.class 做字节码插桩
	                RegisterCodeGenerator.insertInitCodeTo(ext)
	            }
	        }
	    }
	
	    Logger.i("Generate code finish, current cost time: " + (System.currentTimeMillis() - startTime) + "ms")
	}

}
```

上面代码的逻辑很清晰，按照之前设置好的 IRouteRoot IInterceptorGroup IProviderGroup 这三个接口，然后扫描整个项目的代码，分别找到这三者各自的实现类，然后加入到集合中。最后在 LogisticsCenter 中实现字节码插桩。

我们来详细看下 RegisterCodeGenerator.insertInitCodeTo(ext) 的代码

``` java
static void insertInitCodeTo(ScanSetting registerSetting) {
    if (registerSetting != null && !registerSetting.classList.isEmpty()) {
        RegisterCodeGenerator processor = new RegisterCodeGenerator(registerSetting)
        // RegisterTransform.fileContainsInitClass 就是包含了 LogisticsCenter.class 的那个 jar 文件
        File file = RegisterTransform.fileContainsInitClass
        if (file.getName().endsWith('.jar'))
        		 // 开始处理
            processor.insertInitCodeIntoJarFile(file)
    }
}
```

插入的操作在 insertInitCodeIntoJarFile 中实现。

``` java
private File insertInitCodeIntoJarFile(File jarFile) {
    if (jarFile) {
        def optJar = new File(jarFile.getParent(), jarFile.name + ".opt")
        if (optJar.exists())
            optJar.delete()
        def file = new JarFile(jarFile)
        Enumeration enumeration = file.entries()
        JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(optJar))
        // 遍历 jar 文件中的 class
        while (enumeration.hasMoreElements()) {
            JarEntry jarEntry = (JarEntry) enumeration.nextElement()
            String entryName = jarEntry.getName()
            ZipEntry zipEntry = new ZipEntry(entryName)
            InputStream inputStream = file.getInputStream(jarEntry)
            jarOutputStream.putNextEntry(zipEntry)
            // 如果是 LogisticsCenter.class 的话
            if (ScanSetting.GENERATE_TO_CLASS_FILE_NAME == entryName) {

                Logger.i('Insert init code to class >> ' + entryName)
                // 插桩操作
                def bytes = referHackWhenInit(inputStream)
                jarOutputStream.write(bytes)
            } else {
                jarOutputStream.write(IOUtils.toByteArray(inputStream))
            }
            inputStream.close()
            jarOutputStream.closeEntry()
        }
        jarOutputStream.close()
        file.close()
        // 把字节码插桩的 jar 替换掉原来旧的 jar 文件
        if (jarFile.exists()) {
            jarFile.delete()
        }
        optJar.renameTo(jarFile)
    }
    return jarFile
}
```

字节码插桩的代码还在 referHackWhenInit 方法中。

``` java
//refer hack class when object init
private byte[] referHackWhenInit(InputStream inputStream) {
    ClassReader cr = new ClassReader(inputStream)
    ClassWriter cw = new ClassWriter(cr, 0)
    ClassVisitor cv = new MyClassVisitor(Opcodes.ASM5, cw)
    cr.accept(cv, ClassReader.EXPAND_FRAMES)
    return cw.toByteArray()
}

class MyClassVisitor extends ClassVisitor {

    MyClassVisitor(int api, ClassVisitor cv) {
        super(api, cv)
    }

    void visit(int version, int access, String name, String signature,
               String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces)
    }
    @Override
    MethodVisitor visitMethod(int access, String name, String desc,
                              String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions)
        // 对 loadRouterMap 这个方法进行代码插入
        if (name == ScanSetting.GENERATE_TO_METHOD_NAME) {
            mv = new RouteMethodVisitor(Opcodes.ASM5, mv)
        }
        return mv
    }
}

class RouteMethodVisitor extends MethodVisitor {

    RouteMethodVisitor(int api, MethodVisitor mv) {
        super(api, mv)
    }

    @Override
    void visitInsn(int opcode) {
        // 插入的代码在 return 之前
        if ((opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN)) {
            extension.classList.each { name ->
                name = name.replaceAll("/", ".")
                mv.visitLdcInsn(name)//这里的name就是之前扫描出来的 IRouteRoot IInterceptorGroup IProviderGroup 实现类名
                // 生成 LogisticsCenter.register(name) 代码
                mv.visitMethodInsn(Opcodes.INVOKESTATIC
                        , ScanSetting.GENERATE_TO_CLASS_NAME
                        , ScanSetting.REGISTER_METHOD_NAME
                        , "(Ljava/lang/String;)V"
                        , false)
            }
        }
        super.visitInsn(opcode)
    }
    @Override
    void visitMaxs(int maxStack, int maxLocals) {
        super.visitMaxs(maxStack + 4, maxLocals)
    }
}
```

最终，生成的代码会像下面所示：

``` java
private static void loadRouterMap() {
    registerByPlugin = false;
    //auto generate register code by gradle plugin: arouter-auto-register
    // looks like below:
    register("com.alibaba.android.arouter.routes.ARouter$$Root$$app");
    register("com.alibaba.android.arouter.routes.ARouter$$Interceptors$$app");
    register("com.alibaba.android.arouter.routes.ARouter$$Group$$arouter");
}
```

那么顺便来跟踪一下 register 方法的代码，看看里面是如何完成路由表注册的。

``` java
private static void register(String className) {
    if (!TextUtils.isEmpty(className)) {
        try {
            Class<?> clazz = Class.forName(className);
            Object obj = clazz.getConstructor().newInstance();
            if (obj instanceof IRouteRoot) {
                registerRouteRoot((IRouteRoot) obj);
            } else if (obj instanceof IProviderGroup) {
                registerProvider((IProviderGroup) obj);
            } else if (obj instanceof IInterceptorGroup) {
                registerInterceptor((IInterceptorGroup) obj);
            } else {
                logger.info(TAG, "register failed, class name: " + className
                        + " should implements one of IRouteRoot/IProviderGroup/IInterceptorGroup.");
            }
        } catch (Exception e) {
            logger.error(TAG,"register class error:" + className);
        }
    }
}

// 注册 IRouteRoot 类型
private static void registerRouteRoot(IRouteRoot routeRoot) {
    markRegisteredByPlugin();
    if (routeRoot != null) {
        routeRoot.loadInto(Warehouse.groupsIndex);
    }
}

// 注册 IInterceptorGroup 类型
private static void registerInterceptor(IInterceptorGroup interceptorGroup) {
    markRegisteredByPlugin();
    if (interceptorGroup != null) {
        interceptorGroup.loadInto(Warehouse.interceptorsIndex);
    }
}

// 注册 IProviderGroup 类型
private static void registerProvider(IProviderGroup providerGroup) {
    markRegisteredByPlugin();
    if (providerGroup != null) {
        providerGroup.loadInto(Warehouse.providersIndex);
    }
}

// 标记通过gradle plugin完成自动注册
private static void markRegisteredByPlugin() {
    if (!registerByPlugin) {
        registerByPlugin = true;
    }
}
```

这样相比之下，自动注册的方式确实比扫描 dex 文件更高效，扫描 dex 文件是在 app 运行时操作的，这样会影响 app 的性能，对用户造成不好的体验。而自动注册是在 build 的时候完成字节码插桩的，对运行时不产生影响。

学了今天这招，以后 compiler 生成的代码需要注册的步骤都可以通过自动注册来完成了，赞一个👍

番外
====
之前看到自动注册这么神奇，所以想看下插入字节码之后 LogisticsCenter 代码的效果，所以反编译了一下 ARouter demo apk，可以看到 LogisticsCenter.smali 的 loadRouterMap 方法：

``` smali
.method private static loadRouterMap()V
    .locals 1

    .line 64
    const/4 v0, 0x0

    sput-boolean v0, Lcom/alibaba/android/arouter/core/LogisticsCenter;->registerByPlugin:Z

    .line 69
    const-string v0, "com.alibaba.android.arouter.routes.ARouter$$Root$$modulejava"

    invoke-static {v0}, Lcom/alibaba/android/arouter/core/LogisticsCenter;->register(Ljava/lang/String;)V

    const-string v0, "com.alibaba.android.arouter.routes.ARouter$$Root$$modulekotlin"

    invoke-static {v0}, Lcom/alibaba/android/arouter/core/LogisticsCenter;->register(Ljava/lang/String;)V

    const-string v0, "com.alibaba.android.arouter.routes.ARouter$$Root$$arouterapi"

    invoke-static {v0}, Lcom/alibaba/android/arouter/core/LogisticsCenter;->register(Ljava/lang/String;)V

    const-string v0, "com.alibaba.android.arouter.routes.ARouter$$Root$$app"

    invoke-static {v0}, Lcom/alibaba/android/arouter/core/LogisticsCenter;->register(Ljava/lang/String;)V

    const-string v0, "com.alibaba.android.arouter.routes.ARouter$$Interceptors$$modulejava"

    invoke-static {v0}, Lcom/alibaba/android/arouter/core/LogisticsCenter;->register(Ljava/lang/String;)V

    const-string v0, "com.alibaba.android.arouter.routes.ARouter$$Interceptors$$app"

    invoke-static {v0}, Lcom/alibaba/android/arouter/core/LogisticsCenter;->register(Ljava/lang/String;)V

    const-string v0, "com.alibaba.android.arouter.routes.ARouter$$Providers$$modulejava"

    invoke-static {v0}, Lcom/alibaba/android/arouter/core/LogisticsCenter;->register(Ljava/lang/String;)V

    const-string v0, "com.alibaba.android.arouter.routes.ARouter$$Providers$$modulekotlin"

    invoke-static {v0}, Lcom/alibaba/android/arouter/core/LogisticsCenter;->register(Ljava/lang/String;)V

    const-string v0, "com.alibaba.android.arouter.routes.ARouter$$Providers$$arouterapi"

    invoke-static {v0}, Lcom/alibaba/android/arouter/core/LogisticsCenter;->register(Ljava/lang/String;)V

    const-string v0, "com.alibaba.android.arouter.routes.ARouter$$Providers$$app"

    invoke-static {v0}, Lcom/alibaba/android/arouter/core/LogisticsCenter;->register(Ljava/lang/String;)V

    return-void
.end method
```

确实符合我们的预期啊，真好！

References
==========
* [AutoRegister:一种更高效的组件自动注册方案(android组件化开发)](https://juejin.im/post/5a2b95b96fb9a045284669a9)
* [Android 热修复使用Gradle Plugin1.5改造Nuwa插件](https://blog.csdn.net/sbsujjbcy/article/details/50839263)
* [一起玩转Android项目中的字节码](http://quinnchen.me/2018/09/13/2018-09-13-asm-transform/)



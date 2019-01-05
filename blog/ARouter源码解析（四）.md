title: ARouter源码解析（四）
date: 2019-01-03 21:46:43
categories: Android Blog
tags: [Android,开源框架,源码解析]
---
arouter-compiler version : 1.2.2

前言
====
之前对 arouter-api 做了整个流程的分析，今天来看看 arouter-compiler 。

arouter-compiler 主要是利用 apt 在编译期自动生成代码的。之前我们看到的 `ARouter$$Root$$app` 、 `ARouter$$Group$$test` 和 `Test1Activity$$ARouter$$Autowired` 等都是 arouter-compiler 生成的。

那接下来就分析分析 arouter-compiler 是怎么生成这些源码的。

arouter-compiler
================
arouter-compiler 中 processor 有三种：

* AutowiredProcessor : 用来生成像 `Test1Activity$$ARouter$$Autowired` 这种类型；
* InterceptorProcessor : 用来生成像 `ARouter$$Interceptors$$app` 这种类型；
* RouteProcessor : 用来生成像 `ARouter$$Root$$app` ，`ARouter$$Providers$$app` 和 `ARouter$$Group$$test` 这种类型；

RouteProcessor
--------------
在这里我们就只分析 RouteProcessor 了。

RouteProcessor 相比其他两个 Processor 来说，代码更长，逻辑更加复杂。并且 RouteProcessor 主要处理的是路由映射这一块。其他两个 RouteProcessor 也是大同小异，有兴趣的同学可以自行阅读源码。

先来看看 RouteProcessor 的定义：

```
@AutoService(Processor.class)
@SupportedOptions({KEY_MODULE_NAME, KEY_GENERATE_DOC_NAME})
@SupportedSourceVersion(SourceVersion.RELEASE_7)
@SupportedAnnotationTypes({ANNOTATION_TYPE_ROUTE, ANNOTATION_TYPE_AUTOWIRED})
public class RouteProcessor extends AbstractProcessor {
	...
}
```

RouteProcessor 类上面的注解很多，我们一个一个来看：

* @AutoService 会自动在 META-INF 文件夹下生成 Processor 配置信息文件，避免手动配置的麻烦;
* @SupportedOptions 指定 Processor 支持的选项参数名称，KEY_MODULE_NAME 就是 AROUTER_MODULE_NAME ，KEY_GENERATE_DOC_NAME 就是 AROUTER_GENERATE_DOC；没错，这两个就是我们一开始在 build.gradle 中配置的。
* @SupportedSourceVersion 指定 Processor 支持的 JDK 的版本；
* @SupportedAnnotationTypes 指定 Processor 处理的注解；

接着，趁热打铁。来瞧瞧 RouteProcessor 的 init 方法。

``` java
@Override
public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);

    mFiler = processingEnv.getFiler();                  // Generate class.
    types = processingEnv.getTypeUtils();            // Get type utils.
    elements = processingEnv.getElementUtils();      // Get class meta.

    typeUtils = new TypeUtils(types, elements);
    logger = new Logger(processingEnv.getMessager());   // Package the log utils.

    // Attempt to get user configuration [moduleName]
    Map<String, String> options = processingEnv.getOptions();
    if (MapUtils.isNotEmpty(options)) {
        moduleName = options.get(KEY_MODULE_NAME);
        generateDoc = VALUE_ENABLE.equals(options.get(KEY_GENERATE_DOC_NAME));
    }

    if (StringUtils.isNotEmpty(moduleName)) {
        moduleName = moduleName.replaceAll("[^0-9a-zA-Z_]+", "");

        logger.info("The user has configuration the module name, it was [" + moduleName + "]");
    } else {
        logger.error(NO_MODULE_NAME_TIPS);
        throw new RuntimeException("ARouter::Compiler >>> No module name, for more information, look at gradle log.");
    }

    // 如果需要生成路由 doc
    if (generateDoc) {
        try {
            docWriter = mFiler.createResource(
                    StandardLocation.SOURCE_OUTPUT,
                    PACKAGE_OF_GENERATE_DOCS,
                    "arouter-map-of-" + moduleName + ".json"
            ).openWriter();
        } catch (IOException e) {
            logger.error("Create doc writer failed, because " + e.getMessage());
        }
    }

    iProvider = elements.getTypeElement(Consts.IPROVIDER).asType();

    logger.info(">>> RouteProcessor init. <<<");
}
```

在 init 方法中，主要获取了 KEY_MODULE_NAME 和 KEY_GENERATE_DOC_NAME 这两个编译选项参数。然后判断一下是否需要生成路由文档。

在 init 方法中获取参数后，接着就是 process 方法。

process 方法就好像是 main 方法一样，在这里面都是 processer 处理注解自动生成代码的逻辑。

``` java
@Override
public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    if (CollectionUtils.isNotEmpty(annotations)) {
        // 获取 @Route 注解的集合
        Set<? extends Element> routeElements = roundEnv.getElementsAnnotatedWith(Route.class);
        try {
            logger.info(">>> Found routes, start... <<<");
            this.parseRoutes(routeElements);

        } catch (Exception e) {
            logger.error(e);
        }
        return true;
    }

    return false;
}
```

在 process 中调用了 parseRoutes ，parseRoutes 方法实在是太长了，在这里我们进行分段讲解吧。

``` java
private void parseRoutes(Set<? extends Element> routeElements) throws IOException {
    if (CollectionUtils.isNotEmpty(routeElements)) {
        // prepare the type an so on.
	
        logger.info(">>> Found routes, size is " + routeElements.size() + " <<<");
	
        rootMap.clear();
        // Activity 类型
        TypeMirror type_Activity = elements.getTypeElement(ACTIVITY).asType();
        // Service 类型
        TypeMirror type_Service = elements.getTypeElement(SERVICE).asType();
        // Fragment 类型
        TypeMirror fragmentTm = elements.getTypeElement(FRAGMENT).asType();
        // v4 Fragment 类型
        TypeMirror fragmentTmV4 = elements.getTypeElement(Consts.FRAGMENT_V4).asType();
	
        // IRouteGroup 类型
        TypeElement type_IRouteGroup = elements.getTypeElement(IROUTE_GROUP);
        // IProviderGroup 类型
        TypeElement type_IProviderGroup = elements.getTypeElement(IPROVIDER_GROUP);
        // 获取 RouteMeta 和 RouteType 的类名
        ClassName routeMetaCn = ClassName.get(RouteMeta.class);
        ClassName routeTypeCn = ClassName.get(RouteType.class);
	
        /*
           构造 ARouter$$Root$$xxx 的 loadInto 方法入参类型
           Build input type, format as :
	
           Map<String, Class<? extends IRouteGroup>>
         */
        ParameterizedTypeName inputMapTypeOfRoot = ParameterizedTypeName.get(
                ClassName.get(Map.class),
                ClassName.get(String.class),
                ParameterizedTypeName.get(
                        ClassName.get(Class.class),
                        WildcardTypeName.subtypeOf(ClassName.get(type_IRouteGroup))
                )
        );
	
        /*
          构造 ARouter$$Group$$xxx 的 loadInto 方法入参类型
          Map<String, RouteMeta>
         */
        ParameterizedTypeName inputMapTypeOfGroup = ParameterizedTypeName.get(
                ClassName.get(Map.class),
                ClassName.get(String.class),
                ClassName.get(RouteMeta.class)
        );
	
        /*
          构造方法入参参数名称
          Build input param name.
         */
        ParameterSpec rootParamSpec = ParameterSpec.builder(inputMapTypeOfRoot, "routes").build();
        ParameterSpec groupParamSpec = ParameterSpec.builder(inputMapTypeOfGroup, "atlas").build();
        ParameterSpec providerParamSpec = ParameterSpec.builder(inputMapTypeOfGroup, "providers").build();  // Ps. its param type same as groupParamSpec!
	
        /*
          构造 ARouter$$Root$$xxx 的 loadInto 方法
          Build method : 'loadInto'
         */
        MethodSpec.Builder loadIntoMethodOfRootBuilder = MethodSpec.methodBuilder(METHOD_LOAD_INTO)
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .addParameter(rootParamSpec);
	
        ...
        
	}
}
```

parseRoutes 方法一开始，做足了准备。下面就到了放大招的时候了。

``` java
//  Follow a sequence, find out metas of group first, generate java file, then statistics them as root.
for (Element element : routeElements) {
    TypeMirror tm = element.asType();
    Route route = element.getAnnotation(Route.class);
    RouteMeta routeMeta;

    // 如果 element 修饰的类是 Activity 类型的
    if (types.isSubtype(tm, type_Activity)) {                 // Activity
        logger.info(">>> Found activity route: " + tm.toString() + " <<<");

        // 获取 Activity 中 @Autowired 注解的属性，IProvider 类型的除外
        Map<String, Integer> paramsType = new HashMap<>();
        Map<String, Autowired> injectConfig = new HashMap<>();
        for (Element field : element.getEnclosedElements()) {
            if (field.getKind().isField() && field.getAnnotation(Autowired.class) != null && !types.isSubtype(field.asType(), iProvider)) {
                // It must be field, then it has annotation, but it not be provider.
                Autowired paramConfig = field.getAnnotation(Autowired.class);
                String injectName = StringUtils.isEmpty(paramConfig.name()) ? field.getSimpleName().toString() : paramConfig.name();
                paramsType.put(injectName, typeUtils.typeExchange(field));
                injectConfig.put(injectName, paramConfig);
            }
        }
        // 构造 activity 类型的路由数据
        routeMeta = new RouteMeta(route, element, RouteType.ACTIVITY, paramsType);
        routeMeta.setInjectConfig(injectConfig);
    } else if (types.isSubtype(tm, iProvider)) {         // IProvider 类型
        logger.info(">>> Found provider route: " + tm.toString() + " <<<");
        routeMeta = new RouteMeta(route, element, RouteType.PROVIDER, null);
    } else if (types.isSubtype(tm, type_Service)) {           // Service 类型
        logger.info(">>> Found service route: " + tm.toString() + " <<<");
        routeMeta = new RouteMeta(route, element, RouteType.parse(SERVICE), null);
    } else if (types.isSubtype(tm, fragmentTm) || types.isSubtype(tm, fragmentTmV4)) { // fragment 类型
        logger.info(">>> Found fragment route: " + tm.toString() + " <<<");
        routeMeta = new RouteMeta(route, element, RouteType.parse(FRAGMENT), null);
    } else {
        throw new RuntimeException("ARouter::Compiler >>> Found unsupported class type, type = [" + types.toString() + "].");
    }
    // 将生成好的 routeMeta 按组存放进入 groupMap 中
    categories(routeMeta);
}
```

上面这段代码主要将每个 routeElement 进行了分类，将 @Route 修饰的类信息封装进 RouteMeta 中。再把 RouteMeta 按照组名分好组存进 groupMap 中。

``` java
// 构造 ARouter$$Providers$$xxx 的 loadInto 方法
MethodSpec.Builder loadIntoMethodOfProviderBuilder = MethodSpec.methodBuilder(METHOD_LOAD_INTO)
        .addAnnotation(Override.class)
        .addModifiers(PUBLIC)
        .addParameter(providerParamSpec);

Map<String, List<RouteDoc>> docSource = new HashMap<>();

// Start generate java source, structure is divided into upper and lower levels, used for demand initialization.
for (Map.Entry<String, Set<RouteMeta>> entry : groupMap.entrySet()) {
    // 每组的组名
    String groupName = entry.getKey();

    // 构造 ARouter$$Group$$xxx 的 loadInto 方法
    MethodSpec.Builder loadIntoMethodOfGroupBuilder = MethodSpec.methodBuilder(METHOD_LOAD_INTO)
            .addAnnotation(Override.class)
            .addModifiers(PUBLIC)
            .addParameter(groupParamSpec);

    List<RouteDoc> routeDocList = new ArrayList<>();

    // Build group method body
    Set<RouteMeta> groupData = entry.getValue();
    for (RouteMeta routeMeta : groupData) {
        RouteDoc routeDoc = extractDocInfo(routeMeta);
        // 类名。比如 com.alibaba.android.arouter.demo.testservice.HelloService
        ClassName className = ClassName.get((TypeElement) routeMeta.getRawType());

        switch (routeMeta.getType()) {
            case PROVIDER:  // Need cache provider's super class
                // 获取该节点下的接口
                List<? extends TypeMirror> interfaces = ((TypeElement) routeMeta.getRawType()).getInterfaces();
                // 遍历接口
                for (TypeMirror tm : interfaces) {
                    routeDoc.addPrototype(tm.toString());
                    // 如果接口是 iProvider 类型
                    if (types.isSameType(tm, iProvider)) {   // Its implements iProvider interface himself.
                        // This interface extend the IProvider, so it can be used for mark provider
                        loadIntoMethodOfProviderBuilder.addStatement(
                                "providers.put($S, $T.build($T." + routeMeta.getType() + ", $T.class, $S, $S, null, " + routeMeta.getPriority() + ", " + routeMeta.getExtra() + "))",
                                (routeMeta.getRawType()).toString(),
                                routeMetaCn,
                                routeTypeCn,
                                className,
                                routeMeta.getPath(),
                                routeMeta.getGroup());
                    } else if (types.isSubtype(tm, iProvider)) { // 如果是 iProvider 的子接口
                        // This interface extend the IProvider, so it can be used for mark provider
                        loadIntoMethodOfProviderBuilder.addStatement(
                                "providers.put($S, $T.build($T." + routeMeta.getType() + ", $T.class, $S, $S, null, " + routeMeta.getPriority() + ", " + routeMeta.getExtra() + "))",
                                tm.toString(),    // So stupid, will duplicate only save class name.
                                routeMetaCn,
                                routeTypeCn,
                                className,
                                routeMeta.getPath(),
                                routeMeta.getGroup());
                    }
                }
                break;
            default:
                break;
        }
```

上面的代码最终会生成 ARouter$$Providers$$xxx 的 loadInto 方法，比如像这样：

	providers.put("com.alibaba.android.arouter.demo.testservice.HelloService", RouteMeta.build(RouteType.PROVIDER, HelloServiceImpl.class, "/yourservicegroupname/hello", "yourservicegroupname", null, -1, -2147483648));

那我们接着看。

```
    // 构造 RouteMeta 的 paramType 参数
    StringBuilder mapBodyBuilder = new StringBuilder();
    Map<String, Integer> paramsType = routeMeta.getParamsType();
    Map<String, Autowired> injectConfigs = routeMeta.getInjectConfig();
    if (MapUtils.isNotEmpty(paramsType)) {
        List<RouteDoc.Param> paramList = new ArrayList<>();

        for (Map.Entry<String, Integer> types : paramsType.entrySet()) {
            mapBodyBuilder.append("put(\"").append(types.getKey()).append("\", ").append(types.getValue()).append("); ");

            RouteDoc.Param param = new RouteDoc.Param();
            Autowired injectConfig = injectConfigs.get(types.getKey());
            param.setKey(types.getKey());
            param.setType(TypeKind.values()[types.getValue()].name().toLowerCase());
            param.setDescription(injectConfig.desc());
            param.setRequired(injectConfig.required());

            paramList.add(param);
        }

        routeDoc.setParams(paramList);
    }
    String mapBody = mapBodyBuilder.toString();

    // 以下代码生成这种模版 atlas.put("/test/activity1", RouteMeta.build(RouteType.ACTIVITY, Test1Activity.class, "/test/activity1", "test", new java.util.HashMap<String, Integer>(){{put("ser", 9); }}, -1, -2147483648));

    loadIntoMethodOfGroupBuilder.addStatement(
            "atlas.put($S, $T.build($T." + routeMeta.getType() + ", $T.class, $S, $S, " + (StringUtils.isEmpty(mapBody) ? null : ("new java.util.HashMap<String, Integer>(){{" + mapBodyBuilder.toString() + "}}")) + ", " + routeMeta.getPriority() + ", " + routeMeta.getExtra() + "))",
            routeMeta.getPath(),
            routeMetaCn,
            routeTypeCn,
            className,
            routeMeta.getPath().toLowerCase(),
            routeMeta.getGroup().toLowerCase());

    routeDoc.setClassName(className.toString());
    routeDocList.add(routeDoc);
}

// 生成 ARouter$$Group$$xxx 类
String groupFileName = NAME_OF_GROUP + groupName;
JavaFile.builder(PACKAGE_OF_GENERATE_FILE,
        TypeSpec.classBuilder(groupFileName)
                .addJavadoc(WARNING_TIPS)
                .addSuperinterface(ClassName.get(type_IRouteGroup))
                .addModifiers(PUBLIC)
                .addMethod(loadIntoMethodOfGroupBuilder.build())
                .build()
).build().writeTo(mFiler);

logger.info(">>> Generated group: " + groupName + "<<<");
rootMap.put(groupName, groupFileName);
docSource.put(groupName, routeDocList);
```

上面代码主要做的事情就是遍历 groupmap 集合给 ARouter$$Group$$xxx 类中的 loadInto 添加方法体，并生成 java 文件。

``` java
if (MapUtils.isNotEmpty(rootMap)) {
    // Generate root meta by group name, it must be generated before root, then I can find out the class of group.
    // 生成 ARouter$$Root$$app 的 loadInto 方法体
    for (Map.Entry<String, String> entry : rootMap.entrySet()) {
        loadIntoMethodOfRootBuilder.addStatement("routes.put($S, $T.class)", entry.getKey(), ClassName.get(PACKAGE_OF_GENERATE_FILE, entry.getValue()));
    }
}

// Output route doc
if (generateDoc) {
    docWriter.append(JSON.toJSONString(docSource, SerializerFeature.PrettyFormat));
    docWriter.flush();
    docWriter.close();
}

// 生成 ARouter$$Providers$$app 类
String providerMapFileName = NAME_OF_PROVIDER + SEPARATOR + moduleName;
JavaFile.builder(PACKAGE_OF_GENERATE_FILE,
        TypeSpec.classBuilder(providerMapFileName)
                .addJavadoc(WARNING_TIPS)
                .addSuperinterface(ClassName.get(type_IProviderGroup))
                .addModifiers(PUBLIC)
                .addMethod(loadIntoMethodOfProviderBuilder.build())
                .build()
).build().writeTo(mFiler);

logger.info(">>> Generated provider map, name is " + providerMapFileName + " <<<");

// 生成 ARouter$$Root$$app 类 
String rootFileName = NAME_OF_ROOT + SEPARATOR + moduleName;
JavaFile.builder(PACKAGE_OF_GENERATE_FILE,
        TypeSpec.classBuilder(rootFileName)
                .addJavadoc(WARNING_TIPS)
                .addSuperinterface(ClassName.get(elements.getTypeElement(ITROUTE_ROOT)))
                .addModifiers(PUBLIC)
                .addMethod(loadIntoMethodOfRootBuilder.build())
                .build()
).build().writeTo(mFiler);

logger.info(">>> Generated root, name is " + rootFileName + " <<<");
```

以上，就是整个 RouteProcessor 的流程。看完 RouteProcessor 之后，相信你对 ARouter 的的了解也更加深入了。

之后，也会对 ARouter 的 arouter-register 模块做一个深入解析，敬请期待吧。


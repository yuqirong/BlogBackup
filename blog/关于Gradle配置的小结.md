title: 关于Gradle配置的小结
date: 2016-10-23 15:34:50
categories: Android Blog
tags: [Android,Gradle]
---
前言
=====
使用 Android Studio 来开发 Android 工程的过程中，接触 Gradle 是不可避免的，比如配置签名、引入依赖等。那么 Gradle 到底是什么东西呢？ Gradle 是一个基于 Apache Ant 和 Apache Maven 概念的项目自动化建构工具。它使用一种基于 Groovy 的特定领域语言 (DSL) 来声明项目设置，抛弃了基于 XML 的各种繁琐配置 (此定义来自于百度百科-_- !) 。啰里啰唆一堆，幸运的是，一般来说 Android 开发者只要会配置 Gradle 就可以了，并不需要深入了解。那么下面我们就来揭开 Gradle 的面纱吧。

Gradle 配置
=======
首先贴出一张自己项目的文件目录结构图：

![文件目录结构图](/uploads/20161023/20161023155409.png)

从上图中我们可以看到，与 Gradle 有关的文件基本上分为四种：

1. app 下的 build.gradle (当然其他 module 下也有)；
2. 根目录下的 gradle 文件夹；
3. 根目录下的 build.gradle ；
4. 根目录下的 settings.gradle ；

也许有人会说根目录下还有一个 config.gradle 文件呢，其实这是我自定义的 gradle 文件，自定义 Gradle 文件会在下面中讲解，这里先搁置一下。好了，那么我们一个一个地来看看他们的作用吧。

app 下的 build.gradle
---------------
```
apply plugin: 'com.android.application'
android {
    compileSdkVersion 23 // 编译sdk版本
    buildToolsVersion "23.0.2" // 构建工具版本
    defaultConfig {
        applicationId "com.yuqirong.koku" // 应用包名
        minSdkVersion 15 // 最低适用sdk版本
        targetSdkVersion 23 // 目标sdk版本
        versionCode 1 // 版本号
        versionName "1.0" // 版本名称
    }
    buildTypes {
        release {
            minifyEnabled true // 开启混淆
            zipAlignEnabled true // 对齐zip
            shrinkResources false // 删除无用资源
            debuggable false // 是否debug
            versionNameSuffix "_release" // 版本命名后缀
            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro' // 混淆文件
        }

        debug {
            zipAlignEnabled false
            shrinkResources false
            minifyEnabled false
            versionNameSuffix "_debug"
            signingConfig signingConfigs.debug
            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'
        }
    }
}
dependencies {
    compile fileTree(dir: 'libs', include: ['*.jar'])
    testCompile 'junit:junit:4.12'
    compile 'com.android.support:appcompat-v7:23.2.1'
    compile 'com.android.support:design:23.2.1'
}
```

第一句 `apply plugin: 'com.android.application'` 主要用来申明这是一个 Android 程序。而 dependencies 用于引入依赖，这个相信大家都比较了解了。其他的配置比较简单都有注释，就不展开讲了。

当然除了上面的配置之外，还有很多配置也常常写入到 app/build.gradle 中。我们慢慢往下看。

* 签名配置：

```
signingConfigs {
	
    release { // 正式版本的签名
        storeFile file("../koku.jks") // 密钥文件位置
        storePassword "xxxxxxxxx" // 密钥密码
        keyAlias "koku" // 密钥别名
        keyPassword "xxxxxxxxx" // 别名密码
    }
	
    debug { // debug版本的签名
        // no keystore
    }
}
```

使用时只要在 buildTypes 的 release 中加一句 `signingConfig signingConfigs.release` 就好了。

如果你觉得把密钥密码和别名密码放在 app/build.gradle 里不安全，那么可以把相关密码放到不加入版本控制系统的 gradle.properties 文件：

	KEYSTORE_PASSWORD=xxxxxxxxxx
	KEY_PASSWORD=xxxxxxxxx

对应的 signingConfigs 配置：

```
signingConfigs {
    release {
        try {
            storeFile file("../koku.jks")
            storePassword KEYSTORE_PASSWORD
            keyAlias "koku"
            keyPassword KEY_PASSWORD
        }
        catch (ex) {
            throw new InvalidUserDataException("You should define KEYSTORE_PASSWORD and KEY_PASSWORD in gradle.properties.")
        }
    }
}
```

* Java 编译版本配置：

```
compileOptions { // java 版本
    sourceCompatibility JavaVersion.VERSION_1_8
    targetCompatibility JavaVersion.VERSION_1_8
}
```

这里需要注意下，如果 Java 编译版本为1.8的话，另外在 defaultConfig 里要配置 Jack 编译器：

```
jackOptions {
    enabled true
}
```

* Lint 检查配置：

```
lintOptions {
    abortOnError false // 是否忽略lint报错
}
```

* 多渠道信息配置：

```
productFlavors {
    xiaomi {}
    googleplay {}
    wandoujia {}
}
```

整个 app/build.gradle 文件配置如下所示：

```
apply plugin: 'com.android.application'

android {
    compileSdkVersion rootProject.ext.android.compileSdkVersion
    buildToolsVersion rootProject.ext.android.buildToolsVersion
    defaultConfig {
        applicationId "com.yuqirong.koku" // 应用包名
        minSdkVersion 15 // 最低适用sdk版本
        targetSdkVersion 23 // 目标sdk版本
        versionCode 1 // 版本号
        versionName "1.0" // 版本名称
        testInstrumentationRunner "android.support.test.runner.AndroidJUnitRunner"
        // 默认是umeng的渠道
        manifestPlaceholders = [UMENG_CHANNEL_VALUE: "umeng"]
        jackOptions {
            enabled true
        }
    }
    java 版本
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }
    signingConfigs {
        release {
            storeFile file("../koku.jks")
            storePassword "xxxxxx"
            keyAlias "koku"
            keyPassword "xxxxxx"
        }
        debug {
            // no keystore
        }
    }
    buildTypes {
        release {
            // 开启混淆
            minifyEnabled true
            // 对齐zip
            zipAlignEnabled true
            // 删除无用资源
            shrinkResources false
            // 是否debug
            debuggable false
            // 命名后缀
            versionNameSuffix "_release"
            // 签名
            signingConfig signingConfigs.release
            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'
            applicationVariants.all { variant ->
                variant.outputs.each { output ->
                    def outputFile = output.outputFile
                    if (outputFile != null && outputFile.name.endsWith('.apk')) {
                        // 输出apk名称为koku_v1.0_2015-01-15_wandoujia.apk
                        def fileName = "koku_v${defaultConfig.versionName}_${releaseTime()}_${variant.productFlavors[0].name}.apk"
                        output.outputFile = new File(outputFile.parent, fileName)
                    }
                }
            }
        }

        debug {
            zipAlignEnabled false
            shrinkResources false
            minifyEnabled false
            versionNameSuffix "_debug"
            signingConfig signingConfigs.debug
            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'
        }
    }
    lintOptions {
        abortOnError false
    }
    productFlavors {
        xiaomi {}
        googleplay {}
        wandoujia {}
    }
    //针对很多渠道
    productFlavors.all {
        flavor -> flavor.manifestPlaceholders = [UMENG_CHANNEL_VALUE: name]
    }
}

dependencies {
    compile fileTree(dir: 'libs', include: ['*.jar'])
    testCompile 'junit:junit:4.12'
    compile 'com.android.support:appcompat-v7:23.2.1'
    compile 'com.android.support:design:23.2.1'
}

def releaseTime() {
    return new Date().format("yyyy-MM-dd", TimeZone.getTimeZone("UTC"))
}
```

再多嘴一句，有了以上的 build.gradle 配置之后，如果想使用 Gradle 多渠道打包，需要在 AndroidManifest.xml 中申明：

```
<meta-data android:name="UMENG_CHANNEL" android:value="${UMENG_CHANNEL_VALUE}" />
```

最后使用命令 `gradlew assembleRelease` 打包即可。

根目录下的 gradle 文件夹
-------------
gradle 文件夹中主要是 gradle-wrapper.properties 文件比较重要，主要用来声明 Gradle 目录以及 Gradle 下载路径等：

```
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-2.14.1-all.zip
```

根目录下的 build.gradle
----------
根目录下的 build.gradle 主要作用就是定义项目中公共属性，比如有依赖仓库、 Gradle 构建版本等：

```
buildscript {
    repositories {
        jcenter()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:2.2.1'
        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle files
    }
}

allprojects {
    repositories {
        jcenter()
        mavenCentral()
    }
}

task clean(type: Delete) {
    delete rootProject.buildDir
}
```

setting.gradle
--------
setting.gradle 的作用就是一些模块被包含后，会在这里进行申明：

```
include ':app'
```

自定义 Gradle 文件
---------
在上面我们留了一个悬念，就是如何添加我们自定义的 Gradle 文件。接下来我们就动手来实践一下。在项目根目录下创建文件 config.gradle 。然后在根目录下的 build.gradle 开头添加一句 `apply from: "config.gradle"` ：

```
apply from: "config.gradle"

buildscript {
    repositories {
        jcenter()
    }
	...
}

...
```

这句话就代表着把 config.gradle 添加进来了。然后我们可以在 config.gradle 中申明一些配置：

```
ext {

    android = [
            compileSdkVersion: 23,
            buildToolsVersion: "23.0.3",
            applicationId    : "com.yuqirong.koku",
            minSdkVersion    : 14,
            targetSdkVersion : 23,
            versionCode      : 3,
            versionName      : "1.4"
    ]

    dependencies = [
            "appcompat-v7"            : 'com.android.support:appcompat-v7:23.0.1',
            "recyclerview-v7"         : 'com.android.support:recyclerview-v7:24.2.1',
            "design"                  : 'com.android.support:design:23.0.1'
    ]
}
```

最后在 app/build.gradle 中去使用：

```
android {
    compileSdkVersion rootProject.ext.android.compileSdkVersion
    buildToolsVersion rootProject.ext.android.buildToolsVersion
    defaultConfig {
        applicationId rootProject.ext.android.applicationId
        minSdkVersion rootProject.ext.android.minSdkVersion
        targetSdkVersion rootProject.ext.android.targetSdkVersion
        versionCode rootProject.ext.android.versionCode
        versionName rootProject.ext.android.versionName

        jackOptions {
            enabled true
        }
    }
	...
}

dependencies {
    compile fileTree(dir: 'libs', include: ['*.jar'])
    compile rootProject.ext.dependencies["appcompat-v7"]
    compile rootProject.ext.dependencies["recyclerview-v7"]
    compile rootProject.ext.dependencies["design"]
}
```

从上面可以看到，我们把一些固定的配置“拎”出来放到 config.gradle 中，这样以后直接更改 config.gradle 就行了，方便多人协作开发。

结束
=====
关于 Gradle 的平时经常使用方法基本上就上面这些了。其他的一些比如 `buildConfigField` 之类的可以自行百度，相信聪明的你很快就会了。但是 Gradle 并没有以上讲得那么简单，还需要童鞋们继续努力学习了。

如果对本文有不明白的地方，欢迎留言。

Goodbye !

References
====
* [给 ANDROID 初学者的 GRADLE 知识普及](http://stormzhang.com/android/2016/07/02/gradle-for-android-beginners/)
* [ANDROID 开发你需要了解的 GRADLE 配置](http://stormzhang.com/android/2016/07/15/android-gradle-config/)

title: 用Java实现Android多渠道打包工具
date: 2016-09-25 19:52:14
categories: Android Blog
tags: Android
---
0001b
======
最近在公司做了一个多渠道打包的工具，趁今天有空就来讲讲 Android 多渠道打包这件小事。众所周知，随着业务的不断增长，APP 的渠道也会越来越多，如果用 Gradle 打多渠道包的话，可能会耗费几个小时的时间才能打出几百个渠道包。所以就必须有一种方法能够解决这种问题。

目前市面上比较好的解决方案就是在 apk 文件中“动手脚”，比如由一位360 Android 工程师提出的“在 apk 文件中添加 comments 多渠道打包方法”，具体的代码在GitHub 上可以找到：[MultiChannelPackageTool](https://github.com/seven456/MultiChannelPackageTool) 。除此之外，还有美团点评技术团队在博客上发表过一篇[《美团Android自动化之旅—生成渠道包》](http://tech.meituan.com/mt-apk-packaging.html)，里面讲叙了一种在 apk 文件中的 META-INF 目录下添加渠道信息的方法，之后再在程序启动时去动态读取，具体的实现原理可以去美团博客上看，这里就不说了。

我们解压多渠道打出来的 apk 包后，就会发现在 META-INF 目录下多了一个 channel_xxxxx 文件，而这个就是我们的渠道文件：

![channel文件](/uploads/20160925/20160925221513.png)

本文所采用的方法就是根据美团提供的思路实现的，当然网上有很多使用 Python 语言实现美团思路的版本，经过测试发现 Python 版本比 Java 版本打渠道包的速度更快一些。但是，在这里只提供 Java 版本实现方案，Python 版本实现的方案会在文末以参考链接的方式给出。

0010b
======
在这里先说明一下，Java 编写的多渠道打包工具依赖 commons-io.jar 和 zip4j.jar 。下面我们就开始进入正题吧。

我们先规定一下，渠道文件命名为 channel.txt ，并且要打包的 apk 文件和 channel.txt 与多渠道打包工具在同一目录下。

其中 channel.txt 的格式就是每个渠道独占一行，如下所示：

	wandoujia
	googleplay
	xiaomi
	huawei
	kumarket
	anzhi

然后我们先定义几个常量：

``` java
// 渠道文件地址
private static final String CHANNEL_FILE_PATH = "./channel.txt";

private static final String CHARSET_NAME = "UTF-8";
// 当前要打包的apk的路径
private static final String APK_PATH = "./";
// 渠道打包后输出的apk文件夹前缀
private static final String APK_OUT_PATH_PREFIX = "./out_apk_";

private static final String APK_SUFFIX = ".apk";
```

定义好之后，我们下一步就是编写方法去读取 channel.txt 中的渠道信息：

``` java
/**
 * 从文件中读取channel
 * 
 * @return
 */
public static List<String> getChannel() {
	List<String> channelList = new ArrayList<>();
	InputStream inputStream = null;
	BufferedReader reader = null;
	try {
		inputStream = new FileInputStream(CHANNEL_FILE_PATH);
		reader = new BufferedReader(new InputStreamReader(inputStream,
				CHARSET_NAME));
		String buffer;
		while ((buffer = reader.readLine()) != null && buffer.length() != 0) {
			System.out.println("发现已有渠道 : " + buffer);
			channelList.add(buffer);
		}
	} catch (FileNotFoundException e) {
		System.out.println("当前目录下未找到channel.txt");
		e.printStackTrace();
	} catch (UnsupportedEncodingException e) {
		e.printStackTrace();
	} catch (IOException e) {
		e.printStackTrace();
	} finally {
		try {
			if (reader != null) {
				reader.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (inputStream != null) {
				try {
					inputStream.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	return channelList;
}
```

上面 `getChannel()` 方法中都是简单的 I/O 流操作，相信不需要解释大家都可以看得懂吧。之后我们要做的就是去当前路径下查找有无 apk 文件。在这里说明一下，我们这个多渠道打包小工具是支持多个 apk 文件一起打包的，所以我们要把当前目录下所有 apk 文件的路径存储起来。

``` java
/**
 * 得到当前目录下的所有apk
 * 
 * @param file
 * @return
 */
public static List<String> getApk(File file) {
	List<String> apkList = new ArrayList<>();
	File[] childFiles = file.listFiles();
	for (File childFile : childFiles) {
		if (!childFile.isDirectory()
				&& childFile.getName().endsWith(APK_SUFFIX)) {
			System.out.println("发现已有apk : " + childFile.getName());
			apkList.add(childFile.getName());
		}
	}
	return apkList;
}
```
做好上面的步骤后，最后就剩下打包的代码了，一起来看看：

``` java
/**
 * 打包apk
 */
public static void buildApk() {
	List<String> apkList = getApk(new File(APK_PATH));
	int count = apkList.size();
	if (count == 0) {
		System.out.println("当前目录下没有发现apk文件");
		return;
	}
	// 遍历所有apk文件
	for (int i = 0; i < count; i++) {
		String name = apkList.get(i);
		// 得到文件名字
		String baseName = apkList.get(i).substring(0,
				name.lastIndexOf("."));
		// apk输出目录
		File dictionary = new File(APK_OUT_PATH_PREFIX + baseName);
		if (!dictionary.exists()) {
			dictionary.mkdir();
		}
		List<String> channelList = getChannel();
		if (channelList.size() == 0) {
			System.out.println("channel.txt文件中没有多渠道信息");
			return;
		}
		// 遍历所有渠道
		for (String channel : channelList) {
			try {
				String sourceFileName = APK_PATH + name;
				// 输出的apk名字
				String outApkName = baseName + "_" + channel + APK_SUFFIX;
				// apk包的路径
				String outApkFileName = dictionary.getName() + "/" + outApkName;
				// 复制要打包的apk
				copy(sourceFileName, outApkFileName);
				System.out.println("正在打 " + channel + " 的渠道包 : " + outApkName);
				ZipFile zipFile = new ZipFile(outApkFileName);
				ZipParameters parameters = new ZipParameters();
				parameters
						.setCompressionMethod(Zip4jConstants.COMP_DEFLATE);
				parameters
						.setCompressionLevel(Zip4jConstants.DEFLATE_LEVEL_NORMAL);
				parameters.setRootFolderInZip("META-INF/");
				// 当前目录下创建一个channel_xxxxx文件
				File channelFile = new File(dictionary.getName() + "/channel_"
						+ channel);
				if (!channelFile.exists()) {
					channelFile.createNewFile();
				}
				// 在META-INF文件夹中添加channel_xxxxx文件
				zipFile.addFile(channelFile, parameters);
				// 删除当前目录下的channel_xxxxx文件
				channelFile.delete();
			} catch (ZipException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}

/**
 * 复制文件
 * 
 * @param sourceFilePath
 * @param copyFilePath
 */
private static void copy(String sourceFilePath, String copyFilePath){
	try {
		// 这里使用的是 common-io.jar 中的文件复制方法，比原生Java I/O API操作速度要快
		FileUtils.copyFile(new File(sourceFilePath), new File(copyFilePath));
	} catch (IOException e) {
		e.printStackTrace();
	}
}

public static void main(String[] args) {
	long preTime = System.currentTimeMillis();
	buildApk();
	System.out.println("多渠道打包完成，耗时 " + (System.currentTimeMillis() - preTime)/1000 + " s");
}
```

`buildApk()` 方法中主要做的就是两个 for 循环嵌套。遍历当前目录的 apk 文件，然后遍历渠道信息，最后打包。另外需要注意的是要复制出一个 apk 文件来进行多渠道打包，而不是在原文件的基础上。

在这里打包的部分就结束了，我们还有一个步骤需要完成。那就是在应用程序启动时去读取相应的渠道，可以通过以下方法去读取：

``` java
public static String getChannelFromMeta(Context context) {
    ApplicationInfo appinfo = context.getApplicationInfo();
    String sourceDir = appinfo.sourceDir;
    String ret = "";
    ZipFile zipfile = null;
    try {
        zipfile = new ZipFile(sourceDir);
        Enumeration<?> entries = zipfile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = ((ZipEntry) entries.nextElement());
            String entryName = entry.getName();
            if (entryName.startsWith("META-INF/channel_")) {
                ret = entryName;
                break;
            }
        }
    } catch (IOException e) {
        e.printStackTrace();
    } finally {
        if (zipfile != null) {
            try {
                zipfile.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    String[] split = ret.split("_");
    if (split != null && split.length >= 2) {
        return ret.substring(split[0].length() + 1);
    } else {
        return "default";
    }
}
```

读取渠道之后，我们 APP 可以把相应的渠道号发送给服务器或者第三方统计平台做统计。

0011b
====
最后，我们可以把这个多渠道打包的 Java 项目打成一个 jar 包，然后写一个 bat 脚本，这样就通过鼠标双击就可以实现快速打渠道包了。以下是 bat 脚本的内容，要注意的是 bat 脚本要和 jar 包处于同一级目录下才可以哦：

	@echo off
	echo 欢迎使用多渠道打包工具
	echo 请确保当前目录下有要打包的apk文件和渠道信息channel.txt
	java -jar AndroidBuildApkTool.jar
	echo 按任意键退出
	pause>nul
	exit

通过我们的努力 Java 版的多渠道打包工具就做好了。但是不足的是，测试后发现 Java 版打渠道包的速度没有 Python 版的快，主要是在 apk 文件中添加渠道信息文件这一步操作耗费的时间有点多。如果哪位小伙伴有更好的解决方案，欢迎联系我！

附上多渠道打包工具的源码：

[MultiChannelBuildTool.rar](/uploads/20160925/MultiChannelBuildTool.rar)

0100b
======
References：

* [Gradle 多渠道打包实践](http://www.jianshu.com/p/7236ceca2630)
* [快速多渠道打包](http://www.jianshu.com/p/e0783783d26d)
* [深入浅出Android打包](http://geek.csdn.net/news/detail/76488)
* [美团Android自动化之旅—生成渠道包](http://tech.meituan.com/mt-apk-packaging.html)
* [MultiChannelPackageTool](https://github.com/seven456/MultiChannelPackageTool)
title: 初探Android ORM框架之greenDAO
date: 2015-11-24 21:14:30
categories: Android Blog
tags: [Android,开源框架]
---
在Android开发中，我们都不可避免地要使用SQLite数据库来存储数据。但是Android提供给我们的API在操作数据库中并不简洁，而且更重要的一点是，在读取数据时无法把读到的字段直接映射成对象，需要我们手动去写代码↖(>﹏<)↗。于是在这种情况下，产生了许多ORM (对象关系映射 英语：Object Relational Mapping) 的第三方框架，比如greenDAO、ActiveAndroid、ormlite等。说到ORM，相信有过J2EE开发经验的童鞋对此并不陌生，在web开发中就有Hibernate、MyBatis等框架提供使用。那么今天就来介绍一下主角：greenDAO。

根据 [greenrobot](http://greenrobot.org/) 官方的介绍，greenDAO是一款轻量，快速，适用于Android数据库的ORM框架。具有很高的性能以及消耗很少的内存。其他的优点和特性就不在这里一一介绍了，想要了解的同学可以去访问它的项目地址：[https://github.com/greenrobot/greenDAO](https://github.com/greenrobot/greenDAO)。

说了这么多，下面就开始我们的正题吧。

在使用greenDAO之前，我们有一件事情不得不做，那就是用代码生成器生成数据模型以及xxxDao等

新建一个java module，取名greendaogeneration(名字随意取，不要在意细节↖(^ω^)↗)，然后在build.gradle(Module:greendaogeneration)中添加依赖：

`compile 'de.greenrobot:greendao-generator:2.0.0'`

在 src/main 目录下新建一个与 java 同层级的 java-gen 目录，然后配置 Android 工程的 build.gradle(Module:app)，分别添加如下sourceSets。

	android {
	    compileSdkVersion 23
	    buildToolsVersion "23.0.2"
	
	    defaultConfig {
	        applicationId "com.yuqirong.greendaodemo"
	        minSdkVersion 14
	        targetSdkVersion 23
	        versionCode 1
	        versionName "1.0"
	    }
	    buildTypes {
	        release {
	            minifyEnabled false
	            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'
	        }
	    }
	    sourceSets {
	        main {
	            java.srcDirs = ['src/main/java', 'src/main/java-gen']
	        }
	    }
	}

然后在greendaogeneration中创建一个GreenDaoGeneration类，用于生成代码：

``` java
public class GreenDaoGeneration {

    public static void main(String[] arg0) {

        try {
            Schema schema = new Schema(1, "com.yuqirong.greendao");
            addLocation(schema);
            new DaoGenerator().generateAll(schema, "C:/Users/yuqirong/Desktop/GreenDaoDemo/app/src/main/java-gen");
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static void addLocation(Schema schema){
        Entity location= schema.addEntity("Location");
        location.addIdProperty();
        location.addDoubleProperty("lon");
        location.addIntProperty("level");
        location.addStringProperty("address");
        location.addStringProperty("city_name");
        location.addIntProperty("alevel");
        location.addDoubleProperty("lat");
    }
}
```

其中在创建Schema对象的参数中，第一个表示数据库的版本号，我们传入了“1”，第二个参数是生成代码的包名，我们传入了"com.yuqirong.greendao"，那么生成的代码就自动在"com.yuqirong.greendao"包下了。

在addLocatiion方法中，我们打算把“Location”这个类(其实到现在为止，Location实体类还未生成)保存在数据库中，`schema.addEntity("Location")`传入的Location，那么该表的表名就叫Location，然后我们又定义了在Location表中会有lon、lat、city_name三个字段。所以如果你想创建多张表，那么就要像addLocation()这样的方法多写几个。好了，该做的差不多都做了，最后再` new DaoGenerator().generateAll(schema, "C:/Users/yuqirong/Desktop/GreenDaoDemo/app/src/main/java-gen");`把之前的schema传入，第二个参数传的是之前创建的java-gen的路径(建议传入绝对路径，之前在这里被坑了好久↖(~^~)↗)。代码运行之后再到"com.yuqirong.greendao"路径下去看，发现会有许多类生成：

![这里写图片描述](http://ofyt9w4c2.bkt.clouddn.com/20151124/20151124235609.png)

至于这些类的作用，我们到下面再说。

第二步在Android Studio的build.gradle(Module:app)中添加以下依赖：

`compile 'de.greenrobot:greendao:2.0.0'`  

这样我们就可以在项目中使用greenDAO了。

在正式使用greenDAO前我们需要了解几个greenDAO中的类(也就是上面代码生成的几个类)：

* DaoMaster ：一看这个类名我们就知道这个类肯定是大总管级别的，DaoMaster继承自AbstractDaoMaster。在AbstractDaoMaster中保存了sqlitedatebase对象以及以Map的形式保存了各种操作DAO。DaoMaster还提供了一些创建和删除table的静态方法。另外在DaoMaster类里面有一个静态内部类DevOpenHelper，DevOpenHelper间接继承了SQLiteOpenHelper，通常我们会用`new DaoMaster.DevOpenHelper(this, "notes-db", null)`来得到一个DevOpenHelper对象，第一个参数是Context，第二个参数是数据库名，第三个参数是CursorFactory，通常我们传入null。就上面这样简单的一句话你就实际上创建了一个SQLiteOpenHelper对象，而不需要输入`"CREATE TABLE..."` SQL语句，greenDAO已经帮你做好了一切。

* DaoSession：会话层。主要功能就是操作具体的DAO对象，比如各种getXXXDao()方法。

* XXXDao：实际生成的DAO类(即生成的LocationDao)，主要对应于某张表的CRUD，比如说LocationDao，那相对应就是对Location表的操作。

* XXXEntity：主要是各个实体类(也就是上面生成的Location)，里面的属性与表中的字段相对应。比如上面的LocationDao，那么实体类就是Location，Location实体类中有lon，lat，city_name,alevel,level,address六个属性，那么在Location表中就有lon，lat，city_name,alevel,level,address六个字段。

通过上面几个类作用的介绍，相信大家对greenDAO有了一个初步的印象，下面我们就要真枪实弹了，一起来看一个简单的Demo吧：

	  DaoMaster.DevOpenHelper devOpenHelper = new DaoMaster.DevOpenHelper(context, "location", null);
      SQLiteDatabase writableDB = devOpenHelper.getWritableDatabase();
      DaoMaster daoMaster = new DaoMaster(writableDB);
      DaoSession daoSession = daoMaster.newSession();

上面的代码很简单，我们创建了一个名叫“location”的数据库，然后通过daoMaster得到了daoSession，有了daoSession，我们就可以得到各种xxxDao，之后的CRUD都是通过xxxDao来操作。

	  LocationDao locationDao = daoSession.getLocationDao();
      Location local = new Location(null,120.15507,2,"","杭州市",4,30.27408);
      locationDao.insert(local);

我们创建了一个Location的对象，然后调用`locationDao.insert()`方法就把local的数据插入到location的表中，是不是简单到难以让人置信?!只需要三行代码，而不再需要原生的ContentValues了。不得不感叹greenDAO太方便了。

除了添加数据的，greenDAO还提供删除数据的方法：`locationDao.deleteByKey(id);`id为当前要删除的那行的主键。

更新数据的方法：`locationDao.update(local);`local为新的数据。

查询数据的方法： 

* `locationDao.queryRaw(String where, String...selectionArg)`,可以看到greenDAO支持sql语句查询。
* greenDAO还支持一种更为简单的查询方式，不再需要你去写sql语句(查询Location的lon大于120度和lat大于30度)： 

		List<Location> list = locationDao.queryBuilder()
                                .where(LocationDao.Properties.Lat.gt(30d), LocationDao.Properties.Lon.gt(120d))
                                .build()
                                .list();

好了，关于greenDAO的简单使用就先到这里，至于深入使用我们有机会再讲吧！

依据惯例，下面提供本Demo的源码:

[GreenDaoDemo.rar](http://ofytl4mzu.bkt.clouddn.com/20151124/GreenDaoDemo.rar)
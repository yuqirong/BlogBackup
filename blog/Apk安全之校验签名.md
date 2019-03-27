title: Apk安全之校验签名
date: 2019-03-18 23:17:31
categories: Android Blog
tags: [Android]
---
校验签名
=======
一般绝大多数的 app 在上线前都会做一层安全防护，比如代码混淆、加固等。

今天就来讲讲其中的一项：校验签名。

校验签名可以有效的防止二次打包，避免你的 app 被植入广告甚至破解等。而今天就从两个角度来讲签名的具体校验：

* Java 层
* C/C++ 层

那么就先开始讲 java 层好了。

Java 层
=======
``` java
private static boolean validateAppSignature(Context context, String apkSignature) {
    try {
        PackageManager packageManager = context.getApplicationContext().getPackageManager();
        PackageInfo packageInfo = packageManager.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);
        for (Signature signature : packageInfo.signatures) {
            String lowerCaseSignature = signature.toCharsString().toLowerCase();
            if (lowerCaseSignature.equals(apkSignature)) {
                return true;
            }
        }
    } catch (PackageManager.NameNotFoundException e) {
        e.printStackTrace();
    }
    return false;
}
```

Java 层的签名校验核心代码就这些，传入的两个参数 ：

* Context context : 一般都是 Application
* String apkSignature : 你的 apk 的正式签名

Java 层的签名校验比较容易被攻破，因为别人可以反编译一下，然后在 smali 中把 validateAppSignature 方法的返回值改成 true 就大功告成了。

也正因为如此，所以需要在 C/C++ 层中也加入签名校验。

C/C++ 层
========
在 so 文件加载的时候，会去调用 JNI_OnLoad 函数，所以我们可以在这里做签名校验。

``` C
JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env = NULL;
    jint result = -1;
    if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        LOGE("no jni version 1.6");
        return result;
    }
    if (checkAppSignature(env) != JNI_TRUE) {
        LOGE("the signature of apk is invalid");
        return result;
    }
    return JNI_VERSION_1_6;
}
```

签名校验的代码主要在 checkAppSignature 函数中：

``` java
static jboolean checkAppSignature(JNIEnv *env) {
    jclass classNativeContext = (*env)->FindClass(env, "me/yuqirong/security/Security");
    jmethodID midGetAppContext = (*env)->GetStaticMethodID(env, classNativeContext,
                                                        "getContext",
                                                        "()Landroid/content/Context;");
    jobject appContext = (*env)->CallStaticObjectMethod(env, classNativeContext, midGetAppContext);

    if (appContext != NULL) {
        jboolean signatureValid = Android_checkSignature(env, NULL, appContext);
        return signatureValid;
    } else {
        LOGE("app context is null, please check the context");
    }

    return JNI_FALSE;
}
```

可以看出来，checkAppSignature 主要是通过 C 的代码反射来获取 Context 。

对应的 Java 层代码如下，一般来说， `Security.setContext(application)` 会在 Application.onCreate 方法中调用 :

``` java
public class Security {

	private static Application application;
	
	public static Context getContext() {
	    return application;
	}
	
	private static void setContext(Application context) {
	    application = context;
	}

}
```

获取到 Context 之后，就可以来比较签名了 ：

``` C
const char* APP_SIGNATURE = "input your signature of apk";

static jboolean Android_checkSignature(
        JNIEnv *env, jclass clazz, jobject context) {

    jstring appSignature = loadSignature(env, context);
    jstring releaseSignature = (*env)->NewStringUTF(env, APP_SIGNATURE);
    const char *charAppSignature = (*env)->GetStringUTFChars(env, appSignature, NULL);
    const char *charReleaseSignature = (*env)->GetStringUTFChars(env, releaseSignature, NULL);

    jboolean result = JNI_FALSE;
    if (charAppSignature != NULL && charReleaseSignature != NULL) {
        if (strcmp(charAppSignature, charReleaseSignature) == 0) {
            LOGI("the signature of apk is valid, so pass it");
            result = JNI_TRUE;
        }
    }

    (*env)->ReleaseStringUTFChars(env, appSignature, charAppSignature);
    (*env)->ReleaseStringUTFChars(env, releaseSignature, charReleaseSignature);

    return result;
}
```

这里的 APP_SIGNATURE 就是正式版的签名字符串，而 loadSignature 函数需要反射安卓系统的 API 才能获得。

``` java
static jstring loadSignature(JNIEnv *env, jobject context) {
    // 获得Context类
    jclass cls = (*env)->GetObjectClass(env, context);
    // 得到getPackageManager方法的ID
    jmethodID mid = (*env)->GetMethodID(env, cls, "getPackageManager", "()Landroid/content/pm/PackageManager;");

    // 获得应用包的管理器
    jobject pm = (*env)->CallObjectMethod(env, context, mid);

    // 得到getPackageName方法的ID
    mid = (*env)->GetMethodID(env, cls, "getPackageName", "()Ljava/lang/String;");
    // 获得当前应用包名
    jstring packageName = (jstring) (*env)->CallObjectMethod(env, context, mid);

    // 获得PackageManager类
    cls = (*env)->GetObjectClass(env, pm);
    // 得到getPackageInfo方法的ID
    mid = (*env)->GetMethodID(env, cls, "getPackageInfo", "(Ljava/lang/String;I)Landroid/content/pm/PackageInfo;");
    // 获得应用包的信息
    jobject packageInfo = (*env)->CallObjectMethod(env, pm, mid, packageName, 0x40); //GET_SIGNATURES = 64;
    // 获得PackageInfo 类
    cls = (*env)->GetObjectClass(env, packageInfo);
    // 获得签名数组属性的ID
    jfieldID fid = (*env)->GetFieldID(env, cls, "signatures", "[Landroid/content/pm/Signature;");
    // 得到签名数组
    jobjectArray signatures = (jobjectArray) (*env)->GetObjectField(env, packageInfo, fid);
    // 得到签名
    jobject signature = (*env)->GetObjectArrayElement(env, signatures, 0);

    // 获得Signature类
    cls = (*env)->GetObjectClass(env, signature);
    // 得到toCharsString方法的ID
    mid = (*env)->GetMethodID(env, cls, "toCharsString", "()Ljava/lang/String;");
    // 返回当前应用签名信息
    jstring signatureString = (jstring) (*env)->CallObjectMethod(env, signature, mid);

    // toLowerCase
    cls = (*env)->GetObjectClass(env, signatureString);
    mid = (*env)->GetMethodID(env, cls, "toLowerCase", "()Ljava/lang/String;");
    jstring lowerCaseSignatureString = (jstring) (*env)->CallObjectMethod(env, signatureString, mid);

    return lowerCaseSignatureString;
}
```

loadSignature 函数可以说就是用 C 语言把上面 Java 的那段代码实现了一遍，并没有什么差别。

至此，有了 Java 和 C/C++ 的双重保护，app 的安全性又提升了一级。


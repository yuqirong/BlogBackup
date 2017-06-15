title: Kotlin入入入门(二)
date: 2017-06-13 20:16:20
categories: Kotlin Blog
tags: [Android,Kotlin]
---
找不同
=====
之前在 [Kotlin入入入门(一)][url] 中已经介绍了如何配置 Kotlin 。另外，还把 Java 代码转换为了 Kotlin 代码。所以今天我们就来比较一下这两者代码之间的区别，从而实现快速入门 Kotlin 。

[url]: /2017/06/07/Kotlin入入入门(一)/

Now ，我们把之前相同含义的 Java 和 Kotlin 代码粘贴出来（上面是 Java 代码，下面是 Kotlin 代码）：

``` java
package me.yuqirong.kotlindemo;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        TextView textView = (TextView) findViewById(R.id.textView);
        textView.setText("Hello World");
    }
}
```

``` kotlin
package me.yuqirong.kotlindemo

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        textView.text = "Hello World"
    }
}
```

package
-------
我们先慢慢地从上往下看，第一句 `package` 语句就有所不同。我们发现 Kotlin 中的所有代码没有以 `;` 结尾。另外，在 Kotlin 中并不要求包与目录匹配。即源文件可以在文件系统中的任意位置。

比如 `package me.yuqirong.kotlintest` 可能位于 /me/yuqirong/kotlintest2/ 文件夹下，并不会报错；而在 Java 中，包与目录必须匹配。

但是需要注意的一点是，在 AndroidManifest.xml 中配置的 Activity 的全类名必须和其路径一致，否则会找不到！

class
-----
在 Kotlin 中，`class` 默认是 `public` 的，所以平常都省略不写。

继承父类和实现接口都用 `:` 来表示。不同的是继承父类是带 `()` 的，即表示构造器，比如上面的 `AppCompatActivity()` ；而接口则不需要 `()` 。

举个例子：

``` kotlin
class MainActivity : AppCompatActivity(), View.OnClickListener {
	...
}
```

`AppCompatActivity()` 就是继承，而 `View.OnClickListener` 就是实现。

method
------
从比较的代码中可以知道：

1. 在 Kotlin 中默认方法的修饰符就是 `public` ，可以省略不写。
2. 在 Kotlin 中重写的方法是要加 `override` 关键字的，而 Java 是以注解 `@Override` 来修饰的；
3. 在 Kotlin 中方法都是用 `fun` 关键字来声明的；
4. 在 Kotlin 中方法的参数是参数名在前，参数类型在后，中间以 `:` 隔开；若参数可能为空，则在参数类型后加 `?` 来表示。即上面代码中的 `(savedInstanceState: Bundle?)` ；
5. 和参数表示类似，返回值也是以 `: 返回类型` 的方式表示的。比如上面的 Kotlin 代码可写为
		
	``` kotlin
	override fun onCreate(savedInstanceState: Bundle?): Unit {
		...
	}
	```

	Kotlin 中的 `Unit` 类型即 Java 中的 void 类型，可以省略不写。

举个例子：

方法名 `multiplication` ，参数 `int a` 和 `int b` ，返回 `a` 和 `b` 相乘的值：

``` kotlin
fun multiplication(a: Int, b: Int): Int {
    return a * b
}
```

有人也许会有疑问，这和 Java 代码行数也差不多嘛。

当然还有更加简单的写法，函数体可以是表达式，并可从中推断出返回值类型。返回类型就可以省略不写了：

``` kotlin
fun multiplication(a: Int, b: Int) = a * b
```

附加题
=====
定义变量
-------
只读变量 val

``` kotlin
val i: Int = 1
// 推测为 Int 类型
val j = 2
```

Kotlin 中的 `val` 关键字就类似于 Java 中的 `final` 。

可变变量 var

``` kotlin
var i: Int = 1
i += 1
```

字符串模板
---------
字符串可以包含模板表达式，即可求值的代码片段，并将其结果连接到字符串中。一个模板表达式由一个 $ 开始并包含另一个简单的名称。

举个例子：

``` kotlin
fun main(args: Array<String>) {
    val text = "World!"
    println("Hello, ${text}") // 也可以去掉{}，即 println("Hello, $text")
}
```

运行结果：Hello, World!

基本类型
-------
Kotlin 基本类型包括了数值、字符、布尔、字符串和数组。

``` kotlin
// int 类型
val a: Int = 10000
// double 类型
val b: Double = 125.2
// float 类型
val c: Float = 123.2f
// long 类型
val d: Long = 1234L
// boolean 类型
val e: Boolean = false
// char 类型
val f: Char = 'e'
// string 类型
val g: String = "hello"
// byte 类型
val h: Byte = 1
// short 类型
val i: Short = 3
// 数组类型
val x: IntArray = intArrayOf(1, 2, 3)
```

流程控制
-------
* if 表达式
	除了 Java 中 if 使用方法外，在 Kotlin 中还支持如下的写法：
	
	``` kotlin
	fun main(args: Array<String>) {
	    val a = 101
	    val b: Boolean = if(a > 100){
		    print("a > 100 ")
		    true
		    }else{
		    print("a <= 100 ")
		    false
		    }
	    println(b)
	    val c: Boolean = if(a > 0) true else false
	}
	```

	运行结果：a > 100 true
	
	``` kotlin
	fun main(args: Array<String>) {
	    println(testIf(100))
	}
	
	fun testIf(a: Int) = if(a > 100) true else false
	```
	运行结果：false

* when 表达式
	Kotlin 中的 when 表达式就是 Java 中的 switch 表达式，具体例子如下：
	
	``` kotlin
	when (x) {
	    1 -> print("x == 1")
	    2 -> print("x == 2")
	    else -> print("x is neither 1 nor 2")
	}
	```

* for 循环
	``` kotlin
	val collection: IntArray = intArrayOf(1, 2, 3)
	for (item in collection) {
		print(item)
	}
	```

* while 循环
	while 循环与 Java 中的无异。

End
===
今天就讲到这里了，更多 Kotlin 的使用方法就期待下一篇吧！

Goodbye ! ~ ~

更多关于 Kotlin 的博客：

* [Kotlin入入入门(一)][url]

[url]: /2017/06/07/Kotlin入入入门(一)/

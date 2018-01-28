title: ArrayList内部原理解析
date: 2018-01-21 18:12:10
categories: Java Blog
tags: [Java,数据结构,源码解析]
---
注：本文解析的 ArrayList 源代码基于 Java 1.8 。

Header
======
之前讲了 HashMap 的原理后，今天来看一下 ArrayList 。

ArrayList 也是非常常用的集合类。它是有序的并且可以存储重复元素的。 ArrayList 底层其实就是一个数组，并且会动态扩容的。

源码分析
=======
构造方法
-------
``` java
public ArrayList(int initialCapacity) {
    if (initialCapacity > 0) {
        // 创建初始容量的数组
        this.elementData = new Object[initialCapacity];
    } else if (initialCapacity == 0) {
        this.elementData = EMPTY_ELEMENTDATA;
    } else {
        throw new IllegalArgumentException("Illegal Capacity: "+
                                           initialCapacity);
    }
}

public ArrayList() {
    // 默认为空数组
    this.elementData = DEFAULTCAPACITY_EMPTY_ELEMENTDATA;
}

public ArrayList(Collection<? extends E> c) {
    elementData = c.toArray();
    if ((size = elementData.length) != 0) {
        // c.toArray might (incorrectly) not return Object[] (see 6260652)
        // 将集合中的元素复制到数组中
        if (elementData.getClass() != Object[].class)
            elementData = Arrays.copyOf(elementData, size, Object[].class);
    } else {
        // replace with empty array.
        this.elementData = EMPTY_ELEMENTDATA;
    }
}
```

构造方法中的代码比较简短，大家都能理解的吧。

add()
-----
``` java
    public boolean add(E e) {
        // 确保数组的容量，保证可以添加该元素
        ensureCapacityInternal(size + 1);  // Increments modCount!!
        // 将该元素放入数组中
        elementData[size++] = e;
        return true;
    }
```

发现在 `add()` 方法中，代码很简短。可以看出之前的预操作都放入了 `ensureCapacityInternal` 方法中，这个方法会去确保该元素在数组中有位置可以放入。

那么我们来看看这个方法：

``` java
    private void ensureCapacityInternal(int minCapacity) {
        // 如果数组是空的，那么会初始化该数组
        if (elementData == DEFAULTCAPACITY_EMPTY_ELEMENTDATA) {
            // DEFAULT_CAPACITY 为 10 ，所以调用无参默认 ArrayList 构造方法初始化的话，默认的数组容量为 10
            minCapacity = Math.max(DEFAULT_CAPACITY, minCapacity);
        }
    
        ensureExplicitCapacity(minCapacity);
    }

    private void ensureExplicitCapacity(int minCapacity) {
        modCount++;

        // 确保数组的容量，如果不够的话，调用 grow 方法扩容
        if (minCapacity - elementData.length > 0)
            grow(minCapacity);
    }
```

看了半天，扩容是在 grow 方法中完成的，所以我们接着跟进。

``` java
    private void grow(int minCapacity) {
        // 当前数组的容量
        int oldCapacity = elementData.length;
        // 新数组扩容为原来容量的 1.5 倍
        int newCapacity = oldCapacity + (oldCapacity >> 1);
        // 如果新数组扩容容量还是比最少需要的容量还要小的话，就设置扩充容量为最小需要的容量
        if (newCapacity - minCapacity < 0)
            newCapacity = minCapacity;
        //判断新数组容量是否已经超出最大数组范围，MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8
        if (newCapacity - MAX_ARRAY_SIZE > 0)
            newCapacity = hugeCapacity(minCapacity);
        // minCapacity is usually close to size, so this is a win:
        // 复制元素到新的数组中
        elementData = Arrays.copyOf(elementData, newCapacity);
    }
```

扩容方法其实就是新创建一个数组，然后将旧数组的元素都复制到新数组里面。

当然，add 还有一个重载的方法 `add(int index, E element)` ，顺便我们也来看一下。

``` java
    public void add(int index, E element) {
        // 判断 index 有没有超出索引的范围
        rangeCheckForAdd(index);
        // 和之前的操作是一样的，都是保证数组的容量足够
        ensureCapacityInternal(size + 1);  // Increments modCount!!
        // 将指定位置及其后面数据向后移动一位
        System.arraycopy(elementData, index, elementData, index + 1,
                         size - index);
        // 将该元素添加到指定的数组位置
        elementData[index] = element;
        // ArrayList 的大小改变
        size++;
    }
```

好了，add 方法看的差不多了，剩下还有一个 `addAll(Collection<? extends E> c)` 方法也是换汤不换药的，可以自己回去看下，这里就不讲了。

get()
----
get 方法很简单，就是在数组中返回指定位置的元素即可。

``` java
    public E get(int index) {
        // 检查 index 有没有超出索引的范围
        rangeCheck(index);
        // 返回指定位置的元素
        return elementData(index);
    }
```

remove()
--------

``` java
    public E remove(int index) {
        // 检查 index 有没有超出索引的范围
        rangeCheck(index);

        modCount++;
        // 保存一下需要删除的数据
        E oldValue = elementData(index);
        // 让指定删除的位置后面的数据，向前移动一位
        int numMoved = size - index - 1;
        if (numMoved > 0)
            System.arraycopy(elementData, index+1, elementData, index,
                             numMoved);
        // 方便 gc 释放内存
        elementData[--size] = null; // clear to let GC do its work
        // 返回旧值
        return oldValue;
    }
```

remove 中主要是将之后的元素都向前一位移动，然后将最后一位的值设置为空。最后，返回已经删除的值。

同样，remove 还有一个重载的方法 `remove(Object o)` 。

``` java
    public boolean remove(Object o) {
        if (o == null) {
            // 如果有元素的值为 null 的话，移除该元素，fastRemove 的操作和上面的 remove(int index) 是类似的
            for (int index = 0; index < size; index++)
                if (elementData[index] == null) {
                    fastRemove(index);
                    return true;
                }
        } else {
            // 如果有元素的值等于 o 的话，移除该元素
            for (int index = 0; index < size; index++)
                if (o.equals(elementData[index])) {
                    fastRemove(index);
                    return true;
                }
        }
        return false;
    }
```

clear()
-------
clear 方法无非就是遍历数组，然后把所有的值都置为 null 。

``` java
    public void clear() {
        modCount++;

        // clear to let GC do its work
        for (int i = 0; i < size; i++)
            elementData[i] = null;

        size = 0;
    }
```

Footer
======
至此，ArrayList 主要的几个方法就讲完了。ArrayList 的源码还是比较简单的，基本上都可以看得明白。

我们来总结一下：

1. ArrayList底层是基于数组来实现的，因此在 get 的时候效率高，而 add 或者 remove 的时候，效率低；
2. 调用默认的 ArrayList 无参构造方法的话，数组的初始容量为 10 ；
3. ArrayList 会自动扩容，扩容的时候，会将容量扩至原来的 1.5 倍；
4. ArrayList 不是线程安全的；

那么今天就这样了，之后有空给大家讲讲 LinkedList 。

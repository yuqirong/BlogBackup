title: HashMap内部原理解析
date: 2018-01-13 18:14:31
categories: Java Blog
tags: [Java,数据结构,源码解析]
---
注：本文解析的 HashMap 源代码基于 Java 1.7 。

Header
======
HashMap 在平时 Java/Android 开发中，是绝大多数开发者都普遍使用的集合类。

它内部是基于哈希表实现的键值对存储，继承 AbstractMap 并且实现了 Map 接口。

而对于它的 get/put 使用方法相信大家都已经到了炉火纯青的地步。虽然都会用，却可能没有好好深入探讨过 HashMap 内部的实现原理。正好趁着有时间，今天就给大家一步步地解析 HashMap 的内部实现原理。

在这就基于了 Java 1.7 的源代码来讲解了，Java 1.8 的 HashMap 源码相比 Java 1.7 做了一些改动。具体的改动等到我们最后再说。

HashMap 必知
=================
以下是 HashMap 源码里面的一些关键成员变量以及知识点。在后面的源码解析中会遇到，所以我们有必要先了解下。

1. initialCapacity：初始容量。指的是 HashMap 集合初始化的时候自身的容量。可以在构造方法中指定；如果不指定的话，总容量默认值是 16 。需要注意的是初始容量必须是 2 的幂次方。
2. size：当前 HashMap 中已经存储着的键值对数量，即 `HashMap.size()` 。
3. loadFactor：加载因子。所谓的加载因子就是 HashMap (当前的容量/总容量) 到达一定值的时候，HashMap 会实施扩容。加载因子也可以通过构造方法中指定，默认的值是 0.75 。举个例子，假设有一个 HashMap 的初始容量为 16 ，那么扩容的阀值就是 0.75 * 16 = 12 。也就是说，在你打算存入第 13 个值的时候，HashMap 会先执行扩容。
4. threshold：扩容阀值。即 扩容阀值 = HashMap 总容量 * 加载因子。当前 HashMap 的容量大于或等于扩容阀值的时候就会去执行扩容。扩容的容量为当前 HashMap 总容量的两倍。比如，当前 HashMap 的总容量为 16 ，那么扩容之后为 32 。
5. table：Entry 数组。我们都知道 HashMap 内部存储 key/value 是通过 Entry 这个介质来实现的。而 table 就是 Entry 数组。
6. 在 Java 1.7 中，HashMap 的实现方法是数组 + 链表的形式。上面的 table 就是数组，而数组中的每个元素，都是链表的第一个结点。即如下图所示：
	![20180114111559](/uploads/20180114/20180114111559.png)

源码分析
=======
构造方法
-------
``` java
    // 默认的构造方法使用的都是默认的初始容量和加载因子
    // DEFAULT_INITIAL_CAPACITY = 16，DEFAULT_LOAD_FACTOR = 0.75
    public HashMap() {
        this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR);
    }

    // 可以指定初始容量，并且使用默认的加载因子
    public HashMap(int initialCapacity) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    public HashMap(int initialCapacity, float loadFactor) {
        // 对初始容量的值判断
        if (initialCapacity < 0)
            throw new IllegalArgumentException("Illegal initial capacity: " +
                                               initialCapacity);
        if (initialCapacity > MAXIMUM_CAPACITY)
            initialCapacity = MAXIMUM_CAPACITY;
        if (loadFactor <= 0 || Float.isNaN(loadFactor))
            throw new IllegalArgumentException("Illegal load factor: " +
                                               loadFactor);
        // 设置加载因子
        this.loadFactor = loadFactor;
        threshold = initialCapacity;
        // 空方法
        init();
    }
```

HashMap 的所有构造方法最后都会去调用 `HashMap(int initialCapacity, float loadFactor)` 。在其内部去设置初始容量和加载因子。而最后的 `init()` 是空方法。

put 方法
-------
``` java
    public V put(K key, V value) {
        // 如果 table 数组为空时先创建数组，并且设置扩容阀值
        if (table == EMPTY_TABLE) {
            inflateTable(threshold);
        }
        // 如果 key 为空时，调用 putForNullKey 方法特殊处理
        if (key == null)
            return putForNullKey(value);
        // 计算 key 的哈希值
        int hash = hash(key);
        // 根据计算出来的哈希值和当前数组的长度计算在数组中的索引
        int i = indexFor(hash, table.length);
        // 先遍历该数组索引下的整条链表
        // 如果该 key 之前已经在 HashMap 中存储了的话，直接替换对应的 value 值即可
        for (Entry<K,V> e = table[i]; e != null; e = e.next) {
            Object k;
            if (e.hash == hash && ((k = e.key) == key || key.equals(k))) {
                V oldValue = e.value;
                e.value = value;
                e.recordAccess(this);
                return oldValue;
            }
        }

        modCount++;
        // 如果该 key 之前没有被存储过，那么就进入 addEntry 方法
        addEntry(hash, key, value, i);
        return null;
    }
```

看了上面 put 方法的代码，大致分为了以下几个步骤：

1. 如果 table 数组为空时先创建数组，并且设置扩容阀值；
2. 如果 key 为空时，调用 putForNullKey 方法特殊处理；
3. 计算 key 的哈希值；
4. 根据第三步计算出来的哈希值和当前数组的长度来计算得到该 key 在数组中的索引，其实索引最后的值就等于 `hash%table.length` ；
5. 遍历该数组索引下的整条链表，如果之前已经有一样的 key ，那么直接覆盖 value ；
6. 如果该 key 之前没有，那么就进入 addEntry 方法。

下面就来看一下 addEntry 方法。

``` java
    void addEntry(int hash, K key, V value, int bucketIndex) {
        // 当前容量大于或等于扩容阀值的时候，会执行扩容
        if ((size >= threshold) && (null != table[bucketIndex])) {
            // 扩容为原来容量的两倍
            resize(2 * table.length);
            // 重新计算哈希值
            hash = (null != key) ? hash(key) : 0;
            // 重新得到在新数组中的索引
            bucketIndex = indexFor(hash, table.length);
        }
        // 创建节点
        createEntry(hash, key, value, bucketIndex);
    }
```

在 addEntry 方法中，有两个注意点需要我们去看：

1. 如果当前 HashMap 的存储容量到达阀值的时候，会去进行 `resize(int newCapacity)` 扩容；
2. 在 createEntry 方法中增加新的节点。

我们先去 resize 方法中看看是怎么扩容的。

``` java
    void resize(int newCapacity) {
        Entry[] oldTable = table;
        int oldCapacity = oldTable.length;
        if (oldCapacity == MAXIMUM_CAPACITY) {
            threshold = Integer.MAX_VALUE;
            return;
        }
        // 创建新的 entry 数组
        Entry[] newTable = new Entry[newCapacity];
        // 将旧 entry 数组中的数据复制到新 entry 数组中
        transfer(newTable, initHashSeedAsNeeded(newCapacity));
        // 将新数组的引用赋给 table
        table = newTable;
        // 计算新的扩容阀值
        threshold = (int)Math.min(newCapacity * loadFactor, MAXIMUM_CAPACITY + 1);
    }
```

根据代码可以知道，扩容就是创建了一个新的数组，然后把数据全部复制过去，再把新数组的引用赋给 table 。

剩下的还有一个 createEntry 方法。

``` java
    void createEntry(int hash, K key, V value, int bucketIndex) {
        Entry<K,V> e = table[bucketIndex];
        table[bucketIndex] = new Entry<>(hash, key, value, e);
        // 当前 HashMap 的容量加 1
        size++;
    }
```

创建节点的方法中，如果发现 e 是空的，之前没有存值，那么直接把值存进去就行了；如果是之前 e 有值的，即发生 hash 碰撞的情况，就以单链表头插入的方式存储。

get 方法
-------
``` java
    public V get(Object key) {
        // 如果 key 是空的，就调用 getForNullKey 方法特殊处理
        if (key == null)
            return getForNullKey();
        // 获取 key 相对应的 entry 
        Entry<K,V> entry = getEntry(key);

        return null == entry ? null : entry.getValue();
    }
```

在 get 方法中，获取 value 主要步骤是 `getEntry(key)` 。

``` java
    final Entry<K,V> getEntry(Object key) {
        if (size == 0) {
            return null;
        }
        // 计算 key 的哈希值
        int hash = (key == null) ? 0 : hash(key);
        // 得到数组的索引，然后遍历链表，查看是否有相同 key 的 Entry
        for (Entry<K,V> e = table[indexFor(hash, table.length)];
             e != null;
             e = e.next) {
            Object k;
            if (e.hash == hash &&
                ((k = e.key) == key || (key != null && key.equals(k))))
                return e;
        }
        // 没有的话，返回 null
        return null;
    }
```

`getEntry(Object key)` 方法很简单，就是找到对应 key 的数组索引，然后遍历链表查找即可。

Java 1.8 中 HashMap 的不同
=========================
1. 在 Java 1.8 中，如果链表的长度超过了 8 ，那么链表将转化为红黑树；
2. 发生 hash 碰撞时，Java 1.7 会在链表头部插入，而 Java 1.8 会在链表尾部插入；
3. 在 Java 1.8 中，Entry 被 Node 代替（换了一个马甲）。

Footer
======
讲完了，现在对 HashMap 应该有更深一步的了解了吧，建议大家回去再研究下。

如果哪里有问题或者不懂，可以留言。

bye bye
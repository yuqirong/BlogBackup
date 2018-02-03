title: HashSet内部原理解析
date: 2018-01-28 20:07:55
categories: Java Blog
tags: [Java,数据结构,源码解析]
---
注：本文解析的 HashSet 源代码基于 Java 1.8 。

Header
======
HashSet是用来存储没有重复元素的集合类，并且它是无序的。

HashSet 内部实现是基于 HashMap ，实现了 Set 接口。

源码解析
=======
构造方法
-------
``` java
    public HashSet() {
        map = new HashMap<>();
    }

    public HashSet(Collection<? extends E> c) {
        map = new HashMap<>(Math.max((int) (c.size()/.75f) + 1, 16));
        addAll(c);
    }

    public HashSet(int initialCapacity, float loadFactor) {
        map = new HashMap<>(initialCapacity, loadFactor);
    }

    public HashSet(int initialCapacity) {
        map = new HashMap<>(initialCapacity);
    }

    HashSet(int initialCapacity, float loadFactor, boolean dummy) {
        map = new LinkedHashMap<>(initialCapacity, loadFactor);
    }
```

我们发现，除了最后一个 HashSet 的构造方法外，其他所有内部就是去创建一个 Hashap 。没有其他的操作。而最后一个构造方法不是 public 的，所以不对外公开。

add
----
``` java
    public boolean add(E e) {
        // PRESENT = new Object()
        return map.put(e, PRESENT)==null;
    }
```

add 方法很简单，就是在 map 中放入一键值对。 key 就是要存入的元素，value 是 PRESENT ，其实就是 new Object() 。所以，HashSet 是不能重复的。

remove
------
``` java
    public boolean remove(Object o) {
        return map.remove(o)==PRESENT;
    }
```

相应的，remove 就是从 map 中移除 key 。

contains
--------
``` java
    public boolean contains(Object o) {
        return map.containsKey(o);
    }
```

这些代码应该很明白，不需要讲了。

iterator
--------
``` java
    public Iterator<E> iterator() {
        return map.keySet().iterator();
    }
```

内部调用的就是 HashMap 中 keySet 的 iterator 方法。

size
----
``` java
    public int size() {
        return map.size();
    }
```

剩下的 HashSet 方法也不多，内部也都是通过 HashMap 实现的。就不贴出来了，大家回去看一下都会明白的。

Footer
======
从上看下来，HashSet 的源码是挺简单的，内部都是用 HashMap 来实现的。利用了 HashMap 的 key 不能重复这个原理来实现 HashSet 。

内容很简短，都讲完了，再见。
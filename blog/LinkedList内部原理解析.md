title: LinkedList内部原理解析
date: 2018-01-31 19:59:21
categories: Java Blog
tags: [Java,数据结构,源码解析]
---
注：本文解析的 LinkedList 源代码基于 Java 1.8 。

Header
======
List 集合中，之前分析了 ArrayList ，还剩下了 LinkedList 没有分析过。那么趁着今天有空，就把 LinkedList 的内部原理来讲讲吧。

LinkedList 是有序并且可以元素重复的集合，底层是基于双向链表的。也正因为是链表，所以也就没有动态扩容的步骤了。

源码分析
=======
构造方法
-------
``` java
    public LinkedList() {
    }

    public LinkedList(Collection<? extends E> c) {
        this();
        addAll(c);
    }
```
构造方法一个是默认的，另外一个是传入一个集合，然后调用 addAll 方法添加集合所有的元素。

Node
----
LinkedList 既然作为链表，那么肯定会有节点了，我们看下节点的定义：

``` java
    private static class Node<E> {
        E item;
        Node<E> next;
        Node<E> prev;

        Node(Node<E> prev, E element, Node<E> next) {
            this.item = element;
            this.next = next;
            this.prev = prev;
        }
    }
```

每个节点都包含了前一个节点 prev 以及后一个节点 next ，item 就是要当前节点要存储的元素。

add(E e)
--------
``` java
    public boolean add(E e) {
        // 直接往队尾加元素
        linkLast(e);
        return true;
    }

    void linkLast(E e) {
        // 保存原来链表尾部节点，last 是全局变量，用来表示队尾元素
        final Node<E> l = last;
        // 为该元素 e 新建一个节点
        final Node<E> newNode = new Node<>(l, e, null);
        // 将新节点设为队尾
        last = newNode;
        // 如果原来的队尾元素为空，那么说明原来的整个列表是空的，就把新节点赋值给头结点
        if (l == null)
            first = newNode;
        else
        // 原来尾结点的后面为新生成的结点
            l.next = newNode;
        // 节点数 +1
        size++;
        modCount++;
    }
```

在 `linkLast(E e)` 中，先去判断了原来的尾节点是否为空。如果尾节点是空的，那么就说明原来的列表是空的。会将头节点也指向该元素；如果不为空，直接在后面追加即可。

其实在 first 之前，还有一个为 null 的 head 节点。head 节点的 next 才是 first 节点。

add(int index, E element)
--------------------------
``` java
    public void add(int index, E element) {
        // 检查 index 有没有超出索引范围
        checkPositionIndex(index);
        // 如果追加到尾部，那么就跟 add(E e) 一样了
        if (index == size)
            linkLast(element);
        else
        // 否则就是插在其他位置
            linkBefore(element, node(index));
    }
```

在 `add(int index, E element)` 中主要就看 `linkBefore(element, node(index))` 方法了。注意到有一个 `node(index)` ，好奇究竟做了什么操作？

``` java
    Node<E> node(int index) {
        // assert isElementIndex(index);
        // 如果 index 在前半段，从前往后遍历获取 node
        if (index < (size >> 1)) {
            Node<E> x = first;
            for (int i = 0; i < index; i++)
                x = x.next;
            return x;
        } else {
            // 如果 index 在后半段，从后往前遍历获取 node
            Node<E> x = last;
            for (int i = size - 1; i > index; i--)
                x = x.prev;
            return x;
        }
    }
```

原来是为了索引得到 index 对应的节点，在速度上做了算法优化。

得到 Node 后，就会去调用 `linkBefore(element, node)` 。

``` java
    void linkBefore(E e, Node<E> succ) {
        // assert succ != null;
        // 保存 index 节点的前节点
        final Node<E> pred = succ.prev;
        // 新建一个目标节点
        final Node<E> newNode = new Node<>(pred, e, succ);
        succ.prev = newNode;
        // 如果是在开头处插入的话
        if (pred == null)
            first = newNode;
        else
            pred.next = newNode;
        size++;
        modCount++;
    }
```

这段代码和之前的很类似，了解链表节点插入的同学对这段代码应该很 easy 了。

addAll(Collection<? extends E> c)
---------------------------------
``` java
    public boolean addAll(Collection<? extends E> c) {
        return addAll(size, c);
    }
```

在 `addAll(Collection<? extends E> c)` 内部直接调用的是 `addAll(int index, Collection<? extends E> c)` 。

``` java
    public boolean addAll(int index, Collection<? extends E> c) {
        // index 索引范围判断
        checkPositionIndex(index);

        Object[] a = c.toArray();
        int numNew = a.length;
        if (numNew == 0)
            return false;

        // 保存之前的前节点和后节点
        Node<E> pred, succ;
        // 判断是在尾部插入还是在其他位置插入
        if (index == size) {
            succ = null;
            pred = last;
        } else {
            succ = node(index);
            pred = succ.prev;
        }

        for (Object o : a) {
            @SuppressWarnings("unchecked") E e = (E) o;
            Node<E> newNode = new Node<>(pred, e, null);
            // 如果前节点是空的，就说明是在头部插入了
            if (pred == null)
                first = newNode;
            else
                pred.next = newNode;
            pred = newNode;
        }

        if (succ == null) {
            last = pred;
        } else {
            pred.next = succ;
            succ.prev = pred;
        }

        size += numNew;
        modCount++;
        return true;
    }
```

`addAll(int index, Collection<? extends E> c)` 其实就是相当于多次进行 `add(int index, E element)` 操作，在内部循环添加到链表上。

get(int index)
----------------
```
    public E get(int index) {
        checkElementIndex(index);
        return node(index).item;
    }
```

在内部调用了 `node(index)` 方法，而 `node(index)` 方法在上面已经分析过了。就是判断在前半段还是在后半段，然后遍历得到即可。

remove(int index)
-----------------
``` java
    public E remove(int index) {
        checkElementIndex(index);
        return unlink(node(index));
    }
```

`remove(int index)` 中调用了 `unlink(Node<E> x)` 方法来移除该节点。

``` java
    E unlink(Node<E> x) {
        // assert x != null;
        final E element = x.item;
        final Node<E> next = x.next;
        final Node<E> prev = x.prev;
        // 如果要删除的是头节点，那么设置头节点为下一个节点
        if (prev == null) {
            first = next;
        } else {
            // 设置该节点的前节点的 next 为该节点的 next
            prev.next = next;
            x.prev = null;
        }
        // 如果要删除的是尾节点，那么设置尾节点为上一个节点
        if (next == null) {
            last = prev;
        } else {
            // 设置该节点的下一个节点的 prev 为该节点的 prev
            next.prev = prev;
            x.next = null;
        }
        // 设置 null 值，size--
        x.item = null;
        size--;
        modCount++;
        return element;
    }
```

remove(Object o)
----------------
``` java
    public boolean remove(Object o) {
        if (o == null) {
            for (Node<E> x = first; x != null; x = x.next) {
                if (x.item == null) {
                    unlink(x);
                    return true;
                }
            }
        } else {
            for (Node<E> x = first; x != null; x = x.next) {
                if (o.equals(x.item)) {
                    unlink(x);
                    return true;
                }
            }
        }
        return false;
    }
```

`remove(Object o)` 的代码就是遍历链表，然后得到相等的值就把它 `unlink(x)` 了。至于 `unlink(Node<E> x)` 的代码，上面已经分析过啦。

set(int index, E element)
-------------------------
``` java
    public E set(int index, E element) {
        checkElementIndex(index);
        Node<E> x = node(index);
        E oldVal = x.item;
        // 设置 x 节点的值为新值，然后返回旧值
        x.item = element;
        return oldVal;
    }
```

clear()
------
``` java
    public void clear() {
        // 遍历链表，然后一一删除置空
        for (Node<E> x = first; x != null; ) {
            Node<E> next = x.next;
            x.item = null;
            x.next = null;
            x.prev = null;
            x = next;
        }
        first = last = null;
        size = 0;
        modCount++;
    }
```

Footer
======
LinkedList 相对于 ArrayList 来说，源码会复杂一点。因为涉及到了链表，所以会有 prev 和 next 之分。但是静下心来阅读，还是可以看懂的。

基础集合类的源码都看得差不多了，目前为止一共分析了 ArrayList、LinkedList、HashMap 和 HashSet 四个类。

之后有空的话还有更多的集合类会进行源码解析，那么好好努力吧。
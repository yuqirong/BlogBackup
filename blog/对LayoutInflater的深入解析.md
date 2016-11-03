title: 对LayoutInflater的深入解析
date: 2016-02-03 19:19:12
categories: Android Blog
tags: [Android,源码解析]
---
前言
==========
今天要讲的主角就是LayoutInflater，相信大家都用过吧。在动态地加载布局时，经常可以看见它的身影。比如说在Fragment的`onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)`方法里，就需要我们返回Fragment的View。这时就可以用`inflater.inflate(R.layout.fragment_view, container, false)`来加载视图。那么下面就来探究一下LayoutInflater的真面目吧。

from(Context context)
==========
首先我们在使用LayoutInflater时，通常用`LayoutInflater.from(Context context)`这个方法来得到其对象：

``` java
/**
 * Obtains the LayoutInflater from the given context.
 */
public static LayoutInflater from(Context context) {
    LayoutInflater LayoutInflater =
            (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    if (LayoutInflater == null) {
        throw new AssertionError("LayoutInflater not found.");
    }
    return LayoutInflater;
}
```

我们可以看到原来`from(Context context)`这个方法只不过把`context.getSystemService(Context.LAYOUT_INFLATER_SERVICE)`进行简单地封装了一下，方便开发者调用。相信大家都看得懂。

inflate(...)
==========
在得到了LayoutInflater的对象之后，我们就要使用它的inflate()方法了。

![这里填写图片的描述](/uploads/20160203/20160203183605.png)

可以看到inflate()有四个重载的方法。我们先来看看前三个的源码：

``` java
public View inflate(@LayoutRes int resource, @Nullable ViewGroup root) {
    return inflate(resource, root, root != null);
}

public View inflate(XmlPullParser parser, @Nullable ViewGroup root) {
    return inflate(parser, root, root != null);
}

public View inflate(@LayoutRes int resource, @Nullable ViewGroup root, boolean attachToRoot) {
    final Resources res = getContext().getResources();
    if (DEBUG) {
        Log.d(TAG, "INFLATING from resource: \"" + res.getResourceName(resource) + "\" ("
                + Integer.toHexString(resource) + ")");
    }

    final XmlResourceParser parser = res.getLayout(resource);
    try {
        return inflate(parser, root, attachToRoot);
    } finally {
        parser.close();
    }
}
```

看到这里，我们都明白了，前三个inflate()方法到最后都是调用了`inflate(XmlPullParser parser, @Nullable ViewGroup root, boolean attachToRoot)`这个方法。原来第四个inflate()方法才是“幕后黑手”。那让我们来揭开它的黑纱吧：

``` java
public View inflate(XmlPullParser parser, @Nullable ViewGroup root, boolean attachToRoot) {
    synchronized (mConstructorArgs) {
        Trace.traceBegin(Trace.TRACE_TAG_VIEW, "inflate");

        final Context inflaterContext = mContext;
        final AttributeSet attrs = Xml.asAttributeSet(parser);
        Context lastContext = (Context) mConstructorArgs[0];
        mConstructorArgs[0] = inflaterContext;
        View result = root;

        try {
            // Look for the root node.
            int type;
            while ((type = parser.next()) != XmlPullParser.START_TAG &&
                    type != XmlPullParser.END_DOCUMENT) {
                // Empty
            }

            if (type != XmlPullParser.START_TAG) {
                throw new InflateException(parser.getPositionDescription()
                        + ": No start tag found!");
            }

            final String name = parser.getName();
            
            if (DEBUG) {
                System.out.println("**************************");
                System.out.println("Creating root view: "
                        + name);
                System.out.println("**************************");
            }

            if (TAG_MERGE.equals(name)) {
                if (root == null || !attachToRoot) {
                    throw new InflateException("<merge /> can be used only with a valid "
                            + "ViewGroup root and attachToRoot=true");
                }

                rInflate(parser, root, inflaterContext, attrs, false);
            } else {
                // Temp is the root view that was found in the xml
                final View temp = createViewFromTag(root, name, inflaterContext, attrs);

                ViewGroup.LayoutParams params = null;

                if (root != null) {
                    if (DEBUG) {
                        System.out.println("Creating params from root: " +
                                root);
                    }
                    // Create layout params that match root, if supplied
                    params = root.generateLayoutParams(attrs);
                    if (!attachToRoot) {
                        // Set the layout params for temp if we are not
                        // attaching. (If we are, we use addView, below)
                        temp.setLayoutParams(params);
                    }
                }

                if (DEBUG) {
                    System.out.println("-----> start inflating children");
                }

                // Inflate all children under temp against its context.
                rInflateChildren(parser, temp, attrs, true);

                if (DEBUG) {
                    System.out.println("-----> done inflating children");
                }

                // We are supposed to attach all the views we found (int temp)
                // to root. Do that now.
                if (root != null && attachToRoot) {
                    root.addView(temp, params);
                }

                // Decide whether to return the root that was passed in or the
                // top view found in xml.
                if (root == null || !attachToRoot) {
                    result = temp;
                }
            }

        } catch (XmlPullParserException e) {
            InflateException ex = new InflateException(e.getMessage());
            ex.initCause(e);
            throw ex;
        } catch (Exception e) {
            InflateException ex = new InflateException(
                    parser.getPositionDescription()
                            + ": " + e.getMessage());
            ex.initCause(e);
            throw ex;
        } finally {
            // Don't retain static reference on context.
            mConstructorArgs[0] = lastContext;
            mConstructorArgs[1] = null;
        }

        Trace.traceEnd(Trace.TRACE_TAG_VIEW);

        return result;
    }
}
```

这段代码有点长，不过别担心，我们慢慢来看。首先把传入的`parser`进行解析，创建视图。其中我们可以注意到在Android的源码中是用Pull方式来解析xml得到视图的。接下来判断了传入的`root`是否为null，如果`root`不为null并且`attachToRoot`为false的情况下，`temp.setLayoutParams(params);`。也就是说把创建出来的视图的LayoutParams设置为params。那么params又是从哪里来的呢？可以在上面一行可以找到`params = root.generateLayoutParams(attrs);`我们来看看源码：

``` java
public LayoutParams generateLayoutParams(AttributeSet attrs) {
    return new LayoutParams(getContext(), attrs);
}
```

也就是说，在`root`不为null并且`attachToRoot`为false的情况下，把`root`的LayoutParams设置给了新创建出来的View。

好了，再往下看，我们注意到了`root`不为null并且`attachToRoot`为true的情况。调用了`root.addView(temp, params);`，在其内部就是将temp添加进了root中。即最后得到的View的父布局就是root。

最后一个情况就是`(root == null || !attachToRoot)`时，直接返回了temp。

总结
==========
到这里，关于LayoutInflater的讲解就差不多了，最后我们就来总结一下：

* 在root!=null并且attachToRoot为false：将root的LayoutParams设置给了View。
* 在root!=null并且attachToRoot为true：把root作为View的父布局。
* 在root==null时：直接返回View，无视attachToRoot的状态。

今天就到这，如有问题可以在下面留言。

~have a nice day~
title: 使用OpenCV对图片进行二值化和去燥处理
date: 2019-01-13 16:33:00
categories: Android Blog
tags: [Android,OpenCV]
---
最近做的项目中有使用到 OpenCV ，并且利用了 OpenCV 对图片做一些简单的处理。所以今天打算记录一下一些常用的 OpenCV 操作。

以下的 OpenCV 代码都是基于 OpenCV v3.3.0 aar 版本

二值化
=====
所谓的二值化，就是将图片上的像素点的灰度值设置为0或255，也就是将整个图片呈现出明显的只有黑和白的视觉效果。

``` java
public static Bitmap binarization(Bitmap bitmap) {
   // 创建一张新的bitmap
   Bitmap result = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
   Mat origin = new Mat();
   Mat gray = new Mat();
   Mat out = new Mat();
   Utils.bitmapToMat(bitmap, origin);
   Imgproc.cvtColor(origin, gray, Imgproc.COLOR_RGB2GRAY);
   // 二值化处理
   Imgproc.adaptiveThreshold(gray, out, 255.0D, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, 25, 10.0D);
   Utils.matToBitmap(out, result);
   origin.release();
   gray.release();
   out.release();
   return result;
}
```

去燥
====
如果发现二值化后燥点比较多，这时候就需要使用去燥处理了。其中参数 d 为去燥的强度。

``` java
public static Bitmap denoising(Bitmap bitmap, int d) {
    Bitmap result = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.RGB_565);
    Mat origin = new Mat();
    Mat gray = new Mat();
    Mat bf = new Mat();
    Mat out = new Mat();
    Utils.bitmapToMat(bitmap, origin);
    Imgproc.cvtColor(origin, gray, Imgproc.COLOR_RGB2GRAY);
    // 去燥
    Imgproc.bilateralFilter(gray, bf, d, (double) (d * 2), (double) (d / 2));
    Imgproc.adaptiveThreshold(bf, out, 255.0D, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, 25, 10.0D);
    Utils.matToBitmap(out, result);
    origin.release();
    gray.release();
    bf.release();
    out.release();
    return result;
}
```

最后来看一下最终的效果吧

原图：

![source](/uploads/20190113/20190118220513.png)

二值化：

![binarization](/uploads/20190113/20190118220610.png)

去燥：

![denoising](/uploads/20190113/20190118220630.png)




package com.yuqirong.view;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ScrollView;

public class MyScrollView extends ScrollView {

    private OnScrollListener listener;

    public void setOnScrollListener(OnScrollListener listener) {
        this.listener = listener;
    }

    public MyScrollView(Context context) {
        super(context);
    }

    public MyScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MyScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public interface OnScrollListener{
        void onScroll(int scrollY);
    }

    @Override  
	  protected void onScrollChanged(int l, int t, int oldl, int oldt) {  
	      super.onScrollChanged(l, t, oldl, oldt);  
	      if(listener != null){  
	          listener.onScroll(t);  
	      }  
	  }  
}

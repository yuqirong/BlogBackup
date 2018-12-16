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
    public void computeScroll() {
        super.computeScroll();
        if(listener!=null){
            listener.onScroll(getScrollY());
        }
    }
}

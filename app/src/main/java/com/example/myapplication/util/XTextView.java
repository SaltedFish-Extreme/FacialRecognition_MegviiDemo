package com.example.myapplication.util;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.appcompat.widget.AppCompatTextView;

public class XTextView extends AppCompatTextView {
    final int DRAWABLE_START = 0;
    final int DRAWABLE_TOP = 1;
    final int DRAWABLE_END = 2;
    final int DRAWABLE_BOTTOM = 3;
    private DrawableStartListener mStartListener;
    private DrawableEndListener mEndListener;

    public XTextView(Context context) {
        super(context);
    }

    public XTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public XTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setDrawableStartListener(DrawableStartListener listener) {
        this.mStartListener = listener;
    }

    public void setDrawableEndListener(DrawableEndListener listener) {
        this.mEndListener = listener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (mEndListener != null) {
                Drawable drawableEnd = getCompoundDrawables()[DRAWABLE_END];
                if (drawableEnd != null && event.getRawX() >= (getRight() - drawableEnd.getBounds().width())) {
                    mEndListener.onDrawableEndClick(this);
                }
            }
            if (mStartListener != null) {
                Drawable drawableStart = getCompoundDrawables()[DRAWABLE_START];
                if (drawableStart != null && event.getRawX() <= (getLeft() + drawableStart.getBounds().width()))
                    mStartListener.onDrawableStartClick(this);
            }
        }
        if (event.getAction() == MotionEvent.ACTION_UP) {
            performClick();
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    public interface DrawableStartListener {
        public void onDrawableStartClick(View view);
    }

    public interface DrawableEndListener {
        public void onDrawableEndClick(View view);
    }
}
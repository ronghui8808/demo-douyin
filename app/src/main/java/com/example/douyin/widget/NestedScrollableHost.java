package com.example.douyin.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager2.widget.ViewPager2;

/**
 * 解决纵向 ViewPager2 内嵌横向 ViewPager2 时的滑动冲突。
 */
public final class NestedScrollableHost extends FrameLayout {

    private int touchSlop;
    private float initialX;
    private float initialY;

    public NestedScrollableHost(@NonNull Context context) {
        super(context);
        init(context);
    }

    public NestedScrollableHost(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    @Nullable
    private ViewPager2 getParentViewPager() {
        ViewParent parent = getParent();
        while (parent instanceof View) {
            if (parent instanceof ViewPager2) {
                return (ViewPager2) parent;
            }
            parent = parent.getParent();
        }
        return null;
    }

    @Nullable
    private ViewPager2 getChildViewPager() {
        if (getChildCount() > 0) {
            View child = getChildAt(0);
            if (child instanceof ViewPager2) {
                return (ViewPager2) child;
            }
        }
        return null;
    }

    private boolean childCanScrollHorizontally(@NonNull ViewPager2 childPager, float deltaX) {
        int direction = deltaX > 0 ? -1 : 1;
        return childPager.canScrollHorizontally(direction);
    }

    private void handleTouchConflict(@NonNull MotionEvent event) {
        ViewPager2 parentPager = getParentViewPager();
        ViewPager2 childPager = getChildViewPager();
        if (parentPager == null || childPager == null) {
            return;
        }
        if (parentPager.getOrientation() != ViewPager2.ORIENTATION_VERTICAL
                || childPager.getOrientation() != ViewPager2.ORIENTATION_HORIZONTAL) {
            return;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                initialX = event.getX();
                initialY = event.getY();
                getParent().requestDisallowInterceptTouchEvent(true);
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - initialX;
                float dy = event.getY() - initialY;
                if (Math.abs(dx) < touchSlop && Math.abs(dy) < touchSlop) {
                    break;
                }
                float absDx = Math.abs(dx);
                float absDy = Math.abs(dy);
                // 降低垂直滑动权重，优先识别进入作者页的水平滑动
                if (absDx > absDy * 0.75f) {
                    getParent().requestDisallowInterceptTouchEvent(
                            childCanScrollHorizontally(childPager, dx)
                    );
                } else {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                break;
            default:
                break;
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        handleTouchConflict(event);
        return super.onInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        handleTouchConflict(event);
        return super.onTouchEvent(event);
    }
}

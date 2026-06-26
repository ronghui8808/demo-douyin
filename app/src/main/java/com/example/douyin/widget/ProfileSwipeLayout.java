package com.example.douyin.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 在视频页拦截水平滑动手势，用于左滑进入作者主页。
 * 垂直滑动仍交给外层 Feed ViewPager2 处理。
 */
public class ProfileSwipeLayout extends FrameLayout {

    public interface Listener {
        void onSwipeToProfile();

        void onSwipeToVideo();
    }

    public enum Mode {
        /** 视频页：仅识别左滑 */
        VIDEO,
        /** 个人页：仅识别右滑返回 */
        PROFILE
    }

    private Listener listener;
    private Mode mode = Mode.VIDEO;

    private float initialX;
    private float initialY;
    private int touchSlop;
    private int swipeThreshold;
    private boolean draggingHorizontally;

    public ProfileSwipeLayout(@NonNull Context context) {
        super(context);
        init(context);
    }

    public ProfileSwipeLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        ViewConfiguration configuration = ViewConfiguration.get(context);
        touchSlop = configuration.getScaledTouchSlop();
        swipeThreshold = touchSlop * 3;
    }

    public void setSwipeListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    public void setMode(@NonNull Mode mode) {
        this.mode = mode;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (listener == null) {
            return super.onInterceptTouchEvent(event);
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                initialX = event.getX();
                initialY = event.getY();
                draggingHorizontally = false;
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - initialX;
                float dy = event.getY() - initialY;
                if (!draggingHorizontally
                        && Math.abs(dx) > touchSlop
                        && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                    draggingHorizontally = true;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                draggingHorizontally = false;
                break;
            default:
                break;
        }
        return draggingHorizontally || super.onInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (listener == null || !draggingHorizontally) {
            return super.onTouchEvent(event);
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                float dx = event.getX() - initialX;
                float dy = event.getY() - initialY;
                draggingHorizontally = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                if (Math.abs(dx) >= swipeThreshold && Math.abs(dx) > Math.abs(dy)) {
                    if (dx < 0 && mode == Mode.VIDEO) {
                        listener.onSwipeToProfile();
                        return true;
                    }
                    if (dx > 0 && mode == Mode.PROFILE) {
                        listener.onSwipeToVideo();
                        return true;
                    }
                }
                return true;
            default:
                return true;
        }
    }
}

package com.example.douyin.util;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.example.douyin.R;

/**
 * App-wide toast with dark rounded custom UI.
 * Uses an in-window overlay on Activity (reliable on targetSdk 30+);
 * falls back to system Toast text when no Activity is available.
 */
public final class AppToast {

    private static final int BOTTOM_OFFSET_DP = 80;
    private static final long DURATION_SHORT_MS = 2000L;
    private static final long DURATION_LONG_MS = 3500L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static View currentOverlay;
    private static Runnable hideRunnable;

    private AppToast() {
    }

    public static void show(@NonNull Context context, @NonNull CharSequence text) {
        showInternal(context, text, DURATION_SHORT_MS);
    }

    public static void show(@NonNull Context context, @StringRes int resId) {
        Context app = context.getApplicationContext();
        show(context, app.getString(resId));
    }

    public static void showLong(@NonNull Context context, @NonNull CharSequence text) {
        showInternal(context, text, DURATION_LONG_MS);
    }

    public static void showLong(@NonNull Context context, @StringRes int resId) {
        Context app = context.getApplicationContext();
        showLong(context, app.getString(resId));
    }

    private static void showInternal(@NonNull Context context, @NonNull CharSequence text, long durationMs) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            showOnMain(context, text, durationMs);
        } else {
            MAIN.post(() -> showOnMain(context, text, durationMs));
        }
    }

    private static void showOnMain(@NonNull Context context, @NonNull CharSequence text, long durationMs) {
        Activity activity = findActivity(context);
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            Toast.makeText(context.getApplicationContext(), text, Toast.LENGTH_SHORT).show();
            return;
        }

        dismissCurrentOverlay();

        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        View overlay = LayoutInflater.from(activity).inflate(R.layout.layout_app_toast, decor, false);
        TextView tv = overlay.findViewById(R.id.tv_app_toast);
        tv.setText(text);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin = dpToPx(activity, BOTTOM_OFFSET_DP);

        overlay.setAlpha(0f);
        decor.addView(overlay, lp);
        overlay.animate().alpha(1f).setDuration(120).start();

        currentOverlay = overlay;
        hideRunnable = () -> {
            if (currentOverlay == overlay) {
                overlay.animate()
                        .alpha(0f)
                        .setDuration(120)
                        .withEndAction(() -> {
                            if (overlay.getParent() instanceof ViewGroup) {
                                ((ViewGroup) overlay.getParent()).removeView(overlay);
                            }
                            if (currentOverlay == overlay) {
                                currentOverlay = null;
                            }
                        })
                        .start();
            }
        };
        MAIN.postDelayed(hideRunnable, durationMs);
    }

    private static void dismissCurrentOverlay() {
        if (hideRunnable != null) {
            MAIN.removeCallbacks(hideRunnable);
            hideRunnable = null;
        }
        if (currentOverlay != null) {
            View overlay = currentOverlay;
            currentOverlay = null;
            if (overlay.getParent() instanceof ViewGroup) {
                ((ViewGroup) overlay.getParent()).removeView(overlay);
            }
        }
    }

    private static Activity findActivity(Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity) {
                return (Activity) current;
            }
            current = ((ContextWrapper) current).getBaseContext();
        }
        return null;
    }

    private static int dpToPx(@NonNull Context context, int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}

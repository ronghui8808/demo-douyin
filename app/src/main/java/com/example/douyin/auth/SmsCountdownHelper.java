package com.example.douyin.auth;

import android.os.CountDownTimer;
import android.widget.TextView;

import com.example.douyin.R;

public final class SmsCountdownHelper {

    private final TextView button;
    private final int seconds;
    private final CharSequence originalText;
    private CountDownTimer timer;

    public SmsCountdownHelper(TextView button, int seconds) {
        this.button = button;
        this.seconds = seconds;
        this.originalText = button.getText();
    }

    public void start() {
        cancel();
        button.setEnabled(false);
        setRemaining(seconds);
        timer = new CountDownTimer(seconds * 1000L, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                int remaining = (int) Math.ceil(millisUntilFinished / 1000.0);
                setRemaining(remaining);
            }

            @Override
            public void onFinish() {
                restore();
            }
        };
        timer.start();
    }

    public void cancel() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        restore();
    }

    private void setRemaining(int remaining) {
        button.setText(button.getContext().getString(R.string.sms_countdown_format, remaining));
    }

    private void restore() {
        button.setEnabled(true);
        button.setText(originalText);
        timer = null;
    }
}

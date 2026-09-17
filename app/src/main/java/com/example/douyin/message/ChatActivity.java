package com.example.douyin.message;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.example.douyin.R;

/**
 * Minimal stub for Task 4 row navigation. Task 5 replaces layout/logic.
 */
public class ChatActivity extends AppCompatActivity {

    public static final String EXTRA_PEER_USER_ID = "extra_peer_user_id";
    public static final String EXTRA_PEER_NICKNAME = "extra_peer_nickname";

    public static void start(Context context, long peerUserId) {
        start(context, peerUserId, null);
    }

    public static void start(Context context, long peerUserId, @Nullable String nickname) {
        Intent intent = new Intent(context, ChatActivity.class);
        intent.putExtra(EXTRA_PEER_USER_ID, peerUserId);
        if (nickname != null) {
            intent.putExtra(EXTRA_PEER_NICKNAME, nickname);
        }
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        long peerUserId = getIntent().getLongExtra(EXTRA_PEER_USER_ID, 0L);
        String nickname = getIntent().getStringExtra(EXTRA_PEER_NICKNAME);

        TextView textView = new TextView(this);
        textView.setTextColor(getColor(R.color.white));
        textView.setTextSize(16f);
        textView.setPadding(48, 120, 48, 48);
        if (nickname != null && !nickname.isEmpty()) {
            textView.setText(nickname + " (id=" + peerUserId + ")");
        } else {
            textView.setText("peerUserId=" + peerUserId);
        }
        setContentView(textView);
        getWindow().setBackgroundDrawableResource(R.color.black);
    }
}

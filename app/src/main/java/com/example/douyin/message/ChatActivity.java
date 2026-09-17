package com.example.douyin.message;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.douyin.R;
import com.example.douyin.network.ApiCallback;
import com.example.douyin.network.model.MessageDto;
import com.example.douyin.repository.AuthRepository;
import com.example.douyin.repository.MessageRepository;
import com.example.douyin.util.AppToast;

import java.util.ArrayList;
import java.util.List;

public class ChatActivity extends AppCompatActivity {

    public static final String EXTRA_PEER_USER_ID = "extra_peer_user_id";
    public static final String EXTRA_PEER_NICKNAME = "extra_peer_nickname";

    private static final long SEND_DEBOUNCE_MS = 600L;

    private MessageRepository messageRepository;
    private ChatMessageAdapter adapter;
    private LinearLayoutManager layoutManager;

    private RecyclerView rvMessages;
    private EditText etMessage;
    private TextView btnSend;
    private ProgressBar progress;

    private long peerUserId;
    private long myUserId;
    private boolean sending;
    private long lastSendAt;

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
        setContentView(R.layout.activity_chat);
        setupWindowInsets();

        peerUserId = getIntent().getLongExtra(EXTRA_PEER_USER_ID, 0L);
        String nickname = getIntent().getStringExtra(EXTRA_PEER_NICKNAME);
        if (TextUtils.isEmpty(nickname)) {
            nickname = "用户";
        }

        AuthRepository authRepository = new AuthRepository(this);
        myUserId = authRepository.getUserId();
        messageRepository = new MessageRepository(this);

        ImageButton btnBack = findViewById(R.id.btn_back);
        TextView tvNickname = findViewById(R.id.tv_peer_nickname);
        TextView tvAvatarLetter = findViewById(R.id.tv_peer_avatar_letter);
        rvMessages = findViewById(R.id.rv_messages);
        etMessage = findViewById(R.id.et_message);
        btnSend = findViewById(R.id.btn_send);
        progress = findViewById(R.id.progress_chat);

        tvNickname.setText(nickname);
        tvAvatarLetter.setText(nickname.substring(0, 1));

        adapter = new ChatMessageAdapter(myUserId);
        layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvMessages.setLayoutManager(layoutManager);
        rvMessages.setAdapter(adapter);

        btnBack.setOnClickListener(v -> finish());
        btnSend.setOnClickListener(v -> sendMessage());
        etMessage.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            return false;
        });

        loadMessages();
    }

    private void setupWindowInsets() {
        View root = findViewById(R.id.chat_root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            int bottom = Math.max(systemBars.bottom, ime.bottom);
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, bottom);
            return insets;
        });
    }

    private void loadMessages() {
        if (peerUserId <= 0L) {
            AppToast.show(this, R.string.messages_empty);
            return;
        }
        setLoading(true);
        messageRepository.listMessages(peerUserId, new ApiCallback<List<MessageDto>>() {
            @Override
            public void onSuccess(List<MessageDto> data) {
                setLoading(false);
                List<MessageDto> list = data != null ? data : new ArrayList<>();
                adapter.submit(list);
                scrollToBottom();
            }

            @Override
            public void onError(int code, String message) {
                setLoading(false);
                AppToast.show(ChatActivity.this, message);
            }
        });
    }

    private void sendMessage() {
        if (sending) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now - lastSendAt < SEND_DEBOUNCE_MS) {
            return;
        }
        String content = etMessage.getText() != null ? etMessage.getText().toString().trim() : "";
        if (TextUtils.isEmpty(content)) {
            return;
        }
        if (peerUserId <= 0L) {
            return;
        }

        sending = true;
        lastSendAt = now;
        btnSend.setEnabled(false);
        messageRepository.send(peerUserId, content, new ApiCallback<MessageDto>() {
            @Override
            public void onSuccess(MessageDto data) {
                sending = false;
                btnSend.setEnabled(true);
                if (data != null) {
                    adapter.append(data);
                }
                etMessage.setText("");
                scrollToBottom();
            }

            @Override
            public void onError(int code, String message) {
                sending = false;
                btnSend.setEnabled(true);
                if (code == 403) {
                    AppToast.show(ChatActivity.this, R.string.messages_dm_need_follow);
                } else {
                    AppToast.show(ChatActivity.this, message);
                }
            }
        });
    }

    private void scrollToBottom() {
        int count = adapter.getItemCount();
        if (count > 0) {
            rvMessages.post(() -> rvMessages.scrollToPosition(count - 1));
        }
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }
}

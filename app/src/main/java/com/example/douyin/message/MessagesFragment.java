package com.example.douyin.message;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.douyin.R;
import com.example.douyin.friends.FollowingListActivity;
import com.example.douyin.network.ApiCallback;
import com.example.douyin.network.model.ConversationDto;
import com.example.douyin.repository.MessageRepository;
import com.example.douyin.util.AppToast;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class MessagesFragment extends Fragment implements ConversationAdapter.Listener {

    private MessageRepository messageRepository;
    private ConversationAdapter adapter;
    private RecyclerView rvConversations;
    private View layoutEmpty;
    private TextView tvEmpty;
    private MaterialButton btnGoFollowing;
    private ProgressBar progress;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_messages, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        messageRepository = new MessageRepository(requireContext());
        rvConversations = view.findViewById(R.id.rv_conversations);
        layoutEmpty = view.findViewById(R.id.layout_messages_empty);
        tvEmpty = view.findViewById(R.id.tv_messages_empty);
        btnGoFollowing = view.findViewById(R.id.btn_go_following);
        progress = view.findViewById(R.id.progress_messages);

        adapter = new ConversationAdapter(this);
        rvConversations.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvConversations.setAdapter(adapter);

        btnGoFollowing.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), FollowingListActivity.class)));
    }

    @Override
    public void onResume() {
        super.onResume();
        loadConversations();
    }

    private void loadConversations() {
        setLoading(true);
        messageRepository.listConversations(new ApiCallback<List<ConversationDto>>() {
            @Override
            public void onSuccess(List<ConversationDto> data) {
                setLoading(false);
                List<ConversationDto> list = data != null ? data : new ArrayList<>();
                if (list.isEmpty()) {
                    showEmpty();
                } else {
                    layoutEmpty.setVisibility(View.GONE);
                    rvConversations.setVisibility(View.VISIBLE);
                    adapter.submit(list);
                }
            }

            @Override
            public void onError(int code, String message) {
                setLoading(false);
                AppToast.show(requireContext(), message);
                showEmpty();
            }
        });
    }

    private void showEmpty() {
        rvConversations.setVisibility(View.GONE);
        layoutEmpty.setVisibility(View.VISIBLE);
        tvEmpty.setText(R.string.messages_empty);
        btnGoFollowing.setVisibility(View.VISIBLE);
        adapter.submit(new ArrayList<>());
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onConversationClick(@NonNull ConversationDto conversation) {
        if (conversation.peer == null) {
            return;
        }
        String nickname = conversation.peer.nickname;
        ChatActivity.start(requireContext(), conversation.peer.id, nickname);
    }
}

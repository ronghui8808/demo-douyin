package com.example.douyin.message;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.douyin.R;
import com.example.douyin.network.model.ConversationDto;
import com.example.douyin.network.model.UserDto;
import com.example.douyin.util.TimeFormatter;

import java.util.ArrayList;
import java.util.List;

public class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.Holder> {

    public interface Listener {
        void onConversationClick(@NonNull ConversationDto conversation);
    }

    private final List<ConversationDto> items = new ArrayList<>();
    private final Listener listener;

    public ConversationAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    public void submit(@NonNull List<ConversationDto> conversations) {
        items.clear();
        items.addAll(conversations);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_conversation, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        ConversationDto item = items.get(position);
        UserDto peer = item.peer;
        String nickname = peer != null && !TextUtils.isEmpty(peer.nickname)
                ? peer.nickname
                : (peer != null && !TextUtils.isEmpty(peer.username) ? peer.username : "用户");
        holder.tvNickname.setText(nickname);
        holder.tvAvatarLetter.setText(nickname.substring(0, 1));
        holder.tvPreview.setText(item.lastPreview != null ? item.lastPreview : "");
        holder.tvTime.setText(item.lastAt > 0 ? TimeFormatter.formatRelative(item.lastAt) : "");

        if (item.unreadCount > 0) {
            holder.tvUnread.setVisibility(View.VISIBLE);
            holder.tvUnread.setText(item.unreadCount > 99
                    ? "99+"
                    : String.valueOf(item.unreadCount));
        } else {
            holder.tvUnread.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> listener.onConversationClick(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView tvAvatarLetter;
        final TextView tvNickname;
        final TextView tvPreview;
        final TextView tvTime;
        final TextView tvUnread;

        Holder(@NonNull View itemView) {
            super(itemView);
            tvAvatarLetter = itemView.findViewById(R.id.tv_avatar_letter);
            tvNickname = itemView.findViewById(R.id.tv_nickname);
            tvPreview = itemView.findViewById(R.id.tv_preview);
            tvTime = itemView.findViewById(R.id.tv_time);
            tvUnread = itemView.findViewById(R.id.tv_unread);
        }
    }
}

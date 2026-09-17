package com.example.douyin.message;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.douyin.R;
import com.example.douyin.network.model.MessageDto;
import com.example.douyin.util.TimeFormatter;

import java.util.ArrayList;
import java.util.List;

public class ChatMessageAdapter extends RecyclerView.Adapter<ChatMessageAdapter.Holder> {

    private static final int TYPE_IN = 0;
    private static final int TYPE_OUT = 1;

    private final List<MessageDto> items = new ArrayList<>();
    private final long myUserId;

    public ChatMessageAdapter(long myUserId) {
        this.myUserId = myUserId;
    }

    public void submit(@NonNull List<MessageDto> messages) {
        items.clear();
        items.addAll(messages);
        notifyDataSetChanged();
    }

    public void append(@NonNull MessageDto message) {
        items.add(message);
        notifyItemInserted(items.size() - 1);
    }

    @Override
    public int getItemViewType(int position) {
        MessageDto message = items.get(position);
        return message.senderId == myUserId ? TYPE_OUT : TYPE_IN;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = viewType == TYPE_OUT
                ? R.layout.item_chat_message_out
                : R.layout.item_chat_message_in;
        View view = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        MessageDto message = items.get(position);
        holder.tvContent.setText(message.content != null ? message.content : "");
        if (message.createdAt > 0) {
            holder.tvTime.setVisibility(View.VISIBLE);
            holder.tvTime.setText(TimeFormatter.formatRelative(message.createdAt));
        } else {
            holder.tvTime.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView tvContent;
        final TextView tvTime;

        Holder(@NonNull View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tv_content);
            tvTime = itemView.findViewById(R.id.tv_time);
        }
    }
}

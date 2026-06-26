package com.example.douyin.comment;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.douyin.R;
import com.example.douyin.network.model.CommentDto;
import com.example.douyin.util.TimeFormatter;

import java.util.ArrayList;
import java.util.List;

public class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.CommentViewHolder> {

    private final List<CommentDto> comments = new ArrayList<>();

    public void submitList(List<CommentDto> list) {
        comments.clear();
        if (list != null) {
            comments.addAll(list);
        }
        notifyDataSetChanged();
    }

    public void appendList(List<CommentDto> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        int start = comments.size();
        comments.addAll(list);
        notifyItemRangeInserted(start, list.size());
    }

    public void prependComment(CommentDto comment) {
        comments.add(0, comment);
        notifyItemInserted(0);
    }

    @NonNull
    @Override
    public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_comment, parent, false);
        return new CommentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CommentViewHolder holder, int position) {
        holder.bind(comments.get(position));
    }

    @Override
    public int getItemCount() {
        return comments.size();
    }

    static class CommentViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvAvatarLetter;
        private final TextView tvNickname;
        private final TextView tvTime;
        private final TextView tvContent;

        CommentViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAvatarLetter = itemView.findViewById(R.id.tv_avatar_letter);
            tvNickname = itemView.findViewById(R.id.tv_nickname);
            tvTime = itemView.findViewById(R.id.tv_time);
            tvContent = itemView.findViewById(R.id.tv_content);
        }

        void bind(CommentDto comment) {
            String nickname = itemView.getContext().getString(R.string.demo_author);
            if (comment.user != null && !TextUtils.isEmpty(comment.user.nickname)) {
                nickname = comment.user.nickname;
            }
            tvNickname.setText(nickname);
            tvAvatarLetter.setText(nickname.substring(0, 1));
            tvContent.setText(comment.content);
            tvTime.setText(TimeFormatter.formatRelative(comment.createdAt));
        }
    }
}

package com.example.douyin.profile;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.douyin.R;
import com.example.douyin.network.model.VideoDto;
import com.example.douyin.util.CountFormatter;
import com.example.douyin.util.CoverImageLoader;

import java.util.ArrayList;
import java.util.List;

public class ProfileVideoAdapter extends RecyclerView.Adapter<ProfileVideoAdapter.ViewHolder> {

    private final List<VideoDto> videos = new ArrayList<>();

    public void submitList(List<VideoDto> list) {
        videos.clear();
        if (list != null) {
            videos.addAll(list);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_profile_video, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        VideoDto video = videos.get(position);
        holder.tvLikeCount.setText(CountFormatter.format(video.likeCount));
        if (!TextUtils.isEmpty(video.coverUrl)) {
            CoverImageLoader.load(holder.ivCover, video.coverUrl);
        } else {
            holder.ivCover.setImageDrawable(null);
        }
    }

    @Override
    public int getItemCount() {
        return videos.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        final ImageView ivCover;
        final TextView tvLikeCount;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivCover = itemView.findViewById(R.id.iv_cover);
            tvLikeCount = itemView.findViewById(R.id.tv_like_count);
        }
    }
}

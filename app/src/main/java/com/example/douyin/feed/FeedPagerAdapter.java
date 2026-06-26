package com.example.douyin.feed;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.example.douyin.network.model.VideoDto;

import java.util.ArrayList;
import java.util.List;

public class FeedPagerAdapter extends FragmentStateAdapter {

    private final List<VideoDto> videos = new ArrayList<>();

    public FeedPagerAdapter(@NonNull Fragment fragment) {
        super(fragment);
    }

    public void submitList(List<VideoDto> list) {
        videos.clear();
        if (list != null) {
            videos.addAll(list);
        }
        notifyDataSetChanged();
    }

    public VideoDto getVideo(int position) {
        if (position < 0 || position >= videos.size()) {
            return null;
        }
        return videos.get(position);
    }

    public int indexOf(long videoId) {
        for (int i = 0; i < videos.size(); i++) {
            if (videos.get(i).id == videoId) {
                return i;
            }
        }
        return -1;
    }

    public void updateLike(long videoId, boolean isLiked, int likeCount) {
        int index = indexOf(videoId);
        if (index >= 0) {
            videos.get(index).isLiked = isLiked;
            videos.get(index).likeCount = likeCount;
        }
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return VideoPageFragment.newInstance(position);
    }

    @Override
    public int getItemCount() {
        return videos.size();
    }
}

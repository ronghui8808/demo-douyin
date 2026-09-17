package com.example.douyin.friends;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.douyin.R;
import com.example.douyin.network.ApiCallback;
import com.example.douyin.network.model.UserDto;
import com.example.douyin.repository.FriendRepository;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class FollowingListActivity extends AppCompatActivity {

    private FriendRepository friendRepository;
    private FollowingAdapter adapter;
    private RecyclerView rvFollowing;
    private TextView tvEmpty;
    private ProgressBar progress;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_following_list);

        friendRepository = new FriendRepository(this);
        rvFollowing = findViewById(R.id.rv_following);
        tvEmpty = findViewById(R.id.tv_following_empty);
        progress = findViewById(R.id.progress_following);

        adapter = new FollowingAdapter();
        rvFollowing.setLayoutManager(new LinearLayoutManager(this));
        rvFollowing.setAdapter(adapter);
        load();
    }

    private void load() {
        progress.setVisibility(View.VISIBLE);
        friendRepository.getMyFollowing(new ApiCallback<List<UserDto>>() {
            @Override
            public void onSuccess(List<UserDto> data) {
                progress.setVisibility(View.GONE);
                List<UserDto> list = data != null ? data : new ArrayList<>();
                if (list.isEmpty()) {
                    rvFollowing.setVisibility(View.GONE);
                    tvEmpty.setVisibility(View.VISIBLE);
                } else {
                    tvEmpty.setVisibility(View.GONE);
                    rvFollowing.setVisibility(View.VISIBLE);
                    adapter.submit(list);
                }
            }

            @Override
            public void onError(int code, String message) {
                progress.setVisibility(View.GONE);
                Toast.makeText(FollowingListActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private final class FollowingAdapter extends RecyclerView.Adapter<FollowingAdapter.Holder> {

        private final List<UserDto> items = new ArrayList<>();

        void submit(List<UserDto> list) {
            items.clear();
            items.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_following, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            UserDto user = items.get(position);
            holder.tvNickname.setText(user.nickname != null ? user.nickname : user.username);
            holder.btnUnfollow.setOnClickListener(v ->
                    friendRepository.unfollow(user.id, new ApiCallback<Boolean>() {
                        @Override
                        public void onSuccess(Boolean data) {
                            int pos = holder.getBindingAdapterPosition();
                            if (pos >= 0 && pos < items.size()) {
                                items.remove(pos);
                                notifyItemRemoved(pos);
                                if (items.isEmpty()) {
                                    rvFollowing.setVisibility(View.GONE);
                                    tvEmpty.setVisibility(View.VISIBLE);
                                }
                            }
                        }

                        @Override
                        public void onError(int code, String message) {
                            Toast.makeText(FollowingListActivity.this, message, Toast.LENGTH_SHORT)
                                    .show();
                        }
                    }));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final TextView tvNickname;
            final MaterialButton btnUnfollow;

            Holder(@NonNull View itemView) {
                super(itemView);
                tvNickname = itemView.findViewById(R.id.tv_nickname);
                btnUnfollow = itemView.findViewById(R.id.btn_unfollow);
            }
        }
    }
}

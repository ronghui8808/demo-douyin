package com.example.douyin.friends;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.douyin.R;
import com.example.douyin.network.model.PhoneMatchItem;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ContactMatchAdapter extends RecyclerView.Adapter<ContactMatchAdapter.Holder> {

    public interface Listener {
        void onFollowClick(@NonNull PhoneMatchItem item, int position);

        void onItemClick(@NonNull PhoneMatchItem item);
    }

    private final List<PhoneMatchItem> items = new ArrayList<>();
    private final Map<String, String> contactNames = new HashMap<>();
    private final Listener listener;

    public ContactMatchAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    public void setContactNames(@NonNull Map<String, String> names) {
        contactNames.clear();
        contactNames.putAll(names);
    }

    public void submit(@NonNull List<PhoneMatchItem> matches) {
        items.clear();
        items.addAll(matches);
        notifyDataSetChanged();
    }

    public void updateFollowing(int position, boolean following) {
        if (position < 0 || position >= items.size()) {
            return;
        }
        items.get(position).following = following;
        notifyItemChanged(position);
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_contact_match, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        PhoneMatchItem item = items.get(position);
        String contactName = contactNames.get(item.matchedPhone);
        if (TextUtils.isEmpty(contactName)) {
            contactName = !TextUtils.isEmpty(item.user.nickname) ? item.user.nickname : "朋友";
        }
        holder.tvContactName.setText(contactName);
        String nickname = item.user != null ? item.user.nickname : "";
        holder.tvNickname.setText(nickname);
        holder.btnFollow.setText(item.following
                ? R.string.friends_action_following
                : R.string.friends_action_follow);
        holder.btnFollow.setOnClickListener(v ->
                listener.onFollowClick(item, holder.getBindingAdapterPosition()));
        holder.itemView.setOnClickListener(v -> listener.onItemClick(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView tvContactName;
        final TextView tvNickname;
        final MaterialButton btnFollow;

        Holder(@NonNull View itemView) {
            super(itemView);
            tvContactName = itemView.findViewById(R.id.tv_contact_name);
            tvNickname = itemView.findViewById(R.id.tv_nickname);
            btnFollow = itemView.findViewById(R.id.btn_follow);
        }
    }
}

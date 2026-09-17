package com.example.douyin.friends;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.douyin.R;
import com.example.douyin.network.ApiCallback;
import com.example.douyin.network.model.MatchPhonesResult;
import com.example.douyin.network.model.PhoneMatchItem;
import com.example.douyin.profile.UserProfileActivity;
import com.example.douyin.repository.FriendRepository;
import com.example.douyin.util.AppToast;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FriendsFragment extends Fragment implements ContactMatchAdapter.Listener {

    private FriendRepository friendRepository;
    private ContactMatchAdapter adapter;
    private RecyclerView rvMatches;
    private View layoutEmpty;
    private TextView tvEmpty;
    private MaterialButton btnAllowContacts;
    private ProgressBar progress;

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    loadAndMatch();
                } else {
                    showEmpty(getString(R.string.friends_permission_denied), true);
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_friends, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        friendRepository = new FriendRepository(requireContext());
        rvMatches = view.findViewById(R.id.rv_contact_matches);
        layoutEmpty = view.findViewById(R.id.layout_friends_empty);
        tvEmpty = view.findViewById(R.id.tv_friends_empty);
        btnAllowContacts = view.findViewById(R.id.btn_allow_contacts);
        progress = view.findViewById(R.id.progress_friends);

        adapter = new ContactMatchAdapter(this);
        rvMatches.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvMatches.setAdapter(adapter);

        btnAllowContacts.setOnClickListener(v -> requestContactsPermission());
        maybeLoad();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (hasContactsPermission()) {
            loadAndMatch();
        }
    }

    private void maybeLoad() {
        if (hasContactsPermission()) {
            loadAndMatch();
        } else {
            showEmpty(getString(R.string.friends_permission_rationale), true);
        }
    }

    private boolean hasContactsPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestContactsPermission() {
        permissionLauncher.launch(Manifest.permission.READ_CONTACTS);
    }

    private void loadAndMatch() {
        List<ContactEntry> contacts =
                DeviceContactsReader.loadNormalizedContacts(requireContext().getContentResolver());
        if (contacts.isEmpty()) {
            showEmpty(getString(R.string.friends_no_phones), false);
            return;
        }

        Map<String, String> names = new HashMap<>();
        List<String> phones = new ArrayList<>();
        for (ContactEntry entry : contacts) {
            phones.add(entry.phone);
            names.put(entry.phone, entry.displayName);
        }
        adapter.setContactNames(names);

        setLoading(true);
        friendRepository.matchPhones(phones, new ApiCallback<MatchPhonesResult>() {
            @Override
            public void onSuccess(MatchPhonesResult data) {
                setLoading(false);
                List<PhoneMatchItem> matches = data != null && data.matches != null
                        ? data.matches : new ArrayList<>();
                if (matches.isEmpty()) {
                    showEmpty(getString(R.string.friends_empty_matches), false);
                } else {
                    layoutEmpty.setVisibility(View.GONE);
                    rvMatches.setVisibility(View.VISIBLE);
                    adapter.submit(matches);
                }
            }

            @Override
            public void onError(int code, String message) {
                setLoading(false);
                AppToast.show(requireContext(), message);
                showEmpty(message, false);
            }
        });
    }

    private void showEmpty(String message, boolean showAllowButton) {
        rvMatches.setVisibility(View.GONE);
        layoutEmpty.setVisibility(View.VISIBLE);
        tvEmpty.setText(message);
        btnAllowContacts.setVisibility(showAllowButton ? View.VISIBLE : View.GONE);
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onFollowClick(@NonNull PhoneMatchItem item, int position) {
        if (item.user == null) {
            return;
        }
        long userId = item.user.id;
        if (item.following) {
            friendRepository.unfollow(userId, new ApiCallback<Boolean>() {
                @Override
                public void onSuccess(Boolean data) {
                    adapter.updateFollowing(position, false);
                }

                @Override
                public void onError(int code, String message) {
                    AppToast.show(requireContext(), message);
                }
            });
        } else {
            friendRepository.follow(userId, new ApiCallback<Boolean>() {
                @Override
                public void onSuccess(Boolean data) {
                    adapter.updateFollowing(position, true);
                }

                @Override
                public void onError(int code, String message) {
                    AppToast.show(requireContext(), message);
                }
            });
        }
    }

    @Override
    public void onItemClick(@NonNull PhoneMatchItem item) {
        if (item.user == null) {
            return;
        }
        Intent intent = new Intent(requireContext(), UserProfileActivity.class);
        intent.putExtra(UserProfileActivity.EXTRA_USER_ID, item.user.id);
        startActivity(intent);
    }
}

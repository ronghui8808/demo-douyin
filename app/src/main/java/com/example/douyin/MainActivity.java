package com.example.douyin;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.example.douyin.auth.LoginActivity;
import com.example.douyin.feed.FeedFragment;
import com.example.douyin.friends.FriendsFragment;
import com.example.douyin.placeholder.PlaceholderFragment;
import com.example.douyin.profile.ProfileFragment;
import com.example.douyin.publish.CameraRecordActivity;
import com.example.douyin.repository.AuthRepository;
import com.example.douyin.util.AppToast;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private static final String TAG_FEED = "tag_feed";
    private static final String TAG_FRIENDS = "tag_friends";
    private static final String TAG_MESSAGES = "tag_messages";
    private static final String TAG_PROFILE = "tag_profile";

    private BottomNavigationView bottomNav;
    private AuthRepository authRepository;
    @IdRes
    private int currentNavItemId = R.id.nav_home;

    private final ActivityResultLauncher<Intent> publishLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    refreshFeedIfVisible();
                    refreshProfileIfVisible();
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        authRepository = new AuthRepository(this);
        bottomNav = findViewById(R.id.bottom_nav);
        setupWindowInsets();
        setupBottomNavigation();

        if (savedInstanceState != null) {
            currentNavItemId = savedInstanceState.getInt("current_nav_item", R.id.nav_home);
            bottomNav.setSelectedItemId(currentNavItemId);
        } else {
            showFragmentForNavItem(R.id.nav_home);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("current_nav_item", currentNavItemId);
    }

    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(bottomNav, (view, insets) -> {
            Insets navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            view.setPadding(0, 0, 0, navigationBars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(bottomNav);
    }

    private void setupBottomNavigation() {
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_publish) {
                openPublishFlow();
                return false;
            }
            if ((itemId == R.id.nav_profile || itemId == R.id.nav_friends)
                    && !authRepository.isLoggedIn()) {
                AppToast.show(this, R.string.login_required);
                startActivity(new Intent(this, LoginActivity.class));
                return false;
            }
            showFragmentForNavItem(itemId);
            return true;
        });
        bottomNav.setOnItemReselectedListener(item -> {
            if (item.getItemId() == R.id.nav_home) {
                refreshFeedIfVisible();
            }
        });
    }

    private void openPublishFlow() {
        if (!authRepository.isLoggedIn()) {
            AppToast.show(this, R.string.login_required);
            startActivity(new Intent(this, LoginActivity.class));
            return;
        }
        publishLauncher.launch(new Intent(this, CameraRecordActivity.class));
    }

    private void refreshFeedIfVisible() {
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(TAG_FEED);
        if (fragment instanceof FeedFragment) {
            ((FeedFragment) fragment).refreshFeed();
        }
    }

    private void refreshProfileIfVisible() {
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(TAG_PROFILE);
        if (fragment instanceof ProfileFragment) {
            ((ProfileFragment) fragment).refreshProfile();
        }
    }

    private void showFragmentForNavItem(@IdRes int navItemId) {
        currentNavItemId = navItemId;
        Fragment fragment = createFragmentForNavItem(navItemId);
        String tag = getTagForNavItem(navItemId);

        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment, tag)
                .commit();
    }

    @NonNull
    private Fragment createFragmentForNavItem(@IdRes int navItemId) {
        if (navItemId == R.id.nav_home) {
            return new FeedFragment();
        }
        if (navItemId == R.id.nav_friends) {
            return new FriendsFragment();
        }
        if (navItemId == R.id.nav_messages) {
            return PlaceholderFragment.newInstance(R.string.tab_messages_title);
        }
        if (navItemId == R.id.nav_profile) {
            return new ProfileFragment();
        }
        return new FeedFragment();
    }

    @NonNull
    private String getTagForNavItem(@IdRes int navItemId) {
        if (navItemId == R.id.nav_home) {
            return TAG_FEED;
        }
        if (navItemId == R.id.nav_friends) {
            return TAG_FRIENDS;
        }
        if (navItemId == R.id.nav_messages) {
            return TAG_MESSAGES;
        }
        if (navItemId == R.id.nav_profile) {
            return TAG_PROFILE;
        }
        return TAG_FEED;
    }
}

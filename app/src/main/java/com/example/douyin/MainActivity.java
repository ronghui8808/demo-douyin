package com.example.douyin;

import android.os.Bundle;
import android.widget.Toast;

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

import com.example.douyin.feed.FeedFragment;
import com.example.douyin.placeholder.PlaceholderFragment;
import com.example.douyin.profile.ProfileFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private static final String TAG_FEED = "tag_feed";
    private static final String TAG_FRIENDS = "tag_friends";
    private static final String TAG_MESSAGES = "tag_messages";
    private static final String TAG_PROFILE = "tag_profile";

    private BottomNavigationView bottomNav;
    @IdRes
    private int currentNavItemId = R.id.nav_home;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

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
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(
                    view.getPaddingLeft(),
                    view.getPaddingTop(),
                    view.getPaddingRight(),
                    systemBars.bottom
            );
            return insets;
        });
    }

    private void setupBottomNavigation() {
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_publish) {
                Toast.makeText(this, R.string.publish_coming_soon, Toast.LENGTH_SHORT).show();
                return false;
            }
            showFragmentForNavItem(itemId);
            return true;
        });
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
            return PlaceholderFragment.newInstance(R.string.tab_friends_title);
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

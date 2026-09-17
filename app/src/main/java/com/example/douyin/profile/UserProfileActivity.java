package com.example.douyin.profile;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.example.douyin.R;

public class UserProfileActivity extends AppCompatActivity {

    public static final String EXTRA_USER_ID = "extra_user_id";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_user_profile);

        long userId = getIntent().getLongExtra(EXTRA_USER_ID, -1);
        if (userId <= 0) {
            finish();
            return;
        }

        View root = findViewById(R.id.user_profile_root);
        UserProfileController controller = new UserProfileController(
                root,
                userId,
                false,
                true,
                null
        );
        controller.load();
    }
}

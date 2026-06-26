package com.example.douyin.publish;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.douyin.R;
import com.example.douyin.network.ApiCallback;
import com.example.douyin.network.model.VideoDto;
import com.example.douyin.repository.VideoRepository;
import com.example.douyin.util.AppExecutors;
import com.example.douyin.util.CoverExtractor;
import com.google.android.material.button.MaterialButton;

import java.io.File;

public class PublishActivity extends AppCompatActivity {

    public static final String EXTRA_VIDEO_PATH = "video_path";

    private VideoView videoPreview;
    private EditText etDescription;
    private MaterialButton btnPublish;
    private ProgressBar progressPublishing;
    private VideoRepository videoRepository;
    private File videoFile;
    private File coverFile;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_publish);

        videoRepository = new VideoRepository(this);

        videoPreview = findViewById(R.id.video_preview);
        etDescription = findViewById(R.id.et_description);
        btnPublish = findViewById(R.id.btn_publish);
        progressPublishing = findViewById(R.id.progress_publishing);
        ImageButton btnBack = findViewById(R.id.btn_back);

        String videoPath = getIntent().getStringExtra(EXTRA_VIDEO_PATH);
        if (TextUtils.isEmpty(videoPath)) {
            Toast.makeText(this, R.string.publish_video_invalid, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        videoFile = new File(videoPath);
        if (!videoFile.exists()) {
            Toast.makeText(this, R.string.publish_video_invalid, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        videoPreview.setVideoURI(Uri.fromFile(videoFile));
        videoPreview.setOnPreparedListener(mp -> {
            mp.setLooping(true);
            videoPreview.start();
        });

        btnBack.setOnClickListener(v -> finish());
        btnPublish.setOnClickListener(v -> publishVideo());
    }

    @Override
    protected void onPause() {
        if (videoPreview.isPlaying()) {
            videoPreview.pause();
        }
        super.onPause();
    }

    private void publishVideo() {
        String description = etDescription.getText() != null
                ? etDescription.getText().toString().trim()
                : "";
        if (TextUtils.isEmpty(description)) {
            description = getString(R.string.publish_default_description);
        }

        setPublishing(true);
        final String finalDescription = description;
        AppExecutors.get().diskIo(() -> {
            File cover = null;
            try {
                cover = CoverExtractor.extractFirstFrame(
                        videoFile,
                        new File(getCacheDir(), "covers")
                );
            } catch (Exception ignored) {
            }
            File finalCover = cover;
            AppExecutors.get().mainThread(() -> doPublish(finalDescription, finalCover));
        });
    }

    private void doPublish(String description, File cover) {
        coverFile = cover;
        videoRepository.publishVideo(videoFile, coverFile, description, new ApiCallback<VideoDto>() {
            @Override
            public void onSuccess(VideoDto data) {
                setPublishing(false);
                Toast.makeText(PublishActivity.this, R.string.publish_success, Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            }

            @Override
            public void onError(int code, String message) {
                setPublishing(false);
                if (code == 401) {
                    Toast.makeText(PublishActivity.this, R.string.login_required, Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                Toast.makeText(
                        PublishActivity.this,
                        message != null ? message : getString(R.string.publish_failed),
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }

    private void setPublishing(boolean publishing) {
        progressPublishing.setVisibility(publishing ? View.VISIBLE : View.GONE);
        btnPublish.setEnabled(!publishing);
        etDescription.setEnabled(!publishing);
    }
}

package com.example.douyin.publish;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.douyin.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CameraRecordActivity extends AppCompatActivity implements CameraRecorder.RecordCallback {

    public static final String EXTRA_VIDEO_PATH = "video_path";

    private static final long MAX_RECORD_MS = 60_000L;

    private TextureView texturePreview;
    private View recordInner;
    private TextView tvRecordTime;
    private CameraRecorder cameraRecorder;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<Intent> publishLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    setResult(RESULT_OK);
                }
                finish();
            });

    private long recordStartMs;
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (cameraRecorder != null && cameraRecorder.isRecording()) {
                long elapsed = System.currentTimeMillis() - recordStartMs;
                tvRecordTime.setText(formatTime(elapsed));
                if (elapsed >= MAX_RECORD_MS) {
                    cameraRecorder.toggleRecording();
                } else {
                    mainHandler.postDelayed(this, 500L);
                }
            }
        }
    };

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (hasAllPermissions()) {
                    startCamera();
                } else {
                    Toast.makeText(this, R.string.camera_permission_denied, Toast.LENGTH_SHORT).show();
                    finish();
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_camera_record);
        hideSystemBars();

        texturePreview = findViewById(R.id.texture_preview);
        recordInner = findViewById(R.id.view_record_inner);
        tvRecordTime = findViewById(R.id.tv_record_time);

        ImageButton btnClose = findViewById(R.id.btn_close);
        View btnRecord = findViewById(R.id.btn_record);

        cameraRecorder = new CameraRecorder(this, texturePreview, this);

        btnClose.setOnClickListener(v -> finish());
        btnRecord.setOnClickListener(v -> {
            if (hasAllPermissions()) {
                cameraRecorder.toggleRecording();
            } else {
                requestPermissions();
            }
        });

        requestPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasAllPermissions() && cameraRecorder != null) {
            cameraRecorder.startBackgroundThread();
            if (texturePreview.isAvailable()) {
                cameraRecorder.openCamera();
            }
        }
    }

    @Override
    protected void onPause() {
        if (cameraRecorder != null) {
            if (cameraRecorder.isRecording()) {
                cameraRecorder.toggleRecording();
            }
            cameraRecorder.closeCamera();
            cameraRecorder.stopBackgroundThread();
        }
        mainHandler.removeCallbacks(timerRunnable);
        super.onPause();
    }

    private void requestPermissions() {
        List<String> needed = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.CAMERA);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.RECORD_AUDIO);
        }
        if (needed.isEmpty()) {
            startCamera();
        } else {
            permissionLauncher.launch(needed.toArray(new String[0]));
        }
    }

    private boolean hasAllPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void startCamera() {
        cameraRecorder.startBackgroundThread();
        cameraRecorder.openCamera();
    }

    private void hideSystemBars() {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            controller.hide(WindowInsetsCompat.Type.statusBars());
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    private String formatTime(long millis) {
        long seconds = millis / 1000L;
        return String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60);
    }

    @Override
    public void onRecordingStarted() {
        recordStartMs = System.currentTimeMillis();
        tvRecordTime.setVisibility(View.VISIBLE);
        tvRecordTime.setText(R.string.record_time_start);
        recordInner.setBackgroundResource(R.drawable.bg_record_inner_recording);
        mainHandler.post(timerRunnable);
    }

    @Override
    public void onRecordingStopped(@NonNull File outputFile) {
        mainHandler.removeCallbacks(timerRunnable);
        tvRecordTime.setVisibility(View.GONE);
        recordInner.setBackgroundResource(R.drawable.bg_record_inner);

        Intent intent = new Intent(this, PublishActivity.class);
        intent.putExtra(PublishActivity.EXTRA_VIDEO_PATH, outputFile.getAbsolutePath());
        publishLauncher.launch(intent);
    }

    @Override
    public void onError(String message) {
        mainHandler.removeCallbacks(timerRunnable);
        tvRecordTime.setVisibility(View.GONE);
        recordInner.setBackgroundResource(R.drawable.bg_record_inner);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}

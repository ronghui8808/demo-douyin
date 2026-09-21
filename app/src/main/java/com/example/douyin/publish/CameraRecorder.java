package com.example.douyin.publish;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Size;
import android.view.Display;
import android.view.Surface;
import android.view.TextureView;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CameraRecorder implements TextureView.SurfaceTextureListener {

    public interface RecordCallback {
        void onRecordingStarted();

        void onRecordingStopped(File outputFile);

        void onError(String message);
    }

    private final Context context;
    private final TextureView textureView;
    private final RecordCallback callback;

    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder previewBuilder;
    private MediaRecorder mediaRecorder;
    private Size previewSize;
    private Size videoSize;
    private String cameraId;
    private int sensorOrientation = 90;
    private Integer lensFacing;
    private boolean isRecording;
    private File outputFile;

    public CameraRecorder(Context context, TextureView textureView, RecordCallback callback) {
        this.context = context.getApplicationContext();
        this.textureView = textureView;
        this.callback = callback;
        textureView.setSurfaceTextureListener(this);
    }

    public void startBackgroundThread() {
        if (cameraThread == null) {
            cameraThread = new HandlerThread("CameraRecorder");
            cameraThread.start();
            cameraHandler = new Handler(cameraThread.getLooper());
        }
    }

    public void stopBackgroundThread() {
        if (cameraThread != null) {
            cameraThread.quitSafely();
            try {
                cameraThread.join();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            cameraThread = null;
            cameraHandler = null;
        }
    }

    public void openCamera() {
        startBackgroundThread();
        try {
            CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (manager == null) {
                notifyError("无法访问相机");
                return;
            }
            cameraId = chooseBackCameraId(manager);
            if (cameraId == null) {
                notifyError("未找到可用相机");
                return;
            }
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            Integer sensor = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            sensorOrientation = sensor != null ? sensor : 90;
            lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
            StreamConfigurationMap map = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) {
                notifyError("相机配置无效");
                return;
            }
            previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), 1280, 720);
            videoSize = chooseOptimalSize(map.getOutputSizes(MediaRecorder.class), 1280, 720);
            manager.openCamera(cameraId, stateCallback, cameraHandler);
        } catch (CameraAccessException | SecurityException e) {
            notifyError(e.getMessage() != null ? e.getMessage() : "打开相机失败");
        }
    }

    public void closeCamera() {
        if (isRecording) {
            stopRecordingInternal(false);
        }
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    public boolean isRecording() {
        return isRecording;
    }

    public void toggleRecording() {
        if (isRecording) {
            stopRecording();
        } else {
            startRecording();
        }
    }

    private void startRecording() {
        if (cameraDevice == null || isRecording) {
            return;
        }
        try {
            closeSession();
            setupMediaRecorder();
            Surface previewSurface = new Surface(textureView.getSurfaceTexture());
            Surface recorderSurface = mediaRecorder.getSurface();

            previewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            previewBuilder.addTarget(previewSurface);
            previewBuilder.addTarget(recorderSurface);

            List<Surface> surfaces = new ArrayList<>();
            surfaces.add(previewSurface);
            surfaces.add(recorderSurface);
//            textureView.setRotation(90);
            cameraDevice.createCaptureSession(
                    surfaces,
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            captureSession = session;
                            try {
                                captureSession.setRepeatingRequest(
                                        previewBuilder.build(), null, cameraHandler);
                                mediaRecorder.start();
                                isRecording = true;
                                notifyRecordingStarted();
                            } catch (CameraAccessException | IllegalStateException e) {
                                notifyError("开始录制失败");
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            notifyError("录制配置失败");
                        }
                    },
                    cameraHandler
            );
        } catch (IOException | CameraAccessException e) {
            notifyError(e.getMessage() != null ? e.getMessage() : "录制失败");
        }
    }

    private void stopRecording() {
        stopRecordingInternal(true);
    }

    private void stopRecordingInternal(boolean notifySuccess) {
        if (!isRecording) {
            return;
        }
        isRecording = false;
        try {
            mediaRecorder.stop();
        } catch (RuntimeException ignored) {
            if (outputFile != null && outputFile.exists()) {
                outputFile.delete();
            }
            notifyError("录制时间过短");
            notifySuccess = false;
        }
        releaseMediaRecorder();
        if (notifySuccess && outputFile != null && outputFile.exists()) {
            notifyRecordingStopped(outputFile);
        }
        startPreview();
    }

    private void setupMediaRecorder() throws IOException {
        File dir = new File(context.getCacheDir(), "records");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        outputFile = new File(dir, "record_" + System.currentTimeMillis() + ".mp4");

        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setOutputFile(outputFile.getAbsolutePath());
        mediaRecorder.setVideoEncodingBitRate(4_000_000);
        mediaRecorder.setVideoFrameRate(30);
        mediaRecorder.setVideoSize(videoSize.getWidth(), videoSize.getHeight());
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setAudioEncodingBitRate(128_000);
        mediaRecorder.setAudioSamplingRate(44100);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setOrientationHint(computeVideoOrientationHint());
        mediaRecorder.prepare();
    }

    private void releaseMediaRecorder() {
        if (mediaRecorder != null) {
            mediaRecorder.reset();
            mediaRecorder.release();
            mediaRecorder = null;
        }
    }

    private void startPreview() {
        if (cameraDevice == null || !textureView.isAvailable()) {
            return;
        }
        try {
            closeSession();
            SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
            if (previewSize != null) {
                surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            }
            postConfigureTransform(textureView.getWidth(), textureView.getHeight());
            Surface previewSurface = new Surface(surfaceTexture);
            previewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewBuilder.addTarget(previewSurface);
            cameraDevice.createCaptureSession(
                    Collections.singletonList(previewSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            captureSession = session;
                            try {
                                captureSession.setRepeatingRequest(
                                        previewBuilder.build(), null, cameraHandler);
                            } catch (CameraAccessException ignored) {
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            notifyError("预览配置失败");
                        }
                    },
                    cameraHandler
            );
        } catch (CameraAccessException e) {
            notifyError("预览失败");
        }
    }

    private void notifyRecordingStarted() {
        mainHandler.post(callback::onRecordingStarted);
    }

    private void notifyRecordingStopped(File file) {
        mainHandler.post(() -> callback.onRecordingStopped(file));
    }

    private void notifyError(String message) {
        mainHandler.post(() -> callback.onError(message));
    }

    private void postConfigureTransform(int viewWidth, int viewHeight) {
        mainHandler.post(() -> configureTransform(viewWidth, viewHeight));
    }

    private void closeSession() {
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
    }

    @Nullable
    private String chooseBackCameraId(CameraManager manager) throws CameraAccessException {
        for (String id : manager.getCameraIdList()) {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(id);
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                return id;
            }
        }
        String[] ids = manager.getCameraIdList();
        return ids.length > 0 ? ids[0] : null;
    }

    private Size chooseOptimalSize(Size[] choices, int targetWidth, int targetHeight) {
        if (choices == null || choices.length == 0) {
            return new Size(targetWidth, targetHeight);
        }
        List<Size> bigEnough = new ArrayList<>();
        for (Size option : choices) {
            if (option.getWidth() >= targetWidth && option.getHeight() >= targetHeight) {
                bigEnough.add(option);
            }
        }
        if (!bigEnough.isEmpty()) {
            return Collections.min(bigEnough, (a, b) ->
                    Long.signum((long) a.getWidth() * a.getHeight()
                            - (long) b.getWidth() * b.getHeight()));
        }
        return choices[0];
    }

    /**
     * 录制文件元数据中的顺时针旋转角度（MediaRecorder.setOrientationHint）。
     * 后置：R = (sensorOrientation - degree) % 360
     * 前置（含镜像）：R = (360 - (sensorOrientation + degree) % 360) % 360
     */
    private int computeVideoOrientationHint() {
        int degree = getDisplayRotationDegrees();
        if (isFrontCamera()) {
            int r1 = (sensorOrientation + degree) % 360;
            return (360 - r1) % 360;
        }
        return (sensorOrientation - degree + 360) % 360;
    }

    /**
     * 传感器相对屏幕的偏转 W，用于判断预览 buffer 是否需交换宽高。
     */
    private int getSensorToDisplayRotationW() {
        return (sensorOrientation - getDisplayRotationDegrees() + 360) % 360;
    }

    private boolean isFrontCamera() {
        return lensFacing != null && lensFacing == CameraCharacteristics.LENS_FACING_FRONT;
    }

    /**
     * TextureView 预览变换。
     * <p>
     * Camera2 输出到 Surface 时，系统已按 Surface 尺寸排布像素；对 TextureView 再 postRotate(R)
     * 会在 W=90 时额外顺时针转 90°，导致预览错误。实测 R=0、仅做 centerCrop 缩放时预览正确。
     * 录制方向由 {@link #computeVideoOrientationHint()} 写入 MP4 元数据，与预览矩阵分离。
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        if (previewSize == null || textureView == null || viewWidth == 0 || viewHeight == 0) {
            return;
        }

        int w = getSensorToDisplayRotationW();
        boolean swapBuffer = w == 90 || w == 270;

        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();

        RectF bufferRect;
        if (swapBuffer) {
            bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
        } else {
            bufferRect = new RectF(0, 0, previewSize.getWidth(), previewSize.getHeight());
        }
        bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
        matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);

        float scale = Math.max(
                viewHeight / bufferRect.height(),
                viewWidth / bufferRect.width());
        matrix.postScale(scale, scale, centerX, centerY);

        textureView.setTransform(matrix);
    }

    private int getDisplayRotation() {
        Display display = textureView.getDisplay();
        if (display != null) {
            return display.getRotation();
        }
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager != null) {
            return windowManager.getDefaultDisplay().getRotation();
        }
        return Surface.ROTATION_0;
    }

    private int getDisplayRotationDegrees() {
        switch (getDisplayRotation()) {
            case Surface.ROTATION_90:
                return 90;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_270:
                return 270;
            default:
                return 0;
        }
    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            if (textureView.isAvailable()) {
                startPreview();
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            camera.close();
            cameraDevice = null;
            notifyError("相机错误: " + error);
        }
    };

    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
        if (previewSize != null) {
            surface.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
        }
        postConfigureTransform(width, height);
        if (cameraDevice != null) {
            startPreview();
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
        postConfigureTransform(width, height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
    }
}

package com.example.douyin.player;

import android.graphics.Matrix;
import android.view.TextureView;

/**
 * 宽度铺满、保持原始宽高比；左右不留黑边，上下可留黑边。
 */
public final class VideoTransformHelper {

    private VideoTransformHelper() {
    }

    public static void applyFitWidth(TextureView textureView, int videoWidth, int videoHeight) {
        if (textureView == null || videoWidth <= 0 || videoHeight <= 0) {
            return;
        }
        int viewWidth = textureView.getWidth();
        int viewHeight = textureView.getHeight();
        if (viewWidth <= 0 || viewHeight <= 0) {
            textureView.post(() -> applyFitWidth(textureView, videoWidth, videoHeight));
            return;
        }

        // 默认 TextureView 会把视频拉伸到 View 大小；通过 Y 轴缩放还原宽高比，宽度始终铺满
        float scaleY = (viewWidth * (float) videoHeight) / (viewHeight * (float) videoWidth);
        Matrix matrix = new Matrix();
        matrix.setScale(1f, scaleY, viewWidth / 2f, viewHeight / 2f);
        textureView.setTransform(matrix);
    }
}

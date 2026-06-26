package com.example.douyin.oss;

import android.text.TextUtils;

import com.example.douyin.BuildConfig;

public final class OssConfig {

    private static OssConfig instance;

    public final boolean enabled;
    public final String endpoint;
    public final String bucket;
    public final String accessKeyId;
    public final String accessKeySecret;
    public final String publicDomain;

    private OssConfig(
            boolean enabled,
            String endpoint,
            String bucket,
            String accessKeyId,
            String accessKeySecret,
            String publicDomain
    ) {
        this.enabled = enabled;
        this.endpoint = endpoint;
        this.bucket = bucket;
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
        this.publicDomain = publicDomain;
    }

    public static synchronized OssConfig get() {
        if (instance == null) {
            instance = new OssConfig(
                    BuildConfig.OSS_ENABLED,
                    BuildConfig.OSS_ENDPOINT,
                    BuildConfig.OSS_BUCKET,
                    BuildConfig.OSS_ACCESS_KEY_ID,
                    BuildConfig.OSS_ACCESS_KEY_SECRET,
                    BuildConfig.OSS_PUBLIC_DOMAIN
            );
        }
        return instance;
    }

    public boolean isConfigured() {
        return enabled
                && !TextUtils.isEmpty(endpoint)
                && !TextUtils.isEmpty(bucket)
                && !TextUtils.isEmpty(accessKeyId)
                && !TextUtils.isEmpty(accessKeySecret)
                && !TextUtils.isEmpty(publicDomain);
    }

    public String buildPublicUrl(String objectKey) {
        if (TextUtils.isEmpty(objectKey)) {
            return "";
        }
        String domain = publicDomain.trim();
        if (domain.endsWith("/")) {
            domain = domain.substring(0, domain.length() - 1);
        }
        String key = objectKey.trim();
        if (key.startsWith("/")) {
            key = key.substring(1);
        }
        return domain + "/" + key;
    }
}

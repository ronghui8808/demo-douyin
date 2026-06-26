package com.example.douyin.oss;

import com.alibaba.sdk.android.oss.ClientConfiguration;
import com.alibaba.sdk.android.oss.OSS;
import com.alibaba.sdk.android.oss.OSSClient;
import com.alibaba.sdk.android.oss.common.auth.OSSPlainTextAKSKCredentialProvider;

import android.content.Context;

public final class OssClientHolder {

    private static OSS client;

    private OssClientHolder() {
    }

    public static synchronized OSS getClient(Context context) {
        if (client == null) {
            OssConfig config = OssConfig.get();
            if (!config.isConfigured()) {
                throw new IllegalStateException("OSS 未配置");
            }
            ClientConfiguration configuration = new ClientConfiguration();
            configuration.setConnectionTimeout(15_000);
            configuration.setSocketTimeout(15_000);
            configuration.setMaxConcurrentRequest(3);
            configuration.setMaxErrorRetry(2);
            OSSPlainTextAKSKCredentialProvider provider =
                    new OSSPlainTextAKSKCredentialProvider(config.accessKeyId, config.accessKeySecret);
            client = new OSSClient(
                    context.getApplicationContext(),
                    config.endpoint,
                    provider,
                    configuration
            );
        }
        return client;
    }
}

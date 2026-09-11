package com.example.douyin.oss;

import com.alibaba.sdk.android.oss.ClientConfiguration;
import com.alibaba.sdk.android.oss.OSS;
import com.alibaba.sdk.android.oss.OSSClient;
import com.alibaba.sdk.android.oss.common.auth.OSSPlainTextAKSKCredentialProvider;

import android.content.Context;

/**
 * OSS 客户端单例。
 * <p>
 * Demo 阶段用 {@link OSSPlainTextAKSKCredentialProvider} + BuildConfig 明文 AK/SK，便于本地联调。
 * 正确做法：服务端用主账号/RAM 换取 STS 临时凭证（AccessKeyId / AccessKeySecret / SecurityToken），
 * 客户端用 STS 凭证上传；永久密钥不下发 APK，避免反编译泄露。
 */
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
            // Demo only：明文 AK/SK。生产应改为 STS 临时凭证（见类注释）。
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

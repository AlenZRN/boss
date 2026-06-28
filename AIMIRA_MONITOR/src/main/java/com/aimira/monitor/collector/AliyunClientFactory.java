package com.aimira.monitor.collector;

import com.aimira.monitor.config.AliyunConfig;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.profile.DefaultProfile;
import org.springframework.stereotype.Component;

/**
 * 阿里云 OpenAPI 客户端工厂
 * 统一管理 ACS Client 创建
 */
@Component
public class AliyunClientFactory {

    private final AliyunConfig aliyunConfig;

    public AliyunClientFactory(AliyunConfig aliyunConfig) {
        this.aliyunConfig = aliyunConfig;
    }

    /**
     * 创建默认 Region 的 ACS Client
     */
    public IAcsClient createClient() {
        return createClient(aliyunConfig.getRegion());
    }

    /**
     * 创建指定 Region 的 ACS Client
     */
    public IAcsClient createClient(String region) {
        DefaultProfile profile = DefaultProfile.getProfile(
                region,
                aliyunConfig.getAccessKey(),
                aliyunConfig.getSecret()
        );
        return new DefaultAcsClient(profile);
    }
}

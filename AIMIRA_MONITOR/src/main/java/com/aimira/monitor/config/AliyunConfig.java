package com.aimira.monitor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "aliyun")
public class AliyunConfig {

    /** 阿里云 AccessKey */
    private String accessKey;

    /** 阿里云 Secret */
    private String secret;

    /** 默认 Region */
    private String region = "cn-hangzhou";
}

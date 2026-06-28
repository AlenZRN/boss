package com.aimira.monitor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "wecom")
public class WeComConfig {

    /** 企业微信机器人 Webhook URL */
    private String webhookUrl;

    /** 是否启用企业微信通知 */
    private boolean enabled = true;
}

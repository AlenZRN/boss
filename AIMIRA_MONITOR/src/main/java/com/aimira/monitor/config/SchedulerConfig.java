package com.aimira.monitor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "scheduler")
public class SchedulerConfig {

    /** 数据采集 Cron 表达式 */
    private String collectCron = "0 0 * * * *";

    /** 告警检测 Cron 表达式 */
    private String alarmCron = "0 0 * * * *";

    /** 告警冷却时间（秒），默认 86400 = 24小时 */
    private long alarmCooldownSeconds = 86400;
}

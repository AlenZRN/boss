package com.aimira.monitor.alarm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 告警检测定时任务
 * 默认每小时执行一次
 */
@Slf4j
@Component
public class AlarmScheduler {

    private final AlarmDetector alarmDetector;

    public AlarmScheduler(AlarmDetector alarmDetector) {
        this.alarmDetector = alarmDetector;
    }

    /**
     * 定时执行告警检测（默认每小时）
     */
    @Scheduled(cron = "${scheduler.alarm-cron:0 0 * * * *}")
    public void runAlarmDetection() {
        log.info("===== 定时告警检测开始 =====");
        long startTime = System.currentTimeMillis();

        try {
            var records = alarmDetector.detectAll();
            log.info("告警检测完成，本次发送 {} 条告警", records.size());
        } catch (Exception e) {
            log.error("告警检测任务异常", e);
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("===== 定时告警检测完成, 耗时: {}ms =====", elapsed);
    }
}

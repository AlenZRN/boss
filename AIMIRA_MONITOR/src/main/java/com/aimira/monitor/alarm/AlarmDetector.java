package com.aimira.monitor.alarm;

import com.aimira.monitor.config.SchedulerConfig;
import com.aimira.monitor.entity.*;
import com.aimira.monitor.notifier.WeComNotifier;
import com.aimira.monitor.repository.AlarmRecordRepository;
import com.aimira.monitor.repository.AlarmRuleRepository;
import com.aimira.monitor.service.BalanceService;
import com.aimira.monitor.service.ResourceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 告警检测器
 * 扫描最新的余额和资源数据，匹配告警规则，发送通知
 */
@Slf4j
@Component
public class AlarmDetector {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AlarmRuleRepository alarmRuleRepository;
    private final AlarmRecordRepository alarmRecordRepository;
    private final BalanceService balanceService;
    private final ResourceService resourceService;
    private final WeComNotifier weComNotifier;
    private final SchedulerConfig schedulerConfig;

    public AlarmDetector(AlarmRuleRepository alarmRuleRepository,
                         AlarmRecordRepository alarmRecordRepository,
                         BalanceService balanceService,
                         ResourceService resourceService,
                         WeComNotifier weComNotifier,
                         SchedulerConfig schedulerConfig) {
        this.alarmRuleRepository = alarmRuleRepository;
        this.alarmRecordRepository = alarmRecordRepository;
        this.balanceService = balanceService;
        this.resourceService = resourceService;
        this.weComNotifier = weComNotifier;
        this.schedulerConfig = schedulerConfig;
    }

    /**
     * 执行所有告警检测
     * @return 本次发送的告警记录
     */
    public List<AlarmRecord> detectAll() {
        List<AlarmRecord> records = new ArrayList<>();
        records.addAll(detectBalanceAlarms());
        records.addAll(detectExpiryAlarms());
        return records;
    }

    /**
     * 余额告警检测 — 按 cloudProvider 分别检测，独立判断阈值
     */
    private List<AlarmRecord> detectBalanceAlarms() {
        List<AlarmRecord> records = new ArrayList<>();

        List<AlarmRule> rules = alarmRuleRepository.findByAlarmTypeAndEnabledTrue("BALANCE");
        if (rules.isEmpty()) {
            return records;
        }

        List<String> providers = balanceService.getDistinctCloudProviders();
        if (providers.isEmpty()) {
            log.debug("无云厂商余额数据，跳过余额告警检测");
            return records;
        }

        for (String provider : providers) {
            BalanceHistory latestBalance = balanceService.getLatestByCloudProvider(provider);
            if (latestBalance == null) {
                continue;
            }

            for (AlarmRule rule : rules) {
                // 余额告警用 cloudProvider 做去重 key（替代 null），避免跨云冷却互相抑制
                if (isInCooldown(rule.getId(), provider)) {
                    continue;
                }

                BigDecimal threshold = rule.getThreshold();
                BigDecimal currentBalance = latestBalance.getBalance();

                if (isTriggered(currentBalance, threshold, rule.getOperator())) {
                    String message = buildBalanceAlarmMessage(rule, latestBalance, provider);
                    AlarmRecord record = weComNotifier.send(rule.getId(), message);
                    // resourceId 存 provider 以区分跨云冷却
                    record.setResourceId(provider);
                    alarmRecordRepository.save(record);
                    records.add(record);
                    log.warn("[{}] 余额告警触发: ruleId={}, balance={}, threshold={}",
                            provider, rule.getId(), currentBalance, threshold);
                }
            }
        }
        return records;
    }

    /**
     * 到期告警检测
     */
    private List<AlarmRecord> detectExpiryAlarms() {
        List<AlarmRecord> records = new ArrayList<>();

        List<ResourceInfo> resources = resourceService.getResourcesWithExpiry();
        if (resources.isEmpty()) {
            log.debug("无到期资源，跳过到期告警检测");
            return records;
        }

        List<AlarmRule> rules = alarmRuleRepository.findByAlarmTypeAndEnabledTrue("EXPIRY");
        LocalDateTime now = LocalDateTime.now();

        for (ResourceInfo resource : resources) {
            if (resource.getExpireTime() == null) {
                continue;
            }
            long daysLeft = ChronoUnit.DAYS.between(now, resource.getExpireTime());

            for (AlarmRule rule : rules) {
                // 检查冷却时间（按资源 + 规则去重）
                if (isInCooldown(rule.getId(), resource.getResourceId())) {
                    continue;
                }

                BigDecimal threshold = rule.getThreshold();
                if (isTriggered(BigDecimal.valueOf(daysLeft), threshold, rule.getOperator())) {
                    String message = buildExpiryAlarmMessage(rule, resource, daysLeft);
                    AlarmRecord record = weComNotifier.send(rule.getId(), resource.getResourceId(), message);
                    alarmRecordRepository.save(record);
                    records.add(record);
                    log.warn("到期告警触发: ruleId={}, resourceId={}, daysLeft={}",
                            rule.getId(), resource.getResourceId(), daysLeft);
                }
            }
        }
        return records;
    }

    /**
     * 检查是否在冷却时间内，如在冷却期内则打印详细信息
     */
    private boolean isInCooldown(Long ruleId, String resourceId) {
        long cooldownSeconds = schedulerConfig.getAlarmCooldownSeconds();
        LocalDateTime since = LocalDateTime.now().minusSeconds(cooldownSeconds);
        boolean inCooldown = alarmRecordRepository.existsWithinCooldown(ruleId, resourceId, since);
        if (inCooldown) {
            List<LocalDateTime> lastTimes = alarmRecordRepository.findLastSentTimes(
                    ruleId, resourceId, PageRequest.of(0, 1));
            if (!lastTimes.isEmpty()) {
                LocalDateTime lastSent = lastTimes.get(0);
                long secondsAgo = ChronoUnit.SECONDS.between(lastSent, LocalDateTime.now());
                long remaining = cooldownSeconds - secondsAgo;
                log.info("告警在冷却期内: ruleId={}, resourceId={}, 冷却时长={}s, 上次发送={}, {}s前, 剩余={}s",
                        ruleId, resourceId, cooldownSeconds,
                        FMT.format(lastSent), secondsAgo, Math.max(0, remaining));
            } else {
                log.info("告警在冷却期内: ruleId={}, resourceId={}, 冷却时长={}s (未查到上次发送记录)",
                        ruleId, resourceId, cooldownSeconds);
            }
        }
        return inCooldown;
    }

    /**
     * 判断是否触发阈值
     */
    private boolean isTriggered(BigDecimal currentValue, BigDecimal threshold, String operator) {
        if (currentValue == null || threshold == null) {
            return false;
        }
        String op = operator != null ? operator : "LESS_THAN";
        return switch (op) {
            case "LESS_THAN" -> currentValue.compareTo(threshold) < 0;
            case "LESS_THAN_OR_EQUAL" -> currentValue.compareTo(threshold) <= 0;
            case "GREATER_THAN" -> currentValue.compareTo(threshold) > 0;
            case "GREATER_THAN_OR_EQUAL" -> currentValue.compareTo(threshold) >= 0;
            default -> false;
        };
    }

    private String buildBalanceAlarmMessage(AlarmRule rule, BalanceHistory balance, String provider) {
        return String.format("""
                ## ⚠️ %s 余额告警
                > 规则: **%s**
                > 当前余额: **%s %s**
                > 告警阈值: **%s %s**
                > 可用金额: **%s %s**
                > 检测时间: %s
                """,
                provider,
                rule.getRuleName(),
                balance.getBalance(), balance.getCurrency(),
                rule.getThreshold(), balance.getCurrency(),
                balance.getAvailableAmount(), balance.getCurrency(),
                FMT.format(LocalDateTime.now()));
    }

    private String buildExpiryAlarmMessage(AlarmRule rule, ResourceInfo resource, long daysLeft) {
        String provider = resource.getCloudProvider() != null ? resource.getCloudProvider() : "阿里云";
        return String.format("""
                ## ⏰ %s 资源到期告警
                > 规则: **%s**
                > 资源名称: **%s**
                > 资源ID: %s
                > 资源类型: %s
                > 地域: %s
                > 到期时间: %s
                > 剩余天数: **%d 天**
                > 检测时间: %s
                """,
                provider,
                rule.getRuleName(),
                resource.getResourceName(),
                resource.getResourceId(),
                resource.getResourceType(),
                resource.getRegion(),
                resource.getExpireTime() != null ? FMT.format(resource.getExpireTime()) : "未知",
                daysLeft,
                FMT.format(LocalDateTime.now()));
    }
}

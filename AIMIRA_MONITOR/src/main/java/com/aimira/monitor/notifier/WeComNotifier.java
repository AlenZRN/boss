package com.aimira.monitor.notifier;

import com.aimira.monitor.config.WeComConfig;
import com.aimira.monitor.entity.AlarmRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 企业微信机器人 Webhook 通知器
 * 发送 Markdown 格式告警消息
 */
@Slf4j
@Component
public class WeComNotifier {

    private final WeComConfig weComConfig;
    private final RestTemplate restTemplate;

    public WeComNotifier(WeComConfig weComConfig) {
        this.weComConfig = weComConfig;
        this.restTemplate = new RestTemplate();
    }

    /**
     * 发送告警消息
     * @param ruleId   告警规则ID
     * @param message  Markdown 格式消息体
     * @return 发送记录
     */
    public AlarmRecord send(Long ruleId, String message) {
        return send(ruleId, null, message);
    }

    /**
     * 发送告警消息（关联资源）
     * @param ruleId     告警规则ID
     * @param resourceId 关联资源ID
     * @param message    Markdown 格式消息体
     * @return 发送记录
     */
    public AlarmRecord send(Long ruleId, String resourceId, String message) {
        if (!weComConfig.isEnabled()) {
            log.info("企业微信通知未启用，跳过发送: ruleId={}", ruleId);
            return buildRecord(ruleId, resourceId, message, "SKIPPED");
        }

        if (weComConfig.getWebhookUrl() == null || weComConfig.getWebhookUrl().isBlank()) {
            log.warn("企业微信 Webhook URL 未配置，跳过发送: ruleId={}", ruleId);
            return buildRecord(ruleId, resourceId, message, "SKIPPED");
        }

        try {
            Map<String, Object> body = Map.of(
                    "msgtype", "markdown",
                    "markdown", Map.of("content", message)
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            String response = restTemplate.postForObject(weComConfig.getWebhookUrl(), entity, String.class);
            log.info("企业微信通知发送成功: ruleId={}, response={}", ruleId, response);
            return buildRecord(ruleId, resourceId, message, "SUCCESS");
        } catch (Exception e) {
            log.error("企业微信通知发送失败: ruleId={}, error={}", ruleId, e.getMessage(), e);
            return buildRecord(ruleId, resourceId, message, "FAILED");
        }
    }

    private AlarmRecord buildRecord(Long ruleId, String resourceId, String message, String status) {
        return AlarmRecord.builder()
                .ruleId(ruleId)
                .resourceId(resourceId)
                .message(message)
                .sentTime(LocalDateTime.now())
                .status(status)
                .build();
    }
}

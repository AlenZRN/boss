package com.aimira.monitor.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 告警发送记录（用于去重）
 */
@Entity
@Table(name = "alarm_record", indexes = {
        @Index(name = "idx_alarm_rule_time", columnList = "ruleId, sentTime"),
        @Index(name = "idx_alarm_resource_rule", columnList = "resourceId, ruleId, sentTime")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlarmRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联告警规则 */
    @Column(nullable = false)
    private Long ruleId;

    /** 关联资源ID（到期告警时有值，余额告警时为空） */
    @Column(length = 128)
    private String resourceId;

    /** 告警消息内容 */
    @Column(nullable = false, length = 2048)
    private String message;

    /** 发送时间 */
    @Column(nullable = false)
    private LocalDateTime sentTime;

    /** 发送状态: SUCCESS / FAILED */
    @Column(nullable = false, length = 16)
    private String status;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}

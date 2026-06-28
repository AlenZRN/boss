package com.aimira.monitor.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 告警规则配置
 */
@Entity
@Table(name = "alarm_rule")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlarmRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 规则名称 */
    @Column(nullable = false, length = 128)
    private String ruleName;

    /** 告警类型: BALANCE(余额) / EXPIRY(到期) */
    @Column(nullable = false, length = 32)
    private String alarmType;

    /** 阈值：余额告警为金额，到期告警为天数 */
    @Column(precision = 18, scale = 2)
    private BigDecimal threshold;

    /** 比较运算符: LESS_THAN, LESS_THAN_OR_EQUAL, GREATER_THAN 等 */
    @Column(length = 32)
    private String operator;

    /** 是否启用 */
    @Builder.Default
    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

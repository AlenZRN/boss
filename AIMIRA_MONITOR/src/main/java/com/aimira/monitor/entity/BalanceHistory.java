package com.aimira.monitor.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 账户余额历史记录
 */
@Entity
@Table(name = "balance_history", indexes = {
        @Index(name = "idx_balance_sync_time", columnList = "syncTime")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 当前账户余额 */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal balance;

    /** 可用金额 */
    @Column(precision = 18, scale = 2)
    private BigDecimal availableAmount;

    /** 币种 */
    @Column(length = 10)
    private String currency;

    /** 数据同步时间 */
    @Column(nullable = false)
    private LocalDateTime syncTime;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}

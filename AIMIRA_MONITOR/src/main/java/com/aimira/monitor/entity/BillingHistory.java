package com.aimira.monitor.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 费用历史记录
 */
@Entity
@Table(name = "billing_history", indexes = {
        @Index(name = "idx_billing_sync_time", columnList = "syncTime"),
        @Index(name = "idx_billing_bill_date", columnList = "billDate")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 当日费用 */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal dailyCost;

    /** 本月累计费用 */
    @Column(precision = 18, scale = 2)
    private BigDecimal monthlyCost;

    /** 币种 */
    @Column(length = 10)
    private String currency;

    /** 账单日期 */
    @Column(nullable = false)
    private LocalDateTime billDate;

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

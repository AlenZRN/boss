package com.aimira.monitor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Dashboard 首页数据结构
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardDTO {

    /** 概览卡片 */
    private OverviewCard overview;

    /** 趋势数据 */
    private TrendData trend;

    /** 到期资源列表 */
    private ExpiringResourcePage expiringResources;

    // ---- 子结构 ----

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OverviewCard {
        /** 当前账户余额 */
        private BigDecimal balance;
        /** 今日费用 */
        private BigDecimal dailyCost;
        /** 本月费用 */
        private BigDecimal monthlyCost;
        /** 即将到期资源数量 */
        private long expiringCount;
        /** 币种 */
        private String currency;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendPoint {
        private String date;
        private BigDecimal value;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendData {
        /** 最近30天余额变化 */
        private List<TrendPoint> balanceTrend;
        /** 最近30天费用变化 */
        private List<TrendPoint> billingTrend;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExpiringResource {
        private String resourceId;
        private String resourceName;
        private String resourceType;
        private String region;
        /** 云厂商标识 */
        private String cloudProvider;
        private String expireTime;
        /** 剩余天数 */
        private long remainingDays;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExpiringResourcePage {
        private List<ExpiringResource> items;
        private int page;
        private int size;
        private long total;
    }
}

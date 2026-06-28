package com.aimira.monitor.dashboard;

import com.aimira.monitor.dto.ApiResponse;
import com.aimira.monitor.dto.DashboardDTO;
import com.aimira.monitor.entity.AlarmRule;
import com.aimira.monitor.entity.BalanceHistory;
import com.aimira.monitor.entity.BillingHistory;
import com.aimira.monitor.entity.ResourceInfo;
import com.aimira.monitor.service.AlarmService;
import com.aimira.monitor.service.BalanceService;
import com.aimira.monitor.service.BillingService;
import com.aimira.monitor.service.ResourceService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final BalanceService balanceService;
    private final BillingService billingService;
    private final ResourceService resourceService;
    private final AlarmService alarmService;

    public DashboardController(BalanceService balanceService,
                               BillingService billingService,
                               ResourceService resourceService,
                               AlarmService alarmService) {
        this.balanceService = balanceService;
        this.billingService = billingService;
        this.resourceService = resourceService;
        this.alarmService = alarmService;
    }

    /**
     * Dashboard 首页数据
     * GET /api/dashboard/overview
     */
    @GetMapping("/overview")
    public ApiResponse<DashboardDTO> getOverview() {
        // --- 概览卡片 ---
        BalanceHistory latestBalance = balanceService.getLatest();
        BillingHistory latestBilling = billingService.getLatest();
        long expiringCount = resourceService.countExpiringResources(30);

        DashboardDTO.OverviewCard overview = DashboardDTO.OverviewCard.builder()
                .balance(latestBalance != null ? latestBalance.getBalance() : null)
                .dailyCost(latestBilling != null ? latestBilling.getDailyCost() : null)
                .monthlyCost(latestBilling != null ? latestBilling.getMonthlyCost() : null)
                .expiringCount(expiringCount)
                .currency(latestBalance != null ? latestBalance.getCurrency() : "CNY")
                .build();

        // --- 趋势数据 ---
        DashboardDTO.TrendData trend = buildTrendData();

        // --- 到期资源列表（默认第一页） ---
        DashboardDTO.ExpiringResourcePage expiringPage = buildExpiringResourcePage(null, 0, 10);

        DashboardDTO dashboard = DashboardDTO.builder()
                .overview(overview)
                .trend(trend)
                .expiringResources(expiringPage)
                .build();

        return ApiResponse.success(dashboard);
    }

    /**
     * 到期资源列表（分页+搜索）
     * GET /api/dashboard/expiring-resources?keyword=&page=0&size=10
     */
    @GetMapping("/expiring-resources")
    public ApiResponse<DashboardDTO.ExpiringResourcePage> getExpiringResources(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        DashboardDTO.ExpiringResourcePage result = buildExpiringResourcePage(keyword, page, size);
        return ApiResponse.success(result);
    }

    /**
     * 余额趋势数据（最近30天）
     * GET /api/dashboard/trend/balance?days=30
     */
    @GetMapping("/trend/balance")
    public ApiResponse<List<DashboardDTO.TrendPoint>> getBalanceTrend(
            @RequestParam(defaultValue = "30") int days) {
        List<BalanceHistory> history = balanceService.getTrend(days);
        List<DashboardDTO.TrendPoint> points = history.stream()
                .map(h -> DashboardDTO.TrendPoint.builder()
                        .date(h.getSyncTime().toLocalDate().toString())
                        .value(h.getBalance())
                        .build())
                .collect(Collectors.toList());
        return ApiResponse.success(points);
    }

    /**
     * 费用趋势数据（最近30天）
     * GET /api/dashboard/trend/billing?days=30
     */
    @GetMapping("/trend/billing")
    public ApiResponse<List<DashboardDTO.TrendPoint>> getBillingTrend(
            @RequestParam(defaultValue = "30") int days) {
        List<BillingHistory> history = billingService.getTrend(days);
        List<DashboardDTO.TrendPoint> points = history.stream()
                .map(h -> DashboardDTO.TrendPoint.builder()
                        .date(h.getSyncTime().toLocalDate().toString())
                        .value(h.getDailyCost())
                        .build())
                .collect(Collectors.toList());
        return ApiResponse.success(points);
    }

    /**
     * 告警规则管理 API
     */
    @GetMapping("/alarm-rules")
    public ApiResponse<List<AlarmRule>> getAlarmRules() {
        return ApiResponse.success(alarmService.listRules());
    }

    @PostMapping("/alarm-rules")
    public ApiResponse<AlarmRule> createAlarmRule(@RequestBody AlarmRule rule) {
        return ApiResponse.success(alarmService.createRule(rule));
    }

    @PutMapping("/alarm-rules/{id}")
    public ApiResponse<AlarmRule> updateAlarmRule(@PathVariable Long id, @RequestBody AlarmRule rule) {
        return ApiResponse.success(alarmService.updateRule(id, rule));
    }

    @DeleteMapping("/alarm-rules/{id}")
    public ApiResponse<Void> deleteAlarmRule(@PathVariable Long id) {
        alarmService.deleteRule(id);
        return ApiResponse.success(null);
    }

    // ---- private helpers ----

    private DashboardDTO.TrendData buildTrendData() {
        List<BalanceHistory> balanceHistory = balanceService.getTrend(30);
        List<BillingHistory> billingHistory = billingService.getTrend(30);

        List<DashboardDTO.TrendPoint> balanceTrend = balanceHistory.stream()
                .map(h -> DashboardDTO.TrendPoint.builder()
                        .date(h.getSyncTime().toLocalDate().toString())
                        .value(h.getBalance())
                        .build())
                .collect(Collectors.toList());

        List<DashboardDTO.TrendPoint> billingTrend = billingHistory.stream()
                .map(h -> DashboardDTO.TrendPoint.builder()
                        .date(h.getSyncTime().toLocalDate().toString())
                        .value(h.getDailyCost())
                        .build())
                .collect(Collectors.toList());

        return DashboardDTO.TrendData.builder()
                .balanceTrend(balanceTrend)
                .billingTrend(billingTrend)
                .build();
    }

    private DashboardDTO.ExpiringResourcePage buildExpiringResourcePage(String keyword, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "expireTime"));
        Page<ResourceInfo> resourcePage = resourceService.getExpiringResources(keyword, pageRequest);

        List<DashboardDTO.ExpiringResource> items = resourcePage.getContent().stream()
                .map(r -> DashboardDTO.ExpiringResource.builder()
                        .resourceId(r.getResourceId())
                        .resourceName(r.getResourceName())
                        .resourceType(r.getResourceType())
                        .region(r.getRegion())
                        .expireTime(r.getExpireTime() != null ? r.getExpireTime().toString() : null)
                        .remainingDays(r.getExpireTime() != null
                                ? ChronoUnit.DAYS.between(LocalDateTime.now(), r.getExpireTime())
                                : 0)
                        .build())
                .collect(Collectors.toList());

        return DashboardDTO.ExpiringResourcePage.builder()
                .items(items)
                .page(page)
                .size(size)
                .total(resourcePage.getTotalElements())
                .build();
    }
}

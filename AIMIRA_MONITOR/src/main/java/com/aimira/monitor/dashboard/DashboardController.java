package com.aimira.monitor.dashboard;

import com.aimira.monitor.alarm.AlarmScheduler;
import com.aimira.monitor.dto.ApiResponse;
import com.aimira.monitor.dto.DashboardDTO;
import com.aimira.monitor.entity.AlarmRecord;
import com.aimira.monitor.entity.AlarmRule;
import com.aimira.monitor.entity.BalanceHistory;
import com.aimira.monitor.entity.BillingHistory;
import com.aimira.monitor.entity.ResourceInfo;
import com.aimira.monitor.scheduler.CollectScheduler;
import com.aimira.monitor.service.AlarmService;
import com.aimira.monitor.service.BalanceService;
import com.aimira.monitor.service.BillingService;
import com.aimira.monitor.service.ResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "Dashboard", description = "首页概览、趋势数据、到期资源、告警规则管理")
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final BalanceService balanceService;
    private final BillingService billingService;
    private final ResourceService resourceService;
    private final AlarmService alarmService;
    private final CollectScheduler collectScheduler;
    private final AlarmScheduler alarmScheduler;

    public DashboardController(BalanceService balanceService,
                               BillingService billingService,
                               ResourceService resourceService,
                               AlarmService alarmService,
                               CollectScheduler collectScheduler,
                               AlarmScheduler alarmScheduler) {
        this.balanceService = balanceService;
        this.billingService = billingService;
        this.resourceService = resourceService;
        this.alarmService = alarmService;
        this.collectScheduler = collectScheduler;
        this.alarmScheduler = alarmScheduler;
    }

    /**
     * Dashboard 首页数据
     */
    @Operation(summary = "首页概览", description = "返回概览卡片（余额/费用/到期数）、30天趋势、到期资源第一页")
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
     */
    @Operation(summary = "到期资源列表", description = "分页查询即将到期的云资源，支持关键字搜索")
    @GetMapping("/expiring-resources")
    public ApiResponse<DashboardDTO.ExpiringResourcePage> getExpiringResources(
            @Parameter(description = "搜索关键字（匹配资源名称或ID）") @RequestParam(required = false) String keyword,
            @Parameter(description = "页码，从0开始") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size) {

        DashboardDTO.ExpiringResourcePage result = buildExpiringResourcePage(keyword, page, size);
        return ApiResponse.success(result);
    }

    /**
     * 余额趋势数据
     */
    @Operation(summary = "余额趋势", description = "返回最近N天的账户余额变化趋势")
    @GetMapping("/trend/balance")
    public ApiResponse<List<DashboardDTO.TrendPoint>> getBalanceTrend(
            @Parameter(description = "查询天数，默认30") @RequestParam(defaultValue = "30") int days) {
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
     * 费用趋势数据
     */
    @Operation(summary = "费用趋势", description = "返回最近N天的日费用变化趋势")
    @GetMapping("/trend/billing")
    public ApiResponse<List<DashboardDTO.TrendPoint>> getBillingTrend(
            @Parameter(description = "查询天数，默认30") @RequestParam(defaultValue = "30") int days) {
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
    @Operation(summary = "查询告警规则", description = "获取所有告警规则列表")
    @GetMapping("/alarm-rules")
    public ApiResponse<List<AlarmRule>> getAlarmRules() {
        return ApiResponse.success(alarmService.listRules());
    }

    @Operation(summary = "查询告警记录", description = "分页查询告警发送记录，可按时间倒序查看最近触发的告警")
    @GetMapping("/alarm-records")
    public ApiResponse<Page<AlarmRecord>> getAlarmRecords(
            @Parameter(description = "页码，从0开始") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "sentTime"));
        return ApiResponse.success(alarmService.listRecords(pageRequest));
    }

    @Operation(summary = "创建告警规则", description = "新增一条告警规则（BALANCE 或 EXPIRY 类型）")
    @PostMapping("/alarm-rules")
    public ApiResponse<AlarmRule> createAlarmRule(@RequestBody AlarmRule rule) {
        return ApiResponse.success(alarmService.createRule(rule));
    }

    @Operation(summary = "更新告警规则", description = "修改指定告警规则的阈值、操作符或启用状态")
    @PutMapping("/alarm-rules/{id}")
    public ApiResponse<AlarmRule> updateAlarmRule(
            @Parameter(description = "规则ID") @PathVariable Long id,
            @RequestBody AlarmRule rule) {
        return ApiResponse.success(alarmService.updateRule(id, rule));
    }

    @Operation(summary = "删除告警规则", description = "删除指定告警规则")
    @DeleteMapping("/alarm-rules/{id}")
    public ApiResponse<Void> deleteAlarmRule(
            @Parameter(description = "规则ID") @PathVariable Long id) {
        alarmService.deleteRule(id);
        return ApiResponse.success(null);
    }

    // ---- 手动触发 ----

    @Operation(summary = "手动触发数据采集", description = "立即执行一次数据采集（余额+账单+资源），无需等待定时任务")
    @PostMapping("/trigger/collect")
    public ApiResponse<String> triggerCollect() {
        collectScheduler.collectAll();
        return ApiResponse.success("数据采集已触发，请查看日志");
    }

    @Operation(summary = "手动触发告警检测", description = "立即执行一次告警检测，无需等待定时任务")
    @PostMapping("/trigger/alarm")
    public ApiResponse<String> triggerAlarm() {
        alarmScheduler.runAlarmDetection();
        return ApiResponse.success("告警检测已触发，请查看日志");
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

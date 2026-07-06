package com.aimira.monitor.controller;

import com.aimira.monitor.dto.ApiResponse;
import com.aimira.monitor.entity.BalanceHistory;
import com.aimira.monitor.entity.BillingHistory;
import com.aimira.monitor.entity.ResourceInfo;
import com.aimira.monitor.service.BalanceService;
import com.aimira.monitor.service.BillingService;
import com.aimira.monitor.service.ResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 数据查询接口 — 以 API 形式返回采集到的阿里云资源、余额、账单数据
 */
@Tag(name = "DataQuery", description = "资源/余额/账单数据查询，支持分页、筛选、统计")
@RestController
@RequestMapping("/api/query")
public class DataQueryController {

    private final ResourceService resourceService;
    private final BalanceService balanceService;
    private final BillingService billingService;

    public DataQueryController(ResourceService resourceService,
                               BalanceService balanceService,
                               BillingService billingService) {
        this.resourceService = resourceService;
        this.balanceService = balanceService;
        this.billingService = billingService;
    }

    // ==================== 资源查询 ====================

    /**
     * 多条件分页查询资源列表
     */
    @Operation(summary = "查询资源列表", description = "多条件分页查询云资源，支持关键字（名称/ID）、类型、地域、状态、云厂商过滤")
    @GetMapping("/resources")
    public ApiResponse<Page<ResourceInfo>> queryResources(
            @Parameter(description = "搜索关键字（匹配资源名称或ID）")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "资源类型：ECS / SWAS / RDS")
            @RequestParam(required = false) String resourceType,
            @Parameter(description = "地域，如 cn-hangzhou")
            @RequestParam(required = false) String region,
            @Parameter(description = "状态，如 Running / Stopped")
            @RequestParam(required = false) String status,
            @Parameter(description = "云厂商标识（ALIYUN / VOLCENGINE），不传默认全量")
            @RequestParam(required = false) String cloudProvider,
            @Parameter(description = "页码，从0开始")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "排序字段，默认 syncTime")
            @RequestParam(defaultValue = "syncTime") String sortBy,
            @Parameter(description = "排序方向：asc / desc")
            @RequestParam(defaultValue = "desc") String sortDir) {

        Sort sort = Sort.by("desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy);
        PageRequest pageRequest = PageRequest.of(page, size, sort);
        Page<ResourceInfo> result = resourceService.searchResources(keyword, resourceType, region, status, cloudProvider, pageRequest);
        return ApiResponse.success(result);
    }

    /**
     * 资源统计：按类型和地域分组计数
     */
    @Operation(summary = "资源统计", description = "返回按资源类型和地域分组的数量统计，支持按云厂商筛选")
    @GetMapping("/resources/stats")
    public ApiResponse<Map<String, Object>> getResourceStats(
            @Parameter(description = "云厂商标识（ALIYUN / VOLCENGINE），不传默认全量")
            @RequestParam(required = false) String cloudProvider) {
        Map<String, Long> byType = resourceService.countByResourceType(cloudProvider).stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (Long) row[1]
                ));
        Map<String, Long> byRegion = resourceService.countByRegion(cloudProvider).stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (Long) row[1]
                ));

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("byType", byType);
        stats.put("byRegion", byRegion);
        return ApiResponse.success(stats);
    }

    // ==================== 余额查询 ====================

    /**
     * 分页查询余额历史
     */
    @Operation(summary = "查询余额历史", description = "分页查询账户余额历史记录，可按时间范围和云厂商筛选")
    @GetMapping("/balances")
    public ApiResponse<Page<BalanceHistory>> queryBalances(
            @Parameter(description = "开始时间（ISO格式），如 2026-06-01T00:00:00")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @Parameter(description = "结束时间（ISO格式），如 2026-07-01T23:59:59")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @Parameter(description = "云厂商标识（ALIYUN / VOLCENGINE），不传默认全量")
            @RequestParam(required = false) String cloudProvider,
            @Parameter(description = "页码，从0开始")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小")
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageRequest = PageRequest.of(page, size);
        Page<BalanceHistory> result;
        if (cloudProvider != null && start != null && end != null) {
            result = balanceService.findByTimeRangeAndCloudProvider(start, end, cloudProvider, pageRequest);
        } else if (cloudProvider != null) {
            result = balanceService.findAllByCloudProvider(cloudProvider, pageRequest);
        } else if (start != null && end != null) {
            result = balanceService.findByTimeRange(start, end, pageRequest);
        } else {
            result = balanceService.findAll(pageRequest);
        }
        return ApiResponse.success(result);
    }

    // ==================== 账单查询 ====================

    /**
     * 分页查询费用历史
     */
    @Operation(summary = "查询费用历史", description = "分页查询账单费用历史记录，可按时间范围和云厂商筛选")
    @GetMapping("/billings")
    public ApiResponse<Page<BillingHistory>> queryBillings(
            @Parameter(description = "开始时间（ISO格式），如 2026-06-01T00:00:00")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @Parameter(description = "结束时间（ISO格式），如 2026-07-01T23:59:59")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @Parameter(description = "云厂商标识（ALIYUN / VOLCENGINE），不传默认全量")
            @RequestParam(required = false) String cloudProvider,
            @Parameter(description = "页码，从0开始")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小")
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageRequest = PageRequest.of(page, size);
        Page<BillingHistory> result;
        if (cloudProvider != null && start != null && end != null) {
            result = billingService.findByTimeRangeAndCloudProvider(start, end, cloudProvider, pageRequest);
        } else if (cloudProvider != null) {
            result = billingService.findAllByCloudProvider(cloudProvider, pageRequest);
        } else if (start != null && end != null) {
            result = billingService.findByTimeRange(start, end, pageRequest);
        } else {
            result = billingService.findAll(pageRequest);
        }
        return ApiResponse.success(result);
    }
}

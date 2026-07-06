package com.aimira.monitor.cloud.volcengine;

import com.aimira.monitor.cloud.CloudCollector;
import com.aimira.monitor.config.VolcengineConfig;
import com.aimira.monitor.entity.BalanceHistory;
import com.aimira.monitor.entity.BillingHistory;
import com.aimira.monitor.entity.ResourceInfo;
import com.volcengine.ApiClient;
import com.volcengine.ApiException;
import com.volcengine.billing.BillingApi;
import com.volcengine.billing.model.ListBillRequest;
import com.volcengine.billing.model.ListBillResponse;
import com.volcengine.billing.model.ListForListBillOutput;
import com.volcengine.billing.model.QueryBalanceAcctRequest;
import com.volcengine.billing.model.QueryBalanceAcctResponse;
import com.volcengine.ecs.EcsApi;
import com.volcengine.ecs.model.DescribeInstancesRequest;
import com.volcengine.ecs.model.DescribeInstancesResponse;
import com.volcengine.ecs.model.InstanceForDescribeInstancesOutput;
import com.volcengine.rdsmysqlv2.RdsMysqlV2Api;
import com.volcengine.rdsmysqlv2.model.DescribeDBInstancesRequest;
import com.volcengine.rdsmysqlv2.model.DescribeDBInstancesResponse;
import com.volcengine.rdsmysqlv2.model.InstanceForDescribeDBInstancesOutput;
import com.volcengine.sign.Credentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 火山云采集器 — 全部采集逻辑内聚在此。
 * 调用火山引擎 Java SDK（按服务模块拆分），转换后写入 Entity（cloudProvider = "VOLCENGINE"）。
 */
@Slf4j
@Order(2)
@Component
public class VolcengineCollector implements CloudCollector {

    private final VolcengineConfig config;

    public VolcengineCollector(VolcengineConfig config) {
        this.config = config;
    }

    @Override
    public String getCloudProvider() {
        return "VOLCENGINE";
    }

    @Override
    public boolean isEnabled() {
        return config.isEnabled();
    }

    // ---- 余额采集 ----

    @Override
    public Optional<BalanceHistory> collectBalance() {
        try {
            ApiClient client = buildApiClient(defaultRegion());
            BillingApi billingApi = new BillingApi(client);
            QueryBalanceAcctResponse response = billingApi.queryBalanceAcct(new QueryBalanceAcctRequest());

            BigDecimal cashBalance = parseDecimal(response.getCashBalance());
            BigDecimal creditLimit = parseDecimal(response.getCreditLimit());
            BigDecimal availableBalance = parseDecimal(response.getAvailableBalance());

            return Optional.of(BalanceHistory.builder()
                    .balance(cashBalance.add(creditLimit))
                    .availableAmount(availableBalance)
                    .currency("CNY")
                    .cloudProvider("VOLCENGINE")
                    .syncTime(LocalDateTime.now())
                    .build());
        } catch (ApiException e) {
            log.error("[VOLCENGINE] 余额采集失败: {}", e.getResponseBody(), e);
            return Optional.empty();
        } catch (Exception e) {
            log.error("[VOLCENGINE] 余额采集失败: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    // ---- 费用采集 ----

    @Override
    public Optional<BillingHistory> collectBilling() {
        try {
            ApiClient client = buildApiClient(defaultRegion());
            BillingApi billingApi = new BillingApi(client);

            String billPeriod = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            ListBillRequest request = new ListBillRequest()
                    .billPeriod(billPeriod)
                    .needRecordNum(1)
                    .ignoreZero(0);
            ListBillResponse response = billingApi.listBill(request);

            BigDecimal monthlyCost = BigDecimal.ZERO;
            if (response.getList() != null) {
                for (ListForListBillOutput bill : response.getList()) {
                    monthlyCost = monthlyCost.add(parseDecimal(bill.getOriginalBillAmount()));
                }
            }

            int daysInMonth = LocalDateTime.now().toLocalDate().lengthOfMonth();
            BigDecimal dailyCost = daysInMonth > 0
                    ? monthlyCost.divide(BigDecimal.valueOf(daysInMonth), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            return Optional.of(BillingHistory.builder()
                    .dailyCost(dailyCost)
                    .monthlyCost(monthlyCost)
                    .currency("CNY")
                    .cloudProvider("VOLCENGINE")
                    .syncTime(LocalDateTime.now())
                    .build());
        } catch (ApiException e) {
            log.error("[VOLCENGINE] 费用采集失败: {}", e.getResponseBody(), e);
            return Optional.empty();
        } catch (Exception e) {
            log.error("[VOLCENGINE] 费用采集失败: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    // ---- 资源采集 ----

    @Override
    public List<ResourceInfo> collectResources() {
        List<ResourceInfo> allResources = new ArrayList<>();

        for (String region : config.getEffectiveRegions()) {
            try {
                allResources.addAll(collectEcsInstances(region));
            } catch (ApiException e) {
                log.error("[VOLCENGINE] ECS 采集失败 region={}: {}", region, e.getResponseBody(), e);
            } catch (Exception e) {
                log.error("[VOLCENGINE] ECS 采集失败 region={}: {}", region, e.getMessage(), e);
            }

            try {
                allResources.addAll(collectRdsInstances(region));
            } catch (ApiException e) {
                log.error("[VOLCENGINE] RDS 采集失败 region={}: {}", region, e.getResponseBody(), e);
            } catch (Exception e) {
                log.error("[VOLCENGINE] RDS 采集失败 region={}: {}", region, e.getMessage(), e);
            }
        }

        return allResources;
    }

    private List<ResourceInfo> collectEcsInstances(String region) throws ApiException {
        ApiClient client = buildApiClient(region);
        EcsApi ecsApi = new EcsApi(client);

        DescribeInstancesRequest request = new DescribeInstancesRequest();
        request.setMaxResults(100);
        DescribeInstancesResponse response = ecsApi.describeInstances(request);

        List<ResourceInfo> resources = new ArrayList<>();
        if (response.getInstances() != null) {
            for (InstanceForDescribeInstancesOutput instance : response.getInstances()) {
                resources.add(toResourceInfo(instance, region));
            }
        }
        log.info("[VOLCENGINE] ECS region={} 采集到 {} 个实例", region, resources.size());
        return resources;
    }

    private List<ResourceInfo> collectRdsInstances(String region) throws ApiException {
        ApiClient client = buildApiClient(region);
        RdsMysqlV2Api rdsApi = new RdsMysqlV2Api(client);

        DescribeDBInstancesRequest request = new DescribeDBInstancesRequest()
                .pageNumber(1)
                .pageSize(100);
        DescribeDBInstancesResponse response = rdsApi.describeDBInstances(request);

        List<ResourceInfo> resources = new ArrayList<>();
        if (response.getInstances() != null) {
            for (InstanceForDescribeDBInstancesOutput instance : response.getInstances()) {
                resources.add(toResourceInfo(instance, region));
            }
        }
        log.info("[VOLCENGINE] RDS region={} 采集到 {} 个实例", region, resources.size());
        return resources;
    }

    // ---- 转换方法 ----

    /**
     * ECS 实例 → ResourceInfo。
     * 字段映射以火山云 ECS API 契约表为准。
     */
    private ResourceInfo toResourceInfo(InstanceForDescribeInstancesOutput raw, String region) {
        return ResourceInfo.builder()
                .resourceId(raw.getInstanceId())
                .resourceName(raw.getInstanceName())
                .resourceType("ECS")
                .region(region)
                .status(raw.getStatus())
                .expireTime(parseTime(raw.getExpiredAt()))
                .cloudProvider("VOLCENGINE")
                .syncTime(LocalDateTime.now())
                .build();
    }

    /**
     * RDS 实例 → ResourceInfo。
     * 字段映射以火山云 RDS MySQL API 契约表为准。
     */
    private ResourceInfo toResourceInfo(InstanceForDescribeDBInstancesOutput raw, String region) {
        String expireTimeStr = raw.getChargeDetail() != null
                ? raw.getChargeDetail().getChargeEndTime()
                : null;
        return ResourceInfo.builder()
                .resourceId(raw.getInstanceId())
                .resourceName(raw.getInstanceName())
                .resourceType("RDS")
                .region(region)
                .status(raw.getInstanceStatus())
                .expireTime(parseTime(expireTimeStr))
                .cloudProvider("VOLCENGINE")
                .syncTime(LocalDateTime.now())
                .build();
    }

    // ---- 工具方法 ----

    private String defaultRegion() {
        List<String> regions = config.getEffectiveRegions();
        return regions.isEmpty() ? "cn-beijing" : regions.get(0);
    }

    private ApiClient buildApiClient(String region) {
        ApiClient client = new ApiClient();
        client.setCredentials(Credentials.getCredentials(config.getAccessKey(), config.getSecret()));
        client.setRegion(region);
        return client;
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private LocalDateTime parseTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (DateTimeFormatter fmt : TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(raw, fmt);
            } catch (DateTimeParseException ignored) {
            }
        }
        log.warn("[VOLCENGINE] 无法解析时间字符串: {}", raw);
        return null;
    }

    private static final List<DateTimeFormatter> TIME_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME
    );
}

package com.aimira.monitor.cloud;

import com.aimira.monitor.collector.BalanceCollector;
import com.aimira.monitor.collector.BillingCollector;
import com.aimira.monitor.collector.ResourceCollector;
import com.aimira.monitor.config.AliyunConfig;
import com.aimira.monitor.entity.BalanceHistory;
import com.aimira.monitor.entity.BillingHistory;
import com.aimira.monitor.entity.ResourceInfo;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 阿里云采集器包装 — 委托现有三个 Collector，仅做 cloudProvider 写入。
 * 现有 collector/ 包内逻辑一行不改。
 */
@Order(1)
@Component
public class AliyunCollector implements CloudCollector {

    private final AliyunConfig aliyunConfig;
    private final BalanceCollector balanceCollector;
    private final BillingCollector billingCollector;
    private final ResourceCollector resourceCollector;

    public AliyunCollector(AliyunConfig aliyunConfig,
                           BalanceCollector balanceCollector,
                           BillingCollector billingCollector,
                           ResourceCollector resourceCollector) {
        this.aliyunConfig = aliyunConfig;
        this.balanceCollector = balanceCollector;
        this.billingCollector = billingCollector;
        this.resourceCollector = resourceCollector;
    }

    @Override
    public String getCloudProvider() {
        return "ALIYUN";
    }

    @Override
    public boolean isEnabled() {
        return aliyunConfig.isCollectorEnabled();
    }

    @Override
    public Optional<BalanceHistory> collectBalance() {
        return balanceCollector.collect()
                .map(h -> {
                    h.setCloudProvider("ALIYUN");
                    return h;
                });
    }

    @Override
    public Optional<BillingHistory> collectBilling() {
        return billingCollector.collect()
                .map(h -> {
                    h.setCloudProvider("ALIYUN");
                    return h;
                });
    }

    @Override
    public List<ResourceInfo> collectResources() {
        return resourceCollector.collectAll().stream()
                .map(r -> {
                    r.setCloudProvider("ALIYUN");
                    return r;
                })
                .toList();
    }
}

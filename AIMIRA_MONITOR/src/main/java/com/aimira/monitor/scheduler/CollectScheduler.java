package com.aimira.monitor.scheduler;

import com.aimira.monitor.cloud.CloudCollector;
import com.aimira.monitor.service.BalanceService;
import com.aimira.monitor.service.BillingService;
import com.aimira.monitor.service.ResourceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 多云数据采集定时任务。
 * 遍历所有 CloudCollector 实现，每个云独立 try/catch，单云故障不影响其他云。
 */
@Slf4j
@Component
public class CollectScheduler {

    private final List<CloudCollector> collectors;
    private final BalanceService balanceService;
    private final BillingService billingService;
    private final ResourceService resourceService;

    public CollectScheduler(List<CloudCollector> collectors,
                            BalanceService balanceService,
                            BillingService billingService,
                            ResourceService resourceService) {
        this.collectors = collectors;
        this.balanceService = balanceService;
        this.billingService = billingService;
        this.resourceService = resourceService;
    }

    /**
     * 定时采集所有已启用云厂商的数据（默认每小时）
     */
    @Scheduled(cron = "${scheduler.collect-cron:0 0 * * * *}")
    public void collectAll() {
        log.info("===== 多云采集开始, 共 {} 个云厂商 =====", collectors.size());

        for (CloudCollector c : collectors) {
            if (!c.isEnabled()) {
                log.info("[{}] 采集已禁用，跳过", c.getCloudProvider());
                continue;
            }
            collectFromCloud(c);
        }

        log.info("===== 多云采集完成 =====");
    }

    /**
     * 单云采集。只 catch Exception（不 catch Error，OOM 等应让 JVM 暴露）。
     * 单云失败不影响其他云。
     */
    private void collectFromCloud(CloudCollector collector) {
        String provider = collector.getCloudProvider();
        log.info("===== [{}] 采集开始 =====", provider);
        long start = System.currentTimeMillis();

        try {
            collector.collectBalance().ifPresent(balanceService::save);
        } catch (Exception e) {
            log.error("[{}] 余额采集失败（不影响其他操作）: {}", provider, e.getMessage(), e);
        }

        try {
            collector.collectBilling().ifPresent(billingService::save);
        } catch (Exception e) {
            log.error("[{}] 费用采集失败（不影响其他操作）: {}", provider, e.getMessage(), e);
        }

        try {
            resourceService.saveOrUpdateBatch(collector.collectResources());
        } catch (Exception e) {
            log.error("[{}] 资源采集失败（不影响其他操作）: {}", provider, e.getMessage(), e);
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("===== [{}] 采集完成, 耗时: {}ms =====", provider, elapsed);
    }
}

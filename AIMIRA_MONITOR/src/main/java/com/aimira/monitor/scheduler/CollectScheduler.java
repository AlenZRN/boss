package com.aimira.monitor.scheduler;

import com.aimira.monitor.collector.BalanceCollector;
import com.aimira.monitor.collector.BillingCollector;
import com.aimira.monitor.collector.ResourceCollector;
import com.aimira.monitor.entity.BalanceHistory;
import com.aimira.monitor.entity.BillingHistory;
import com.aimira.monitor.entity.ResourceInfo;
import com.aimira.monitor.service.BalanceService;
import com.aimira.monitor.service.BillingService;
import com.aimira.monitor.service.ResourceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 数据采集定时任务
 * 默认每小时执行一次
 */
@Slf4j
@Component
public class CollectScheduler {

    private final BalanceCollector balanceCollector;
    private final BillingCollector billingCollector;
    private final ResourceCollector resourceCollector;
    private final BalanceService balanceService;
    private final BillingService billingService;
    private final ResourceService resourceService;

    public CollectScheduler(BalanceCollector balanceCollector,
                            BillingCollector billingCollector,
                            ResourceCollector resourceCollector,
                            BalanceService balanceService,
                            BillingService billingService,
                            ResourceService resourceService) {
        this.balanceCollector = balanceCollector;
        this.billingCollector = billingCollector;
        this.resourceCollector = resourceCollector;
        this.balanceService = balanceService;
        this.billingService = billingService;
        this.resourceService = resourceService;
    }

    /**
     * 定时采集所有数据（默认每小时）
     */
    @Scheduled(cron = "${scheduler.collect-cron:0 0 * * * *}")
    public void collectAll() {
        log.info("===== 定时数据采集开始 =====");
        long startTime = System.currentTimeMillis();

        collectBalance();
        collectBilling();
        collectResources();

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("===== 定时数据采集完成, 耗时: {}ms =====", elapsed);
    }

    private void collectBalance() {
        try {
            Optional<BalanceHistory> result = balanceCollector.collect();
            result.ifPresent(balanceService::save);
        } catch (Exception e) {
            log.error("余额采集任务异常", e);
        }
    }

    private void collectBilling() {
        try {
            Optional<BillingHistory> result = billingCollector.collect();
            result.ifPresent(billingService::save);
        } catch (Exception e) {
            log.error("费用采集任务异常", e);
        }
    }

    private void collectResources() {
        try {
            List<ResourceInfo> resources = resourceCollector.collectAll();
            resourceService.saveOrUpdateBatch(resources);
        } catch (Exception e) {
            log.error("资源采集任务异常", e);
        }
    }
}

package com.aimira.monitor.cloud;

import com.aimira.monitor.entity.BalanceHistory;
import com.aimira.monitor.entity.BillingHistory;
import com.aimira.monitor.entity.ResourceInfo;

import java.util.List;
import java.util.Optional;

/**
 * 云厂商采集器统一接口。
 * 每个云厂商实现此接口，内聚采集逻辑 + 转换逻辑。
 */
public interface CloudCollector {

    /** 云厂商标识，如 "ALIYUN"、"VOLCENGINE" */
    String getCloudProvider();

    /** 是否启用该云厂商采集（可通过配置开关） */
    boolean isEnabled();

    /** 采集账户余额 */
    Optional<BalanceHistory> collectBalance();

    /** 采集费用信息 */
    Optional<BillingHistory> collectBilling();

    /** 采集云资源列表 */
    List<ResourceInfo> collectResources();
}

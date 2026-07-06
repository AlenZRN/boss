package com.aimira.monitor.metrics;

import com.aimira.monitor.entity.BalanceHistory;
import com.aimira.monitor.repository.BalanceHistoryRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 暴露多云账户余额 Prometheus 指标。
 * <p>
 * 每个云厂商注册两个 Gauge：
 * <ul>
 *   <li>{@code aimira_cloud_balance} — 当前余额</li>
 *   <li>{@code aimira_cloud_available_amount} — 可用金额</li>
 * </ul>
 * 标签：{@code cloud_provider}、{@code currency}。
 * <p>
 * Gauge 的值函数从内存快照中惰性读取，Prometheus 每次 scrape 时拿到最新值。
 */
@Slf4j
@Component
public class BalanceMetrics {

    private final MeterRegistry meterRegistry;
    private final BalanceHistoryRepository balanceHistoryRepository;

    /** 每个云厂商的最新余额快照（线程安全） */
    private final ConcurrentHashMap<String, BalanceSnapshot> snapshots = new ConcurrentHashMap<>();

    /** 已注册 Gauge 的云厂商集合，避免重复注册 */
    private final Set<String> registeredProviders = ConcurrentHashMap.newKeySet();

    public BalanceMetrics(MeterRegistry meterRegistry,
                          BalanceHistoryRepository balanceHistoryRepository) {
        this.meterRegistry = meterRegistry;
        this.balanceHistoryRepository = balanceHistoryRepository;
    }

    /**
     * 应用启动后从数据库加载最新余额，初始化 Prometheus 指标。
     * 这样 Prometheus 在第一次 scrape 时就能拿到数据（即使采集任务尚未运行）。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initFromDatabase() {
        var providers = balanceHistoryRepository.findDistinctCloudProviders();
        log.info("从数据库初始化余额指标, 云厂商: {}", providers);
        for (String provider : providers) {
            balanceHistoryRepository.findTopByCloudProviderOrderBySyncTimeDesc(provider)
                    .ifPresent(this::recordBalance);
        }
    }

    /**
     * 采集器保存余额后调用此方法，更新内存快照并推送到 Prometheus。
     *
     * @param history 已持久化的 BalanceHistory（必须含 cloudProvider、balance、availableAmount、currency）
     */
    public void recordBalance(BalanceHistory history) {
        String provider = history.getCloudProvider();
        if (provider == null || provider.isBlank()) {
            log.warn("跳过余额指标记录，cloudProvider 为空");
            return;
        }

        String currency = history.getCurrency() != null ? history.getCurrency() : "CNY";
        Tags tags = Tags.of("cloud_provider", provider, "currency", currency);

        // 更新内存快照
        snapshots.put(provider, new BalanceSnapshot(
                provider,
                history.getBalance(),
                history.getAvailableAmount()
        ));

        // 首次遇到该云厂商时注册 Gauge
        if (registeredProviders.add(provider)) {
            registerGauges(provider, tags);
            log.info("已注册 Prometheus 余额指标: provider={}, currency={}", provider, currency);
        }

        log.debug("余额指标已更新: provider={}, balance={}, availableAmount={}",
                provider, history.getBalance(), history.getAvailableAmount());
    }

    private void registerGauges(String provider, Tags tags) {
        Gauge.builder("aimira.cloud.balance", snapshots,
                map -> {
                    BalanceSnapshot s = map.get(provider);
                    return s != null ? toDouble(s.balance) : Double.NaN;
                })
                .tags(tags)
                .description("Current cloud account balance")
                .register(meterRegistry);

        Gauge.builder("aimira.cloud.available.amount", snapshots,
                map -> {
                    BalanceSnapshot s = map.get(provider);
                    return s != null ? toDouble(s.availableAmount) : Double.NaN;
                })
                .tags(tags)
                .description("Current cloud available amount")
                .register(meterRegistry);
    }

    private static double toDouble(BigDecimal value) {
        return value != null ? value.doubleValue() : Double.NaN;
    }

    /**
     * 余额快照（不可变）。
     */
    private record BalanceSnapshot(String provider, BigDecimal balance, BigDecimal availableAmount) {
    }
}

package com.aimira.monitor.service;

import com.aimira.monitor.entity.BillingHistory;
import com.aimira.monitor.repository.BillingHistoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class BillingService {

    private final BillingHistoryRepository billingHistoryRepository;

    public BillingService(BillingHistoryRepository billingHistoryRepository) {
        this.billingHistoryRepository = billingHistoryRepository;
    }

    /** 保存费用历史记录 */
    public BillingHistory save(BillingHistory history) {
        return billingHistoryRepository.save(history);
    }

    /** 获取最新费用 */
    public BillingHistory getLatest() {
        return billingHistoryRepository.findTopByOrderBySyncTimeDesc().orElse(null);
    }

    /** 获取最近 N 天的费用趋势 */
    public List<BillingHistory> getTrend(int days) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(days);
        return billingHistoryRepository.findBySyncTimeBetween(start, end);
    }

    /** 分页查询费用历史（按同步时间倒序） */
    public Page<BillingHistory> findAll(Pageable pageable) {
        return billingHistoryRepository.findAllByOrderBySyncTimeDesc(pageable);
    }

    /** 按时间范围分页查询费用历史 */
    public Page<BillingHistory> findByTimeRange(LocalDateTime start, LocalDateTime end, Pageable pageable) {
        return billingHistoryRepository.findBySyncTimeBetween(start, end, pageable);
    }
}

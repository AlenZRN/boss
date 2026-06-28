package com.aimira.monitor.service;

import com.aimira.monitor.entity.BalanceHistory;
import com.aimira.monitor.repository.BalanceHistoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class BalanceService {

    private final BalanceHistoryRepository balanceHistoryRepository;

    public BalanceService(BalanceHistoryRepository balanceHistoryRepository) {
        this.balanceHistoryRepository = balanceHistoryRepository;
    }

    /** 保存余额历史记录 */
    public BalanceHistory save(BalanceHistory history) {
        return balanceHistoryRepository.save(history);
    }

    /** 获取最新余额 */
    public BalanceHistory getLatest() {
        return balanceHistoryRepository.findTopByOrderBySyncTimeDesc().orElse(null);
    }

    /** 获取最近 N 天的余额趋势 */
    public List<BalanceHistory> getTrend(int days) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(days);
        return balanceHistoryRepository.findBySyncTimeBetween(start, end);
    }
}

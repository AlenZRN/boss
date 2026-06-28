package com.aimira.monitor.repository;

import com.aimira.monitor.entity.BillingHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BillingHistoryRepository extends JpaRepository<BillingHistory, Long> {

    /** 获取最新一条费用记录 */
    Optional<BillingHistory> findTopByOrderBySyncTimeDesc();

    /** 获取指定时间范围内的费用历史（用于趋势图） */
    @Query("SELECT b FROM BillingHistory b WHERE b.syncTime BETWEEN :start AND :end ORDER BY b.syncTime ASC")
    List<BillingHistory> findBySyncTimeBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}

package com.aimira.monitor.repository;

import com.aimira.monitor.entity.BalanceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BalanceHistoryRepository extends JpaRepository<BalanceHistory, Long> {

    /** 获取最新一条余额记录 */
    Optional<BalanceHistory> findTopByOrderBySyncTimeDesc();

    /** 获取指定时间范围内的余额历史（用于趋势图） */
    @Query("SELECT b FROM BalanceHistory b WHERE b.syncTime BETWEEN :start AND :end ORDER BY b.syncTime ASC")
    List<BalanceHistory> findBySyncTimeBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}

package com.aimira.monitor.repository;

import com.aimira.monitor.entity.BillingHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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

    /** 获取费用历史（分页，按同步时间倒序） */
    @Query("SELECT b FROM BillingHistory b ORDER BY b.syncTime DESC")
    Page<BillingHistory> findAllByOrderBySyncTimeDesc(Pageable pageable);

    /** 按时间范围分页查询费用历史 */
    @Query("SELECT b FROM BillingHistory b WHERE b.syncTime BETWEEN :start AND :end ORDER BY b.syncTime DESC")
    Page<BillingHistory> findBySyncTimeBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, Pageable pageable);

    /** 按云厂商获取最新一条费用记录 */
    Optional<BillingHistory> findTopByCloudProviderOrderBySyncTimeDesc(String cloudProvider);

    /** 按云厂商获取指定时间范围内的费用历史（用于趋势图） */
    @Query("SELECT b FROM BillingHistory b WHERE b.cloudProvider = :cloudProvider AND b.syncTime BETWEEN :start AND :end ORDER BY b.syncTime ASC")
    List<BillingHistory> findByCloudProviderAndSyncTimeBetween(@Param("cloudProvider") String cloudProvider, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 按云厂商分页查询费用历史（按同步时间倒序） */
    @Query("SELECT b FROM BillingHistory b WHERE b.cloudProvider = :cloudProvider ORDER BY b.syncTime DESC")
    Page<BillingHistory> findAllByCloudProviderOrderBySyncTimeDesc(@Param("cloudProvider") String cloudProvider, Pageable pageable);

    /** 按云厂商 + 时间范围分页查询费用历史 */
    @Query("SELECT b FROM BillingHistory b WHERE b.cloudProvider = :cloudProvider AND b.syncTime BETWEEN :start AND :end ORDER BY b.syncTime DESC")
    Page<BillingHistory> findByCloudProviderAndSyncTimeBetween(@Param("cloudProvider") String cloudProvider, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end, Pageable pageable);
}

package com.aimira.monitor.repository;

import com.aimira.monitor.entity.AlarmRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface AlarmRecordRepository extends JpaRepository<AlarmRecord, Long> {

    /** 查找冷却时间内是否已发送过相同规则的告警（去重） */
    @Query("SELECT COUNT(a) > 0 FROM AlarmRecord a WHERE a.ruleId = :ruleId " +
           "AND (:resourceId IS NULL AND a.resourceId IS NULL OR a.resourceId = :resourceId) " +
           "AND a.sentTime >= :since AND a.status = 'SUCCESS'")
    boolean existsWithinCooldown(@Param("ruleId") Long ruleId,
                                 @Param("resourceId") String resourceId,
                                 @Param("since") LocalDateTime since);
}

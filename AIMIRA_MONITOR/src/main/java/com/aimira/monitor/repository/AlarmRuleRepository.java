package com.aimira.monitor.repository;

import com.aimira.monitor.entity.AlarmRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlarmRuleRepository extends JpaRepository<AlarmRule, Long> {

    /** 查找所有启用的告警规则 */
    List<AlarmRule> findByEnabledTrue();

    /** 按告警类型查找启用的规则 */
    List<AlarmRule> findByAlarmTypeAndEnabledTrue(String alarmType);
}

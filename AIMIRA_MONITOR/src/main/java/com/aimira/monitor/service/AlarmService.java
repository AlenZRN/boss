package com.aimira.monitor.service;

import com.aimira.monitor.entity.AlarmRule;
import com.aimira.monitor.entity.AlarmRecord;
import com.aimira.monitor.repository.AlarmRuleRepository;
import com.aimira.monitor.repository.AlarmRecordRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AlarmService {

    private final AlarmRuleRepository alarmRuleRepository;
    private final AlarmRecordRepository alarmRecordRepository;

    public AlarmService(AlarmRuleRepository alarmRuleRepository,
                        AlarmRecordRepository alarmRecordRepository) {
        this.alarmRuleRepository = alarmRuleRepository;
        this.alarmRecordRepository = alarmRecordRepository;
    }

    // ---- 告警规则 CRUD ----

    public List<AlarmRule> listRules() {
        return alarmRuleRepository.findAll();
    }

    public List<AlarmRule> listEnabledRules() {
        return alarmRuleRepository.findByEnabledTrue();
    }

    public AlarmRule createRule(AlarmRule rule) {
        return alarmRuleRepository.save(rule);
    }

    public AlarmRule updateRule(Long id, AlarmRule rule) {
        rule.setId(id);
        return alarmRuleRepository.save(rule);
    }

    public void deleteRule(Long id) {
        alarmRuleRepository.deleteById(id);
    }

    // ---- 告警记录查询 ----

    public Page<AlarmRecord> listRecords(Pageable pageable) {
        return alarmRecordRepository.findAll(pageable);
    }
}

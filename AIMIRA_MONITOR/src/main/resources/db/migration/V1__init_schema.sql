-- =============================================
-- V1: 初始化数据库表结构 (PostgreSQL)
-- =============================================

-- 账户余额历史
CREATE TABLE IF NOT EXISTS balance_history (
    id              BIGSERIAL       PRIMARY KEY,
    balance         NUMERIC(18,2)   NOT NULL,
    available_amount NUMERIC(18,2),
    currency        VARCHAR(10),
    sync_time       TIMESTAMP       NOT NULL,
    created_at      TIMESTAMP       DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_balance_sync_time ON balance_history(sync_time);

-- 费用历史
CREATE TABLE IF NOT EXISTS billing_history (
    id              BIGSERIAL       PRIMARY KEY,
    daily_cost      NUMERIC(18,2)   NOT NULL,
    monthly_cost    NUMERIC(18,2),
    currency        VARCHAR(10),
    bill_date       TIMESTAMP       NOT NULL,
    sync_time       TIMESTAMP       NOT NULL,
    created_at      TIMESTAMP       DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_billing_sync_time ON billing_history(sync_time);
CREATE INDEX IF NOT EXISTS idx_billing_bill_date ON billing_history(bill_date);

-- 资源信息
CREATE TABLE IF NOT EXISTS resource_info (
    id              BIGSERIAL       PRIMARY KEY,
    resource_id     VARCHAR(128)    NOT NULL,
    resource_name   VARCHAR(256),
    resource_type   VARCHAR(64)     NOT NULL,
    region          VARCHAR(64)     NOT NULL,
    status          VARCHAR(32),
    expire_time     TIMESTAMP,
    sync_time       TIMESTAMP       NOT NULL,
    created_at      TIMESTAMP       DEFAULT NOW(),
    updated_at      TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_res_type ON resource_info(resource_type);
CREATE INDEX IF NOT EXISTS idx_res_expire_time ON resource_info(expire_time);
CREATE INDEX IF NOT EXISTS idx_res_status ON resource_info(status);

-- 告警规则
CREATE TABLE IF NOT EXISTS alarm_rule (
    id              BIGSERIAL       PRIMARY KEY,
    rule_name       VARCHAR(128)    NOT NULL,
    alarm_type      VARCHAR(32)     NOT NULL,
    threshold       NUMERIC(18,2),
    operator        VARCHAR(32),
    enabled         BOOLEAN         DEFAULT TRUE,
    created_at      TIMESTAMP       DEFAULT NOW(),
    updated_at      TIMESTAMP
);

-- 告警发送记录（用于去重）
CREATE TABLE IF NOT EXISTS alarm_record (
    id              BIGSERIAL       PRIMARY KEY,
    rule_id         BIGINT          NOT NULL,
    resource_id     VARCHAR(128),
    message         VARCHAR(2048)   NOT NULL,
    sent_time       TIMESTAMP       NOT NULL,
    status          VARCHAR(16)     NOT NULL,
    created_at      TIMESTAMP       DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_alarm_rule_time ON alarm_record(rule_id, sent_time);
CREATE INDEX IF NOT EXISTS idx_alarm_resource_rule ON alarm_record(resource_id, rule_id, sent_time);

-- =============================================
-- 默认告警规则
-- =============================================
INSERT INTO alarm_rule (rule_name, alarm_type, threshold, operator, enabled) VALUES
('余额低于500', 'BALANCE', 500.00, 'LESS_THAN', true),
('到期不足30天', 'EXPIRY', 30, 'LESS_THAN_OR_EQUAL', true),
('到期不足15天', 'EXPIRY', 15, 'LESS_THAN_OR_EQUAL', true),
('到期不足7天', 'EXPIRY', 7, 'LESS_THAN_OR_EQUAL', true)
ON CONFLICT DO NOTHING;

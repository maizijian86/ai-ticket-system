-- 添加开发者需求字段到ticket表
ALTER TABLE ticket ADD COLUMN tech_stack VARCHAR(500) AFTER rejection_reason;
ALTER TABLE ticket ADD COLUMN project_type VARCHAR(50) AFTER tech_stack;
ALTER TABLE ticket ADD COLUMN budget DECIMAL(10,2) AFTER project_type;
ALTER TABLE ticket ADD COLUMN deadline DATETIME AFTER budget;
ALTER TABLE ticket ADD COLUMN detailed_requirements TEXT AFTER deadline;

-- 添加AI生成内容字段
ALTER TABLE ticket ADD COLUMN ai_prd TEXT AFTER detailed_requirements;
ALTER TABLE ticket ADD COLUMN ai_tech_suggestion TEXT AFTER ai_prd;
ALTER TABLE ticket ADD COLUMN ai_task_breakdown TEXT AFTER ai_tech_suggestion;
ALTER TABLE ticket ADD COLUMN ai_estimated_hours INT AFTER ai_task_breakdown;

-- 添加接单状态字段
ALTER TABLE ticket ADD COLUMN accepted_by BIGINT AFTER ai_estimated_hours;
ALTER TABLE ticket ADD COLUMN accepted_by_name VARCHAR(100) AFTER accepted_by;
ALTER TABLE ticket ADD COLUMN is_visible BOOLEAN DEFAULT TRUE AFTER accepted_by_name;

-- 添加索引
ALTER TABLE ticket ADD INDEX idx_is_visible (is_visible);
ALTER TABLE ticket ADD INDEX idx_accepted_by (accepted_by);
ALTER TABLE ticket ADD INDEX idx_project_type (project_type);

-- ==================== AI 辅助开发额度（ticket-service） ====================
-- DDL 与 db/schema.sql 末尾一致。ticket-service 是 ddl-auto: update，正常情况下
-- 启动就会自动建表，本段是给"库已存在、想手工先建好"或"想比对结构"用的。
-- 三条都是 CREATE TABLE IF NOT EXISTS，可重复执行。

CREATE TABLE IF NOT EXISTS ai_credit_batch (
    id               BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT       NOT NULL COMMENT '额度所属账号',
    source           VARCHAR(20)  NOT NULL COMMENT 'REGISTER / MEMBERSHIP / ADMIN',
    biz_key          VARCHAR(100) NOT NULL COMMENT '发放幂等键: SIGNUP:{userId} / MEMBERSHIP:{orderId} / ADMIN:{uuid}',
    total_amount     INT          NOT NULL COMMENT '本批发放总额',
    remaining_amount INT          NOT NULL COMMENT '本批剩余',
    expires_at       DATETIME     DEFAULT NULL COMMENT '过期时间; NULL = 永久有效',
    granted_at       DATETIME     NOT NULL,
    remark           VARCHAR(255) DEFAULT NULL,
    created_at       DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at       DATETIME     DEFAULT NULL,
    UNIQUE KEY uk_batch_biz_key (biz_key),
    KEY idx_batch_user_expiry (user_id, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI额度批次';

CREATE TABLE IF NOT EXISTS ai_usage (
    id              BIGINT       AUTO_INCREMENT PRIMARY KEY,
    ticket_id       BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL COMMENT '消耗者(接单人)',
    feature         VARCHAR(20)  NOT NULL COMMENT 'ANALYSIS / PRD / CHAT',
    status          VARCHAR(20)  NOT NULL COMMENT 'RESERVED / SUCCESS / REFUNDED',
    active_slot     TINYINT      DEFAULT 1 COMMENT '1=占用中; NULL=已释放(失败可重试)',
    batch_id        BIGINT       DEFAULT NULL COMMENT '实际扣减的批次,退款回滚到同一批',
    amount          INT          NOT NULL DEFAULT 1,
    failure_reason  VARCHAR(500) DEFAULT NULL,
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at      DATETIME     DEFAULT NULL,
    UNIQUE KEY uk_usage_slot (ticket_id, feature, active_slot),
    KEY idx_usage_user (user_id),
    KEY idx_usage_batch (batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI额度使用流水';

CREATE TABLE IF NOT EXISTS user_membership (
    id              BIGINT      AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT      NOT NULL,
    plan_code       VARCHAR(32) NOT NULL COMMENT '如 MONTHLY_30',
    status          VARCHAR(16) NOT NULL COMMENT 'ACTIVE / EXPIRED / CANCELED',
    credits_granted INT         NOT NULL COMMENT '本周期发放次数(30)',
    started_at      DATETIME    NOT NULL,
    expires_at      DATETIME    NOT NULL COMMENT 'started_at + 30 天',
    auto_renew      TINYINT     DEFAULT 0,
    created_at      DATETIME    DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at      DATETIME    DEFAULT NULL,
    KEY idx_membership_user (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员订阅(本次仅建表)';

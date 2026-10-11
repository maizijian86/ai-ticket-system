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

-- 会员订单：一行 = 一次「开通会员」的支付单据。DDL 与 db/schema.sql 末尾一致。
-- uk_order_out_trade_no 是回调反查订单的依据，也是「一个单号只对应一次支付尝试」的兜底；
-- idx_order_user 支撑「我的订单」列表（user_id + status）。
CREATE TABLE IF NOT EXISTS membership_order (
    id           BIGINT        AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT        NOT NULL COMMENT '下单账号',
    plan_code    VARCHAR(32)   NOT NULL COMMENT '下单时的套餐，如 MONTHLY_30',
    plan_credits INT           NOT NULL COMMENT '权益快照: 该套餐发放次数',
    plan_days    INT           NOT NULL COMMENT '权益快照: 该套餐有效天数',
    amount       DECIMAL(10,2) NOT NULL COMMENT '下单金额(元)',
    pay_channel  VARCHAR(20)   NOT NULL COMMENT 'ALIPAY_SANDBOX',
    out_trade_no VARCHAR(64)   NOT NULL COMMENT '商户订单号, 给支付宝的那个',
    trade_no     VARCHAR(64)   DEFAULT NULL COMMENT '支付宝交易号, 回调里带回来',
    status       VARCHAR(16)   NOT NULL COMMENT 'PENDING / PAID / CLOSED',
    paid_at      DATETIME      DEFAULT NULL,
    created_at   DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at   DATETIME      DEFAULT NULL,
    UNIQUE KEY uk_order_out_trade_no (out_trade_no),
    KEY idx_order_user (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员订单';

-- ticket.ai_prepaid：发布者发布时预附 1 次 AI 额度（接单人用完「AI分析与对话」不扣自己的）。
-- 纯展示标记（大厅置顶与打标用），解锁判据始终是 ai_usage，详 DDL 见 db/schema.sql 的 ticket 表。
--
-- 给已存在的库补列。MySQL 的 ADD COLUMN 没有 IF NOT EXISTS，这段**不幂等**，跑过一次就完了；
-- 全新建库由 db/schema.sql 直接带上。
--
-- 三句都要跑：本项目实际发生的是"Hibernate 的 ddl-auto: update 先按它自己的类型把列建了出来"，
-- 而它建成的是 bit(1)、**可空、无默认** —— 存量 91 行因此全是 NULL。这不只是类型不齐：
-- 大厅按 `ai_prepaid DESC, created_at DESC` 排序，而 NULL 在 DESC 里排在 0 **之后**，
-- 于是新发的一张普通单会插到所有历史工单前面。第二句把类型纠正成与 ticket.is_visible
-- 相同的 tinyint(1) NOT NULL（同样是 Boolean，那边就是这么建的，映射已验证可用），
-- 第三句把存量行的 NULL 归零。第一句在列已存在时会报 Duplicate column，跳过即可。
ALTER TABLE ticket ADD COLUMN ai_prepaid TINYINT(1) NOT NULL DEFAULT 0;
ALTER TABLE ticket MODIFY COLUMN ai_prepaid TINYINT(1) NOT NULL DEFAULT 0;
UPDATE ticket SET ai_prepaid = 0 WHERE ai_prepaid IS NULL;

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

-- ============================================================
-- 2026-10-15 会员拆三档（Lite / Pro / Max）—— 大厅优先曝光
-- ============================================================
-- ticket.member_promoted：发布者在建单那一刻是带「大厅优先曝光」权益的会员（目前只有
-- Max 档），纯展示/排序标记，与 ai_prepaid 同构。大厅新次序：
--     member_promoted DESC, ai_prepaid DESC, created_at DESC
-- 完整说明见 db/schema.sql 的 ticket 表与 Ticket.memberPromoted 的注释。
--
-- ⚠️ 三句都要跑，理由与上面的 ai_prepaid 一字不差：本项目实际发生的是
-- "Hibernate 的 ddl-auto: update 先把列建了出来"，实测建出来的是 bit(1) NULL DEFAULT NULL
-- （81 行存量全是 NULL），而 NULL 在 DESC 里排在 0 **之后**
-- （实测 `ORDER BY c DESC` 对 {0,1,NULL} 返回 1,0,NULL）——
-- 于是新发的一张普通单会插到所有历史工单前面。第一句在列已存在时报 Duplicate column，
-- 跳过即可；后两句把它纠正成与 ai_prepaid / is_visible 完全相同的 tinyint(1) NOT NULL。
--
-- ⚠️ 顺序与上面 ai_prepaid 那组不同（这里是 UPDATE 在 MODIFY 之前），这是**改过的**：
-- 若列已由 Hibernate 建出且存量是 NULL，先 MODIFY ... NOT NULL 会直接报
-- `ERROR 1265 Data truncated for column 'member_promoted'`（strict 模式拒绝把 NULL 塞进
-- NOT NULL），根本走不到第三句。先归零再收紧，两条路径（列不存在 / 列已存在）就都能跑通。
--
-- ⚠️ 跑法：`mysql ... --force < db/migration.sql`（或逐句手工执行）。
-- 本文件从第一行起就是**不可重跑**的历史累积脚本，第 2 句就会撞上已有的 tech_stack；
-- 不加 --force 的话，客户端的 batch 模式会在第一个错误处直接停住，后面的语句一句都不跑
-- （实测：用 `-e "..."` 一次传整串时，前面的 Duplicate column 会把后面全吞掉）。
ALTER TABLE ticket ADD COLUMN member_promoted TINYINT(1) NOT NULL DEFAULT 0;
UPDATE ticket SET member_promoted = 0 WHERE member_promoted IS NULL;
ALTER TABLE ticket MODIFY COLUMN member_promoted TINYINT(1) NOT NULL DEFAULT 0;

-- 套餐码变更说明（**这两张表都不需要数据迁移**）：
--   · 在售档位码从 MONTHLY_30 变成 LITE / PRO / MAX，但 MONTHLY_30 就是今天的 PRO，
--     价格与权益一字未改。存量 user_membership / membership_order 里的 MONTHLY_30
--     由 MembershipPlan.resolve() 在**读路径**上认（→ PRO），故无需 UPDATE 改写历史行。
--   · 订单表里的 plan_credits / plan_days 本就存的是下单时的快照，发放只用快照，
--     所以改了枚举既不影响历史未支付订单，也不影响已开通的会员。

-- ==================== 用户头像（user-service） ====================
-- user-service 是 ddl-auto: validate —— Hibernate **不会**自动建列，这段必须手工执行，
-- 漏了的话启动直接报 schema validation 失败（找不到 avatar_url）。

ALTER TABLE users ADD COLUMN avatar_url VARCHAR(500) AFTER nickname;

-- 存量用户回填：随机分配一个默认头像。取值域与注册时新分配的完全一致
-- （见 UserService.randomDefaultAvatar 的 1..9），前端把这 9 个 fixture 放在
-- public/avatars/{n}.svg。只补 NULL 的，重复执行不会把用户自己换过的头像冲掉。
UPDATE users SET avatar_url = CONCAT('default:', 1 + FLOOR(RAND() * 9)) WHERE avatar_url IS NULL;

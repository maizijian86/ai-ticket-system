-- AI Ticket System Database Schema
-- MySQL 8.x
--
-- ⚠️ 两条必须遵守的约定（都是被 ddl-auto=validate 真实拦下来过才补上的）：
--
-- 1) Boolean 列一律写 BIT(1)，不要写 TINYINT(1) / BOOLEAN。
--    MySQL 的 BOOLEAN 只是 TINYINT(1) 的别名，JDBC 报的是 Types#TINYINT，
--    而 Hibernate 对 Java Boolean 期望 Types#BOOLEAN（MySQL 那边就是 bit）。
--    `ddl-auto: validate` 会直接报
--    `wrong column type encountered in column [x]; found [tinyint], but expecting [bit]`。
--    （`ddl-auto: update` 不会校验，所以这个问题能藏很久 —— 只在新建库 + validate 时炸。）
--
-- 2) 本文件必须与实体逐列对齐，包括 BaseEntity 的 updated_at / deleted_at。
--    历史教训：ticket 表曾有 10 列只写在 db/migration.sql 里，
--    于是拿本文件建库后 db/seed/*.sql 全部 `ERROR 1054 Unknown column`。
--
-- 校验方式（改完本文件后必跑）：建一个临时库 → 导入 schema.sql → 用
--   SPRING_JPA_HIBERNATE_DDL_AUTO=validate 起 ticket-service
-- 必须能正常 Started，而不是 Schema-validation 报错。

-- ============================================
-- User & Auth Tables
-- ============================================

-- User table
CREATE TABLE users (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    username        VARCHAR(100) NOT NULL UNIQUE,
    password        VARCHAR(255) NOT NULL,
    email           VARCHAR(255) NOT NULL UNIQUE,
    nickname        VARCHAR(100),

    -- 头像。两种取值，前端据此分叉：
    --   "default:N"（N=1..9）→ public/avatars/N.svg，注册时随机分配一个，用户不改就不变
    --   "avatar/{userId}/{uuid}.png" → 用户自己传的，存在 OSS 里的对象 key
    -- 判据是前缀（见 UserService.isOssKey）—— 换头像时只有 OSS key 需要删对象，
    -- 把 "default:3" 当 key 去删会报 KeyNotFound。
    avatar_url      VARCHAR(500),

    role            VARCHAR(20) DEFAULT 'USER',
    status          VARCHAR(20) DEFAULT 'active',
    last_login_at   DATETIME,

    -- GitHub info (optional)
    github_username VARCHAR(100),
    github_repos    JSON,

    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at      DATETIME,
    INDEX idx_users_username (username),
    INDEX idx_users_email (email),
    INDEX idx_users_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- User skill profile
CREATE TABLE user_skill (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT NOT NULL UNIQUE,
    user_name       VARCHAR(100),

    -- Skill tags (JSON array)
    skill_tags      JSON,

    -- Expertise level (JSON: {"java": 5, "mysql": 4})
    expertise_level JSON,

    -- Metrics
    total_resolved  INT DEFAULT 0,
    avg_resolution_hours DECIMAL(6,2),
    satisfaction_score DECIMAL(3,2) DEFAULT 5.00,

    -- Current load
    current_load    INT DEFAULT 0,
    max_load        INT DEFAULT 10,

    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_skill_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- Ticket Tables
-- ============================================

-- Ticket main table
CREATE TABLE ticket (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    title           VARCHAR(500) NOT NULL,
    content         TEXT NOT NULL,

    -- AI classification
    category        VARCHAR(50),
    category_confidence DECIMAL(5,2),

    -- AI priority
    priority        VARCHAR(10) DEFAULT 'P2',
    priority_score  DECIMAL(5,2),

    -- Status: OPEN, PROCESSING, RESOLVED, CLOSED
    status          VARCHAR(20) DEFAULT 'OPEN',

    -- Urgency level from user: LOW, NORMAL, HIGH, CRITICAL
    urgency         VARCHAR(20) DEFAULT 'NORMAL',

    -- Submitter
    creator_id      BIGINT NOT NULL,
    creator_name    VARCHAR(100),

    -- Handler
    handler_id      BIGINT,
    handler_name    VARCHAR(100),

    -- AI recommendation
    recommended_handler_id  BIGINT,
    recommend_reason TEXT,

    -- AI summary
    ai_summary      TEXT,

    -- Attachments (JSON array)
    attachments     JSON,

    -- GitHub repositories (JSON array: [{name, url}])
    github_repos    JSON,

    -- Price
    price           DECIMAL(10,2),
    ai_price_suggestion DECIMAL(10,2),

    -- 发布者发布时预附的 1 次 AI 额度：接单人用「AI分析与对话」不扣自己的次数。
    -- 只用于大厅置顶与打标（展示标记），**不是**解锁判据 ——
    -- 解锁与否永远只看 ai_usage 里那行 (ticket_id, feature=ANALYSIS, status=SUCCESS)。
    ai_prepaid      BIT(1) NOT NULL DEFAULT b'0',

    -- 发布这张单的人在建单那一刻是带「大厅优先曝光」权益的会员（目前只有 Max 档）。
    -- 与 ai_prepaid 完全同构：纯展示/排序标记，大厅按
    -- `member_promoted DESC, ai_prepaid DESC, created_at DESC` 排。
    -- 刻意是发布时的快照、不随会员到期收回，也不给老单回填（见 Ticket.memberPromoted）。
    member_promoted BIT(1) NOT NULL DEFAULT b'0',

    -- Completion proof (GitHub link or other)
    completion_proof TEXT,

    -- Timestamps
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    accepted_at     DATETIME,
    completed_at    DATETIME,
    resolved_at     DATETIME,
    closed_at       DATETIME,

    -- Rejection reason (when user rejects completion)
    rejection_reason TEXT,

    -- ==================== 开发者需求字段 ====================
    -- 以下 10 列此前只写在 db/migration.sql 里，schema.sql 一直没有。
    -- 后果：拿本文件建一个干净库，db/seed/real_tickets.sql 必定
    -- `ERROR 1054 Unknown column`。以前能跑只是因为 ticket-service 设了
    -- `ddl-auto: update`，启动时把缺的列悄悄补上了 —— 靠副作用蒙对，换台机器就炸。
    -- 类型逐条对齐 Ticket.java 的字段声明与 migration.sql 第 2-17 行。

    -- 技术栈（如：Spring Boot, Vue, MyBatis）
    tech_stack          VARCHAR(500),
    -- 项目类型（JavaWeb / Python / 前端 / 全栈 / 移动端）
    project_type        VARCHAR(50),
    -- 预算
    budget              DECIMAL(10,2),
    -- 截止日期
    deadline            DATETIME,
    -- 详细需求描述
    detailed_requirements TEXT,

    -- ==================== AI 生成内容 ====================
    -- 由 POST /api/v1/tickets/{id}/ai-analysis 与 GET /{id}/download-prd 读写
    ai_prd              TEXT,
    ai_tech_suggestion  TEXT,
    ai_task_breakdown   TEXT,
    ai_estimated_hours  INT,

    -- 是否在大厅显示（接单后隐藏）
    is_visible          BIT(1) DEFAULT b'1',

    -- Soft delete
    deleted_at      DATETIME,

    INDEX idx_ticket_category (category),
    INDEX idx_ticket_priority (priority),
    INDEX idx_ticket_status (status),
    INDEX idx_ticket_creator (creator_id),
    INDEX idx_ticket_handler (handler_id),
    INDEX idx_ticket_created (created_at DESC),
    -- 大厅查询用：is_visible = 1 过滤 + project_type 筛选（见 TicketService.listTickets）
    INDEX idx_is_visible (is_visible),
    INDEX idx_project_type (project_type),
    FULLTEXT INDEX ft_ticket_content (title, content)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Ticket comment/reply table
CREATE TABLE ticket_comment (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id       BIGINT NOT NULL,
    user_id         BIGINT NOT NULL,
    user_name       VARCHAR(100),
    content         TEXT NOT NULL,

    -- Internal note (handler only)
    is_internal         BIT(1) DEFAULT b'0',

    -- AI suggested reply
    is_ai_suggested BIT(1) DEFAULT b'0',

    -- AI reference sources (JSON array)
    ai_references      JSON,

    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (ticket_id) REFERENCES ticket(id) ON DELETE CASCADE,
    INDEX idx_ticket_comment_ticket_id (ticket_id),
    INDEX idx_ticket_comment_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Ticket chat (private messaging between user and handler)
CREATE TABLE ticket_chat (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id       BIGINT NOT NULL,
    sender_id       BIGINT NOT NULL,
    sender_name     VARCHAR(100),
    sender_role     VARCHAR(20),  -- USER or HANDLER
    content         TEXT NOT NULL,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    -- BaseEntity 带 @UpdateTimestamp，缺这一列时 Hibernate 启动校验（validate）会直接失败
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at      DATETIME DEFAULT NULL,

    FOREIGN KEY (ticket_id) REFERENCES ticket(id) ON DELETE CASCADE,
    INDEX idx_ticket_chat_ticket_id (ticket_id),
    INDEX idx_ticket_chat_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- Knowledge Base (MySQL FULLTEXT Search)
-- ============================================
--
-- ⚠️ 当前**没有任何代码读写这张表**。它唯一的消费者是 ai-service 里的 AiService
-- （那是个 MiniMax/Anthropic 时代的旧实现），该模块已整体删除。
-- 表本身保留（活库里还有 3 行数据），等知识库功能真正立项时再决定：
-- 要么补后端接口 + 前端页面，要么连表一起删。
-- 前端也没有知识库页面 —— 顶栏入口已摘除（见 Phase 5e）。

CREATE TABLE knowledge_base (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    title           VARCHAR(500) NOT NULL,
    content         TEXT NOT NULL,

    -- Category
    category        VARCHAR(50),

    source_type     VARCHAR(50),  -- 'ticket', 'manual', 'document'
    source_id       BIGINT,

    -- Quality metrics
    view_count      INT DEFAULT 0,
    helpful_count   INT DEFAULT 0,
    not_helpful_count INT DEFAULT 0,

    -- Status: DRAFT, PUBLISHED, ARCHIVED
    status          VARCHAR(20) DEFAULT 'DRAFT',

    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_knowledge_category (category),
    INDEX idx_knowledge_status (status),
    FULLTEXT INDEX ft_knowledge_content (title, content)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- Operation Log / AI 分类历史 —— 已移除
-- ============================================
--
-- 这里原有 operation_log 与 ai_classification_log 两张表，已随 Phase 3 一起删除：
--   · operation_log          —— 全仓库零 .java 引用（无实体/仓储/服务/控制器/原生 SQL），
--                               活库里也是 0 行。真正的审计诉求不存在。
--   · ai_classification_log  —— 实体与仓储曾有，但仓储从未被注入、findByTicketId
--                               永不被调用；它唯一的读取方是已删除的 ai-service。
--
-- 若将来要做操作审计，请重新设计（按需求定字段），不要照抄这两张空表。

-- ============================================
-- File Attachments Metadata
-- ============================================

CREATE TABLE file_attachment (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id       BIGINT NOT NULL,
    file_name       VARCHAR(255) NOT NULL,
    original_name   VARCHAR(255) NOT NULL,
    file_path       VARCHAR(500) NOT NULL,
    file_size       BIGINT,
    mime_type       VARCHAR(100),
    uploaded_by     BIGINT,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (ticket_id) REFERENCES ticket(id) ON DELETE CASCADE,
    INDEX idx_file_attachment_ticket_id (ticket_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- Project Management Tables (New)
-- ============================================

-- Project main table
CREATE TABLE project (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(200) NOT NULL COMMENT '项目名称',
    description     TEXT COMMENT '项目描述',
    project_type    VARCHAR(50) COMMENT '项目类型: THESIS/OUTSOURCE/INTERNAL/PERSONAL',

    -- AI analysis results
    ai_prd          MEDIUMTEXT COMMENT 'AI生成的PRD文档',
    ai_tech_stack   VARCHAR(500) COMMENT 'AI推荐的技术栈',
    ai_estimated_hours INT COMMENT 'AI预估工时(小时)',
    ai_estimated_days INT COMMENT 'AI预估天数',
    ai_risk_analysis TEXT COMMENT 'AI风险分析',

    -- Status: PLANNING, IN_PROGRESS, TESTING, COMPLETED, ARCHIVED
    status          VARCHAR(30) DEFAULT 'PLANNING',
    priority        VARCHAR(10) DEFAULT 'P2',

    -- Time management
    deadline        DATE COMMENT '截止日期',
    actual_start_date DATE COMMENT '实际开始日期',
    actual_end_date DATE COMMENT '实际结束日期',

    -- Progress
    progress        INT DEFAULT 0 COMMENT '进度百分比 0-100',
    total_tasks     INT DEFAULT 0,
    completed_tasks INT DEFAULT 0,

    -- Creator
    creator_id      BIGINT NOT NULL,
    creator_name    VARCHAR(100),

    -- Attachments
    attachments     JSON COMMENT '项目文档附件',

    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at      DATETIME,

    INDEX idx_project_creator (creator_id),
    INDEX idx_project_status (status),
    INDEX idx_project_created (created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Project module table
CREATE TABLE project_module (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id      BIGINT NOT NULL,
    name            VARCHAR(200) NOT NULL COMMENT '模块名称',
    description     TEXT COMMENT '模块描述',
    sort_order      INT DEFAULT 0 COMMENT '排序',
    status          VARCHAR(30) DEFAULT 'PENDING' COMMENT 'PENDING/IN_PROGRESS/COMPLETED',
    progress        INT DEFAULT 0,
    total_tasks     INT DEFAULT 0,
    completed_tasks INT DEFAULT 0,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at      DATETIME DEFAULT NULL COMMENT 'BaseEntity 软删列，ProjectTaskRepository 等按它过滤',

    INDEX idx_module_project (project_id),
    FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Project task table
CREATE TABLE project_task (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id      BIGINT NOT NULL,
    module_id       BIGINT COMMENT '所属模块，可为空',
    parent_task_id  BIGINT COMMENT '父任务ID，支持子任务',

    -- Task info
    title           VARCHAR(200) NOT NULL,
    description     TEXT,
    task_type       VARCHAR(30) COMMENT 'FEATURE/BUG/TASK/REFACTOR',
    status          VARCHAR(30) DEFAULT 'TODO' COMMENT 'TODO/IN_PROGRESS/REVIEW/DONE',
    priority        VARCHAR(10) DEFAULT 'P2',

    -- Work hours
    estimated_hours DECIMAL(8,2) COMMENT '预估工时',
    actual_hours    DECIMAL(8,2) COMMENT '实际工时',

    -- AI evaluation
    ai_estimated_hours DECIMAL(8,2) COMMENT 'AI预估工时',
    ai_suggestion   TEXT COMMENT 'AI实现建议',

    -- Time
    deadline        DATE,
    completed_at    DATETIME,

    -- Assignee
    assignee_id     BIGINT,
    assignee_name   VARCHAR(100),

    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at      DATETIME DEFAULT NULL,

    INDEX idx_task_project (project_id),
    INDEX idx_task_module (module_id),
    INDEX idx_task_status (status),
    INDEX idx_task_parent (parent_task_id),
    FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,
    FOREIGN KEY (module_id) REFERENCES project_module(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Milestone table
CREATE TABLE milestone (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id      BIGINT NOT NULL,
    name            VARCHAR(200) NOT NULL COMMENT '里程碑名称',
    description     TEXT,
    due_date        DATE COMMENT '计划日期',
    actual_date     DATE COMMENT '实际完成日期',
    status          VARCHAR(30) DEFAULT 'PENDING' COMMENT 'PENDING/ACHIEVED/MISSED',
    sort_order      INT DEFAULT 0,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at      DATETIME DEFAULT NULL,

    INDEX idx_milestone_project (project_id),
    FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Project discussion table (replaces ticket_comment for projects)
CREATE TABLE project_discussion (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id      BIGINT NOT NULL,
    target_type     VARCHAR(30) COMMENT 'PROJECT/MODULE/TASK',
    target_id       BIGINT COMMENT '关联的项目/模块/任务ID',
    user_id         BIGINT NOT NULL,
    user_name       VARCHAR(100),
    content         TEXT NOT NULL,
    is_ai_suggested BIT(1) DEFAULT b'0',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at      DATETIME DEFAULT NULL,

    INDEX idx_discussion_project (project_id),
    INDEX idx_discussion_target (target_type, target_id),
    FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== AI 辅助开发额度（ticket-service） ====================
-- 与实体 com.aiticket.ticket.entity.AiCreditBatch / AiUsage / UserMembership / MembershipOrder
-- 一一对应。ticket-service 是 ddl-auto: update，首次启动会自动建这四张表；这里留存一份
-- DDL 作为记录，也给已有的库做手工迁移用（见 db/migration.sql）。

-- 额度批次：一行 = 一次发放。注册赠送 / 会员发放 / 管理员调整都往这里插一行。
-- 余额 = SUM(remaining_amount) where remaining_amount > 0 AND (expires_at IS NULL OR expires_at > NOW())
CREATE TABLE ai_credit_batch (
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

-- 使用流水：一行 = 一次功能调用尝试。同时是消耗流水和"每工单每槽位一次"的数据库级约束。
-- uk_usage_slot 是这条规则的**权威**：SUCCESS 行永久占位，同票同槽位再插必然冲突，
-- 并发/多实例/Redis 挂了都成立。active_slot=NULL 在 MySQL 唯一键里不参与去重，
-- 所以失败退款后可以重试（失败行保留下来做审计）。
-- 槽位 ≠ 功能（2026-10-10 起）：AI 分析与 AI 对话是**同一个槽位**（谁先用谁扣，
-- 另一个免费），两者都记在 feature=ANALYSIS 上 —— 各记一行就成了两条唯一键，
-- 数据库谁也挡不住谁，并发下会被扣成两次。所以一张工单最多两行 SUCCESS：
-- ANALYSIS（分析+对话）与 PRD。feature=CHAT 是合并前的历史写法，只读不写。
CREATE TABLE ai_usage (
    id              BIGINT       AUTO_INCREMENT PRIMARY KEY,
    ticket_id       BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL COMMENT '消耗者(接单人)',
    feature         VARCHAR(20)  NOT NULL COMMENT 'ANALYSIS(分析+对话) / PRD；CHAT 为历史值',
    status          VARCHAR(20)  NOT NULL COMMENT 'RESERVED / SUCCESS / REFUNDED',
    active_slot     INT          DEFAULT 1 COMMENT '1=占用中; NULL=已释放(失败可重试)。实体是 Integer，故必须是 INT 而不是 TINYINT —— 否则 ddl-auto=validate 会报 wrong column type',
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

-- 会员订阅：会员身份记在这张表，次数记在 ai_credit_batch（source=MEMBERSHIP,
-- expires_at=发放日+30天），方案见仓库根目录 会员额度plan.txt。
-- "到期没用完的次数作废"由批次的 expires_at 天然表达，不需要额外核销逻辑。
CREATE TABLE user_membership (
    id              BIGINT      AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT      NOT NULL,
    plan_code       VARCHAR(32) NOT NULL COMMENT 'LITE / PRO / MAX；存量的 MONTHLY_30 是 PRO 的前身',
    status          VARCHAR(16) NOT NULL COMMENT 'ACTIVE / EXPIRED / CANCELED',
    credits_granted INT         NOT NULL COMMENT '本周期发放次数(Lite 10 / Pro 30 / Max 120)',
    started_at      DATETIME    NOT NULL,
    expires_at      DATETIME    NOT NULL COMMENT 'started_at + 30 天',
    auto_renew      BIT(1)      DEFAULT b'0',
    created_at      DATETIME    DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at      DATETIME    DEFAULT NULL,
    KEY idx_membership_user (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员订阅(本次仅建表)';

-- 会员订单：一行 = 一次「开通会员」的支付单据。
--
-- 这张表是**业务单据**，支付宝那边的 trade_no 是**资金凭据**，两者不是一回事：
-- 我们这边的单永远留着（谁在什么时候点了开通、该付多少、付没付），
-- 支付宝那边的交易会超时关闭、记录会老。所以「会员有没有开通」一律只看这张表
-- 与 user_membership，不看支付宝 —— trade_status 只用来驱动 PENDING → PAID 这一次跃迁。
--
-- 未支付的单也**必须**落库（不是"等支付成功再插"）：out_trade_no 要先有唯一键才谈得上
-- 下单，防连点的复用逻辑要有行可查，对账要有单据。用户关掉支付页后那单留在这里、
-- 由读路径按支付宝的查单结果改成 CLOSED；不提供「继续支付」，想付就重新下单，
-- 于是「一个 out_trade_no 只对应一次支付尝试、作废后不重用」这条语义成立。
CREATE TABLE membership_order (
    id           BIGINT        AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT        NOT NULL COMMENT '下单账号',
    plan_code    VARCHAR(32)   NOT NULL COMMENT '下单时的套餐：LITE / PRO / MAX；存量的 MONTHLY_30 是 PRO 的前身',
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

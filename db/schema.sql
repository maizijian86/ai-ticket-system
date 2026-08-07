-- AI Ticket System Database Schema
-- MySQL 8.x

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

    -- Soft delete
    deleted_at      DATETIME,

    INDEX idx_ticket_category (category),
    INDEX idx_ticket_priority (priority),
    INDEX idx_ticket_status (status),
    INDEX idx_ticket_creator (creator_id),
    INDEX idx_ticket_handler (handler_id),
    INDEX idx_ticket_created (created_at DESC),
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
    is_internal     BOOLEAN DEFAULT FALSE,

    -- AI suggested reply
    is_ai_suggested BOOLEAN DEFAULT FALSE,

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
    deleted_at      DATETIME DEFAULT NULL,

    FOREIGN KEY (ticket_id) REFERENCES ticket(id) ON DELETE CASCADE,
    INDEX idx_ticket_chat_ticket_id (ticket_id),
    INDEX idx_ticket_chat_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- Knowledge Base (MySQL FULLTEXT Search)
-- ============================================

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
-- Operation Log
-- ============================================

CREATE TABLE operation_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT,
    user_name       VARCHAR(100),
    action          VARCHAR(50) NOT NULL,
    resource_type   VARCHAR(50),
    resource_id     BIGINT,
    details         JSON,
    ip_address      VARCHAR(50),
    user_agent      VARCHAR(500),
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_operation_log_user_id (user_id),
    INDEX idx_operation_log_action (action),
    INDEX idx_operation_log_created (created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- AI Processing History
-- ============================================

CREATE TABLE ai_classification_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id       BIGINT NOT NULL,
    ai_category     VARCHAR(50),
    ai_confidence   DECIMAL(5,2),
    corrected_category VARCHAR(50),
    corrected_by    BIGINT,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_ai_classification_ticket_id (ticket_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
    is_ai_suggested BOOLEAN DEFAULT FALSE,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_discussion_project (project_id),
    INDEX idx_discussion_target (target_type, target_id),
    FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

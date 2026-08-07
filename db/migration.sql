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

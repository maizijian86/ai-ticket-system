-- Sample data for AI Ticket System (MySQL 8.x)

-- Note: Password hash is a placeholder. Register users through API or generate proper BCrypt hashes.

-- Insert sample users
INSERT INTO users (username, password, email, nickname, role, status, created_at, updated_at) VALUES
('admin', '$2a$10$XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', 'admin@aiticket.com', 'Administrator', 'ADMIN', 'active', NOW(), NOW()),
('handler_zhang', '$2a$10$XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', 'zhang@aiticket.com', '张三', 'USER', 'active', NOW(), NOW()),
('handler_li', '$2a$10$XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', 'li@aiticket.com', '李四', 'USER', 'active', NOW(), NOW()),
('user_wang', '$2a$10$XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', 'wang@aiticket.com', '王五', 'USER', 'active', NOW(), NOW());

-- Insert skill profiles for handlers (using JSON format for MySQL)
INSERT INTO user_skill (user_id, user_name, skill_tags, expertise_level, total_resolved, avg_resolution_hours, satisfaction_score, current_load, max_load) VALUES
(2, '张三', '["java", "mysql", "redis", "kafka"]', '{"java": 5, "mysql": 4, "redis": 4, "kafka": 3}', 150, 2.50, 4.80, 3, 10),
(3, '李四', '["python", "docker", "kubernetes", "aws"]', '{"python": 5, "docker": 4, "kubernetes": 4, "aws": 3}', 120, 3.20, 4.60, 5, 10);

-- Insert sample tickets
INSERT INTO ticket (title, content, category, category_confidence, priority, priority_score, status, creator_id, creator_name, created_at, updated_at) VALUES
('登录按钮点击无反应', '用户反映在首页点击登录按钮后没有任何反应，控制台报500错误。浏览器版本Chrome 120。', 'BUG', 92.50, 'P1', 85.00, 'OPEN', 4, '王五', NOW(), NOW()),
('如何修改个人信息', '请问在哪里可以修改我的个人资料和头像？找了半天没找到入口。', 'CONSULT', 88.00, 'P3', 25.00, 'OPEN', 4, '王五', NOW(), NOW()),
('系统响应速度太慢', '最近系统非常卡，打开工单列表要等很久，希望能够优化一下性能。', 'SUGGESTION', 78.00, 'P2', 50.00, 'OPEN', 4, '王五', NOW(), NOW());

-- Insert sample knowledge base entries
INSERT INTO knowledge_base (title, content, category, source_type, status, helpful_count, view_count) VALUES
('登录功能常见问题解决方案', '登录失败可能由以下原因导致：\n1. 密码错误 - 请尝试重置密码\n2. 账号被锁定 - 联系管理员解锁\n3. 浏览器缓存问题 - 清除浏览器缓存后重试\n4. 服务端Session过期 - 重新打开浏览器访问', 'BUG', 'manual', 'PUBLISHED', 45, 120),
('如何创建工单', '创建工单的步骤：\n1. 点击"新建工单"按钮\n2. 填写工单标题和详细描述\n3. 选择紧急程度（可选）\n4. 添加附件（可选）\n5. 点击提交按钮\n\n提交后系统会自动进行AI分类和优先级评估。', 'CONSULT', 'manual', 'PUBLISHED', 32, 85),
('系统性能优化建议', '为提升系统性能，建议：\n1. 开启Redis缓存\n2. 数据库连接池优化\n3. 使用CDN加速静态资源\n4. 实施数据库读写分离\n5. 添加索引优化查询', 'SUGGESTION', 'manual', 'PUBLISHED', 18, 50);

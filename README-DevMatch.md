# DevMatch - 开发者项目接单平台

## 系统概述

DevMatch是一个面向个人开发者的项目接单平台，帮助开发者找到合适的项目，同时帮助需求方找到优秀的开发者。

### 核心功能

1. **需求发布** - 发布项目需求，包含技术栈、预算、详细需求等
2. **AI开发分析** - 自动生成PRD文档、技术栈建议、任务拆解
3. **项目接单** - 开发者接单后，项目从大厅消失，双方进入私聊
4. **实时沟通** - 接单后双方可以通过聊天室实时沟通

## 技术栈

### 后端
- Spring Boot 3.2.x
- Spring Cloud Gateway
- LangChain4j 0.36.0 (AI框架)
- MySQL 8.0
- Redis 7.x
- JWT认证

### 前端
- Vue 3 + Composition API
- Element Plus UI
- Vite构建
- Pinia状态管理

## 数据库新字段

### ticket表新增字段

```sql
-- 开发者需求字段
tech_stack VARCHAR(500)      -- 技术栈
project_type VARCHAR(50)     -- 项目类型
budget DECIMAL(10,2)         -- 预算
deadline DATETIME            -- 截止日期
detailed_requirements TEXT   -- 详细需求

-- AI生成内容
ai_prd TEXT                  -- PRD文档
ai_tech_suggestion TEXT      -- 技术建议
ai_task_breakdown TEXT       -- 任务拆解
ai_estimated_hours INT       -- 预估工时

-- 接单状态
accepted_by BIGINT           -- 接单者ID
accepted_by_name VARCHAR(100) -- 接单者姓名
is_visible BOOLEAN           -- 是否在大厅显示
```

## API接口

### 工单接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/v1/tickets | 创建项目需求 |
| GET | /api/v1/tickets/:id | 获取项目详情 |
| GET | /api/v1/tickets | 获取需求大厅列表 |
| POST | /api/v1/tickets/:id/pickup | 接单 |
| POST | /api/v1/tickets/:id/ai-analysis | AI开发分析 |
| GET | /api/v1/tickets/:id/chat | 获取聊天记录 |
| POST | /api/v1/tickets/:id/chat | 发送聊天消息 |

## 前端页面

### 页面结构

```
/tickets              -- 需求大厅（首页）
/tickets/new          -- 发布需求
/tickets/:id          -- 项目详情
/tickets/:id/edit     -- 编辑项目
/tickets/:id/chat     -- 聊天沟通
/my-tickets           -- 我的需求
/pending              -- 待处理
/knowledge            -- 知识库
```

## 使用流程

### 需求方

1. 注册/登录系统
2. 点击"发布需求"按钮
3. 填写项目信息：
   - 项目名称
   - 项目类型（JavaWeb/Python/前端等）
   - 技术栈
   - 项目描述
   - 详细需求
   - 预算
   - 截止日期
4. 可选：点击"AI智能分析"生成PRD
5. 提交需求
6. 等待开发者接单
7. 接单后进入聊天沟通

### 开发者

1. 注册/登录系统
2. 浏览需求大厅
3. 查看项目详情
4. 可选：点击"AI开发分析"查看AI建议
5. 点击"接单"按钮
6. 进入聊天与需求方沟通
7. 完成项目开发

## AI功能

### 生成PRD文档

AI会根据项目信息生成完整的产品需求文档，包含：
- 项目概述
- 功能需求
- 非功能需求
- 技术架构建议
- 数据库设计
- API接口设计
- 开发计划
- 风险评估

### 技术栈建议

AI会根据项目类型推荐合适的技术栈：
- 后端技术栈
- 前端技术栈
- 数据库选型
- 中间件建议
- 部署方案

### 任务拆解

AI会将项目拆解为可执行的开发任务：
- 按模块组织
- 标注优先级
- 估算工时
- 标注依赖关系

## 部署说明

### 后端

```bash
# 1. 执行数据库迁移
mysql -u aiticket -p aiticket < db/migration.sql

# 2. 启动服务
cd ai-service && mvn spring-boot:run
cd ticket-service && mvn spring-boot:run
cd user-service && mvn spring-boot:run
cd api-gateway && mvn spring-boot:run
```

### 前端

```bash
cd frontend
npm install
npm run dev
```

## 配置说明

### application.yml

```yaml
langchain4j:
  open-ai:
    chat-model:
      api-key: your-api-key
      model-name: mimo-v2.5
      base-url: https://api.xiaomimimo.com/anthropic
```

## 开发计划

### Phase 1 (已完成)
- [x] LangChain4j框架集成
- [x] 开发者需求字段
- [x] AI分析接口
- [x] 接单功能
- [x] 聊天功能

### Phase 2 ( planned)
- [ ] 向量数据库RAG
- [ ] 知识库检索
- [ ] 项目评价系统
- [ ] 支付功能
- [ ] 消息通知

## 注意事项

1. 首次使用需要执行数据库迁移脚本
2. AI功能需要有效的API Key
3. 聊天消息每5秒自动刷新
4. 接单后项目将从大厅隐藏

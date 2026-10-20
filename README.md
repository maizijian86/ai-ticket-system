# 百川灵构 · AI 开发者接单平台

需求发布 → 接单开发 → 交付验收的完整闭环，AI 全程辅助（技术分析、PRD、任务拆解、开发对话），
外加会员额度与支付宝支付。

---

## 技术栈

| 层 | 实际使用 |
|---|---|
| 后端 | Spring Boot **3.3.0** · **Java 17**（见下方警告）· Spring Cloud Gateway 2023.0.1 |
| 数据库 | **MySQL 8.x**（不是 PostgreSQL） |
| 缓存 | Redis 7.x（双 Token、AI 对话记忆、额度） |
| AI | LangChain4j 1.0.1-beta6 · DeepSeek（OpenAI 兼容协议） |
| 支付 | 支付宝沙箱（alipay-sdk-java） |
| 存储 | 阿里云 OSS（交付物压缩包、用户头像） |
| 前端 | **React 19** + TypeScript + Vite 5 + Tailwind v4 + shadcn/base-ui + zustand |

> ⚠️ **必须用 JDK 17 构建。** JDK 23 起 `javac` 默认不再隐式运行注解处理器，
> 在 JDK 24 下 `mvn compile` 会因 Lombok 完全不生效而大面积 `cannot find symbol`
> （更坑的是增量编译时看着"通过"，因为 javac 根本没跑）。
> `pom.xml` 里的 `<java.version>` 就是 17。

## 服务

只有 **3 个** 服务。曾有一个 `ai-service`，已删除 —— 它只有两个接口且零调用方，
PRD 与开发建议现在由 `ticket-service` 的 `TicketAiService` 直接完成。

| 模块 | 端口 | 职责 |
|---|---|---|
| `api-gateway` | 8080 | 唯一入口。JWT 校验，向下游注入 `X-User-Id` / `X-User-Role` |
| `user-service` | 8081 | 注册登录、双 Token、用户资料、头像（OSS）、接单者技能 |
| `ticket-service` | 8082 | 工单、私聊、AI（LangChain4j）、会员与订单、支付宝、项目 |
| `common` | — | 共享代码：Result/PageResult、全局异常、JWT、OSS、实体基类 |

**三个服务都只监听 `127.0.0.1`。** 这不是保守：下游服务**不做鉴权**，身份完全依赖
网关注入的头。一旦绑到 `0.0.0.0`，同网段任何人都能直连 8081/8082 并伪造
`X-User-Id`，鉴权等于不存在。部署时保持回环 + 由网关反代。

## 快速开始

### 1. 基础设施

```bash
docker compose up -d          # MySQL 3306 + Redis 6379
```

### 2. 数据库

```bash
mysql -uroot -p < db/schema.sql
```

`db/schema.sql` 与全部 JPA 实体逐列一致（含 `updated_at` / `deleted_at` 与所有
`BIT(1)` 布尔列），**可以直接用它建库**。历史教训写在文件头注释里：它曾经缺 16 个列，
导致所有种子脚本在干净库上 `ERROR 1054`。

可选种子：

```bash
mysql -uroot -p aiticket < db/sample-data.sql            # 小样本，各列齐全
mysql -uroot -p aiticket < db/seed/real_tickets.sql      # 50 张演示工单
```

> `db/migration.sql` 是**历史累积脚本**（不可重跑，需 `--force`），只在给已存在的旧库
> 补列时用；新建库不需要它。

### 3. 环境变量

都可选 —— 不配服务照样启动，只有对应功能不可用。

```bash
# 数据库（三个服务共用，默认 aiticket/aiticket）
DB_URL=jdbc:mysql://localhost:3306/aiticket?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
DB_USERNAME=aiticket
DB_PASSWORD=aiticket

# JWT。**网关与 user-service 必须是同一个值**，否则表现是"登录成功但之后每个接口都 401"
JWT_SECRET=<至少 32 字节的随机串>

# AI（DeepSeek）
DEEPSEEK_API_KEY=sk-...
DEEPSEEK_MODEL=deepseek-chat

# 阿里云 OSS（头像 / 交付物）
OSS_ENDPOINT=oss-cn-beijing.aliyuncs.com
OSS_BUCKET=...
OSS_ACCESS_KEY_ID=...
OSS_ACCESS_KEY_SECRET=...

# 支付宝沙箱（会员支付）
ALIPAY_APP_ID=...
ALIPAY_PRIVATE_KEY=...
ALIPAY_PUBLIC_KEY=...
ALIPAY_NOTIFY_URL=https://<你的公网隧道>/api/v1/membership/callback/alipay
ALIPAY_RETURN_URL=http://localhost:3000/membership/result
```

也可以放到各服务的 `src/main/resources/application-secret.yml`（已被 `.gitignore`）。

### 4. 启动后端

```bash
mvn clean install -DskipTests

java -jar user-service/target/user-service-1.0.0-SNAPSHOT.jar      # 8081
java -jar ticket-service/target/ticket-service-1.0.0-SNAPSHOT.jar  # 8082
java -jar api-gateway/target/api-gateway-1.0.0-SNAPSHOT.jar        # 8080
```

开发时也可以：

```bash
mvn -pl user-service  spring-boot:run
mvn -pl ticket-service spring-boot:run
mvn -pl api-gateway   spring-boot:run
```

### 5. 启动前端

```bash
cd frontend
npm install
npm run dev          # http://localhost:3000，/api 已代理到 127.0.0.1:8080
```

## 前端工程约定

```bash
npm run type-check   # tsc --noEmit
npm run lint         # eslint（含 unused-imports，防止死代码回潮）
npm run verify       # 上面两个一起跑
npm run build        # tsc + vite build
npm run audit:reach  # 从 main.tsx 做可达性分析，列出没人引用的文件
```

几条**在代码里反复踩过**的约定：

- 图标只从 `src/components/icons.ts` 取，不许直接 `import from 'lucide-react'`。
- 卡片/页头的样式常量只在 `src/components/layout/PageShell.tsx` 定义，页面不要重写。
- 所有 HTTP 走 `src/api/client.ts`（`request`）。它已处理：剥响应信封、40102 刷新单飞、
  失败 toast。**不要再自建 axios 实例** —— 曾经 `api/project.ts` 就是自建的一套：
  没有刷新、不注入身份、业务错误还被当成功。
- 页面走路由级懒加载（`router/index.tsx` 的 `page()`），vendor 在 `vite.config.ts` 切三块。
  首屏 chunk 已从 950 kB 降到 ~318 kB，别把静态 import 加回去。
- 页面标题用 `PageHeader`（等宽大写 eyebrow + 中文标题）。虹彩背景是高饱和蓝，
  直接压上去的次要文字要用 `text-foreground/60` 并带一层淡白色 text-shadow，
  否则在浅蓝区域会糊掉。

## 功能完成度

| 模块 | 状态 |
|---|---|
| 注册 / 登录 / 双 Token 自动续期 | ✅ |
| 需求大厅（筛选、分页、身份卡片、滚动位置恢复） | ✅ |
| 工单详情（AI 分析 / PRD / 对话、交付与验收、私聊入口） | ✅ |
| 发布 / 编辑需求 | ✅ |
| 工单私聊 | ✅ |
| 会员套餐 / 支付宝下单 / 支付结果页（轮询 + 切回重查） | ✅ |
| 个人主页（头像、昵称、额度、订单、接单者技能） | ✅ |
| 用户管理 / 统计分析（管理员） | ✅ |
| **项目管理（项目 / 模块 / 任务 / 里程碑 / 讨论）** | ⚠️ **后端 35 个接口可用，前端三个页面未做**（入口显示「即将上线」） |
| 知识库 | ❌ 不做。`knowledge_base` 表当前**没有任何代码读写**，入口已撤 |

## 目录

```
ai-ticket-system/
├── api-gateway/     # 8080 唯一入口，JWT → X-User-Id
├── user-service/    # 8081 账号、资料、头像、技能
├── ticket-service/  # 8082 工单、AI、会员、支付、项目
├── common/          # 共享：Result / 异常 / JWT / OSS / BaseEntity
├── frontend/        # React 19 + Vite
├── db/
│   ├── schema.sql   # 唯一权威建库脚本
│   ├── migration.sql# 历史补列脚本（不可重跑）
│   └── seed/        # 演示数据
├── docker-compose.yml
└── DESIGN.md        # 视觉规范
```

## 已知运维要点

- **网关与 user-service 的 `jwt.secret` 必须一致。**
- 支付宝回调需要公网可达（开发期用 natapp 之类隧道，免费版域名每次重启都变）。
  `notify-url` 改完要**重启 ticket-service**，且旧的待支付订单不要再用
  （它带着下单那一刻的 notify-url）。
- 支付结果页会轮询查单；`PENDING` 超过 15 分钟时后端会主动向支付宝查一次兜底，
  所以回调丢了也能自愈。
- 会员额度「发布即消耗、不退还」；一张工单最多消耗 2 次（分析+对话算一次，PRD 另算）。

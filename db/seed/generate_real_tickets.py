# -*- coding: utf-8 -*-
"""
生成 50 张"看起来像真业务"的工单，以及一批对应身份的发布者 / 接单人。

为什么用脚本而不是手写 SQL：一张工单有 30 多个字段，50 张手写必然错位，
出问题还难查。这里用 dict 表达，列顺序由程序决定。

用法（在本目录下）：
    python -I generate_real_tickets.py
    mysql -u aiticket -paiticket -h 127.0.0.1 aiticket --default-character-set=utf8mb4 < real_tickets.sql

⚠️ 脚本会清空 ticket 表（连同 ticket_chat / ticket_comment / file_attachment
   的级联行、以及 ai_usage 里引用这些工单的额度记录）。跑之前先 mysqldump。
"""

import os

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, 'real_tickets.sql')

# 与 admin2 相同的 bcrypt 哈希，对应明文 123456
PASSWORD = '$2a$10$3TDhnn/Po5I68q/I3MN5pepnqScfLBh01hVkIpcpOGYwbvB9KDUQy'

# id, username, nickname, email, avatar, role, created_at
USERS = [
    (101, 'yunqi_tech', '杭州云启科技', 'yunqi@demo.local', 'default:2', 'PUBLISHER', '2026-08-12 09:20:00'),
    (102, 'shiguang_wen', '上海拾光文创', 'shiguang@demo.local', 'default:5', 'PUBLISHER', '2026-08-15 14:10:00'),
    (103, 'limingyuan', '李明远', 'limingyuan@demo.local', 'default:1', 'PUBLISHER', '2026-08-21 20:35:00'),
    (104, 'zhouyutong', '周雨桐', 'zhouyutong@demo.local', 'default:7', 'PUBLISHER', '2026-08-26 10:05:00'),
    (105, 'qifan_edu', '广州启帆教育', 'qifan@demo.local', 'default:4', 'PUBLISHER', '2026-09-01 09:15:00'),
    (106, 'chenmo_dev', '陈默', 'chenmo@demo.local', 'default:3', 'PUBLISHER', '2026-09-04 16:40:00'),
    (107, 'ruilian_ai', '深圳睿联智能', 'ruilian@demo.local', 'default:6', 'PUBLISHER', '2026-09-07 11:25:00'),
    (108, 'henghui_soft', '苏州恒汇软件', 'henghui@demo.local', 'default:8', 'PUBLISHER', '2026-09-10 08:50:00'),
    (109, 'wangyan', '王砚', 'wangyan@demo.local', 'default:9', 'HANDLER', '2026-08-18 22:12:00'),
    (110, 'liusiyuan', '刘思远', 'liusiyuan@demo.local', 'default:2', 'HANDLER', '2026-08-23 13:48:00'),
    (111, 'zhaoyiming', '赵一鸣', 'zhaoyiming@demo.local', 'default:4', 'HANDLER', '2026-08-29 19:02:00'),
    (112, 'sunjiaqi', '孙佳琪', 'sunjiaqi@demo.local', 'default:6', 'HANDLER', '2026-09-02 10:33:00'),
    (113, 'zhengkai', '郑凯', 'zhengkai@demo.local', 'default:1', 'HANDLER', '2026-09-05 15:27:00'),
    (114, 'wutong_dev', '吴桐', 'wutong@demo.local', 'default:5', 'HANDLER', '2026-09-08 09:41:00'),
    (115, 'hejiawei', '何嘉伟', 'hejiawei@demo.local', 'default:3', 'HANDLER', '2026-09-11 21:06:00'),
    (116, 'linxiao', '林晓', 'linxiao@demo.local', 'default:7', 'HANDLER', '2026-09-13 12:19:00'),
]

NAME = {u[0]: u[2] for u in USERS}

# 每张单：common 字段 + 事件时间。status 决定哪些时间字段有值。
TICKETS = [
    dict(
        id=1,
        title='企业内部 OA 审批流系统，支持多级审批与抄送',
        content='公司现有的审批全靠线下签字，报销和请假单据经常卡在某个领导桌上找不到人。'
                '想做一个内部 OA：员工在线提单，按部门和金额自动走不同审批链，领导在手机上就能批。'
                '需要和现有员工花名册对接，后续还会接考勤和薪酬模块，所以接口要留好。',
        req='1. 支持请假、报销、采购、用章四类流程，每类可配置审批节点和条件分支（如金额超过 5000 追加财务总监）；\n'
            '2. 审批人可在待办列表一键通过或驳回，驳回必须填写原因并回退到发起人；\n'
            '3. 支持抄送，抄送人在详情页只读，不参与审批；\n'
            '4. 前端做 PC 后台和手机 H5 两套入口，H5 走企业微信免登；\n'
            '5. 所有操作留痕，管理员可导出近 12 个月的审批记录为 Excel；\n'
            '6. 提供员工、部门、角色三个基础数据接口，方便后续对接薪酬。',
        category='OTHER', conf=0.91, priority='P1', pscore=0.86, urgency='HIGH', status='OPEN',
        ptype='JavaWeb', tech='Spring Boot, MyBatis-Plus, MySQL, Vue3, 企业微信 API',
        budget=6800, deadline='2026-11-30 00:00:00',
        creator=101, created='2026-10-19 09:24:00', prepaid=1,
    ),
    dict(
        id=2,
        title='微信小程序商城，含拼团与分销',
        content='我们是做地方特产的，现在主要靠朋友圈卖，想有一个自己的小程序商城。'
                '用户能在小程序里下单、参与拼团，老客户可以申请成为分销员拿佣金。'
                '后台需要管理商品、订单、团长和提现申请。支付走微信支付，物流先用快递单号手动录入。',
        req='1. 商品支持多规格、库存和上下架，首页可配置轮播与专题；\n'
            '2. 拼团：2 人成团，未成团自动退款，超时 24 小时关闭；\n'
            '3. 分销：二级分佣，佣金比例按商品单独设置，满 50 元可申请提现；\n'
            '4. 订单状态完整（待付款 / 待发货 / 待收货 / 已完成 / 退款中），支持一键发货和批量导入快递单号；\n'
            '5. 后台按角色分权：运营只管商品和活动，财务只管提现和流水；\n'
            '6. 提供 3 个月免费维护，上线后帮忙配置域名和 HTTPS。',
        category='OTHER', conf=0.88, priority='P1', pscore=0.84, urgency='HIGH', status='OPEN',
        ptype='Mobile', tech='微信小程序, uni-app, Vue3, Node.js, MySQL, 微信支付',
        budget=12000, deadline='2026-12-05 00:00:00',
        creator=102, created='2026-10-19 14:08:00', prepaid=1,
    ),
    dict(
        id=3,
        title='园区能耗监测数据大屏',
        content='客户是园区管理方，要把 6 栋楼的水电表数据实时汇总到一块大屏上，放在展厅给领导参观。'
                '现在数据已经通过网关推到我们服务器，需要做一个可视化页面，按楼栋、楼层切换，异常能耗要标红。',
        req='1. 大屏按 1920×1080 适配，4K 下等比缩放不变形；\n'
            '2. 总览页展示总用电、总用水、今日峰值和同比环比；\n'
            '3. 楼栋页用柱状图对比各层能耗，点击可下钻到具体表计；\n'
            '4. 数据通过 WebSocket 实时刷新，断线自动重连并在角落提示；\n'
            '5. 超标阈值可在配置文件里调整，超限的表计置顶闪烁；\n'
            '6. 交付源码和部署说明，能容器化最好。',
        category='OTHER', conf=0.9, priority='P2', pscore=0.62, urgency='NORMAL', status='OPEN',
        ptype='Frontend', tech='Vue3, TypeScript, ECharts, WebSocket, Vite',
        budget=4200, deadline='2026-11-15 00:00:00',
        creator=107, created='2026-10-19 10:32:00', promoted=1,
    ),
    dict(
        id=4,
        title='图书借阅管理系统（毕业设计）',
        content='本科毕业设计，题目是图书借阅管理系统。学校要求有完整的借还流程、读者管理和统计报表，'
                '代码要能跑起来参加答辩演示。我用 Java 比较多，希望是 Spring Boot 那一套。',
        req='1. 读者注册、借书、还书、续借，超期自动计算罚金；\n'
            '2. 图书按 ISBN 管理，支持批量导入 Excel 书单；\n'
            '3. 管理员可查看借阅排行、超期名单和按月统计；\n'
            '4. 前端用 Vue3，页面别太丑，答辩时能演示清晰即可；\n'
            '5. 附带数据库设计文档和部署说明；\n'
            '6. 需要远程帮我讲一遍代码逻辑，答辩可能被问。',
        category='CONSULT', conf=0.83, priority='P2', pscore=0.58, urgency='NORMAL', status='OPEN',
        ptype='JavaWeb', tech='Spring Boot, MyBatis, MySQL, Vue3, Element Plus',
        budget=1500, deadline='2026-11-10 00:00:00',
        creator=103, created='2026-10-18 20:15:00',
    ),
    dict(
        id=5,
        title='企业官网重构，响应式与 SEO 优化',
        content='现在官网是五年前做的静态页，手机打开错位严重，百度也搜不到内容。'
                '想重做一版，注重打开速度和 SEO，能自己发新闻和案例。设计稿我们出，你们按稿实现就行。',
        req='1. 首页、产品、案例、新闻、关于我们、联系我们六个页面；\n'
            '2. 响应式适配 375 到 1920，移动端首屏不横向滚动；\n'
            '3. 新闻和案例做成后台可发布，支持富文本和封面图；\n'
            '4. 服务端渲染或静态生成，Lighthouse 性能分 90 以上；\n'
            '5. 配置 sitemap、robots.txt 和结构化数据；\n'
            '6. 迁移旧站文章约 120 篇，保留原 URL 做 301。',
        category='OTHER', conf=0.87, priority='P2', pscore=0.61, urgency='NORMAL', status='OPEN',
        ptype='Frontend', tech='Next.js, TypeScript, Tailwind CSS, MDX',
        budget=3600, deadline='2026-11-20 00:00:00',
        creator=104, created='2026-10-18 16:40:00',
    ),
    dict(
        id=6,
        title='招聘平台岗位数据采集与薪酬分析入库',
        content='我们要做行业薪酬分析，需要定期采集几个招聘网站上目标岗位的 JD、薪资区间和公司规模。'
                '之前用脚本抓过一部分，网站改版后失效了。希望重做一套能稳定跑的采集程序，结果入库。',
        req='1. 采集岗位名称、公司、城市、薪资区间、经验、学历、发布时间和 JD 正文；\n'
            '2. 支持按关键词和城市配置任务，频率可调，避免被封 IP；\n'
            '3. 薪资文本统一解析成月薪下限和上限两个数值字段；\n'
            '4. 数据去重（同一岗位多平台重复），保留来源平台字段；\n'
            '5. 输出到 MySQL，并给一个查询页面按关键词和城市筛选；\n'
            '6. 交付部署脚本，需要能在 Linux 上挂定时任务跑。',
        category='OTHER', conf=0.86, priority='P1', pscore=0.8, urgency='HIGH', status='OPEN',
        ptype='Python', tech='Python, Scrapy, Playwright, MySQL, pandas',
        budget=2200, deadline='2026-11-05 00:00:00',
        creator=107, created='2026-10-17 11:05:00', prepaid=1,
    ),
    dict(
        id=7,
        title='少儿编程机构学员与课时管理后台',
        content='我们有三家校区、两千多个学员，现在用 Excel 排课和记录课时，经常对不上账。'
                '需要一套系统管学员、课程包、排课和课时消耗，家长也能在小程序里看剩余课时。',
        req='1. 学员档案：基本信息、报读课程、剩余课时、上课记录；\n'
            '2. 课程包可配置课时数和有效期，支持转课与退费；\n'
            '3. 排课按教室和教师维度检测冲突，支持批量排一个学期；\n'
            '4. 上课签到后自动扣课时，教师端支持手机签到；\n'
            '5. 家长端小程序查看课表和剩余课时，收到上课提醒；\n'
            '6. 财务模块按校区和月份统计收入、退费、欠费，可导出 Excel。',
        category='OTHER', conf=0.89, priority='P1', pscore=0.82, urgency='NORMAL', status='OPEN',
        ptype='FullStack', tech='Spring Boot, MySQL, Vue3, 微信小程序, Redis',
        budget=8000, deadline='2026-12-01 00:00:00',
        creator=105, created='2026-10-17 09:30:00', promoted=1,
    ),
    dict(
        id=8,
        title='私有知识库智能客服，接入企业微信',
        content='我们有上万条设备手册和售后问答，客服每天重复回答相同问题。'
                '想做一个接入企业微信的智能客服：用户提问先查知识库，答不上来再转人工。',
        req='1. 支持上传 PDF、Word、Markdown，自动切分并生成向量入库；\n'
            '2. 回答必须给出引用的原文段落，避免模型编造；\n'
            '3. 企业微信客服接口对接，用户消息进来先检索知识库；\n'
            '4. 知识库文档可增删改，重新索引不中断服务；\n'
            '5. 后台能看会话记录和未命中问题，方便补充文档；\n'
            '6. 提供 Docker Compose 一键部署，模型走我们自己的 API。',
        category='OTHER', conf=0.92, priority='P0', pscore=0.95, urgency='CRITICAL', status='OPEN',
        ptype='Python', tech='Python, FastAPI, LangChain, Milvus, PostgreSQL, React',
        budget=15000, deadline='2026-11-12 00:00:00',
        creator=107, created='2026-10-16 15:20:00', prepaid=1, promoted=1,
    ),
    dict(
        id=9,
        title='JSP 老系统迁移到 Spring Boot + Vue3',
        content='客户有一套 2012 年的 JSP 系统，业务跑得还行但没人敢改，服务器也快到期。'
                '要迁到 Spring Boot + Vue3 前后端分离，数据库结构基本保留，历史数据要完整搬过去。',
        req='1. 梳理现有 68 个功能页，分批迁移，先做登录、权限和最常用的 30 个页面；\n'
            '2. 保留原系统的角色权限模型，迁移后权限行为一致；\n'
            '3. 前端拆成独立项目，接口按模块拆分，原有 URL 尽量保持不变；\n'
            '4. 历史数据迁移脚本可重复执行，且支持回滚；\n'
            '5. 新旧系统并行运行两周，期间数据双写；\n'
            '6. 交付迁移方案文档，配合客户做一次上线演练。',
        category='OTHER', conf=0.85, priority='P1', pscore=0.87, urgency='HIGH', status='OPEN',
        ptype='JavaWeb', tech='Spring Boot, MyBatis, Vue3, MySQL, Redis, Nginx',
        budget=20000, deadline='2026-12-20 00:00:00',
        creator=108, created='2026-10-16 10:00:00',
    ),
    dict(
        id=10,
        title='电商商品详情页性能优化，首屏 2 秒内',
        content='我们商城详情页在 4G 网络下要 5 秒才能看到主图，跳出率很高。'
                '希望找有前端性能经验的人做一轮优化，Lighthouse 移动端性能分从 42 提到 80 以上。',
        req='1. 定位首屏阻塞资源，给出优化前后的性能报告；\n'
            '2. 图片转 WebP / AVIF，按需加载并生成多尺寸 srcset；\n'
            '3. 路由和组件做代码分割，首屏 JS 体积降到 200KB 以内；\n'
            '4. 主图骨架屏与预加载，避免布局抖动；\n'
            '5. 第三方脚本延迟加载，不影响首屏；\n'
            '6. 交付改动清单和回滚方案，不引入新框架。',
        category='OTHER', conf=0.84, priority='P2', pscore=0.66, urgency='HIGH', status='OPEN',
        ptype='Frontend', tech='Vue3, Vite, Lighthouse, WebP',
        budget=3000, deadline='2026-10-30 00:00:00',
        creator=102, created='2026-10-15 21:12:00',
    ),
    dict(
        id=11,
        title='理发店在线预约小程序',
        content='朋友开了两家理发店，现在预约靠微信私聊，经常撞单。'
                '想做个简单的预约小程序：顾客选门店、发型师和时间段，店里能看到当天排期。',
        req='1. 门店、发型师、服务项目可后台维护，每个项目有参考时长；\n'
            '2. 顾客选时间后锁定 10 分钟，未确认自动释放；\n'
            '3. 同一发型师同一时段不可重复预约；\n'
            '4. 店里端能看当日和本周排期，支持手动加单和取消；\n'
            '5. 预约前 2 小时发微信订阅消息提醒；\n'
            '6. 后台支持导出月度到店记录。',
        category='OTHER', conf=0.9, priority='P2', pscore=0.55, urgency='NORMAL', status='OPEN',
        ptype='Mobile', tech='微信小程序, 微信云开发, JavaScript',
        budget=4800, deadline='2026-11-18 00:00:00',
        creator=106, created='2026-10-15 19:05:00',
    ),
    dict(
        id=12,
        title='财务月报自动化脚本（Excel 汇总与图表）',
        content='每月初财务要把 6 个部门的报销表合并、分类汇总、生成月报，手工要花一整天。'
                '想写个脚本，读取固定格式的 Excel，自动生成汇总表和图表，减少出错。',
        req='1. 读取指定文件夹下的部门报销表，字段固定但行数不定；\n'
            '2. 按费用类型和部门汇总，计算同比环比；\n'
            '3. 输出带格式的 Excel（冻结首行、金额千分位、超支标红）；\n'
            '4. 自动生成费用构成图和月度趋势图，嵌入表格；\n'
            '5. 异常数据（缺部门、金额非数字、重复单号）单独列出提示；\n'
            '6. 双击 bat 就能运行，不需要装 Python。',
        category='OTHER', conf=0.81, priority='P3', pscore=0.33, urgency='LOW', status='OPEN',
        ptype='Python', tech='Python, openpyxl, pandas, matplotlib, PyInstaller',
        budget=1200, deadline='2026-10-28 00:00:00',
        creator=103, created='2026-10-15 14:22:00',
    ),
    dict(
        id=13,
        title='在线考试系统，含防作弊与自动阅卷',
        content='培训业务需要给学员做结课考试，题库有两千多道。'
                '想做一个在线考试系统：老师在后台组卷，学员在浏览器答题，交卷后客观题自动判分。',
        req='1. 题库支持单选、多选、判断、填空，按知识点和难度标签分类；\n'
            '2. 组卷可手动选题或按规则随机抽题，支持试卷预览和导出 PDF；\n'
            '3. 考试限时，倒计时到点自动交卷，刷新页面不丢失答题进度；\n'
            '4. 防作弊：切屏次数记录、禁止复制粘贴、题目和选项乱序；\n'
            '5. 交卷后客观题自动判定，主观题由老师批注打分；\n'
            '6. 成绩单和错题分析，学员可以查看解析。',
        category='OTHER', conf=0.9, priority='P1', pscore=0.83, urgency='HIGH', status='OPEN',
        ptype='FullStack', tech='Spring Boot, MySQL, Redis, Vue3, Element Plus',
        budget=9800, deadline='2026-11-25 00:00:00',
        creator=105, created='2026-10-14 17:30:00', prepaid=1,
    ),
    dict(
        id=14,
        title='公司内部知识库全文检索',
        content='公司内部的规范、方案、会议纪要都散在共享盘和钉钉里，新人找资料只能靠问人。'
                '想做一个统一搜索入口，能全文检索 Word、PDF 和 Markdown。',
        req='1. 接入共享盘目录和指定钉钉群文件，定时同步增量文件；\n'
            '2. 支持按文件名和内容全文检索，中文分词准确；\n'
            '3. 搜索结果显示高亮片段、文件类型和所属目录；\n'
            '4. 权限继承目录结构，没有权限的文件不出现在结果里；\n'
            '5. 支持在线预览 Office 和 PDF，无需下载；\n'
            '6. 后台可配置抓取目录和同步频率，并有同步日志。',
        category='OTHER', conf=0.88, priority='P2', pscore=0.64, urgency='NORMAL', status='OPEN',
        ptype='JavaWeb', tech='Spring Boot, Elasticsearch, Vue3, MinIO',
        budget=7600, deadline='2026-12-02 00:00:00',
        creator=108, created='2026-10-14 10:48:00',
    ),
    dict(
        id=15,
        title='招商会 H5 报名页与现场核销',
        content='客户下个月有一场线下招商会，需要一个 H5 报名页：扫码进入填写信息，'
                '报名成功后生成电子票二维码，现场核销。',
        req='1. 单页 H5，深色品牌视觉，滚动有入场动画；\n'
            '2. 表单收集姓名、公司、职位、手机号，手机号做格式校验；\n'
            '3. 提交后生成唯一电子票，含二维码和报名编号；\n'
            '4. 现场扫码核销，重复核销提示已入场；\n'
            '5. 后台能看报名列表、导出 Excel、实时统计核销人数；\n'
            '6. 兼容微信内置浏览器，首屏 1.5 秒内可交互。',
        category='OTHER', conf=0.86, priority='P2', pscore=0.59, urgency='NORMAL', status='OPEN',
        ptype='Frontend', tech='Vue3, Vant, Node.js, MySQL',
        budget=2600, deadline='2026-10-31 00:00:00',
        creator=102, created='2026-10-13 15:10:00',
    ),
    dict(
        id=16,
        title='蓝牙温湿度传感器数据采集与告警服务',
        content='现场有 300 多台蓝牙温湿度传感器，网关能收到广播数据。'
                '需要做一个服务把数据解析入库，并提供实时曲线和超温告警。',
        req='1. 服务常驻运行，从 MQTT 主题订阅网关上报的原始数据；\n'
            '2. 按设备 MAC、温湿度、电量、时间戳解析并写入时序库；\n'
            '3. 温度超过阈值时推送企业微信告警，5 分钟内不重复推送；\n'
            '4. 支持按设备、时间段查询曲线，自动聚合降采样；\n'
            '5. 设备离线超过 10 分钟标记为离线并提示；\n'
            '6. 提供 Grafana 看板模板，现场人员能直接看。',
        category='OTHER', conf=0.87, priority='P1', pscore=0.79, urgency='HIGH', status='OPEN',
        ptype='Python', tech='Python, paho-mqtt, InfluxDB, Grafana, Docker',
        budget=5600, deadline='2026-11-08 00:00:00',
        creator=107, created='2026-10-13 09:20:00',
    ),
    dict(
        id=17,
        title='物业报修小程序与派单后台',
        content='物业公司现在用电话登记报修，派单和回访全靠本子记。'
                '想做一个报修小程序：业主拍照报修，后台按区域派给师傅，修完业主评价。',
        req='1. 业主选择楼栋房号和问题类型，上传 1-3 张照片并描述问题；\n'
            '2. 后台按片区自动分配给对应师傅，也可手动改派；\n'
            '3. 师傅接单、到场、完工三个状态分别记录时间；\n'
            '4. 完工需上传现场照片，业主收到通知后确认并评价；\n'
            '5. 超时未接单自动升级给主管，超过 24 小时未完工标红；\n'
            '6. 后台统计各片区工单量、平均处理时长和满意度。',
        category='OTHER', conf=0.89, priority='P1', pscore=0.77, urgency='NORMAL', status='OPEN',
        ptype='FullStack', tech='微信小程序, Spring Boot, MySQL, Vue3',
        budget=11000, deadline='2026-12-10 00:00:00',
        creator=106, created='2026-10-12 20:40:00', prepaid=1,
    ),
    dict(
        id=18,
        title='论文查重系统（毕业设计，本地文档比对）',
        content='毕业设计想做一个本地论文查重系统：把自己的论文和往届论文库比对，'
                '给出重复率和重复段落。不需要联网，答辩时演示即可。',
        req='1. 上传 docx 或 txt 论文，自动提取正文，去掉目录和参考文献；\n'
            '2. 与本地论文库比对，用 SimHash 或余弦相似度计算段落相似度；\n'
            '3. 输出总重复率和标红的重复段落，给出来源论文；\n'
            '4. 论文库支持批量导入往届论文并建立索引；\n'
            '5. 前端用简单页面展示比对报告，可导出 PDF；\n'
            '6. 代码有注释，附设计文档，能讲清楚算法原理。',
        category='CONSULT', conf=0.8, priority='P2', pscore=0.57, urgency='NORMAL', status='OPEN',
        ptype='Python', tech='Python, Flask, scikit-learn, jieba, MySQL',
        budget=2000, deadline='2026-11-14 00:00:00',
        creator=103, created='2026-10-12 16:12:00',
    ),
    dict(
        id=19,
        title='经营数据日报自动推送企业微信',
        content='每天早上 9 点要把前一天的销售额、订单数、客单价、退款率发到管理层企业微信群。'
                '现在人工复制粘贴，节假日经常忘，想做成自动任务。',
        req='1. 从现有 MySQL 只读账号拉取前一天数据，不能影响线上；\n'
            '2. 生成纯文本和一张趋势图，按企业微信机器人格式推送；\n'
            '3. 销售额环比下降超过 20% 时消息前加醒目标记；\n'
            '4. 推送失败重试 3 次，并把失败原因发到运维群；\n'
            '5. 支持手动补发生成任意一天的数据；\n'
            '6. 用 cron 部署在服务器上，附操作说明。',
        category='OTHER', conf=0.85, priority='P2', pscore=0.53, urgency='LOW', status='OPEN',
        ptype='Python', tech='Python, pandas, MySQL, 企业微信 API, matplotlib',
        budget=1800, deadline='2026-10-26 00:00:00',
        creator=104, created='2026-10-12 11:30:00',
    ),
    dict(
        id=20,
        title='多语言官网内容管理后台（中英日）',
        content='公司要开拓日本市场，官网需要中、英、日三语，内容由各国市场同事自己维护，'
                '不想每次改文案都找技术。需要一个内容管理后台。',
        req='1. 页面按区块管理，三种语言分别填写，可一键复制其他语言再修改；\n'
            '2. 保存草稿、预览、发布三个状态，发布前需市场负责人审核；\n'
            '3. 修改历史可追溯，能回滚到上一个版本；\n'
            '4. 图片上传自动压缩并生成多尺寸；\n'
            '5. 前端页面读取已发布内容，切换语言不刷新整页；\n'
            '6. 接入现有账号体系，用邮箱登录即可。',
        category='OTHER', conf=0.88, priority='P2', pscore=0.6, urgency='NORMAL', status='OPEN',
        ptype='FullStack', tech='Node.js, Next.js, PostgreSQL, React, Prisma',
        budget=8800, deadline='2026-12-08 00:00:00',
        creator=104, created='2026-10-11 14:45:00', prepaid=1,
    ),

    # ---------------- 开发中 ----------------
    dict(
        id=21,
        title='社区团购小程序（自提点 + 团长分佣）',
        content='我们做社区生鲜，有 40 多个团长，现在下单靠微信群接龙，统计对账很乱。'
                '需要一个小程序：居民下单后到自提点取货，团长能看到自己团的订单和佣金。',
        req='1. 按小区选择自提点，商品分今日达和明日至；\n'
            '2. 下单后生成取货码，团长端核销；\n'
            '3. 团长佣金按订单实付比例计算，次日结算到余额；\n'
            '4. 平台端管理商品、自提点、团长和结算单；\n'
            '5. 支持缺货退款和部分退款，退款原路返回；\n'
            '6. 每日订单按自提点自动汇总成拣货单，可打印。',
        category='OTHER', conf=0.9, priority='P1', pscore=0.85, urgency='HIGH', status='ACCEPTED',
        ptype='Mobile', tech='微信小程序, Spring Boot, MySQL, Redis, 微信支付',
        budget=13500, deadline='2026-11-28 00:00:00',
        creator=102, created='2026-10-08 09:15:00', handler=109, accepted='2026-10-15 10:20:00',
        prepaid=1,
        ai=dict(
            summary='社区团购的核心是「自提点」和「团长」两条独立的结算线：订单按自提点汇总拣货，'
                    '佣金按团长归集。需求里未说明的是缺货时的退款边界，建议在合同里约定。',
            tech='建议订单与佣金分表：订单表记录自提点和团长快照，佣金流水单独建表按日结算，'
                 '避免团长换绑后历史佣金错乱。取货码用短码 + 订单号校验，防止伪造。',
            tasks='1. 自提点与团长模型；2. 下单与支付回调；3. 取货码核销；4. 佣金结算；'
                  '5. 拣货单汇总；6. 退款与部分退款。',
            hours=120,
        ),
    ),
    dict(
        id=22,
        title='仓储 WMS 系统（库位、批次、PDA 扫码）',
        content='客户有一个 8000 平米的仓库，现在出入库靠 Excel 和纸单，盘一次库要三天。'
                '需要一套 WMS：管库位、批次和库存流水，支持 PDA 扫码作业。',
        req='1. 商品、库位、批次基础资料维护，支持 Excel 导入；\n'
            '2. 入库：收货、质检、上架，PDA 扫库位码确认；\n'
            '3. 出库：波次拣货、复核、发货，缺货自动生成补货建议；\n'
            '4. 库存实时准确，任何变动都有流水可查；\n'
            '5. 盘点支持按库位和按商品，差异生成调整单；\n'
            '6. 报表：库龄分析、周转率、呆滞库存。',
        category='OTHER', conf=0.91, priority='P1', pscore=0.86, urgency='HIGH', status='ACCEPTED',
        ptype='JavaWeb', tech='Spring Boot, MySQL, Redis, Vue3, PDA 安卓端',
        budget=18000, deadline='2026-12-15 00:00:00',
        creator=108, created='2026-10-06 14:00:00', handler=110, accepted='2026-10-16 09:10:00',
        ai=dict(
            summary='这是典型的重流程仓储系统，难点在库存一致性而不是界面：任何库存变动都必须'
                    '落在流水上，并且 PDA 与后台要能离线容错。',
            tech='库存表建议用「可用量 + 锁定量」双字段，扣减用带条件的 UPDATE 保证并发安全；'
                 'PDA 端本地缓存任务，断网时先记本地队列，恢复后按时间顺序回放。',
            tasks='1. 基础资料；2. 入库流程；3. 出库波次；4. 库存流水与盘点；5. PDA 端；6. 报表。',
            hours=160,
        ),
    ),
    dict(
        id=23,
        title='校园二手交易平台（毕业设计）',
        content='毕业设计题目是校园二手交易平台，需要商品发布、搜索、聊天、下单和评价。'
                '学校要求技术栈体现前后端分离，答辩时演示完整流程。',
        req='1. 用户注册登录，校园认证（学号邮箱验证）；\n'
            '2. 商品发布支持多图和分类，可按分类和关键词搜索；\n'
            '3. 站内聊天，买卖双方可以议价；\n'
            '4. 下单、确认收货、评价，支持举报违规商品；\n'
            '5. 管理端审核商品、处理举报、查看统计；\n'
            '6. 附开题报告需要的系统架构图和数据库设计文档。',
        category='CONSULT', conf=0.82, priority='P2', pscore=0.56, urgency='NORMAL', status='ACCEPTED',
        ptype='FullStack', tech='Spring Boot, MySQL, Vue3, WebSocket',
        budget=2500, deadline='2026-11-20 00:00:00',
        creator=103, created='2026-10-09 19:30:00', handler=111, accepted='2026-10-17 10:05:00',
    ),
    dict(
        id=24,
        title='短视频素材库后台（标签、预览、项目隔离）',
        content='内容团队每天产出大量短视频，素材存在网盘里，找一条素材要翻半天。'
                '需要一个素材库：能按标签、项目、上传人检索，并支持给素材打时间点标记。',
        req='1. 素材上传后自动提取封面、时长和分辨率；\n'
            '2. 多级标签体系，支持批量打标签；\n'
            '3. 检索按标签、上传人、时间、项目组合过滤；\n'
            '4. 视频在线预览，可在时间点添加备注，方便剪辑对接；\n'
            '5. 权限按项目隔离，外部协作者只能看指定项目；\n'
            '6. 存储用对象存储，上传走分片和断点续传。',
        category='OTHER', conf=0.87, priority='P2', pscore=0.63, urgency='NORMAL', status='ACCEPTED',
        ptype='FullStack', tech='Node.js, React, PostgreSQL, MinIO, FFmpeg',
        budget=9600, deadline='2026-11-22 00:00:00',
        creator=102, created='2026-10-05 10:30:00', handler=112, accepted='2026-10-14 15:40:00',
    ),
    dict(
        id=25,
        title='连锁门店收银系统（扫码、会员、库存）',
        content='有三家烘焙店，收银机还是老式的，会员和库存都不联网。'
                '想换一套收银系统，支持扫码收款、会员储值和库存扣减，月底能看各店报表。',
        req='1. PC 收银端支持扫码枪、钱箱和小票打印机；\n'
            '2. 商品管理、改价、折扣和组合套餐；\n'
            '3. 会员储值、积分和优惠券，储值卡可挂失；\n'
            '4. 销售自动扣减库存，原料和成品分开管理；\n'
            '5. 交班对账：现金、微信、支付宝分开统计；\n'
            '6. 总部后台看各店营业额、客单价、畅销商品。',
        category='OTHER', conf=0.88, priority='P1', pscore=0.78, urgency='HIGH', status='ACCEPTED',
        ptype='JavaWeb', tech='Spring Boot, MySQL, Vue3, Electron',
        budget=7200, deadline='2026-11-25 00:00:00',
        creator=105, created='2026-10-07 11:20:00', handler=113, accepted='2026-10-18 09:35:00',
    ),
    dict(
        id=26,
        title='人脸识别考勤系统对接门禁一体机',
        content='工厂有 600 多名员工，现在打卡靠指纹，冬天手干经常打不上。'
                '想接门禁一体机做人脸考勤，数据同步到考勤系统，月底导出工时。',
        req='1. 对接现有海康门禁设备，读取识别记录；\n'
            '2. 员工人脸照片批量导入设备，支持新增和删除同步；\n'
            '3. 考勤规则可配置：班次、弹性时间、加班计算；\n'
            '4. 异常记录（迟到、早退、缺卡）自动标记，可申请补卡；\n'
            '5. 月末导出考勤表和工时统计，格式按客户模板；\n'
            '6. 现场部署调试，配合客户 IT 完成网络打通。',
        category='OTHER', conf=0.9, priority='P0', pscore=0.94, urgency='CRITICAL', status='ACCEPTED',
        ptype='Python', tech='Python, FastAPI, MySQL, 海康 ISAPI, Docker',
        budget=14000, deadline='2026-11-10 00:00:00',
        creator=107, created='2026-10-04 16:50:00', handler=114, accepted='2026-10-19 08:40:00',
        ai=dict(
            summary='硬件对接类需求，最大风险不在考勤规则而在现场网络和设备固件差异。'
                    '建议先确认设备型号和 ISAPI 版本，再做排期。',
            tech='建议先做一层设备适配接口，把不同型号的 ISAPI 差异吃掉；考勤计算用独立服务，'
                 '按天预计算并落库，避免月底批量算导致超时。',
            tasks='1. 设备对接验证；2. 人员同步；3. 考勤规则引擎；4. 异常与补卡；5. 报表导出；6. 现场部署。',
            hours=96,
        ),
    ),
    dict(
        id=27,
        title='OA 审批待办打通企业微信',
        content='公司用企业微信办公，但 OA 是独立的，领导要在两个地方审批。'
                '希望把 OA 的待办推送到企业微信，能直接在企微里通过或驳回。',
        req='1. 企业微信自建应用，员工免登进入 OA 待办；\n'
            '2. OA 待办生成企微消息卡片，含单号和关键字段；\n'
            '3. 点击卡片可跳转详情，也能直接在卡片上通过或驳回；\n'
            '4. 审批结果回写 OA，状态保持一致；\n'
            '5. 消息发送失败要有重试和日志；\n'
            '6. 协助申请企业微信应用和配置可信域名。',
        category='OTHER', conf=0.86, priority='P2', pscore=0.61, urgency='NORMAL', status='ACCEPTED',
        ptype='JavaWeb', tech='Spring Boot, 企业微信 API, MySQL, Redis',
        budget=6800, deadline='2026-11-18 00:00:00',
        creator=101, created='2026-10-10 10:12:00', handler=115, accepted='2026-10-18 14:20:00',
    ),
    dict(
        id=28,
        title='商城物流轨迹统一查询与订单同步',
        content='商城后台现在要发货后手动去各家快递网站查物流，客户来问才回。'
                '需要一个接口统一查询顺丰、中通、圆通的轨迹，并在后台显示。',
        req='1. 对接至少 3 家快递的查询接口，统一返回格式；\n'
            '2. 按运单号和快递公司查询，结果缓存 30 分钟；\n'
            '3. 后台订单详情展示轨迹时间线，最新状态同步到订单；\n'
            '4. 签收后自动把订单改成已完成；\n'
            '5. 查询失败或超限时降级为手动填写；\n'
            '6. 提供接口文档和调用示例。',
        category='OTHER', conf=0.84, priority='P2', pscore=0.6, urgency='NORMAL', status='ACCEPTED',
        ptype='Python', tech='Python, FastAPI, Redis, MySQL',
        budget=3800, deadline='2026-11-12 00:00:00',
        creator=102, created='2026-10-11 09:05:00', handler=116, accepted='2026-10-19 11:15:00',
    ),
    dict(
        id=29,
        title='培训学校自动排课算法与课表导出',
        content='培训学校每个学期排课要人工排两周，还是会有教师和教室冲突。'
                '需要一个自动排课功能：给定教师、班级、教室和课程周次，生成无冲突课表。',
        req='1. 基础数据：教师、班级、教室、课程、每周课时；\n'
            '2. 硬约束：同一教师 / 教室 / 班级同一时段不冲突；\n'
            '3. 软约束：教师连堂不超过 2 节、尽量不排晚课，冲突时给出可读提示；\n'
            '4. 算法时间控制在 10 秒内，排不出来给出原因；\n'
            '5. 生成课表支持手工拖拽调整，调整后再次校验；\n'
            '6. 课表可导出 Excel 和 PDF，按班级和教师两种视角。',
        category='OTHER', conf=0.89, priority='P1', pscore=0.81, urgency='HIGH', status='ACCEPTED',
        ptype='Python', tech='Python, OR-Tools, FastAPI, Vue3',
        budget=5400, deadline='2026-11-16 00:00:00',
        creator=105, created='2026-10-09 15:40:00', handler=110, accepted='2026-10-17 16:30:00',
    ),
    dict(
        id=30,
        title='H5 商城 Vue2 升级 Vue3 + Vite',
        content='我们的 H5 商城是 Vue2 + Vant2 写的，依赖好几个已经停止维护，打包很慢。'
                '想升级到 Vue3 + Vite，功能和页面保持一致，顺便把首屏提升到 2 秒内。',
        req='1. 逐页迁移，保持现有交互和样式一致；\n'
            '2. 组件库从 Vant2 换到 Vant4，弹窗和选择器行为对齐；\n'
            '3. 状态管理从 Vuex 换到 Pinia，购物车逻辑重写；\n'
            '4. 首屏体积下降 50% 以上，路由按需加载；\n'
            '5. 迁移期间保留旧版可回滚，按页面灰度；\n'
            '6. 交付迁移文档，说明每个坑的处理方式。',
        category='OTHER', conf=0.85, priority='P2', pscore=0.65, urgency='NORMAL', status='ACCEPTED',
        ptype='Frontend', tech='Vue3, Vite, Vant4, Pinia, TypeScript',
        budget=9200, deadline='2026-11-20 00:00:00',
        creator=102, created='2026-10-06 17:20:00', handler=109, accepted='2026-10-16 09:50:00',
    ),
    dict(
        id=31,
        title='会员积分与优惠券体系接入商城',
        content='商城要上线积分体系：下单得积分、积分抵现，还要支持满减券、折扣券和新人券。'
                '希望接进现有订单流程，别影响结算。',
        req='1. 积分规则可配置：订单完成发放、退款扣回、有效期；\n'
            '2. 优惠券类型：满减、折扣、新人、定向发放；\n'
            '3. 结算时自动计算最优券组合，用户可手动取消；\n'
            '4. 优惠金额按商品分摊，退款时按分摊退回；\n'
            '5. 管理后台可批量发券、查看核销率；\n'
            '6. 并发安全，防止同一张券被重复使用。',
        category='OTHER', conf=0.88, priority='P1', pscore=0.76, urgency='NORMAL', status='ACCEPTED',
        ptype='JavaWeb', tech='Spring Boot, MySQL, Redis, Vue3',
        budget=6400, deadline='2026-11-14 00:00:00',
        creator=101, created='2026-10-08 13:30:00', handler=111, accepted='2026-10-15 15:10:00',
    ),
    dict(
        id=32,
        title='行业展会预约小程序与现场签到',
        content='下个月有一场行业展会，观众需要在小程序上预约参观时段，主办方要控制每个时段的人流。'
                '现场扫码签到。',
        req='1. 观众选择日期和时段预约，填写人数和单位；\n'
            '2. 每个时段有名额上限，满额不可选；\n'
            '3. 预约成功生成电子票，含二维码；\n'
            '4. 现场扫码签到，重复签到提示；\n'
            '5. 主办方后台看各时段预约和签到人数，实时刷新；\n'
            '6. 活动前一天发服务通知提醒。',
        category='OTHER', conf=0.87, priority='P2', pscore=0.58, urgency='HIGH', status='ACCEPTED',
        ptype='Mobile', tech='微信小程序, 微信云开发, 微信订阅消息',
        budget=5800, deadline='2026-10-30 00:00:00',
        creator=102, created='2026-10-07 09:00:00', handler=112, accepted='2026-10-18 10:30:00',
    ),

    # ---------------- 待验收 ----------------
    dict(
        id=33,
        title='集团利润分析 BI 看板（5 家子公司）',
        content='集团有 5 家子公司，每月财务报表在各自系统里，合并分析要财务手工做。'
                '想做一个利润分析看板，按公司、产品线、月份下钻，领导能随时看。',
        req='1. 接入 5 个业务库，每日凌晨抽数到分析库；\n'
            '2. 看板展示收入、成本、毛利、净利，支持同比环比；\n'
            '3. 按公司、产品线、客户维度下钻，图表联动；\n'
            '4. 指标口径可在后台配置，计算公式可维护；\n'
            '5. 支持导出当期分析报告 PDF；\n'
            '6. 权限按公司隔离，子公司只能看自己。',
        category='OTHER', conf=0.9, priority='P1', pscore=0.84, urgency='HIGH', status='PENDING_APPROVAL',
        ptype='FullStack', tech='Spring Boot, ClickHouse, Vue3, ECharts',
        budget=7800, deadline='2026-11-05 00:00:00',
        creator=108, created='2026-09-28 10:20:00', handler=113, accepted='2026-10-15 09:30:00',
        proof='https://github.com/henghui-demo/profit-bi',
        ai=dict(
            summary='跨 5 家子公司的利润分析看板，核心是把分散的财务数据按日汇总到分析库，'
                    '再以多维下钻方式呈现。指标口径配置是需求里最容易扯皮的部分。',
            tech='分析库选 ClickHouse 合适，抽数用定时任务 + 增量水位。口径配置建议做成'
                 '「指标 - 公式 - 数据源」三段式，改口径不用发版。',
            tasks='1. 数据接入与抽数；2. 指标口径配置；3. 看板与下钻；4. 权限隔离；5. 报告导出。',
            hours=110,
        ),
    ),
    dict(
        id=34,
        title='宠物医院预约小程序与电子病历',
        content='宠物医院现在电话预约，医生排班和宠物档案都在纸上。'
                '想做小程序：宠主能预约、看诊记录和疫苗提醒，医生能看当天排班。',
        req='1. 宠主建档：宠物昵称、品种、年龄、绝育情况、疫苗记录；\n'
            '2. 预约选医生和时段，医生排班后台维护；\n'
            '3. 到院签到后进入候诊队列，显示前面还有几位；\n'
            '4. 看诊后医生填写诊断和处理，生成电子病历；\n'
            '5. 疫苗和驱虫到期自动提醒；\n'
            '6. 会员储值和消费记录。',
        category='OTHER', conf=0.86, priority='P2', pscore=0.62, urgency='NORMAL', status='PENDING_APPROVAL',
        ptype='Mobile', tech='微信小程序, Spring Boot, MySQL',
        budget=6500, deadline='2026-11-15 00:00:00',
        creator=102, created='2026-10-02 14:10:00', handler=114, accepted='2026-10-16 10:00:00',
        proof='https://gitee.com/shiguang/pet-hospital',
        ai=dict(
            summary='宠物医疗预约系统，业务主体是「宠物档案 + 预约排班 + 电子病历」。'
                    '需求里没提退款和爽约规则，验收时容易漏。',
            tech='预约表按医生 + 时段建唯一索引，避免并发重复预约；病历用结构化字段 + '
                 '富文本描述，方便后续统计常见病。',
            tasks='1. 宠物档案；2. 排班与预约；3. 候诊队列；4. 电子病历；5. 提醒。',
            hours=80,
        ),
    ),
    dict(
        id=35,
        title='PHP 官网迁移到 Next.js 静态站',
        content='官网是 2015 年的 PHP 网站，服务器要下线了，需要迁到 Next.js 静态站。'
                '页面不多，但编辑器是给市场部用的，得能继续发文。',
        req='1. 迁移 8 个页面和 300 多篇新闻，URL 保持不变；\n'
            '2. 新闻用 Headless CMS 管理，市场部能自己发；\n'
            '3. 旧文章里的图片迁移到对象存储并替换链接；\n'
            '4. 移动端适配，首屏性能分 90 以上；\n'
            '5. 配置 301 跳转和 sitemap；\n'
            '6. 提供部署文档，运行在客户自己的服务器。',
        category='OTHER', conf=0.85, priority='P2', pscore=0.61, urgency='NORMAL', status='PENDING_APPROVAL',
        ptype='Frontend', tech='Next.js, TypeScript, CMS, Tailwind CSS',
        budget=8200, deadline='2026-11-08 00:00:00',
        creator=104, created='2026-09-30 11:00:00', handler=115, accepted='2026-10-14 09:20:00',
        proof='https://github.com/zhouyt/website-migration',
    ),
    dict(
        id=36,
        title='销售 CRM 客户跟进与合同回款',
        content='销售团队 20 多人，客户信息在个人微信和 Excel 里，人员离职就带走客户。'
                '需要一套 CRM，管线索、跟进记录和成交。',
        req='1. 线索录入、分配、回收，支持批量导入；\n'
            '2. 跟进记录含通话、微信、拜访，可上传截图；\n'
            '3. 客户阶段漏斗，超过 N 天未跟进自动提醒；\n'
            '4. 成交后生成合同和回款计划；\n'
            '5. 销售只能看自己的客户，主管看团队；\n'
            '6. 统计各销售线索量、转化率、回款额。',
        category='OTHER', conf=0.89, priority='P1', pscore=0.8, urgency='NORMAL', status='PENDING_APPROVAL',
        ptype='JavaWeb', tech='Spring Boot, MySQL, Vue3, Redis',
        budget=15500, deadline='2026-11-10 00:00:00',
        creator=101, created='2026-09-29 09:40:00', handler=116, accepted='2026-10-15 13:00:00',
        proof='https://github.com/yunqi/crm-demo',
        ai=dict(
            summary='CRM 的实质是「客户归属权」问题：线索如何分配、离职如何回收、'
                    '数据权限如何隔离。功能本身不复杂，权限模型要先定。',
            tech='建议数据权限用「本人 / 团队 / 全部」三级 + 部门树，在查询层统一注入，'
                 '不要在每个接口里各写一遍。跟进提醒用定时任务扫描最后跟进时间。',
            tasks='1. 线索池；2. 跟进记录；3. 阶段漏斗；4. 合同回款；5. 数据权限；6. 统计。',
            hours=140,
        ),
    ),
    dict(
        id=37,
        title='商品图片批量压缩加水印桌面工具',
        content='运营每天要处理几百张商品图，手动改尺寸加水印太慢。'
                '需要一个桌面小工具：拖进去一批图，按预设批量压缩、改尺寸、加水印。',
        req='1. 支持拖拽文件夹，递归处理子目录；\n'
            '2. 预设多种尺寸（主图、详情图、缩略图），一次输出多份；\n'
            '3. 水印支持文字和图片，可调位置、透明度、边距；\n'
            '4. 压缩后体积控制在原图 30% 以内，肉眼无明显损失；\n'
            '5. 处理进度和失败清单可见，失败可重试；\n'
            '6. 打包成 exe，双击即用，不需要装环境。',
        category='OTHER', conf=0.83, priority='P2', pscore=0.55, urgency='NORMAL', status='PENDING_APPROVAL',
        ptype='Python', tech='Python, PyQt6, Pillow, PyInstaller',
        budget=2400, deadline='2026-11-02 00:00:00',
        creator=102, created='2026-10-01 16:30:00', handler=111, accepted='2026-10-16 09:15:00',
        proof='https://gitee.com/shiguang/img-tool',
    ),
    dict(
        id=38,
        title='Spring Boot 生产环境 Docker 部署方案',
        content='我们的 Spring Boot 项目要上阿里云，之前是开发直接 scp 上去，没有监控和备份。'
                '需要一套规范的 Docker 部署方案和运维文档。',
        req='1. Dockerfile 多阶段构建，镜像体积尽量小；\n'
            '2. docker-compose 编排应用、MySQL、Redis、Nginx；\n'
            '3. Nginx 配置 HTTPS、gzip、静态资源和反向代理；\n'
            '4. 日志按天切割并保留 30 天，接入简单监控；\n'
            '5. MySQL 每日自动备份到 OSS，保留 7 天；\n'
            '6. 提供上线、回滚和常见故障处理文档。',
        category='CONSULT', conf=0.79, priority='P2', pscore=0.54, urgency='NORMAL', status='PENDING_APPROVAL',
        ptype='Other', tech='Docker, Docker Compose, Nginx, MySQL, 阿里云',
        budget=3200, deadline='2026-10-28 00:00:00',
        creator=108, created='2026-09-26 10:15:00', handler=110, accepted='2026-10-17 09:00:00',
        proof='https://github.com/henghui/deploy-kit',
    ),
    dict(
        id=39,
        title='小程序直播带货组件接入与直播间配置',
        content='我们的商城小程序想加直播功能：主播在小程序里开播，观众边看边下单。'
                '不自己开发直播能力，用微信小程序直播组件。',
        req='1. 申请并开通小程序直播能力，配置直播间；\n'
            '2. 首页增加直播入口，开播时显示悬浮条；\n'
            '3. 直播间挂商品，点击跳转商品详情；\n'
            '4. 直播预约和开播提醒；\n'
            '5. 后台管理直播场次和回放；\n'
            '6. 协助完成类目资质和组件审核。',
        category='OTHER', conf=0.82, priority='P2', pscore=0.57, urgency='NORMAL', status='PENDING_APPROVAL',
        ptype='Mobile', tech='微信小程序, 微信直播组件, Spring Boot',
        budget=6800, deadline='2026-11-06 00:00:00',
        creator=102, created='2026-10-03 13:20:00', handler=109, accepted='2026-10-15 10:40:00',
        proof='https://gitee.com/shiguang/live-module',
    ),

    # ---------------- 已完成 ----------------
    dict(
        id=40,
        title='高校学生选课系统（高并发抢课）',
        content='学校的选课系统太老，开放选课就崩。需要重做一套，支持 3000 人同时抢课，名额不能超选。',
        req='1. 学生端选课、退课，已选课程表展示；\n'
            '2. 教师端查看选课名单、导出 Excel；\n'
            '3. 课程容量并发安全，不能超选；\n'
            '4. 选课高峰期限流排队，界面显示排队位置；\n'
            '5. 与教务系统对接课程和成绩；\n'
            '6. 管理员可调整选课时间窗口。',
        category='OTHER', conf=0.9, priority='P1', pscore=0.86, urgency='HIGH', status='COMPLETED',
        ptype='JavaWeb', tech='Spring Boot, MySQL, Redis, Vue3',
        budget=8600, deadline='2026-10-15 00:00:00',
        creator=105, created='2026-09-12 10:00:00', handler=112, accepted='2026-09-18 09:00:00',
        completed='2026-10-12 16:00:00', proof='https://github.com/qifan/course-selection',
        ai=dict(
            summary='选课系统的核心风险是并发抢课，需要在名额扣减上做原子操作，并配合排队限流。'
                    '教务系统对接属于外部依赖，排期要留缓冲。',
            tech='名额扣减用 Redis 预扣 + 数据库最终一致：先在 Redis 做原子减，成功后再异步落库；'
                 '超额时直接返回而非重试。排队用令牌桶 + 前端轮询位置。',
            tasks='1. 课程与容量模型；2. 并发扣减；3. 排队与限流；4. 教务对接；5. 教师端与报表。',
            hours=130,
        ),
    ),
    dict(
        id=41,
        title='库存预警邮件机器人',
        content='仓库管理员每天要手动核对库存，经常断货才发现。'
                '想做一个定时任务，低于安全库存就发邮件提醒采购。',
        req='1. 每天 8 点和 14 点检查一次库存；\n'
            '2. 低于安全库存的商品按供应商分组发邮件；\n'
            '3. 邮件含商品编码、名称、当前库存、建议采购量；\n'
            '4. 安全库存可按商品单独配置；\n'
            '5. 连续 3 天未补货的升级抄送主管；\n'
            '6. 记录发送日志，避免重复发送。',
        category='OTHER', conf=0.8, priority='P3', pscore=0.32, urgency='LOW', status='COMPLETED',
        ptype='Python', tech='Python, pandas, MySQL, smtplib',
        budget=1900, deadline='2026-10-10 00:00:00',
        creator=106, created='2026-09-15 09:30:00', handler=116, accepted='2026-09-19 10:00:00',
        completed='2026-10-09 11:30:00', proof='https://gitee.com/chenmo/stock-alert',
    ),
    dict(
        id=42,
        title='双十一活动落地页（移动端）',
        content='双十一要上一个活动落地页，包含爆款直降、领券和倒计时，风格喜庆但要有质感，移动端为主。',
        req='1. 视觉稿由我们出，页面按稿实现并适配 375-1440；\n'
            '2. 爆款商品卡片从接口取，价格和库存实时；\n'
            '3. 领券按钮有状态（已领 / 已抢光 / 未开始）；\n'
            '4. 倒计时到点自动刷新活动状态；\n'
            '5. 埋点上报曝光和点击，接现有统计；\n'
            '6. 活动结束后页面可一键下线。',
        category='OTHER', conf=0.84, priority='P2', pscore=0.6, urgency='HIGH', status='COMPLETED',
        ptype='Frontend', tech='HTML5, CSS3, JavaScript, Vue3',
        budget=2800, deadline='2026-10-10 00:00:00',
        creator=102, created='2026-09-20 14:00:00', handler=115, accepted='2026-09-22 09:00:00',
        completed='2026-10-08 18:00:00', proof='https://gitee.com/shiguang/1111-page',
    ),
    dict(
        id=43,
        title='公司食堂订餐与取餐码小程序',
        content='食堂有 400 多人就餐，每天中午排队很长。'
                '想做个订餐小程序，员工提前一天选餐，食堂按订单备餐。',
        req='1. 每周菜单维护，含图片、价格、库存份数；\n'
            '2. 员工前一天 16 点前订餐，可取消；\n'
            '3. 取餐码扫码取餐，重复取餐提示；\n'
            '4. 食堂端看当日各餐品汇总数量；\n'
            '5. 餐补按部门结算，每月导出明细；\n'
            '6. 支持停餐和请假，自动扣减。',
        category='OTHER', conf=0.87, priority='P2', pscore=0.59, urgency='NORMAL', status='COMPLETED',
        ptype='Mobile', tech='微信小程序, 微信云开发, 微信支付',
        budget=5600, deadline='2026-10-05 00:00:00',
        creator=106, created='2026-09-10 11:00:00', handler=113, accepted='2026-09-15 10:00:00',
        completed='2026-10-06 15:00:00', proof='https://gitee.com/chenmo/canteen',
    ),
    dict(
        id=44,
        title='CRM 重复客户识别与数据清洗',
        content='销售离职后 CRM 里留下大量重复客户和脏数据，手机号格式混乱，公司名写法不一。'
                '需要一次性清洗，并给出合并规则。',
        req='1. 按手机号、公司名、邮箱识别疑似重复客户；\n'
            '2. 输出重复清单和建议保留记录；\n'
            '3. 合并时保留跟进记录和合同，不能丢数据；\n'
            '4. 清洗前后各出一份统计报告；\n'
            '5. 清洗脚本可重复执行，带事务回滚；\n'
            '6. 在测试库先跑一遍，确认无误再上生产。',
        category='OTHER', conf=0.81, priority='P2', pscore=0.52, urgency='NORMAL', status='COMPLETED',
        ptype='Python', tech='Python, pandas, MySQL',
        budget=2200, deadline='2026-10-01 00:00:00',
        creator=101, created='2026-09-08 16:00:00', handler=114, accepted='2026-09-12 09:00:00',
        completed='2026-10-03 10:00:00', proof='https://github.com/yunqi/crm-clean',
    ),
    dict(
        id=45,
        title='校园导航 H5 地图与步行路线',
        content='学校迎新需要一个 H5 校园地图，新生扫码能看到教学楼、食堂、宿舍和报到点，并给出步行路线。',
        req='1. 手绘风格地图，支持双指缩放和拖动；\n'
            '2. 主要建筑标注，点击弹出简介和照片；\n'
            '3. 选择目的地后画出步行路线和预计时间；\n'
            '4. 定位当前位置，允许用户手动纠正；\n'
            '5. 兼容低端安卓机，页面体积控制在 800KB；\n'
            '6. 后台可维护点位信息。',
        category='OTHER', conf=0.83, priority='P2', pscore=0.53, urgency='NORMAL', status='COMPLETED',
        ptype='Frontend', tech='Vue3, Leaflet, Vant, Node.js',
        budget=3400, deadline='2026-09-28 00:00:00',
        creator=105, created='2026-09-05 10:00:00', handler=111, accepted='2026-09-10 09:00:00',
        completed='2026-09-30 17:00:00', proof='https://github.com/qifan/campus-map',
    ),
    dict(
        id=46,
        title='报销发票 OCR 识别与录入',
        content='员工报销要手工录入发票信息，财务核对很慢。'
                '想接入 OCR，拍照自动识别发票号码、金额、日期和税号，直接填进报销单。',
        req='1. 拍照或上传发票图片，自动识别关键字段；\n'
            '2. 支持增值税专票、普票、电子发票和出租车票；\n'
            '3. 识别结果可人工修正后保存；\n'
            '4. 同一发票号码重复报销时提示；\n'
            '5. 识别失败的图片转人工队列；\n'
            '6. 按调用量统计 OCR 成本，超预算告警。',
        category='OTHER', conf=0.86, priority='P1', pscore=0.75, urgency='NORMAL', status='COMPLETED',
        ptype='Python', tech='Python, 阿里云 OCR, FastAPI, MySQL',
        budget=7200, deadline='2026-09-30 00:00:00',
        creator=108, created='2026-09-06 14:00:00', handler=110, accepted='2026-09-11 09:00:00',
        completed='2026-09-28 16:00:00', proof='https://github.com/henghui/ocr-reimburse',
        ai=dict(
            summary='发票 OCR 的关键是识别后的人工校正流程和重复报销校验，识别本身依赖云服务，'
                    '精度主要看票据类型覆盖。',
            tech='OCR 调用封装成适配层，先本地预检图片质量再做识别，降低无效调用成本；'
                 '发票号 + 开票日期建唯一索引防重。',
            tasks='1. 上传与预处理；2. OCR 适配；3. 字段校正页；4. 重复校验；5. 成本统计。',
            hours=90,
        ),
    ),

    # ---------------- 被驳回 ----------------
    dict(
        id=47,
        title='电商后台商品批量改价工具',
        content='大促前要把 2000 多个商品调价，后台一个一个改太慢。'
                '想要一个工具：导入 Excel 批量改价，并保留改价记录。',
        req='1. 导入 Excel（商品编码、新价格），校验编码和价格范围；\n'
            '2. 批量更新，单次最多 5000 条，失败行单独列出原因；\n'
            '3. 改价前自动备份原价，支持一键回滚；\n'
            '4. 记录操作人和时间，可在后台查询；\n'
            '5. 需要权限控制，只有主管能执行。',
        category='OTHER', conf=0.85, priority='P2', pscore=0.58, urgency='HIGH', status='REJECTED',
        ptype='JavaWeb', tech='Spring Boot, MySQL, Vue3',
        budget=4600, deadline='2026-11-05 00:00:00',
        creator=102, created='2026-10-09 10:00:00', handler=109, accepted='2026-10-17 09:20:00',
        reject='交付的批量改价没有做价格范围校验，测试时把 199 元的商品改成了 1.99 元；'
               '也没有原价备份和回滚，无法恢复到改价前状态。请补齐校验与备份逻辑，'
               '另外导入失败的行要给出具体原因，不能只提示失败条数。',
    ),
    dict(
        id=48,
        title='销售日报自动生成与推送',
        content='销售每天下班前要写日报，内容重复。'
                '想自动从 CRM 拉当天跟进记录，生成一段日报草稿，销售改改就能发。',
        req='1. 从 CRM 拉当天新增线索、跟进次数、成交金额；\n'
            '2. 生成一段 150 字左右的日报草稿，语气自然；\n'
            '3. 销售可编辑后保存，历史日报可查；\n'
            '4. 支持按周汇总；\n'
            '5. 邮件或企微发送给主管。',
        category='OTHER', conf=0.8, priority='P3', pscore=0.34, urgency='NORMAL', status='REJECTED',
        ptype='Python', tech='Python, FastAPI, MySQL, OpenAI API',
        budget=1600, deadline='2026-11-03 00:00:00',
        creator=101, created='2026-10-10 09:00:00', handler=115, accepted='2026-10-18 10:00:00',
        reject='生成的日报内容与 CRM 数据对不上：测试账号当天成交金额显示为 0，实际 CRM 里有 3 笔订单；'
               '另外日报语气偏机械，几乎是字段拼接。请先核对数据口径，再调整文案模板。',
    ),

    # ---------------- 已关闭 ----------------
    dict(
        id=49,
        title='旧 ERP 系统临时维护（按月）',
        content='之前的 ERP 需要人临时维护两个月，处理日常报错和月度结账问题，按月结算。',
        req='1. 工作日远程待命，2 小时内响应；\n'
            '2. 处理日常报错和简单的数据修正；\n'
            '3. 配合月度结账跑批；\n'
            '4. 每周给一份问题清单。',
        category='CONSULT', conf=0.78, priority='P2', pscore=0.5, urgency='NORMAL', status='CLOSED',
        ptype='Other', tech='PHP, MySQL',
        budget=2000, deadline='2026-11-30 00:00:00',
        creator=108, created='2026-09-25 10:00:00', closed='2026-10-10 09:00:00',
    ),
    dict(
        id=50,
        title='内部流程工具原型（仅验证）',
        content='想做一个内部用的小工具原型，先验证一下流程，暂时不需要上线。',
        req='1. 页面原型，能点通主要流程；\n'
            '2. 数据用假数据；\n'
            '3. 一周内给 demo。',
        category='OTHER', conf=0.75, priority='P3', pscore=0.3, urgency='LOW', status='CLOSED',
        ptype='Frontend', tech='HTML, CSS, JavaScript',
        budget=1500, deadline='2026-10-08 00:00:00',
        creator=104, created='2026-09-22 10:00:00', closed='2026-10-05 15:00:00',
    ),
]


def q(v):
    """SQL 字面量。反斜杠先转义，再把换行写成 \n（sql_mode 未开 NO_BACKSLASH_ESCAPES）。"""
    if v is None:
        return 'NULL'
    if isinstance(v, bool):
        return '1' if v else '0'
    if isinstance(v, (int, float)):
        return str(v)
    s = str(v).replace('\\', '\\\\').replace("'", "\\'").replace('\r', '')
    s = s.replace('\n', '\\n')
    return "'" + s + "'"


COLS = [
    'id', 'title', 'content', 'detailed_requirements', 'category', 'category_confidence',
    'priority', 'priority_score', 'urgency', 'status', 'project_type', 'tech_stack',
    'budget', 'deadline', 'creator_id', 'creator_name', 'handler_id', 'handler_name',
    'ai_prepaid', 'member_promoted', 'is_visible',
    'created_at', 'updated_at', 'accepted_at', 'completed_at', 'closed_at',
    'completion_proof', 'rejection_reason',
    'ai_summary', 'ai_tech_suggestion', 'ai_task_breakdown', 'ai_prd', 'ai_estimated_hours',
]


def ticket_values(t):
    ai = t.get('ai') or {}
    accepted = t.get('accepted')
    completed = t.get('completed')
    closed = t.get('closed')
    # updated_at 取最后一个业务事件
    updated = completed or closed or accepted or t['created']
    return [
        t['id'], t['title'], t['content'], t['req'], t['category'], t['conf'],
        t['priority'], t['pscore'], t['urgency'], t['status'], t['ptype'], t['tech'],
        t['budget'], t['deadline'], t['creator'], NAME[t['creator']],
        t.get('handler'), NAME[t['handler']] if t.get('handler') else None,
        t.get('prepaid', 0), t.get('promoted', 0), 1 if t['status'] == 'OPEN' else 0,
        t['created'], updated, accepted, completed, closed,
        t.get('proof'), t.get('reject'),
        ai.get('summary'), ai.get('tech'), ai.get('tasks'), ai.get('prd'), ai.get('hours'),
    ]


lines = []
lines.append('-- 由 generate_real_tickets.py 生成，请勿手工编辑。')
lines.append('-- 用途：清空现有工单并写入 50 张业务内容完整的演示工单。')
lines.append('SET NAMES utf8mb4;')
lines.append('START TRANSACTION;')
lines.append('')
lines.append('-- 1) 清空旧工单。ticket_chat / ticket_comment / file_attachment 都是 ON DELETE CASCADE；')
lines.append('--    ai_usage 没有外键，需要手工清掉引用这些工单的额度记录。')
lines.append('DELETE FROM ai_usage WHERE ticket_id IS NOT NULL AND ticket_id IN (SELECT id FROM ticket);')
lines.append('DELETE FROM ticket;')
lines.append('ALTER TABLE ticket AUTO_INCREMENT = 1;')
lines.append('')
lines.append('-- 2) 演示用的发布者与接单人（密码同 admin2，明文 123456）。')
lines.append('INSERT IGNORE INTO users (id, username, password, email, nickname, avatar_url, role, status, created_at, updated_at) VALUES')
rows = []
for uid, username, nickname, email, avatar, role, created in USERS:
    rows.append('(%d, %s, %s, %s, %s, %s, %s, %s, %s, %s)'
                % (uid, q(username), q(PASSWORD), q(email), q(nickname), q(avatar),
                   q(role), q('active'), q(created), q(created)))
lines.append(',\n'.join(rows) + ';')
lines.append('')
lines.append('-- 3) 50 张工单。')
lines.append('INSERT INTO ticket (%s) VALUES' % ', '.join(COLS))
rows = []
for t in TICKETS:
    rows.append('(' + ', '.join(q(v) for v in ticket_values(t)) + ')')
lines.append(',\n'.join(rows) + ';')
lines.append('')
lines.append('COMMIT;')
lines.append('')

with open(OUT, 'w', encoding='utf-8', newline='\n') as f:
    f.write('\n'.join(lines))

assert len(TICKETS) == 50, len(TICKETS)
assert len({t['id'] for t in TICKETS}) == 50
print('written %s, tickets=%d, lines=%d' % (OUT, len(TICKETS), len(lines)))

-- 把全部工单退回「待接单」并放回大厅。
-- 适用于 seed 之后的演示重置：所有单都能被接，进度、交付凭证、驳回理由一并清掉。
-- AI 产出（ai_summary / ai_tech_suggestion / ai_task_breakdown / ai_prd）**保留** ——
-- 它属于工单本身的分析结果，不是进度；AI 预附与优先曝光标记也保留，大厅排序层次不变。
-- 重跑安全：已是 OPEN 的单再跑一次没有副作用（时间字段本来就是 NULL）。
--
-- 2026-10 修：此前这里还 SET accepted_by = NULL, accepted_by_name = NULL。
-- 这两列对应的实体字段早已删除（见 Ticket.java「接单状态」段的注释），
-- 且 schema.sql 里从来没有它们 —— 只在 db/migration.sql 里 ALTER 过。
-- 也就是说本脚本在任何"新库"上都会 ERROR 1054。已删掉那两行。

SET NAMES utf8mb4;
START TRANSACTION;

UPDATE ticket
SET status            = 'OPEN',
    handler_id        = NULL,
    handler_name      = NULL,
    accepted_at       = NULL,
    completed_at      = NULL,
    resolved_at       = NULL,
    closed_at         = NULL,
    completion_proof  = NULL,
    rejection_reason  = NULL,
    is_visible        = 1,
    -- 没有后续事件了，更新时间退回创建时间，别让全站看起来"今天全被改过"
    updated_at        = created_at
WHERE deleted_at IS NULL;

-- 已完成 / 已关闭的单里有 8 张 deadline 已经过去。退回大厅后它们会变成
-- 「待接单但期限已过」，看起来像脏数据。按 id 错开顺延到未来 14~34 天。
UPDATE ticket
SET deadline = DATE_ADD(CURDATE(), INTERVAL (14 + (id * 5) % 21) DAY)
WHERE deleted_at IS NULL
  AND deadline IS NOT NULL
  AND deadline < CURDATE();

COMMIT;

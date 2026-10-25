-- ============================================================================
--  演示数据初始化：为「钱包功能上线之前」创建的工单补托管记录
-- ============================================================================
--
-- 背景
--   钱包功能是 2026-10 才上线的。在那之前创建的工单没有 ticket_escrow 行、
--   也没冻结过任何钱，于是工单详情页会显示「这张工单创建于钱包功能上线前，
--   不涉及资金结算」。现网 53 张在架工单全部如此 —— 一句本该是例外说明的话
--   挂在了几乎每张单上。
--
-- 这个脚本做什么
--   让这些历史工单「看起来像是走过资金流程」：
--     1) 给发布者注资（他们没有充过值，余额为 0，直接冻结会撞 available >= 0）
--     2) 建 ticket_escrow（round 1，FROZEN）
--     3) 记 FREEZE 流水
--     4) 对那张已经 COMPLETED 的历史单，补完整：冻结 → 结算给接单人
--
-- ⚠️ 这是**造数据**，不是真实交易
--   · 注资记成 RECHARGE，但 ref_type / ref_id / uniq_key 全部留 NULL ——
--     它不对应任何一笔支付宝订单，WalletService.fulfillRecharge 从没跑过。
--     流水列表里靠 "ref 全空 + remark" 就能认出来。
--   · 不做这件事的话，那 53 张单永远不会结算，接单人做完也拿不到钱；
--     做了之后，账本三条恒等式仍然成立（脚本末尾有校验）。
--
-- 可重复执行
--   所有步骤都带 `NOT IN (SELECT ticket_id FROM ticket_escrow)` 或
--   `NOT EXISTS` 守卫，跑第二遍不会重复注资。
--
-- 执行
--   mysql -uroot aiticket < db/seed/backfill_escrow_for_legacy_tickets.sql
-- ============================================================================

START TRANSACTION;

-- ---------------------------------------------------------------- 1. 注资
-- 只处理**非终态**的历史单：OPEN / ACCEPTED / PENDING_APPROVAL / REJECTED。
-- 终态（COMPLETED / CLOSED）单独处理，见第 4 步 —— 给它们建个 FROZEN 托管
-- 会永远卡在冻结里（没机会验收了）。
DROP TEMPORARY TABLE IF EXISTS _needs;
CREATE TEMPORARY TABLE _needs AS
SELECT t.creator_id AS uid,
       COUNT(*)     AS tickets,
       SUM(t.budget) AS need
FROM ticket t
WHERE t.deleted_at IS NULL
  AND t.budget > 0
  AND t.status IN ('OPEN', 'ACCEPTED', 'PENDING_APPROVAL', 'REJECTED')
  AND t.id NOT IN (SELECT ticket_id FROM ticket_escrow)
GROUP BY t.creator_id;

-- 钱包行不存在就先建（正常情况下 me() 已经懒创建过，但这脚本可能跑在别人机器上）
INSERT INTO user_wallet (user_id, available_amount, frozen_amount)
SELECT n.uid, 0, 0 FROM _needs n
WHERE NOT EXISTS (SELECT 1 FROM user_wallet w WHERE w.user_id = n.uid);

-- 注资流水。balance_after = 注资前可用余额 + 本次注资
INSERT INTO wallet_transaction
    (user_id, type, amount, frozen_delta, balance_after, ref_type, ref_id, escrow_round, uniq_key, remark)
SELECT n.uid, 'RECHARGE', n.need, 0,
       COALESCE(w.available_amount, 0) + n.need,
       NULL, NULL, NULL, NULL,
       CONCAT('演示数据初始化：为 ', n.tickets, ' 张历史工单补托管注资（非真实充值）')
FROM _needs n
LEFT JOIN user_wallet w ON w.user_id = n.uid;

-- 可用余额加上去
UPDATE user_wallet w
JOIN _needs n ON n.uid = w.user_id
SET w.available_amount = w.available_amount + n.need;

-- ---------------------------------------------------------------- 2. 建托管
INSERT INTO ticket_escrow (ticket_id, round, publisher_id, amount, status)
SELECT t.id, 1, t.creator_id, t.budget, 'FROZEN'
FROM ticket t
WHERE t.deleted_at IS NULL
  AND t.budget > 0
  AND t.status IN ('OPEN', 'ACCEPTED', 'PENDING_APPROVAL', 'REJECTED')
  AND t.id NOT IN (SELECT ticket_id FROM ticket_escrow);

-- ---------------------------------------------------------------- 3. 记 FREEZE 流水
-- balance_after 必须逐单递减才对得上账，所以用窗口函数累减。
-- 读的是上一步注资之后的可用余额（此处还没扣冻结）。
INSERT INTO wallet_transaction
    (user_id, type, amount, frozen_delta, balance_after, ref_type, ref_id, escrow_round, uniq_key, remark)
SELECT x.uid,
       'FREEZE',
       -x.amount,
       x.amount,
       x.start_balance - SUM(x.amount) OVER (
           PARTITION BY x.uid ORDER BY x.tid ROWS UNBOUNDED PRECEDING
       ),
       'TICKET', x.tid, 1,
       CONCAT('TICKET:', x.tid, ':1:FREEZE'),
       '演示数据初始化：历史工单补托管（冻结）'
FROM (
    SELECT t.creator_id AS uid,
           t.id         AS tid,
           t.budget     AS amount,
           COALESCE(w.available_amount, 0) AS start_balance
    FROM ticket t
    JOIN user_wallet w ON w.user_id = t.creator_id
    WHERE t.deleted_at IS NULL
      AND t.budget > 0
      AND t.status IN ('OPEN', 'ACCEPTED', 'PENDING_APPROVAL', 'REJECTED')
      AND t.id IN (SELECT ticket_id FROM ticket_escrow WHERE round = 1 AND status = 'FROZEN')
      AND NOT EXISTS (
          SELECT 1 FROM wallet_transaction x2
          WHERE x2.ref_type = 'TICKET' AND x2.ref_id = t.id
            AND x2.type = 'FREEZE' AND x2.escrow_round = 1)
) x;

-- 可用 → 冻结
UPDATE user_wallet w
JOIN _needs n ON n.uid = w.user_id
SET w.available_amount = w.available_amount - n.need,
    w.frozen_amount    = w.frozen_amount + n.need;

-- ---------------------------------------------------------------- 4. 历史 COMPLETED 单：补完整流程
-- 这类单已经完成、不会再走「验收通过」，只建 FROZEN 托管会让钱永远卡住。
-- 所以一次补完：注资 → 冻结 → 结算给接单人。
DROP TEMPORARY TABLE IF EXISTS _done;
CREATE TEMPORARY TABLE _done AS
SELECT t.id AS tid, t.creator_id AS publisher, t.handler_id AS handler, t.budget AS amount
FROM ticket t
WHERE t.deleted_at IS NULL
  AND t.budget > 0
  AND t.status = 'COMPLETED'
  AND t.handler_id IS NOT NULL
  AND t.id NOT IN (SELECT ticket_id FROM ticket_escrow);

INSERT INTO user_wallet (user_id, available_amount, frozen_amount)
SELECT d.publisher, 0, 0 FROM _done d
WHERE NOT EXISTS (SELECT 1 FROM user_wallet w WHERE w.user_id = d.publisher);
INSERT INTO user_wallet (user_id, available_amount, frozen_amount)
SELECT d.handler, 0, 0 FROM _done d
WHERE NOT EXISTS (SELECT 1 FROM user_wallet w WHERE w.user_id = d.handler);

-- 4a 注资给发布者
INSERT INTO wallet_transaction
    (user_id, type, amount, frozen_delta, balance_after, ref_type, ref_id, escrow_round, uniq_key, remark)
SELECT d.publisher, 'RECHARGE', SUM(d.amount), 0,
       COALESCE(w.available_amount, 0) + SUM(d.amount),
       NULL, NULL, NULL, NULL,
       '演示数据初始化：为历史已完成工单补托管注资（非真实充值）'
FROM _done d
JOIN user_wallet w ON w.user_id = d.publisher
GROUP BY d.publisher, w.available_amount;
UPDATE user_wallet w
JOIN (SELECT publisher, SUM(amount) s FROM _done GROUP BY publisher) d ON d.publisher = w.user_id
SET w.available_amount = w.available_amount + d.s;

-- 4b 建托管（直接标 SETTLED：这张单早完成了）
INSERT INTO ticket_escrow (ticket_id, round, publisher_id, amount, status)
SELECT d.tid, 1, d.publisher, d.amount, 'SETTLED' FROM _done d;

-- 4c 三条流水：冻结 / 从发布者划走 / 进接单人余额
INSERT INTO wallet_transaction
    (user_id, type, amount, frozen_delta, balance_after, ref_type, ref_id, escrow_round, uniq_key, remark)
SELECT d.publisher, 'FREEZE', -d.amount, d.amount,
       COALESCE(w.available_amount, 0) - d.amount,
       'TICKET', d.tid, 1, CONCAT('TICKET:', d.tid, ':1:FREEZE'),
       '演示数据初始化：历史已完成工单补托管（冻结）'
FROM _done d JOIN user_wallet w ON w.user_id = d.publisher;

INSERT INTO wallet_transaction
    (user_id, type, amount, frozen_delta, balance_after, ref_type, ref_id, escrow_round, uniq_key, remark)
SELECT d.publisher, 'SETTLE_OUT', 0, -d.amount,
       COALESCE(w.available_amount, 0) - d.amount,
       'TICKET', d.tid, 1, CONCAT('TICKET:', d.tid, ':1:SETTLE_OUT'),
       '演示数据初始化：历史已完成工单补结算（支出）'
FROM _done d JOIN user_wallet w ON w.user_id = d.publisher;

INSERT INTO wallet_transaction
    (user_id, type, amount, frozen_delta, balance_after, ref_type, ref_id, escrow_round, uniq_key, remark)
SELECT d.handler, 'SETTLE_IN', d.amount, 0,
       COALESCE(w.available_amount, 0) + d.amount,
       'TICKET', d.tid, 1, CONCAT('TICKET:', d.tid, ':1:SETTLE_IN'),
       '演示数据初始化：历史已完成工单补结算（收入）'
FROM _done d JOIN user_wallet w ON w.user_id = d.handler;

-- 4d 余额落地：发布者扣掉、接单人加上（发布者的钱最终走完 冻结→划走，净变化 0）
UPDATE user_wallet w
JOIN (SELECT publisher, SUM(amount) s FROM _done GROUP BY publisher) d ON d.publisher = w.user_id
SET w.available_amount = w.available_amount - d.s;

UPDATE user_wallet w
JOIN (SELECT handler, SUM(amount) s FROM _done GROUP BY handler) d ON d.handler = w.user_id
SET w.available_amount = w.available_amount + d.s;

COMMIT;

DROP TEMPORARY TABLE IF EXISTS _needs;
DROP TEMPORARY TABLE IF EXISTS _done;

-- ============================================================================
--  跑完自己验一遍（三条都必须返回空）
-- ============================================================================
-- SELECT w.user_id, w.available_amount - COALESCE(SUM(t.amount),0) AS diff
-- FROM user_wallet w LEFT JOIN wallet_transaction t ON t.user_id = w.user_id
-- GROUP BY w.user_id, w.available_amount HAVING diff <> 0;
--
-- SELECT w.user_id, w.frozen_amount - COALESCE(SUM(t.frozen_delta),0) AS diff
-- FROM user_wallet w LEFT JOIN wallet_transaction t ON t.user_id = w.user_id
-- GROUP BY w.user_id, w.frozen_amount HAVING diff <> 0;
--
-- SELECT w.user_id, w.frozen_amount - COALESCE((
--          SELECT SUM(e.amount) FROM ticket_escrow e
--          WHERE e.publisher_id = w.user_id AND e.status = 'FROZEN'),0) AS diff
-- FROM user_wallet w HAVING diff <> 0;

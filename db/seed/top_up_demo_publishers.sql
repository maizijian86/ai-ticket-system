-- ============================================================================
--  演示数据初始化（可选）：给「历史工单补托管」之后的发布者留一点可用余额
-- ============================================================================
--
-- 为什么需要
--   backfill_escrow_for_legacy_tickets.sql 只注资了"冻结所必需"的金额，所以那批
--   发布者跑完之后 available_amount = 0、frozen_amount = 全部在架工单预算之和。
--   数字上是对的（钱确实全押在单子里），但演示时他们想再发一张单会直接拿到
--   402「余额不足」—— 看起来像功能坏了。
--
-- 这个脚本做什么
--   把「可用余额 < 5000」的账号补到 5000（只补差额），记一条 RECHARGE 流水
--  （ref 全 NULL，不对应任何支付宝订单，remark 里写清是演示注资）。
--
-- 范围
--   只处理**当前有托管资金**的账号（也就是上一步注资过的那批发布者）
--   + 已登录演示账号 demo_qa_user(117)。其余账号不动。
--
-- 可重复执行：只有 available < 5000 才会补差额，第二遍不会重复加钱。
--
-- 执行
--   mysql -uroot aiticket < db/seed/top_up_demo_publishers.sql
-- ============================================================================

START TRANSACTION;

DROP TEMPORARY TABLE IF EXISTS _topup;
CREATE TEMPORARY TABLE _topup AS
SELECT w.user_id,
       5000 - w.available_amount AS delta
FROM user_wallet w
WHERE w.available_amount < 5000
  AND (
        w.frozen_amount > 0                                  -- 上一步注资过的发布者
     OR w.user_id = 117                                      -- 演示/联调账号 demo_qa_user
  );

-- 注资流水
INSERT INTO wallet_transaction
    (user_id, type, amount, frozen_delta, balance_after, ref_type, ref_id, escrow_round, uniq_key, remark)
SELECT p.user_id, 'RECHARGE', p.delta, 0,
       w.available_amount + p.delta,
       NULL, NULL, NULL, NULL,
       '演示数据初始化：可用余额补足到 5000（非真实充值）'
FROM _topup p JOIN user_wallet w ON w.user_id = p.user_id;

-- 余额落地
UPDATE user_wallet w
JOIN _topup p ON p.user_id = w.user_id
SET w.available_amount = w.available_amount + p.delta;

COMMIT;
DROP TEMPORARY TABLE IF EXISTS _topup;

-- ============================================================================
--  校验（三条都必须为空）
-- ============================================================================
-- SELECT w.user_id, w.available_amount - COALESCE(SUM(t.amount),0) diff
-- FROM user_wallet w LEFT JOIN wallet_transaction t ON t.user_id = w.user_id
-- GROUP BY w.user_id, w.available_amount HAVING diff <> 0;
--
-- SELECT w.user_id, w.frozen_amount - COALESCE(SUM(t.frozen_delta),0) diff
-- FROM user_wallet w LEFT JOIN wallet_transaction t ON t.user_id = w.user_id
-- GROUP BY w.user_id, w.frozen_amount HAVING diff <> 0;
--
-- SELECT w.user_id, w.frozen_amount - COALESCE((
--          SELECT SUM(e.amount) FROM ticket_escrow e
--          WHERE e.publisher_id = w.user_id AND e.status = 'FROZEN'),0) diff
-- FROM user_wallet w HAVING diff <> 0;

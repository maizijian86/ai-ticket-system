<template>
  <div class="result-page">
    <div class="glass-card-static result-card">
      <div class="result-icon" :class="stateClass">
        <el-icon size="30">
          <component :is="iconComponent" />
        </el-icon>
      </div>

      <h2 class="result-title">{{ title }}</h2>
      <p class="result-desc">{{ description }}</p>

      <div class="result-detail" v-if="order">
        <div class="detail-row">
          <span class="detail-label">订单号</span>
          <span class="detail-value mono">{{ order.outTradeNo }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">金额</span>
          <span class="detail-value">¥{{ order.amount }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">状态</span>
          <el-tag :type="getOrderStatusType(order.status) as any" size="small" effect="dark">
            {{ getOrderStatusLabel(order.status) }}
          </el-tag>
        </div>
        <div class="detail-row" v-if="order.paidAt">
          <span class="detail-label">支付时间</span>
          <span class="detail-value">{{ formatDateTime(order.paidAt) }}</span>
        </div>
        <div class="detail-row" v-if="order.expiresAt">
          <span class="detail-label">会员到期</span>
          <span class="detail-value">{{ formatDateTime(order.expiresAt) }}</span>
        </div>
      </div>

      <div class="result-actions">
        <el-button
          v-if="manualRefreshAvailable"
          class="btn-glass"
          :loading="refreshing"
          @click="manualRefresh"
        >
          刷新状态
        </el-button>
        <el-button class="btn-glass btn-glass-primary" @click="router.push('/profile')">
          查看我的订单
        </el-button>
        <el-button class="btn-glass" @click="router.push('/membership')">
          返回会员页
        </el-button>
      </div>

      <!-- 说明而不是承诺："1 分钟内到账"这种话在没有通知任务的前提下不成立 -->
      <p class="result-tip" v-if="order?.status === 'PENDING'">
        支付成功后额度会在几秒内到账。若你已经付款但状态仍停在「待支付」，
        通常是回执延迟 —— 点「刷新状态」即可；超过 15 分钟后刷新会主动向支付宝核对一次。
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { CircleCheckFilled, CircleCloseFilled, Loading, WarningFilled } from '@element-plus/icons-vue'
import { membershipApi } from '@/api'
import { getOrderStatusLabel, getOrderStatusType } from '@/utils/order-labels'
import type { MembershipOrderDTO } from '@/types'

const route = useRoute()
const router = useRouter()

/** 轮询间隔与上限：2 秒 × 30 次 = 1 分钟。再长下去用户只会盯着一个转圈。 */
const POLL_INTERVAL_MS = 2000
const MAX_POLLS = 30

const order = ref<MembershipOrderDTO | null>(null)
const refreshing = ref(false)
const exhausted = ref(false)
const loadFailed = ref(false)
let timer: ReturnType<typeof setInterval> | null = null
let polls = 0

/**
 * 单号有两个来源，都要认：
 *  - `out_trade_no` —— **支付宝同步跳转回来时自己加的**，参数名是蛇形；
 *  - `outTradeNo`   —— 我们下单后 router.push 时带过去的驼峰名，
 *                      以及用户从「我的订单」点进来的。
 * 只认一个的话，另一个入口进来会看到"缺少订单号"。
 */
const outTradeNo = computed(() => {
  const q = route.query
  return (q.out_trade_no as string) || (q.outTradeNo as string) || ''
})

const title = computed(() => {
  if (!outTradeNo.value) return '缺少订单号'
  if (loadFailed.value) return '查询失败'
  if (!order.value) return '正在确认支付结果…'
  switch (order.value.status) {
    case 'PAID':
      return '开通成功'
    case 'CLOSED':
      return '订单已关闭'
    default:
      return '等待支付确认'
  }
})

const description = computed(() => {
  if (!outTradeNo.value) {
    return '没有带上订单号，无法查询。请到个人主页的「我的订单」里查看。'
  }
  if (loadFailed.value) {
    return '暂时查不到这笔订单，请稍后重试或到「我的订单」里查看。'
  }
  if (!order.value) {
    return '正在与服务器核对，请稍候。'
  }
  switch (order.value.status) {
    case 'PAID':
      return `已为你发放 ${order.value.planCredits} 次 AI 辅助开发额度，有效期 ${order.value.planDays} 天。`
    case 'CLOSED':
      return '这笔订单已关闭（可能是支付超时或你关闭了支付页）。需要的话可以重新下单。'
    default:
      return exhausted.value
        ? '还没收到支付结果。若确实已付款，请点「刷新状态」。'
        : '收到支付结果后本页会自动更新，请不要关闭。'
  }
})

const stateClass = computed(() => {
  if (loadFailed.value) return 'error'
  if (!order.value) return 'loading'
  if (order.value.status === 'PAID') return 'success'
  if (order.value.status === 'CLOSED') return 'closed'
  return 'loading'
})

const iconComponent = computed(() => {
  if (loadFailed.value) return WarningFilled
  if (!order.value || order.value.status === 'PENDING') return Loading
  return order.value.status === 'PAID' ? CircleCheckFilled : CircleCloseFilled
})

/** 轮询停了、单还没终态时才给手动刷新的入口 —— 否则那按钮是个摆设 */
const manualRefreshAvailable = computed(
  () => !!order.value && order.value.status === 'PENDING' && exhausted.value
)

async function fetchOrder() {
  if (!outTradeNo.value) return
  try {
    const data = await membershipApi.getOrder(outTradeNo.value)
    order.value = data
    loadFailed.value = false
    // 到终态就停轮询，别让一个已经确定的结果继续每 2 秒打一次接口
    if (data.status !== 'PENDING') {
      stopPolling()
    }
  } catch (e: any) {
    stopPolling()
    // 401（未登录）也走这里：GET 订单要带身份，失败时给的是"查询失败"而不是假成功
    loadFailed.value = true
    ElMessage.error(e?.message || '查询订单失败')
  }
}

function startPolling() {
  stopPolling()
  polls = 0
  timer = setInterval(() => {
    polls += 1
    if (polls > MAX_POLLS) {
      stopPolling()
      exhausted.value = true
      return
    }
    fetchOrder()
  }, POLL_INTERVAL_MS)
}

function stopPolling() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

async function manualRefresh() {
  refreshing.value = true
  await fetchOrder()
  if (order.value?.status === 'PENDING') {
    exhausted.value = false
    startPolling()
  }
  refreshing.value = false
}

function formatDateTime(value?: string | null) {
  return value ? new Date(value).toLocaleString('zh-CN') : '-'
}

onMounted(() => {
  if (!outTradeNo.value) return
  fetchOrder()
  startPolling()
})

// 离开页面必须停掉，否则回到别的页面还在后台打接口（并且它还会改一个已经卸载的 ref）
onBeforeUnmount(stopPolling)
</script>

<style scoped>
.result-page {
  display: flex;
  justify-content: center;
  padding-top: 40px;
}

.result-card {
  width: 100%;
  max-width: 560px;
  padding: 40px 32px;
  text-align: center;
}

.result-icon {
  width: 64px;
  height: 64px;
  margin: 0 auto 20px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--glass-border);
  background: var(--glass-bg);
  color: var(--text-secondary);
}

.result-icon.success {
  color: var(--accent-success);
  border-color: var(--accent-success);
}

.result-icon.error {
  color: var(--accent-danger);
  border-color: var(--accent-danger);
}

.result-icon.closed {
  color: var(--text-tertiary);
}

.result-icon.loading {
  color: var(--accent-info);
  border-color: var(--accent-info);
}

.result-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-primary);
}

.result-desc {
  margin-top: 8px;
  font-size: 14px;
  color: var(--text-secondary);
}

.result-detail {
  margin: 24px 0;
  padding: 16px 20px;
  border-radius: var(--radius-md);
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  text-align: left;
}

.detail-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 8px 0;
  font-size: 13px;
}

.detail-label {
  color: var(--text-tertiary);
  flex-shrink: 0;
}

.detail-value {
  color: var(--text-primary);
  word-break: break-all;
  text-align: right;
}

/* 订单号是给人核对/报障用的，等宽字体下数字不会串行 */
.mono {
  font-family: var(--font-mono, ui-monospace, SFMono-Regular, Menlo, Consolas, monospace);
  font-size: 12px;
}

.result-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  flex-wrap: wrap;
}

.result-tip {
  margin-top: 20px;
  font-size: 12px;
  line-height: 1.8;
  color: var(--text-tertiary);
}
</style>

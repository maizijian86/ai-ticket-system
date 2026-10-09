<template>
  <div class="membership">
    <div class="page-header">
      <h2>开通会员</h2>
      <p class="subtitle">会员按 30 天一期，额度是账户级池子，全站通用</p>
    </div>

    <!-- 我的会员状态。余额与身份都来自 /membership/me 现查 —— store 里没有这些字段 -->
    <div class="glass-card-static status-card" v-loading="loading">
      <div class="status-main">
        <div class="status-icon" :class="{ active: membership?.isMember }">
          <el-icon size="22"><Medal /></el-icon>
        </div>
        <div class="status-text">
          <div class="status-title">
            {{ membership?.isMember ? '会员生效中' : '还不是会员' }}
          </div>
          <div class="status-detail">
            <template v-if="membership?.isMember">
              有效期至 {{ formatDate(membership.expiresAt) }}，本期发放 {{ membership.creditsGranted }} 次
            </template>
            <template v-else>
              开通后可获得更多 AI 辅助开发额度
            </template>
          </div>
        </div>
      </div>
      <div class="status-balance">
        <div class="balance-number">{{ membership?.remaining ?? 0 }}</div>
        <div class="balance-label">
          剩余次数<template v-if="membership?.nextExpireAt">（最近一批 {{ formatExpireDate(membership.nextExpireAt) }} 过期）</template>
        </div>
      </div>
    </div>

    <!-- 套餐。价格与次数**全部来自后端**（/membership/plans），前端一个字都不写死 -->
    <div class="plan-grid" v-loading="loading">
      <div v-for="plan in plans" :key="plan.planCode" class="glass-card plan-card">
        <div class="plan-name">{{ plan.name }}</div>
        <div class="plan-price">
          <span class="price-symbol">¥</span>
          <span class="price-number">{{ plan.price }}</span>
          <span class="price-unit">/ {{ plan.days }} 天</span>
        </div>
        <div class="plan-desc">{{ plan.desc }}</div>
        <ul class="plan-points">
          <li><el-icon><Check /></el-icon>{{ plan.credits }} 次 AI 辅助开发额度</li>
          <li><el-icon><Check /></el-icon>有效期 {{ plan.days }} 天，随用随扣</li>
          <li><el-icon><Check /></el-icon>未到期也能续，剩余天数不浪费</li>
        </ul>
        <el-button
          class="btn-glass btn-glass-primary plan-btn"
          :loading="creating === plan.planCode"
          :disabled="!!creating"
          @click="goPay(plan)"
        >
          {{ membership?.isMember ? '再买一期' : '立即开通' }}
        </el-button>
      </div>
    </div>

    <div class="glass-card-static benefits-card">
      <div class="benefits-title">权益说明</div>
      <ul class="benefits-list">
        <li>额度记在<strong>账户</strong>上，不按工单分配；一张工单上「AI 分析 / PRD 生成 / AI 对话」各消耗 1 次。</li>
        <li>到期后<strong>没用完的次数跟着作废</strong>，不能攒、不能顺延到下一期。</li>
        <li>会员未到期时再买一期，新周期从旧的到期日往后接，不会白扔剩余天数。</li>
        <li>购买后即时到账。已支付的订单不支持退款（会员是虚拟权益）。</li>
      </ul>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Medal, Check } from '@element-plus/icons-vue'
import { membershipApi } from '@/api'
import { formatExpireDate } from '@/utils/ai-quota'
import type { MembershipPlanDTO, MembershipDTO } from '@/types'

const router = useRouter()

const plans = ref<MembershipPlanDTO[]>([])
const membership = ref<MembershipDTO | null>(null)
const loading = ref(false)
/** 正在下单的套餐 code；同时兼作"全局只允许一个下单请求"的锁 */
const creating = ref<string | null>(null)

async function loadAll() {
  loading.value = true
  try {
    const [planList, mine] = await Promise.all([membershipApi.plans(), membershipApi.me()])
    plans.value = planList
    membership.value = mine
  } catch (e: any) {
    ElMessage.error(e?.message || '加载会员信息失败')
  } finally {
    loading.value = false
  }
}

/**
 * 开通/续费。
 *
 * **必须先同步开一个空窗口占位，再去 await 下单** —— 反过来写（先 await 拿到链接
 * 再 window.open）会被浏览器当成弹窗拦截，现象是"点了没反应"，不报任何错。
 * 所以这里的顺序不能"优化"。
 */
async function goPay(plan: MembershipPlanDTO) {
  const w = window.open('', '_blank')
  if (!w) {
    ElMessage.warning('浏览器拦截了新窗口，请允许本站弹出窗口后重试')
    return
  }

  creating.value = plan.planCode
  try {
    const res = await membershipApi.createOrder(plan.planCode)

    // 后端返回的 payUrl 正常是 https://…gateway.do?…&sign=… 的 URL；
    // 个别 SDK 版本返回的是自动提交的 form HTML，两种都处理掉 —— 免得版本一变
    // 就出现"点开通打开一个空白页"。
    if (/^https?:\/\//i.test(res.payUrl)) {
      w.location.href = res.payUrl
    } else {
      w.document.write(res.payUrl)
      w.document.close()
    }
    // 结果页自己读 ?out_trade_no=，但支付宝跳回来时带的参数名、以及用户手动刷新，
    // 都不保证一致，所以这里把单号写进 URL 一起带过去（结果页两种参数名都认）
    router.push({ path: '/membership/result', query: { outTradeNo: res.outTradeNo } })
  } catch (e: any) {
    // 下单失败就把占位窗口关掉，别留一个空白页在那儿
    w.close()
    ElMessage.error(e?.message || '创建订单失败，请稍后重试')
  } finally {
    creating.value = null
  }
}

function formatDate(value?: string | null) {
  return value ? new Date(value).toLocaleString('zh-CN') : '-'
}

onMounted(loadAll)
</script>

<style scoped>
.page-header {
  margin-bottom: 24px;
}

.page-header h2 {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.5px;
}

.subtitle {
  margin-top: 8px;
  font-size: 14px;
  color: var(--text-tertiary);
}

.status-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 24px;
  margin-bottom: 24px;
  flex-wrap: wrap;
}

.status-main {
  display: flex;
  align-items: center;
  gap: 16px;
}

.status-icon {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
}

.status-icon.active {
  color: #fff;
  background: linear-gradient(135deg, var(--accent-warning), var(--accent-danger));
  border: none;
}

.status-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.status-detail {
  margin-top: 4px;
  font-size: 13px;
  color: var(--text-tertiary);
}

.status-balance {
  text-align: right;
}

.balance-number {
  font-size: 30px;
  font-weight: 700;
  line-height: 1.1;
  color: var(--accent-info);
}

.balance-label {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-tertiary);
}

.plan-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 20px;
  margin-bottom: 24px;
}

.plan-card {
  padding: 24px;
  display: flex;
  flex-direction: column;
}

.plan-name {
  font-size: 17px;
  font-weight: 600;
  color: var(--text-primary);
}

.plan-price {
  margin: 12px 0;
  display: flex;
  align-items: baseline;
  gap: 4px;
  color: var(--text-primary);
}

.price-symbol {
  font-size: 18px;
  color: var(--accent-primary);
}

.price-number {
  font-size: 36px;
  font-weight: 700;
  line-height: 1;
}

.price-unit {
  font-size: 13px;
  color: var(--text-tertiary);
}

.plan-desc {
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 16px;
}

.plan-points {
  list-style: none;
  padding: 0;
  margin: 0 0 20px;
  flex: 1;
}

.plan-points li {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--text-secondary);
  padding: 6px 0;
}

.plan-points li .el-icon {
  color: var(--accent-success);
}

.plan-btn {
  width: 100%;
}

.benefits-card {
  padding: 20px 24px;
}

.benefits-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 12px;
}

.benefits-list {
  margin: 0;
  padding-left: 20px;
}

.benefits-list li {
  font-size: 13px;
  line-height: 1.9;
  color: var(--text-secondary);
}

/* 强调色统一走变量。深色背景下浏览器默认的 <strong> 仍然是黑色，
   不覆盖的话那两处强调反而比正文更难看清。 */
.benefits-list strong {
  color: var(--text-primary);
  font-weight: 600;
}
</style>

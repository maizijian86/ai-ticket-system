<template>
  <div class="profile">
    <div class="page-header">
      <h2>个人主页</h2>
    </div>

    <el-tabs v-model="activeTab" type="border-card" class="glass-card-static profile-tabs">
      <!-- ------------------------------------------------ 账号信息 -->
      <el-tab-pane label="账号信息" name="account">
        <el-descriptions :column="2" border v-loading="loadingAccount">
          <el-descriptions-item label="用户名">{{ account?.username || '-' }}</el-descriptions-item>
          <el-descriptions-item label="昵称">{{ account?.nickname || '-' }}</el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ account?.email || '-' }}</el-descriptions-item>
          <el-descriptions-item label="身份">
            <el-tag :type="getRoleType(account?.role || '') as any" size="small" class="glass-tag">
              {{ account ? getRoleLabel(account.role) : '-' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="账号状态">{{ statusLabel }}</el-descriptions-item>
          <el-descriptions-item label="注册时间">{{ formatDateTime(account?.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="最近登录">{{
            formatDateTime(account?.lastLoginAt)
          }}</el-descriptions-item>
          <el-descriptions-item label="用户 ID">{{ account?.id ?? '-' }}</el-descriptions-item>
        </el-descriptions>
        <p class="tab-tip">
          账号信息为只读。需要改昵称或邮箱请联系管理员（本期没有自助修改资料的入口）。
        </p>
      </el-tab-pane>

      <!-- ------------------------------------------------ 我的会员 -->
      <el-tab-pane label="我的会员" name="membership">
        <div class="membership-panel" v-loading="loadingMembership">
          <div class="membership-head">
            <div>
              <div class="membership-status">
                {{ membership?.isMember ? '会员生效中' : '还不是会员' }}
              </div>
              <div class="membership-sub">
                <template v-if="membership?.isMember && membership.expiresAt">
                  有效期至 {{ formatDateTime(membership.expiresAt) }}
                  <template v-if="membership.creditsGranted">
                    ，本期发放 {{ membership.creditsGranted }} 次
                  </template>
                </template>
                <template v-else>开通会员可获得更多 AI 辅助开发额度</template>
              </div>
            </div>
            <el-button class="btn-glass btn-glass-primary" @click="router.push('/membership')">
              {{ membership?.isMember ? '再买一期' : '去开通' }}
            </el-button>
          </div>

          <div class="quota-row">
            <div class="quota-item">
              <div class="quota-number">{{ membership?.remaining ?? 0 }}</div>
              <div class="quota-label">剩余次数</div>
            </div>
            <div class="quota-item">
              <div class="quota-number small">{{ formatExpireDate(membership?.nextExpireAt) || '不过期' }}</div>
              <div class="quota-label">最近一批过期时间</div>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- ------------------------------------------------ 我的订单 -->
      <el-tab-pane label="我的订单" name="orders">
        <el-table :data="orders" v-loading="loadingOrders" class="glass-table">
          <el-table-column prop="outTradeNo" label="订单号" min-width="220">
            <template #default="{ row }">
              <span class="mono">{{ row.outTradeNo }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="planCode" label="套餐" width="140">
            <template #default="{ row }">{{ row.planDays }} 天 / {{ row.planCredits }} 次</template>
          </el-table-column>
          <el-table-column prop="amount" label="金额" width="100">
            <template #default="{ row }">¥{{ row.amount }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="getOrderStatusType(row.status) as any" size="small" class="glass-tag">
                {{ getOrderStatusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="paidAt" label="支付时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.paidAt) }}</template>
          </el-table-column>
          <el-table-column prop="expiresAt" label="会员到期" width="170">
            <!-- 只有 PAID 的行有到期日；PENDING/CLOSED 显示 '-' 而不是算出来的天数 -->
            <template #default="{ row }">{{ formatDateTime(row.expiresAt) }}</template>
          </el-table-column>
          <el-table-column prop="createdAt" label="下单时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button
                link
                type="primary"
                @click="router.push({ path: '/membership/result', query: { outTradeNo: row.outTradeNo } })"
              >
                查看
              </el-button>
            </template>
          </el-table-column>
          <template #empty>
            <!-- 空表说明要写清"包括未支付和已关闭的单"：用户看到过一个"待支付"，
                 如果这行文案说"没有订单"，他会以为那张单丢了 -->
            <div class="empty-text">
              还没有会员订单。开通会员后在<router-link to="/membership">会员页</router-link>下单，
              未支付与已关闭的订单也会留在这里。
            </div>
          </template>
        </el-table>

        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="loadOrders"
          @size-change="loadOrders"
          class="pagination glass-pagination"
        />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { membershipApi, userApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { getOrderStatusLabel, getOrderStatusType } from '@/utils/order-labels'
import { getRoleLabel, getRoleType } from '@/utils/role-labels'
import { formatExpireDate } from '@/utils/ai-quota'
import type { MembershipDTO, MembershipOrderDTO, UserDTO } from '@/types'

const router = useRouter()
const authStore = useAuthStore()

const activeTab = ref('account')

const account = ref<UserDTO | null>(null)
const membership = ref<MembershipDTO | null>(null)
const orders = ref<MembershipOrderDTO[]>([])
const loadingAccount = ref(false)
const loadingMembership = ref(false)
const loadingOrders = ref(false)

const pagination = reactive({ page: 1, pageSize: 10, total: 0 })

/** 后端 user.status 是 VARCHAR，不是枚举；只有 'active' 是"能登录"的意思 */
const statusLabel = computed(() => {
  if (!account.value) return '-'
  return account.value.status === 'active' ? '正常' : '禁用'
})

/**
 * 账号信息**必须再拉一次接口**，不能只用 store 里的 userInfo：
 * store 那份只填了 id / username / nickname / role（email 还是写死的空串），
 * **注册时间 createdAt 根本不在里面** —— 只用 store 的话这一格永远是 '-'，
 * 而且看上去像是"这个账号没有注册时间"。
 */
async function loadAccount() {
  const id = authStore.userInfo?.id
  if (!id) return
  loadingAccount.value = true
  try {
    account.value = await userApi.getUser(id)
  } catch (e: any) {
    ElMessage.error(e?.message || '加载账号信息失败')
  } finally {
    loadingAccount.value = false
  }
}

async function loadMembership() {
  loadingMembership.value = true
  try {
    membership.value = await membershipApi.me()
  } catch (e: any) {
    ElMessage.error(e?.message || '加载会员信息失败')
  } finally {
    loadingMembership.value = false
  }
}

/** 只查自己的单：user_id 由后端从网关注入的身份强制，前端不传也不该传 */
async function loadOrders() {
  loadingOrders.value = true
  try {
    const res = await membershipApi.listOrders(pagination.page, pagination.pageSize)
    orders.value = res.data
    pagination.total = res.total
  } catch (e: any) {
    ElMessage.error(e?.message || '加载订单失败')
  } finally {
    loadingOrders.value = false
  }
}

function formatDateTime(value?: string | null) {
  return value ? new Date(value).toLocaleString('zh-CN') : '-'
}

onMounted(() => {
  loadAccount()
  loadMembership()
  loadOrders()
})
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

.profile-tabs {
  padding: 0;
}

.tab-tip {
  margin-top: 16px;
  font-size: 12px;
  color: var(--text-tertiary);
}

.membership-panel {
  padding: 4px 0;
}

.membership-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  flex-wrap: wrap;
  margin-bottom: 24px;
}

.membership-status {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.membership-sub {
  margin-top: 6px;
  font-size: 13px;
  color: var(--text-tertiary);
}

.quota-row {
  display: flex;
  gap: 40px;
  flex-wrap: wrap;
}

.quota-item {
  min-width: 120px;
}

.quota-number {
  font-size: 28px;
  font-weight: 700;
  color: var(--accent-info);
  line-height: 1.2;
}

/* 过期时间是个日期，用 28px 会撑破布局 —— 同一块区域里两处数字不该一样大 */
.quota-number.small {
  font-size: 18px;
  color: var(--text-primary);
}

.quota-label {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-tertiary);
}

.mono {
  font-family: var(--font-mono, ui-monospace, SFMono-Regular, Menlo, Consolas, monospace);
  font-size: 12px;
}

.empty-text {
  padding: 12px 0;
  font-size: 13px;
  line-height: 1.9;
  color: var(--text-tertiary);
}

.pagination {
  margin-top: 20px;
  justify-content: flex-end;
}
</style>

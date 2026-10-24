import axios from 'axios'
import { toast } from 'sonner'
import { useAuthStore } from '@/stores/authStore'
import { useUiStore } from '@/stores/uiStore'
import type { ApiEnvelope } from '@/types'

/**
 * 全站 HTTP 客户端。由 `utils/axios.ts`（Vue 版）改写而来 —— **只换了 3 个耦合点**：
 *
 *   1. `ElMessage` → `toast`（sonner）
 *   2. `useAuthStore()`（Pinia，靠 active pinia 实例）→ `useAuthStore.getState()`（Zustand，模块级单例）
 *   3. `forceLogin()` 里 store 的取法与 uiStore 的拆分
 *
 * 拦截器逻辑本身**一行没动，尤其不要动**：
 *   - 单飞刷新（isRefreshing + pendingQueue + flushQueue）
 *   - blob 直通
 *   - 只对 40102 刷新、其余 401 直接清态
 * 这些看着可以"简化"的地方，每一处都对应一个会复现的线上症状，见下面各段注释。
 */
const request = axios.create({
  baseURL: '/api',
  timeout: 30000
})

/** 后端约定的 Access Token 过期码（网关只在"确实过期"时返回它）。 */
const CODE_TOKEN_EXPIRED = 40102

// ---------------------------------------------------------------- 刷新并发锁
// 一个页面上常常同时飞着好几个请求（列表 + 统计 + 详情）。access 一过期它们会
// 一起收到 40102，如果每个都去刷新，就会打出 N 个刷新请求、并且互相把对方刚拿到的
// 新 token 冲掉。所以同一时刻只允许一个刷新在飞，其余挂进队列等结果。
let isRefreshing = false
let pendingQueue: Array<(token: string | null) => void> = []

/**
 * 唤醒队列。token 为 null 表示刷新失败，等待者应该一并失败而不是永久挂起。
 */
function flushQueue(token: string | null) {
  pendingQueue.forEach((notify) => notify(token))
  pendingQueue = []
}

/**
 * 清空本地登录态，并把登录卡片摆到用户眼前。
 *
 * 不再 `router.push('/login')`：整页登录路由已删除，而且把用户从他正在看的页面
 * 拽走再让他自己走回来是多此一举 —— 弹卡片就够了，背后的内容还在原处。
 * 这里是模块级函数，拿不到组件里的 store，所以用 `getState()` 现取
 * （Zustand 的 store 是模块单例，不依赖组件树，任何时候都能调）。
 */
function forceLogin(message = '登录已过期，请重新登录') {
  useAuthStore.getState().logout()
  useUiStore.getState().openAuthCard('login')
  toast.error(message)
}

request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`
    }

    // 这里曾经还会带 X-User-Id（供后端在没有网关时调试）与 X-User-Name。
    // 两个都删了：
    //   · X-User-Name 一旦含中文，XMLHttpRequest 会抛
    //     "String contains non ISO-8859-1 code point"，于是登录之后**每一个**请求都发不出去；
    //   · X-User-Id 纯属误导 —— 网关的 JwtAuthenticationFilter 会用 JWT 里的值覆盖它，
    //     前端传什么都会被丢掉。一个"看起来能声明身份、实际不起作用"的头，
    //     只会让读代码的人以为身份是由前端决定的。
    // 身份的唯一来源是 Authorization 里的 JWT，其余一律由网关注入。

    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

request.interceptors.response.use(
  (response) => {
    // 文件下载（responseType: 'blob'）拿到的是一条二进制流，不是本项目的统一
    // 响应体 { code, message, data }。若不放行，下面的 res.code 会是 undefined，
    // 被判定成"请求失败"并 reject —— 表现为下载 PRD 按钮点了报错、文件下不来。
    if (response.config.responseType === 'blob') {
      return response.data
    }

    // 业务码判定。标注成 ApiEnvelope 是为了让下面的 res.code / res.data 有类型，
    // 而不是让它成为调用方要处理的形状 —— **这一层就把信封剥掉了**。
    const res = response.data as ApiEnvelope<unknown>
    if (res.code !== 200 && res.code !== 0) {
      toast.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res.data !== undefined ? res.data : res
  },
  async (error) => {
    const originalRequest = error.config
    const code = error.response?.data?.code

    // 只有 40102 才值得刷新。其余 401（token 缺失/非法/类型不对）说明本地登录态
    // 已经没有挽救价值，直接清掉回登录页，免得拿着一个坏 token 反复触发刷新。
    if (
      error.response?.status === 401 &&
      code === CODE_TOKEN_EXPIRED &&
      originalRequest &&
      !originalRequest._isRetry
    ) {
      originalRequest._isRetry = true

      // 已有刷新在飞：排队等它的结果，不重复发起
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          pendingQueue.push((token) => {
            if (!token) {
              reject(error)
              return
            }
            originalRequest.headers['Authorization'] = `Bearer ${token}`
            resolve(request(originalRequest))
          })
        })
      }

      isRefreshing = true
      try {
        const authStore = useAuthStore.getState()
        const refreshToken = authStore.refreshToken
        if (!refreshToken) {
          throw new Error('本地没有 refreshToken')
        }

        // 用原生 axios 绕开本实例的拦截器，否则刷新失败时又会触发刷新，死循环。
        // 相对路径会被 vite/nginx 代理到网关，和 baseURL 效果一致。
        const { data } = await axios.post('/api/v1/auth/refresh', {
          userId: authStore.userInfo?.id,
          refreshToken
        })

        if (data?.code !== 200 || !data.data?.accessToken) {
          throw new Error(data?.message || '刷新 Token 失败')
        }

        const newAccessToken: string = data.data.accessToken
        // 走 store 而不是直接写 localStorage，否则 store 里的 token 会和存储漂移，
        // 而路由守卫读的正是 store 里的值。
        authStore.setTokens(newAccessToken, data.data.refreshToken)

        flushQueue(newAccessToken)
        originalRequest.headers['Authorization'] = `Bearer ${newAccessToken}`
        return request(originalRequest)
      } catch (refreshError) {
        // 队列里等待的请求必须显式唤醒并失败，否则它们的 Promise 永远挂着，
        // 页面会一直停在 loading 上。
        flushQueue(null)
        forceLogin()
        return Promise.reject(refreshError)
      } finally {
        isRefreshing = false
      }
    }

    if (error.response?.status === 401) {
      /*
        提示语优先用**后端给的那句**。
        网关对"从没登录"返回的是 "请先登录"，token 过期才是"登录已过期" ——
        这里写死一个文案的话，从没登录的人会被告知"你刚刚被踢下线了"，
        会去怀疑是自己网络/账号出了问题。上面第 157 行（刷新失败那条路径）
        保持默认文案不动：那里确实就是过期。
      */
      const backendMessage = (error.response?.data as { message?: string } | undefined)?.message
      forceLogin(backendMessage || '请先登录')
    } else {
      toast.error(error.message || '网络错误')
    }
    return Promise.reject(error)
  }
)

export default request

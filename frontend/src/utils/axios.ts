import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { useAuthStore } from '@/stores/auth'

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

/** 清空本地登录态并回登录页。 */
function forceLogin(message = '登录已过期，请重新登录') {
  useAuthStore().logout()
  if (router.currentRoute.value.path !== '/login') {
    router.push('/login')
  }
  ElMessage.error(message)
}

request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`
    }

    const userId = localStorage.getItem('userId')
    const userName = localStorage.getItem('userName')
    if (userId) {
      config.headers['X-User-Id'] = userId
    }
    if (userName) {
      config.headers['X-User-Name'] = userName
    }

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

    const res = response.data
    if (res.code !== 200 && res.code !== 0) {
      ElMessage.error(res.message || '请求失败')
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
        const authStore = useAuthStore()
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
        // 走 store 而不是直接写 localStorage，否则 Pinia 里的 token 会和存储漂移，
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
      forceLogin()
    } else {
      ElMessage.error(error.message || '网络错误')
    }
    return Promise.reject(error)
  }
)

export default request

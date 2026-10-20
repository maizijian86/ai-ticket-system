import { useEffect, useMemo, useState } from 'react'
import { userApi } from '@/api/user'

/**
 * 模块级缓存：id → 头像地址。
 *
 * 大厅一屏 12 张卡、翻页又换一批发布者，同一批 id 会在"筛选切换 / 返回大厅 /
 * 详情页里点返回"时被反复问到。缓存放在 hook 外面，组件卸载后仍然有效 ——
 * 头像是不怎么变的数据，没必要每次挂载都重发。
 *
 * 查过但没头像的 id 记成空串：不记的话每次挂载都会重新请求同一批
 * "永远不会有的"头像。
 */
const cache = new Map<number, string>()

function snapshot(ids: number[]): Record<number, string> {
  const out: Record<number, string> = {}
  for (const id of ids) {
    const url = cache.get(id)
    if (url) out[id] = url
  }
  return out
}

/**
 * 批量取用户头像，返回 `id → 头像地址`。
 *
 * 调用方直接 `useUserAvatars(tickets.map(t => t.creatorId))`，拿到的 map 里
 * 没有的 id 让 `UserAvatar` 走首字母兜底即可 —— 这里不区分"没有头像"和
 * "还没加载出来"，因为两者在 UI 上是同一件事。
 */
export function useUserAvatars(ids: number[]): Record<number, string> {
  // 调用方每次渲染都会传一个新数组，先归一再拼成字符串当依赖，避免 effect 空转
  const key = useMemo(() => {
    const set = new Set<number>()
    for (const id of ids) {
      if (Number.isInteger(id) && id > 0) set.add(id)
    }
    return [...set].sort((a, b) => a - b).join(',')
  }, [ids])

  const wanted = useMemo(() => (key ? key.split(',').map(Number) : []), [key])
  const [avatars, setAvatars] = useState<Record<number, string>>(() => snapshot(wanted))

  useEffect(() => {
    const missing = wanted.filter((id) => !cache.has(id))
    if (missing.length === 0) {
      // 缓存已经齐全（翻回上一页、从详情页退回大厅），同步出结果，不发请求
      setAvatars(snapshot(wanted))
      return
    }

    let alive = true
    userApi
      .getAvatars(missing)
      .then((res) => {
        for (const [k, v] of Object.entries(res ?? {})) {
          const id = Number(k)
          if (Number.isInteger(id) && v) cache.set(id, v)
        }
        // 服务端没返回的 id 也记下来，避免下次挂载再问一遍
        missing.forEach((id) => {
          if (!cache.has(id)) cache.set(id, '')
        })
      })
      .catch(() => {
        // 不缓存失败结果，下次挂载还能再试；调用方走首字母兜底。
        // 不额外 toast：能走到这里时，列表接口多半也已经报过错了。
      })
      .finally(() => {
        if (alive) setAvatars(snapshot(wanted))
      })

    return () => {
      alive = false
    }
    // 依赖写 `wanted` 而不是 `key`：两者同生同灭（wanted 由 key 派生），
    // 但 effect 体里用的是 wanted，写它才能让 lint 看懂依赖关系。
  }, [wanted])

  return avatars
}

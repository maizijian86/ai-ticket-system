import { create } from 'zustand'
import type { Role, TicketDTO, TicketStatsDTO } from '@/types'

/**
 * 跨页面的「该刷新了」总线。
 *
 * 场景：在工单详情里接了单 / 交付 / 验收，返回大厅时列表和统计卡必须跟着变；
 * 而大厅和详情是两个路由，各自持有自己的数据。与其让详情页去 import 大厅的
 * fetch 函数（反向依赖，且大厅此刻多半没挂载），不如让详情页把一个自增的
 * 计数器捅一下，大厅订阅它 —— 挂载中的大厅自然会重新拉数据，没挂载的则什么
 * 也不做，下次挂载时本来就拉的是新的。
 *
 * React 侧消费方式是把 `refreshTrigger` 放进 useEffect 依赖数组：
 *   const refreshTrigger = useTicketStore(s => s.refreshTrigger)
 *   useEffect(() => { load() }, [refreshTrigger, ...])
 */
/**
 * 从大厅点进一张工单时，把大厅当时的样子（哪一档、翻到第几页、已加载的行、
 * 滚动位置）留在这里，回到大厅时原样恢复。
 *
 * 为什么要存到 store：路由切换会把 `TicketList` 整个卸载（`AppLayout` 的
 * `<Outlet key=…>` 还保证了同一路径也会重挂），页内的 useState 活不过一次跳转。
 * 存在内存里就够了 —— 刷新页面本来就该回到默认大厅。
 *
 * **身份是快照的一部分**：登录/退出/换号后，旧的"我的发布"快照对新的身份毫无意义，
 * 甚至可能是别人的行，所以 restore 时必须比对 userId + role，不一致就丢弃。
 *
 * `refreshTrigger` 也一起存：详情页里接单/交付/验收会捅计数器，回到大厅时如果
 * 计数器变过，说明列表已经过时，不能光拿旧行充数（见 TicketList 的 fetch）。
 */
export interface HallSnapshot {
  userId: number | undefined
  role: Role | undefined
  /** 实际在显示的那一档（`scope.key`，不是 `scopeKey`） */
  scopeKey: string
  tickets: TicketDTO[]
  total: number
  page: number
  stats: TicketStatsDTO | null
  scrollY: number
  refreshTrigger: number
}

interface TicketState {
  refreshTrigger: number
  triggerRefresh: () => void
  hall: HallSnapshot | null
  /** 进工单详情前调用 */
  saveHall: (snapshot: HallSnapshot) => void
  /** 大厅挂载时读一次就清掉，避免下次从别处进来还在恢复旧位置 */
  clearHall: () => void
}

export const useTicketStore = create<TicketState>()((set) => ({
  refreshTrigger: 0,
  triggerRefresh: () => set((s) => ({ refreshTrigger: s.refreshTrigger + 1 })),
  hall: null,
  saveHall: (hall) => set({ hall }),
  clearHall: () => set({ hall: null })
}))

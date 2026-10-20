import { useState } from 'react'
import { toast } from 'sonner'

import { ticketApi } from '@/api/ticket'
import { Button } from '@/components/ui/button'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { useTicketStore } from '@/stores/ticketStore'
import { useUiStore } from '@/stores/uiStore'
import type { TicketDTO } from '@/types'

/**
 * 「接单」—— **绿色按钮 + 贴着它的一级确认菜单**（用户要求：绿色、二级菜单确认）。
 *
 * 为什么不是一个普通按钮直接发请求：接单是一次**不可撤销**的承诺（后端会把工单
 * 置 is_visible=false、从大厅下架，只有发布者验收通过或重开才会回来），误点一次的
 * 代价比多点一次大得多。所以点第一下只是把菜单摆出来，点第二下才真的发请求。
 *
 * 菜单里那三行不是装饰，是接单人真正需要知道的三件事：接完从大厅消失、交付要传
 * 压缩包、验收权在发布者手上。这些信息放 toast 里没人看得到（出现即消失）。
 *
 * ⚠️ **访客点的是同一颗绿色按钮**，但不是弹菜单而是弹登录卡片：走的是
 * `onOpenChange` 里那道闸，所以按钮本身不必分两个版本。
 */
export function PickupConfirmMenu({ ticket, loggedIn }: { ticket: TicketDTO; loggedIn: boolean }) {
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  const openAuthCard = useUiStore((s) => s.openAuthCard)
  const triggerRefresh = useTicketStore((s) => s.triggerRefresh)

  async function confirm() {
    setBusy(true)
    try {
      await ticketApi.pickupTicket(ticket.id)
      toast.success(`已接单：#${ticket.id} 现在在你的「我接的单」里`)
      setOpen(false)
      // 列表和统计卡都跟着变（卡片数字、这一档的行）。今天大厅自己订阅了这个计数器。
      triggerRefresh()
    } catch {
      // 失败原因由 axios 拦截器统一 toast（409 已被别人接走、400 不能接自己的单…）。
      // 这里要做的只是**别让用户对着一个点了必然报错的按钮反复点**：关掉菜单 + 刷新，
      // 刷新后这颗按钮通常就自己消失了（位置和状态都不再满足 showPickup 的条件）。
      setOpen(false)
      triggerRefresh()
    } finally {
      setBusy(false)
    }
  }

  return (
    <Popover
      open={open}
      onOpenChange={(next) => {
        // 访客：不给他看"确认接单"—— 点了也是 401。直接把登录卡片摆出来。
        if (next && !loggedIn) {
          openAuthCard('login')
          return
        }
        setOpen(next)
      }}
    >
      <PopoverTrigger
        render={
          <Button
            size="sm"
            // 全站唯一的绿色按钮（用户点名要的）。绿底白字压在**白卡**上，
            // 不复用玻璃：玻璃只在虹彩上有折射，铺在白卡上等于一块看不见的膜。
            className="bg-emerald-600 font-semibold text-white hover:bg-emerald-700"
          />
        }
      >
        接单
      </PopoverTrigger>
      {/* align="end" + sideOffset：菜单贴着按钮右下角展开，不盖住按钮本身 */}
      <PopoverContent align="end" sideOffset={6} className="w-[19rem] gap-3">
        <div className="flex flex-col gap-1">
          <span className="text-sm font-semibold text-foreground">确认接下这张单？</span>
          <span className="font-mono text-[11px] text-muted-foreground">#{ticket.id}</span>
        </div>
        <ul className="flex flex-col gap-1.5 text-xs leading-5 text-muted-foreground">
          <li>· 接单后这张单会从大厅下架，只有你能做</li>
          <li>· 交付时要上传一个压缩包，发布者验收通过才算完成</li>
          <li>· 驳回后可以重新交付，交付前一直算「待完成」</li>
        </ul>
        <div className="flex justify-end gap-2">
          <Button variant="ghost" size="sm" disabled={busy} onClick={() => setOpen(false)}>
            再想想
          </Button>
          <Button
            size="sm"
            disabled={busy}
            onClick={confirm}
            className="bg-emerald-600 font-semibold text-white hover:bg-emerald-700"
          >
            {busy ? '接单中…' : '确认接单'}
          </Button>
        </div>
      </PopoverContent>
    </Popover>
  )
}

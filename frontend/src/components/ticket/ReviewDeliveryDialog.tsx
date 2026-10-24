import { useCallback, useEffect, useState } from 'react'
import { toast } from 'sonner'

import { ticketApi } from '@/api/ticket'
import { Download, FileArchive, TriangleAlert } from '@/components/icons'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from '@/components/ui/dialog'
import { Skeleton } from '@/components/ui/skeleton'
import { Textarea } from '@/components/ui/textarea'
import { useTicketStore } from '@/stores/ticketStore'
import type { DeliveryFile, TicketDTO } from '@/types'

function formatSize(bytes?: number) {
  if (!bytes) return ''
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function formatTime(iso?: string) {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** 列表里的一行：文件名 + 体积 + 上传时间，右侧是下载入口。 */
function DeliveryFileRow({ file }: { file: DeliveryFile }) {
  const meta = [formatSize(file.fileSize), formatTime(file.createdAt)].filter(Boolean).join(' · ')
  return (
    <div className="flex items-center gap-3 rounded-lg border border-border px-3 py-2">
      <FileArchive size={18} className="shrink-0 text-muted-foreground" />
      <div className="flex min-w-0 flex-1 flex-col">
        <span className="truncate text-sm font-medium" title={file.originalName}>
          {file.originalName}
        </span>
        {meta && <span className="text-xs text-muted-foreground">{meta}</span>}
      </div>
      {file.url ? (
        // 跨域链接上 `download` 属性是无效的（浏览器只对同源生效），真正让浏览器
        // 下载而不是预览的是后端上传时写死的 Content-Type: application/octet-stream。
        // 这里仍然写上 download：同源代理的情况下它会生效，兜个底没坏处。
        <a
          href={file.url}
          download={file.originalName}
          target="_blank"
          rel="noreferrer"
          className="inline-flex shrink-0 items-center gap-1 text-xs font-medium text-foreground underline underline-offset-4 hover:text-foreground/70"
        >
          <Download size={14} />
          下载
        </a>
      ) : (
        // url 为空的唯一成因是服务端没配 OSS（AK 缺失）时的历史数据。**不隐藏这一行**：
        // 验收人要知道"确实交过东西、只是链接没生成"，比看见一个空列表强。
        <span className="shrink-0 text-xs text-muted-foreground">链接不可用</span>
      )}
    </div>
  )
}

/**
 * 「去验收」—— 发布者查看接单者上传的交付包，然后通过或驳回。
 *
 * 文件列表是**进弹窗才拉**的（`load()` 挂在 open 上），不跟着大厅列表一起取：
 * 大厅一页 12 张单，其中大多数根本不该让当前用户看到交付物（服务端也只放给
 * 该单的发布者/接单人），预取等于一半请求注定 403。
 *
 * 权限由服务端把关（`requireParticipantTicket`），前端不做"我是不是发布者"的判断 ——
 * 那颗「去验收」按钮本来就只在 creatorId === 我 且 PENDING_APPROVAL 时才出现。
 */
export function ReviewDeliveryDialog({
  ticket,
  open,
  onOpenChange
}: {
  ticket: TicketDTO
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const [files, setFiles] = useState<DeliveryFile[]>([])
  const [loading, setLoading] = useState(false)
  const [failed, setFailed] = useState(false)
  const [rejecting, setRejecting] = useState(false)
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const triggerRefresh = useTicketStore((s) => s.triggerRefresh)

  const load = useCallback(async () => {
    setLoading(true)
    setFailed(false)
    try {
      setFiles(await ticketApi.listDeliveryFiles(ticket.id))
    } catch {
      // 这里**不要**让拦截器那句 toast 决定一切：失败时要有个可重试的落点，
      // 否则只剩一个空白弹窗，用户会以为接单人什么都没交。
      setFailed(true)
    } finally {
      setLoading(false)
    }
  }, [ticket.id])

  useEffect(() => {
    if (!open) return
    // 每次打开都重新拉：另开一个标签页重新提过交的情况下，缓存的旧列表会让
    // 发布者验收一份已经不是最新的包。
    setRejecting(false)
    setReason('')
    void load()
  }, [open, load])

  async function decide(approved: boolean) {
    if (busy) return
    if (!approved && !reason.trim()) return
    setBusy(true)
    try {
      await ticketApi.approveTicket(ticket.id, approved ? { approved } : { approved, reason: reason.trim() })
      toast.success(approved ? '已验收通过，工单完成' : '已驳回，等接单者重新交付')
      onOpenChange(false)
      triggerRefresh()
    } catch {
      // 拦截器已 toast（状态不对/不是发布者）。不关弹窗，理由还在里面，可以重试。
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>验收交付</DialogTitle>
          <DialogDescription>
            {ticket.handlerName ? `${ticket.handlerName} 交来的交付物` : '接单者交来的交付物'}
            ，下载看过之后再决定。
            <strong className="font-semibold text-foreground">验收通过这张单就完成了</strong>
            ；驳回要写清楚哪里不合格，接单者会带着这条理由重新交付。
          </DialogDescription>

          {/*
            资金提示必须出现在「通过」按钮上方。
            通过 = 结算：预算从发布者的冻结里划给接单者，**不可撤销**，
            而且之后这张单不能再「重新开放」（钱已经在对方账上了）。

            历史工单（`funded === false`，钱包上线前创建的）没有托管记录，
            通过时不会发生任何转账 —— 不说清的话，接单者会以为"干完了怎么没收到钱"，
            发布者会以为"怎么没扣我钱"。
          */}
          {ticket.funded === false ? (
            <p className="mt-3 rounded-lg border border-amber-300/60 bg-amber-50/70 p-3 text-xs leading-5 text-amber-800">
              这张工单创建于资金功能上线前，<strong className="font-semibold">不涉及资金结算</strong>，
              验收通过不会发生任何转账。
            </p>
          ) : (
            <p className="mt-3 rounded-lg border border-amber-300/60 bg-amber-50/70 p-3 text-xs leading-5 text-amber-800">
              验收通过后，
              <strong className="font-semibold">
                {ticket.budget != null ? `¥${ticket.budget.toFixed(2)} ` : ''}
              </strong>
              将从你的托管余额结算给接单者，
              <strong className="font-semibold">不可撤销</strong>，且这张工单之后不能再重新开放。
            </p>
          )}
        </DialogHeader>

        <div className="flex flex-col gap-2">
          {loading &&
            [0, 1].map((i) => <Skeleton key={i} className="h-[52px] w-full rounded-lg" />)}

          {!loading && failed && (
            <div className="flex items-center justify-between rounded-lg border border-border px-3 py-2 text-xs text-muted-foreground">
              <span>交付物读不出来</span>
              <Button variant="ghost" size="sm" onClick={() => void load()}>
                重试
              </Button>
            </div>
          )}

          {!loading && !failed && files.length === 0 && (
            <div className="rounded-lg border border-dashed border-border px-3 py-6 text-center text-xs text-muted-foreground">
              这单还没有交付物 —— 接单者上传压缩包之后才会进入待验收
            </div>
          )}

          {!loading && !failed && files.map((f) => <DeliveryFileRow key={f.id} file={f} />)}
        </div>

        {rejecting ? (
          <>
            <Textarea
              autoFocus
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              rows={3}
              placeholder="哪里不合格？比如：缺了后台管理页面 / 部署文档没写"
              className="resize-none text-sm"
            />
            <DialogFooter>
              <Button variant="outline" disabled={busy} onClick={() => setRejecting(false)}>
                返回
              </Button>
              {/* 驳回理由后端不校验，前端必须自己拦住 —— 空的理由等于让接单者
                  在一句提示都没有的情况下重做，比直接报错还糟。 */}
              <Button
                variant="destructive"
                disabled={busy || !reason.trim()}
                onClick={() => void decide(false)}
              >
                {busy ? '提交中…' : '确认驳回'}
              </Button>
            </DialogFooter>
          </>
        ) : (
          <DialogFooter>
            <Button variant="outline" disabled={busy} onClick={() => setRejecting(true)}>
              驳回
            </Button>
            {/* 没读到文件就禁用「通过」：通过一份自己没看过的交付是把验收这道关卡虚化。
                读失败时把失败信息摆在眼前，让用户先修好再验收，而不是顺手点绿。 */}
            <Button
              disabled={busy || loading || failed || files.length === 0}
              onClick={() => void decide(true)}
            >
              {busy ? '提交中…' : '通过并完成'}
            </Button>
          </DialogFooter>
        )}

        {ticket.rejectionReason && !rejecting && (
          <p className="flex items-start gap-1.5 text-xs leading-5 text-muted-foreground">
            <TriangleAlert size={13} className="mt-0.5 shrink-0" />
            <span>这张单上一次是被驳回的：{ticket.rejectionReason}</span>
          </p>
        )}
      </DialogContent>
    </Dialog>
  )
}

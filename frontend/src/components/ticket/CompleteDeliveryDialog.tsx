import { useEffect, useState } from 'react'
import { toast } from 'sonner'

import { ticketApi } from '@/api/ticket'
import { FileArchive, TriangleAlert, Upload } from '@/components/icons'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from '@/components/ui/dialog'
import { useTicketStore } from '@/stores/ticketStore'
import type { TicketDTO } from '@/types'

/**
 * 交付包的体积上限。**与后端 TicketService.DELIVERY_MAX_BYTES（以及
 * application.yml 里的 multipart max-file-size）是同一个数，改一处要三处一起改。**
 *
 * 前端这一道只是为了"别让用户白等一次上传"：真正说了算的是服务端那两道，
 * 这里放行了也不算数。
 */
const MAX_BYTES = 10 * 1024 * 1024

/** 与后端 DELIVERY_ALLOWED_EXTENSIONS 对齐，顺序也照抄一份便于对照。 */
const ALLOWED_EXTENSIONS = ['zip', 'rar', '7z', 'tar', 'gz', 'tgz']

/** `<input accept>` 用的串，从上面那份派生 —— 两处写死必然漂移。 */
const ACCEPT = ALLOWED_EXTENSIONS.map((ext) => `.${ext}`).join(',')

function extensionOf(name: string) {
  const dot = name.lastIndexOf('.')
  return dot < 0 ? '' : name.slice(dot + 1).toLowerCase()
}

function formatSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

/**
 * 「去完成」—— 接单者上传一个压缩包作为交付物。
 *
 * 这个弹窗存在的意义是把**交付**和**上传**绑成一件事（用户要求：只有接单者上传了
 * 文件，工单状态才流转）。所以后端那个接口就是 multipart，没有"只改状态不传文件"
 * 的用法，这个界面也就不提供"跳过上传"的口子 —— 没选文件时提交键是灰的，
 * 而不是"能点、点了报错"。
 */
export function CompleteDeliveryDialog({
  ticket,
  open,
  onOpenChange
}: {
  ticket: TicketDTO
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const [file, setFile] = useState<File | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const triggerRefresh = useTicketStore((s) => s.triggerRefresh)

  // 每次打开都从零开始：上一次选的文件不该在关掉再打开后还挂在这里
  // （用户会以为自己已经选过了，而实际上那一轮根本没提交）。
  useEffect(() => {
    if (open) {
      setFile(null)
      setError('')
    }
  }, [open])

  function pick(selected: File | null) {
    setFile(selected)
    if (!selected) {
      setError('')
      return
    }
    // 两道校验都给**具体数字**（"10MB"、"zip / rar / …"），不要只说"格式不对" ——
    // 用户手上那个包到底哪一条不符合，得让他一眼看出来。
    if (selected.size > MAX_BYTES) {
      setError(`这个包 ${formatSize(selected.size)}，超过了 ${formatSize(MAX_BYTES)} 上限`)
      return
    }
    if (!ALLOWED_EXTENSIONS.includes(extensionOf(selected.name))) {
      setError('只支持压缩包：zip / rar / 7z / tar / gz / tgz')
      return
    }
    setError('')
  }

  async function submit() {
    if (!file || error || busy) return
    setBusy(true)
    try {
      await ticketApi.uploadDelivery(ticket.id, file)
      toast.success('已提交交付，等发布者验收')
      onOpenChange(false)
      triggerRefresh()
    } catch {
      // 拦截器已经 toast 过（超限、格式、状态不对、OSS 没配…）。**不关弹窗**：
      // 用户选好的文件还在，改完再传一次不用重选。
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>提交交付</DialogTitle>
          <DialogDescription>
            上传一个压缩包作为交付物。<strong className="font-semibold text-foreground">上传成功这张单才会进入待验收</strong>
            —— 发布者验收通过后变成已完成；不传文件，状态不会流转。
          </DialogDescription>
        </DialogHeader>

        {/* 被驳回过的单：上一次的理由必须先摆出来，否则接单人不知道改哪里 */}
        {ticket.status === 'REJECTED' && ticket.rejectionReason && (
          <div className="flex items-start gap-2 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-xs leading-5 text-red-700">
            <TriangleAlert size={14} className="mt-0.5 shrink-0" />
            <span>上次被驳回：{ticket.rejectionReason}</span>
          </div>
        )}

        <label
          className={[
            'flex cursor-pointer flex-col items-center gap-2 rounded-xl border border-dashed px-4 py-6 text-center transition-colors',
            file && !error
              ? 'border-emerald-300 bg-emerald-50/60'
              : 'border-border hover:border-foreground/30 hover:bg-muted/60'
          ].join(' ')}
        >
          <input
            type="file"
            className="hidden"
            accept={ACCEPT}
            onChange={(e) => pick(e.target.files?.[0] ?? null)}
          />
          {file ? (
            <>
              <FileArchive size={22} className="text-foreground" />
              <span className="max-w-full truncate text-sm font-medium break-all">{file.name}</span>
              <span className="text-xs text-muted-foreground">
                {formatSize(file.size)} · 点击可以换一个
              </span>
            </>
          ) : (
            <>
              <Upload size={22} className="text-muted-foreground" />
              <span className="text-sm font-medium">点击选择压缩包</span>
              <span className="text-xs text-muted-foreground">
                zip / rar / 7z / tar / gz / tgz，不超过 {formatSize(MAX_BYTES)}
              </span>
            </>
          )}
        </label>

        {error && (
          <p className="text-xs leading-5 text-red-600">{error}</p>
        )}

        <DialogFooter>
          <Button variant="outline" disabled={busy} onClick={() => onOpenChange(false)}>
            取消
          </Button>
          <Button disabled={!file || !!error || busy} onClick={submit}>
            {busy ? '上传中…' : '提交交付'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

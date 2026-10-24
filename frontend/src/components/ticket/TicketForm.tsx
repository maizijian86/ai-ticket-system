import { type ReactNode } from 'react'
import { cn } from '@/lib/utils'
import { CARD_BASE } from '@/components/layout/PageShell'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import AnimatedContent from '@/components/animation/AnimatedContent'
import { Check, Coins, LoaderCircle, TriangleAlert } from '@/components/icons'
import { formatExpireDate } from '@/utils/ai-quota'
import type { Urgency } from '@/types'

/*
  发布 / 编辑需求共用的表单**外壳与控件**。

  设计不照抄旧的 Vue 页（那版是 `el-form label-width:120px` 的左标签纵向表单，
  迁过来会立刻显出"这是个 Element Plus 页面"）。这里按本项目的视觉语言重排：

    · 分成四节，每节一张白卡，节头是一个等宽大写的序号 + 中文小标题
      （`01 BASIC` / 基本信息）—— 与全站 eyebrow 那套同源，
      而不是"一块超长表单从上滚到底"。
    · 选择项用**胶囊按钮组**而不是原生 select / radio：本站的 CTA、筛选、
      导航全是胶囊，塞一个系统下拉会断层。焦点与选中都靠底色+边框表达。
    · 标签放在控件**上方**（不是左侧）：左标签在窄屏上要么被压成两行、
      要么把控件挤到只剩一半宽。
    · 每节入场用 AnimatedContent 错峰（stagger），整页一次性淡入会显得呆。

  这个文件只管"长什么样 + 怎么校验"，提交与副作用留给调用页
  （create 页要带上 AI 额度预附、edit 页要带 id）。
*/

// ---------------------------------------------------------------- 选项常量

/**
 * 项目类型。取值与后端 `TicketCreateRequest.projectType` 一致（存英文枚举），
 * 展示用中文 —— 与 utils/ticket-labels.ts 的 getProjectTypeLabel 同一套文案。
 */
export const PROJECT_TYPE_OPTIONS = [
  { value: 'JavaWeb', label: 'JavaWeb' },
  { value: 'Python', label: 'Python' },
  { value: 'Frontend', label: '前端' },
  { value: 'FullStack', label: '全栈' },
  { value: 'Mobile', label: '移动端' },
  { value: 'Other', label: '其他' }
] as const

/** 紧急程度四档。文案与 utils/ticket-labels.ts 的 URGENCY_LABELS 保持一致。 */
export const URGENCY_OPTIONS = [
  { value: 'LOW', label: '低' },
  { value: 'NORMAL', label: '普通' },
  { value: 'HIGH', label: '紧急' },
  { value: 'CRITICAL', label: '非常紧急' }
] as const

// ---------------------------------------------------------------- 小组件

/** 节头：等宽大写序号 + 中文标题。 */
function SectionHeading({ index, en, zh }: { index: string; en: string; zh: string }) {
  return (
    <div className="mb-5 flex items-baseline gap-3">
      <span className="font-mono text-xs font-medium tracking-[0.24em] text-muted-foreground/70">
        {index}
      </span>
      <h2 className="text-base font-semibold tracking-tight">{zh}</h2>
      <span className="font-mono text-[11px] tracking-[0.18em] text-muted-foreground/50 uppercase">
        {en}
      </span>
    </div>
  )
}

/** 一节 = 一张白卡。 */
function FormSection({
  index,
  en,
  zh,
  children
}: {
  index: string
  en: string
  zh: string
  children: ReactNode
}) {
  return (
    <section className={cn(CARD_BASE, 'p-6 sm:p-7', index !== '01' && 'mt-4')}>
      <SectionHeading index={index} en={en} zh={zh} />
      <div className="space-y-5">{children}</div>
    </section>
  )
}

/** 字段：标签 + 控件 + 错误/提示。 */
function Field({
  htmlFor,
  label,
  hint,
  error,
  required,
  children
}: {
  htmlFor?: string
  label: string
  hint?: ReactNode
  error?: string
  required?: boolean
  children: ReactNode
}) {
  return (
    <div>
      <div className="mb-1.5 flex items-baseline justify-between gap-3">
        <Label htmlFor={htmlFor} className="text-sm font-medium">
          {label}
          {required ? <span className="ml-0.5 text-red-500">*</span> : null}
        </Label>
        {hint ? <span className="text-[11px] text-muted-foreground">{hint}</span> : null}
      </div>
      {children}
      {error ? (
        <p className="mt-1.5 flex items-center gap-1 text-xs text-red-600">
          <TriangleAlert size={12} />
          {error}
        </p>
      ) : null}
    </div>
  )
}

/**
 * 胶囊单选组。
 *
 * 用 `role="radiogroup"` + 按钮而不是 `<select>`：本站所有可选状态的表达都是胶囊
 * （大厅的四档筛选、顶栏导航的选中态）。顺带解决一个实际问题 —— 原生 select 的
 * 下拉面板由操作系统绘制，白底页面上会突然弹出一块系统灰，与虹彩背景打架。
 */
export function ChoicePills<T extends string>({
  name,
  value,
  options,
  onChange,
  disabled
}: {
  name: string
  value: T | ''
  options: readonly { value: T; label: string }[]
  onChange: (v: T) => void
  disabled?: boolean
}) {
  return (
    <div role="radiogroup" aria-label={name} className="flex flex-wrap gap-2">
      {options.map((opt) => {
        const active = value === opt.value
        return (
          <button
            key={opt.value}
            type="button"
            role="radio"
            aria-checked={active}
            disabled={disabled}
            onClick={() => onChange(opt.value)}
            className={cn(
              'inline-flex h-8 cursor-pointer items-center gap-1.5 rounded-full border px-3.5 text-sm font-medium transition-all duration-150',
              'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-foreground/40',
              'disabled:cursor-not-allowed disabled:opacity-50',
              active
                ? 'border-foreground bg-foreground text-background'
                : 'border-border bg-card text-muted-foreground hover:border-foreground/30 hover:text-foreground'
            )}
          >
            {/* 选中时给一个小勾：只靠黑白反转在"选中项本来就没变"时不够明确 */}
            {active ? <Check size={13} /> : null}
            {opt.label}
          </button>
        )
      })}
    </div>
  )
}

/**
 * 「附上 1 次 AI 额度」的开关行。
 *
 * 不用原生 checkbox：这一行要同时表达"能勾/不能勾/为什么不能勾"，原生框放不下
 * 那些文案，而且勾选态在黑白色系里很难看出。做成一张可点的行 + 右侧开关。
 */
export function AiCreditToggle({
  checked,
  onCheckedChange,
  loaded,
  remaining,
  nextExpireAt
}: {
  checked: boolean
  onCheckedChange: (v: boolean) => void
  loaded: boolean
  remaining: number | null
  nextExpireAt?: string | null
}) {
  const can = loaded && (remaining ?? 0) >= 1
  const disabled = !can

  return (
    <div>
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        disabled={disabled}
        onClick={() => onCheckedChange(!checked)}
        className={cn(
          'flex w-full cursor-pointer items-start gap-3 rounded-xl border p-4 text-left transition-all duration-150',
          'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-foreground/40',
          'disabled:cursor-not-allowed disabled:opacity-60',
          checked ? 'border-foreground/40 bg-foreground/5' : 'border-border bg-background hover:bg-muted/60'
        )}
      >
        <span
          className={cn(
            'mt-0.5 flex size-8 shrink-0 items-center justify-center rounded-lg transition-colors',
            checked ? 'bg-foreground text-background' : 'border border-border text-muted-foreground'
          )}
        >
          <Coins size={15} />
        </span>
        <span className="min-w-0 flex-1">
          <span className="block text-sm font-medium">
            附上 1 次 AI 额度，接单人免费用「AI 分析与对话」
          </span>
          <span className="mt-1 block text-xs leading-5 text-muted-foreground">
            {!loaded ? (
              <span className="inline-flex items-center gap-1.5">
                <LoaderCircle size={12} className="animate-spin" /> 额度信息加载中…
              </span>
            ) : remaining === null ? (
              <>读不到你的额度余额，暂时不能附 —— 稍后重试，或到个人主页查看。</>
            ) : remaining < 1 ? (
              <>当前剩余 0 次，额度不足 —— 可以去开通会员，或等注册赠送的批次到账。</>
            ) : (
              <>
                当前剩余 <strong className="text-foreground">{remaining}</strong> 次
                {nextExpireAt ? `，最近一批 ${formatExpireDate(nextExpireAt)} 过期` : ''}。
                附上后<strong className="text-foreground">不退还</strong>
                （没人接、工单关闭或删除都不退）。附上的单会在大厅排到前列，
                更容易被接单；PRD 生成不在内，仍由接单人自付。
              </>
            )}
          </span>
        </span>
        {/* 开关本体：纯装饰，真正的语义在 role="switch" 上 */}
        <span
          aria-hidden="true"
          className={cn(
            'mt-1 flex h-5 w-9 shrink-0 items-center rounded-full p-0.5 transition-colors',
            checked ? 'bg-foreground' : 'bg-border'
          )}
        >
          <span
            className={cn(
              'size-4 rounded-full bg-background transition-transform duration-200',
              checked ? 'translate-x-4' : 'translate-x-0'
            )}
          />
        </span>
      </button>
    </div>
  )
}

// ---------------------------------------------------------------- 表单主体

/** 表单值。与 CreateTicketRequest / UpdateTicketRequest 的字段名逐字对应。 */
export type TicketFormValues = {
  title: string
  content: string
  detailedRequirements: string
  projectType: string
  techStack: string
  /** 空串表示没填。提交时收敛成 undefined（后端 budget 可选） */
  budget: string
  /** `YYYY-MM-DD`，提交时补成 `T00:00:00` */
  deadline: string
  urgency: Urgency
}

export const EMPTY_TICKET_FORM: TicketFormValues = {
  title: '',
  content: '',
  detailedRequirements: '',
  projectType: '',
  techStack: '',
  budget: '',
  deadline: '',
  urgency: 'NORMAL'
}

/** 前端校验。只做后端不做或做了也不好提示的那几条（长度/必填）。 */
export function validateTicketForm(
  v: TicketFormValues,
  opts: {
    requireProjectType?: boolean
    /** 钱包可用余额（整数元）。传了就校验"不超余额"，不传则跳过（编辑页已接单时不校验） */
    availableBalance?: number
    /** 单笔上限（元）。后端 maxRecharge 同源下发，这里只做提前提示 */
    maxBudget?: number
  } = {}
) {
  const errors: Partial<Record<keyof TicketFormValues, string>> = {}

  if (!v.title.trim()) errors.title = '请填写项目名称'
  else if (v.title.length > 500) errors.title = '项目名称最多 500 字'

  if (!v.content.trim()) errors.content = '请填写项目描述'

  if (opts.requireProjectType && !v.projectType) errors.projectType = '请选择项目类型'

  /*
    预算规则与后端**完全一致**：必填、整数元、1 ~ 50000、不超过可用余额。

    ⚠️ 这里说的是"不支持角、分"，不是"最多两位小数" —— 后端
    `stripTrailingZeros().scale() > 0` 会拒掉 1.5 和 1.50，只接受整数。
    前端要是放过了，用户会先看到"提交成功"再收到一个 400，体验很差。
  */
  const raw = v.budget.trim()
  if (!raw) {
    errors.budget = '请填写预算（发布时将从余额中冻结）'
  } else if (!/^\d+$/.test(raw)) {
    errors.budget = '预算只能填整数元（不支持角、分）'
  } else {
    const n = Number(raw)
    if (n < 1) errors.budget = '预算最少 1 元'
    else if (opts.maxBudget != null && n > opts.maxBudget) {
      errors.budget = `预算最多 ${opts.maxBudget} 元`
    } else if (opts.availableBalance != null && n > opts.availableBalance) {
      const shortfall = n - opts.availableBalance
      errors.budget = `余额不足，还差 ${shortfall} 元（可用 ¥${opts.availableBalance}），请先充值`
    }
  }

  return errors
}

/**
 * 把表单值收敛成请求体。create 与 edit 共用。
 *
 * 注意两个空值的处理差别：
 *   · `budget` / `deadline` 空 -> undefined（后端这两个字段可选）
 *   · 文本字段空 -> 空串照发（编辑页清空详细需求是**合法操作**，不能被当成"没改"）
 */
export function toRequestPayload(v: TicketFormValues) {
  return {
    title: v.title.trim(),
    content: v.content.trim(),
    projectType: v.projectType || undefined,
    techStack: v.techStack.trim() || undefined,
    budget: v.budget.trim() ? Number(v.budget) : undefined,
    // 后端是 LocalDateTime，只给日期会反序列化失败；补零点
    deadline: v.deadline ? `${v.deadline}T00:00:00` : undefined,
    detailedRequirements: v.detailedRequirements,
    urgency: v.urgency
  }
}

/** 表单骨架。调用方把"动作区"作为 children 塞进来，提交逻辑不在这儿。 */
export function TicketFormSections({
  values,
  errors,
  onChange,
  requireProjectType = true,
  aiCredit,
  budgetDisabled = false,
  budgetHint
}: {
  values: TicketFormValues
  errors: Partial<Record<keyof TicketFormValues, string>>
  onChange: <K extends keyof TicketFormValues>(key: K, value: TicketFormValues[K]) => void
  /** 发布页要求选类型；编辑页允许留空（存量单可能没有） */
  requireProjectType?: boolean
  /**
   * 预算框是否禁用。编辑页在「已有接单人」时传 true ——
   * 那时金额已锁死到结算，改预算会让已投入工作的接单者失去预期。
   */
  budgetDisabled?: boolean
  /** 预算框下方的提示语。发布页可以在这里显示"当前余额 ¥X" */
  budgetHint?: ReactNode
  /** 只有发布页传：AI 额度预附那一节 */
  aiCredit?: {
    checked: boolean
    onCheckedChange: (v: boolean) => void
    loaded: boolean
    remaining: number | null
    nextExpireAt?: string | null
  }
}) {
  return (
    /*
      stagger 只作用于**直接子元素** —— 所以四张卡必须平铺在 AnimatedContent 下面，
      不能再套一层 `<div className="space-y-4">`（那样只会有一个子元素，错峰等于没有）。
      卡之间的间距由每张卡自己的 `mt-4`（除第一张）给。
    */
    <AnimatedContent
      trigger="mount"
      distance={28}
      duration={0.55}
      ease="power2.out"
      stagger={0.07}
      threshold={0}
    >
      {/* ── 01 基本信息 ── */}
      <FormSection index="01" en="Basics" zh="基本信息">
        <Field
          htmlFor="ticket-title"
          label="项目名称"
          required
          error={errors.title}
          hint={`${values.title.length}/500`}
        >
          <Input
            id="ticket-title"
            value={values.title}
            onChange={(e) => onChange('title', e.target.value)}
            placeholder="一句话说清要做什么，例如：企业 OA 管理系统"
            maxLength={500}
            aria-invalid={Boolean(errors.title)}
            className="h-10 text-sm"
          />
        </Field>

        <Field label="项目类型" required={requireProjectType} error={errors.projectType}>
          <ChoicePills
            name="项目类型"
            value={values.projectType}
            options={PROJECT_TYPE_OPTIONS}
            onChange={(v) => onChange('projectType', v)}
          />
        </Field>

        <Field label="技术栈" hint="逗号分隔，接单者用来判断自己会不会">
          <Input
            value={values.techStack}
            onChange={(e) => onChange('techStack', e.target.value)}
            placeholder="例如：Spring Boot, MyBatis, Vue3, MySQL"
            className="h-10 text-sm"
          />
        </Field>
      </FormSection>

      {/* ── 02 需求内容 ── */}
      <FormSection index="02" en="Requirement" zh="需求内容">
        <Field
          htmlFor="ticket-content"
          label="项目描述"
          required
          error={errors.content}
          hint={`${values.content.length}/2000`}
        >
          <Textarea
            id="ticket-content"
            value={values.content}
            onChange={(e) => onChange('content', e.target.value)}
            placeholder="简要描述项目背景与目标：现在遇到什么问题、想做成什么样。"
            rows={4}
            maxLength={2000}
            aria-invalid={Boolean(errors.content)}
            className="min-h-24 text-sm leading-6"
          />
        </Field>

        <Field label="详细需求" hint="写得越具体，报价越准 · 选填">
          <Textarea
            value={values.detailedRequirements}
            onChange={(e) => onChange('detailedRequirements', e.target.value)}
            placeholder={'1. 核心功能模块\n2. 用户角色与权限\n3. 业务流程\n4. 数据要求\n5. 特殊要求'}
            rows={8}
            className="min-h-44 text-sm leading-6"
          />
        </Field>
      </FormSection>

      {/* ── 03 预算与周期 ── */}
      <FormSection index="03" en="Budget &amp; Timeline" zh="预算与周期">
        <div className="grid gap-5 sm:grid-cols-2">
          {/*
            预算现在是**必填项**，而且提交时会被冻结：
            发布工单 = 从可用余额里扣出这一笔押住，验收通过才结算给接单者，
            关闭/删除会退回。所以这里的提示语必须说清"钱会被扣走"，
            不能只写"必填"—— 用户会以为只是个报价数字。
          */}
          <Field
            label="预算（必填）"
            error={errors.budget}
            hint={
              budgetHint ?? '整数元，发布时从余额冻结，验收通过后结算给接单者'
            }
          >
            <div className="relative">
              <span className="pointer-events-none absolute top-1/2 left-3 -translate-y-1/2 text-sm text-muted-foreground">
                ¥
              </span>
              <Input
                inputMode="numeric"
                value={values.budget}
                disabled={budgetDisabled}
                onChange={(e) => onChange('budget', e.target.value)}
                placeholder="例如 500"
                aria-invalid={Boolean(errors.budget)}
                className="h-10 pl-7 text-sm disabled:cursor-not-allowed disabled:opacity-60"
              />
            </div>
          </Field>

          <Field label="截止日期" hint="选填">
            {/*
              原生 date 输入：react-day-picker 在 Phase 2 已随死代码删除，
              为了一个日期框重新引一个日历库不划算。原生控件在键盘操作与
              时区语义上反而更稳，样式差异用 class 抹平。
            */}
            <Input
              type="date"
              value={values.deadline}
              onChange={(e) => onChange('deadline', e.target.value)}
              className="h-10 text-sm"
            />
          </Field>
        </div>

        <Field label="紧急程度">
          <ChoicePills
            name="紧急程度"
            value={values.urgency}
            options={URGENCY_OPTIONS}
            onChange={(v) => onChange('urgency', v)}
          />
        </Field>
      </FormSection>

      {/* ── 04 AI 额度（只有发布页传了 aiCredit 才出现）── */}
      {aiCredit ? (
        <FormSection index="04" en="AI Credit" zh="AI 辅助额度">
          <AiCreditToggle {...aiCredit} />
        </FormSection>
      ) : null}
    </AnimatedContent>
  )
}

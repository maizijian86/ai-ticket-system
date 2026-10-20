import type { ComponentType, HTMLAttributes } from 'react'

/**
 * SpotlightCard 的类型声明。理由与 MicroSlats.d.ts / ScrollVelocity.d.ts 同：
 * 从 `.jsx` 直接推断时，TS 会把「没有默认值的解构参数」判成**必需**
 * （这里是 `spotlightColor`），于是不传它就报 TS2740。
 *
 * 另外 **import 时不能带 `.jsx` 后缀** —— 带后缀 TS 会绕开本文件去读源码。
 * 正确写法：`import SpotlightCard from '@/components/cards/SpotlightCard'`。
 *
 * 继承 HTMLAttributes<HTMLDivElement>：组件把 `...rest` 整个铺在根 div 上，
 * 所以 onClick / role / tabIndex / aria-* 都是真的会被转发的（大厅的工单卡
 * 就靠这个把整张卡变成可点、可聚焦的入口）。
 */
export interface SpotlightCardProps extends HTMLAttributes<HTMLDivElement> {
  children?: React.ReactNode
  /** 光斑颜色，任何 CSS 颜色都行（alpha 会被尊重）。默认深色卡上是白色。 */
  spotlightColor?: string
  /** 卡面亮度，0–1。默认 0.15。 */
  intensity?: number
  /** 光斑半径（px）。默认 240。 */
  spotlightSize?: number
  /** 边缘衰减的渐进度，0 = 硬边圆盘，1 = 从中心慢慢化开。默认 0.7。 */
  softness?: number
  /** circle = 跟着指针的圆光；beam = 从顶边往下打的光柱。默认 circle。 */
  shape?: 'circle' | 'beam'
  /** 指针附近卡边被照亮的程度，0–1。默认 0.6。 */
  borderGlow?: number
  /** 指针离卡片多少 px 时光就开始生效（相邻的卡因此能共用一束光）。0 = 只在悬停时亮。 */
  proximity?: number
  /** 光斑滞后于指针的跟随阻尼，0 = 完全贴合。默认 0.3。 */
  smoothing?: number
  /** 没人指它时让光缓慢游走（触屏上让卡片"活着"）。默认 false。 */
  ambient?: boolean
  /** 按下时光斑涨大并变亮。默认 true。 */
  flare?: boolean
  /** 光斑内的胶片颗粒，0–1；一点点就能消掉大面积柔光上的色带。默认 0。 */
  grain?: number
  /** 深色卡还是浅色卡。默认 dark。配色可用 --spotlight-card-surface / -border 覆盖。 */
  theme?: 'dark' | 'light'
}

declare const SpotlightCard: ComponentType<SpotlightCardProps>
export default SpotlightCard

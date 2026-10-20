import type { ComponentType, CSSProperties } from 'react'

/**
 * MicroSlats 的类型声明。
 *
 * **为什么不直接从 .jsx 推断**：TS 对 JS 文件里的解构参数，会把「有默认值」的推断成
 * 可选、把「没有默认值的」推断成**必需**。而 MicroSlats 的参数里
 * `scale / speed / direction / chop / stretch / glint / contrast / perspective / fog`
 * 这九个恰恰没有默认值（它们的语义是"不给就用 preset 里的值"，见组件里的 `pick()`），
 * 于是展开一组不写这些 key 的 props 会报 TS2740「缺少属性」。
 *
 * 加声明文件而不是给源码补默认值：那个文件是 reactbits 上游源码原样引入的，
 * 它的价值就在于和上游一致（见文件头的说明）。类型是我们的关切，就放在我们这侧。
 *
 * 全部可选是准确的 —— 组件本身对每个参数都有兜底（`pick()` 回落到 preset）。
 */
export interface MicroSlatsProps {
  /** 波形预设名，见组件里的 SWELL_PRESETS。默认 'swell'。 */
  preset?: string
  /** 板条颜色。默认 '#A855F7'。 */
  color?: string
  /** 浪峰高光色。默认 '#ffffff'。 */
  glintColor?: string
  /** 水面底色（板条之间的缝）。默认 '#000000'。 */
  backgroundColor?: string
  slatWidth?: number
  slatHeight?: number
  gap?: number
  roundness?: number
  /** 以下九个不传则取 preset 里的值。 */
  scale?: number
  speed?: number
  direction?: number
  chop?: number
  stretch?: number
  glint?: number
  contrast?: number
  perspective?: number
  fog?: number
  /** 是否响应鼠标（在 window 上听 pointermove）。默认 true。 */
  interactive?: boolean
  cursorStrength?: number
  cursorSize?: number
  swirl?: number
  trail?: number
  lean?: number
  /** 是否播放入场动画。默认 true。 */
  intro?: boolean
  introDuration?: number
  /** 传 true 冻结水面（仍渲染，只是不推进时间）。默认 false。 */
  paused?: boolean
  className?: string
  style?: CSSProperties
}

declare const MicroSlats: ComponentType<MicroSlatsProps>
export default MicroSlats

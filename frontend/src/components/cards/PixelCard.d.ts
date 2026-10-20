import type { ComponentType, CSSProperties, ReactNode } from 'react'

/**
 * reactbits PixelCard 的类型声明（源码是 .jsx，见 PixelCard.jsx）。
 *
 * **每个 prop 都必须写 `?`**：TS 对 JS 里的解构参数，会把"没有默认值的"推断成必需，
 * 而这里 `colors` / `gap` / `speed` / `glowColor` / `noFocus` / `style` / `children`
 * 七个正好没有默认值 —— 不显式标可选，调用处会报 TS2740。
 * 同理，import 时**不要带 `.jsx` 扩展名**，否则会绕过这份 .d.ts。
 */
export interface PixelCardProps {
  /** 预设调色板（自带 gap / speed），传 `colors` 会盖掉它。默认 `'default'` */
  variant?: 'default' | 'blue' | 'yellow' | 'pink'
  /** 像素颜色，数组或逗号分隔字符串。默认取 variant 在**当前 theme 下**的那一组 */
  colors?: string | string[]
  /** 像素间距 px。默认取 variant */
  gap?: number
  /** 闪烁速度 0–100。默认取 variant */
  speed?: number
  /** 像素最大边长 px。默认 2 */
  pixelSize?: number
  shape?: 'square' | 'circle' | 'diamond'
  /** 揭示波的起点：卡片中心，或指针进入的位置 */
  origin?: 'center' | 'pointer'
  /** 揭示波扫过整张卡的时长 ms。默认 600 */
  revealDuration?: number
  /** 光标附近像素的膨胀/提亮程度 0–1 */
  spotlight?: number
  /** 像素底下那团柔光的强度 0–1 */
  glow?: number
  /** 柔光颜色。默认取 variant */
  glowColor?: string
  /** 像素与柔光的整体不透明度。卡上有字时调低它。默认 1 */
  opacity?: number
  /** 像素往中间淡出的程度 0–1，用来给内容让出干净的中心。默认 0 */
  fade?: number
  trigger?: 'hover' | 'click' | 'always'
  clickPulse?: boolean
  /** 首次滚入视口时自动播一次揭示。默认 true */
  intro?: boolean
  /** 揭示前的等待 ms，一排卡片可以用它错开 */
  introDelay?: number
  /** 不让键盘聚焦触发效果。默认取 variant */
  noFocus?: boolean
  theme?: 'dark' | 'light'
  /** 圆角 px。默认 24 */
  radius?: number
  className?: string
  /**
   * 内联样式。**要改卡片尺寸只能走这里** —— 宽高被 `.pixel-card` 这个类写死成
   * 300×400，内联样式赢过类选择器，不受 dev/prod 下 vendored CSS 先后顺序影响。
   */
  style?: CSSProperties
  children?: ReactNode
}

declare const PixelCard: ComponentType<PixelCardProps>
export default PixelCard

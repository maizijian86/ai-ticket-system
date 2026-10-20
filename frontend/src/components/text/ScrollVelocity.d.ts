import type { ComponentType, CSSProperties, RefObject } from 'react'

/**
 * ScrollVelocity 的类型声明。理由同 MicroSlats.d.ts：从 .jsx 推断会把没有默认值的
 * 参数（`scrollContainerRef`、`parallaxStyle`、`scrollerStyle`）判成必需，而
 * `texts = []` 会被推断成 `never[]`，于是传一个普通的 string[] 反而报错。
 *
 * 两个上游行为上的注意点（不是类型问题，见组件文件头的说明）：
 *   1. 它按 **window** 滚动算速度。本项目页面就是 window 在滚，契合。
 *   2. 每个 text 一个副本组，**奇偶行方向自动相反**（index % 2 !== 0 取负），
 *      所以要两行反向滚动，传两个 text 即可，不用自己算方向。
 */
export interface ScrollVelocityProps {
  /** 传了就按这个容器的滚动算速度，不传按 window。 */
  scrollContainerRef?: RefObject<HTMLElement | null>
  /** 一行一条。奇数行（下标 1、3…）的方向自动取反。 */
  texts?: string[]
  /** 基础速度，px/秒。0 = 完全静止（给 prefers-reduced-motion 用）。默认 100。 */
  velocity?: number
  /** 贴在每个文本 span 上的类名。 */
  className?: string
  damping?: number
  stiffness?: number
  /** 每行复制几份用于无缝循环。默认 6。 */
  numCopies?: number
  /** 把 window 滚动速度映射成速度倍率。 */
  velocityMapping?: { input: number[]; output: number[] }
  /** 每个文本外层那圈（overflow 容器）的类名。 */
  parallaxClassName?: string
  /** 内层滚动条的类名。 */
  scrollerClassName?: string
  parallaxStyle?: CSSProperties
  scrollerStyle?: CSSProperties
}

export declare const ScrollVelocity: ComponentType<ScrollVelocityProps>
declare const ScrollVelocityDefault: ComponentType<ScrollVelocityProps>
export default ScrollVelocityDefault

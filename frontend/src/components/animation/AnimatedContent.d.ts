import type { ComponentType, CSSProperties, HTMLAttributes, ReactNode } from 'react'

/**
 * AnimatedContent 的类型声明。理由与 GlassSurface.d.ts / Iridescence.d.ts 同：
 * 从 `.jsx` 直接推断时，TS 会把「没有默认值的解构参数」判成**必需**，展开一组 props
 * 就会报 TS2740。
 *
 * **import 时不能带 `.jsx` 后缀**（带了 TS 会绕开本文件去读源码）：
 * `import AnimatedContent from '@/components/animation/AnimatedContent'`。
 *
 * 与 GlassSurface 不同，这个组件的根 div **会把其余 HTML 属性摊上去**
 * （`<div ... {...props}>`），所以这里继承 HTMLAttributes 是准确的。
 */
export interface AnimatedContentProps
  extends Omit<HTMLAttributes<HTMLDivElement>, 'children' | 'style' | 'className'> {
  children?: ReactNode
  /** 驱动动画的滚动容器，选择器或元素。默认页面本身。 */
  container?: string | HTMLElement | null
  /** 入场位移距离（px）。默认 100。 */
  distance?: number
  /** 位移轴。默认 'vertical'。 */
  direction?: 'vertical' | 'horizontal'
  /** 从反方向进入。默认 false。 */
  reverse?: boolean
  /** 动画时长（秒）。默认 0.8。 */
  duration?: number
  /** 任意 GSAP ease。默认 'power3.out'。 */
  ease?: string
  /** 起始不透明度。默认 0。 */
  initialOpacity?: number
  /** 是否淡入。默认 true。 */
  animateOpacity?: boolean
  /** 起始缩放。默认 1。 */
  scale?: number
  /** 起始模糊（px）。默认 0。 */
  blur?: number
  /** 起始旋转角度。默认 0。 */
  rotate?: number
  /** 3D 翻起角度。默认 0。 */
  tilt?: number
  /** 擦除式显影。默认 'none'。 */
  wipe?: 'none' | 'up' | 'down' | 'left' | 'right' | 'center'
  /** 内容顶部进入视口多深才播放，0–1。默认 0.1。 */
  threshold?: number
  /** 延迟（秒）。默认 0。 */
  delay?: number
  /** 直接子元素错峰间隔（秒）。默认 0。 */
  stagger?: number
  /** 错峰顺序。默认 'start'。 */
  staggerFrom?: 'start' | 'end' | 'center' | 'edges' | 'random'
  /** 'scroll' 由视口触发，'mount' 挂载即播。默认 'scroll'。 */
  trigger?: 'scroll' | 'mount'
  /** 只播一次。默认 true。 */
  once?: boolean
  /** 与滚动位置绑定，数字表示平滑秒数。默认 false。 */
  scrub?: boolean | number
  /** 出现后多少秒重新隐藏，0 = 一直显示。默认 0。 */
  disappearAfter?: number
  disappearDuration?: number
  disappearEase?: string
  onStart?: () => void
  onComplete?: () => void
  onDisappearanceComplete?: () => void
  className?: string
  style?: CSSProperties
}

declare const AnimatedContent: ComponentType<AnimatedContentProps>
export default AnimatedContent

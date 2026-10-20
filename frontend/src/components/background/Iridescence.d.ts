import type { ComponentType, CSSProperties } from 'react'

/**
 * Iridescence 的类型声明。
 *
 * 为什么要有这个文件（跟 MicroSlats.d.ts 同一条理由）：TS 对 JS 文件里的解构
 * 参数，会把**没有默认值**的推断成必需。这个组件的参数几乎都有默认值，只有
 * `style` 没有 —— 所以直接展开一组 props 会在 `style` 上报 TS2740（MicroSlats
 * 那边则是九个波形参数一起报）。
 *
 * 加声明文件而不是给源码补默认值：那个文件是 reactbits 上游源码原样引入的，
 * 价值就在于跟上游一致。类型是我们的关切，放在我们这一侧。
 *
 * 全部可选是准确的 —— 组件本身对每个参数都有兜底。
 */
export interface IridescenceProps {
  /** 叠在所有颜色上的染色。CSS 颜色字符串，或 0–1 的三元 RGB 数组。默认 [1, 1, 1]。 */
  color?: string | number[]
  /** 色相旋转，单位度。默认 0。 */
  hueShift?: number
  /** 色彩浓度。0 = 单色铬镜效果。默认 1。 */
  saturation?: number
  /** 整体亮度。默认 1.5。 */
  brightness?: number
  /** 褶皱之间的对比度。默认 1。 */
  contrast?: number
  /** 动画速度。0 为静止。默认 1。 */
  speed?: number
  /** 花纹尺寸。越大褶皱越宽。默认 1。 */
  scale?: number
  /** 叠加多少层褶皱，1–24。低更柔，高更繁复。默认 8。 */
  detail?: number
  /** 褶皱互相弯折的强度。默认 1。 */
  warp?: number
  /** 花纹旋转，单位度。默认 0。 */
  rotation?: number
  /** 是否响应指针（视差 / 搅动 / 光泽）。默认 true。 */
  mouseReact?: boolean
  /** 花纹朝指针漂移的距离。默认 0.1。 */
  amplitude?: number
  /** 褶皱像液体一样被指针拖着走。0 关闭。默认 0.5。 */
  stir?: number
  /** 指针周围柔和的提亮。默认 0.3。 */
  sheen?: number
  /** 点击处搅出一个缓慢展开的漩涡。默认 true。 */
  clickSwirl?: boolean
  /** 表面胶片颗粒，0–1。默认 0。 */
  grain?: number
  /** 边缘向页面淡出，0–1。淡出处画布是透明的。默认 0。 */
  fade?: number
  /** 整个效果的透明度。默认 1。 */
  opacity?: number
  /** 渲染倍率。降低它以牺牲清晰度换大屏下的性能。默认 1。 */
  resolution?: number
  /** 冻结动画（仍然渲染）。默认 false。 */
  paused?: boolean
  className?: string
  style?: CSSProperties
}

declare const Iridescence: ComponentType<IridescenceProps>
export default Iridescence

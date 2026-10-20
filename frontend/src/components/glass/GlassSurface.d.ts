import type { ComponentType, CSSProperties, ReactNode, RefObject } from 'react'

/**
 * GlassSurface 的类型声明。理由与 MicroSlats.d.ts / SpotlightCard.d.ts 同：
 * 从 `.jsx` 直接推断时，TS 会把「没有默认值的解构参数」判成**必需** ——
 * 这里是 `children` / `backdrop` / `shape` 三个，不写声明就展开一组 props 会报 TS2740。
 *
 * **import 时不能带 `.jsx` 后缀**（带了 TS 会绕开本文件去读源码）：
 * `import GlassSurface from '@/components/glass/GlassSurface'`。
 *
 * 根元素是个普通 div，**不转发其它 HTML 属性** —— 组件只把 `className` 和 `style`
 * 合并上去。所以这里不继承 HTMLAttributes：把 onClick 传进来不会报错也不会生效，
 * 那比报错更难查。要点击就把 GlassSurface 放进自己的 <button> 里（见 glass-button.tsx）。
 */
export interface GlassSurfaceProps {
  children?: ReactNode
  /** 数字按 px，字符串按原样（'100%' / '100vh' 都行）。默认 200。 */
  width?: number | string
  /** 同上。默认 80。 */
  height?: number | string
  /** 圆角，单位 px（注意传大数不会出错，CSS 和 SVG 都会自己夹到半高）。默认 20。 */
  borderRadius?: number
  /** 用一个 SVG / 透明 PNG 当玻璃的轮廓，会按 width/height 等比内嵌。给了就别用 borderRadius。 */
  shape?: string
  /** 是否折射。false = 全块磨砂。默认 true。 */
  refraction?: boolean
  /** 'auto' 在 Chromium 走 SVG 滤镜、在 Safari/Firefox 有 backdrop 时走 WebGL。默认 'auto'。 */
  renderer?: 'auto' | 'svg' | 'webgl'
  /** 玻璃背后那张图/视频/画布。WebGL 只能折射你给它的东西。 */
  backdrop?:
    | RefObject<HTMLImageElement | HTMLVideoElement | HTMLCanvasElement | null>
    | HTMLImageElement
    | HTMLVideoElement
    | HTMLCanvasElement
    | null
  /** 边缘折射强度，负数往里收。默认 -180。 */
  distortionScale?: number
  /** 折射边的宽度，占短边的比例。默认 0.07。 */
  borderWidth?: number
  /** 折射边到平静中心的过渡柔度（px）。默认 11。 */
  blur?: number
  /** 平静中心的亮度（%），50 = 不失真。默认 50。 */
  brightness?: number
  /** 平静中心把画面"定住"的程度，0–1。默认 0.93。 */
  opacity?: number
  /** 透过玻璃的光被打散的像素数 —— 磨砂感。默认 0。 */
  displace?: number
  /** 三个通道各自的额外偏移，拉开就是彩色边缘。默认 0 / 10 / 20。 */
  redOffset?: number
  greenOffset?: number
  blueOffset?: number
  /** 覆盖在玻璃上的一层柔和磨砂染色，0–1。默认 0。 */
  backgroundOpacity?: number
  /** 透过玻璃看到的东西的饱和度。默认 1。 */
  saturation?: number
  /** 位移贴图里管横向/纵向的通道。默认 'R' / 'G'。 */
  xChannel?: 'R' | 'G' | 'B'
  yChannel?: 'R' | 'G' | 'B'
  /** 位移贴图两张边缘渐变怎么叠。默认 'difference'。 */
  mixBlendMode?: string
  className?: string
  style?: CSSProperties
}

declare const GlassSurface: ComponentType<GlassSurfaceProps>
export default GlassSurface

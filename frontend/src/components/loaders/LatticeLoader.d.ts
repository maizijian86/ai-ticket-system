import type { ComponentType, CSSProperties } from 'react'

/**
 * reactbits LatticeLoader 的类型声明（源码是 .jsx，见 LatticeLoader.jsx）。
 *
 * 理由与 MicroSlats.d.ts / GlassSurface.d.ts 同：TS 对 JS 的解构参数会把
 * 「没有默认值的」推断成必需 —— 这里是 `elapsed` 和 `style` 两个，不写声明时
 * 调用处会报 TS2740。所以这里**每个 prop 都显式标了 `?`**。
 * 同理，import 时**不要带 `.jsx` 扩展名**，否则会绕开这份 .d.ts：
 *
 *     import LatticeLoader from '@/components/loaders/LatticeLoader'
 *
 * 根节点是个 `<span role="status">`，组件**不转发其它 HTML 属性**（没有 props 展开），
 * 所以这里刻意不继承 HTMLAttributes —— 把 onClick 传进来既不报错也不生效，那比
 * 报错更难查。要点击就把它塞进自己的按钮里。
 *
 * 无障碍不用自己加：`role="status"` + 内嵌的 `.lattice-loader__sr` 会朗读状态与耗时，
 * 那截文字是给读屏器的，页面上看不见。
 */

/** 自定义波形的形状：每个格子一个延迟（单位是 step），null = 那个位置是个洞。 */
export interface LatticePattern {
  /** 长度应等于 grid²，短了按 null 补。 */
  cells: Array<number | null>
  /** 一整圈的时长（单位是 step 的倍数）。默认 = 最大延迟 + 4.2。 */
  loop?: number
  /** 邻居之间延迟差的倍率，越大波跑得越慢、圈子越大。默认 1。 */
  scale?: number
  /** 一个格子在整圈里保持亮着的比例：0.25 / 0.35 / 0.45 / 0.62。默认 0.62。 */
  lit?: number
}

/** 内置波形名。3×3 与 4×4 各有一套，**grid 和名字不匹配时会静默退回该尺寸的默认波形**。 */
export type LatticePatternName =
  // 3 × 3
  | 'arrow'
  | 'dots'
  | 'orbit'
  | 'ripple'
  | 'snake'
  | 'spiral'
  // 4 × 4
  | 'pulse'
  | 'rain'
  | 'sweep'
  | 'spin'

export interface LatticeLoaderProps {
  /** 进行中的动词。默认 `'Thinking'`（英文）。 */
  label?: string
  /** 完成后的动词，紧跟着冻结的耗时。默认 `'Done in'`。 */
  doneLabel?: string
  /** 失败后的动词。默认 `'Failed after'`。 */
  errorLabel?: string
  /**
   * 唯一的开关。`working` 跑波动画并走秒表；切成 `done` / `error` 时动画**冻住**、
   * 溶解成一个对勾/叉，秒表停在那一刻。
   *
   * 所以别用「条件渲染换一个组件」表达完成 —— 卸载重挂就看不到那一下溶解，
   * 而且秒表会从 0 重新开始。让同一个实例从 working 变过去。
   */
  status?: 'working' | 'done' | 'error'
  /** 波形名或自定义形状。默认 `'orbit'`（3×3）/ `'sweep'`（4×4）。 */
  pattern?: LatticePatternName | LatticePattern
  /** 每边几格。3×3 更"点阵"，4×4 更"扫描"。默认 3。 */
  grid?: 3 | 4
  /** 圆点还是圆角方块。默认 `'round'`。 */
  shape?: 'square' | 'round'
  /**
   * 格子和文字的墨色。默认 `'currentColor'` —— **全站白底黑字时它默认就是墨色**，
   * 想换色改外层文本颜色比传这个更省事（`label`、秒表会一起变）。
   */
  color?: string
  /** 对勾的颜色。默认 `'#22c55e'`。 */
  doneColor?: string
  /** 叉的颜色。默认 `'#ef4444'`。 */
  errorColor?: string
  /** 单个格子边长 px。默认 6。 */
  cellSize?: number
  /** 格子之间的缝 px。默认 2。 */
  gap?: number
  /** 文字 px；秒表的字号和这行的高度都跟着它缩放。默认 14。 */
  fontSize?: number
  /** 相邻格子点亮的时间差 ms，整圈时长随它等比缩放。默认 90。 */
  step?: number
  /** 没被点亮的格子有多可见（那层暗色剪影）。默认 0.15。 */
  idleOpacity?: number
  /** 给点亮的格子加光晕。**白底上默认别开** —— 光晕是发光特效，压在纯白上看不见。 */
  glow?: boolean
  /** 光晕颜色，留空则跟随墨色 / 对勾叉的颜色。 */
  glowColor?: string
  /** 显示秒表。默认 true。AI 那种好几分钟的等待就靠它给"还在动"的实感。 */
  showTimer?: boolean
  /** 受控的已用秒数。**传了它内部时钟就不再跑**（适合把耗时存在外层 state 里）。 */
  elapsed?: number
  className?: string
  /** 内联样式，最后合并 —— 里面写 `--ll-*` 变量可以覆盖组件自己算的那几个。 */
  style?: CSSProperties
}

declare const LatticeLoader: ComponentType<LatticeLoaderProps>
export default LatticeLoader

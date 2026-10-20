import { useEffect, useRef, type ButtonHTMLAttributes } from 'react'
import GlassSurface from '@/components/glass/GlassSurface'
import { cn } from '@/lib/utils'

/*
  玻璃按钮 —— 取代原来 `bg-primary`（紫色实心）的那批主按钮。

  结构是**玻璃在底下当背景、按钮本体在外层**：

      <button class="relative ...">          ← 真正可点、可聚焦、定尺寸的元素
        <GlassSurface class="absolute inset-0" />   ← 铺满按钮整个盒子（含内边距）
        <span class="relative z-10">标签</span>
      </button>

  反过来把按钮塞进 GlassSurface 的 children 里不行：GlassSurface 的
  `.glass-surface__content` 自带 `padding: 0.5rem`，按钮会被缩掉一圈（32px 的胶囊
  只剩 16px 可点）。

  2026-10-19 起走的是 **WebGL 渲染器**：它只折射我们显式交给它的那张虹彩 canvas，
  不再采样"元素背后已绘制的内容"。这免掉了 SVG 版的两条几何约束（祖先有
  backdrop-filter/filter/opacity/transform 就会关掉折射），所以玻璃放在磨砂顶栏里
  也能看见真正的折射。但反过来，它折射的**永远是虹彩**，压在白卡上时会从白卡里
  "透出"一小块蓝色 —— 需要"融进白面"的场景仍该用实心按钮。

  尺寸/排版由调用方用 className 给（仓库里原来的按钮就是这么写的），这里只钉住
  「相对定位 + 圆角 + 居中 + 前景色」这几条玻璃必须的前提。
*/
type GlassButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  /** 圆角 px。默认 999 = 胶囊；GlassSurface 的 SVG 和 CSS 都会自己夹到半高。 */
  radius?: number
  /** 关掉折射，退成一块磨砂（调试或者压在纯色上时用）。 */
  refraction?: boolean
}

/*
  玻璃那层本身 —— 「铺满调用方整个盒子的一块玻璃」。做成了独立组件，因为有**两块
  必须一起搬的知识**，散在调用处迟早会漏：

  1. **位置只能写内联 style，不能用 Tailwind 的 `absolute inset-0`。** 这不是风格
     偏好，是踩出来的：`.glass-surface` 自带 `position: relative`，而**开发模式下
     vendored CSS 是后于 index.css 注入的，顺序和产物里正好相反**（产物里它在前、
     工具类在后，所以那边写 `absolute` 能赢；dev 这边赢不了）。两种顺序通吃的只有
     内联样式。症状很显眼但容易认错：玻璃不脱流、变成一个 flex 项去抢宽度，盒子塌成
     一条、文字被挤成一列一个字。
  2. **折射的那几个参数**（`distortionScale -180` / `displace 0.5` /
     `backgroundOpacity 0.1`）是用户 2026-10-19 指定的取值（reactbits 的示例参数），
     逐个调用处抄一遍，调的时候必然漏掉几处。

  `blur={4}` **不是**上游默认（11）。11 是"折射边到平静中心"的过渡柔度，在 320×116
  这种矮卡上太宽，过渡环被推到离边框约 10px 的地方，沿圆角连成一整条内缩的轮廓线，
  四个角看起来像压了两个小纽扣（用户 2026-10-18 报的就是这个）。收到 4 之后折射只
  贴着边框走一圈，是"薄玻璃边"而不是"内嵌按钮"。**别再把它当美观参数随手调大。**
  用户 2026-10-19 给的示例没有列 blur，它不属于这次改的那组取值，保持 4。

  `frost` 是给玻璃自己加的一层白雾（GlassSurface 的 `backgroundOpacity` → CSS 变量
  `--glass-frost`，WebGL 下由 `__frame` 那层画出来）。默认 0.1，即用户 2026-10-19
  给的示例取值；0 = 完全透明、只靠边缘折射立形。**要表达"选中/按下"就调它，别去给
  外层换底色**：底色画在玻璃**底下**，WebGL 渲染器根本不采样它（只认 backdrop），
  等于没画。
*/
type GlassLayerProps = {
  /** 圆角 px。必须与调用方盒子自己的圆角一致，否则玻璃四角会露出方角。 */
  radius?: number
  /** 白雾浓度 0–1。默认 0.1 是用户 2026-10-19 指定的一组取值之一。 */
  frost?: number
  /** 关掉折射，退成一块磨砂。 */
  refraction?: boolean
}

/*
  全站背景（Iridescence）那张 `<canvas>` 的引用。

  **WebGL 渲染器只能折射你显式交给它的东西** —— 和 SVG 版直接采样"元素背后已绘制的
  内容"不同。组件里 `webgl = ... && Boolean(backdrop)`：不给 backdrop，`renderer="webgl"`
  是个空指令，Chromium 下会静默退回 SVG 分支。全站唯一画在玻璃底下、又能当纹理的东西
  就是 AppLayout 里那层虹彩，所以这里把它找出来。

  查询时机：GlassLayer 的 effect 和 Iridescence 挂在同一次 commit 里，正常情况下画布
  已经在 DOM 中；万一还没挂（例如将来把玻璃放进 portal），退几帧重试，避免永久空纹理。
  留空的后果是玻璃只剩那圈内阴影与白膜，看起来像"没渲染出来"。
*/
function useSiteBackdrop() {
  const backdrop = useRef<HTMLCanvasElement | null>(null)

  useEffect(() => {
    let raf = 0
    let tries = 0
    const find = () => {
      const canvas = document.querySelector<HTMLCanvasElement>('canvas.iridescence-canvas')
      if (canvas) backdrop.current = canvas
      else if (tries++ < 30) raf = requestAnimationFrame(find)
    }
    find()
    return () => cancelAnimationFrame(raf)
  }, [])

  return backdrop
}

export function GlassLayer({ radius = 999, frost = 0.1, refraction }: GlassLayerProps) {
  const backdrop = useSiteBackdrop()
  return (
    <GlassSurface
      style={{ position: 'absolute', inset: 0 }}
      width="100%"
      height="100%"
      borderRadius={radius}
      distortionScale={-180}
      blur={4}
      displace={0.5}
      backgroundOpacity={frost}
      refraction={refraction}
      /*
        用户 2026-10-19："换个渲染器" —— 照 spec 里的 WebGL 用法（backdrop +
        renderer="webgl"）。强制 WebGL 的好处是它不依赖 backdrop-filter：
        祖先的 backdrop-filter / filter / opacity / transform 都关不住它，
        顶栏那个玻璃按钮也能真的折射虹彩，而不是只靠一圈 inset 阴影立形。
      */
      renderer="webgl"
      backdrop={backdrop}
    />
  )
}

export function GlassButton({ className, children, radius = 999, refraction, ...props }: GlassButtonProps) {
  return (
    <button
      {...props}
      className={cn(
        'relative inline-flex cursor-pointer items-center justify-center gap-2 rounded-full font-semibold',
        'text-foreground transition-transform duration-150 active:translate-y-px',
        'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-foreground/50',
        'disabled:pointer-events-none disabled:opacity-50',
        className
      )}
    >
      <GlassLayer radius={radius} refraction={refraction} />
      <span className="relative z-10 inline-flex items-center gap-2">{children}</span>
    </button>
  )
}

/*
  小图块（logo、头像、图标底托、选中标记）用的两套配方。**它们不用玻璃**，两条理由：

  1. 这些块只有 24–44px。GlassSurface 的位移贴图在这个尺寸上糊成一团，看不出折射，
     白搭一个 SVG 滤镜 + ResizeObserver。
  2. 更要命的是**玻璃只能在有东西可透的地方显形**。顶栏是 70% 白 + 磨砂、白卡是纯白、
     移动端浮层是纯白背景 —— 半透明白压在这些上面就是一块白，连边都看不见。
     原来那批 `bg-primary` 图块正长在这三种底上。

  所以按底色分两档：
  - `INK_TILE`：浅中性底，压在**白面**上用（顶栏、白卡、移动端浮层）。
  - `SOLID_TILE`：实心近黑，压在**任何地方**都够重 —— 原来的实心紫块（品牌标、头像、
    选中徽标）对应的是"强调"，换成中性就该是最重的墨色，降成淡灰会丢掉强调。
*/
export const INK_TILE = 'border border-foreground/15 bg-foreground/8 text-foreground'
export const SOLID_TILE = 'bg-foreground text-background'

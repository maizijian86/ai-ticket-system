import { useLayoutEffect, useState } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger
} from '@/components/ui/dropdown-menu'
import Iridescence from '@/components/background/Iridescence'
import { GlassButton, SOLID_TILE } from '@/components/ui/glass-button'
import { ArrowDown, Menu, UserRound, X } from '@/components/icons'
import { selectIsAdmin, useAuthStore } from '@/stores/authStore'
import { useUiStore } from '@/stores/uiStore'

/*
  全站背景：虹彩（Iridescence）。

  **它是不透明地铺满画布的那类背景**（`opacity` 默认 1、`fade` 默认 0），页面自己的
  `bg-background` 被整个盖在下面 —— 所以现在全站真正的底色就是这个着色器的输出，
  `index.css` 里那套白底只在顶栏那种半透明的地方露一点脸。

  props 是照 reactbits 上游那份用法示例逐字抄的，没有自己加值：
  `color #3B82F6`（蓝）、`brightness 1.35`、`mouseReact false`、`clickSwirl false`。
  两个 false 是有意的 —— 这样背景是**静的**，不和页面上的鼠标动作抢戏。
  注意 `stir` / `sheen` / `amplitude` 这时是**失效的**：着色器里
  `uStir = mouseReact ? stir : 0`、`uPointer.z` 同理，所以留着它们只是为了和上游
  那份示例一致，改它们不会有任何效果。

  性能：`resolution: 1` 全屏重绘；静止（暂停或系统开了"减少动态效果"）时帧循环会
  自己停下来。大屏上嫌烫就把 resolution 降到 0.75。

  要换回那片海：把 MicroSlats 渲染回来（文件都还在 components/background/ 下）。
*/
const IRIDESCENCE_PROPS = {
  color: '#3B82F6',
  hueShift: 0,
  saturation: 1,
  brightness: 1.35,
  contrast: 1,
  speed: 1,
  scale: 1,
  detail: 8,
  warp: 1,
  rotation: 0,
  mouseReact: false,
  amplitude: 0.1,
  stir: 0.5,
  sheen: 0.3,
  clickSwirl: false,
  grain: 0,
  fade: 0
}

/*
  导航项。顶栏和移动端浮层两处渲染同一份数据，改动只需要动一处。

  项目流水（我的需求/待处理/我接的单/已完成）不在这里：它们说的是同一件事，
  入口统一由大厅顶上的身份卡片承载。那些 URL 在路由表里重定向回 /tickets。

  **「知识库」已移除**（2026-10）：后端 knowledge_base 表没有任何代码读写 ——
  它唯一的消费者是 ai-service 里那套 MiniMax 时代的 AiService，而那个模块已经
  整个删掉了（见 Phase 3）。留着入口只会把人引到一个空模块。
  等知识库功能真立项时，先补后端接口，再把这一项加回来。

  **「项目管理」已移除**（2026-10）：整个模块下线，后端 32 个接口、5 张表、
  前端 api 与类型全部删除。它当初的设想是"接单后管理交付过程"，但和工单流程
  完全没打通（Project 表里连 ticketId 都没有），现网 7 个项目全是自动化测试留下的。
  这个位置改成「个人信息统计」—— 回答"我干得怎么样"，与大厅的"有什么活"互补。
*/
const NAV_ITEMS = [
  { path: '/tickets', label: '需求大厅' },
  { path: '/stats', label: '个人信息统计' }
]

export default function AppLayout() {
  const navigate = useNavigate()
  const location = useLocation()

  const token = useAuthStore((s) => s.token)
  const userInfo = useAuthStore((s) => s.userInfo)
  const isAdmin = useAuthStore(selectIsAdmin)
  const logoutRemote = useAuthStore((s) => s.logoutRemote)
  const openAuthCard = useUiStore((s) => s.openAuthCard)

  const [menuOpen, setMenuOpen] = useState(false)

  /*
    换页回到顶部（用户 2026-10-20）。

    SPA 换路由时浏览器**不会**把 scrollY 归零 —— 它只在整页导航时做历史位置恢复，
    pushState 之后滚动位置原样留着。于是从大厅滚到第 30 张卡再点进去，详情页一打开
    就停在半截，看起来像"详情页缺了上半部分"。

    `/tickets` 必须跳过：大厅自己有快照恢复（TicketList 挂载后按 `restored.scrollY`
    滚回去），在这里抢先归零，"返回大厅"就永远回到顶部了。只盯 pathname，大厅内部
    切档位、翻页都不会触发。

    用 layout effect 而不是 effect：它在新页面绘制前跑完，View Transition 拍到的新
    快照就是顶部 —— 共享元素卡片才会从大厅那屏直接落到页头，而不是落到屏幕中间。
  */
  const pathname = location.pathname
  useLayoutEffect(() => {
    if (pathname === '/tickets') return
    window.scrollTo(0, 0)
  }, [pathname])

  function isActive(path: string) {
    return location.pathname === path || location.pathname.startsWith(path + '/')
  }

  function go(path: string) {
    setMenuOpen(false)
    navigate(path)
  }

  async function handleLogout() {
    await logoutRemote()
    // 登出后回大厅并弹登录卡片：/login 整页路由已删，而且用户登出后最可能的
    // 下一步就是「换个账号登录」，直接把卡片摆出来比留一个空页面合理。
    navigate('/tickets')
    openAuthCard('login')
  }

  return (
    <div className="flex min-h-screen flex-col bg-background">
      {/*
        虹彩是 `fixed inset-0 z-0` 的一层，**pointer-events-none**。

        分层是这里的硬约束：定位元素（z-index 0 也是定位）画在**普通流内容之上**，
        所以内容必须抬起来才不会被它盖住 —— 顶栏是 `sticky z-50`、<main> 是
        `relative z-10`，两处都不能去掉。加新的顶层容器时记得同一条。

        关掉命中也是必须的：它盖住的正是不吃事件的留白区（页边距、区块间距、页脚），
        开着的话那些地方的点击会被它吞掉。它自己是在 window 上听 pointermove 的，
        不需要命中 —— 拖动褶皱、点出漩涡都照常好用。
      */}
      <div className="fixed inset-0 z-0 pointer-events-none" aria-hidden="true">
        <Iridescence className="size-full" {...IRIDESCENCE_PROPS} />
      </div>

      {/*
        顶部导航：56px 磨砂条。**底下一有东西可透，就还是半透明的好** —— 全白底
        那版改成实心是因为没东西可透，现在虹彩回来了，实心白条会变成一道横贯屏幕
        的色带，把它压在身下的那截背景齐齐切断。所以恢复 `bg-background/70` +
        backdrop-blur：像一块毛玻璃压在虹彩上，滚动时内容从底下穿过去。
      */}
      <header className="sticky top-0 z-50 border-b border-border bg-background/70 backdrop-blur-md">
        <div className="mx-auto flex h-14 w-full max-w-[1200px] items-center gap-6 px-6">
          {/*
            品牌名点进首页，不是大厅 —— 顶栏左上角在用户心里就是"回首页"，
            指向需求大厅会让人以为自己已经在首页。

            LOGO 用 `logo.png`（黑色字形 + 透明底，由用户给的图裁切生成，见
            frontend/public/ 下三个文件）。顶栏是浅色磨砂，直接放黑色字形，
            不再套那个黑底图标块 —— 用户给的就是黑字标，套块等于改了它的样子。
          */}
          <button
            type="button"
            onClick={() => navigate('/')}
            className="flex cursor-pointer items-center gap-2 font-semibold tracking-tight"
          >
            <img src="/logo.png" alt="" className="h-6 w-auto" />
            <span>百川灵构</span>
          </button>

          <nav className="hidden items-center gap-1 min-[960px]:flex">
            {NAV_ITEMS.map((item) => (
              <button
                key={item.path}
                type="button"
                onClick={() => navigate(item.path)}
                className={[
                  'cursor-pointer rounded-md px-3 py-1.5 text-sm font-medium transition-colors',
                  isActive(item.path)
                    ? 'bg-accent text-foreground'
                    : 'text-muted-foreground hover:text-foreground'
                ].join(' ')}
              >
                {item.label}
              </button>
            ))}

            {/*
              管理入口是**管理员专有**的，摆在主导航里会让所有人都看到一栏自己点不开
              的东西 —— 收进一个下拉。
            */}
            {isAdmin && (
              <DropdownMenu>
                <DropdownMenuTrigger
                  className={[
                    'flex cursor-pointer items-center gap-1 rounded-md px-3 py-1.5 text-sm font-medium transition-colors',
                    location.pathname.startsWith('/admin')
                      ? 'bg-accent text-foreground'
                      : 'text-muted-foreground hover:text-foreground'
                  ].join(' ')}
                >
                  管理
                  <ArrowDown size={12} />
                </DropdownMenuTrigger>
                <DropdownMenuContent align="start">
                  <DropdownMenuItem onClick={() => navigate('/admin/users')}>用户管理</DropdownMenuItem>
                  <DropdownMenuItem onClick={() => navigate('/admin/stats')}>统计分析</DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            )}
          </nav>

          {/*
            右上角的一对胶囊。
            **同一个视口里只出现一枚主按钮**：访客看到的是「注册(次) + 登录(主)」，
            登录后换成「开通会员(次) + 发布需求(主)」，主按钮始终对应这个页面最该
            发生的那件事。

            「发布需求」只给 PUBLISHER（用户要求：发布者才有发布按钮，其他角色都没有）。
            这是**前端鉴别**：后端没有按角色拦创建，别把这里当成权限边界。ADMIN 与
            存量的 USER 身份同样不显示 —— 按用户的话，"其他的都没有"。
          */}
          <div className="ml-auto flex items-center gap-2">
            {!token ? (
              <>
                <button
                  type="button"
                  onClick={() => openAuthCard('register')}
                  className="hidden h-8 cursor-pointer items-center rounded-full px-4 text-sm font-medium text-muted-foreground transition-colors hover:text-foreground min-[560px]:inline-flex"
                >
                  注册领额度
                </button>
                <GlassButton
                  type="button"
                  onClick={() => openAuthCard('login')}
                  className="h-8 px-4 text-sm"
                >
                  登录
                </GlassButton>
              </>
            ) : (
              <>
                <button
                  type="button"
                  onClick={() => navigate('/membership')}
                  className="hidden h-8 cursor-pointer items-center rounded-full px-4 text-sm font-medium text-muted-foreground transition-colors hover:text-foreground min-[560px]:inline-flex"
                >
                  开通会员
                </button>
                {userInfo?.role === 'PUBLISHER' && (
                  <GlassButton
                    type="button"
                    onClick={() => navigate('/tickets/new')}
                    className="h-8 px-4 text-sm max-[559px]:hidden"
                  >
                    发布需求
                  </GlassButton>
                )}

                <DropdownMenu>
                  <DropdownMenuTrigger className="flex cursor-pointer items-center gap-2 rounded-full py-1 pr-3 pl-1 transition-colors hover:bg-accent">
                    <span className={`flex size-7.5 items-center justify-center rounded-full ${SOLID_TILE}`}>
                      <UserRound size={15} />
                    </span>
                    <span className="max-w-[120px] truncate text-sm font-medium">
                      {userInfo?.nickname || userInfo?.username}
                    </span>
                    <ArrowDown size={12} />
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end">
                    <DropdownMenuItem onClick={() => navigate('/profile')}>个人主页</DropdownMenuItem>
                    {/* 钱包放在「个人主页」下面、「开通会员」上面：
                        它是"我的资产"的一部分，但比会员常用得多（发布工单要先充值）。 */}
                    <DropdownMenuItem onClick={() => navigate('/wallet')}>我的钱包</DropdownMenuItem>
                    <DropdownMenuItem onClick={() => navigate('/membership')}>开通会员</DropdownMenuItem>
                    {/*
                      分隔线把「去某个页面」和「退出」分开：退出是不可逆的副作用，
                      和上面两项混在一起容易被误点。
                    */}
                    <DropdownMenuSeparator />
                    <DropdownMenuItem
                      onClick={handleLogout}
                      className="text-destructive data-highlighted:text-destructive"
                    >
                      退出登录
                    </DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>
              </>
            )}

            {/* 960px 以下主导航收进汉堡，两颗胶囊保持可见 */}
            <button
              type="button"
              onClick={() => setMenuOpen(true)}
              aria-label="打开菜单"
              className="flex size-8 cursor-pointer items-center justify-center rounded-md text-muted-foreground hover:text-foreground min-[960px]:hidden"
            >
              <Menu size={18} />
            </button>
          </div>
        </div>
      </header>

      {/*
        此处原有系统亮点带（ScrollVelocity）。它先被挪去首页（不在这一层，
        所以别的页面一直没有卷轴文字），后来整条也撤掉了 —— 现在全站没有任何
        卷轴文字。组件文件留着没删，见 pages/Home.tsx 末尾那段说明。
      */}

      {/*
        key 让**参数变化时整页重挂**。
        没有它的话，/tickets/1 → /tickets/2 会被 React 当成同一次渲染，详情页里
        所有 useState（AI 面板展开没展开、评论框里的半截草稿、轮询的进度）都会
        原样带到下一张工单上 —— 表现为"打开另一张单，看到的还是上一张的展开状态"。
      */}
      <main className="relative z-10 flex-1">
        <Outlet key={location.pathname + location.search + location.hash} />
      </main>

      {/* 移动端菜单浮层：整幅画布 */}
      {menuOpen && (
        <div
          className="fixed inset-0 z-1000 flex flex-col gap-4 overflow-y-auto bg-background px-4 pt-6 pb-10"
          onClick={(e) => e.target === e.currentTarget && setMenuOpen(false)}
        >
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium tracking-[0.2em] text-muted-foreground uppercase">
              Menu
            </span>
            <button
              type="button"
              onClick={() => setMenuOpen(false)}
              aria-label="关闭菜单"
              className="flex size-8 cursor-pointer items-center justify-center rounded-md text-muted-foreground hover:text-foreground"
            >
              <X size={18} />
            </button>
          </div>
          <nav className="flex flex-col gap-1.5">
            {NAV_ITEMS.map((item) => (
              <button
                key={item.path}
                type="button"
                onClick={() => go(item.path)}
                className={[
                  'cursor-pointer rounded-lg border-b border-border px-1 py-4 text-left text-lg font-semibold tracking-tight',
                  isActive(item.path) ? 'bg-foreground/10 pl-3.5 text-foreground' : 'text-foreground'
                ].join(' ')}
              >
                {item.label}
              </button>
            ))}
            {isAdmin && (
              <>
                <button
                  type="button"
                  onClick={() => go('/admin/users')}
                  className={[
                    'cursor-pointer rounded-lg border-b border-border px-1 py-4 text-left text-lg font-semibold tracking-tight',
                    isActive('/admin/users') ? 'bg-foreground/10 pl-3.5 text-foreground' : 'text-foreground'
                  ].join(' ')}
                >
                  用户管理
                </button>
                <button
                  type="button"
                  onClick={() => go('/admin/stats')}
                  className={[
                    'cursor-pointer rounded-lg border-b border-border px-1 py-4 text-left text-lg font-semibold tracking-tight',
                    isActive('/admin/stats') ? 'bg-foreground/10 pl-3.5 text-foreground' : 'text-foreground'
                  ].join(' ')}
                >
                  统计分析
                </button>
              </>
            )}
          </nav>
        </div>
      )}
    </div>
  )
}

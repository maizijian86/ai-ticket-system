<template>
  <div class="app-layout">
    <!--
      全站背景：一片由板条拼成的水面（MicroSlats，见 components/background/）。

      这里是 **React 组件**，不是 Vue 的。项目主体仍是 Vue，靠
      components/react/ReactMount.vue 这个通用挂载点把它挂进来 —— 以后从
      React 生态借来的组件都走同一条路（见那个文件的注释）。

      它铺在整个应用最底下，靠**分层**而不是靠透明度让步：海是 z-index:0 的
      定位层，.app-main / 顶栏 / 跑马灯各自压在它之上。所以海只在页面的留白处
      露出来 —— 版心两侧的 gutter、区块间距、页面底部的 96px —— 而卡片是白底
      不透明的，正文永远压在自己那块干净的白面上。

      **配色是上游文档里的原始示例**：黑底(#000000) + 紫板条(#7d26c0)+ 白色浪峰。
      这跟 DESIGN.md 的亮色体系是拧着的 —— 页面的留白区会变成黑水面，落在
      gutter 上的深色文字（面包屑、区块副标题、空态提示）会看不清。要么把那些
      文字改成浅色，要么把下面三行换成亮色配色，二选一：

          background-color="#ffffff"  color="#bda6f2"  preset="tide"

      （亮色那套之前调过：color 太淡会让波浪形态整个消失，浓淡该用 contrast 调。）
    -->
    <div class="app-sea" aria-hidden="true">
      <ReactMount
        :component="MicroSlats"
        preset="swell"
        color="#7d26c0"
        glint-color="#ffffff"
        background-color="#000000"
        :slat-width="10"
        :slat-height="25"
        :gap="3"
        :roundness="0.75"
        :interactive="true"
        :cursor-strength="1"
        :cursor-size="40"
        :swirl="0"
        :trail="1.4"
        :lean="0"
        :intro="true"
      />
    </div>

    <!--
      顶部导航。原来是「左侧边栏 + 顶栏」的后台骨架，现按 DESIGN.md 换成
      Figma 官网那种 56px 单色导航：左品牌字标、中间导航链接、右侧一对黑白胶囊。

      为什么可以不要侧边栏：导航项本来就只有 3 项（+ 管理 2 项），侧边栏 240px
      的宽度主要是留给能折叠的三级菜单的；而设计文档的整条叙事（色块要占满内容
      宽度、display 字号要撑开）在窄栏里根本施展不开。少一列，色块才立得住。
    -->
    <header class="ds-topnav">
      <div class="ds-container ds-topnav__inner">
        <div class="ds-topnav__brand" @click="router.push('/tickets')">
          <span class="ds-topnav__mark">
            <el-icon size="15"><Monitor /></el-icon>
          </span>
          <span>DevMatch</span>
        </div>

        <nav class="ds-topnav__links">
          <button
            v-for="item in navItems"
            :key="item.path"
            class="ds-topnav__link"
            :class="{ 'is-active': isActive(item.path) }"
            @click="router.push(item.path)"
          >
            {{ item.label }}
          </button>

          <!--
            管理入口。侧边栏删掉之后它没有别的地方可去，但它是**管理员专有**的，
            摆在主导航里会让所有人都看到一栏自己点不开的东西 —— 收进一个下拉。
          -->
          <el-dropdown v-if="authStore.isAdmin" trigger="click" @command="router.push">
            <button class="ds-topnav__link" :class="{ 'is-active': route.path.startsWith('/admin') }">
              管理
              <el-icon size="12"><ArrowDown /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="/admin/users">用户管理</el-dropdown-item>
                <el-dropdown-item command="/admin/stats">统计分析</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </nav>

        <!--
          右上角的一对胶囊。
          **同一个视口里只出现一枚主按钮**（文档 Do：primary 要稀缺）：
          访客看到的是「注册(次) + 登录(主)」，登录后换成「开通会员(次) + 发布需求(主)」，
          主按钮始终对应这个页面最该发生的那件事。移动端这两颗仍然留在条上。
        -->
        <div class="ds-topnav__right">
          <template v-if="!authStore.token">
            <button class="ds-btn ds-btn--secondary nav-cta" @click="authStore.openAuthCard('register')">
              注册领额度
            </button>
            <button class="ds-btn ds-btn--primary nav-cta" @click="authStore.openAuthCard('login')">
              登录
            </button>
          </template>

          <template v-else>
            <button class="ds-btn ds-btn--secondary nav-cta" @click="router.push('/membership')">
              开通会员
            </button>
            <button class="ds-btn ds-btn--primary nav-cta" @click="router.push('/tickets/new')">
              发布需求
            </button>

            <el-dropdown trigger="click" @command="handleCommand">
              <span class="user-info">
                <span class="user-avatar">
                  <el-icon size="15"><UserFilled /></el-icon>
                </span>
                <span class="user-name">{{ authStore.userInfo?.nickname || authStore.userInfo?.username }}</span>
                <el-icon size="12"><ArrowDown /></el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="profile">个人主页</el-dropdown-item>
                  <el-dropdown-item command="membership">开通会员</el-dropdown-item>
                  <!-- 分隔线把「去某个页面」和「退出」分开：退出是不可逆的副作用，
                       和上面两项混在一起容易被误点 -->
                  <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>

          <!-- 960px 以下主导航收进汉堡，两颗胶囊保持可见（文档的 Collapsing Strategy） -->
          <button class="ds-icon-btn nav-burger" @click="menuOpen = true" aria-label="打开菜单">
            <el-icon size="18"><Menu /></el-icon>
          </button>
        </div>
      </div>
    </header>

    <!--
      系统亮点带：原本是紧贴导航下方的一条 36px 反相窄带（CSS keyframes 匀速平移），
      现改成 reactbits 的 ScrollVelocity —— 两条**大字号**文本横向贯穿整屏、
      方向相反地滚，并且滚动页面时会被"甩"起来（滚动越快走越快，停手后弹性回落到
      基础速度）。窄带承担的是"报菜名"，大字号才读得出这是这个产品的主张。

      留高：两行 × 64px(display-lg)，加行距和 32px 纵向内边距，整条 200px 上下。
      字号/配色在 main.css 的 .ds-velocity 一节，不是写死的。

      **reduced-motion 时 velocity 传 0**：上游组件没有这个处理（它靠 JS 驱动
      transform，CSS 的 @media 拦不住），而原来那条 CSS 动画是有的 —— 不能因为
      换组件就把这条无障碍行为丢掉。速度 0 时 moveBy 恒等于 0，正好完全静止，
      页面滚动也不会带动它。
    -->
    <div class="ds-velocity" aria-hidden="true">
      <ReactMount
        :component="ScrollVelocity"
        :texts="marqueeRows"
        :velocity="reducedMotion ? 0 : 80"
        parallax-class-name="ds-velocity__row"
        scroller-class-name="ds-velocity__track"
        class-name="ds-velocity__item"
      />
    </div>

    <main class="app-main">
      <router-view :key="route.fullPath" />
    </main>

    <!--
      登录/注册浮层。挂在这里而不是各页面里，是因为「什么时候需要登录」散落在
      大厅、工单详情、顶栏、axios 拦截器 —— 它们只需要调 store 的 openAuthCard()，
      浮层由布局统一渲染，全站只有一份。
    -->
    <AuthCardOverlay />

    <!-- 移动端菜单浮层：整幅画布 -->
    <div v-if="menuOpen" class="mobile-menu" @click.self="menuOpen = false">
      <div class="mobile-menu__head">
        <span class="ds-eyebrow">MENU</span>
        <button class="ds-icon-btn" @click="menuOpen = false" aria-label="关闭菜单">
          <el-icon size="18"><Close /></el-icon>
        </button>
      </div>
      <nav class="mobile-menu__list">
        <button
          v-for="item in navItems"
          :key="item.path"
          class="mobile-menu__item"
          :class="{ 'is-active': isActive(item.path) }"
          @click="go(item.path)"
        >
          {{ item.label }}
        </button>
        <template v-if="authStore.isAdmin">
          <button class="mobile-menu__item" :class="{ 'is-active': isActive('/admin/users') }" @click="go('/admin/users')">
            用户管理
          </button>
          <button class="mobile-menu__item" :class="{ 'is-active': isActive('/admin/stats') }" @click="go('/admin/stats')">
            统计分析
          </button>
        </template>
      </nav>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores'
import AuthCardOverlay from '@/components/auth/AuthCardOverlay.vue'
// 背景是 React 组件（上游源码原样引入），经 ReactMount 挂载。
import MicroSlats from '@/components/background/MicroSlats.jsx'
// 系统亮点带也是 React 组件，同样走 ReactMount。
import ScrollVelocity from '@/components/text/ScrollVelocity.jsx'
import ReactMount from '@/components/react/ReactMount.vue'
import { Monitor, ArrowDown, UserFilled, Menu, Close } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const menuOpen = ref(false)

/*
  导航项。原来是硬编码在侧边栏里的 <el-menu-item>，这里提成数组 —— 顶栏和
  移动端浮层两处渲染同一份数据，改动只需要动一处。

  项目流水（我的需求/待处理/我接的单/已完成）不在这里：它们说的是同一件事，
  入口统一由大厅顶上的身份卡片承载（见 views/ticket/TicketList.vue）。
  URL 在 router 里重定向回 /tickets。
*/
const navItems = [
  { path: '/tickets', label: '需求大厅' },
  { path: '/projects', label: '项目管理' },
  { path: '/knowledge', label: '知识库' },
]

/*
  亮点带的两行内容。ScrollVelocity 的 texts 是「一行一条」，奇数行方向自动取反 ——
  所以拆成两行就得到上下反向滚动的效果，不需要自己算方向。

  行内用 ` · ` 连接、并且**结尾也留一个**：组件在每份拷贝后面补一个 &nbsp;，
  结尾不留分隔符的话，循环接缝处会变成「…沟通留痕 发布需求…」，读起来像断句。
*/
const HIGHLIGHTS = [
  ['发布需求 · 预附 AI 额度', '接单即得 AI 技术分析', '一键生成 PRD 文档'],
  ['任务拆解到小时', 'AI 开发助手全程对话', '交付验收 · 沟通留痕'],
]

const marqueeRows = HIGHLIGHTS.map(items => items.join('   ·   ') + '   ·   ')

// 无障碍：系统开了"减少动态效果"就不要让它自己滚（见模板里的注释）。
const reducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false

const activeMenu = computed(() => route.path)

function isActive(path: string) {
  return activeMenu.value === path || activeMenu.value.startsWith(path + '/')
}

function go(path: string) {
  menuOpen.value = false
  router.push(path)
}

async function handleCommand(command: string) {
  // 两个跳转命令：下拉菜单不依赖导航栏，是没有常驻入口的页面的固定去处
  if (command === 'profile') {
    router.push('/profile')
    return
  }
  if (command === 'membership') {
    router.push('/membership')
    return
  }
  if (command === 'logout') {
    await authStore.logoutRemote()
    // 登出后回大厅并弹登录卡片：/login 整页路由已删，而且用户登出后最可能的
    // 下一步就是「换个账号登录」，直接把卡片摆出来比留一个空页面合理。
    router.push('/tickets')
    authStore.openAuthCard('login')
  }
}
</script>

<style scoped>
.app-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  /* 这层白底现在是**兜底**：WebGL2 不可用时背景组件直接不渲染，页面必须
     仍然是干净的白纸，而不是露出一块没画完的黑。 */
  background: var(--ds-canvas);
}

/* 海：铺满视口、不吃事件。
   fixed 而不是 absolute —— 页面滚动时背景不该跟着走，否则长列表滚到底会
   把海的下边缘整个拉进视野。 */
.app-sea {
  position: fixed;
  inset: 0;
  z-index: 0;
  /* 一定要关掉命中：它盖住的正是页面的留白区，开着的话
     留白处的点击会被这一层吞掉。水面本身不需要收事件 ——
     组件是在 window 上听 pointermove 的。 */
  pointer-events: none;
}

/* 内容层压在海上。
   **这一条不能省**：定位元素（z-index:0 的海）的绘制顺序排在「普通流块级背景」
   之上，而 .ds-card 恰恰就是普通流块。不抬起来的话，整页卡片的白底会被海
   盖住，正文糊成一片淡紫。 */
.app-main {
  position: relative;
  z-index: 1;
  flex: 1;
  min-height: 0;
}

/* 亮点带是普通流里的实心黑条，同样在「普通流块背景」那一档，一并抬上来。
   顶栏不用管：它是 sticky 定位且 z-index:900，本来就在海之上。 */
.ds-velocity {
  position: relative;
  z-index: 1;
}

/* —— 右上角用户区 —— */
.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 12px 5px 6px;
  border-radius: var(--ds-r-pill);
  cursor: pointer;
  outline: none;
  transition: background 0.2s ease;
}

.user-info:hover {
  background: var(--ds-surface-soft);
}

/* 圆形而不是文档里「营销页不出现头像」的那种缺席 —— 这是登录后的应用导航，
   必须有身份指示。用纯黑圆 + 白色人像，与字标里的黑色方块同一套语言。 */
.user-avatar {
  width: 30px;
  height: 30px;
  border-radius: var(--ds-r-full);
  background: var(--ds-primary);
  color: var(--ds-on-primary);
  display: flex;
  align-items: center;
  justify-content: center;
}

.user-name {
  font-size: 15px;
  font-weight: var(--ds-fw-medium);
  color: var(--ds-ink);
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nav-burger {
  display: none;
}

/* —— 移动端菜单 —— */
.mobile-menu {
  position: fixed;
  inset: 0;
  z-index: 2000;
  background: var(--ds-canvas);
  padding: 24px var(--ds-s-lg) 40px;
  display: flex;
  flex-direction: column;
  gap: var(--ds-s-lg);
  overflow-y: auto;
}

.mobile-menu__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.mobile-menu__list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.mobile-menu__item {
  text-align: left;
  font-family: var(--ds-font-sans);
  font-size: var(--ds-fs-card-title);
  font-weight: var(--ds-fw-semi);
  letter-spacing: -0.26px;
  color: var(--ds-ink);
  background: transparent;
  border: none;
  border-bottom: 1px solid var(--ds-hairline-soft);
  padding: 18px 4px;
  cursor: pointer;
}

/* 选中 = 主色面，与顶栏/选项卡同一条规则 */
.mobile-menu__item.is-active {
  background: var(--ds-primary);
  color: var(--ds-on-primary);
  border-radius: var(--ds-r-md);
  border-bottom-color: transparent;
  padding-left: 14px;
  padding-right: 14px;
}

/* 960px 以下主导航收进汉堡（文档的 Collapsing Strategy）；560px 以下连两颗
   胶囊也一起收，否则条上只剩按钮没有字标。 */
@media (max-width: 960px) {
  .ds-topnav__links {
    display: none;
  }
  .nav-burger {
    display: inline-flex;
  }
}

@media (max-width: 560px) {
  .nav-cta {
    display: none;
  }
  .user-name {
    display: none;
  }
}
</style>

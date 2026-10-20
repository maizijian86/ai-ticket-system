<template>
  <!--
    这个 div 是 React 的地盘：模板里**故意不写子节点**，Vue 就永远不会去 patch
    React 插进来的那些 DOM。两边各管各的，互不越界。
  -->
  <div ref="host" class="react-mount"></div>
</template>

<script setup lang="ts">
/**
 * 在 Vue 里挂一个 React 组件。
 *
 * 项目主体是 Vue，但要往 React 生态借组件（背景、以及后续更多数据展示件）。
 * 与其为每个 React 组件手写一层适配，不如保留一个通用的挂载点：传进来的
 * props 原样透传，kebab-case 自动转成 React 的 camelCase，`class` 转 `className`。
 *
 * 用法：
 *
 *   <ReactMount :component="MicroSlats" color="#7d26c0" :slat-width="10" />
 *
 * 三个约定，踩过就知道为什么：
 *
 * 1. **父组件更新时会跟着重渲，但 props 值没变的话会跳过**（见 sameProps 的
 *    注释）。注意这只挡得住"值没变"：运行时真的改了某个 prop，下游组件就会
 *    重渲 —— 而 MicroSlats 的 useEffect 是无依赖数组的，它会重新同步一遍配置
 *    并唤醒渲染循环（这正是原版的设计意图，不是 bug）。
 * 2. 不转发 DOM 事件（@click 之类）。需要回调的 React 组件，在外面包一层 Vue
 *    组件再传函数 props，比在这里造事件系统更省事。
 * 3. `.react-mount` 用 `display: contents` —— 这层壳不产生盒子，React 组件
 *    直接按**外层容器**（如 .app-sea）算尺寸。改成普通块会多出一层需要自己
 *    撑高的中间元素。
 */
import { createElement, type ComponentType } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { onBeforeUnmount, onMounted, onUpdated, ref, useAttrs } from 'vue'

const props = defineProps<{
  /** 要挂载的 React 组件本身（import 进来传进来） */
  component: ComponentType<any>
}>()

const attrs = useAttrs()
const host = ref<HTMLDivElement | null>(null)
let root: Root | null = null

function toReactProps(raw: Record<string, unknown>): Record<string, unknown> {
  const out: Record<string, unknown> = {}
  for (const [key, value] of Object.entries(raw)) {
    if (key === 'class') {
      out.className = value
      continue
    }
    // glint-color → glintColor。Vue 模板里按惯例写 kebab-case，React 只认驼峰。
    out[key.replace(/-([a-z])/g, (_, c: string) => c.toUpperCase())] = value
  }
  return out
}

let lastProps: Record<string, unknown> | null = null

/**
 * 值没变就**复用上一个 props 对象的引用**。
 *
 * 这不是省性能的小聪明，是正确性问题：每次渲染都造一个新对象的话，React 会认为
 * props 变了（`oldProps !== newProps`）而重渲子组件。对一个纯展示的组件无所谓，
 * 但 ScrollVelocity 把内部组件 `VelocityText` 定义在函数体里 —— 重渲 = 新的组件
 * 类型 = React 卸载重挂整棵子树 = useMotionValue 归零 = **滚动位置肉眼可见地跳回
 * 开头**。传同一个引用，React 走 bailout 分支，压根不进渲染。
 */
function sameProps(a: Record<string, unknown> | null, b: Record<string, unknown>): boolean {
  if (!a) return false
  const ka = Object.keys(a)
  const kb = Object.keys(b)
  if (ka.length !== kb.length) return false
  return ka.every(k => a[k] === b[k])
}

function draw() {
  if (!root) return
  const next = toReactProps(attrs)
  const stable = sameProps(lastProps, next) ? lastProps! : next
  lastProps = stable
  root.render(createElement(props.component, stable))
}

onMounted(() => {
  if (!host.value) return
  // 不走 createElement + ReactDOM.render（那是 React 17 的写法，React 19 已删）
  root = createRoot(host.value)
  draw()
})

onUpdated(draw)

onBeforeUnmount(() => {
  // 必须在 Vue 把这个 div 摘走之前卸载，否则 React 会往一个已经脱离文档的
  // 容器里收尾，监听器清不干净（背景的 rAF 循环尤其怕这个）。
  root?.unmount()
  root = null
  lastProps = null
})
</script>

<style scoped>
.react-mount {
  display: contents;
}
</style>

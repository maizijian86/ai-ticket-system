#!/usr/bin/env node
/**
 * 检查有没有 .vue 的 script 块漏写 lang="ts"。
 *
 * 为什么需要这个：vue-tsc 只对带 lang="ts" 的 script 做类型检查，写成 <script setup>
 * （没有 lang）时整个 SFC 会被**整体跳过**，连模板里的属性访问也一起跳过。
 * views/project/ 下 8 个文件曾经全是这个状态，结果是：
 *   - 后端从不产生的状态值（TESTING / COMPLETED）写进类型和 UI，谁都不报错
 *   - Date 直接相减、比较永远为假的表达式，全都安静地通过
 *   - 一共藏着 146 个类型错误，直到加上 lang="ts" 才一次性暴露
 *
 * npm run build 里的 vue-tsc 只在有人主动构建时才跑，拦不住新文件重蹈覆辙，
 * 所以这里单独做一道**廉价**的静态检查，适合放进 pre-commit。
 *
 * 用法：node scripts/check-sfc-lang.mjs
 * 退出码：0 = 全部合规；1 = 有文件漏写
 */
import { readdirSync, readFileSync } from 'node:fs'
import { join, relative } from 'node:path'
import { fileURLToPath } from 'node:url'

const SRC = fileURLToPath(new URL('../src', import.meta.url))
const ROOT = fileURLToPath(new URL('..', import.meta.url))

/** 递归收集所有 .vue 文件；node_modules / dist 不在 src 下，天然排除。 */
function collectVueFiles(dir) {
  const found = []
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name)
    if (entry.isDirectory()) {
      found.push(...collectVueFiles(full))
    } else if (entry.name.endsWith('.vue')) {
      found.push(full)
    }
  }
  return found
}

/** 找出文件里所有 <script ...> 开标签并判断是否声明了 lang="ts"。 */
function findOffenders(file) {
  const lines = readFileSync(file, 'utf8').split(/\r?\n/)
  const bad = []
  lines.forEach((line, index) => {
    // 只认开标签，跳过 </script> 和 </script > 之类的闭合写法
    const match = line.match(/<script\b[^>]*>/i)
    if (!match) return
    if (match[0].includes('</script')) return
    if (!/\blang\s*=\s*["']ts["']/.test(match[0])) {
      bad.push({ line: index + 1, tag: match[0].trim() })
    }
  })
  return bad
}

const offenders = []
for (const file of collectVueFiles(SRC)) {
  const bad = findOffenders(file)
  if (bad.length > 0) {
    offenders.push({ file, bad })
  }
}

if (offenders.length === 0) {
  console.log('✓ 所有 .vue 的 script 块都声明了 lang="ts"')
  process.exit(0)
}

console.error('✗ 以下 .vue 的 script 块没有 lang="ts"，vue-tsc 会整体跳过它们：\n')
for (const { file, bad } of offenders) {
  for (const { line, tag } of bad) {
    console.error(`  ${relative(ROOT, file)}:${line}`)
    console.error(`    ${tag}`)
  }
}
console.error(`\n共 ${offenders.length} 个文件。改成 <script setup lang="ts"> 即可纳入类型检查。`)
process.exit(1)

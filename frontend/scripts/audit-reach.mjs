// 一次性审计脚本：从 main.tsx 出发做 import 可达性分析，列出没人引用的文件。
// 用完可删（它不属于产品代码）。
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { dirname, join, relative, resolve, sep } from 'node:path'

const SRC = resolve(process.cwd(), 'src')
const EXTS = ['.tsx', '.ts', '.jsx', '.js', '.vue', '.css']

function walk(dir, out = []) {
  for (const e of readdirSync(dir, { withFileTypes: true })) {
    const p = join(dir, e.name)
    if (e.isDirectory()) walk(p, out)
    else out.push(p)
  }
  return out
}

const files = walk(SRC)
const fileSet = new Set(files)

function resolveSpec(spec, fromFile) {
  let base
  if (spec.startsWith('@/')) base = join(SRC, spec.slice(2))
  else if (spec.startsWith('.')) base = resolve(dirname(fromFile), spec)
  else return null // 第三方包
  const cands = []
  for (const ext of EXTS) cands.push(base + ext)
  for (const ext of EXTS) cands.push(join(base, 'index' + ext))
  cands.push(base)
  for (const c of cands) if (fileSet.has(c) && statSync(c).isFile()) return c
  return null
}

const IMPORT_RE = /(?:import\s+(?:[\s\S]*?)\s+from\s*|import\s*|export\s+(?:[\s\S]*?)\s+from\s*|import\s*\(\s*|require\s*\(\s*)['"]([^'"]+)['"]/g
// CSS 里的 @import
const CSS_IMPORT_RE = /@import\s+['"]([^'"]+)['"]/g

const graph = new Map() // file -> Set(dep)
const external = new Set()

for (const f of files) {
  const src = readFileSync(f, 'utf8')
  const deps = new Set()
  const re = f.endsWith('.css') ? CSS_IMPORT_RE : IMPORT_RE
  re.lastIndex = 0
  let m
  while ((m = re.exec(src))) {
    const spec = m[1]
    const r = resolveSpec(spec, f)
    if (r) deps.add(r)
    else if (!spec.startsWith('.') && !spec.startsWith('@/')) {
      const pkg = spec.startsWith('@')
        ? spec.split('/').slice(0, 2).join('/')
        : spec.split('/')[0]
      external.add(pkg)
    }
  }
  graph.set(f, deps)
}

const ENTRY = join(SRC, 'main.tsx')
const seen = new Set()
const stack = [ENTRY]
while (stack.length) {
  const cur = stack.pop()
  if (seen.has(cur)) continue
  seen.add(cur)
  for (const d of graph.get(cur) || []) if (!seen.has(d)) stack.push(d)
}

const orphan = files.filter((f) => !seen.has(f) && f !== ENTRY)

const rel = (p) => relative(SRC, p).split(sep).join('/')
console.log(`总文件 ${files.length}，从 main.tsx 可达 ${seen.size}，不可达 ${orphan.length}\n`)
console.log('=== 不可达文件（没有任何链从入口引到它）===')
for (const o of orphan.sort()) console.log('  ' + rel(o))

// 反向：谁引用了这个不可达文件？帮判断"孤岛"还是"仅被孤岛引用"
const rev = new Map()
for (const [f, deps] of graph) for (const d of deps) {
  if (!rev.has(d)) rev.set(d, new Set())
  rev.get(d).add(f)
}
console.log('\n=== 不可达文件被谁引用（若引用者也不可达 = 死岛）===')
for (const o of orphan.sort()) {
  const rs = [...(rev.get(o) || [])].filter((x) => x !== o)
  const tag = rs.length === 0 ? '【完全无人引用】' : rs.map(rel).join(', ')
  console.log(`  ${rel(o)}  <-  ${tag}`)
}

// 第三方包使用情况
const declared = JSON.parse(readFileSync(resolve(process.cwd(), 'package.json'), 'utf8'))
const allDeps = { ...declared.dependencies, ...declared.devDependencies }
const unusedPkgs = Object.keys(allDeps).filter((p) => !external.has(p))
console.log('\n=== package.json 里但 src 中从未 import 的包 ===')
for (const p of unusedPkgs) console.log('  ' + p)

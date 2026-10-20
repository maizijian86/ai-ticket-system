/**
 * LatticeLoader —— reactbits.dev 上游源码，**原样引入**。
 *
 * 请不要在这个文件里改代码。它唯一的价值就是与上游保持一致：reactbits 更新时
 * 整份覆盖即可。要调观感就在使用处传 props，要改行为就另外包一层。
 *
 * 相对上游只删了一行：首行的 'use client'。那是 Next.js / React Server
 * Components 的指令，在本项目的 Vite SPA 里不起任何作用（Rollup 反而会为它
 * 报一条 "Module level directives cause errors when bundled" 的警告）。
 * 类型声明见同名 .d.ts，所以 **import 时不要带 `.jsx` 后缀**：
 *
 *     import LatticeLoader from '@/components/loaders/LatticeLoader'
 *
 * 三条使用时要留意的：
 *
 * 1. **它是个行内元素**（`inline-flex`，根节点是 <span>）。放进按钮、放进一行
 *    文字里都行，但它的宽度是内容定的，别指望它自己占满容器。
 * 2. **颜色默认吃 `currentColor`**，所以在白底黑字的全站里默认就是墨色。要它变
 *    别的颜色，改**外层文本颜色**比传 `color` 更省事（`label` 和格子会一起变）。
 * 3. **`status` 是唯一的开关**：`working` 跑波动画 + 走秒表，切到 `done` / `error`
 *    时动画冻住、溶解成一个对勾或叉，秒表停在那一刻。所以不要用 `v-if` 式的
 *    "换一个组件"来表达完成 —— 让它自己从 working 变成 done，才有溶解动画。
 *    组件内部靠 `data-status` 属性驱动，卸载重挂就看不到那一下了。
 */

import { useEffect, useLayoutEffect, useRef, useState } from 'react';

import './LatticeLoader.css';

const PATTERNS = {
  arrow: { 3: { cells: [1, 2, 3, 0, 1, 2, 1, 2, 3], loop: 7.2, scale: 1 } },
  dots: { 3: { cells: [0, 1, 2, 0, 1, 2, 0, 1, 2], loop: 3, scale: 2.4 } },
  ripple: { 3: { cells: [2, 1, 2, 1, 0, 1, 2, 1, 2], loop: 4.8, scale: 1.5 } },
  spiral: { 3: { cells: [0, 1, 2, 7, 8, 3, 6, 5, 4], loop: 9, scale: 1.2, lit: 0.35 } },
  orbit: {
    3: { cells: [0, 1, 2, 7, null, 3, 6, 5, 4], loop: 8, scale: 1.2 },
    4: { cells: [0, 1, 2, 3, 11, null, null, 4, 10, null, null, 5, 9, 8, 7, 6], loop: 6, scale: 1.2, lit: 0.45 }
  },
  snake: {
    3: { cells: [0, 1, 2, 5, 4, 3, 6, 7, 8], loop: 9, scale: 1, lit: 0.35 },
    4: { cells: [0, 1, 2, 3, 7, 6, 5, 4, 8, 9, 10, 11, 15, 14, 13, 12], loop: 16, scale: 1, lit: 0.25 }
  },
  sweep: { 4: { cells: [0, 1, 2, 3, 1, 2, 3, 4, 2, 3, 4, 5, 3, 4, 5, 6], loop: 5, scale: 1, lit: 0.45 } },
  spin: { 4: { cells: [0, 0, 1, 1, 0, 0, 1, 1, 3, 3, 2, 2, 3, 3, 2, 2], loop: 4, scale: 1.6, lit: 0.35 } },
  rain: { 4: { cells: [0, 2, 1, 3, 1, 3, 2, 4, 2, 4, 3, 5, 3, 5, 4, 6], loop: 4, scale: 1.2, lit: 0.35 } },
  pulse: { 4: { cells: [2, 1, 1, 2, 1, 0, 0, 1, 1, 0, 0, 1, 2, 1, 1, 2], loop: 2.4, scale: 2.5, lit: 0.45 } }
};
const DEFAULT_PATTERN = { 3: 'orbit', 4: 'sweep' };
const MARKS = {
  3: { done: [2, 3, 5, 7], error: [0, 2, 4, 6, 8] },
  4: { done: [7, 8, 10, 13], error: [0, 3, 5, 6, 9, 10, 12, 15] }
};

const resolvePattern = (pattern, grid) => {
  if (typeof pattern === 'string') {
    const named = PATTERNS[pattern];
    return (named && named[grid]) || PATTERNS[DEFAULT_PATTERN[grid]][grid];
  }
  const cells = Array.from({ length: grid * grid }, (_, i) => pattern.cells[i] ?? null);
  const max = Math.max(0, ...cells.filter(v => v != null));
  return { cells, loop: pattern.loop ?? max + 4.2, scale: pattern.scale ?? 1, lit: pattern.lit ?? 0.62 };
};
const fmt = ds => (ds < 600 ? `${(ds / 10).toFixed(1)}s` : `${Math.floor(ds / 600)}m ${((ds % 600) / 10).toFixed(1)}s`);
const spoken = ds =>
  ds < 600
    ? `${(ds / 10).toFixed(1)} seconds`
    : `${Math.floor(ds / 600)} minutes ${((ds % 600) / 10).toFixed(1)} seconds`;

export default function LatticeLoader({
  label = 'Thinking',
  doneLabel = 'Done in',
  errorLabel = 'Failed after',
  status = 'working',
  pattern = 'orbit',
  grid = 3,
  shape = 'round',
  color = 'currentColor',
  doneColor = '#22c55e',
  errorColor = '#ef4444',
  cellSize = 6,
  gap = 2,
  fontSize = 14,
  step = 90,
  idleOpacity = 0.15,
  glow = false,
  glowColor = '',
  showTimer = true,
  elapsed,
  className = '',
  style
}) {
  const n = grid === 4 ? 4 : 3;
  const pat = resolvePattern(pattern, n);
  const marks = MARKS[n];
  const d = step * pat.scale;
  const cycle = Math.round(pat.loop * d);

  const timerRef = useRef(null);
  const dsRef = useRef(0);
  const markRef = useRef('done');
  const mark = status === 'working' ? markRef.current : status;
  markRef.current = mark;
  const [announce, setAnnounce] = useState(`${label}, in progress`);

  const paint = ds => {
    dsRef.current = ds;
    if (timerRef.current) timerRef.current.textContent = fmt(ds);
  };

  useLayoutEffect(() => {
    if (elapsed != null) {
      paint(Math.round(elapsed * 10));
      return undefined;
    }
    if (status !== 'working') return undefined;
    const startedAt = performance.now();
    paint(0);
    const id = setInterval(() => paint(Math.floor((performance.now() - startedAt) / 100)), 100);
    return () => clearInterval(id);
  }, [status, elapsed]);

  useEffect(() => {
    if (status === 'working') setAnnounce(`${label}, in progress`);
    else setAnnounce(`${status === 'done' ? doneLabel : errorLabel}${showTimer ? ` ${spoken(dsRef.current)}` : ''}`);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status]);

  return (
    <span
      role="status"
      className={`lattice-loader${className ? ` ${className}` : ''}`}
      data-status={status}
      data-shape={shape}
      data-glow={glow ? '' : undefined}
      style={{
        '--ll-n': n,
        '--ll-cell': `${cellSize}px`,
        '--ll-gap': `${gap}px`,
        '--ll-font': `${fontSize}px`,
        '--ll-color': color,
        '--ll-mark': status === 'error' ? errorColor : doneColor,
        '--ll-idle': idleOpacity,
        '--ll-glow': glowColor || color,
        '--ll-mark-glow': glowColor || (status === 'error' ? errorColor : doneColor),
        '--ll-cycle': `${cycle}ms`,
        ...style
      }}
    >
      <span className="lattice-loader__grid" aria-hidden="true">
        <span className="lattice-loader__layer lattice-loader__run">
          {pat.cells.map((unit, i) => (
            <span
              key={i}
              className="lattice-loader__cell"
              data-hole={unit == null ? '' : undefined}
              data-lit={pat.lit && pat.lit !== 0.62 ? Math.round(pat.lit * 100) : undefined}
              style={unit == null ? undefined : { animationDelay: `${Math.round(unit * d)}ms` }}
            />
          ))}
        </span>
        <span className="lattice-loader__layer lattice-loader__mark">
          {pat.cells.map((_, i) => (
            <span key={i} className="lattice-loader__cell" data-on={marks[mark].includes(i) ? '' : undefined} />
          ))}
        </span>
      </span>
      <span className="lattice-loader__label" aria-hidden="true">
        <span className="lattice-loader__text" data-active={status === 'working' ? '' : undefined}>
          {label}
        </span>
        <span className="lattice-loader__text" data-active={status === 'done' ? '' : undefined}>
          {doneLabel}
        </span>
        <span className="lattice-loader__text" data-active={status === 'error' ? '' : undefined}>
          {errorLabel}
        </span>
      </span>
      {showTimer ? (
        <span ref={timerRef} className="lattice-loader__timer" aria-hidden="true">
          0.0s
        </span>
      ) : null}
      <span className="lattice-loader__sr">{announce}</span>
    </span>
  );
}

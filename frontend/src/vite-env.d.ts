/// <reference types="vite/client" />

// 这里原本还有一个 `declare module '*.vue'` 块，给 vue-tsc 认 SFC 用。
// .vue 全部删除后它没有意义，而且块里 `import type { DefineComponent } from 'vue'`
// 会在 vue 依赖被移除后直接变成编译错误（TS2307），所以整块删掉了。

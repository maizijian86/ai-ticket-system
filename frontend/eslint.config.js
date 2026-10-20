import js from '@eslint/js'
import tseslint from 'typescript-eslint'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import unusedImports from 'eslint-plugin-unused-imports'
import globals from 'globals'

/**
 * ESLint 扁平配置。
 *
 * 这个项目此前**一个 lint 配置都没有**：只有 `tsc` 兜类型，而 `noUnusedLocals`
 * 只能管"变量声明了没用"，管不了"整个文件没人 import" —— 于是 30 个 .vue 在
 * 迁移半年后仍然躺在 src 里（见 Phase 2 删掉的 59 个不可达文件）。
 * `unused-imports/no-unused-imports` 是这里最关键的一条：它挡住的是
 * "新代码往下叠、旧代码永远不删"这个累积过程。
 *
 * 规则取向：宁少勿多。只开**能自动修、且不会与现有代码打架**的规则；
 * 风格交给 Prettier，不在这里争。
 */
export default tseslint.config(
  {
    // 不 lint 的目录：构建产物、依赖、一次性审计脚本
    ignores: ['dist/**', 'node_modules/**', '.shots/**']
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ['**/*.{ts,tsx,js,jsx}'],
    languageOptions: {
      ecmaVersion: 2022,
      globals: { ...globals.browser, ...globals.es2021 }
    },
    plugins: {
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
      'unused-imports': unusedImports
    },
    rules: {
      ...reactHooks.configs.recommended.rules,

      // 挡住"删了逻辑、忘删 import"和"整份文件没人用"的累积
      'unused-imports/no-unused-imports': 'error',
      'unused-imports/no-unused-vars': [
        'warn',
        { args: 'after-used', argsIgnorePattern: '^_', varsIgnorePattern: '^_' }
      ],

      // 组件文件只导出组件（HMR 友好）。
      // 这个项目有几个**刻意**把"组件 + 命令式函数"放一起的文件（ConfirmHost 的
      // confirmDialog、TicketForm 的校验函数、button 的 variants）—— 拆成两个文件
      // 会让"对话框永远只有一份"那类约束更难维持。代价是这些模块的 fast-refresh
      // 会退化成整页刷新，接受。所以这条降为 off，不当作问题报。
      'react-refresh/only-export-components': 'off',

      // 上游 vendored 的 .jsx（reactbits）不该被本项目的规则挑刺
      '@typescript-eslint/no-explicit-any': 'off',
      '@typescript-eslint/no-unused-vars': 'off' // 交给 unused-imports，避免同一件事报两遍
    }
  },
  {
    files: ['src/components/**/*.jsx'],
    rules: {
      'react-hooks/rules-of-hooks': 'off'
    }
  },
  {
    files: ['scripts/**/*.mjs'],
    languageOptions: { globals: { ...globals.node } }
  }
)

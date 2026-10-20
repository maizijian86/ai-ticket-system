#!/bin/sh
# 前端类型闸。这是**源文件**，实际生效的钩子在 .git/hooks/pre-commit。
# 安装 / 更新：sh frontend/scripts/install-hooks.sh
#
# 为什么需要它：仓库没有 CI，唯一的类型检查挂在 npm run build 里，
# 而没人会在提交前主动构建。结果是类型错误可以长期存在而无人拦。
#
# 只在前端文件进入暂存区时才跑，改后端不会因此变慢。
# 紧急情况可以 git commit --no-verify 绕过（绕过前请想清楚为什么要绕）。
#
# 2026-10 变更：Vue 迁移收尾，.vue 已全部删除，因此
#   - 去掉了原来的 check-sfc-lang.mjs 一步（它只检查 .vue 的 script 有没有 lang="ts"）
#   - npm run type-check 现在跑的是 tsc（不再是 vue-tsc）

# 暂存区里属于 frontend/ 的新增或修改文件
staged=$(git diff --cached --name-only --diff-filter=ACM -- frontend/ || true)

if [ -z "$staged" ]; then
  exit 0
fi

cd frontend || exit 1

echo "[pre-commit] 运行 tsc 类型检查 ..."
npm run --silent type-check || {
  echo ""
  echo "[pre-commit] 类型检查未通过，提交已阻止。"
  echo "             修复后重试；确需绕过用 git commit --no-verify（请先说明理由）。"
  exit 1
}

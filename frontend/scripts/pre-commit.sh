#!/bin/sh
# 前端类型闸。这是**源文件**，实际生效的钩子在 .git/hooks/pre-commit。
# 安装 / 更新：sh frontend/scripts/install-hooks.sh
#
# 为什么需要它：仓库既没有 CI 也没有任何钩子，唯一的类型检查挂在 npm run build 里，
# 而没人会在提交前主动构建。结果是 55 个类型错误长期存在、views/project/ 下 8 个文件
# 长期不被检查 —— 不是谁疏忽，而是没有任何环节会拦住它们。
#
# 只在前端文件进入暂存区时才跑，改后端不会因此变慢。
# 紧急情况可以 git commit --no-verify 绕过（绕过前请想清楚为什么要绕）。

# 暂存区里属于 frontend/ 的新增或修改文件
staged=$(git diff --cached --name-only --diff-filter=ACM -- frontend/ || true)

if [ -z "$staged" ]; then
  exit 0
fi

cd frontend || exit 1

# 1) 廉价检查先跑：漏写 lang="ts" 的 .vue 会被 vue-tsc 整体跳过
if echo "$staged" | grep -qE '\.vue$'; then
  echo "[pre-commit] 检查 .vue 的 script 是否都声明了 lang=\"ts\" ..."
  node scripts/check-sfc-lang.mjs || exit 1
fi

# 2) 真正的类型检查
echo "[pre-commit] 运行 vue-tsc ..."
npm run --silent type-check || {
  echo ""
  echo "[pre-commit] 类型检查未通过，提交已阻止。"
  echo "             修复后重试；确需绕过用 git commit --no-verify（请先说明理由）。"
  exit 1
}

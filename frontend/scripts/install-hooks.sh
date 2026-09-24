#!/bin/sh
# 把仓库里的钩子源文件安装到 .git/hooks/。
#
# .git/hooks/ 不受版本控制，所以钩子必须有个可复制、可 review 的来源 —— 这个脚本
# 负责把 frontend/scripts/pre-commit.sh 装过去。新克隆仓库后跑一次即可：
#   sh frontend/scripts/install-hooks.sh
set -e

root=$(git rev-parse --show-toplevel)
src="$root/frontend/scripts/pre-commit.sh"
dst="$root/.git/hooks/pre-commit"

if [ ! -f "$src" ]; then
  echo "找不到钩子源文件：$src" >&2
  exit 1
fi

# 删掉 CR：Windows 上文件可能是 CRLF 行尾，而带 \r 的 shebang 会让钩子直接失败，
# 报错信息还相当难懂（"cannot execute: required file not found"）。
tr -d '\r' < "$src" > "$dst"
chmod +x "$dst"

echo "已安装 pre-commit 钩子 -> $dst"
echo "钩子会在有 frontend/ 改动时跑 vue-tsc；改后端不受影响。"

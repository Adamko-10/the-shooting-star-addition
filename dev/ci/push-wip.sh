#!/usr/bin/env bash
# push-wip.sh <branch>: pushes the local branch <branch> plus one commit with the CI files on top to wip/<branch>.
# The local branch itself is left untouched.
set -euo pipefail
b="$1"
repo=/home/claude/the-shooting-star-addition
cd "$repo"
base=$(git rev-parse "refs/heads/$b")
idx=$(mktemp)
export GIT_INDEX_FILE="$idx"
git read-tree "$base"
add() { # add <source file> <path in repo>
  local blob; blob=$(git hash-object -w "$1")
  local mode=100644; [ -x "$1" ] && mode=100755
  git update-index --add --cacheinfo "$mode,$blob,$2"
}
(cd /home/claude/ci-files/common && find . -type f | sed 's#^\./##') | while read -r f; do add "/home/claude/ci-files/common/$f" "$f"; done
add "/home/claude/ci-files/branches/$b.sh" ".github/ci/branch.sh"
tree=$(git write-tree)
unset GIT_INDEX_FILE; rm -f "$idx"
commit=$(git -c user.name="Adamko-10" -c user.email="Adamko-10@users.noreply.github.com" commit-tree "$tree" -p "$base" -m "CI (work in progress builds only)")
git push -q --force origin "$commit:refs/heads/wip/$b"
echo "$b -> wip/$b $commit (base $base)"

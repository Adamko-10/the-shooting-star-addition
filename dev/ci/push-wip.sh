#!/usr/bin/env bash
# push-wip.sh <branch> [local ref]: pushes the local branch <branch> (or [local ref]) plus one commit with the CI files on top to wip/<branch>.
# The local branch itself is left untouched.
set -euo pipefail
b="$1"
# the CI files are this script's own dev/ci/ (of whichever checkout it is run from); the branch is the repository's
ci="$(cd "$(dirname "$0")" && pwd)"
cd "$ci"
cd "$(git rev-parse --show-toplevel)"
base=$(git rev-parse "refs/heads/${2:-$b}")
idx=$(mktemp)
export GIT_INDEX_FILE="$idx"
git read-tree "$base"
add() { # add <source file> <path in repo>
  # (--path: stored with the repository's line endings, whatever the checkout has; scripts are executable)
  local blob; blob=$(git hash-object -w --path "$2" "$1")
  local mode=100644; case "$2" in *.sh) mode=100755 ;; esac
  git update-index --add --cacheinfo "$mode,$blob,$2"
}
(cd "$ci/common" && find . -type f | sed 's#^\./##') | while read -r f; do add "$ci/common/$f" "$f"; done
add "$ci/branches/$b.sh" ".github/ci/branch.sh"
tree=$(git write-tree)
unset GIT_INDEX_FILE; rm -f "$idx"
commit=$(git -c user.name="Adamko-10" -c user.email="Adamko-10@users.noreply.github.com" commit-tree "$tree" -p "$base" -m "CI (work in progress builds only)")
git push -q --force origin "$commit:refs/heads/wip/$b"
echo "$b -> wip/$b $commit (base $base)"

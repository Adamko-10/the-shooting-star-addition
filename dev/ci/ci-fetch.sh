#!/usr/bin/env bash
# ci-fetch.sh <branch> [run-number]: downloads the newest (or given) CI draft for <branch> into $CI_RESULTS/<branch>-<n>/
# (default ~/.tssa-ci/results) and decrypts its secret part with $CI_KEY (default ~/.tssa-ci/ci.key, never in git).
set -euo pipefail
b="$1"; want="${2:-}"
repo=Adamko-10/the-shooting-star-addition
prefix="ci-${b//[^A-Za-z0-9.]/-}-"
rel=$(gh api "repos/$repo/releases?per_page=100" --jq "[.[] | select(.draft and (.tag_name | startswith(\"$prefix\")))] | sort_by(.created_at) | reverse | .[0]$( [ -n "$want" ] && echo '' )")
if [ -n "$want" ]; then
  rel=$(gh api "repos/$repo/releases?per_page=100" --jq "[.[] | select(.draft and .tag_name == \"$prefix$want\")] | .[0]")
fi
tag=$(echo "$rel" | python -c 'import json,sys; print(json.load(sys.stdin)["tag_name"])')
dir=${CI_RESULTS:-$HOME/.tssa-ci/results}/${tag#ci-}
mkdir -p "$dir"
echo "$rel" | python -c 'import json,sys; [print(a["id"], a["name"]) for a in json.load(sys.stdin)["assets"]]' | while read -r id name; do
  curl -sSL -H "Authorization: Bearer $(gh auth token 2>/dev/null || true)" -H 'Accept: application/octet-stream' \
    -o "$dir/$name" "https://api.github.com/repos/$repo/releases/assets/$id" || gh api -H 'Accept: application/octet-stream' "repos/$repo/releases/assets/$id" > "$dir/$name"
done
if [ -f "$dir/secret.key.enc" ]; then
  openssl pkeyutl -decrypt -inkey "${CI_KEY:-$HOME/.tssa-ci/ci.key}" -in "$dir/secret.key.enc" -out "$dir/k"
  openssl enc -d -aes-256-cbc -pbkdf2 -in "$dir/secret.tgz.enc" -out "$dir/secret.tgz" -pass file:"$dir/k"
  tar xzf "$dir/secret.tgz" -C "$dir" && rm -f "$dir/k" "$dir/secret.tgz"
fi
for z in "$dir"/shots-*.zip; do [ -f "$z" ] && mkdir -p "${z%.zip}" && unzip -q -o "$z" -d "${z%.zip}"; done
echo "$dir"; ls "$dir"; cat "$dir/summary.txt" 2>/dev/null

#!/usr/bin/env bash
# github_140.sh <branch> <jar>: pushes the branch, tags it, makes its GitHub release with the changelog and the jar
# (named "The Shooting Star Addition 1.4.0 [<Loader> <MC>].jar").
set -euo pipefail
b="$1"; jar="$2"
repo=Adamko-10/the-shooting-star-addition
ver=1.4.0
case "$b" in
  main) tag="v$ver"; label="NeoForge 1.21.1";;
  forge-1.21.1) tag="v$ver+forge-1.21.1"; label="Forge 1.21.1";;
  forge-1.20.1) tag="v$ver+1.20.1"; label="Forge 1.20.1";;
  fabric-26.3) tag="v$ver+fabric-26.3"; label="Fabric 26.3";;
  fabric-1.21.11) tag="v$ver+fabric-1.21.11"; label="Fabric 1.21.11";;
  fabric-1.21.1) tag="v$ver+fabric-1.21.1"; label="Fabric 1.21.1";;
  fabric-1.20.1) tag="v$ver+fabric-1.20.1"; label="Fabric 1.20.1";;
  *) echo "unknown branch $b"; exit 1;;
esac
name="The Shooting Star Addition $ver [$label]"
file="$name.jar"
notes="/home/claude/work/publish/1.4.0/$b.md"
[ -f "$notes" ] || { echo "no changelog for $b"; exit 1; }
cd /home/claude/the-shooting-star-addition
sha=$(git rev-parse "refs/heads/$b")
# the branch itself (a fast-forward of what's on GitHub, or new)
git push -q origin "refs/heads/$b:refs/heads/$b"
# the release (it makes the tag on that commit)
latest=false; [ "$b" = main ] && latest=true
id=$(python3 - "$tag" "$sha" "$name" "$notes" "$latest" <<'PY' | gh api -X POST "repos/Adamko-10/the-shooting-star-addition/releases" --input - --jq .id
import json, sys
tag, sha, name, notes, latest = sys.argv[1:]
print(json.dumps({"tag_name": tag, "target_commitish": sha, "name": name, "body": open(notes, encoding="utf-8").read(),
                  "draft": False, "prerelease": False, "make_latest": latest}))
PY
)
echo "$b: release $id ($tag at ${sha:0:7})"
# the jar, under its own name
enc=$(python3 -c 'import sys, urllib.parse; print(urllib.parse.quote(sys.argv[1]))' "$file")
curl -sS -f -X POST -H "Authorization: Bearer $(gh auth token)" -H "Content-Type: application/java-archive" \
  --data-binary @"$jar" "https://uploads.github.com/repos/$repo/releases/$id/assets?name=$enc&label=$enc" \
  | python3 -c 'import json, sys; a = json.load(sys.stdin); print("  asset:", a["name"], "|", a["label"], "|", a["size"], "bytes")'

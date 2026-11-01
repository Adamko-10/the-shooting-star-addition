#!/usr/bin/env bash
# github_160.sh <branch> <jar> <changelog.md>: tags the branch as it is on GitHub and makes its release with the
# changelog and the jar (named "The Shooting Star Addition 1.6.0 [<Loader> <MC>].jar"). Push the branch first.
set -euo pipefail
b="$1"; jar="$2"; notes="$3"
repo=Adamko-10/the-shooting-star-addition
ver=1.6.0
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
[ -f "$notes" ] || { echo "no changelog for $b"; exit 1; }
sha=$(gh api "repos/$repo/branches/$b" --jq .commit.sha)
latest=false; [ "$b" = main ] && latest=true
id=$(python - "$tag" "$sha" "$name" "$notes" "$latest" <<'PY' | gh api -X POST "repos/Adamko-10/the-shooting-star-addition/releases" --input - --jq .id
import json, sys
tag, sha, name, notes, latest = sys.argv[1:]
print(json.dumps({"tag_name": tag, "target_commitish": sha, "name": name, "body": open(notes, encoding="utf-8").read(),
                  "draft": False, "prerelease": False, "make_latest": latest}))
PY
)
id=${id//$'\r'/}
echo "$b: release $id ($tag at ${sha:0:7})"
enc=$(python -c 'import sys, urllib.parse; print(urllib.parse.quote(sys.argv[1]))' "$file" | tr -d '\r')
curl -sS -f -X POST -H "Authorization: Bearer $(gh auth token)" -H "Content-Type: application/java-archive" \
  --data-binary @"$jar" "https://uploads.github.com/repos/$repo/releases/$id/assets?name=$enc&label=$enc" \
  | python -c 'import json, sys; a = json.load(sys.stdin); print("  asset:", a["name"], "|", a["size"], "bytes")'

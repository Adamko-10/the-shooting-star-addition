#!/usr/bin/env bash
# github_180.sh <branch> <jar> <changelog.md>: tags the branch as it is on GitHub and makes its 1.8.0 release with the
# changelog and the jar (named "The Shooting Star Addition 1.8.0 [<Loader> <MC>].jar"). Push the branch first.
# (github_160.sh did the same through Python, which Adam's PC doesn't have; gh release create needs none.)
set -euo pipefail
b="$1"; jar="$2"; notes="$3"
repo=Adamko-10/the-shooting-star-addition
ver=1.8.0
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
[ -f "$notes" ] || { echo "no changelog for $b"; exit 1; }
[ -f "$jar" ] || { echo "no jar $jar"; exit 1; }
sha=$(gh api "repos/$repo/branches/$b" --jq .commit.sha | tr -d '\r')
latest=false; [ "$b" = main ] && latest=true
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
cp "$jar" "$tmp/$name.jar"
# GitHub turns spaces and brackets in asset names into dots; the label keeps the proper name
asset="$tmp/$name.jar"; command -v cygpath > /dev/null && asset=$(cygpath -w "$asset")  # gh on Windows cannot read Git Bash paths
gh release create "$tag" "$asset#$name.jar" --repo "$repo" --target "$sha" --title "$name" \
  --notes-file "$notes" --latest="$latest"
echo "$b: released $tag at ${sha:0:7}"

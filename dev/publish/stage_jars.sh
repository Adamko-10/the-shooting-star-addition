#!/usr/bin/env bash
# stage_jars.sh <branch>...: takes each branch's newest CI jar (which must be built from the branch's head), checks it,
# and copies it under its release name to publish/1.4.0/jars/ and to the shared outputs folder (for the browser upload).
set -uo pipefail
out=/home/claude/work/publish/1.4.0/jars
share="/mnt/user-data/outputs/The Shooting Star Addition 1.4.0"
mkdir -p "$out" "$share"
for b in "$@"; do
  case "$b" in
    main) label="NeoForge 1.21.1";; forge-1.21.1) label="Forge 1.21.1";; forge-1.20.1) label="Forge 1.20.1";;
    fabric-26.3) label="Fabric 26.3";; fabric-1.21.11) label="Fabric 1.21.11";; fabric-1.21.1) label="Fabric 1.21.1";;
    fabric-1.20.1) label="Fabric 1.20.1";; *) echo "unknown $b"; continue;;
  esac
  d=$(ls -dt /home/claude/ci-results/$b-* | head -1)
  want=$(git -C /home/claude/the-shooting-star-addition rev-parse "refs/heads/$b")
  got=$(git -C /home/claude/the-shooting-star-addition rev-parse "$(cat $d/commit.txt)^" 2>/dev/null)
  if [ "$want" != "$got" ]; then echo "$b: CI result $d is not from the branch head ($got vs $want)"; continue; fi
  if ! grep -q "^ok: build" "$d/summary.txt"; then echo "$b: no successful build in $d"; continue; fi
  jar=$(ls "$d"/shooting_star_addition-1.4.0*.jar 2>/dev/null | head -1)
  [ -n "$jar" ] || { echo "$b: no 1.4.0 jar in $d"; continue; }
  name="The Shooting Star Addition 1.4.0 [$label].jar"
  echo "== $b <- $(basename "$d")"
  bash /home/claude/work/publish/verify_jar.sh "$jar" 1.4.0 || { echo "$b: jar check FAILED"; continue; }
  cp "$jar" "$out/$name"
  cp "$jar" "$share/$name"
  echo "  staged: $name"
done
ls -la "$share"

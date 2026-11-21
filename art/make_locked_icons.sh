#!/usr/bin/env bash
# The remote's cards for TON 618 Addition's skills while they are locked (1.9.0, LockedSkill): its own icons (Adam's
# own art, made by TON 618 Addition's tools/icons), brought down to our 64x32 grid, greyed and dimmed, with a pixel
# padlock in the middle. Needs ffmpeg and a checkout of TON 618 Addition.
# Run: bash art/make_locked_icons.sh "<TON 618 Addition repo>"
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
TON="${1:?the TON 618 Addition repo folder}"
FROM="$TON/src/main/resources/assets/shooting_star_demo/textures/gui/sprites/skill/stellar_remote"
OUT="$HERE/../src/main/resources/assets/shooting_star_demo/textures/gui/sprites/skill/stellar_remote"
DARK=0x1E222B; LIGHT=0xD9DDE6; HI=0xFFFFFF
box() { echo "drawbox=x=$1:y=$2:w=$3:h=$4:color=$5:t=fill"; }
LOCK="$(box 28 8 8 3 $DARK),$(box 28 11 3 4 $DARK),$(box 33 11 3 4 $DARK),$(box 29 9 6 1 $LIGHT),$(box 29 10 1 5 $LIGHT),$(box 34 10 1 5 $LIGHT)"
LOCK="$LOCK,$(box 26 15 12 10 $DARK),$(box 27 16 10 8 $LIGHT),$(box 27 16 10 1 $HI),$(box 31 18 2 4 $DARK)"
for name in last_light quasar_jets; do
  ffmpeg -hide_banner -loglevel error -y -i "$FROM/$name.png" -frames:v 1 \
    -vf "scale=64:32:flags=neighbor,format=rgb24,hue=s=0.3,eq=brightness=-0.14:contrast=0.8,$LOCK" "$OUT/ton618_$name.png"
  echo "$OUT/ton618_$name.png"
done

#!/usr/bin/env bash
# Luna's music for Epic mode (MoonPlan.EPIC_FALL): the whole track, its drop 24 s after the alarm instead of 16.
#   bash art/make_luna_epic_music.sh [source]   (default: the project folder's music/Luna full (rebuilt).flac)
# The source is the full track rebuilt 2026-11-05 (CLAUDE.md): Video Project 48 up to 10.03 s (+13.03 dB) joined onto
# luna_music.ogg, so it is already at luna_music.ogg's level; its drop is at 24.445 s. The game starts it at ALARM, so
# it starts 24.445 - EPIC_FALL / 20 = 0.445 s in. Faded in like luna_music.ogg (docs/SS-06-LUNA.md).
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
SRC=${1:-"$HERE/../../music/Luna full (rebuilt).flac"}
OUT="$HERE/../src/main/resources/assets/shooting_star_addition/sounds/luna_music_epic.ogg"
DROP=24.445; EPIC_FALL=480
START=$(awk "BEGIN{print $DROP - $EPIC_FALL / 20}")
ffmpeg -hide_banner -loglevel error -y -ss "$START" -i "$SRC" -vn -af "afade=t=in:st=0:d=0.6,alimiter=limit=0.95:level=disabled" \
  -ac 2 -ar 44100 -c:a libvorbis -q:a 6 "$OUT"
echo "$OUT: from ${START}s"

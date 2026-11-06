#!/usr/bin/env bash
# The remote's card for SA-00 Stellar Convergence (64x32, like halley.png and luna.png): weapon-coloured streaks coming
# in from every side to one white point. Needs ffmpeg. Run from anywhere: bash art/make_convergence_icon.sh
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
OUT="$HERE/../src/main/resources/assets/shooting_star_demo/textures/gui/sprites/skill/stellar_remote/stellar_convergence.png"
PX=40; PY=15
# angle the streak comes from (degrees), and its colour
STREAKS="200:111:232:255 160:255:201:60 235:255:90:90 120:190:140:255 275:205:225:255 95:255:150:60 320:150:255:200"
expr() {  # $1 = channel index (1 r, 2 g, 3 b)
  local e="0"
  for s in $STREAKS; do
    IFS=: read -r deg r g b <<< "$s"
    local c; c=$(echo "$r $g $b" | cut -d' ' -f"$1")
    local a; a=$(awk -v d="$deg" 'BEGIN{printf "%.5f", d * 3.14159265 / 180}')
    # along: how far back up the streak (0 at the point); across: off its line
    e="$e+$c*lt(abs((X-$PX)*sin($a)-(Y-$PY)*cos($a)),0.75)*gt((X-$PX)*cos($a)+(Y-$PY)*sin($a),0)*max(0,1-((X-$PX)*cos($a)+(Y-$PY)*sin($a))/34)"
  done
  echo "$e"
}
bg() { echo "$1+($2-$1)*Y/31+45*lt(mod(X*7919+Y*104729,173),2)"; }
core="255*max(0,1-hypot(X-$PX,Y-$PY)/4.5)"
ffmpeg -hide_banner -loglevel error -y -f lavfi -i "color=c=black:s=64x32:d=1" -frames:v 1 -vf "format=rgb24,geq=r='clip($(bg 10 26)+$(expr 1)+$core,0,255)':g='clip($(bg 8 14)+$(expr 2)+$core,0,255)':b='clip($(bg 30 46)+$(expr 3)+$core,0,255)'" "$OUT"
echo "$OUT"

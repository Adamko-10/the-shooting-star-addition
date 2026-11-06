#!/usr/bin/env bash
# The remote's card for SA-03 Proxima (64x32, like halley.png and luna.png): a red dwarf inside a broken Dyson lattice,
# a beam of the flare leaving it to the right. Needs ffmpeg. Run from anywhere: bash art/make_proxima_icon.sh
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
OUT="$HERE/../src/main/resources/assets/shooting_star_demo/textures/gui/sprites/skill/stellar_remote/proxima.png"
SX=21; SY=16
D="hypot(X-$SX,Y-$SY)"
STAR="lt($D,7.5)"
CORE="max(0,1-$D/7.5)"
HALO="max(0,1-($D-7.5)/5)*(1-$STAR)"
RING="lt(abs($D-11),0.9)*lt(mod(floor((atan2(Y-$SY,X-$SX)+3.1416)*14/3.1416),3),2)"
BEAM="lt(abs(Y-$SY),1.1-0.02*(X-30))*gt(X,30)*max(0,1-(X-30)/36)"
SPARK="45*lt(mod(X*7919+Y*104729,173),2)"
r="clip(14+$SPARK+$STAR*(175+80*$CORE)+90*$HALO+110*$RING+255*$BEAM,0,255)"
g="clip(6+$SPARK+$STAR*(30+200*$CORE*$CORE)+15*$HALO+100*$RING+120*$BEAM,0,255)"
b="clip(14+$SPARK+$STAR*(45+170*$CORE*$CORE*$CORE)+30*$HALO+115*$RING+150*$BEAM,0,255)"
ffmpeg -hide_banner -loglevel error -y -f lavfi -i "color=c=black:s=64x32:d=1" -frames:v 1 -vf "format=rgb24,geq=r='$r':g='$g':b='$b'" "$OUT"
echo "$OUT"

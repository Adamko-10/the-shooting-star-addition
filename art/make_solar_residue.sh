#!/usr/bin/env bash
# Solar Residue's block texture (16x192: 12 animation frames, like comet_heart.png): dark crimson crystal facets, veins
# of white-pink plasma pulsing through them. Needs ffmpeg. Run from anywhere: bash art/make_solar_residue.sh
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
OUT="$HERE/../src/main/resources/assets/shooting_star_addition/textures/block/solar_residue.png"
# a facet's steady random shade (0..1), by its 4x4 cell
FACET="(abs(sin(floor(X/4)*12.9898+floor(Y/4)*78.233)*43758.5453)-floor(abs(sin(floor(X/4)*12.9898+floor(Y/4)*78.233)*43758.5453)))"
# the veins, and how bright they burn in this frame
VEIN="lt(abs(sin((X+Y*0.6)*0.75+1.6*sin(Y*0.45))),0.32)"
PULSE="(0.55+0.45*sin(2*PI*N/12+X*0.35))"
ffmpeg -hide_banner -loglevel error -y -f lavfi -i "color=c=black:s=16x16:r=12:d=1" \
  -vf "format=rgb24,geq=r='clip(80+70*$FACET+175*$VEIN*$PULSE,0,255)':g='clip(8+18*$FACET+140*$VEIN*$PULSE,0,255)':b='clip(34+34*$FACET+165*$VEIN*$PULSE,0,255)',tile=1x12" \
  -frames:v 1 "$OUT"
echo "$OUT"

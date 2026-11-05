#!/usr/bin/env bash
# Proxima's ash layer texture (16x16): grey ash, lighter flecks and a few dark cinders. Needs ffmpeg.
# Run from anywhere: bash art/make_ash.sh
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
OUT="$HERE/../src/main/resources/assets/shooting_star_addition/textures/block/proxima_ash.png"
N="(abs(sin(X*12.9898+Y*78.233)*43758.5453)-floor(abs(sin(X*12.9898+Y*78.233)*43758.5453)))"
M="(abs(sin(X*39.346+Y*11.135)*24634.6345)-floor(abs(sin(X*39.346+Y*11.135)*24634.6345)))"
V="(120+40*$N-70*lt($M,0.08)+35*gt($M,0.9))"
ffmpeg -hide_banner -loglevel error -y -f lavfi -i "color=c=black:s=16x16:d=1" -frames:v 1 \
  -vf "format=rgb24,geq=r='clip($V+6,0,255)':g='clip($V,0,255)':b='clip($V-4,0,255)'" "$OUT"
echo "$OUT"

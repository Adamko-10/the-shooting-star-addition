#!/usr/bin/env bash
# Proxima's Earth (client/proxima/EarthSphere), from the same NASA imagery The Shooting Star's own space Earth uses (its
# credits name the sources; nothing of its own is used). The sources are kept in the project folder's
# "art sources/nasa", downloaded 2026-11-06 from NASA Visible Earth / Earth Observatory (public domain):
#   world.topo.bathy.200406.3x5400x2700.jpg  Blue Marble: Next Generation, topography and bathymetry, June 2004
#     https://assets.science.nasa.gov/content/dam/science/esd/eo/images/bmng/bmng-topography-bathymetry/june/world.topo.bathy.200406.3x5400x2700.jpg
#   BlackMarble_2016_01deg.jpg               Black Marble 2016 (Suomi NPP VIIRS), night lights
#     https://assets.science.nasa.gov/content/dam/science/esd/eo/images/imagerecords/144000/144898/BlackMarble_2016_01deg.jpg
# The clouds are made here (make_earth_clouds.js): NASA's Blue Marble clouds image is no longer online.
# Usage: bash art/make_earth.sh [sources dir]
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
SRC=${1:-"$HERE/../../art sources/nasa"}
OUT="$HERE/../src/main/resources/assets/shooting_star_addition/textures/proxima"
mkdir -p "$OUT"
# the day side, a little more saturated (as seen from space through the air it reads paler otherwise)
ffmpeg -hide_banner -loglevel error -y -i "$SRC/world.topo.bathy.200406.3x5400x2700.jpg" \
  -vf "scale=2048:1024:flags=lanczos,eq=saturation=1.12:contrast=1.04" -pix_fmt rgb24 "$OUT/earth_day.png"
# the night lights: only the cities, the faint background taken out, warmed
ffmpeg -hide_banner -loglevel error -y -i "$SRC/BlackMarble_2016_01deg.jpg" \
  -vf "scale=1024:512:flags=lanczos,format=gray,curves=all='0/0 0.3/0 0.5/0.7 1/1',gblur=sigma=0.6,format=rgb24" "$OUT/earth_lights.png"
node "$HERE/make_earth_clouds.js" "$OUT/earth_clouds.png" 2048
ls -la "$OUT"

#!/usr/bin/env bash
# Proxima's Earth (client/proxima/ProximaCosmos), from real imagery: NASA's (as The Shooting Star's own space Earth;
# its credits name the sources; nothing of its own is used) and Solar System Scope's (CC BY 4.0, credited in
# textures/proxima/CREDITS.txt). The sources are kept in the project folder's "art sources":
#   nasa/world.topo.bathy.200406.3x5400x2700.jpg  Blue Marble: Next Generation, topography and bathymetry, June 2004
#     https://assets.science.nasa.gov/content/dam/science/esd/eo/images/bmng/bmng-topography-bathymetry/june/world.topo.bathy.200406.3x5400x2700.jpg
#   nasa/BlackMarble_2016_01deg.jpg               Black Marble 2016 (Suomi NPP VIIRS), night lights
#     https://assets.science.nasa.gov/content/dam/science/esd/eo/images/imagerecords/144000/144898/BlackMarble_2016_01deg.jpg
#   solar system scope/Solarsystemscope_texture_8k_earth_{clouds.jpg,normal_map.tif,specular_map.tif}
#     https://commons.wikimedia.org/wiki/File:Solarsystemscope_texture_8k_earth_clouds.jpg (and _normal_map, _specular_map)
# Two images, 4096x2048, equirectangular:
#   earth_day.png     RGB the day side, A the water (where the sun glints)
#   earth_detail.png  RG the land's relief (a normal map, its slopes boosted 5x), B the city lights, A the clouds
# Usage: bash art/make_earth.sh [art sources dir]
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
SRC=${1:-"$HERE/../../art sources"}
SSS="$SRC/solar system scope/Solarsystemscope_texture_8k_earth"
OUT="$HERE/../src/main/resources/assets/shooting_star_addition/textures/proxima"
mkdir -p "$OUT"
W=4096
H=2048
ffmpeg -hide_banner -loglevel error -y -i "$SRC/nasa/world.topo.bathy.200406.3x5400x2700.jpg" -i "${SSS}_specular_map.tif" \
  -filter_complex "[0]scale=$W:$H:flags=lanczos,eq=saturation=1.05,format=gbrp,extractplanes=r+g+b[r][g][b];\
[1]scale=$W:$H:flags=lanczos,format=gray[a];[g][b][r][a]mergeplanes=mapping=0x00102030:format=gbrap" \
  -pix_fmt rgba "$OUT/earth_day.png"
ffmpeg -hide_banner -loglevel error -y -i "${SSS}_normal_map.tif" -i "$SRC/nasa/BlackMarble_2016_01deg.jpg" -i "${SSS}_clouds.jpg" \
  -filter_complex "[0]scale=$W:$H:flags=area,format=gbrp,extractplanes=r+g[nr][ng];\
[ng]setsar=1,lut=y='clip(128+(val-128)*5,0,255)'[g];[nr]setsar=1,lut=y='clip(128+(val-128)*5,0,255)'[r];\
[1]scale=$W:$H:flags=lanczos,format=gray,curves=all='0/0 0.3/0 0.5/0.7 1/1',setsar=1[b];\
[2]scale=$W:$H:flags=lanczos,format=gray,curves=all='0/0 0.22/0 0.6/0.75 0.9/1 1/1',setsar=1[a];\
[g][b][r][a]mergeplanes=mapping=0x00102030:format=gbrap" \
  -pix_fmt rgba "$OUT/earth_detail.png"
rm -f "$OUT/earth_lights.png" "$OUT/earth_clouds.png"
ls -la "$OUT"

#!/usr/bin/env bash
# SA-04 Proxima's own sounds, synthesised with ffmpeg (no samples). Run from anywhere: bash art/make_proxima_sounds.sh
#   proxima_radio: the coronal front's Type II radio burst, a whine drifting down from 1600 to 220 Hz over the front's
#   8.5 s (an exponential sweep with two harmonics and a radio flutter), over banded static, a little crushed.
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
OUT="$HERE/../src/main/resources/assets/shooting_star_addition/sounds"
T=8.5
# the sweep's phase: 2 pi f0 T / ln(f0/f1) (1 - exp(-t ln(f0/f1) / T)), with f0 1600, f1 220 (ln = 1.984)
P="(2*PI*1600*$T/1.984*(1-exp(-t*1.984/$T)))"
ffmpeg -hide_banner -loglevel error -y \
  -f lavfi -i "aevalsrc='(sin($P)+0.35*sin(2*$P)+0.15*sin(3*$P))*(0.7+0.3*sin(2*PI*6.5*t))*0.4':s=44100:d=$T" \
  -f lavfi -i "anoisesrc=d=$T:c=pink:a=0.25:r=44100" \
  -filter_complex "[1]bandpass=f=2200:width_type=h:w=2400,tremolo=f=11:d=0.6[n];[0][n]amix=inputs=2:weights='1 0.6',highpass=f=220,lowpass=f=5000,acrusher=bits=10:mix=0.25,afade=t=in:d=0.6,afade=t=out:st=7:d=1.5,loudnorm=I=-18:TP=-2" \
  -ac 1 -ar 44100 -c:a libvorbis -q:a 4 "$OUT/proxima_radio.ogg"
echo "$OUT/proxima_radio.ogg"

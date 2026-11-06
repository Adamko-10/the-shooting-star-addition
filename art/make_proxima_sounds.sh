#!/usr/bin/env bash
# SA-04 Proxima's own sounds, synthesised with ffmpeg (no samples). Run from anywhere: bash art/make_proxima_sounds.sh
#   proxima_radio:  the coronal front's Type II radio burst, a whine drifting down from 1600 to 220 Hz over the front's
#                   8.5 s (an exponential sweep with two harmonics and a radio flutter), over banded static, a little crushed.
#   proxima_beam:   the Dyson sphere's lens firing (played at LENS): a charge whining up for 0.5 s, then the discharge, a
#                   bright zap and a buzzing hum dying away.
#   proxima_flash:  the flare's light arriving: a searing white rush, crackling like a fire catching, over a low thump.
#   proxima_impact: ground zero: a falling sub-bass thump, a crack, a roaring tail and the plasma's sizzle on top.
#   proxima_wave:   a shock front passing (the coronal front, then ground zero's wave): a roar coming on high and leaving
#                   low (two bands, the low one later), with a thud as it passes.
# Levels by role, like art/overhaul_sounds.sh (impacts -12.5 LUFS, the flash -14, cues -15/-16).
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
OUT="$HERE/../src/main/resources/assets/shooting_star_addition/sounds"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
SR=44100
ff() { ffmpeg -hide_banner -loglevel error -y "$@"; }
loudness() { ffmpeg -hide_banner -nostats -i "$1" -af ebur128 -f null - 2>&1 | awk '/^ *I:/{v=$2} END{print v}'; }

# finish <wav> <name> <LUFS>: to its level, peaks held under -1 dB, mono Ogg Vorbis (a second pass makes up what the
# limiter took, by up to 3 dB)
finish() {
  local gain short
  gain=$(awk -v t="$3" -v i="$(loudness "$1")" 'BEGIN{printf "%.2f", t - i}')
  for pass in 1 2; do
    ff -i "$1" -af "volume=${gain}dB,alimiter=limit=0.89:attack=3:release=80:level=0" -ac 1 -ar $SR -c:a libvorbis -q:a 5 "$OUT/$2.ogg"
    short=$(awk -v t="$3" -v i="$(loudness "$OUT/$2.ogg")" 'BEGIN{d = t - i; if (d > 3) d = 3; printf "%.2f", d}')
    awk -v s="$short" 'BEGIN{exit !(s > 0.3)}' || break
    gain=$(awk -v g="$gain" -v s="$short" 'BEGIN{printf "%.2f", g + s}')
  done
  echo "$2: $(loudness "$OUT/$2.ogg") LUFS"
}

T=8.5
# the sweep's phase: 2 pi f0 T / ln(f0/f1) (1 - exp(-t ln(f0/f1) / T)), with f0 1600, f1 220 (ln = 1.984)
P="(2*PI*1600*$T/1.984*(1-exp(-t*1.984/$T)))"
ff -f lavfi -i "aevalsrc='(sin($P)+0.35*sin(2*$P)+0.15*sin(3*$P))*(0.7+0.3*sin(2*PI*6.5*t))*0.4':s=44100:d=$T" \
  -f lavfi -i "anoisesrc=d=$T:c=pink:a=0.25:r=44100" \
  -filter_complex "[1]bandpass=f=2200:width_type=h:w=2400,tremolo=f=11:d=0.6[n];[0][n]amix=inputs=2:weights='1 0.6',highpass=f=220,lowpass=f=5000,acrusher=bits=10:mix=0.25,afade=t=in:d=0.6,afade=t=out:st=7:d=1.5,loudnorm=I=-18:TP=-2" \
  -ac 1 -ar 44100 -c:a libvorbis -q:a 4 "$OUT/proxima_radio.ogg"
echo "proxima_radio"

# the beam: charge 180 -> 900 Hz over 0.5 s (phase 2 pi (180 t + 720 t^2)), then a zap and a 110 Hz buzz
ff -filter_complex "
  aevalsrc='if(lt(t,0.5),(sin(2*PI*(180*t+720*t*t))+0.4*sin(4*PI*(180*t+720*t*t)))*pow(t/0.5,2),0)':s=$SR:d=1.8[charge];
  aevalsrc='if(gte(t,0.5),(sin(2*PI*110*t)+0.5*sin(2*PI*220*t)+0.33*sin(2*PI*330*t)+0.25*sin(2*PI*440*t)+0.2*sin(2*PI*550*t))*exp(-2.4*(t-0.5))*(0.75+0.25*sin(2*PI*31*t)),0)':s=$SR:d=1.8[buzz];
  anoisesrc=d=1.8:c=white:r=$SR:a=0.9:seed=21,bandpass=f=3500:width_type=h:w=4000,volume='if(gte(t,0.5),exp(-9*(t-0.5)),0)':eval=frame[zap];
  [charge][buzz][zap]amix=inputs=3:weights='0.7 0.8 1':normalize=0,highpass=f=40,afade=t=out:st=1.4:d=0.4" \
  -c:a pcm_f32le "$TMP/beam.wav"
finish "$TMP/beam.wav" proxima_beam -16

# the flash: a white rush (5 ms attack), sparse crackle, a low thump
ff -filter_complex "
  anoisesrc=d=2.6:c=white:r=$SR:a=0.8:seed=31,highpass=f=800,lowpass=f=11000,volume='min(1,t/0.005)*exp(-2.6*t)':eval=frame[rush];
  aevalsrc='if(gt(random(1),0.9965),(random(2)-0.5)*2,0)*exp(-1.1*t)':s=$SR:d=2.6,highpass=f=1500,lowpass=f=7000[crackle];
  aevalsrc='sin(2*PI*(70*t-9*t*t))*exp(-6*t)':s=$SR:d=1.0[thump];
  [rush][crackle][thump]amix=inputs=3:weights='1 0.7 0.8':normalize=0:duration=longest,afade=t=out:st=2.0:d=0.6" \
  -c:a pcm_f32le "$TMP/flash.wav"
finish "$TMP/flash.wav" proxima_flash -14

# the impact: sub thump 55 Hz falling, a crack, a roaring echoing tail, the plasma's sizzle
ff -filter_complex "
  aevalsrc='sin(2*PI*(55*t-12*t*t))*exp(-1.6*t)':s=$SR:d=2.5[sub];
  anoisesrc=d=0.4:c=white:r=$SR:a=0.9:seed=41,highpass=f=1200,lowpass=f=9000,volume='exp(-18*t)':eval=frame[crack];
  anoisesrc=d=6.0:c=brown:r=$SR:a=0.9:seed=42,lowpass=f=400,volume='min(1,t/0.03)*exp(-0.7*t)':eval=frame,aecho=0.8:0.55:190|460|820:0.45|0.32|0.22[roar];
  anoisesrc=d=4.0:c=pink:r=$SR:a=0.5:seed=43,bandpass=f=5000:width_type=h:w=3000,tremolo=f=23:d=0.7,volume='exp(-1.2*t)':eval=frame[sizzle];
  [sub][crack][roar][sizzle]amix=inputs=4:weights='1.2 0.8 1 0.45':normalize=0:duration=longest,
  acompressor=threshold=0.1:ratio=3:attack=10:release=260:makeup=1.5,afade=t=out:st=4.6:d=1.4" \
  -c:a pcm_f32le "$TMP/impact.wav"
finish "$TMP/impact.wav" proxima_impact -12.5

# the wave: a high band peaking at 0.8 s, a low band at 1.2 s (it comes on high and leaves low), a thud at 1.0 s
ff -filter_complex "
  anoisesrc=d=3.5:c=pink:r=$SR:a=0.8:seed=51,bandpass=f=1800:width_type=h:w=1800,volume='exp(-pow((t-0.8)/0.35,2))':eval=frame[high];
  anoisesrc=d=3.5:c=brown:r=$SR:a=0.9:seed=52,lowpass=f=450,volume='if(lt(t,1.2),pow(t/1.2,2),exp(-1.4*(t-1.2)))':eval=frame[low];
  aevalsrc='if(gte(t,1.0),sin(2*PI*(45*(t-1)-6*(t-1)*(t-1)))*exp(-5*(t-1)),0)':s=$SR:d=2.0[thud];
  [high][low][thud]amix=inputs=3:weights='0.8 1 1':normalize=0:duration=longest,afade=t=out:st=2.9:d=0.6" \
  -c:a pcm_f32le "$TMP/wave.wav"
finish "$TMP/wave.wav" proxima_wave -15

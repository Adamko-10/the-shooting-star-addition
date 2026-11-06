#!/usr/bin/env bash
# The sound overhaul (2026-11-04; the user: rework the sounds we have, don't replace them). Takes the sounds
# make_sounds.py synthesised, kept as they came out in art/raw/ (python3 make_sounds.py art/raw remakes one there), and
# reworks them with ffmpeg into the ones the addon ships:
#   - every sound: the synthesis' harsh sheen (3-4 kHz, the top end) and sub-audible rumble taken down a little;
#   - the impacts: a sub-bass drop, a crack on top and a rolling, echoing low tail layered in, then compressed;
#   - levels by role, matched to The Shooting Star's own (its hits ~-11 LUFS, hums ~-17, cues ~-18; measured only);
#   - the sounds that repeat get variants (sounds.json picks one at random);
#   - new for Luna's build-up: a rising rumble from the alarm to the fall, the ground's rumble swelling then dropping
#     to near-silence just before the impact, and a reversed "inhale" into it.
# Needs ffmpeg (with libvorbis). Run from anywhere: bash art/overhaul_sounds.sh [resources dir]. Luna's music is the
# user's track and isn't touched.
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
RAW="$HERE/raw/assets/shooting_star_addition/sounds"
OUT="${1:-$HERE/../src/main/resources}/assets/shooting_star_addition/sounds"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
SR=44100
CLEAN="highpass=f=25,equalizer=f=3600:t=q:w=1.2:g=-2.5,highshelf=f=8000:g=-2"

ff() { ffmpeg -hide_banner -loglevel error -y "$@"; }
loudness() { ffmpeg -hide_banner -nostats -i "$1" -af ebur128 -f null - 2>&1 | awk '/^ *I:/{v=$2} END{print v}'; }
length() { ffprobe -v error -show_entries format=duration -of csv=p=0 "$1"; }

# finish <wav> <name> <LUFS>: to its level, peaks held under -1 dB, mono Ogg Vorbis. The limiter takes some loudness
# back (most from the hits' sub-bass), so a second pass makes up what it took, by up to 3 dB.
finish() {
  local gain short
  gain=$(awk -v t="$3" -v i="$(loudness "$1")" 'BEGIN{printf "%.2f", t - i}')
  for pass in 1 2; do
    ff -i "$1" -af "volume=${gain}dB,alimiter=limit=0.89:attack=3:release=80:level=0" -ac 1 -ar $SR -c:a libvorbis -q:a 5 "$OUT/$2.ogg"
    short=$(awk -v t="$3" -v i="$(loudness "$OUT/$2.ogg")" 'BEGIN{d = t - i; if (d > 3) d = 3; printf "%.2f", d}')
    awk -v s="$short" 'BEGIN{exit !(s > 0.3)}' || break
    gain=$(awk -v g="$gain" -v s="$short" 'BEGIN{printf "%.2f", g + s}')
  done
  printf '%-22s %6s LUFS  %5ss\n' "$2" "$(loudness "$OUT/$2.ogg")" "$(length "$OUT/$2.ogg" | cut -c1-5)"
}

# plain <name> <LUFS> [extra filters] [out name]: cleaned and set to its level
plain() {
  ff -i "$RAW/$1.ogg" -af "$CLEAN${3:+,$3}" -c:a pcm_f32le "$TMP/$1.wav"
  finish "$TMP/$1.wav" "${4:-$1}" "$2"
}

# hit <name> <LUFS> <sub Hz> <sub drop> <sub decay> <sub s> <crack lo> <crack hi> <crack decay> <tail lowpass> <tail s>
#     <weights: original sub crack tail>: the original with a falling sub-bass thump, a noise crack and an echoing low
#     tail under it (the tail runs <tail s> past the end, fading out)
hit() {
  local name=$1 lufs=$2 hz=$3 drop=$4 decay=$5 len=$6 lo=$7 hi=$8 cdecay=$9 lp=${10} pad=${11} weights=${12}
  local dur fade
  dur=$(length "$RAW/$name.ogg")
  fade=$(awk -v d="$dur" -v p="$pad" 'BEGIN{printf "%.3f", d + p - 1.6}')
  ff -i "$RAW/$name.ogg" -filter_complex "
    [0:a]$CLEAN,asplit=2[o][t];
    aevalsrc='sin(2*PI*($hz*t-$drop*t*t))*exp(-$decay*t)':s=$SR:d=$len[sub];
    anoisesrc=d=0.4:c=white:r=$SR:a=0.8:seed=7,highpass=f=$lo,lowpass=f=$hi,volume='exp(-$cdecay*t)':eval=frame[crack];
    [t]apad=pad_dur=$pad,lowpass=f=$lp,aecho=0.8:0.55:190|460|820:0.45|0.32|0.22,adelay=delays=110:all=1[tail];
    [o][sub][crack][tail]amix=inputs=4:weights='$weights':normalize=0:duration=longest,
    acompressor=threshold=0.1:ratio=3:attack=10:release=260:makeup=1.5,afade=t=out:st=$fade:d=1.6" \
    -c:a pcm_f32le "$TMP/$name.wav"
  finish "$TMP/$name.wav" "$name" "$lufs"
}

# variant <name> <n> <LUFS> <filters>: another take of a sound that repeats
variant() {
  ff -i "$RAW/$1.ogg" -af "$CLEAN,$4" -c:a pcm_f32le "$TMP/$1_$2.wav"
  finish "$TMP/$1_$2.wav" "$1_$2" "$3"
}

rate() { echo "asetrate=$SR*$1,aresample=$SR"; }

echo "== hits"
#        name              LUFS   Hz drop decay  s     crack lo  hi    dec  tail lp  s    orig sub crack tail
hit halley_impact         -11.0   60  9  2.4  2.2   2600 11000  34   500  2.5  '1 0.85 0.55 0.6'
hit luna_impact           -10.5   48  8  1.7  2.6    900  5500  20   320  3.5  '1 1.0 0.5 0.75'
hit halley_touchdown      -12.5   70 12  3.5  1.4   2000 10000  40   600  1.2  '1 0.7 0.4 0.4'
hit halley_boom           -13.0   65 14  4.0  1.2   1500  9000  45   700  1.5  '1 0.6 0.6 0.5'
hit luna_entry            -12.5   50  9  2.5  1.8   1200  7000  25   450  2.0  '1 0.8 0.45 0.55'
hit luna_tear             -13.5   45  7  2.0  2.0    800  5000  15   400  1.5  '1 0.7 0.5 0.5'
hit luna_shockwave        -13.5   40  5  1.5  2.5    600  4000  12   300  3.0  '1 0.6 0.25 0.8'

echo "== cues and beds"
plain halley_mark         -16.0
plain halley_countdown    -18.0
plain halley_sight        -15.0
plain halley_approach     -16.0
plain halley_plough       -14.5
plain halley_frost        -15.5
plain halley_evac         -16.0
plain halley_dusk         -17.0
plain luna_uplink         -16.0
plain luna_alarm          -17.0
plain luna_crack          -14.5
plain luna_fall           -15.5
plain luna_settle         -15.0
plain luna_sizzle         -17.0

echo "== repeated, with variants"
plain halley_hum          -19.0
variant halley_hum 2      -19.0 "$(rate 0.94),lowshelf=f=200:g=2"
variant halley_hum 3      -19.0 "$(rate 1.05),tremolo=f=3:d=0.25"
plain halley_burst        -15.0
variant halley_burst 2    -15.0 "$(rate 0.9)"
variant halley_burst 3    -15.0 "$(rate 1.12),highshelf=f=3000:g=2"
plain luna_debris         -18.0
variant luna_debris 2     -18.0 "$(rate 0.9),lowpass=f=3000"
variant luna_debris 3     -18.0 "$(rate 1.08),aecho=0.8:0.5:60:0.3"
plain luna_aftershock     -16.0
variant luna_aftershock 2 -16.0 "$(rate 0.88),lowpass=f=600"

echo "== Luna's build-up"
# the ground's rumble (3 s, from CONTACT - 60): swelling, then near-silence for its last half second (CONTACT - 11 on)
plain luna_rumble         -15.0 "volume='if(lt(t,2.35),0.4+0.6*pow(t/2.35,1.7),max(0.06,1-(t-2.35)/0.12))':eval=frame"
# from the alarm to the fall (2 s): a low roar rising and brightening, a shuddering drone, the crust grinding
ff -i "$RAW/luna_crack.ogg" -filter_complex "
  anoisesrc=d=2.0:c=brown:r=$SR:a=0.9:seed=11,lowpass=f=160,volume='pow(t/2.0,1.5)':eval=frame[a];
  anoisesrc=d=2.0:c=pink:r=$SR:a=0.6:seed=12,bandpass=f=420:w=500:t=h,volume='pow(t/2.0,3)':eval=frame[b];
  aevalsrc='0.8*sin(2*PI*(30*t+5*t*t))*pow(t/2.0,2)*(0.65+0.35*sin(2*PI*(5*t+3*t*t)))':s=$SR:d=2.0[c];
  [0:a]$(rate 0.6),lowpass=f=1500,volume=0.45,adelay=delays=500:all=1,apad,atrim=0:2.0[d];
  [a][b][c][d]amix=inputs=4:normalize=0:duration=first,$CLEAN,afade=t=out:st=1.92:d=0.08" \
  -c:a pcm_f32le "$TMP/luna_buildup.wav"
finish "$TMP/luna_buildup.wav" luna_buildup -16.0
# into the impact (0.65 s, ending on CONTACT): the start of the shock wave played backwards, with a rising rush
ff -i "$RAW/luna_shockwave.ogg" -filter_complex "
  [0:a]atrim=0.15:0.8,asetpts=PTS-STARTPTS,areverse,highpass=f=250,volume='pow(t/0.65,2.2)':eval=frame[r];
  anoisesrc=d=0.65:c=pink:r=$SR:a=0.5:seed=13,bandpass=f=1800:w=2400:t=h,volume='pow(t/0.65,3)':eval=frame[w];
  [r][w]amix=inputs=2:normalize=0:duration=longest,$CLEAN" \
  -c:a pcm_f32le "$TMP/luna_inhale.wav"
finish "$TMP/luna_inhale.wav" luna_inhale -17.0

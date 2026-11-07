#!/usr/bin/env bash
# SA-04 Proxima's music: the user's track, cut to Proxima's timeline (ProximaPlan). Usage:
#   bash art/make_proxima_music.sh [source]   (default: the project folder's music/Video Project 49.m4a)
# The game starts it at tick MUSIC_AT (10, ProximaFx) and keeps it in step, so the file's own time 0 is tick 10:
#   1. the track from A0 for (HUSH - 10) ticks, chosen so that the track's gap-and-entry (ENTRY, 16.85 s in the draft)
#      lands on the flash (FLASH 300, since 2026-11-07): its pulses play under the space intro, its calmer middle under the coronal front;
#   2. half a second of silence: the hush (HUSH 448 to IMPACT 460);
#   3. the main section from its downbeat (DROP, 28.85 s in the draft), hard on the impact, faded out by FADE_END ticks.
# Then levelled like luna_music.ogg (-18 LUFS integrated, peaks held under -0.5 dBFS).
# For a new version of the track, find its moments again (a spectrogram and a loudness curve, 50 ms steps) and set
# ENTRY and DROP; the ticks come from ProximaPlan.
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
SRC=${1:-"$HERE/../../music/Video Project 49.m4a"}
OUT="$HERE/../src/main/resources/assets/shooting_star_addition/sounds/proxima_music.ogg"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

MUSIC_AT=10; FLASH=300; HUSH=448; IMPACT=460; FADE_END=690
ENTRY=16.85; DROP=28.85
A0=$(awk "BEGIN{print $ENTRY - ($FLASH - $MUSIC_AT) / 20}")
ALEN=$(awk "BEGIN{print ($HUSH - $MUSIC_AT) / 20}")
GAP=$(awk "BEGIN{print ($IMPACT - $HUSH) / 20}")
CLEN=$(awk "BEGIN{print ($FADE_END - $IMPACT) / 20}")
ffmpeg -hide_banner -loglevel error -y -i "$SRC" -filter_complex "
  [0]aformat=sample_rates=48000:channel_layouts=stereo,asplit[s1][s2];
  [s1]atrim=start=$A0:duration=$ALEN,asetpts=PTS-STARTPTS,afade=t=in:d=0.3,afade=t=out:st=$(awk "BEGIN{print $ALEN - 0.45}"):d=0.45[a];
  aevalsrc=0|0:s=48000:d=$GAP,aformat=channel_layouts=stereo[g];
  [s2]atrim=start=$DROP:duration=$CLEN,asetpts=PTS-STARTPTS,afade=t=in:d=0.012,afade=t=out:st=$(awk "BEGIN{print $CLEN - 3}"):d=3[c];
  [a][g][c]concat=n=3:v=0:a=1" "$TMP/cut.wav"
I=$(ffmpeg -hide_banner -nostats -i "$TMP/cut.wav" -af ebur128 -f null - 2>&1 | awk '/^ +I:/{print $2}' | tail -1)
GAIN=$(awk "BEGIN{print -18 - ($I)}")
ffmpeg -hide_banner -loglevel error -y -i "$TMP/cut.wav" -af "volume=${GAIN}dB,alimiter=limit=0.94:level=disabled" \
  -ac 2 -ar 44100 -c:a libvorbis -q:a 6 "$OUT"
echo "$OUT: track from ${A0}s, drop ${DROP}s on IMPACT, ${GAIN} dB"

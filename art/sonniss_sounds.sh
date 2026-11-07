#!/usr/bin/env bash
# The Sonniss sound overhaul (docs/SOUNDS.md; 2026-11-06): every sound of Halley and Luna remade from the
# Sonniss GDC 2026 game-audio bundle. Each sound is a recipe: layers cut from the bundle's files, each brought to the
# same loudness first (-20 LUFS, so a layer's gain means the same for every source), then its own gain, start time
# and filters (pitch, EQ, envelope, reverse); mixed, trimmed to the length the code expects, set to its level by role
# (impacts -10.5 to -12.5 LUFS, cues -15/-16, beds -16/-18, hums -19; as art/overhaul_sounds.sh) and written as mono
# Ogg Vorbis (mono: Minecraft only places mono sounds in the world).
# The layers were picked by measuring the sources (the project folder's `sound library\envelope.js`), not by ear:
# every result needs the user's ears in game.
#
# Usage: bash art/sonniss_sounds.sh <out dir> [sound names...]
#   <out dir>: where the .ogg files go. No default on purpose: a listening folder until the user approves the sounds,
#   then src/main/resources/assets/shooting_star_addition/sounds. Names pick sounds (a base name takes its variants,
#   halley_hum = halley_hum, _2, _3); none = all, which also rewrites art/sounds/sources.md (which Sonniss file went
#   into which sound, so a pick can be swapped out if Sonniss ever objects).
# The bundle stays outside the repo (only our mixes ship); SONNISS=<the bundle's files folder> to point elsewhere.
# Needs ffmpeg (with libvorbis) and awk.
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
LIB="${SONNISS:-$HERE/../../sound library/sonniss gdc 2026/files}"
[ $# -ge 1 ] || { echo "usage: bash art/sonniss_sounds.sh <out dir> [sound names...]" >&2; exit 2; }
OUT=$1; shift
NAMES=("$@")
mkdir -p "$OUT"
[ -d "$LIB" ] || { echo "no Sonniss bundle at $LIB (set SONNISS)" >&2; exit 1; }
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
SR=44100
: > "$TMP/sources"

ff() { ffmpeg -hide_banner -loglevel error -y "$@"; }
calc() { awk "BEGIN{printf \"%.3f\", $1}"; }
loudness() { ffmpeg -hide_banner -nostats -i "$1" -af ebur128 -f null - 2>&1 | awk '/^ *I:/{v=$2} END{print v}'; }
length() { ffprobe -v error -show_entries format=duration -of csv=p=0 "$1"; }
# rate <k>: tape-style pitch and speed by k (0.5 = an octave down and twice as long)
rate() { awk -v k="$1" -v r=$SR 'BEGIN{printf "asetrate=%d,aresample=%d", r * k + 0.5, r}'; }
want() {
  [ ${#NAMES[@]} -eq 0 ] && return 0
  local n; for n in "${NAMES[@]}"; do [ "$n" = "$1" ] && return 0; done; return 1
}

# level <file> <start> <length>: sets LEVEL to the cut's loudness in LUFS (a cut too short to measure: its peak - 12);
# cached, as the countdown and the debris use the same cuts many times (call it directly, not in $(...))
declare -A LEVELS
level() {
  local key="$1|$2|$3" out i p
  if [ -z "${LEVELS[$key]:-}" ]; then
    out=$(ffmpeg -hide_banner -nostats -ss "$2" -t "$3" -i "$1" -af "aformat=channel_layouts=mono,ebur128=peak=sample" -f null - 2>&1)
    i=$(awk '/^ *I:/{v=$2} END{print v}' <<< "$out")
    p=$(awk '/^ *Peak:/{v=$2} END{print v}' <<< "$out")
    LEVELS[$key]=$(awk -v i="$i" -v p="$p" 'BEGIN{ if (i == "" || i + 0 <= -69) i = p - 12; if (i < -60) i = -60; printf "%.2f", i }')
  fi
  LEVEL=${LEVELS[$key]}
}

# L <source> <from s> <length s> <gain dB> <at s> [filters]: one layer of the next sound. The cut is levelled to
# -20 LUFS, faded in and out by a few ms (no clicks), then the filters, the gain, and it starts <at> into the sound.
LAYERS=()
L() { LAYERS+=("$1~$2~$3~$4~$5~${6:-}"); }

# finish <wav> <name> <LUFS>: to its level, peaks held under -1 dB, mono Ogg Vorbis. The limiter takes some loudness
# back (most from the hits' sub-bass and the cues' transients), so up to two more passes make up what it took, by up to
# 3 dB each.
finish() {
  local gain short
  gain=$(awk -v t="$3" -v i="$(loudness "$1")" 'BEGIN{printf "%.2f", t - i}')
  for pass in 1 2 3; do
    ff -i "$1" -af "volume=${gain}dB,alimiter=limit=0.89:attack=3:release=80:level=0" -ac 1 -ar $SR -c:a libvorbis -q:a 5 "$OUT/$2.ogg"
    short=$(awk -v t="$3" -v i="$(loudness "$OUT/$2.ogg")" 'BEGIN{d = t - i; if (d > 3) d = 3; printf "%.2f", d}')
    awk -v s="$short" 'BEGIN{exit !(s > 0.3)}' || break
    gain=$(awk -v g="$gain" -v s="$short" 'BEGIN{printf "%.2f", g + s}')
  done
  printf '%-20s %6s LUFS (aimed %s)  %6ss\n' "$2" "$(loudness "$OUT/$2.ogg")" "$3" "$(length "$OUT/$2.ogg" | cut -c1-5)"
}

# make <name> <length s> <LUFS> [filters on the mix]: mixes the layers given since the last make into <name>.ogg.
# Each distinct cut is one input, split to every layer that uses it, and the graph goes through a file: the countdown's
# seventy-odd layers would pass Windows' command-line limit otherwise.
make() {
  local name=$1 len=$2 lufs=$3 post=${4:-} args=() graph="" layers="" labels="" n=0 m=0 i u spec file start dur gain at
  local filt norm path key splits
  local -A input uses
  for spec in "${LAYERS[@]}"; do
    IFS='~' read -r file start dur gain at filt <<< "$spec"
    case $file in /*|?:*) path=$file ;; *) path="$LIB/$file" ;; esac
    [ -f "$path" ] || { echo "$name: no source $path" >&2; exit 1; }
    key="$path|$start|$dur"
    if [ -z "${input[$key]:-}" ]; then
      input[$key]=$m; uses[$m]=0; m=$((m + 1))
      args+=(-ss "$start" -t "$dur" -i "$path")
    fi
    i=${input[$key]}; u=${uses[$i]}; uses[$i]=$((u + 1))
    level "$path" "$start" "$dur"
    norm=$(awk -v l="$LEVEL" -v g="$gain" 'BEGIN{printf "%.2f", -20 - l + g}')
    layers+="[c${i}_$u]afade=t=in:d=0.004,afade=t=out:st=$(calc "$dur - 0.012"):d=0.012${filt:+,$filt},volume=${norm}dB,adelay=delays=$(awk -v a="$at" -v r=$SR 'BEGIN{printf "%d", a * r + 0.5}')S:all=1[l$n];"
    labels+="[l$n]"; n=$((n + 1))
    case $file in "$TMP"/*) file="synthesised: $(basename "$file" .wav)" ;; esac
    printf '| %s | %s | %s | %s | %s | %s | %s |\n' "$name" "${file//|/\\|}" "$start" "$dur" "$gain" "$at" "$(sed 's/|/\\|/g' <<< "${filt:-}")" >> "$TMP/sources"
  done
  for ((i = 0; i < m; i++)); do
    splits=""; for ((u = 0; u < uses[$i]; u++)); do splits+="[c${i}_$u]"; done
    graph+="[$i:a]aformat=sample_fmts=fltp:channel_layouts=mono,aresample=$SR,asetpts=N/SR/TB,asplit=${uses[$i]}$splits;"
  done
  graph+="$layers${labels}amix=inputs=$n:normalize=0:duration=longest,asetpts=N/SR/TB,highpass=f=22${post:+,$post},apad=whole_dur=$len,atrim=end_sample=$(awk -v l="$len" -v r=$SR 'BEGIN{printf "%d", l * r + 0.5}'),afade=t=out:st=$(calc "$len - 0.015"):d=0.015[out]"
  printf '%s' "$graph" > "$TMP/graph.txt"
  ff "${args[@]}" -/filter_complex "$TMP/graph.txt" -map "[out]" -c:a pcm_f32le "$TMP/$name.wav"
  finish "$TMP/$name.wav" "$name" "$lufs"
  LAYERS=()
}

# ---- The sources (paths in the bundle's files folder; numbers = rows of catalogue.csv) -------------------------------
BLASTOFF="344 Audio - Air Designed/AEROJet_Blast Off Clean_344 Audio_Air Designed.wav"                           # 1
ENCOUNTER="344 Audio - Air Designed/AEROJet_Unidentified Encounter_344 Audio_Air Designed.wav"                   # 2
BOOKS="344 Audio - Antique Books/PAPRMisc_Pile Of Antique Books Falling Over 8_344 Audio_Antiques - Books.wav"   # 11
DROP_FAST16="344 Audio - Bass Drops & Downers Vol. 1/DSGNBass_Bass Drop & Downer Fast 16_344 Audio_Bass Drops & Downers.wav"
DROP_SLOW10="344 Audio - Bass Drops & Downers Vol. 1/DSGNBass_Bass Drop & Downer Slow 10_344 Audio_Bass Drops & Downers.wav"
DROP_FAST12="344 Audio - Bass Drops & Downers Vol. 2/DSGNBass_Bass Drop & Downer Fast 12_344 Audio_Bass Drops & Downers Vol 2.wav"
DROP_SLOW07="344 Audio - Bass Drops & Downers Vol. 2/DSGNBass_Bass Drop & Downer Slow 07_344 Audio_Bass Drops & Downers Vol 2.wav"
RATTLING="344 Audio - Bass Drops & Downers Vol. 3/DSGNBass_Rattling Downer 3_344 Audio_Bass Drops and Downers Vol 3.wav"
TONEDOWN="344 Audio - Bass Drops & Downers Vol. 3/DSGNBass_Tone Downer (Reverb)_344 Audio_Bass Drops and Downers Vol 3.wav"
PUNCH="344 Audio - Cinematic Fight Vol. 1/FGHTImpt_4 x Punch, Body 02_344 Audio_Cinematic Fight Vol 1.wav"       # 57
WIND_RUSH="344 Audio - Elemental Palette Designed Vol. 1/WINDDsgn_Wind, Rush, Whoosh, Long x5 01_344 Audio_Elemental Palette Designed Vol 1.wav"
METALBOX_DRAG="344 Audio - Extreme Winds Vol. 1/METLFric_Large Metal Box, Drag, Geofon_344 Audio_Extreme Winds Vol 1.wav"
STORM_EYE="344 Audio - Extreme Winds Vol. 1/WINDDsgn_EXT, Eye Of The Storm_344 Audio_Extreme Winds Vol 1.wav"   # 74
ICE_CRUSH="Alexander Kopeikin - 100 kHz Designed Ice/ice, block of ice crushed, heavy-015.wav"                     # 83
ICE_SNAP="Alexander Kopeikin - 100 kHz Designed Ice/ice, crack, ice block snapping-001.wav"                        # 84
ICE_FIELD="Alexander Kopeikin - 100 kHz Designed Ice/ice, movement, ice drift, ice field cracking up, initial, wide-001.wav"
ICE_FISSURE="Alexander Kopeikin - 100 kHz Designed Ice/ice, surface cracking, fissure, fast, hard-003.wav"         # 86
TENSION_DRONE="Alexander Kopeikin - Emotion and Magic/magic, drone, tension, spellbound, evanescence-002.wav"      # 88
ENERGY_FLOW="Alexander Kopeikin - Emotion and Magic/magic, energy flow, astonishment-001.wav"                      # 89
ARROW_RATTLE="Cinematic Sound Design - Cartoon Bloopers/Arrow Hit Rattle.wav"                                      # 98
WOOSH_DEBRIS="Cinematic Sound Design - Colossal Impacts/Woosh Debris.wav"                                          # 106
GLASSY_SNAP="Cinematic Sound Design - Interface & Infographics/Interface Accept Glassy Snap.wav"                   # 110
POP_HIGH="Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav"                          # 112
DARK_INDUSTRIAL="Cinematic Sound Design - Sci-Fi Drones/Dark Industrial Ambience.wav"                              # 116
SPACESHIP="Cinematic Sound Design - Sci-Fi Drones/Roomtone Space Ship Interior Muted.wav"                          # 118
SCIFI_PING="Cinematic Sound Design - System & UI Feedback Elements/Interface Sci-Fi Ping Down.wav"                 # 121
BRAAM_CREEPY="Cinematic Sound Design - Ultra Transitions & Impacts/Transition Braam Slow Dark Creepy.wav"          # 126
TWINKLE="Cinematic Sound Design - User Interface/Button Arp Twinkle.wav"                                           # 128
METAL_RING="David Dumais Audio - Melee Weapons Sound Effects Pack 2/METLImpt_METAL SWING HIT Weapon Swing To Metallic Body Impact And Resonant Tail 01_DDUMAIS_MWP2.wav"
WHIP="David Dumais Audio - Melee Weapons Sound Effects Pack 2/WEAPWhip_WHIP Snap Crack 05_DDUMAIS_MWP2.wav"       # 134
EXPL_SMALL="Epic Stock Media - Anime Game/EXPLDsgn_Explosion Small Blast Enemy Death Crunchy Boom Cartoon Noisy Crash Impact Delay 03_ESM_AG.wav"
BOARD="Epic Stock Media - Board Game - Sound Set Kit for Tabletop and Digital Games/GAMEBoard_Event Board Reset Organic Multiple Pieces Wood Small 02_ESM_BG.wav"
ELEC_IMPACT="Epic Stock Media - Elemental Mutation Whooshes and Impacts/ELECMisc_Impact Electric Tonal Deep Movement Motion Hiss Glitch 01_ESM_EMWI.wav"
FIRE_WHOOSH="Epic Stock Media - Elemental Mutation Whooshes and Impacts/FIREWhsh_Whoosh Fire Deep Growl Monster Saturated Crisp 03_ESM_EMWI.wav"
GLASS_SHARDS="Epic Stock Media - Elemental Mutation Whooshes and Impacts/GLASMvmt_Whoosh Glass Crystal Fragments Sharp Shards Dry 05_ESM_EMWI.wav"
RADIO_STATIC="Epic Stock Media - Fake Advertisements and Radio Sound Effects Audio Construction Kit/COMStatic_Radio Ham Loop Static Hum Active Powered On Garbled Noise 01_ESM_FA.wav"
MAGIC_LIGHT="Epic Stock Media - Fantasy Game 2 - Sound Kit for Enchanted Realms/MAGAngl_Magic Light Spell Enchantment Potion Effect Tonal Bright 03_ESM_FG2.wav"
SCIFI_BURST="Epic Stock Media - Fantasy Game 2 - Sound Kit for Enchanted Realms/UIAlert_Collect Scifi Futuristic Electronic Bass Burst Sweep Heavy 04_ESM_FG2.wav"
GORE_SMASH="Epic Stock Media - Halloween Game - Haunted House and Horror Audio Scare Kit/GORESplt_Gore Designed Transient Heavy Impact Smash 01_ESM_HALG.wav"
DRYICE_SQUEAL="Epic Stock Media - HD Game Materials/ICEFric_Dry Ice High Metal Squeal Groan Bright Squeak Dissonant Short 13_ESM_HDGM.wav"
DRYICE_SQUEAK="Epic Stock Media - HD Game Materials/ICEFric_Dry Ice Squeak Metal Animal Mouse Imitation Short 07_ESM_HDGM.wav"
METAL_TAP="Epic Stock Media - HD Game Materials/METLImpt_Metal Old File Impact Tap Against Tire Iron Metallic Hit 01_ESM_HDGM.wav"
LATCH="Epic Stock Media - HD Lock And Mechanism Sound Design Kit/MECHLtch_Click Deep Mechanism Latch Button Nearfield Thunk 02_ESM_HDLM.wav"
SPRING="Epic Stock Media - HD Lock And Mechanism Sound Design Kit/METLTonl_Item Spring Wire Impact Flick Top Clatter Light Tap Roll Handling Short 01_ESM_HDLM.wav"
STORM_CRASH="Epic Stock Media - Public Spaces - Storms Lakes Parks and Rural Nature Exteriors/STORM_Texas Rain Thunder Initial Crash Boom Storm 01 Clap Lightning_ESM_CPS.wav"
REACTOR="Epic Stock Media - Strange Game Ambient Loops 3/DSGNSynth_Scifi Loop Ship Reactor Synth Layer Layered Saw Buzz_ESM_SGA3.wav"
SHIMMER="Epic Stock Media - Strange Game Ambient Loops 3/MAGShim_Shimmer Loop Small Bell Metal Taps_ESM_SGA3.wav"  # 198
FIRE_LOOP="Epic Stock Media - Synthesized Nature Loops and Sounds/FIREBurn_Loop Elements Fire Crackling Crunchy Flame Burn 03_ESM_SNLS.wav"
WIND_WHIP="Epic Stock Media - Synthesized Nature Loops and Sounds/WINDInt_Loop Weather Wind Whipping Constricted Flow Turbulent 01_ESM_SNLS.wav"
SWISH="Epic Stock Media - Tower Defense Game/DSGNStngr_Action Deploy Units Sword Slice Special Move Layered Swish 04_ESM_TDG.wav"
ICE_SHATTER="Epic Stock Media - Tower Defense Game/ICEBrk_Skill Freeze Whoosh Break Impact Layered Movement Shatter 03_ESM_TDG.wav"
SERVO="Epic Stock Media - Tower Defense Game/ROBTMvmt_Tower Deploy Hitech Robot Motor Dark Thump Servo Whine 04_ESM_TDG.wav"
ALARM_WHOLE="Federico Soler - Effective Trailer Alarms Vol. 2/EffectiveTrailer_Alarms_Vol2_WholeNotes_011.wav"     # 209
BOOM011="Federico Soler - Effective Trailer Booms Vol. 2/EffectiveTrailer_Booms_Vol2_011.wav"                     # 210
BOOM075="Federico Soler - Effective Trailer Booms Vol. 2/EffectiveTrailer_Booms_Vol2_075.wav"                     # 211
BOOM214="Federico Soler - Effective Trailer Booms Vol. 2/EffectiveTrailer_Booms_Vol2_214.wav"                     # 212
ARC="InMotionAudio - Arc/ELECArc_ArcDesign15_InMotionAudio_Arc.wav"                                               # 213
ARC_POWERUP="InMotionAudio - Arc/ELECArc_ArcPowerUpDesign04_InMotionAudio_Arc.wav"                                # 214
BUZZ="InMotionAudio - Arc/ELECBuzz_Buzz27_InMotionAudio_Arc.wav"                                                  # 215
CASE_DOWN="InMotionAudio - Instrument Case/OBJLug_CaseDown_Concrete12_InMotionAudio_InstrumentCase.wav"          # 229
PIPE="InMotionAudio - Sinister Textures 5/GOREMisc_Concrete_MetalPipe02_InMotionAudio_SinisterTextures5.wav"     # 245
DEATH_DRONE="InMotionAudio - The Death Whistle/CREAEthr_Aztec Death Whistle Drone_05_IMA_Death Whistle Samples.wav"
CAMPFIRE="Ivo Vicic - Campfire - Bonfire FX/24 Campfire, Dropping Fresh Pine Branches in Fire, Crackling, Sizzling Strong, Close 02.wav"
FIRE_OUT="Ivo Vicic - Campfire - Bonfire FX/42 Campfire, Putting Out Fire, Water from Bottle, Variation, Close.wav"
HORN32="Jake Fielding - Cinematic Horn Braams/DSGNBram____Cinematic Horn Braam, Epic, Cinematic, Dark, Instrument, Huge-32.wav"
FRIDGE="Jake Fielding - Fridge Hums Vol.2/MACHAppl_Deep Fridge Hum, Buzz, Electric Tone, Deep_Jake Fielding_Fridge Hums Pt2.wav"
THUNDER="Jake Fielding - Interior Wind Rain and Storms/THUN_Interior Thunder Rumble_JF_INT Storm_01.wav"          # 273
AM_SWEEP="Sonic Bat - Vintage Radio/SBvr_AM EQ Sweeping 002.wav"                                                 # 289
POWER_BTN="Sonic Bat - Vintage Radio/SBvr_Power Button 003.wav"                                                  # 291
GRAVEL="SoundBits - Cars - Mad Mustang Mercury/VEHSkid_Tire Skids on Gravel 15 06_SNDBTS_CRS-MMM.wav"             # 305
TRUCK_PASS="SoundBits - Pass-By - Trains, Trucks & Cars 2/VEHFrght_Freight Truck Pass By 22_SNDBTS_PB-TTC2.wav"   # 317
HURRICANE="The Noisery - City Rain/WINDTonl_Wind Strong Gusts Hurricane Vents Rattle 06_The Noisery_City Rain.wav"
METAL_THUD="The Noisery - Moaning Metal/DSGNImpt_Metal Hit Thud Thump Low Ring Geofon 1_The Noisery_Moaning Metal.wav"
METAL_BOWED="The Noisery - Moaning Metal/DSGNTonl_Designed Metal Bowed Screech Tonal Reverb 7_The Noisery_Moaning Metal.wav"
METAL_SCRAPE="The Noisery - Moaning Metal/DSGNTonl_Metal Scrape Low Tonal LFE 4_The Noisery_Moaning Metal.wav"    # 332
GLITCH_DATA="The Noisery - Rich Glitch/UIGlitch_Designed_Glitch_Corrupted_Data Error_The Noisery_Rich Glitch_06.wav"
TRAIN_ELEC="The Noisery - Transit Tunes - The German Train Collection/TRNElec_Train Electric Passenger By Close Right To_The Noisery_Transit Tunes_ The German Train Collection_01.wav"
CRANE="Victor Ermakov - Industrial Ambiences - Ship Repair Factory/MACHInd_Crane Onboard Ride Squeaks Motors_CW.wav"
# Where the trailer booms start: their hit is 0.25 s in. Their tails hold loud for seconds, so the hits below get a
# decay over the whole mix (volume exp(-k t)), or the limiter would level the attack and the tail into one wall.
B=0.2

# ======================================================================================================================
# Halley (SA-01)
# ======================================================================================================================
if want halley_mark; then   # the target locks: a sharp sci-fi lock-on, glassy
  L "$SCIFI_BURST" 0 0.31 0 0
  L "$GLASSY_SNAP" 0 0.44 1 0.01
  L "$SCIFI_PING" 0.08 0.67 -3 0.03 "$(rate 1.5)"
  L "$TWINKLE" 0 1.81 -9 0.1 "highpass=f=1500,aecho=0.8:0.5:90|210:0.35|0.2"
  make halley_mark 2.4 -16 "afade=t=out:st=1.6:d=0.8"
fi

if want halley_countdown; then
  # COUNTDOWN (t=29) to TOUCHDOWN (t=300), 13.55 s: a glassy tick with a deep latch thunk under it, the gap shrinking
  # from 1 s by 8% a tick to 0.09 s (a trill at the end), rising a little in pitch, over a cold drone and wind swelling
  while read -r at k gap; do
    L "$POP_HIGH" 0 0.3 "$(calc "20 * log((0.32 + 0.3 * $k) / 0.62) / log(10)")" "$at" "$(rate "$(calc "1 + 0.25 * $k")")"
    if awk -v g="$gap" 'BEGIN{exit !(g >= 0.2)}'; then
      L "$LATCH" 0 0.38 "$(calc "-4 + 20 * log((0.32 + 0.3 * $k) / 0.62) / log(10)")" "$at" "$(rate 0.85)"
    fi
  done < <(awk 'BEGIN{ s = 13.55; at = 0; gap = 1.0; while (at < s - 0.05) { printf "%.3f %.4f %.4f\n", at, at / s, gap; at += gap; gap *= 0.92; if (gap < 0.09) gap = 0.09 } }')
  L "$TENSION_DRONE" 20 13.55 -4 0 "lowpass=f=2000,volume='0.06+0.94*pow(t/13.55,2.5)':eval=frame"
  L "$HURRICANE" 30 13.55 -6 0 "highpass=f=300,volume='0.03+0.97*pow(t/13.55,3)':eval=frame"
  make halley_countdown 13.55 -18
fi

if want halley_sight; then   # the comet sighted: a cold, airy, shimmering swell from far off
  L "$MAGIC_LIGHT" 0 3.6 0 0 "afade=t=in:d=0.6"
  L "$SHIMMER" 2 3.6 -8 0 "afade=t=in:d=1.0"
  L "$GLASS_SHARDS" 0.1 2.5 -9 0.4 "highpass=f=2500"
  make halley_sight 3.6 -15 "aecho=0.8:0.4:140|330:0.3|0.2,afade=t=out:st=2.6:d=1.0"
fi

if want halley_approach; then   # from t=130 to touchdown (8.5 s): a roar building, wind and an icy whistle rising in it
  L "$BLASTOFF" 0 8.5 0 0
  L "$HURRICANE" 40 8.5 -4 0 "volume='0.15+0.85*pow(t/8.5,2.5)':eval=frame"
  L "$ENERGY_FLOW" 3 8.5 -9 0 "highpass=f=900,volume='pow(t/8.5,4)':eval=frame"
  L "$ICE_FIELD" 30 8.5 -10 0 "highpass=f=1500,volume='pow(t/8.5,3)':eval=frame"
  make halley_approach 8.5 -16 "afade=t=out:st=8.3:d=0.2"
fi

if want halley_boom; then   # ENTRY: the sonic boom, a hard double boom with a thunder clap rolling off
  L "$GORE_SMASH" 0.05 1.6 -3 0 "highpass=f=200"
  L "$BOOM075" $B 5.5 0 0
  L "$BOOM011" $B 5.4 -2 0.17 "highpass=f=40"
  L "$STORM_CRASH" 3.0 5.6 -3 0.02 "lowpass=f=5000"
  make halley_boom 5.63 -13 "volume='exp(-0.3*t)':eval=frame,afade=t=out:st=4.2:d=1.4"
fi

if want halley_touchdown; then   # TOUCHDOWN: a heavy crunching hit, ice crushed, debris thrown
  L "$BOOM214" $B 6.0 0 0
  L "$ICE_CRUSH" 0.1 2.7 0 0
  L "$ICE_SHATTER" 0 2.5 -3 0.02
  L "$DROP_FAST12" 0 2.6 -3 0
  L "$WOOSH_DEBRIS" 0.3 2.0 -5 0.25
  make halley_touchdown 6.13 -12.5 "volume='exp(-0.3*t)':eval=frame,afade=t=out:st=4.6:d=1.5"
fi

if want halley_plough; then   # ploughing the trench (follows the comet): grinding, crunching ice over a low drag
  L "$ICE_FIELD" 34 2.6 0 0
  L "$GRAVEL" 24.5 2.6 -5 0 "lowpass=f=5000"
  L "$METALBOX_DRAG" 30 2.6 0 0 "lowpass=f=250"
  L "$RATTLING" 1.0 2.6 -8 0
  make halley_plough 2.6 -14.5 "afade=t=in:d=0.08,afade=t=out:st=2.2:d=0.4"
fi

if want halley_impact; then   # the impact: a crack, a sub drop under a trailer boom, the ice tearing, a rolling tail
  L "$GORE_SMASH" 0.05 1.7 -3 0
  L "$BOOM011" $B 9.2 0 0
  L "$DROP_SLOW10" 0 7.96 -3 0
  L "$ICE_FISSURE" 0.27 4.7 -2 0
  L "$ICE_SHATTER" 0 2.5 -4 0.03
  L "$WOOSH_DEBRIS" 0.3 2.0 -6 0.5
  L "$THUNDER" 5.0 8.9 -3 0.5 "afade=t=in:d=0.6,lowpass=f=1200"
  make halley_impact 9.43 -11 "volume='exp(-0.18*t)':eval=frame,afade=t=out:st=7.2:d=2.2"
fi

if want halley_frost; then   # frost spreading over the crater: crackling ice
  L "$ICE_SNAP" 0.47 3.2 0 0
  L "$ICE_FIELD" 74.0 3.2 -3 0 "highpass=f=400"
  L "$GLASS_SHARDS" 0.2 2.4 -10 0.3 "highpass=f=3000"
  make halley_frost 3.2 -15.5 "afade=t=out:st=2.6:d=0.6"
fi

if want halley_hum; then   # the Comet Heart's hum, every 5 s (three takes, picked at random): icy, tonal
  L "$METAL_BOWED" 8 6 0 0 "lowpass=f=1800"
  L "$DEATH_DRONE" 3 6 -8 0 "lowpass=f=400"
  L "$SHIMMER" 10 6 -16 0
  make halley_hum 6.0 -19 "afade=t=in:d=0.8,afade=t=out:st=4.6:d=1.4"
  L "$METAL_BOWED" 14 6.0 0 0 "$(rate 0.94),lowpass=f=1600,lowshelf=f=200:g=2"
  L "$DEATH_DRONE" 9 6.4 -8 0 "lowpass=f=400"
  L "$SHIMMER" 20 6.4 -16 0
  make halley_hum_2 6.38 -19 "afade=t=in:d=0.8,afade=t=out:st=5.0:d=1.38"
  L "$METAL_BOWED" 2 6.0 0 0 "$(rate 1.05),lowpass=f=2000,tremolo=f=3:d=0.25"
  L "$DEATH_DRONE" 12 5.71 -8 0 "lowpass=f=400"
  L "$SHIMMER" 30 5.71 -16 0
  make halley_hum_3 5.71 -19 "afade=t=in:d=0.8,afade=t=out:st=4.3:d=1.41"
fi

if want halley_evac; then   # the evac lift: a quick whoosh
  L "$WIND_RUSH" 0 1.0 0 0
  L "$SWISH" 0.15 0.9 -6 0.05 "highpass=f=300"
  make halley_evac 1.0 -16 "afade=t=out:st=0.7:d=0.3"
fi

if want halley_dusk; then   # the sky going dark: a low, soft swell
  L "$BRAAM_CREEPY" 0 4.5 0 0 "$(rate 0.82),lowpass=f=1500"
  L "$DARK_INDUSTRIAL" 2 5.5 -6 0 "lowpass=f=500"
  make halley_dusk 5.5 -17 "afade=t=in:d=1.5,afade=t=out:st=4.3:d=1.2"
fi

if want halley_burst; then   # ice bursting out of the crater (three takes): short icy shatters
  L "$ICE_SHATTER" 0 2.5 0 0
  L "$GLASS_SHARDS" 0.1 2.5 -6 0.05
  L "$ICE_CRUSH" 0.1 2.7 -8 0
  make halley_burst 3.0 -15 "afade=t=out:st=2.3:d=0.7"
  L "$ICE_SNAP" 0.47 3.0 0 0 "$(rate 0.9)"
  L "$GLASS_SHARDS" 0.1 2.5 -8 0.05 "$(rate 0.9)"
  make halley_burst_2 3.33 -15 "afade=t=out:st=2.6:d=0.73"
  L "$ICE_FISSURE" 0.27 2.4 0 0 "$(rate 1.12)"
  L "$ICE_SHATTER" 0 2.5 -4 0 "$(rate 1.12),highshelf=f=3000:g=2"
  make halley_burst_3 2.68 -15 "afade=t=out:st=2.1:d=0.58"
fi

# ======================================================================================================================
# Luna (SA-02)
# ======================================================================================================================
if want luna_uplink; then   # the remote's uplink (PRESS): a sci-fi confirm with a breath of radio
  L "$POWER_BTN" 0 0.78 0 0
  L "$SCIFI_BURST" 0 0.31 -3 0.04
  L "$TWINKLE" 0 1.6 -5 0.12
  L "$RADIO_STATIC" 3 1.5 -14 0.05 "bandpass=f=1800:width_type=h:w=2000"
  make luna_uplink 1.7 -16 "afade=t=out:st=1.2:d=0.5"
fi

if want luna_alarm; then   # EARTH SYSTEM SHUT DOWN (ALARM): a trailer alarm's hit and wail, a system glitch under it
  L "$ALARM_WHOLE" 0 4.0 0 0
  L "$GLITCH_DATA" 0 2.0 -12 0.1 "highpass=f=800"
  make luna_alarm 4.0 -17 "afade=t=out:st=3.4:d=0.6"
fi

if want luna_buildup; then   # ALARM to FALL (2 s): a rumble and a shuddering drone rising, a braam, the crust grinding
  L "$THUNDER" 5.0 2.0 0 0 "lowpass=f=400"
  L "$DEATH_DRONE" 2.0 2.0 -3 0 "tremolo=f=6:d=0.4"
  L "$HORN32" 0 2.0 -6 0 "lowpass=f=1200"
  L "$METAL_SCRAPE" 0.2 1.2 -5 0.3 "$(rate 0.7)"
  make luna_buildup 2.0 -16 "volume='0.08+0.92*pow(t/2.0,1.6)':eval=frame,afade=t=out:st=1.92:d=0.08"
fi

if want luna_crack; then   # BREAK: the moon cracking, ice snapping an octave down, metal stress groaning
  L "$ICE_SNAP" 0.47 1.8 0 0 "$(rate 0.6)"
  L "$GORE_SMASH" 0.05 1.7 -3 0 "$(rate 0.7)"
  L "$DRYICE_SQUEAL" 0 2.0 -10 0.1 "$(rate 0.5),lowpass=f=2000"
  L "$BOOM075" $B 3.0 -8 0 "lowpass=f=300"
  make luna_crack 3.0 -14.5 "afade=t=out:st=2.3:d=0.7"
fi

if want luna_tear; then   # FALL: the moon tearing loose, groaning metal and rock over a sub
  L "$METAL_SCRAPE" 0.15 6.4 0 0
  L "$METAL_BOWED" 6 4.5 -3 0 "$(rate 0.7)"
  L "$ICE_FISSURE" 0.27 3.2 -4 0 "$(rate 0.5)"
  L "$DROP_SLOW07" 0 4.54 -3 0
  make luna_tear 6.43 -13.5 "afade=t=out:st=5.2:d=1.2"
fi

if want luna_fall; then   # FALL to CONTACT (follows the moon): a deep roaring descent, growing
  L "$ENCOUNTER" 20 12.75 0 0 "lowpass=f=3000"
  L "$HURRICANE" 22 12.75 -4 0
  L "$THUNDER" 2 12.75 -5 0 "lowpass=f=500"
  make luna_fall 12.75 -15.5 "volume='0.3+0.7*pow(t/12.75,1.5)':eval=frame,afade=t=in:d=1.0,afade=t=out:st=12.2:d=0.55"
fi

if want luna_entry; then   # ENTRY: the moon hitting the air, a fire whoosh into a boom, burning after
  L "$FIRE_WHOOSH" 0.05 2.4 0 0
  L "$BOOM214" $B 6.5 0 0.35
  L "$EXPL_SMALL" 0 1.48 -4 0.35
  L "$FIRE_LOOP" 2 6.5 -10 0.4 "lowpass=f=3000,afade=t=out:st=3:d=3.5"
  make luna_entry 6.93 -12.5 "volume='if(lt(t,0.4),1,exp(-0.3*(t-0.4)))':eval=frame,afade=t=out:st=5.4:d=1.5"
fi

if want luna_rumble; then   # CONTACT - 60 (3 s): the ground's rumble swelling, then near-silence for its last 0.5 s
  L "$THUNDER" 5 3 0 0 "lowpass=f=800"
  L "$RATTLING" 0.1 3 -4 0
  make luna_rumble 3.0 -15 "volume='if(lt(t,2.35),0.4+0.6*pow(t/2.35,1.7),max(0.06,1-(t-2.35)/0.12))':eval=frame"
fi

if want luna_inhale; then   # 0.65 s ending on CONTACT: the air drawn in, a boom and a rush played backwards
  L "$BOOM011" $B 0.65 0 0 "areverse,highpass=f=250"
  L "$WIND_RUSH" 0 0.65 -2 0 "areverse"
  make luna_inhale 0.65 -17 "volume='pow(t/0.65,2.2)':eval=frame"
fi

if want luna_impact; then   # CONTACT: the biggest hit, two trailer booms on a sub drop, rock cracking, a long rolling tail
  L "$GORE_SMASH" 0.05 1.7 -2 0
  L "$BOOM011" $B 13.5 0 0
  L "$BOOM214" $B 13.5 -3 0.06
  L "$DROP_SLOW10" 0 7.96 -2 0
  L "$ICE_FISSURE" 0.27 3.0 -6 0 "$(rate 0.5)"
  L "$WOOSH_DEBRIS" 0.3 2.0 -5 0.4
  L "$THUNDER" 6 12.6 -2 1.0 "afade=t=in:d=1.0,lowpass=f=900"
  make luna_impact 13.93 -10.5 "volume='exp(-0.12*t)':eval=frame,afade=t=out:st=10.9:d=3.0"
fi

if want luna_shockwave; then   # the shock wave rolling over: a wind blast, a boom's tail, debris, a low pass-by
  L "$HURRICANE" 25 8.4 0 0 "volume='min(1,t/0.12)*exp(-0.3*t)':eval=frame"
  L "$BOOM075" $B 8.2 -3 0 "lowpass=f=800"
  L "$WOOSH_DEBRIS" 0.1 2.2 -4 0.05
  L "$TRAIN_ELEC" 17 4.5 -6 0 "$(rate 0.6),lowpass=f=1500"
  make luna_shockwave 8.43 -13.5 "afade=t=out:st=6.5:d=1.9"
fi

if want luna_settle; then   # CONTACT + 5: the moon grinding in, groaning
  L "$METAL_SCRAPE" 2.0 3.6 0 0
  L "$METAL_BOWED" 9.9 2.2 -4 0 "$(rate 0.6)"
  L "$METALBOX_DRAG" 13 3.6 0 0 "lowpass=f=300"
  L "$GRAVEL" 3 2.6 -8 0 "$(rate 0.7),lowpass=f=1500"
  make luna_settle 3.6 -15 "afade=t=in:d=0.15,afade=t=out:st=2.8:d=0.8"
fi

if want luna_aftershock; then   # aftershocks (two takes): deep rumbles
  L "$THUNDER" 9.0 4.5 0 0 "lowpass=f=700"
  L "$RATTLING" 0.1 4.5 -4 0
  make luna_aftershock 4.5 -16 "afade=t=in:d=0.3,afade=t=out:st=3.4:d=1.1"
  L "$STORM_CRASH" 5.5 5.11 0 0 "lowpass=f=500"
  L "$RATTLING" 0.1 4.0 -5 0.3 "$(rate 0.8)"
  make luna_aftershock_2 5.11 -16 "afade=t=in:d=0.4,afade=t=out:st=3.9:d=1.21"
fi

if want luna_sizzle; then   # the molten seam: hissing steam, spitting, crackling
  L "$FIRE_OUT" 0.3 3.2 0 0
  L "$FIRE_LOOP" 2.0 3.2 -5 0
  L "$CAMPFIRE" 12 3.2 -2 0
  make luna_sizzle 3.2 -17 "afade=t=in:d=0.1,afade=t=out:st=2.6:d=0.6"
fi

if want luna_debris; then
  # falling debris (three takes, every 5.5 s): a debris whoosh, gravel settling, then twenty small knocks thinning out
  # (books, a case, wooden pieces, a rattle, punches, all pitched down to rocks), placed by a seeded random
  POOL=("$BOOKS" "$BOOKS" "$CASE_DOWN" "$BOARD" "$ARROW_RATTLE" "$PUNCH")
  POOL_AT=(0.85 1.5 0 0 0.25 0.1)
  POOL_LEN=(0.55 0.6 0.6 0.9 0.8 0.5)
  debris() {
    L "$WOOSH_DEBRIS" 0.3 2.0 -3 0
    L "$GRAVEL" 10 "$2" -12 0 "lowpass=f=2500,volume='exp(-0.3*t)':eval=frame"
    while read -r i at k g; do
      L "${POOL[$i]}" "${POOL_AT[$i]}" "${POOL_LEN[$i]}" "$g" "$at" "$(rate "$k"),lowpass=f=4000"
    done < <(awk -v n=20 -v len="$2" -v seed="$3" -v m=${#POOL[@]} 'BEGIN{ srand(seed); for (j = 0; j < n; j++) {
      at = 0.1 + (len - 1.5) * (j / n) ^ 1.5 + rand() * 0.15
      printf "%d %.3f %.3f %.1f\n", int(rand() * m), at, 0.55 + rand() * 0.4, -1 - 9 * at / len - rand() * 4 } }')
    make "$1" "$2" -18 "afade=t=out:st=$(calc "$2 - 1.2"):d=1.2"
  }
  debris luna_debris 8.0 11
  debris luna_debris_2 8.89 23
  debris luna_debris_3 7.47 37
fi

# Proxima (SA-04): all of its sounds are made by art/make_proxima_film.sh since 2026-11-07 (its film soundtrack, and the
# land's world sounds cut from it).

# ---- the source list (only when every sound was made, so it always covers all of them) -------------------------------
if [ ${#NAMES[@]} -eq 0 ]; then
  mkdir -p "$HERE/sounds"
  {
    echo "# Which Sonniss file went into which sound"
    echo
    echo "Written by \`art/sonniss_sounds.sh\` (don't edit by hand; change the recipe and re-run it). Every source is"
    echo "from the Sonniss GDC 2026 game-audio bundle (in the project folder's \`sound library\\sonniss gdc 2026\\files\\\`,"
    echo "\`<library>/<file>\`). From/Length: the cut in the source, in seconds. Gain: dB against the other layers (each cut"
    echo "is first levelled to -20 LUFS). At: where the layer starts in our sound. Processing: ffmpeg filters on the layer"
    echo "(\`asetrate\` = pitch and speed together, \`areverse\` = backwards). The mix is then set to its level by role."
    echo
    echo "| Sound | Source | From | Length | Gain | At | Processing |"
    echo "|---|---|---|---|---|---|---|"
    cat "$TMP/sources"
  } > "$HERE/sounds/sources.md"
  echo "wrote art/sounds/sources.md"
fi

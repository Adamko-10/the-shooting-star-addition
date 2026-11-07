#!/usr/bin/env bash
# SA-04 Proxima's film soundtrack (2026-11-07; the user: the film's sounds were "wrong for the shot", "cheap", "it feels
# empty like there's silence", "overhaul the whole thing"). One continuous stereo track for the caster's whole film
# (proxima_film.ogg, 31.5 s, time 0 = the cast; played by client/proxima/ProximaFx through client/StrikeMusic, kept in
# step with the film like the music), laid out shot by shot on the film's own timeline (ProximaPlan, docs/SS-07-PROXIMA.md
# "The new film"): a bed that never drops out, a whoosh or swell into every cut, a hit on every title card, servos and
# clamps for the Dyson sphere's panels, the lens charging and firing, the gate catching the beam, then the land. Its land
# part is also cut into the world sounds everyone near the strike hears (proxima_flash, _front, _inhale, _impact, _wave,
# mono). Layers from the Sonniss GDC 2026 bundle (as art/sonniss_sounds.sh; only our mixes ship) plus a few synthesised
# ones (the lens's charging whine, the radio burst, the flash's ringing).
# The film's moments, in seconds (ticks / 20): press 0.35, the pull up 1.6-2.95 (white punch 2.65-3.0), Earth from orbit
# 2.95-5.4, Proxima Centauri 5.4 (its title 5.7), the Dyson sphere 7.8 (title 8.15, the iris opening 8.05-9.75), the
# loops 10.0 (snap 10.85-10.95), the lens 11.4 (tiles gathering to 12.4, charging 12.1, title 11.55), the beam 12.45 (shock
# rings 12.45, 12.63), Earth and the gate 13.0 (panels 13.1-13.4, opening 13.35-13.95, the beam arriving 13.75, the
# plasma spreading 14.05-14.95), the flash 15.0 (title 15.25), the front 16.8, the hush 22.4, the impact 23.0, the shock
# wave 24.6, afterwards 27.6, the end 31.0.
#
# Usage: bash art/make_proxima_film.sh [out dir]   (default: the mod's sounds folder)
# Needs ffmpeg (with libvorbis) and awk. SONNISS=<the bundle's files folder> to point elsewhere.
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
LIB="${SONNISS:-$HERE/../../sound library/sonniss gdc 2026/files}"
OUT=${1:-"$HERE/../src/main/resources/assets/shooting_star_addition/sounds"}
mkdir -p "$OUT"
[ -d "$LIB" ] || { echo "no Sonniss bundle at $LIB (set SONNISS)" >&2; exit 1; }
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
SR=44100
LEN=31.5

ff() { ffmpeg -hide_banner -loglevel error -y "$@"; }
loudness() { ffmpeg -hide_banner -nostats -i "$1" -af ebur128 -f null - 2>&1 | awk '/^ *I:/{v=$2} END{print v}'; }
# rate <k>: tape-style pitch and speed by k (0.5 = an octave down and twice as long)
rate() { awk -v k="$1" -v r=$SR 'BEGIN{printf "asetrate=%d,aresample=%d", r * k + 0.5, r}'; }
# pan <-1..1>: placed left or right, keeping its loudness
pan() { awk -v p="$1" 'BEGIN{l = sqrt((1 - p) / 2) * 1.414; r = sqrt((1 + p) / 2) * 1.414; printf "pan=stereo|c0=%.3f*c0+%.3f*c1|c1=%.3f*c0+%.3f*c1", l / 2, l / 2, r / 2, r / 2}'; }
# up <seconds>: a swell, from nothing to full over the layer's length (a curve: slow, then fast)
up() { echo "volume='pow(min(t/$1,1),2.2)':eval=frame"; }
# rise <seconds>: a quarter at first, rising to full
rise() { echo "volume='0.25+0.75*pow(min(t/$1,1),1.5)':eval=frame"; }

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

# L <source> <from s> <length s> <gain dB> <at s> [filters]: one layer. The cut is levelled to -20 LUFS, faded in and
# out by a few ms, then the filters, the gain, and it starts <at> seconds into the film.
LAYERS=()
L() { LAYERS+=("$1~$2~$3~$4~$5~${6:-}"); }
# synth <name> <seconds> <expression of t>: a sound made here (aevalsrc), usable as a source by its path "$TMP/<name>.wav"
synth() { ff -f lavfi -i "aevalsrc='$3':s=$SR:d=$2" -c:a pcm_f32le "$TMP/$1.wav"; }

# ---- The sources (paths in the bundle's files folder) -----------------------------------------------------------------
BLASTOFF="344 Audio - Air Designed/AEROJet_Blast Off Clean_344 Audio_Air Designed.wav"
UFO="344 Audio - Air Designed/AEROJet_Unidentified Encounter_344 Audio_Air Designed.wav"
DROP_SLOW="344 Audio - Bass Drops & Downers Vol. 1/DSGNBass_Bass Drop & Downer Slow 10_344 Audio_Bass Drops & Downers.wav"
DROP_MED="344 Audio - Bass Drops & Downers Vol. 1/DSGNBass_Bass Drop & Downer Medium 04_344 Audio_Bass Drops & Downers.wav"
DROP_FAST="344 Audio - Bass Drops & Downers Vol. 1/DSGNBass_Bass Drop & Downer Fast 16_344 Audio_Bass Drops & Downers.wav"
JUMPDROP="344 Audio - Bass Drops & Downers Vol. 3/DSGNBass_Jump Start Drop 3_344 Audio_Bass Drops and Downers Vol 3.wav"
RATTLEDOWN="344 Audio - Bass Drops & Downers Vol. 3/DSGNBass_Rattling Downer 3_344 Audio_Bass Drops and Downers Vol 3.wav"
TONEDOWN="344 Audio - Bass Drops & Downers Vol. 3/DSGNBass_Tone Downer (Reverb)_344 Audio_Bass Drops and Downers Vol 3.wav"
WINDRUSH="344 Audio - Elemental Palette Designed Vol. 1/WINDDsgn_Wind, Rush, Whoosh, Long x5 01_344 Audio_Elemental Palette Designed Vol 1.wav"
METALPASS="344 Audio - Elemental Palette Designed Vol. 1/METLMisc_Metal, Slow Whoosh, Rattle, Pass By x4 01_344 Audio_Elemental Palette Designed Vol 1.wav"
STORM="344 Audio - Extreme Winds Vol. 1/WINDDsgn_EXT, Eye Of The Storm_344 Audio_Extreme Winds Vol 1.wav"
CAULDRON="344 Audio - Haunting Ambiences Vol. 5/FIRECrkl_Fire Crackling, Popping, Witch's Cauldron_344 Audio_Haunting Ambiences Vol 5.wav"
ASTRAL="Alexander Kopeikin - Emotion and Magic/magic, energy flow, astral travel-015.wav"
SPELLBOUND="Alexander Kopeikin - Emotion and Magic/magic, drone, tension, spellbound, evanescence-002.wav"
CUTSWEEP="Cinematic Sound Design - Colossal Impacts/Impact Cut Sweep.wav"
DEBRIS="Cinematic Sound Design - Colossal Impacts/Woosh Debris.wav"
INDUSTRIAL="Cinematic Sound Design - Sci-Fi Drones/Dark Industrial Ambience.wav"
SHIPROOM="Cinematic Sound Design - Sci-Fi Drones/Roomtone Space Ship Interior Muted.wav"
PINGDOWN="Cinematic Sound Design - System & UI Feedback Elements/Interface Sci-Fi Ping Down.wav"
READOUT="Cinematic Sound Design - User Interface/Readout Thin Long.wav"
CHORD="Cinematic Sound Design - Ultra Transitions & Impacts/Impact Hit Rapid Chord Reverb.wav"
SWEEPSLIDE="Cinematic Sound Design - Ultra Transitions & Impacts/Woosh Sweep Slide Infographics Basic.wav"
LATCH="Epic Stock Media - HD Lock And Mechanism Sound Design Kit/MECHLtch_Click Deep Mechanism Latch Button Nearfield Thunk 02_ESM_HDLM.wav"
ELECHIT="Epic Stock Media - Elemental Mutation Whooshes and Impacts/ELECMisc_Impact Electric Tonal Deep Movement Motion Hiss Glitch 01_ESM_EMWI.wav"
FIREWHOOSH="Epic Stock Media - Elemental Mutation Whooshes and Impacts/FIREWhsh_Whoosh Fire Deep Growl Monster Saturated Crisp 03_ESM_EMWI.wav"
GLASSWHOOSH="Epic Stock Media - Elemental Mutation Whooshes and Impacts/GLASMvmt_Whoosh Glass Crystal Fragments Sharp Shards Dry 05_ESM_EMWI.wav"
SWELL="Epic Stock Media - Halloween Game - Haunted House and Horror Audio Scare Kit/UIAlert_Alert Designed Transition Swell Creature Strange Alien Layered 01_ESM_HALG.wav"
THUNDER="Epic Stock Media - Public Spaces - Storms Lakes Parks and Rural Nature Exteriors/STORM_Texas Rain Thunder Initial Crash Boom Storm 01 Clap Lightning_ESM_CPS.wav"
FACTORY="Epic Stock Media - Strange Game Ambient Loops 3/AMBRoom_Factory Loop Heavy Machinery Tonal Roomtone Dark Wind Vent_ESM_SGA3.wav"
REACTOR="Epic Stock Media - Strange Game Ambient Loops 3/DSGNSynth_Scifi Loop Ship Reactor Synth Layer Layered Saw Buzz_ESM_SGA3.wav"
FIRELOOP="Epic Stock Media - Synthesized Nature Loops and Sounds/FIREBurn_Loop Elements Fire Crackling Crunchy Flame Burn 03_ESM_SNLS.wav"
SERVO="Epic Stock Media - Tower Defense Game/ROBTMvmt_Tower Deploy Hitech Robot Motor Dark Thump Servo Whine 04_ESM_TDG.wav"
BOOM_A="Federico Soler - Effective Trailer Booms Vol. 2/EffectiveTrailer_Booms_Vol2_011.wav"
BOOM_B="Federico Soler - Effective Trailer Booms Vol. 2/EffectiveTrailer_Booms_Vol2_075.wav"
BOOM_C="Federico Soler - Effective Trailer Booms Vol. 2/EffectiveTrailer_Booms_Vol2_214.wav"
ARC="InMotionAudio - Arc/ELECArc_ArcDesign15_InMotionAudio_Arc.wav"
ARCUP="InMotionAudio - Arc/ELECArc_ArcPowerUpDesign04_InMotionAudio_Arc.wav"
BUZZ="InMotionAudio - Arc/ELECBuzz_Buzz27_InMotionAudio_Arc.wav"
FIREWORKS="Ivo Vicic - Fireworks FX/13 Fireworks_powerful explosions_multiples in a row_near.wav"
CAMPFIRE="Ivo Vicic - Campfire - Bonfire FX/24 Campfire, Dropping Fresh Pine Branches in Fire, Crackling, Sizzling Strong, Close 02.wav"
BRAAM_A="Jake Fielding - Cinematic Horn Braams/DSGNBram____Cinematic Horn Braam, Epic, Cinematic, Dark, Instrument, Huge-32.wav"
BRAAM_B="Jake Fielding - Cinematic Horn Braams/DSGNBram____Cinematic Horn Braam, Epic, Cinematic, Dark, Instrument, Huge-67.wav"
RUMBLE="Jake Fielding - Interior Wind Rain and Storms/THUN_Interior Thunder Rumble_JF_INT Storm_01.wav"
AMRADIO="Sonic Bat - Vintage Radio/SBvr_AM EQ Sweeping 002.wav"
METALTHUD="The Noisery - Moaning Metal/DSGNImpt_Metal Hit Thud Thump Low Ring Geofon 1_The Noisery_Moaning Metal.wav"
METALBOW="The Noisery - Moaning Metal/DSGNTonl_Designed Metal Bowed Screech Tonal Reverb 7_The Noisery_Moaning Metal.wav"
METALSCRAPE="The Noisery - Moaning Metal/DSGNTonl_Metal Scrape Low Tonal LFE 4_The Noisery_Moaning Metal.wav"
GUSTS="The Noisery - City Rain/WINDTonl_Wind Strong Gusts Hurricane Vents Rattle 06_The Noisery_City Rain.wav"

# synthesised: the lens charging (a whine rising from 180 Hz to ~2 kHz, with a growl an octave down), the radio burst (a
# whine falling from 2.4 kHz to 260 Hz, chopped, as a CME's type II burst drifts down), the ringing after the flash
synth charge 0.85 "0.5*sin(2*PI*180*(exp(2.85*t)-1)/2.85)+0.3*sin(PI*180*(exp(2.85*t)-1)/2.85)+0.08*(random(0)-0.5)"
synth radio 5.6 "(0.5*sin(2*PI*(2400*5.6/2.22)*(1-exp(-2.22*t/5.6)))+0.25*(random(0)-0.5))*(0.6+0.4*sin(2*PI*7*t))*(0.7+0.3*sin(2*PI*1.3*t))"
synth ring 2.6 "0.4*sin(2*PI*3150*t)*exp(-1.6*t)+0.2*sin(2*PI*4720*t)*exp(-2.4*t)"
CHARGE="$TMP/charge.wav"
RADIO="$TMP/radio.wav"
RING="$TMP/ring.wav"

# ---- 1. The press and the uplink (0-1.6) ------------------------------------------------------------------------------
L "$PINGDOWN" 0 0.8 -5 0.35 "highpass=f=300"
L "$READOUT" 0 1.5 -7 0.45 "highpass=f=500,afade=t=out:st=1.0:d=0.5"
L "$REACTOR" 5 2.0 -7 0.1 "lowpass=f=2400,afade=t=in:d=0.4,afade=t=out:st=1.3:d=0.7"
L "$BUZZ" 1 1.5 -17 0.2 "highpass=f=1500,afade=t=in:d=0.2,afade=t=out:st=1.0:d=0.5,$(pan -0.4)"

# ---- 2. The pull up, Railgun-paced, through the clouds (1.6-2.95), the white punch, the cut to orbit ----------------
L "$BLASTOFF" 4 1.38 2 1.57 "$(rise 1.38),highpass=f=40"
L "$STORM" 30 1.38 -1 1.57 "$(rise 1.38),highpass=f=250"
L "$WINDRUSH" 0 1.0 -6 1.95 "$(rate 0.85),$(up 1.0)"
L "$CUTSWEEP" 0 1.0 -5 1.95 "highpass=f=120"
L "$DEBRIS" 0.25 1.3 -4 2.88 "$(pan 0.2)"
L "$DROP_FAST" 0 2.0 -5 2.9
L "$FIREWHOOSH" 0.4 1.0 -11 2.85 "$(rate 0.6),lowpass=f=1200"

# ---- 3. Earth from orbit (2.95-5.4); the space bed runs on to the flash -----------------------------------------------
L "$INDUSTRIAL" 4 12.6 -11 2.92 "$(rate 0.8),lowpass=f=700,afade=t=in:d=0.5,afade=t=out:st=11.6:d=1.0"
L "$SHIPROOM" 12 12.4 -17 3.0 "lowpass=f=1200,afade=t=in:d=0.8,afade=t=out:st=11.4:d=1.0"
L "$ASTRAL" 0 2.6 -4 2.95 "afade=t=in:d=0.3,afade=t=out:st=2.0:d=0.6"
L "$SPELLBOUND" 0 2.5 -8 3.0 "afade=t=in:d=0.8,$(up 2.5)"
L "$AMRADIO" 20 2.3 -19 3.1 "highpass=f=400,lowpass=f=3200,afade=t=in:d=0.4,afade=t=out:st=1.5:d=0.8,$(pan 0.5)"
L "$GLASSWHOOSH" 0 1.5 -12 3.9 "lowpass=f=7000"

# ---- 4. Proxima Centauri (5.4-7.8): the star's boiling roar (to the cut to Earth), its title card -------------------
L "$CAULDRON" 30 7.6 -11 5.4 "$(rate 0.45),lowpass=f=1500,afade=t=in:d=0.25,afade=t=out:st=7.1:d=0.5"
L "$STORM" 90 7.6 -12 5.4 "$(rate 0.6),lowpass=f=900,afade=t=in:d=0.25,afade=t=out:st=7.1:d=0.5"
L "$FIREWHOOSH" 0 2.4 -6 5.38 "$(rate 0.5),lowpass=f=2500"
L "$BOOM_C" 0.15 4.2 -1 5.6 "afade=t=out:st=2.4:d=1.8"
L "$BRAAM_B" 0 3.8 -6 5.66 "afade=t=out:st=2.2:d=1.6"
L "$METALSCRAPE" 0 2.4 -10 6.4 "$(pan -0.3)"
L "$METALBOW" 2 2.6 -15 6.9 "lowpass=f=3000,afade=t=in:d=0.4,afade=t=out:st=1.8:d=0.8,$(pan 0.4)"

# ---- 5. The Dyson sphere and its iris (7.8-10.0): title card 8.15, the iris opening ring by ring 8.05-9.75 ----------
L "$METALPASS" 0 1.2 -8 7.55
L "$BRAAM_A" 0 3.6 -6 8.05 "afade=t=out:st=2.0:d=1.6"
L "$BOOM_B" 0.15 3.2 -6 8.0 "afade=t=out:st=1.6:d=1.6"
L "$FACTORY" 10 2.4 -13 7.85 "afade=t=in:d=0.3,afade=t=out:st=1.6:d=0.8"
L "$SERVO" 0 0.9 -8 8.1 "$(rate 0.6),$(pan -0.5)"
L "$SERVO" 0 0.9 -8 8.5 "$(rate 0.55),$(pan 0.5)"
L "$SERVO" 0 0.9 -8 8.9 "$(rate 0.5),$(pan -0.3)"
L "$SERVO" 0 0.9 -8 9.3 "$(rate 0.45),$(pan 0.3)"
L "$LATCH" 0 0.38 -3 8.55 "$(rate 0.5),$(pan -0.5)"
L "$LATCH" 0 0.38 -3 8.95 "$(rate 0.5),$(pan 0.5)"
L "$LATCH" 0 0.38 -3 9.35 "$(rate 0.45),$(pan -0.3)"
L "$METALTHUD" 0 2.0 -5 9.72 "afade=t=out:st=1.2:d=0.8"
L "$SWEEPSLIDE" 0 0.5 -15 8.2 "$(rate 0.5)"
L "$SWEEPSLIDE" 0 0.5 -15 9.0 "$(rate 0.45)"

# ---- 6. The loops (10.0-11.4): crackling, rising to the snap where they cross (10.85-10.95) ------------------------------
L "$ARCUP" 0 0.95 -8 10.0 "$(up 0.95)"
L "$BUZZ" 3 0.95 -11 10.0 "highpass=f=800,$(up 0.95)"
L "$SWELL" 5.2 0.9 -10 10.0 "areverse,highpass=f=200"
L "$ARC" 0 1.0 1 10.85
L "$ELECHIT" 0 2.0 0 10.88 "afade=t=out:st=1.2:d=0.8"
L "$DROP_FAST" 0 2.0 -6 10.9
L "$THUNDER" 3.0 1.6 -2 10.84 "lowpass=f=5000"
L "$CHORD" 0 2.5 -11 10.95 "highpass=f=200"

# ---- 7. The lens (11.4-12.45): tiles flying in and locking, faster and faster, the charge, its title 11.55 ------------
L "$SWEEPSLIDE" 0 0.5 -10 11.35 "$(rate 0.7)"
L "$METALPASS" 0 1.2 -6 11.4 "$(rate 0.9),$(pan -0.4)"
L "$METALPASS" 0 1.2 -7 11.75 "$(rate 1.1),$(pan 0.4)"
L "$SERVO" 0 0.9 -10 11.45 "$(rate 0.8)"
L "$LATCH" 0 0.38 -6 11.66 "$(rate 0.7),$(pan -0.6)"
L "$LATCH" 0 0.38 -6 11.82 "$(rate 0.75),$(pan 0.6)"
L "$LATCH" 0 0.38 -7 11.95 "$(rate 0.8),$(pan -0.3)"
L "$LATCH" 0 0.38 -7 12.05 "$(rate 0.85),$(pan 0.3)"
L "$LATCH" 0 0.38 -8 12.13 "$(rate 0.9),$(pan -0.5)"
L "$LATCH" 0 0.38 -8 12.2 "$(rate 0.95),$(pan 0.5)"
L "$LATCH" 0 0.38 -9 12.26 "$(rate 1.0)"
L "$CHORD" 0 2.0 -9 11.5 "highpass=f=150,afade=t=out:st=1.0:d=0.8"
L "$CHARGE" 0 0.85 -7 11.6 "$(up 0.85),lowpass=f=6000"
L "$ARCUP" 1 0.85 -9 11.6 "$(up 0.85)"
L "$CUTSWEEP" 0 1.0 -4 11.45 "highpass=f=100"

# ---- 8. The beam (12.45-13.0): the discharge, its roar, two shock rings ------------------------------------------------
L "$BOOM_C" 0.2 3.0 3 12.45 "afade=t=out:st=1.5:d=1.5"
L "$ELECHIT" 0 2.0 -2 12.43
L "$FIREWORKS" 9.05 1.2 -6 12.4 "lowpass=f=6000"
L "$JUMPDROP" 0 2.5 -5 12.45 "afade=t=out:st=1.5:d=1.0"
L "$BLASTOFF" 6 0.6 -3 12.45 "afade=t=out:st=0.5:d=0.1"
L "$BUZZ" 0 0.6 -8 12.45 "highpass=f=600,afade=t=out:st=0.5:d=0.1"
L "$DEBRIS" 0.2 1.0 -9 12.45 "$(pan -0.5)"
L "$DEBRIS" 0.2 1.0 -10 12.63 "$(pan 0.5)"

# ---- 9. Earth and the gate (13.0-15.0): the beam coming in, the gate unfolding and locking, catching it (13.75), the
# plasma spreading toward Earth, a swell into the flash ------------------------------------------------------------------
L "$UFO" 10 2.2 -10 13.0 "lowpass=f=3000,afade=t=in:d=0.15,afade=t=out:st=1.4:d=0.8"
L "$BLASTOFF" 7 0.8 -12 13.0 "lowpass=f=1500,afade=t=out:st=0.3:d=0.5"
L "$SERVO" 0 0.9 -10 13.1 "$(rate 0.7),$(pan -0.4)"
L "$SERVO" 0 0.9 -10 13.28 "$(rate 0.65),$(pan 0.4)"
L "$LATCH" 0 0.38 -6 13.6 "$(rate 0.6),$(pan -0.3)"
L "$LATCH" 0 0.38 -6 13.78 "$(rate 0.55),$(pan 0.3)"
L "$METALTHUD" 0 1.6 -7 13.92 "afade=t=out:st=0.8:d=0.8"
L "$ELECHIT" 0 1.8 -3 13.73
L "$DROP_MED" 0 2.0 -7 13.75 "afade=t=out:st=1.2:d=0.8"
L "$THUNDER" 5.8 1.6 -8 13.72 "lowpass=f=4000"
L "$BUZZ" 4 1.2 -11 13.8 "highpass=f=500,afade=t=out:st=0.8:d=0.4"
L "$FIREWHOOSH" 0 1.6 -7 13.95 "$(rate 0.7)"
L "$STORM" 50 1.0 -8 14.0 "highpass=f=200,$(up 1.0)"
L "$CUTSWEEP" 0 1.0 -4 14.0 "highpass=f=100"

# ---- 10. The flash (15.0): a searing burst, the title card 15.25, ringing ears, the land smoking -----------------------
L "$BOOM_A" 0.2 3.0 3 14.95 "afade=t=out:st=1.0:d=2.0"
L "$SWELL" 0 2.2 -5 15.0 "highpass=f=500,afade=t=out:st=1.0:d=1.2"
L "$RING" 0 2.6 -17 15.05
L "$BRAAM_A" 0 2.6 -8 15.15 "afade=t=out:st=1.2:d=1.4"
L "$CAULDRON" 60 6.0 -17 15.2 "afade=t=in:d=0.5,afade=t=out:st=1.2:d=0.4"
L "$FIRELOOP" 0 1.8 -17 15.4 "afade=t=in:d=0.4,afade=t=out:st=1.2:d=0.6"

# ---- 11. The coronal front (16.8-22.4): the radio burst whining down, a roar building across the sky, sparks, then cut
# dead for the hush --------------------------------------------------------------------------------------------------------
L "$RADIO" 0 5.6 -6 16.8 "highpass=f=200,lowpass=f=5000,afade=t=in:d=0.5"
L "$AMRADIO" 40 5.6 -11 16.8 "highpass=f=300,lowpass=f=4000,afade=t=in:d=0.6"
L "$STORM" 100 5.6 1 16.8 "$(rise 5.6),lowpass=f=2500"
L "$BOOM_B" 6.0 5.6 -5 16.8 "$(rise 5.6)"
L "$RUMBLE" 0 5.4 -4 17.0 "$(rise 5.4)"
L "$GUSTS" 20 5.6 -5 16.8 "$(rise 5.6)"
L "$ARC" 0 1.0 -13 18.2 "$(rate 1.2),$(pan -0.6)"
L "$ARC" 0 1.0 -13 19.6 "$(rate 1.1),$(pan 0.6)"
L "$ARC" 0 1.0 -12 20.9 "$(rate 1.3),$(pan -0.2)"
L "$DEBRIS" 0.1 1.6 -6 19.1
L "$CUTSWEEP" 0 1.0 -6 21.35 "highpass=f=100"

# ---- 12. The hush (22.4-23.0): near silence, a low drone, the air drawn in, ending on the impact ------------------------
L "$TONEDOWN" 1.0 0.6 -18 22.4 "lowpass=f=200"
L "$FIREWHOOSH" 0 0.65 -13 22.35 "areverse,$(up 0.65)"
L "$BOOM_A" 0.25 0.6 -14 22.4 "areverse,$(up 0.6)"

# ---- 13. The impact (23.0) and the shock wave across the land (24.6-27.6) ------------------------------------------------
L "$BOOM_C" 0.2 7.0 5 22.97 "afade=t=out:st=3.5:d=3.5"
L "$BRAAM_B" 0 4.0 -5 22.95 "afade=t=out:st=2.0:d=2.0"
L "$FIREWORKS" 1.35 1.5 1 22.98 "lowpass=f=7000"
L "$THUNDER" 3.0 4.0 -2 22.95 "afade=t=out:st=2.5:d=1.5"
L "$DROP_SLOW" 0 6.0 0 23.0 "afade=t=out:st=4.0:d=2.0"
L "$RATTLEDOWN" 0 4.0 -7 23.0 "afade=t=out:st=2.5:d=1.5"
L "$ELECHIT" 0 2.5 -6 23.0
L "$DEBRIS" 0.1 2.0 -4 23.1
L "$STORM" 140 3.6 -6 24.4 "afade=t=in:d=0.3,afade=t=out:st=2.6:d=1.0"
L "$DEBRIS" 0 2.2 -6 24.6 "$(rate 0.8),$(pan -0.4)"
L "$DEBRIS" 0 2.2 -9 25.4 "$(rate 0.9),$(pan 0.4)"
L "$BOOM_B" 0.2 4.0 -8 24.55 "afade=t=out:st=2.0:d=2.0"

# ---- 14. Afterwards (27.6-31.0): fire, wind and far rumbles, fading -----------------------------------------------------
L "$CAMPFIRE" 5 3.8 -12 27.2 "afade=t=in:d=0.8,afade=t=out:st=2.4:d=1.4"
L "$GUSTS" 60 4.0 -13 27.0 "afade=t=in:d=1.0,afade=t=out:st=2.6:d=1.4"
L "$RUMBLE" 8 3.5 -12 27.6 "afade=t=out:st=2.0:d=1.5"

# ---- The mix ----------------------------------------------------------------------------------------------------------
# each distinct cut is one input, split to every layer that uses it; the graph goes through a file (Windows' command line
# is too short for it otherwise)
args=(); graph=""; layers=""; labels=""; n=0; m=0
declare -A input uses
for spec in "${LAYERS[@]}"; do
  IFS='~' read -r file start dur gain at filt <<< "$spec"
  case $file in /*|?:*) path=$file ;; *) path="$LIB/$file" ;; esac
  [ -f "$path" ] || { echo "no source $path" >&2; exit 1; }
  command -v cygpath >/dev/null && path=$(cygpath -w "$path")
  key="$path|$start|$dur"
  if [ -z "${input[$key]:-}" ]; then
    input[$key]=$m; uses[$m]=0; m=$((m + 1))
    args+=(-ss "$start" -t "$dur" -i "$path")
  fi
  i=${input[$key]}; u=${uses[$i]}; uses[$i]=$((u + 1))
  level "$path" "$start" "$dur"
  norm=$(awk -v l="$LEVEL" -v g="$gain" 'BEGIN{printf "%.2f", -20 - l + g}')
  fadeout=$(awk -v d="$dur" 'BEGIN{printf "%.3f", d - 0.012}')
  delay=$(awk -v a="$at" 'BEGIN{printf "%d", a * 1000 + 0.5}')
  layers+="[c${i}_$u]afade=t=in:d=0.004,afade=t=out:st=$fadeout:d=0.012${filt:+,$filt},volume=${norm}dB,aformat=channel_layouts=stereo,adelay=delays=$delay:all=1[l$n];"
  labels+="[l$n]"; n=$((n + 1))
done
for ((i = 0; i < m; i++)); do
  splits=""; for ((u = 0; u < uses[$i]; u++)); do splits+="[c${i}_$u]"; done
  graph+="[$i:a]aformat=sample_fmts=fltp:channel_layouts=stereo,aresample=$SR,asetpts=N/SR/TB,asplit=${uses[$i]}$splits;"
done
end=$(awk -v l="$LEN" -v r=$SR 'BEGIN{printf "%d", l * r}')
graph+="$layers${labels}amix=inputs=$n:normalize=0:duration=longest,asetpts=N/SR/TB,highpass=f=25,"
graph+="apad=whole_dur=$LEN,atrim=end_sample=$end[out]"
printf '%s' "$graph" > "$TMP/graph.txt"
ff "${args[@]}" -/filter_complex "$TMP/graph.txt" -map "[out]" -c:a pcm_f32le "$TMP/mix.wav"

# to its level (-16 LUFS over the film; its hits stand ~10 dB over its beds), peaks held under -1 dB
gain=$(awk -v i="$(loudness "$TMP/mix.wav")" 'BEGIN{printf "%.2f", -16 - i}')
ff -i "$TMP/mix.wav" -af "volume=${gain}dB,alimiter=limit=0.89:attack=3:release=80:level=0" -c:a pcm_f32le "$TMP/film.wav"
ff -i "$TMP/film.wav" -ar $SR -c:a libvorbis -q:a 5 "$OUT/proxima_film.ogg"
printf '%-16s %6s LUFS  %ss  (%d layers, %d cuts)\n' proxima_film "$(loudness "$OUT/proxima_film.ogg")" "$LEN" "$n" "$m"

# the land's world sounds, heard by everyone near (mono, placed in the world): cut from the film's land part
# cut <name> <from s> <length s> <LUFS> [filters]
cut() {
  ff -ss "$2" -t "$3" -i "$TMP/film.wav" -af "aformat=channel_layouts=mono${5:+,$5}" -c:a pcm_f32le "$TMP/$1.wav"
  local g; g=$(awk -v t="$4" -v i="$(loudness "$TMP/$1.wav")" 'BEGIN{printf "%.2f", t - i}')
  ff -i "$TMP/$1.wav" -af "volume=${g}dB,alimiter=limit=0.89:attack=3:release=80:level=0" -ar $SR -c:a libvorbis -q:a 5 "$OUT/$1.ogg"
  printf '%-16s %6s LUFS  %ss\n' "$1" "$(loudness "$OUT/$1.ogg")" "$3"
}
cut proxima_flash 14.95 2.6 -14 "afade=t=out:st=2.0:d=0.6"
cut proxima_front 16.8 5.6 -15
cut proxima_inhale 22.35 0.65 -17
cut proxima_impact 22.95 6.8 -12.5 "afade=t=out:st=5.2:d=1.6"
cut proxima_wave 24.45 3.5 -15 "afade=t=in:d=0.15,afade=t=out:st=2.9:d=0.6"

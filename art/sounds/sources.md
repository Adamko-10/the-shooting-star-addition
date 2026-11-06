# Which Sonniss file went into which sound

Written by `art/sonniss_sounds.sh` (don't edit by hand; change the recipe and re-run it). Every source is
from the Sonniss GDC 2026 game-audio bundle (in the project folder's `sound library\sonniss gdc 2026\files\`,
`<library>/<file>`). From/Length: the cut in the source, in seconds. Gain: dB against the other layers (each cut
is first levelled to -20 LUFS). At: where the layer starts in our sound. Processing: ffmpeg filters on the layer
(`asetrate` = pitch and speed together, `areverse` = backwards). The mix is then set to its level by role.

| Sound | Source | From | Length | Gain | At | Processing |
|---|---|---|---|---|---|---|
| halley_mark | Epic Stock Media - Fantasy Game 2 - Sound Kit for Enchanted Realms/UIAlert_Collect Scifi Futuristic Electronic Bass Burst Sweep Heavy 04_ESM_FG2.wav | 0 | 0.31 | 0 | 0 |  |
| halley_mark | Cinematic Sound Design - Interface & Infographics/Interface Accept Glassy Snap.wav | 0 | 0.44 | 1 | 0.01 |  |
| halley_mark | Cinematic Sound Design - System & UI Feedback Elements/Interface Sci-Fi Ping Down.wav | 0.08 | 0.67 | -3 | 0.03 | asetrate=66150,aresample=44100 |
| halley_mark | Cinematic Sound Design - User Interface/Button Arp Twinkle.wav | 0 | 1.81 | -9 | 0.1 | highpass=f=1500,aecho=0.8:0.5:90\|210:0.35\|0.2 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -5.745 | 0.000 | asetrate=44100,aresample=44100 |
| halley_countdown | Epic Stock Media - HD Lock And Mechanism Sound Design Kit/MECHLtch_Click Deep Mechanism Latch Button Nearfield Thunk 02_ESM_HDLM.wav | 0 | 0.38 | -9.745 | 0.000 | asetrate=37485,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -5.164 | 1.000 | asetrate=44894,aresample=44100 |
| halley_countdown | Epic Stock Media - HD Lock And Mechanism Sound Design Kit/MECHLtch_Click Deep Mechanism Latch Button Nearfield Thunk 02_ESM_HDLM.wav | 0 | 0.38 | -9.164 | 1.000 | asetrate=37485,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -4.661 | 1.920 | asetrate=45644,aresample=44100 |
| halley_countdown | Epic Stock Media - HD Lock And Mechanism Sound Design Kit/MECHLtch_Click Deep Mechanism Latch Button Nearfield Thunk 02_ESM_HDLM.wav | 0 | 0.38 | -8.661 | 1.920 | asetrate=37485,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -4.223 | 2.766 | asetrate=46349,aresample=44100 |
| halley_countdown | Epic Stock Media - HD Lock And Mechanism Sound Design Kit/MECHLtch_Click Deep Mechanism Latch Button Nearfield Thunk 02_ESM_HDLM.wav | 0 | 0.38 | -8.223 | 2.766 | asetrate=37485,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -3.840 | 3.545 | asetrate=46967,aresample=44100 |
| halley_countdown | Epic Stock Media - HD Lock And Mechanism Sound Design Kit/MECHLtch_Click Deep Mechanism Latch Button Nearfield Thunk 02_ESM_HDLM.wav | 0 | 0.38 | -7.840 | 3.545 | asetrate=37485,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -3.500 | 4.261 | asetrate=47584,aresample=44100 |
| halley_countdown | Epic Stock Media - HD Lock And Mechanism Sound Design Kit/MECHLtch_Click Deep Mechanism Latch Button Nearfield Thunk 02_ESM_HDLM.wav | 0 | 0.38 | -7.500 | 4.261 | asetrate=37485,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -3.200 | 4.921 | asetrate=48113,aresample=44100 |
| halley_countdown | Epic Stock Media - HD Lock And Mechanism Sound Design Kit/MECHLtch_Click Deep Mechanism Latch Button Nearfield Thunk 02_ESM_HDLM.wav | 0 | 0.38 | -7.200 | 4.921 | asetrate=37485,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -2.932 | 5.527 | asetrate=48598,aresample=44100 |
| halley_countdown | Epic Stock Media - HD Lock And Mechanism Sound Design Kit/MECHLtch_Click Deep Mechanism Latch Button Nearfield Thunk 02_ESM_HDLM.wav | 0 | 0.38 | -6.932 | 5.527 | asetrate=37485,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -2.693 | 6.085 | asetrate=49039,aresample=44100 |
| halley_countdown | Epic Stock Media - HD Lock And Mechanism Sound Design Kit/MECHLtch_Click Deep Mechanism Latch Button Nearfield Thunk 02_ESM_HDLM.wav | 0 | 0.38 | -6.693 | 6.085 | asetrate=37485,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -2.479 | 6.598 | asetrate=49480,aresample=44100 |
| halley_countdown | Epic Stock Media - HD Lock And Mechanism Sound Design Kit/MECHLtch_Click Deep Mechanism Latch Button Nearfield Thunk 02_ESM_HDLM.wav | 0 | 0.38 | -6.479 | 6.598 | asetrate=37485,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -2.286 | 7.070 | asetrate=49833,aresample=44100 |
| halley_countdown | Epic Stock Media - HD Lock And Mechanism Sound Design Kit/MECHLtch_Click Deep Mechanism Latch Button Nearfield Thunk 02_ESM_HDLM.wav | 0 | 0.38 | -6.286 | 7.070 | asetrate=37485,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -2.113 | 7.505 | asetrate=50186,aresample=44100 |
| halley_countdown | Epic Stock Media - HD Lock And Mechanism Sound Design Kit/MECHLtch_Click Deep Mechanism Latch Button Nearfield Thunk 02_ESM_HDLM.wav | 0 | 0.38 | -6.113 | 7.505 | asetrate=37485,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -1.956 | 7.904 | asetrate=50539,aresample=44100 |
| halley_countdown | Epic Stock Media - HD Lock And Mechanism Sound Design Kit/MECHLtch_Click Deep Mechanism Latch Button Nearfield Thunk 02_ESM_HDLM.wav | 0 | 0.38 | -5.956 | 7.904 | asetrate=37485,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -1.814 | 8.272 | asetrate=50847,aresample=44100 |
| halley_countdown | Epic Stock Media - HD Lock And Mechanism Sound Design Kit/MECHLtch_Click Deep Mechanism Latch Button Nearfield Thunk 02_ESM_HDLM.wav | 0 | 0.38 | -5.814 | 8.272 | asetrate=37485,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -1.686 | 8.610 | asetrate=51112,aresample=44100 |
| halley_countdown | Epic Stock Media - HD Lock And Mechanism Sound Design Kit/MECHLtch_Click Deep Mechanism Latch Button Nearfield Thunk 02_ESM_HDLM.wav | 0 | 0.38 | -5.686 | 8.610 | asetrate=37485,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -1.569 | 8.921 | asetrate=51377,aresample=44100 |
| halley_countdown | Epic Stock Media - HD Lock And Mechanism Sound Design Kit/MECHLtch_Click Deep Mechanism Latch Button Nearfield Thunk 02_ESM_HDLM.wav | 0 | 0.38 | -5.569 | 8.921 | asetrate=37485,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -1.464 | 9.208 | asetrate=51597,aresample=44100 |
| halley_countdown | Epic Stock Media - HD Lock And Mechanism Sound Design Kit/MECHLtch_Click Deep Mechanism Latch Button Nearfield Thunk 02_ESM_HDLM.wav | 0 | 0.38 | -5.464 | 9.208 | asetrate=37485,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -1.367 | 9.471 | asetrate=51818,aresample=44100 |
| halley_countdown | Epic Stock Media - HD Lock And Mechanism Sound Design Kit/MECHLtch_Click Deep Mechanism Latch Button Nearfield Thunk 02_ESM_HDLM.wav | 0 | 0.38 | -5.367 | 9.471 | asetrate=37485,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -1.280 | 9.713 | asetrate=51994,aresample=44100 |
| halley_countdown | Epic Stock Media - HD Lock And Mechanism Sound Design Kit/MECHLtch_Click Deep Mechanism Latch Button Nearfield Thunk 02_ESM_HDLM.wav | 0 | 0.38 | -5.280 | 9.713 | asetrate=37485,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -1.200 | 9.936 | asetrate=52170,aresample=44100 |
| halley_countdown | Epic Stock Media - HD Lock And Mechanism Sound Design Kit/MECHLtch_Click Deep Mechanism Latch Button Nearfield Thunk 02_ESM_HDLM.wav | 0 | 0.38 | -5.200 | 9.936 | asetrate=37485,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -1.128 | 10.141 | asetrate=52347,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -1.061 | 10.330 | asetrate=52523,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -1.000 | 10.504 | asetrate=52655,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -0.945 | 10.663 | asetrate=52788,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -0.894 | 10.810 | asetrate=52876,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -0.848 | 10.945 | asetrate=53008,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -0.805 | 11.070 | asetrate=53096,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -0.767 | 11.184 | asetrate=53185,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -0.731 | 11.289 | asetrate=53273,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -0.699 | 11.386 | asetrate=53361,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -0.668 | 11.476 | asetrate=53449,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -0.638 | 11.566 | asetrate=53493,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -0.608 | 11.656 | asetrate=53582,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -0.578 | 11.746 | asetrate=53670,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -0.549 | 11.836 | asetrate=53714,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -0.519 | 11.926 | asetrate=53802,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -0.489 | 12.016 | asetrate=53890,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -0.460 | 12.106 | asetrate=53934,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -0.430 | 12.196 | asetrate=54023,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -0.401 | 12.286 | asetrate=54111,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -0.372 | 12.376 | asetrate=54155,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -0.343 | 12.466 | asetrate=54243,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -0.314 | 12.556 | asetrate=54331,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -0.285 | 12.646 | asetrate=54375,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -0.256 | 12.736 | asetrate=54464,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -0.227 | 12.826 | asetrate=54552,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -0.199 | 12.916 | asetrate=54596,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -0.170 | 13.006 | asetrate=54684,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -0.142 | 13.096 | asetrate=54772,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -0.113 | 13.186 | asetrate=54816,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -0.085 | 13.276 | asetrate=54905,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -0.057 | 13.366 | asetrate=54993,aresample=44100 |
| halley_countdown | Cinematic Sound Design - Interface & Infographics/Interface Pop High Short.wav | 0 | 0.3 | -0.029 | 13.456 | asetrate=55037,aresample=44100 |
| halley_countdown | Alexander Kopeikin - Emotion and Magic/magic, drone, tension, spellbound, evanescence-002.wav | 20 | 13.55 | -4 | 0 | lowpass=f=2000,volume='0.06+0.94*pow(t/13.55,2.5)':eval=frame |
| halley_countdown | The Noisery - City Rain/WINDTonl_Wind Strong Gusts Hurricane Vents Rattle 06_The Noisery_City Rain.wav | 30 | 13.55 | -6 | 0 | highpass=f=300,volume='0.03+0.97*pow(t/13.55,3)':eval=frame |
| halley_sight | Epic Stock Media - Fantasy Game 2 - Sound Kit for Enchanted Realms/MAGAngl_Magic Light Spell Enchantment Potion Effect Tonal Bright 03_ESM_FG2.wav | 0 | 3.6 | 0 | 0 | afade=t=in:d=0.6 |
| halley_sight | Epic Stock Media - Strange Game Ambient Loops 3/MAGShim_Shimmer Loop Small Bell Metal Taps_ESM_SGA3.wav | 2 | 3.6 | -8 | 0 | afade=t=in:d=1.0 |
| halley_sight | Epic Stock Media - Elemental Mutation Whooshes and Impacts/GLASMvmt_Whoosh Glass Crystal Fragments Sharp Shards Dry 05_ESM_EMWI.wav | 0.1 | 2.5 | -9 | 0.4 | highpass=f=2500 |
| halley_approach | 344 Audio - Air Designed/AEROJet_Blast Off Clean_344 Audio_Air Designed.wav | 0 | 8.5 | 0 | 0 |  |
| halley_approach | The Noisery - City Rain/WINDTonl_Wind Strong Gusts Hurricane Vents Rattle 06_The Noisery_City Rain.wav | 40 | 8.5 | -4 | 0 | volume='0.15+0.85*pow(t/8.5,2.5)':eval=frame |
| halley_approach | Alexander Kopeikin - Emotion and Magic/magic, energy flow, astonishment-001.wav | 3 | 8.5 | -9 | 0 | highpass=f=900,volume='pow(t/8.5,4)':eval=frame |
| halley_approach | Alexander Kopeikin - 100 kHz Designed Ice/ice, movement, ice drift, ice field cracking up, initial, wide-001.wav | 30 | 8.5 | -10 | 0 | highpass=f=1500,volume='pow(t/8.5,3)':eval=frame |
| halley_boom | Epic Stock Media - Halloween Game - Haunted House and Horror Audio Scare Kit/GORESplt_Gore Designed Transient Heavy Impact Smash 01_ESM_HALG.wav | 0.05 | 1.6 | -3 | 0 | highpass=f=200 |
| halley_boom | Federico Soler - Effective Trailer Booms Vol. 2/EffectiveTrailer_Booms_Vol2_075.wav | 0.2 | 5.5 | 0 | 0 |  |
| halley_boom | Federico Soler - Effective Trailer Booms Vol. 2/EffectiveTrailer_Booms_Vol2_011.wav | 0.2 | 5.4 | -2 | 0.17 | highpass=f=40 |
| halley_boom | Epic Stock Media - Public Spaces - Storms Lakes Parks and Rural Nature Exteriors/STORM_Texas Rain Thunder Initial Crash Boom Storm 01 Clap Lightning_ESM_CPS.wav | 3.0 | 5.6 | -3 | 0.02 | lowpass=f=5000 |
| halley_touchdown | Federico Soler - Effective Trailer Booms Vol. 2/EffectiveTrailer_Booms_Vol2_214.wav | 0.2 | 6.0 | 0 | 0 |  |
| halley_touchdown | Alexander Kopeikin - 100 kHz Designed Ice/ice, block of ice crushed, heavy-015.wav | 0.1 | 2.7 | 0 | 0 |  |
| halley_touchdown | Epic Stock Media - Tower Defense Game/ICEBrk_Skill Freeze Whoosh Break Impact Layered Movement Shatter 03_ESM_TDG.wav | 0 | 2.5 | -3 | 0.02 |  |
| halley_touchdown | 344 Audio - Bass Drops & Downers Vol. 2/DSGNBass_Bass Drop & Downer Fast 12_344 Audio_Bass Drops & Downers Vol 2.wav | 0 | 2.6 | -3 | 0 |  |
| halley_touchdown | Cinematic Sound Design - Colossal Impacts/Woosh Debris.wav | 0.3 | 2.0 | -5 | 0.25 |  |
| halley_plough | Alexander Kopeikin - 100 kHz Designed Ice/ice, movement, ice drift, ice field cracking up, initial, wide-001.wav | 34 | 2.6 | 0 | 0 |  |
| halley_plough | SoundBits - Cars - Mad Mustang Mercury/VEHSkid_Tire Skids on Gravel 15 06_SNDBTS_CRS-MMM.wav | 24.5 | 2.6 | -5 | 0 | lowpass=f=5000 |
| halley_plough | 344 Audio - Extreme Winds Vol. 1/METLFric_Large Metal Box, Drag, Geofon_344 Audio_Extreme Winds Vol 1.wav | 30 | 2.6 | 0 | 0 | lowpass=f=250 |
| halley_plough | 344 Audio - Bass Drops & Downers Vol. 3/DSGNBass_Rattling Downer 3_344 Audio_Bass Drops and Downers Vol 3.wav | 1.0 | 2.6 | -8 | 0 |  |
| halley_impact | Epic Stock Media - Halloween Game - Haunted House and Horror Audio Scare Kit/GORESplt_Gore Designed Transient Heavy Impact Smash 01_ESM_HALG.wav | 0.05 | 1.7 | -3 | 0 |  |
| halley_impact | Federico Soler - Effective Trailer Booms Vol. 2/EffectiveTrailer_Booms_Vol2_011.wav | 0.2 | 9.2 | 0 | 0 |  |
| halley_impact | 344 Audio - Bass Drops & Downers Vol. 1/DSGNBass_Bass Drop & Downer Slow 10_344 Audio_Bass Drops & Downers.wav | 0 | 7.96 | -3 | 0 |  |
| halley_impact | Alexander Kopeikin - 100 kHz Designed Ice/ice, surface cracking, fissure, fast, hard-003.wav | 0.27 | 4.7 | -2 | 0 |  |
| halley_impact | Epic Stock Media - Tower Defense Game/ICEBrk_Skill Freeze Whoosh Break Impact Layered Movement Shatter 03_ESM_TDG.wav | 0 | 2.5 | -4 | 0.03 |  |
| halley_impact | Cinematic Sound Design - Colossal Impacts/Woosh Debris.wav | 0.3 | 2.0 | -6 | 0.5 |  |
| halley_impact | Jake Fielding - Interior Wind Rain and Storms/THUN_Interior Thunder Rumble_JF_INT Storm_01.wav | 5.0 | 8.9 | -3 | 0.5 | afade=t=in:d=0.6,lowpass=f=1200 |
| halley_frost | Alexander Kopeikin - 100 kHz Designed Ice/ice, crack, ice block snapping-001.wav | 0.47 | 3.2 | 0 | 0 |  |
| halley_frost | Alexander Kopeikin - 100 kHz Designed Ice/ice, movement, ice drift, ice field cracking up, initial, wide-001.wav | 74.0 | 3.2 | -3 | 0 | highpass=f=400 |
| halley_frost | Epic Stock Media - Elemental Mutation Whooshes and Impacts/GLASMvmt_Whoosh Glass Crystal Fragments Sharp Shards Dry 05_ESM_EMWI.wav | 0.2 | 2.4 | -10 | 0.3 | highpass=f=3000 |
| halley_hum | The Noisery - Moaning Metal/DSGNTonl_Designed Metal Bowed Screech Tonal Reverb 7_The Noisery_Moaning Metal.wav | 8 | 6 | 0 | 0 | lowpass=f=1800 |
| halley_hum | InMotionAudio - The Death Whistle/CREAEthr_Aztec Death Whistle Drone_05_IMA_Death Whistle Samples.wav | 3 | 6 | -8 | 0 | lowpass=f=400 |
| halley_hum | Epic Stock Media - Strange Game Ambient Loops 3/MAGShim_Shimmer Loop Small Bell Metal Taps_ESM_SGA3.wav | 10 | 6 | -16 | 0 |  |
| halley_hum_2 | The Noisery - Moaning Metal/DSGNTonl_Designed Metal Bowed Screech Tonal Reverb 7_The Noisery_Moaning Metal.wav | 14 | 6.0 | 0 | 0 | asetrate=41454,aresample=44100,lowpass=f=1600,lowshelf=f=200:g=2 |
| halley_hum_2 | InMotionAudio - The Death Whistle/CREAEthr_Aztec Death Whistle Drone_05_IMA_Death Whistle Samples.wav | 9 | 6.4 | -8 | 0 | lowpass=f=400 |
| halley_hum_2 | Epic Stock Media - Strange Game Ambient Loops 3/MAGShim_Shimmer Loop Small Bell Metal Taps_ESM_SGA3.wav | 20 | 6.4 | -16 | 0 |  |
| halley_hum_3 | The Noisery - Moaning Metal/DSGNTonl_Designed Metal Bowed Screech Tonal Reverb 7_The Noisery_Moaning Metal.wav | 2 | 6.0 | 0 | 0 | asetrate=46305,aresample=44100,lowpass=f=2000,tremolo=f=3:d=0.25 |
| halley_hum_3 | InMotionAudio - The Death Whistle/CREAEthr_Aztec Death Whistle Drone_05_IMA_Death Whistle Samples.wav | 12 | 5.71 | -8 | 0 | lowpass=f=400 |
| halley_hum_3 | Epic Stock Media - Strange Game Ambient Loops 3/MAGShim_Shimmer Loop Small Bell Metal Taps_ESM_SGA3.wav | 30 | 5.71 | -16 | 0 |  |
| halley_evac | 344 Audio - Elemental Palette Designed Vol. 1/WINDDsgn_Wind, Rush, Whoosh, Long x5 01_344 Audio_Elemental Palette Designed Vol 1.wav | 0 | 1.0 | 0 | 0 |  |
| halley_evac | Epic Stock Media - Tower Defense Game/DSGNStngr_Action Deploy Units Sword Slice Special Move Layered Swish 04_ESM_TDG.wav | 0.15 | 0.9 | -6 | 0.05 | highpass=f=300 |
| halley_dusk | Cinematic Sound Design - Ultra Transitions & Impacts/Transition Braam Slow Dark Creepy.wav | 0 | 4.5 | 0 | 0 | asetrate=36162,aresample=44100,lowpass=f=1500 |
| halley_dusk | Cinematic Sound Design - Sci-Fi Drones/Dark Industrial Ambience.wav | 2 | 5.5 | -6 | 0 | lowpass=f=500 |
| halley_burst | Epic Stock Media - Tower Defense Game/ICEBrk_Skill Freeze Whoosh Break Impact Layered Movement Shatter 03_ESM_TDG.wav | 0 | 2.5 | 0 | 0 |  |
| halley_burst | Epic Stock Media - Elemental Mutation Whooshes and Impacts/GLASMvmt_Whoosh Glass Crystal Fragments Sharp Shards Dry 05_ESM_EMWI.wav | 0.1 | 2.5 | -6 | 0.05 |  |
| halley_burst | Alexander Kopeikin - 100 kHz Designed Ice/ice, block of ice crushed, heavy-015.wav | 0.1 | 2.7 | -8 | 0 |  |
| halley_burst_2 | Alexander Kopeikin - 100 kHz Designed Ice/ice, crack, ice block snapping-001.wav | 0.47 | 3.0 | 0 | 0 | asetrate=39690,aresample=44100 |
| halley_burst_2 | Epic Stock Media - Elemental Mutation Whooshes and Impacts/GLASMvmt_Whoosh Glass Crystal Fragments Sharp Shards Dry 05_ESM_EMWI.wav | 0.1 | 2.5 | -8 | 0.05 | asetrate=39690,aresample=44100 |
| halley_burst_3 | Alexander Kopeikin - 100 kHz Designed Ice/ice, surface cracking, fissure, fast, hard-003.wav | 0.27 | 2.4 | 0 | 0 | asetrate=49392,aresample=44100 |
| halley_burst_3 | Epic Stock Media - Tower Defense Game/ICEBrk_Skill Freeze Whoosh Break Impact Layered Movement Shatter 03_ESM_TDG.wav | 0 | 2.5 | -4 | 0 | asetrate=49392,aresample=44100,highshelf=f=3000:g=2 |
| luna_uplink | Sonic Bat - Vintage Radio/SBvr_Power Button 003.wav | 0 | 0.78 | 0 | 0 |  |
| luna_uplink | Epic Stock Media - Fantasy Game 2 - Sound Kit for Enchanted Realms/UIAlert_Collect Scifi Futuristic Electronic Bass Burst Sweep Heavy 04_ESM_FG2.wav | 0 | 0.31 | -3 | 0.04 |  |
| luna_uplink | Cinematic Sound Design - User Interface/Button Arp Twinkle.wav | 0 | 1.6 | -5 | 0.12 |  |
| luna_uplink | Epic Stock Media - Fake Advertisements and Radio Sound Effects Audio Construction Kit/COMStatic_Radio Ham Loop Static Hum Active Powered On Garbled Noise 01_ESM_FA.wav | 3 | 1.5 | -14 | 0.05 | bandpass=f=1800:width_type=h:w=2000 |
| luna_alarm | Federico Soler - Effective Trailer Alarms Vol. 2/EffectiveTrailer_Alarms_Vol2_WholeNotes_011.wav | 0 | 4.0 | 0 | 0 |  |
| luna_alarm | The Noisery - Rich Glitch/UIGlitch_Designed_Glitch_Corrupted_Data Error_The Noisery_Rich Glitch_06.wav | 0 | 2.0 | -12 | 0.1 | highpass=f=800 |
| luna_buildup | Jake Fielding - Interior Wind Rain and Storms/THUN_Interior Thunder Rumble_JF_INT Storm_01.wav | 5.0 | 2.0 | 0 | 0 | lowpass=f=400 |
| luna_buildup | InMotionAudio - The Death Whistle/CREAEthr_Aztec Death Whistle Drone_05_IMA_Death Whistle Samples.wav | 2.0 | 2.0 | -3 | 0 | tremolo=f=6:d=0.4 |
| luna_buildup | Jake Fielding - Cinematic Horn Braams/DSGNBram____Cinematic Horn Braam, Epic, Cinematic, Dark, Instrument, Huge-32.wav | 0 | 2.0 | -6 | 0 | lowpass=f=1200 |
| luna_buildup | The Noisery - Moaning Metal/DSGNTonl_Metal Scrape Low Tonal LFE 4_The Noisery_Moaning Metal.wav | 0.2 | 1.2 | -5 | 0.3 | asetrate=30870,aresample=44100 |
| luna_crack | Alexander Kopeikin - 100 kHz Designed Ice/ice, crack, ice block snapping-001.wav | 0.47 | 1.8 | 0 | 0 | asetrate=26460,aresample=44100 |
| luna_crack | Epic Stock Media - Halloween Game - Haunted House and Horror Audio Scare Kit/GORESplt_Gore Designed Transient Heavy Impact Smash 01_ESM_HALG.wav | 0.05 | 1.7 | -3 | 0 | asetrate=30870,aresample=44100 |
| luna_crack | Epic Stock Media - HD Game Materials/ICEFric_Dry Ice High Metal Squeal Groan Bright Squeak Dissonant Short 13_ESM_HDGM.wav | 0 | 2.0 | -10 | 0.1 | asetrate=22050,aresample=44100,lowpass=f=2000 |
| luna_crack | Federico Soler - Effective Trailer Booms Vol. 2/EffectiveTrailer_Booms_Vol2_075.wav | 0.2 | 3.0 | -8 | 0 | lowpass=f=300 |
| luna_tear | The Noisery - Moaning Metal/DSGNTonl_Metal Scrape Low Tonal LFE 4_The Noisery_Moaning Metal.wav | 0.15 | 6.4 | 0 | 0 |  |
| luna_tear | The Noisery - Moaning Metal/DSGNTonl_Designed Metal Bowed Screech Tonal Reverb 7_The Noisery_Moaning Metal.wav | 6 | 4.5 | -3 | 0 | asetrate=30870,aresample=44100 |
| luna_tear | Alexander Kopeikin - 100 kHz Designed Ice/ice, surface cracking, fissure, fast, hard-003.wav | 0.27 | 3.2 | -4 | 0 | asetrate=22050,aresample=44100 |
| luna_tear | 344 Audio - Bass Drops & Downers Vol. 2/DSGNBass_Bass Drop & Downer Slow 07_344 Audio_Bass Drops & Downers Vol 2.wav | 0 | 4.54 | -3 | 0 |  |
| luna_fall | 344 Audio - Air Designed/AEROJet_Unidentified Encounter_344 Audio_Air Designed.wav | 20 | 12.75 | 0 | 0 | lowpass=f=3000 |
| luna_fall | The Noisery - City Rain/WINDTonl_Wind Strong Gusts Hurricane Vents Rattle 06_The Noisery_City Rain.wav | 22 | 12.75 | -4 | 0 |  |
| luna_fall | Jake Fielding - Interior Wind Rain and Storms/THUN_Interior Thunder Rumble_JF_INT Storm_01.wav | 2 | 12.75 | -5 | 0 | lowpass=f=500 |
| luna_entry | Epic Stock Media - Elemental Mutation Whooshes and Impacts/FIREWhsh_Whoosh Fire Deep Growl Monster Saturated Crisp 03_ESM_EMWI.wav | 0.05 | 2.4 | 0 | 0 |  |
| luna_entry | Federico Soler - Effective Trailer Booms Vol. 2/EffectiveTrailer_Booms_Vol2_214.wav | 0.2 | 6.5 | 0 | 0.35 |  |
| luna_entry | Epic Stock Media - Anime Game/EXPLDsgn_Explosion Small Blast Enemy Death Crunchy Boom Cartoon Noisy Crash Impact Delay 03_ESM_AG.wav | 0 | 1.48 | -4 | 0.35 |  |
| luna_entry | Epic Stock Media - Synthesized Nature Loops and Sounds/FIREBurn_Loop Elements Fire Crackling Crunchy Flame Burn 03_ESM_SNLS.wav | 2 | 6.5 | -10 | 0.4 | lowpass=f=3000,afade=t=out:st=3:d=3.5 |
| luna_rumble | Jake Fielding - Interior Wind Rain and Storms/THUN_Interior Thunder Rumble_JF_INT Storm_01.wav | 5 | 3 | 0 | 0 | lowpass=f=800 |
| luna_rumble | 344 Audio - Bass Drops & Downers Vol. 3/DSGNBass_Rattling Downer 3_344 Audio_Bass Drops and Downers Vol 3.wav | 0.1 | 3 | -4 | 0 |  |
| luna_inhale | Federico Soler - Effective Trailer Booms Vol. 2/EffectiveTrailer_Booms_Vol2_011.wav | 0.2 | 0.65 | 0 | 0 | areverse,highpass=f=250 |
| luna_inhale | 344 Audio - Elemental Palette Designed Vol. 1/WINDDsgn_Wind, Rush, Whoosh, Long x5 01_344 Audio_Elemental Palette Designed Vol 1.wav | 0 | 0.65 | -2 | 0 | areverse |
| luna_impact | Epic Stock Media - Halloween Game - Haunted House and Horror Audio Scare Kit/GORESplt_Gore Designed Transient Heavy Impact Smash 01_ESM_HALG.wav | 0.05 | 1.7 | -2 | 0 |  |
| luna_impact | Federico Soler - Effective Trailer Booms Vol. 2/EffectiveTrailer_Booms_Vol2_011.wav | 0.2 | 13.5 | 0 | 0 |  |
| luna_impact | Federico Soler - Effective Trailer Booms Vol. 2/EffectiveTrailer_Booms_Vol2_214.wav | 0.2 | 13.5 | -3 | 0.06 |  |
| luna_impact | 344 Audio - Bass Drops & Downers Vol. 1/DSGNBass_Bass Drop & Downer Slow 10_344 Audio_Bass Drops & Downers.wav | 0 | 7.96 | -2 | 0 |  |
| luna_impact | Alexander Kopeikin - 100 kHz Designed Ice/ice, surface cracking, fissure, fast, hard-003.wav | 0.27 | 3.0 | -6 | 0 | asetrate=22050,aresample=44100 |
| luna_impact | Cinematic Sound Design - Colossal Impacts/Woosh Debris.wav | 0.3 | 2.0 | -5 | 0.4 |  |
| luna_impact | Jake Fielding - Interior Wind Rain and Storms/THUN_Interior Thunder Rumble_JF_INT Storm_01.wav | 6 | 12.6 | -2 | 1.0 | afade=t=in:d=1.0,lowpass=f=900 |
| luna_shockwave | The Noisery - City Rain/WINDTonl_Wind Strong Gusts Hurricane Vents Rattle 06_The Noisery_City Rain.wav | 25 | 8.4 | 0 | 0 | volume='min(1,t/0.12)*exp(-0.3*t)':eval=frame |
| luna_shockwave | Federico Soler - Effective Trailer Booms Vol. 2/EffectiveTrailer_Booms_Vol2_075.wav | 0.2 | 8.2 | -3 | 0 | lowpass=f=800 |
| luna_shockwave | Cinematic Sound Design - Colossal Impacts/Woosh Debris.wav | 0.1 | 2.2 | -4 | 0.05 |  |
| luna_shockwave | The Noisery - Transit Tunes - The German Train Collection/TRNElec_Train Electric Passenger By Close Right To_The Noisery_Transit Tunes_ The German Train Collection_01.wav | 17 | 4.5 | -6 | 0 | asetrate=26460,aresample=44100,lowpass=f=1500 |
| luna_settle | The Noisery - Moaning Metal/DSGNTonl_Metal Scrape Low Tonal LFE 4_The Noisery_Moaning Metal.wav | 2.0 | 3.6 | 0 | 0 |  |
| luna_settle | The Noisery - Moaning Metal/DSGNTonl_Designed Metal Bowed Screech Tonal Reverb 7_The Noisery_Moaning Metal.wav | 9.9 | 2.2 | -4 | 0 | asetrate=26460,aresample=44100 |
| luna_settle | 344 Audio - Extreme Winds Vol. 1/METLFric_Large Metal Box, Drag, Geofon_344 Audio_Extreme Winds Vol 1.wav | 13 | 3.6 | 0 | 0 | lowpass=f=300 |
| luna_settle | SoundBits - Cars - Mad Mustang Mercury/VEHSkid_Tire Skids on Gravel 15 06_SNDBTS_CRS-MMM.wav | 3 | 2.6 | -8 | 0 | asetrate=30870,aresample=44100,lowpass=f=1500 |
| luna_aftershock | Jake Fielding - Interior Wind Rain and Storms/THUN_Interior Thunder Rumble_JF_INT Storm_01.wav | 9.0 | 4.5 | 0 | 0 | lowpass=f=700 |
| luna_aftershock | 344 Audio - Bass Drops & Downers Vol. 3/DSGNBass_Rattling Downer 3_344 Audio_Bass Drops and Downers Vol 3.wav | 0.1 | 4.5 | -4 | 0 |  |
| luna_aftershock_2 | Epic Stock Media - Public Spaces - Storms Lakes Parks and Rural Nature Exteriors/STORM_Texas Rain Thunder Initial Crash Boom Storm 01 Clap Lightning_ESM_CPS.wav | 5.5 | 5.11 | 0 | 0 | lowpass=f=500 |
| luna_aftershock_2 | 344 Audio - Bass Drops & Downers Vol. 3/DSGNBass_Rattling Downer 3_344 Audio_Bass Drops and Downers Vol 3.wav | 0.1 | 4.0 | -5 | 0.3 | asetrate=35280,aresample=44100 |
| luna_sizzle | Ivo Vicic - Campfire - Bonfire FX/42 Campfire, Putting Out Fire, Water from Bottle, Variation, Close.wav | 0.3 | 3.2 | 0 | 0 |  |
| luna_sizzle | Epic Stock Media - Synthesized Nature Loops and Sounds/FIREBurn_Loop Elements Fire Crackling Crunchy Flame Burn 03_ESM_SNLS.wav | 2.0 | 3.2 | -5 | 0 |  |
| luna_sizzle | Ivo Vicic - Campfire - Bonfire FX/24 Campfire, Dropping Fresh Pine Branches in Fire, Crackling, Sizzling Strong, Close 02.wav | 12 | 3.2 | -2 | 0 |  |
| luna_debris | Cinematic Sound Design - Colossal Impacts/Woosh Debris.wav | 0.3 | 2.0 | -3 | 0 |  |
| luna_debris | SoundBits - Cars - Mad Mustang Mercury/VEHSkid_Tire Skids on Gravel 15 06_SNDBTS_CRS-MMM.wav | 10 | 8.0 | -12 | 0 | lowpass=f=2500,volume='exp(-0.3*t)':eval=frame |
| luna_debris | Cinematic Sound Design - Cartoon Bloopers/Arrow Hit Rattle.wav | 0.25 | 0.8 | -1.8 | 0.157 | asetrate=25225,aresample=44100,lowpass=f=4000 |
| luna_debris | InMotionAudio - Instrument Case/OBJLug_CaseDown_Concrete12_InMotionAudio_InstrumentCase.wav | 0 | 0.6 | -3.7 | 0.204 | asetrate=29944,aresample=44100,lowpass=f=4000 |
| luna_debris | 344 Audio - Antique Books/PAPRMisc_Pile Of Antique Books Falling Over 8_344 Audio_Antiques - Books.wav | 1.5 | 0.6 | -4.4 | 0.428 | asetrate=33604,aresample=44100,lowpass=f=4000 |
| luna_debris | Epic Stock Media - Board Game - Sound Set Kit for Tabletop and Digital Games/GAMEBoard_Event Board Reset Organic Multiple Pieces Wood Small 02_ESM_BG.wav | 0 | 0.9 | -2.0 | 0.505 | asetrate=41322,aresample=44100,lowpass=f=4000 |
| luna_debris | Epic Stock Media - Board Game - Sound Set Kit for Tabletop and Digital Games/GAMEBoard_Event Board Reset Organic Multiple Pieces Wood Small 02_ESM_BG.wav | 0 | 0.9 | -3.4 | 0.715 | asetrate=34883,aresample=44100,lowpass=f=4000 |
| luna_debris | 344 Audio - Antique Books/PAPRMisc_Pile Of Antique Books Falling Over 8_344 Audio_Antiques - Books.wav | 0.85 | 0.55 | -2.8 | 0.914 | asetrate=26416,aresample=44100,lowpass=f=4000 |
| luna_debris | Cinematic Sound Design - Cartoon Bloopers/Arrow Hit Rattle.wav | 0.25 | 0.8 | -4.4 | 1.208 | asetrate=40043,aresample=44100,lowpass=f=4000 |
| luna_debris | Epic Stock Media - Board Game - Sound Set Kit for Tabletop and Digital Games/GAMEBoard_Event Board Reset Organic Multiple Pieces Wood Small 02_ESM_BG.wav | 0 | 0.9 | -5.9 | 1.506 | asetrate=34045,aresample=44100,lowpass=f=4000 |
| luna_debris | 344 Audio - Antique Books/PAPRMisc_Pile Of Antique Books Falling Over 8_344 Audio_Antiques - Books.wav | 0.85 | 0.55 | -3.4 | 1.781 | asetrate=24564,aresample=44100,lowpass=f=4000 |
| luna_debris | Cinematic Sound Design - Cartoon Bloopers/Arrow Hit Rattle.wav | 0.25 | 0.8 | -7.2 | 2.108 | asetrate=38808,aresample=44100,lowpass=f=4000 |
| luna_debris | 344 Audio - Antique Books/PAPRMisc_Pile Of Antique Books Falling Over 8_344 Audio_Antiques - Books.wav | 0.85 | 0.55 | -4.0 | 2.542 | asetrate=39646,aresample=44100,lowpass=f=4000 |
| luna_debris | Epic Stock Media - Board Game - Sound Set Kit for Tabletop and Digital Games/GAMEBoard_Event Board Reset Organic Multiple Pieces Wood Small 02_ESM_BG.wav | 0 | 0.9 | -6.8 | 2.898 | asetrate=34045,aresample=44100,lowpass=f=4000 |
| luna_debris | 344 Audio - Cinematic Fight Vol. 1/FGHTImpt_4 x Punch, Body 02_344 Audio_Cinematic Fight Vol 1.wav | 0.1 | 0.5 | -8.0 | 3.255 | asetrate=36427,aresample=44100,lowpass=f=4000 |
| luna_debris | 344 Audio - Antique Books/PAPRMisc_Pile Of Antique Books Falling Over 8_344 Audio_Antiques - Books.wav | 1.5 | 0.6 | -6.0 | 3.562 | asetrate=39646,aresample=44100,lowpass=f=4000 |
| luna_debris | Epic Stock Media - Board Game - Sound Set Kit for Tabletop and Digital Games/GAMEBoard_Event Board Reset Organic Multiple Pieces Wood Small 02_ESM_BG.wav | 0 | 0.9 | -7.7 | 3.975 | asetrate=37044,aresample=44100,lowpass=f=4000 |
| luna_debris | 344 Audio - Cinematic Fight Vol. 1/FGHTImpt_4 x Punch, Body 02_344 Audio_Cinematic Fight Vol 1.wav | 0.1 | 0.5 | -7.9 | 4.411 | asetrate=35456,aresample=44100,lowpass=f=4000 |
| luna_debris | Epic Stock Media - Board Game - Sound Set Kit for Tabletop and Digital Games/GAMEBoard_Event Board Reset Organic Multiple Pieces Wood Small 02_ESM_BG.wav | 0 | 0.9 | -6.5 | 4.819 | asetrate=25710,aresample=44100,lowpass=f=4000 |
| luna_debris | Cinematic Sound Design - Cartoon Bloopers/Arrow Hit Rattle.wav | 0.25 | 0.8 | -8.1 | 5.237 | asetrate=38588,aresample=44100,lowpass=f=4000 |
| luna_debris | 344 Audio - Cinematic Fight Vol. 1/FGHTImpt_4 x Punch, Body 02_344 Audio_Cinematic Fight Vol 1.wav | 0.1 | 0.5 | -9.9 | 5.682 | asetrate=29812,aresample=44100,lowpass=f=4000 |
| luna_debris | InMotionAudio - Instrument Case/OBJLug_CaseDown_Concrete12_InMotionAudio_InstrumentCase.wav | 0 | 0.6 | -10.6 | 6.227 | asetrate=24343,aresample=44100,lowpass=f=4000 |
| luna_debris_2 | Cinematic Sound Design - Colossal Impacts/Woosh Debris.wav | 0.3 | 2.0 | -3 | 0 |  |
| luna_debris_2 | SoundBits - Cars - Mad Mustang Mercury/VEHSkid_Tire Skids on Gravel 15 06_SNDBTS_CRS-MMM.wav | 10 | 8.89 | -12 | 0 | lowpass=f=2500,volume='exp(-0.3*t)':eval=frame |
| luna_debris_2 | 344 Audio - Antique Books/PAPRMisc_Pile Of Antique Books Falling Over 8_344 Audio_Antiques - Books.wav | 0.85 | 0.55 | -3.3 | 0.110 | asetrate=40131,aresample=44100,lowpass=f=4000 |
| luna_debris_2 | Epic Stock Media - Board Game - Sound Set Kit for Tabletop and Digital Games/GAMEBoard_Event Board Reset Organic Multiple Pieces Wood Small 02_ESM_BG.wav | 0 | 0.9 | -4.3 | 0.330 | asetrate=40528,aresample=44100,lowpass=f=4000 |
| luna_debris_2 | Epic Stock Media - Board Game - Sound Set Kit for Tabletop and Digital Games/GAMEBoard_Event Board Reset Organic Multiple Pieces Wood Small 02_ESM_BG.wav | 0 | 0.9 | -4.9 | 0.453 | asetrate=36471,aresample=44100,lowpass=f=4000 |
| luna_debris_2 | 344 Audio - Cinematic Fight Vol. 1/FGHTImpt_4 x Punch, Body 02_344 Audio_Cinematic Fight Vol 1.wav | 0.1 | 0.5 | -3.0 | 0.573 | asetrate=40087,aresample=44100,lowpass=f=4000 |
| luna_debris_2 | Cinematic Sound Design - Cartoon Bloopers/Arrow Hit Rattle.wav | 0.25 | 0.8 | -3.0 | 0.897 | asetrate=34133,aresample=44100,lowpass=f=4000 |
| luna_debris_2 | InMotionAudio - Instrument Case/OBJLug_CaseDown_Concrete12_InMotionAudio_InstrumentCase.wav | 0 | 0.6 | -5.8 | 1.031 | asetrate=40881,aresample=44100,lowpass=f=4000 |
| luna_debris_2 | 344 Audio - Cinematic Fight Vol. 1/FGHTImpt_4 x Punch, Body 02_344 Audio_Cinematic Fight Vol 1.wav | 0.1 | 0.5 | -3.5 | 1.388 | asetrate=40660,aresample=44100,lowpass=f=4000 |
| luna_debris_2 | 344 Audio - Antique Books/PAPRMisc_Pile Of Antique Books Falling Over 8_344 Audio_Antiques - Books.wav | 0.85 | 0.55 | -5.7 | 1.751 | asetrate=31443,aresample=44100,lowpass=f=4000 |
| luna_debris_2 | Cinematic Sound Design - Cartoon Bloopers/Arrow Hit Rattle.wav | 0.25 | 0.8 | -4.2 | 2.003 | asetrate=40528,aresample=44100,lowpass=f=4000 |
| luna_debris_2 | InMotionAudio - Instrument Case/OBJLug_CaseDown_Concrete12_InMotionAudio_InstrumentCase.wav | 0 | 0.6 | -6.9 | 2.446 | asetrate=32987,aresample=44100,lowpass=f=4000 |
| luna_debris_2 | 344 Audio - Cinematic Fight Vol. 1/FGHTImpt_4 x Punch, Body 02_344 Audio_Cinematic Fight Vol 1.wav | 0.1 | 0.5 | -7.2 | 2.843 | asetrate=27122,aresample=44100,lowpass=f=4000 |
| luna_debris_2 | InMotionAudio - Instrument Case/OBJLug_CaseDown_Concrete12_InMotionAudio_InstrumentCase.wav | 0 | 0.6 | -7.1 | 3.128 | asetrate=34222,aresample=44100,lowpass=f=4000 |
| luna_debris_2 | 344 Audio - Antique Books/PAPRMisc_Pile Of Antique Books Falling Over 8_344 Audio_Antiques - Books.wav | 1.5 | 0.6 | -6.0 | 3.541 | asetrate=35942,aresample=44100,lowpass=f=4000 |
| luna_debris_2 | Epic Stock Media - Board Game - Sound Set Kit for Tabletop and Digital Games/GAMEBoard_Event Board Reset Organic Multiple Pieces Wood Small 02_ESM_BG.wav | 0 | 0.9 | -8.0 | 4.096 | asetrate=34927,aresample=44100,lowpass=f=4000 |
| luna_debris_2 | 344 Audio - Antique Books/PAPRMisc_Pile Of Antique Books Falling Over 8_344 Audio_Antiques - Books.wav | 1.5 | 0.6 | -7.4 | 4.564 | asetrate=35280,aresample=44100,lowpass=f=4000 |
| luna_debris_2 | 344 Audio - Antique Books/PAPRMisc_Pile Of Antique Books Falling Over 8_344 Audio_Antiques - Books.wav | 1.5 | 0.6 | -9.9 | 4.999 | asetrate=28092,aresample=44100,lowpass=f=4000 |
| luna_debris_2 | 344 Audio - Antique Books/PAPRMisc_Pile Of Antique Books Falling Over 8_344 Audio_Antiques - Books.wav | 0.85 | 0.55 | -10.2 | 5.526 | asetrate=37970,aresample=44100,lowpass=f=4000 |
| luna_debris_2 | Epic Stock Media - Board Game - Sound Set Kit for Tabletop and Digital Games/GAMEBoard_Event Board Reset Organic Multiple Pieces Wood Small 02_ESM_BG.wav | 0 | 0.9 | -10.0 | 5.922 | asetrate=30605,aresample=44100,lowpass=f=4000 |
| luna_debris_2 | 344 Audio - Cinematic Fight Vol. 1/FGHTImpt_4 x Punch, Body 02_344 Audio_Cinematic Fight Vol 1.wav | 0.1 | 0.5 | -8.2 | 6.441 | asetrate=25887,aresample=44100,lowpass=f=4000 |
| luna_debris_2 | 344 Audio - Antique Books/PAPRMisc_Pile Of Antique Books Falling Over 8_344 Audio_Antiques - Books.wav | 0.85 | 0.55 | -10.6 | 6.988 | asetrate=35236,aresample=44100,lowpass=f=4000 |
| luna_debris_3 | Cinematic Sound Design - Colossal Impacts/Woosh Debris.wav | 0.3 | 2.0 | -3 | 0 |  |
| luna_debris_3 | SoundBits - Cars - Mad Mustang Mercury/VEHSkid_Tire Skids on Gravel 15 06_SNDBTS_CRS-MMM.wav | 10 | 7.47 | -12 | 0 | lowpass=f=2500,volume='exp(-0.3*t)':eval=frame |
| luna_debris_3 | InMotionAudio - Instrument Case/OBJLug_CaseDown_Concrete12_InMotionAudio_InstrumentCase.wav | 0 | 0.6 | -3.0 | 0.107 | asetrate=33163,aresample=44100,lowpass=f=4000 |
| luna_debris_3 | 344 Audio - Antique Books/PAPRMisc_Pile Of Antique Books Falling Over 8_344 Audio_Antiques - Books.wav | 1.5 | 0.6 | -4.2 | 0.178 | asetrate=30694,aresample=44100,lowpass=f=4000 |
| luna_debris_3 | 344 Audio - Cinematic Fight Vol. 1/FGHTImpt_4 x Punch, Body 02_344 Audio_Cinematic Fight Vol 1.wav | 0.1 | 0.5 | -2.7 | 0.301 | asetrate=28489,aresample=44100,lowpass=f=4000 |
| luna_debris_3 | 344 Audio - Cinematic Fight Vol. 1/FGHTImpt_4 x Punch, Body 02_344 Audio_Cinematic Fight Vol 1.wav | 0.1 | 0.5 | -4.4 | 0.526 | asetrate=41851,aresample=44100,lowpass=f=4000 |
| luna_debris_3 | 344 Audio - Antique Books/PAPRMisc_Pile Of Antique Books Falling Over 8_344 Audio_Antiques - Books.wav | 0.85 | 0.55 | -5.8 | 0.755 | asetrate=41189,aresample=44100,lowpass=f=4000 |
| luna_debris_3 | Cinematic Sound Design - Cartoon Bloopers/Arrow Hit Rattle.wav | 0.25 | 0.8 | -2.6 | 0.968 | asetrate=41101,aresample=44100,lowpass=f=4000 |
| luna_debris_3 | 344 Audio - Antique Books/PAPRMisc_Pile Of Antique Books Falling Over 8_344 Audio_Antiques - Books.wav | 0.85 | 0.55 | -3.7 | 1.131 | asetrate=30870,aresample=44100,lowpass=f=4000 |
| luna_debris_3 | 344 Audio - Antique Books/PAPRMisc_Pile Of Antique Books Falling Over 8_344 Audio_Antiques - Books.wav | 1.5 | 0.6 | -3.9 | 1.371 | asetrate=30914,aresample=44100,lowpass=f=4000 |
| luna_debris_3 | Epic Stock Media - Board Game - Sound Set Kit for Tabletop and Digital Games/GAMEBoard_Event Board Reset Organic Multiple Pieces Wood Small 02_ESM_BG.wav | 0 | 0.9 | -5.0 | 1.626 | asetrate=32193,aresample=44100,lowpass=f=4000 |
| luna_debris_3 | 344 Audio - Antique Books/PAPRMisc_Pile Of Antique Books Falling Over 8_344 Audio_Antiques - Books.wav | 1.5 | 0.6 | -4.5 | 1.927 | asetrate=35412,aresample=44100,lowpass=f=4000 |
| luna_debris_3 | Epic Stock Media - Board Game - Sound Set Kit for Tabletop and Digital Games/GAMEBoard_Event Board Reset Organic Multiple Pieces Wood Small 02_ESM_BG.wav | 0 | 0.9 | -3.8 | 2.244 | asetrate=28533,aresample=44100,lowpass=f=4000 |
| luna_debris_3 | 344 Audio - Antique Books/PAPRMisc_Pile Of Antique Books Falling Over 8_344 Audio_Antiques - Books.wav | 1.5 | 0.6 | -7.3 | 2.683 | asetrate=35765,aresample=44100,lowpass=f=4000 |
| luna_debris_3 | 344 Audio - Antique Books/PAPRMisc_Pile Of Antique Books Falling Over 8_344 Audio_Antiques - Books.wav | 1.5 | 0.6 | -7.4 | 2.972 | asetrate=39470,aresample=44100,lowpass=f=4000 |
| luna_debris_3 | 344 Audio - Cinematic Fight Vol. 1/FGHTImpt_4 x Punch, Body 02_344 Audio_Cinematic Fight Vol 1.wav | 0.1 | 0.5 | -5.0 | 3.284 | asetrate=38896,aresample=44100,lowpass=f=4000 |
| luna_debris_3 | 344 Audio - Cinematic Fight Vol. 1/FGHTImpt_4 x Punch, Body 02_344 Audio_Cinematic Fight Vol 1.wav | 0.1 | 0.5 | -9.3 | 3.663 | asetrate=27651,aresample=44100,lowpass=f=4000 |
| luna_debris_3 | Cinematic Sound Design - Cartoon Bloopers/Arrow Hit Rattle.wav | 0.25 | 0.8 | -7.4 | 3.986 | asetrate=29282,aresample=44100,lowpass=f=4000 |
| luna_debris_3 | InMotionAudio - Instrument Case/OBJLug_CaseDown_Concrete12_InMotionAudio_InstrumentCase.wav | 0 | 0.6 | -8.7 | 4.515 | asetrate=34354,aresample=44100,lowpass=f=4000 |
| luna_debris_3 | InMotionAudio - Instrument Case/OBJLug_CaseDown_Concrete12_InMotionAudio_InstrumentCase.wav | 0 | 0.6 | -9.8 | 4.890 | asetrate=40704,aresample=44100,lowpass=f=4000 |
| luna_debris_3 | 344 Audio - Cinematic Fight Vol. 1/FGHTImpt_4 x Punch, Body 02_344 Audio_Cinematic Fight Vol 1.wav | 0.1 | 0.5 | -10.3 | 5.298 | asetrate=33207,aresample=44100,lowpass=f=4000 |
| luna_debris_3 | 344 Audio - Antique Books/PAPRMisc_Pile Of Antique Books Falling Over 8_344 Audio_Antiques - Books.wav | 0.85 | 0.55 | -8.4 | 5.773 | asetrate=36294,aresample=44100,lowpass=f=4000 |
| proxima_beam | Epic Stock Media - Elemental Mutation Whooshes and Impacts/ELECMisc_Impact Electric Tonal Deep Movement Motion Hiss Glitch 01_ESM_EMWI.wav | 0 | 0.5 | -2 | 0 | areverse,volume='pow(t/0.5,2)':eval=frame |
| proxima_beam | InMotionAudio - Arc/ELECArc_ArcPowerUpDesign04_InMotionAudio_Arc.wav | 4.6 | 0.5 | 0 | 0 | volume='pow(t/0.5,2.5)':eval=frame,highpass=f=600 |
| proxima_beam | Epic Stock Media - Elemental Mutation Whooshes and Impacts/ELECMisc_Impact Electric Tonal Deep Movement Motion Hiss Glitch 01_ESM_EMWI.wav | 0 | 3.5 | 0 | 0.5 |  |
| proxima_beam | Epic Stock Media - Fantasy Game 2 - Sound Kit for Enchanted Realms/UIAlert_Collect Scifi Futuristic Electronic Bass Burst Sweep Heavy 04_ESM_FG2.wav | 0 | 0.31 | 0 | 0.5 |  |
| proxima_beam | InMotionAudio - Arc/ELECArc_ArcDesign15_InMotionAudio_Arc.wav | 0 | 1.0 | -3 | 0.5 |  |
| proxima_beam | InMotionAudio - Arc/ELECBuzz_Buzz27_InMotionAudio_Arc.wav | 1 | 2.5 | -8 | 0.6 | volume='exp(-1.6*t)':eval=frame |
| proxima_beam | 344 Audio - Bass Drops & Downers Vol. 2/DSGNBass_Bass Drop & Downer Fast 12_344 Audio_Bass Drops & Downers Vol 2.wav | 0 | 2.6 | -3 | 0.5 |  |
| proxima_beam | Federico Soler - Effective Trailer Booms Vol. 2/EffectiveTrailer_Booms_Vol2_075.wav | 0.2 | 3.4 | -6 | 0.5 | lowpass=f=300 |
| proxima_flash | Epic Stock Media - Elemental Mutation Whooshes and Impacts/ELECMisc_Impact Electric Tonal Deep Movement Motion Hiss Glitch 01_ESM_EMWI.wav | 0 | 2.6 | 0 | 0 | highpass=f=500 |
| proxima_flash | Epic Stock Media - Elemental Mutation Whooshes and Impacts/FIREWhsh_Whoosh Fire Deep Growl Monster Saturated Crisp 03_ESM_EMWI.wav | 0.3 | 2.2 | -4 | 0 |  |
| proxima_flash | Epic Stock Media - Synthesized Nature Loops and Sounds/FIREBurn_Loop Elements Fire Crackling Crunchy Flame Burn 03_ESM_SNLS.wav | 4.0 | 2.6 | -6 | 0 | highpass=f=1500 |
| proxima_flash | Ivo Vicic - Campfire - Bonfire FX/24 Campfire, Dropping Fresh Pine Branches in Fire, Crackling, Sizzling Strong, Close 02.wav | 14 | 2.6 | -6 | 0.1 | highpass=f=1000 |
| proxima_flash | 344 Audio - Bass Drops & Downers Vol. 1/DSGNBass_Bass Drop & Downer Fast 16_344 Audio_Bass Drops & Downers.wav | 0.05 | 1.5 | -5 | 0 |  |
| proxima_radio | synthesised: radio whine | 0 | 8.5 | 0 | 0 |  |
| proxima_radio | Epic Stock Media - Fake Advertisements and Radio Sound Effects Audio Construction Kit/COMStatic_Radio Ham Loop Static Hum Active Powered On Garbled Noise 01_ESM_FA.wav | 3 | 8.5 | -4 | 0 | bandpass=f=2200:width_type=h:w=2400,tremolo=f=11:d=0.5 |
| proxima_radio | Sonic Bat - Vintage Radio/SBvr_AM EQ Sweeping 002.wav | 10 | 8.5 | -6 | 0 | highpass=f=200 |
| proxima_radio | The Noisery - Rich Glitch/UIGlitch_Designed_Glitch_Corrupted_Data Error_The Noisery_Rich Glitch_06.wav | 0 | 5.43 | -12 | 1.5 | highpass=f=1000 |
| proxima_impact | Epic Stock Media - Halloween Game - Haunted House and Horror Audio Scare Kit/GORESplt_Gore Designed Transient Heavy Impact Smash 01_ESM_HALG.wav | 0.05 | 1.7 | -3 | 0 |  |
| proxima_impact | 344 Audio - Bass Drops & Downers Vol. 3/DSGNBass_Tone Downer (Reverb)_344 Audio_Bass Drops and Downers Vol 3.wav | 0.03 | 5.8 | 0 | 0 |  |
| proxima_impact | Federico Soler - Effective Trailer Booms Vol. 2/EffectiveTrailer_Booms_Vol2_214.wav | 0.2 | 6.6 | 0 | 0 |  |
| proxima_impact | Epic Stock Media - Elemental Mutation Whooshes and Impacts/ELECMisc_Impact Electric Tonal Deep Movement Motion Hiss Glitch 01_ESM_EMWI.wav | 0 | 3.7 | -5 | 0 |  |
| proxima_impact | Epic Stock Media - Elemental Mutation Whooshes and Impacts/FIREWhsh_Whoosh Fire Deep Growl Monster Saturated Crisp 03_ESM_EMWI.wav | 0.3 | 2.2 | -4 | 0.1 |  |
| proxima_impact | Ivo Vicic - Campfire - Bonfire FX/42 Campfire, Putting Out Fire, Water from Bottle, Variation, Close.wav | 0.3 | 4.0 | -9 | 0.5 | afade=t=in:d=0.4 |
| proxima_impact | InMotionAudio - Arc/ELECArc_ArcDesign15_InMotionAudio_Arc.wav | 0 | 1.0 | -12 | 0.05 |  |
| proxima_wave | SoundBits - Pass-By - Trains, Trucks & Cars 2/VEHFrght_Freight Truck Pass By 22_SNDBTS_PB-TTC2.wav | 1.2 | 2.8 | 0 | 0 | asetrate=35280,aresample=44100 |
| proxima_wave | Epic Stock Media - Elemental Mutation Whooshes and Impacts/FIREWhsh_Whoosh Fire Deep Growl Monster Saturated Crisp 03_ESM_EMWI.wav | 0.05 | 2.4 | -3 | 0.3 |  |
| proxima_wave | The Noisery - City Rain/WINDTonl_Wind Strong Gusts Hurricane Vents Rattle 06_The Noisery_City Rain.wav | 25 | 3.5 | -5 | 0 | volume='exp(-pow((t-0.9)/0.6,2))':eval=frame |
| proxima_wave | 344 Audio - Bass Drops & Downers Vol. 2/DSGNBass_Bass Drop & Downer Fast 12_344 Audio_Bass Drops & Downers Vol 2.wav | 0 | 2.0 | -3 | 0.95 |  |
| proxima_wave | Cinematic Sound Design - Colossal Impacts/Woosh Debris.wav | 0.3 | 2.0 | -6 | 1.0 |  |
| proxima_uplink | Epic Stock Media - Strange Game Ambient Loops 3/DSGNSynth_Scifi Loop Ship Reactor Synth Layer Layered Saw Buzz_ESM_SGA3.wav | 10 | 3 | 0 | 0 | lowpass=f=900 |
| proxima_uplink | Jake Fielding - Fridge Hums Vol.2/MACHAppl_Deep Fridge Hum, Buzz, Electric Tone, Deep_Jake Fielding_Fridge Hums Pt2.wav | 44 | 3 | -3 | 0 |  |
| proxima_uplink | Sonic Bat - Vintage Radio/SBvr_Power Button 003.wav | 0 | 0.78 | -6 | 0 |  |
| proxima_ascent | 344 Audio - Air Designed/AEROJet_Blast Off Clean_344 Audio_Air Designed.wav | 4.9 | 3.8 | 0 | 0 |  |
| proxima_ascent | Epic Stock Media - Synthesized Nature Loops and Sounds/WINDInt_Loop Weather Wind Whipping Constricted Flow Turbulent 01_ESM_SNLS.wav | 0 | 3.8 | -3 | 0 |  |
| proxima_ascent | 344 Audio - Elemental Palette Designed Vol. 1/WINDDsgn_Wind, Rush, Whoosh, Long x5 01_344 Audio_Elemental Palette Designed Vol 1.wav | 0 | 1.05 | -2 | 2.0 |  |
| proxima_space | Cinematic Sound Design - Sci-Fi Drones/Roomtone Space Ship Interior Muted.wav | 10 | 10 | 0 | 0 |  |
| proxima_space | InMotionAudio - The Death Whistle/CREAEthr_Aztec Death Whistle Drone_05_IMA_Death Whistle Samples.wav | 4 | 10 | -6 | 0 | lowpass=f=300 |
| proxima_space | Cinematic Sound Design - Sci-Fi Drones/Dark Industrial Ambience.wav | 5 | 10 | -8 | 0 | lowpass=f=500 |
| proxima_star | 344 Audio - Extreme Winds Vol. 1/WINDDsgn_EXT, Eye Of The Storm_344 Audio_Extreme Winds Vol 1.wav | 60 | 6 | 0 | 0 | asetrate=26460,aresample=44100,lowpass=f=400 |
| proxima_star | Epic Stock Media - Synthesized Nature Loops and Sounds/FIREBurn_Loop Elements Fire Crackling Crunchy Flame Burn 03_ESM_SNLS.wav | 3 | 3 | -3 | 0 | asetrate=22050,aresample=44100,lowpass=f=600 |
| proxima_star | Jake Fielding - Interior Wind Rain and Storms/THUN_Interior Thunder Rumble_JF_INT Storm_01.wav | 5 | 6 | -4 | 0 | lowpass=f=300 |
| proxima_star | 344 Audio - Extreme Winds Vol. 1/WINDDsgn_EXT, Eye Of The Storm_344 Audio_Extreme Winds Vol 1.wav | 70 | 6 | -3 | 0 | asetrate=30870,aresample=44100,lowpass=f=3000,volume='if(lt(t,2.4),0,min(1,(t-2.4)/1.5))':eval=frame |
| proxima_creak | Victor Ermakov - Industrial Ambiences - Ship Repair Factory/MACHInd_Crane Onboard Ride Squeaks Motors_CW.wav | 0 | 3 | -2 | 0 | highpass=f=200 |
| proxima_creak | Epic Stock Media - HD Game Materials/ICEFric_Dry Ice Squeak Metal Animal Mouse Imitation Short 07_ESM_HDGM.wav | 0 | 2.18 | -8 | 0.3 | asetrate=22050,aresample=44100 |
| proxima_creak | Epic Stock Media - HD Lock And Mechanism Sound Design Kit/METLTonl_Item Spring Wire Impact Flick Top Clatter Light Tap Roll Handling Short 01_ESM_HDLM.wav | 2 | 3 | -8 | 0 |  |
| proxima_creak | Epic Stock Media - HD Game Materials/METLImpt_Metal Old File Impact Tap Against Tire Iron Metallic Hit 01_ESM_HDGM.wav | 0.1 | 0.37 | -6 | 1.4 | asetrate=26460,aresample=44100 |
| proxima_iris | Epic Stock Media - Tower Defense Game/ROBTMvmt_Tower Deploy Hitech Robot Motor Dark Thump Servo Whine 04_ESM_TDG.wav | 0 | 1.04 | -2 | 0.000 | asetrate=30870,aresample=44100 |
| proxima_iris | Epic Stock Media - HD Lock And Mechanism Sound Design Kit/MECHLtch_Click Deep Mechanism Latch Button Nearfield Thunk 02_ESM_HDLM.wav | 0 | 0.38 | 0 | 0.300 | asetrate=26460,aresample=44100,lowshelf=f=120:g=4 |
| proxima_iris | Epic Stock Media - Tower Defense Game/ROBTMvmt_Tower Deploy Hitech Robot Motor Dark Thump Servo Whine 04_ESM_TDG.wav | 0 | 1.04 | -2 | 0.500 | asetrate=33075,aresample=44100 |
| proxima_iris | Epic Stock Media - HD Lock And Mechanism Sound Design Kit/MECHLtch_Click Deep Mechanism Latch Button Nearfield Thunk 02_ESM_HDLM.wav | 0 | 0.38 | 0 | 0.800 | asetrate=26460,aresample=44100,lowshelf=f=120:g=4 |
| proxima_iris | Epic Stock Media - Tower Defense Game/ROBTMvmt_Tower Deploy Hitech Robot Motor Dark Thump Servo Whine 04_ESM_TDG.wav | 0 | 1.04 | -2 | 1.000 | asetrate=35280,aresample=44100 |
| proxima_iris | Epic Stock Media - HD Lock And Mechanism Sound Design Kit/MECHLtch_Click Deep Mechanism Latch Button Nearfield Thunk 02_ESM_HDLM.wav | 0 | 0.38 | 0 | 1.300 | asetrate=26460,aresample=44100,lowshelf=f=120:g=4 |
| proxima_iris | Epic Stock Media - Tower Defense Game/ROBTMvmt_Tower Deploy Hitech Robot Motor Dark Thump Servo Whine 04_ESM_TDG.wav | 0 | 1.04 | -2 | 1.500 | asetrate=37485,aresample=44100 |
| proxima_iris | Epic Stock Media - HD Lock And Mechanism Sound Design Kit/MECHLtch_Click Deep Mechanism Latch Button Nearfield Thunk 02_ESM_HDLM.wav | 0 | 0.38 | 0 | 1.800 | asetrate=26460,aresample=44100,lowshelf=f=120:g=4 |
| proxima_iris | David Dumais Audio - Melee Weapons Sound Effects Pack 2/METLImpt_METAL SWING HIT Weapon Swing To Metallic Body Impact And Resonant Tail 01_DDUMAIS_MWP2.wav | 0.05 | 2.8 | -4 | 1.8 | asetrate=26460,aresample=44100 |
| proxima_iris | Victor Ermakov - Industrial Ambiences - Ship Repair Factory/MACHInd_Crane Onboard Ride Squeaks Motors_CW.wav | 5.25 | 3 | -8 | 0 | lowpass=f=1500 |
| proxima_reconnect | InMotionAudio - Arc/ELECArc_ArcPowerUpDesign04_InMotionAudio_Arc.wav | 4.0 | 0.9 | 0 | 0 | volume='pow(t/0.9,2.5)':eval=frame |
| proxima_reconnect | InMotionAudio - Arc/ELECBuzz_Buzz27_InMotionAudio_Arc.wav | 1 | 0.9 | -4 | 0 | volume='pow(t/0.9,2)':eval=frame |
| proxima_reconnect | InMotionAudio - Arc/ELECArc_ArcDesign15_InMotionAudio_Arc.wav | 0 | 0.4 | 0 | 0.88 |  |
| proxima_reconnect | David Dumais Audio - Melee Weapons Sound Effects Pack 2/WEAPWhip_WHIP Snap Crack 05_DDUMAIS_MWP2.wav | 0.1 | 0.6 | -2 | 0.86 |  |
| proxima_reconnect | 344 Audio - Elemental Palette Designed Vol. 1/WINDDsgn_Wind, Rush, Whoosh, Long x5 01_344 Audio_Elemental Palette Designed Vol 1.wav | 0.2 | 0.55 | -4 | 1.05 | areverse,highpass=f=400 |
| proxima_lens | The Noisery - Moaning Metal/DSGNImpt_Metal Hit Thud Thump Low Ring Geofon 1_The Noisery_Moaning Metal.wav | 0 | 1.6 | 0 | 0 |  |
| proxima_lens | Epic Stock Media - HD Lock And Mechanism Sound Design Kit/MECHLtch_Click Deep Mechanism Latch Button Nearfield Thunk 02_ESM_HDLM.wav | 0 | 0.38 | 0 | 0 | asetrate=24255,aresample=44100 |
| proxima_lens | Epic Stock Media - HD Lock And Mechanism Sound Design Kit/MECHLtch_Click Deep Mechanism Latch Button Nearfield Thunk 02_ESM_HDLM.wav | 0 | 0.38 | -2 | 0.35 | asetrate=26460,aresample=44100 |
| proxima_lens | Epic Stock Media - HD Lock And Mechanism Sound Design Kit/MECHLtch_Click Deep Mechanism Latch Button Nearfield Thunk 02_ESM_HDLM.wav | 0 | 0.38 | -3 | 0.6 | asetrate=28665,aresample=44100 |
| proxima_lens | InMotionAudio - Sinister Textures 5/GOREMisc_Concrete_MetalPipe02_InMotionAudio_SinisterTextures5.wav | 0 | 0.44 | -8 | 0.35 | asetrate=22050,aresample=44100 |
| proxima_lens | David Dumais Audio - Melee Weapons Sound Effects Pack 2/METLImpt_METAL SWING HIT Weapon Swing To Metallic Body Impact And Resonant Tail 01_DDUMAIS_MWP2.wav | 0.05 | 1.5 | -4 | 0.8 | asetrate=22050,aresample=44100 |
| proxima_gate | Epic Stock Media - Tower Defense Game/DSGNStngr_Action Deploy Units Sword Slice Special Move Layered Swish 04_ESM_TDG.wav | 0.1 | 2.3 | -2 | 0 | asetrate=30870,aresample=44100 |
| proxima_gate | The Noisery - Moaning Metal/DSGNTonl_Designed Metal Bowed Screech Tonal Reverb 7_The Noisery_Moaning Metal.wav | 2 | 2.0 | -3 | 0.4 | afade=t=in:d=0.4 |
| proxima_gate | Epic Stock Media - Strange Game Ambient Loops 3/DSGNSynth_Scifi Loop Ship Reactor Synth Layer Layered Saw Buzz_ESM_SGA3.wav | 20 | 2.5 | -8 | 0 | bandpass=f=300:width_type=h:w=300 |
| proxima_front | 344 Audio - Extreme Winds Vol. 1/WINDDsgn_EXT, Eye Of The Storm_344 Audio_Extreme Winds Vol 1.wav | 100 | 8 | 0 | 0 | lowpass=f=1200 |
| proxima_front | Jake Fielding - Interior Wind Rain and Storms/THUN_Interior Thunder Rumble_JF_INT Storm_01.wav | 4 | 8 | -3 | 0 |  |
| proxima_front | The Noisery - Transit Tunes - The German Train Collection/TRNElec_Train Electric Passenger By Close Right To_The Noisery_Transit Tunes_ The German Train Collection_01.wav | 12 | 4.8 | -6 | 0 | asetrate=26460,aresample=44100,lowpass=f=600 |
| proxima_front | Epic Stock Media - Fake Advertisements and Radio Sound Effects Audio Construction Kit/COMStatic_Radio Ham Loop Static Hum Active Powered On Garbled Noise 01_ESM_FA.wav | 5 | 8 | -16 | 0 | highpass=f=2000,tremolo=f=9:d=0.7 |
| proxima_inhale | Epic Stock Media - Elemental Mutation Whooshes and Impacts/FIREWhsh_Whoosh Fire Deep Growl Monster Saturated Crisp 03_ESM_EMWI.wav | 0.1 | 0.65 | 0 | 0 | areverse,highpass=f=200 |
| proxima_inhale | 344 Audio - Elemental Palette Designed Vol. 1/WINDDsgn_Wind, Rush, Whoosh, Long x5 01_344 Audio_Elemental Palette Designed Vol 1.wav | 0 | 0.65 | -2 | 0 | areverse |
| proxima_after | The Noisery - City Rain/WINDTonl_Wind Strong Gusts Hurricane Vents Rattle 06_The Noisery_City Rain.wav | 60 | 6 | -4 | 0 | lowpass=f=2000,afade=t=in:d=2.5 |
| proxima_after | Ivo Vicic - Campfire - Bonfire FX/24 Campfire, Dropping Fresh Pine Branches in Fire, Crackling, Sizzling Strong, Close 02.wav | 20 | 6 | 0 | 0 | highpass=f=800 |
| proxima_after | Alexander Kopeikin - 100 kHz Designed Ice/ice, movement, ice drift, ice field cracking up, initial, wide-001.wav | 74 | 6 | -8 | 0 | highpass=f=2500 |
| proxima_after | Jake Fielding - Interior Wind Rain and Storms/THUN_Interior Thunder Rumble_JF_INT Storm_01.wav | 11 | 6 | -6 | 0.5 | lowpass=f=200 |

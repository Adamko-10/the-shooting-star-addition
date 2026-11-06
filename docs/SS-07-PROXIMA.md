# SA-04 · Proxima

(Renamed from SA-03 on 2026-11-05: comet, moon, then a star made no sense; SA-03 is kept for a skill between them.)
A red-dwarf flare star inside a Dyson sphere fires a solar flare, then a coronal mass ejection (CME), at the crosshair.
Skill id `proxima`, colour crimson `0xE8294C`, default key **Z**. The agreed design is in CLAUDE.md §15 (look, intro,
the lens beam, the three phases, crater, shelter, knockback, sliders, nerd details, aftermath, random flares, music,
config); this file is the build's own record: what exists, where it lives, and what is next.

## Stages (built on `main` first, CLAUDE.md §0)

1. **Playable strike** (2026-11-05): on the remote, the timeline, the flash burn, the bowl and the scorch, ground-zero
   damage and knockback, shelter, basic effects (screen, particles, borrowed sounds), Stellar Convergence knows it.
   The sky (the user: "with shaders the sky doesn't change at all"): `ProximaFx.sky` gives `client/sky/HalleySky` a
   `SkyLook` in its own palette (`SkyLook.PALETTE_PROXIMA`, crimson zenith to a burning orange horizon, an ashen red
   haze after ground zero, red clouds): without a shader pack the addon's sky shader paints it; with one, the sky's
   clock is wound on to **sunset** (`toSunset`, the pack's own red sky) at the flash and back over the aftermath, as
   Halley and Luna wind it to night.
2. **The wave** (2026-11-05; game-tested the same evening: `test builds\2026-11-05 auto test 19 proxima wave\`, `far`
   and `low` views: the shell fills a side of the sky with its white-hot nose, a white wall rises at ground zero and
   rolls out over the land, the scorch spreading behind it. Tuned after: the wall up to 145 tall, filled in halfway
   up, the burning band 40 wide; each chunk scorched once the wave is past its far corner, so the scorch follows
   just behind the wall instead of showing in squares ahead of it, not re-tested). The user: the CME should be "on a
   massive scale, many chunks and much more vfx, like a proper big wave"; the sky "changes but not much" with shaders.
   - The coronal front (`ProximaFx.render`, drawn in `CometRenderer`'s glow pass through `GlowBatch.corner`, a plain
     mesh sampling the flat middle of the DISC shape, so it works with shader packs too): a shell of plasma
     `FRONT_RADIUS` 2600 across its radius, coming down 22° out of the sky from a side set by the seed
     (`ProximaPlan.frontDirection`), its nose `FRONT_START` 2600 blocks out at FRONT and on the mark at IMPACT
     (`frontDistance`, closing faster and faster). A cap 0.72 rad round the nose, 18 x 48 quads, recoloured every frame:
     brightest edge-on (`rim`, the leading line sweeping across the sky and then the land), streaming filaments, a
     white-hot nose glare, faded below the mark's level and at the cap's edge; a second, fainter sheath 260 behind.
     Far parts are pulled in along the line of sight (GlowBatch), so it shows at any render distance.
   - Ground zero's shock wave (`ProximaPlan.waveRadius`: out to 1.12 x the scorch in `WAVE_TICKS` 70, slowing): a wall
     of fire and dust on the land (white-hot foot, crimson top, lower as it runs) and a burning band behind it; a white
     point at the mark for 24 ticks. **The server follows it**: `ProximaStrike.wave` hurts and throws what it reaches as
     it passes (only the bowl's are hit at once), and `ProximaLand.tick(radius)` scorches only the chunks it has
     reached, so the scorch spreads out with the wave.
   - Where they pass the camera: a roar and a jolt when the front's line crosses it (embers round it while near), the
     blast, a jolt and fire and dust thrown up along the wave; the front's rumble builds on screen.
   - Radii doubled (the user, "decently larger"); Proxima's red sky is painted over a shader pack's sky too (its sunset
     alone was faint): `HalleySky.paint` covers it with the crimson night for `PALETTE_PROXIMA`.
   - The rest of stage 2 (2026-11-05, game-tested: `auto test 19 proxima wave\cracks\`): the Type II radio burst
     (`proxima_radio`, made by `art/make_proxima_sounds.sh`: a whine sweeping down 1600 -> 220 Hz over the front's 8.5 s
     with two harmonics and a flutter, over banded static; played at FRONT; no missing-sound warning, not heard by
     Claude); compasses and recovery compasses spin from the front to 5 s after the impact within 3000 blocks
     (`compat/mixin/CompassSpinMixin` -> `ProximaFx.compassSpin`); redstone near the camera sparks (30 blocks sampled a
     tick: wire, lamps, torches, blocks); water under the open sky steams in the flash and as the wave passes; nine
     cracks of light race out from the bowl at the impact and cool over 4 s (`ProximaFx.cracks`). Not checked in game:
     the compass, sparks and steam (the flat test world has none of them).
3. **The film, part 1: in the world** (2026-11-05, game-tested: `test builds\2026-11-05 auto test 20 proxima film\film\`).
   `client/film/ProximaStoryboard` (Luna's film types; `END` = IMPACT + 190): (1) the remote over the caster's shoulder;
   (2) looking up as the flash burns the sky; (3) low beside the mark, the CME rising over the horizon; (4) a slow turn
   high over the land as the front's edge sweeps down; (5) the hush, looking straight up into the nose; (6) ground zero
   wide, three-quarters on; (7) low on the land as the wall rolls past; (8) rising over the scorched land (pulled in
   after the test: it was lost in the fog). `compat/tss/client/ProximaCutscene`, played from
   `StarClientBridge.startProxima`. Title card `FilmTitle.proxima`: SA-04 PROXIMA ("PROXIMA CENTAURI · FLARE STAR"),
   from FRONT+30 to +96, letters flaring in white-hot and burning down to crimson, lifting away like embers.
   `client/proxima/ProximaHud.film`: letterbox, brackets, a caption and readout per phase (uplink and 4.24 LY; flare
   class from the power slider, logarithmic: 0.1x ~C1, 1x M5, 3x X3, 10x X25; the front's distance in km; the hush;
   impact; the shock wave's reach); **the radio blackout**: from FRONT to IMPACT+30 the captions are garbled (letters
   swapped for noise) and drop out now and then; only the skill's own text.
   **Part 2, the space intro** (2026-11-05, game-tested: `auto test 20 proxima film\intro\`). The timeline moved 60 ticks
   later for it (FLASH 150, FRONT 160, HUSH 320, IMPACT 330 with Halley's and Luna's, DURATION 660); its beats in
   `ProximaPlan`: SPACE 30, IRIS 60, LOOPS 100, COLLAPSE 118, LENS 125, BEAM 135, GATE 145. `client/proxima/ProximaSpace`
   (from `ProximaFx.render`, glow pipeline): the star (`starCentre`: 20000 out, 50° up on the front's side, radius
   1400) dim behind a lattice ring of struts and eight iris blades that twist open, then blazing; two plasma loops
   drawn as a lemniscate in front of it, a white flare at the crossing, collapsing; the lens (ring + faint disc), a
   beam fired out of it across the frame toward the camera; the gate (`plan.gate()`, where the front's nose starts)
   catching a beam from the star's side of the sky and bursting at the flash. The glow only adds light, so the closed
   panels are shown by holding the star dim. The sky goes to deep starry night for it (`ProximaFx.sky`, Halley's
   palette; with a shader pack the clock is wound on to midnight, then to sunset at the flash, hidden by the flash).
   Everyone looking at that sky sees it, not only the caster's film. Shots: rising 300 blocks off the land turning up
   to the star (FOV 70 -> 26), the Dyson sphere (24 -> 16), the loops (16 -> 12), the lens and beam (14 -> 26), back
   on the land looking up at the gate for the flash. Cards: `FilmTitle.dyson` (steel letters sliding in from both
   sides, "PROXIMA CENTAURI · 4.24 LIGHT YEARS"), `FilmTitle.cme` (flickering half-formed plasma letters torn apart at
   BEAM+6). Captions per beat (uplink, 4.24 light years, aperture opening, magnetic reconnection and the class, beam
   focused, gate open).
   **Part 3, Earth, BUILT 2026-11-06** (the user: "do whatever the original mod did"): the same NASA imagery The
   Shooting Star's own space Earth credits (its CREDITS.txt read for the sources only; nothing of its own used),
   downloaded with the user's OK into the project folder's `art sources
asa` (Blue Marble NG June 2004 topo+bathy
   5400x2700, Black Marble 2016 0.1 degree): `art/make_earth.sh` makes `textures/proxima/earth_day.png` (2048x1024),
   `earth_lights.png` (1024x512, cities only) and, since NASA's clouds image is gone, `earth_clouds.png` from noise
   on the sphere (`art/make_earth_clouds.js`); credits in `textures/proxima/CREDITS.txt`. `client/proxima/EarthSphere`
   (AFTER_ENTITIES, like MoonSphere): a lat/lon sphere, only the cap the camera sees, four layers in new no-depth render
   types (`HalleyRenderTypes.EARTH_*`, the glowing-eyes shader: day opaque, lights added, clouds blended, a blue air
   rim added with Luna's glow texture), lit from the west with a soft terminator, the Atlantic facing the camera, turning
   slowly; it hangs 16 degrees across just behind the gate as the camera sees it, pulled in past the far plane. Only in
   the caster's film, in its new Earth shot (`ProximaStoryboard` take 6, EARTH 140 to FLASH - 7: from 300 above the
   caster, pushing in toward the gate, Earth behind it). The timeline moved 40 ticks for it (GATE 160, FLASH 190, FRONT
   200, HUSH 360, IMPACT 370, DURATION 700; the film 28 s); the lens's beam now runs to the gate (into Earth's shot from
   above); the HUD's caption there reads EARTH / BEAM INBOUND / GATE OPEN; the music re-cut (from 7.85 s; its pulses
   near SPACE, IRIS, LOOPS and the Earth shot, its gap on the cut back to the ground, its entry on FLASH).
   The intro to space (title card "DYSON SPHERE", the lattice opening like an iris, the infinity-sign loops,
   "CORONAL MASS EJECTION" in plasma filaments), the lens beam and the gate near Earth, the pullback afterwards.
   RimuLib's film tools (`CameraRig`, `Shot`, `Subject`, `CutsceneDirector`), our own Earth shader (free imagery only).
4. **The aftermath, first half** (2026-11-05, game-tested: `auto test 19 proxima wave\residue-close\`):
   - **Solar Residue** (`shooting_star_addition:solar_residue`, `content/SolarResidueBlock`): glowing (light 12),
     emissive, amethyst sounds, sparks and an odd chime (`animateTick`), a pickaxe mines it and it drops itself;
     strength 3. Texture `art/make_solar_residue.sh` (ffmpeg, 12 frames: crimson crystal facets, pulsing pink veins).
     Left in the bowl's glassy floor: about 2.5% of its columns (`bowlSurface`, noise 0.12-0.145, not near the rim;
     ~100 blocks at 1x). In the natural blocks tab. Not decided: an event tag so creative copies don't count (§14).
   - **The radiation zone** (`ProximaStrike.Zone`, server tick): 1.3 x the bowl, for `RADIATION_TICKS` 12000 (10 min)
     times sqrt(power) (at least half): once a second, whatever lingers in it is hurt (0.5 to 3, by how close to the
     middle and how fresh) and weakened; damage type `proxima_radiation`. Not saved: it ends with the server. Clients
     (`ProximaFx.tickZones`, client tick): crimson motes and white sparks rising over the bowl near the camera, a low hum
     at its edge, fading with it. Not checked in game: the damage and the hum.
   - Fixed in the same round: a ring of grass left at the bowl's wobbly rim (the scorch now skips exactly the columns
     the bowl cuts).
   - **Flare Ash** (`shooting_star_addition:proxima_ash`, 2026-11-05, game-tested: `auto test 19 proxima wave\aurora\`):
     vanilla's `SnowLayerBlock` without random ticks (it never melts), grey (`art/make_ash.sh`), snow models with its
     texture for all 8 layers, a shovel, drops nothing. One layer laid by `ProximaLand` over the scorched ground: half the
     charred ring, thinning to a third and out toward the scorch's edge; only on scorched soil with air above.
   - **Auroras** (same test): every flare leaves one for `AURORA_TICKS` 36000 (a day and a half), by night only, within
     2000 blocks, fading in over a minute after the strike and out over its last 6000 ticks (`ProximaFx.aurora`, through
     HalleySky when nothing else changes the sky, so shader packs get it too). The sky shader's curtain tops are a
     uniform now (`AuroraTop`): red for Proxima's palette, violet as before for Halley's. Client only, not saved.
   Still to do: the "radio blackout" on the skill's own HUD (Proxima has no HUD until its film, stage 3), permanent
   zones and the rest of the config (stage 5).
5. Sounds (the sound overhaul's way: `art/overhaul_sounds.sh`) and the user's music track, cut so its drop lands on
   `IMPACT`; then config (the §15 list), random flares (off by default).
   - **Music, built 2026-11-05 from the user's draft** (`Video Project 49.m4a`, "not quite done"; in the project
     folder's music folder): `proxima_music.ogg` (27.5 s, -18 LUFS like Luna's), made by `art/make_proxima_music.sh`. The
     draft's moments (measured: a spectrogram and a 50 ms loudness curve): pulses every 2.1 s (~114 BPM) to 16.4 s,
     a 0.25 s gap and an entry at 16.85 s, a calmer middle without a steady beat, the main section full at 28.85 s. Cut:
     the track from 9.85 s plays from tick 10 (`ProximaFx.MUSIC_AT`), so its pulses fall near SPACE (tick 27), IRIS
     (68) and the loops (111), its gap on the gate (141-146) and its entry on FLASH (150); it fades out into the hush
     (silent 320-329); the main section from 28.85 s comes in hard on IMPACT (330) and fades out by tick 560. Checked
     in the file: the entry rises at 149-151, silence 320-329, full at 330. Played by `client/StrikeMusic` (Luna's
     music player, moved out of MoonFx and shared): the Music volume, pitched with the speed slider, kept in step with
     the strike's clock; heard by the caster and within 1500 blocks; the plate's M switch is now "Music" (Luna's and
     Proxima's; config key still `luna_music`). For the finished track: find ENTRY and DROP again, set them in the
     script, re-run it.
   - **Config, built 2026-11-05** (`ProximaConfig`, common config): `[proxima]` `enabled`, `cooldown_seconds` (60);
     `[proxima_size]` `bowl_radius` 36, `char_radius` 100, `scorch_radius` 220, `bowl_depth` 6 (at power 1; the slider
     scales them; also the menu's hint and Stellar Convergence's reach); `[proxima_world]` `carve_terrain` (false:
     looks and damage only), `solar_residue`, `shelter_blocks` 3, `player_knockback` 0.6 (the most a player is pushed),
     `mob_knockback` 1.0 (a multiple), `radiation_minutes` 10 (0: none), `permanent_radiation`, `radiation_damage` 3.0
     per second. The radiation's length rides in `ProximaParams` (version 2, `radiationTicks`, -1 = for good), so
     clients fade their shimmer with the server; the zones are saved with each world (`ProximaStrike.Zones`, SavedData
     `shooting_star_addition_proxima_zones`) and outlast restarts. shortcut: a player who joins after the strike feels
     the zone but sees no shimmer (the shimmer starts from the effect packet); sync the zones on join if it matters.
   - **Own sounds, built 2026-11-05** (`art/make_proxima_sounds.sh`, synthesised like proxima_radio):
     `proxima_beam` (at LENS, only while the caster's film shows: a 0.5 s charge whining up, the discharge on BEAM, a
     zap and a buzzing hum), `proxima_flash` (at FLASH: a searing white rush, crackle, a low thump; replaces Halley's
     burst and the fire charge), `proxima_impact` (at IMPACT: a falling sub thump, a crack, a roaring echoing tail, the
     plasma's sizzle; replaces Luna's impact and shock wave), `proxima_wave` (the coronal front and ground zero's wave
     passing the camera: a roar coming on high and leaving low, a thud). Still borrowed: Luna's rumble under the front
     and her inhale before the impact. Not heard by anyone yet.
   - **Random flares, built 2026-11-05** (`world/SolarFlares`; `[proxima_flares]`: `random_flares` false,
     `average_minutes` 120, `min_minutes_between` 30, `max_tier` MAJOR, `warning_seconds` 60, `major_burns_plants`
     true). Once a minute (overworld game time), with players in the overworld and none pending, a 1-in-
     `average_minutes` chance: a tier (minor 70, major 25, cataclysm 5, up to `max_tier`), a non-spectator player, a
     spot (minor 100-400 blocks off at power 0.2-0.5; major 20-80 off, power 0.5-1.2; cataclysm 150-270 off, power
     1-2), the ground's height there only if loaded (else the player's). An alert goes to everyone in that world (yellow /
     gold "get under N solid blocks" / red bold emergency, with the class and the x, z), logged too; after the warning
     the strike runs as a Proxima spell with no caster (`Spell`'s second constructor: a random UUID, so nobody is warded
     and no player's own Proxima counts it; the player only stands in as the caster in the effect packet), its clock
     starting at FLASH (`ProximaParams.tier`, `startTick`; version 3), so there is no space intro, no film
     (`StarClientBridge` checks `natural()`) and no music (it starts at tick 10). Tiers (`cme()`, `burns()`): minor =
     the flash's light and sky only (softer), auroras; major = the flash burn (fire and damage under the open sky) and,
     with `major_burns_plants` and `carve_terrain`, the plants burnt away in the scorch radius at once
     (`ProximaLand` plants-only: leaves, grass, flowers, crops, vines, snow; no trunks, ground or ash), auroras; no
     front, no crater, no radiation; cataclysm = the whole strike from the flash, hits held to 60 damage (no erasing).
     Operators: `/ssa flare <minor|major|cataclysm> [seconds]` near themselves.

## Timeline (`ProximaPlan`, ticks at speed 1)

| | tick | |
|---|---|---|
| PRESS | 7 | the remote's button |
| MUSIC_AT | 10 | the music starts (`ProximaFx`) |
| SPACE | 30 | the film leaves for space: the star in its Dyson sphere |
| IRIS, LOOPS, COLLAPSE | 60, 100, 118 | the iris opens; the infinity loops; they collapse to a point |
| LENS, BEAM | 125, 135 | the lens folds, fires |
| EARTH, GATE | 140, 160 | the film's Earth shot; the gate before it opens and catches the beam |
| FLASH | 190 | phase 1: the flare's light arrives; exposed things burn (the music's entry) |
| FRONT | 200 | phase 2: the coronal front crosses the sky |
| HUSH | 360 | near-silence: everyone thinks it's over (the music too) |
| IMPACT | 370 | phase 3: ground zero (the music's main section) |
| DURATION | 700 | the effects end; the film ends at 560 |

The space intro (stage 3) runs from SPACE to FLASH. Stellar Convergence uses `IMPACT` to land it with the others.

## The Railgun reference (for the new film's zoom-out; recorded 2026-11-06)

TSS 1.5.2's Railgun, cast by `/ssa strike the_shooting_star at 250 ~ 0` with its film on, on real land at noon
(`test scripts\autotest16.sh`; results in `test builds\2026-11-06 auto test 23 railgun reference\`: `film-camera.txt`
= the film camera every film tick, `other_film_<seconds>.png` = a frame every half second of film time). Recorded with
the harness's new `-Dss05.clienttest.filmlog=true` (any mod's film, timed by RimuLib's `CutsceneDirector.seconds`, so
server lag doesn't shift the frames). Only to match the feel and pace: none of TSS's assets, lettering or shaders are
ours to use (CLAUDE.md §9).

Its film runs 28.9 s at speed 1:

| Film time | What |
|---|---|
| 0-1.0 s | at the caster: close on them, then over the shoulder |
| 1.5 s | cut to the ground ~7 blocks from the mark, low, the beam standing from the sky into the land |
| 2.0-2.3 s | at the mark looking up the beam (pitch -73, a 4 degree roll) |
| 2.35-2.45 s | a whip turn in 0.1 s from looking up to looking down (pitch -67 to +55) |
| **2.45-3.43 s** | **the pull up**: straight above the mark, looking down along the beam |
| ~3.4 s | through TSS's own soft cloud layer (lit pink by the beam), then on top of a cloud field, the beam glowing red where it goes in |
| 4.5 s | Earth from orbit filling the frame, the beam hitting it |
| 5.5 s | Earth small and half lit in a starfield (a hard cut, not one move) |
| 7-11 s | following the beam back: past Saturn, the Milky Way, its source over the galaxy |
| 13.5 s | the satellite with its title card |
| 16.5 s | warp streaks rushing back to Earth |
| 17-21 s | the impact (an IMPACT caption at 18 s), wide shots |
| 21-25 s | low in the shaft's red glow with slabs of debris, then high above looking down (y 306-336, pitch 42) |
| 25-28.9 s | the camera coming back down to the caster's eyes |

**The pull up, by numbers** (height of the camera above the ground at the mark): 6 blocks at 2.45 s, 7.7 at 2.62,
13.5 at 2.71, 20 at 2.76, 33 at 2.81, 50 at 2.85, 82 at 2.90, 468 at 3.43 (then held). So: a short ease-in (~0.25 s),
then exponential at about 9 per second (the height doubles every ~0.075 s, x1.5 per frame-ish) up to ~80 blocks, then
easing out to ~480 by ~3.4 s. **About one second from the ground to above the clouds.** The pitch tightens from 55 to
87 degrees (straight down), the FOV narrows from 70 to 64, and the camera drifts ~20 blocks off the mark sideways as it
rises (the mark slides a little in the frame). From 3.9 s to 17 s the logged camera sits parked at 480 blocks above
the mark: TSS draws its whole space part as its own scene, so only the frames show it.

What this means for Proxima's film (previz shot 2, "Leaving Earth", 1.6-5.4 s, one continuous 3.8 s pull): the
Railgun's rise is much faster (about 1 s through the clouds) and its way to orbit is two shots (the rise, then a cut
to Earth from orbit filling the frame, then Earth small). Matching it means a fast exponential rise looking straight
down, through a soft cloud layer, then the orbit shot. **Decided 2026-11-06: the Railgun's pace** (the user left it
to Claude; they had asked for a zoom-out "just like railgun"). Shot 2 keeps its 3.8 s slot (1.6-5.4 s), so the 31 s
timeline doesn't move, filled the Railgun way: a whip turn to look straight down (~0.15 s), the rise (~1.2 s: ease in,
exponential, easing out above the clouds; pitch to straight down, FOV 70 to ~64, a little sideways drift), a hard cut
to Earth from orbit filling the frame, then pulling back until the curve of the Earth sits in the frame (the rest of
the slot), then shot 3 as planned.

## Sizes (`ProximaParams`, at power 1; the power slider scales the radii, the depth by its square root)

- bowl radius 36 (24 until the user asked for "decently larger", 2026-11-05), depth 6 (decided): tinted glass and obsidian in the middle, magma threads, blackstone toward the rim;
- char radius 100 (56): trunks to basalt, the ground to blackstone, basalt and coarse dirt;
- scorch radius 220 (110): leaves, grass, flowers, snow, vines and crops burn away, the topsoil to coarse dirt (dirt at the
  edge); the edges are ragged (`ProximaPlan.edge`).
- Buildings stand outside the bowl (only natural ground is scorched, logs only charred). Water and lava are left.

## Code map

- `ProximaPlan`, `ProximaParams` (root package): timeline, sizes (sent in the effect packet), the scorch's shape.
- `world/ProximaInfo` (id, colour, key Z, cooldown), `world/ProximaCasting` (the button), `world/ProximaStrike` (the
  flash burn at FLASH; at IMPACT the tiers, knockback, shelter, the bowl's lazy carving through `StarBridge.excavate`),
  `world/ProximaLand` (the scorch, chunk by chunk, 12 ms a tick, loaded land only; the rest as it loads, for 20
  minutes, `ProximaStrike.tickLingering` from the server tick). Damage type `proxima_flare` (burning, bypasses armour
  and shields).
- `compat/tss/ProximaSkill`, `ProximaSpell`; `TssLink` joins it to the remote after Luna and routes its casts;
  `StarLink`/`StarBridge` `startProxima`, `proximaRunning`.
- Client: `client/proxima/ProximaFx` (tick: sounds and particles; screen: flash, red sky, hush, impact frames, shake),
  `compat/tss/client/ProximaFxAdapter` (one of the remote's effects; reuses Luna's `MoonFx.Host` and the adapter's
  `Screen`), routed in `StarClientBridge`.
- Icon `art/make_proxima_icon.sh` (ffmpeg): a red dwarf in a broken Dyson lattice, the beam leaving it.
- Rules (CLAUDE.md §0, §9): never TSS's assets; the user's OST has full permission; free imagery for the Earth only,
  credited.

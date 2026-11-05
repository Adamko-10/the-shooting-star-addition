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
   Part 3, Earth, still to do (ask before downloading NASA imagery).
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
   `IMPACT`; then config (the §15 list), random flares (off by default), and the title card.

## Timeline (`ProximaPlan`, ticks at speed 1)

| | tick | |
|---|---|---|
| PRESS | 7 | the remote's button |
| FLASH | 90 | phase 1: the flare's light arrives; exposed things burn |
| FRONT | 100 | phase 2: the coronal front crosses the sky (looks only) |
| HUSH | 260 | near-silence: everyone thinks it's over |
| IMPACT | 270 | phase 3: ground zero |
| DURATION | 600 | the effects end |

The intro film (stage 3) will run in the first 90 ticks. Stellar Convergence uses `IMPACT` to land it with the others.

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

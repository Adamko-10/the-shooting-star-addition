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
2. The coronal front: the 3D ring sweeping across the sky (thin bright shock + translucent sheath with filaments), the
   Type II radio burst whine drifting down, compasses spinning, redstone sparks; ground zero's look (white point,
   cracks of light, shock ring, heat shimmer); steam off exposed water.
3. The film: the intro to space (title card "DYSON SPHERE", the lattice opening like an iris, the infinity-sign loops,
   "CORONAL MASS EJECTION" in plasma filaments), the lens beam and the gate near Earth, the pullback afterwards.
   RimuLib's film tools (`CameraRig`, `Shot`, `Subject`, `CutsceneDirector`), our own Earth shader (free imagery only).
4. The aftermath: the Solar Residue block, the ash layer, the radiation zone (shimmer, hum, damage over time, fading
   by default), auroras for a night or two, the "radio blackout" on the skill's own HUD text.
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

- bowl radius 24, depth 6 (decided): tinted glass and obsidian in the middle, magma threads, blackstone toward the rim;
- char radius 56: trunks to basalt, the ground to blackstone, basalt and coarse dirt;
- scorch radius 110: leaves, grass, flowers, snow, vines and crops burn away, the topsoil to coarse dirt (dirt at the
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

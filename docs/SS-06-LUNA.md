# SS-06 · Luna: the moonfall

The second skill this addon adds to the Stellar Remote (after SS-05 Halley). This is the design. Every loader/version
port of the addon implements the same thing, so read this before porting it.

## What the player sees

At night, with the Stellar Remote: press **J** (or pick SS-06 in the remote's menu) and aim at the ground.

1. **ARM / PRESS (0-7):** the remote's cover flips, the button goes down (The Shooting Star's own clicks).
2. **ALARM (10):** **"EARTH SYSTEM SHUT DOWN"** slams across the screen in big red letters with a glitch/scanline
   look, over a siren (`luna_alarm`). Other players nearby get it in their action bar. The night sky starts to turn a
   sick red-orange. The mark appears on the ground (gold).
3. **BREAK (50-90):** the moon, *exactly where the vanilla moon hangs in the sky* (same direction, same apparent
   size), shudders. Glowing molten seams crack open across it (`luna_crack`). The vanilla moon is hidden from here on
   (it is replaced by ours).
4. **FALL (90-320):** it leaves its place and comes down a straight line onto the mark, slow at first and then
   faster and faster (`MoonPlan.fall`, ease 2.6), slowly turning, growing until it fills the sky. A long rising roar
   (`luna_fall`). The ground starts to rumble (camera shake grows). Fragments break off and stream behind it.
5. **ENTRY (250-320):** it hits the atmosphere: a burning orange-white shroud and a bow shock on its leading face,
   flames streaming back up its path, the whole sky lit orange (`luna_entry`).
6. **CONTACT (320):** its lowest point touches the mark. White flash, impact frames, huge camera shake, a shock
   ring racing out along the ground, a dome of dust, rock and cheese chunks thrown out on long arcs, `luna_impact`.
   The server erases everything in the crater and blasts what is beyond it.
7. **SETTLE (320-360):** it ploughs on down into the crater it digs and comes to rest, part buried (`luna_settle`).
   The server has the crater cut and the moon built out of **moon cheese**, with **one block of molten moon cheese**
   at its very centre.
8. **AFTERMATH (360-600):** dust hangs and drifts, debris rains down (`luna_debris`), embers glow on the moon's
   surface, the sky slowly returns to night.

The caster gets a cutscene (like SS-05's), skippable with the remote's cutscene key. Everyone else watches it live.

## Rules

- **Night only** (config `night_only`, default true): `ServerLevel.isNight()`; in dimensions with fixed time
  (Nether/End) it refuses ("there is no moon here").
- One moonfall per caster at a time; cooldown 120 s (config).
- The mark is placed the way the remote's other skills aim (`StarBridge.aimGround`).

## Geometry (all in `MoonPlan`, shared by server and client)

- `sky`: unit vector toward the vanilla moon at cast time (`MoonPlan.skyDirection(level.getTimeOfDay(1))`), lifted
  to at least 18 degrees above the horizon. Sent to clients inside `MoonParams`.
- The moon (radius `moon_radius`, default 32) starts `START_DISTANCE` (3200) blocks up `sky` from the mark, falls
  to `contactCentre` (its surface touching the mark), then sinks to `restCentre` (centre `moon_radius*(1-2*sink)`
  above the ground; `sink_percent` default 35).
- Crater: radius `crater_radius` (60), depth `crater_depth` (22), bowl shape `MoonPlan.craterFloor`. Everything
  within `eraseRadius()` is erased at CONTACT; the shock wave reaches `blastRadius()` (crater radius × 2.5).
- Moon at rest: every block whose centre is within `moon_radius` of `restCentre` becomes moon cheese, and the block
  `core` (holding `restCentre`) becomes molten moon cheese (if `molten_core`). Blocks above the world's build height
  are skipped.

## Server (world/MoonStrike)

- ALARM: action-bar alert to players in range; EVAC: lift a creative caster out of the crater (like SS-05).
- CONTACT: erase living things within `eraseRadius()` (`StarBridge.erase`), hurt/throw/set on fire things out to
  `blastRadius()`; start cutting the crater and building the moon, a few chunks per tick (`chunks_per_tick`),
  nearest the mark first, via The Shooting Star's fast carver/painter (`StarBridge.excavate` / `StarBridge.paint`).
- If the spell is cut short, finish the build at once (like SS-05's `end()`).
- `carve_terrain = false`: no blocks change, creatures still get hit.

## Moon cheese

- **Moon cheese** (block + edible block item): right-click a block to place it, right-click in the air to eat it.
  Food like a golden carrot (6 hunger, 14.4 saturation).
- **Molten moon cheese** (block + edible block item, light 15, emissive): eating it gives, for `molten_minutes`
  (10): **`molten_hearts` (500) hearts** (max health raised to hearts × 2 for the duration, healed to full) and
  **Strength `molten_strength` (255)**, i.e. amplifier 254. Shown as the "Molten Might" effect.
  Always edible (like a golden apple).
- Both mine with a pickaxe or by hand and drop themselves.

## Content names (fixed; other code relies on them)

Blocks `moon_cheese`, `molten_moon_cheese` (+ their items). Effect `molten_might`. Sounds `luna_alarm`,
`luna_crack`, `luna_fall`, `luna_entry`, `luna_impact`, `luna_settle`, `luna_debris`. Skill id `luna`, card
`assets/shooting_star_demo/textures/gui/sprites/skill/stellar_remote/luna.png` (64×32 like `halley.png`). Moon
surface textures for the sky moon: `assets/shooting_star_addition/textures/luna/surface.png` (albedo, equirectangular
2:1) and `assets/shooting_star_addition/textures/luna/seams.png` (molten seams, emissive mask).

## Code map

| What | Where |
|---|---|
| Timeline, geometry | `MoonPlan` |
| Sizes sent with the strike | `MoonParams` |
| Config | `MoonConfig` (sections `moonfall*`, `moon_cheese` in `shooting_star_addition-common.toml`) |
| Name, colour, key, alert text | `world/MoonInfo` |
| Casting (night check, aim) | `world/MoonCasting` |
| What it does to the world | `world/MoonStrike` |
| Remote entry, spell | `compat/MoonSkill`, `compat/MoonSpell`; registered with SS-05 in `compat/StarBridge` |
| Client effects | `client/luna/` (`MoonFx`, rendering, HUD), `client/film/MoonStoryboard`, `compat/client/MoonFxAdapter`, `MoonCutscene` |
| Blocks, items, effect, sounds | `content/` |

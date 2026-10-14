# SA-02 · Luna: the moonfall

The second skill this addon adds to the Stellar Remote (after SA-01 Halley). This is the design. Every loader/version
port of the addon implements the same thing, so read this before porting it. (The file keeps its old name, from when
the skills were numbered after The Shooting Star's own.)

## What the player sees

With the Stellar Remote, by day or night: press **N** (or pick SA-02 in the remote's menu) and aim at the ground.
Times are ticks since the button (`MoonPlan`).

1. **ARM / PRESS (0-7):** the remote's cover flips, the button goes down (The Shooting Star's own clicks).
2. **ALARM (10):** **"EARTH SYSTEM SHUT DOWN"** slams across the screen in big red letters with a glitch/scanline
   look, over a siren (`luna_alarm`). Other players nearby get it in their action bar. The night sky starts to turn a
   sick red-orange. The mark appears on the ground (gold).
3. **BREAK (45-85):** the moon, *exactly where the vanilla moon hangs in the sky* (same direction, same apparent
   size), shudders. Glowing molten seams crack open across it (`luna_crack`). The vanilla moon is hidden from here on
   (it is replaced by ours).
4. **FALL (85-340):** it leaves its place and comes down a straight line onto the mark, slow at first and then
   faster and faster (`MoonPlan.fall`, ease 2.6), slowly turning, growing until it fills the sky. A long rising roar
   (`luna_fall`). The ground starts to rumble (camera shake grows). Fragments break off and stream behind it.
5. **ENTRY (260-340):** it hits the atmosphere: a burning orange-white shroud and a bow shock on its leading face,
   flames streaming back up its path, the whole sky lit orange (`luna_entry`).
6. **CONTACT (340):** its lowest point touches the mark. White flash, impact frames, huge camera shake, a shock
   ring racing out along the ground, jets of fire squirting out from under it, a dome of dust, rock and cheese chunks
   thrown out on long arcs, `luna_impact`. The server erases everything in the impact zone, blasts what is beyond it
   and digs the crater.
7. **SETTLE (340-410):** it drives on down into the crater and comes to rest, most of it buried (`luna_settle`),
   while the crust buckles up against it and the crater's rim heaves up and cracks race out across the land. The
   server builds the moon out of **moon cheese**, with **one block of molten moon cheese** at its very centre.
8. **AFTERMATH (410-680):** dust hangs and drifts, debris rains down (`luna_debris`), smoke rises off the burning
   crater floor, the molten seam round the moon cools from white to dull red, the sky slowly returns to night.

The caster gets a cutscene (like Halley's), skippable with the remote's cutscene key. Everyone else watches it live.

## Rules

- **Any time of day** (config `require_night`, default false; it was `night_only` in 1.2.0 and was renamed so old
  config files don't keep the old behaviour): like Halley, the client turns the sky to night for the strike (only the
  look; under shader packs the sky clock is wound on). By day the moon starts where the sun is
  (`skyDirection(timeOfDay + 0.5)`). In dimensions with fixed time
  (Nether/End) it refuses ("there is no moon here").
- One moonfall per caster at a time; cooldown 120 s (config).
- The mark is placed the way the remote's other skills aim (`StarBridge.aimGround`).

## Geometry (all in `MoonPlan`, shared by server and client)

- `sky`: unit vector toward the vanilla moon at cast time (`MoonPlan.skyDirection(level.getTimeOfDay(1))`), lifted
  to at least 18 degrees above the horizon. Sent to clients inside `MoonParams`.
- The moon (radius `moon_radius`, default 48) starts `START_DISTANCE` (3200) blocks up `sky` from the mark, falls
  to `contactCentre` (its surface touching the mark), then drives down to `restCentre` (centre
  `moon_radius*(1-2*buried)` above the old ground; `buried_percent` default 70), easing out hard (`MoonPlan.settle`).
- The crater, round the mark (`r` = the moon's radius):
  - its floor is dug `craterDepth` = `min(0.34 r, 0.6 r buried)` under the old ground next to the moon, rising to
    the old ground at the rim's crest (`floorDepth`, `floorY`: a true bowl near the moon, the land's own lie further
    out); burnt black and glowing near the moon, scorched rock and rubble further out (`MoonStrike`'s floor palettes);
  - the moon meets the floor at `restFootprint`; a molten seam (magma and lava, `seamWidth`, a trench 2 deeper)
    runs round it out to `floorInner`, where the floor starts;
  - the rim's crest is at `impact_radius` (`rimCrest`, default 110: the impact zone): its inner wall rises out of the
    floor from `rimInner`, its outer slope falls away to the old ground by `rimOuter` (crest + 40%), in upturned
    plates (`crestPlates`) `rim_height` (9) tall at the crest, with a broken spill of thrown ground past it;
  - slabs of crust buckle up against the moon over the floor (`rimPlates`, out `plateReach` = 0.32 r from the seam,
    up to 0.8 × `rim_height` over the floor); all in `rimHeight`, heaved up in jolts (`rimStep`) while the moon
    drives in.
- Everything within `eraseRadius()` (the rim's crest + 2) is erased at CONTACT; the shock wave reaches
  `blastRadius()` (impact radius × `blast_reach`, 3).
- Cracks: `MAIN_CRACKS` (16) fissures out of the rim, with branches, out to about the blast radius, and
  `FLOOR_VEINS` (12) shallow veins of molten rock across the crater's floor, along `crackPoint`/`crackHalfWidth`,
  racing out over `CRACK_TICKS` (26) from CONTACT. The server cuts them (`world/MoonCracks`: magma floor, scorched
  walls, loaded chunks only); the client draws them glowing along the same lines.
- Debris: `debrisCount()` (90-160) lumps of moon cheese and crust thrown out, landing 1.05-2.65 impact radii out.
- Moon at rest: every block whose centre is within `moon_radius` of `restCentre` becomes moon cheese (about one in
  eight holey), and the block `core` (holding `restCentre`) becomes molten moon cheese (if `molten_core`). Blocks
  outside the world's build height are skipped.

## Server (world/MoonStrike)

- ALARM: action-bar alert to players in range; EVAC: lift a creative caster out of the crater (like Halley).
- CONTACT: erase living things within `eraseRadius()` (`StarBridge.erase`), hurt/throw/set on fire things out to
  `blastRadius()`; dig the crater (`crater`/`craterSurface`), a few chunks per tick (`chunks_per_tick`), nearest the
  mark first, via The Shooting Star's fast carver/painter (`StarBridge.excavate` / `StarBridge.paint`). Once it is
  dug, the rim and the slabs heave up (`world/MoonRim`) and the cracks run out (`world/MoonCracks`).
- SETTLE: build the moon (`world/MoonBuild`), once the crater is dug. When everything is cut and built, the debris
  is left where the client saw it land (`scatterDebris`).
- If the spell is cut short, finish the build at once (like Halley's `end()`).
- `carve_terrain = false`: no blocks change, creatures still get hit.

## Moon cheese

- **Moon cheese** (block + edible block item): right-click a block to place it, right-click in the air to eat it.
  Food like a golden carrot (6 hunger, 14.4 saturation).
- **Holey moon cheese** (block + edible block item): moon cheese with the cheese's eyes in it (three textures, each
  turned at random), about one block in eight of the moon (`MoonBuild.HOLEY`) and a fifth of the cheese debris.
  Otherwise it is moon cheese. (Plain moon cheese's own texture has no holes, and is turned at random too.)
- Eating any moon cheese first clears your effects like milk (`CheeseItem`: on NeoForge the ones milk cures, on
  Forge 1.20.1 those a milk bucket cures, elsewhere all of them, as vanilla milk does); molten moon cheese's own
  effects are given after that, so they stay.
- **Molten moon cheese** (block + edible block item, light 15, emissive): eating it gives, for `molten_minutes`
  (10): **`molten_hearts` (500) hearts** (max health raised to hearts × 2 for the duration, healed to full) and
  **Strength `molten_strength` (255)**, i.e. amplifier 254. Shown as the "Molten Might" effect.
  Always edible (like a golden apple), and fills hunger and saturation to the top (20 / 20).
- Both mine with a pickaxe or by hand and drop themselves.

## Content names (fixed; other code relies on them)

Blocks `moon_cheese`, `holey_moon_cheese`, `molten_moon_cheese` (+ their items). Effect `molten_might`. Sounds `luna_alarm`,
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
| Remote entry, spell | `compat/tss/MoonSkill`, `compat/tss/MoonSpell`; registered with Halley in `compat/tss/TssLink` |
| Client effects | `client/luna/` (`MoonFx`, rendering, HUD), `client/film/MoonStoryboard`, `compat/tss/client/MoonFxAdapter`, `MoonCutscene` |
| Blocks, items, effect, sounds | `content/` |

## Fabric 1.21.1

Ported from `fabric-1.21.11` (the Fabric version of `main`) back to Minecraft 1.21.1, for 1.4.0. What's different
from `main`:

- **The Shooting Star's Fabric download is one bundle**, with a jar nested in it for every Minecraft version it
  supports. The build takes this version's (`tssNested` in `build.gradle`) by its whole name - `+mc1.21.1` is also
  the start of `+mc1.21.11`, and picking that one compiles against the wrong build.
- **Loom 1.18 runs on JDK 25**; the code itself is compiled for Java 21 through a Gradle toolchain.
- **Mojang's names**, through `loom.officialMojangMappings()`: the code reads like `main`'s; Loom remaps the release
  jar to intermediary names, the descriptors in the mixins into The Shooting Star included.
- **`RenderType.create` isn't public** in vanilla (NeoForge widens it): an access widener does it here
  (`shooting_star_addition.accesswidener`, declared in `fabric.mod.json`).
- **The creative tabs' fields are private** in vanilla: the tabs are looked up by `ResourceKey` from their ids.
- **`javax.annotation.Nullable`** comes from JSR-305 (`compileOnly`): Fabric's Minecraft classpath doesn't carry it.
- **Fabric API's `WorldRenderContext`** on 1.21.1: `camera()`, `projectionMatrix()`, `positionMatrix()`,
  `tickCounter()`.

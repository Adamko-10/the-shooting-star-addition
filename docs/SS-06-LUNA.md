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
3. **BREAK (30-50):** the moon, *where the vanilla moon hangs in the sky* (same direction, same apparent
   size), shudders. Glowing molten seams crack open across it (`luna_crack`). The vanilla moon is hidden from here on
   (it is replaced by ours).
4. **FALL (50-200):** it tears loose and comes down a straight line onto the mark, steadily, slowly turning,
   visibly growing (`MoonPlan.fall`: 30% of the way by ENTRY, its speed still rising). It is always drawn at its true
   size and place (`MoonSphere.placement`), so it looks the same from every camera and at every power. From TRAIL
   (FALL + 35) a pale trail of moon dust and glinting chips streams behind it (`MoonVisuals.approachTrail`). A long
   rising roar (`luna_fall`). The ground starts to rumble (camera shake grows).
5. **ENTRY (200-330):** it hits the atmosphere and dives, faster and faster (the rest of the way, its speed rising
   about fourfold): its leading face turns molten orange (still rock: its craters stay readable), its seams blaze, a
   shell of fire wraps its leading edge, a bow shock stands off it, tongues of flame peel off its edge, and the dust
   trail gives way to a long blazing wake; fragments break off and burst; the whole sky lit orange (`luna_entry`).
6. **CONTACT (330, the same time as Halley's impact):** its lowest point touches the mark. White flash, impact frames, huge camera shake, a shock
   ring racing out along the ground, jets of fire squirting out from under it, a dome of dust, rock and cheese chunks
   thrown out on long arcs, `luna_impact`. The server erases everything in the impact zone, blasts what is beyond it
   and digs the crater.
7. **SETTLE (330-400):** it drives on down into the crater and comes to rest, most of it buried (`luna_settle`),
   while the crust buckles up against it and the crater's rim heaves up and cracks race out across the land. The
   server builds the moon out of **moon cheese**, with **one block of molten moon cheese** at its very centre.
8. **AFTERMATH (400-670):** dust hangs and drifts, debris rains down (`luna_debris`), smoke rises off the burning
   crater floor, the molten seam round the moon cools from white to dull red, the sky slowly returns to night.

The caster gets a cutscene (like Halley's), skippable with the remote's cutscene key; it ends at SETTLE + 95. Over
the impact it runs in slow motion (`MoonFx.slowLag`: a fifth of the speed for about two seconds, caught up by
CONTACT + 130; the strike itself, the music and the screen effects keep real time). Everyone else watches it live.

**Music** (`luna_music.ogg`, a `sounds.json` entry, no registered SoundEvent): the user's track (`Video-Project-48.mp3`,
its drop at 24.45 s), cut 8.45 s in so its drop lands on CONTACT, 16.0 s after ALARM - never time-stretched (1.5.0's
stretched cut sounded bad). `MoonFx.Music` starts it at ALARM (`MUSIC_AT`) on the Music volume slider (each player's
`luna_music` switch, **M** in the remote's menu), pitched with the speed slider, and nudges its speed by up to 5% every
tick to stay in step with the strike's clock. If the timeline changes, re-cut it from the original: `ffmpeg -ss <24.45 -
(CONTACT - MUSIC_AT) / 20> -i <source> -vn -af "afade=t=in:st=0:d=0.6,volume=13dB,alimiter=limit=0.95:level=disabled"
-ac 2 -ar 44100 -c:a libvorbis -q:a 6 luna_music.ogg`.

## Rules

- **Any time of day** (config `require_night`, default false; it was `night_only` in 1.2.0 and was renamed so old
  config files don't keep the old behaviour): like Halley, the client turns the sky to night for the strike (only the
  look; under shader packs the sky clock is wound on). By day the moon starts where the sun is
  (`skyDirection(timeOfDay + 0.5)`). In dimensions with fixed time
  (Nether/End) it refuses ("there is no moon here").
- One moonfall per caster at a time (unless no-cooldown applies to them, `NoCooldown`); cooldown 120 s (config).
- The mark is placed the way the remote's other skills aim (`StarBridge.aimGround`).
- **The remote's sliders** (power, damage, speed; `CastTuning`, the same three The Shooting Star's own skills have):
  power (0.1-10) scales `moon_radius`, `impact_radius` and `rim_height` for that cast (`MoonParams.of(..., scale)`,
  sent to clients with the rest); damage below ∞ hits creatures in the impact zone for that much instead of erasing
  them (`StarBridge.hit`) and caps the blast's hurt; speed (0.3-3) runs the whole timeline, server and film, at that
  rate (The Shooting Star's spell engine; the film's clock is `Host.clock`, its sounds are pitched with it).

## Geometry (all in `MoonPlan`, shared by server and client)

- `sky`: unit vector toward the vanilla moon at cast time (`MoonPlan.skyDirection(level.getTimeOfDay(1))`), lifted
  to at least 18 degrees above the horizon. Sent to clients inside `MoonParams`.
- The moon (radius `moon_radius`, default 48) starts `moon_radius / START_RATIO` (ten radii) up `sky` from the
  mark - so it looks as big as the vanilla moon whatever its size - falls to `contactCentre` (its surface touching the mark), then drives down to `restCentre` (centre
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
    up to 0.8 × `rim_height` over the floor); all in `rimHeight`, heaved up in jolts while the moon drives in
    (each chunk's columns together, `MoonLand.JOLTS`, timed by `rimStep` at the chunk's middle).
- Everything within `eraseRadius()` (the rim's crest + 2) is erased at CONTACT; the shock wave reaches
  `blastRadius()` (impact radius × `blast_reach`, 3).
- Cracks: `MAIN_CRACKS` (16) fissures out of the rim, with branches, out to about the blast radius, and
  `FLOOR_VEINS` (12) shallow veins of molten rock across the crater's floor, along `crackPoint`/`crackHalfWidth`,
  racing out over `CRACK_TICKS` (26) from CONTACT. The server cuts them (`world/MoonLand`: magma floor, scorched
  walls; the veins with the crater's floor itself, `world/MoonVeins`); the client draws them glowing along the same
  lines.
- Debris: `debrisCount()` (90-160) lumps of moon cheese and crust thrown out, landing 1.05-2.65 impact radii out.
- Moon at rest: every block whose centre is within `moon_radius` of `restCentre` becomes moon cheese (about one in
  eight holey), and the block `core` (holding `restCentre`) becomes molten moon cheese (if `molten_core`). Blocks
  outside the world's build height are skipped.

## Server (world/MoonStrike)

- ALARM: action-bar alert to players in range; EVAC: lift a creative caster out of the crater (like Halley).
- CONTACT: erase living things within `eraseRadius()` (`StarBridge.erase`), hurt/throw/set on fire things out to
  `blastRadius()`; dig the crater (`crater`/`craterSurface`), a few chunks per tick (`chunks_per_tick`), nearest the
  mark first, via The Shooting Star's fast carver/painter (`StarBridge.excavate` / `StarBridge.paint`), in two parts:
  round the moon, out past the slabs (`coreRadius`), loaded as it is cut (`Excavation.Loader`, radius-0 tickets); the
  rest of the floor, out to the rim's inner wall, only where land is loaded (a lazy carving, handed to The Shooting
  Star's lingering carvings at the end, so it finishes as land loads). Once the part round the moon is dug, the rim
  (its inner wall cut down to the floor first), the slabs, the cracks and the debris are done chunk by chunk
  (`world/MoonLand`): animated where loaded, the other chunks in one go as they load, after the strike
  (`MoonLand.tickLingering`, every server tick). No land is ever loaded or generated for anything outside the moon's
  own crater.
- **Every chunk written is relit and resent whole** (The Shooting Star's `Carving.paint`; the client rebuilds all of
  it), so each chunk is written as few times as it can be: the rim in `JOLTS` (3) jolts per chunk, its cracks at most
  every `PAINT_EVERY` (6) ticks, at most `MAX_PAINTS` (12) chunks a tick, the moon in one pass per chunk (at most
  15 ms a tick), and the crater resent once, 60 ticks after everything is done. (1.5.0 heaved the rim column by column,
  which resent most of its chunks every tick for seconds: the lag players felt.)
- SETTLE: build the moon (`world/MoonBuild`), once the crater round it is dug: each column as a height range, each
  chunk once, waiting for chunks through the loader rather than loading them on the spot.
- If the spell is cut short, finish the crater round the moon and the moon at once (like Halley's `end()`); the rest
  lingers.
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
| The rim, slabs, cracks, debris; the floor's veins | `world/MoonLand`, `world/MoonVeins` |
| Client effects | `client/luna/` (`MoonFx`, rendering, HUD), `client/film/MoonStoryboard` (+ `FilmLand`), `compat/tss/client/MoonFxAdapter`, `MoonCutscene` |
| Blocks, items, effect, sounds | `content/` |

## Forge 1.20.1

Ported from `main` (NeoForge 1.21.1) onto the `forge-1.20.1` branch, after SS-05 Halley had already been ported there.
The design above is identical; only these things changed, all the usual 1.21.1 → 1.20.1 / NeoForge → Forge
differences already documented for SS-05 (see `CLAUDE.md` §0), applied the same way to SS-06's own files:

- **The Strength amplifier (vanilla quirk, server-side):** `MobEffectInstance` saves its amplifier as a signed byte
  in 1.20.1 (1.21 saves an int). Strength's amplifier for Molten Might is 254 (`molten_strength` 255 by default),
  which overflows a byte to -2 after a world save/reload. The attack-damage boost itself is unaffected (vanilla's own
  `MobEffect.addAttributeModifiers` adds Strength's attribute modifier as a permanent one, saved and restored
  independently of the amplifier), but anything reading `MobEffectInstance.getAmplifier()` afterwards would see -2.
  Worked around in `content/MoltenMightEffect.java`: Molten Might ticks once a second and, if the Strength effect's
  amplifier has gone negative, re-applies Strength fresh at the configured amplifier with its current remaining
  duration.
- **No `onEffectStarted` (server-side):** that hook is a later addition; 1.20.1's `MobEffect` doesn't have it. But
  unlike the 1.21.1 original, 1.20.1's `addAttributeModifiers` is handed the `LivingEntity` directly, so the "heal to
  the new full health" step that lived in `onEffectStarted` on `main` is folded into `addAttributeModifiers` instead
  (`content/MoltenMightEffect.java`). It still runs every time the effect (re)starts, after the max-health modifier
  has been (re)applied, so "full health" already means the raised max.
- **The vanilla moon-hiding mixin (client-side, `compat/mixin/LevelRendererMoonMixin.java`):** same redirect
  (`LevelRenderer.renderSky`, the third `BufferUploader.drawWithShader` call, `ordinal = 2` - the sunset glow fan,
  the sun, then the moon), but the type redirected is different: 1.21.1's `BufferBuilder.end()` returns a `MeshData`
  (closed with `.close()`); 1.20.1's returns a `BufferBuilder.RenderedBuffer` (released with `.release()`). The
  redirect's target descriptor changed accordingly
  (`Lcom/mojang/blaze3d/vertex/BufferUploader;drawWithShader(Lcom/mojang/blaze3d/vertex/BufferBuilder$RenderedBuffer;)V`).
  `require = 0`, as with every other optional mixin: if Minecraft's own sky rendering changes shape, the vanilla
  moon just shows up alongside SS-06's own, a visual nit rather than a crash.
- **Model-view handling for every SS-06 draw (client-side, `client/luna/render/`):** `MoonRenderer` (the glow:
  entry shroud, fragments, the detonation, embers - same `halley_glow` shader and shapes SS-05's comet uses, only the
  tint differs) and `MoonSphere` (the solid, lit, textured moon, drawn with vanilla's `entity_cutout_no_cull` /
  `entity_translucent_emissive` render types) both build camera-relative vertices, same as SS-05's `CometRenderer`.
  In 1.20.1, `RenderLevelStageEvent.getPoseStack()` holds the camera's rotation at every stage, but the shared
  model-view (`RenderSystem.getModelViewStack()`) is back to identity by `AFTER_PARTICLES`/`AFTER_ENTITIES`. So both
  push the event's pose onto the model-view stack around their draws (`view.mulPoseMatrix(event.getPoseStack().last().pose())`
  before, `view.popPose()` after), exactly as `CometRenderer`/`HalleySky` do, and draw with
  `RenderType.end(bufferBuilder, RenderSystem.getVertexSorting())` rather than building a `MeshData` and calling
  `RenderType.draw(mesh)`.
- **Vertex building (client-side, `client/luna/render/`):** 1.21's `BufferBuilder.addVertex(x, y, z).setUv(u, v)
  .setColor(...)` (etc.) became 1.20.1's `BufferBuilder.vertex(x, y, z).uv(u, v).color(...)....endVertex()`. 1.20.1's
  `BufferBuilder` is a single reused, growable buffer per draw site (`begin(mode, format)` / `end()`), not a fresh
  one built from a `ByteBufferBuilder` each frame; `begin()` throws if the previous `end()` was skipped, so both
  `MoonRenderer.render` and `MoonSphere.renderSolid` always call `end()` (through `RenderType.end`) even when nothing
  was added that frame - it no-ops the draw itself on an empty buffer.
- **The sky palette uniforms (client-side, `client/sky/HalleySky.java`, `halley_sky.fsh`/`.json`):** unchanged by
  the loader port itself, but worth noting here since it is the one piece SS-06 added to a file SS-05 already owned.
  `SkyLook` gained a `palette` field (`PALETTE_HALLEY` / `PALETTE_LUNA`); the dark sky's and haze's colours became
  shader uniforms (`NightZenith`/`NightMiddle`/`NightHorizon`/`VeilTop`/`VeilHorizon`) set per strike instead of GLSL
  constants, so SS-06's sick red-orange night and warm dusty haze can share the dome and the shader with SS-05's icy
  blue one. `HalleySky.fog()` picks the same per-palette horizon colours so the fog still matches the dome exactly.
- **Not checked (needs a real client, see `CLAUDE.md` §0):** anything visual - the moon sphere's look and scale as
  it falls, the entry shroud and fragments, the impact, the vanilla-moon-hiding mixin actually firing, the alarm HUD
  and marker, the cutscene, the raised-arm pose fallback in `PlayerModelMixin`, SS-06's palette under a shader pack
  (Oculus), and sounds.

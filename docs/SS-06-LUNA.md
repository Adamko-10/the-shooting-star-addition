# SS-06 · Luna: the moonfall

The second skill this addon adds to the Stellar Remote (after SS-05 Halley). This is the design. Every loader/version
port of the addon implements the same thing, so read this before porting it.

## What the player sees

With the Stellar Remote, by day or night: press **Y** (or pick SS-06 in the remote's menu) and aim at the ground.

1. **ARM / PRESS (0-7):** the remote's cover flips, the button goes down (The Shooting Star's own clicks).
2. **ALARM (10):** **"EARTH SYSTEM SHUT DOWN"** slams across the screen in big red letters with a glitch/scanline
   look, over a siren (`luna_alarm`). Other players nearby get it in their action bar. The night sky starts to turn a
   sick red-orange. The mark appears on the ground (gold).
3. **BREAK (60-120):** the moon, *exactly where the vanilla moon hangs in the sky* (same direction, same apparent
   size), shudders. Glowing molten seams crack open across it (`luna_crack`). The vanilla moon is hidden from here on
   (it is replaced by ours).
4. **FALL (120-480):** it leaves its place and comes down a straight line onto the mark, slow at first and then
   faster and faster (`MoonPlan.fall`, ease 2.6), slowly turning, growing until it fills the sky. A long rising roar
   (`luna_fall`). The ground starts to rumble (camera shake grows). Fragments break off and stream behind it.
5. **ENTRY (380-480):** it hits the atmosphere: a burning orange-white shroud and a bow shock on its leading face,
   flames streaming back up its path, the whole sky lit orange (`luna_entry`).
6. **CONTACT (480):** its lowest point touches the mark. White flash, impact frames, huge camera shake, a shock
   ring racing out along the ground, a dome of dust, rock and cheese chunks thrown out on long arcs, `luna_impact`.
   The server erases everything in the crater and blasts what is beyond it.
7. **SETTLE (480-540):** it ploughs on down into the crater it digs and comes to rest, part buried (`luna_settle`).
   The server has the crater cut and the moon built out of **moon cheese**, with **one block of molten moon cheese**
   at its very centre.
8. **AFTERMATH (540-840):** dust hangs and drifts, debris rains down (`luna_debris`), embers glow on the moon's
   surface, the sky slowly returns to night.

The caster gets a cutscene (like SS-05's), skippable with the remote's cutscene key. Everyone else watches it live.

## Rules

- **Any time of day** (config `require_night`, default false; it was `night_only` in 1.2.0 and was renamed so old
  config files don't keep the old behaviour): like SS-05, the client turns the sky to night for the strike (only the
  look; under shader packs the sky clock is wound on). By day the moon starts where the sun is
  (`skyDirection(timeOfDay + 0.5)`). In dimensions with fixed time
  (Nether/End) it refuses ("there is no moon here").
- One moonfall per caster at a time; cooldown 120 s (config).
- The mark is placed the way the remote's other skills aim (`StarBridge.aimGround`).

## Geometry (all in `MoonPlan`, shared by server and client)

- `sky`: unit vector toward the vanilla moon at cast time (`MoonPlan.skyDirection(level.getTimeOfDay(1))`), lifted
  to at least 18 degrees above the horizon. Sent to clients inside `MoonParams`.
- The moon (radius `moon_radius`, default 48) starts `START_DISTANCE` (3200) blocks up `sky` from the mark, falls
  to `contactCentre` (its surface touching the mark), then sinks to `restCentre` (centre `moon_radius*(1-2*sink)`
  above the ground; `sink_percent` default 35).
- Crater: radius `crater_radius` (90), depth `crater_depth` (30), bowl shape `MoonPlan.craterFloor`. Everything
  within `eraseRadius()` is erased at CONTACT; the shock wave reaches `blastRadius()` (crater radius × 3).
- Cracks: `MoonPlan.CRACKS` (16) fissures along `crackPoint`/`crackHalfWidth`, out to about the blast radius,
  racing out over `CRACK_TICKS` (24) from CONTACT. The server cuts them (`world/MoonCracks`: magma floor, scorched
  walls, loaded chunks only); the client draws them glowing along the same lines.
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

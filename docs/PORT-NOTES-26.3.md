# Port notes: NeoForge 1.21.1 → Fabric 26.3

What changed in the move from the `main` branch (NeoForge 1.21.1) to this one, and what was checked in game. Read
this before the next Minecraft or Shooting Star update.

## The Shooting Star on Fabric 26.3

- Downloaded as **one jar for several Minecraft versions** (`the-shooting-star-demo-1.3.1-fabric.jar`, mod id
  `shooting_star_demo_multi`). The 26.3 build is nested inside it: `META-INF/jars/the-shooting-star-demo-1.3.1-demo+mc26.3.jar`,
  mod id `shooting_star_demo`, package `dev.aek.shootingstardemo` (no `mc1211` part any more). The Gradle build
  extracts that inner jar into `build/shooting-star/` and compiles against it.
- Its versions are SemVer **prereleases** (`1.3.1-demo+mc26.3`). A range like `>=1.3` happens to accept 1.3.1-demo,
  but not 1.3.0-demo; `fabric.mod.json` says `>=1.3.1-demo` so the intent is explicit.
- The skill API is the same shape as 1.21.1: `SkillSet` (`ALL`, `OWNER`, `INDEX`, `skills`, `caster`), `Skill`
  (one new default method, `locksTarget()`), `ActiveSpell`, `SpellEngine`, `SpellUtil`, `Carving`, `Erasure`,
  `GreaterTeleportation`. Only the imports changed in `StarBridge`, `HalleySkill` and `HalleySpell`.
- The client no longer has `Network.clientSpellFx`. Its client initializer registers a Fabric global receiver for
  `SpellFxPayload` that calls `FxManager.start(payload)`. `StarClientBridge.install()` (run at `CLIENT_STARTED`,
  after every mod's client init) unregisters that receiver, keeps it, and registers a wrapper: SS-05's packets start
  `HalleyFx`, everything else goes to the original handler.
- HUD code takes `GuiGraphicsExtractor` instead of `GuiGraphics` (`FxManager.renderFilmHud`, `SpellFx.hud`, `Overlay`).
- Key bindings: `Skill.defaultKey()` is still a capital letter; The Shooting Star turns it into a scancode itself
  (`4 + letter - 'A'`). SS-05 stays on **O**; the remote's menu is **H**, cutscenes **J**.
- **The Shooting Star refuses to run on Vulkan or on a software renderer** (`GpuCheck` → a screen with only an
  "Exit game" button). Players must use *Graphics API → Prefer OpenGL*. SS-05's own drawing works on both backends,
  but that only matters without The Shooting Star.

## Minecraft 26.3

- Unobfuscated: Mojang names everywhere, no mappings, Java 25.
- Rendering is the Renderpearl API with OpenGL and Vulkan backends, reversed-Z depth, and a frame graph. SS-05 draws
  its light at `LevelRenderEvents.END_MAIN` in a render pass of its own (`CometRenderer`), with pipelines compiled
  from `assets/shooting_star_addition/shaders/core/` (`HalleyPipelines`). Uploads happen before a pass opens.
- The sky dome is drawn at the end of `SkyRenderer.render` (`SkyRendererMixin`); the fog colour at the end of
  `FogRenderer.computeFogColor` (`FogRendererMixin`).
- The land's light and the clouds go through **environment attributes**: `ClientLevelMixin` adds time-based layers
  for `SKY_LIGHT_FACTOR`, `SKY_LIGHT_COLOR` and `CLOUD_COLOR`.
- With a shader pack (Iris 1.11.7), unknown pipelines are dropped by Iris's final pass. So with a pack on, the light
  and the sky are painted onto textures before the level renders (`GlowAtlas`, `HalleySky.paint`) and submitted as
  `RenderTypes.eyes(texture)` geometry (both windings: that pipeline culls back faces). The pack's own sky is turned
  to night by winding the sky clock on (`AttributeTrackSamplerMixin`), only on the client and only for the look.

## Checked in game (cloud test rig, software OpenGL, so frame rates there mean nothing)

| Setup | Result |
|---|---|
| Vanilla 26.3 + Fabric API, effects started directly | whole strike draws: night sky, aurora, comet, fireball, plough, detonation, haze, halo |
| + The Shooting Star 1.3.1, cast through the remote (its cast packet, spell engine, effect packet) | joins as skill #4 (index 3), key O, HUD card and art; cutscene, film overlay, impact frames; server carves trench and crater, leaves the heart; evac lift; strike ends cleanly |
| + Iris 1.11.7, Sodium 0.9.3, Complementary Reimagined r5.9.3, with The Shooting Star | runs to the end; light and sky drawn through the pack |
| Vulkan backend (lavapipe), without The Shooting Star | SS-05's drawing works |
| Distant Horizons 3.3.4, and the full 26.3 mod list (Sodium, Sodium Extra, ImmediatelyFast, BadOptimizations, Lithium, C2ME, ModernFix, FerriteCore, Entity Culling, Jade, JEI, Chunky, Iris) | same sky, land light and flashes as vanilla, frame for frame |
| Dedicated server (Fabric, from the 26.3 jar) with The Shooting Star | boots, SS-05 joins on the server; no server class touches client classes (checked in the bytecode) |

Pitfall when comparing screenshots between setups: the test harness has to **hold the strike's clock** at each shot
until a few frames have drawn it. Without that, slow setups capture a different moment (the entry and touchdown flashes
last a few ticks), which once looked like Distant Horizons washing out the sky. It doesn't.

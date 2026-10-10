# Port notes: Forge 1.21.1

Written during the port from `main` (NeoForge 1.21.1) to this `forge-1.21.1` branch. For whoever has to redo this
after a NeoForge or Forge update - what actually differed, verified against the real build.

## Build

- **Plugin**: `net.minecraftforge.gradle` (ForgeGradle 7), `version '[7.0.23,8.0)'`. It's on the Gradle Plugin
  Portal now - no `maven { url = 'https://maven.minecraftforge.net/releases' }` needed in `settings.gradle`
  (unlike ForgeGradle 6 and older).
- **Gradle 9.3.0 or newer is required.** FG7 refuses to apply on anything older with a clear error. The wrapper here
  uses 9.7.1.
- **Forge version**: `52.1.16` (latest 52.1.x for 1.21.1 at the time of this port; `forge_version_range=[52,)` in
  `gradle.properties`).
- **Dependency**: `implementation minecraft.dependency("net.minecraftforge:forge:$minecraft_version-$forge_version")`.
  Calling `minecraft.dependency(...)` a **second time** with the same coordinate (e.g. to add it to the `clienttest`
  source set too) throws `Cannot add task 'slimeLauncherMetadataForForge' as a task with that name already exists` -
  resolve it once, keep the returned `MavenizerInstance`, and reuse that same object for every source set.
- **Repositories**: `minecraft.mavenizer(it)`, `maven fg.forgeMaven`, `maven fg.minecraftLibsMaven`, `mavenCentral()`.
  All four are required or Mavenizer/Forge/MC library dependencies fail to resolve.
- **Mixin**: `annotationProcessor 'org.spongepowered:mixin:0.8.7:processor'`, wired into the jar via the
  `MixinConfigs` manifest attribute (`tasks.named('jar') { manifest { attributes(['MixinConfigs': '...']) } }`) -
  **not** `[[mixins]]` in `mods.toml` (Forge 1.21.1 doesn't read that). Dev runs need
  `args "--mixin.config=${mod_id}.mixins.json"` added explicitly (`minecraft.runs.configureEach { args ... }`);
  nothing adds it for you the way NeoForge's ModDevGradle `[[mixins]]` block did.
- **No refmap needed, confirmed**: Forge 1.21.1 runs with Mojang (official) names at runtime, same as NeoForge
  1.21.1. But the Mixin **annotation processor** still tries to validate mixin targets against an obfuscation
  mapping by default (it auto-discovers `ObfuscationServiceMCP supports [searge,notch]` from somewhere in FG7's
  toolchain) and **fails the build** for any mixin into a vanilla class without `remap = false` - error "Unable to
  locate obfuscation mapping for @Inject target ...". The fix is `remap = false` on every `@Mixin`/`@Inject`/
  `@Redirect`/`@At` that targets a real Minecraft class, exactly like the ones that already targeted The Shooting
  Star did. (A `-AdisableRefMap=true` javac arg does *not* work - the processor doesn't recognize that option in
  this setup.)

## Mod entry point

- Forge only supports **two** mod constructor shapes: no-args, or a single `FMLJavaModLoadingContext` parameter
  (`net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext`) - not NeoForge's three-arg
  `(IEventBus, ModContainer, Dist)`. Get the mod bus from `context.getModEventBus()`, register configs with
  `context.registerConfig(...)` (inherited from `ModLoadingContext`), and get the side from the static
  `net.minecraftforge.fml.loading.FMLEnvironment.dist` field (no constructor injection for `Dist`).
- Event bus: `net.minecraftforge.common.MinecraftForge.EVENT_BUS` (not `NeoForge.EVENT_BUS`).
- **`IEventBus.addListener` has no `(Class<T>, Consumer<T>)` overload.** Only `addListener(Consumer<T>)` (plus
  `EventPriority`-qualified variants). Drop the leading `SomeEvent.class` argument everywhere; for a method
  reference (`Foo::bar`) the compiler infers `T` from the method's own parameter type, so that just works. For an
  inline lambda, the parameter type has to be written explicitly (`(SomeEvent event) -> ...`) since there's no
  longer a `Class<T>` argument to infer it from.

## Config

- `net.neoforged.neoforge.common.ModConfigSpec` → `net.minecraftforge.common.ForgeConfigSpec`. Identical API
  (`Builder.push/pop/comment/define/defineInRange/defineEnum/build`, `IntValue/DoubleValue/BooleanValue/EnumValue`,
  `.isLoaded()`/`.get()`) - a straight rename, nothing else changed.

## Registries

- No `DeferredRegister.Blocks`/`.Items`, `DeferredBlock`, `DeferredItem`, `DeferredHolder`, or `.registerSimpleBlock`/
  `.registerSimpleBlockItem` convenience on Forge - those are NeoForge-only sugar. Use plain
  `net.minecraftforge.registries.DeferredRegister<T>` (`DeferredRegister.create(Registries.BLOCK, modid)` etc. -
  the vanilla `ResourceKey`-based factory works for blocks/items too, not just sounds/effects) and
  `net.minecraftforge.registries.RegistryObject<T>` (`.register(name, supplier)`, `.get()`, `.getId()`).
  `RegistryObject` is **not** itself a `Holder`, unlike NeoForge's `DeferredHolder` - wrap it with
  `BuiltInRegistries.XXX.wrapAsHolder(registryObject.get())` wherever a vanilla API wants a `Holder<T>` (e.g.
  `MobEffectInstance`'s constructor, `LivingEntity.getEffect`/`.removeEffect`).
- `DeferredRegister.Blocks.addAlias(oldId, newId)` (NeoForge-only, used on `main` to keep old `ss05_halley:comet_heart`
  saves working) has no Forge equivalent on the public API (`ForgeRegistry.addAlias` exists but only on the internal
  `ForgeRegistry` class, not the `IForgeRegistry` interface `DeferredRegister` exposes). Dropped for this port -
  there's no pre-existing Forge 1.21.1 world to preserve compatibility with anyway.
- `FoodProperties.Builder.effect(...)` is a **plain vanilla method** taking `(MobEffectInstance, float)` - there is
  no `Supplier`-based overload on Forge (if `main`'s NeoForge code used one, that's a NeoForge patch to this vanilla
  class; Forge's decompiled sources only show the one signature). That means an effect baked in through
  `FoodProperties.Builder` is evaluated once, at class-init/registration time, **before config has loaded** - too
  early to read `MoonConfig.tuning()` live. Molten moon cheese's Molten Might/Strength now come from a small
  `MoltenMoonCheeseItem extends BlockItem` override of `finishUsingItem` instead, which reads the config at the
  moment the player actually eats it (verified by `-Dss05.clienttest.eat=true`: `maxHealth`/`strength`/duration all
  matched the configured defaults, and survived a save/load round trip).

## Client events

Checked against Forge's own 1.21.1 branch source (`MinecraftForge/MinecraftForge`, branch `1.21.1`), not guessed:

- `RenderLevelStageEvent`: same stages, but `getPartialTick()` returns a plain `float` directly (NeoForge wraps it
  in a `DeltaTracker`) - drop the `.getGameTimeDeltaPartialTick(true)` call, just use the float. There is no
  `getModelViewMatrix()`; use `getPoseStack()` instead (returns a `Matrix4f` despite the name, not an actual
  `PoseStack` - deprecated-for-removal in favour of "you shouldn't need it", but it's still the only way to get the
  model-view matrix for positioning world-space draws, and it still works correctly).
- `RenderFrameEvent` (NeoForge-only) doesn't exist on Forge. The once-a-frame "before anything is drawn" hook is
  `net.minecraftforge.event.TickEvent.RenderTickEvent.Pre` instead (`.getTimer()` returns the `DeltaTracker`, same
  class as NeoForge's `event.getPartialTick()` used to return - so `event.getTimer().getGameTimeDeltaPartialTick(true)`
  reads the same as before, just reached through `.getTimer()`).
- `ClientTickEvent`/`ServerTickEvent` (used only by the dev-only clienttest harness) are likewise
  `TickEvent.ClientTickEvent`/`TickEvent.ServerTickEvent` nested classes, not their own top-level NeoForge classes.
- `RegisterShadersEvent`, `ViewportEvent.ComputeFogColor`, `ClientPlayerNetworkEvent.LoggingOut`,
  `BuildCreativeModeTabContentsEvent`, `PlayerEvent.PlayerLoggedInEvent`, `FMLConstructModEvent` all exist on Forge
  1.21.1 with the same shape as NeoForge - package `net.minecraftforge.*`/`net.minecraftforge.fml.*` instead of
  `net.neoforged.neoforge.*`/`net.neoforged.fml.*`, nothing else changed.

## mods.toml

- `[[dependencies.<modid>]]` entries use `mandatory = true/false`, not `type = "required"`.
- No `[[mixins]]` block (see Mixin, above).
- `modId = "forge"` (not `"neoforge"`) for the loader dependency itself.

## Mod dev runs (ForgeGradle 7 / "Slime Launcher")

This is the part that cost the most time, because FG7's run DSL is new and mostly undocumented outside its own
source. Findings, so the next port doesn't have to re-derive them:

- `minecraft { runs { register('client') { ... } } }` only gets a working `mainClass` etc. for the **conventional**
  names the plugin pre-seeds from Forge's own userdev run templates - `client`, `server`, `data`, `gameTestServer`.
  Registering a run under any other name (e.g. `clientTest`, for this addon's dev-only screenshot harness) compiles
  and configures fine, but fails at execution with `Cannot query the value of this provider because it has no value
  available. ... derived from: property 'mainClass'` - there's no template for that name to inherit from, and
  nothing in the public API lets you set `mainClass` to the right bootstrap value by hand (it's internal).
- The actual mechanism for "the same run, but with extra mods/properties/a different working directory for a second
  source set" is `SlimeLauncherOptions.with(SourceSet, Action<SlimeLauncherOptionsNested>)`, inside the `client`
  run's own registration block:
  ```groovy
  register('client') {
      workingDir = layout.projectDirectory.dir('run')
      mods { "${mod_id}" { source sourceSets.main } }
      with(sourceSets.clienttest) {
          workingDir = layout.projectDirectory.dir('run/clienttest')
          mods {
              "${mod_id}" { source sourceSets.main }
              ss05_clienttest { source sourceSets.clienttest }
          }
          systemProperty 'ss05.clienttest', 'true'
          // ...
      }
  }
  ```
  This generates a **second** task, `runClienttestClient` (sourceSet name + run name, not the other way round),
  that reuses `client`'s working `mainClass`/launch args. `./gradlew runClienttestClient -Dss05.clienttest.skill=...`
  is the actual command to use - not `runClientTest` (there's no run of that name).
- `register('client')` can only be called **once** per run name - calling it again (even just to add the `with(...)`
  block in a second call) throws "Cannot add ... already exists". The `with(...)` block has to live inside the
  single `register('client') { ... }` call.
- `mods { "<modid>" { source sourceSets.xxx } }` (the `SlimeLauncherOptionsNested.ModConfig` container) is how you
  tell a run which source set(s) a given mod id's classes live in - needed here because `main` and `clienttest` are
  two separate mods (`shooting_star_addition`, `ss05_clienttest`) that both need to be on the harness run's
  classpath, while normal `runClient`/`runServer` should only ever see `shooting_star_addition`.
- `net.minecraftforge.gradle.merge-source-sets=true` in `gradle.properties` (present in Forge's own `mixins-only`
  MDK example for a second source set) was kept; removing it wasn't tested, so it's not confirmed necessary, but it
  didn't cause problems.

## Verified against the real jar

`javap`'d the Forge 1.3.4 jar's `cyou.rimuru.shootingstardemo.mc1211` classes directly (not assumed): `SkillSet`
(fields `ALL`, `OWNER`, `INDEX`, `skills`, `caster`, methods `register/all/skillCount/byIndex/indexOf/of/cast/art/
keyName/description`), `Skill`, and `SpellEngine` (`start/running/aimGround`) all match what `compat/StarBridge.java`
and the clienttest harness expect, byte-for-byte the same shape as the NeoForge build's classes. `compat/` needed
**no changes at all** for this port - it was already written against `cyou.rimuru.shootingstardemo.mc1211` (the
package rename in The Shooting Star 1.3.3+), confirming the CLAUDE.md handoff's `dev.aek.shootingstardemo` references
are from before that rename and no longer apply to either loader.

## Things that needed no changes

Everything **not** listed above: `world/`, `client/film/`, `client/render/` (apart from the two matrix/partial-tick
call sites listed), `client/sky/HalleySky` (apart from the same), `client/luna/`, `compat/` (including every mixin's
*body* - only the `remap = false` flags were added), `content/MoltenMightEffect`, shaders, textures, sounds, lang,
loot tables, damage types, tags, `pack.mcmeta` (`pack_format` 34, same as `main` - it tracks the Minecraft version,
not the loader). Plain Minecraft/vanilla code is identical on both loaders at 1.21.1.

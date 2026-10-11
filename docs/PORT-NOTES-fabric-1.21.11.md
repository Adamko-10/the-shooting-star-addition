# Port notes: Fabric 1.21.11

Branch `fabric-1.21.11`, made from `fabric-26.3` (1.3.0). Version `1.3.0+fabric-1.21.11`.

## Build

- Classic `fabric-loom` 1.18.4 with `loom.officialMojangMappings()`: 1.21.11 is still obfuscated, and The Shooting
  Star's nested `the-shooting-star-demo-<ver>+mc1.21.11.jar` is in intermediary names. So it goes in as
  `modCompileOnly`/`modLocalRuntime` (remapped by Loom). There is no refmap: Loom 1.18 rewrites the mixin
  annotations' targets to intermediary names when it remaps the release jar (check with `javap -v` on a mixin class
  in `build/libs`: you should see `method_…`). `AttributeTrackSampler.applyTimeBased` keeps its name in intermediary
  (it isn't obfuscated).
- Java 21 (`options.release = 21`, mixin `compatibilityLevel` `JAVA_21`). `fabricloader >=0.17.0`, the same floor as
  The Shooting Star's 1.21.11 build.
- On Windows, run Gradle with `JAVA_HOME` unset if it points at an old JDK.

## 26.3 → 1.21.11

- **Rendering:** 26.3's renderpearl API has no 1.21.11 equivalent. `HalleyPipelines`, `DynamicMesh`, `BakedTexture`,
  `HalleySky`, `MoonSphere`, `CometRenderer` and `GlowAtlas` use blaze3d (1.21.5+ style): `RenderPipeline.Builder`
  with `withUniform`/`withSampler`, `CommandEncoder.createRenderPass`, `RenderPass.drawIndexed`,
  `DynamicUniforms.writeTransform`. Depth is classic `LEQUAL` (26.x uses reversed depth). `client/render/Gfx` holds
  the camera, projection and level-pass helpers that 26.3 took from its camera render state.
- **Shaders:** `#version 330` with `#moj_import <minecraft:...glsl>`, plain `in`/`out`, no `layout(location)`.
- **Sky:** `SkyRendererMixin` draws the dome at the end of `renderSunMoonAndStars`. `FogRendererMixin` takes the
  returned `Vector4f` of `computeFogColor`. Environment attributes `SKY_LIGHT_COLOR` and `CLOUD_COLOR` are ARGB ints
  (`ARGB.srgbLerp`).
- **Shader-pack night:** `AttributeTrackSamplerMixin` winds the `LongSupplier` day time on the render thread only
  (1.21.11 has no world clock manager).
- **Once a frame:** `GameRendererMixin` hooks the head of `GameRenderer.render(DeltaTracker, boolean)` (26.3:
  `extract`). Every injection is `require = 0`, so a wrong target is silent. To check them all, set `defaultRequire`
  to 1 in both mixin configs and run `runClientTest` once.

## Tested (dev client, The Shooting Star 1.3.4)

`runClientTest` for SS-06 by day (sky turns to night, the moon falls, impact frames, crater, moon of cheese), the SS-06
cutscene, eating moon cheese, and SS-05. Screenshots match the 26.3 build.

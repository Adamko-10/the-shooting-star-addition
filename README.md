# The Shooting Star Addition

An **unofficial**, fan-made addon for [The Shooting Star [Demo]](https://www.curseforge.com/minecraft/mc-mods/the-shooting-star-demo)
by rimuru_dev. It adds two new skills to the Stellar Remote: **Halley**, a comet called down onto your
crosshair, and **Luna**, which at night brings the moon itself down, leaving a moon of edible moon cheese with a
molten heart. On the remote they come after The Shooting Star's own skills and are numbered after them:
with The Shooting Star 1.4.0, which has its own SS-05 · Tesseract, they are **SS-06 · Halley** and **SS-07 · Luna**.

**Download:** [The Shooting Star Addition on CurseForge](https://www.curseforge.com/minecraft/mc-mods/the-shooting-star-addition)

![Halley in game](docs/screenshots/overview.jpg)

> **Compatibility:** this branch (`forge-1.20.1`) is the **Forge and NeoForge, Minecraft 1.20.1** version (one jar for both), for **The Shooting
> Star [Demo] 1.4.0** (its Forge file, which carries the 1.20.1 build; exactly that version: 1.4.0 is obfuscated, see
> below). Other versions of the addon: NeoForge 1.21.1 (`main`), Forge 1.21.1 (`forge-1.21.1`), Fabric 26.3
> (`fabric-26.3`) and Fabric 1.21.11 (`fabric-1.21.11`). See
> [Will it work with future versions?](#will-it-work-with-future-versions-of-the-shooting-star) below.

## Halley

Flip the cover and press the button. Your crosshair is marked in ice-blue and an approach corridor is projected
across the land beyond it. A comet rises over the horizon with its tails stretched across the sky, hits the atmosphere
as a fireball, touches down at the far end of the corridor, ploughs a frozen trench back toward you and detonates on
the mark — leaving an icy crater with the comet's glowing heart standing in it, and rays of frost thrown out over the
land round it.

- **The sky goes dark as it comes in**: stars come out, the band of the galaxy, an aurora, and the comet blazes
  across them with its tails, lighting up the air round it. The fireball outshines the stars as it comes down, the
  blast lights up the night, and then the air fills with ice: a pale haze with a halo, sun dogs and a light pillar
  round the sun (or the moon), clearing as the crystals settle.
- **You can't miss it**: outside the cutscene the comet is drawn bigger, and an arrow at the edge of the screen points
  at it while it's out of view.
- **Works with shader packs** (Oculus, the Forge port of Iris, e.g. with Complementary): with one on, Halley paints its comet and sky onto textures
  and draws them the way the pack expects, and the dark sky becomes the pack's own night: the sky's clock races through
  a dusk into the night as the comet comes in, and on through a dawn after the impact. Only what you see changes, never
  the world's actual time.
- Pieces break off the comet and burst in the sky, the fireball sheds sparks and throws lens flares, ice is thrown
  out of the crater on long arcs, glowing cracks race across the land, a wall of snow rides the shock front, and the
  comet heart shines a beam into the sky.
- Joins the Stellar Remote like the built-in skills: its own key (**O** by default), menu card and description, HUD
  card, cooldown and recharge display.
- Its own cutscene (skippable with the remote's cutscene key), with impact frames, flashes and camera shake in the
  remote's style, and the remote's cover, button and screen animate for it.
- Thirteen new sounds, and two new blocks: the **Comet Heart** and the **Frozen Comet Trail** (mine them with a pickaxe to take them home).
- Everything in the trench and the crater is erased, the way the remote's other skills erase their strike zones (you
  too if you stand there; in creative you are lifted clear). Past the crater, the shock wave throws, hurts and
  freezes whatever it meets.
- **It permanently changes the land.** Back up worlds you care about, as with the original mod.

| | | |
|---|---|---|
| ![](docs/screenshots/01-mark-and-corridor.jpg) | ![](docs/screenshots/03-fireball.jpg) | ![](docs/screenshots/04-plough.jpg) |
| ![](docs/screenshots/05-detonation.jpg) | ![](docs/screenshots/06-comet-heart.jpg) | ![](docs/screenshots/07-crater.jpg) |
| ![The dark sky and the aurora, in third person](docs/screenshots/10-night-sky.jpg) | ![The ice halo and sun dogs after the impact](docs/screenshots/11-ice-halo.jpg) | ![With a shader pack (Complementary)](docs/screenshots/12-shader-pack.jpg) |

## Luna

Flip the cover and press the button: **EARTH SYSTEM SHUT DOWN**, and the sky falls to night (like Halley's). The moon, right where it hangs in your
sky, shudders and cracks open along glowing molten seams, leaves its place and falls onto your crosshair, slowly at
first and then faster and faster, growing until it fills the sky. It burns through the atmosphere in a shroud of fire,
hits the ground in a white flash and a shock ring, ploughs into the crater it digs and comes to rest there, half
buried: a whole moon of **moon cheese**, with one block of **molten moon cheese** at its very centre.

- Works by day or night (the sky turns to night for it; only what you see changes, never the world's time). Not where there is no moon, like the Nether.
- Fissures tear out across the land all round the crater (like Gungnir's cracks), and the shock wave throws, burns and hurts far beyond it.
- Its own key (**N** by default), menu card, cooldown (120 s), cutscene (skippable), sounds and alarm.
- Everything in the crater is erased; past it, the shock wave throws, burns and hurts whatever it meets.
- **Moon cheese**: right-click a block to place it, right-click the air to eat it. It's as good as a golden carrot.
- **Molten moon cheese**: eat it for **500 hearts** and **Strength 255** for 10 minutes (Molten Might).
- **Any size**: the moon, crater and shock wave are server settings, from a pebble to a monster (see below).
- **It permanently changes the land.** Back up worlds you care about.

| | | |
|---|---|---|
| ![EARTH SYSTEM SHUT DOWN](docs/screenshots/13-luna-alarm.jpg) | ![The moon burning in](docs/screenshots/14-luna-fall.jpg) | ![The moon in its crater](docs/screenshots/15-luna-crater.jpg) |

## Installing

1. Minecraft **1.20.1** with **Forge 47.1 or newer** (47.4.x recommended) or **NeoForge 1.20.1** (47.1.x).
2. **The Shooting Star [Demo] 1.4.0**: its Forge file on Forge, its NeoForge file on NeoForge (both carry the 1.20.1 build).
3. `shooting_star_addition-1.3.0+1.20.1.jar` ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/the-shooting-star-addition))
   in the same `mods` folder. On a server, it goes on the server **and** on every player's client.

In game: hold the Stellar Remote, press **O** for Halley or **J** for Luna (or pick them in the remote's menu,
**H**), aim at the ground and right-click. The keys can be changed in Controls or from the remote's menu like the
others.

## Settings — `config/shooting_star_addition-common.toml`

| Section  | Setting                    | Default | What it does |
|----------|----------------------------|---------|--------------|
| skill    | `cooldown_seconds`         | 60      | Recharge time (the remote's own skills use 60). |
| skill    | `reach`                    | 420     | How far away the crosshair can place the mark. |
| impact   | `trench_length`            | 240     | Length of the trench ploughed toward the mark. |
| impact   | `trench_width`             | 30      | Width of the trench where it meets the crater. |
| impact   | `trench_depth`             | 22      | Depth of the trench. |
| impact   | `crater_radius`            | 56      | Radius of the crater; everything inside dies. |
| impact   | `crater_depth`             | 24      | Depth of the crater. |
| impact   | `blast_reach`              | 2.6     | Shock wave reach, in crater radii (throws, hurts, freezes). |
| world    | `carve_terrain`            | true    | `false` = the strike only hurts creatures, the land is untouched. |
| world    | `leave_comet_heart`        | true    | The glowing heart crystal and the frozen trail down the trench. |
| world    | `frost_rays`               | true    | Rays of snow painted out from the crater. |
| safety   | `evacuate_creative_caster` | true    | Lift a creative-mode caster out of the crater first (Halley and Luna). |

Luna and moon cheese, in the same file:

| Section          | Setting            | Default | Range     | What it does |
|------------------|--------------------|---------|-----------|--------------|
| moonfall         | `cooldown_seconds` | 120     | 1-3600    | Recharge time. |
| moonfall         | `reach`            | 420     | 32-1024   | How far away the crosshair can place the mark. |
| moonfall         | `require_night`    | false   |           | `true` = it refuses by day. |
| moonfall_size    | `moon_radius`      | 48      | 2-200     | Radius of the moon (96 blocks across). |
| moonfall_size    | `crater_radius`    | 90      | 4-320     | Radius of the crater; everything inside dies. |
| moonfall_size    | `crater_depth`     | 30      | 1-256     | Depth of the crater. |
| moonfall_size    | `sink_percent`     | 35      | 0-90      | How much of the moon ends up buried. |
| moonfall_size    | `blast_reach`      | 3.0     | 1.0-6.0   | Shock wave reach, in crater radii. |
| moonfall_size    | `chunks_per_tick`  | 8       | 1-64      | How fast the crater and moon are cut and built (huge moons take a while). |
| moonfall_world   | `carve_terrain`    | true    |           | `false` = purely visual, only creatures are hit. |
| moonfall_world   | `molten_core`      | true    |           | The block of molten moon cheese at the moon's centre. |
| moon_cheese      | `molten_hearts`    | 500     | 1-512     | Hearts while Molten Might lasts. |
| moon_cheese      | `molten_strength`  | 255     | 1-255     | Strength level it gives. |
| moon_cheese      | `molten_minutes`   | 10      | 1-120     | How long it lasts. |

The server's values are sent with every strike, so every client films exactly the crater the server cuts.

## Looks — `config/shooting_star_addition-client.toml`

Each player can set these for themselves; they only change what you see.

| Setting          | Default | What it does |
|------------------|---------|--------------|
| `sky`            | true    | The sky goes dark while the comet comes in, and hazy with ice after the impact. |
| `darken_land`    | true    | The land dims under that dark sky, so the comet's light stands out. |
| `extra_effects`  | true    | Fragments, sparks, ice from the crater, cracks, the shock wall, the heart's beam, glittering air. |
| `world_flashes`  | true    | The whole world lights up for an instant at touchdown and impact. Turn off if flashing light bothers you. |
| `comet_marker`   | true    | The arrow at the edge of the screen pointing at the comet while it's out of view. |
| `shader_packs`   | AUTO    | How it's drawn with a shader pack: `AUTO` switches to the shader-pack way whenever Oculus has a pack on, `ALWAYS` uses it all the time, `NEVER` doesn't (the comet and the sky then won't show under a shader pack). |

## Will it work with future versions of The Shooting Star?

Not without an update of this addon. Since 1.4.0, The Shooting Star is obfuscated: the names inside its code are
scrambled (`a`, `b`, `c`...) and can change with every release. It has no official way for other mods to add skills,
so this addon attaches to its internals by those names, and each version of the addon is made for one version of The
Shooting Star:

- This version needs **The Shooting Star 1.4.0**. With any other version the game says so when it starts, instead of
  crashing.
- When The Shooting Star updates, wait for the matching update of this addon.
- On the remote, The Shooting Star's own skills keep their numbers and Halley and Luna take the next ones: if it adds
  an SS-06 of its own, they become SS-07 and SS-08 by themselves.
- If something it attaches to has really changed, the addon doesn't crash your game: it switches its skills off, logs
  why, and tells server operators in chat.
- **If The Shooting Star changes its mod id** (for example when it stops being a demo), this addon won't load until
  it is updated.

- **Other mod loaders and Minecraft versions** each need their own build: this one is Forge 1.20.1; there are also
  NeoForge 1.21.1 and Fabric 26.3 versions.


## Plans

This is version 1.2. If people enjoy it and there's support for it, I'll keep it updated for new versions of The
Shooting Star and keep improving it: more skills for the Stellar Remote, balancing and polish from your feedback,
and versions for other loaders and Minecraft versions if there's demand. Bug reports, ideas and feedback are very
welcome in the issues.

---

## For developers

### Building

Gradle runs on JDK 17-21 and fetches JDK 17 for the build itself. Put The Shooting Star's Forge jar in `libs/` (see
`libs/README.txt`); the build remaps it to Mojang names for compiling and for the dev runs (ModDevGradle Legacy), then:

- `./gradlew build` → `build/libs/shooting_star_addition-<version>.jar`, reobfuscated to SRG names with a mixin
  refmap: that's the one to release (`build/devlibs/` holds the dev-named jar, not for release)
- `./gradlew runServer` → a dev server with both mods loaded
- `./gradlew runServerTest` → a dev server that casts Halley for real in a fresh world, checks what it did and stops
  (`src/servertest`, not part of the addon's jar; look for `[SS05TEST]` in `run/servertest/logs/latest.log`)
- `./gradlew -Pneoforge runServerTest` → the same test on NeoForge 1.20.1 (put The Shooting Star's NeoForge jar in
  `libs/` instead of the Forge one first)
- `./gradlew runClient` → a dev client. **The Shooting Star's 1.20.1 client mixins don't run in a dev client** (they
  name their targets in SRG, with no refmap, and Mixin can't map their `@Shadow`s to Mojang names), so it crashes on
  start. Test client-side things with the built jar in a normal Forge 1.20.1 install instead.

### When The Shooting Star updates

Since 1.4.0 The Shooting Star is obfuscated. `compat/` (the only code that touches it) is still written against its
readable names, those of 1.3.x: the build makes a readable copy of its jar with a mapping (`gradle/tss.gradle`, tools in
`tools/tss/`), compiles and runs against that copy, and maps the release jar back to the real names. Mappings are
made on your own machine and kept out of git (`libs/mappings/`): the repository never carries a de-obfuscation map of
The Shooting Star.

1. Keep a readable build of the previous version: `build/shooting-star-named/<jar>` from the last build (for 1.4.0,
   the 1.3.4 jar itself is the readable one). Then put the new jar in `libs/` in place of the old one.
2. `./gradlew tssMappings -PtssNamed=<that readable jar>` matches the two by their structure and writes
   `libs/mappings/<name>.tiny`, with a `.report.txt` of what it couldn't match (what's new in the update, and anything
   that changed). Nothing `compat/` uses should be in the report.
3. Set `shooting_star_version_range` in `gradle.properties` to the new version and run `./gradlew build`. It compiles
   against the readable copy, and `tssReobf` checks every reference into The Shooting Star against the real jar: the
   build fails if one doesn't exist there.
4. **Compile errors** are in `compat/` only, where something really changed: each helper is a one-line wrapper at the
   bottom of `compat/StarBridge.java`; the skills are `compat/HalleySkill.java`/`MoonSkill.java`, the spells
   `compat/HalleySpell.java`/`MoonSpell.java`, the effects `compat/client/*.java`.
5. Start the game. The log should say `SS-06 · Halley (Halley) joined the Stellar Remote as skill #N` (with the number
   that follows The Shooting Star's own skills). If it says "couldn't attach", the remote's skill list (`SkillSet`)
   changed shape: `StarBridge.install()` finds the fields it needs by their types.
6. **Smaller things that may stop without breaking anything** — optional mixins in `compat/mixin/` that switch
   themselves off if their target moved:
   - the remote's cover/button/screen not animating for Halley and Luna or Luna → `RemoteRendererMixin` (`strikeTime`);
   - no Halley/Luna overlay on the cutscene → `FxManagerMixin` (`renderFilmHud`);
   - the caster's arm not raising (vanilla target, very unlikely to change) → `PlayerModelMixin`;
   - the land not dimming under the dark sky (vanilla target, very unlikely to change) → `ClientLevelMixin`;
   - with a shader pack on, the sky not going to night (vanilla target, very unlikely to change) →
     `ClientLevelTimeMixin`;
   - the vanilla moon showing through Luna's own moon (vanilla target, very unlikely to change) →
     `LevelRendererMoonMixin`.
7. **A new built-in skill takes the O or N key** → change `DEFAULT_KEY` in `world/HalleyInfo.java` or
   `world/MoonInfo.java`. Its SS number needs nothing: Halley and Luna are numbered after the remote's own skills
   (`world/SkillNumbers.java`).

### Changing the skill

| To change… | Look in |
|---|---|
| Sizes, cooldown, what it leaves behind | `config/shooting_star_addition-common.toml` (defaults in `HalleyConfig.java`) |
| The timeline (when it is sighted, enters, touches down) | `HalleyPlan.java` — film, sounds and server all follow it |
| What it does to the world | `world/HalleyStrike.java` (block palettes at the top) |
| Name, colour, default key | `world/HalleyInfo.java`; description and key name in `assets/shooting_star_addition/lang/en_us.json` |
| The look: colours, comet size, tails | top of `client/render/CometVisuals.java`; shapes in `halley_glow.fsh` |
| The extras: fragments, ice from the crater, cracks, shock wall, beam | top of `client/render/CometExtras.java` |
| The sky: when it darkens, the aurora, the haze | `sky()` in `client/HalleyFx.java` (timing); `halley_sky.fsh` (the look; shared with Luna, see `SkyLook.palette()`) |
| Shader packs (Oculus) | `client/render/ShaderPacks.java` (when), `GlowAtlas.java` (the painted shapes), `paint()` in `client/sky/HalleySky.java` |
| Sounds, particles, screen effects, HUD | `client/HalleyFx.java`, `client/HalleyHud.java` |
| The cutscene | `client/film/HalleyStoryboard.java` (one block per shot) |
| Luna (the moonfall), moon cheese | see [docs/SS-06-LUNA.md](docs/SS-06-LUNA.md) (design and code map) |
| Textures, menu card, thumbnail, sounds themselves | `art/` (generator scripts — see `art/README.md`) |

### Project layout

```
src/main/java/dev/ss05/halley/
  HalleyAddon.java       mod entry point
  HalleyConfig.java      the settings file
  HalleyPlan.java        timeline and geometry, shared by server and client
  HalleyParams.java      the sizes of one strike, sent to clients with it
  compat/                everything that touches The Shooting Star (server side, client side, mixins)
  content/               blocks, sounds, damage type
  world/                 what the strike does to the world (server)
  client/                what it looks and sounds like (renderer, effects, HUD, cutscene)
  client/luna/           Luna's own effects, HUD and renderer (the moon sphere, its glow)
  client/sky/            the sky during a strike (night, aurora, the ice haze; shared by Halley and Luna)
src/main/resources/      shader, textures, sounds, language file, data
src/main/templates/      META-INF/mods.toml (filled in from gradle.properties)
src/servertest/          the dev-only game-logic test (./gradlew runServerTest)
art/                     scripts that generate the textures, thumbnail and sounds
docs/screenshots/        in-game screenshots
```

## Credits

The Shooting Star and the Stellar Remote are by **rimuru_dev** (All Rights Reserved). This addon is not affiliated
with or endorsed by them, and contains none of the original mod's code or assets — you need the original mod
installed for it to do anything.

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

**The Shooting Star Addition** - handoff notes for whoever (human or Claude) works on this next. Written 2026-10-14 at
the end of the 1.4.0 work, updated 2026-10-17 for 1.5.0 (§12; status in §9).
Everything here was true of the code at that point; if the code disagrees, the code wins. The player-facing
documentation is `README.md`; Luna's design and code map is `docs/SS-06-LUNA.md`.

## 0. The user's rules (follow these exactly)

- **Never ship or commit The Shooting Star's jar, code, data, assets or mappings.** `libs/*.jar` and `libs/mappings/`
  are git-ignored. Mappings are made locally or in CI and never pushed.
- Commit as `Adamko-10 <Adamko-10@users.noreply.github.com>`, with the session's `Co-Authored-By:` / `Claude-Session:`
  trailers. Pull request bodies end with the "Generated with Claude Code" line and the session link.
- **Push and make GitHub releases only when the user says so (after testing).** Work-in-progress CI pushes go to
  `wip/<branch>` only (§4), never to the real branches.
- Every push to GitHub comes with a changelog: one GitHub release per branch, tag `v<ver>` on `main` and
  `v<ver>+<suffix>` on the others, named `The Shooting Star Addition <ver> [<Loader> <MC>]`, the release jar attached,
  and a Markdown changelog written for players. Reuse the same text for CurseForge.
- Release jar names: **only** `The Shooting Star Addition <mod version> [<Loader> <Minecraft version>].jar`.
- CurseForge files: **Release**, **Client + Server**, depending on **The Shooting Star [Demo]** (required), plus
  **Fabric API** (required) on Fabric builds.
- In Markdown changelogs, don't put `(` right after `]`.
- Never print the GitHub token. Never enter credentials anywhere.
- **Every change goes to every branch** (§3). Sonnet subagents (`model: "sonnet"`) are fine for bounded mechanical work.
- For browser uploads (CurseForge), ask the user to disable their Chrome tab suspender first.
- Deleting the user's files needs permission (rename mods to `.disabled` instead).
- Talking to the user: after coding, give a clear list of **what they need to do**; for testing, a list of **what to
  test**. They may say "you can stop telling me now"; start again when the topic changes (building → testing etc.).
- If finishing a task past 2 AM their time, shut down their PC (unless told not to). If running low on tokens, schedule
  a continuation.
- The user is budget-conscious: don't over-test; one CI round per change set is normally enough, and they test in game.

## 1. What this is

An unofficial addon for **The Shooting Star [Demo]** ("TSS", mod id `shooting_star_demo`, by rimuru_dev, CurseForge
project 1719195, All Rights Reserved). It adds two skills to TSS's Stellar Remote:

- **SA-01 · Halley** (skill id `halley`): a comet comes in over the horizon, ploughs a frozen trench and detonates on the
  crosshair, leaving an icy crater with a Comet Heart block.
- **SA-02 · Luna** (skill id `moon`): the moon cracks, tears loose and falls onto the crosshair, leaving a crater with
  the moon (made of moon cheese) buried in it.

Mod id `shooting_star_addition`, Java package `dev.ss05.halley` (the "SS-05/SS-06" names in code are the old
codenames; players see SA-01/SA-02). GitHub `Adamko-10/the-shooting-star-addition`; CurseForge project **1725916**
(author `adamko_10`). Current version **1.5.0** (in `gradle.properties` on every branch; 1.4.0 is the last published).

## 2. Branches

All branches live in **one** git repository, one worktree per branch. On the user's PC they are under
`C:\Users\HamzaTom\Desktop\Dont Touch\software files\shooting star adon with adam\`: `tssa-main` (main),
`tssa-forge1211`, `the-shooting-star-addition-main` (forge-1.20.1; the main checkout, `.git` lives there), `tssa-fabric`
(fabric-26.3), `tssa-fabric12111`, `tssa-fabric1211-next` (local branch `fabric-1.21.1-next` tracking
`origin/fabric-1.21.1`: push it with `git push origin fabric-1.21.1-next:fabric-1.21.1`), `tssa-fabric1201`. Leave
`tssa-fabric1211` alone: it holds the user's old uncommitted 1.3.0-era edits on the local `fabric-1.21.1` branch.
**Never use `git stash` with worktrees**: the stash stack is shared by all of them and pops land on the wrong branch.

| Branch | Loader / MC | TSS package prefix | Release tag | Jar label |
|---|---|---|---|---|
| `main` | NeoForge 1.21.1 | `cyou.rimuru.shootingstardemo.mc1211.` | `v<ver>` | `[NeoForge 1.21.1]` |
| `forge-1.21.1` | Forge 1.21.1 | `…mc1211.` | `v<ver>+forge-1.21.1` | `[Forge 1.21.1]` |
| `forge-1.20.1` | Forge 1.20.1 (also NeoForge 1.20.1) | `…mc1201.` | `v<ver>+1.20.1` | `[Forge 1.20.1]` |
| `fabric-26.3` | Fabric 26.3 | `cyou.rimuru.shootingstardemo.` (none) | `v<ver>+fabric-26.3` | `[Fabric 26.3]` |
| `fabric-1.21.11` | Fabric 1.21.11 | none | `v<ver>+fabric-1.21.11` | `[Fabric 1.21.11]` |
| `fabric-1.21.1` | Fabric 1.21.1 | none | `v<ver>+fabric-1.21.1` | `[Fabric 1.21.1]` |
| `fabric-1.20.1` | Fabric 1.20.1 | none | `v<ver>+fabric-1.20.1` | `[Fabric 1.20.1]` |

Differences between branches you will hit when porting:

- **Config**: `main` uses NeoForge `ModConfigSpec`; the Forge branches `ForgeConfigSpec` (same builder style, just the
  type name); the Fabric branches a small TOML writer of our own, `config/ConfigFile.java`
  (`b.section(...)`, `b.defineInRange(name, default, min, max, comments...)`, `b.define(...)`; getters are
  `SPEC.isLoaded() && X.get()`).
- **Registered content**: NeoForge/Forge hold `DeferredHolder`/`RegistryObject`s (`HalleyContent.MOON_SIZZLE.get()`),
  Fabric holds the objects directly (`HalleyContent.MOON_SIZZLE`).
- **26.3 and 1.21.11** world code: chunk tickets are `addTicketWithRadius(TICKET, pos, r)` / `removeTicketWithRadius`,
  `TicketType TICKET = HalleyContent.CHUNKS` (registered), `player.level()`, jspecify `@Nullable`, `Identifier` instead
  of `ResourceLocation` in places. Their client-test harness uses a `finalTicks`/`pendingShot` variant. Moon/comet
  rendering there is our own pipeline (`client/render/HalleyPipelines`, `DynamicMesh`), not vanilla RenderTypes, so
  render changes are ported by hand. TSS's menu takes `WhiteSpace.key(KeyEvent)` there (not `key(III)V`). No
  `DimensionSpecialEffects`: the cloud height is `camera.attributeProbe().getValue(EnvironmentAttributes.CLOUD_HEIGHT,
  partial)`. 26.3 also: `ChunkPos` is a record (`x()`, `pack()`), `net.minecraft.util.Util`,
  `player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)`.
- **26.3 keys**: Minecraft 26.3 uses SDL scancodes; TSS translates GLFW codes. Vanilla 26.3 binds *Friends* to **O**,
  so Halley's default key there is **G** (71); everywhere else it is **O** (79). Luna is **N** (78) everywhere. TSS's own
  keys: menu **H**, cutscenes **J**, skills **U, I, K, Y**.
- **1.20.1 tags**: Forge 1.20.1 uses `data/forge/tags/blocks|items/...`; Fabric 1.20.1 `data/c/tags/blocks|items/`
  with the old flat names (`c:tungsten_blocks`); the 1.21 branches `data/c/tags/block|item/storage_blocks/tungsten.json`.
- `forge-1.20.1` carries the server test (`src/servertest`, 70 checks); every branch carries the client-test harness
  (`src/clienttest`).
- **Networking** (`net/`, since 1.5.0) is per loader: NeoForge `RegisterPayloadHandlersEvent`, Forge 1.21.1
  `ChannelBuilder`/`SimpleChannel`, Forge 1.20.1 `NetworkRegistry.newSimpleChannel`, Fabric `PayloadTypeRegistry` +
  `Server/ClientPlayNetworking` (Fabric 1.20.1: the old byte-buf API). The Fabric branches' `config/ConfigFile` gained
  `set`/public `save()` for the client switches.

## 3. Porting a change to every branch

1. Make and commit the change on `main` and build it (`./gradlew --no-daemon build -x test`, §4).
2. On each other branch: `git cherry-pick <sha>`. Conflicts are almost always one of:
   - `HalleyConfig.java` → Forge: take main's side with `ModConfigSpec` → `ForgeConfigSpec`; Fabric: keep the branch's
     side and re-apply the change in `ConfigFile` style;
   - `compat/tss/**` → the package prefix (`mc1211` → `mc1201` on forge-1.20.1, removed on Fabric). Check
     **auto-merged** parts too: mixin descriptors like `Lcyou/rimuru/shootingstardemo/mc1211/magic/Skill;` slip through;
   - `.get()` on registered content (remove it on Fabric);
   - 26.3/1.21.11 ticket and world API lines (keep the branch's side).
   `dev/tools/resolve.py <file> ours|theirs [from=to ...]` resolves all conflict hunks of a file to one side with text
   substitutions.
3. Build each branch in its worktree (§4), then one CI round on all seven branches. A merge that "applied cleanly" can
   still carry main's API into a branch (NeoForge `.get()`, `getMinBuildHeight()`, `mc1211.` prefixes, `ChunkPos`
   fields): only the build tells. Sonnet subagents, one per branch, did this well with a written brief; give them the
   SHAs, the branch's API differences and the hard rules (no stash, no push, never commit libs/).

## 4. Building and CI

**Local build** (any branch): `./gradlew --no-daemon build -x test` with `JAVA_HOME` = JDK 21
(`C:\Program Files\Java\jdk-21.0.11`; NeoForge/Forge) or JDK 25 (`C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot`;
the Fabric branches, as in CI). Put TSS jars in `libs/`, one per supported version, named
`the-shooting-star-demo-<ver>-<loader>.jar` and downloaded like CI does
(`https://www.cursemaven.com/curse/maven/the-shooting-star-demo-1719195/<id>/the-shooting-star-demo-1719195-<id>.jar`,
ids in `dev/ci/branches/<branch>.sh`). Make the mappings oldest first, each from the previous one:
`./gradlew tssMappings -PtssJar=1.4.0`, then 1.4.1, 1.4.2, 1.4.3 (a mapping made without the previous version's is
garbage). The build's `tssPack` step checks every TSS member and mixin target against every version: a "missing"
there is a real bug. Fabric 1.20.1's Loom sometimes fails once with "Cannot mutate content repository descriptor":
run again. The `dev/tools/*.sh` scripts are the old container's (`/home/claude/...`), not usable here.

**Local client test** (real GPU, opens a game window): `./gradlew --no-daemon runClientTest -Dss05.clienttest.skill=moon
-Dss05.clienttest.film=true -Dss05.clienttest.hud=true -Dss05.clienttest.time=6000 -Dss05.clienttest.shots=70,250,395`;
screenshots land in `run/clienttest/screenshots/` (`run/clienttest/options.txt` sets game options, e.g.
`renderClouds:"false"`). About 5 minutes; worth it for rendering bugs CI screenshots can't explain.

How the build works: it makes per-version mappings (`./gradlew tssMappings`), compiles
against a readable copy of the newest jar and packs one mapped copy of `compat/tss/` per version into the release jar
(README "For developers" explains it in full). The dependency range in the mod metadata comes from the jars in `libs/`.

**CI** (GitHub Actions, only on `wip/*` branches). The CI files are *not* on the real branches; they live in `dev/ci/`:

- `dev/ci/push-wip.sh <branch> [local ref]` (run from any worktree; it uses its own `dev/ci/`) builds a commit = the branch + `dev/ci/common/` + `dev/ci/branches/<branch>.sh` (as
  `.github/ci/branch.sh`) and force-pushes it to `wip/<branch>`. The branch itself is untouched.
- The workflow downloads TSS jars from CurseForge by file id (`TSS_FILES` in `dev/ci/branches/<branch>.sh`), maps every
  version in `TSS_OBF` (1.4.0 1.4.1 1.4.2 1.4.3), builds, runs the client tests and (forge-1.20.1) the server test, and
  publishes everything as a **draft release** `ci-<branch>-<run>`: `summary.txt` (`ok:`/`FAIL:` lines), `commit.txt`,
  the jar, `shots-*.zip` screenshots, and an encrypted `secret` part (logs, mappings, `skill-api.txt` = javap of TSS's
  `Skill` per version) that only the holder of the private key can read.
- `dev/ci/ci-fetch.sh <branch> [run]` downloads and decrypts the newest draft into `~/.tssa-ci/results/<branch>-<n>/`
  (logs in `secret/`, e.g. `servertest-forge.log`, `client-moon.log`). The private key is `~/.tssa-ci/ci.key` on the
  user's PC (made 2026-10-16, never in git; its public half is `dev/ci/common/.github/ci/ci-key.pub.pem`).
- Status: `gh api "repos/Adamko-10/the-shooting-star-addition/actions/runs?per_page=8" --jq '.workflow_runs[] |
  "\(.head_branch) \(.status) \(.conclusion) \(.run_number)"'`. A full round of 7 takes roughly 15-25 minutes.
- Client tests (`client-test.sh`, run three times per branch): `halley`, `moon` (the real film with its HUD:
  `film=true hud=true`; its last shot is the H menu with the addon's plate), and `tuned` (Luna with
  `power=0.4 speed=3 damage=20`, aerial view, then the remote menu scrolled to the sliders). Options are
  `-Dss05.clienttest.<name>` (`skill`, `shots`, `view`, `final`, `time`, `power`, `speed`, `damage`); `build.gradle`
  forwards them. On 26.3 the script installs EGL (`libegl1 libegl-mesa0`) when missing.
- Delete the `wip/*` branches and `ci-*` draft releases after publishing.

`dev/ci/*` works from Git Bash on Windows; the `dev/publish/*` and `dev/tools/*.sh` scripts still have the old
container's absolute paths (`/home/claude/...`): adjust before use.

## 5. How the addon attaches to TSS

- TSS has no addon API. Since 1.4.0 it is obfuscated with new names every release. `compat/tss/` is written against the
  readable 1.3.x names; the build maps it per version (`tools/tss/`: `TssMappings`, `TssMatch`, `TssRemap`, `TssPack`).
  `TssMatch` renames new members whose names collide with matched targets (`name_1`) and lists them in the
  `.report.txt`; anything `compat/tss/` uses must not be in a report.
- `compat/StarBridge` picks the copy for the running version (`compat/TssVersion`); `compat/TssMixins` (mixin plugin)
  lets only that copy's mixins apply. The rest of the addon only sees `compat/StarLink` (interface).
- `compat/tss/TssLink.java` joins Halley/Luna to TSS's skill list (`SkillSet`, found by field types), wraps every TSS
  call in a one-line helper at the bottom, and routes casts: `skill == SKILL ? HalleyCasting.cast(player, tune(t)) :
  skill == MOON ? MoonCasting.cast(...) : builtIn.cast(...)`.
- TSS API facts worth knowing (readable names): `Casting.cast(ServerPlayer, Skill, Tuning)` (all versions; 1.4.3
  renamed `Casting.request` to an obfuscated `a(..., boolean)`); `Tuning(power, damage, speed)` record with
  `initial(skill)`, `clampFor(skill)`, `INFINITE`, `SPEED` (0.3-3); `Power(min, max, initial, factor, blocks)`;
  `Skill.power()`, `Skill.k()` (the props key, "power"), `Skill.l()` (the warning threshold, 1.4.2+);
  `ActiveSpell(..., boolean, Tuning)` runs the spell clock at the speed slider; `SpellFx.time(partial)` and its
  protected `speed`; `Cutscene.speed(float)`; `Erasure.hit(level, entity, ServerPlayer, float)` (∞ = strike);
  `Carving.streaming(Carving.Tickets)`, `MAX_TICKET_RADIUS = 32`; `SpellFxPayload` flag bit 8 = "play for everyone"
  (1.4.3). TSS stores slider values client-side in `config/shooting_star_demo_skills.properties`. Careful:
  `SkillTuning.speed`/`power` come out swapped in the mapping chain, so don't use them.
- `compat/tss/mixin/`: `CastingMixin` (no-cooldown, and the server slider limits, §6), `WhiteSpaceMixin` (the addon's
  plate in the menu and its M/C keys),
  `RemoteRendererMixin`, `FxManagerMixin`. All optional (`require = 0`).
- `dev/tools/RefCheck.java` lists references our classes make that a given readable TSS jar lacks;
  `dev/tools/Deobf.java` applies Tiny mappings to a jar with ASM (a stand-in for the Gradle task).

## 6. Features added in 1.4.0 (and where they live)

- **Sliders like TSS's own** (Power 0.1-10×, Damage 2-∞, Speed 0.3-3×), under each skill in the remote's menu (H).
  `CastTuning` is our copy of a cast's sliders; `HalleySkill`/`MoonSkill.power()` return
  `Power(0.1, 10, 1, true, size×2)`, `l()` warns above 3× (Halley) / 2× (Luna). Power scales `HalleyParams`/`MoonParams`
  (`scale` field; params VERSION 2 / 3), the cutscene, plough speed and visuals. Damage: ∞ erases (TSS's `Erasure`),
  otherwise each hit and the blast are capped. Speed: the spell clock (`ActiveSpell`), `HalleyFx`/`MoonFx` via
  `Host.clock/speed`, sound pitch (0.5-2) and `Cutscene.speed`.
- **Big strikes stream their chunks** (`Excavation.Loader`, radius-0 tickets per chunk, `MAX_TICKET_RADIUS = 12`) instead
  of loading the whole area; spells wait for the world work (`worldDone()`, `MAX_EXTRA = 6000` ticks).
- **Server limits on the sliders** (`[stellar_remote]` in `config/shooting_star_addition-common.toml`): `max_power`
  (multiple of each skill's default power, 0 = off), `max_damage` (0 = off; also turns ∞ into that number),
  `max_speed` (3 = off). `CastingMixin.ss05halley$limit` re-casts a cast held to them, for every remote skill.
- **`chunks_per_tick`** for Halley (`[impact]`, 12; the crater does 2×). Luna already had its own (8).
- **Tungsten tag**: TSS's `shooting_star_demo:gungnir_hull` in `c:storage_blocks/tungsten` (optional entry), block and
  item, paths per branch (§2).
- **`no_cooldown`** (`[stellar_remote]`): reworked in 1.5.0 (§12); 1.4.0's version didn't work.
- **Luna's music**: `assets/shooting_star_addition/sounds/luna_music.ogg` (`sounds.json` entry `luna_music`,
  `stream: true`, no registered SoundEvent). Since 1.5.0 see §12 (master volume, its own switch, stretched to the
  slower fall, kept in step). The 1.4.0 file was the user's track cut so its drop landed on `CONTACT`: drop at 24.5 s
  of the source, minus 12.75 s, so start at 11.75 s: `ffmpeg -ss 11.75 -i <source> -vn -af "afade=t=in:st=0:d=0.5,
  volume=13dB,alimiter=limit=0.89" -ac 2 -ar 44100 -c:a libvorbis -q:a 4 luna_music.ogg` (the source isn't on the
  user's PC any more; 1.5.0 stretched that file instead).
- **Shorter endings**: Luna's film ends at `SETTLE + 95` (6 shots), Halley's at `impact + 128` (13 shots). The molten
  seam's hiss plays once (`CONTACT + 30`).

### Timelines (ticks, 20 per second, at speed 1)

- **Halley** (`HalleyPlan`): ARM 0, PRESS 7, MARK 9, COUNTDOWN 29, SIGHT 30, ENTRY 250, TOUCHDOWN 300, impact =
  TOUCHDOWN + trench_length / 8 (default 330 = 16.5 s), effects end impact + 196 (526 = 26.3 s); film ends impact + 128
  (458 = 22.9 s).
- **Luna** (`MoonPlan`, since 1.5.0): PRESS 7, ALARM 10, BREAK 45, FALL 85 (4.25 s), ENTRY 305, CONTACT 405 (20.25 s),
  SETTLE 475, effects end 745 (37.25 s); film ends SETTLE + 95 (570 = 28.5 s). (1.4.0: ENTRY 260, CONTACT 340.)

Film, sounds and server all follow these plans; change a plan constant and everything moves with it.

## 7. When TSS updates

Follow README "When The Shooting Star updates". In CI terms: add the new version's CurseForge file ids to `TSS_FILES`
and the version to `TSS_OBF` in every `dev/ci/branches/*.sh`, push all branches to `wip/`, and read `summary.txt`
(mapping, build, client tests) and the mapping report. For 1.4.3 the only real break was the dependency range; the
build now takes the range from `libs/`. If `TssMatch` fails with "Mapping target name conflicts", it's a new member
whose obfuscated name equals a readable target: the rename logic in `TssMatch` handles that case; extend it if a new
kind appears.

TSS 1.4.3 CurseForge file ids: NeoForge 9171007, Forge 9171001, Fabric 9170986 (older ones are in the branch scripts).

## 8. Publishing (only when the user says so)

1. One CI round on all branches from the final commits; every `summary.txt` all `ok:`.
2. `dev/publish/stage_jars.sh <branches...>` takes each branch's CI jar (checks it's from the branch head and passes
   `verify_jar.sh`) and copies it under its release name. Give the user the jars to test first.
3. Changelogs: `dev/publish/changelog_150.py [out dir]` (1.5.0; `_140` for 1.4.0) writes one Markdown file per branch (first line names the MC/loader
   and says server + every client; then the sections). Update its text for every new feature.
4. GitHub: push the branches, then `dev/publish/github_140.sh <branch> <jar>` (creates the tag and release, uploads
   the jar).
5. CurseForge (browser, Claude in Chrome): upload each jar on project 1725916's Files page. The changelog box is a
   react-mde `textarea.mde-text` (switch to Markdown). Release type **Release**; environment **Client + Server**;
   game versions per branch; relations: **The Shooting Star [Demo]** required (+ **Fabric API** required on Fabric);
   there are no default relations, so add them every time. Upload from the path the file chooser can reach. Ask the
   user to turn off their tab suspender first.
6. Project description: `dev/publish/curseforge-description.md`. **Keep the top part (title line, Discord, YouTube,
   other mod links) exactly as it is**; update the rest to match the features.
7. Delete `wip/*` branches and `ci-*` drafts.

## 9. Status (2026-10-17)

- **1.5.0 is pushed, not released**: all seven branches are on GitHub (CI green on every one: builds, client tests,
  server test 70/70). No 1.5.0 tags, GitHub releases or CurseForge files yet: the user asked only for the branches.
  Next, when the user says so: §8 steps 3-7 (changelog ready in `dev/publish/changelog_150.py`).
- Test jars under their release names: `The Shooting Star Addition releases\1.5.0 (to test)\` (from the last CI round).
- The user added `me mantainer need contrib.txt` on main (kept; main's 1.5.0 commits are rebased on it).
- Leftovers: the `wip/*` branches and `ci-*` draft releases (1.4.0's and 1.5.0's); one stash entry
  ("additionpanel-soundevent-fix-temp") a porting agent made against the rules, superseded and harmless.
- 1.4.0 (2026-10-15): seven GitHub releases and CurseForge files. From the cloud session GitHub refused API release
  creation (HTTP 403), so those releases were made in the user's Chrome; GitHub turns spaces and brackets in asset
  names into dots, the release titles carry the proper names. On CurseForge's upload form the changelog editor starts
  in WYSIWYG: switch it to Markdown and check it switched before setting the text; the form copies game versions and
  relations from an earlier file, so check them every time.

## 10. Ideas not done yet

From the community list the user forwarded (they picked 1, 2 and 4 for 1.4.0):

3. **Survival Stellar Remote**: a way to get the remote in survival (check how TSS gives it in that version). Would need
   a recipe or loot for TSS's item; check TSS's item id per version; ideally a config switch.
5. **GPU offloading** of the strike work: not really possible for world edits (they must happen on the server
   thread); the realistic version is cheaper rendering (instancing the debris, fewer particles) and the existing
   chunk streaming. Mostly a "no", explain it to players.
6. **Protected-blocks tag**: `shooting_star_addition:unbreakable` (block tag) that Halley/Luna never carve (e.g. bedrock,
   spawn blocks, claims). Check it in `Excavation`/the strike carvers before replacing a block. Cheap and useful.
7. **Slider scroll steps**: TSS's slider behaviour; ours follow `Power`'s own `step()`. Could only be changed in TSS.
8. **Typing a number into the sliders** (text box): TSS's menu; a mixin into `WhiteSpace` would be fragile.
9. **Fonts**: TSS's menu font; not ours to change.
10. **Harder recipe**: for the remote; goes with 3.
11. **Yog-Sothoth** skill, 12. **supernova / black hole / TON-618** skills, 14. **planets**: new skills (SA-03...).
   Each is a project like Luna (plan, params, strike, fx, storyboard, sounds, config, docs).
13. The CurseForge comment asking for speed/range/damage options: done by the sliders (1.4.0).
15. **Music for skills**: Luna has hers; the user plans one for Halley too (same approach as §6: cut the track so its
   drop lands on Halley's impact, `HalleyPlan` impact = 330 at default trench length, start at e.g. ENTRY or SIGHT).

Visual/sound polish the user asked for but that was cut for budget:

- **Luna's build-up**: the user finds it thin next to Halley ("Halley sounds cool and has super cool re-entry vfx").
  Ideas, not a copy of Halley: a rising rumble layered from PRESS to FALL; the seams glowing brighter with crackling
  sparks before BREAK; a plasma sheath/bow shock in front of the moon from ENTRY to CONTACT (glowing shell, streaks
  peeling off its rim, a long burning trail across the sky); heat shimmer and embers raining ahead of it; a pressure
  wave flattening trees/grass ahead of the impact; a brief silence (duck everything) just before CONTACT so the
  impact and the music's drop hit harder. Code: `client/luna/MoonFx.java` (sounds/particles), `client/luna/` renderers,
  `MoonPlan` timing.
- General VFX polish for both skills.

## 11. Pitfalls already paid for

- `git stash` across worktrees (shared stack) - don't.
- Mixin descriptors in auto-merged hunks keep the wrong TSS package prefix - grep after every cherry-pick.
- The TSS mapping chain can swap names of look-alike members (e.g. `SkillTuning.speed/power`); verify with
  `skill-api.txt` (javap per version) before using a new TSS member.
- Big Luna strikes stalled the server for ~29 s when loading their whole area; fixed by streaming chunks with capped
  tickets. Keep world edits incremental.
- Halley's trench must be cut after the crater is finished, or the server test's `crater_carved` check fails.
- A 1.21 record codec silently ignores unknown JSON keys: re-check data files against vanilla after porting.
- CurseForge's upload page loses state when the tab is suspended.
- `TssMatch` used to pair same-shaped methods by obfuscated name, which silently swapped `Subject.forward/target` and
  shifted `CutsceneDirector.automatic` in 1.4.3 (§12). It now compares the fields they use, but after a TSS update
  still check what `compat/tss` calls: deobfuscate with `dev/tools/Deobf.java` and compare bodies with readable 1.3.4.
- Minecraft's clouds write depth over their whole layer: anything drawn above it shows only where a cloud is (§12).
- Git Bash on Windows: long inline heredocs/Python with quotes sometimes fail to parse (write scripts to files);
  Windows Python prints CRLF (strip `\r` when piping); native openssl doesn't take Git Bash paths after `file:`.
- Background agents can be cut off by usage limits mid cherry-pick: check each worktree's `git status` and resume.

## 12. 1.5.0 (2026-10-16/17, pushed, not released)

What the user asked for (roughly): the moon shrinks just before it lands; sync the music better and make it "a slower
thing"; the moon cutscene is broken / sometimes doesn't play (they thought Forge), better shots, a cooler UI, a slow-mo
zoom out of the impact with VFX; a cooler moon that glows more as it approaches (not overboard); music on by default on
every loader with a setting in the H menu; a no-cooldown switch in the H menu, a config to allow/disallow survival
players, and info in creative and survival about the config; Luna better with and without shaders; chunks loading
late / wrong in cutscenes; no cooldown doesn't work at all; and the optimization from the 1.4.0 analysis (never load
land for the outer parts, leaner moon/rim/crack building). "Slower" was read as both: a slower fall, with the music
stretched (same pitch) to match.

Findings and fixes:

- **The mapping tool paired look-alike methods wrongly** (`tools/tss/TssMatch.matchMethods`): `Subject.forward()` and
  `target()` were swapped in every 1.4.x mapping, so the films' first shots put the camera at feet + 2 x the mark's
  world coordinates (Luna's "broken film" and the "chunks loading late" in it); in 1.4.3 a new "play for everyone" field
  shifted CutsceneDirector's obfuscated names, so `automatic()` pointed at "cutscenes on AND play for everyone" and the
  caster's own films only played with that on (every loader). Same-shaped methods are now also scored by the fields of
  their own class they use (through the field matches), and equal short obfuscated names only pair when that agrees.
  An audit of every TSS member `compat/tss` uses (bodies compared with readable 1.3.4) found nothing else. To make
  readable jars locally: `dev/tools/Deobf.java` with ASM 9.9.1 from `~/.gradle/caches/minecraftforge/...`. The local
  libs/ has 1.4.2 and 1.4.3 too (mappings made in order with `./gradlew tssMappings -PtssJar=<v>`, oldest first:
  without the previous version's mapping the match is garbage).
- **No cooldown**: TSS's cooldown was cleared, but Halley/Luna refused a second cast while one was running, and 1.4.3's
  `Casting.clear()` also wipes every player's "play for everyone" flags. Now `NoCooldown` (server): allowed =
  `no_cooldown` and (creative/spectator or `no_cooldown_survival`), and the player's own switch (default on, sent by the
  client). `CastingMixin` clears only that player's entry of Casting's `Map<UUID, long[]>` (found by type,
  `TssLink.clearCooldowns`) at the head of `cast` and of `syncCooldowns`. HalleyCasting/MoonCasting skip their "already
  on its way" refusal for them. Join message (`NoCooldown.greet`): everyone while on, operators while off.
- **Networking** (new, `net/`): `SettingsPayload` (server to client: no_cooldown, no_cooldown_survival; on join and on
  config reload) and `ChoicePayload` (client to server: the player's switch, sent when settings arrive and on toggle).
  NeoForge: `AddonNetwork` (optional registrar); each loader has its own registration. `ServerSettings` holds what the
  client was told (plain fields, safe on a dedicated server).
- **H menu**: TSS's menu holds the mouse (things are picked by looking at them), so the addon's plate
  (`client/AdditionPanel`, replaces NoCooldownNote) has keyboard switches, **M** Luna's music and **C** no cooldown,
  through `WhiteSpaceMixin` on `key(III)V` (skipped while a skill key is being rebound or when the key is one of the
  remote's, `StarClientBridge.menuUses`). Client config `[stellar_remote]`: `luna_music`, `no_cooldown` (both true).
- **Moon shrinking**: `MoonSphere.placement` drew it up to 2.4x its true size while falling and collapsed that within 4
  ticks of CONTACT. Removed: it grows steadily to its true size.
- **Slower fall + music**: ENTRY 305, CONTACT 405, SETTLE 475 (fall 16 s). `luna_music.ogg` stretched by
  12.75/16 = 0.797 (atempo, pitch kept; 21.76 s, drop at 16.00 s). `MoonFx.Music` (tickable): SoundSource.MASTER (heard
  with the Music slider down), switch `luna_music`, and a drift correction (up to 5% pitch) against the strike's clock.
- **Slow motion** (film only): `MoonFx.time()` = real time minus `slowLag(t - CONTACT)` times the film weight (0.2x for
  about 2 s, caught up by CONTACT + 130); `realTime()` is the strike's own clock (sounds, music, screen effects, sky,
  remote). Film sounds are pitched down and particles slowed while it lasts.
- **Film** (`client/film/MoonStoryboard`, 9 shots, see its comments) with `Stage.keep` / `client/film/FilmLand` (pull
  the camera toward the caster until it is over loaded land, lift it out of the ground). HUD (`MoonHud.film`):
  letterbox, phase captions, descent bar, high-speed capture tag.
- **Moon look** (`MoonSphere`): rugged outline while falling, terminator shading, the leading face heating up (additive
  `eyes(glow.png)` shell), seams brighter as it nears (`eyes(seams)`), adaptive mesh; with a shader pack the base is
  `beaconBeam(surface, false)` (solid, full-bright); the culled types get both windings.
- **World** (Luna): no region ticket. The crater round the moon (out past the slabs) is streamed; the rest of the floor
  is a lazy carving, then `Excavation.linger()` (TSS `Carving.linger`). `world/MoonLand` (replaces MoonRim/MoonCracks):
  rim (its inner wall cut to the floor), slabs, cracks, debris, per chunk; loaded chunks animated, the others done when
  they load (`MoonStrike.tickLingering` from the server tick; cleared on server stop). `world/MoonVeins`: the floor's
  veins, cut by the crater's own carver. `MoonBuild`: column ranges, waits for chunks through the loader.
- **The moon hidden by the clouds** (every loader, also in 1.4.0): Minecraft's clouds are one plane drawn twice, a
  depth-only pass first (`cloudsDepthOnly`) that writes depth over the whole layer, gaps included, so anything drawn
  above the cloud layer only shows where a cloud is drawn (the moon was cut to the clouds' shapes whenever the camera
  looked up at it). While pulled in, the moon is now drawn under the clouds (`MoonSphere.underClouds`, and no further
  than 256 blocks); its surface is `beaconBeam` (solid, full-bright) everywhere, not lit entity geometry (dull brown
  at night). Found with a local run on the real GPU: `./gradlew runClientTest -Dss05.clienttest.skill=moon
  -Dss05.clienttest.film=true -Dss05.clienttest.hud=true -Dss05.clienttest.shots=...` (screenshots in
  `run/clienttest/screenshots`; `renderClouds:"false"` in `run/clienttest/options.txt` turns clouds off).
- CI: the `moon` client test now runs the real film with its HUD (`film=true hud=true`), shots retimed.
- The forge-1.20.1 server test has no player near the mark: it loads the area round the mark itself now (as a player
  there would), or the outer crater/rim checks see unloaded land.

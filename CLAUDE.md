# CLAUDE.md: The Shooting Star Addition

Handoff notes for whoever (human or Claude) works on this next. Written 2026-10-14 at the end of the 1.4.0 work.
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
(author `adamko_10`). Current version **1.4.0** (in `gradle.properties` on every branch).

## 2. Branches

All branches live in **one** git repository. The original session used one worktree per branch
(`/home/claude/tssa/<branch>`, with `forge-1.20.1` in the main checkout). **Never use `git stash` with worktrees**: the
stash stack is shared by all of them and pops land on the wrong branch.

| Branch | Loader / MC | TSS package prefix | Release tag | Jar label |
|---|---|---|---|---|
| `main` | NeoForge 1.21.1 | `cyou.rimuru.shootingstardemo.mc1211.` | `v1.4.0` | `[NeoForge 1.21.1]` |
| `forge-1.21.1` | Forge 1.21.1 | `…mc1211.` | `v1.4.0+forge-1.21.1` | `[Forge 1.21.1]` |
| `forge-1.20.1` | Forge 1.20.1 (also NeoForge 1.20.1) | `…mc1201.` | `v1.4.0+1.20.1` | `[Forge 1.20.1]` |
| `fabric-26.3` | Fabric 26.3 | `cyou.rimuru.shootingstardemo.` (none) | `v1.4.0+fabric-26.3` | `[Fabric 26.3]` |
| `fabric-1.21.11` | Fabric 1.21.11 | none | `v1.4.0+fabric-1.21.11` | `[Fabric 1.21.11]` |
| `fabric-1.21.1` | Fabric 1.21.1 | none | `v1.4.0+fabric-1.21.1` | `[Fabric 1.21.1]` |
| `fabric-1.20.1` | Fabric 1.20.1 | none | `v1.4.0+fabric-1.20.1` | `[Fabric 1.20.1]` |

Differences between branches you will hit when porting:

- **Config**: `main` uses NeoForge `ModConfigSpec`; the Forge branches `ForgeConfigSpec` (same builder style, just the
  type name); the Fabric branches a small TOML writer of our own, `config/ConfigFile.java`
  (`b.section(...)`, `b.defineInRange(name, default, min, max, comments...)`, `b.define(...)`; getters are
  `SPEC.isLoaded() && X.get()`).
- **Registered content**: NeoForge/Forge hold `DeferredHolder`/`RegistryObject`s (`HalleyContent.MOON_SIZZLE.get()`),
  Fabric holds the objects directly (`HalleyContent.MOON_SIZZLE`).
- **26.3 and 1.21.11** world code: chunk tickets are `addTicketWithRadius(TICKET, pos, r)` / `removeTicketWithRadius`,
  `TicketType TICKET = HalleyContent.CHUNKS` (registered), `player.level()`, jspecify `@Nullable`, `Identifier` instead
  of `ResourceLocation` in places. Their client-test harness uses a `finalTicks`/`pendingShot` variant.
- **26.3 keys**: Minecraft 26.3 uses SDL scancodes; TSS translates GLFW codes. Vanilla 26.3 binds *Friends* to **O**,
  so Halley's default key there is **G** (71); everywhere else it is **O** (79). Luna is **N** (78) everywhere. TSS's own
  keys: menu **H**, cutscenes **J**, skills **U, I, K, Y**.
- **1.20.1 tags**: Forge 1.20.1 uses `data/forge/tags/blocks|items/...`; Fabric 1.20.1 `data/c/tags/blocks|items/`
  with the old flat names (`c:tungsten_blocks`); the 1.21 branches `data/c/tags/block|item/storage_blocks/tungsten.json`.
- `forge-1.20.1` carries the server test (`src/servertest`, 69 checks); every branch carries the client-test harness
  (`src/clienttest`).

## 3. Porting a change to every branch

1. Make and commit the change on `main` (compile locally if you can: `dev/tools/build-main.sh`).
2. On each other branch: `git cherry-pick <sha>`. Conflicts are almost always one of:
   - `HalleyConfig.java` → Forge: take main's side with `ModConfigSpec` → `ForgeConfigSpec`; Fabric: keep the branch's
     side and re-apply the change in `ConfigFile` style;
   - `compat/tss/**` → the package prefix (`mc1211` → `mc1201` on forge-1.20.1, removed on Fabric). Check
     **auto-merged** parts too: mixin descriptors like `Lcyou/rimuru/shootingstardemo/mc1211/magic/Skill;` slip through;
   - `.get()` on registered content (remove it on Fabric);
   - 26.3/1.21.11 ticket and world API lines (keep the branch's side).
   `dev/tools/resolve.py <file> ours|theirs [from=to ...]` resolves all conflict hunks of a file to one side with text
   substitutions.
3. Compile what you can locally (`dev/tools/compile-263.sh`, `dev/tools/compile-fabric-1.21.1.sh`), then one CI round
   on all seven branches (§4).

## 4. Building and CI

**Local build** (any branch): JDK 21 (and the Gradle wrapper). Put TSS jars in `libs/`, one per supported version (see
`libs/README.txt`), then `./gradlew build`. The build makes per-version mappings (`./gradlew tssMappings`), compiles
against a readable copy of the newest jar and packs one mapped copy of `compat/tss/` per version into the release jar
(README "For developers" explains it in full). The dependency range in the mod metadata comes from the jars in `libs/`.

**CI** (GitHub Actions, only on `wip/*` branches). The CI files are *not* on the real branches; they live in `dev/ci/`:

- `dev/ci/push-wip.sh <branch>` builds a commit = the branch + `dev/ci/common/` + `dev/ci/branches/<branch>.sh` (as
  `.github/ci/branch.sh`) and force-pushes it to `wip/<branch>`. The branch itself is untouched.
- The workflow downloads TSS jars from CurseForge by file id (`TSS_FILES` in `dev/ci/branches/<branch>.sh`), maps every
  version in `TSS_OBF` (1.4.0 1.4.1 1.4.2 1.4.3), builds, runs the client tests and (forge-1.20.1) the server test, and
  publishes everything as a **draft release** `ci-<branch>-<run>`: `summary.txt` (`ok:`/`FAIL:` lines), `commit.txt`,
  the jar, `shots-*.zip` screenshots, and an encrypted `secret` part (logs, mappings, `skill-api.txt` = javap of TSS's
  `Skill` per version) that only the holder of the private key can read.
- `dev/ci/ci-fetch.sh <branch> [run]` downloads and decrypts the newest draft into `ci-results/<branch>-<n>/`.
  **The private key stayed in the old container** (`/home/claude/ci-key/ci.key`). To get readable logs again, make a
  new pair (`openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:4096 -out ci.key; openssl pkey -in ci.key -pubout
  -out ci-key.pub.pem`), replace `dev/ci/common/.github/ci/ci-key.pub.pem`, and keep `ci.key` out of git.
- Status: `gh api "repos/Adamko-10/the-shooting-star-addition/actions/runs?per_page=8" --jq '.workflow_runs[] |
  "\(.head_branch) \(.status) \(.conclusion) \(.run_number)"'`. A full round of 7 takes roughly 15-25 minutes.
- Client tests (`client-test.sh`, run three times per branch): `halley`, `moon`, and `tuned` (Luna with
  `power=0.4 speed=3 damage=20`, aerial view, then the remote menu scrolled to the sliders). Options are
  `-Dss05.clienttest.<name>` (`skill`, `shots`, `view`, `final`, `time`, `power`, `speed`, `damage`); `build.gradle`
  forwards them. On 26.3 the script installs EGL (`libegl1 libegl-mesa0`) when missing.
- Delete the `wip/*` branches and `ci-*` draft releases after publishing.

Absolute paths in `dev/` scripts (`/home/claude/...`) are from the original container; adjust them.

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
- `compat/tss/mixin/`: `CastingMixin` (no-cooldown, and the server slider limits, §6), `WhiteSpaceMixin` (menu note),
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
- **`no_cooldown`** (`[stellar_remote]`): clears every cooldown before TSS syncs them.
- **Luna's music**: `assets/shooting_star_addition/sounds/luna_music.ogg` (`sounds.json` entry `luna_music`,
  `stream: true`, no registered SoundEvent). `MoonFx.music()` starts it at `MoonPlan.FALL` as a non-positional
  `SoundSource.MUSIC` sound (the Music volume slider), pitch = the speed slider, for the caster in the film and anyone
  within 1500 blocks; it stops vanilla music first and is stopped in `removed()`. The file is the user's track cut so
  its drop lands on `CONTACT`: drop at 24.5 s of the source, minus (CONTACT − FALL)/20 = 12.75 s → start at 11.75 s.
  Re-make: `ffmpeg -ss 11.75 -i <source> -vn -af "afade=t=in:st=0:d=0.5,volume=13dB,alimiter=limit=0.89" -ac 2
  -ar 44100 -c:a libvorbis -q:a 4 luna_music.ogg` (the source was quiet: max −14.8 dB). If Luna's timeline changes,
  recompute the offset.
- **Shorter endings**: Luna's film ends at `SETTLE + 95` (6 shots), Halley's at `impact + 128` (13 shots). The molten
  seam's hiss plays once (`CONTACT + 30`).

### Timelines (ticks, 20 per second, at speed 1)

- **Halley** (`HalleyPlan`): ARM 0, PRESS 7, MARK 9, COUNTDOWN 29, SIGHT 30, ENTRY 250, TOUCHDOWN 300, impact =
  TOUCHDOWN + trench_length / 8 (default 330 = 16.5 s), effects end impact + 196 (526 = 26.3 s); film ends impact + 128
  (458 = 22.9 s).
- **Luna** (`MoonPlan`): PRESS 7, ALARM 10, BREAK 45, FALL 85 (4.25 s), ENTRY 260, CONTACT 340 (17.0 s), SETTLE 410,
  effects end 680 (34 s); film ends SETTLE + 95 (505 = 25.25 s).

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
3. Changelogs: `dev/publish/changelog_140.py` writes one Markdown file per branch (first line names the MC/loader
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

## 9. Status at handoff (1.4.0)

- **1.4.0 is published** (2026-10-15): all 7 branches pushed and tagged, 7 GitHub releases (`v1.4.0` is Latest), 7
  CurseForge files (Release, Client + Server, The Shooting Star [Demo] required, + Fabric API on Fabric) and the new
  CurseForge description.
- From the cloud session, GitHub refused API release creation and branch deletion (HTTP 403, "not permitted for this
  session type"), so the releases were made in the user's Chrome (github.com release form, with the user's OK). GitHub
  turns spaces and brackets in asset names into dots (`The.Shooting.Star.Addition.1.4.0.NeoForge.1.21.1.jar`); the
  release titles carry the proper names.
- Not cleaned up yet: the `wip/*` branches and the `ci-*` draft releases (the session couldn't delete them; the user
  can, or a later session).
- On CurseForge's upload form the changelog editor starts in WYSIWYG; switch it to Markdown and check it really
  switched before setting the text, or the Markdown is saved as one plain paragraph. The form copies game versions and
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

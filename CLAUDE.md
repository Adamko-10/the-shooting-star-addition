# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

**The Shooting Star Addition** - handoff notes for whoever (human or Claude) works on this next. Written 2026-10-14 at
the end of the 1.4.0 work, updated 2026-10-17 for 1.5.0 (§12) and 2026-10-18 for the user's 1.5.0 test round (§13; status in §9),
and 2026-10-29 with the agreed plan for the next updates and Proxima (§14, §15; SA-04 since 2026-11-05).
2026-11-06: the Sonniss sound overhaul is made and waits for the user's ears (START HERE, docs/SOUNDS.md).
**2026-10-31: 1.6.0 = The Shooting Star 1.5.0 + RimuLib support (§16); read §16 before §5, which describes 1.4.x.**
2026-11-03: how RimuLib 1.2.2 makes huge carvings fast and how we'd use it (§17); Stellar Convergence, fire every
weapon at once (§18).
Everything here was true of the code at that point; if the code disagrees, the code wins. The player-facing
documentation is `README.md`; Luna's design and code map is `docs/SS-06-LUNA.md`.

### RELEASE PLAN (decided 2026-11-06 evening; the user: "what can we do to push it out today? either this session or the one after")

- **1.7.0 = everything on main except Proxima**, for **all seven versions** (the user's pick), one big push. **Proxima
  (SA-04) waits for the next update** with its new previz film and SA-04B CME (the user: "skip cme for now and add it
  next update"; held back because its current film "won't do"). It is taken out on main by `b3f05a6` ("Hold Proxima
  for the next update") and `d6d2b5f` (2/2: the vanilla damage/block tags that still named its content, which broke
  `mineable/shovel` loading); after the release, `git revert d6d2b5f b3f05a6` on main to bring Proxima back. Version
  1.7.0 is set (`51047e0`), changelog: `node dev/publish/changelog_170.js [out] [--sonniss]`.
  **Game-tested 2026-11-06** (`test scripts\autotest19.sh`, `test builds\2026-11-06 auto test 25 1.7.0 without
  proxima\`): the remote lists Halley #6, Luna #7, Convergence #8 and no Proxima; Halley's strike carved; the plate's
  music hint reads "Luna's"; Convergence shows its Stellar Quartet lock. The run found the tag errors above (fixed in
  `d6d2b5f`, built, not re-run in game). **Careful with the sounds**: `art/sonniss_sounds.sh` also writes the
  `proxima_*` files; when applying the approved sounds to `src`, copy only the halley_*/luna_* ones.
- **Sounds**: the new Sonniss ones only if the user likes them (listening in game; test jar in the Modrinth profile);
  else the 2026-11-04 overhauled ones stay. Licence grey area known to the user (Sonniss: no supplying as files).
- **Builds/CI**: the six other branches are ported (parallel Sonnet helpers, one per branch, §3) and built by CI on
  `wip/<branch>` (the user OK'd wip pushes, 2026-11-06; never the real branches until the user says push). JDK 25
  (Fabric) isn't on Adam's PC.
- Order: (1) hold Proxima, build, game-test main; (2) version 1.7.0, changelog (Python isn't on Adam's PC: write it by
  hand or in JS); (3) port six branches + one CI round; (4) the user's test; (5) push, GitHub releases, CurseForge
  (ask about the tab suspender first, §0). ETA given: main today, all seven next session at the earliest.
- **Ponytail**: Claude recommended turning it back on for this release/porting work (the user asked, 2026-11-06); the
  user set it to **lite**.
- **The ports run in the user's own agents (the user, 2026-11-06: "start them but via my antigravity or my chatgpt
  app")**: Claude recommended Antigravity (works on local folders; the ChatGPT app only if it has a local coding mode).
  Set up: `port-1.7.0` = `4927f36`, main's whole 1.7.0 squashed onto `origin/main` (same tree as main at `deb9792`);
  six worktrees in the project folder's `ports\<branch>\` on local branches `port/<branch>` (from `origin/<branch>`);
  `ports\<branch>\BRIEF.md` (made by `ports\make-briefs.js`; `BRIEF.md` and `REPORT.md` are in the repo's
  `.git/info/exclude`, never committed) = one self-contained brief per branch: cherry-pick
  `4927f36`, conflict rules, release files (TSS 1.5.1/1.5.2 and RimuLib 1.2.2 ids: Forge 9400022/9438881/9438825,
  Fabric 9400005/9438887/9438845), `StellarQuartet.REQUIRED` (false on forge-1.20.1, fabric-1.20.1,
  fabric-1.21.11), the branch's API differences, how to build (Forge: locally with JDK 21; Fabric: not here, CI);
  reports to `ports\<branch>\REPORT.md`; never push or stash. **Started 2026-11-06 ~21:35** (the user: "just send the
  prompt yourself"; Antigravity holds about one agent under its limits): Claude typed the one-line prompt ("Read
  BRIEF.md in this folder and do exactly what it says ...") through computer use into **Antigravity** (Claude Sonnet
  4.6 Thinking, Turbo) for `fabric-1.21.1`, and the **Codex app** ("ChatGPT"; GPT-6.1 Sol, reasoning High; projects
  "TSSA port forge-1.20.1" and "TSSA port fabric-1.20.1", folders trusted) for `forge-1.20.1` and `fabric-1.20.1`;
  Claude's own background helpers took `fabric-26.3`, `fabric-1.21.11` (default model) and `forge-1.21.1` (Sonnet).
  If Antigravity runs out of quota mid-way, Claude finishes that port.
  **Status 2026-11-07 01:40**: Codex finished `forge-1.20.1` (`cf4bb73`, built here, tssPack clean; Forge's effect
  packet is the readable `net.SpellFxPayload`) and `fabric-1.20.1` (`db2770b` + `2178a55`, not buildable here; its
  REPORT.md lists the TSS names CI must confirm: Fabric 1.5.2's packet record is `il11ill1iil`, kept readable
  `net.SpellFxPayload`). Both pushed to `wip/` (CI runs 153, 154). Antigravity's agent ran out of quota after 12 min
  having only touched build.gradle/gradle.properties, so a Claude helper (Sonnet) took over `fabric-1.21.1`. Claude's
  three helpers hit the 5-hour usage limit at ~23:00 mid-merge and were resumed at 01:31 (their work was saved).
  **The user, 2026-11-07 ~01:45: "once you're done push everything to github and publish all of it on curseforge,
  dont wait for confirmation just publish it"** = standing OK for the push, the seven GitHub releases and the seven
  CurseForge uploads once all seven pass CI (no user test first: their call). Sounds: the current (2026-11-04) ones
  ship; the user never gave a Sonniss verdict. `wip/main` pushed for CI too. Python isn't on this PC (the
  `dev/publish/github_160.sh` release script needs it): do the release step with `gh api` from JS or bash instead.
  **CI at 06:35**: passed `wip/main` #155, `wip/forge-1.20.1` #153, `wip/fabric-1.20.1` #157 (after `e7a6155`),
  `wip/forge-1.21.1` #158 (port `188d69a` + `731310e` foojay resolver 1.0.0, needed to build on Gradle 9.7.1 here).
  `fabric-1.21.1`: #156 failed on TSS 1.5.2's renamed Fabric packet record (`llil1ii111` -> `il11ill1iil`, fixed
  `372c243`); #159 failed after compiling with no error shown, so `ci.sh` now also prints the pack check's "missing"
  lines and Gradle's summary (main `dev/ci/common`, and the port's copy); re-run. fabric-26.3 and fabric-1.21.11
  helpers hit the usage limit again at ~02:00 and were resumed at 06:31. CurseForge still not signed in (06:35).
  **2026-11-07 ~07:25: ALL SEVEN PUSHED AND RELEASED ON GITHUB** (main `b605256` latest, forge-1.21.1 `731310e`,
  forge-1.20.1 `cf4bb73`, fabric-26.3 `7e8546d`, fabric-1.21.11 `89cd60e`, fabric-1.21.1 `8d781d8`, fabric-1.20.1
  `e7a6155`; every CI green, client tests on all four Fabric branches and main). Fixed on the way: fabric-1.21.1's
  test harness (`KeyMapping.getKey()` is NeoForge's), 1.21.11's settings receiver, 26.3's RimuLib range; CI's
  `client-test.sh` prints Gradle's error. The `wip/*` branches and all `ci-*` drafts are deleted. **Proxima is back
  on main** (`0028b31`, one revert of `d6d2b5f` + `b3f05a6`; local, not pushed; code as before the hold, not rebuilt).
  **LEFT: the seven CurseForge uploads + the project description** (`dev/publish/curseforge-description.md`,
  rewritten for 1.7.0, no Proxima mention): jars in the project folder's `releases\1.7.0\jars\` (release names),
  changelogs in `releases\1.7.0\changelogs\<branch>.md`; Release, Client + Server, TSS [Demo] + RimuLib required,
  + Fabric API on Fabric; game versions per branch. Blocked on the user signing in (§0: never enter credentials). The user picked Chrome (2026-11-07): waiting for the Claude in Chrome extension to connect and the tab suspender to be off.
  The `ports\` worktrees (local `port/<branch>` branches, BRIEF.md/REPORT.md) can go once CurseForge is done.
  Not pushed: main's commits after `b605256` (github_170.sh path fix, CLAUDE.md, the Proxima revert).
  Earlier, **Status 2026-11-07 ~08:00: PUSHED and RELEASED on GitHub: main (`b605256`), forge-1.20.1, fabric-1.20.1,
  forge-1.21.1** (tags `v1.7.0`, `v1.7.0+1.20.1`, `v1.7.0+fabric-1.20.1`, `v1.7.0+forge-1.21.1`; jars = their CI
  jars, also in the project folder's `releases\1.7.0\jars\` under release names; changelogs in
  `releases\1.7.0\changelogs\` from `changelog_170.js`, which now says Z for Epic mode on 26.3 and lists the Fabric
  server-hooks fix and 1.21.11's settings fix). Still in CI: `fabric-1.21.1` (#168: build ok, its three client tests
  die in ~13 s before the game starts; `client-test.sh` now prints Gradle's error, re-run), `fabric-1.21.11` (#166
  passed, but its plate said "This server doesn't run the addon": the client's SettingsPayload receiver was never
  registered on that branch since 1.5.0, fixed `89cd60e`, re-run), `fabric-26.3` (#167 passed, client tests included:
  TSS 1.5.2's 26.3 build starts now; its jar asked for RimuLib >=1.1.5, fixed to >=1.2.2 in `7e8546d`, re-run).
  `github_170.sh` needed a Windows path for gh (fixed). `curseforge-description.md` rewritten for 1.7.0 (top kept).
  CurseForge: the app's browser pane is still "Unauthorized" and Claude in Chrome isn't connected: the user must sign
  in (never enter credentials for them).
  **CI so far (01:50)**: `wip/forge-1.20.1` #153 ok; `wip/fabric-1.20.1` #154 failed to compile (1.20.1's
  FriendlyByteBuf has no writeVec3/readVec3 in `ConvergenceFx`; fixed `e7a6155`, re-pushed); `wip/main` #155 and
  `wip/fabric-1.21.1` #156 running. The CI key isn't on Adam's PC: read failures from the Actions job log
  (`gh run view <run id> --log`), `summary.txt` from the draft release. `fabric-1.21.1` port done by a Claude helper
  (`9c1bc01`); it and Codex's fabric-1.20.1 both made the Fabric mixin configs apply on servers too (`environment`
  "client" -> "*"/none; the json's client/common lists are split right, so it's a real fix: in 1.6.0 the server-side
  mixins never applied on a dedicated Fabric server). **CurseForge**: Claude in Chrome isn't connected and the app's
  browser pane isn't signed in to CurseForge ("Unauthorized"): asked the user to sign in there (or import its
  curseforge cookies from Chrome via the pane's menu) or reconnect the Chrome extension. GitHub releases:
  `dev/publish/github_170.sh` (no Python needed).
  After publishing: delete the `wip/*` branches and `ci-*` drafts, `git revert d6d2b5f b3f05a6` on main (Proxima
  back), and since it'll be past 2 AM, schedule the PC's shutdown with a 10-minute warning (`shutdown /s /t 600`;
  the user can cancel with `shutdown /a`).
  **Next for Claude**: read the reports, review each
  branch's diff, push each to `wip/<branch>` with `dev/ci/push-wip.sh` (the user OK'd wip pushes), fix from CI.
  The README is updated for 1.7.0 (`deb9792`).

### START HERE (2026-11-06, late evening, Adam's PC)

- **Doing right now: the sound overhaul from Sonniss: ALL 52 SOUNDS MADE, waiting for the user's ears** (status,
  how the recipe works, what was kept: `docs/SOUNDS.md` "Status"). `art/sonniss_sounds.sh <out dir> [names]` makes
  them (~3 min; no default out dir on purpose); `art/sounds/sources.md` (written by it) = which Sonniss file went
  into which sound. The user listens in `test builds\2026-11-06 Sonniss sounds\` (`new\` = all 52, `old\` = the 40
  shipped ones, same names, for A/B; `levels.txt`; the test jar = main's jar with the 40 swapped in, **in the Modrinth
  profile since 2026-11-06 ~16:25** (the user's pick; the Earth build there renamed `(Proxima Earth build).jar.disabled`;
  it also brings the untested Stellar Quartet unlock)). **The old files stay in `src/` until the user approves**; then run
  the script with `src/main/resources/assets/shooting_star_addition/sounds` and commit. Picks were made by
  measuring: the project folder's `sound library\envelope.js <catalogue row or file>` (level + low/mid/high per
  0.1 s, onsets, spectral peaks). The bundle: `sound library\sonniss gdc 2026\` (`files\` 347 sounds,
  `catalogue.csv`, `candidates.txt`). **Next**: the user's verdict per sound (fix recipes from it), then approve ->
  ship; Proxima's 12 film sounds get `sounds.json` entries and code with the film (they're cut to the previz's
  shots; `proxima_front`/`proxima_inhale` can replace the borrowed `luna_rumble`/`luna_inhale` in ProximaFx).
  `proxima_beam` is 4.0 s now (was 1.8; film-only). Not checked: a game run with the new sounds (same ffmpeg/Vorbis
  pipeline as before, so low risk; only the user can judge them anyway).
  **Licence caveat**: Sonniss GDC licence v2.0 forbids supplying the sounds "as sound effects" (files); a jar can be
  unzipped, so it's a grey area; the user may email Sonniss for written OK (offer to draft it).
- **The Railgun reference is DONE (2026-11-06)**: docs/SS-07-PROXIMA.md "The Railgun reference" (its whole film by
  film time; the pull up = ~1 s from the mark to above the clouds, exponential, straight down; then a cut to Earth from
  orbit). Recorded with the harness's new `filmlog=true` (`test scripts\autotest16.sh`, frames and camera log in
  `test builds\2026-11-06 auto test 23 railgun reference\`). **Decided** (the user left it to Claude): Proxima's zoom-out
  at the Railgun's pace, inside shot 2's 3.8 s slot (docs). The user asked again for their Stellar Quartet
  announcement (2026-11-06): the corrected text from the earlier conversation was given again. Next on the list: Proxima's new film in game (item 2).
- **Stellar Quartet on TSS 1.5.2 (2026-11-06)**: the user does **not** want to contact ikun520 (no pressure on them) and
  asked whether we "just hook in" to fix it. Advised against patching their All Rights Reserved mod (rewriting its
  hooks from outside, breaks with every update of theirs or TSS's, bugs would land on them); first see whether it
  breaks at all: SQ 1.4.0 has its own check ("incompatible with this demo version ... disabled itself for this
  session"), and our unlock only checks it is installed. Game runs `test scripts\autotest17.sh` and `18.sh` (new
  dev-only `-PextraMods=<folder>` puts other mods' jars in the prod test run; harness `press=<key name>@<tick>`) ->
  `test builds\2026-11-06 auto test 24 stellar quartet on TSS 1.5.2\`. **Result: SQ 1.4.0 on TSS 1.5.2 doesn't crash
  and doesn't switch itself off**: the game starts, TSS's Railgun works, P starts its four-screen show and H its setup
  menu, but two of its hooks into TSS fail (log ERRORs "effect filter unavailable: FxManager active list not found"
  and "fx camera hooks unavailable: NoSuchFieldException liiillli1l (Matrix4f)": keeping each screen to its own
  strike's effects, and following the strikes' films per screen); firing from it not tried. Our unlock checks the mod
  id `star_quartet`, which the game loaded, so Convergence unlocks (not screenshotted: SQ's menu took the H key).
  **So nothing for us to do**; SQ's degraded parts are theirs to update (the user won't ask them).
- **The user's PC**: free again (the user, 2026-11-06 ~16:00: "u can use my pc now"): game test runs and the
  browser pane are OK again. Still: **don't run game tests or swap the Modrinth jar while they play**, ask if unsure.
- **Ponytail mode is OFF** for this creative work (the user's pick; their `/ponytail off` worked; they asked again
  2026-11-06 and were told it is off); it may come back on for porting.
- **The rest of the update, in order, with the ETA given (about 5-8 days of sessions)**: see "Next, the update's
  remaining list" below (Railgun reference; Proxima's new film as the approved previz and the SA-04A/SA-04B split;
  the sound overhaul; SA-04B Proxima CME design + previz + build; the user's test; porting; CI/docs/release).
- **Built today, not tested by the user**: Earth in Proxima's film, random flares, Epic mode, Proxima config + sounds
  + music, the Stellar Quartet unlock (all below). Not swapped into the Modrinth profile: the Stellar Quartet unlock
  build (main at `4a21e9c` and later; the profile has the Earth build).
- **Reminders still owed** (§0): idea #16 (Mrityu mod); Proxima's "Extra content" film ideas (mentioned 2026-11-06,
  mention again when it ships); SA-05+ candidates and the free SA-03 slot.

### Resume here (2026-11-04, Adam's PC)

- **Working on**: the next major update, on `main` only (§0: one branch until the user approves), **not pushed**: the
  user wants **one big push** when the whole update is done (§9). ~25 local commits ahead of `origin/main`.
- **Done on main** (§14 has the details): TSS 1.5.1 + RimuLib 1.2.1 (§16); Luna only where land is loaded, its moon
  and crater through RimuLib's section sink (§17); no cooldown for TSS's skills; newest overlay only; evac switch (E);
  Gravitia key (unbound); keep-the-moon (L, cools down); Halley's shake eased; impact frames FULL/SOFT/OFF (F);
  protected-blocks tag; `/ssa strike` for operators; title cards in both films, with the code name (§14); the sound
  overhaul (§14, needs the user's ears). Game-tested by Claude with `runProdClientTest` (§4, "Testing on
  Adam's PC"); the user tests the jar in their Modrinth profile (§9).
- **Commands checked**: `/ssa strike <any skill> at ...` aims right, the Railgun included (§14). Title cards and the
  film overlays (broken since 1.6.0, fixed 2026-11-04) checked by screenshots (§14). **Not yet checked in game**:
  Halley's shake (feel), impact frames OFF (the white flash covers those frames), the
  Gravitia key when bound, the user's own test of the latest jar.
- **Stellar Convergence (SA-00) is built** (§18): the user's to test in game; its film's panels work with every weapon
  (the "open" bug was the test harness, fixed 2026-11-05, §18). Partly on RimuLib's public API (§17a).
- **Done 2026-11-05**: the Convergence plate is smaller, shows only while SA-00 is selected, and V folds it to its name
  (§18). Master sliders on SA-00, Gravitia last, and the Convergence shake setting built 2026-11-05 (§18).
- **2026-11-05: main moved to TSS 1.5.2 + RimuLib 1.2.2 and partly to RimuLib's public API** (§16a, §17a; the user
  picked the API switch, TSS 1.5.2 dropped meanwhile). The mapping tool's long-standing anchor bug is fixed (§16a).
- **SA-04 Proxima: stage 1 of 5 built 2026-11-05** (renamed from SA-03 the same day, the user: comet, moon, then a
  star skipped a step; **SA-03 is kept free** for a skill between the moon and a star, to fill later). The playable
  strike: on the remote as skill #8, key Z; flash burn,
  glassy bowl, scorch, damage tiers, basic effects with borrowed sounds). Its build record, stages and code map:
  `docs/SS-07-PROXIMA.md`; the agreed design: §15. Game-tested on the flat test world (`test builds\2026-11-05 auto
  test 18 proxima\flat\`: the bowl 6 deep tapering to 0 at 24 blocks, cut in 14 ms; the scorch round a village whose
  houses stand). Not yet seen: trees burning and charring (the harness can't film real land: its camera follows its own
  mark, which was water there). Its red sky (docs/SS-07-PROXIMA.md stage 1) is seen without a shader pack; not yet
  with one (the test client has none: the user's to check). Test jar
  `test builds\2026-11-05 Proxima SA-04, sky, plate style, finale\`, in the Modrinth profile.
- **2026-11-05, evening: Proxima stage 2 (the wave) built and game-tested** once the user had stopped playing (they
  asked not to test while they played: a game run opens a window on their screen, and the Modrinth profile's jar is
  held open by their running game, so don't swap it then). Then the rest of stage 2 (radio burst,
  spinning compasses, redstone sparks, steam, cracks of light), game-tested too. Jar `test builds\2026-11-05 Proxima
  stage 2 complete\`. Then stage 4's first half, game-tested: Solar Residue (a new block, left in the bowl) and the
  radiation zone (docs stage 4). Then the rest of stage 4: Flare Ash and the auroras, game-tested (the radio blackout waits for
  Proxima's HUD, stage 3). Jar `test builds\2026-11-05 Proxima aftermath\`, in the Modrinth profile.
  (Reminded the user of the Solar Residue economy, §14, on 2026-11-05.)
- **Proxima's film, stage 3: part 1 (in the world) BUILT and game-tested 2026-11-05** (docs stage 3; jar
  `test builds\2026-11-05 Proxima film\`). **Part 2, the space intro, BUILT and game-tested** the same night
  (docs stage 3; the timeline moved 60 ticks later for it: IMPACT 330; jar `test builds\2026-11-05 Proxima space
  intro\`, in the Modrinth profile). **Next: part 3 (Earth: ask before downloading imagery), or stage 5 (sounds,
  music, config)**.
- **Stage 5: the music (below), the `[proxima]` config and Proxima's own sounds are BUILT (2026-11-05, late; the
  user: "rebuild and continue work"; details: docs/SS-07-PROXIMA.md stage 5). Random flares BUILT too (the user's
  pick, 2026-11-05 night; docs stage 5; `/ssa flare <tier> [seconds]` for operators; off by default).
  **2026-11-06: Earth (stage 3 part 3) BUILT** (the user: "build earth ... do whatever the original mod did": the NASA
  imagery TSS credits, downloaded into the project folder's `art sources\nasa`; docs stage 3 part 3): `EarthSphere` in
  the film's new Earth shot, **the timeline 40 ticks later (IMPACT 370, film 28 s)**, the music re-cut. Game-tested
  (`test builds\2026-11-05 auto test 22 proxima earth\`): Earth with its terminator and clouds behind the gate, the
  beam tapered after the first run (it filled the shot). Jar 7.6 MB now (Earth's textures). Also the major flare's
  plants tested on real land (`...auto test 21 proxima music\major-trees\`: a forest's leaves gone, trunks and
  ground left). **Proxima is complete on main** (to test in game by the user).** Test jar `test builds\2026-11-06
  Proxima Earth\`, in the Modrinth profile (the random-flares build renamed `.disabled`). Flares game-tested (`test builds\2026-11-05 auto test 21
  proxima music\flares\`: `/ssa flare major 5` and `cataclysm 20`; both alerts in chat, both fired on time, the
  cataclysm's impact 9.35 s after its flash, villagers "burnt by a solar flare", no film; screenshots of the white
  flash, the red sky, the impact and the scorch). Not checked: the major flare's plants burning (the flat test world),
  the random roll itself (once a minute, 1 in average_minutes). Test jar `test builds\2026-11-05 Proxima random
  flares\`, in the Modrinth profile (the Epic mode build renamed `.disabled`). Also done then: Luna's full track rebuilt for Epic
  mode (`music\Luna full (rebuilt).flac` in the project folder: 29.09 s, drop at 24.45 s = up to 24.45 s of lead; Video
  Project 48 up to 10.03 s, +13.03 dB, crossfaded 60 ms onto luna_music.ogg from 1.52498 s; offset 8.44502 s, checked
  sample-exact, no click). **Epic mode then BUILT** (§14 "Epic mode", docs/SS-06-LUNA.md). Game run (`test builds\2026-11-05 auto test 21 proxima
  music\sounds-config\`): no sound errors, the bowl 6 deep, the config sections written, the zones' save file made.
  Test jar `test builds\2026-11-05 Proxima config, sounds\`, in the Modrinth profile (the music build renamed
  `.disabled`). Not heard by the user yet. Earlier:
  **stage 5 was started late 2026-11-05 and then PAUSED by the user** ("dont proceed, i will
  do it rn": they are getting Proxima's music file). Nothing of stage 5 is written; the tree was clean. When they send
  the file: cut it so its drop lands on IMPACT (330), like Luna's (§6, docs/SS-06-LUNA.md), then Proxima's own sounds
  (ffmpeg, like `art/make_proxima_sounds.sh`: space drone, iris grind, beam, flash, impact, wave) and a `[proxima]`
  config section (model: `MoonConfig.define`, sections in the common config; §15's list: enable switch, sizes,
  shelter blocks, knockback, radiation minutes/damage, terrain on/off, residue on/off).
  **Proxima's music is BUILT from the user's draft** (2026-11-05; the user: "take this and tweak it to fit beat wise"):
  `Video Project 49.m4a` (in the project folder's music folder), cut by `art/make_proxima_music.sh` into `proxima_music.ogg`:
  the track's gap-and-entry on FLASH, silent through the hush, its main section hard on IMPACT (details and what lands
  where: docs/SS-07-PROXIMA.md stage 5). Played by `client/StrikeMusic` (Luna's music player moved out of MoonFx and
  shared); the plate's M is now "Music" for both. When the finished track comes: set ENTRY/DROP in the script, re-run.
  Rated by measurement only (Claude can't hear): 7/10 as a draft; its main section swells in instead of hitting
  (suggested a short gap or riser and a hit there); mastered quiet (-26 LUFS, raised in the cut).
  **Luna's original** (the user, 2026-11-05: "what I think the original file for the luna one is"): `Video Project
  48.m4a` (29.1 s, in the project folder's music folder) is the same project and cut point (luna_music.ogg = it from 8.445 s, +13 dB; the first
  ~7 s match, r 0.95), **but a different version**: it lacks the riser layer (a sweep at 2-4 kHz and a noise riser,
  ~20.5-25.7 s) that builds into the drop in the shipped file, which is 5-9 dB louder at the drop (24.45 s). For Epic
  mode (a longer lead before the drop) it still helps: its 0-8.45 s are what the shipped cut lacks, so the full
  track = this file up to ~10 s (+13 dB) joined onto luna_music.ogg from 1.555 s (sample-aligned there, short
  crossfade). Epic mode no longer needs Hamza's PC for that, unless the real original turns up.
  Game run (`test builds\2026-11-05 auto test 21 proxima music\`): no sound errors of ours. Test jar and the cut file:
  `test builds\2026-11-05 Proxima music\`, in the Modrinth profile (the space-intro build there renamed `.disabled`).
  Not heard yet: by the user, in game.
- **2026-11-06, afternoon: the user wants much higher quality** ("this won't do quality wise"): better VFX in general,
  proper SFX, a zoom-out "just like railgun", a proper Dyson sphere and Proxima Centauri, a title card for Proxima,
  the infinity sign fainter, and **to see it before it's built**. Ponytail mode was turned off for this (the user's
  pick; it can come back for porting). **A film previz was built and the user approved it ("it looks good")**:
  artifact https://claude.ai/artifact/HavGduihZs5GjdHR74yqsc ("Proxima Film Previz"; three.js; source and build
  script in the project folder's `previz\`: `template.html` + `build.js` inlines the Earth jpgs, `serve.js` serves it
  for the app's browser pane (launch config `.claude/launch.json` "proxima-previz", kept out of git via
  `.git/info/exclude`; frames are grabbed by POSTing `canvas.toDataURL` to `/shot`, the pane's screenshots time out).
  13 shots, 31 s (impact 23 s = tick 460): uplink; a Railgun-style pull straight up from the shoulder through the
  clouds to orbit; Proxima Centauri (boiling surface, starspots, prominences, corona) in a ~980-panel Dyson shell with
  glowing seams, collector rings and satellites; the iris opening panel by panel (hole ~35 degrees, a dark rim stays);
  faint infinity loops; panels flying into a hex lens, the beam (core, sheath, shock rings); Earth with the gate
  catching the beam and a cone of plasma streaming to Earth; back on the land: flash, front, hush, impact, wave,
  afterwards. Title cards: PROXIMA CENTAURI (new), DYSON SPHERE, CORONAL MASS EJECTION, SA-04 PROXIMA (at the flash).
  Each shot's planned sounds are listed on the page. **Not built in game yet.** The Railgun reference (record TSS's
  Railgun film to match its zoom-out) is still to do when the user's game is closed.
- **Decided 2026-11-06 (the user's picks)**: (1) **Proxima becomes two skills: SA-04A · Proxima Flare = today's skill
  (everything it does now, with the previz's film) and SA-04B · Proxima CME = a new, bigger strike, still to design**
  (previz it first). (2) **Sounds: overhaul ALL of them (Halley, Luna, Proxima) with the Sonniss GDC library.** Its
  licence (v2.0, effective 2026-08-27; sonniss.com/gdc-bundle-license): commercial use, no attribution, "finished
  projects ... may be shared, published and sold"; but the sounds may not be supplied "as sound effects" (files),
  modified ones included. A mod jar can be unzipped: a grey area; written confirmation from Sonniss (custom licences
  offered) is the clean route, suggested to the user. Bundles: GDC 2026 = 7.47 GB, 5 zips (downloads.sonniss.com;
  track list = a public Google Sheet, 346 files from ~100 libraries: bass drops, trailer booms, braams, impacts,
  whooshes, electric arcs, sci-fi drones/reactor loops, servos, latches, metal screeches, winds, storms, fire loops,
  ice cracks, radio static, AM sweeps); GDC 2024 = 23.5 GB, 9 parts; older ones bigger. Download needs the user's OK.
  **The user OK'd downloading GDC 2026 (2026-11-06), not done yet**: downloads.sonniss.com sits behind a Cloudflare
  bot challenge (scripts get 403 "Just a moment..."; never get round it), and the page's Google Drive mirror
  (Mirror 1; file ids 1ndDWhCoq0NgkrJH6ku5MSA65zinEby9r, 1s9GAn7KBS4jr4NlF_ggTYDrXySCg6HXS,
  16PX5T28pQaEs6S8j-m6ArR16JwQCQNNB, 1HqMHc0o9Uxv5SmR3KQzx2nHIyrn3sW6M, 1XL8zDflfC86Asd4D7G1T5N1DJ6hNLQVc; direct
  download `https://drive.usercontent.google.com/download?id=<id>&export=download&confirm=t`, part 1 = 1.30 GB) said
  "Quota exceeded" (too many downloads that day). Either the user downloads the five zips in their own browser, or
  retry the mirror later. They go in the project folder's `sound library\sonniss gdc 2026\` (outside the repo; only
  our processed sounds go in the jar).
- **Film lengths** (the user asked): Proxima's film is 520 ticks = **26 s** at speed 1 (`ProximaStoryboard.END` =
  IMPACT + 190); Halley's ~23 s, Luna's ~25 s; the speed slider scales them. The user then asked **how long TSS's own
  films are** (answered 2026-11-05): each skill's `duration()` in TSS 1.5.2 (whole skill, film included; its film
  may end a little sooner, as ours do): Railgun 526 ticks (26.3 s), Gungnir 480 (24.0 s), Seven Stars 560 (28.0 s),
  Tesseract 682 (34.1 s), Gravitia 802 (40.1 s); cooldown 1200 for all. Read from `StarSkill`'s static init in the
  readable jar: its ints are XORed with a key (`lli1IIil`, from a float constant through a hash mix); the decoder
  is a few lines of JS (enum order = remote order; keys K, I, U, Y, none). The plan as agreed:
  Agreed with the user in three parts: **(1) the in-world film first** (shots following the strike: the caster's
  remote, the flash, the CME front coming down the sky, the wall rolling past, a pull-back over the scorched land;
  a title card "SA-04 PROXIMA" like Halley's/Luna's (`compat/tss/client/FilmTitle`); a film HUD (like `MoonHud.film`)
  carrying the "radio blackout": garble the skill's OWN HUD text only); **(2) the space intro** (the red star in its
  Dyson sphere, infinity-sign loops, the lens beam, title cards "DYSON SPHERE" / "CORONAL MASS EJECTION"), faked high
  above the world with our sky shader (night + stars) and the glow pipeline; **(3) Earth**: TSS-style needs NASA
  Blue Marble imagery (public domain): **downloading needs the user's OK first** (state file, source, size), ask then.
  How Luna's film is built (copy it for Proxima): `client/film/MoonStoryboard` (`Take.cut/sweep(start, end, [blend],
  Path)`, `Path.pose(Stage, partial, k)` -> `Pose(position, look, fov, roll)`, k 0..1 over the take; `Stage` gives
  `feet`, `forward`, `time` (the fx clock), `keep(pos, home)` = `FilmLand.keep`, never over unloaded land or in the
  ground); `compat/tss/client/MoonCutscene.of(adapter)` turns the takes into RimuLib `Shot`s in a `Cutscene(subject,
  tier, title, color, 0, shots).hardCut().fullFrame().priority(2).speed(adapter.filmSpeed())`, played from
  `StarClientBridge.startMoon` when `films(mine, strike)`; `ProximaFxAdapter` needs a `subject()` (`this.liiillli1l()`)
  and `filmSpeed()` like `MoonFxAdapter`, and `StarClientBridge` routes PROXIMA without a film today. Film overlays
  draw from `StarClientBridge.renderFilmHud` (both FxManager passes, once a frame); add Proxima's there.
  Then stage 5: sounds, the user's music track (cut so its drop lands on IMPACT), config (§15 list), random flares.
  The user's feedback on stage 1: the sky with shaders "changes but not much" (now painted over the pack's sky too);
  radius "decently larger" (doubled: bowl 36, char 100, scorch 220); the CME "on a massive scale, many chunks and much
  more vfx, like a proper big wave" (the coronal front shell and ground zero's shock wave, which the scorch and the hits
  now follow). Details and test results: docs/SS-07-PROXIMA.md stage 2.
- **Next, the update's remaining list** (rewritten 2026-11-06 evening, with the new quality work; ETA given then:
  **about 5-8 days of sessions**, plus the user's test rounds and two outside waits):
  1. the Railgun reference (DONE 2026-11-06: docs/SS-07-PROXIMA.md "The Railgun reference");
  2. Proxima's new film in game, as the previz (the zoom-out from the land to orbit, a real star shader, the ~980-panel
     Dyson sphere with its iris, rings, the hex lens, Earth and the gate, the title cards, the 31 s timeline, the music
     re-cut), and the split into **SA-04A · Proxima Flare** (today's skill) / **SA-04B** (1.5-3 days);
  3. **the sound overhaul from Sonniss** (MADE 2026-11-06: all 52 files, waiting for the user's ears, START HERE; all ~35 events of Halley, Luna and Proxima, plus the new film's: docs/SOUNDS.md;
     the bundle is downloaded and catalogued in the project folder's `sound library\sonniss gdc 2026\` with
     `catalogue.csv` and `candidates.txt`), then the user's listening rounds (1-1.5 days);
  4. **SA-04B · Proxima CME**: design, a previz like Flare's, then build it (strike, effects, film, sounds, config)
     (1.5-2.5 days);
  5. the user's in-game test of everything built so far and fixes from it;
  6. port to the six other branches (§3; TSS 1.5.2 + RimuLib 1.2.2 and the API switch on each; 26.3/1.21.11 render code
     by hand, much new rendering now; the Stellar Quartet lock off on 1.20.1/1.21.11) (1.5-2 days);
  7. CI, one Forge test, README/changelog/CurseForge text (credits: NASA imagery, Stellar Quartet; Sonniss needs no
     credit), the one big push and the releases (about half a day).
  Waiting on others: the user's finished Proxima track; a Stellar Quartet update for TSS 1.5.2 (else nobody can unlock
  Convergence); Sonniss's answer if the user emails them about shipping sounds in a mod.
- **Remind the user** (§0): idea #16 for the Mrityu mod (asked again 2026-11-04).

## 0. The user's rules (follow these exactly)

**Two people work on this, on two PCs. Check which one you are on first:** if the project sits in a folder called
`software files` (e.g. `C:\Users\HamzaTom\Desktop\Dont Touch\software files\shooting star adon with adam\`), you
are on **Hamza's PC**; anywhere else, you are on **Adam's PC**. Adam is the one with the Chrome tab suspender and the
2 AM shutdown policy; Hamza has neither (no tab suspender to ask about, never shut his PC down). The two rules marked
*(Adam's PC only)* below apply only on Adam's PC; everything else applies on both.

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
- CurseForge files: **Release**, **Client + Server**, depending on **The Shooting Star [Demo]** (required) and, since
  1.6.0, **RimuLib** (required), plus **Fabric API** (required) on Fabric builds.
- In Markdown changelogs, don't put `(` right after `]`.
- Never print the GitHub token. Never enter credentials anywhere.
- **New features are built on one branch first** (`main`, unless the user picks another; user, 2026-11-03): the user
  wants to see a feature as they imagine it (look, VFX, film, sounds) in one version, test it and approve it before
  anything is ported. Only after that approval does it go to the other six branches (§3); from then on **every change
  goes to every branch**. Sonnet subagents (`model: "sonnet"`) are fine for bounded mechanical work.
- *(Adam's PC only)* For browser uploads (CurseForge), ask the user to disable their Chrome tab suspender first.
- Deleting the user's files needs permission (rename mods to `.disabled` instead).
- Talking to the user: after coding, give a clear list of **what they need to do**; for testing, a list of **what to
  test**. They may say "you can stop telling me now"; start again when the topic changes (building → testing etc.).
  Adam skims long replies (2026-11-03): keep replies short, ask every open question as clickable choices
  (AskUserQuestion), and re-ask anything earlier left unanswered.
- **Reminders to give the user** (bring each up at its moment, unasked; the user wants none forgotten):
  - idea #16 (§10), for the Mrityu mod: "later", e.g. at the end of a work session;
  - Proxima's other film ideas (§15, "Extra content") once Proxima's film is built and again when it ships;
  - the SA-05+ candidates (§10; reminded 2026-11-05) and the free SA-03 slot; the Solar Residue economy (§14) when
    Proxima is built.
- **Update this file at the end of every turn** (user, 2026-11-03): decisions, answers, new ideas, what changed and
  what's open, in the section it belongs to, then commit it on its own (push only when the user says so, as always).
- **Keep every project file in the project folder** (§2), not in Downloads or elsewhere.
- *(Adam's PC only)* If finishing a task past 2 AM their time, shut down their PC (unless told not to). If running low on tokens, schedule
  a continuation.
- The user is budget-conscious: don't over-test; one CI round per change set is normally enough, and they test in game.

## 1. What this is

An unofficial addon for **The Shooting Star [Demo]** ("TSS", mod id `shooting_star_demo`, by rimuru_dev, CurseForge
project 1719195, All Rights Reserved). It adds two skills to TSS's Stellar Remote:

- **SA-01 · Halley** (skill id `halley`): a comet comes in over the horizon, ploughs a frozen trench and detonates on the
  crosshair, leaving an icy crater with a Comet Heart block.
- **SA-02 · Luna** (skill id `luna`, `MoonInfo.ID`; the code calls it "moon" everywhere else): the moon cracks, tears loose and falls onto the crosshair, leaving a crater with
  the moon (made of moon cheese) buried in it.

Mod id `shooting_star_addition`, Java package `dev.ss05.halley` (the "SS-05/SS-06" names in code are the old
codenames; players see SA-01/SA-02). GitHub `Adamko-10/the-shooting-star-addition`; CurseForge project **1725916**
(author `adamko_10`). Current version **1.7.0** (released on GitHub 2026-11-07, all seven branches; TSS 1.5.2 + RimuLib 1.2.2 only; 1.6.0 = TSS 1.5.0, §16).
Since 2026-11-03 `main` (not released) targets **TSS 1.5.1 only** with RimuLib 1.2.1 (§16, "TSS 1.5.1"); the other six
branches are still on 1.5.0.

## 2. Branches

All branches live in **one** git repository, one worktree per branch. On the user's PC they are under
`C:\Users\HamzaTom\Desktop\Dont Touch\software files\shooting star adon with adam\`: `tssa-main` (main),
`tssa-forge1211`, `the-shooting-star-addition-main` (forge-1.20.1; the main checkout, `.git` lives there), `tssa-fabric`
(fabric-26.3), `tssa-fabric12111`, `tssa-fabric1211-next` (local branch `fabric-1.21.1-next` tracking
`origin/fabric-1.21.1`: push it with `git push origin fabric-1.21.1-next:fabric-1.21.1`), `tssa-fabric1201`. Leave
`tssa-fabric1211` alone: it holds the user's old uncommitted 1.3.0-era edits on the local `fabric-1.21.1` branch.
**Never use `git stash` with worktrees**: the stash stack is shared by all of them and pops land on the wrong branch.
On **Adam's PC** everything lives in the project folder `C:\Users\adam_\Documents\Shooting Star Addition\` (made
2026-11-03): `the-shooting-star-addition\` (one plain clone on `main`, no worktrees), `early access\` (TSS 1.5.0
early build, TSS 1.5.2's locked launcher, RimuLib 1.1.4 and 1.2.2: never commit, never share), `old versions\` (older
release jars and the 1.1.0 handoff), `old notes\` (older CLAUDE.md copies, superseded by this file). Release jars go in
`releases\<ver>\`, test jars in `test builds\<date> <what>\`. The first clone, `C:\Users\adam_\Documents\the-shooting-star-addition`,
went to the Recycle Bin (user's OK, 2026-11-03; nothing unpushed). The clone's `libs/` has TSS 1.3.3-1.5.1 (NeoForge)
and RimuLib 1.2.1, mapped (§4, §16).

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
`./gradlew tssMappings -PtssJar=1.4.0`, then 1.4.1, 1.4.2, 1.4.3, 1.4.4 (a mapping made without the previous version's
is garbage). Every worktree's libs/ has all of them, mapped, as of 2026-10-18. The build's `tssPack` step checks every TSS member and mixin target against every version: a "missing"
there is a real bug. Fabric 1.20.1's Loom sometimes fails once with "Cannot mutate content repository descriptor":
run again. The `dev/tools/*.sh` scripts are the old container's (`/home/claude/...`), not usable here.

**Local client test** (real GPU, opens a game window): `./gradlew --no-daemon runClientTest -Dss05.clienttest.skill=moon
-Dss05.clienttest.film=true -Dss05.clienttest.hud=true -Dss05.clienttest.time=6000 -Dss05.clienttest.shots=70,250,395`;
screenshots land in `run/clienttest/screenshots/` (`run/clienttest/options.txt` sets game options, e.g.
`renderClouds:"false"`). About 5 minutes; worth it for rendering bugs CI screenshots can't explain.

**Testing on Adam's PC** (since TSS 1.5.0 the readable dev run can't run TSS, §16): `./gradlew --no-daemon
runProdClientTest -Dss05.clienttest....` (JDK 21 at `C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot`)
loads the release jar with TSS's real jar from `libs/` in `run/prodclient`. The addon's client switches are set by
writing `run/prodclient/config/shooting_star_addition-client.toml` before the run (e.g. `[stellar_remote]`,
`keep_moon = true`, `impact_frames = "OFF"`; NeoForge fills in the rest). Harness options beyond §4's CI list:
`world=normal` (real land), `command=<chat command>` (cast through a command, e.g. `ssa strike halley at 160 ~ 30`,
instead of the remote; the player is made operator; several separated by `;`), `protect=true` (a command block near
the mark, checked at the end), `probe=x,z` (logs the land's height there at the end), `final=menu` (the H menu and
the plate). The harness's own lines (`[SS05CLIENTTEST] ...`: the strike's real mark, the probes, the Gravitia key) go to
the Gradle output, not `latest.log`. Screenshots in `run/prodclient/screenshots`. The batch scripts used are in the
project folder's `test scripts\` (results in `test builds\2026-11-04 auto test*\`). About 5 minutes a run; a game
window opens on the user's screen.

How the build works: it makes per-version mappings (`./gradlew tssMappings`), compiles
against a readable copy of the newest jar and packs one mapped copy of `compat/tss/` per version into the release jar
(README "For developers" explains it in full). The dependency range in the mod metadata comes from the jars in `libs/`.

**CI** (GitHub Actions, only on `wip/*` branches). The CI files are *not* on the real branches; they live in `dev/ci/`:

- `dev/ci/push-wip.sh <branch> [local ref]` (run from any worktree; it uses its own `dev/ci/`) builds a commit = the branch + `dev/ci/common/` + `dev/ci/branches/<branch>.sh` (as
  `.github/ci/branch.sh`) and force-pushes it to `wip/<branch>`. The branch itself is untouched.
- The workflow downloads TSS jars from CurseForge by file id (`TSS_FILES` in `dev/ci/branches/<branch>.sh`), maps every
  version in `TSS_OBF` (1.4.0 1.4.1 1.4.2 1.4.3 1.4.4), builds, runs the client tests and (forge-1.20.1) the server test, and
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
- **Luna** (`MoonPlan`, since §13): PRESS 7, ALARM 10 (music starts), BREAK 30, FALL 50, TRAIL 85, ENTRY 200, CONTACT
  330 (16.5 s, Halley's default impact), SETTLE 400, effects end 670 (33.5 s); film ends SETTLE + 95 (495). (1.4.0:
  ENTRY 260, CONTACT 340; the first 1.5.0 push: ENTRY 305, CONTACT 405.)

Film, sounds and server all follow these plans; change a plan constant and everything moves with it.

## 7. When TSS updates

Follow README "When The Shooting Star updates". In CI terms: add the new version's CurseForge file ids to `TSS_FILES`
and the version to `TSS_OBF` in every `dev/ci/branches/*.sh`, push all branches to `wip/`, and read `summary.txt`
(mapping, build, client tests) and the mapping report. For 1.4.3 the only real break was the dependency range; the
build now takes the range from `libs/`. If `TssMatch` fails with "Mapping target name conflicts", it's a new member
whose obfuscated name equals a readable target: the rename logic in `TssMatch` handles that case; extend it if a new
kind appears.

TSS 1.4.4 CurseForge file ids (2026-10-17): NeoForge 9231715, Forge 9231721, Fabric 9231726; 1.4.3: 9171007, 9171001,
9170986 (older ones are in the branch scripts). 1.4.4 needed no code change: the mapping is clean (it removed TSS's evac
members, MouseTurnMixin's and, on 1.21.11, `WhiteSpace.ITEMS/itemOf`, none of which `compat/tss` uses); the 1.5.0 jars
just didn't list it. To find what a new TSS's own skills do differently, decompile the readable jar the build makes
(`build/shooting-star-named/*.jar`) with Vineflower (`~/.gradle/caches/modules-2/files-2.1/org.vineflower/...`) into
the scratchpad, never into the repo. Its own log (`run/clienttest/shooting_star.log`) times every carving
(`[carve-timer]`).

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
   user to turn off their tab suspender first (Adam's PC only, see §0).
6. Project description: `dev/publish/curseforge-description.md`. **Keep the top part (title line, Discord, YouTube,
   other mod links) exactly as it is**; update the rest to match the features.
7. Delete `wip/*` branches and `ci-*` drafts.

## 9. Status (2026-11-03)

- **2026-11-03, evening** (Adam's PC): the project folder is made (§2). Its clone's `libs/` has TSS 1.3.3-1.5.0
  (NeoForge) and RimuLib 1.1.5 from cursemaven, the mapping chain is made, and `main` builds (pack check clean).
  Luna now holds the land round the mark from the cast (§17, item 2): built, **not tested in game**, not pushed.
  Then (the user's OK) `main` moved to TSS 1.5.1 + RimuLib 1.2.1 (§16): test jar in
  `test builds\2026-11-03 Luna preload, TSS 1.5.1\` (the folder marked "TSS 1.5.0 (older)" is the first build).
  New idea: Stellar Convergence (§18). Commands: TSS's skills too, and a no-limits warning (§14). The user reset the
  API key and bot token that were in their Discord export. Still unknown: the server finale date.
  **Where the user plays (Adam's PC)**: the Modrinth App profile `D:\ModrinthApp\profiles\NeoForge 1.21.1 (1)\mods`
  (the user asked for test jars to go there). It has TSS 1.5.1, RimuLib 1.2.1 and the test addon jar since
  2026-11-03 (copied in by hand, so Modrinth shows them as unmanaged), next to Sodium, Iris, Lithium, C2ME (alpha:
  changes chunk generation, suspect it first if craters or loading misbehave), ModernFix, Distant Horizons and others.
  Replace the addon jar there with each new test build (the old one renamed `.disabled`, §0).
- **2026-11-03, night** (Adam's PC): the user tested the TSS 1.5.1 build: it works. Their log (Luna at 10x power):
  server stalls of 2-18 s for ~5 minutes, from `MoonBuild` (a `Map<BlockPos, BlockState>` per chunk, then
  `Carving.paint`, on the server thread; a 10x moon has ~1000x the blocks), not from C2ME; MoonLand's rim and cracks
  took 1.8 s in all (930 writes, 27108 chunks, worst tick 66 ms). Also ~720 "Tried to load a DUMMY block entity ... but
  found not block entity block moon_cheese" warnings (saved block entities left under the moon; cause not proven).
  Done on main, built, **not tested in game**: `MoonBuild` now builds through RimuLib's `SectionSink` (§17, item 3);
  no cooldown now works for TSS's own skills (§12, below); `TssRemap` maps descriptors of unmapped mixin targets.
  Test jar `test builds\2026-11-03 Luna fast build\`, also in the Modrinth profile (the two earlier test jars there are
  renamed `.disabled`). The next major update's contents are decided (§14).
  Then the user's test: Gungnir twice works but TSS's overlay drew twice. Now only the newest effect of each kind
  draws its overlay (the user's pick): `FxManagerMixin` on `renderHud` takes the older ones out of FxManager's list
  while it draws (`StarClientBridge.newestHudsOnly/allHuds`), and our film HUDs draw the newest strike's only. Luna at
  1x looks the same; at 10x "not noticeably faster, it takes forever": second version (§17 items 2, 3), test jar
  `test builds\2026-11-03 Luna fast build 2, newest overlay\`, also in the Modrinth profile. Not tested in game.
- **2026-11-04, past midnight** (Adam's PC): the user's test: 10x "significantly faster", but (1) the evac put them
  in bedrock and the strike seemed slow to start: the evac's teleport to unloaded land read its height as the bottom
  and blocked the server tick on loading it, behind the 65x65 preload (40-58 s, ModernFix's watchdog dump showed
  `ServerPlayer.teleportTo` -> `getChunkBlocking` from `TssLink.liftClear`); (2) the countdown ("KINETIC STRIKE
  INBOUND") still drew twice: TSS calls each effect's `hud` from `FxManager.renderFilmHud`, not `renderHud`. Timing
  lines: crater round the moon cut 57.85 s (of spell clock) after the impact (generating ~4000 new chunks), the moon
  built in 6.5 s (2940 chunks, worst tick 58 ms), rim and cracks 2 s (worst tick 846 ms); 244 DUMMY warnings left
  (MoonLand's `paint` loading saved block entities the crater left). Fixed on main (built, not tested): the evac
  switch (§14), the newest-only filter on `renderFilmHud` too, and `TssLink.paint` drops those block entities first.
  Test jar `test builds\2026-11-04 evac switch, countdown fix\`, also in the Modrinth profile (four older test jars
  there renamed `.disabled`). For the user's server: pre-generating the map (Chunky is installed) makes big strikes
  fast without any code. Then both speed-ups (§14) on main, built, not tested in game: test jar
  `test builds\2026-11-04 only where land is loaded\`, also in the Modrinth profile.
- **2026-11-04** (Adam's PC): the "only where land is loaded" build is **not tested yet** (the game's last log is from
  before it). Meanwhile, on the user's pick: the Gravitia keybind and the keep-the-moon switch (§14), built, not
  tested; test jar `test builds\2026-11-04 Gravitia key, keep the moon\`, in the Modrinth profile (it includes the
  untested speed-ups; the older jar is renamed `.disabled`). Sound-overhaul scope and finale date answered (§14).
  Then (user's pick) Halley's shake and the impact-frames setting (§14); test jar
  `test builds\2026-11-04 shake, impact frames\`, in the Modrinth profile (holds everything above, untested).
  20 commits on main, none pushed. The user asked again to be reminded of idea #16 (§10) later.
  **Then tested by Claude** (the user asked; production-style runs, `runProdClientTest` with TSS 1.5.1 + RimuLib
  1.2.1 on this PC's GPU, client settings written to `run/prodclient/config/shooting_star_addition-client.toml` before
  each run; screenshots and logs in the project folder's `test builds\2026-11-04 auto test\`): no errors, no "Can't
  keep up", no DUMMY warnings in any run. Luna at 10x (world=normal): the crater round the moon and the moon done where
  land is loaded, 841 chunks in 4.6 s, worst tick 22 ms, 5177 waiting for their land (the rim, ~1100 out, had 0 loaded
  chunks: right). Luna at 1x: 87 chunks in 3.6 s, rim 747 writes in 265 ms (worst 29 ms). The plate's five rows fit.
  The kept moon stayed past the end, but with its seams glowing orange for good (`crack` and `approach` are 1 at
  rest): the user picked "cool down", so a kept moon's seam glow fades and its seam lines darken over DURATION-300 to
  DURATION (`MoonSphere`: `embers`, `soot`), re-tested (B3). Not checkable by screenshot: Halley's shake, whether the
  impact frames are off (the white flash fills those frames), and Gravitia's key (unbound shows no label; the hook is
  packed, target `SkillKeys.register()V`). The harness waited forever for a kept moon to end before its final views:
  it now goes on when `MoonFx.kept()`. Test jar `test builds\2026-11-04 kept moon cools\`, in the Modrinth profile.
  **No push until the update is done** (the user: "lets push it all into one big push"): everything stays on local
  `main` until the release. Then (user's pick) **operator commands and the protected-blocks tag** (§14), both built
  and game-tested (`test builds\2026-11-04 auto test 2\`): `/ssa strike halley at 160 ~ 30 2 inf 1.5` struck at
  exactly (160.5, 41, 30.5); `/ssa strike the_shooting_star at 140 ~ 0` fired TSS's Railgun (RimuLib logged its cut of
  544 chunks; its aim not checked, the harness has no effect of its own to film it by and waited until killed); a
  command block placed in Luna's crater was still there after the strike. Test jar
  `test builds\2026-11-04 commands, protected blocks\`, in the Modrinth profile.
- **2026-11-03** (Adam's PC):
  - **TSS 1.5.1 is out** (CurseForge 2026-11-01, after our 1.6.0). `tssVersionRange` (gradle/tss.gradle) allows each
    supported version up to its next patch, so 1.6.0 declares TSS `[1.5.0,1.5.1)` and refuses to load next to 1.5.1
    (checked in a local build of the same code; that's why the Luna test jar first needed TSS 1.5.0). Main now
    supports 1.5.1 (§16).
  - **Why TSS's big carvings feel instant** (read from an unobfuscated early TSS 1.5.0 build for Fabric 1.21.11 and
    RimuLib 1.1.4, decompiled in the scratchpad): Gungnir (`RADIUS` 400) holds one chunk ticket (radius <= 32) from
    the cast to its impact, so the 16 s wind-up loads the land; `Carving.buildAhead(front)` builds each chunk's carved
    sections on 2-8 "Carving builder" threads from copied palettes a few ticks before the wave reaches it, and the
    server thread only swaps them in (a `ChunkEdits` stamp throws away copies of chunks that changed meanwhile);
    sections wholly above the floor become empty sections; `batched(floors)` computes floors for many chunks at once;
    `perTick(36)` follows the visual wave front. We already use flatten/perTick/streaming/linger/paint
    (`TssLink.excavate`), not `buildAhead` or `batched`, and Luna holds no wind-up ticket (§12).
  - The server finale is "in a while": no deadline. The Discord bot: decide later. Halley's music: the user thought of
    something like Interstellar's "Cornfield Chase" (Hans Zimmer, copyrighted: not shippable without a licence).
  - **Decisions**: the user plays and tests on **NeoForge 1.21.1**, so `main` is the one-version branch (§0). TSS 1.5.1
    (and newer) support ships **with Proxima**, not as a fix of its own. Their server uses a claims mod (FTB Chunks):
    no claim support wanted (our carving writes straight into chunks, so it ignores claims). The user is making
    Proxima's music now. When work starts: the addon's plate in the H menu says that Luna's music follows Minecraft's
    **Music** volume slider (§13).
  - **TSS 1.5.2 / RimuLib 1.2.2 (early access)**: TSS 1.5.2's early jar is a launcher that logs in (hardware id) and
    loads the mod into memory: nothing to read in it, and don't try to get round that (no memory dumps either: the user
    asked, 2026-11-03, and dropped the idea). RimuLib 1.2.2 is a plain jar and holds all the speed-ups: see §17.
  - **The TSS developer's terms** (the user's notes, 2026-10-18): rimuru.dev said the addon may go on despite the
    obfuscation; he doesn't want Bedrock ports or his assets used. Never use TSS's assets (players asking for films
    "exactly like the original" get the style, made with RimuLib's film tools, not copies).

- **1.6.0 is released** (2026-11-01, §16): all seven branches pushed (CI green: builds everywhere; in-game tests on
  main and Fabric 1.21.1/1.20.1/1.21.11; none on Forge, none possible on 26.3, see §16), seven GitHub releases
  `v1.6.0...` and seven CurseForge files (TSS [Demo] + RimuLib required, + Fabric API on Fabric). Jars: CI's, from the
  pushed code; copies in `The Shooting Star Addition releases/1.6.0 (to test)/`. The user didn't test before release.

- **2026-10-29** open items:
  - **NeoForge music** (reported against 1.5.0): Luna's music and its M switch didn't work on NeoForge but did on
    Forge. 1.5.1 fixed M/C (they needed Shift) and moved the music to `SoundSource.MUSIC` (§13); the user will retest
    on 1.5.1. If still broken, suspect `AddonNetwork` (NeoForge's optional payload registrar): `SettingsPayload` not
    reaching the client or the switch not reaching `MoonFx.Music`.
  - The user wants to be told when to start work; **don't start §14/§15 until they say so**.
- **1.5.1 is released** (2026-10-18): all seven branches pushed; seven GitHub releases `v1.5.1...` (main's is latest)
  with the jars and the changelog from `dev/publish/changelog_151.py` (everything since 1.4.0), and seven CurseForge
  files (Release, Client + Server, auto-publish once approved; TSS [Demo] required, + Fabric API on Fabric). The jars
  were built locally from the release commits (code identical to §13's green CI round; only the version changed); copies
  and changelogs in `The Shooting Star Addition releases/1.5.1/`. The `wip/*` branches and all `ci-*` drafts were
  deleted afterwards. Not done: the CurseForge project description (§8 step 6) - only when the user asks.
- CurseForge's upload form (authors.curseforge.com, `#/projects/1725916/files/create`) on this PC: screenshots time
  out, so it was driven by script: MUI autocompletes found by their label (Environment, Modloader, Java, Minecraft;
  remove chips via `.MuiChip-deleteIcon`, add by setting the input's value with the native setter + an `input` event,
  then clicking the exact `[role=option]`); the editor switched to Markdown, then `textarea.mde-text` set the same way;
  Fabric API added through "Add related project" (search "Fabric API", option "ID: 306612", "Required Dependency").
  The form copies the previous file's fields, sometimes stale ones and never Fabric API reliably: set every field each
  time. The tool output hides version-like strings (dots): print them with the dots replaced.
- 1.5.0 is the first push of the same work: GitHub releases only (2026-10-17, 5 downloads), never on CurseForge; the
  user tested it and asked for §13. Its releases were left as they are; 1.5.1 supersedes it.
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
   **Planned for the next update (§14).**
7. **Slider scroll steps**: TSS's slider behaviour; ours follow `Power`'s own `step()`. Could only be changed in TSS.
8. **Typing a number into the sliders** (text box): TSS's menu; a mixin into `WhiteSpace` would be fragile.
9. **Fonts**: TSS's menu font; not ours to change.
10. **Harder recipe**: for the remote; goes with 3.
11. **Yog-Sothoth** skill, 12. **supernova / black hole / TON-618** skills, 14. **planets**: new skills (SA-03, the free slot between moon and star, or SA-05...).
   Each is a project like Luna (plan, params, strike, fx, storyboard, sounds, config, docs). SA-04 is Proxima (§15; SA-03 until 2026-11-05).
   **SA-05+ candidates the user wants to discuss later (reminded 2026-11-05, when Proxima got under way):**
   - **Neutron star**: gravity waves causing earthquakes, volcanoes, tsunamis and cracks "in the entire world". Must be
     scaled to a region (whole-map edits would freeze the server): pulses of gravity waves, cracks rippling out from a
     centre, a few magma vents, the sea pulling back and surging in.
   - **White hole**: spews matter and energy over the land. Concept still rough.
   (Both came from the user's community poll, 2026-10-20, alongside the solar flare; inspired by TSS's own black hole.)
16. **Custom animation when a boss is hit by a shooting-star laser** (user's note, 2026-10-15): for the user's
   **Mrityu mod**, not this addon. **Remind the user about it later.**
13. The CurseForge comment asking for speed/range/damage options: done by the sliders (1.4.0).
15. **Music for skills**: Luna has hers; the user plans one for Halley too (same approach as §6: cut the track so its
   drop lands on Halley's impact, `HalleyPlan` impact = 330 at default trench length, start at e.g. ENTRY or SIGHT).

From the user's own notes (notebook and Discord self-DMs, 2026-10-23..11-03; none decided yet):

- **Luna Omega**: retcon Luna into a random rogue moon (so it can stay made of cheese) and add **Luna Omega**, the
  real moon, not made of cheese. (Reading confirmed 2026-11-03; the user isn't sure about the idea yet.)
- **Black holes** (for the TON-618 idea, #12): a small one with no film (or a spawn animation) that compresses mass and
  sucks it in, destabilises, then stabilises, with a switch to stop it stabilising; a huge one in the sky
  ("gigantophobia") with a film.
- **Combined skills**: e.g. Luna + Gungnir = a molten moon-cheese moon, its own model and a new film.
- Proxima: blocks thrown into the air (block displacement); its own default key and configs.
- Luna: a switch on the H-menu plate to keep the moon model after landing instead of crumbling (for filming; the
  note "make this toggleable in h menu" meant this, user 2026-11-03); a delayed shockwave.
- Halley: a little less screen shake in the seconds after the explosion. Both skills: a setting to reduce or remove
  impact frames.

From the CurseForge comments (reviewed 2026-11-03):

- **Gravitia keybind** (TSS's own SS-06, which has no key): a toggle to give it one. The user promised it for "the next
  major update". Done on main 2026-11-04 (§14).
- **Title cards** like TSS 1.5.0's for our films (Proxima's are planned, §15).
- **The sounds sound AI-made** (they are): replace the main ones with real or licensed sounds.
- **Films "exactly like the original"**: the style, not TSS's assets (§9).
- **Incursion** (gomez_96): two Earths colliding. Could reuse Luna's machinery.
- **Pulsar** (sound_dream): a gamma-ray beam sweeping a line, leaving radiation. Fits the neutron-star candidate.
- Forge: players found it buggy, and since 1.6.0 Forge gets no in-game test (§16): test it once before a release.

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
- The TSS mapping chain can swap names of look-alike members (e.g. `SkillTuning.speed/power`, and since 1.5
  `FxManager.renderHud/renderFilmHud`); verify with `skill-api.txt` (javap per version) before using a new TSS member,
  and look at a film test's screenshots with `hud=true` after every TSS update (the film overlays vanished silently).
- Big Luna strikes stalled the server for ~29 s when loading their whole area; fixed by streaming chunks with capped
  tickets. Keep world edits incremental.
- Halley's trench must be cut after the crater is finished, or the server test's `crater_carved` check fails.
- A 1.21 record codec silently ignores unknown JSON keys: re-check data files against vanilla after porting.
- CurseForge's upload page loses state when the tab is suspended.
- `TssMatch` used to pair same-shaped methods by obfuscated name, which silently swapped `Subject.forward/target` and
  shifted `CutsceneDirector.automatic` in 1.4.3 (§12). It now compares the fields they use, but after a TSS update
  still check what `compat/tss` calls: deobfuscate with `dev/tools/Deobf.java` and compare bodies with readable 1.3.4.
- Minecraft's clouds (1.21.1) are drawn after the entities, and their shader discards the gaps, so a moon drawn at its
  true depth before them is hidden by the clouds and shows between them. §12's claim that they write depth over the whole
  layer was wrong; pulling the moon under the cloud layer (1.5.0's first push) made it show *through* the clouds (§13).
- Every `Carving.paint` (and TSS carving) relights the chunk and resends it whole to every player: write each chunk as
  few times as possible (§13).
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
- **World** (Luna): no region ticket (since 2026-11-03 one again, for the crater round the moon only, §17). The crater
  round the moon (out past the slabs) is streamed; the rest of the floor
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

## 13. 1.5.0, second round (2026-10-18: the user's test of the first push)

What the user asked for: the moon's approach longer, landing at the same time as Halley's; it fully accelerates a
little earlier; a different trail while it approaches (not from the very start) that turns into a cooler one as it
is about to crash; the model looked worse, especially in re-entry ("it just looks wrong"); a smoother hand-over from
the model to the blocks; the moon not seen through the clouds (until it is under them); the music sounded bad (the
user gave the original track, `Video-Project-48.mp3` in the parent folder) and should start a little sooner; the music
not following Minecraft's Music slider; M and C in the H menu doing nothing; chunk destruction not as smooth as TSS's
own; the moon resizing wrongly with the power slider and changing size mid-fall; and "fix for new shooting star ver"
(TSS 1.4.4, out 2026-10-17).

Findings and fixes (main `449de31`, `f64b0c3`, `e72839c`; cherry-picked to every branch, 26.3 and 1.21.11 by hand):

- **M/C**: TSS calls `WhiteSpace.key(key, scancode, modifiers)`, presses only; the mixin read the third argument as the
  action and required 1, so the keys worked only with Shift held. 26.3/1.21.11 (KeyEvent) never had the bug.
- **Timeline** (§6): CONTACT 330. `MoonPlan.fall()` is two stages with no speed jump: a steady approach (a cubic) to
  `ENTRY_SHARE` 0.3 at ENTRY, then a dive whose speed rises about fourfold (`DIVE_POWER` 2.5). `burn()` catches fire at
  ENTRY; `dustTrail()` for the approach. Film takes retimed.
- **True size**: the moon starts `moon_radius / START_RATIO` (ten radii) from the mark, so it looks like the vanilla
  moon at any power, and `MoonSphere.placement` always draws it at its true size and place, pulled in along the line
  of sight past the far plane (`fits = far / (1 + R/D)`, scaled about the camera: identical on screen). The first
  push blended a made-up apparent size that depended on the camera (jumping between film shots, shrinking at low power).
  `MoonVisuals` maps everything attached to the moon with the same camera scale.
- **Clouds**: with true depth the clouds hide it while it's above them; when it can't be drawn far enough out to stay
  behind the cloud layer (`hiddenByClouds`), it is drawn without depth (`beaconBeam(surface, true)`; 26.3/1.21.11:
  `HalleyPipelines.MOON_BLEND`) so the clouds drawn later still cover it. Only camera-facing quads are built.
- **Look**: no sun-like hot core or lens flare; the pale moon with a real terminator while it comes on; through entry its
  leading face is tinted molten (craters still visible), the seams blaze, a shell of fire sits on its leading edge
  (rim-weighted); one bow-shock crescent hugging its edge (two read as ghost copies), a sheath round its outline,
  `FLAMES` tongues of flame off its edge, a longer wake; a pale dust trail with glinting chips from TRAIL until entry.
- **Hand-over**: at rest the sphere (a block bigger) crumbles away patch by patch (`MoonSphere.crumble`, translucent)
  over 50 ticks, glowing as it cools, with chips, dust and steam (`MoonFx.crumbleParticles`).
- **Music**: re-cut from the original (drop at 24.45 s) 8.45 s in, not stretched; starts at ALARM (`MoonFx.MUSIC_AT`),
  drop on CONTACT 16 s later; `SoundSource.MUSIC`. Recipe in `docs/SS-06-LUNA.md`.
- **The impact's lag**: `Carving.paint` relights and resends each chunk it touches. `MoonLand` heaved the rim column by
  column with jittered steps, so most rim chunks were rewritten every tick for seconds (and the cracks every tick while
  they ran). Now each chunk's rim rises in `JOLTS` (3) jolts, all its columns together; cracks at most every
  `PAINT_EVERY` (6) ticks; at most `MAX_PAINTS` (12) chunks a tick; `MoonBuild` writes each chunk once (no surface-first
  pass), 15 ms a tick at most; the crater is resent once. Measured on a default strike: 673 writes for 589 chunks,
  worst tick 44 ms (TSS's own crater carving: 38-55 ms). TSS 1.4.x also has a build-ahead carving path (sections built on
  a worker thread, `Carving.b(double)` / a batch floor callback `a$a.floors`, used by Gungnir) - obfuscated, new
  members only, not used by us.
- **Local client test**: use `-Dss05.clienttest.view=aerial` with `film=true`, as CI does; without a view the film
  doesn't show.

## 14. Next updates (agreed 2026-10-22/23; the next major update's list decided 2026-11-03)

**The next major update** (the user, 2026-11-03), on `main` first (§0), then ported:
- done on main, to test: TSS 1.5.1 + RimuLib 1.2.1 (§16); Luna holds its land from the cast and builds its moon
  through RimuLib's section sink (§17); no cooldown for TSS's own skills (below); only the newest overlay of each
  kind; the **evac switch** (E on the plate: lifted straight up, or to the crater's edge when that land is loaded,
  else up; `Evac`, sent in `ChoicePayload`, network version 2; client config `evac_lift`, default up); the plate's
  note that Luna's music uses the Music volume;
- done on main 2026-11-04, to test (the user picked both): (a) **Luna only where land is loaded** (§17 item 3: the
  moon's builder cuts the crater round the moon too, per chunk; nothing is loaded or generated for a strike); (b) the
  rim and cracks' 846 ms tick at 10x: `MoonLand.end()` finished every loaded chunk at once; now it queues them and
  `lingerTick` works 15 ms a tick (loaded and ready chunks), and a chunk waits for `MoonBuild.ready`. MoonLand itself
  still paints per chunk (not through the sink): port it only if it still shows up in the timings;
- done on main 2026-11-04, game-tested: the **operator command** and the **protected-blocks tag** (below); still to do:
  **Epic mode** (needs `Video-Project-48.mp3` from Hamza's PC for its music), and saying both in the README, the
  changelog and the CurseForge description at release (with the command's no-limits warning);
- **SA-04 Proxima** (§15);
- **Stellar Convergence** (§18);
- done on main 2026-11-04, to test: the **Gravitia keybind** (`compat/tss/mixin/SkillKeysMixin`: RimuLib's
  `SkillKeys.register` skips any skill whose `selectWarning()` isn't null, which is why Gravitia, "it can only be
  chosen from the menu", had none; at its TAIL we register a KeyMapping for each such skill, **unbound**, under the
  set's key name (TSS's lang already has `key.shooting_star_demo.stellar_remote.gravitia`) and category, and put it in
  `KEYS`/`SKILLS`, so the menu shows it and can rebind it. A skill key only *selects* the skill
  (`ClientSkills.select`), it never fires it, and the menu's warning page is skipped for a key press); the
  **keep-the-moon switch** (L on the plate, client config `keep_moon`, default off: `MoonFx.kept()` keeps the sphere
  at full visibility after SETTLE (no crumble or crumble particles) and `finished()` false past DURATION;
  `MoonFxAdapter.duration()` returns `Integer.MAX_VALUE` while on (TSS reads it every tick); `realTime()` stops at
  DURATION so every other effect stays faded; switching off removes kept moons at once);
- done on main 2026-11-04, to test: **less shake after Halley** (`HalleyFx` detonation `screen.shake` window: full
  until 0.8 s, gone by 3.5 s; was 2.0 / 5.0; the TSS trauma kick at the impact unchanged) and **impact frames FULL /
  SOFT (0.35) / OFF** (client config `impact_frames`, F on the plate cycles it; scales `impactSequence` in `HalleyFx`
  and `MoonFx`; only our skills' frames, not TSS's). The plate now has five rows: M, L, F, E, C. The client config's
  `get`/`set` are generic and fall back to each setting's own default before the config loads;
- done on main 2026-11-04 (the user's pick), to test: **title cards** (§10, a CurseForge request), the skill's name
  across its film at its reveal, like TSS 1.5's own (TSS draws its titles as 3D stroke letters with its own shader,
  `client/fx/SkillTitle`, ~1.5 s, each skill with its own entrance; we can't use its shader or lettering, §9).
  `compat/tss/client/FilmTitle`, drawn after our film HUDs (`StarClientBridge.renderFilmHud`) in RimuLib's public
  display type (`TessType.draw`, Face.DISPLAY = Poppins SemiBold; `veil`, `rule`), letter by letter, anchored to the
  projected subject: **HALLEY** in shot 4 (ticks 74-114, under the comet), letters streaking in from high right like
  small comets, a flash as each locks, then sublimating upward; **LUNA** in shot 2 (BREAK + 4 to FALL + 12, over the
  moon), letters cracking in from the middle outward, molten and shuddering, cooling to the moon's grey, then the outer
  ones first dropping away and reheating as the moon tears loose. The code name (`SA-01`, `SA-02`, the user's idea)
  sits before the word on its baseline at 0.42 its size (`FilmTitle.Line`), flickering on (Halley) or glowing in and
  breaking away first (Luna); a small label above (`1P/HALLEY · PERIODIC COMET`, `THE MOONFALL`) and a growing rule
  under. Off in RimuLib's legacy film style (`CutsceneDirector.legacy()`),
  as TSS's are. No sound yet (the sound overhaul can add one). Game-tested (`test builds\2026-11-04 auto test 8
  titles, film overlay\`); afterwards HALLEY moved further under the comet (its small label touched the comet's range
  readout) and its streaks made stronger, not re-tested. Test jar `test builds\2026-11-04 title cards, film overlay\`,
  in the Modrinth profile (the commands build there renamed `.disabled`).
  **Found on the way: our film overlays (UPLINK, Luna's letterbox, phase captions, descent bar, the target boxes)
  never drew in TSS 1.5.x films**: FxManager's two overlay passes have the same shape and the mapping names them the
  wrong way round: by their bodies (javap of 1.5.1's `llli1l1ll` against readable 1.3.4) `i1Ii11l` ("renderFilmHud")
  calls each effect's `hud` and the big `liiillli1l` ("renderHud") draws the skills' film overlays. `FxManagerMixin`
  now calls `StarClientBridge.renderFilmHud` from the TAIL of both, and that draws once per frame (same
  `GuiGraphics` = same frame), which holds whichever way a loader build names them. Port this to every branch. The
  plate's Music-volume note is done;
- **sounds: overhaul the existing ones** (the user: "dont replace ai sounds lets just overhaul them"; rework,
  layer and process what we have rather than swapping in licensed packs). Scope (the user, 2026-11-04, all four):
  punchier impacts (layered bass, a crack on top, a rolling tail), better mixing (against each other and TSS's),
  less repetition (pitch/variation for debris and rumbles), and Luna's build-up (a rising rumble before the fall, a
  near-silence just before impact; see §10 "Luna's build-up").
  **Done on main 2026-11-04, to test (by ear: a screenshot test can't)**: the sounds were synthesised by
  `art/make_sounds.py` (Python, not installed on Adam's PC); the originals moved to `art/raw/` and
  `art/overhaul_sounds.sh` (ffmpeg 9 from winget is on Adam's PC) makes the shipped ones: every sound de-harshed (3.6 kHz
  and the top end down, below 25 Hz cut); the hits (`halley_impact`, `luna_impact`, `halley_touchdown`, `halley_boom`,
  `luna_entry`, `luna_tear`, `luna_shockwave`) get a falling sub-bass thump, a noise crack and an echoing low tail
  (1.2-3.5 s longer), compressed; levels by role against TSS's (measured, never copied: its hits ~-11 LUFS, hums ~-17,
  cues ~-18): our impacts went from -14/-16 to -12.5/-13 LUFS (the limiter holds them ~2 dB under the -11 aimed at),
  cues -15/-16, beds -16/-18, hums -19; variants (sounds.json picks at random): `halley_hum` x3 (plus a random pitch in
  `HalleyFx`), `halley_burst` x3, `luna_debris` x3, `luna_aftershock` x2. Luna's build-up: `luna_buildup` (new, 2 s,
  played at ALARM from the moon's place in the sky: a brown-noise roar rising and brightening, a shuddering 30-40 Hz
  drone, the crack's grinding pitched down) ends on FALL; `luna_rumble` (CONTACT - 60) now swells and then drops to
  near-silence for its last half second; `luna_inhale` (new, 0.65 s, at CONTACT - 13, as late as the impact for far
  listeners) is the shock wave's start reversed with a rising rush, ending on the impact. Luna's music is not ducked
  (its drop is on CONTACT; the user's track). New subtitles in `en_us.json`; new events `MOON_BUILDUP`/`MOON_INHALE`.
  Possible next steps if the user wants more: a hush and inhale before Halley's impact too, a sound for the title cards.
  Game run (`test builds\2026-11-04 auto test 9 code names, sounds\`): no missing-sound warnings of ours, no errors;
  the title cards with their code names screenshotted (a run whose game window was minimised saves 1x1 shots: re-run).
  Test jar `test builds\2026-11-04 code names, sound overhaul\`, in the Modrinth profile (the title-cards build there
  renamed `.disabled`).

**No cooldown for TSS's own skills** (fixed 2026-11-03, main): TSS 1.5.1's cast of its own skills (`il1i1li1l11
.liiillli1l(ServerPlayer, liiliiii1, Tuning)`, unmapped) refuses one while the same player's last of that skill runs
("X is already on its way"): it asks `l111i1lil1.liiillli1l(liiliiii1, UUID)` (the old `SpellEngine`'s "running spell
of this skill by this caster"). `StarCastMixin` sets `NoCooldown.casting` around that cast for players no cooldown
applies to; `SpellsMixin` makes the lookup return null meanwhile. Clearing the cooldown map alone (`CastingMixin`)
never covered that check. Both names change with every TSS update (§16).

Order before that (2026-10-22): **(1)** NeoForge music check (§9) → **(2)** operator commands, Epic mode,
protected-blocks tag → **(3)** Proxima (§15). **One addon** for everything (the user decided; per-skill on/off
switches in the config instead of a second addon, which would double the mapping/branch/CI/CurseForge work every TSS
update).

- **Operator strike commands** (the user definitely wants these next): operators only (permission level 2), trigger a
  skill without the remote, e.g. `/ssa strike <halley|luna> <pos> [power] [speed] [damage]` (add `proxima` when it
  exists). Reuse the cast path (`HalleyCasting`/`MoonCasting`) with a `CastTuning`; decide what "caster" means for
  the film (the command's source player if any, else no film, or the nearest player). Commands ignore the server
  slider limits (§6): operators set them (user, 2026-11-03). Needed for the user's server finale (below).
  **The commands cover TSS's own skills too** (user, 2026-11-03): any remote skill, cast through RimuLib's
  `SkillSet.of(skill).cast(player, skill, tuning)` (every mod's skill set has a caster), which also gives other
  addons' skills for free. Check how a TSS skill can be aimed at a given position (its casts aim at the player's
  crosshair). **Warn about the missing limits**: the command's usage/help text, a warning line in chat each time it
  runs with values beyond the remote's normal range ("no limits: huge values can freeze or crash the server"), the
  README and the changelog.
- **Epic mode** (user, 2026-10-19): the plate's **M** switch becomes "Epic mode": Luna's music **and** a slower, more
  ominous fall. Decided: **the slow fall applies to everyone watching** (server-side timing); the music stays each
  player's own choice. So the caster's switch must travel with the cast (like the no-cooldown choice via
  `ChoicePayload`) and pick a slower `MoonPlan` timeline; the music needs its own cut for it (1.5.1's is cut, not
  stretched, to the 16 s from ALARM to CONTACT, §13; recipe in `docs/SS-06-LUNA.md`). The normal fall keeps
  1.5.1's (CONTACT 330, with Halley's impact, which the user asked for in §13; confirmed 2026-11-03).
  **BUILT 2026-11-05 (the user: "Epic mode")**: since M became "Music" for Luna and Proxima, Epic mode is its own
  switch, **G** on the plate (client config `epic_mode`, off; G is free on main, but on 26.3 Halley's key is G: pick
  another letter there when porting). The fall from ALARM to CONTACT takes 24 s instead of 16 (`MoonPlan.EPIC_FALL`
  480, a time warp, not a second timeline), with `luna_music_epic.ogg` from the rebuilt full track. How it works:
  docs/SS-06-LUNA.md "Epic mode". The plate has six rows now: M, G, L, F, E, C.
  Game-tested (`test builds\2026-11-05 auto test 21 proxima music\epic\` and `plate\`): the impact 24.6 s after the
  cast (server log; 16.5 s normally), the moon still high at tick 330 and the impact frames at 495 (screenshots), the
  plate's G row. Not heard yet (the epic music, the rumble and inhale timing); the film in Epic mode not screenshotted.
  Test jar `test builds\2026-11-05 Luna Epic mode\`, in the Modrinth profile (the config/sounds build renamed
  `.disabled`).
- **Protected-blocks tag** (§10 #6): `shooting_star_addition:unbreakable`; matters even more for Proxima's wide area.
  **Done 2026-11-04** (`world/Protected`, `data/shooting_star_addition/tags/block/unbreakable.json`: bedrock, barrier,
  command/structure blocks, jigsaw, end portal and frame, reinforced deepslate; servers add more by data pack). Its
  blocks are registered as RimuLib carving cores (`Carving.keepCores` in `TssLink.install`) and our carvings
  `keepsCores()`, so RimuLib keeps them block by block (one of TSS's own skills, `il1li1111` in 1.5.1, keeps cores
  too, so it spares them as well; TSS's other skills don't); `TssLink.paint` drops writes onto them (palette check
  per chunk, `Protected.scan`); `MoonBuild` puts them back after cutting and building a chunk (and never starts a
  section empty there). A protected block whose column is cut can be left floating, as RimuLib's cores are.
- **The operator command, as built (2026-11-04)**: `compat/tss/StrikeCommand` (registered through
  `StarBridge.registerCommands` from NeoForge's `RegisterCommandsEvent`): `/ssa` (help + the no-limits warning),
  `/ssa strike <skill> [at <x y z>] [power] [damage|inf] [speed]`, permission 2, run by a player (console: `/execute
  as <player> run ssa ...`). `<skill>` is any non-hidden skill's id (suggested: `the_shooting_star`, `gungnir`,
  `seven_stars`, `tesseract`, `gravitia`, `halley`, `luna`, other addons'); unset values are `Tuning.initial`. It casts
  through `SkillSet.of(skill).cast` (TSS's cast router, so no cooldown, no server slider limits), with
  `NoCooldown.forced` on (no "already on its way": `NoCooldown.casting()`); `at` is answered by the aim hook in
  `SpellsMixin` on `l111i1lil1.liiillli1l(ServerPlayer, double, double)` (every skill's ground aim, ours through
  `TssLink.aimGround`) via `StrikeAim`, set only while the cast is made. A red warning line when power or speed is
  past `Tuning.power/speed(skill)`. The film plays for the player who ran it. Luna still refuses where there's no
  moon (and by day if the server's `night_only` is on).
  **Tested 2026-11-04** (`test builds\2026-11-04 auto test 3\` and `4\`): `/ssa` prints the help and the warning;
  `/ssa strike luna 1 inf 5` warns (speed past 3) and casts (Luna's id is `luna`: `moon` answers "No remote skill
  called moon"); the Gravitia key is registered (`key.shooting_star_demo.stellar_remote.gravitia`, the remote's
  category, `key.keyboard.unknown`). **The Railgun with `at` aims right** (`test builds\2026-11-04 auto test 5
  railgun debug\` and `6 railgun aim\`): with temporary debug mixins, one aim call (answered `at`), one spell
  (`THE_SHOOTING_STAR` from the caster to (0.5, 41, 300.5)) and one RimuLib column carving at (0.5, 41, 300.5),
  radius 50; the land there cut to the bottom (-105), the land where the player looked untouched. It **looked** as if
  it struck twice only because the harness probed (a) unloaded chunks, which read as the world's bottom, and (b)
  heightmaps, which RimuLib leaves as they were where it cuts to the bottom (`primeHeightmaps` skips a chunk whose
  floors are all below the world, `unread`); the probes now read blocks and say "unloaded". The Railgun fires ~18 s
  after the cast. Halley (`at 160 ~ 30` landed at 160.5, 41, 30.5) and Luna aim right too. Not done: a caster other than the player running it, and the harness filming
  non-addon skills (`skill=other` times them from the cast, but its camera stays on the default mark).

### The user's server (personal, not a mod feature as such)

The user runs a modded server (Montauk County partnership) where this addon will be used; its finale is a big event
(to be recorded for a long-form video). Plans and what the mod could do for them:

- **Solar Residue economy**: at the end of the server the user fires flares; players collect Solar Residue and sell it
  to the user for currency in a Discord-bot economy (also limited roles/badges). Remind the user of this when Proxima
  is built. Mod-side ideas (none decided):
  - a "flare storm" operator command, e.g. `/ssa flarestorm <radius> <count>`, scattering several small Proxima flares
    (since 2026-11-05 `/ssa flare <minor|major|cataclysm> [seconds]` calls one announced flare near the operator;
    several of those in a row already make a small storm);
  - residue stamped with an event tag (data component/NBT) so creative-spawned copies don't count;
  - getting it to the bot: simplest is in-game hand-over and manual payout; smoother is a sell command that removes
    the residue and writes `player, amount` to a log file the bot reads (or a webhook). Decide once the bot is known.
- **The finale date**: "later / don't know" (the user, 2026-11-04): no rush on the strike commands.
- Other event ideas from the user's notes (not discussed in detail): an operator-only block that spawns through a
  portal animation and opens like a case to give the Stellar Remote (ties into §10 #3); keep one skill unused in the
  finale and point players to download the addon to try it; mention in the community that updates may be slower.

## 15. SA-04 · Proxima (design agreed 2026-10-22/23; stage 1 built 2026-11-05, see docs/SS-07-PROXIMA.md)

A red-dwarf flare star inside a Dyson sphere fires a solar flare and then a coronal mass ejection (CME) at the
crosshair. Name: **Proxima** (Proxima Centauri, a real flare star; the user loves it), skill id `proxima`, SA-04 (SA-03 until 2026-11-05; SA-03 is kept free).
Sources: the user's notes, a Gemini chat, another AI's design brief (`solar_flare_chat.txt`) and a CurseForge thread
(conaialator: "an artificial red dwarf with a Dyson sphere ... or more likely a CME, which brings the plasma"; the
user: "a large radius but small block damage depth-wise"). The user approved all of the ideas below; numbers are
starting points to tune. Write this up as `docs/SS-07-PROXIMA.md` (like `docs/SS-06-LUNA.md`) when work starts.

**Look**: deep crimson, magenta, white-violet at the hottest points (distinct from Halley's ice and Luna's grey/orange).

**Intro** (styled like TSS 1.5.0's intro): the camera leaves the world for space (a camera sequence, not a teleport;
skippable like the other films). Title card **"DYSON SPHERE"**: a dark metal lattice round a red star, panels opening
like an iris. Two plasma loops twist into an **infinity sign** (the user's idea; real physics: twisted "sigmoid"
regions erupt when the loops reconnect), flare white at the crossing, collapse to a point. Title card **"CORONAL MASS
EJECTION"** written in the CME's own plasma filaments, half-formed, torn apart as the wave launches.

**How it reaches Earth: the lens beam** (decided 2026-11-03; the intro above stays): the star stays home, 4.2 light
years away. After the infinity-sign flare, the Dyson sphere's panels fold into one giant lens that focuses the flare
into a beam; a gate near Earth catches it and spreads it back out into the wave (phase 2's coronal front comes out of
the gate).

**Look like TSS's films** (user, 2026-11-03: "as similar as the original mod as possible", the Earth and the camera
movement especially): use RimuLib's film tools (`client.cinematic`: `CameraRig`, `Shot`, `Subject`, `CutsceneDirector`),
which TSS's own films use, so the camera feels the same. TSS's space Earth comes from free imagery it credits
(`textures/moonfall`, `shooting_star/CREDITS.txt`): NASA Blue Marble / Black Marble (day, lights, clouds, height), the
LRO Moon, the ESO Milky Way (CC BY 4.0), Solar System Scope planets (CC BY 4.0). Download those from their sources
(credit the CC BY ones), never copy TSS's files or shaders (§9, the developer's terms); write our own Earth shader
and match it by eye.

**Extra content** (the user liked these too; not for now, **mention them** per §0): a gate in the sky above the
target with the star staying home; an artificial red dwarf in its Dyson sphere parked high above Earth (the
commenter's original idea; "Proxima" = now the nearest star); "fired years ago" (a "4.2 YEARS AGO" card at Proxima
Centauri as the flare launches, then "NOW" as it arrives); Proxima itself warping next to Earth. Could be alternate
films, a setting, or other skills.

**Draft `ProximaPlan`** (ticks): ARM/PRESS 0-7 as the others; INTRO ~10-90; FLASH ~90 (phase 1); FRONT ~100-260
(phase 2); HUSH ~260-270 (near-silence, everyone thinks it's over); IMPACT ~270 (phase 3); AFTER ~270-600 (pullback
shot, sky arc breaking up, residue zone). Flash before plasma is real: flare light arrives in minutes, a CME in days.

- **Phase 1 · Flash Burn**: sky goes white-red; leaves ignite/char, plants wither, exposed water steams (shallow pools
  may partly evaporate at high power, never oceans); exposed non-creative players and mobs catch fire and take damage.
- **Phase 2 · Coronal Front**: a giant 3D ring sweeping across the sky (bright thin shock front + a larger translucent
  plasma sheath with filaments). **Visual only**, no crater, no main damage.
- **Phase 3 · Ground Zero**: white point at the mark, cracks of light, pulse + bass hit, crater and radial shockwave,
  dust/embers/heat shimmer. Damage in tiers: centre devastating, middle heavy + burning, outer moderate + stagger.

**Crater: wide and shallow** (user's CurseForge idea; realistic and cheap): outer zone (huge radius) removes/chars
leaves, grass, flowers, snow, crops and scorches 1-2 blocks of topsoil; middle zone turns trunks to charred logs/ash
and ground to coarse dirt/blackstone/basalt; centre a bowl **6 blocks deep** (decided), tapering to the 1-2 block
scorch, glassy floor (tinted glass, obsidian, magma veins). Power scales radius and depth. Reuse Luna's machinery
(streamed carving near the centre, lazy carving + `linger()` further out), honour the `unbreakable` tag (§14), never
load the whole area (§11).

**Shelter**: a player is safe from phases 1/3 surface damage with N solid blocks above them (config; not just
`canSeeSky`, so a glass roof isn't a bunker). Underground players still feel it: muffled boom and rumble with camera
shake, dust from the ceiling, cave mouths glowing red, torches/lanterns flickering, the radio-burst sound.

**Knockback**: players get a small capped push (config) sold by camera shake, flash and bass; mobs get full knockback;
the caster, creative and spectator players are never pushed; optional observer protection in the server config.

**Sliders**: Power = flare class (label it like real classes, logarithmic: e.g. 0.1x = C1, 1x = M5, 3x = X1,
10x = X20/"superflare"), sets radius and crater; Damage and Speed as for the other skills. **Visual wave size** is
cosmetic only and goes on the addon's plate (a key like M/C) or the client config, never in TSS's menu (fragile, §10
#7/#8), and must never change damage or destruction.

**Space details for nerds**: Type II radio burst (the CME shock's radio whine drifts **down** in pitch) as the front's
signature sound; compasses (and recovery compasses) spin during the front; Carrington-style sparks: redstone lamps
flicker and some redstone wire sparks (harmless); auroras (red/green curtains) for a night or two after; "radio
blackout": briefly garble the skill's own chat/HUD text only, never other players' chat.

**Aftermath**: **Solar Residue** block (Proxima's Comet Heart / moon cheese: glows, crackles, minable; the user's
server economy uses it, §14); thin ash layer (snow-layer-like) over the scorched zone; **radiation zone** in the
crater with a visible shimmer and rising hum at its edge, damage over time inside, **fades by default (~10 min at 1x
power), config to make it permanent**; final shot pulls back over the ruined land, tilts up to the plasma arc breaking
apart, ambient sound returns slowly; smoke columns, drifting ash, steam.

**Random solar flares** (server tick, no caster, **off by default**): three tiers - minor (sky flash only), major
(burns exposed plants and players, no crater), cataclysm (full event, only if an admin enables it). Warn first in chat
("Space weather alert: X-class flare incoming"), give time to reach shelter, respect the protected tag, cooldowns and
frequency in config.

**Music**: the user has **full permission to use the OST** they picked (the G-Man / Drillman track, the part where
Drillman shields everyone, remixed; 2026-10-20). They will send the file; cut it so its drop lands on IMPACT, as with
Luna (§6).

**Config** (server unless noted): per-skill enable switch; crater radius/depth caps; scorch radius; shelter depth;
player/mob knockback; residue zone duration/damage/permanent; random flares (on, frequency, max tier); cosmetic-only
mode (no terrain changes). Client: wave size, flash/shake intensity.

## 16. 1.6.0: The Shooting Star 1.5.0 and RimuLib (2026-10-30/31)

TSS 1.5.0 (CurseForge 2026-10-30; NeoForge 9377531, Forge 9377538, Fabric 9377519) needs a new library mod,
**RimuLib** (project 1753907, mod id `rimulib`, 1.1.5: NeoForge 9377390, Forge 9377397, Fabric 9377404; Fabric is a
bundle with nested jars per MC like TSS's). Everything below is true of 1.6.0; §5 describes 1.4.x.

- **What moved**: the skill API (`skill.Skill/Power/Tuning/SkillSet`), the remote's menu (`client.magic.WhiteSpace`,
  `SkillKeys`), films (`client.cinematic.*`), `client.render.ScreenFx`, `client.text.Overlay`, `erase.Erasure` (now with
  an `ErasePolicy`), `terrain.Carving` and networking (`net.RimuNet/RimuPacket`) are RimuLib's, **readable**, package
  `cyou.rimuru.rimulib.mc1211.` (`mc1201.` on Forge 1.20.1, none on Fabric). Use RimuLib wherever it has the thing.
- **What's left in TSS** is obfuscated harder: classes flattened into the version package (`mc1211/i1l1...`), members
  overloaded on one name (`liiillli1l`), generic signatures stripped, and its calls hidden behind invokedynamic
  bootstraps that look members up by obfuscated name. Consequences:
  - **The readable copy of TSS can't run** (its bootstraps can't find renamed members): `runClient`/`runClientTest` and
    forge-1.20.1's server test fail inside TSS. Test with the production-style runs (`runProdClientTest` on main,
    `prodClientTest` on Fabric), which load the release jar with TSS's real jar. The Forge branches have none, so CI
    only builds them (the pack check verifies every TSS reference).
  - `TssMatch.flat()` lets classes match across the flat package (66/146 classes on main). Unmatched members keep
    their obfuscated names in the readable copy, and `compat/tss` uses a few of them as they are (`Casting.liiillli1l(
    ServerPlayer,int,float,float,float,float,boolean)` = the cast request, the payload record `l1l1i1lii1`,
    `SpellFx.liiillli1l()` = its Subject, static `SpellFx.liiillli1l(ScreenFx,float,float)` = stopped world); names
    may differ per loader build. Check them with javap after every TSS update.
- **TSS's spell engine only takes its own skills** (`ActiveSpell(StarSkill, ...)`, an enum). So `compat/tss/Spell`
  runs Halley and Luna (clock at the speed slider, as ActiveSpell did), ticked by `StarBridge.tick(server)` from the
  loader's server tick (`StarBridge.clear()` on stop). `StrikeFx` (our record, sent with `RimuNet.send`, declared with
  `RimuNet.s2c` in `TssLink.install()`) starts the effects on clients; the client builds TSS's own payload record from
  it and adds our `SpellFx` subclasses to `FxManager`. Fabric's client side is `RimuNetClient.receive`.
- **Joining the remote**: `TssLink.install()` writes Halley/Luna into RimuLib `SkillSet`'s tables by field name
  (`BY_NAMESPACE`, `OWNER`, `INDEX`, `skills`, `caster`), into the set of namespace `shooting_star_demo` that holds
  TSS's skill enum. RimuLib marks them in the menu as another mod's skills (`AddonSkills.unofficial`, a red ⚠).
- **Gone with 1.5.0**: the remote animation for our strikes (`RemoteRendererMixin`, `RemoteRenderer.strikeTime` no
  longer exists), cast titles for bystanders, the evac lift (`liftClear` now sets the caster down outside the crater
  with slow falling). Ward/affects are our own copies of 1.4.x `SpellUtil`.
- **Build**: `tss_min_version=1.5.0` (gradle.properties) - only TSS >= it is packed; the older jars stay in libs/ for
  the mapping chain (1.4.0 ... 1.4.4, then `./gradlew tssMappings -PtssJar=1.5.0`). RimuLib's jar goes in libs/
  (`rimulib-<ver>-<loader>.jar`, one only); `rimulib` is a required dependency in the metadata
  (`rimulib_version_range`). CI downloads it (`RIMULIB_FILE` in `dev/ci/branches/*.sh`).
- **Verified**: main (NeoForge) in a local production run on the real GPU: attaches (Halley #6, Luna #7), Luna's cast,
  film, screen effects, crater, the H menu with the plate.
- **Fabric attach order**: Fabric runs main entrypoints in no fixed order, and TSS registers its skills in its own,
  so attaching in our `onInitialize` failed ("registered no Stellar Remote skills"). We attach at the head of RimuLib's
  client entrypoint (`RimulibClientMixin`, in the branch's own mixin package: `compat/mixin` on 1.21.1/1.20.1,
  `client/mixin` on 1.21.11/26.3; before RimuLib makes the skill keys) and, on dedicated servers, in the `server`
  entrypoint `HalleyServer`.
- **Fabric 26.3**: TSS 1.5.0's own 26.3 build ships its core shaders (`shaders/core/armillary_glow`, `remote_screen`)
  with scrambled text (`#extension ... : _lllll`, renamed includes), so the game dies loading them, on CI and on the
  user's GPU alike; nothing in TSS/RimuLib rewrites them. Our side attaches (Halley #6, Luna #7). Wait for a TSS fix.
- Per-branch name differences in 1.5.0's readable copy (javap them after every TSS update): the payload record is
  `l1l1i1lii1` on NeoForge, readable `net.SpellFxPayload` on Forge/Fabric; FxManager's film-HUD hook maps to
  `renderFilmHud` on NeoForge/Forge 1.21.1, an obfuscated name on Forge 1.20.1/Fabric 1.20.1/26.3, `renderHud` on
  Fabric 1.21.1 (and on NeoForge 1.5.x the two names are swapped: our film overlays didn't draw in films until
  2026-11-04, §14 title cards; the mixin now hooks both passes). forge-1.20.1's `TssRemap` renames clashing
  field names (its SRG tool crashed on them); Fabric's `TssMatch` copies compare `flat()` against the prefix.
- Changelog: `dev/publish/changelog_160.py`. Players on TSS 1.4.4 or older keep 1.5.1.
- **TSS 1.5.1** (CurseForge 2026-11-01; NeoForge file 9400029) needs RimuLib >= 1.1.6; RimuLib **1.2.1** came out the
  same day (NeoForge 9399927, Forge 9399933, Fabric 9399905). On `main` (2026-11-03, built, not tested in game): the
  1.5.1 mapping is made from 1.5.0's (`tssMappings -PtssJar=1.5.1`, 119/169 classes), and two classes `compat/tss`
  uses unmapped got new obfuscated names: the payload record `l1l1i1lii1` is now `ll1lil1iill` (same 15 fields; 1.5.1
  has an unrelated class called `l1l1i1lii1`), and `SpellEngine` didn't match: `aimGround` is
  `l111i1lil1.liiillli1l(ServerPlayer, double, double)`. Because those names differ, one jar can't pack 1.5.0 and
  1.5.1: `tss_min_version=1.5.1`, `rimulib_version_range=[1.2.1,2.0)`; players on TSS 1.5.0 keep 1.6.0. libs/ holds
  RimuLib 1.2.1 (1.1.5 moved to the project folder's `old versions\rimulib\`). `dev/ci/branches/main.sh` has 1.5.1 and
  RimuLib 1.2.1; the other branches' scripts don't yet. 1.5.1 also flattens its control flow (`switch` on a static
  int), so decompiled bodies are harder to read. The other six branches need the same per-loader check (their raw
  names differ per build, see above).

## 16a. TSS 1.5.2 and RimuLib 1.2.2 (public 2026-11-04; main switched 2026-11-05)

- **Files**: TSS 1.5.2 NeoForge 9438871, Forge 9438881, Fabric 9438887; RimuLib 1.2.2 NeoForge 9438830, Forge
  9438825, Fabric 9438845 (found with `https://api.cfwidget.com/minecraft/mc-mods/<slug>`, which lists file ids
  without a key). TSS 1.5.2 requires RimuLib `[1.2.2,2.0)`. It is a normal jar (the early-access 1.5.2 was a locked
  launcher). main: `tss_min_version=1.5.2`, `rimulib_version_range=[1.2.2,2.0)`, `dev/ci/branches/main.sh` has both;
  libs/ holds RimuLib 1.2.2 (1.2.1 moved to the project folder's `old versions\rimulib\`).
- **The mapping tool had been pairing classes wrongly since 1.5.0**: `TssMatch.isObfName` only knew the old 1-2
  letter names, so TSS 1.5's long ones (`i1i1iii11`) counted as readable and any class whose obfuscated name came back
  in the next version was anchored as "the same class" (TSS reuses names for unrelated classes). That glued 1.5.2's
  `SpellFx` to 1.5.1's film-subject class and left `FxManager`/`ActiveSpell` unmatched, and it is why 1.5.1 needed raw
  names (`ll1lil1iill`, `l111i1lil1`, `il1i1li1l11`, `liiliiii1`). Now `[liI1]{5,}` names are obfuscated too: 1.5.1
  matches 141/169 classes (was 119), 1.5.2 161/180 (was 125), report 32 lines (was 1052). Also: the rule that keeps an
  old obfuscated name off a method that another method in the class (or a supertype) keeps now knows 1.5-style names
  and identity-matched methods (1.5.2: `lliii1lil.liiiIll(F)F` onto `liiillli1l(F)F`, an "unfixable conflict" in the
  remapper). The old mappings are backed up in the project folder's `old versions\mappings-before-2026-11-05\`; the chain
  1.5.0 -> 1.5.1 -> 1.5.2 was remade in order.
- **What compat/tss names now (1.5.2, readable again)**: `star/StarEngine.cast(ServerPlayer, StarSkill, Tuning)Z`
  (`StarCastMixin`), `spell/SpellEngine.aimGround(ServerPlayer, double, double)` (`TssLink.aimGround`, `SpellsMixin`)
  and its running-spell lookup, still unmapped: `SpellEngine.liiillli1l(StarSkill, UUID)ActiveSpell` (`SpellsMixin`);
  `Casting.liiillli1l(ServerPlayer, int, float x4, boolean)` (the cast request, `CastingMixin`), `FxManager`,
  `SpellFx`, `Frame`. Still raw: the effect packet record `ll1i111l11` (same 15 fields; 1.5.0 `l1l1i1lii1`, 1.5.1
  `ll1lil1iill`). New in 1.5.2: a readable `magic/CastBridge` (static `install()`; not used), and TSS's casting state
  goes through RimuLib (`CastState$Source`), FxManager owns RimuLib's `PostFxRenderer`.
- Game test: `test builds\2026-11-05 auto test 13 TSS 1.5.2\`.

## 17a. RimuLib's public API (announced 2026-11-04; partly used since 2026-11-05)

**Switched on main 2026-11-05**: `TssLink.install` joins the remote with `SkillApi.whenRegistered` + `SkillApi.extend`
(no more writing into SkillSet's private tables) after `RimulibApi.connect(MOD_ID, HalleyAddon.class, "1.2.2")`;
packets through `NetApi`/`NetClientApi` (`NetApi.packet`, `toClient`, `toServer`, `receive`, `send`, `canSend`; the
client handlers hop to the game thread); the plates and their keys through `UiApi.onMenuDraw` + `MenuApi.onKey`
(`WhiteSpaceMixin` deleted; `menuUses` reads `MenuApi.binding/menuKey/filmKey/key`); overlay units through
`UiApi.overlay/width/height`. Still RimuLib internals (no public equivalent): `Carving` (our carvings tick themselves and
Luna's outer crater needs `linger()`; `CarveApi.Cut` has neither), `SectionSink`, `relightAll`, `Bulwark.spares`,
`Erasure`, `CutsceneDirector` (+ `CutsceneDirectorMixin`), `SkillKeys` (+ `SkillKeysMixin`), `SkillTuning`,
`ClientSkills`, `TessType`, `ScreenFx`, film types.

RimuLib **1.2.2 is public on CurseForge** and is the first with a **public API**: only `cyou.rimuru.rimulib.api` and
`cyou.rimuru.rimulib.client.api` (plus the types they use) are supported; "everything else is internal and can change in
any release". Docs: https://docs.rimuru.cyou/rimulib/ (pages: getting-started, carving, ui, films, fx, skills,
erase-wards, networking, reference, versions). Classes: `RimulibApi` (connect + version/loader checks:
`RimulibApi.connect(modId, Main.class, "1.2.2")`), `CarveApi` (column/flatten/slit/wedge/line/lens, Floor/Surface,
`lazy()`, `streaming(...)`, `keepBlocks`, `paint`; no section sink, no Fresh), `SkillApi` (`register`,
`whenRegistered(modId, set -> extend(set, skills, handler))` = **the official way to add skills to TSS's remote**,
`gate(set, ...)` sees every cast first, `deny`, `clamp`, `limit(s)`, `tuning`, `setOf`/`byIndex`/`sets`, `forceKill`,
`CastState` cooldowns), `EraseApi`, `WardApi`, `AimApi`, `NetApi`/`NetClientApi`, `UiApi` (fixed-size text,
`onMenuDraw`), `FilmApi`, `FxApi`, `MenuApi` (`source`, `onKey`, `capturesInput`, `binding`, `key`). Loaders: Fabric
1.20.1/1.21.1/1.21.11/26.3, Forge 1.20.1/1.21.1, NeoForge 1.20.1/1.21.1 (26.3's `UiApi` takes `GuiGraphicsExtractor`;
on Forge/NeoForge the package carries a version tag).

**What it would replace** (the user asked 2026-11-04; the recommendation: switch as its own step after Stellar
Convergence is approved and before porting to the six branches, so the cleaner code is ported once; needs
`rimulib_version_range` from 1.2.2): `TssLink.install`'s reflection into SkillSet's private tables -> `SkillApi.extend`;
`CastingMixin`'s slider limits -> maybe `SkillApi.gate`; `WhiteSpaceMixin` (plates, keys) -> `UiApi.onMenuDraw` +
`MenuApi.onKey`; `Carving`/`Bulwark`/`RimuNet`/`CutsceneDirector`/`ScreenFx` -> their API classes. **Not** replaced:
everything inside TSS (cast request, running-spell lookup, its aim for `/ssa strike at`, FxManager's passes, the
payload record) still needs the mappings; `SectionSink` (MoonBuild) isn't public. No speed change from the API itself.

## 17. How RimuLib 1.2.x makes big carvings fast (read 2026-11-03)

Read from RimuLib 1.2.2's Fabric 1.21.11 jar (early access, decompiled in the scratchpad). **The public RimuLib 1.2.1**
(NeoForge, checked with javap 2026-11-03) already has the parallel cutting (`cutTogether`, `inParallel`,
`TICK_BUDGET_NS`, `HELPED_BATCH`), `relightAll`, `primeHeightmaps`, `buildAhead`, `Bulwark` and `SectionSink`
(`take(level, deadline)` only); only `Fresh` (and `SectionSink`'s Fresh-aware `take`) is 1.2.2's. `Fresh` hooks
`ChunkStatusTasks` (1.20.5+), so 1.20.1 builds may lack it. The
idea behind all of it: **the server thread only copies and swaps; the rest runs on helper threads or never happens.**

- **One shared budget**: `Carving.tick(front)` cuts until 30 ms of the server tick are used (`TICK_BUDGET_NS`), shared
  by every carving that tick (static `budgetTick`/`budgetUsedNs`), so ours and TSS's together stay under 30 ms.
  `perTick(n)` now only applies when something listens to block changes (`Carving.watch`) or on a client level;
  otherwise a batch is up to 128 chunks (`HELPED_BATCH`), so our `chunks_per_tick` (§6) stops mattering for floors.
- **Parallel cutting** (`cutTogether`/`inParallel`): the batch is split over 2-8 helper threads (`BUILDERS` = cores - 2)
  plus the server thread, each writing straight into its own chunks' live sections (`setBlockState(..., false)`: no
  updates, no neighbours). Each thread stops when its measured time per chunk would pass the deadline; leftovers
  carry to the next tick. Sections wholly above the floor are replaced by a new empty section, not cleared block by
  block. Floors (`Carving.Floor`) and surfaces (`Surface`) of `flatten` are still computed on the server thread, so
  ours need not be thread-safe for this.
- **Batched aftermath** (server thread): light marks only for sections whose emptiness changed, one light job per
  chunk, block entities above the floor removed; `sendAll` builds the chunk packets on helpers (chunks without block
  entities) and sends each chunk once. `relightAll(level, chunks)` does the same for any chunk list, and since 1.2.2
  `Carving.paint` uses it, so `MoonLand`/`MoonBuild` get batched relight/send for free.
- **Heightmaps from the palette** (`primeHeightmaps`): one pass per column over the section palettes, skipping empty
  sections and starting at the known floor, filling every heightmap type at once (vanilla rescans each type).
- **Build-ahead** (`buildAhead(front)`, since 1.1.4, §9): chunks the wave reaches soon are built from copied palettes on
  the `BUILD` pool and swapped in when it arrives; a `ChunkEdits` stamp (bumped by `ChunkEdits.touched`) discards a copy
  whose chunk changed meanwhile.
- **Lazy carvings** hold tickets for up to 64 waiting chunks at a time (`Tickets.hold`) and re-sweep a waiting list of
  over 4096 chunks only every 20 ticks.
- **`SectionSink`** (bulk edits for shapes that aren't a floor: our moon, rim, cracks): `begin(level, ranges, x0, z0,
  cell, clearLo, clearHi)` lists the chunks and their y ranges; `take(level, deadline[, held])` (server thread,
  time-boxed) copies those sections and watches their stamps; `prepare()` (off-thread) makes working copies, sections
  wholly inside the cell and the clear band starting empty; `get`/`put`/`clear` (off-thread) build the shape;
  `stale(level)` says whether a chunk changed meanwhile; `apply(level)` (server thread) swaps the built sections in,
  drops dead block entities, marks light and returns the chunk keys for `relightAll`. `SectionCodec` reads/builds a
  section straight from palette ids.
- **`Fresh`** (carve land before it exists): `Fresh.hold(level, chunkKey)` *before* the chunk generates (null if
  already held). RimuLib's `ChunkStatusTasksMixin` parks that chunk just before its light step (`hold.held()`,
  `hold.chunk()` = the proto-chunk). Pass `key -> hold` to `SectionSink.take`; `apply` writes the parked chunk and
  releases it (`settle`: dead block entities, heightmaps, light marks), then generation lights it once. Generated,
  carved, lit and sent once: no carve-relight-resend. Only for chunks not generated yet (an existing chunk never
  arrives). Release every hold (`dropFresh` or `release(level, false)`), or that chunk never finishes generating
  (`Fresh.releaseAll` runs on server stop).
- **`Bulwark`** (not speed): a shield API for other mods. Every `Carving` warns raised `Ward`s when it is made
  (`Bulwark.cut`), and carvings and `paint` skip the columns a ward spares (`Bulwark.spares`); the eraser spares
  sheltered entities. So our strikes already respect other mods' RimuLib shields.

**What it means for us** (on `main` first, §0):
1. RimuLib 1.2.1 (public) alone gives the crater floors (`TssLink.excavate`, `Carving.flatten`) parallel cutting,
   the shared budget and batched sends, and `paint` batched relight/send. No code change; main requires 1.2.1 now.
2. Hold the land ahead from the cast, as Gungnir does: tried 2026-11-03 (a 12-, then 32-chunk region ticket from the
   cast) and **dropped 2026-11-04** for item 3's "only where land is loaded": at 10x the ticket made the server
   generate ~4000 new chunks (a minute), and the evac's teleport into that queue froze the tick for 40-58 s. Halley
   still holds its crater and trench from the cast, capped at 12 (`HalleyStrike.holdChunks`). With RimuLib 1.2.2
   public, `Fresh.hold` could carve chunks as they generate, if loading land ever comes back.
3. **`MoonBuild` owns the crater round the moon and the moon, only where land is loaded (2026-11-04, main, not tested
   in game)**. Made at CONTACT for every chunk touching the crater round the moon (`coreRadius() + 2` from the mark,
   floor `MoonStrike.crater(x, z, true)`, top blocks `craterSurface`) or the moon's footprint; `moon()` at SETTLE.
   Each tick (15 ms) it swaps in finished copies oldest first (a stale copy is dropped and made again; a failed one is
   logged and the chunk left), then starts loaded chunks with work left, nearest the mark first, up to 32 in flight:
   on the server thread the floors and new top blocks (they read the land; the caster's 5x5 footing and RimuLib wards
   spared), then `StarBridge.sink(level, pos, fromY, toY, clearFrom)` (whole sections above the highest floor start
   empty when all 256 columns are cut); on the "Luna moon builder" pool (cores - 2, 1 to 4 threads) each column
   cleared above its floor, its top block set, and the moon put in from SETTLE (cut and moon in one job when a chunk
   loads late). Chunks swapped in a tick are relit and sent together (`StarBridge.relight`). Unloaded chunks wait; after
   the spell `end()` joins `MoonBuild.tickLingering` (from `MoonStrike.tickLingering`) and finishes them as they
   load. Doing the cut and the moon in the same chunks keeps them in order (a cut after the moon would take its bulge
   over the floor). `ready(pos)` tells MoonLand a chunk's crater is cut. `worldDone()` = settled and every loaded chunk
   done. RimuLib's core carving, the preload ticket and Luna's chunk loader are gone; the outer crater (coreRadius to
   rimInner) is still RimuLib's lazy carving + `linger()`. After swapping, saved block entities whose block is gone are
   dropped (`compat/mixin/ChunkAccessMixin`), and `TssLink.paint` does the same first. Log line: "Luna: the crater round
   the moon and the moon are done where land is loaded: N chunks in N s (worst tick N ms); N wait for their land to
   load". Earlier on 2026-11-03: the moon alone through the sink (6.5 s for 2940 chunks at 10x).
4. `buildAhead`: skip. 1.2.2's parallel cutting covers most of it, and its builder threads would call our floors and
   surfaces: Halley's are plan maths (safe), but Luna's `MoonStrike.crater` reads the live world (`hasChunk`,
   `MoonLand.groundAt`) and `MoonVeins` caches in a plain map, neither safe off the server thread.

## 18. Stellar Convergence (idea agreed 2026-11-03, not started)

The user's idea, inspired by **The Shooting Star - Stellar Quartet** (星际四重奏) by ikun520
(https://www.curseforge.com/minecraft/mc-mods/the-shooting-star-stellar-quartet). **Credit it** in the README, the
CurseForge description, the changelog and the feature's own H-menu text. What Stellar Quartet does (its page,
2026-11-03): P splits the screen into small screens and fires every TSS hit at once, one per screen; AUTO mode spreads
the strikes round the player, AIM mode puts them on one point; its settings page is in the H menu. Ours must be its
own thing, not a copy of it.

- **What**: one key (or remote skill) that fires every weapon the player ticked, at once, at the crosshair: TSS's own
  skills and ours, later other addons'. The ticks live on the addon's plate in the H menu. Each is cast with its own
  slider values through RimuLib's `SkillSet.of(skill).cast(player, skill, tuning)` (§14).
- **Name**: **Stellar Convergence** (user's pick). Others offered: Stellar Symphony, Syzygy, Grand Conjunction.
- **Film: panel montage** (user's pick): quick cuts to each ticked weapon's signature moment; each shot freezes into a
  comic-style panel (a captured frame, so no second camera has to render) that slides aside until the screen is a
  grid; the grid shatters as everything hits. RimuLib's film tools (§15). The weapons' own films don't play meanwhile.
- **Outside the film (the user definitely wants this)**: a mixed effect of the ticked weapons on top of each one's own,
  e.g. one sigil ring over the mark with a coloured segment per weapon (Halley ice blue, Luna molten orange, Proxima
  crimson, others from `Skill.color()`), streaks spiralling in, one shared shockwave ring blending their colours.
  Screen shake, flashes and particles must not simply add up: one shared, capped shake.
- **Timing**: ideally every impact on one tick (hold back the quicker ones). Ours are known (`HalleyPlan`, `MoonPlan`);
  TSS's and other addons' aren't exposed (only `Skill.duration()`): measure them, or fire as a timed barrage.
- **Other addons** (the user wants Quasar, Miku Star etc.): makes sense and costs little: `SkillSet.all()` lists every
  mod's skills with their casters, so the list fills itself. Their films and effects stay theirs.
- **Risks**: several huge strikes on one spot (RimuLib 1.2.2's shared 30 ms carving budget keeps the tick in check,
  §17, but loading and effects stack); clashes with Stellar Quartet when both are installed (its key is P, its page is
  in the H menu: check both).
- **No limits**: like the commands (§14), warn on the plate that firing everything at high power can freeze a server.
- **Unlock with Stellar Quartet? (the user's plan, 2026-11-06, for their announcement; not built, not decided)**: SA-00
  only unlocks when Stellar Quartet is installed, so the addon doesn't pull players away from it. Today nothing checks
  for it (SA-00 is always there and only credits it), and SA-00 lists every mod's remote skills (ours and TSS's are
  timed to land together; other addons' fire at once). Stellar Quartet (CurseForge 1732380, ikun520, All Rights
  Reserved, **client-only**, needs TSS): 1.4.0 (2026-11-02) for NeoForge/Forge/Fabric 1.21.1 and Fabric 26.3 only
  (none for 1.20.1, 1.21.11, Forge/NeoForge 26.3); says it supports TSS up to 1.5.1 (this update needs 1.5.2).
  Answer given: checking whether it is loaded is fine (nothing of theirs used or shipped; asking ikun520 is courteous,
  not required); it must be a per-player client check told to the server (it's client-only); a wrong-loader jar isn't
  loaded, so it wouldn't unlock (a file-presence check is possible but a bad idea: wrong-version jars of the same
  loader can stop the game starting). Its mod id needs its jar (download needs the user's OK).
  **BUILT on main 2026-11-06 (the user: "do only where sq exists")**: `StellarQuartet` (`MOD_ID` "star_quartet", read
  from its 1.4.0 NeoForge jar, downloaded with the user's OK into the project folder's `other mods\stellar quartet\`,
  never shipped): each client says `ModList.isLoaded` in `ChoicePayload` (network version 4), `Convergence.cast`
  refuses the remote's cast without it (operators' `/ssa strike` always can, `NoCooldown.commandCast()`), the plate's
  hint becomes "Locked: install Stellar Quartet ..." in red. `REQUIRED` = true on main; **set it false on the
  forge-1.20.1, fabric-1.20.1 and fabric-1.21.11 branches when porting** (no Stellar Quartet there). Built, not tested
  in game (the user's game was open). Found in its jar: it already lists *our* addon as an optional dependency (it
  fires our Halley in one of its quarters), and it hooks TSS's obfuscated classes by their 1.5.1 names
  (`lllli111i`, `llli1l1ll` = 1.5.1's FxManager, `ii1lilll1ll`) plus RimuLib internals; in TSS 1.5.2 those names are
  different classes, and its TSS range is open (`[1.5.1-demo,)`), so 1.4.0 loads on 1.5.2 and most likely crashes or
  breaks (not tested). Like us it needs an update after every TSS update; until it has one for 1.5.2, nobody on our
  update can unlock Convergence. The user's announcement was corrected (they're posting it).
- **Decided 2026-11-04 (the user's picks)**: it is **its own remote skill** (in the remote's list as SA-00 · Stellar
  Convergence; selected and fired like any skill, with its own key slot); **every impact lands on the same tick** (the
  quicker weapons are cast later); where they land is **a switch on the plate**: all on the crosshair, or spread in a
  ring round it. Built in stages on main: (1) ticking the weapons on the plate and firing them together, (2) the mixed
  effect outside the film, (3) the panel-montage film (TSS's films are RimuLib `Cutscene`s with `pose(t)`, so the
  montage can borrow each weapon's own camera instead of copying anything).
- **Built on main 2026-11-04** (all three stages; not pushed). Code map:
  - `compat/tss/ConvergenceSkill` (SA-00 · Stellar Convergence, id `stellar_convergence`, `Codenames.CONVERGENCE`,
    colour 0xE6DCFF, default key **B** (66; not P, Stellar Quartet's), cooldown 1200, `powerSliders()` false: each
    weapon uses its own sliders), joined to the remote after Luna in `TssLink.install` (routed to `Convergence.cast`).
    Icon: `assets/shooting_star_demo/textures/gui/sprites/skill/stellar_remote/stellar_convergence.png`, made by
    `art/make_convergence_icon.sh` (ffmpeg).
  - `compat/tss/Convergence` (server): each player's picks arrive as `Convergence.Picks` (RimuLib packet c2s: the ring
    switch, and per ticked weapon its id and the sliders the client has, `SkillTuning.of`), held to the server's slider
    limits (`compat/tss/Limits`, shared with `CastingMixin`). The cast aims once (`aimGround`, so `/ssa strike
    stellar_convergence at ...` works too), works out each weapon's ticks to impact at its speed (`TSS_IMPACT`
    table, Halley from `HalleyPlan`, Luna `MoonPlan.CONTACT`; unknown ones, other addons' and Tesseract/Gravitia, fire
    at once with the slowest), and queues the casts so all hit together; `Convergence.tick` (server tick) casts each
    through `SkillSet.cast` with `StrikeAim` (its point: the mark, or a ring of radius 64 round it in the caster's facing
    order) and `NoCooldown.forced`. It sends `ConvergenceFx` (s2c: caster, mark, hit game time, ring, each weapon's
    colour and point) to players within 1500 blocks. Both packets go through RimuLib's network (no per-loader code).
  - **Impact times (TSS 1.5.1 and 1.5.2 alike, speed 1, power 1; measured `test builds\2026-11-04 auto test 11
    convergence\point`, re-measured on 1.5.2 in `test builds\2026-11-05 auto test 15 impact timing TSS 1.5.2\` with
    `test scripts\autotest10.sh`)**:
    Railgun 353 ticks, Gungnir 323, Seven Stars 339 (its seven stars land one every 16 ticks from 291, the middle one on
    the mark, then its constellation lines from ~400), Halley 331 (its plan's 330 + the cast tick), Luna 330. Tesseract
    carves no land (no moment to time); Gravitia turns the whole overworld into its space stage (`gravitia_space`) and
    stopped the other weapons' impacts in a measuring run: it fires last (below). Remeasure after every TSS
    update: a temporary `compat/mixin/DebugCarvingMixin` (in `shooting_star_addition.mixins.json`) injecting at the
    RETURN of RimuLib `Carving`'s private named constructor `(ServerLevel, String, Carving$Floor, BlockPos, Vec3, double)`
    to log `[TIMING] carving '<name>' gametime=... centre=... radius=...`, plus a `[TIMING] cast` log line in
    `StrikeCommand` before `StrikeAim.set`; impact = carving tick - cast tick. Remove both afterwards.
  - Client: `compat/tss/client/ConvergencePlate` (top right of the H menu, beside the skill list: weapons 1-9 with tick
    boxes in their colours and their slider values, R = on the crosshair / in a ring, the credit line "Inspired by
    Stellar Quartet, by ikun520" and the red no-limits warning; sends the picks when they change while the menu is open
    and as the game joins, via `ServerSettings.onReceived`); client config `convergence` (list of ids; default all but
    Gravitia) and `convergence_ring`. `ConvergenceClient` keeps the convergences told; while the player's own is on its
    way, `CutsceneDirectorMixin` holds every weapon's film back (and steps their clocks each tick, so their cameras keep
    running). `client/ConvergenceView` is the shared effect (sigil, segments, ring marks, spiral, flash, one colour-
    blended shock ring), drawn by `CometRenderer` with the comet's glow shapes. `ConvergenceFilm` is the montage
    (shoulder shot, a high turn round the mark, then ~14 ticks per weapon through its held film's `pose()`, frozen into
    `client/FilmPanels` (a half-size copy of the screen per panel, blitted from the main framebuffer in the film overlay
    pass) sliding into a grid, which shatters at the hit onto a wide rising shot).
  - **Ring layout (the user, 2026-11-04: "keep in mind the distance", or Halley and Luna vanish under the giants)**:
    `Convergence.ring` gives each weapon a stretch of the circle as wide as its own reach (`reach`: Halley's/Luna's
    crater radius from their params, TSS's from `TSS_REACH` (Railgun 200, Gungnir 412, Seven Stars 160 per star,
    Tesseract 96) times its power over its default, since some TSS power sliders count blocks: the Railgun's shows
    "200×"), smallest facing the caster, biggest round the back, and moves the ring out along the aim until the caster
    is 24+ blocks outside every reach (tested: the centre moved from 121 to 341 blocks out). Point mode is unchanged
    (all on the crosshair; the caster's own risk, as with the weapons alone).
  - **Sliders** (the user asked how power works, 2026-11-04): each weapon fires with its own sliders from the remote's
    menu; the plate says so ("Each uses its own sliders, set under it in this menu (shown on the right)") and shows
    them per weapon; so does the skill's description. **Master sliders (the user's pick, built 2026-11-05)**: SA-00
    has its own sliders in the menu (`ConvergenceSkill.power()` = `Power(0.1, 10, 1, true, 0)`, warns above 2×).
    `Convergence.scaled(pick, master)`: each ticked weapon fires at its own power × SA-00's, its own speed × SA-00's,
    and the lower of its own damage and SA-00's; then its own slider range (`clampFor`) and the server limits
    (`Limits.hold`). The plate shows each weapon's values after that (hint: "Each fires with its own sliders times
    SA-00's"); the menu's line under SA-00 reads "every ticked weapon at N× its own power". RimuLib keeps SA-00's
    sliders in `config/shooting_star_demo_skills.properties` (`stellar_remote.stellar_convergence.power=...`).
    Game-tested at 2× (`test builds\2026-11-05 auto test 14 master power\`: the plate showed Railgun 400×, Gungnir
    and Seven Stars 8×, Halley and Luna 2×); test jar `test builds\2026-11-05 master power\`, in the Modrinth profile.
  - **UI too big (the user, 2026-11-05: "overwhelming", do both)**: done: `ConvergencePlate` draws only while SA-00 is
    the remote's selected skill (`ClientSkills.selected(SkillSet.of(CONVERGENCE))`), at 0.8 of the addon plate's scale,
    with one hint line ("Each fires with its own sliders. 1-9 tick, R where, V hide.") and 12-pixel rows; **V** folds it
    to a small tag with its name and the V key (client config `convergence_plate`, default true); 1-9 and R only act
    while it's shown. The picks still sync every menu frame whatever is shown (the other skills' sliders change while
    they're selected). Its text never drops below one screen pixel per font pixel (scale >= 1 / GUI scale). Harness
    option `select=<skill id>` selects a skill before the final menu shot (`test builds\2026-11-05 auto test 12
    convergence plate\`). Test jar `test builds\2026-11-05 Convergence plate smaller\`, in the Modrinth profile (the
    Stellar Convergence build renamed `.disabled`).
  - **The film's panels work with TSS's weapons too (solved 2026-11-05: it was the test harness).** `FilmPanels`
    draws them at the end of every frame (`RenderFrameEvent.Post`, LOWEST). The frames actually presented to the window
    (saved at the start of the next frame) show the grid with the Railgun, Gungnir and Seven Stars ticked
    (`test builds\2026-11-05 auto test 17 film panels\film-presented\presented_330.png`, `_345`). Every earlier test
    said "no panels" because the harness's screenshots don't line up with the film when TSS's weapons run: the
    integrated server falls ~200 ticks behind under five strikes, the client level's game time (the `skill=other`
    shot clock) snaps back when it catches up (5138 -> 4940 at film time 242), and the film starts when the server's
    `ConvergenceFx` arrives; the shots showed the first-person view after the film had ended. Harness fixes (a client
    tick clock, grabbing at the next frame's start) were tried and didn't line the shots up: reverted. **To check a
    film with TSS's weapons, save frames by the film's own time**: a temporary RenderFrameEvent.Pre listener that, at
    chosen `current.time(0)` values in `ConvergenceFilm`, calls `Screenshot.grab` on the main target (the frame just
    presented). Ruled out on the way (don't redo): GL state (draw buffer, colour mask, clip distances, rasterizer
    discard, depth range, polygon mode, sample mask: all normal), the main framebuffer's colour attachment
    (unchanged), drawing in the GUI pass (`Gui.render` is cut short at its start while a film plays). Worth knowing:
    under heavy server lag the film runs on the client's clock, so its "hit" can come before the server's hits land.
  - Test runs: `test builds\2026-11-04 auto test 11 convergence\` (point, film-*, film-ring*); test jar
    `test builds\2026-11-05 Stellar Convergence\`, in the Modrinth profile (the code-names/sounds build renamed
    `.disabled`). The client-test harness now takes its screenshots at the lowest priority, registered at the first
    client tick (after every mod's own end-of-frame drawing).
  - **Found in testing**: the caster must aim far: the danger zone is the biggest weapon's (Gungnir 412 at 1×, the
    Railgun's column 200 to the bottom of the world, Seven Stars' pattern ~600 round the mark); the test player 116
    blocks off fell into the void, as with those weapons alone. At the hit every weapon's own screen effects (TSS's
    impact frames, glitches) run at once; flashes and impact frames don't add up (RimuLib's `ScreenFx.addFlash`/
    `addImpact` keep the strongest), the camera shake did (below).
  - **Gravitia fires last, once the others are done (the user, 2026-11-05; first a fixed 30 s: "super delayed")**:
    `Convergence.finale(skill)` (TSS's `gravitia`) waits at least `FINALE_MIN` = 100 ticks after the hit (Seven Stars
    starts its constellation lines ~2.5 s after it), then fires as soon as `othersDone`: none of the caster's addon
    strikes still working on the land (`Spell.busy`: the spell's `worldDone()`; not `Spell.running`, which held it to
    Luna's whole aftermath, 341 ticks; tested: Halley + Luna + Gravitia fired it 100 ticks after the hit, `test builds\2026-11-05
    auto test 18 proxima\finale2\`) and no RimuLib carving in that world still cutting (reflection on
    `Carving.LIVE`, a weak set of every carving, each `done()`; the ones in `Carving.LINGERING`, waiting for unloaded
    land, don't count); at the latest `FINALE_MAX` = 1200 ticks after the hit. Logged: "Stellar Convergence fires its
    finale, gravitia, N ticks after the hit". At the mark (not in the ring); alone, it fires at once; its own film plays
    then (Convergence holds films only until 20 ticks after the hit). The plate shows "last ·" on its row. Still
    unticked by default.
  - **The plate's old look (the user, 2026-11-05)**: T on the plate switches old/new, client config
    `convergence_plate_old` (default false = new). Old = as before the 2026-11-05 tweak: always shown whatever skill is
    selected, the addon plate's size, 236 wide, 13-pixel rows, a two-line hint, no V fold, its keys work whatever is
    selected. New = 0.8 of that, only while SA-00 is selected, V folds it.
  - First Gravitia version tested (`test builds\2026-11-05 auto test 16 gravitia last, shake cap\capped\`): the hits at 353, the
    world in Imaginary Space at 1000, Gravitia's film at the end.
  - **Convergence shake: CAPPED / FULL / OFF (the user, 2026-11-05)**, X on the plate, client config
    `convergence_shake` (default CAPPED), from the cast until 20 s after the hit (`ConvergenceClient.active()`). TSS's
    camera shake (`FxManager.applyShake`) reads two static floats: `trauma` (impact kicks, held to 1 by TSS) and
    `rumble` (every effect's `ScreenFx.shake` added up: TSS's effects add, ours take the max). Measured: rumble 2.32 at
    the hit with five weapons. `FxManagerMixin` at the HEAD of `applyShake` calls `StarClientBridge.holdShake`: CAPPED
    holds both floats to 1.0 (found by reflection as FxManager's two static non-final floats: shadows in TSS mixins
    aren't remapped per version; if the count changes it logs and only OFF works), OFF cancels the shake;
    `PostFxRendererMixin` (RimuLib) zeroes `ScreenFx.shake` (its screen jitter) when OFF. The plate's new row isn't
    screenshotted yet.

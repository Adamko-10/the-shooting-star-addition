# Other Shooting Star addons and RimuLib's API

Checked 2026-11-10 from each addon's newest jar (in the project folder's `other mods\`). The list of addons comes from
TSS's CurseForge "Dependents" tab (`/minecraft/mc-mods/the-shooting-star-demo/relations/dependents`, three pages, mods
mixed with modpacks): look there again for new ones. Not Java (ignored): "Shooting star mod by hamzah" (Bedrock). RimuLib 1.2.2's public API has
one official way to add a skill to the Stellar Remote: `SkillApi.whenRegistered("shooting_star_demo", set -> ...)` +
`SkillApi.extend(set, skills, caster)` (docs: https://docs.rimuru.cyou/rimulib/ "skills").

## The addons we want to work with

| Addon | Dev (CurseForge) | Loaders | Joins the remote through the API? | What it breaks |
|---|---|---|---|---|
| [The Shooting Star Addition [SS-21 Quasar]](https://www.curseforge.com/minecraft/mc-mods/the-shooting-star-addition-ss-21-quasar) 1.8.0 | Aether21 | Fabric 1.20.1, 1.21.1, 1.21.11, 26.3 (one jar); 1.7.1 also Forge/NeoForge | **No.** Writes RimuLib's private `SkillSet` fields by reflection (`skills`, `caster`, `BY_NAMESPACE`, `OWNER`, `INDEX`) and sets `skills` to a frozen `List.copyOf(...)` | Every skill added with the API after it (ours, SX-01's) is castable but missing from the H menu. The CurseForge reports. |
| [The TON618 demo](https://www.curseforge.com/minecraft/mc-addons/the-ton618-demo) ("TON-618 Experience") 1.0.1 | user_b6931bptem90f0r2 | Forge 1.20.1, Fabric 1.21.11 | **No.** Same as Quasar (`StellarButton`: reflection, frozen `List.copyOf`, its caster wrapped in a proxy) | Same as Quasar. |
| [The Shooting Star Addition [SX-01 Solar Flare]](https://www.curseforge.com/minecraft/mc-mods/the-shooting-star-addition-sx-01-solar-flare) 1.1.1 | Exener323 (jar: "Martina") | Fabric 1.20.1, 1.21.1, 1.21.11, 26.3 | **Yes** (`SkillApi.whenRegistered` + `extend`), plus a workaround that puts itself back when another addon froze the list (`RemoteLink.ensure`) | Nothing. |
| [The Shooting Star - Stellar Quartet](https://www.curseforge.com/minecraft/mc-mods/the-shooting-star-stellar-quartet) 1.4.0 | ikun520 | NeoForge/Forge 1.21.1, Fabric 1.21.1 + 26.3 ("fabric-multi") | Adds no remote skill. Its own H-menu row is a mixin into RimuLib's internal `LockedSkill.rows`; casts through `SkillSet.of(skill).cast(...)`; many hooks into RimuLib and TSS internals | Nothing of ours. Its hooks break with RimuLib/TSS updates (theirs to update). The API has no "add a menu row" call, so there's little to ask them. |
| [SS-22 Hyperion](https://www.curseforge.com/minecraft/mc-mods/ss-22-hyperion) 1.9.5 (2026-11-09) | wyt12357 | Fabric 1.21.11 | **Not checked**: its jar (file 9525205) gave 403 from CurseForge's CDN on 2026-11-10 (too new?). Download it in a browser into `other mods\hyperion\` and check | ? |
| [Miku Star; A Shooting Star Addon](https://www.curseforge.com/minecraft/mc-mods/miku-star-a-shooting-star-addon) 1.4.4.2 | UsagiTsukino01 | Forge 1.20.1 / 1.21.1 | Not applicable: made for TSS 1.4.4 (`shooting_star_demo [1.4.4,)`, no RimuLib), from before TSS 1.5 moved the remote into RimuLib | Doesn't work on TSS 1.5.x at all (ours needs 1.5.2); when it updates, ask it to use the API |
| [The Shooting Star Addition [SS-12 Spacetime]](https://www.curseforge.com/minecraft/mc-addons/the-shooting-star-addition-ss-12-spacetime) 1.0.3 | Diddi | Fabric 26.3 | Not applicable: made for TSS 1.4.4 (`shooting_star_demo >=1.4.4-`, no RimuLib) | Same as Miku Star |
| `project-1729784` (CurseForge, unnamed) | ikun520 | ? | Unpublished project listing TSS as optional | Look again when it's out |
| **Ours**, The Shooting Star Addition 1.8.x | adamko_10 | all seven | **Yes** for joining (`SkillApi.extend`) | Still uses RimuLib internals elsewhere where the API has nothing (carving's `linger`, `SectionSink`, `CutsceneDirector`, `SkillKeys`, `ScreenFx`, CLAUDE.md §17a). |

## Can we manage without them switching? Yes (built 2026-11-10)

`TssLink.showInMenu(set)` (main `029c9a3`, on every branch): right after our `SkillApi.extend` it puts every skill
either list holds back into RimuLib's rows and `skills` back to a live view of them. So:

- our skills show whichever order the addons load in (Quasar or TON618 before us froze the list; after us, their copy
  already holds ours);
- API addons that join after us show too (until another reflection addon freezes the list again later; SX-01 heals
  itself anyway);
- the reflection addons' own skills stay in the menu (they're in the frozen list, which is merged in);
- casting was never broken: RimuLib's `SkillSet.cast` asks the API's per-skill casters first, and the addons' caster
  wrappers pass everything else on.

Limits: it reads two private RimuLib fields (`rows`, `skills`), so a RimuLib update that renames them turns it off (a
warning in the log, our skills could vanish again next to those addons); their reflection would break then too. The
clean fix is theirs: one call instead of the reflection.

## What to ask Quasar's and TON618's devs (copy and paste)

> Hi! I'm adamko_10 (The Shooting Star Addition). Players running your addon next to mine or SX-01 Solar Flare found
> our skills missing from the Stellar Remote's menu. The cause: your addon joins the remote by setting RimuLib's
> private `SkillSet.skills` by reflection to a frozen `List.copyOf(...)`. Since RimuLib 1.2.2 that list is a live view,
> and every addon that joins after yours through RimuLib's API disappears from the menu. RimuLib 1.2.2 has an official
> way that keeps everyone's skills:
>
> ```java
> SkillApi.whenRegistered("shooting_star_demo", set -> {
>     if (set.hidden || !"stellar_remote".equals(set.id.getPath()) || SkillApi.setOf(YOUR_SKILL) != null) return;
>     SkillApi.extend(set, new Skill[]{YOUR_SKILL}, (player, skill, tuning) -> YourCasting.cast(player, tuning));
> });
> ```
>
> (`cyou.rimuru.rimulib.api.SkillApi`, docs: https://docs.rimuru.cyou/rimulib/). It replaces the reflection into
> `skills`, `caster`, `BY_NAMESPACE`, `OWNER` and `INDEX`, and won't break when RimuLib's internals change. I've added a
> workaround on my side for now, so no rush. Thanks!

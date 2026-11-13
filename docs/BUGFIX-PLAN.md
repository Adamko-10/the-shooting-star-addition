# Fixing Fable's 2026-11-12 findings

The findings: the project folder's `reviews\fable-bug-review\FINDINGS.md` (Fable 5.2, read-only). The user,
2026-11-13: "lets just plan on fixing every bug on that list". Claude checked the main claims in the code first:
- the effect packet's 512-block range (`Spell.java:107`);
- no catch round the lingering ticks (`HalleyAddon`'s server tick listener);
- `RuntimeException`-only catches (`Spell.tickAll`, `Convergence.tick`);
- no `end_gateway` in the tag;
- the comet heart sized `craterRadius / 56`;
- no dimension check in `ProximaCasting`.

All are as Fable says.

**Every fix goes to all seven branches** (they are fixes to released features), then one CI round and a test jar.
The plan is to ship them together with the Meridian port (one release).

## The bugs (all of them get fixed)

| # | What | Fix | Size |
|---|---|---|---|
| R1 | An error in the work left after a strike (Luna's/Proxima's leftover land, radiation zones, random flares) is not caught: the server closes, and a bad zone would repeat on every start | Wrap each lingering item's tick (`MoonBuild`, `MoonLand`, `ProximaLand`, each zone, `SolarFlares`) in a try/catch like `Spell.tickAll`'s: log once, drop that item | S |
| R3 | A changed RimuLib internal throws an `Error` (`NoSuchMethodError`), which `Spell.tickAll` and `Convergence.tick` don't catch: the server closes mid-cast | Catch `LinkageError` too (as `Spell.everyone` already does), log once, stop that strike | S |
| B1 | Strikes bigger than 512 blocks hurt players whose game was never sent the effect (Luna at 10x kills out to ~1,100 blocks) | The effect's range = the larger of 512 and the strike's own reach + 64 (each spell knows its reach). Players arriving mid-strike: later, optional | S |
| B2 | Halley's comet heart at high power: millions of loop steps in one tick (seconds at 10x; the 60 s watchdog at the config's extremes) | Fill each spike by columns instead of overlapping cubes, paint it chunk by chunk over a few ticks like the rest, and cap its size (3x) | M |
| B4 | With a shader pack on, Proxima's gate in the sky (outside the film) isn't drawn, on all seven | With a pack on, draw it after the world pass (where the film's space shots already draw fine with packs); it sits high in the sky, so being drawn over hills rarely shows. Fallback if that looks wrong: skip it with packs and say so | M |
| B3 | Proxima in the Nether: erases at the mark but cuts its bowl into the bedrock roof; cast in a cave, the bowl is cut on the surface | Refuse it where there's no sky (a ceiling, e.g. the Nether: "there is no sky here", like Luna's "no moon"); when the mark is well under the land's top (a cave), measure the bowl from the mark | S |
| B5 | An End gateway inside a crater is gone for good | Add `minecraft:end_gateway` to the `unbreakable` tag (data only; 1.20.1 branches use `tags/blocks/`) | S |
| A0 | The close-on-pick key is shown as "[" (wrong on German/French keyboards) | Show the key's real name from the keyboard layout | S |
| Docs | README and CLAUDE.md lines out of date; four code comments | As Fable lists them | S |

## Fable's other points: the user's call (not bugs as such)

- **R2**: things dropped by a player killed in Proxima's bowl are wiped by the wave a tick later. Fix: leave items and
  experience dropped after the impact alone. Recommended.
- **R4**: with `no_cooldown` on, nothing caps how many strikes one player has running. Fix: a server setting for the
  most strikes running per player (and in all), and one shared time budget for all our land work.
- **A6, radiation**: no way to clear zones, and players who weren't there don't see them. Fix: `/ssa zones list|clear`
  and send zones to players when they join.
- **A5, other mods' things**: a strike removes item frames, minecarts, Create trains and grave entities with no drop.
  Fix: an entity tag "never removed" that servers and modpacks can fill (graves in it by default if we know the ids).
- **A5, Convergence**: it can fire a skill from any RimuLib skill set, not only the remote's. Fix: limit it to the remote.
- **A1, OptiFine**: not detected. Fix: one line in the description and README (`shader_packs = ALWAYS` is the switch).
- **R5, the Forge branches**: things only a game run settles (forge-1.21.1's saved zones after a restart, an early
  Forge 52 build, a dedicated server). Fix: raise forge-1.21.1's Forge range to the build we compile against; the rest
  is one person's game test.

## For the Meridian port (from A2)

Check by hand after the update, not only the build:
- `StarClientBridge.running` (the first static list of FxManager);
- the raw names table (the effect packet record per branch, the cast request, the running-spell lookup);
- `RimulibFabricClient.onInitializeClient` (where we attach on Fabric);
- the silent mixins that matter most, the server's slider limits (`CastingMixin`) and Convergence's ring aim
  (`aimGround`);
- the weapon impact timings in `Convergence` (re-measure).

## Order and cost

1. On main: R1, R3, B1, B5, B3, A0, docs (small, one sitting), then B2 and B4 (each needs a game test: B2 at 10x
   power, B4 with Complementary on).
2. Port to the six branches (mostly the same lines; B4 by hand on 1.21.11 and 26.3), one CI round, a test jar.
3. Ship with Meridian.

About one long session of Claude's work, plus a CI round (free) and the user's in-game check.

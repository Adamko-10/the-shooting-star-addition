# SA-05 · Great Attractor and SA-06 · Hypervoid: how to build them in the game

A plan only (2026-11-12): nothing is built. It turns Fable's approved previz (version 2,
https://claude.ai/artifact/S5kyk3ACpBUWZg6fPi48VR; source and tick tables in the project folder's
`previz\great-attractor\`, `NOTES.md`) into stages, the way Proxima was built (`docs/SS-07-PROXIMA.md`). The design and
the user's decisions are in `docs/NEXT-SKILLS.md`. The previz is "near perfect" (the user, 2026-11-12); what is still
missing gets improved in a later update, not before the build.

Rules that hold throughout:
- built on `main` first, the user tests and approves, then it is ported (CLAUDE.md §0);
- every effect is a pure function of one clock, as the previz's `story(u)` is: no state that builds up frame by
  frame, so films can be scrubbed and Reverse Collision is the same code run backwards;
- nothing from The Shooting Star or other mods; the two galaxy pictures are public domain (credit both: NASA/JPL-Caltech/
  R. Hurt's Milky Way map, Hubble's M74 used as Andromeda's body).

## Stage 1: SA-05 on the remote, and the time stop (no film yet)

- `GreatAttractorSkill` / `GreatAttractorSpell` like `ProximaSkill` / `ProximaSpell`; `GreatAttractorPlan` with the
  previz's ticks (`NOTES.md`): the freeze fires at 1312, time stops at home at 1392, the film is 1680 ticks (84 s).
- The time stop: Minecraft's own tick freeze (1.20.3+, `ServerTickRateManager`; players still move, build and fight,
  as the design wants). Fully reversible, no terrain touched. The 1.20.1 branches have none: decide there (skip, or a
  smaller one of our own).
- The Sun out while time is stopped (sky, as Halley's and Proxima's skies are painted).
- Reverse Collision: SA-05's row in the H menu turns red while time is stopped; pressing it unfreezes (and later
  plays the film backwards). REMOTE: CRITICAL on our plate.
- Config (`[great_attractor]`): operators only on servers by default, the time stop on/off, the Sun on/off.
- Test: cast, time stops, players move, Reverse Collision restores it; a dedicated server.

## Stage 2: the space scene (the biggest and riskiest part)

One space scene with its own camera on the film clock, drawn in the film's overlay pass, like `ProximaCosmos`. From
the previz's "Rendering: what the game needs":
- **galaxies**: a textured disc plus about 55,000 points baked offline from the two pictures; one placement function
  (rotation, bridge, tail, "merge"); point brightness that holds its light, or wide shots and close-ups blow out;
- **black holes**: one small screen-facing quad each, a ray stepped ~60 times past a sphere (cheap because small);
- **the machine**: about 450 instanced boxes, all motion in the vertex shader (emitters telescoping, tearing off,
  spiralling in);
- **filaments**, **the Ultimate Freeze shell** (inside it things stop and go grey-blue), **the dark wave** (SA-06);
- **lensing**: in the star shader (slide stars along the ring), not the previz's extra pass; the machine, galaxies and
  black holes are never bent.

Upload everything that doesn't move once (vertex buffers), and animate on the GPU: rebuilding 55,000 points on the CPU
every frame would cost too much. Check it with a shader pack (Complementary), as the film overlay pass may behave
differently there. Fable could propose the shaders (read-only, patch files), since it wrote the previz's; Opus applies
them (the user's rule).

## Stage 3: SA-05's film

The 12 shots as a storyboard (`client/film/`, like `ProximaStoryboard`) and a cutscene (`compat/tss/client/`), the
title cards GREAT ATTRACTOR, MERGER, THE ULTIMATE FREEZE (`FilmTitle`), the HUD words (LOAD, CORE TEMP, DANGER: CANNOT
BE CALCULATED, the year counter, which stays), the remote at its limit (all ours: heat, sparks, steam, static; TSS's
model untouched). Sounds as Proxima's: one soundtrack cut to the film (the previz lists each shot's sounds).

## Stage 4: SA-05 seen from the ground (everyone, outside the film)

The flash, the thin arc of light across the sky, the pale line crossing the land, colours draining, the stopped merger
overhead. Sky arc and darkness are sky-shader work. Draining colour on real blocks needs a depth-aware screen pass
that shader packs may break: the safe fallback is a quick whole-screen drain at the clunk plus the line on the
ground. Then Reverse Collision's own cut (`REV_CUT` in the previz: triple speed, the remote vents its heat last).

## Stage 5: SA-06 · Hypervoid

- Locked until SA-05 has stopped time (config to drop that); operators only; two presses (EXTEND THE ULTIMATE FREEZE
  TO ALL OF SPACE?, then fire), no sliders. Its first shot ("the second press") may be cut, since the first press is
  before the film.
- Its 10 shots reuse stage 2's scene (the stall at 0.69%, the machine tearing apart, the implosion, time restarting,
  the dark wave), title card SA-06 · HYPERVOID.
- The world (void by default, config to keep it): wipe the loaded land at the strike (empty sections, as `MoonBuild`
  cuts Luna's crater), kill every entity in the dimension (creative players put on the platform), the 3x3 glass
  platform under the caster, and the dimension marked "voided" in its saved data so every chunk that loads or
  generates afterwards comes in empty. This is the one part that can lose a world for good: warn on the plate and in
  the README to back up first.
- Seen from the ground: in film order (the previz's 11 s).

## Stage 6: test, port, release

The user's game test, then the six other branches. 26.3 and 1.21.11 draw through their own pipelines, so the whole
space scene is ported by hand there (the slowest port). 1.20.1: the tick freeze decision from stage 1.

## Rough size

Proxima took about a week of sessions with its film. This is two films with a heavier space scene: about 2 to 3 weeks
of sessions in all (stage 2 alone about a week), plus the user's test rounds. The roadmap's mid-January date for SA-05
fits if work starts in December, after SA-03.

## Still open (ask the user when it is built)

- What "near perfect" is missing (for a later update).
- The Unfreeze ending ("good luck :)"), a later update.
- SA-05 in third person: is the remote's alarm heard by nearby players?
- Andromeda's body is M74's photo: fine to ship (credited)?
- The 1.20.1 time stop.

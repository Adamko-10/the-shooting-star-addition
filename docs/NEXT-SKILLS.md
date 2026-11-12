# Next skills and big changes (the user's directions, 2026-11-11)

Nothing here is built. Each section says what the user asked for and Claude's proposal where there is one.

## SA-04B · Proxima CME (the stronger Proxima)

What the user wants it to look like, on top of SA-04A Proxima Flare (today's skill):

- **Visibly stronger.** Both lenses heat up and glow (the hex lens the Dyson sphere's panels form in front of the star,
  and the gate near Earth); if a third lens fits (a relay between them), it heats too.
- **A stronger-looking beam** than the Flare's.
- **At the Earth gate it takes time**: the film holds on the gate while it "processes" the energy: it heats up, strains,
  and lets out a **shockwave in space** before it fires.
- **Two shockwaves you can both see**: the one in space at the gate, and the one at the impact.
- **A much bigger wave and shockwave that look very different** from the Flare's.
- **A much bigger impact with an actual hole** (the Flare leaves a 6-deep glassy bowl; the CME punches a real hole).

How the lens works (as built in 1.8.0): Proxima Centauri sits in a Dyson sphere 4.2 light years away; the sphere's panels
open like an iris and fly together into a giant hexagonal lens in front of the star; the lens soaks up the star's
energy (~3 s, energy flowing into the disc, the screen shaking) and focuses it into one beam that crosses the light
years to a gate near Earth; the gate catches it and spreads it into a cone of plasma aimed at the crosshair (the flash,
the front coming down, the impact). Code: `client/proxima/ProximaCosmos`, timeline `ProximaPlan`/`ProximaStoryboard`
(docs/SS-07-PROXIMA.md).

## TON-618 (another addon) and Stellar Convergence

The TON618 demo's remote button doesn't strike: it opens its own menu/video screen, and (the user, 2026-11-11) **it
also deletes your world**. Wanted later (not 1.8.1): in Convergence, treat it like Gravitia: fire it **last, delayed**,
once every other weapon is done (`Convergence.finale`), so its screen and its world-wipe don't cut the convergence
short. Its skill id is `ton618`; it exists for Forge 1.20.1 and Fabric 1.21.11 only.

## The skill ladder

SA-01 comet, SA-02 moon, (SA-03 planet: docs/SA-03-IDEAS.md), SA-04 star, **SA-05 galaxy to multi-galaxy**,
**SA-06 universe**.

## SA-05 · Great Attractor and SA-06 · Hypervoid (one story; brainstormed with the user 2026-11-11)

Not built. Everything below is agreed unless marked open. "Freeze" always means **time stopped** (everything halts
mid-motion, colours drain, sound cuts: Sinister Clockman's freeze in the Multiverse series; style only, nothing copied),
never ice.

### The Great Attractor (the device)

- **Name stays "Great Attractor"** for the skill and the device (the user turned down Laniakea/Zero Hour/etc.).
- **Look: a hidden black ring** (the user's pick), wider than a galaxy, sitting where the real Great Attractor is
  (behind the Milky Way's dust, the "Zone of Avoidance"). Invisible except for the starlight bending around it (a
  lensing halo). When it wakes, glowing lines race over it; its centre charges and fires.
- **How it pulls (the user's pick): dark-matter filaments.** The cosmic web's dark-matter filaments (normally invisible)
  light up violet when it wakes and tighten like cables toward the ring, reeling the Milky Way and Andromeda in. Real
  galaxies do flow toward the Great Attractor along such streams; this fast-forwards the real merger (Andromeda really
  is falling toward us). The ring's dark matter is also what floods out when it implodes (below).
- **It is what saves you**: it holds the stopped time. When it implodes (Unfreeze, or SA-06 asking too much of it), the
  protection is gone: hence the remote's "good luck :)".
- **The wave it fires: "The Ultimate Freeze"** (the user's name): a shell of stopped time racing out far faster than
  light. Shown in SA-05's ending, SA-06's opening, and **in the sky outside the films** for everyone.

### SA-05 · Great Attractor (galaxy level)

Film, in order:
1. Press; the crosshair; the remote uplinks: GREAT ATTRACTOR · DORMANT -> AWAKENING.
2. Pull out past the Milky Way to the ring; it wakes.
3. Violet dark-matter filaments light up across the void and tighten toward the ring; Andromeda is reeled toward us.
4. MERGER: tidal streams; Sagittarius A\* and M31\* circle closer as two glowing rings, gravitational-wave ripples.
5. Back home the Solar System starts to tear apart: planets drifting off their paths, the Sun guttering and going out.
6. **The remote is pushed to its limits** (all ours to draw, TSS's model untouched): heat shimmer, sparks and steam in
   your hand, a slight tremor; LOAD 340% -> 999%, CORE TEMP 4,800 K; red chevrons, the HUD stuttering into static; a
   rising whine and a soft alarm (not too loud); then DANGER: CANNOT BE CALCULATED · INITIATING THE ULTIMATE FREEZE.
7. The camera swings to the Great Attractor: it charges and fires the Ultimate Freeze.
8. The wave sweeps both galaxies just before the Solar System comes apart: everything stops (the black holes a hair
   apart), colours drain, a heavy clunk, silence.
9. Pull back: the two stopped galaxies hang still while the rest of the universe still moves.

In the world afterwards (nothing touches terrain, fully reversible):
- time stopped: mobs, water, fire, crops, redstone halt, dust hangs; **players can still move, build and fight** (the
  user's pick; Minecraft 1.20.3+ has a tick freeze built in, the 1.20.1 branches need our own or skip it);
- the Sun out; for everyone the device's flash, the wave sweeping the sky and land, the stopped merger overhead.

On the remote while time is stopped (GUI, no physical button):
- the H-menu plate reads REMOTE: CRITICAL, faintly glitching;
- **SA-05's row turns red: "SA-05 · Reverse Collision?"**. Pressing it plays the film backwards: the wave pulls back
  into the ring, the black holes drift apart, Andromeda slides away, the Sun relights, time restarts, the remote vents
  its heat.

**Later update: an Unfreeze option** (the user, 2026-11-11), to see the rest of SA-05's film (it is unfinished by
design: you never see the end). It sits next to Reverse Collision while time is stopped. The remote flashes
**"good luck :)"** and turns off; the Great Attractor, no longer holding time, **implodes**; time restarts and **the
merger finishes for real** (a huge flash, the merged galaxy hanging in the sky), but **the world survives**, changed
(the user's pick, 2026-11-11).

Config: operators only on servers by default; the time stop on/off; the Sun on/off.

### SA-06 · Hypervoid (universe level; a player's suggestion)

- **Rules (decided)**: locked until SA-05 has stopped time (config to drop that); **operators only** on servers; turns
  the world into a void by default, **config to keep the world** (then only the film and the deaths); "flip the cover":
  the first press shows EXTEND THE ULTIMATE FREEZE TO ALL OF SPACE?, the second fires; no sliders, like Gravitia.
- **Film (the user's idea, 2026-11-11: an implosion, then a dark-matter wave)**: the white crosshair burns into the
  ground, a 12 s countdown, the remote strains again; the camera zooms in on the Great Attractor, ordered to stop time
  across the whole universe: it charges far harder (white-hot, space bending round it), starts the Ultimate Freeze out
  past the stopped galaxies, and can't hold it: it buckles and **implodes**, collapsing to a point (silence, a hairline
  of light); then **an unimaginably massive wave of dark matter** bursts out of where it was (all the dark matter it
  was holding) and swallows galaxy after galaxy, cluster after cluster, the whole universe going dark; back in the
  world: the wave rolls over the land, then nothing: endless void and you on the glass platform. Title card SA-06 ·
  HYPERVOID. (Replaces the earlier shatter + bubble-universe ending.)
- **Outside the film**: the ring's implosion flash, the dark wave rolling across the sky and the land, the world
  dissolving into the void.
- **The world**: wipe the loaded land at the strike (empty sections, the way MoonBuild cuts Luna's crater), kill every
  entity in the dimension (creative players put on the platform), mark the dimension "voided" in its saved data so
  every chunk that loads or generates afterwards comes in empty (an endless void without freezing the server).
  **The glass platform goes under the caster** (the user's pick).
- Afterwards there is nothing to reverse: SA-05's row goes back to normal (with the void config off, Reverse
  Collision stays).

### The previz (Fable 5.2, version 2, 2026-11-12)

https://claude.ai/artifact/S5kyk3ACpBUWZg6fPi48VR; source, every shot in ticks and the rendering notes in the project
folder's `previz\great-attractor\` (`HANDOVER.md`, `NOTES.md`). Version 1 was rejected ("not according to my vision");
version 2: "absolutely awesome ... its actually near perfect". What "near perfect" is missing is **written down for a
later update** (the user, 2026-11-12: "fine with just writing this down for later and improving it in an update");
the build follows version 2. How to build it: `docs/SA-05-06-BUILD-PLAN.md`.

What version 2 changed from the design above (the user's picks or accepted):
- **Length**: SA-05 84 s (1680 ticks), SA-06 58 s (1160 ticks): "as long as it needs".
- **The Great Attractor is a machine**: 72 plated hull segments, masts, an inner rail, 24 emitters that telescope
  out, an inner ring turning against the hull; still black and unseen until it wakes. A new shot, "The machine wakes".
- **Galaxies from real pictures plus stars** (the user's pick): NASA/JPL-Caltech/R. Hurt's Milky Way map and Hubble's
  M74 as Andromeda's body (both public domain; credit both). Real-looking black holes (bent light, hot disc).
- **The Ultimate Freeze has an 8 s charge-up** and a half-second hold; the stop is shown three times (galaxies, black
  holes, Solar System), each starting 1.3 s before it happens there.
- **Reverse Collision is a recut**, not a plain reverse: wave back, black holes apart, Andromeda away, ring dormant,
  Sun relights, time restarts, **the remote vents its heat last** (the user's pick), at triple speed.
- **SA-06**: its countdown ends when the order is sent (tick 320); a long pull back where the freeze stalls at 0.69%
  of the universe (POWER INSUFFICIENT); **the device breaks apart, then implodes** (the user's pick; no shatter of
  space); a "time restarts" beat before the dark wave; on the land, in film order.
- **Kept (the user, 2026-11-12: "all can stay theyre fine")**: Fable's three unasked additions, the year counter
  (to 4.5 billion, stops dead at the freeze), the "time restarts" beat, Reverse Collision at triple speed.

### Still open

- SA-05 in third person: is the remote's alarm heard by players nearby, or only the caster?
- The exact look of the Unfreeze ending and of the changed sky afterwards (later update; not in the previz).
- What "near perfect" is missing (later update).
- Andromeda's body is M74's photo: fine to ship, credited?
- SA-06's first shot ("the second press") may be cut in the game: the first press is before the film.

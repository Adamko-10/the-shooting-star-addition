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

## SA-05 · Great Attractor (proposal)

The user's outline: the Great Attractor pulls two galaxies together; they merge; the two central black holes (the
Milky Way's **Sagittarius A\*** and Andromeda's **M31\***) collide, in the film and visibly in the sky for everyone
else; that wipes out the Sun; the player's remote overheats and blares an alarm (not too loud); a message like
"DANGER CANNOT BE CALCULATED · INITIATING ULTIMATE FREEZE"; the whole universe freezes, the camera zooming out further
and further (the feel of Sinister Clockman freezing the galaxy in the Multiverse series, scaled up; style only); it
stays frozen until the player chooses to reverse it, which plays the film backwards.

**Claude's proposal**

- **Name**: SA-05 · Great Attractor. The film's title card: GREAT ATTRACTOR, then MERGER, then the freeze's own card.
- **Film (~40 s)**, beats:
  1. The remote, a press; the crosshair; the sky starts to bend (stars streaking slowly toward one point).
  2. Pull-out from Earth past the Milky Way: far away a dark region (the Great Attractor, shown as a lensing ripple in
     the background stars: it's hidden behind dust in real life, which makes it eerie).
  3. Andromeda comes in from the side, both galaxies stretched into tidal tails, spiral arms tearing into streams.
  4. MERGER: the cores spiral toward each other; Sagittarius A\* and M31\* as two bright accretion rings circling
     closer, faster, gravitational-wave ripples spreading out over the starfield.
  5. They collide: a white-violet flash, a ring of light racing out through both galaxies; the Sun, back home, is
     snuffed out (a quick cut: the Sun shrinking to a point in Earth's sky and going dark).
  6. The remote overheats: the HUD glitches red, a soft alarm, then the text
     DANGER: CANNOT BE CALCULATED / INITIATING ULTIMATE FREEZE.
  7. The freeze: everything stops mid-motion, colours drain to an icy blue-grey, the camera zooms out and keeps going
     (galaxy, cluster, supercluster, the cosmic web), all of it frozen still. Hold. Silence with a faint ringing.
  8. Back in the world: frozen.
- **What the world does (all reversible, no terrain changes)**:
  - the Sun gone from the sky (the day stays dark; a dimmed sky, stars visible all day);
  - the universe frozen: the world's ticking stopped (Minecraft 1.20.3+ has a tick-freeze built in: mobs, water,
    fire, crops, redstone all stop; players can still move; 1.20.1 branches would need our own freeze, or skip it);
    an icy tint and still particles for everyone;
  - in the sky for everyone not in the film: the two black holes' rings circling and colliding high above.
- **Reversing it (the remote)**: no new physical button can be added to TSS's remote (its model and menu are TSS's and
  RimuLib's). Two clean ways that fit the remote as it is:
  - **Recommended: SA-05's own row changes while the universe is frozen.** Its title becomes
    "SA-05 · Reverse Collision?" (the remote's screen still shows SA-05); pressing it plays the film backwards
    (the freeze cracking, the camera zooming back in, the black holes pulling apart, the Sun relighting), then unfreezes.
    No extra row, nothing to find, and it can't be pressed by accident while nothing is frozen.
  - Or a separate row, "SA-05R · Revert Timeline", only listed while frozen.
  Name options for the reverse: Reverse Collision, Revert Timeline, Rewind, Thaw. Claude's pick: **Reverse Collision**.
- **Config**: who may fire it on a server (operators only by default), the freeze on/off, the Sun on/off.

## SA-06 · Hypervoid (a player's suggestion; the user's decisions 2026-11-11)

The suggestion: flip the cover and press; a white crosshair marks the ground; 12 s later the whole universe is seen,
galaxies vanish into darkness, a flash, a multiversal shockwave erases all matter: every block gone except a small
glass platform; everything dies (creative players are put on the platform). No sliders, like TSS's Gravitia.

**Decided**: it turns the world into a void by default, with a config to turn that off (then only the film and the
deaths, the world stays); **only operators** can fire it on a server; by default **SA-05 Great Attractor must have been
used first** (config to drop that requirement).

**Claude's plan**: wipe the loaded land at the strike (empty sections, the way MoonBuild cuts Luna's crater, fast),
kill every entity in the dimension, then mark the dimension "voided" in its saved data so every chunk that loads or
generates afterwards comes in empty: an endless void without freezing the server. Two presses (arm, then fire: the
"flip the cover") and a warning page like Gravitia's. Film: the countdown, a pull-out from Earth past the galaxy to the
cosmic web, galaxies going out one by one, silence, a point of light, the flash, bubble universes popping, then the
void and the platform. If SA-05 froze the universe first, Hypervoid can play into the frozen universe (the frozen
cosmic web is what gets erased): the two skills read as one story.

## Open questions (for when this starts)

1. SA-05's freeze: should players still be able to build and fight while frozen (vanilla tick-freeze: yes), or should
   everything, players included, stand still?
2. SA-05 in third person: sky only, or also the remote's alarm heard by players nearby?
3. Hypervoid's platform: at the caster or at the world spawn?

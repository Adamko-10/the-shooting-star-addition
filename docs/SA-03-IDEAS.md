# SA-03 ideas (the free slot between Luna and Proxima)

The series climbs: comet (Halley), moon (Luna), **planet-scale (SA-03)**, star (Proxima). Every idea below is bigger than
a moon and smaller than a star. All of them stay region-sized (carved near the centre, lazy carving further out, the
`unbreakable` tag honoured, never the whole map). Taken elsewhere, so avoided: Quasar (jet), Solar Flare (Sun whips
plasma), Hyperion (small sun dropped), TON618 (black hole). Sized S/M/L against Luna as the baseline.

## 1. SA-03 · Theia

**Pitch:** a Mars-sized planet hits the Earth at an angle, the collision that made the Moon.
**Player sees:**
- The remote fires; the camera leaves for space and shows a small red-brown world on a slow collision course with Earth.
- Back on the ground, the sky grows a second disc, rust-coloured, then a dark shape across a third of the sky.
- Contact is a glancing blow: one long line of white fire rips across the horizon and the planet tilts into the ground.
- Magma sprays up and out in a wide arc; the ejecta cools into a glowing ring of rock that hangs in the sky.
- Pullback: a ploughed, molten gouge on the land, the glowing ring slowly raining down and fading.
**World:** an oblique gouge (a long, wide, shallow furrow, not a round crater) with a lava-glass floor, and a fan of
ejecta craters thrown downrange. Lava pools only in the deepest part.
**New:** an angled impact (Luna is straight down, Halley a trench but a small body), a debris ring that orbits before it
rains, and a magma-ocean look. Lore bonus: it is the origin of Luna.
**Size:** M (Luna's plan and carving, plus an oblique footprint and the debris ring).
**Risk:** reading as "Luna but bigger"; the debris ring must be the star of the film.

## 2. SA-03 · Great Red Spot

**Pitch:** Jupiter's storm, a vortex wider than the Earth, is dragged down onto the crosshair.
**Player sees:**
- The camera drops to Jupiter's cloud tops and zooms on the red oval, churning for a few seconds.
- The oval peels off, spins into a funnel and falls toward Earth as a blood-red ammonia cloud.
- On the ground the sky goes rust and bruise-purple, a spiral of cloud turns overhead, lightning in the eye.
- The funnel touches down and starts to move; wind lifts leaves, items and loose blocks in a ring.
- It collapses after about a minute and leaves a wide scoured plain under a sky that slowly clears.
**World:** no crater. Foliage and loose blocks stripped, topsoil sandblasted to coarse dirt, scattered lightning
scorches, blocks lifted and dropped. The vortex travels along a short path (a few hundred blocks).
**New:** a moving, long-lived effect (every other skill is a single instant), destruction by wind not impact, entities
carried in a rotating flow, no crater at all.
**Size:** L (a spiral renderer, a moving server effect, a block-lifting system).
**Risk:** lag from many airborne blocks; cap them hard and use falling-block effects only near the centre.

## 3. SA-03 · Saturn's Rings

**Pitch:** a ring of ice and rock collapses out of orbit and rains down in one thin line across the land.
**Player sees:**
- Camera at Saturn: the rings thin, ripple, then break at one point like a snapped bracelet.
- Over Earth, a bright arc of sparkling ice appears from horizon to horizon, a second Milky Way.
- The arc buckles and sheds thousands of burning streaks, all falling in one band toward the crosshair.
- A curtain of impacts walks across the land, ice shattering to steam and shards.
- The last shot is a straight scarred line of small craters and frozen pools running over the horizon.
**World:** a long strip (say 600 by 60 blocks) of small impacts: shallow pits, packed ice and snow, steam, some water
pooling in the pits. Many tiny craters, not one big one.
**New:** a line of impact, hundreds of small events instead of one, and ice/water as the aftermath. A different
spectacle (an arc in the sky) from the single falling body of Halley and Luna.
**Size:** M (a sweep timeline, many cheap craters on the lazy-carving path, instanced fake streaks).
**Risk:** hundreds of carvings at once; stage them along the sweep and reuse Halley's debris code.

## 4. SA-03 · Io

**Pitch:** Jupiter's tide-flexed moon is thrown close and its volcanoes erupt, tearing the land open.
**Player sees:**
- A yellow-orange, blotchy moon pulled by tides, its surface flexing and cracking in a time-lapse.
- Sky turns sulphur yellow; a huge pale plume of volcanic gas rises from the moon's limb.
- Io swings past close overhead (a flyby, it does not hit) and the land answers: fissures split the ground in a line.
- Lava fountains burst from the fissures one after another, throwing glowing bombs high.
- Moon recedes; the sky stays tinted and the vents keep glowing.
**World:** a field of fissures and a dozen volcano vents in a region, sulphur-coloured blocks (yellow terracotta,
raw gold or glowstone sparing), some lava, ash and fumes.
**New:** a flyby with no impact, a tidal cause, many vents erupting in sequence, and a volcanic landscape. This also
covers part of the "neutron star: cracks and volcanoes" idea without the gravity-wave scale.
**Size:** M to L.
**Risk:** lava flow updates are expensive; use placed lava source blocks that freeze quickly, not a flood.

## 5. SA-03 · Ceres

**Pitch:** the asteroid belt's dwarf planet falls with its whole belt, a barrage of rocks and one big bright-spotted
world.
**Player sees:**
- A dim belt of grey rocks swinging through the sky as a ring of pebbles and boulders.
- Ceres, small and pale, glints from the middle with a white salt spot (the Occator craters).
- A first rain of boulders streaks across the sky, then a heavier one; the ground starts to pit.
- Ceres itself lands last, cracking its crust and releasing a burst of salty brine.
- The sky clears over a field of craters around a bowl with a bright white rim.
**World:** a wide scatter of small and medium craters plus one central bowl, salt-white and grey stone, a few brine
pools. Region-sized by design.
**New:** a barrage over time (the others are one object), a bright salt aftermath, and a mix of small and large
impacts. A reuse of Halley's machinery with new staging.
**Size:** S to M, the cheapest on this list.
**Risk:** feeling like "Halley, many times", so the salt spot and brine burst need to carry the identity.

## 6. SA-03 · PSO J318.5-22 (rogue planet)

**Pitch:** a planet with no star drifts past in the dark; it never touches Earth but its gravity wrecks the land.
**Player sees:**
- Stars blink out one by one in a moving patch of black; a dim, red-glowing planet slides into view.
- It fills a quarter of the sky; the horizon bulges and the sea starts to lean toward it.
- The ground heaves: slow tidal waves roll through the land, rock plates tilt and crack.
- Water floods inland in one slow wall, items and mobs lift a little, the sky stays dark red.
- It drifts away; the sea drains back, leaving a cracked, flooded, silent landscape.
**World:** earth tremors and cracks (a few radial fissures), a tidal flood limited to a set radius, a field of
temporary low-gravity for mobs. No crater.
**New:** the first "near miss" skill: no impact, and a slow dread instead of a bang. Overlaps with the neutron-star
idea (gravity, tides), so the two should be built as a pair or one of them dropped.
**Size:** L (fake water surge, tidal motion, entity effects).
**Risk:** water edits are expensive and hard to undo; use only the sea or lakes near the mark and give it a hard cap.

## Recommendation

**1. Theia.** It has the strongest story and spectacle, the least machinery risk, and the clearest answer to "what
goes between a moon and a star" (a planet, and the one that made the Moon). It reuses Luna's carving and film code, so
the cost is moderate, and the glowing debris ring gives it a look no other skill has. It is the safe pick.

**2. Great Red Spot.** It is the most different from everything else: a moving storm, not a crater. It would show the
series can do more than impacts, and its aftermath (a wind-scoured plain) is cheap to keep region-sized. The cost is
a bigger build and the lag risk with airborne blocks.

Ceres is the cheap fallback if the budget is tight, and Saturn's Rings is the strongest visual if the user wants an
"arc in the sky" moment. Rogue Planet and Io are better kept for SA-05+ next to the neutron-star idea.

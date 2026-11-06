# The sound overhaul (Sonniss)

The user's pick (2026-11-06): every sound of Halley, Luna and Proxima is remade from the Sonniss GDC 2026 game-audio
bundle (CLAUDE.md, "Decided 2026-11-06"), instead of the synthesised ones. The bundle lives outside the repo in the
project folder's `sound library\sonniss gdc 2026\` (the five zips, `files\` unpacked, `catalogue.csv` from
`sound library\catalogue.js`: length, loudness, peak per file). Only our cut, layered and mixed results go in the jar.

**Licence**: Sonniss GDC licence v2.0 allows finished projects (commercial too, no credit needed) but not supplying the
sounds themselves as files; a jar can be unzipped, so written confirmation from Sonniss is the clean route (the user's
to send). Keep every source file's name in `art/sounds/sources.md` so a pick can be swapped out if needed.

**How each sound is made**: a recipe script (like `art/overhaul_sounds.sh`) cuts the chosen source files, layers them
(each layer its own gain, filter, pitch and offset), adds the tail, sets the level by role and writes the shipped
`.ogg`. Levels by role, as the first overhaul (measured against The Shooting Star's own, never copied): impacts about
-11 to -12.5 LUFS, cues -15/-16, beds -16/-18, hums -19. Claude can't hear: every result is checked by the user in game.

## Status (2026-11-06, evening): all made, waiting for the user's ears

- **`art/sonniss_sounds.sh <out dir> [names]`** makes all 52 files (the 40 the game plays + Proxima's 12 new film
  ones) in ~3 minutes; **`art/sounds/sources.md`** (written by it) lists every layer: Sonniss file, cut, gain, start,
  filters. No default out dir on purpose. How a recipe works: each cut is levelled to -20 LUFS first (so a layer's gain
  means the same whatever the source), faded a few ms at both ends, filtered (pitch = `asetrate`, reverse, EQ,
  envelope), delayed, mixed, trimmed by samples to the length the code expects, then `finish` (level by role, limiter
  at -1 dBFS, up to three make-up passes, mono Vorbis q5). The countdown is generated (53 ticks on the old schedule,
  1 s gap shrinking 8% a tick to 0.09 s, a latch thunk under the first 20); Luna's debris is twenty seeded-random small
  knocks (books, a case, wood, a rattle, punches, pitched down) over gravel.
- Picks were made by measuring (the project folder's `sound library\envelope.js <catalogue row or file>`: level and
  three bands per 0.1 s, onsets, spectral peaks; Claude can't hear). Main sources: trailer booms 011/075/214 (hit 0.25 s
  in), 344 Audio bass drops, Kopeikin ice (crush, snap, fissure, ice field), ESM ice shatter / glass shards / fire
  whoosh / electric impact, Jake Fielding thunder rumble, The Noisery moaning metal and hurricane wind, AEROJet blast
  off (Halley's approach, Proxima's ascent), trailer alarm WholeNotes_011 (Luna's alarm), InMotionAudio arcs, campfire
  "putting out fire" (steam sizzle), a freight truck pass-by (Proxima's wave: high coming, low going).
- Kept from the old ones: every length and timing the code relies on (Luna's rumble swelling then near-silent for its
  last 0.5 s, the inhales ending on the impact, the build-up ending on FALL, the beam's discharge 0.5 s in, the wave's
  pass at ~1 s); Proxima's radio keeps the old synthesised whine (as planned), cleaned, with real static under it.
  Changed: `proxima_beam` is 4.0 s now (was 1.8; a sub-bass tail after the discharge, as the previz asks).
- The hits get a decay over the whole mix (trailer booms hold their tails loud; without it the limiter made attack and
  tail one wall). Levels reached: within 0.5 dB of target, except the hits 0.5-1.5 dB under (limiter-bound; the old ones
  were 1.5-2 dB under).
- **For the user to judge**: the listening folder `test builds\2026-11-06 Sonniss sounds\` (`new\` all 52, `old\` the
  40 shipped ones, same names, for A/B) and the test jar there (main's jar with the 40 swapped in; nothing else
  changed). **The old files stay in `src/` until the user approves**; then run the script with
  `src/main/resources/assets/shooting_star_addition/sounds` (it also writes the 12 film ones, unused until the film
  code and their `sounds.json` entries come).
- Proxima's new film events (uplink, ascent, space, star, creak, iris, reconnect, lens, gate, front, inhale, after)
  are cut to the previz's shot lengths; `proxima_front` is FRONT to HUSH (8 s) and `proxima_inhale` 0.65 s, so they can
  replace the borrowed `luna_rumble`/`luna_inhale` in ProximaFx once wired.

## Halley (SA-01)

| Event | When | What it should be | Layers to look for |
|---|---|---|---|
| halley_mark | the target locks | a sharp sci-fi lock-on | UI sci-fi ping, glassy snap |
| halley_countdown | each countdown step | a tight tick with a sub thump | UI arp/ping, low thump |
| halley_sight | the comet seen | a cold shimmering reveal | ice shimmer, magic light, riser tail |
| halley_approach | the comet coming in | a rising icy roar | wind rush, whoosh, riser |
| halley_boom | the sonic boom | a hard double boom | trailer boom, thunder crack |
| halley_touchdown | it touches down | a heavy crunching hit | trailer boom, ice crush, debris |
| halley_plough | ploughing the trench | a grinding, crunching rumble | ice field cracking, debris, low rumble |
| halley_impact | the impact | the biggest hit: sub drop, crack, rolling tail | bass drop, trailer boom, ice shatter, debris |
| halley_frost | frost spreading | crackling ice | ice cracks, fissures |
| halley_hum (x3) | the comet's hum | an icy, tonal hum | sci-fi drone, metal bowed tonal |
| halley_evac | the evac lift | a quick whoosh | wind whoosh |
| halley_dusk | the sky darkening | a low swell | dark drone, braam (soft) |
| halley_burst (x3) | ice bursts | short icy shatters | ice break, glass shards |

## Luna (SA-02)

| Event | When | What it should be | Layers to look for |
|---|---|---|---|
| luna_uplink | the remote's uplink | a sci-fi confirm | UI sci-fi, radio |
| luna_alarm | EARTH SYSTEM SHUT DOWN | an ominous alarm | trailer alarms |
| luna_buildup | alarm to the fall | a rising rumble and drone | riser, braam, low rumble |
| luna_crack | the moon cracks | a huge rock crack | ice/rock crack pitched down, metal stress |
| luna_tear | it tears loose | a groaning tear | moaning metal, rock grind, sub |
| luna_fall | the fall | a deep roaring descent | wind, rumble, low whoosh |
| luna_entry | it hits the air | a fiery boom | fire whoosh, trailer boom |
| luna_rumble | before the impact | a swelling rumble into near silence | rumble, sub swell |
| luna_inhale | the air drawn in | a reversed rush | reversed whoosh/boom |
| luna_impact | the impact | the biggest hit | bass drop, trailer booms, debris |
| luna_shockwave | the shock wave | a rolling blast | explosion tail, wind blast |
| luna_settle | it settles | grinding and groaning | moaning metal, rock grind |
| luna_aftershock (x2) | aftershocks | deep rumbles | thunder rumble, sub |
| luna_sizzle | the molten seam | hissing and spitting | fire crackle, steam |
| luna_debris (x3) | falling debris | pattering rocks | debris whoosh, rubble |

## Proxima (SA-04A Flare; the previz film)

| Event | When | What it should be | Layers to look for |
|---|---|---|---|
| proxima_uplink | the press | a low uplink hum under the remote's press | electric hum, sci-fi drone |
| proxima_ascent | the zoom-out | wind rising and rushing, cut to near silence | wind rush, whoosh |
| proxima_space | space shots | a deep sub drone | sci-fi drones, dark ambience |
| proxima_star | the star in its shell | a huge muffled roar | fire roar pitched down, storm |
| proxima_creak | the shell's panels | metal creaks and ticks | moaning metal, latches |
| proxima_iris | the iris opening | servos and locking clamps, one clank a ring | servo whine, mechanism thunks, metal hits |
| proxima_reconnect | the loops | crackle rising to a snap | electric arcs, zaps |
| proxima_lens | the lens assembling | panels thudding into place | metal impacts, latches |
| proxima_beam | the lens firing | a charge whine and a thunderous discharge | arc power-up, bass drop, trailer boom |
| proxima_gate | the gate catching the beam | a far whoosh and a resonant hum | whoosh, tonal drone |
| proxima_flash | the flash | a searing rush with crackling fire, a low thump | fire whoosh, crackle, sub |
| proxima_radio | the radio burst | a whine falling in pitch (keep the current one, cleaned) | radio static, AM sweep |
| proxima_front | the front coming down | a rolling roar building | storm wind, rumble |
| proxima_inhale | before the impact | the air drawn in | reversed whoosh |
| proxima_impact | the impact | sub drop, crack, roaring tail, plasma sizzle | bass drop, trailer boom, fire, electric |
| proxima_wave | the wave passing | a roar coming on high and leaving low | wind blast, fire whoosh, debris |
| proxima_after | afterwards | wind returning, sizzling glass | wind, fire crackle, ice/glass ticks |

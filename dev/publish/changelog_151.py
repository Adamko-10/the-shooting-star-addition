#!/usr/bin/env python3
"""The 1.5.1 changelog for players (everything since 1.4.0: 1.5.0 was only ever on GitHub), one per branch (GitHub release notes, reused for CurseForge).

Writes <out>/<branch>.md (default ~/.tssa-ci/publish/1.5.1/, or the folder given as the first argument)."""
import os
import sys

OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.expanduser('~/.tssa-ci/publish/1.5.1/')
HEAD = {
    'main': "**For Minecraft 1.21.1 with NeoForge 21.1.x.** Goes on the server and on every client.",
    'forge-1.21.1': "**For Minecraft 1.21.1 with Forge 52.1+.** Goes on the server and on every client.",
    'forge-1.20.1': "This jar runs on both **Forge and NeoForge 1.20.1**. Goes on the server and on every client.",
    'fabric-26.3': "**For Minecraft 26.3 with Fabric Loader 0.19.5+ and Fabric API.** Goes on the server and on every client.",
    'fabric-1.21.11': "**For Minecraft 1.21.11 with Fabric Loader 0.17.0+ and Fabric API.** Goes on the server and on every client.",
    'fabric-1.21.1': "**For Minecraft 1.21.1 with Fabric Loader 0.17.0+ and Fabric API.** Goes on the server and on every client.",
    'fabric-1.20.1': "**For Minecraft 1.20.1 with Fabric Loader 0.17.0+ and Fabric API.** Goes on the server and on every client.",
}
BODY = """
Everything since 1.4.0 (1.5.0 was an early build on GitHub only; this is it, finished after testing).

### Fixed
- **Luna's and Halley's cutscenes work again.** With The Shooting Star 1.4.3 your own cutscene only played if you had "Play for everyone" on, and since 1.4.0 the first shot of each film could put the camera far off in unloaded land (the void, chunks popping in). Both came from how the addon finds its way round The Shooting Star's scrambled code, and both are fixed on every loader.
- **No cooldown works.** Halley and Luna no longer refuse a second cast while one is still coming down, and switching cooldowns off for one player no longer touches anyone else's.
- **The moon no longer shrinks as it lands.** It was drawn bigger than it really is on the way down and snapped back at the impact; now it grows steadily to its true size.
- **The moon and the clouds.** Looking up at it, Minecraft's clouds could cut the moon down to the shapes of the clouds. Now it is properly behind them while it's above them (you see it between the clouds) and in front of them once it comes down through them.
- **The M and C keys in the remote's menu work.** They only worked while Shift was held.
- **The moon keeps its size whatever the power slider says.** It used to be drawn at a made-up size that could jump between camera angles and even shrink on the way down at low power; it's now always drawn at its true size, starting as big as the moon in the sky.
- **Luna's impact lags far less.** The rim heaving up and the cracks running out used to make the server re-send most of the crater's chunks to every player every tick for seconds; each chunk is now rebuilt only a few times, like The Shooting Star's own skills do.

### Luna
- **A longer approach**: the moon tears loose two seconds in and comes on steadily, visibly growing, with **a trail of moon dust** behind it; then it hits the atmosphere and **dives, faster and faster**, with **a blazing trail**, a bow shock and flames peeling off it, and lands 16.5 seconds after the button, the same moment Halley's comet does.
- **The music**: cut fresh from the original track (no more stretching), starting with the alarm so the drop lands the moment it hits. It keeps itself in step with the moon, even when the game stutters, and follows your **Music** volume slider.
- **A smoother landing**: when the moon comes to rest it crumbles into its blocks patch by patch, glowing and steaming as it cools, instead of swapping all at once.
- **A new cutscene**: the uplink, a long lens on the moon cracking open, low on the land as it starts to fall, a slow orbit as it grows, tracking it through entry, the moon looming over you, then **the impact in slow motion** while the camera pulls back over the fireball and the wall of dust, the crater from above, and the moon at rest. The camera always stays over land your game has loaded.
- **A new film overlay**: cinematic bars, each phase typed out as it begins, a descent bar counting down to the impact, telemetry, and a high-speed camera tag during the slow motion.
- **The moon heats up as it comes**: the pale moon with glowing seams while it comes on; through entry its leading face turns molten, its seams blaze and a shell of fire wraps its edge, while its craters stay visible. Its surface is rugged as it falls. It also **looks right with shader packs** now (solid and bright instead of see-through).
- **Lighter on the server**: Luna only ever loads the land round the moon itself. The rest of the crater, the rim and the cracks are cut wherever land is already loaded, and anywhere else as soon as that land loads. No new land is ever generated for it, and the rim and cracks are built a chunk at a time with far less bookkeeping.

### The remote's menu (H)
- **A plate in the corner with two switches**, in creative and survival: **M** turns Luna's music on or off, **C** turns no cooldown on or off for you (where the server allows it). It also shows what the server allows.

### No cooldown (optional, off by default)
- `no_cooldown = true` (in `[stellar_remote]` of `config/shooting_star_addition-common.toml`) now lets players switch off the cooldown of every skill on the Stellar Remote, The Shooting Star's own included. Each player switches it for themselves in the menu (**C**); it's on until they do.
- **New:** `no_cooldown_survival` (false): whether survival players may use it too. Otherwise it's for creative and spectator players only.
- Players are told in chat as they join while it's on (and operators while it's off, with where to switch it on).

### Also
- Works with **The Shooting Star [Demo] 1.3.3, 1.3.4, 1.4.0, 1.4.1, 1.4.2, 1.4.3 and 1.4.4** (and 1.4.0A): one jar for all of them.
"""
EXTRA = {
    'fabric-26.3': "\nNote for Fabric 26.3: use The Shooting Star **1.4.0A, 1.4.1, 1.4.2, 1.4.3 or 1.4.4**. The first 1.4.0 file can't load worlds on 26.3 (a bug in The Shooting Star itself, fixed in 1.4.0A).\n",
}
os.makedirs(OUT, exist_ok=True)
for b, head in HEAD.items():
    text = head + "\n" + BODY + EXTRA.get(b, '')
    assert '](' not in text
    open(os.path.join(OUT, b + '.md'), 'w', encoding='utf-8', newline='\n').write(text.rstrip('\n') + '\n')
    print(b, len(text))

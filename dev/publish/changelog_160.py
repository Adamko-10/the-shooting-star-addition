#!/usr/bin/env python3
"""The 1.6.0 changelog for players (The Shooting Star 1.5.0 support), one per branch (GitHub release notes, reused for CurseForge).

Writes <out>/<branch>.md (default ~/.tssa-ci/publish/1.6.0/, or the folder given as the first argument)."""
import os
import sys

OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.expanduser('~/.tssa-ci/publish/1.6.0/')
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
### Works with The Shooting Star 1.5.0
- **Updated for The Shooting Star [Demo] 1.5.0.** It moved its skills, the remote's menu and its cutscenes into a new library mod, **RimuLib**, and changed how its spells run. Halley and Luna now join the Stellar Remote through RimuLib, and run their strikes themselves.
- **You now need RimuLib** (1.1.5 or newer), which The Shooting Star 1.5.0 needs anyway.
- Halley and Luna show in the remote's menu with RimuLib's marker for skills from another mod.
- **This version needs The Shooting Star 1.5.0.** On 1.4.4 or older, keep using The Shooting Star Addition 1.5.1.

### Changed
- The Stellar Remote in your hand no longer opens and counts down for Halley's and Luna's strikes (The Shooting Star 1.5.0 has no way in for that any more). Its own skills still animate it.
- Players near a strike who aren't watching its film no longer get the skill's title card on screen.
- A caster standing where the strike lands is now set down just outside the crater, falling slowly, instead of being lifted with The Shooting Star's teleport (which 1.5.0 no longer offers).

Everything else (both skills, their films, music, sliders, no cooldown and the server's limits) works as in 1.5.1.
"""
EXTRA = {
    'fabric-26.3': "\nNote for Fabric 26.3: as of 1.6.0's release, The Shooting Star 1.5.0's own Fabric 26.3 build doesn't start: two of its own shaders fail to load, a problem in The Shooting Star itself. This version is ready for when The Shooting Star fixes that.\n",
}
os.makedirs(OUT, exist_ok=True)
for b, head in HEAD.items():
    text = head + "\n" + BODY + EXTRA.get(b, '')
    assert '](' not in text
    open(os.path.join(OUT, b + '.md'), 'w', encoding='utf-8', newline='\n').write(text.rstrip('\n') + '\n')
    print(b, len(text))

#!/usr/bin/env python3
"""The 1.4.0 changelog for players, one per branch (GitHub release notes, reused for CurseForge)."""
OUT = '/home/claude/work/publish/1.4.0/'
HEAD = {
    'main': "**For Minecraft 1.21.1 with NeoForge 21.1.x.** Goes on the server and on every client.",
    'forge-1.21.1': "**For Minecraft 1.21.1 with Forge 52.1+.** Goes on the server and on every client.",
    'forge-1.20.1': "This jar runs on both **Forge and NeoForge 1.20.1**. Goes on the server and on every client.",
    'fabric-26.3': "**For Minecraft 26.3 with Fabric Loader 0.19.5+ and Fabric API.** Goes on the server and on every client.",
    'fabric-1.21.11': "**For Minecraft 1.21.11 with Fabric Loader 0.17.0+ and Fabric API.** Goes on the server and on every client.",
    'fabric-1.21.1': "**For Minecraft 1.21.1 with Fabric Loader 0.17.0+ and Fabric API.** Goes on the server and on every client. New: the first Fabric version for 1.21.1.",
    'fabric-1.20.1': "**For Minecraft 1.20.1 with Fabric Loader 0.17.0+ and Fabric API.** Goes on the server and on every client. New: the first Fabric version for 1.20.1.",
}
BODY = """
### Sliders for Halley and Luna, like the original's
**Halley and Luna now have The Shooting Star's own three sliders** under them in the remote's menu (**H**), and they work like the original skills' do:
- **Power:** the whole strike from **0.1x to 10x** as big. For Halley that's the comet, its trench and the crater; for Luna, the moon and its crater. The cutscene scales with it.
- **Damage:** each hit from **2 up to infinity**. At infinity (the default), whatever it hits is erased, as before.
- **Speed:** the whole skill, cutscene and all, from **0.3x to 3x** as fast.
- Big strikes are heavy on the game: with The Shooting Star 1.4.2 or newer, the menu warns you above 3x for Halley and 2x for Luna.

### Server settings for the sliders
New in the `[stellar_remote]` section of `config/shooting_star_addition-common.toml`, for every skill on the remote, The Shooting Star's own included:
- `max_power`: the most Power anyone can cast with, as a multiple of each skill's default (0, the default, means no limit).
- `max_damage`: the most Damage per hit; it also holds casts set to infinity to that number (0 means no limit).
- `max_speed`: the fastest Speed allowed (3, the default, means no limit).
- And for Halley, `chunks_per_tick` in `[impact]` (12): how many chunks of the trench are cut each tick (the crater does twice as many). Lower it on a busy server.

### Luna, reworked
- **A real impact crater.** The moon now blasts a wide bowl out of the land, more than twice its own width, and drives deep into it: most of it ends up buried. The crater's floor is burnt black and still glowing near the moon, with veins of molten rock across it, a band of lava where moon and floor meet, and a ragged rim of upturned crust at its edge.
- **Bigger and more destructive:** the crust buckles up against the moon, fissures tear out across the land, lumps of moon and crust rain down far and wide, and smoke rises off the crater while it glows.
- **Faster and harder:** the moon tears loose and comes down sooner (it hits about 17 seconds after the button instead of 24) and slams in with jets of fire, a wall of dust racing out over the land and a mushroom cloud rising over it.
- **Music:** Luna now has her own track. It starts as the moon tears loose and its drop lands on the impact (it follows the Speed slider too). It plays under your **Music** volume.
- **Shorter ending:** the cutscene now ends about 9 seconds sooner, with one shot fewer. The molten rock hisses once instead of over and over.
- **New sounds:** the uplink locking on, the moon tearing loose from the sky, the ground rumbling, the shock wave, the moon grinding into the earth, aftershocks and molten rock hissing.
- **New crater settings:** `impact_radius` (110), `rim_height` (9) and `buried_percent` (70) replace `crater_radius`, `crater_depth` and `sink_percent`, so an old config file gets the new look by itself.

### Moon cheese
- **New texture**, and **holey moon cheese**: the cheese's holes are now a block of their own, scattered all through the fallen moon and its debris.
- **Any moon cheese clears your effects, like milk.** Molten moon cheese then gives its own effects as before, and now also fills your hunger bar and saturation to the top.

### No cooldown (optional, off by default)
- Set `no_cooldown = true` in the new `[stellar_remote]` section of `config/shooting_star_addition-common.toml`, and every skill on the Stellar Remote, The Shooting Star's own included, can be cast again straight away, for every player. The remote's menu (**H**) shows a note in its corner saying whether it's on. Be careful with this!

### Also
- **New codenames: SA-01 · Halley and SA-02 · Luna.** They're a series of their own now, so they never clash with The Shooting Star's SS numbers, whatever it adds. KEYS_LINE
- **Halley's cutscene ends sooner** too, with one shot fewer.
- **Tungsten:** The Shooting Star's Gungnir hull now counts as a tungsten block (the common `tungsten` tag), so other mods that use tungsten can use it.
- **Works with The Shooting Star [Demo] 1.3.3, 1.3.4, 1.4.0, 1.4.1, 1.4.2 and the new 1.4.3** (and 1.4.0A): one jar for all of them.
- **New Fabric versions for Minecraft 1.21.1 and 1.20.1.** The addon now comes for NeoForge 1.21.1, Forge 1.21.1, Forge and NeoForge 1.20.1, and Fabric 26.3, 1.21.11, 1.21.1 and 1.20.1.
"""
EXTRA = {
    'forge-1.20.1': "- **Fixed:** the comet's and the sky's glow could show up as black boxes on 1.20.1 without a shader pack.\n",
    'fabric-1.20.1': "",
    'fabric-26.3': "\nNote for Fabric 26.3: use The Shooting Star **1.4.0A, 1.4.1, 1.4.2 or 1.4.3**. The first 1.4.0 file can't load worlds on 26.3 (a bug in The Shooting Star itself, fixed in 1.4.0A).\n",
}
KEYS = "Same keys as before: Halley on **O**, Luna on **N**."
KEYS_263 = "Halley's default key is now **G** (Minecraft 26.3 uses O for its Friends list); Luna stays on **N**. Keys can be changed in Controls or from the remote's menu."
for b, head in HEAD.items():
    text = head + "\n" + BODY.replace('KEYS_LINE', KEYS_263 if b == 'fabric-26.3' else KEYS) + EXTRA.get(b, '')
    assert '](' not in text
    open(OUT + b + '.md', 'w', encoding='utf-8').write(text.rstrip('\n') + '\n')
    print(b, len(text))

# The Shooting Star Addition

An **unofficial**, fan-made addon for **[The Shooting Star \[Demo\]](https://www.curseforge.com/minecraft/mc-mods/the-shooting-star-demo)** by rimuru\_dev. It adds two skills to the Stellar Remote: **SA-01 · Halley**, a comet called down onto your crosshair, and **SA-02 · Luna**, which brings the moon itself down.

### My discord server is now open! join for leaks on upcoming updates and projects!

### [https://discord.gg/QHqVJWsbJN](https://discord.gg/QHqVJWsbJN)

### Thank you for your support

### Consider Supporting my youtube channel, any help is VERY appreciated 🙏 [https://www.youtube.com/@The\_Game\_Explainer](https://www.youtube.com/@The_Game_Explainer/?sub_confirmation=1)

### Check out my other mod: [https://www.curseforge.com/minecraft/mc-mods/etch-gate](https://www.curseforge.com/minecraft/mc-mods/etch-gate)

## ⚠️ Compatibility — please read

This addon works with **The Shooting Star \[Demo\] 1.3.3, 1.3.4, 1.4.0, 1.4.1, 1.4.2 and 1.4.3** (and 1.4.0A). One jar covers all of them, so you can update the two mods in either order. Older versions of The Shooting Star are not supported.

### Available builds

Download the build that matches your Minecraft version and mod loader:

*   **NeoForge 1.21.1**
*   **Forge 1.21.1**
*   **Forge 1.20.1** (also runs on NeoForge 1.20.1)
*   **Fabric 26.3**
*   **Fabric 1.21.11**
*   **Fabric 1.21.1**
*   **Fabric 1.20.1**

The Fabric builds also need **Fabric API**.

Like the original mod's skills, Halley and Luna **permanently change the land**. Back up worlds you care about.

## 🎚️ Sliders, like the original's

Halley and Luna have the same three sliders as The Shooting Star's own skills, under them in the remote's menu (**H**):

*   **Power**: the whole strike from **0.1× to 10×** as big. For Halley that's the comet, its trench and the crater; for Luna, the moon and its crater. The cutscene scales with it.
*   **Damage**: each hit from **2 up to ∞**. At ∞ (the default), whatever it hits is erased.
*   **Speed**: the whole skill, cutscene and all, from **0.3× to 3×** as fast.

Big strikes are heavy on the game. With The Shooting Star 1.4.2 or newer, the menu warns you above 3× for Halley and above 2× for Luna.

**Server owners can cap the sliders** for every skill on the remote (The Shooting Star's own too): `max_power`, `max_damage` and `max_speed` in the `[stellar_remote]` section of `config/shooting_star_addition-common.toml`.

## SA-01 · Halley

Flip the cover and press the button. Your crosshair is marked in ice-blue and an approach corridor is projected across the land beyond it. A comet rises over the horizon with its tails stretched across the sky, hits the atmosphere as a fireball, touches down at the far end of the corridor, ploughs a frozen trench back toward you and detonates on the mark. It leaves an icy crater with the comet's glowing heart standing in it, and rays of frost thrown out over the land round it.

*   **The sky goes dark as it comes in**: stars, the band of the galaxy and an aurora come out, and the comet blazes across them with its tails, lighting up the air. The fireball outshines the stars as it comes down and the blast lights up the night. Then the air fills with ice: a pale haze with a halo, sun dogs and a light pillar round the sun or the moon.
*   **You can't miss it**: outside the cutscene the comet is drawn bigger, and an arrow at the edge of the screen points at it while it's out of view.
*   Pieces break off the comet and burst in the sky, the fireball sheds sparks and lens flares, ice is thrown out of the crater on long arcs, glowing cracks race across the land, a wall of snow rides the shock front, and the comet heart shines a beam into the sky.
*   Joins the Stellar Remote like the built-in skills: its own key (**O** by default, **G** on Fabric 26.3), menu card, sliders, description, HUD card and cooldown.
*   Its own cutscene (skippable with the remote's cutscene key), with impact frames, flashes and camera shake in the remote's style. The remote's cover, button and screen animate for it too.
*   Thirteen new sounds, and two new blocks: the **Comet Heart** and the **Frozen Comet Trail**.
*   Everything in the trench and the crater is erased, like the remote's other skills erase their strike zones. That includes you if you stand there; in creative you are lifted clear. Past the crater, the shock wave throws, hurts and freezes whatever it meets.

## SA-02 · Luna

Flip the cover and press the button: **EARTH SYSTEM SHUT DOWN** is slammed across the screen and the sky falls to a red night. The moon, right where it hangs in your sky, shudders and cracks open along glowing molten seams. Then it tears loose and falls onto your crosshair, slowly at first and then faster and faster, burning through the air until it fills the sky.

*   **The impact**: a white flash, jets of fire, a wall of dust racing out over the land and a mushroom cloud rising over it.
*   **A real crater**: the moon blasts a bowl more than twice its own width out of the land and drives deep into it, with most of it buried. The crater floor is burnt black and still glowing near the moon, with veins of molten rock across it, a band of lava where moon and floor meet, and a ragged rim of upturned crust at its edge.
*   Fissures tear out across the land, lumps of moon and crust rain down far and wide, and the shock wave throws, burns and hurts whatever it meets past the crater.
*   **Its own music**: it starts as the moon tears loose, and the drop lands on the impact (under your Music volume).
*   Works by day or night (only the sky you see changes, never the world's time), but not where there is no moon, like the Nether.
*   Its own key (**N** by default), menu card, sliders, cooldown (120 s), cutscene (skippable), sounds and alarm.
*   Everything in the crater is erased, you too if you stand there (in creative you are lifted clear).

### Moon cheese

The fallen moon is made of it.

*   **Moon cheese** and **holey moon cheese** (the cheese's holes, scattered all through the moon): right-click a block to place it, right-click the air to eat it. It's as good as a golden carrot, and being cheese, it clears your effects like milk.
*   **Molten moon cheese**, one block at the moon's very centre: it gives **500 hearts** and **Strength 255** for 10 minutes (Molten Might), and fills your hunger bar and saturation to the top.

## ⏱️ No cooldown (optional)

Off by default. Set `no_cooldown = true` in the `[stellar_remote]` section of `config/shooting_star_addition-common.toml`, and every skill on the Stellar Remote can be cast again straight away, for every player. That includes The Shooting Star's own skills. The remote's menu (**H**) shows whether it's on. Be careful with this!

## ✨ Shaders and other mods

*   **Works with shader packs** (Iris), tested with **Complementary Reimagined**. With a pack on, Halley switches to a way the pack can show, so the comet, its light and all its effects show up. The dark sky uses the pack's own night: the sky races through a dusk as the comet comes in, and back through a dawn after the impact. Only what you see changes, never the world's actual time.
*   Without a shader pack, Halley draws its own night sky.
*   Tested together with **Sodium** and **ImmediatelyFast**.
*   **Distant Horizons**: not tested yet.

## Settings

*   `config/shooting_star_addition-common.toml` (the server's, sent with every strike): both skills' sizes, blast reach and cooldowns, whether they carve the land, Halley's comet heart and frost rays, Luna's molten core, the moon cheese's effects, how many chunks each skill cuts per tick, `no_cooldown`, and the slider limits.
*   `config/shooting_star_addition-client.toml` (each player's own): Halley's dark sky, the dimmed land, the extra effects, the world-lighting flashes (turn them off if flashing light bothers you), the comet arrow and how it's drawn under shader packs.

## Requirements

1.  Minecraft **1.21.1** (NeoForge, Forge or Fabric), **1.20.1** (Forge, NeoForge or Fabric), **1.21.11** (Fabric) or **26.3** (Fabric).
2.  **The Shooting Star \[Demo\] 1.3.3 to 1.4.3**, the build for your Minecraft version and mod loader. On Fabric, also **Fabric API**.
3.  Install on the **server and every client**.

In game: hold the Stellar Remote, press **O** for Halley (**G** on Fabric 26.3) or **N** for Luna (or pick one in the remote's menu with **H**), aim at the ground and right-click. The keys can be changed in Controls or from the remote's menu.

## Will it work with future versions of The Shooting Star?

Not without an update of this addon. The Shooting Star has no official way for other mods to add skills, so this addon attaches itself to its internals, and since 1.4.0 those change names with every release. The addon carries a separate attachment for each version of The Shooting Star it knows. That means:

*   **When The Shooting Star updates, wait for the next update of this addon.** It adds the new version and keeps the old ones.
*   **It won't crash your game.** With a version it doesn't know, the game says so when it starts. If something it attaches to has really changed, it switches its skills off, logs why and tells server operators in chat.
*   **If The Shooting Star changes its mod id** (for example when it stops being a demo), this addon won't load until it is updated.
*   **Other mod loaders and Minecraft versions not listed above** are not supported yet. Each needs its own build.

## Plans

This is version **1.4.0**. More skills for the Stellar Remote are planned, along with balancing, polish and changes based on your feedback. Comments, bug reports and ideas are very welcome!

## Credits

The Shooting Star and the Stellar Remote are by **rimuru\_dev**. This addon is **not affiliated with or endorsed by** them, and contains none of the original mod's code or assets — you need the original mod installed for it to do anything.

Source code and bug reports: [github.com/Adamko-10/the-shooting-star-addition](https://github.com/Adamko-10/the-shooting-star-addition)

You are free to use this mod in modpacks but, please do not distribute or reupload this mod on other websites without my consent, CurseForge is the only mod hoster/distributor this mod will be on.

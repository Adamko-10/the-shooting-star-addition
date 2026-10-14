# The Shooting Star Addition

An **unofficial**, fan-made addon for **[The Shooting Star [Demo]](https://www.curseforge.com/minecraft/mc-mods/the-shooting-star-demo)** by rimuru_dev. It adds a new skill to the Stellar Remote: **SS-05 · Halley**, a comet called down onto your crosshair.

## ⚠️ Compatibility — please read

This addon was made for **The Shooting Star [Demo] 1.2.2** on **NeoForge, Minecraft 1.21.1**, and it also works with **The Shooting Star 1.3**.

The Shooting Star itself is also available for Forge, Fabric and other Minecraft versions — **this addon is not**. It only works on **NeoForge 1.21.1**, and future versions of The Shooting Star may need an update of this addon. Please check you have the NeoForge 1.21.1 file of The Shooting Star (1.2.2 or 1.3) before reporting a problem.

Like the original mod's skills, SS-05 **permanently changes the land**. Back up worlds you care about.

## SS-05 · Halley

Flip the cover and press the button. Your crosshair is marked in ice-blue and an approach corridor is projected across the land beyond it. A comet rises over the horizon with its tails stretched across the sky, hits the atmosphere as a fireball, touches down at the far end of the corridor, ploughs a frozen trench back toward you and detonates on the mark — leaving an icy crater with the comet's glowing heart standing in it, and rays of frost thrown out over the land round it.

- **The sky goes dark as it comes in**: stars, the band of the galaxy and an aurora come out, and the comet blazes across them with its tails, lighting up the air. The fireball outshines the stars as it comes down, the blast lights up the night — and then the air fills with ice: a pale haze with a halo, sun dogs and a light pillar round the sun or the moon.
- **You can't miss it**: outside the cutscene the comet is drawn bigger, and an arrow at the edge of the screen points at it while it's out of view.
- **Works with shader packs** (Iris, e.g. Complementary): the comet, its light and the sky show up under shaders too — and the dark sky becomes the pack's own night, racing through a dusk as the comet comes in and back through a dawn after the impact (only what you see changes, never the world's time).
- Pieces break off the comet and burst in the sky, the fireball sheds sparks and lens flares, ice is thrown out of the crater on long arcs, glowing cracks race across the land, a wall of snow rides the shock front, and the comet heart shines a beam into the sky.
- Joins the Stellar Remote like the built-in skills: its own key (**O** by default), menu card and description, HUD card and cooldown.
- Its own cutscene (skippable with the remote's cutscene key), with impact frames, flashes and camera shake in the remote's style — and the remote's cover, button and screen animate for it.
- Thirteen new sounds, and two new blocks: the **Comet Heart** and the **Frozen Comet Trail**.
- Everything in the trench and the crater is erased, the way the remote's other skills erase their strike zones (you too if you stand there — in creative you are lifted clear). Past the crater, the shock wave throws, hurts and freezes whatever it meets.
- Configurable in `config/shooting_star_addition-common.toml`: trench and crater size, blast reach, cooldown, and whether it carves the land, leaves the comet heart and paints frost rays.
- Each player can tune the looks in `config/shooting_star_addition-client.toml`: the dark sky, the dimmed land, the extra effects, the world-lighting flashes (off if flashing light bothers you), the comet arrow and how it's drawn under shader packs.

## Requirements

1. Minecraft **1.21.1** with **NeoForge 21.1.x**
2. **The Shooting Star [Demo] 1.2.2 or 1.3** (its NeoForge 1.21.1 file)
3. Install on the **server and every client**

In game: hold the Stellar Remote, press **O** (or pick SS-05 in the remote's menu with **H**), aim at the ground and right-click.

## Will it work with future versions of The Shooting Star?

Honestly: maybe, maybe not. The Shooting Star has no official way for other mods to add skills, so this addon attaches itself to some of its internals — and The Shooting Star is updated often. That means:

- **Small updates** that don't touch those parts should keep working — the addon accepts The Shooting Star 1.2 and anything newer, and 1.3 works.
- **Updates that change those parts won't crash your game.** The addon checks everything before it attaches; if something has moved, it switches SS-05 off, logs why and tells server operators in chat, until this addon is updated. Its optional touches (the remote's cover animation, the cutscene overlay, the raised arm) quietly switch themselves off instead.
- **If The Shooting Star changes its mod id** (for example when it stops being a demo), this addon won't load until it is updated.
- **Other mod loaders** (Forge, Fabric) and **other Minecraft versions** are not supported — each would need its own port.

## Plans

This is version 1.1. **If people enjoy it and there's support for it, I'll keep it updated** for new versions of The Shooting Star and keep improving it: more skills for the Stellar Remote, balancing and polish from your feedback, and versions for other loaders and Minecraft versions if there's demand. Comments, bug reports and ideas are very welcome!

## Credits

The Shooting Star and the Stellar Remote are by **rimuru_dev**. This addon is **not affiliated with or endorsed by** them, and contains none of the original mod's code or assets — you need the original mod installed for it to do anything.

Source code and bug reports: [github.com/Adamko-10/the-shooting-star-addition](https://github.com/Adamko-10/the-shooting-star-addition)

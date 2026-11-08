#!/usr/bin/env node
// The 1.8.0 changelog for players, one per branch (GitHub release notes, reused for CurseForge).
// node dev/publish/changelog_180.js [out dir]   writes <out>/<branch>.md (default ~/.tssa-ci/publish/1.8.0/)
// In Markdown changelogs, never a "(" right after "]" (CLAUDE.md §0): checked below.
const fs = require("fs");
const os = require("os");
const path = require("path");

const OUT = process.argv[2] || path.join(os.homedir(), ".tssa-ci", "publish", "1.8.0");

const HEAD = {
  "main": "**For Minecraft 1.21.1 with NeoForge 21.1.x.** Goes on the server and on every client.",
  "forge-1.21.1": "**For Minecraft 1.21.1 with Forge 52.1+.** Goes on the server and on every client.",
  "forge-1.20.1": "This jar runs on both **Forge and NeoForge 1.20.1**. Goes on the server and on every client.",
  "fabric-26.3": "**For Minecraft 26.3 with Fabric Loader 0.19.5+ and Fabric API.** Goes on the server and on every client.",
  "fabric-1.21.11": "**For Minecraft 1.21.11 with Fabric Loader 0.17.0+ and Fabric API.** Goes on the server and on every client.",
  "fabric-1.21.1": "**For Minecraft 1.21.1 with Fabric Loader 0.17.0+ and Fabric API.** Goes on the server and on every client.",
  "fabric-1.20.1": "**For Minecraft 1.20.1 with Fabric Loader 0.17.0+ and Fabric API.** Goes on the server and on every client.",
};
// Proxima's default skill key: Z everywhere (check fabric-26.3's port report: Z is the plate's Epic-mode key there)
const KEY = b => "Z";

const body = b => `
### New: SA-04 · Proxima
- **A new remote skill (default key ${KEY(b)})**: Proxima Centauri, a red dwarf inside a Dyson sphere 4.2 light years away, charges a giant lens and fires a solar flare at Earth. A gate high above the land catches the shot and focuses it onto your crosshair.
- **Flash burn**: the sky turns white-red, leaves and plants burn, shallow water steams, and exposed players and mobs catch fire.
- **The coronal front** sweeps across the sky, then a hush, then **ground zero**: a wide, shallow, glassy bowl at the mark (36 blocks out from the middle, 6 deep), charred trees and ground round it, a scorched zone out to 220 blocks, and a slow shock wave rolling out to the horizon. Damage falls off from the centre outward.
- **Shelter works**: three solid blocks over your head (configurable) keep you safe from the burn. Underground you still feel the boom and the rumble.
- **Space weather**: compasses spin, redstone sparks, a radio burst, and auroras for a while afterwards.
- **Aftermath**: **Solar Residue** (a glowing block left in the bowl, minable), **Flare Ash** over the scorched land, and a **radiation zone** in the crater that hurts whoever stands in it and fades after about 10 minutes (or stays, if the server wants).
- **Its own 36-second film**: from your shoulder up through the clouds and out of the world, a warp to Proxima Centauri, the Dyson sphere opening, the lens charging for three seconds, the shot crossing to Earth, the gate catching it, and back on the land for the flash, the front and the shock wave. Title cards, captions and a readout that glitches as the front hits.
- **Everyone else sees the gate in the sky** above the land, charging, catching the shot and beaming it down.
- **Power sets the flare's class**, like real ones: 0.1x is about C1, 1x M5, 3x X3, 10x X25.
- **Its own music**, by **DOM_MusicST** and **FarzanNB**.
- **Random solar flares** (off by default): servers can turn on flares that arrive by themselves, announced in chat first so players can find shelter: minor (a flash in the sky), major (burns what's outside), or a full cataclysm if the server allows it. Operators can call one with \`/ssa flare <minor|major|cataclysm> [seconds]\`.
- **Server settings** in the \`[proxima]\` sections of the common config: on/off, cooldown, sizes, terrain changes, residue, shelter depth, knockback, radiation time and damage, random flares.
- Proxima can be ticked in **Stellar Convergence** too, timed to land with the others.
- Earth from space uses NASA's Blue Marble and Black Marble imagery and textures from Solar System Scope (CC BY 4.0).

### New in the remote's menu (H)
- **=** : music stacking. Off by default: casting a skill again while its music plays no longer starts a second copy on top.
- **-** : kilometres or miles for the films' readouts.
- The addon's panel is a little smaller.

### Fixed
- With RimuLib's legacy film style on, none of the addon's film overlays showed (Halley's uplink, Luna's letterbox, captions and descent bar).
`;

fs.mkdirSync(OUT, { recursive: true });
for (const [b, head] of Object.entries(HEAD)) {
  const text = (head + "\n" + body(b)).replace(/\n+$/, "") + "\n";
  if (text.includes("](")) throw new Error(b + ": a \"(\" right after \"]\"");
  fs.writeFileSync(path.join(OUT, b + ".md"), text, "utf8");
  console.log(b, text.length);
}

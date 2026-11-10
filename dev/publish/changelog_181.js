#!/usr/bin/env node
// The 1.8.1 changelog for players, one per branch (GitHub release notes, reused for CurseForge).
// node dev/publish/changelog_181.js [out dir]   writes <out>/<branch>.md (default ~/.tssa-ci/publish/1.8.1/)
// In Markdown changelogs, never a "(" right after "]" (CLAUDE.md §0): checked below.
const fs = require("fs");
const os = require("os");
const path = require("path");

const OUT = process.argv[2] || path.join(os.homedir(), ".tssa-ci", "publish", "1.8.1");

const HEAD = {
  "main": "**For Minecraft 1.21.1 with NeoForge 21.1.x.** Goes on the server and on every client.",
  "forge-1.21.1": "**For Minecraft 1.21.1 with Forge 52.1+.** Goes on the server and on every client.",
  "forge-1.20.1": "This jar runs on both **Forge and NeoForge 1.20.1**. Goes on the server and on every client.",
  "fabric-26.3": "**For Minecraft 26.3 with Fabric Loader 0.19.5+ and Fabric API.** Goes on the server and on every client.",
  "fabric-1.21.11": "**For Minecraft 1.21.11 with Fabric Loader 0.17.0+ and Fabric API.** Goes on the server and on every client.",
  "fabric-1.21.1": "**For Minecraft 1.21.1 with Fabric Loader 0.17.0+ and Fabric API.** Goes on the server and on every client.",
  "fabric-1.20.1": "**For Minecraft 1.20.1 with Fabric Loader 0.17.0+ and Fabric API.** Goes on the server and on every client.",
};

const body = b => `
### Renamed: The Shooting Star Addition: SA Series
- The mod is now **The Shooting Star Addition: SA Series** (in the mod list, the H menu's plate title and the chat messages, which start with **[SA Series]**), with a new icon. Other addons call themselves "The Shooting Star Addition [skill]" and players mixed them up.
- **Nothing to do**: the mod id is unchanged, so your configs, worlds and other mods' links keep working.

### New
- **\`menu_plates\`** in \`config/shooting_star_addition-client.toml\`, section \`[stellar_remote]\` (on by default): \`false\` hides this addon's settings plate and Stellar Convergence's plate in the H menu, with their keys. The settings still apply; edit them in that file.
- **SA-03 is planned**, as players asked.

### Fixed
- **Our skills were missing from the remote's H menu** (Halley, Luna, Proxima and Stellar Convergence) when **SS-21 Quasar** or **The TON618 demo** was installed. You could still cast them; only the menu hid them. They now always show, and so do other addons that join the remote the official way (like **SX-01 Solar Flare**).
- **Stellar Convergence with other addons**: **SS-21 Quasar** and **SX-01 Solar Flare** now land on the same tick as every other weapon. Before, they fired at once, and Quasar's long build-up filled almost every panel of the film.
- **Stellar Convergence fires each ticked weapon once**, and weapons still on their way are called off if you change dimension before they land.
- **Stellar Convergence's film panels** now draw the frozen frames solid: on some setups the screen's own transparency let the white panel behind show through.${b === "fabric-26.3" ? `
- **Fabric 26.3: Stellar Convergence's film panels were blank white.** The frozen frames were drawn in the wrong order and copied too early in the frame, before the films' scenes and screen effects. Now they show the shots as you saw them.` : ""}
`;
fs.mkdirSync(OUT, { recursive: true });
for (const [b, head] of Object.entries(HEAD)) {
  const text = (head + "\n" + body(b)).replace(/\n+$/, "") + "\n";
  if (text.includes("](")) throw new Error(b + ": a \"(\" right after \"]\"");
  fs.writeFileSync(path.join(OUT, b + ".md"), text, "utf8");
  console.log(b, text.length);
}

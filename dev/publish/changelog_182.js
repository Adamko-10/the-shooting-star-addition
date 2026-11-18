#!/usr/bin/env node
// The 1.8.2 changelog for players, one per branch (GitHub release notes, reused for CurseForge).
// node dev/publish/changelog_182.js [out dir]   writes <out>/<branch>.md (default ~/.tssa-ci/publish/1.8.2/)
// In Markdown changelogs, never a "(" right after "]" (CLAUDE.md §0): checked below.
const fs = require("fs");
const os = require("os");
const path = require("path");

const OUT = process.argv[2] || path.join(os.homedir(), ".tssa-ci", "publish", "1.8.2");

const HEAD = {
  "main": "**For Minecraft 1.21.1 with NeoForge 21.1.x.** Goes on the server and on every client.",
  "forge-1.21.1": "**For Minecraft 1.21.1 with Forge 52.1.16+.** Goes on the server and on every client.",
  "forge-1.20.1": "This jar runs on both **Forge and NeoForge 1.20.1**. Goes on the server and on every client.",
  "fabric-26.3": "**For Minecraft 26.3 with Fabric Loader 0.19.5+ and Fabric API.** Goes on the server and on every client.",
  "fabric-1.21.11": "**For Minecraft 1.21.11 with Fabric Loader 0.17.0+ and Fabric API.** Goes on the server and on every client.",
  "fabric-1.21.1": "**For Minecraft 1.21.1 with Fabric Loader 0.17.0+ and Fabric API.** Goes on the server and on every client.",
  "fabric-1.20.1": "**For Minecraft 1.20.1 with Fabric Loader 0.17.0+ and Fabric API.** Goes on the server and on every client.",
};

const only = (b, list, text) => (list.includes(b) ? text : "");

const body = b => `
### Needs The Shooting Star 1.6.0 and RimuLib 1.3.0
- Updated for **The Shooting Star 1.6.0** and **RimuLib 1.3.0**, which this version needs. If you stay on The Shooting Star 1.5.2, keep 1.8.1.

### New
- **\`/ssa zones\`** (operators): \`/ssa zones list\` shows Proxima's radiation zones in this world, \`/ssa zones clear\` removes them.
- **A "never removed" entity tag**, \`shooting_star_addition:never_removed\`: strikes never wipe the entities in it. Graves from **Corpse** and **Create**'s contraptions are in it already; servers can add more with a data pack.${only(b, ["main"], `
- **Close the H menu when you pick a skill**: **[** on this addon's plate switches it (off by default; \`close_menu_on_pick\` in \`config/shooting_star_addition-client.toml\`).`)}

### Fixed
- **Halley at high power could freeze the server for seconds** while it built its Comet Heart. The heart is now at most 3 times its normal size and is built in far fewer steps.
- **A strike's effects now reach as far as the strike does.** Players more than 512 blocks away from a very big strike didn't see or hear it.
- **Proxima needs open sky**: it now refuses in the Nether (anywhere with a ceiling) and when the mark has no open sky above it, instead of striking from inside the rock.
- **Items and XP dropped after Proxima's impact**, such as a player's drops after dying in the bowl, are no longer wiped by its wave.
- **Proxima outside the film**: the small red beam and bolt from the gate down onto the mark, which did nothing, are gone.${only(b, ["main", "forge-1.21.1", "forge-1.20.1", "fabric-1.21.1", "fabric-1.20.1"], `
- **Proxima's sky gate with a shader pack**: it was drawn under the pack's sky and couldn't be seen. It now shows.`)}${only(b, ["main", "forge-1.21.1"], `
- **Proxima's shock wave with a shader pack** (Iris, Oculus) was invisible when Proxima was the only strike running.`)}
- **End gateways are protected** like bedrock and end portals: no strike breaks them.
- **Safer servers**: an error in one strike, in Stellar Convergence or in the work left after a strike is now caught and logged, instead of stopping everything else.
- **Stellar Convergence fires only the Stellar Remote's weapons**, not skills from other RimuLib menus.${only(b, ["main", "forge-1.21.1", "forge-1.20.1", "fabric-1.21.1", "fabric-1.20.1", "fabric-1.21.11"], `
- **The plate's symbol keys** (such as - and =) show their real name for your keyboard layout.`)}
`;
fs.mkdirSync(OUT, { recursive: true });
for (const [b, head] of Object.entries(HEAD)) {
  const text = (head + "\n" + body(b)).replace(/\n+$/, "") + "\n";
  if (text.includes("](")) throw new Error(b + ": a \"(\" right after \"]\"");
  fs.writeFileSync(path.join(OUT, b + ".md"), text, "utf8");
  console.log(b, text.length);
}

#!/usr/bin/env node
// The 1.8.3 changelog for players, one per branch (GitHub release notes, reused for CurseForge).
// node dev/publish/changelog_183.js [out dir]   writes <out>/<branch>.md (default ~/.tssa-ci/publish/1.8.3/)
// In Markdown changelogs, never a "(" right after "]" (CLAUDE.md §0): checked below.
const fs = require("fs");
const os = require("os");
const path = require("path");

const OUT = process.argv[2] || path.join(os.homedir(), ".tssa-ci", "publish", "1.8.3");

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
A small patch on top of 1.8.2. Still for **The Shooting Star 1.6.0** and **RimuLib 1.3.0**.

### Fixed${only(b, ["fabric-26.3", "fabric-1.21.11"], `
- **Block names**: Moon Cheese, Solar Residue, the Comet Heart and the other blocks showed up as \`item.shooting_star_addition...\` in your inventory. They have their real names again.`)}${only(b, ["forge-1.21.1", "forge-1.20.1"], `
- **Luna's sky on Forge**: the vanilla sun and moon stayed in the sky next to Luna's falling moon. They're hidden again while it falls, as on the other versions.`)}${only(b, ["main", "fabric-1.21.1", "fabric-1.20.1"], `
- Luna's sky code is now written the same way on every version (no change in game on this one).`)}
- The mod list shows the right requirements (The Shooting Star 1.6.0 and RimuLib 1.3.0).
`;
fs.mkdirSync(OUT, { recursive: true });
for (const [b, head] of Object.entries(HEAD)) {
  const text = (head + "\n" + body(b)).replace(/\n+$/, "") + "\n";
  if (text.includes("](")) throw new Error(b + ": a \"(\" right after \"]\"");
  fs.writeFileSync(path.join(OUT, b + ".md"), text, "utf8");
  console.log(b, text.length);
}

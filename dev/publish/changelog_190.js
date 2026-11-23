#!/usr/bin/env node
// The 1.9.0 changelog for players, one per branch (GitHub release notes, reused for CurseForge).
// node dev/publish/changelog_190.js [out dir]   writes <out>/<branch>.md (default ~/.tssa-ci/publish/1.9.0/)
// Pass ..\releases\1.9.0\changelogs as the out dir for the release.
// In Markdown changelogs, never a "(" right after "]" (CLAUDE.md §0): checked below.
const fs = require("fs");
const os = require("os");
const path = require("path");

const OUT = process.argv[2] || path.join(os.homedir(), ".tssa-ci", "publish", "1.9.0");

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
const not = (b, list, text) => (list.includes(b) ? "" : text);
const NO_SKY_PASS = ["fabric-1.21.11", "fabric-26.3"];

const body = b => `
A big update: a new settings menu, a bigger Halley, a reworked Luna, Stellar Convergence's split screen and much more.
Still for **The Shooting Star 1.6.0** and **RimuLib 1.3.0**.

### New settings menu
- The plate in the remote's menu (H) is redone: the general settings, plus a **card for the skill you have selected** with only that skill's settings.
- Every setting has a short explanation under it. The rarely used ones sit in a **Less used** bar (press 0 to open it).
- Backspace folds the plate away. The card's keys win over skill keys while it's shown, so nothing clashes.
- Right-click a skill in the list to read what it does (a gold hint says so).
- Title cards are on by default, in the legacy film style too.${not(b, ["main"], `
- **Close on pick** ([ on the plate, off by default): picking a skill closes the menu, the way H or Esc does. A skill with a warning page (Gravitia) still shows its warning first.`)}

### SA-01 · Halley
- **A bigger blast**: a wider crater and a longer reach, with new blast effects. If you never changed its size settings, they move up to the new defaults once; your own values are kept.
- A hush and an inhale just before the hit, sounds for the title cards, the comet a bit bigger when you first see it, a longer aftermath.
- The beacon stops at the clouds. The Comet Heart is now always placed in the crater.

### SA-02 · Luna
- A build-up before the fall, and both films reworked.
- **Epic mode** lasts 20 seconds and is synced with its music. The fall has its own sounds per mode.
- The impact and aftermath sounds are the 1.8.3 ones again (they hit harder).
- The moon no longer flickers once it rests in its crater.

### Behind your shader pack's clouds
${only(b, NO_SKY_PASS, `- Not on this version yet: Luna's moon, Halley's comet and Proxima's sky gate still draw in front of a shader pack's clouds, as in 1.8.3.`)}${not(b, NO_SKY_PASS, `- With a shader pack, Luna's moon, Halley's comet and Proxima's sky gate now sit behind its clouds instead of in front of them.`)}

### SA-00 · Stellar Convergence
- Its card lists the addons it can fire, installed or missing (SS-22 Hyperion included).
- Sounds as the panels pop in and the grid breaks.
- **More than nine weapons**: pages of nine; press 0 to turn the page (a gold bar tells you).
- SS-22 Hyperion and SS-02 Meridian are timed to land with the rest. Gravitia's finale comes 3 to 12 seconds after the hit.
- **Split screen** (experimental, F on its card): every weapon in its own part of the screen, one live at a time. The comic panels stay the default. Heavy on big setups.
- SS-21 Quasar's space shot and HUD no longer cover the film. Far shots stay inside your render distance.

### Works better with other mods
- **TON 618**: it hides the HUD during its films, which hid our film overlays, title cards and Proxima's space part. They're drawn again.
- **TON 618 Addition**: its skills show in the remote, locked, when it isn't installed, so you know what it adds.

### Fixed
- Film cameras no longer end up over land your game hasn't loaded (a far shot could show only sky, mostly at low render distances).${only(b, ["fabric-26.3"], `
- Proxima's scorch burns the grass around its bowl again (it stayed green on 26.3).`)}

### Your own music
- Put your own \`luna.ogg\`, \`luna_epic.ogg\` or \`proxima.ogg\` in \`config/shooting_star_addition/music/\` to replace ours. A README there says where each one's drop should land.
`;
fs.mkdirSync(OUT, { recursive: true });
for (const [b, head] of Object.entries(HEAD)) {
  const text = (head + "\n" + body(b)).replace(/\n+$/, "") + "\n";
  if (text.includes("](")) throw new Error(b + ": a \"(\" right after \"]\"");
  fs.writeFileSync(path.join(OUT, b + ".md"), text, "utf8");
  console.log(b, text.length);
}

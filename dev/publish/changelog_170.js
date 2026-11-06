#!/usr/bin/env node
// The 1.7.0 changelog for players, one per branch (GitHub release notes, reused for CurseForge).
// node dev/publish/changelog_170.js [out dir] [--sonniss]
//   writes <out>/<branch>.md (default ~/.tssa-ci/publish/1.7.0/); --sonniss when the new Sonniss sounds ship.
// In Markdown changelogs, never a "(" right after "]" (CLAUDE.md §0): checked below.
const fs = require("fs");
const os = require("os");
const path = require("path");

const args = process.argv.slice(2);
const sonniss = args.includes("--sonniss");
const OUT = args.find(a => !a.startsWith("--")) || path.join(os.homedir(), ".tssa-ci", "publish", "1.7.0");

const HEAD = {
  "main": "**For Minecraft 1.21.1 with NeoForge 21.1.x.** Goes on the server and on every client.",
  "forge-1.21.1": "**For Minecraft 1.21.1 with Forge 52.1+.** Goes on the server and on every client.",
  "forge-1.20.1": "This jar runs on both **Forge and NeoForge 1.20.1**. Goes on the server and on every client.",
  "fabric-26.3": "**For Minecraft 26.3 with Fabric Loader 0.19.5+ and Fabric API.** Goes on the server and on every client.",
  "fabric-1.21.11": "**For Minecraft 1.21.11 with Fabric Loader 0.17.0+ and Fabric API.** Goes on the server and on every client.",
  "fabric-1.21.1": "**For Minecraft 1.21.1 with Fabric Loader 0.17.0+ and Fabric API.** Goes on the server and on every client.",
  "fabric-1.20.1": "**For Minecraft 1.20.1 with Fabric Loader 0.17.0+ and Fabric API.** Goes on the server and on every client.",
};
// Stellar Quartet exists for 1.21.1 (NeoForge, Forge, Fabric) and Fabric 26.3 only: elsewhere Convergence is open to all.
const QUARTET = new Set(["main", "forge-1.21.1", "fabric-1.21.1", "fabric-26.3"]);

const unlock = b => QUARTET.has(b)
  ? "- **It unlocks when you have Stellar Quartet installed** (by ikun520), the addon that inspired it. Each player's own game checks for it; servers don't need it. Operators can always fire it with `/ssa strike stellar_convergence`."
  : "- **Everyone gets it on this version** (Stellar Quartet, the addon that inspired it, isn't made for this Minecraft version).";

const sounds = sonniss
  ? "- **New sounds for both skills**, built from real recorded sound effects (trailer booms, bass drops, ice, metal, wind, fire) instead of the old synthesised ones, with the same timing: punchier impacts, a rising build-up and a hush before Luna's impact, and more variety for the sounds that repeat."
  : "- **Reworked sounds**: punchier impacts (a sub-bass hit, a crack and a rolling tail), better levels against The Shooting Star's own, more variety for debris and rumbles, and Luna's new build-up: a rising rumble before the fall and a hush before the impact.";

const body = b => `
### Works with The Shooting Star 1.5.2
- **Updated for The Shooting Star [Demo] 1.5.2** and **RimuLib 1.2.2** (now needed). On The Shooting Star 1.5.0, keep using The Shooting Star Addition 1.6.0.
- Halley and Luna now join the remote through RimuLib's new public API.

### New: SA-00 · Stellar Convergence
- **A new remote skill that fires every weapon you tick, all at once**, timed so every impact lands on the same tick: Halley, Luna and The Shooting Star's Railgun, Gungnir and Seven Stars (Tesseract fires along with them). Gravitia, if ticked, comes last, once the others are done. Other addons' remote skills can be ticked too; they fire straight away, as their timing isn't known.
- Tick the weapons on its panel in the remote's menu (H) while SA-00 is selected: 1-9 tick, R picks where they land, V folds the panel.
- **On the crosshair, or spread in a ring round it**, each weapon given room for its own size so the small ones aren't lost under the giants.
- **Its own film**: quick cuts to each weapon's moment, frozen into comic-style panels that fill the screen and shatter as everything hits.
- **Master sliders**: SA-00's own power and speed scale every ticked weapon's own sliders.
- One shared sigil and shock ring over the mark in the weapons' colours, and the camera shake held to one strike's worth (X on the panel: capped, full or off).
${unlock(b)}
- Inspired by **Stellar Quartet** by ikun520, with thanks.
- No limits: firing everything at high power can freeze a server. You've been warned.

### New
- **Title cards** in both films: HALLEY streaking in like small comets, LUNA cracking in and breaking away, each with its code name.
- **Operator command**: \`/ssa strike <skill> [at <x y z>] [power] [damage|inf] [speed]\` fires any remote skill (The Shooting Star's too) as you, aimed where you look or at a point, with no cooldown. It ignores the server's slider limits; values past the remote's own range warn you in chat, as they can freeze or crash a server.
- **Luna's Epic mode** (G in the remote's menu): the moon falls slower and more ominously (24 s from the alarm to the impact instead of 16), for everyone watching, with a longer cut of her music.
- **Keep the moon** (L): Luna's moon model stays where it landed instead of crumbling, cooling down, until you switch it off. For filming.
- **Impact frames** (F): full, softer or off, for Halley's and Luna's.
- **Where a creative caster goes** when standing where their own strike lands (E): straight up, or out to the crater's edge.
- **A key for The Shooting Star's Gravitia**, which has none: it's in the remote's menu and the controls, unbound until you set one.
- **No cooldown now covers The Shooting Star's own skills** too: cast one again while the last is still running.
- **Protected blocks**: the block tag \`shooting_star_addition:unbreakable\` (bedrock, barriers, command and structure blocks, end portals, reinforced deepslate) is never carved by Halley or Luna; servers can add more with a data pack.
${sounds}

### Faster and smoother
- **Luna no longer loads or generates land for her strike**: her crater and the moon are built where land is loaded, on worker threads, and finished as the rest loads. Big strikes (high power) no longer stall the server for seconds.
- Fewer chunk rewrites and resends for her rim and cracks.
- Less screen shake in the seconds after Halley's explosion.

### Fixed
- Halley's and Luna's film overlays (the uplink, Luna's letterbox, captions and descent bar) didn't show in The Shooting Star 1.5's films.
- With two strikes running, their overlays drew over each other: now only the newest shows.
- "Tried to load a DUMMY block entity" warnings after Luna's strike.
`;

const EXTRA = {
  "fabric-26.3": "\nNote for Fabric 26.3: check that The Shooting Star itself starts on 26.3 (its 1.5.0 build had a shader problem of its own).\n",
};

fs.mkdirSync(OUT, { recursive: true });
for (const [b, head] of Object.entries(HEAD)) {
  const text = (head + "\n" + body(b) + (EXTRA[b] || "")).replace(/\n+$/, "") + "\n";
  if (text.includes("](")) throw new Error(b + ": a \"(\" right after \"]\"");
  fs.writeFileSync(path.join(OUT, b + ".md"), text, "utf8");
  console.log(b, text.length);
}

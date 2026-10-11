import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.regex.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

/**
 * Makes the release jar, which works with every version of The Shooting Star the build was given:
 *
 *   TssPack <addon.jar> <out.jar> (<tag> <mapping.tiny> <tss.jar>)...
 *
 * Only compat/tss/ (and the mixin config for its mixins) touches The Shooting Star. For each version, the addon jar is
 * mapped to that version's names and verified against its real jar ({@link TssRemap#reobf}), and its compat/tss/ is
 * copied into the release jar as compat/{tag}/ (tag: "v1_4_1" for 1.4.1), with a mixin config of its own. Everything
 * else goes in once, as it is; it must not refer to The Shooting Star at all, and the build fails if it does. The mod
 * metadata (mods.toml, fabric.mod.json or the manifest) gets every copy's mixin config instead of the one for
 * compat/tss/. At run time compat/StarBridge and compat/TssMixins pick the copy for the running version.
 */
public class TssPack {
    static final String TSS = "cyou/rimuru/shootingstardemo";
    static final String SOURCE = "dev/ss05/halley/compat/tss/";
    static final String PREFIX = "dev/ss05/halley/compat/";
    static final String MIXIN_PACKAGE = "dev.ss05.halley.compat.tss.mixin";

    record Variant(String tag, Path mapping, Path tss) {}

    public static void main(String[] args) throws Exception {
        Path in = Path.of(args[0]), out = Path.of(args[1]);
        List<Variant> variants = new ArrayList<>();
        if (args.length < 5 || (args.length - 2) % 3 != 0) {
            throw new IllegalArgumentException("usage: TssPack <addon.jar> <out.jar> (<tag> <mapping.tiny> <tss.jar>)...");
        }
        for (int i = 2; i < args.length; i += 3) {
            variants.add(new Variant(args[i], Path.of(args[i + 1]), Path.of(args[i + 2])));
        }

        Map<String, byte[]> addon = read(in);
        String mixinConfig = mixinConfig(addon);
        List<String> problems = new ArrayList<>();
        for (var e : addon.entrySet()) {
            if (e.getKey().endsWith(".class") && !e.getKey().startsWith(SOURCE) && mentions(e.getValue(), TSS)) {
                problems.add(e.getKey());
            }
        }
        if (!problems.isEmpty()) {
            problems.forEach(p -> System.err.println("  " + p));
            throw new IllegalStateException("only compat/tss/ may refer to The Shooting Star, but these do too");
        }

        Map<String, byte[]> result = new LinkedHashMap<>();
        for (var e : addon.entrySet()) {
            if (!e.getKey().startsWith(SOURCE) && !e.getKey().equals(mixinConfig)) result.put(e.getKey(), e.getValue());
        }
        List<String> configs = new ArrayList<>();
        for (Variant v : variants) {
            System.out.println("== " + v.tag + ": " + v.tss.getFileName());
            Path mapped = Files.createTempFile("tss-" + v.tag, ".jar");
            try {
                TssRemap.reobf(in, mapped, v.mapping, v.tss);
                String target = PREFIX + v.tag + "/";
                int n = 0;
                for (var e : read(mapped).entrySet()) {
                    if (!e.getKey().startsWith(SOURCE)) continue;
                    String name = target + e.getKey().substring(SOURCE.length());
                    byte[] data = e.getKey().endsWith(".class") ? relocate(e.getValue(), target) : e.getValue();
                    if (result.put(name, data) != null) throw new IllegalStateException("two versions map to " + name);
                    n++;
                }
                if (mixinConfig != null) {
                    String config = mixinConfig.replace(".tss.", "." + v.tag + ".");
                    String json = new String(addon.get(mixinConfig), StandardCharsets.UTF_8)
                        .replace("\"" + MIXIN_PACKAGE + "\"", "\"" + (target + "mixin").replace('/', '.') + "\"");
                    result.put(config, json.getBytes(StandardCharsets.UTF_8));
                    configs.add(config);
                }
                System.out.println("   " + n + " files as " + target);
            } finally {
                Files.deleteIfExists(mapped);
            }
        }
        if (mixinConfig != null) declareMixins(result, mixinConfig, configs);

        Files.createDirectories(out.toAbsolutePath().getParent());
        try (ZipOutputStream zout = new ZipOutputStream(Files.newOutputStream(out))) {
            // the manifest first, as jar readers expect
            List<String> names = new ArrayList<>(result.keySet());
            names.sort(Comparator.comparing((String s) -> !s.equals("META-INF/")).thenComparing(s -> !s.equals("META-INF/MANIFEST.MF")));
            for (String name : names) {
                ZipEntry e = new ZipEntry(name);
                e.setTime(315532800000L + 60000L);
                zout.putNextEntry(e);
                zout.write(result.get(name));
                zout.closeEntry();
            }
        }
        System.out.println("packed " + variants.size() + " version(s) of The Shooting Star -> " + out);
    }

    static Map<String, byte[]> read(Path jar) throws IOException {
        Map<String, byte[]> res = new LinkedHashMap<>();
        try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(jar))) {
            for (ZipEntry e; (e = zin.getNextEntry()) != null; ) res.put(e.getName(), zin.readAllBytes());
        }
        return res;
    }

    /** Whether the class file names The Shooting Star (its constant pool holds the package, in / or . form). */
    static boolean mentions(byte[] data, String pkg) {
        String s = new String(data, StandardCharsets.ISO_8859_1);
        return s.contains(pkg) || s.contains(pkg.replace('/', '.'));
    }

    /** The mixin config of compat/tss/mixin, or null if there is none. */
    static String mixinConfig(Map<String, byte[]> jar) {
        String found = null;
        for (var e : jar.entrySet()) {
            if (e.getKey().contains("/") || !e.getKey().endsWith(".json")) continue;
            if (!new String(e.getValue(), StandardCharsets.UTF_8).contains("\"" + MIXIN_PACKAGE + "\"")) continue;
            if (found != null) throw new IllegalStateException("two mixin configs for compat/tss: " + found + ", " + e.getKey());
            if (!e.getKey().contains(".tss.")) throw new IllegalStateException(e.getKey() + ": the mixin config for compat/tss must be called <something>.tss.mixins.json");
            found = e.getKey();
        }
        return found;
    }

    /** compat/tss/... becomes {@code target}... in every name the class uses (its own, its neighbours', lambdas'). */
    static byte[] relocate(byte[] data, String target) {
        ClassReader reader = new ClassReader(data);
        ClassWriter writer = new ClassWriter(0);
        reader.accept(new ClassRemapper(writer, new Remapper() {
            @Override
            public String map(String internalName) {
                return internalName.startsWith(SOURCE) ? target + internalName.substring(SOURCE.length()) : internalName;
            }
        }), 0);
        return writer.toByteArray();
    }

    /** Swaps the one mixin config in the mod's metadata for all of {@code configs}. */
    static void declareMixins(Map<String, byte[]> jar, String config, List<String> configs) throws IOException {
        int found = 0;
        for (String name : List.of("META-INF/neoforge.mods.toml", "META-INF/mods.toml", "fabric.mod.json", "META-INF/MANIFEST.MF")) {
            byte[] data = jar.get(name);
            if (data == null) continue;
            String text = new String(data, StandardCharsets.UTF_8);
            if (!text.contains(config)) continue;
            String changed;
            if (name.endsWith(".toml")) {
                // [[mixins]] config = "x.tss.mixins.json"  ->  one [[mixins]] block per config
                Matcher m = Pattern.compile("(?m)^config\\s*=\\s*\"" + Pattern.quote(config) + "\"[ \\t]*$").matcher(text);
                if (!m.find()) throw new IllegalStateException(name + " mentions " + config + " outside a [[mixins]] block");
                StringBuilder b = new StringBuilder();
                for (String c : configs) b.append(b.length() == 0 ? "" : "\n\n[[mixins]]\n").append("config = \"").append(c).append('"');
                changed = text.substring(0, m.start()) + b + text.substring(m.end());
            } else if (name.endsWith(".json")) {
                // "mixins": [ ..., { "config": "x.tss.mixins.json", ... } ]  ->  one entry per config
                Matcher m = Pattern.compile("\\{[^{}]*\"" + Pattern.quote(config) + "\"[^{}]*\\}").matcher(text);
                String quoted = "\"" + config + "\"";
                if (m.find()) {
                    StringJoiner j = new StringJoiner(",\n    ");
                    for (String c : configs) j.add(m.group().replace(quoted, "\"" + c + "\""));
                    changed = text.substring(0, m.start()) + j + text.substring(m.end());
                } else {
                    StringJoiner j = new StringJoiner(", ");
                    for (String c : configs) j.add("\"" + c + "\"");
                    changed = text.replace(quoted, j.toString());
                }
            } else {
                Manifest manifest = new Manifest(new ByteArrayInputStream(data));
                Attributes attributes = manifest.getMainAttributes();
                String list = attributes.getValue("MixinConfigs");
                if (list == null) continue;
                List<String> all = new ArrayList<>();
                for (String c : list.split(",")) {
                    if (c.trim().equals(config)) all.addAll(configs);
                    else if (!c.isBlank()) all.add(c.trim());
                }
                attributes.putValue("MixinConfigs", String.join(",", all));
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                manifest.write(bytes);
                jar.put(name, bytes.toByteArray());
                found++;
                continue;
            }
            jar.put(name, changed.getBytes(StandardCharsets.UTF_8));
            found++;
        }
        if (found == 0) throw new IllegalStateException("the mod metadata doesn't declare " + config);
    }
}

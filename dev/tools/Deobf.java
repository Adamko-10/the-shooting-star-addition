import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;

/**
 * Local stand-in for TssRemap.deobf (which needs tiny-remapper): applies one or more Tiny v2 mappings (obf -> named)
 * to a jar with ASM's ClassRemapper. Members are looked up on the owner and then up its super types (as far as they
 * are in the jar), so inherited calls get the right names too.
 *
 *   Deobf <in.jar> <out.jar> <mapping.tiny>...
 */
public class Deobf {
    static final Map<String, String> CLS = new HashMap<>();
    static final Map<String, String> MEMBERS = new HashMap<>(); // owner.name or owner.name+desc (obf) -> named
    static final Map<String, List<String>> SUPERS = new HashMap<>();

    public static void main(String[] args) throws Exception {
        for (int i = 2; i < args.length; i++) {
            read(Path.of(args[i]));
        }
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(Path.of(args[0])))) {
            for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
                if (!e.isDirectory()) entries.put(e.getName(), in.readAllBytes());
            }
        }
        for (var e : entries.entrySet()) {
            if (!e.getKey().endsWith(".class")) continue;
            ClassReader r = new ClassReader(e.getValue());
            List<String> s = new ArrayList<>();
            if (r.getSuperName() != null) s.add(r.getSuperName());
            s.addAll(List.of(r.getInterfaces()));
            SUPERS.put(r.getClassName(), s);
        }
        // an unmapped class whose name a mapped one takes gets a suffix (a readable name can be an old obfuscated one)
        Set<String> taken = new HashSet<>(CLS.values());
        for (String c : SUPERS.keySet()) {
            if (!CLS.containsKey(c) && taken.contains(c)) {
                CLS.put(c, c + "_x");
                System.err.println("renamed unmapped " + c + " -> " + c + "_x");
            }
        }
        Remapper remapper = new Remapper() {
            @Override
            public String map(String internalName) {
                return CLS.getOrDefault(internalName, internalName);
            }

            @Override
            public String mapMethodName(String owner, String name, String descriptor) {
                String found = find(owner, "." + name + descriptor, new HashSet<>());
                return found != null ? found : name;
            }

            @Override
            public String mapFieldName(String owner, String name, String descriptor) {
                String found = find(owner, "." + name, new HashSet<>());
                return found != null ? found : name;
            }

            @Override
            public String mapRecordComponentName(String owner, String name, String descriptor) {
                return mapFieldName(owner, name, descriptor);
            }
        };
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(Path.of(args[1])))) {
            for (var e : entries.entrySet()) {
                String name = e.getKey();
                byte[] bytes = e.getValue();
                if (name.endsWith(".class")) {
                    ClassReader r = new ClassReader(bytes);
                    ClassWriter w = new ClassWriter(0);
                    r.accept(new ClassRemapper(w, remapper), 0);
                    bytes = w.toByteArray();
                    name = remapper.map(r.getClassName()) + ".class";
                }
                out.putNextEntry(new ZipEntry(name));
                out.write(bytes);
                out.closeEntry();
            }
        }
    }

    static String find(String owner, String key, Set<String> seen) {
        if (owner == null || !seen.add(owner)) return null;
        String v = MEMBERS.get(owner + key);
        if (v != null) return v;
        for (String s : SUPERS.getOrDefault(owner, List.of())) {
            v = find(s, key, seen);
            if (v != null) return v;
        }
        return null;
    }

    static void read(Path tiny) throws IOException {
        String owner = null;
        for (String line : Files.readAllLines(tiny)) {
            if (line.startsWith("tiny\t") || line.isBlank()) continue;
            String[] p = line.split("\t", -1);
            if (!line.startsWith("\t")) {
                if (p[0].equals("c")) {
                    owner = p[1];
                    CLS.put(p[1], p[2]);
                }
            } else if (p.length >= 5 && p[1].equals("m")) {
                MEMBERS.put(owner + "." + p[3] + p[2], p[4]);
            } else if (p.length >= 5 && p[1].equals("f")) {
                MEMBERS.put(owner + "." + p[3], p[4]);
            }
        }
    }
}

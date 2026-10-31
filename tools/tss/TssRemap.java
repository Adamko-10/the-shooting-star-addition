import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import net.fabricmc.tinyremapper.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * The Shooting Star 1.4.0+ is obfuscated. This remaps between its obfuscated names and readable ones, with a Tiny v2
 * mapping made by TssMatch (namespaces "obf" and "named"):
 *
 *   deobf <obf.jar> <out.jar> <mapping.tiny>   - The Shooting Star's jar, for compiling and dev runs
 *   reobf <addon.jar> <out.jar> <mapping.tiny> <tss.jar>
 *                                              - an addon jar, to run with the real The Shooting Star jar (tss.jar)
 *
 * Without the mapping file (a readable, pre-1.4.0 The Shooting Star), both just copy. reobf then checks the result
 * against the real jar (see {@link #verify}) and fails if anything it uses isn't there.
 *
 * reobf also rewrites the target-method strings of this addon's mixins into The Shooting Star ({@code remap = false},
 * so no remapper touches them otherwise). The release jar is made by {@link TssPack}, which runs reobf once per
 * supported version of The Shooting Star.
 */
public class TssRemap {
    public static void main(String[] args) throws Exception {
        Path in = Path.of(args[1]), out = Path.of(args[2]), mapping = Path.of(args[3]);
        if (args[0].equals("reobf")) {
            reobf(in, out, mapping, Path.of(args[4]));
        } else {
            deobf(in, out, mapping);
        }
    }

    /** A readable copy of The Shooting Star's jar. */
    static void deobf(Path obf, Path out, Path mapping) throws IOException {
        remap(obf, out, mapping, false, List.of());
    }

    /** The addon jar mapped to the real names of The Shooting Star's jar {@code tss}, and verified against it. */
    static void reobf(Path addon, Path out, Path mapping, Path tss) throws IOException {
        if (!Files.exists(mapping)) {
            remap(addon, out, mapping, true, List.of());
        } else {
            // tiny-remapper needs The Shooting Star's class hierarchy (under the names it maps from) to map members
            // that the addon's classes inherit from it
            Path named = Files.createTempFile("tss-named", ".jar");
            try {
                deobf(tss, named, mapping);
                remap(addon, out, mapping, true, List.of(named));
            } finally {
                Files.deleteIfExists(named);
            }
        }
        verify(out, tss);
    }

    static void remap(Path in, Path out, Path mapping, boolean reobf, List<Path> classpath) throws IOException {
        String mode = reobf ? "reobf" : "deobf";
        Files.createDirectories(out.toAbsolutePath().getParent());
        if (!Files.exists(mapping)) {
            // a readable The Shooting Star (1.3.x), or no mapping made for this one yet: nothing to remap
            Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
            System.out.println(mode + ": no mapping " + mapping.getFileName() + ", copied " + in.getFileName());
            return;
        }
        Path input = in;
        if (reobf) {
            input = Files.createTempFile("tss-mixins", ".jar");
            rewriteMixinTargets(in, input, Mapping.read(mapping));
        }
        TinyRemapper remapper = TinyRemapper.newRemapper()
            .withMappings(TinyUtils.createTinyMappingProvider(mapping, reobf ? "named" : "obf", reobf ? "obf" : "named"))
            .ignoreConflicts(true)
            .build();
        Files.deleteIfExists(out);
        try (OutputConsumerPath consumer = new OutputConsumerPath.Builder(out).assumeArchive(true).build()) {
            consumer.addNonClassFiles(input, NonClassCopyMode.UNCHANGED, remapper);
            remapper.readInputs(input);
            for (Path p : classpath) remapper.readClassPath(p);
            remapper.apply(consumer);
        } finally {
            remapper.finish();
        }
        if (reobf) Files.deleteIfExists(input);
        if (!reobf) dedupFields(out);
        System.out.println(mode + " -> " + out);
    }

    /**
     * The Shooting Star's own obfuscator reuses one short field name across several fields of different types in the
     * same class (legal bytecode: fields are keyed by name AND descriptor); the readable copy keeps whichever of them
     * TssMatch couldn't match under that same shared name too. NeoForge's reobfuscation tool (net.neoforged.art)
     * builds its class model keyed by name alone and throws "Duplicate key" on such a class, so every repeat beyond the
     * first is renamed here (declarations and every reference to that exact name+descriptor, wherever it appears in the
     * jar: safe, since a rename is only ever triggered by a genuine same-class clash).
     */
    static void dedupFields(Path jar) throws IOException {
        Map<String, ClassNode> classes = new LinkedHashMap<>();
        loadClasses(jar, classes, "");
        Map<String, String> renames = new HashMap<>(); // "name;desc" -> new name
        int[] counter = {0};
        for (ClassNode c : classes.values()) {
            Map<String, List<FieldNode>> byName = new LinkedHashMap<>();
            for (FieldNode f : c.fields) {
                byName.computeIfAbsent(f.name, k -> new ArrayList<>()).add(f);
            }
            for (List<FieldNode> group : byName.values()) {
                for (int i = 1; i < group.size(); i++) {
                    FieldNode f = group.get(i);
                    String key = f.name + ";" + f.desc;
                    String newName = renames.computeIfAbsent(key, k -> f.name + "$dup" + (counter[0]++));
                    f.name = newName;
                }
            }
        }
        if (renames.isEmpty()) {
            return;
        }
        for (ClassNode c : classes.values()) {
            for (MethodNode m : c.methods) {
                for (AbstractInsnNode in : m.instructions) {
                    if (in instanceof FieldInsnNode fi) {
                        String newName = renames.get(fi.name + ";" + fi.desc);
                        if (newName != null) fi.name = newName;
                    }
                }
            }
        }
        Path tmp = Files.createTempFile("tss-dedup", ".jar");
        try (ZipFile in = new ZipFile(jar.toFile()); ZipOutputStream zout = new ZipOutputStream(Files.newOutputStream(tmp))) {
            for (ZipEntry e : Collections.list(in.entries())) {
                if (e.isDirectory()) continue;
                zout.putNextEntry(new ZipEntry(e.getName()));
                if (e.getName().endsWith(".class")) {
                    String cls = e.getName().substring(0, e.getName().length() - 6);
                    ClassNode n = classes.get(cls);
                    ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                    n.accept(w);
                    zout.write(w.toByteArray());
                } else {
                    zout.write(in.getInputStream(e).readAllBytes());
                }
                zout.closeEntry();
            }
        }
        Files.move(tmp, jar, StandardCopyOption.REPLACE_EXISTING);
        System.out.println("deobf: renamed " + renames.size() + " clashing field name(s) in " + jar.getFileName());
    }

    // ---- verify ------------------------------------------------------------------------------------------------------

    static final String TSS = "cyou/rimuru/shootingstardemo/";

    /**
     * Checks the addon's jar against the real The Shooting Star jar: every class, field and method of The Shooting Star
     * it uses exists there (inherited ones included), every abstract method of a Shooting Star type it extends or
     * implements is implemented under the real name, and its mixins' targets exist. Fails otherwise.
     */
    static void verify(Path addonJar, Path tssJar) throws IOException {
        Map<String, ClassNode> tss = new HashMap<>(), addon = new HashMap<>();
        loadClasses(tssJar, tss, TSS);
        loadClasses(addonJar, addon, "");
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (ClassNode c : addon.values()) {
            List<String> refs = new ArrayList<>();
            if (c.superName != null) refs.add(c.superName);
            refs.addAll(c.interfaces);
            for (String r : refs) {
                if (r.startsWith(TSS) && !tss.containsKey(r)) problems.add(c.name + ": extends missing " + r);
            }
            for (MethodNode m : c.methods) {
                for (AbstractInsnNode in : m.instructions) {
                    String owner = null, name = null, desc = null;
                    boolean method = false;
                    if (in instanceof MethodInsnNode mi) {
                        owner = mi.owner;
                        name = mi.name;
                        desc = mi.desc;
                        method = true;
                    } else if (in instanceof FieldInsnNode fi) {
                        owner = fi.owner;
                        name = fi.name;
                        desc = fi.desc;
                    } else if (in instanceof TypeInsnNode ti && ti.desc.startsWith(TSS) && !tss.containsKey(ti.desc)) {
                        problems.add(c.name + "." + m.name + ": missing class " + ti.desc);
                    } else if (in instanceof InvokeDynamicInsnNode d) {
                        for (Object a : d.bsmArgs) {
                            if (a instanceof Handle h && h.getOwner().startsWith(TSS)) {
                                checked++;
                                if (!resolves(tss, h.getOwner(), h.getName(), h.getDesc(), h.getTag() >= Opcodes.H_INVOKEVIRTUAL)) {
                                    problems.add(c.name + "." + m.name + ": missing " + h.getOwner() + "." + h.getName() + h.getDesc());
                                }
                            }
                        }
                    }
                    if (owner == null || !owner.startsWith(TSS)) continue;
                    checked++;
                    if (!resolves(tss, owner, name, desc, method)) {
                        problems.add(c.name + "." + m.name + ": missing " + owner + "." + name + (method ? desc : " " + desc));
                    }
                }
            }
            // everything abstract above it is implemented
            if (refs.stream().noneMatch(r -> r.startsWith(TSS)) || (c.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE)) != 0) continue;
            for (String[] am : abstractMethods(tss, c)) {
                checked++;
                if (!implemented(tss, addon, c, am[0], am[1])) problems.add(c.name + ": doesn't implement " + am[2] + "." + am[0] + am[1]);
            }
        }
        // mixins into The Shooting Star
        for (ClassNode c : addon.values()) {
            for (String target : mixinTargets(c)) {
                if (!target.startsWith(TSS)) continue;
                checked++;
                ClassNode t = tss.get(target);
                if (t == null) {
                    problems.add(c.name + ": mixin target missing " + target);
                    continue;
                }
                for (MethodNode m : c.methods) {
                    for (List<AnnotationNode> list : Arrays.asList(m.visibleAnnotations, m.invisibleAnnotations)) {
                        if (list == null) continue;
                        for (AnnotationNode a : list) {
                            for (String s : methodStrings(a)) {
                                checked++;
                                int p = s.indexOf('(');
                                String name = p < 0 ? s : s.substring(0, p), desc = p < 0 ? null : s.substring(p);
                                boolean found = t.methods.stream().anyMatch(tm -> tm.name.equals(name) && (desc == null || tm.desc.equals(desc)));
                                if (!found) problems.add(c.name + ": mixin method " + s + " not in " + target);
                            }
                        }
                    }
                }
            }
        }
        if (!problems.isEmpty()) {
            problems.forEach(p -> System.err.println("  " + p));
            throw new IllegalStateException(problems.size() + " reference(s) into The Shooting Star do not resolve in " + tssJar.getFileName());
        }
        System.out.println("verified " + checked + " references into The Shooting Star against " + tssJar.getFileName());
    }

    static void loadClasses(Path jar, Map<String, ClassNode> out, String prefix) throws IOException {
        try (ZipFile z = new ZipFile(jar.toFile())) {
            for (ZipEntry e : Collections.list(z.entries())) {
                if (!e.getName().endsWith(".class") || !e.getName().startsWith(prefix)) continue;
                ClassNode n = new ClassNode();
                new ClassReader(z.getInputStream(e).readAllBytes()).accept(n, 0);
                out.put(n.name, n);
            }
        }
    }

    /** Whether owner.name(desc) exists on owner or a supertype; true once the search leaves The Shooting Star. */
    static boolean resolves(Map<String, ClassNode> tss, String owner, String name, String desc, boolean method) {
        Deque<String> todo = new ArrayDeque<>(List.of(owner));
        Set<String> seen = new HashSet<>();
        while (!todo.isEmpty()) {
            String o = todo.poll();
            if (!seen.add(o)) continue;
            ClassNode c = tss.get(o);
            if (c == null) {
                if (o.startsWith("java/")) {
                    // the JDK is right here: check it (a TSS class's missing method mustn't pass as one of Object's)
                    if (jdkHas(o, name, desc, method)) return true;
                    continue;
                }
                if (!o.startsWith(TSS)) return true; // reached Minecraft: not ours to check
                continue;
            }
            if (method ? c.methods.stream().anyMatch(m -> m.name.equals(name) && m.desc.equals(desc))
                : c.fields.stream().anyMatch(f -> f.name.equals(name) && f.desc.equals(desc))) {
                return true;
            }
            if (c.superName != null) todo.add(c.superName);
            todo.addAll(c.interfaces);
        }
        return false;
    }

    /** Whether a JDK class (or anything above it) has name(desc) / field name of type desc. */
    static boolean jdkHas(String owner, String name, String desc, boolean method) {
        Class<?> c;
        try {
            c = Class.forName(owner.replace('/', '.'), false, TssRemap.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            return true; // not in this JDK: can't tell
        }
        Deque<Class<?>> todo = new ArrayDeque<>(List.of(c));
        while (!todo.isEmpty()) {
            Class<?> k = todo.poll();
            if (method) {
                for (java.lang.reflect.Method m : k.getDeclaredMethods()) {
                    if (m.getName().equals(name) && Type.getMethodDescriptor(m).equals(desc)) return true;
                }
            } else {
                for (java.lang.reflect.Field f : k.getDeclaredFields()) {
                    if (f.getName().equals(name) && Type.getDescriptor(f.getType()).equals(desc)) return true;
                }
            }
            if (k.getSuperclass() != null) todo.add(k.getSuperclass());
            todo.addAll(List.of(k.getInterfaces()));
        }
        return false;
    }

    /** Abstract methods of the Shooting Star types above c: {name, desc, owner}. */
    static List<String[]> abstractMethods(Map<String, ClassNode> tss, ClassNode c) {
        List<String[]> res = new ArrayList<>();
        Deque<String> todo = new ArrayDeque<>();
        if (c.superName != null) todo.add(c.superName);
        todo.addAll(c.interfaces);
        Set<String> seen = new HashSet<>();
        while (!todo.isEmpty()) {
            ClassNode s = tss.get(todo.poll());
            if (s == null || !seen.add(s.name)) continue;
            for (MethodNode m : s.methods) {
                if ((m.access & Opcodes.ACC_ABSTRACT) != 0) res.add(new String[] {m.name, m.desc, s.name});
            }
            if (s.superName != null) todo.add(s.superName);
            todo.addAll(s.interfaces);
        }
        return res;
    }

    /** Whether name(desc) has a body in c or anything above it (a default method included). */
    static boolean implemented(Map<String, ClassNode> tss, Map<String, ClassNode> addon, ClassNode c, String name, String desc) {
        Deque<String> todo = new ArrayDeque<>(List.of(c.name));
        Set<String> seen = new HashSet<>();
        while (!todo.isEmpty()) {
            String n = todo.poll();
            if (!seen.add(n)) continue;
            ClassNode k = addon.containsKey(n) ? addon.get(n) : tss.get(n);
            if (k == null) continue;
            if (k.methods.stream().anyMatch(m -> m.name.equals(name) && m.desc.equals(desc) && (m.access & Opcodes.ACC_ABSTRACT) == 0)) return true;
            if (k.superName != null) todo.add(k.superName);
            todo.addAll(k.interfaces);
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    static List<String> methodStrings(AnnotationNode a) {
        List<String> res = new ArrayList<>();
        if (a.values == null || !a.desc.startsWith("Lorg/spongepowered/asm/mixin/injection/") && !a.desc.startsWith("Lcom/llamalad7/mixinextras/")) return res;
        for (int i = 0; i < a.values.size(); i += 2) {
            if (a.values.get(i).equals("method") && a.values.get(i + 1) instanceof List<?> l) {
                for (Object o : l) if (o instanceof String s) res.add(s);
            }
        }
        return res;
    }

    /** The mapping, looked up from the named side. */
    record Mapping(Map<String, String> classes, Map<String, String> methods) {
        static Mapping read(Path p) throws IOException {
            Map<String, String> obfToNamed = new HashMap<>(), classes = new HashMap<>(), methods = new HashMap<>();
            List<String[]> rows = new ArrayList<>();
            String cls = null;
            for (String line : Files.readAllLines(p)) {
                String[] t = line.split("\t", -1);
                if (t[0].equals("c")) {
                    cls = t[1];
                    obfToNamed.put(t[1], t[2]);
                    classes.put(t[2], t[1]);
                } else if (t.length >= 5 && t[0].isEmpty() && t[1].equals("m")) {
                    rows.add(new String[] {cls, t[2], t[3], t[4]});
                }
            }
            for (String[] r : rows) {
                String namedOwner = obfToNamed.getOrDefault(r[0], r[0]);
                methods.put(namedOwner + "." + r[3] + mapDesc(r[1], obfToNamed), r[2]);
            }
            return new Mapping(classes, methods);
        }
    }

    static String mapDesc(String desc, Map<String, String> classes) {
        StringBuilder b = new StringBuilder();
        int i = 0;
        while (i < desc.length()) {
            char c = desc.charAt(i);
            b.append(c);
            i++;
            if (c == 'L') {
                int end = desc.indexOf(';', i);
                String name = desc.substring(i, end);
                b.append(classes.getOrDefault(name, name)).append(';');
                i = end + 1;
            }
        }
        return b.toString();
    }

    /** For mixins whose target is a mapped class: "name(desc)" in method = ... becomes the obfuscated name/desc. */
    static void rewriteMixinTargets(Path in, Path out, Mapping m) throws IOException {
        try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(in));
             ZipOutputStream zout = new ZipOutputStream(Files.newOutputStream(out))) {
            for (ZipEntry e; (e = zin.getNextEntry()) != null; ) {
                byte[] data = zin.readAllBytes();
                if (e.getName().endsWith(".class")) data = rewriteClass(data, m);
                ZipEntry ne = new ZipEntry(e.getName());
                ne.setTime(e.getTime());
                zout.putNextEntry(ne);
                zout.write(data);
                zout.closeEntry();
            }
        }
    }

    static byte[] rewriteClass(byte[] data, Mapping m) {
        ClassNode node = new ClassNode();
        new ClassReader(data).accept(node, 0);
        List<String> targets = mixinTargets(node);
        targets.removeIf(t -> !m.classes.containsKey(t));
        if (targets.isEmpty()) return data;
        boolean changed = rewriteTargetStrings(node, m);
        for (MethodNode method : node.methods) {
            for (List<AnnotationNode> list : Arrays.asList(method.visibleAnnotations, method.invisibleAnnotations)) {
                if (list == null) continue;
                for (AnnotationNode a : list) changed |= rewriteMethodValues(a, targets, m);
            }
        }
        if (!changed) return data;
        ClassWriter w = new ClassWriter(0);
        node.accept(w);
        System.out.println("  mixin targets rewritten in " + node.name);
        return w.toByteArray();
    }

    /** {@code @Mixin(targets = "a.b.C")}: class names as strings, which no remapper touches. */
    @SuppressWarnings("unchecked")
    static boolean rewriteTargetStrings(ClassNode node, Mapping m) {
        boolean changed = false;
        for (List<AnnotationNode> list : Arrays.asList(node.visibleAnnotations, node.invisibleAnnotations)) {
            if (list == null) continue;
            for (AnnotationNode a : list) {
                if (!a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;") || a.values == null) continue;
                for (int i = 0; i < a.values.size(); i += 2) {
                    if (!a.values.get(i).equals("targets")) continue;
                    List<Object> l = (List<Object>) a.values.get(i + 1);
                    for (int j = 0; j < l.size(); j++) {
                        String obf = l.get(j) instanceof String s ? m.classes.get(s.replace('.', '/')) : null;
                        if (obf != null) {
                            l.set(j, obf.replace('/', '.'));
                            changed = true;
                        }
                    }
                }
            }
        }
        return changed;
    }

    static List<String> mixinTargets(ClassNode node) {
        List<String> res = new ArrayList<>();
        for (List<AnnotationNode> list : Arrays.asList(node.visibleAnnotations, node.invisibleAnnotations)) {
            if (list == null) continue;
            for (AnnotationNode a : list) {
                if (!a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;") || a.values == null) continue;
                for (int i = 0; i < a.values.size(); i += 2) {
                    Object v = a.values.get(i + 1);
                    if (v instanceof List<?> l) for (Object o : l) {
                        if (o instanceof Type t) res.add(t.getInternalName());
                        if (o instanceof String s) res.add(s.replace('.', '/'));
                    }
                }
            }
        }
        return res;
    }

    @SuppressWarnings("unchecked")
    static boolean rewriteMethodValues(AnnotationNode a, List<String> targets, Mapping m) {
        if (a.values == null) return false;
        boolean changed = false;
        for (int i = 0; i < a.values.size(); i += 2) {
            if (!a.values.get(i).equals("method")) continue;
            List<Object> l = (List<Object>) a.values.get(i + 1);
            for (int j = 0; j < l.size(); j++) {
                if (!(l.get(j) instanceof String s)) continue;
                int p = s.indexOf('(');
                String name = p < 0 ? s : s.substring(0, p), desc = p < 0 ? null : s.substring(p);
                for (String t : targets) {
                    String obf = null;
                    if (desc != null) {
                        obf = m.methods.get(t + "." + name + desc);
                    } else {
                        for (var e : m.methods.entrySet()) if (e.getKey().startsWith(t + "." + name + "(")) obf = e.getValue();
                    }
                    if (obf != null) {
                        String obfDesc = desc == null ? "" : mapDesc(desc, m.classes);
                        l.set(j, obf + obfDesc);
                        changed = true;
                        break;
                    }
                }
            }
        }
        return changed;
    }
}

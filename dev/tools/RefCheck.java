import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * Lists every reference the given classes make to The Shooting Star (owner under the prefix) that a readable jar of
 * some version doesn't have: classes, fields and methods (looked up through super types).
 *
 *   RefCheck <readable tss.jar> <prefix e.g. cyou/rimuru/shootingstardemo/mc1211/> <prefix to rewrite from, or -> <classes dir>...
 */
public class RefCheck {
    static final Map<String, ClassNode> TSS = new HashMap<>();

    public static void main(String[] args) throws Exception {
        String prefix = args[1];
        String from = args[2].equals("-") ? null : args[2];
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(Path.of(args[0])))) {
            for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
                if (e.getName().endsWith(".class") && e.getName().startsWith(prefix)) {
                    ClassNode n = new ClassNode();
                    new ClassReader(in.readAllBytes()).accept(n, ClassReader.SKIP_CODE);
                    TSS.put(n.name, n);
                }
            }
        }
        Set<String> missing = new TreeSet<>();
        for (int i = 3; i < args.length; i++) {
            try (var walk = Files.walk(Path.of(args[i]))) {
                for (Path p : walk.filter(f -> f.toString().endsWith(".class")).toList()) {
                    ClassNode n = new ClassNode();
                    new ClassReader(Files.readAllBytes(p)).accept(n, 0);
                    String self = n.name;
                    java.util.function.Function<String, String> fix = s -> s == null ? null : (from == null ? s : s.replace(from, prefix));
                    // super types and annotations' mixin targets are left to the build; calls, fields, types here
                    for (MethodNode m : n.methods) {
                        if (m.instructions == null) continue;
                        for (AbstractInsnNode insn : m.instructions) {
                            if (insn instanceof MethodInsnNode mi) {
                                String owner = fix.apply(mi.owner);
                                if (owner.startsWith(prefix) && !hasMethod(owner, mi.name, fix.apply(mi.desc))) {
                                    missing.add(self + " -> " + owner + "." + mi.name + fix.apply(mi.desc));
                                }
                            } else if (insn instanceof FieldInsnNode fi) {
                                String owner = fix.apply(fi.owner);
                                if (owner.startsWith(prefix) && !hasField(owner, fi.name)) {
                                    missing.add(self + " -> " + owner + "." + fi.name + " " + fix.apply(fi.desc));
                                }
                            } else if (insn instanceof TypeInsnNode ti) {
                                String t = fix.apply(ti.desc);
                                if (t.startsWith(prefix) && !TSS.containsKey(t)) missing.add(self + " -> class " + t);
                            } else if (insn instanceof LdcInsnNode li && li.cst instanceof Type t) {
                                String c = fix.apply(t.getInternalName());
                                if (c.startsWith(prefix) && !TSS.containsKey(c)) missing.add(self + " -> class " + c);
                            }
                        }
                    }
                    String sup = fix.apply(n.superName);
                    if (sup != null && sup.startsWith(prefix) && !TSS.containsKey(sup)) missing.add(self + " -> super " + sup);
                    for (String itf : n.interfaces) {
                        String c = fix.apply(itf);
                        if (c.startsWith(prefix) && !TSS.containsKey(c)) missing.add(self + " -> interface " + c);
                    }
                }
            }
        }
        missing.forEach(System.out::println);
        System.out.println(missing.size() + " missing");
    }

    static boolean hasMethod(String owner, String name, String desc) {
        ClassNode c = TSS.get(owner);
        if (c == null) return false;
        for (MethodNode m : c.methods) if (m.name.equals(name) && m.desc.equals(desc)) return true;
        if (c.superName != null && TSS.containsKey(c.superName) && hasMethod(c.superName, name, desc)) return true;
        for (String i : c.interfaces) if (TSS.containsKey(i) && hasMethod(i, name, desc)) return true;
        if (c.superName != null && c.superName.equals("java/lang/Object") || c.superName.equals("java/lang/Record") || c.superName.equals("java/lang/Enum")) {
            return Set.of("equals", "hashCode", "toString", "getClass", "values", "valueOf", "ordinal", "name", "compareTo").contains(name);
        }
        return c.superName != null && !TSS.containsKey(c.superName); // inherited from outside (vanilla): assume ok
    }

    static boolean hasField(String owner, String name) {
        ClassNode c = TSS.get(owner);
        if (c == null) return false;
        for (FieldNode f : c.fields) if (f.name.equals(name)) return true;
        if (c.superName != null && TSS.containsKey(c.superName)) return hasField(c.superName, name);
        if (Set.of("java/lang/Object", "java/lang/Record", "java/lang/Enum").contains(c.superName)) return false;
        return c.superName != null && !TSS.containsKey(c.superName);
    }
}

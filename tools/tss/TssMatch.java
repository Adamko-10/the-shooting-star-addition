import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * Matches the classes and members of an obfuscated build of The Shooting Star against a named build of an earlier
 * version (same loader, same Minecraft version), by structure: package, kind, nesting, super types, field and method
 * descriptors (with already-matched classes substituted), generic signatures, string constants and calls. Writes a
 * Tiny v2 mapping obf -> named covering every class/member it matched; anything new in the obfuscated build keeps
 * its obfuscated name.
 *
 * Usage: TssMatch <named.jar> <obf.jar> <out.tiny> <package prefix, e.g. cyou/rimuru/shootingstardemo/> [nested-jar filter]
 * (a jar may be a Fabric bundle: then its nested jar whose name contains the filter, e.g. "+mc26.3", is read).
 * It also writes <out.tiny>.report.txt: what it couldn't match. Check that nothing compat/ uses is in there.
 */
public class TssMatch {
    static String PREFIX;
    static String NESTED;
    static Map<String, String> CLS, BACK;
    static final Map<String, List<String>> CALLERS_OLD = new HashMap<>(), CALLERS_NEW = new HashMap<>();

    record Cls(ClassNode node, String name) {}

    public static void main(String[] args) throws Exception {
        PREFIX = args[3];
        NESTED = args.length > 4 ? args[4] : null;
        Map<String, ClassNode> oldC = load(args[0]);
        Map<String, ClassNode> newC = load(args[1]);
        Map<String, String> cls = new HashMap<>(); // new(obf) -> old(named)
        Map<String, String> back = new HashMap<>();
        CLS = cls;
        BACK = back;
        index(oldC, CALLERS_OLD);
        index(newC, CALLERS_NEW);
        // anchors: same names on both sides (entry points, mixins, kept classes)
        for (String n : newC.keySet()) {
            if (oldC.containsKey(n) && !isObfName(n)) {
                cls.put(n, n);
                back.put(n, n);
            }
        }
        for (int round = 0; round < 12; round++) {
            int before = cls.size();
            matchClasses(oldC, newC, cls, back, round);
            if (cls.size() == before && round > 2) {
                break;
            }
        }
        List<String> report = new ArrayList<>();
        // methods: match per class, then make names agree along each override family (interface/abstract methods have
        // no body to match on; their implementations do)
        Map<String, Map<MethodNode, MethodNode>> allMethods = new HashMap<>();
        Map<String, String> methodNames = new HashMap<>();
        Map<String, Map<String, Integer>> votes = new HashMap<>();
        Map<String, String> parent = new HashMap<>();
        for (String n : newC.keySet()) {
            String o = cls.get(n);
            if (o == null) continue;
            allMethods.put(n, matchMethods(oldC.get(o), newC.get(n), back, cls));
        }
        for (ClassNode c : newC.values()) {
            for (MethodNode m : c.methods) {
                String key = c.name + "." + m.name + m.desc;
                find(parent, key);
                if ((m.access & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE)) != 0 || m.name.startsWith("<")) continue;
                for (String sup : supertypes(c, newC)) {
                    for (MethodNode sm : newC.get(sup).methods) {
                        if (sm.name.equals(m.name) && sm.desc.equals(m.desc) && (sm.access & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE)) == 0) {
                            union(parent, key, sup + "." + sm.name + sm.desc);
                        }
                    }
                }
            }
        }
        for (var e : allMethods.entrySet()) {
            for (var me : e.getValue().entrySet()) {
                String root = find(parent, e.getKey() + "." + me.getKey().name + me.getKey().desc);
                int w = (me.getKey().access & Opcodes.ACC_ABSTRACT) != 0 ? 1 : 3;
                votes.computeIfAbsent(root, k -> new HashMap<>()).merge(me.getValue().name, w, Integer::sum);
            }
        }
        for (ClassNode c : newC.values()) {
            for (MethodNode m : c.methods) {
                String key = c.name + "." + m.name + m.desc;
                Map<String, Integer> v = votes.get(find(parent, key));
                if (v == null) continue;
                methodNames.put(key, Collections.max(v.entrySet(), Map.Entry.comparingByValue()).getKey());
            }
        }
        // no two methods visible in one class may end up with the same name and descriptor: the better-backed keeps it
        for (ClassNode c : newC.values()) {
            Map<String, String> taken = new HashMap<>(); // named name + desc -> key holding it
            List<String> keys = new ArrayList<>();
            for (MethodNode m : c.methods) keys.add(c.name + "." + m.name + m.desc);
            for (String sup : supertypes(c, newC)) for (MethodNode m : newC.get(sup).methods) {
                if ((m.access & Opcodes.ACC_PRIVATE) == 0) keys.add(sup + "." + m.name + m.desc);
            }
            for (String key : keys) {
                String named = methodNames.get(key);
                if (named == null || key.contains(".<")) continue;
                String desc = key.substring(key.indexOf('('));
                String slot = named + desc;
                String other = taken.get(slot);
                if (other == null || find(parent, other).equals(find(parent, key))) {
                    taken.put(slot, key);
                    continue;
                }
                String loser = support(votes, parent, key, named) > support(votes, parent, other, named) ? other : key;
                methodNames.remove(loser);
                if (loser.equals(other)) taken.put(slot, key);
                report.add("  dropped duplicate name " + named + desc + " on " + loser);
            }
        }
        StringBuilder out = new StringBuilder("tiny\t2\t0\tobf\tnamed\n");
        int fields = 0, methods = 0;
        for (String n : new TreeSet<>(newC.keySet())) {
            String o = cls.get(n);
            if (o == null) {
                report.add("NEW CLASS (unmatched): " + n + "  " + summary(newC.get(n)));
                continue;
            }
            out.append("c\t").append(n).append('\t').append(o).append('\n');
            ClassNode a = oldC.get(o), b = newC.get(n);
            Map<FieldNode, FieldNode> fm = matchFields(a, b, back, cls);
            // A member that is new in this version keeps its obfuscated name - unless a matched one now takes that name
            // (a readable name can be an old obfuscated one): then it gets a suffix, or the remapper refuses the clash.
            Set<String> fieldNames = new HashSet<>();
            for (FieldNode f : b.fields) {
                FieldNode of = fm.get(f);
                if (of != null) fieldNames.add(of.name + ";" + f.desc);
            }
            for (FieldNode f : b.fields) {
                FieldNode of = fm.get(f);
                String target = of != null ? of.name : f.name;
                if (of == null && fieldNames.contains(f.name + ";" + f.desc)) {
                    int i = 1;
                    while (fieldNames.contains(f.name + "_" + i + ";" + f.desc)) i++;
                    target = f.name + "_" + i;
                    fieldNames.add(target + ";" + f.desc);
                    report.add("  renamed new field " + n + "." + f.name + " " + f.desc + " -> " + target + " (its name is taken)");
                }
                if (!target.equals(f.name)) {
                    out.append("\tf\t").append(f.desc).append('\t').append(f.name).append('\t').append(target).append('\n');
                    fields++;
                }
            }
            Map<MethodNode, MethodNode> mm = allMethods.get(n);
            Set<String> methodSlots = new HashSet<>();
            for (MethodNode m : b.methods) {
                String named = methodNames.get(n + "." + m.name + m.desc);
                if (named != null) methodSlots.add(named + m.desc);
            }
            for (MethodNode m : b.methods) {
                String named = methodNames.get(n + "." + m.name + m.desc);
                if (named == null && !m.name.startsWith("<") && (m.access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC)) != 0
                    && methodSlots.contains(m.name + m.desc)) {
                    int i = 1;
                    while (methodSlots.contains(m.name + "_" + i + m.desc)) i++;
                    named = m.name + "_" + i;
                    methodSlots.add(named + m.desc);
                    report.add("  renamed new method " + n + "." + m.name + m.desc + " -> " + named + " (its name is taken)");
                }
                if (named != null && !named.equals(m.name)) {
                    out.append("\tm\t").append(m.desc).append('\t').append(m.name).append('\t').append(named).append('\n');
                    methods++;
                }
            }
            for (FieldNode f : a.fields) {
                if (!fm.containsValue(f)) report.add("  old field not found: " + o + "." + f.name + " " + f.desc);
            }
            for (MethodNode m : a.methods) {
                if (!mm.containsValue(m) && !m.name.startsWith("lambda$")) report.add("  old method not found: " + o + "." + m.name + m.desc);
            }
        }
        for (String o : new TreeSet<>(oldC.keySet())) {
            if (!back.containsKey(o)) report.add("OLD CLASS (gone/unmatched): " + o + "  " + summary(oldC.get(o)));
        }
        Files.writeString(Path.of(args[2]), out);
        Files.write(Path.of(args[2] + ".report.txt"), report);
        System.out.println("classes " + cls.size() + "/" + newC.size() + " (old " + oldC.size() + "), fields " + fields + ", methods " + methods
            + ", report lines " + report.size());
    }

    static int support(Map<String, Map<String, Integer>> votes, Map<String, String> parent, String key, String name) {
        Map<String, Integer> v = votes.get(find(parent, key));
        return v == null ? 0 : v.getOrDefault(name, 0);
    }

    static String find(Map<String, String> parent, String k) {
        String p = parent.get(k);
        if (p == null) {
            parent.put(k, k);
            return k;
        }
        if (p.equals(k)) return k;
        String r = find(parent, p);
        parent.put(k, r);
        return r;
    }

    static void union(Map<String, String> parent, String a, String b) {
        String ra = find(parent, a), rb = find(parent, b);
        if (!ra.equals(rb)) parent.put(ra, rb);
    }

    /** All TSS supertypes (classes and interfaces) of c, transitively. */
    static Set<String> supertypes(ClassNode c, Map<String, ClassNode> all) {
        Set<String> res = new LinkedHashSet<>();
        Deque<String> todo = new ArrayDeque<>();
        if (c.superName != null) todo.add(c.superName);
        todo.addAll(c.interfaces);
        while (!todo.isEmpty()) {
            String s = todo.poll();
            ClassNode sc = all.get(s);
            if (sc == null || !res.add(s)) continue;
            if (sc.superName != null) todo.add(sc.superName);
            todo.addAll(sc.interfaces);
        }
        return res;
    }

    static void index(Map<String, ClassNode> classes, Map<String, List<String>> callers) {
        for (ClassNode c : classes.values()) for (MethodNode m : c.methods) for (AbstractInsnNode in : m.instructions) {
            if (in instanceof MethodInsnNode mi && mi.owner.startsWith(PREFIX)) {
                callers.computeIfAbsent(mi.owner + "." + mi.name + mi.desc, k -> new ArrayList<>()).add(c.name.replaceAll("[$].*", ""));
            }
        }
    }

    /** How alike the classes calling the two methods are (named side; new callers translated where matched). */
    static double callerSim(String oldOwner, MethodNode a, String newOwner, MethodNode b) {
        List<String> ca = CALLERS_OLD.getOrDefault(oldOwner + "." + a.name + a.desc, List.of());
        List<String> cb = CALLERS_NEW.getOrDefault(newOwner + "." + b.name + b.desc, List.of()).stream()
            .map(x -> CLS.getOrDefault(x, "?" + x)).toList();
        if (ca.isEmpty() && cb.isEmpty()) return 0.5;
        return jaccard(ca, cb);
    }

    static boolean isObfName(String internal) {
        String simple = internal.substring(internal.lastIndexOf('/') + 1);
        for (String part : simple.split("\\$")) {
            if (part.length() <= 2 && part.chars().allMatch(Character::isLowerCase)) return true;
        }
        return false;
    }

    static String summary(ClassNode c) {
        return "super=" + c.superName + " ifs=" + c.interfaces + " f=" + c.fields.size() + " m=" + c.methods.size();
    }

    /** The classes under PREFIX, from the jar itself or, for a Fabric bundle, from its nested jar for NESTED. */
    static Map<String, ClassNode> load(String jar) throws IOException {
        Map<String, ClassNode> map = new HashMap<>();
        try (ZipFile z = new ZipFile(jar)) {
            for (ZipEntry e : Collections.list(z.entries())) {
                if (e.getName().endsWith(".class") && e.getName().startsWith(PREFIX)) {
                    read(z.getInputStream(e).readAllBytes(), map);
                }
            }
            if (map.isEmpty() && NESTED != null) {
                for (ZipEntry e : Collections.list(z.entries())) {
                    if (!e.getName().startsWith("META-INF/jars/") || !e.getName().endsWith(NESTED + ".jar")) continue;
                    try (ZipInputStream in = new ZipInputStream(z.getInputStream(e))) {
                        for (ZipEntry ne; (ne = in.getNextEntry()) != null; ) {
                            if (ne.getName().endsWith(".class") && ne.getName().startsWith(PREFIX)) read(in.readAllBytes(), map);
                        }
                    }
                }
            }
        }
        if (map.isEmpty()) throw new IOException("no classes under " + PREFIX + " in " + jar);
        return map;
    }

    static void read(byte[] bytes, Map<String, ClassNode> map) {
        ClassNode n = new ClassNode();
        new ClassReader(bytes).accept(n, 0);
        map.put(n.name, n);
    }

    // ---- type normalisation: TSS class names -> named side if known, else a placeholder ----

    /** Normalises a descriptor/signature from side 'side' (true = new/obf) so both sides compare. */
    static String norm(String s, boolean obfSide, Map<String, String> cls, Map<String, String> back) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            char ch = s.charAt(i);
            if (ch == 'L' && s.startsWith(PREFIX, i + 1)) {
                int end = i + 1;
                while (end < s.length() && s.charAt(end) != ';' && s.charAt(end) != '<') end++;
                String name = s.substring(i + 1, end);
                String mapped = obfSide ? cls.get(name) : (back.containsKey(name) ? name : null);
                b.append('L').append(mapped != null ? mapped : "?");
                i = end;
            } else {
                b.append(ch);
                i++;
            }
        }
        return b.toString();
    }

    static Set<String> strings(ClassNode c) {
        Set<String> s = new HashSet<>();
        for (MethodNode m : c.methods) s.addAll(strings(m));
        for (FieldNode f : c.fields) if (f.value instanceof String v) s.add(v);
        return s;
    }

    static Set<String> strings(MethodNode m) {
        Set<String> s = new HashSet<>();
        for (AbstractInsnNode in : m.instructions) {
            if (in instanceof LdcInsnNode l && l.cst instanceof String v) s.add(v);
            if (in instanceof InvokeDynamicInsnNode d) for (Object a : d.bsmArgs) if (a instanceof String v) s.add("indy:" + v);
        }
        return s;
    }

    /** Calls/field uses to non-TSS owners (Minecraft, JDK): stable across obfuscation. */
    static List<String> externalRefs(MethodNode m) {
        List<String> s = new ArrayList<>();
        for (AbstractInsnNode in : m.instructions) {
            if (in instanceof MethodInsnNode mi && !mi.owner.startsWith(PREFIX)) s.add(mi.owner + "." + mi.name + mi.desc);
            if (in instanceof FieldInsnNode fi && !fi.owner.startsWith(PREFIX)) s.add(fi.owner + "." + fi.name);
        }
        return s;
    }

    static Set<String> externalRefs(ClassNode c) {
        Set<String> s = new HashSet<>();
        for (MethodNode m : c.methods) s.addAll(externalRefs(m));
        return s;
    }

    static double jaccard(Collection<String> a, Collection<String> b) {
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        Map<String, Integer> ca = count(a), cb = count(b);
        int inter = 0, union = 0;
        Set<String> keys = new HashSet<>(ca.keySet());
        keys.addAll(cb.keySet());
        for (String k : keys) {
            int x = ca.getOrDefault(k, 0), y = cb.getOrDefault(k, 0);
            inter += Math.min(x, y);
            union += Math.max(x, y);
        }
        return union == 0 ? 1.0 : (double) inter / union;
    }

    static Map<String, Integer> count(Collection<String> c) {
        Map<String, Integer> m = new HashMap<>();
        for (String s : c) m.merge(s, 1, Integer::sum);
        return m;
    }

    static int kind(ClassNode c) {
        return c.access & (Opcodes.ACC_INTERFACE | Opcodes.ACC_ENUM | Opcodes.ACC_ANNOTATION | Opcodes.ACC_ABSTRACT | Opcodes.ACC_RECORD);
    }

    static String pkg(String n) {
        return n.substring(0, n.lastIndexOf('/'));
    }

    static String outer(String n) {
        int i = n.lastIndexOf('$');
        return i < 0 ? null : n.substring(0, i);
    }

    static boolean anonymous(String n) {
        int i = n.lastIndexOf('$');
        return i >= 0 && Character.isDigit(n.charAt(i + 1));
    }

    static double score(ClassNode o, ClassNode n, Map<String, String> cls, Map<String, String> back) {
        if (!pkg(o.name).equals(pkg(n.name)) || kind(o) != kind(n)) return -1;
        if ((outer(o.name) == null) != (outer(n.name) == null) || anonymous(o.name) != anonymous(n.name)) return -1;
        if (outer(n.name) != null) {
            String mo = cls.get(outer(n.name));
            if (mo != null && !mo.equals(outer(o.name))) return -1;
        }
        String so = norm("L" + o.superName + ";", false, cls, back), sn = norm("L" + n.superName + ";", true, cls, back);
        double s = 0;
        s += so.equals(sn) ? 2 : (so.contains("?") || sn.contains("?") ? 0 : -3);
        List<String> io = o.interfaces.stream().map(x -> norm("L" + x + ";", false, cls, back)).toList();
        List<String> ino = n.interfaces.stream().map(x -> norm("L" + x + ";", true, cls, back)).toList();
        s += 2 * jaccard(io, ino);
        List<String> fo = o.fields.stream().map(f -> (f.access & Opcodes.ACC_STATIC) + norm(f.signature != null ? f.signature : f.desc, false, cls, back)).toList();
        List<String> fn = n.fields.stream().map(f -> (f.access & Opcodes.ACC_STATIC) + norm(f.signature != null ? f.signature : f.desc, true, cls, back)).toList();
        s += 4 * jaccard(fo, fn);
        List<String> mo = o.methods.stream().map(m -> (m.access & Opcodes.ACC_STATIC) + norm(m.desc, false, cls, back)).toList();
        List<String> mn = n.methods.stream().map(m -> (m.access & Opcodes.ACC_STATIC) + norm(m.desc, true, cls, back)).toList();
        s += 4 * jaccard(mo, mn);
        Set<String> sto = strings(o), stn = strings(n);
        if (!sto.isEmpty() || !stn.isEmpty()) s += 8 * jaccard(sto, stn);
        s += 3 * jaccard(externalRefs(o), externalRefs(n));
        return s;
    }

    static void matchClasses(Map<String, ClassNode> oldC, Map<String, ClassNode> newC, Map<String, String> cls, Map<String, String> back, int round) {
        record Pair(String o, String n, double s) {}
        List<Pair> pairs = new ArrayList<>();
        Map<String, Double> bestForNew = new HashMap<>(), secondForNew = new HashMap<>();
        Map<String, Double> bestForOld = new HashMap<>(), secondForOld = new HashMap<>();
        for (ClassNode n : newC.values()) {
            if (cls.containsKey(n.name)) continue;
            for (ClassNode o : oldC.values()) {
                if (back.containsKey(o.name)) continue;
                double s = score(o, n, cls, back);
                if (s < 0) continue;
                pairs.add(new Pair(o.name, n.name, s));
                rank(bestForNew, secondForNew, n.name, s);
                rank(bestForOld, secondForOld, o.name, s);
            }
        }
        pairs.sort(Comparator.comparingDouble(Pair::s).reversed());
        double minScore = round < 3 ? 9.0 : 5.0;
        double margin = round < 3 ? 1.5 : 0.4;
        for (Pair p : pairs) {
            if (cls.containsKey(p.n) || back.containsKey(p.o)) continue;
            if (p.s < minScore) continue;
            // only take a pair that is clearly the best for both sides
            if (p.s < bestForNew.get(p.n) || p.s < bestForOld.get(p.o)) continue;
            double s2 = Math.max(secondForNew.getOrDefault(p.n, -1.0), secondForOld.getOrDefault(p.o, -1.0));
            if (p.s - s2 < margin) continue;
            cls.put(p.n, p.o);
            back.put(p.o, p.n);
        }
    }

    static void rank(Map<String, Double> best, Map<String, Double> second, String k, double s) {
        double b = best.getOrDefault(k, -1.0);
        if (s > b) {
            second.put(k, b);
            best.put(k, s);
        } else if (s > second.getOrDefault(k, -1.0)) {
            second.put(k, s);
        }
    }

    // ---- members ----

    static Map<FieldNode, FieldNode> matchFields(ClassNode o, ClassNode n, Map<String, String> back, Map<String, String> cls) {
        Map<FieldNode, FieldNode> res = new HashMap<>();
        // static fields set up from string constants in <clinit> (registry ids, enum constant names): match by those
        Map<String, FieldNode> ko = clinitKeys(o), kn = clinitKeys(n);
        for (var e : kn.entrySet()) {
            FieldNode fa = ko.get(e.getKey());
            if (fa != null && fkey(fa, false, cls, back).equals(fkey(e.getValue(), true, cls, back))) res.put(e.getValue(), fa);
        }
        Map<String, List<FieldNode>> go = new LinkedHashMap<>(), gn = new LinkedHashMap<>();
        for (FieldNode f : o.fields) if (!res.containsValue(f)) go.computeIfAbsent(fkey(f, false, cls, back), k -> new ArrayList<>()).add(f);
        for (FieldNode f : n.fields) if (!res.containsKey(f)) gn.computeIfAbsent(fkey(f, true, cls, back), k -> new ArrayList<>()).add(f);
        for (var e : gn.entrySet()) {
            List<FieldNode> a = go.get(e.getKey());
            if (a == null) continue;
            List<FieldNode> b = e.getValue();
            if (a.size() == b.size()) {
                for (int i = 0; i < b.size(); i++) res.put(b.get(i), a.get(i));
            } else {
                // same name kept (unobfuscated) first, then constant values, then nothing (ambiguous)
                for (FieldNode fb : b) for (FieldNode fa : a) {
                    if (fa.name.equals(fb.name) || (fa.value != null && fa.value.equals(fb.value))) {
                        if (!res.containsValue(fa)) res.put(fb, fa);
                        break;
                    }
                }
            }
        }
        // a field may also keep its name with a changed type
        for (FieldNode fb : n.fields) {
            if (res.containsKey(fb)) continue;
            for (FieldNode fa : o.fields) if (fa.name.equals(fb.name) && !isShort(fb.name) && !res.containsValue(fa)) res.put(fb, fa);
        }
        return res;
    }

    /** For each static field of c written in its methods, the strings loaded since the previous static write (only keys
     * that are unique within the class). */
    static Map<String, FieldNode> clinitKeys(ClassNode c) {
        Map<String, FieldNode> byName = new HashMap<>();
        for (FieldNode f : c.fields) byName.put(f.name, f);
        Map<String, FieldNode> res = new HashMap<>();
        Set<String> dup = new HashSet<>();
        for (MethodNode m : c.methods) {
            // <clinit>, or wherever else they are set (NeoForge/Forge register items in a RegisterEvent handler)
            StringBuilder seg = new StringBuilder();
            String last = null;
            int since = 0;
            for (AbstractInsnNode in : m.instructions) {
                if (in instanceof LdcInsnNode l && l.cst instanceof String v) seg.append(v).append('|');
                // read right after its own strings, e.g. register(id("stellar_remote"), STELLAR_REMOTE) in a lambda
                if (in instanceof FieldInsnNode fi && fi.getOpcode() == Opcodes.GETSTATIC && fi.owner.equals(c.name) && seg.length() > 0) {
                    FieldNode f = byName.get(fi.name);
                    String k = "read:" + seg;
                    seg.setLength(0);
                    if (f == null || (f.access & Opcodes.ACC_STATIC) == 0) continue;
                    if (res.containsKey(k) || dup.contains(k)) {
                        res.remove(k);
                        dup.add(k);
                    } else {
                        res.put(k, f);
                    }
                    continue;
                }
                if (in instanceof FieldInsnNode fi && fi.getOpcode() == Opcodes.PUTSTATIC && fi.owner.equals(c.name)) {
                    FieldNode f = byName.get(fi.name);
                    String k = seg.toString();
                    seg.setLength(0);
                    if (!k.isEmpty()) {
                        last = k;
                        since = 0;
                    } else if (last != null) {
                        // no strings of its own (e.g. an item registered under the key set just before it)
                        k = last + "+" + (++since);
                    }
                    if (f == null || k.isEmpty()) continue;
                    if (res.containsKey(k) || dup.contains(k)) {
                        res.remove(k);
                        dup.add(k);
                    } else {
                        res.put(k, f);
                    }
                }
            }
        }
        return res;
    }

    static boolean isShort(String n) {
        return n.length() <= 2;
    }

    static String fkey(FieldNode f, boolean obf, Map<String, String> cls, Map<String, String> back) {
        return (f.access & (Opcodes.ACC_STATIC | Opcodes.ACC_FINAL)) + " " + norm(f.signature != null ? f.signature : f.desc, obf, cls, back);
    }

    static Map<MethodNode, MethodNode> matchMethods(ClassNode o, ClassNode n, Map<String, String> back, Map<String, String> cls) {
        Map<MethodNode, MethodNode> res = new HashMap<>();
        Map<String, List<MethodNode>> go = new LinkedHashMap<>(), gn = new LinkedHashMap<>();
        for (MethodNode m : o.methods) if (!m.name.startsWith("lambda$")) go.computeIfAbsent(mkey(m, false, cls, back), k -> new ArrayList<>()).add(m);
        for (MethodNode m : n.methods) if (!m.name.startsWith("lambda$")) gn.computeIfAbsent(mkey(m, true, cls, back), k -> new ArrayList<>()).add(m);
        for (var e : gn.entrySet()) {
            List<MethodNode> a = go.get(e.getKey());
            if (a == null) continue;
            List<MethodNode> b = new ArrayList<>(e.getValue());
            List<MethodNode> left = new ArrayList<>(a);
            // kept names first
            for (MethodNode mb : List.copyOf(b)) for (MethodNode ma : List.copyOf(left)) {
                if (ma.name.equals(mb.name)) {
                    res.put(mb, ma);
                    b.remove(mb);
                    left.remove(ma);
                    break;
                }
            }
            if (b.size() == 1 && left.size() == 1) {
                res.put(b.get(0), left.get(0));
                continue;
            }
            // several with the same shape: best body similarity, greedy
            record P(MethodNode a, MethodNode b, double s) {}
            List<P> ps = new ArrayList<>();
            for (MethodNode mb : b) for (MethodNode ma : left) ps.add(new P(ma, mb, (2 * bodySim(ma, mb) + callerSim(o.name, ma, n.name, mb)) / 3.0));
            ps.sort(Comparator.comparingDouble(P::s).reversed());
            for (P p : ps) {
                if (res.containsKey(p.b) || res.containsValue(p.a) || p.s < 0.35) continue;
                res.put(p.b, p.a);
            }
        }
        // leftovers whose descriptor changed a little (a parameter widened, e.g. LivingEntity -> Entity)
        record Q(MethodNode a, MethodNode b, double s) {}
        List<Q> qs = new ArrayList<>();
        for (MethodNode mb : n.methods) {
            if (res.containsKey(mb) || mb.name.startsWith("lambda$") || mb.name.startsWith("<")) continue;
            for (MethodNode ma : o.methods) {
                if (res.containsValue(ma) || ma.name.startsWith("lambda$") || ma.name.startsWith("<")) continue;
                if ((ma.access & Opcodes.ACC_STATIC) != (mb.access & Opcodes.ACC_STATIC)) continue;
                int vis = Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED;
                if ((ma.access & vis) != (mb.access & vis)) continue;
                Type[] pa = Type.getArgumentTypes(norm(ma.desc, false, cls, back).replace("L?", "Ljava/lang/Object")), pb = Type.getArgumentTypes(norm(mb.desc, true, cls, back).replace("L?", "Ljava/lang/Object"));
                if (pa.length != pb.length) continue;
                if (!Type.getReturnType(norm(ma.desc, false, cls, back).replace("L?", "Ljava/lang/Object")).equals(Type.getReturnType(norm(mb.desc, true, cls, back).replace("L?", "Ljava/lang/Object")))) continue;
                int same = 0;
                boolean ok = true;
                for (int i = 0; i < pa.length; i++) {
                    if (pa[i].equals(pb[i])) same++;
                    else if (pa[i].getSort() != Type.OBJECT || pb[i].getSort() != Type.OBJECT) ok = false;
                }
                if (!ok || same < pa.length - 1) continue;
                qs.add(new Q(ma, mb, (2 * bodySim(ma, mb) + callerSim(o.name, ma, n.name, mb)) / 3.0));
            }
        }
        qs.sort(Comparator.comparingDouble(Q::s).reversed());
        for (Q q : qs) {
            if (res.containsKey(q.b) || res.containsValue(q.a) || q.s < 0.45) continue;
            res.put(q.b, q.a);
        }
        return res;
    }

    static String mkey(MethodNode m, boolean obf, Map<String, String> cls, Map<String, String> back) {
        return (m.access & (Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT)) + " " + norm(m.desc, obf, cls, back);
    }

    static double bodySim(MethodNode a, MethodNode b) {
        double s = 0;
        Set<String> sa = strings(a), sb = strings(b);
        s += (sa.isEmpty() && sb.isEmpty()) ? 0.3 : jaccard(sa, sb);
        s += jaccard(externalRefs(a), externalRefs(b));
        int ia = a.instructions.size(), ib = b.instructions.size();
        s += ia + ib == 0 ? 1 : 1.0 - Math.abs(ia - ib) / (double) Math.max(ia, ib);
        return s / 3.0;
    }
}

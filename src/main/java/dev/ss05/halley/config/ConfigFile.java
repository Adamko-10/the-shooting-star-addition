package dev.ss05.halley.config;

import dev.ss05.halley.HalleyAddon;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

/**
 * A small TOML config file in the same layout NeoForge writes ({@code [section]}, {@code key = value}, comments with
 * the allowed range), so the files read the same on every loader. Values are read once when {@link #load()} runs;
 * anything missing or out of range falls back to its default, and the file is written back complete.
 */
public final class ConfigFile {
    private final String fileName;
    private final List<Section> sections = new ArrayList<>();
    private Section current;
    private boolean loaded;

    public ConfigFile(String fileName) {
        this.fileName = fileName;
    }

    // ---- Building the spec. -----------------------------------------------------------------------------------------

    public ConfigFile section(String name, String... comment) {
        this.current = new Section(name, comment);
        this.sections.add(this.current);
        return this;
    }

    public BoolValue define(String key, boolean value, String... comment) {
        return this.add(new BoolValue(key, value, comment));
    }

    public IntValue defineInRange(String key, int value, int min, int max, String... comment) {
        return this.add(new IntValue(key, value, min, max, comment));
    }

    public DoubleValue defineInRange(String key, double value, double min, double max, String... comment) {
        return this.add(new DoubleValue(key, value, min, max, comment));
    }

    public <E extends Enum<E>> EnumValue<E> defineEnum(String key, E value, String... comment) {
        return this.add(new EnumValue<>(key, value, comment));
    }

    private <V extends Value<?>> V add(V value) {
        if (this.current == null) {
            throw new IllegalStateException("start a section first");
        }
        this.current.values.add(value);
        return value;
    }

    public boolean isLoaded() {
        return this.loaded;
    }

    public Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(this.fileName);
    }

    // ---- Reading and writing. ---------------------------------------------------------------------------------------

    /** Reads the file (creating it the first time) and writes it back with every key present. Never throws. */
    public synchronized void load() {
        Path path = this.path();
        Map<String, String> raw = new HashMap<>();
        boolean complete = Files.exists(path);
        if (complete) {
            try {
                raw = parse(Files.readAllLines(path, StandardCharsets.UTF_8));
            } catch (IOException | RuntimeException failed) {
                HalleyAddon.LOG.warn("Couldn't read {}, using the defaults: {}", path, failed.toString());
                complete = false;
            }
        }
        for (Section section : this.sections) {
            for (Value<?> value : section.values) {
                String text = raw.get(section.name + "." + value.key);
                if (text == null || !value.read(text)) {
                    if (text != null) {
                        HalleyAddon.LOG.warn("{}: {}.{} = {} isn't allowed, using {}", this.fileName, section.name, value.key, text,
                            value.writeFallback());
                    }
                    value.reset();
                    complete = false;
                }
            }
        }
        this.loaded = true;
        if (!complete) {
            this.save();
        }
    }

    private void save() {
        Path path = this.path();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, this.render(), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException failed) {
            HalleyAddon.LOG.warn("Couldn't write {}: {}", path, failed.toString());
        }
    }

    private String render() {
        StringBuilder out = new StringBuilder();
        for (Section section : this.sections) {
            for (String line : section.comment) {
                out.append('#').append(line).append('\n');
            }
            out.append('[').append(section.name).append("]\n");
            for (Value<?> value : section.values) {
                for (String line : value.comment) {
                    out.append("\t#").append(line).append('\n');
                }
                String hint = value.hint();
                if (hint != null) {
                    out.append("\t#").append(hint).append('\n');
                }
                out.append('\t').append(value.key).append(" = ").append(value.writeCurrent()).append('\n');
            }
            out.append('\n');
        }
        return out.toString();
    }

    /** {@code section.key -> raw value text}, for the plain subset of TOML this file uses. */
    static Map<String, String> parse(List<String> lines) {
        Map<String, String> out = new LinkedHashMap<>();
        String section = "";
        for (String line : lines) {
            String s = stripComment(line).trim();
            if (s.isEmpty()) {
                continue;
            }
            if (s.startsWith("[") && s.endsWith("]")) {
                section = s.substring(1, s.length() - 1).trim();
                continue;
            }
            int eq = s.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = s.substring(0, eq).trim();
            String value = s.substring(eq + 1).trim();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            out.put(section.isEmpty() ? key : section + "." + key, value);
        }
        return out;
    }

    private static String stripComment(String line) {
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            } else if (c == '#' && !quoted) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    // ---- Values. ----------------------------------------------------------------------------------------------------

    private record Section(String name, String[] comment, List<Value<?>> values) {
        Section(String name, String[] comment) {
            this(name, comment, new ArrayList<>());
        }
    }

    public abstract static class Value<T> {
        final String key;
        final T fallback;
        final String[] comment;
        volatile T value;

        Value(String key, T fallback, String[] comment) {
            this.key = key;
            this.fallback = fallback;
            this.comment = comment;
            this.value = fallback;
        }

        public T get() {
            return this.value;
        }

        void reset() {
            this.value = this.fallback;
        }

        abstract boolean read(String text);

        abstract String write(T value);

        String writeCurrent() {
            return this.write(this.value);
        }

        String writeFallback() {
            return this.write(this.fallback);
        }

        String hint() {
            return null;
        }
    }

    public static final class BoolValue extends Value<Boolean> {
        BoolValue(String key, boolean fallback, String[] comment) {
            super(key, fallback, comment);
        }

        @Override
        boolean read(String text) {
            if (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("false")) {
                this.value = Boolean.parseBoolean(text);
                return true;
            }
            return false;
        }

        @Override
        String write(Boolean value) {
            return value.toString();
        }
    }

    public static final class IntValue extends Value<Integer> {
        private final int min;
        private final int max;

        IntValue(String key, int fallback, int min, int max, String[] comment) {
            super(key, fallback, comment);
            this.min = min;
            this.max = max;
        }

        @Override
        boolean read(String text) {
            try {
                int v = Integer.parseInt(text.replace("_", ""));
                if (v < this.min || v > this.max) {
                    return false;
                }
                this.value = v;
                return true;
            } catch (NumberFormatException notANumber) {
                return false;
            }
        }

        @Override
        String write(Integer value) {
            return value.toString();
        }

        @Override
        String hint() {
            return "Range: " + this.min + " ~ " + this.max;
        }
    }

    public static final class DoubleValue extends Value<Double> {
        private final double min;
        private final double max;

        DoubleValue(String key, double fallback, double min, double max, String[] comment) {
            super(key, fallback, comment);
            this.min = min;
            this.max = max;
        }

        @Override
        boolean read(String text) {
            try {
                double v = Double.parseDouble(text.replace("_", ""));
                if (!(v >= this.min && v <= this.max)) {
                    return false;
                }
                this.value = v;
                return true;
            } catch (NumberFormatException notANumber) {
                return false;
            }
        }

        @Override
        String write(Double value) {
            return value.toString();
        }

        @Override
        String hint() {
            return "Range: " + this.min + " ~ " + this.max;
        }
    }

    public static final class EnumValue<E extends Enum<E>> extends Value<E> {
        EnumValue(String key, E fallback, String[] comment) {
            super(key, fallback, comment);
        }

        @Override
        boolean read(String text) {
            for (E constant : this.fallback.getDeclaringClass().getEnumConstants()) {
                if (constant.name().equalsIgnoreCase(text.trim())) {
                    this.value = constant;
                    return true;
                }
            }
            return false;
        }

        @Override
        String write(E value) {
            return "\"" + value.name() + "\"";
        }

        @Override
        String hint() {
            StringBuilder out = new StringBuilder("Allowed Values: ");
            E[] constants = this.fallback.getDeclaringClass().getEnumConstants();
            for (int i = 0; i < constants.length; i++) {
                out.append(i == 0 ? "" : ", ").append(constants[i].name().toUpperCase(Locale.ROOT));
            }
            return out.toString();
        }
    }
}

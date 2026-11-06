package dev.ss05.halley.config;

import java.util.List;

/** Dev-only regression check for saving Stellar Convergence's selection, run when the harness starts. */
public final class ConfigFileCheck {
    private ConfigFileCheck() {
    }

    public static void run() {
        var value = new ConfigFile.StringListValue("convergence", List.of("halley"), new String[0]);
        List<String> selected = List.of("halley", "luna", "addon:other_skill");
        String saved = value.write(selected);
        String parsed = ConfigFile.parse(List.of("[stellar_remote]", "convergence = " + saved + " # picks"))
            .get("stellar_remote.convergence");
        check(value.read(parsed) && value.get().equals(selected), "selection round trip");
        check(value.read("[]") && value.get().isEmpty(), "empty selection");
        check(!value.read("[\"halley\", false]"), "reject non-string entry");
        check(!value.read("[\"halley\",]"), "reject missing entry");
        check(!value.read("halley"), "reject non-array");
        check(value.get().isEmpty(), "malformed arrays leave the value intact");
        value.reset();
        check(value.get().equals(List.of("halley")), "restore default");
    }

    private static void check(boolean passed, String name) {
        if (!passed) {
            throw new AssertionError("ConfigFile: " + name);
        }
    }
}

import java.nio.file.*;
import java.util.*;

/**
 * Makes the mapping for a new The Shooting Star from the one before it:
 *
 *   TssMappings <previous.jar> <previous-mapping.tiny> <new.jar> <new-mapping.tiny> <prefix> [<nested>]
 *
 * The previous version is made readable with its own mapping (or taken as it is, if it has none: a readable 1.3.x),
 * and {@link TssMatch} matches the new jar against that, so the readable names carry on from version to version.
 */
public class TssMappings {
    public static void main(String[] args) throws Exception {
        Path previous = Path.of(args[0]), previousMapping = Path.of(args[1]);
        Path named = previous;
        if (Files.exists(previousMapping)) {
            named = Files.createTempFile("tss-previous-named", ".jar");
            TssRemap.deobf(previous, named, previousMapping);
        }
        try {
            List<String> match = new ArrayList<>(List.of(named.toString(), args[2], args[3], args[4]));
            if (args.length > 5) match.add(args[5]);
            TssMatch.main(match.toArray(String[]::new));
        } finally {
            if (!named.equals(previous)) Files.deleteIfExists(named);
        }
    }
}

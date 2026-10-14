#!/usr/bin/env bash
# verify_jar.sh <jar> <expected version>: the release jar carries the right version, a copy of compat for every
# supported The Shooting Star, and nothing of The Shooting Star itself (no classes, no mappings).
set -uo pipefail
jar="$1"; ver="$2"
bad=0
list=$(unzip -Z1 "$jar")
for v in v1_3_3 v1_3_4 v1_4_0 v1_4_1 v1_4_2; do
  echo "$list" | grep -q "dev/ss05/halley/compat/$v/" || { echo "  MISSING compat/$v"; bad=1; }
done
if echo "$list" | grep -E '^cyou/|\.tiny$|^libs/|mappings' > /dev/null; then echo "  SHIPS THE SHOOTING STAR STUFF:"; echo "$list" | grep -E '^cyou/|\.tiny$|^libs/|mappings' | head; bad=1; fi
meta=$( (unzip -p "$jar" META-INF/neoforge.mods.toml 2>/dev/null; unzip -p "$jar" META-INF/mods.toml 2>/dev/null; unzip -p "$jar" fabric.mod.json 2>/dev/null) )
echo "$meta" | grep -q "$ver" || { echo "  version $ver not in the metadata"; bad=1; }
echo "$meta" | grep -o -E '1\.4\.2[^",]*' | head -1 | sed 's/^/  supports: /'
for c in CastingMixin WhiteSpaceMixin; do echo "$list" | grep -q "compat/v1_4_2/mixin/$c.class" || { echo "  MISSING $c"; bad=1; }; done
echo "$list" | grep -q "holey_moon_cheese" || { echo "  MISSING holey moon cheese"; bad=1; }
[ $bad = 0 ] && echo "  OK $(basename "$jar") ($(stat -c %s "$jar") bytes)"
exit $bad

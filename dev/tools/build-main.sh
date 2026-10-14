#!/bin/bash
# Local dev build of the main branch (NeoForge 1.21.1) against the real NeoForge 21.1.248 jars and the readable
# The Shooting Star 1.3.4 (dev layout: plain compat/tss, no per-version copies). Also builds the clienttest harness.
set -e
ROOT=${ROOT:-/home/claude/tssa/main}
W=/home/claude/work/localdev
TSS=$W/tss-1.3.4-neoforge.jar
CP=$(ls /home/claude/work/libs/*.jar | grep -v -E "sources|userdev|installer|client-extra|universal" | tr '\n' ':')$TSS
rm -rf $W/out && mkdir -p $W/out/classes $W/out/jar $W/out/ct-classes $W/out/ct-jar
find $ROOT/src/main/java -name '*.java' > $W/out/sources.txt
javac --release 21 -encoding UTF-8 -nowarn -proc:none -d $W/out/classes -cp "$CP" @$W/out/sources.txt 2>&1 | grep -v 'Picked up' | grep -v '^Note:' || true
[ -f $W/out/classes/dev/ss05/halley/HalleyAddon.class ] || { echo "COMPILE FAILED"; exit 1; }
cp -r $W/out/classes/. $W/out/jar/
cp -r $ROOT/src/main/resources/. $W/out/jar/
python3 - "$ROOT" "$W/out/jar/META-INF/neoforge.mods.toml" <<'PY'
import re, sys, os
root, out = sys.argv[1], sys.argv[2]
props = {}
for line in open(root + '/gradle.properties', encoding='utf-8'):
    line = line.strip()
    if not line or line.startswith('#') or '=' not in line: continue
    k, v = line.split('=', 1); props[k.strip()] = v.strip()
props['shooting_star_version_range'] = '[1.3.3,)'
tpl = open(root + '/src/main/templates/META-INF/neoforge.mods.toml', encoding='utf-8').read()
os.makedirs(os.path.dirname(out), exist_ok=True)
open(out, 'w', encoding='utf-8').write(re.sub(r'\$\{([a-z_]+)\}', lambda m: props[m.group(1)], tpl))
PY
(cd $W/out/jar && jar --create --file $W/out/addon.jar .)
# the harness
find $ROOT/src/clienttest/java -name '*.java' > $W/out/ct-sources.txt
javac --release 21 -encoding UTF-8 -nowarn -proc:none -d $W/out/ct-classes -cp "$CP:$W/out/classes" @$W/out/ct-sources.txt 2>&1 | grep -v 'Picked up' | grep -v '^Note:' || true
cp -r $W/out/ct-classes/. $W/out/ct-jar/ && cp -r $ROOT/src/clienttest/resources/. $W/out/ct-jar/
(cd $W/out/ct-jar && jar --create --file $W/out/clienttest.jar .)
ls -la $W/out/*.jar

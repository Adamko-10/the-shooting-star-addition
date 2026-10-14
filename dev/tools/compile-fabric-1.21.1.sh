#!/bin/bash
# Compile-check the fabric-1.21.1 branch (main + clienttest) against Minecraft 1.21.1 (NeoForge's merged jar: Mojang's
# names, NeoForge's own additions too - so a pass here still needs the CI build), stubs of the Fabric API classes it
# uses, and The Shooting Star 1.3.4 (NeoForge's build, its package moved to the Fabric one's).
ROOT=${ROOT:-/home/claude/tssa/fabric-1.21.1}
W=/home/claude/work/fab1211
OUT=$W/out
rm -rf $OUT && mkdir -p $OUT/stubs $OUT/classes
LIBS=$(ls /home/claude/work/libs/*.jar | grep -v -E 'sources|userdev|installer|universal|client-extra' | tr '\n' ':')
CP=$LIBS/home/claude/work/mc263/libs/fabric-loader-0.19.5.jar:$W/tss-1.3.4-relocated.jar
javac --release 21 -nowarn -proc:none -d $OUT/stubs -cp "$CP" $(find $W/stubs/src -name '*.java') 2>&1 | grep -v 'Picked up'
find $ROOT/src/main/java $ROOT/src/clienttest/java -name '*.java' > $OUT/sources.txt
javac --release 21 -encoding UTF-8 -nowarn -Xmaxerrs 300 -proc:none -d $OUT/classes -cp "$CP:$OUT/stubs" @$OUT/sources.txt 2>&1 | grep -v 'Picked up' | grep -v '^Note:'
echo "classes: $(find $OUT/classes -name '*.class' | wc -l)"

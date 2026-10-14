#!/bin/bash
# Compile-check the fabric-26.3 branch (main + clienttest sources) against Minecraft 26.3, Fabric and TSS 1.3.4.
ROOT=${ROOT:-/home/claude/tssa/fabric-26.3}
OUT=/home/claude/work/localdev/out263
J=/home/claude/work/jdk25/jdk-25.0.1+8/bin
M=/home/claude/work/mc263
TSS=/home/claude/work/localdev/tssfab/META-INF/jars/the-shooting-star-demo-1.3.4-demo+mc26.3.jar
rm -rf $OUT && mkdir -p $OUT/classes
CP=$M/minecraft-26.3-client.jar:$(ls $M/libs/*.jar $M/libs-fallback/*.jar $M/libs-compile/*.jar $M/fabric-api/META-INF/jars/*.jar | tr '\n' ':')$TSS
find $ROOT/src/main/java $ROOT/src/clienttest/java -name '*.java' > $OUT/sources.txt
$J/javac --release 25 -encoding UTF-8 -nowarn -Xmaxerrs 300 -proc:none -d $OUT/classes -cp "$CP" @$OUT/sources.txt 2>&1 | grep -v 'Picked up' | grep -v '^Note:'
echo "classes: $(find $OUT/classes -name '*.class' | wc -l)"

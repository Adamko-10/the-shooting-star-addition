#!/bin/bash
# run-main.sh <name> [-Dss05.clienttest.xxx=...]...: a fresh game dir with the local build, the harness and
# The Shooting Star 1.3.4; screenshots end up in shots/<name>/.
set -u
NAME=$1; shift
W=/home/claude/work/localdev
R=$W/run-$NAME
rm -rf $R && mkdir -p $R/mods $R/config $W/shots
cp $W/out/addon.jar $W/out/clienttest.jar $W/tss-1.3.4-neoforge.jar $R/mods/
cp /home/claude/work/run-client/options.txt $R/ 2>/dev/null || true
sed -i "s/^renderDistance:.*/renderDistance:${RD:-14}/; s/^simulationDistance:.*/simulationDistance:10/; s/^ao:.*/ao:false/; s/^biomeBlendRadius:.*/biomeBlendRadius:0/; s/^entityShadows:.*/entityShadows:false/" $R/options.txt
cp -r /home/claude/work/run-client/assets $R/
cd $R
LIBS=/home/claude/work/libs
MODULES=$(ls $LIBS/bootstraplauncher-*.jar $LIBS/securejarhandler-*.jar $LIBS/asm-*.jar $LIBS/JarJarFileSystems-*.jar | tr '\n' ':')
CP=$(ls $LIBS/*.jar /home/claude/work/lwjgl/natives/*.jar | grep -v -E "sources|userdev|installer|bootstraplauncher|securejarhandler|/asm-|JarJarFileSystems" | tr '\n' ':')
CP=${CP%:}
export LIBGL_ALWAYS_SOFTWARE=1
timeout ${TIMEOUT:-1500} xvfb-run -a -s '-screen 0 1280x720x24' java -Xmx6G -XX:+UseG1GC \
  -p "$MODULES" --add-modules ALL-MODULE-PATH \
  --add-opens java.base/java.util.jar=cpw.mods.securejarhandler \
  --add-opens java.base/java.lang.invoke=cpw.mods.securejarhandler \
  --add-exports java.base/sun.security.util=cpw.mods.securejarhandler \
  --add-exports jdk.naming.dns/com.sun.jndi.dns=java.naming \
  -Djava.net.preferIPv6Addresses=system -Dfml.earlyWindowControl=false \
  -DignoreList=mixinextras-neoforge-0.5.3.jar,client-extra,neoforge- \
  -DlegacyClassPath="$CP" -Dss05.clienttest=true "$@" \
  -cp "$CP" cpw.mods.bootstraplauncher.BootstrapLauncher \
  --launchTarget forgeclientdev --gameDir . --assetsDir assets --assetIndex 17 \
  --username Dev --uuid 380df991-f603-344c-a090-369bad2a924a --accessToken 0 --userType legacy --version 1.21.1 \
  --width 1280 --height 720 \
  --fml.fmlVersion 4.0.43 --fml.mcVersion 1.21.1 --fml.neoForgeVersion 21.1.248 --fml.neoFormVersion 20240808.144430 \
  > $R/client.out 2>&1
echo "exit $?"
rm -rf $W/shots/$NAME && mkdir -p $W/shots/$NAME && cp $R/screenshots/*.png $W/shots/$NAME/ 2>/dev/null
grep -E 'SS05|clienttest|Exception|ERROR' $R/client.out | grep -v -E 'Yggdrasil|icon_16|profile|Couldn.t look up|realms' | tail -40
ls $W/shots/$NAME | head -40

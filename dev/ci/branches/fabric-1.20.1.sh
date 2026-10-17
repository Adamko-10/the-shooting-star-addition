TSS_LOADER=fabric
TSS_FILES="1.3.3:9098051 1.3.4:9104576 1.4.0:9129722 1.4.1:9132133 1.4.2:9143863 1.4.3:9170986"
TSS_OBF="1.4.0 1.4.1 1.4.2 1.4.3"
GRADLE_JAVA="$JAVA_HOME_25_X64"
BUILD_ARGS=""
source .github/ci/client-test.sh
# Builds against the newest Fabric API for Minecraft 1.20.1 (gradle.properties names one; this run uses the newest).
ci_prepare() {
  local meta newest
  meta=$(curl -fsSL --retry 4 --retry-delay 5 https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml) || { fail "fabric api versions"; return; }
  newest=$(printf '%s\n' "$meta" | grep -oE '<version>[^<]+\+1\.20\.1</version>' | sed -E 's#</?version>##g' | sort -V | tail -1)
  if [ -n "$newest" ]; then
    sed -i "s/^fabric_api_version=.*/fabric_api_version=$newest/" gradle.properties
    ok "fabric api $newest"
  else
    fail "no fabric api for 1.20.1 in the maven metadata"
  fi
}
ci_tests() {
  client_test halley runClientTest -Dss05.clienttest.skill=halley -Dss05.clienttest.shots=0,100,250,300,330,345,400,500 -Dss05.clienttest.view=wide
  client_test moon runClientTest -Dss05.clienttest.skill=moon -Dss05.clienttest.shots=20,70,140,250,340,395,412,440,500,560 -Dss05.clienttest.view=aerial -Dss05.clienttest.film=true -Dss05.clienttest.hud=true -Dss05.clienttest.final=aerial,rim,cheese,menu -Dss05.clienttest.time=6000
  client_test tuned runClientTest -Dss05.clienttest.skill=moon -Dss05.clienttest.shots=150,399,417,455 -Dss05.clienttest.view=aerial -Dss05.clienttest.final=rim,skills -Dss05.clienttest.time=6000 -Dss05.clienttest.power=0.4 -Dss05.clienttest.speed=3 -Dss05.clienttest.damage=20
}

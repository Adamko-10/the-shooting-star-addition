TSS_LOADER=neoforge
TSS_FILES="1.3.3:9098077 1.3.4:9104569 1.4.0:9127477 1.4.1:9132156 1.4.2:9143845 1.4.3:9171007 1.4.4:9231715 1.5.0:9377531"
# RimuLib (The Shooting Star 1.5.0+ needs it): its CurseForge file id
RIMULIB_FILE=9377390
TSS_OBF="1.4.0 1.4.1 1.4.2 1.4.3 1.4.4 1.5.0"
GRADLE_JAVA="$JAVA_HOME_21_X64"
BUILD_ARGS=""
source .github/ci/client-test.sh
ci_tests() {
  client_test halley runProdClientTest -Dss05.clienttest.skill=halley -Dss05.clienttest.shots=0,100,250,300,330,345,400,500 -Dss05.clienttest.view=wide
  client_test moon runProdClientTest -Dss05.clienttest.skill=moon -Dss05.clienttest.shots=20,70,140,215,290,320,340,370,430,480 -Dss05.clienttest.view=aerial -Dss05.clienttest.film=true -Dss05.clienttest.hud=true -Dss05.clienttest.final=aerial,rim,cheese,menu -Dss05.clienttest.time=6000
  client_test tuned runProdClientTest -Dss05.clienttest.skill=moon -Dss05.clienttest.shots=150,324,342,380 -Dss05.clienttest.view=aerial -Dss05.clienttest.final=rim,skills -Dss05.clienttest.time=6000 -Dss05.clienttest.power=0.4 -Dss05.clienttest.speed=3 -Dss05.clienttest.damage=20
}

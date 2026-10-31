TSS_LOADER=fabric
TSS_FILES="1.3.3:9098051 1.3.4:9104576 1.4.0:9129722 1.4.1:9132133 1.4.2:9143863 1.4.3:9170986 1.4.4:9231726 1.5.0:9377519"
# RimuLib (The Shooting Star 1.5.0+ needs it): its CurseForge file id
RIMULIB_FILE=9377404
TSS_OBF="1.4.0 1.4.1 1.4.2 1.4.3 1.4.4 1.5.0"
GRADLE_JAVA="$JAVA_HOME_25_X64"
BUILD_ARGS=""
# 26.x opens its window through SDL, which finds no matching GLX visual on the virtual display: EGL instead
export SDL_VIDEO_FORCE_EGL=1
source .github/ci/client-test.sh
ci_tests() {
  client_test halley prodClientTest -Dss05.clienttest.skill=halley -Dss05.clienttest.shots=0,100,250,300,330,345,400,500 -Dss05.clienttest.view=wide
  client_test moon prodClientTest -Dss05.clienttest.skill=moon -Dss05.clienttest.shots=20,70,140,215,290,320,340,370,430,480 -Dss05.clienttest.view=aerial -Dss05.clienttest.film=true -Dss05.clienttest.hud=true -Dss05.clienttest.final=aerial,rim,cheese,menu -Dss05.clienttest.time=6000
  client_test tuned prodClientTest -Dss05.clienttest.skill=moon -Dss05.clienttest.shots=150,324,342,380 -Dss05.clienttest.view=aerial -Dss05.clienttest.final=rim,skills -Dss05.clienttest.time=6000 -Dss05.clienttest.power=0.4 -Dss05.clienttest.speed=3 -Dss05.clienttest.damage=20
}

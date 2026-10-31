TSS_LOADER=forge
TSS_FILES="1.3.3:9098070 1.3.4:9104570 1.4.0:9127469 1.4.1:9132153 1.4.2:9143858 1.4.3:9171001 1.4.4:9231721 1.5.0:9377538"
# RimuLib (The Shooting Star 1.5.0+ needs it): its CurseForge file id
RIMULIB_FILE=9377397
TSS_OBF="1.4.0 1.4.1 1.4.2 1.4.3 1.4.4 1.5.0"
GRADLE_JAVA="$JAVA_HOME_21_X64"
BUILD_ARGS=""
source .github/ci/client-test.sh
source .github/ci/servertest.sh
ci_tests() {
  # The Shooting Star 1.5.0 calls its own methods by obfuscated name at run time, so its readable copy (what the dev
  # runs load) can't run, and this branch has no production-style run yet: the build's pack check is the test here.
  log "in-game tests skipped (no production-style run on this branch)"
}


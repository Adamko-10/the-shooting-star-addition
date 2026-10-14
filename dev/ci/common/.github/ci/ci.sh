#!/usr/bin/env bash
# CI for The Shooting Star Addition (wip/... branches only). Fetches The Shooting Star, makes its mappings, builds,
# runs the branch's tests and uploads the results to a draft release. Logs and mappings are encrypted (they mention
# The Shooting Star's internals); jars and screenshots are not.
set -uo pipefail
source .github/ci/branch.sh

OUT="$PWD/ci-out"
SECRET="$OUT/secret"
mkdir -p "$OUT/jars" "$SECRET" libs
STATUS=0
log() { echo "[ci] $*"; }
fail() { log "FAILED: $*"; echo "FAILED: $*" >> "$OUT/summary.txt"; STATUS=1; }
ok() { log "ok: $*"; echo "ok: $*" >> "$OUT/summary.txt"; }

# ---- The Shooting Star (for this run only) ----
for entry in $TSS_FILES; do
  ver=${entry%%:*}; id=${entry##*:}
  file="libs/the-shooting-star-demo-${ver}-${TSS_LOADER}.jar"
  if curl -fsSL --retry 4 --retry-delay 5 -o "$file" \
      "https://www.cursemaven.com/curse/maven/the-shooting-star-demo-1719195/${id}/the-shooting-star-demo-1719195-${id}.jar"; then
    log "fetched The Shooting Star $ver ($TSS_LOADER)"
  else
    fail "download of The Shooting Star $ver"
  fi
done

# ---- Anything the branch needs to set up first (defined in branch.sh) ----
if declare -f ci_prepare > /dev/null; then
  ci_prepare
fi

export JAVA_HOME="$GRADLE_JAVA"
export PATH="$JAVA_HOME/bin:$PATH"
G() { ./gradlew --no-daemon --stacktrace "$@"; }
# Runs Gradle into a log; when it fails on a network hiccup (a 5xx from a Maven, a reset, a timeout) it waits and
# tries again, up to four times.
GR() {
  local log="$1"; shift
  local try
  for try in 1 2 3 4; do
    if G "$@" > "$log" 2>&1; then return 0; fi
    # (also Loom on Minecraft 1.20.1 the first time the readable The Shooting Star is made while the project configures:
    # "Cannot mutate content repository descriptor ... after repository has been used"; the next run finds it made)
    if ! grep -qE 'status code 5[0-9][0-9]|Connection reset|Read timed out|connect timed out|Remote host terminated|Premature end of Content|Could not HEAD|Could not GET|Cannot mutate content repository descriptor' "$log"; then
      return 1
    fi
    log "network trouble (try $try), waiting $((try * 60)) s"
    cp "$log" "$log.try$try"
    sleep $((try * 60))
  done
  return 1
}

# ---- Mappings, oldest obfuscated version first (each one is matched against the one before it) ----
for v in $TSS_OBF; do
  if GR "$SECRET/mappings-$v.log" tssMappings -PtssJar="$v"; then ok "mapping $v"; else fail "mapping $v"; fi
done
[ -d libs/mappings ] && cp -r libs/mappings "$SECRET/"

# ---- Build ----
if GR "$SECRET/build.log" build $BUILD_ARGS; then
  ok "build"
  cp build/libs/*.jar "$OUT/jars/" 2>/dev/null
else
  fail "build"
  grep -E '\.java:[0-9]+: error:' "$SECRET/build.log" | grep -v 'cyou/rimuru' | sort -u | head -60 || true
fi

# ---- The readable The Shooting Star's skill interface (the method names Halley and Luna override; logs only) ----
for j in build/shooting-star-named/*.jar; do
  [ -f "$j" ] || continue
  for c in $(unzip -Z1 "$j" | grep -E '/magic/Skill\.class$'); do
    echo "== $(basename "$j") $c" >> "$SECRET/skill-api.txt"
    "$JAVA_HOME/bin/javap" -cp "$j" -p "${c%.class}" >> "$SECRET/skill-api.txt" 2>&1 || true
  done
done

# ---- The branch's own tests (defined in branch.sh) ----
if [ "$STATUS" = 0 ] && declare -f ci_tests > /dev/null; then
  ci_tests
fi

# ---- Hand back ----
printf '%s\n' "$GITHUB_SHA" > "$OUT/commit.txt"
tar czf "$OUT/secret.tgz" -C "$OUT" secret
openssl rand -hex 32 > "$OUT/k"
openssl enc -aes-256-cbc -pbkdf2 -salt -in "$OUT/secret.tgz" -out "$OUT/secret.tgz.enc" -pass file:"$OUT/k"
openssl pkeyutl -encrypt -pubin -inkey .github/ci/ci-key.pub.pem -in "$OUT/k" -out "$OUT/secret.key.enc"
rm -rf "$OUT/k" "$OUT/secret.tgz" "$SECRET"
BRANCH="${GITHUB_REF_NAME#wip/}"
TAG="ci-${BRANCH//[^A-Za-z0-9.]/-}-${GITHUB_RUN_NUMBER}"
FILES=("$OUT"/secret.tgz.enc "$OUT"/secret.key.enc "$OUT"/summary.txt "$OUT"/commit.txt)
for f in "$OUT"/jars/*.jar "$OUT"/*.zip; do [ -f "$f" ] && FILES+=("$f"); done
gh release create "$TAG" --draft --target "$GITHUB_SHA" --title "CI $BRANCH #$GITHUB_RUN_NUMBER" \
  --notes "Work-in-progress build of $GITHUB_SHA (draft, not a release)." "${FILES[@]}" || log "upload failed"
cat "$OUT/summary.txt"
exit "$STATUS"

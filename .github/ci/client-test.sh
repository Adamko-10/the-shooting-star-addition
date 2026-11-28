#!/usr/bin/env bash
# One run of the in-game screenshot harness under a virtual display with software OpenGL:
#   client_test <name> <gradle task> <-D properties...>
# Screenshots end up in ci-out/shots-<name>.zip, the log in the encrypted part.
client_setup() {
  # xvfb is often there already, Mesa's EGL (which 26.x opens its window through) often isn't: check both
  if ! command -v xvfb-run > /dev/null || ! ldconfig -p | grep -q 'libEGL_mesa.so.0'; then
    sudo apt-get update -qq > /dev/null && sudo apt-get install -y -qq xvfb libgl1-mesa-dri mesa-utils libegl1 libegl-mesa0 > /dev/null
  fi
}
client_test() {
  local name="$1" task="$2"; shift 2
  # the production-style run (the release jar with The Shooting Star's own jar) has its own game directory
  local dir=run/clienttest
  case "$task" in *rodClientTest*) dir=run/prodclient ;; esac
  client_setup
  rm -rf "$dir/screenshots"
  # Forge's early loading window wants OpenGL 4.6, which the software renderer doesn't offer: switch it off
  mkdir -p run/clienttest/config
  printf 'earlyWindowControl = false\n' > run/clienttest/config/fml.toml
  if LIBGL_ALWAYS_SOFTWARE=1 timeout 1500 xvfb-run -a -s '-screen 0 1280x720x24' ./gradlew --no-daemon "$task" "$@" \
      > "$SECRET/client-$name.log" 2>&1; then
    :
  fi
  if ls "$dir"/screenshots/*.png > /dev/null 2>&1; then
    (cd "$dir/screenshots" && zip -q -r "$OUT/shots-$name.zip" .)
    ok "client test $name ($(ls "$dir"/screenshots/*.png | wc -l) screenshots)"
  else
    fail "client test $name (no screenshots)"
  fi
  grep -a -E 'never attached|Exception|ERROR' "$SECRET/client-$name.log" | grep -a -v -E 'Realms|authlib|Yggdrasil|profile|OpenAL|SoundSystem|telemetry' | head -5 || true
}

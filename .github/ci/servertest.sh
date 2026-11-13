server_test() {
  local name="$1"; shift
  mkdir -p run/servertest && echo "eula=true" > run/servertest/eula.txt
  rm -f run/servertest/logs/latest.log
  timeout 1500 ./gradlew --no-daemon runServerTest "$@" > "$SECRET/servertest-$name.log" 2>&1 || true
  grep -a 'SS05TEST' run/servertest/logs/latest.log > "$OUT/servertest-$name.txt" 2>/dev/null || true
  local summary
  summary=$(grep -a 'SUMMARY' "$OUT/servertest-$name.txt" | tail -1)
  local pass total
  pass=$(echo "$summary" | sed -n 's/.*SUMMARY \([0-9]*\)\/\([0-9]*\).*/\1/p')
  total=$(echo "$summary" | sed -n 's/.*SUMMARY \([0-9]*\)\/\([0-9]*\).*/\2/p')
  if [ -n "$pass" ] && [ "$pass" = "$total" ]; then ok "server test $name: $pass/$total"; else fail "server test $name: ${summary:-no summary}"; grep -a 'FAIL' "$OUT/servertest-$name.txt" | head -20; fi
}

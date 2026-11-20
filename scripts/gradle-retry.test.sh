#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Checks scripts/gradle-retry.sh with a fake Gradle that fails as told for its first N runs, and
# that the workflows run Gradle through it.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
script="$here/gradle-retry.sh"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
failures=0

# A Gradle stand-in: logs its arguments, prints "out" to stdout and "err" to stderr, and for its
# first $FAKE_FAILS runs prints $FAKE_MESSAGE to stderr and exits 1.
cat > "$work/gradlew" <<'EOF'
#!/usr/bin/env bash
echo "$*" >> "$FAKE_CALLS"
runs=$(wc -l < "$FAKE_CALLS")
echo out
echo err >&2
if [[ $runs -le $FAKE_FAILS ]]; then echo "$FAKE_MESSAGE" >&2; exit 1; fi
EOF
chmod +x "$work/gradlew"

check() { # name fails message expected-status expected-calls
  local calls="$work/calls" status=0
  : > "$calls"
  FAKE_CALLS="$calls" FAKE_FAILS="$2" FAKE_MESSAGE="$3" GRADLE_RETRY_PAUSE_SECONDS=0 \
    "$script" "$work/gradlew" build > "$work/stdout" 2> "$work/stderr" || status=$?
  local actual
  actual="$(paste -sd'|' "$calls")"
  if [[ $status -eq $4 && "$actual" == "$5" ]]; then
    echo "ok   $1"
  else
    echo "FAIL $1: expected exit $4 and calls '$5', got exit $status and calls '$actual'"
    failures=$((failures + 1))
  fi
}

miss='> Could not find org.jetbrains.kotlin:kotlin-stdlib:2.4.20.'
throttled="> Could not resolve org.jetbrains.kotlin:kotlin-stdlib:2.4.20. Received status code 429 from server: Too Many Requests"

check "a green command runs once" 0 "" 0 "build"
check "a miss retries with refreshed dependencies" 1 "$miss" 0 "build|build --refresh-dependencies"
check "a plugin miss retries with refreshed dependencies" 1 "Plugin [id: 'x'] was not found in any of the following sources:" 0 "build|build --refresh-dependencies"
check "a 429 retries without refreshing" 1 "$throttled" 0 "build|build"
check "a 5xx retries without refreshing" 1 "Could not GET 'https://x'. Received status code 502 from server" 0 "build|build"
check "a second failure is the result" 2 "$miss" 1 "build|build --refresh-dependencies"
check "a test failure is not retried" 1 "> There were failing tests." 1 "build"

# The streams stay apart, so `$(... -q ...)` captures stdout only.
: > "$work/calls"
FAKE_CALLS="$work/calls" FAKE_FAILS=0 FAKE_MESSAGE="" "$script" "$work/gradlew" build > "$work/stdout" 2> "$work/stderr"
if [[ "$(cat "$work/stdout")" == out && "$(cat "$work/stderr")" == err ]]; then
  echo "ok   stdout and stderr stay apart"
else
  echo "FAIL stdout and stderr stay apart: stdout '$(cat "$work/stdout")', stderr '$(cat "$work/stderr")'"
  failures=$((failures + 1))
fi

# Every Gradle call in the pull request workflows goes through the wrapper. Allowed directly: the
# daemon stop, which resolves nothing.
workflows=(ci verify templates render-evidence obfuscated-templates font-atlas build-logic-macos)
for workflow in "${workflows[@]}"; do
  file="$here/../.github/workflows/$workflow.yml"
  while IFS= read -r line; do
    echo "FAIL $workflow.yml runs Gradle without scripts/gradle-retry.sh: $line"
    failures=$((failures + 1))
  done < <(grep -nE '(^|[^-/[:alnum:]])\./gradlew' "$file" | grep -v 'gradle-retry.sh' | grep -vE '^[0-9]+:\s*#' | grep -v 'gradlew --stop' || true)
done
[[ $failures -gt 0 ]] || echo "ok   the pull request workflows run Gradle through the wrapper"

[[ $failures -eq 0 ]] || { echo "$failures case(s) failed"; exit 1; }

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
# first $FAKE_FAILS runs prints $FAKE_MESSAGE to stderr and exits 1. From run $FAKE_THEN_AT on, a
# failing run prints $FAKE_THEN instead.
cat > "$work/gradlew" <<'EOF'
#!/usr/bin/env bash
echo "$*" >> "$FAKE_CALLS"
runs=$(wc -l < "$FAKE_CALLS")
echo out
echo err >&2
if [[ $runs -le $FAKE_FAILS ]]; then
  if [[ $runs -ge ${FAKE_THEN_AT:-999} ]]; then echo "$FAKE_THEN" >&2; else echo "$FAKE_MESSAGE" >&2; fi
  exit 1
fi
EOF
chmod +x "$work/gradlew"

# A sleep stand-in on PATH, so the pauses are recorded instead of waited.
mkdir "$work/bin"
cat > "$work/bin/sleep" <<'EOF'
#!/usr/bin/env bash
echo "$1" >> "$FAKE_SLEEPS"
EOF
chmod +x "$work/bin/sleep"

check() { # name fails message expected-status expected-calls expected-pauses
  local calls="$work/calls" sleeps="$work/sleeps" status=0
  : > "$calls"
  : > "$sleeps"
  PATH="$work/bin:$PATH" FAKE_CALLS="$calls" FAKE_SLEEPS="$sleeps" FAKE_FAILS="$2" FAKE_MESSAGE="$3" \
    "$script" "$work/gradlew" build > "$work/stdout" 2> "$work/stderr" || status=$?
  local actual waited
  actual="$(paste -sd'|' "$calls")"
  waited="$(paste -sd' ' "$sleeps")"
  if [[ $status -eq $4 && "$actual" == "$5" && "$waited" == "$6" ]]; then
    echo "ok   $1"
  else
    echo "FAIL $1: expected exit $4, calls '$5' and pauses '$6', got exit $status, calls '$actual' and pauses '$waited'"
    failures=$((failures + 1))
  fi
}

miss='> Could not find org.jetbrains.kotlin:kotlin-stdlib:2.4.20.'
throttled="> Could not resolve org.jetbrains.kotlin:kotlin-stdlib:2.4.20. Received status code 429 from server: Too Many Requests"

retried="build --info"
check "a green command runs once" 0 "" 0 "build" ""
check "a miss retries the same command, never refreshed" 1 "$miss" 0 "build|$retried" "60"
check "a plugin miss retries the same command" 1 "Plugin [id: 'x'] was not found in any of the following sources:" 0 "build|$retried" "60"
check "a 429 retries without refreshing" 1 "$throttled" 0 "build|build --info" "60"
check "a 5xx retries without refreshing" 1 "Could not GET 'https://x'. Received status code 502 from server" 0 "build|build --info" "60"
check "a miss that outlasts two retries never adds a refresh" 3 "$miss" 0 "build|$retried|$retried|$retried" "60 180 300"
check "an outage that outlasts two retries passes on the third" 3 "$throttled" 0 "build|build --info|build --info|build --info" "60 180 300"
check "a fourth failure is the result, after three retries" 4 "$throttled" 1 "build|build --info|build --info|build --info" "60 180 300"
check "a test failure is not retried" 1 "> There were failing tests." 1 "build" ""
FAKE_THEN_AT=2 FAKE_THEN="> There were failing tests." \
  check "a test failure after a retry is the result" 3 "$throttled" 1 "build|build --info" "60"
GRADLE_RETRY_PAUSES="5" check "GRADLE_RETRY_PAUSES sets the retries and their pauses" 2 "$throttled" 1 "build|build --info" "5"

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

# The wrapper is a bash script: in a job that can run on Windows, whose default shell is PowerShell,
# a step that calls it must say `shell: bash`, or PowerShell opens the file and runs no Gradle.
windows_steps="$(python3 - "$here/../.github/workflows" <<'PY'
import glob, os, sys, yaml
for path in sorted(glob.glob(os.path.join(sys.argv[1], "*.yml"))):
    for name, job in (yaml.safe_load(open(path)).get("jobs") or {}).items():
        runs_on, matrix = str(job.get("runs-on", "")), str((job.get("strategy") or {}).get("matrix", ""))
        if "windows" not in runs_on and not ("matrix" in runs_on and "windows" in matrix):
            continue
        if (job.get("defaults") or {}).get("run", {}).get("shell"):
            continue
        for step in job.get("steps", []):
            if "gradle-retry.sh" in (step.get("run") or "") and not step.get("shell"):
                print(f"{os.path.basename(path)} {name}: {step.get('name')}")
PY
)"
if [[ -n "$windows_steps" ]]; then
  while IFS= read -r line; do echo "FAIL a step that can run on Windows calls the wrapper without shell: bash: $line"; done <<< "$windows_steps"
  failures=$((failures + 1))
else
  echo "ok   a step that can run on Windows runs the wrapper in bash"
fi

[[ $failures -eq 0 ]] || { echo "$failures case(s) failed"; exit 1; }

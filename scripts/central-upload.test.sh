#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Checks scripts/central-upload.sh against a stand-in for curl that answers as Central's Publisher API
# does, one case at a time. Nothing leaves the machine.
set -euo pipefail

script="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/central-upload.sh"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
cd "$work"
mkdir bin
printf 'zip\n' > bundle.zip

# Logs each call, answers an upload with a deployment id, a status request with the next state from
# ./states, and a drop with nothing.
cat > bin/curl <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$STUB_DIR/calls.log"
url="${!#}"
case "$url" in
  */published*)
    if [[ -f "$STUB_DIR/refuse-published" ]]; then echo 'Bad Gateway' && exit 22; fi
    if [[ -f "$STUB_DIR/published" ]]; then echo '{"published":true}'; else echo '{"published":false}'; fi ;;
  */upload*)
    if [[ -f "$STUB_DIR/refuse-upload" ]]; then echo '{"error":"Invalid token"}' && exit 22; fi
    echo '"dep-1"' ;;
  */status*)
    state="$(head -n 1 "$STUB_DIR/states")"
    tail -n +2 "$STUB_DIR/states" > "$STUB_DIR/states.next" && mv "$STUB_DIR/states.next" "$STUB_DIR/states"
    if [[ "$state" == DROPPED_REQUEST ]]; then echo 'Bad Gateway' && exit 22; fi
    printf '{"deploymentId":"dep-1","deploymentState":"%s","errors":{"com.example:lib":["Missing signature"]}}\n' "$state" ;;
esac
EOF
chmod +x bin/curl

export STUB_DIR="$work" PATH="$work/bin:$PATH" CENTRAL_POLL_SECONDS=0
export MAVEN_CENTRAL_USERNAME=user MAVEN_CENTRAL_PASSWORD=pass

failures=0
pass() { echo "ok   $1"; }
fail() { echo "FAIL $1"; failures=$((failures + 1)); }
states() { rm -f calls.log && printf '%s\n' "$@" > states; }
calls() { grep -c "$1" calls.log || true; }

states PENDING VALIDATING VALIDATED PUBLISHING
if "$script" bundle.zip "awake v0.5.0" > out.txt; then pass "publishes once Central starts publishing"; else fail "publishes once Central starts publishing"; fi
if grep -q 'upload?name=awake%20v0.5.0&publishingType=AUTOMATIC' calls.log; then pass "names the deployment, AUTOMATIC by default"; else fail "names the deployment: $(cat calls.log)"; fi
if grep -q 'bundle=@bundle.zip' calls.log; then pass "sends the bundle"; else fail "sends the bundle"; fi
# base64 of user:pass
if [[ "$(calls 'Authorization: Bearer dXNlcjpwYXNz')" -eq 5 ]]; then pass "authenticates every call with the user token"; else fail "authenticates every call"; fi
if [[ "$(calls 'status?id=dep-1')" -eq 4 && "$(calls DELETE)" -eq 0 ]]; then pass "waits past VALIDATED, drops nothing"; else fail "waits past VALIDATED: $(cat calls.log)"; fi

states PENDING VALIDATED
if "$script" bundle.zip check USER_MANAGED > out.txt; then pass "a check passes once validated"; else fail "a check passes once validated"; fi
if grep -q 'publishingType=USER_MANAGED' calls.log && grep -q 'DELETE .*deployment/dep-1' calls.log; then
  pass "a check drops its deployment"; else fail "a check drops its deployment: $(cat calls.log)"; fi

states VALIDATING FAILED
if "$script" bundle.zip "awake v0.5.0" > out.txt 2> err.txt; then fail "a refused deployment fails"; else
  if grep -q 'Missing signature' err.txt && [[ "$(calls DELETE)" -eq 0 ]]; then pass "a refused deployment fails, kept, with Central's errors"; else
    fail "a refused deployment fails: $(cat err.txt)"; fi
fi

states VALIDATING DROPPED_REQUEST PUBLISHING
if "$script" bundle.zip "awake v0.5.0" > out.txt 2> err.txt; then pass "asks again after a failed status request"; else
  fail "asks again after a failed status request: $(cat err.txt)"; fi

touch refuse-upload && states PUBLISHING
if "$script" bundle.zip "awake v0.5.0" > out.txt 2> err.txt; then fail "a refused upload fails"; else
  if grep -q 'Invalid token' err.txt; then pass "a refused upload fails with Central's reason"; else fail "a refused upload fails: $(cat err.txt)"; fi
fi
rm refuse-upload

states VALIDATING VALIDATING
if CENTRAL_TIMEOUT_SECONDS=0 "$script" bundle.zip "awake v0.5.0" > out.txt 2> err.txt; then fail "gives up after the timeout"; else
  if grep -q 'still VALIDATING' err.txt; then pass "gives up after the timeout"; else fail "gives up after the timeout: $(cat err.txt)"; fi
fi

states PUBLISHING
if MAVEN_CENTRAL_PASSWORD='' "$script" bundle.zip "awake v0.5.0" > /dev/null 2>&1; then fail "refuses to run without a token"; else
  if [[ ! -f calls.log ]]; then pass "refuses to run without a token, before any call"; else fail "refuses to run without a token"; fi
fi

if "$script" bundle.zip "awake v0.5.0" PUBLISH > /dev/null 2>&1; then fail "refuses an unknown publishing type"; else pass "refuses an unknown publishing type"; fi

# A real bundle, laid out as central-bundle.sh zips a staging repository.
mkdir -p staged/com/example/lib/1.0.0
printf '<project/>\n' > staged/com/example/lib/1.0.0/lib-1.0.0.pom
(cd staged && zip -qrX ../release.zip .)

touch published && states PUBLISHING
if "$script" release.zip "awake v1.0.0" > out.txt 2> err.txt; then
  if [[ "$(calls upload)" -eq 0 ]] && grep -q 'com.example:lib:1.0.0 is already published' out.txt; then
    pass "a published version is not uploaded again"; else fail "a published version is not uploaded again: $(cat calls.log)"; fi
else fail "a re-run of a published version succeeds: $(cat err.txt)"; fi
if grep -q 'published?namespace=com.example&name=lib&version=1.0.0' calls.log; then
  pass "asks Central about the bundle's own coordinates"; else fail "asks about the bundle's coordinates: $(cat calls.log)"; fi
rm published

states PUBLISHING
if "$script" release.zip "awake v1.0.0" > out.txt 2> err.txt && [[ "$(calls upload)" -eq 1 ]]; then
  pass "an unpublished version is uploaded"; else fail "an unpublished version is uploaded: $(cat calls.log)"; fi

touch refuse-published && states PUBLISHING
if "$script" release.zip "awake v1.0.0" > out.txt 2> err.txt && [[ "$(calls upload)" -eq 1 ]] && grep -q 'uploading' err.txt; then
  pass "uploads when Central cannot say whether it is published"; else fail "uploads when the check fails: $(cat err.txt)"; fi
rm refuse-published

touch published && states VALIDATED
if "$script" release.zip check USER_MANAGED > out.txt 2> err.txt && [[ "$(calls published)" -eq 0 && "$(calls upload)" -eq 1 ]]; then
  pass "a check does not ask whether the version is published"; else fail "a check asks nothing: $(cat calls.log)"; fi
rm published

exit "$failures"

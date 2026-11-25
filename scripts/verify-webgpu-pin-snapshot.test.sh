#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Checks scripts/verify-webgpu-pin-snapshot.sh against a stand-in for curl serving local fixtures
# for both KOTLIN_ARCHIVE (inline wasmJs variants) and legacy split (webgpu-wasm-js available-at)
# publications. Nothing leaves the machine.
set -euo pipefail

script="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/verify-webgpu-pin-snapshot.sh"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
cd "$work"
mkdir -p bin repo

cat > bin/curl <<'EOF'
#!/usr/bin/env bash
url="${!#}"
rel="${url#https://snapshots.test/}"
path="$STUB_REPO/$rel"
if [[ -f "$path" ]]; then
  cat "$path"
else
  exit 22
fi
EOF
chmod +x bin/curl

export STUB_REPO="$work/repo" PATH="$work/bin:$PATH"
BASE="https://snapshots.test"

failures=0
pass() { echo "ok   $1"; }
fail() { echo "FAIL $1"; failures=$((failures + 1)); }

write_metadata() {
  local artifact="$1" core="$2" ts="$3" bn="$4"
  mkdir -p "$STUB_REPO/$artifact/$core-SNAPSHOT"
  cat > "$STUB_REPO/$artifact/$core-SNAPSHOT/maven-metadata.xml" <<EOF
<metadata>
  <versioning>
    <snapshot>
      <timestamp>$ts</timestamp>
      <buildNumber>$bn</buildNumber>
    </snapshot>
  </versioning>
</metadata>
EOF
}

write_module() {
  local artifact="$1" core="$2" ts="$3" bn="$4" body="$5"
  mkdir -p "$STUB_REPO/$artifact/$core-SNAPSHOT"
  printf '%s\n' "$body" > "$STUB_REPO/$artifact/$core-SNAPSHOT/$artifact-$core-$ts-$bn.module"
}

# 1. KOTLIN_ARCHIVE inline wasmJs variants strictly pinned to Core 0.74.0 -> passes.
rm -rf "$STUB_REPO"/*
write_metadata webgpu 0.74.0 20261124.221925 1
write_module webgpu 0.74.0 20261124.221925 1 '{
  "variants": [
    {
      "name": "wasmJsRuntimeElements-published",
      "dependencies": [
        {"group": "com.awakekt.awake.core", "module": "math", "version": {"requires": "0.74.0"}},
        {"group": "io.ygdrasil", "module": "wgpu4k", "version": {"requires": "0.0.1-SNAPSHOT"}}
      ],
      "dependencyConstraints": [
        {"group": "com.awakekt.awake.core", "module": "math", "version": {"strictly": "0.74.0"}}
      ]
    },
    {
      "name": "desktopRuntimeElements-published",
      "available-at": {"url": "../../webgpu-desktop/0.74.0-SNAPSHOT/webgpu-desktop-0.74.0-SNAPSHOT.module", "group": "com.awakekt.awake.backend", "module": "webgpu-desktop", "version": "0.74.0-SNAPSHOT"}
    }
  ]
}'
if "$script" 0.74.0 "$BASE" > out.txt 2> err.txt && grep -q 'via webgpu' out.txt; then
  pass "KOTLIN_ARCHIVE inline wasmJs variant passes"
else
  fail "KOTLIN_ARCHIVE inline wasmJs variant: $(cat out.txt err.txt)"
fi

# 2. Legacy split publication redirecting wasmJsRuntimeElements-published to webgpu-wasm-js -> passes.
rm -rf "$STUB_REPO"/*
write_metadata webgpu 0.62.0 20261120.100000 2
write_module webgpu 0.62.0 20261120.100000 2 '{
  "variants": [
    {
      "name": "wasmJsRuntimeElements-published",
      "available-at": {"url": "../../webgpu-wasm-js/0.62.0-SNAPSHOT/webgpu-wasm-js-0.62.0-SNAPSHOT.module", "group": "com.awakekt.awake.backend", "module": "webgpu-wasm-js", "version": "0.62.0-SNAPSHOT"}
    }
  ]
}'
write_metadata webgpu-wasm-js 0.62.0 20261120.100001 2
write_module webgpu-wasm-js 0.62.0 20261120.100001 2 '{
  "variants": [
    {
      "name": "wasmJsRuntimeElements-published",
      "dependencies": [
        {"group": "com.awakekt.awake.core", "module": "math", "version": {"requires": "0.62.0"}}
      ],
      "dependencyConstraints": [
        {"group": "com.awakekt.awake.core", "module": "math", "version": {"strictly": "0.62.0"}}
      ]
    }
  ]
}'
if "$script" 0.62.0 "$BASE" > out.txt 2> err.txt && grep -q 'via webgpu-wasm-js' out.txt; then
  pass "legacy available-at webgpu-wasm-js redirect passes"
else
  fail "legacy available-at redirect: $(cat out.txt err.txt)"
fi

# 3. Missing wasmJsRuntimeElements-published variant -> fails even if metadata variant has deps.
rm -rf "$STUB_REPO"/*
write_metadata webgpu 0.74.0 20261124.221925 1
write_module webgpu 0.74.0 20261124.221925 1 '{
  "variants": [
    {
      "name": "metadataApiElements-published",
      "dependencies": [
        {"group": "com.awakekt.awake.core", "module": "math", "version": {"requires": "0.74.0"}}
      ],
      "dependencyConstraints": [
        {"group": "com.awakekt.awake.core", "module": "math", "version": {"strictly": "0.74.0"}}
      ]
    }
  ]
}'
if ! "$script" 0.74.0 "$BASE" > out.txt 2> err.txt && grep -q 'no wasmJsRuntimeElements-published variant' err.txt; then
  pass "rejects module missing wasmJsRuntimeElements-published"
else
  fail "missing wasmJsRuntimeElements-published should fail: $(cat out.txt err.txt)"
fi

# 4. Dependency pointing to a snapshot or wrong Core version -> fails.
rm -rf "$STUB_REPO"/*
write_metadata webgpu 0.74.0 20261124.221925 1
write_module webgpu 0.74.0 20261124.221925 1 '{
  "variants": [
    {
      "name": "wasmJsRuntimeElements-published",
      "dependencies": [
        {"group": "com.awakekt.awake.core", "module": "math", "version": {"requires": "0.74.1-SNAPSHOT"}}
      ],
      "dependencyConstraints": [
        {"group": "com.awakekt.awake.core", "module": "math", "version": {"strictly": "0.74.0"}}
      ]
    }
  ]
}'
if ! "$script" 0.74.0 "$BASE" > out.txt 2> err.txt; then
  pass "rejects wrong Core dependency version"
else
  fail "wrong Core dependency version should fail"
fi

# 5. Missing strictly constraint for a Core dependency -> fails.
rm -rf "$STUB_REPO"/*
write_metadata webgpu 0.74.0 20261124.221925 1
write_module webgpu 0.74.0 20261124.221925 1 '{
  "variants": [
    {
      "name": "wasmJsRuntimeElements-published",
      "dependencies": [
        {"group": "com.awakekt.awake.core", "module": "math", "version": {"requires": "0.74.0"}}
      ],
      "dependencyConstraints": []
    }
  ]
}'
if ! "$script" 0.74.0 "$BASE" > out.txt 2> err.txt; then
  pass "rejects missing strictly constraint"
else
  fail "missing strictly constraint should fail"
fi

# 6. Wrong strictly constraint version -> fails.
rm -rf "$STUB_REPO"/*
write_metadata webgpu 0.74.0 20261124.221925 1
write_module webgpu 0.74.0 20261124.221925 1 '{
  "variants": [
    {
      "name": "wasmJsRuntimeElements-published",
      "dependencies": [
        {"group": "com.awakekt.awake.core", "module": "math", "version": {"requires": "0.74.0"}}
      ],
      "dependencyConstraints": [
        {"group": "com.awakekt.awake.core", "module": "math", "version": {"strictly": "0.73.1"}}
      ]
    }
  ]
}'
if ! "$script" 0.74.0 "$BASE" > out.txt 2> err.txt; then
  pass "rejects wrong strictly constraint version"
else
  fail "wrong strictly constraint version should fail"
fi

# 7. Missing snapshot metadata (404) -> fails.
rm -rf "$STUB_REPO"/*
if ! "$script" 0.74.0 "$BASE" > out.txt 2> err.txt; then
  pass "rejects missing maven-metadata.xml"
else
  fail "missing maven-metadata.xml should fail"
fi

exit "$failures"

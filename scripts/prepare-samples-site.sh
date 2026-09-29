#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail

site_dir="${1:-build/samples-site}"

find_bundle_dir() {
  local sample="$1"
  local dist_dir="samples/$sample/build/dist/wasmJs/productionExecutable"
  local webpack_dir="samples/$sample/build/kotlin-webpack/wasmJs/productionExecutable"

  if [[ -d "$dist_dir" ]]; then
    printf '%s' "$dist_dir"
  elif [[ -d "$webpack_dir" ]]; then
    printf '%s' "$webpack_dir"
  else
    return 1
  fi
}

engine_dist="$(find_bundle_dir engine-showcase || true)"
ui_dist="$(find_bundle_dir ui-showcase || true)"

[[ -n "$engine_dist" ]] || {
  echo "Missing engine showcase output: $engine_dist" >&2
  echo "Run :samples:engine-showcase:wasmJsBrowserProductionWebpack first." >&2
  exit 1
}
[[ -n "$ui_dist" ]] || {
  echo "Missing UI showcase output: $ui_dist" >&2
  echo "Run :samples:ui-showcase:wasmJsBrowserProductionWebpack first." >&2
  exit 1
}

rm -rf "$site_dir"
mkdir -p "$site_dir/engine" "$site_dir/ui"
cp -R "$engine_dist/." "$site_dir/engine/"
cp -R "$ui_dist/." "$site_dir/ui/"

# The webpack output holds only the JS/Wasm bundle. index.html and the runtime assets (models,
# scenes, textures) are processed resources; without them Pages answers each asset request with
# its HTML fallback.
for sample in engine:engine-showcase ui:ui-showcase; do
  resources="samples/${sample#*:}/build/processedResources/wasmJs/main"
  [[ -d "$resources" ]] && cp -R "$resources/." "$site_dir/${sample%%:*}/"
done

for bundle_dir in "$site_dir/engine" "$site_dir/ui"; do
  # Every script, not the first one found: awake-loader.js sits beside the bundle.
  bundle_scripts=("$bundle_dir"/*.js)
  for wasm_file in "$bundle_dir"/*.wasm; do
    [[ -e "$wasm_file" ]] || continue
    wasm_name="$(basename "$wasm_file")"
    if ! grep -Fq "$wasm_name" "${bundle_scripts[@]}"; then
      rm "$wasm_file"
    fi
  done
done

# The index page lives in website/samples; the mark is shared with the landing page.
cp website/samples/* "$site_dir/"
cp website/landing/awake-mark.svg "$site_dir/"

cat > "$site_dir/_headers" <<'EOF'
/engine/*.wasm
  Cache-Control: public, max-age=31536000, immutable
/ui/*.wasm
  Cache-Control: public, max-age=31536000, immutable
EOF

echo "Prepared samples site at $site_dir"

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

# The webpack output directory contains the JS/Wasm bundle; the HTML entry point is a processed
# resource and is emitted beside it only for some Kotlin plugin versions.
if [[ -f samples/engine-showcase/build/processedResources/wasmJs/main/index.html ]]; then
  cp samples/engine-showcase/build/processedResources/wasmJs/main/index.html "$site_dir/engine/index.html"
fi
if [[ -f samples/ui-showcase/build/processedResources/wasmJs/main/index.html ]]; then
  cp samples/ui-showcase/build/processedResources/wasmJs/main/index.html "$site_dir/ui/index.html"
fi

for bundle_dir in "$site_dir/engine" "$site_dir/ui"; do
  bundle_js="$(find "$bundle_dir" -maxdepth 1 -type f -name '*.js' ! -name '*.map' -print -quit)"
  for wasm_file in "$bundle_dir"/*.wasm; do
    [[ -e "$wasm_file" ]] || continue
    wasm_name="$(basename "$wasm_file")"
    if ! grep -Fq "$wasm_name" "$bundle_js"; then
      rm "$wasm_file"
    fi
  done
done

cat > "$site_dir/index.html" <<'EOF'
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Awake Engine Samples</title>
  <style>
    :root { color-scheme: dark; font-family: system-ui, sans-serif; }
    body { margin: 0; min-height: 100vh; display: grid; place-items: center; background: #050816; color: #f5f7ff; }
    main { width: min(42rem, calc(100% - 3rem)); }
    a { color: #9cc5ff; }
    li { margin: 0.8rem 0; }
  </style>
</head>
<body>
  <main>
    <h1>Awake Engine Samples</h1>
    <ul>
      <li><a href="./engine/">Engine Showcase</a> — WebGPU rendering and scene samples</li>
      <li><a href="./ui/">UI Showcase</a> — Awake UI component gallery</li>
    </ul>
  </main>
</body>
</html>
EOF

cat > "$site_dir/_headers" <<'EOF'
/engine/*
  Cache-Control: public, max-age=31536000, immutable
/ui/*
  Cache-Control: public, max-age=31536000, immutable
EOF

echo "Prepared samples site at $site_dir"

#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
# Compile a separate project using the published plugin markers and processor, without includeBuild.
set -euo pipefail
ENGINE_ROOT=$(cd "$(dirname "$0")/.." && pwd)
VERSION=${TEMPLATE_CORE_VERSION:?Set TEMPLATE_CORE_VERSION to the Core and build-logic version published locally}
[[ "$VERSION" =~ ^[0-9A-Za-z.+-]+$ ]] || { echo "Invalid consumer version" >&2; exit 1; }
WORK_DIR=${1:-$(mktemp -d "${TMPDIR:-/tmp}/awake-scene-consumer.XXXXXX")}
CHECKOUT="$WORK_DIR/scene-components-consumer"
[[ ! -e "$CHECKOUT" ]] || { echo "Consumer directory already exists: $CHECKOUT" >&2; exit 1; }
mkdir -p "$CHECKOUT"
cp -R "$ENGINE_ROOT/tools/scene-components-consumer/." "$CHECKOUT/"
KOTLIN_VERSION=$(sed -n 's/^kotlin = "\([^"]*\)"/\1/p' "$ENGINE_ROOT/gradle/libs.versions.toml" | head -1)
[[ "$KOTLIN_VERSION" =~ ^[0-9A-Za-z.+-]+$ ]] || { echo "Missing Kotlin version" >&2; exit 1; }
AGP_VERSION=$(sed -n 's/^agp = "\([^"]*\)"/\1/p' "$ENGINE_ROOT/gradle/libs.versions.toml" | head -1)
[[ "$AGP_VERSION" =~ ^[0-9A-Za-z.+-]+$ ]] || { echo "Missing AGP version" >&2; exit 1; }
sed -i.bak -e "s/@AWAKE_VERSION@/$VERSION/g" -e "s/@KOTLIN_VERSION@/$KOTLIN_VERSION/g" -e "s/@AGP_VERSION@/$AGP_VERSION/g" "$CHECKOUT/build.gradle.kts"
if [[ -n "${ANDROID_HOME:-}" ]]; then
  printf 'sdk.dir=%s\n' "${ANDROID_HOME//\\//}" > "$CHECKOUT/local.properties"
fi
TASKS=(checkProcessorCoordinate desktopTest wasmJsBrowserTest compileAndroidMain)
ARGS=()
if [[ -n "${AWAKE_SCENE_COMPONENTS_MAVEN_LOCAL:-}" ]]; then
  ARGS+=("-Dmaven.repo.local=$AWAKE_SCENE_COMPONENTS_MAVEN_LOCAL")
fi
if [[ "$(uname -s)" == Darwin ]]; then
  TASKS+=(compileKotlinIosArm64 compileKotlinIosSimulatorArm64)
fi
"$ENGINE_ROOT/gradlew" -p "$CHECKOUT" "${TASKS[@]}" "${ARGS[@]}" --no-daemon --max-workers=2 --no-configuration-cache

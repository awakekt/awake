#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Builds awake-template against Awake as a *published dependency*, the way someone who clicked
# "Use this template" would.
#
# The template substitutes a sibling `../awaken` checkout whenever one exists, which means a
# developer with the engine beside them never resolves the artifact at all -- they build engine
# source and the published surface goes untested. That is how the template spent weeks importing a
# package namespace the engine had already renamed, and how two libraries came to declare the same
# Android namespace: both are invisible from inside either repository.
#
# So this copies the template somewhere with no engine beside it, points its version catalog at the
# version this working tree produces, and builds every target it declares.
#
# Usage:
#   tools/check_template_consumer.sh <path-to-awake-template> [work-dir]
#
# Assumes the engine has been published where the template can resolve it:
#   ./gradlew publishToMavenLocal -PisMainHost=true

set -euo pipefail

TEMPLATE_SOURCE=${1:?"Usage: $0 <path-to-awake-template> [work-dir]"}
WORK_DIR=${2:-$(mktemp -d "${TMPDIR:-/tmp}/awake-consumer.XXXXXX")}
ENGINE_ROOT=$(cd "$(dirname "$0")/.." && pwd)
CHECKOUT="$WORK_DIR/awake-template"

if [[ ! -d "$TEMPLATE_SOURCE" ]]; then
  echo "No template checkout at $TEMPLATE_SOURCE" >&2
  exit 1
fi

# The parent directory must not hold an `awaken` checkout, or settings.gradle.kts substitutes the
# engine's projects and this whole exercise tests nothing.
if [[ -d "$WORK_DIR/awaken" ]]; then
  echo "$WORK_DIR holds an 'awaken' checkout; the template would substitute it instead of resolving." >&2
  exit 1
fi

echo "==> Copying the template to $CHECKOUT"
rm -rf "$CHECKOUT"
mkdir -p "$CHECKOUT"
# git archive rather than cp: it takes exactly what is committed, leaving build output, .gradle
# caches and local.properties behind -- the same content a fresh clone would have.
git -C "$TEMPLATE_SOURCE" archive HEAD | tar -x -C "$CHECKOUT"

VERSION=$(git -C "$ENGINE_ROOT" describe --tags --match "v*" --always 2>/dev/null | sed -E 's/^v//; s/-([0-9]+)-g[0-9a-f]+$/-SNAPSHOT/' || echo "0.1.0-dev.11-SNAPSHOT")
if [[ -z "$VERSION" ]]; then
  echo "Could not read the engine version" >&2
  exit 1
fi

echo "==> Pointing the template at Awake $VERSION"
# The catalog pins whatever the last release was; this run is about the artifacts built here.
CATALOG="$CHECKOUT/gradle/libs.versions.toml"
sed -i.bak -E "s/^awake = \".*\"/awake = \"$VERSION\"/" "$CATALOG" && rm -f "$CATALOG.bak"
grep -E '^awake = ' "$CATALOG"

# Android needs an SDK location, and a fresh copy has no local.properties.
if [[ -n "${ANDROID_HOME:-}" ]]; then
  echo "sdk.dir=$ANDROID_HOME" > "$CHECKOUT/local.properties"
elif [[ -f "$ENGINE_ROOT/local.properties" ]]; then
  grep '^sdk.dir=' "$ENGINE_ROOT/local.properties" > "$CHECKOUT/local.properties" || true
fi
cp "$CHECKOUT/local.properties" "$CHECKOUT/core/local.properties" 2>/dev/null || true
cp "$CHECKOUT/local.properties" "$CHECKOUT/app/androidApp/local.properties" 2>/dev/null || true

run_target() {
  local label=$1
  shift
  echo "==> $label"
  (cd "$CHECKOUT" && export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}" ANDROID_HOME="${ANDROID_HOME:-/Users/ronvaldoz/Library/Android/sdk}" && ./gradlew "$@" -Dsdk.dir="${ANDROID_HOME:-/Users/ronvaldoz/Library/Android/sdk}" --no-configuration-cache --console=plain)
}

run_target "Shared and core (JVM)" :core:compileKotlinJvm :app:shared:compileKotlinJvm
run_target "Desktop app" :app:desktopApp:compileKotlin
run_target "Web app (wasmJs/WebGPU)" :app:webApp:compileKotlinWasmJs

# Linking is where a Kotlin/Native consumer finds out an artifact is unusable -- compiling only
# proves the klib resolved. Apple toolchain only, so a Linux runner skips it rather than failing.
if [[ "$(uname -s)" == "Darwin" ]]; then
  run_target "iOS framework link" :app:shared:linkDebugFrameworkIosSimulatorArm64
else
  echo "==> iOS framework link skipped: not an Apple host"
fi

# Manifest merging is the step that catches two libraries claiming one Android namespace, which is
# why this assembles rather than only compiling.
if [[ -f "$CHECKOUT/local.properties" ]]; then
  run_target "Android app" :app:androidApp:assembleDebug
else
  echo "==> Android app skipped: no Android SDK location"
fi

echo
echo "Template builds against Awake $VERSION as a published dependency."
echo "Checkout kept at $CHECKOUT"

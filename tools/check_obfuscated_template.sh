#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Builds a starter template's release the way a game ships it, shrunk and obfuscated, against Awake as
# a published dependency, then runs it. A class that native code finds by name and that no keep rule
# keeps is renamed in the release, and the run fails here rather than on a player's device (#648).
#
#   1. Builds the desktop (ProGuard), Android (R8) and web (minified) releases.
#   2. Checks the desktop release is obfuscated: Awake's classes are renamed.
#   3. Runs it for -Dawake.frames frames in a window, then with no window, capturing a frame that must
#      not be blank.
#   4. Control: rebuilds it without the window's keep rules, and that run must fail at the native
#      call. So a missing rule fails this check.
#
# The Android release is copied to <out-dir>/<template>-release.apk for the caller to launch on a
# device or emulator with scripts/android-launch-check.sh.
#
# Usage:
#   tools/check_obfuscated_template.sh <path-to-template> <work-dir> <out-dir>
#
# Assumes Awake is published where the template can resolve it, at the versions TEMPLATE_CORE_VERSION
# and TEMPLATE_VULKAN_VERSION name (see tools/check_template_consumer.sh), and a Vulkan driver and
# Xvfb for the desktop runs.
set -euo pipefail

TEMPLATE_SOURCE=${1:?"Usage: $0 <path-to-template> <work-dir> <out-dir>"}
WORK_DIR=${2:?"Usage: $0 <path-to-template> <work-dir> <out-dir>"}
OUT_DIR=${3:?"Usage: $0 <path-to-template> <work-dir> <out-dir>"}
ENGINE_ROOT=$(cd "$(dirname "$0")/.." && pwd)
NAME=$(basename "$TEMPLATE_SOURCE")
CHECKOUT="$WORK_DIR/awake-template"
FRAMES=${TEMPLATE_FRAMES:-120}
mkdir -p "$OUT_DIR"
OUT_DIR=$(cd "$OUT_DIR" && pwd)

fail() {
  echo "✗ $NAME: $*" >&2
  exit 1
}

TEMPLATE_PREPARE_ONLY=1 "$ENGINE_ROOT/tools/check_template_consumer.sh" "$TEMPLATE_SOURCE" "$WORK_DIR"

gradle() {
  (cd "$CHECKOUT" && ./gradlew "$@" --no-configuration-cache --console=plain)
}

echo "==> $NAME: release builds"
gradle :app:desktopApp:proguardReleaseJars :app:androidApp:assembleRelease :app:webApp:wasmJsBrowserDistribution

JARS="$CHECKOUT/app/desktopApp/build/compose/tmp/main-release/proguard"
MAIN=$(sed -n 's/.*mainClass = "\(.*\)".*/\1/p' "$CHECKOUT/app/desktopApp/build.gradle.kts" | head -n 1)
[[ -n "$MAIN" ]] || fail "no mainClass in app/desktopApp/build.gradle.kts"
ls "$JARS"/*.jar >/dev/null 2>&1 || fail "no ProGuard output in $JARS"

# A release that isn't obfuscated would pass every run below without checking a single rule.
for jar in "$JARS"/*.jar; do
  if unzip -l "$jar" | grep -q 'com/awakekt/awake/ecs/World\.class'; then
    fail "the desktop release isn't obfuscated: $(basename "$jar") still holds com.awakekt.awake.ecs.World"
  fi
done
echo "✓ $NAME: the desktop release is obfuscated"

desktop() {
  local log=$1
  shift
  timeout 600 xvfb-run -a java "$@" -cp "$JARS/*" "$MAIN" >"$log" 2>&1
}

echo "==> $NAME: $FRAMES frames in a window"
desktop "$OUT_DIR/$NAME-window.log" "-Dawake.frames=$FRAMES" || { cat "$OUT_DIR/$NAME-window.log" >&2; fail "the windowed run failed"; }
echo "✓ $NAME: played $FRAMES frames in a window"

echo "==> $NAME: a frame captured with no window"
FRAME="$OUT_DIR/$NAME-frame.png"
desktop "$OUT_DIR/$NAME-capture.log" "-Dawake.capture=$FRAME" "-Dawake.frames=$FRAMES" \
  || { cat "$OUT_DIR/$NAME-capture.log" >&2; fail "the capture run failed"; }
python3 "$ENGINE_ROOT/tools/png_colours.py" "$FRAME" 2 || fail "the captured frame is blank"

WEB="$CHECKOUT/app/webApp/build/dist/wasmJs/productionExecutable"
ls "$WEB"/*.wasm >/dev/null 2>&1 || fail "no minified web build in $WEB"
echo "✓ $NAME: web build, $(du -sh "$WEB" | cut -f1) in $WEB"

APK=$(ls "$CHECKOUT"/app/androidApp/build/outputs/apk/release/*.apk 2>/dev/null | head -n 1)
[[ -n "$APK" ]] || fail "no Android release APK"
cp "$APK" "$OUT_DIR/$NAME-release.apk"
echo "✓ $NAME: Android release at $OUT_DIR/$NAME-release.apk"

# The control: without the window's keep rules ProGuard renames GlfwWindow, and the run must then fail
# at its first native call. If it doesn't, these runs couldn't catch a missing rule.
echo "==> $NAME: control, the release without the window's keep rules"
RULES="$CHECKOUT/app/desktopApp/build/library-keep-rules"
[[ -f "$RULES/awake-engine-window.pro" ]] || fail "no awake-engine-window.pro in $RULES; is the template collecting library keep rules?"
rm "$RULES/awake-engine-window.pro"
gradle :app:desktopApp:proguardReleaseJars -x :app:desktopApp:libraryKeepRules
if desktop "$OUT_DIR/$NAME-control.log" "-Dawake.frames=$FRAMES"; then
  fail "the release without the window's keep rules still ran, so a missing rule would go unnoticed"
fi
grep -q 'UnsatisfiedLinkError' "$OUT_DIR/$NAME-control.log" \
  || { cat "$OUT_DIR/$NAME-control.log" >&2; fail "the control failed, but not at a native call"; }
echo "✓ $NAME: without the window's keep rules the release fails at its native call, as it must"

echo
echo "$NAME's obfuscated release builds and runs against Awake ${TEMPLATE_CORE_VERSION:-as published}."

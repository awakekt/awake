#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Builds a starter template (templates/game, templates/project or templates/plugin, published as
# awake-template, awake-project-template and awake-plugin-template) against Awake as a
# *published dependency*, the way someone who clicked
# "Use this template" would.
#
# The template substitutes a sibling `../awaken` checkout whenever one exists, which means a
# developer with the engine beside them never resolves the artifact at all -- they build engine
# source and the published surface goes untested. That is how the template spent weeks importing a
# package namespace the engine had already renamed, and how two libraries came to declare the same
# Android namespace: both are invisible from inside either repository.
#
# So this copies the template somewhere with no engine beside it, points its Core, Vulkan and WebGPU
# catalog entries at the versions this working tree produces, and builds every target it declares.
#
# Usage:
#   tools/check_template_consumer.sh templates/<game|project|plugin> [work-dir]
#
# Assumes the engine has been published where the template can resolve it:
#   ./gradlew publishToMavenLocal -PisMainHost=true
# and, on a release tag, its WebGPU release pin:
#   ./gradlew :awake:backend:webgpu:publishToMavenLocal -Pawake.publishFamily=webgpu -PisMainHost=true

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
# Only the files git tracks, rather than cp: build output, .gradle caches and local.properties stay
# behind, the same content a fresh clone would have. It works for a template that is a directory of
# a larger checkout, as templates/ in Core is, and takes uncommitted edits to tracked files.
git -C "$TEMPLATE_SOURCE" ls-files -z | (cd "$TEMPLATE_SOURCE" && tar --null -T - -cf -) | tar -x -C "$CHECKOUT"

# Ask Gradle for the versions actually assigned to each publication family. Do not duplicate the
# root build's git-describe/bump rules here. A local publish (`-Pawake.version`) names its own.
# awk reads all of Gradle's output rather than exiting at the match: an early exit can SIGPIPE
# the still-writing Gradle, which pipefail turns into this script failing (exit 141).
CORE_VERSION=${TEMPLATE_CORE_VERSION:-$(cd "$ENGINE_ROOT" && ./gradlew -q :awake:core:math:properties \
  --no-configuration-cache | awk '$1 == "version:" && v == "" { v = $2 } END { print v }')}
# A Core-only release builds no Vulkan, so its caller names the Vulkan release to test against.
VULKAN_VERSION=${TEMPLATE_VULKAN_VERSION:-$(cd "$ENGINE_ROOT" && ./gradlew -q :awake:backend:vulkan:properties \
  --no-configuration-cache | awk '$1 == "version:" && v == "" { v = $2 } END { print v }')}
if [[ -z "$CORE_VERSION" || -z "$VULKAN_VERSION" ]]; then
  echo "Could not read both publication-family versions from Gradle" >&2
  exit 1
fi
# A Core release pairs with its WebGPU release pin, `<release>-SNAPSHOT` (tools/publish_webgpu_pin.sh),
# which its caller publishes locally first; a Core snapshot publishes WebGPU at its own version. A
# version the caller names is a local publish (`-Pawake.version`), which puts WebGPU at it too.
if [[ -n "${TEMPLATE_WEBGPU_VERSION:-}" ]]; then
  WEBGPU_VERSION=$TEMPLATE_WEBGPU_VERSION
elif [[ -n "${TEMPLATE_CORE_VERSION:-}" || "$CORE_VERSION" == *-SNAPSHOT ]]; then
  WEBGPU_VERSION=$CORE_VERSION
else
  WEBGPU_VERSION=$CORE_VERSION-SNAPSHOT
fi

echo "==> Pointing the template at Core $CORE_VERSION, Vulkan $VULKAN_VERSION and WebGPU $WEBGPU_VERSION"
# The catalog pins whatever releases the template last used; this run is about the artifacts built
# here. The transformation also lets Core CI exercise the new split before the template PR merges.
CATALOG="$CHECKOUT/gradle/libs.versions.toml"
CORE_VERSION="$CORE_VERSION" VULKAN_VERSION="$VULKAN_VERSION" WEBGPU_VERSION="$WEBGPU_VERSION" perl -0pi -e \
  's/^awake = ".*"$/awake = "$ENV{CORE_VERSION}"/m;
   if (!/^awake-vulkan = /m) {
     s/^(awake = ".*")$/$1\nawake-vulkan = "$ENV{VULKAN_VERSION}"/m;
   } else {
     s/^awake-vulkan = ".*"$/awake-vulkan = "$ENV{VULKAN_VERSION}"/m;
   }
   s/(^awake-backend-vulkan = \{[^\n]*version\.ref = )"awake"/${1}"awake-vulkan"/m;
   s/^awake-webgpu = ".*"$/awake-webgpu = "$ENV{WEBGPU_VERSION}"/m' \
  "$CATALOG"

# Snapshot family artifacts are served from Central's dedicated snapshot repository.
SETTINGS="$CHECKOUT/settings.gradle.kts"
if ! grep -q 'central\.sonatype\.com/repository/maven-snapshots' "$SETTINGS"; then
  perl -0pi -e 's#(dependencyResolutionManagement\s*\{\s*repositories\s*\{\n)#$1        maven("https://central.sonatype.com/repository/maven-snapshots/")\n#' "$SETTINGS"
fi
# Maven Local first, for Awake only. The templates list the snapshot repository first, and an earlier
# upload of the same snapshot version there would otherwise win over the build published here.
perl -0pi -e 's#(dependencyResolutionManagement\s*\{\s*repositories\s*\{\n)#$1        mavenLocal { mavenContent { includeGroupAndSubgroups("com.awakekt.awake") } }\n#' "$SETTINGS"
grep -E '^awake(-vulkan|-webgpu)? = ' "$CATALOG"

# Android needs an SDK location, and a fresh copy has no local.properties.
if [[ -n "${ANDROID_HOME:-}" ]]; then
  echo "sdk.dir=$ANDROID_HOME" > "$CHECKOUT/local.properties"
elif [[ -f "$ENGINE_ROOT/local.properties" ]]; then
  grep '^sdk.dir=' "$ENGINE_ROOT/local.properties" > "$CHECKOUT/local.properties" || true
fi
cp "$CHECKOUT/local.properties" "$CHECKOUT/core/local.properties" 2>/dev/null || true
cp "$CHECKOUT/local.properties" "$CHECKOUT/app/androidApp/local.properties" 2>/dev/null || true

# A caller that builds its own targets, such as tools/check_obfuscated_template.sh, stops here.
if [[ -n "${TEMPLATE_PREPARE_ONLY:-}" ]]; then
  echo "Template prepared at $CHECKOUT against Core $CORE_VERSION and Vulkan $VULKAN_VERSION"
  exit 0
fi

template_gradle() {
  (cd "$CHECKOUT" && export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}" ANDROID_HOME="${ANDROID_HOME:-/Users/ronvaldoz/Library/Android/sdk}" && ./gradlew "$@" -Dsdk.dir="${ANDROID_HOME:-/Users/ronvaldoz/Library/Android/sdk}" --no-configuration-cache --console=plain)
}

run_target() {
  local label=$1
  shift
  echo "==> $label"
  template_gradle "$@"
}

# The plugin template is a single module against the editor contract, with no apps to build.
if [[ -f "$CHECKOUT/plugin/plugin.json" ]]; then
  run_target "Plugin (desktop) and its tests" :plugin:desktopTest
  run_target "Plugin (wasmJs)" :plugin:compileKotlinWasmJs
  run_target "Plugin archive" :plugin:awakePlugin
  echo
  echo "Plugin template builds against Core $CORE_VERSION as a published dependency."
  echo "Checkout kept at $CHECKOUT"
  exit 0
fi

run_target "Shared and core (JVM)" :core:compileKotlinJvm :app:shared:compileKotlinJvm
run_target "Desktop app" :app:desktopApp:compileKotlin
# The development executable, not just the klib: the wasm link is where a WebGPU built against
# another Core fails, on a symbol the resolved Core does not have.
run_target "Web app (wasmJs/WebGPU)" :app:webApp:compileDevelopmentExecutableKotlinWasmJs

# WebGPU must not move any Core module of the web build off the Core under test. Each line of the
# report names a module as group:name:requested, with " -> selected" when resolution chose another.
echo "==> Web app's Awake modules"
web_report=$(template_gradle -q :app:webApp:dependencies --configuration wasmJsRuntimeClasspath)
if grep -q 'com\.awakekt\.awake.* FAILED' <<< "$web_report"; then
  echo "The web app's wasmJsRuntimeClasspath does not resolve:" >&2
  grep 'com\.awakekt\.awake.* FAILED' <<< "$web_report" >&2
  exit 1
fi
web_modules=$(perl -ne 'while (m/(com\.awakekt\.awake[\w.-]*):([\w.-]+)(?::(\{[^}]*\}|[^\s:]+))?(?: -> ([^\s]+))?/g) {
      my ($module, $selected) = ("$1:$2", $4 // $3);
      next if !defined $selected || $module =~ /^com\.awakekt\.awake\.backend:webgpu/;
      $selected =~ s/^\{(?:strictly|require|prefer) ([^}]+)\}$/$1/;
      print "$module:$selected\n";
    }' <<< "$web_report" | sort -u)
if [[ -z "$web_modules" ]]; then
  echo "The web app's wasmJsRuntimeClasspath names no Awake module." >&2
  exit 1
fi
off_core=$(awk -F: -v core="$CORE_VERSION" '$NF != core' <<< "$web_modules")
if [[ -n "$off_core" ]]; then
  echo "The web app resolves Awake modules other than Core $CORE_VERSION:" >&2
  echo "$off_core" >&2
  exit 1
fi
echo "Every Awake module of the web app is at Core $CORE_VERSION ($(grep -c . <<< "$web_modules") modules)."

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
echo "Template builds against Core $CORE_VERSION and Vulkan $VULKAN_VERSION as published dependencies."
echo "Checkout kept at $CHECKOUT"

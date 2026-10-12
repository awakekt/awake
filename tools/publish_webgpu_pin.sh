#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Publishes the WebGPU release pin, com.awakekt.awake.backend:webgpu:<release>-SNAPSHOT, from a
# checkout of the commit tagged v<release>. WebGPU builds on wgpu4k snapshots, so Central releases
# refuse it, and this snapshot is the WebGPU build a consumer pairs with that Core release: its
# metadata names every Core module at exactly <release> and holds each one there with a strictly
# constraint, so a newer Core on the same classpath fails resolution instead of linking at startup.
#
# Usage, from the tagged checkout:
#   bash tools/publish_webgpu_pin.sh
set -euo pipefail

tag="$(git describe --exact-match --tags --match 'v[0-9]*' HEAD 2> /dev/null || true)"
if [[ -z "$tag" ]]; then
  echo "The WebGPU release pin is built from a commit tagged v<release>; HEAD has no such tag." >&2
  exit 1
fi
core="${tag#v}"

gradle_args=(
  -Pawake.publishFamily=webgpu
  -PisMainHost=true
  --no-configuration-cache
  --max-workers=4
)

# awk reads all of Gradle's output rather than exiting at the match, which can SIGPIPE Gradle.
version="$(./gradlew -q :awake:backend:webgpu:properties "${gradle_args[@]}" \
  | awk '$1 == "version:" && v == "" { v = $2 } END { print v }')"
if [[ "$version" != "$core-SNAPSHOT" ]]; then
  echo "The WebGPU release pin for $tag is $core-SNAPSHOT, but Gradle derived '$version'." >&2
  exit 1
fi

echo "Verifying WebGPU $version against Core $core"
./gradlew :awake:backend:webgpu:publishToMavenLocal "${gradle_args[@]}"
./gradlew verifyPublishedArtifacts "-Pawake.verifyPublishedVersion=$version" -Pawake.verifyPublishedFamily=webgpu \
  "-Pawake.verifyPinnedCoreVersion=$core" --no-configuration-cache

# The Core release's upload returns once Central is publishing it, before repo1 serves the files.
./gradlew waitForCoreCentral "-Pawake.waitForCoreVersion=$core" --no-configuration-cache

# A -SNAPSHOT version, so this goes to Central's snapshot repository; WebGPU never stages a release.
./gradlew :awake:backend:webgpu:publishAllPublicationsToMavenCentralRepository "${gradle_args[@]}"

# Read back what the web build resolves. The latest build of the version is the one consumers get,
# so this also fails if anything else uploaded the same version after this one.
snapshots="https://central.sonatype.com/repository/maven-snapshots/com/awakekt/awake/backend/webgpu-wasm-js/$version"
metadata="$(curl -fsS --retry 5 --retry-all-errors "$snapshots/maven-metadata.xml")"
timestamp="$(sed -n 's:.*<timestamp>\(.*\)</timestamp>.*:\1:p' <<< "$metadata")"
build_number="$(sed -n 's:.*<buildNumber>\(.*\)</buildNumber>.*:\1:p' <<< "$metadata")"
if [[ -z "$timestamp" || -z "$build_number" ]]; then
  echo "The snapshot repository lists no build of webgpu-wasm-js $version." >&2
  exit 1
fi
build="$core-$timestamp-$build_number"
if ! curl -fsS --retry 5 --retry-all-errors "$snapshots/webgpu-wasm-js-$build.module" | jq -e --arg core "$core" '
  def awake: .group | test("^com\\.awakekt\\.awake(\\.|$)");
  [.variants[] | .dependencies[]? | select(awake and (.module | startswith("webgpu") | not))] as $deps
  | [.variants[] | .dependencyConstraints[]? | select(awake)] as $constraints
  | ($deps | length) > 0
    and ($deps | all(.version.requires == $core))
    and ($constraints | all(.version.strictly == $core))
    and (([$deps[] | "\(.group):\(.module)"] - [$constraints[] | "\(.group):\(.module)"]) | length) == 0
' > /dev/null; then
  echo "webgpu-wasm-js $build on the snapshot repository does not hold every Core module strictly at $core." >&2
  exit 1
fi
echo "::notice::WebGPU $version (build $build) pins Core $core"

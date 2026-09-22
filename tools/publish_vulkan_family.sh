#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail

channel="${1:-}"
prebuilt_natives="${2:-}"
if [[ "$channel" != "snapshot" && "$channel" != "release" ]]; then
  echo "Usage: bash tools/publish_vulkan_family.sh <snapshot|release> <prebuilt-native-root>" >&2
  exit 2
fi
if [[ -z "$prebuilt_natives" || ! -d "$prebuilt_natives" ]]; then
  echo "A directory containing the collected Vulkan native libraries is required." >&2
  exit 2
fi

if [[ "$channel" == "release" ]]; then
  core_tag="$(git describe --abbrev=0 --tags --match 'v[0-9]*')"
  core_version="${core_tag#v}"
  if [[ -z "$core_version" || "$core_version" == *-SNAPSHOT ]]; then
    echo "The Vulkan release requires a stable Core release tag; got '$core_tag'." >&2
    exit 1
  fi
else
  core_version="$(./gradlew -q :awake:core:math:properties --no-configuration-cache \
    | awk '/^version:/ { print $2 }')"
  if [[ -z "$core_version" ]]; then
    echo "Could not derive the current Core integration version." >&2
    exit 1
  fi
fi

gradle_args=(
  -Pawake.publishFamily=vulkan
  "-Pawake.coreVersion=$core_version"
  "-Pawake.prebuiltNatives=$prebuilt_natives"
  -PisMainHost=true
  --no-configuration-cache
  --max-workers=4
)

# Keep this family publish deliberately narrow. The root aggregate tasks publish every
# publication in Awake Core, which can accidentally republish unrelated modules when the
# Vulkan workflow is only assembling the three Vulkan coordinates.
vulkan_local_tasks=(
  :awake:backend:vulkan:publishToMavenLocal
  :awake:backend:vulkan:bindings:publishToMavenLocal
  :awake:backend:vulkan:bindings:android-native:publishToMavenLocal
)
vulkan_central_tasks=(
  :awake:backend:vulkan:publishAllPublicationsToMavenCentralRepository
  :awake:backend:vulkan:bindings:publishAllPublicationsToMavenCentralRepository
  :awake:backend:vulkan:bindings:android-native:publishAllPublicationsToMavenCentralRepository
)

version="$(./gradlew -q :awake:backend:vulkan:properties "${gradle_args[@]}" | awk '/^version:/ { print $2 }')"
if [[ -z "$version" ]]; then
  echo "Could not derive the Vulkan family version." >&2
  exit 1
fi

./gradlew :awake:backend:vulkan:bindings:verifyDesktopNatives \
  "-Pawake.prebuiltNatives=$prebuilt_natives"

if [[ "$channel" == "release" ]]; then
  if [[ "$version" == *-SNAPSHOT || "${GITHUB_REF_NAME:-}" != "vulkan-v$version" ]]; then
    echo "Vulkan tag '${GITHUB_REF_NAME:-}' does not match stable version '$version'." >&2
    exit 1
  fi
else
  if [[ "$version" != *-SNAPSHOT ]]; then
    echo "Snapshot publishing derived stable version '$version'." >&2
    exit 1
  fi
fi

echo "Verifying Vulkan $version against Core $core_version"
./gradlew "${vulkan_local_tasks[@]}" "${gradle_args[@]}"
./gradlew verifyPublishedArtifacts "-Pawake.verifyPublishedVersion=$version" -Pawake.verifyPublishedFamily=vulkan --no-configuration-cache

./gradlew "${vulkan_central_tasks[@]}" "${gradle_args[@]}"

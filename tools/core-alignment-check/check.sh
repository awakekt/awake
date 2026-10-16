#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Checks, against Maven Central, that a consumer holding Core release <core> and the latest Vulkan
# release resolves every Core module at <core>, however old the Core that Vulkan release pins.
# build-and-publish.yml runs it after each Core release (job "Core alignment"); see
# docs/release-process.md "Core alignment BOM".
#
# Usage, from the repo root:
#   bash tools/core-alignment-check/check.sh <core> [<vulkan>]
# <vulkan> defaults to the latest Vulkan release on Central. The check waits up to an hour for the
# Core release's BOM on repo1, which is served after the upload returns.
set -euo pipefail

core="${1:?Usage: check.sh <core> [<vulkan>]}"
central=https://repo1.maven.org/maven2/com/awakekt/awake

for _ in $(seq 60); do
  curl -fsI "$central/bom/$core/bom-$core.pom" > /dev/null && break
  sleep 60
done
curl -fsI "$central/bom/$core/bom-$core.pom" > /dev/null \
  || { echo "Core $core has no BOM on repo1 after an hour." >&2; exit 1; }

vulkan="${2:-$(curl -fsS --retry 5 "$central/backend/vulkan/maven-metadata.xml" \
  | sed -n 's:.*<release>\(.*\)</release>.*:\1:p')}"
[[ -n "$vulkan" ]] || { echo "No Vulkan release on Central." >&2; exit 1; }

echo "Resolving Core $core with Vulkan $vulkan"
./gradlew -p tools/core-alignment-check checkCoreAlignment -q "-Pcore=$core" "-Pvulkan=$vulkan"

#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Pins a template's gradle/libs.versions.toml to given Awake Core, Vulkan and WebGPU versions.
# Vulkan and WebGPU are stamped only when a version is given and the catalog already names that
# entry: the plugin template depends on neither, so passing them here is a no-op on its catalog
# rather than adding an unused entry.
#
# Used by tools/check_template_consumer.sh (this build's own versions, for testing) and
# tools/publish_template.sh (a release's versions, for publishing).
#
# Usage: tools/stamp_template_versions.sh <catalog> <core-version> [vulkan-version] [webgpu-version]
set -euo pipefail

CATALOG=${1:?"Usage: $0 <catalog> <core-version> [vulkan-version] [webgpu-version]"}
CORE_VERSION=${2:?"Usage: $0 <catalog> <core-version> [vulkan-version] [webgpu-version]"}
VULKAN_VERSION=${3:-}
WEBGPU_VERSION=${4:-}
[[ -f "$CATALOG" ]] || { echo "No catalog at $CATALOG" >&2; exit 1; }

CORE_VERSION="$CORE_VERSION" perl -0pi -e 's/^awake = ".*"$/awake = "$ENV{CORE_VERSION}"/m' "$CATALOG"

if [[ -n "$VULKAN_VERSION" ]] && grep -q '^awake-vulkan = ' "$CATALOG"; then
  VULKAN_VERSION="$VULKAN_VERSION" perl -0pi -e 's/^awake-vulkan = ".*"$/awake-vulkan = "$ENV{VULKAN_VERSION}"/m' "$CATALOG"
fi
if [[ -n "$WEBGPU_VERSION" ]] && grep -q '^awake-webgpu = ' "$CATALOG"; then
  WEBGPU_VERSION="$WEBGPU_VERSION" perl -0pi -e 's/^awake-webgpu = ".*"$/awake-webgpu = "$ENV{WEBGPU_VERSION}"/m' "$CATALOG"
fi
grep -E '^awake(-vulkan|-webgpu)? = ' "$CATALOG"

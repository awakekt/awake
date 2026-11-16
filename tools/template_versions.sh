#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Prints the versions tools/check_template_consumer.sh builds a template against, as KEY=value lines
# for $GITHUB_ENV, from the versions a Publish job has already resolved. Without them each template
# check asks Gradle again, a cold root configuration of about 25 to 65 s per template (#1592).
#
# Reads, from the environment:
#   CORE_VERSION              the Core family version this build publishes (required)
#   VULKAN_VERSION            the Vulkan family version this build publishes
#   VULKAN_PUBLISH            'true' when this build publishes Vulkan too
#   TEMPLATE_VULKAN_VERSION   the Vulkan release on Central to test against when it does not
#
# The choice matches check_template_consumer.sh's own when it is given no versions: a Core release
# pairs with its WebGPU release pin, <release>-SNAPSHOT, and a Core snapshot with WebGPU at its own
# version. A missing version fails rather than letting a template build against the wrong one.
set -euo pipefail

core=${CORE_VERSION:-}
if [[ -z "$core" ]]; then
  echo "CORE_VERSION is not set" >&2
  exit 1
fi

if [[ "${VULKAN_PUBLISH:-}" == 'true' ]]; then
  vulkan=${VULKAN_VERSION:-}
else
  vulkan=${TEMPLATE_VULKAN_VERSION:-}
fi
if [[ -z "$vulkan" ]]; then
  echo "No Vulkan version: VULKAN_VERSION when this build publishes Vulkan, TEMPLATE_VULKAN_VERSION when not" >&2
  exit 1
fi

if [[ "$core" == *-SNAPSHOT ]]; then
  webgpu=$core
else
  webgpu=$core-SNAPSHOT
fi

printf 'TEMPLATE_CORE_VERSION=%s\nTEMPLATE_VULKAN_VERSION=%s\nTEMPLATE_WEBGPU_VERSION=%s\n' "$core" "$vulkan" "$webgpu"

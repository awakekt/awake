#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Checks tools/template_versions.sh, one case per line, and that Publish's Consumer hands its
# resolved versions to every template check rather than letting each ask Gradle again (#1592).
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
script="$root/tools/template_versions.sh"
failures=0

expect() { # name core vulkan publish templateVulkan expected
  local actual status=0
  actual="$(env -i PATH="$PATH" CORE_VERSION="$2" VULKAN_VERSION="$3" VULKAN_PUBLISH="$4" \
    TEMPLATE_VULKAN_VERSION="$5" "$script" 2>/dev/null | tr '\n' ' ')" || status=$?
  [[ $status -eq 0 ]] || actual="exit $status"
  actual="${actual% }"
  if [[ "$actual" == "$6" ]]; then echo "ok   $1"; else echo "FAIL $1: expected $6, got $actual"; failures=$((failures + 1)); fi
}

expect "a release with its own Vulkan pairs WebGPU with its pin" 0.55.0 0.1.60 true '' \
  "TEMPLATE_CORE_VERSION=0.55.0 TEMPLATE_VULKAN_VERSION=0.1.60 TEMPLATE_WEBGPU_VERSION=0.55.0-SNAPSHOT"
expect "a Core-only release tests the Vulkan release on Central" 0.55.0 0.1.61-SNAPSHOT false 0.1.60 \
  "TEMPLATE_CORE_VERSION=0.55.0 TEMPLATE_VULKAN_VERSION=0.1.60 TEMPLATE_WEBGPU_VERSION=0.55.0-SNAPSHOT"
expect "a snapshot pairs WebGPU with itself" 0.56.0-SNAPSHOT 0.1.61-SNAPSHOT true '' \
  "TEMPLATE_CORE_VERSION=0.56.0-SNAPSHOT TEMPLATE_VULKAN_VERSION=0.1.61-SNAPSHOT TEMPLATE_WEBGPU_VERSION=0.56.0-SNAPSHOT"
expect "no Core version fails" '' 0.1.60 true '' "exit 1"
expect "a Core-only build without a Central Vulkan fails" 0.55.0 0.1.61-SNAPSHOT false '' "exit 1"
expect "a Vulkan build without its version fails" 0.55.0 '' true 0.1.60 "exit 1"

# The Consumer step must load these versions before its first template check; without them each
# check_template_consumer.sh call configures the root build twice to read them.
step="$(awk '/- name: Build the templates against it/ { on = 1 } on && /^  [a-z]/ { exit } on' \
  "$root/.github/workflows/build-and-publish.yml")"
loads="$( { grep -n 'tools/template_versions.sh' <<< "$step" || true; } | head -1 | cut -d: -f1 || true)"
first="$( { grep -n 'tools/check_template_consumer.sh' <<< "$step" || true; } | head -1 | cut -d: -f1 || true)"
if [[ -n "$loads" && -n "$first" && "$loads" -lt "$first" ]]; then
  echo "ok   Consumer passes the resolved versions to the template checks"
else
  echo "FAIL Consumer passes the resolved versions to the template checks: tools/template_versions.sh is not loaded before the first check"
  failures=$((failures + 1))
fi

[[ $failures -eq 0 ]] || { echo "$failures case(s) failed"; exit 1; }

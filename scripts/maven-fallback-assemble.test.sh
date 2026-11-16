#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Checks scripts/maven-fallback-assemble.sh and scripts/maven-fallback-closure.sh against throwaway
# releases (#1694): two deploys in a row, as two releases' Publish runs would make them, where the
# second must still serve the Core release a kept Vulkan release imports.
set -euo pipefail

scripts="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
cd "$work"

# One release's bundle: a BOM and a math module for Core; for Vulkan, a backend importing the BOM of
# [core] and depending on its math module.
release() { # tag family version [core]
  local tag="$1" family="$2" version="$3" core="${4:-}" dir pom
  rm -rf content && mkdir -p "source/$tag"
  if [[ "$family" == core ]]; then
    for artifact in bom core/math core/math-desktop core/unused; do
      dir="content/com/awakekt/awake/$artifact/$version"
      mkdir -p "$dir"
      pom="$dir/$(basename "$artifact")-$version.pom"
      printf '<project xmlns="http://maven.apache.org/POM/4.0.0"><groupId>g</groupId></project>\n' > "$pom"
    done
    # A module nothing names, two megabytes of it, as most of a real Core bundle is.
    head -c 2097152 /dev/urandom > "content/com/awakekt/awake/core/unused/$version/unused-$version.jar"
  else
    dir="content/com/awakekt/awake/backend/vulkan/$version"
    mkdir -p "$dir"
    cat > "$dir/vulkan-$version.pom" <<EOF
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <dependencyManagement><dependencies><dependency>
    <groupId>com.awakekt.awake</groupId><artifactId>bom</artifactId><version>$core</version><scope>import</scope>
  </dependency></dependencies></dependencyManagement>
  <dependencies><dependency>
    <groupId>com.awakekt.awake.core</groupId><artifactId>math</artifactId><version>$core</version>
  </dependency></dependencies>
</project>
EOF
    # Gradle resolves the module first: its desktop variant needs Core math's desktop artifact.
    cat > "$dir/vulkan-$version.module" <<EOF
{"variants": [{"name": "desktopApiElements", "dependencies": [
  {"group": "com.awakekt.awake.core", "module": "math-desktop", "version": {"requires": "$core"}}]}]}
EOF
  fi
  (cd content && zip -qr "../source/$tag/awake-$version-maven.zip" .)
}

failures=0
pass() { echo "ok   $1"; }
fail() { echo "FAIL $1"; failures=$((failures + 1)); }
served() { [[ -f "site/com/awakekt/awake/$1" ]]; }
export MAVEN_FALLBACK_SOURCE="$work/source"

# First deploy: Core 0.9.0, and Vulkan 0.1.20 built against it.
release v0.9.0 core 0.9.0
release vulkan-v0.1.20 vulkan 0.1.20 0.9.0
printf 'v0.9.0\nvulkan-v0.1.20\n' > source/tags
if "$scripts/maven-fallback-assemble.sh" site 2 3 > first.txt 2>&1; then pass "first deploy builds"; else fail "first deploy: $(cat first.txt)"; fi

# Second deploy, two Core releases later: the newest two Core releases alone would drop 0.9.0, which
# the still-served Vulkan 0.1.20 imports. It must be kept, by reference.
release v0.10.0 core 0.10.0
release v0.11.0 core 0.11.0
release vulkan-v0.1.21 vulkan 0.1.21 0.11.0
printf 'v0.11.0\nvulkan-v0.1.21\nv0.10.0\nv0.9.0\nvulkan-v0.1.20\n' > source/tags
if "$scripts/maven-fallback-assemble.sh" site 2 3 > second.txt 2>&1; then pass "second deploy builds"; else fail "second deploy: $(cat second.txt)"; fi
if served bom/0.9.0/bom-0.9.0.pom && served core/math/0.9.0/math-0.9.0.pom; then
  pass "keeps the first release's BOM and artifacts while a served Vulkan release names them"
else fail "dropped the first release: $(find site -name '*.pom' | sort)"; fi
if served bom/0.11.0/bom-0.11.0.pom && served bom/0.10.0/bom-0.10.0.pom && served backend/vulkan/0.1.20/vulkan-0.1.20.pom; then
  pass "serves the newest Core releases and every kept Vulkan release"; else fail "newest: $(find site -name '*.pom' | sort)"; fi
if grep -q 'named by a served POM' second.txt; then pass "says which releases it kept by reference"; else fail "reason: $(cat second.txt)"; fi
if served core/math-desktop/0.9.0/math-desktop-0.9.0.pom; then pass "serves what a Gradle module names"; else
  fail "module dependency: $(find site -name '*.pom' | sort)"; fi
if ! served core/unused/0.9.0/unused-0.9.0.pom && served core/unused/0.11.0/unused-0.11.0.pom; then
  pass "serves only the named artifacts of a release kept by reference, and newest releases whole"
else fail "partial: $(find site -name '*.pom' | sort)"; fi
if grep -q 'Serving v0.11.0 whole: ' second.txt && grep -q 'artifacts of v0.9.0 (named by a served POM): ' second.txt; then
  pass "prints what each release adds"; else fail "sizes: $(cat second.txt)"; fi

# Over the size limit it fails at assembly, with what each release adds, keeping the live site.
if "$scripts/maven-fallback-assemble.sh" small 2 3 3 > small.txt 2>&1; then fail "served over the limit: $(cat small.txt)"; else
  if grep -q 'over 3 MB' small.txt && grep -q 'Serving v0.10.0 whole: ' small.txt; then pass "fails over the size limit, naming each release's size"; else
    fail "limit: $(cat small.txt)"; fi
fi
if "$scripts/maven-fallback-assemble.sh" roomy 2 3 50 > roomy.txt 2>&1; then pass "builds within the size limit"; else fail "roomy: $(cat roomy.txt)"; fi
if [[ -z "$("$scripts/maven-fallback-closure.sh" site)" ]]; then pass "leaves nothing a served POM names unresolved"; else
  fail "unresolved: $("$scripts/maven-fallback-closure.sh" site)"; fi

# A Vulkan release whose Core release has no bundle fails the build, keeping the live site.
release vulkan-v0.1.22 vulkan 0.1.22 0.12.0
printf 'vulkan-v0.1.22\nv0.11.0\n' > source/tags
if "$scripts/maven-fallback-assemble.sh" broken 2 3 > broken.txt 2>&1; then fail "served a hole: $(cat broken.txt)"; else
  if grep -q 'com.awakekt.awake:bom:0.12.0' broken.txt; then pass "fails, naming the missing BOM, when a named release has no bundle"; else
    fail "names the missing BOM: $(cat broken.txt)"; fi
fi

# The closure check alone: a site missing the BOM a POM imports reports it.
rm -rf site/com/awakekt/awake/bom/0.9.0
if "$scripts/maven-fallback-closure.sh" site | grep -qx 'com.awakekt.awake:bom:0.9.0'; then pass "closure names a removed BOM"; else
  fail "closure: $("$scripts/maven-fallback-closure.sh" site)"; fi

exit "$failures"

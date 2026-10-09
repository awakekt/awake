#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Builds the fallback Maven repository from release bundles (scripts/central-bundle.sh, attached to
# each GitHub Release): unpacks them side by side, writes the maven-metadata.xml Central would serve
# for each artifact, and an index page saying how to use it.
#
#   scripts/maven-fallback-site.sh build/maven-fallback awake-0.5.0-maven.zip awake-0.4.0-maven.zip
#
# The files are the signed ones staged for Central, under the same coordinates, so a build that adds
# this repository after mavenCentral() resolves a release Central refused without other changes.
set -euo pipefail

usage="usage: maven-fallback-site.sh <site-dir> <bundle.zip>..."
site="${1:?$usage}"
shift
[[ $# -gt 0 ]] || { echo "$usage" >&2; exit 1; }
url="${MAVEN_FALLBACK_URL:-https://awakekt.github.io/awake/}"

rm -rf "$site"
mkdir -p "$site"
for bundle in "$@"; do unzip -q -o "$bundle" -d "$site"; done

# One artifact directory per POM's grandparent: <group path>/<artifactId>/<version>/<file>.pom.
updated="$(date -u +%Y%m%d%H%M%S)"
artifacts=0
while IFS= read -r artifact; do
  artifacts=$((artifacts + 1))
  relative="${artifact#"$site"/}"
  group="$(dirname "$relative" | tr / .)"
  name="$(basename "$relative")"
  versions="$(find "$artifact" -mindepth 2 -maxdepth 2 -name '*.pom' -exec dirname {} \; | xargs -n 1 basename | sort -V)"
  latest="$(tail -n 1 <<< "$versions")"
  metadata="$artifact/maven-metadata.xml"
  {
    echo '<?xml version="1.0" encoding="UTF-8"?>'
    echo '<metadata>'
    echo "  <groupId>$group</groupId>"
    echo "  <artifactId>$name</artifactId>"
    echo '  <versioning>'
    echo "    <latest>$latest</latest>"
    echo "    <release>$latest</release>"
    echo '    <versions>'
    while IFS= read -r version; do echo "      <version>$version</version>"; done <<< "$versions"
    echo '    </versions>'
    echo "    <lastUpdated>$updated</lastUpdated>"
    echo '  </versioning>'
    echo '</metadata>'
  } > "$metadata"
  md5sum "$metadata" | cut -d ' ' -f 1 > "$metadata.md5"
  sha1sum "$metadata" | cut -d ' ' -f 1 > "$metadata.sha1"
done < <(find "$site" -mindepth 3 -name '*.pom' -exec dirname {} \; | xargs -n 1 dirname | sort -u)

[[ "$artifacts" -gt 0 ]] || { echo "The bundles hold no POM; there is nothing to serve." >&2; exit 1; }

# The Core versions held, newest first, read from the module every release publishes.
held="$(find "$site/com/awakekt/awake/core/math" -mindepth 1 -maxdepth 1 -type d -exec basename {} \; 2> /dev/null | sort -rV | sed 's|.*|<li><code>&</code></li>|')"
cat > "$site/index.html" <<EOF
<!doctype html>
<html lang="en">
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Awake Maven fallback</title>
<style>
  body { font: 16px/1.5 system-ui, sans-serif; max-width: 42rem; margin: 2rem auto; padding: 0 1rem; color: #1f2328; background: #fff; }
  pre { background: #f6f8fa; padding: 1rem; overflow-x: auto; }
  @media (prefers-color-scheme: dark) { body { color: #e6edf3; background: #0d1117; } pre { background: #161b22; } }
</style>
<h1>Awake Maven fallback</h1>
<p>Awake releases are published to Maven Central. This repository holds the newest Core releases,
the same signed files under the same coordinates, for a release Central did not accept.</p>
<pre>repositories {
    mavenCentral()
    maven("$url")
}</pre>
<p>Releases held:</p>
<ul>
$held
</ul>
<p><a href="https://github.com/awakekt/awake">github.com/awakekt/awake</a></p>
</html>
EOF

echo "Fallback repository: $artifacts artifacts from $# bundles in $site"

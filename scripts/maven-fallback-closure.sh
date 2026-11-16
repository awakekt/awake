#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Prints what the fallback repository's own POMs name but it does not hold (#1694): each Awake
# (com.awakekt.*) parent, imported BOM and versioned dependency, one `group:artifact:version` a line,
# sorted. Prints nothing when every one resolves within the site.
#
#   scripts/maven-fallback-closure.sh build/maven-fallback
#   scripts/maven-fallback-closure.sh --live https://awakekt.github.io/awake/ build/maven-fallback
#
# A Vulkan release's POMs import the Core BOM and depend on Core artifacts of the Core release it was
# built against, so serving the Vulkan release without that Core release breaks every consumer who
# resolves through the site. With --live, each named POM is fetched from the deployed site instead of
# looked up in the directory, retrying a few times, so a deploy can check what it actually serves.
set -euo pipefail

live=""
if [[ "${1:-}" == "--live" ]]; then
  live="${2:?usage: maven-fallback-closure.sh [--live <url>] <site-dir>}"
  shift 2
fi
site="${1:?usage: maven-fallback-closure.sh [--live <url>] <site-dir>}"
[[ -d "$site" ]] || { echo "No site at $site" >&2; exit 1; }

named="$(
  find "$site" -name '*.pom' -print0 | xargs -0 python3 -c '
import sys, xml.etree.ElementTree as ET
ns = {"m": "http://maven.apache.org/POM/4.0.0"}
def text(node, tag):
    found = node.find("m:" + tag, ns)
    return found.text.strip() if found is not None and found.text else None
for path in sys.argv[1:]:
    root = ET.parse(path).getroot()
    nodes = [root.find("m:parent", ns)]
    nodes += root.findall("m:dependencyManagement/m:dependencies/m:dependency", ns)
    nodes += root.findall("m:dependencies/m:dependency", ns)
    for node in nodes:
        if node is None:
            continue
        group, artifact, version = text(node, "groupId"), text(node, "artifactId"), text(node, "version")
        if group and artifact and version and group.startswith("com.awakekt") and "$" not in version:
            print(f"{group}:{artifact}:{version}")
' | sort -u
)"

while IFS=: read -r group artifact version; do
  [[ -n "$group" ]] || continue
  path="${group//.//}/$artifact/$version/$artifact-$version.pom"
  if [[ -n "$live" ]]; then
    # Pages can answer 503 for a moment around a deploy; only a POM that stays missing counts.
    ok=""
    for attempt in 1 2 3 4 5; do
      if curl -fsS -o /dev/null "${live%/}/$path"; then ok=1; break; fi
      sleep "$attempt"
    done
    [[ -n "$ok" ]] || echo "$group:$artifact:$version"
  elif [[ ! -f "$site/$path" ]]; then
    echo "$group:$artifact:$version"
  fi
done <<< "$named"

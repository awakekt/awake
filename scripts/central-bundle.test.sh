#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Checks scripts/central-bundle.sh against a throwaway staging repository, one case at a time.
set -euo pipefail

script="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/central-bundle.sh"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
cd "$work"

# What Gradle writes into a file repository for one signed artifact: the file, its signature, four
# checksums of each, and maven-metadata.xml with its own checksums one level up.
publish() { # dir/file
  local file="staging/$1"
  mkdir -p "$(dirname "$file")"
  printf 'content of %s\n' "$1" > "$file"
  printf 'signature\n' > "$file.asc"
  for sum in md5 sha1 sha256 sha512; do printf 'sum\n' > "$file.$sum" && printf 'sum\n' > "$file.asc.$sum"; done
  local meta
  meta="$(dirname "$(dirname "$file")")/maven-metadata.xml"
  printf '<metadata/>\n' > "$meta"
  for sum in md5 sha1 sha256 sha512; do printf 'sum\n' > "$meta.$sum"; done
}
fresh() {
  rm -rf staging bundle.zip
  publish com/awakekt/awake/core/math/0.5.0/math-0.5.0.pom
  publish com/awakekt/awake/core/math/0.5.0/math-0.5.0.jar
  publish com/awakekt/awake/build/build-logic/0.5.0/build-logic-0.5.0.jar
}

failures=0
pass() { echo "ok   $1"; }
fail() { echo "FAIL $1"; failures=$((failures + 1)); }

fresh
if output="$("$script" staging bundle.zip)"; then pass "complete staging bundles"; else fail "complete staging bundles"; fi
entries="$(unzip -Z1 bundle.zip | grep -v '/$' | sort)"
expected="$(for f in com/awakekt/awake/build/build-logic/0.5.0/build-logic-0.5.0.jar \
  com/awakekt/awake/core/math/0.5.0/math-0.5.0.jar com/awakekt/awake/core/math/0.5.0/math-0.5.0.pom; do
  printf '%s\n%s.asc\n%s.md5\n%s.sha1\n' "$f" "$f" "$f" "$f"; done | sort)"
if [[ "$entries" == "$expected" ]]; then pass "keeps each file with its .asc, .md5 and .sha1, from the root"; else
  fail "keeps each file with its .asc, .md5 and .sha1, from the root"; diff <(echo "$expected") <(echo "$entries") || true; fi
if [[ "$output" == "Central bundle: 12 files (3 artifacts),"* ]]; then pass "counts what it bundles"; else fail "counts what it bundles: $output"; fi

fresh
rm staging/com/awakekt/awake/core/math/0.5.0/math-0.5.0.jar.asc
if "$script" staging bundle.zip 2> err.txt; then fail "refuses a missing signature"; else
  if grep -q 'Missing com/awakekt/awake/core/math/0.5.0/math-0.5.0.jar.asc' err.txt && [[ ! -f bundle.zip ]]; then
    pass "refuses a missing signature"; else fail "refuses a missing signature: $(cat err.txt)"; fi
fi

fresh
publish com/awakekt/awake/core/math/0.5.1-SNAPSHOT/math-0.5.1-SNAPSHOT.jar
if "$script" staging bundle.zip 2> err.txt; then fail "refuses a snapshot"; else
  if grep -q 'snapshot version was staged' err.txt; then pass "refuses a snapshot"; else fail "refuses a snapshot: $(cat err.txt)"; fi
fi

rm -rf staging && mkdir staging
if "$script" staging bundle.zip 2> /dev/null; then fail "refuses an empty staging repository"; else pass "refuses an empty staging repository"; fi

if "$script" missing bundle.zip 2> /dev/null; then fail "refuses a missing staging repository"; else pass "refuses a missing staging repository"; fi

exit "$failures"

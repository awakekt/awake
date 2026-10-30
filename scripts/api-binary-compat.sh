#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Lists the public JVM signatures present in the published modules' api/*.api dumps (under awake/) at
# <base> and missing at <head>, one "<dump>: <class> <member>" line each. Empty output and exit 0 means
# every released signature is still there; otherwise the script exits 1.
#
# Usage: scripts/api-binary-compat.sh <base> [<head>]
#   <base>  the last release tag, e.g. v0.27.1
#   <head>  a commit; omitted, the working tree's dumps are read
#
# Before 1.0 a released signature stays as a @Deprecated(level = HIDDEN) overload. A hidden
# declaration is dumped as `synthetic`, and an overload may change `final`, `static`, `abstract` or
# `open`, none of which breaks linking, so those modifiers are dropped before comparing. A dump lists
# only direct supertypes, so a released supertype counts as kept while any supertype chain across the
# <head> dumps still reaches it. A dump that disappears entirely is reported once, as "<dump>: removed".
set -euo pipefail

base="$1" head="${2:-}"

dumps() { # rev -> the api dump paths at that revision
  git ls-tree -r --name-only "$1" | grep -E '^awake/(.*/)?api/.*\.api$' || true
}

show() { # rev path -> file content; an empty rev reads the working tree
  if [[ -z "$1" ]]; then cat "$2" 2> /dev/null || true; else git show "$1:$2" 2> /dev/null || true; fi
}

# One "<class>\t<entry>" line per class, supertype and member, with the modifiers normalized away.
signatures() {
  awk '
    function norm(s) {
      gsub(/(^|[ \t])(synthetic|final|static|abstract|open)([ \t]|$)/, " ", s)
      gsub(/(^|[ \t])(synthetic|final|static|abstract|open)([ \t]|$)/, " ", s)
      gsub(/[ \t]+/, " ", s); sub(/^ /, "", s); sub(/ $/, "", s)
      return s
    }
    /^[^ \t].*\{$/ {
      line = $0; sub(/ *\{$/, "", line)
      supers = ""
      if (index(line, " : ") > 0) { supers = substr(line, index(line, " : ") + 3); line = substr(line, 1, index(line, " : ") - 1) }
      cls = norm(line)
      print cls "\t<class>"
      n = split(supers, s, /, */)
      for (i = 1; i <= n; i++) if (s[i] != "") print cls "\t: " s[i]
      next
    }
    /^}/ { cls = ""; next }
    cls != "" && NF { print cls "\t" norm($0) }
  ' | sort -u
}

if [[ -n "$head" ]]; then head_dumps="$(dumps "$head")"; else head_dumps="$(git ls-files -co --exclude-standard | grep -E '^awake/(.*/)?api/.*\.api$' || true)"; fi

# "<class> <supertype>..." for every class with supertypes in every <head> dump.
edges="$(mktemp)"
trap 'rm -f "$edges"' EXIT
while IFS= read -r dump; do show "$head" "$dump"; done <<< "$head_dumps" |
  sed -nE 's/^[^ \t].* class ([^ ]+) : (.*) \{$/\1 \2/p' | sed -E 's/, */ /g' > "$edges"

# Drops "<class>\t: <supertype>" lines whose supertype <class> still reaches through <edges>.
reachable() {
  awk -v edges="$edges" '
    FILENAME == edges { for (j = 2; j <= NF; j++) up[$1] = up[$1] " " $j; next }
    function reaches(c, t,   q, h, seen, k, m, f, i, x) {
      q[1] = c; h = 1; k = 1
      while (h <= k) { x = q[h++]; if (x == t) return 1; if (seen[x]++) continue; m = split(up[x], f, " "); for (i = 1; i <= m; i++) q[++k] = f[i] }
      return 0
    }
    { split($0, p, "\t"); split(p[1], w, " ") }
    p[2] ~ /^: / && reaches(w[length(w)], substr(p[2], 3)) { next }
    { print }
  ' "$edges" -
}

status=0
while IFS= read -r dump; do
  [[ -n "$dump" ]] || continue
  if ! grep -qxF "$dump" <<< "$head_dumps"; then
    echo "$dump: removed"
    status=1
    continue
  fi
  missing="$(comm -23 <(show "$base" "$dump" | signatures) <(show "$head" "$dump" | signatures) | reachable)"
  if [[ -n "$missing" ]]; then
    sed "s|^|$dump: |; s|\t| |" <<< "$missing"
    status=1
  fi
done < <(dumps "$base")
exit "$status"

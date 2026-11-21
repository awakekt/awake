#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Lists the public functions and constructors that a caller at <base> could end with a trailing
# lambda, and that can't take one at <head>, one "<dump>: <class> <member>" line each. Empty output
# and exit 0 means every such call still compiles; otherwise the script exits 1.
#
# Usage: scripts/api-trailing-lambda.sh <base> [<head>]
#   <base>  the last release tag, e.g. v0.61.0
#   <head>  a commit; omitted, the working tree's dumps are read
#
# A released public API never gets a new parameter after a trailing function-typed or `fun interface`
# parameter (#1845). #1832 put `limits` after `keys` in IndexedAssetSource and kept the old
# signatures as @Deprecated(level = HIDDEN) overloads, so every JVM signature stayed and
# api-binary-compat.sh passed, while a 0.60.0 call passing `keys` as a trailing lambda stopped
# compiling. A hidden declaration is dumped as `synthetic`, which is what this script reads: a
# member visible at <base> whose last parameter takes a lambda must still be visible at <head>, with
# the same JVM signature.
#
# A last parameter takes a lambda when its type is a Kotlin function type (kotlin/jvm/functions/Function*,
# suspend ones included) or an interface with exactly one abstract method in the <head> dumps. A
# suspend function's Continuation parameter is not counted. What it can't see:
# - the JVM dumps only (api/**/*.api, not the klib dumps), so a member declared for one platform only;
# - a single-method interface declared outside Awake's dumps, such as one from a dependency;
# - which single-method Kotlin interfaces are `fun interface`s: a plain one is checked too, which can
#   only fail a change that keeps compiling, never pass one that doesn't.
# A member removed altogether is listed too, as api-binary-compat.sh lists it: that call breaks as well.
set -euo pipefail

base="$1" head="${2:-}"

dumps() { # rev -> the JVM api dump paths at that revision
  git ls-tree -r --name-only "$1" | grep -E '^awake/(.*/)?api/.*\.api$' | grep -v '\.klib\.api$' || true
}

show() { # rev path -> file content; an empty rev reads the working tree
  if [[ -z "$1" ]]; then cat "$2" 2> /dev/null || true; else git show "$1:$2" 2> /dev/null || true; fi
}

if [[ -n "$head" ]]; then head_dumps="$(dumps "$head")"; else
  head_dumps="$(git ls-files -co --exclude-standard | grep -E '^awake/(.*/)?api/.*\.api$' | grep -v '\.klib\.api$' || true)"
fi

# Every interface in the <head> dumps with exactly one abstract method, one per line.
sams="$(mktemp)"
trap 'rm -f "$sams"' EXIT
while IFS= read -r dump; do [[ -n "$dump" ]] && show "$head" "$dump"; done <<< "$head_dumps" | awk '
  /^[^ \t].* interface class [^ ]+/ { for (i = 1; i <= NF; i++) if ($i == "class") cls = $(i + 1); n = 0; next }
  /^[^ \t].*\{$/ { cls = ""; next }
  /^}/ { if (cls != "" && n == 1) print cls; cls = ""; next }
  cls != "" && / abstract / && / fun / { n++ }
' > "$sams"

# "<class>\t<name> <descriptor>" for every visible member at <base> whose last parameter takes a lambda.
takes_lambda() {
  awk -v sams="$sams" '
    FILENAME == sams { sam["L" $1 ";"] = 1; next }
    function last_param(desc,   params, types, n, t, c) {
      params = substr(desc, 2, index(desc, ")") - 2)
      n = 0
      while (length(params) > 0) {
        t = ""
        while (substr(params, 1, 1) == "[") { t = t "["; params = substr(params, 2) }
        c = substr(params, 1, 1)
        if (c == "L") { t = t substr(params, 1, index(params, ";")); params = substr(params, index(params, ";") + 1) }
        else { t = t c; params = substr(params, 2) }
        types[++n] = t
      }
      if (n > 0 && types[n] == "Lkotlin/coroutines/Continuation;") n--
      return n > 0 ? types[n] : ""
    }
    /^[^ \t].*\{$/ { line = $0; sub(/ *(: .*)?\{$/, "", line); cls = line; sub(/.* /, "", cls); next }
    /^}/ { cls = ""; next }
    cls != "" && / fun / && !/ synthetic / {
      for (i = 1; i <= NF; i++) if ($i == "fun") { name = $(i + 1); desc = $(i + 2) }
      if (name ~ /\$default$/) next
      t = last_param(desc)
      if (t ~ /^Lkotlin\/jvm\/functions\/Function[0-9N]*;$/ || (t in sam)) print cls "\t" name " " desc
    }
  ' "$sams" -
}

# The same, for every visible member at <head>.
visible() {
  awk '
    /^[^ \t].*\{$/ { line = $0; sub(/ *(: .*)?\{$/, "", line); cls = line; sub(/.* /, "", cls); next }
    /^}/ { cls = ""; next }
    cls != "" && / fun / && !/ synthetic / {
      for (i = 1; i <= NF; i++) if ($i == "fun") print cls "\t" $(i + 1) " " $(i + 2)
    }
  '
}

status=0
while IFS= read -r dump; do
  [[ -n "$dump" ]] || continue
  grep -qxF "$dump" <<< "$head_dumps" || continue # a removed dump is api-binary-compat.sh's to report
  lost="$(comm -23 <(show "$base" "$dump" | takes_lambda | sort -u) <(show "$head" "$dump" | visible | sort -u))"
  if [[ -n "$lost" ]]; then
    sed "s|^|$dump: |; s|\t| |" <<< "$lost"
    status=1
  fi
done < <(dumps "$base")
exit "$status"

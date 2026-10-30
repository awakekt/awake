#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Checks scripts/api-binary-compat.sh against a throwaway repository, one change per case.
set -euo pipefail

script="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/api-binary-compat.sh"
repo="$(mktemp -d)"
trap 'rm -rf "$repo"' EXIT
cd "$repo"
git init -q
git config user.email test@example.com
git config user.name test

dump=awake/ui/api/desktop/ui.api
mkdir -p awake/ui/api/desktop awake/core/api/desktop samples/demo/api
cat > "$dump" << 'API'
public final class a/Style {
	public fun <init> (I)V
	public final fun copy (I)La/Style;
	public static synthetic fun copy$default (La/Style;IILjava/lang/Object;)La/Style;
}

public final class a/Source : a/Base {
	public fun read ()I
}
API
printf 'public abstract interface class a/Base {\n\tpublic abstract fun read ()I\n}\n' > awake/core/api/desktop/core.api
printf 'public final class a/Demo {\n\tpublic final fun run ()V\n}\n' > samples/demo/api/demo.api
git add -A && git commit -qm base
base="$(git rev-parse HEAD)"

failures=0
expect() { # name expected-output
  local actual
  actual="$("$script" "$base" HEAD || true)"
  if [[ "$actual" == "$2" ]]; then echo "ok   $1"; else echo "FAIL $1: expected '$2', got '$actual'"; failures=$((failures + 1)); fi
  git reset -q --hard "$base"
}
commit() { git add -A && git commit -qm change; }
edit() { sed -i.bak "$1" "$dump" && rm "$dump.bak"; } # BSD and GNU sed both take -i.bak

edit 's|^}$|\tpublic final fun extra ()V\n}|' && commit
expect "signature added" ""

edit 's|public fun <init> (I)V|public fun <init> (IZ)V|' && commit
expect "constructor parameter added" "$dump: public class a/Style public fun <init> (I)V"

edit 's|public fun <init> (I)V|public synthetic fun <init> (I)V|; s|public final fun copy (I)|public final synthetic fun copy (I)|' && commit
expect "kept as a hidden overload" ""

edit '/copy\$default/d' && commit
expect "copy\$default dropped" "$dump: public class a/Style public fun copy\$default (La/Style;IILjava/lang/Object;)La/Style;"

edit 's|class a/Source : a/Base|class a/Source : a/Prefetchable|' &&
  printf 'public abstract interface class a/Prefetchable : a/Base {\n}\n' >> awake/core/api/desktop/core.api && commit
expect "supertype kept through a new parent" ""

edit 's|class a/Source : a/Base|class a/Source|' && commit
expect "supertype removed" "$dump: public class a/Source : a/Base"

git rm -q "$dump" && commit
expect "dump removed" "$dump: removed"

: > samples/demo/api/demo.api && commit
expect "unpublished sample ignored" ""

"$script" "$base" > /dev/null && echo "ok   working tree" || { echo "FAIL working tree"; failures=$((failures + 1)); }

exit "$failures"

#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Checks scripts/api-trailing-lambda.sh against a throwaway repository, one change per case.
set -euo pipefail

script="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/api-trailing-lambda.sh"
repo="$(mktemp -d)"
trap 'rm -rf "$repo"' EXIT
cd "$repo"
git init -q
git config user.email test@example.com
git config user.name test

dump=awake/project/api/desktop/project.api
mkdir -p awake/project/api/desktop awake/project/api
cat > "$dump" << 'API'
public abstract interface class a/Keys {
	public abstract fun key (Ljava/lang/String;)[B
}

public abstract interface class a/Two {
	public abstract fun first ()V
	public abstract fun second ()V
}

public final class a/Source {
	public fun <init> (ILkotlin/jvm/functions/Function1;)V
	public fun <init> (ILa/Keys;)V
	public final fun two (ILa/Two;)V
	public final fun middle (Lkotlin/jvm/functions/Function0;I)V
	public final fun load (ILkotlin/jvm/functions/Function2;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;
}
API
printf 'klib.dump\n// a/Source.klib (kotlin.Function0) // not read\n' > awake/project/api/project.klib.api
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
hide() { # member -> marks it synthetic, as @Deprecated(level = HIDDEN) does
  sed -i.bak "s|public fun $1|public synthetic fun $1|; s|public final fun $1|public final synthetic fun $1|" "$dump" && rm -f "$dump.bak"
}

expect "nothing changed" ""

hide '<init> (ILkotlin/jvm/functions/Function1;)V'
sed -i.bak 's|^}$|	public fun <init> (ILkotlin/jvm/functions/Function1;La/Limits;)V\n}|' "$dump" && rm -f "$dump.bak"
commit
expect "a function-typed last parameter followed by a new one, the old form hidden" \
  "$dump: a/Source <init> (ILkotlin/jvm/functions/Function1;)V"

hide '<init> (ILa/Keys;)V'
commit
expect "a single-method interface last, hidden" "$dump: a/Source <init> (ILa/Keys;)V"

hide 'two (ILa/Two;)V'
commit
expect "an interface of two abstract methods last, hidden: no lambda could be passed" ""

hide 'middle (Lkotlin/jvm/functions/Function0;I)V'
commit
expect "a function type that isn't last, hidden" ""

hide 'load (ILkotlin/jvm/functions/Function2;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;'
commit
expect "a suspend function's lambda before its Continuation, hidden" \
  "$dump: a/Source load (ILkotlin/jvm/functions/Function2;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;"

sed -i.bak '/fun <init> (ILa\/Keys;)V/d' "$dump" && rm -f "$dump.bak"
commit
expect "a member removed altogether, which api-binary-compat.sh lists too" "$dump: a/Source <init> (ILa/Keys;)V"

hide '<init> (ILa/Keys;)V'
commit
git checkout -q "$base" -- "$dump"
commit
expect "hidden, then visible again" ""

printf 'klib.dump\n' > awake/project/api/project.klib.api
commit
expect "a klib dump is not read" ""

# Against the working tree, as before a commit.
hide '<init> (ILa/Keys;)V'
actual="$("$script" "$base" || true)"
if [[ "$actual" == "$dump: a/Source <init> (ILa/Keys;)V" ]]; then echo "ok   the working tree, with no <head>"; else
  echo "FAIL the working tree, with no <head>: got '$actual'"; failures=$((failures + 1)); fi
git reset -q --hard "$base"

[[ $failures -eq 0 ]] || { echo "$failures case(s) failed"; exit 1; }

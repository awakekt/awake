#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Fails when a Linux native library in DIR needs a glibc newer than MAX (default 2.35).
#
# A shared library records the newest glibc symbol version it links, and the dynamic loader refuses
# it on any system with an older glibc. The published jars carry one Linux build for every player,
# so that build must link against an old glibc: a runner image that moves to a newer one (as
# `ubuntu-latest` did, to glibc 2.43 in 0.45.0's naga build) otherwise ships a library that no
# current LTS distribution can load.
#
# Usage: tools/check_linux_glibc.sh DIR [MAX]
set -euo pipefail

dir="${1:?usage: check_linux_glibc.sh DIR [MAX]}"
max="${2:-2.35}"
failed=0
found=0
while IFS= read -r -d '' lib; do
    found=1
    need="$(objdump -T "$lib" | grep -o 'GLIBC_[0-9][0-9.]*' | sed 's/GLIBC_//' | sort -uV | tail -n 1)"
    if [[ -z "$need" ]]; then
        echo "$lib: needs no versioned glibc symbol"
    elif [[ "$(printf '%s\n%s\n' "$need" "$max" | sort -V | tail -n 1)" != "$max" ]]; then
        echo "::error file=$lib::needs glibc $need, newer than $max: build it on an older runner"
        objdump -T "$lib" | grep "GLIBC_$need" || true
        failed=1
    else
        echo "$lib: needs glibc $need (at most $max)"
    fi
done < <(find "$dir" -name '*.so' -print0)

if [[ "$found" == 0 ]]; then
    echo "::error::no .so under $dir"
    exit 1
fi
exit "$failed"

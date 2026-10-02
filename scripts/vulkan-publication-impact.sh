#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Prints true when the change between two commits needs the Vulkan family republished, else false.
#
# Usage: scripts/vulkan-publication-impact.sh <base-sha> <head-sha>
#
# A change counts when it touches the family (awake/backend/vulkan/) in a way that can change what
# ships. Markdown, API dumps and hunks that only edit comments or blank lines do not: they changed
# nothing a consumer runs, and each one used to cost a Vulkan release.
#
# A comment line starts with //, /*, */, or a * followed by a space or the line's end (C, C++,
# Kotlin, Java, Objective-C), or with # in CMake files. A Kotlin line that starts with a
# multiplication `* x` inside parentheses reads as a comment here; that is rare, and it errs
# towards skipping a release, so keep multiplications at the end of the line.
set -euo pipefail

base="$1" head="$2"
family='awake/backend/vulkan/'

only_comments() { # file
  local pattern='^[[:space:]]*(//|/\*|\*/|\*([[:space:]]|$))'
  case "$1" in
    *CMakeLists.txt | *.cmake) pattern='^[[:space:]]*#' ;;
  esac
  git diff -U0 --no-renames "$base" "$head" -- "$1" \
    | grep -E '^[+-]' | grep -vE '^(\+\+\+|---) ' | cut -c2- \
    | grep -vE "$pattern|^[[:space:]]*$" | grep -q . && return 1
  return 0
}

while IFS= read -r path; do
  case "$path" in
    "$family"*) ;;
    *) continue ;;
  esac
  case "$path" in
    *.md | */api/*.api) continue ;;
    *.kt | *.kts | *.java | *.c | *.cc | *.cpp | *.h | *.hpp | *.m | *.mm | *CMakeLists.txt | *.cmake)
      only_comments "$path" && continue ;;
  esac
  echo true
  exit 0
done < <(git diff --name-only --no-renames "$base" "$head")

echo false

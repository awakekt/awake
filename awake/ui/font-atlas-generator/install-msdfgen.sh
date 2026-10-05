#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Builds the msdfgen the committed font atlas is generated with and installs it to <prefix>/bin
# (default ~/.local). CI runs this same script before it regenerates the atlas, so a machine that
# builds msdfgen this way produces what CI expects.
#
#   awake/ui/font-atlas-generator/install-msdfgen.sh [prefix]
#
# Needs git, cmake, ninja and a C++ compiler, and the FreeType, libpng and tinyxml2 development
# packages. On Ubuntu: build-essential cmake ninja-build libfreetype-dev libpng-dev libtinyxml2-dev.
set -euo pipefail

# The pin. Move it only together with a regenerated atlas (see README.md).
MSDFGEN_TAG="v1.13"
MSDFGEN_COMMIT="1874bcf7d9624ccc85b4bc9a85d78116f690f35b"

prefix="${1:-$HOME/.local}"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

# Fetch the commit itself, not the tag: a tag can be moved, a commit cannot.
git init --quiet "$work/src"
git -C "$work/src" remote add origin https://github.com/Chlumsky/msdfgen.git
git -C "$work/src" fetch --quiet --depth 1 origin "$MSDFGEN_COMMIT"
git -C "$work/src" checkout --quiet FETCH_HEAD
actual="$(git -C "$work/src" rev-parse HEAD)"
if [ "$actual" != "$MSDFGEN_COMMIT" ]; then
  echo "expected msdfgen $MSDFGEN_TAG at $MSDFGEN_COMMIT but fetched $actual" >&2
  exit 1
fi

# Skia is off because the official release builds use it and draw a different atlas (about 13% of
# the bytes differ). vcpkg is off so FreeType, libpng and tinyxml2 are the system's, as on CI.
cmake -S "$work/src" -B "$work/build" -G Ninja -DCMAKE_BUILD_TYPE=Release \
  -DMSDFGEN_USE_VCPKG=OFF -DMSDFGEN_USE_SKIA=OFF
cmake --build "$work/build"

install -D -m 0755 "$work/build/msdfgen" "$prefix/bin/msdfgen"
echo "Installed msdfgen $MSDFGEN_TAG ($MSDFGEN_COMMIT) to $prefix/bin/msdfgen"

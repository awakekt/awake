#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Publishes templates/<name> of this checkout onto its own template repository's main branch, as
# one commit (awakekt/awake#721).
#
# Copies only the files templates/<name> tracks (`git ls-files | tar`, the same copy
# tools/check_template_consumer.sh takes to build a template on its own) over a fresh clone of the
# repository: tar extraction overwrites a path that already exists there and adds a new one, but
# never removes a path the repository carries that templates/<name> does not. That is how a file
# deliberately left out of the import -- the project template's own GitHub Pages web player and its
# webgpu-pin check script, or anything the repository's own issue templates add later -- stays
# there untouched. It does not propagate a deletion made inside templates/<name> itself; removing a
# published path needs a one-off commit directly in the template repository too.
#
# Usage: tools/publish_template.sh <templates-subdir> <owner/repo> <commit-message>
# Needs TEMPLATES_PUSH_TOKEN: a token with Contents read/write on <owner/repo>.
set -euo pipefail

SRC_DIR=${1:?"Usage: $0 <templates-subdir> <owner/repo> <commit-message>"}
REPO=${2:?"Usage: $0 <templates-subdir> <owner/repo> <commit-message>"}
MESSAGE=${3:?"Usage: $0 <templates-subdir> <owner/repo> <commit-message>"}
TOKEN=${TEMPLATES_PUSH_TOKEN:?"TEMPLATES_PUSH_TOKEN is not set"}

[[ -d "$SRC_DIR" ]] || { echo "No template checkout at $SRC_DIR" >&2; exit 1; }
SRC_DIR=$(cd "$SRC_DIR" && pwd)

WORK=$(mktemp -d "${TMPDIR:-/tmp}/awake-publish-template.XXXXXX")
trap 'rm -rf "$WORK"' EXIT
CLONE="$WORK/repo"

echo "==> Cloning $REPO"
git clone --quiet --depth 1 "https://${TOKEN}@github.com/${REPO}.git" "$CLONE"

echo "==> Copying $SRC_DIR onto $REPO (adds and overwrites only; never deletes a repository-only path)"
git -C "$SRC_DIR" ls-files -z | (cd "$SRC_DIR" && tar --null -T - -cf -) | tar -x -C "$CLONE"

cd "$CLONE"
git add -A
if git diff --cached --quiet; then
  echo "No changes for $REPO at this version; nothing to push."
  exit 0
fi

git -c user.name="github-actions[bot]" \
  -c user.email="41898282+github-actions[bot]@users.noreply.github.com" \
  commit --quiet -m "$MESSAGE"
# A plain push, not --force: something else landing on main between the clone above and this push
# rejects it rather than overwriting that commit. Re-running this script (the workflow's
# workflow_dispatch) clones again and retries from the new tip.
git push --quiet origin HEAD:main
echo "Pushed to $REPO: $MESSAGE"

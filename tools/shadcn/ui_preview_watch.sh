#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# INVESTIGATION: re-runs the preview on change.
# Kinds are defined in docs/tasks/2026-08-23-ui-tooling-formalization-plan.md. Only a GATE can fail
# a build; visual tooling tests cover the helper itself.
#
# Near-real-time UI preview loop: Gradle --continuous regenerates the ui-showcase preview gallery
# on source change, scoped to the narrow preview-writing test class rather than the module's whole
# desktopTest suite. A tiny static server auto-reloads the browser tab once the gallery HTML
# changes. See docs/reference/developer-docs.md's "Live Preview Loop" section.
#
# This served the deleted `uiComponentLookupReport` merge of the showcase and headless galleries.
# It now watches the showcase gallery, which is the one with per-component images.
set -euo pipefail
# Two levels: shadcn/ -> tools/ -> repository root.
cd "$(dirname "$0")/../.."

PORT="${1:-8090}"
REPORT_DIR="samples/ui-showcase/build/reports/ui-previews"

./gradlew \
    :samples:ui-showcase:desktopTest --tests "*UiShowcasePreviewDocsTest*" \
    --continuous -q &
GRADLE_PID=$!
trap 'kill "$GRADLE_PID" 2>/dev/null || true' EXIT

exec python3 tools/shadcn/ui_preview_server.py "$REPORT_DIR" "$PORT"

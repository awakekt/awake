# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
"""MkDocs hooks for release-coupled public documentation."""

from __future__ import annotations

import os


def on_page_markdown(markdown, page, config, files):
    """Render the library version used by installation examples.

    Release builds set ``AWAKE_DOCS_VERSION`` from the Git tag. The value in
    ``mkdocs.yml`` keeps local previews deterministic and reviewable.
    """

    version = os.environ.get("AWAKE_DOCS_VERSION") or config.extra["awake_version"]
    return markdown.replace("{{ awake_version }}", version)

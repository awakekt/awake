# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
"""MkDocs hooks for release-coupled public documentation."""

from __future__ import annotations

import html
import os
import re


def on_page_markdown(markdown, page, config, files):
    """Render the library version used by installation examples.

    Release builds set ``AWAKE_DOCS_VERSION`` from the Git tag. The value in
    ``mkdocs.yml`` keeps local previews deterministic and reviewable.
    """

    version = os.environ.get("AWAKE_DOCS_VERSION") or config.extra["awake_version"]
    vulkan_version = os.environ.get("AWAKE_DOCS_VULKAN_VERSION") or config.extra["awake_vulkan_version"]
    vulkan_core_version = (
        os.environ.get("AWAKE_DOCS_VULKAN_CORE_VERSION") or config.extra["awake_vulkan_core_version"]
    )
    markdown = (
        markdown.replace("{{ awake_version }}", version)
        .replace("{{ awake_vulkan_version }}", vulkan_version)
        .replace("{{ awake_vulkan_core_version }}", vulkan_core_version)
    )
    if not page.meta.get("description"):
        description = _summary(markdown)
        if description:
            page.meta["description"] = description
    return markdown


_MAX_DESCRIPTION = 200
_LEDE = re.compile(r'<p class="awake-lede">(.*?)</p>', re.DOTALL)
_NOT_PROSE = ("#", "<", "!!!", "???", "|", "-", "*", "1.", "=", ">")


def _plain(text):
    """Reduce inline Markdown and HTML to the words a search result should show."""

    text = re.sub(r"<[^>]+>", "", text)
    text = re.sub(r"!?\[([^\]]*)\]\([^)]*\)", r"\1", text)
    text = re.sub(r"[`*_]", "", text)
    return re.sub(r"\s+", " ", html.unescape(text)).strip()


def _summary(markdown):
    """The page's lede, or else its first prose paragraph, as a meta description.

    Most pages lead with a one-sentence lede, so search results and link previews describe the
    page instead of repeating the site's description. A page can still set ``description:`` in
    its front matter.
    """

    lede = _LEDE.search(markdown)
    if lede:
        text = _plain(lede.group(1))
    else:
        text = ""
        for block in re.split(r"\n\s*\n", markdown):
            stripped = block.strip()
            # A page that opens with code has no prose lede.
            if stripped.startswith(("```", "~~~")):
                break
            if stripped and not stripped.startswith(_NOT_PROSE):
                text = _plain(stripped)
                break
    if len(text) > _MAX_DESCRIPTION:
        text = text[:_MAX_DESCRIPTION].rsplit(" ", 1)[0].rstrip(",;:") + "…"
    return text

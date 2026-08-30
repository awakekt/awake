#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# GATE: exit 1 means the vendoring transform is wrong.
# Kinds are defined in docs/tasks/2026-08-23-ui-tooling-formalization-plan.md. Only a GATE can fail
# a build; `scripts/awake verify` runs every one.
"""Unit tests for the alias rewrite in vendor_reference_components.py.

The rewrite is the whole judgement in that script -- everything else is copying files. It runs
against every component the parity screenshots are taken from, so a wrong rule silently produces a
reference that will not resolve, and the failure surfaces as a broken npm build much later.

Deliberately no filesystem or pinned checkout: these test the pure function, so they run anywhere
and in milliseconds.
"""
from __future__ import annotations

import pytest

from vendor_reference_components import vendor


def test_component_alias_becomes_a_sibling_import():
    source = 'import { Button } from "@/registry/new-york-v4/ui/button"\n'
    assert vendor(source) == 'import { Button } from "./button"\n'


def test_bare_utils_alias_climbs_one_level():
    # src/ui/x.tsx -> src/lib/utils, so `..` not `.`.
    assert vendor('import { cn } from "@/lib/utils"\n') == 'import { cn } from "../lib/utils"\n'


def test_registry_qualified_utils_resolves_to_the_same_place():
    # shadcn writes this one two ways; both have to land on the app's single utils module.
    source = 'import { cn } from "@/registry/new-york-v4/lib/utils"\n'
    assert vendor(source) == 'import { cn } from "../lib/utils"\n'


def test_the_longer_utils_alias_wins_over_the_ui_prefix():
    # `@/registry/new-york-v4/lib/utils` and `@/registry/new-york-v4/ui/` share a prefix. If the
    # `ui/` rule ran first it would leave `./lib/utils`, pointing at a directory that does not exist
    # under src/ui. Order is load-bearing, so it is pinned.
    source = (
        'import { cn } from "@/registry/new-york-v4/lib/utils"\n'
        'import { Badge } from "@/registry/new-york-v4/ui/badge"\n'
    )
    assert vendor(source) == (
        'import { cn } from "../lib/utils"\n'
        'import { Badge } from "./badge"\n'
    )


def test_third_party_imports_are_left_alone():
    source = (
        'import * as React from "react"\n'
        'import { Slot } from "radix-ui"\n'
        'import { cva } from "class-variance-authority"\n'
    )
    assert vendor(source) == source


def test_an_unmapped_alias_fails_loudly_rather_than_emitting_it():
    # The whole reason the hand-copied version drifted was that nothing complained. An alias this
    # script has not been taught must stop the run, not produce a file that cannot resolve.
    with pytest.raises(SystemExit) as caught:
        vendor('import { useIsMobile } from "@/registry/new-york-v4/hooks/use-mobile"\n')
    assert "@/registry/new-york-v4/hooks/use-mobile" in str(caught.value)


def test_a_known_hooks_alias_is_mapped_not_rejected():
    source = 'import { useThing } from "@/hooks/use-thing"\n'
    assert vendor(source) == 'import { useThing } from "../hooks/use-thing"\n'


def test_content_is_otherwise_untouched():
    # A rewrite that reformatted or reordered would make every future diff unreadable.
    source = (
        '"use client"\n\n'
        'const buttonVariants = cva(\n'
        '  "inline-flex items-center bg-accent data-[state=on]:bg-accent",\n'
        ')\n'
    )
    assert vendor(source) == source

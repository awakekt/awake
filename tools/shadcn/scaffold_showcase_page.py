#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
"""scaffold_showcase_page.py — Automated Showcase Page Scaffolder for Awake UI

Generates:
  1. Showcase Page: `samples/ui-showcase/.../pages/<category>/<Name>Page.kt`
  2. Registers in `ShowcaseCatalog.kt` automatically.
"""

import argparse
import re
import sys
from pathlib import Path

CATEGORY_MAP = {
    "inputs": ("Inputs", "inputs"),
    "layout": ("Layout", "layout"),
    "overlays": ("Overlays", "overlays"),
    "status": ("Status", "status"),
    "typography": ("Typography", "typography"),
    "blocks": ("Blocks", "blocks"),
    "gettingstarted": ("GettingStarted", "gettingstarted"),
}


def generate_page_source(name: str, category_enum: str, category_dir: str, description: str) -> str:
    page_id = re.sub(r"(?<!^)(?=[A-Z])", "-", name).lower()
    return f"""/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.ui.pages.{category_dir}

import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.sample.uishowcase.state.UiShowcaseRuntimeState
import com.awakekt.awake.sample.uishowcase.ui.ShowcaseCategory
import com.awakekt.awake.sample.uishowcase.ui.ShowcasePage
import com.awakekt.awake.ui.shadcn.components.shadcnMuted

internal val {name}Page = ShowcasePage(
    id = "{page_id}",
    title = "{name}",
    category = ShowcaseCategory.{category_enum},
    description = "{description}",
    usageCode = "// TODO: Add usage snippet",
    hero = {{ state -> {name}Hero(state) }},
)

context(_: Composer)
private fun {name}Hero(state: UiShowcaseRuntimeState) {{
    Column {{
        shadcnMuted("{name} component interactive preview.")
        Spacer(Modifier.height(12.dp))
        // TODO: Render component here
    }}
}}
"""


def register_in_catalog(catalog_path: Path, name: str, category_dir: str, dry_run: bool = False) -> bool:
    content = catalog_path.read_text(encoding="utf-8")
    import_stmt = f"import com.awakekt.awake.sample.uishowcase.ui.pages.{category_dir}.{name}Page"
    page_entry = f"    {name}Page,"

    if import_stmt in content and page_entry in content:
        print(f"  ℹ️ {name}Page is already registered in ShowcaseCatalog.kt")
        return False

    # Insert import in alphabetical order among imports
    lines = content.splitlines()
    import_idx = -1
    for idx, line in enumerate(lines):
        if line.startswith("import com.awakekt.awake.sample.uishowcase.ui.pages."):
            import_idx = idx

    if import_idx != -1 and import_stmt not in content:
        lines.insert(import_idx + 1, import_stmt)

    # Insert into ShowcasePages list
    content = "\n".join(lines)
    list_marker = "internal val ShowcasePages: List<ShowcasePage> = listOf("
    if list_marker in content and page_entry not in content:
        content = content.replace(list_marker, f"{list_marker}\n{page_entry}")

    if dry_run:
        print(f"  [dry-run] would register {name}Page in {catalog_path}")
    else:
        catalog_path.write_text(content, encoding="utf-8")
        print(f"  ✅ Registered {name}Page in {catalog_path.name}")
    return True


def scaffold_page(project_root: Path, name: str, category_key: str, description: str, dry_run: bool = False) -> None:
    category_key = category_key.lower().strip()
    if category_key not in CATEGORY_MAP:
        print(f"❌ Unknown category: '{category_key}'. Available: {', '.join(CATEGORY_MAP.keys())}", file=sys.stderr)
        sys.exit(1)

    category_enum, category_dir = CATEGORY_MAP[category_key]
    pages_base = project_root / "samples" / "ui-showcase" / "src" / "commonMain" / "kotlin" / "io" / "github" / "ronjunevaldoz" / "awake" / "sample" / "uishowcase" / "ui" / "pages"
    target_file = pages_base / category_dir / f"{name}Page.kt"
    catalog_file = project_root / "samples" / "ui-showcase" / "src" / "commonMain" / "kotlin" / "io" / "github" / "ronjunevaldoz" / "awake" / "sample" / "uishowcase" / "ui" / "ShowcaseCatalog.kt"

    print(f"\n🎨 Scaffolding Showcase Page: '{name}Page'")
    print(f"   Category : {category_enum} ({category_dir})")
    print(f"   Target   : {target_file}")

    if target_file.exists():
        print(f"⚠️ Target file already exists: {target_file}")
    else:
        source_code = generate_page_source(name, category_enum, category_dir, description)
        if dry_run:
            print(f"  [dry-run] would create {target_file}")
        else:
            target_file.parent.mkdir(parents=True, exist_ok=True)
            target_file.write_text(source_code, encoding="utf-8")
            print(f"  ✅ Created {target_file.name}")

    if catalog_file.exists():
        register_in_catalog(catalog_file, name, category_dir, dry_run)


def main() -> int:
    parser = argparse.ArgumentParser(description="Scaffold a new UI Showcase Page in Awake")
    parser.add_argument("--name", type=str, required=True, help="Component name (e.g. AspectRatio, Chart)")
    parser.add_argument("--category", type=str, required=True, help="Category: inputs, layout, overlays, status, typography, blocks")
    parser.add_argument("--description", type=str, default="Interactive component showcase preview.", help="Page description")
    parser.add_argument("--project", type=Path, default=Path.cwd(), help="Awake project root")
    parser.add_argument("--dry-run", action="store_true", help="Preview without modifying disk")

    args = parser.parse_args()
    scaffold_page(args.project, args.name, args.category, args.description, args.dry_run)
    return 0


if __name__ == "__main__":
    sys.exit(main())

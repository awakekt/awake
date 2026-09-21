#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
"""Regression tests for the generated publication/dependency inventory."""

from __future__ import annotations

import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

from tools.verify_publication_closure import VULKAN_FAMILY, inventory, is_published
from tools.verify_published_artifacts import internal_dependency_versions, snapshot_dependencies

PUBLICATIONS = inventory()


class PublicationInventoryTest(unittest.TestCase):
    def test_detects_both_convention_and_explicit_publications(self) -> None:
        self.assertTrue(is_published('plugins { id("com.awakekt.awake.plugin.publish") }'))
        self.assertTrue(
            is_published(
                'plugins { id("com.vanniktech.maven.publish") }\n'
                'mavenPublishing { publishToMavenCentral() }'
            )
        )
        self.assertFalse(is_published('plugins { id("com.awakekt.awake.plugin.library") }'))

    def test_inventory_contains_expected_release_families(self) -> None:
        publications = inventory()
        self.assertEqual(62, len(publications))
        self.assertEqual(59, sum(item["releaseFamily"] == "core" for item in publications))
        self.assertEqual(3, sum(item["releaseFamily"] == "vulkan" for item in publications))
        self.assertEqual(
            VULKAN_FAMILY,
            {item["module"] for item in publications if item["releaseFamily"] == "vulkan"},
        )

    def test_coordinates_include_android_native_override(self) -> None:
        android_native = next(
            item for item in inventory()
            if item["module"] == ":awake:backend:vulkan:bindings:android-native"
        )
        self.assertEqual(
            {"group": "com.awakekt.awake", "artifact": "vulkan-kmp-android-native"},
            android_native["coordinate"],
        )

    def test_every_main_source_project_edge_is_published(self) -> None:
        published = {item["module"] for item in inventory()}
        missing = [
            (item["module"], edge["target"], edge["scope"])
            for item in inventory()
            for edge in item["dependencies"]
            if edge["target"] not in published
        ]
        self.assertEqual([], missing)

    def test_inventory_preserves_api_and_implementation_edges(self) -> None:
        scopes = {
            edge["scope"]
            for item in inventory()
            for edge in item["dependencies"]
        }
        self.assertEqual({"api", "implementation"}, scopes)

    def test_vulkan_release_pom_pins_core_and_family_versions_exactly(self) -> None:
        core_math = next(item for item in PUBLICATIONS if item["module"] == ":awake:core:math")
        group = core_math["coordinate"]["group"]
        artifact = core_math["coordinate"]["artifact"]
        with TemporaryDirectory() as temp_dir:
            pom = Path(temp_dir) / "vulkan.pom"
            pom.write_text(
                """<project xmlns="http://maven.apache.org/POM/4.0.0">
                  <groupId>com.awakekt.awake.backend</groupId><artifactId>vulkan</artifactId>
                  <dependencies><dependency>
                    <groupId>""" + group + """</groupId><artifactId>""" + artifact + """</artifactId>
                    <version>0.1.0-alpha.3</version>
                  </dependency></dependencies>
                </project>"""
            )
            self.assertEqual([], internal_dependency_versions(pom, "vulkan", "0.1.0", "0.1.0-alpha.3"))
            self.assertEqual([], snapshot_dependencies(pom))
            pom.write_text(pom.read_text().replace("0.1.0-alpha.3", "0.1.0-alpha.4-SNAPSHOT"))
            self.assertNotEqual(
                [],
                internal_dependency_versions(pom, "vulkan", "0.1.0", "0.1.0-alpha.3"),
            )
            self.assertEqual(1, len(snapshot_dependencies(pom)))

    def test_vulkan_snapshot_may_pin_the_exact_core_integration_snapshot(self) -> None:
        core_math = next(item for item in PUBLICATIONS if item["module"] == ":awake:core:math")
        group = core_math["coordinate"]["group"]
        artifact = core_math["coordinate"]["artifact"]
        with TemporaryDirectory() as temp_dir:
            pom = Path(temp_dir) / "vulkan.pom"
            pom.write_text(
                """<project xmlns="http://maven.apache.org/POM/4.0.0">
                  <groupId>com.awakekt.awake.backend</groupId><artifactId>vulkan</artifactId>
                  <dependencies><dependency>
                    <groupId>""" + group + """</groupId><artifactId>""" + artifact + """</artifactId>
                    <version>0.1.0-alpha.4-SNAPSHOT</version>
                  </dependency></dependencies>
                </project>"""
            )
            self.assertEqual(
                [],
                internal_dependency_versions(
                    pom, "vulkan", "0.1.0-SNAPSHOT", "0.1.0-alpha.4-SNAPSHOT"
                ),
            )
            self.assertEqual(1, len(snapshot_dependencies(pom)))


if __name__ == "__main__":
    unittest.main()

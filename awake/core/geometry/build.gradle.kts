/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    id("awake.kmp-library-convention")
    id("awake.publish-convention")
    id("awake.dokka-convention")
    id("awake.detekt-convention")
    id("awake.spotless-convention")
}

kotlin {
    android {
        namespace = "com.awakekt.awake.core.geometry"
    }

    sourceSets {
        commonMain.dependencies {
            // Aabb, Vec3 -- api, not implementation: MeshGeometry.bounds returns an Aabb, so a
            // consumer needs the type visible. awake:core:math has no dependencies of its own,
            // so this costs nothing transitively.
            api(project(":awake:core:math"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Core Geometry")
        description.set("Portable mesh geometry math: integer-normalization decode, mesh simplification")
    }
}

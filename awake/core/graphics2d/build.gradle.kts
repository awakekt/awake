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
        namespace = "io.github.awakelab.awake.core.graphics2d"
    }

    sourceSets {
        commonMain.dependencies {
            // Rectangle, Vec2, Size2D appear in these types' own public signatures.
            api(project(":awake:core:math2d"))
            api(project(":awake:core:color"))
            // VertexFormat/VertexAttribute/GpuDataShape -- UiVertexLayout describes the 2D
            // vertex layout in exactly the terms the 3D side already uses.
            api(project(":awake:core:geometry"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Core Graphics 2D")
        description.set("The CPU 2D draw vocabulary: draw commands, paths, gradients, meshes")
    }
}

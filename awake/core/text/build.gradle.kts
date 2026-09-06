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
        namespace = "com.awakekt.awake.core.text"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":awake:core:math2d"))
            implementation(project(":awake:core:color"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Core Text")
        description.set("Font atlases, glyph metrics and text shaping shared by every UI surface")
    }
}

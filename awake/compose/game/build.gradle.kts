/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    id("com.awakekt.awake.plugin.library")
    id("com.awakekt.awake.plugin.publish")
    id("com.awakekt.awake.plugin.dokka")
    id("com.awakekt.awake.plugin.detekt")
    id("com.awakekt.awake.plugin.spotless")
}

kotlin {
    android {
        namespace = "com.awakekt.awake.compose.game"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":awake:compose:foundation"))
            implementation(project(":awake:core:input"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Compose Game")
        description.set("Game UI built on Awake Compose: on-screen touch controls")
    }
}

tasks.named<Test>("desktopTest") {
    // Allocation gates count every byte. With escape analysis on, C2 removes about half of what a frame
    // allocates, which Android's runtime does not (#1169); :awake:ui:canvas and :awake:compose:ui set the same flag.
    jvmArgs("-XX:-DoEscapeAnalysis")
}

/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
/*
 * Awake
 * Awake.awake-asset-terrain
 *
 * Copyright (c) Ron June Valdoz 2026.
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
        namespace = "com.awakekt.awake.asset.terrain"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":awake:core:color"))
            api(project(":awake:core:geometry"))
            api(project(":awake:core:math"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Asset Terrain")
        description.set("Backend-neutral heightmap terrain assets and grid mesh generation")
    }
}

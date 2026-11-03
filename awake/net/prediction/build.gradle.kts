/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
// Client-side prediction and reconciliation for a locally controlled entity: a history of the poses
// its inputs left it at, and a pure decision of what a server snapshot corrects. Plain numbers in and
// out, so it needs no ECS, scene, renderer or transport; `awake:scene:net` wraps it for root ECS transforms.
plugins {
    id("com.awakekt.awake.plugin.library")
    id("com.awakekt.awake.plugin.publish")
    id("com.awakekt.awake.plugin.dokka")
    id("com.awakekt.awake.plugin.detekt")
    id("com.awakekt.awake.plugin.spotless")
}

kotlin {
    android {
        namespace = "com.awakekt.awake.net.prediction"
    }

    sourceSets {
        commonMain.dependencies {
            // api: PredictionHistory.poseAt and PredictedEntity.position speak Vec3f.
            api(project(":awake:core:math"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Net Prediction")
        description.set("Client-side prediction and reconciliation: an input pose history and a pure decision of what a server snapshot corrects")
    }
}

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
        namespace = "com.awakekt.awake.physics.ragdoll"
        // Instrumented: every test here needs jolt-jni's native library, and its Android artifact
        // ships device ABIs only. `sourceSetTreeName = "test"` lets androidDeviceTest inherit
        // commonTest.
        withDeviceTestBuilder { sourceSetTreeName = "test" }
            .configure { instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    }

    sourceSets {
        commonMain.dependencies {
            // Bodies and constraints through the backend-neutral API; the application picks the
            // backend.
            api(project(":awake:physics:api"))
            // Skeleton and AnimationPose, for RagdollRig and RagdollSkeleton.
            api(project(":awake:core:animation"))
            api(project(":awake:core:math"))
        }
        named("androidDeviceTest") {
            dependencies {
                implementation(libs.androidx.test.runner)
            }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            // createJoltPhysicsWorld suspends while the native library loads.
            implementation(libs.kotlinx.coroutines.test)
            // A ragdoll is bodies and constraints; whether it holds together is a question only a
            // real solver answers.
            implementation(project(":awake:backend:jolt"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Physics Ragdoll")
        description.set("Ragdolls built from physics bodies and constraints, and the skeleton drive that makes them visible")
    }
}

// jolt-jni has no JVM host library for Android, and every test here simulates; the device test
// (`connectedAndroidDeviceTest`) runs the same sources.
tasks.matching { it.name == "testAndroidHostTest" }.configureEach { enabled = false }

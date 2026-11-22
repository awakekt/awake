/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
plugins {
    id("com.awakekt.awake.plugin.kmp-library")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    android {
        compileSdk = (findProperty("android.compileSdk") as String).toInt()
        minSdk = (findProperty("android.minSdk") as String).toInt()
        withHostTest {}
    }
}

// A module whose native code finds its classes by name keeps those names in consumer-rules.pro, and
// every app that shrinks its release gets them: Android apps from the library, and desktop apps from
// the jar (configured in kmp-library), under META-INF/proguard, where R8 looks for a library's rules.
// verifyKeepRules checks the file.
val keepRulesFile = layout.projectDirectory.file("consumer-rules.pro")
if (keepRulesFile.asFile.isFile) {
    kotlin {
        android {
            optimization {
                consumerKeepRules.publish = true
                consumerKeepRules.file(keepRulesFile)
            }
        }
    }
}

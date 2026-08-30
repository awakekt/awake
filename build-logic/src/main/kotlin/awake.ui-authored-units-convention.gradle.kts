/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */

val verifyUiAuthoredUnits = tasks.register<VerifyUiAuthoredUnitsTask>("verifyUiAuthoredUnits") {
    group = "verification"
    description = "Reject numeric `.px` literals in authored shared/sample UI source."
    modulePath.set(project.path)
    sourceFiles.from(
        fileTree("src") {
            include("**/*Main/**/*.kt")
            exclude("**/*Test/**/*.kt")
        }
    )
}

tasks.named("check").configure {
    dependsOn(verifyUiAuthoredUnits)
}

if (project.path == ":awake:ui:shadcn") {
    val verifyUiTestLifecycle = tasks.register<VerifyUiTestLifecycleTask>("verifyUiTestLifecycle") {
        group = "verification"
        description = "Reject manual UI frame setup in ordinary component test fixtures."
        modulePath.set(project.path)
        sourceFiles.from(
            fileTree("src") {
                include("**/*Test/**/*.kt")
            },
        )
    }

    tasks.named("check").configure {
        dependsOn(verifyUiTestLifecycle)
    }
}

/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import com.awakekt.awake.build.tasks.AwakeRepositoryVerificationTask
import com.awakekt.awake.build.tasks.ReleaseCutTask
import com.awakekt.awake.build.tasks.VerifyPublishedArtifactsTask
import org.gradle.api.GradleException

tasks.register<AwakeRepositoryVerificationTask>("awakeVerify") {
    group = "verification"
    description = "Run Awake's repository, documentation, publication, and ownership gates."
}

val verifyGenerated = tasks.register("verifyGenerated") {
    group = "verification"
    description = "Verify that required generated product sources are present."
    doLast {
        val outputs = listOf(
            "awake/tailwind/src/commonMain/kotlin/com/awakekt/awake/tailwind/Tw.kt",
            "awake/core/text/src/commonMain/kotlin/com/awakekt/awake/core/text/font/RobotoRegularUiFontData.kt",
        )
        val missing = outputs.filterNot { project.rootProject.file(it).isFile }
        if (missing.isNotEmpty()) {
            throw GradleException("Generated output is missing:\n" + missing.joinToString("\n") { "  $it" })
        }
        logger.lifecycle("Generated product outputs are present")
    }
}

tasks.register<ReleaseCutTask>("releaseCut") {
    group = "release"
    description = "Promote CHANGELOG Unreleased notes and create a Core release tag."
}

tasks.register<VerifyPublishedArtifactsTask>("verifyPublishedArtifacts") {
    group = "verification"
    description = "Verify Maven-local POM metadata and published native JAR contents."
}

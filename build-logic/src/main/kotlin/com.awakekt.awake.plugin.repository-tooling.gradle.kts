/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import com.awakekt.awake.build.tasks.AwakeRepositoryVerificationTask
import com.awakekt.awake.build.tasks.VerifyPublishedArtifactsTask

plugins {
    id("com.awakekt.awake.plugin.release-cut")
}

tasks.register<AwakeRepositoryVerificationTask>("awakeVerify") {
    group = "verification"
    description = "Run Awake's repository, documentation, publication, and ownership gates."
}

tasks.register<VerifyPublishedArtifactsTask>("verifyPublishedArtifacts") {
    group = "verification"
    description = "Verify Maven-local POM metadata and published native JAR contents."
}

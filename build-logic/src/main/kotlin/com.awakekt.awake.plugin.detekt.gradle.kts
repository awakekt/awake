/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import dev.detekt.gradle.Detekt
import dev.detekt.gradle.DetektCreateBaselineTask
import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("dev.detekt")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

extensions.configure<DetektExtension> {
    toolVersion.set(libs.findVersion("detekt").get().requiredVersion)
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig.set(true)
    baseline.set(layout.projectDirectory.file("detekt-baseline.xml"))
}

fun detektSources() = fileTree("src") {
    include("**/*.kt")
    exclude("**/build/**", "**/attic/**")
}

// The plain tasks read every source set, tests included, as they did before the per-source-set and
// type-resolved tasks detekt 2 adds; those keep the sources detekt gives them.
tasks.withType<Detekt>().matching { it.name == "detekt" }.configureEach { setSource(detektSources()) }
tasks.withType<DetektCreateBaselineTask>().matching { it.name == "detektBaseline" }.configureEach { setSource(detektSources()) }

// detekt 2's type-resolved run over the desktop main compilation, which compiles commonMain with it. It
// reports only the type-resolved rules config/detekt/detekt-type-resolved.yml turns on, such as
// SuspendFunSwallowedCancellation, against its own baseline, detekt-baseline-mainDesktop.xml.
val typeResolvedConfig = listOf(
    rootProject.file("config/detekt/detekt.yml"),
    rootProject.file("config/detekt/detekt-type-resolved.yml"),
)
val typeResolvedBaseline = layout.projectDirectory.file("detekt-baseline-mainDesktop.xml")
tasks.withType<Detekt>().matching { it.name == "detektMainDesktop" }.configureEach {
    config.setFrom(typeResolvedConfig)
    baseline.set(typeResolvedBaseline.takeIf { it.asFile.exists() })
}
tasks.withType<DetektCreateBaselineTask>().matching { it.name == "detektBaselineMainDesktop" }.configureEach {
    config.setFrom(typeResolvedConfig)
    baseline.set(typeResolvedBaseline)
}

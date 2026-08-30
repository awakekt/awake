/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
plugins {
    base
}

// Same "point AWAKE_SNAPSHOT_ROOT at this module's own directory" fix ui-showcase's original
// inline task needed -- project.rootDir is the *build's* root (repo root), which silently
// points snapshot read/write at a git-untracked folder outside this module otherwise (see
// AwakeUiSnapshot.desktop.kt's AWAKE_SNAPSHOT_ROOT handling).
// A preview capture renders through Vulkan, and shipped shaders are WGSL, so the capture
// compiles them with the same native naga binding the renderer uses -- which the forked test
// JVM can only find if it is built and pointed at. Set here rather than per module: every
// consumer of this convention captures previews, so every one of them needs it.
val nagaLibrary = project(":awake:asset:shader-compiler").layout.projectDirectory
    .dir("rust-native/target/release")
    .asFile.resolve(HostOs.libraryFileName("awake_naga"))

tasks.matching { it.name == "desktopTest" }.configureEach {
    doFirst {
        delete(layout.buildDirectory.dir("ui-previews"))
        delete(layout.buildDirectory.dir("reports/ui-previews"))
    }
    dependsOn(":awake:asset:shader-compiler:buildNagaDesktop")
    (this as Test).systemProperty("awake.naga.library", nagaLibrary.path)
    (this as Test).systemProperty("AWAKE_SNAPSHOT_ROOT", project.projectDir.absolutePath)
    // `-DAWAKE_RECORD_SNAPSHOTS=true` on the Gradle CLI only sets the property on Gradle's own
    // JVM -- desktopTest runs in a forked test JVM, so forward it explicitly.
    System.getProperty("AWAKE_RECORD_SNAPSHOTS")?.let {
        (this as Test).systemProperty("AWAKE_RECORD_SNAPSHOTS", it)
    }
    finalizedBy("uiPreviewReport")
}

tasks.register<UiPreviewReportTask>("uiPreviewReport") {
    group = "documentation"
    description = "Generate an HTML gallery for this module's AwakeUiPreviewWriter-recorded previews."
    reportTitle.convention("${project.name} UI Previews")
    previewsDir.set(layout.buildDirectory.dir("ui-previews"))
    manifestFile.set(layout.buildDirectory.file("ui-previews/previews.tsv"))
    reportFile.set(layout.buildDirectory.file("reports/ui-previews/index.html"))
    designReportFile.set(layout.buildDirectory.file("reports/ui-previews/design-report.json"))
}

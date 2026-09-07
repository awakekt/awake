/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import com.awakekt.awake.build.extension.*
import com.awakekt.awake.build.tasks.*
plugins {
    base
}

tasks.matching { it.name == "desktopTest" }.configureEach {
    doFirst {
        delete(layout.buildDirectory.dir("ui-previews"))
        delete(layout.buildDirectory.dir("reports/ui-previews"))
    }
    val shaderCompilerProj = project.findProject(":awake:asset:shader-compiler")
    if (shaderCompilerProj != null) {
        dependsOn("${shaderCompilerProj.path}:buildNagaDesktop")
        val nagaLibrary = shaderCompilerProj.layout.projectDirectory
            .dir("rust-native/target/release")
            .asFile.resolve(HostOs.libraryFileName("awake_naga"))
        (this as Test).systemProperty("awake.naga.library", nagaLibrary.path)
    }
    (this as Test).systemProperty("AWAKE_SNAPSHOT_ROOT", project.projectDir.absolutePath)
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

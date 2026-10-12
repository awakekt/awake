plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

group = "com.awakekt.awake.plugin.template"
version = "0.1.0"

kotlin {
    jvmToolchain(17)
    jvm("desktop")
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            // The contract is the only editor API a plugin may use. The host supplies it at run time,
            // so the archive below does not bundle it.
            implementation(libs.awake.editor.contract)
            implementation(libs.awake.ui.shadcn)
            implementation(libs.awake.scene.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// A .awakeplugin is a zip: plugin.json at the root and the plugin's classes under lib/.
// Desktop hosts load it at run time; other platforms compile the plugin module in instead.
val awakePlugin by tasks.registering(Zip::class) {
    group = "awake"
    description = "Packages the desktop plugin as an .awakeplugin archive."
    archiveFileName = "${project.group}-${project.version}.awakeplugin"
    destinationDirectory = layout.buildDirectory.dir("awakeplugin")
    from(layout.projectDirectory.file("plugin.json")) {
        expand("version" to project.version)
    }
    from(tasks.named("desktopJar")) { into("lib") }
}

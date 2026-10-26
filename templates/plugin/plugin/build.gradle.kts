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

// Never read from the repository: the signing key is a secret, supplied by the environment a
// publisher's own machine or CI controls, or (for local testing) a Gradle property.
val signingKey = providers.environmentVariable("AWAKE_PLUGIN_SIGNING_KEY")
    .orElse(providers.gradleProperty("awake.plugin.signingKey"))

// A .awakeplugin is a zip: plugin.json at the root and the plugin's classes under lib/.
// Desktop hosts load it at run time; other platforms compile the plugin module in instead.
val awakePlugin by tasks.registering(Zip::class) {
    group = "awake"
    description = "Packages the desktop plugin as a .awakeplugin archive, signed when a " +
        "publisher key is available."
    archiveFileName = "${project.group}-${project.version}.awakeplugin"
    destinationDirectory = layout.buildDirectory.dir("awakeplugin")
    from(layout.projectDirectory.file("plugin.json")) {
        expand("version" to project.version)
    }
    from(tasks.named("desktopJar")) { into("lib") }

    val signed = signingKey.map { it.isNotBlank() }.orElse(false)
    doLast {
        if (!signed.get()) {
            logger.lifecycle(
                "awakePlugin: no AWAKE_PLUGIN_SIGNING_KEY (or -Pawake.plugin.signingKey) is set; " +
                    "packaged ${archiveFile.get().asFile.name} unsigned.",
            )
        }
    }
}

// The signer is Core's own (com.awakekt.awake.pluginarchive.PluginArchiveSigner), the same code
// hosts verify with, run in its own JVM so its libraries never meet the build's.
val awakePluginSigner by configurations.creating {
    isCanBeConsumed = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.attribute, org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.jvm)
        attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, objects.named(TargetJvmEnvironment.STANDARD_JVM))
    }
}

dependencies {
    awakePluginSigner(libs.awake.plugin.archive)
}

val signAwakePlugin by tasks.registering(JavaExec::class) {
    group = "awake"
    description = "Signs the .awakeplugin archive in place with the publisher's key."
    val archive = awakePlugin.flatMap { it.archiveFile }
    val key = signingKey
    onlyIf("a signing key is set") { key.map { it.isNotBlank() }.getOrElse(false) }
    classpath = awakePluginSigner
    mainClass = "com.awakekt.awake.pluginarchive.PluginArchiveSignerMain"
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(17) }
    argumentProviders.add(CommandLineArgumentProvider { listOf(archive.get().asFile.absolutePath) })
    // Through the environment, not the command line, so the key never shows in a process list.
    doFirst { environment("AWAKE_PLUGIN_SIGNING_KEY", key.get()) }
    doLast { logger.lifecycle("awakePlugin: signed ${archive.get().asFile.name}.") }
}

awakePlugin { finalizedBy(signAwakePlugin) }

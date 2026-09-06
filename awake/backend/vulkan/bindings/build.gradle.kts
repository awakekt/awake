/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */

// Raw generated Vulkan API surface, split out of :awake:backend:vulkan (see
// docs/tasks/2026-08-09-application-seam-and-module-naming-plan.md, Part 3): gen/ (JNI-backed
// vkCreate*/vkDestroy* calls), handles/, models/, enums/, plus the small hand-authored
// vulkan/ root (Vulkan, VkArray, Version), utils/, and VulkanSurface's per-platform surface
// creation. Zero Awake engine opinions -- GraphicsDevice/SwapchainManager/RenderPipeline and
// everything else that decides HOW to use these calls stays in :awake:backend:vulkan, which
// depends on this module. Standalone-publishable for a consumer who wants raw Vulkan access
// without Awake's renderer.
//
// All the native/CMake/cinterop machinery below is bindings-layer entirely, confirmed by
// reading desktop-native/CMakeLists.txt's own add_library() call: it compiles ONLY
// awake-vulkan.cpp and the four gen/*_jni.gen.cpp JNI glue files, linked against vulkan_kotlin
// (the per-struct accessor/mutator tree matching models/). There is no engine-layer C++ --
// GraphicsDevice/RenderPipeline/etc. are pure Kotlin calling into this module's `Vulkan.*`
// functions, which this native library backs.

plugins {
    id("awake.kmp-library-convention")
    id("awake.publish-convention")
    id("awake.dokka-convention")
    id("awake.detekt-convention")
    id("awake.spotless-convention")
    id("awake.native-build-convention")
}

// Mirrored Khronos API: the Vulkan spec is the documentation for Vk* names, and demanding
// KDoc on ~800 generated enum entries would only produce paraphrases. The strict
// undocumented-API guardrail stays for authored modules; this one opts out.
dokka {
    dokkaSourceSets.configureEach {
        reportUndocumented.set(false)
    }
}

kotlin {
    android {
        namespace = "com.awakekt.awake.vulkan"
    }

    // See :awake:backend:vulkan's build.gradle.kts history for the full MoltenVK rationale
    // (static vs dynamic linking, why iosX64 is dropped). Unchanged by the split -- MoltenVK
    // backs the raw Vulkan-over-Metal calls this module wraps, not anything engine-specific.
    val moltenVkIncludeDir = file("ios-native/MoltenVK/Package/Release/MoltenVK/include")
    val moltenVkStaticDir = mapOf(
        "iosArm64" to file(
            "ios-native/MoltenVK/Package/Release/MoltenVK/static/MoltenVK.xcframework/ios-arm64"
        ),
        "iosSimulatorArm64" to file(
            "ios-native/MoltenVK/Package/Release/MoltenVK/static/MoltenVK.xcframework/" +
                    "ios-arm64_x86_64-simulator"
        ),
    )
    listOf(
        "iosArm64",
        "iosSimulatorArm64"
    ).forEach { targetName ->
        val staticDir = moltenVkStaticDir.getValue(targetName)
        val target =
            kotlin.targets.getByName(targetName) as org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

        registerGeneratedDefCinterop(
            target = target,
            interopName = "MoltenVK",
            baseDefFile = project.file("src/nativeInterop/cinterop/MoltenVK.def"),
            headerDirs = listOf(moltenVkIncludeDir),
            linkerOpts = listOf(
                "-L${staticDir.path}", "-lMoltenVK", "-lc++",
                "-framework", "Metal",
                "-framework", "QuartzCore",
                "-framework", "IOSurface",
                "-framework", "CoreGraphics",
                "-framework", "Foundation",
                "-framework", "UIKit",
            ),
        )
    }

    jvm("desktop")

    sourceSets {
        named("desktopMain") {
            resources.srcDir(layout.buildDirectory.dir("generated/natives-resources"))
            // Natives built on other machines. A host can only compile its own, so a jar that
            // covers more than one platform has to be assembled from several builds -- CI fans out
            // per runner and points this at the collected `natives/<platform>/` tree.
            (findProperty("awake.prebuiltNatives") as String?)
                ?.takeIf { it.isNotBlank() }
                ?.let { resources.srcDir(it) }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            // CMake/NDK build + bundled validation layers (AGP 9 KMP plugin has no
            // externalNativeBuild support, so a plain library owns it).
            api(project(":awake:backend:vulkan:bindings:android-native"))
        }
    }
}

// Manual/on-demand, like android-native's generateJniBindings -- NOT wired as an automatic
// dependency of compileKotlinDesktop or desktopTest, since CMake configure+build is slow and
// this native lib only changes when the C++ sources themselves change. Run explicitly:
//   ./gradlew :awake:backend:vulkan:bindings:configureDesktopNative :awake:backend:vulkan:bindings:buildDesktopNative
val desktopNativeBuildDir = layout.buildDirectory.dir("desktop-native")
val desktopNativeLibDir = layout.buildDirectory.dir("desktop-native-libs")

val buildDesktopNative = registerCMakeLibrary(
    name = "DesktopNative",
    sourceDir = layout.projectDirectory.dir("desktop-native").asFile,
    buildDir = desktopNativeBuildDir.get().asFile,
    description = "Build the desktop native library (.dylib/.so/.dll) and copy it where the " +
        "desktop JVM's System.loadLibrary(\"awake-vulkan\") can find it (-Djava.library.path).",
    // desktop-native/CMakeLists.txt builds ../src/main/cpp/*.cpp -- outside its own sourceDir --
    // so that tree has to be named explicitly or a real C++ change would never invalidate the
    // cache Gradle now keeps for this task.
    extraInputDirs = listOf(layout.projectDirectory.dir("src/main/cpp").asFile),
)

// The copy is this module's own: nothing else needs its output next to a java.library.path.
buildDesktopNative.configure {
    doLast {
        val libDir = desktopNativeLibDir.get().asFile.also { it.mkdirs() }
        // An exact filename, not a pattern. The pattern this replaced was written
        // `lib?awake-vulkan\.(dylib|so|dll)` -- "li" plus an optional "b" -- so it missed
        // awake-vulkan.dll, the real Windows name, and matched liawake-vulkan.so.
        val expected = HostOs.libraryFileName("awake-vulkan")
        val built = desktopNativeBuildDir.get().asFile.walkTopDown()
            .firstOrNull { it.isFile && it.name == expected }
            ?: throw GradleException("$expected not found under $desktopNativeBuildDir")
        built.copyTo(File(libDir, built.name), overwrite = true)
        println("Desktop native library copied to: ${File(libDir, built.name)}")

        val platformTag = when {
            HostOs.isMac && (System.getProperty("os.arch").lowercase().contains("arm64") || System.getProperty("os.arch").lowercase().contains("aarch64")) -> "macos-arm64"
            HostOs.isMac -> "macos-x86_64"
            HostOs.isLinux && (System.getProperty("os.arch").lowercase().contains("arm64") || System.getProperty("os.arch").lowercase().contains("aarch64")) -> "linux-arm64"
            HostOs.isLinux -> "linux-x86_64"
            HostOs.isWindows -> "windows-x86_64"
            else -> HostOs.slug
        }
        val nativesResDir = layout.buildDirectory.dir("generated/natives-resources/natives/$platformTag").get().asFile.also { it.mkdirs() }
        built.copyTo(File(nativesResDir, built.name), overwrite = true)
        println("Desktop native library packaged for resources: ${File(nativesResDir, built.name)}")
    }
}

val desktopVulkanEnv = VulkanDesktopEnv.environment()

tasks.named<Test>("desktopTest") {
    requireExclusiveGpu(this)
    jvmArgs("-Djava.library.path=${desktopNativeLibDir.get().asFile.absolutePath}")
    environment(desktopVulkanEnv)
}

val startOnFirstThread = if (HostOs.isMac) {
    listOf("-XstartOnFirstThread")
} else {
    emptyList()
}

// The platforms a release must carry a native library for. Windows is deliberately absent: no CI
// job has ever compiled the desktop C++ on it, so promising it would be a guess. macOS arm64 and
// Linux x86_64 are both built by existing jobs, and macOS x86_64 by the runner added alongside this.
val requiredNativePlatforms = listOf("macos-arm64", "macos-x86_64", "linux-x86_64")

// A one-platform jar is indistinguishable from a correct one until a consumer on another OS tries
// to load it, and then it fails at run time with "not found for platform". The published jar
// carried only the host's library for its whole life because nothing ever looked.
tasks.register("verifyDesktopNatives") {
    group = "verification"
    description = "Fail if the desktop jar's resources are missing a native library for a supported platform."
    doLast {
        val roots = buildList {
            add(layout.buildDirectory.dir("generated/natives-resources").get().asFile)
            (findProperty("awake.prebuiltNatives") as String?)
                ?.takeIf { it.isNotBlank() }
                ?.let { add(file(it)) }
        }
        val missing = requiredNativePlatforms.filter { platform ->
            roots.none { root ->
                File(root, "natives/$platform").listFiles()?.any { it.isFile && it.length() > 0 } == true
            }
        }
        check(missing.isEmpty()) {
            "The desktop jar would ship without a native library for: ${missing.joinToString(", ")}.\n" +
                "Looked under: ${roots.joinToString(", ") { it.absolutePath }}\n" +
                "A host builds only its own library, so a release assembles them from per-OS CI runs " +
                "and passes -Pawake.prebuiltNatives=<collected dir>."
        }
        println("Desktop natives present for: ${requiredNativePlatforms.joinToString(", ")}")
    }
}

// Manual diagnostic task (same convention as checkJniBindings) -- proves GLFW window + Vulkan
// surface creation actually works, which desktopTest itself cannot safely check (see
// GlfwManualVerify.kt's doc comment for the macOS main-thread reason). Run via
// `./gradlew :awake:backend:vulkan:bindings:verifyGlfwMain` after buildDesktopNative.
tasks.register<JavaExec>("verifyGlfwMain") {
    group = "native"
    description = "Manually verify GLFW window + Vulkan surface creation on the real OS main " +
            "thread (see GlfwManualVerify.kt) -- run after buildDesktopNative."
    dependsOn("compileTestKotlinDesktop")
    mainClass.set("GlfwManualVerifyKt")
    classpath = files(
        layout.buildDirectory.dir("classes/kotlin/desktop/test"),
        kotlin.jvm("desktop").compilations.getByName("test").runtimeDependencyFiles
    )
    jvmArgs(startOnFirstThread + "-Djava.library.path=${desktopNativeLibDir.get().asFile.absolutePath}")
    environment(desktopVulkanEnv)
}

mavenPublishing {
    // Flat, searchable coordinates for the standalone library ("vulkan kmp" is the query its
    // audience types) -- distinct from the path-derived group other modules keep for
    // capability-collision safety; "vulkan-kmp" is unique so the projectsEvaluated duplicate
    // check stays satisfied.
    coordinates("com.awakekt.awake", "vulkan-kmp", version.toString())
    pom {
        name.set("Vulkan KMP Bindings")
        description.set(
            "Raw Vulkan API bindings for Kotlin Multiplatform -- desktop JVM, Android, and iOS via MoltenVK"
        )
    }
}

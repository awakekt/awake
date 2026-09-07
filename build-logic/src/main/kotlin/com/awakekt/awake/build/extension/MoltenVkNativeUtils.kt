/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.extension
import org.gradle.api.Project
import java.io.File

/**
 * Resolves the static MoltenVK framework directory for Apple target [targetName] (`iosArm64` or `iosSimulatorArm64`).
 */
fun Project.moltenVkStaticDir(targetName: String): File? {
    val bindingsProj = findProject(":awake:backend:vulkan:bindings") ?: return null
    val subPath = when (targetName) {
        "iosArm64" -> "ios-native/MoltenVK/Package/Release/MoltenVK/static/MoltenVK.xcframework/ios-arm64"
        "iosSimulatorArm64" -> "ios-native/MoltenVK/Package/Release/MoltenVK/static/MoltenVK.xcframework/ios-arm64_x86_64-simulator"
        else -> error("Unsupported target for MoltenVK static framework: '$targetName'. Supported targets: iosArm64, iosSimulatorArm64.")
    }
    return bindingsProj.file(subPath)
}

/**
 * Returns native linker options for linking static MoltenVK for [targetName].
 */
fun Project.moltenVkLinkerOpts(targetName: String): List<String> {
    val dir = moltenVkStaticDir(targetName) ?: return emptyList()
    return listOf(
        "-L${dir.path}",
        "-lMoltenVK",
        "-lc++",
        "-framework", "Metal",
        "-framework", "QuartzCore",
        "-framework", "IOSurface",
        "-framework", "CoreGraphics",
        "-framework", "Foundation",
        "-framework", "UIKit",
    )
}

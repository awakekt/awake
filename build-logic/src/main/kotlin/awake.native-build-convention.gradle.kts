/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
/**
 * Marks a module as building a native library through CMake, and brings [registerCMakeLibrary]
 * into its build script's scope.
 *
 * Applied by `backend:vulkan:bindings` and `backend:jolt`. See `NativeCMake.kt` for what is
 * shared and what deliberately is not.
 *
 * Also fails early when CMake is absent. Without this the first symptom is a raw non-zero exit
 * from an `Exec` task, which says nothing about what is missing or how to install it.
 */
tasks.register("verifyNativeToolchain") {
    group = "verification"
    description = "Check that CMake is available before any native task tries to use it."
    doLast {
        // findOnPath also tries cmake.exe -- a bare "cmake" check reports "not installed" on
        // Windows, where cmake.exe is present.
        require(NativeCMake.findOnPath("cmake") != null) {
            "cmake is not on PATH, so this module's native library cannot be built. " +
                "Install it (`brew install cmake`) and re-run. Every native task here shells " +
                "out to cmake, and without this check the failure is a bare non-zero exit code."
        }
    }
}


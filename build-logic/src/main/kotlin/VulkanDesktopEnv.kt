/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import java.io.File

/**
 * The environment a desktop-JVM process needs to reach Vulkan on this machine, in one place.
 *
 * On macOS there is no system Vulkan: the loader finds MoltenVK through an ICD manifest, and
 * the loader dylib itself comes from Homebrew. Three build files (`bindings`,
 * `backend:vulkan`, `samples:studio`) each carried their own copy of the same two Homebrew
 * globs and the same fallback path list; they agreed, but nothing made them agree — the exact
 * drift shape `HostOs` was created for. Non-mac hosts get an empty map: their loader finds
 * the driver without help.
 */
object VulkanDesktopEnv {

    private val homebrewCellars = listOf("/opt/homebrew/Cellar", "/usr/local/Cellar")

    private const val DYLD_FALLBACK =
        "/opt/homebrew/opt/vulkan-loader/lib:/opt/homebrew/lib:/usr/local/lib"

    /** `VK_ICD_FILENAMES` + `DYLD_FALLBACK_LIBRARY_PATH` for a test/run task on this host, or
     * empty off macOS. The ICD entry is omitted when no Homebrew MoltenVK is installed — the
     * task then fails at Vulkan init with the loader's own message, which names the fix
     * better than a missing-file error here would. */
    fun environment(): Map<String, String> {
        if (!HostOs.isMac) return emptyMap()
        val icd = homebrewCellars.asSequence()
            .map { File("$it/molten-vk") }
            .filter { it.isDirectory }
            .flatMap { cellar -> cellar.listFiles().orEmpty().asSequence() }
            .map { File(it, "etc/vulkan/icd.d/MoltenVK_icd.json") }
            .firstOrNull { it.isFile }
        return buildMap {
            if (icd != null) put("VK_ICD_FILENAMES", icd.absolutePath)
            put("DYLD_FALLBACK_LIBRARY_PATH", DYLD_FALLBACK)
        }
    }
}

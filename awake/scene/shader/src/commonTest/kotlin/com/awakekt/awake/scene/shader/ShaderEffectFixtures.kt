/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.shader

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource

/** Shader documents and a project of files to load them from, for these tests. */
internal object ShaderEffectFixtures {
    /** A sky with one colour parameter. */
    const val SKY = """
        {"name":"Sky","surface":"background",
         "parameters":[{"name":"tint","type":"color","default":[0.2,0.4,0.8,1]}],
         "fragment":{"color":{"op":"param","name":"tint"}}}
    """

    /** A plane with a float parameter and one texture. */
    const val POOL = """
        {"name":"Pool","surface":"plane","plane":{"size":[4,4],"segments":1},
         "parameters":[{"name":"depth","type":"float","default":[1]}],
         "textures":[{"name":"ripples"}],
         "fragment":{"color":{"op":"sample","texture":"ripples","uv":{"op":"input","input":"uv"}}}}
    """

    /** A file that is not a shader document: it reads a parameter it never declares. */
    const val BROKEN = """{"name":"Broken","surface":"overlay","fragment":{"color":{"op":"param","name":"missing"}}}"""

    /** Path to text, as a project's files; every read is counted by path. */
    class Files(private val files: Map<String, String>) : AssetSource {
        val reads = mutableMapOf<String, Int>()

        override suspend fun read(path: AssetPath): Result<ByteArray> {
            reads[path.value] = (reads[path.value] ?: 0) + 1
            return files[path.value]?.let { Result.success(it.encodeToByteArray()) }
                ?: Result.failure(NoSuchElementException("no file ${path.value}"))
        }
    }
}

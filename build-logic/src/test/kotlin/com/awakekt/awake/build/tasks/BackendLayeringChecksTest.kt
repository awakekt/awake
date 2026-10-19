/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.tasks

import kotlin.test.Test
import kotlin.test.assertEquals

class BackendLayeringChecksTest {
    private val forbiddenSimpleNames = setOf("DrawCall", "SceneLight", "Lens")
    private val forbiddenQualifiedNames = setOf(
        "com.awakekt.awake.render.renderer.DrawCall",
        "com.awakekt.awake.render.renderer.SceneLight",
        "com.awakekt.awake.core.math.Lens",
    )
    private val forbiddenQualifiedPrefixes = setOf(
        "com.awakekt.awake.scene.",
        "com.awakekt.awake.ecs.",
        "com.awakekt.awake.render.effects.",
    )
    private val forbiddenContentVocabulary = setOf(
        "Blur",
        "Backdrop",
        "Kawase",
        "Bloom",
        "Glass",
        "Tonemap",
        "ToneMap",
        "Grade",
        "Grading",
        "Fxaa",
    )

    @Test
    fun detectsImportsAliasesAndQualifiedReferences() {
        val violations = findRenderRuntimeReferenceViolations(
            lines = listOf(
                "import com.awakekt.awake.render.renderer.DrawCall",
                "typealias Camera = com.awakekt.awake.core.math.Lens",
                "val light: com.awakekt.awake.render.renderer.SceneLight? = null",
            ),
            forbiddenSimpleNames = forbiddenSimpleNames,
            forbiddenQualifiedNames = forbiddenQualifiedNames,
            forbiddenQualifiedPrefixes = forbiddenQualifiedPrefixes,
            forbiddenContentVocabulary = forbiddenContentVocabulary,
        )

        assertEquals(
            listOf(
                "1: imports DrawCall",
                "2: aliases Lens",
                "3: references render-runtime vocabulary directly",
            ),
            violations,
        )
    }

    @Test
    fun ignoresCommentaryAndAllowsHardwareOnlyReferences() {
        val violations = findRenderRuntimeReferenceViolations(
            lines = listOf(
                "// com.awakekt.awake.render.renderer.DrawCall is scene vocabulary.",
                "val pipeline: PipelineHandle? = null",
            ),
            forbiddenSimpleNames = forbiddenSimpleNames,
            forbiddenQualifiedNames = forbiddenQualifiedNames,
            forbiddenQualifiedPrefixes = forbiddenQualifiedPrefixes,
            forbiddenContentVocabulary = forbiddenContentVocabulary,
        )

        assertEquals(emptyList(), violations)
    }

    @Test
    fun detectsWildcardImportsAndUnlistedQualifiedSceneReferences() {
        val violations = findRenderRuntimeReferenceViolations(
            lines = listOf(
                "import com.awakekt.awake.scene.components.*",
                "val world: com.awakekt.awake.ecs.World? = null",
            ),
            forbiddenSimpleNames = forbiddenSimpleNames,
            forbiddenQualifiedNames = forbiddenQualifiedNames,
            forbiddenQualifiedPrefixes = forbiddenQualifiedPrefixes,
            forbiddenContentVocabulary = forbiddenContentVocabulary,
        )

        assertEquals(
            listOf(
                "1: imports forbidden render-runtime package",
                "2: references render-runtime vocabulary directly",
            ),
            violations,
        )
    }

    @Test
    fun detectsEffectPackageAndEffectVocabularyImports() {
        val violations = findRenderRuntimeReferenceViolations(
            lines = listOf(
                "import com.awakekt.awake.render.effects.TintImageEffect",
                "import com.awakekt.awake.render.effects.*",
                "import com.awakekt.awake.custom.KawaseStep",
                "typealias LocalBloom = com.awakekt.awake.custom.BloomConfig",
                "val fx = com.awakekt.awake.render.effects.TintImageEffect",
            ),
            forbiddenSimpleNames = forbiddenSimpleNames,
            forbiddenQualifiedNames = forbiddenQualifiedNames,
            forbiddenQualifiedPrefixes = forbiddenQualifiedPrefixes,
            forbiddenContentVocabulary = forbiddenContentVocabulary,
        )

        assertEquals(
            listOf(
                "1: imports forbidden render-runtime package",
                "2: imports forbidden render-runtime package",
                "3: imports KawaseStep (Kawase)",
                "4: aliases BloomConfig (Bloom)",
                "5: references render-runtime vocabulary directly",
            ),
            violations,
        )
    }
}

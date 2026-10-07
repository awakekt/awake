/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderdocument

import com.awakekt.awake.asset.shaderdocument.ShaderDocumentFixtures.issuesOf
import com.awakekt.awake.asset.shaderdocument.ShaderDocumentFixtures.overlay
import com.awakekt.awake.asset.shaderdocument.ShaderDocumentFixtures.plane
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every rule a document must pass, each broken alone. Each test checks where the issue is reported
 * and that its message names what is wrong, since an author reads only the message.
 */
class ShaderDocumentCheckTest {
    private fun assertRejected(json: String, path: String, vararg mentions: String, limits: ShaderDocumentLimits = ShaderDocumentLimits.Default) {
        val issues = issuesOf(json, limits)
        val issue = issues.firstOrNull { it.path == path }
        assertTrue(issue != null, "expected an issue at '$path', got $issues")
        mentions.forEach { assertTrue(it in issue.message, "'$it' should be in: ${issue.message}") }
    }

    private fun const(vararg values: Number) = """{"op":"const","value":[${values.joinToString()}]}"""

    private fun let(name: String, value: String) = """{"statement":"let","name":"$name","value":$value}"""

    @Test
    fun theFixturesCompile() {
        listOf(ShaderDocumentFixtures.SKY, ShaderDocumentFixtures.TINT, ShaderDocumentFixtures.WATER).forEach {
            assertEquals(emptyList(), issuesOf(it))
        }
    }

    // --- names

    @Test
    fun anUnknownParameterIsNamed() =
        assertRejected(overlay(color = """{"op":"param","name":"tint"}"""), "fragment.color", "'tint'", "parameters")

    @Test
    fun anUndeclaredLocalIsNamed() =
        assertRejected(overlay(color = """{"op":"local","name":"glow"}"""), "fragment.color", "'glow'")

    @Test
    fun aLocalCannotBeDeclaredTwiceWhileVisible() = assertRejected(
        overlay(statements = let("a", const(1)) + "," + let("a", const(2))),
        "fragment.statements[1].name",
        "'a'",
        "already declared",
    )

    @Test
    fun aLocalNameMustBeAnIdentifier() =
        assertRejected(overlay(statements = let("1st", const(1))), "fragment.statements[0].name", "'1st'")

    @Test
    fun aLocalIsNotVisibleOutsideItsBlock() = assertRejected(
        overlay(
            statements = """{"statement":"if","condition":{"op":"lt","a":${const(0)},"b":{"op":"input","input":"time"}},"then":[${let("inner", const(1))}]}""",
            color = """{"op":"vec4","args":[{"op":"local","name":"inner"}]}""",
        ),
        "fragment.color.args[0]",
        "'inner'",
    )

    @Test
    fun setChangesOnlyAVar() = assertRejected(
        overlay(statements = let("a", const(1)) + """,{"statement":"set","name":"a","value":${const(2)}}"""),
        "fragment.statements[1].name",
        "'a'",
        "'var'",
    )

    @Test
    fun setKeepsTheVarsShape() = assertRejected(
        overlay(statements = """{"statement":"var","name":"a","value":${const(1)}},{"statement":"set","name":"a","value":${const(1, 2)}}"""),
        "fragment.statements[1].value",
        "'a'",
        "a scalar",
    )

    @Test
    fun parameterNamesAreUniqueAndValid() {
        val parameters = ""","parameters":[{"name":"x","type":"float","default":[1]},{"name":"x","type":"float","default":[2]},{"name":"bad name","type":"float","default":[3]}]"""
        assertRejected(overlay(extra = parameters), "parameters[1].name", "'x'", "twice")
        assertRejected(overlay(extra = parameters), "parameters[2].name", "'bad name'")
    }

    @Test
    fun aParameterDefaultHoldsWhatItsTypeHolds() = assertRejected(
        overlay(extra = ""","parameters":[{"name":"tint","type":"color","default":[1,0,0]}]"""),
        "parameters[0].default",
        "'tint'",
        "4",
    )

    // --- shapes

    @Test
    fun vectorsOfDifferentSizesDoNotAdd() = assertRejected(
        overlay(color = """{"op":"vec4","args":[{"op":"add","a":{"op":"input","input":"screenUv"},"b":{"op":"input","input":"cameraPosition"}},${const(0, 1)}]}"""),
        "fragment.color.args[0]",
        "'+'",
    )

    @Test
    fun comparisonsTakeScalars() = assertRejected(
        overlay(statements = """{"statement":"discard_if","condition":{"op":"lt","a":{"op":"input","input":"screenUv"},"b":${const(0.5)}}}"""),
        "fragment.statements[0].condition",
        "'<'",
        "two scalars",
    )

    @Test
    fun logicalOperatorsTakeConditions() = assertRejected(
        overlay(statements = """{"statement":"discard_if","condition":{"op":"and","a":{"op":"input","input":"time"},"b":{"op":"input","input":"time"}}}"""),
        "fragment.statements[0].condition",
        "'&&'",
    )

    @Test
    fun anIfNeedsACondition() = assertRejected(
        overlay(statements = """{"statement":"if","condition":{"op":"input","input":"time"},"then":[]}"""),
        "fragment.statements[0].condition",
        "'if'",
        "condition",
    )

    @Test
    fun aScalarHasNoComponents() = assertRejected(
        overlay(color = """{"op":"vec4","args":[{"op":"swizzle","value":{"op":"input","input":"time"},"components":"x"},${const(0, 0, 1)}]}"""),
        "fragment.color.args[0]",
        "only a vector",
    )

    @Test
    fun aSwizzleStaysWithinTheVectorAndOneLetterSet() {
        val uv = """{"op":"input","input":"screenUv"}"""
        assertRejected(overlay(color = """{"op":"vec4","args":[{"op":"swizzle","value":$uv,"components":"xz"},${const(0, 1)}]}"""), "fragment.color.args[0]", "'xz'")
        assertRejected(overlay(color = """{"op":"vec4","args":[{"op":"swizzle","value":$uv,"components":"xg"},${const(0, 1)}]}"""), "fragment.color.args[0]", "'xg'")
    }

    @Test
    fun aFunctionChecksItsArguments() {
        val time = """{"op":"input","input":"time"}"""
        assertRejected(overlay(color = """{"op":"vec4","args":[{"op":"call","fn":"sin","args":[$time,$time]}]}"""), "fragment.color.args[0]", "sin", "1 argument")
        assertRejected(overlay(color = """{"op":"vec4","args":[{"op":"call","fn":"normalize","args":[$time]}]}"""), "fragment.color.args[0]", "normalize", "vector")
        assertRejected(
            overlay(color = """{"op":"vec4","args":[{"op":"call","fn":"dot","args":[{"op":"input","input":"screenUv"},{"op":"input","input":"cameraPosition"}]}]}"""),
            "fragment.color.args[0]",
            "dot",
        )
    }

    @Test
    fun aVectorIsBuiltFromExactlyItsComponentsOrOneScalar() = assertRejected(
        overlay(color = """{"op":"vec4","args":[${const(1, 2)},${const(3)}]}"""),
        "fragment.color",
        "vec4",
    )

    @Test
    fun theColourIsFourNumbers() = assertRejected(overlay(color = const(1, 1, 1)), "fragment.color", "four numbers")

    @Test
    fun aDisplacementIsOneNumber() = assertRejected(plane(displacement = const(1, 1)), "vertex.displacement", "one number")

    // --- stages and surfaces

    @Test
    fun aPlaneOnlyInputIsRejectedOnAFullScreenSurface() = assertRejected(
        overlay(color = """{"op":"vec4","args":[{"op":"input","input":"worldPosition"},${const(1)}]}"""),
        "fragment.color.args[0]",
        "'worldPosition'",
        "plane",
    )

    @Test
    fun aFragmentOnlyInputIsRejectedInAVertexStage() = assertRejected(
        plane(displacement = """{"op":"swizzle","value":{"op":"input","input":"viewDirection"},"components":"y"}"""),
        "vertex.displacement.value",
        "'viewDirection'",
        "fragment",
    )

    @Test
    fun aVertexStageCannotSample() = assertRejected(
        plane(displacement = """{"op":"swizzle","value":{"op":"sample","texture":"t","uv":{"op":"input","input":"uv"}},"components":"r"}""", extra = ""","textures":[{"name":"t"}]"""),
        "vertex.displacement.value",
        "fragment stage only",
    )

    @Test
    fun aSampleNamesADeclaredTextureAndReadsAtA2DCoordinate() {
        assertRejected(overlay(color = """{"op":"sample","texture":"noise","uv":{"op":"input","input":"screenUv"}}"""), "fragment.color", "'noise'")
        assertRejected(
            overlay(color = """{"op":"sample","texture":"t","uv":{"op":"input","input":"cameraPosition"}}""", extra = ""","textures":[{"name":"t"}]"""),
            "fragment.color",
            "2-component",
        )
    }

    @Test
    fun aTextureThatIsNeverSampledIsRejected() =
        assertRejected(overlay(extra = ""","textures":[{"name":"unused"}]"""), "textures[0]", "'unused'", "never sampled")

    @Test
    fun discardIfBelongsAtTheFragmentStagesTopLevel() {
        val discard = """{"statement":"discard_if","condition":{"op":"lt","a":{"op":"input","input":"time"},"b":${const(1)}}}"""
        assertRejected(
            overlay(statements = """{"statement":"if","condition":{"op":"lt","a":{"op":"input","input":"time"},"b":${const(1)}},"then":[$discard]}"""),
            "fragment.statements[0].then[0]",
            "'discard_if'",
        )
        assertRejected(plane(vertexStatements = discard), "vertex.statements[0]", "'discard_if'")
    }

    @Test
    fun aBackgroundIsOpaqueAndAnOverlayBlendsByAlpha() {
        assertRejected("""{"name":"B","surface":"background","blend":"additive","fragment":{"color":${const(0, 0, 0, 1)}}}""", "blend", "opaque")
        assertRejected(overlay(extra = ""","blend":"opaque""""), "blend", "alpha")
    }

    @Test
    fun onlyAPlaneHasAPlaneAndAVertexStage() {
        assertRejected(overlay(extra = ""","plane":{"size":[1,1]}"""), "plane", "plane surface")
        assertRejected(overlay(extra = ""","vertex":{"statements":[]}"""), "vertex", "vertex stage")
        assertRejected("""{"name":"P","surface":"plane","fragment":{"color":${const(1, 1, 1, 1)}}}""", "plane", "size")
    }

    @Test
    fun aPlanesSizeIsTwoPositiveNumbersAndItsSegmentsAreBounded() {
        assertRejected(plane(extra = "").replace(""""size":[1,1]""", """"size":[1,0]"""), "plane.size", "positive")
        assertRejected(plane().replace(""""size":[1,1]""", """"size":[1,1],"segments":129"""), "plane.segments", "129")
    }

    @Test
    fun anUnknownFormatVersionIsRejected() = assertRejected(overlay(extra = ""","formatVersion":2"""), "formatVersion", "2")

    // --- loops

    @Test
    fun aLoopsBoundsAreOrderedAndNotNegative() {
        val body = """"body":[]"""
        assertRejected(overlay(statements = """{"statement":"for","counter":"i","from":-1,"until":2,$body}"""), "fragment.statements[0].from", "0 or more")
        assertRejected(overlay(statements = """{"statement":"for","counter":"i","from":3,"until":3,$body}"""), "fragment.statements[0].until", "more than")
    }

    @Test
    fun aLoopHasAtMostTheIterationLimit() = assertRejected(
        overlay(statements = """{"statement":"for","counter":"i","from":0,"until":33,"body":[]}"""),
        "fragment.statements[0].until",
        "33",
        "32",
    )

    @Test
    fun loopsNestAtMostTwoDeep() {
        val innermost = """{"statement":"for","counter":"k","from":0,"until":2,"body":[]}"""
        val middle = """{"statement":"for","counter":"j","from":0,"until":2,"body":[$innermost]}"""
        assertRejected(overlay(statements = """{"statement":"for","counter":"i","from":0,"until":2,"body":[$middle]}"""), "fragment.statements[0].body[0].body[0]", "2 deep")
    }

    @Test
    fun aLoopCounterReadsAsAScalarInsideItsBody() {
        val body = """{"statement":"discard_if","condition":{"op":"gt","a":{"op":"local","name":"i"},"b":${const(2)}}}"""
        // A discard is not allowed in a loop, but the counter itself must type as a scalar: only the
        // placement is reported.
        val issues = issuesOf(overlay(statements = """{"statement":"for","counter":"i","from":0,"until":4,"body":[$body]}"""))
        assertEquals(listOf("fragment.statements[0].body[0]"), issues.map { it.path })
    }

    // --- costs and limits

    @Test
    fun samplesInALoopCountOncePerIteration() {
        val sample = """{"op":"sample","texture":"t","uv":{"op":"input","input":"screenUv"}}"""
        val loop = """{"statement":"for","counter":"i","from":0,"until":17,"body":[${let("s", sample)}]}"""
        val issues = issuesOf(overlay(statements = loop, extra = ""","textures":[{"name":"t"}]"""))
        assertTrue(issues.any { "17 texture samples" in it.message }, issues.toString())
    }

    @Test
    fun theNodeAndWeightedCostLimitsApply() {
        val tight = ShaderDocumentLimits(maxNodes = 5, maxWeightedCost = 5)
        val issues = issuesOf(ShaderDocumentFixtures.SKY, tight)
        assertTrue(issues.any { "expressions and statements; the limit is 5" in it.message }, issues.toString())
        assertTrue(issues.any { "weighted cost" in it.message }, issues.toString())
    }

    @Test
    fun expressionsNestAtMostTheDepthLimit() {
        val deep = (1..40).fold(const(1)) { inner, _ -> """{"op":"neg","value":$inner}""" }
        val issues = issuesOf(overlay(color = """{"op":"vec4","args":[$deep]}"""))
        assertTrue(issues.any { "nest more than 32 deep" in it.message }, issues.toString())
    }

    @Test
    fun noLimitRaisesTheTextureCountPastTheFixedSlots() {
        val five = (1..5).joinToString(",") { """{"name":"t$it"}""" }
        val issues = issuesOf(overlay(extra = ""","textures":[$five]"""), ShaderDocumentLimits(maxTextures = 8))
        assertTrue(issues.any { it.path == "textures" && "the limit is 4" in it.message }, issues.toString())
    }

    // --- values known before the shader runs

    @Test
    fun aDivisionByZeroBetweenConstantsIsRejectedWhereItIs() = assertRejected(
        overlay(color = """{"op":"vec4","args":[{"op":"div","a":${const(1)},"b":${const(0)}}]}"""),
        "fragment.color.args[0]",
        "not a finite number",
    )

    @Test
    fun aConstantCarriedThroughALetIsStillFolded() = assertRejected(
        overlay(statements = let("zero", const(0)), color = """{"op":"vec4","args":[{"op":"call","fn":"sqrt","args":[{"op":"sub","a":{"op":"local","name":"zero"},"b":${const(1)}}]}]}"""),
        "fragment.color.args[0]",
        "not a finite number",
    )

    @Test
    fun aVarIsNotFoldedBecauseItCanChange() {
        val statements = """{"statement":"var","name":"z","value":${const(0)}},{"statement":"set","name":"z","value":${const(2)}}"""
        assertEquals(emptyList(), issuesOf(overlay(statements = statements, color = """{"op":"vec4","args":[{"op":"div","a":${const(1)},"b":{"op":"local","name":"z"}}]}""")))
    }

    @Test
    fun smoothstepWithEqualEdgesIsRejectedWhenConstant() = assertRejected(
        overlay(color = """{"op":"vec4","args":[{"op":"call","fn":"smoothstep","args":[${const(1)},${const(1)},${const(0.5)}]}]}"""),
        "fragment.color.args[0]",
        "not a finite number",
    )

    @Test
    fun aCleanDocumentReportsNothingAndEveryIssueIsReportedAtOnce() {
        val two = overlay(color = """{"op":"vec4","args":[{"op":"param","name":"a"},{"op":"local","name":"b"},${const(1, 1)}]}""")
        assertEquals(2, issuesOf(two).size, "both unknown names are reported, not only the first")
    }
}

/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn

import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.text.TextFieldState
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.ui.shadcn.components.ShadcnFocusRingMode
import com.awakekt.awake.ui.shadcn.theme.provideShadcnTheme
import java.lang.reflect.Method
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.lang.reflect.Modifier as JvmModifier

/**
 * Verifies that adding `enabled` to `BasicTextField` and `onSubmit` to `ShadcnInput` and
 * `ShadcnInputGroup` preserves every pre-existing JVM binary descriptor and `$default` entry point.
 */
class ShadcnFormBinaryCompatibilityTest {
    private val theme = ShadcnThemeValues(ShadcnTheme)

    @Test
    fun basicTextFieldAndShadcnInputsPreserveLegacyAndAdditiveJvmDescriptors() {
        val basicSignatures = Class.forName(BASIC_TEXT_FIELD_CLASS).publicStaticSignatures()
        assertTrue(LEGACY_BASIC_TEXT_FIELD in basicSignatures, "Missing legacy BasicTextField: $basicSignatures")
        assertTrue(
            LEGACY_BASIC_TEXT_FIELD_DEFAULT in basicSignatures,
            "Missing legacy BasicTextField\$default: $basicSignatures",
        )
        assertTrue(ENABLED_BASIC_TEXT_FIELD in basicSignatures, "Missing enabled BasicTextField: $basicSignatures")

        val inputSignatures = Class.forName(SHADCN_INPUT_CLASS).publicStaticSignatures()
        assertTrue(LEGACY_SHADCN_INPUT in inputSignatures, "Missing legacy ShadcnInput: $inputSignatures")
        assertTrue(
            LEGACY_SHADCN_INPUT_DEFAULT in inputSignatures,
            "Missing legacy ShadcnInput\$default: $inputSignatures",
        )
        assertTrue(SUBMIT_SHADCN_INPUT in inputSignatures, "Missing onSubmit ShadcnInput: $inputSignatures")

        val groupSignatures = Class.forName(SHADCN_INPUT_GROUP_CLASS).publicStaticSignatures()
        assertTrue(LEGACY_SHADCN_INPUT_GROUP in groupSignatures, "Missing legacy ShadcnInputGroup: $groupSignatures")
        assertTrue(
            LEGACY_SHADCN_INPUT_GROUP_DEFAULT in groupSignatures,
            "Missing legacy ShadcnInputGroup\$default: $groupSignatures",
        )
        assertTrue(
            SUBMIT_SHADCN_INPUT_GROUP in groupSignatures,
            "Missing onSubmit ShadcnInputGroup: $groupSignatures",
        )
    }

    @Test
    fun legacyShadcnInputAndInputGroupBinaryEntryPointsExecuteInComposeHost() {
        val legacyInputDefault = Class.forName(SHADCN_INPUT_CLASS).getDeclaredMethod(
            "ShadcnInput\$default",
            Composer::class.java,
            TextFieldState::class.java,
            Modifier::class.java,
            Boolean::class.javaPrimitiveType,
            String::class.java,
            ShadcnFocusRingMode::class.java,
            Function0::class.java,
            Char::class.javaObjectType,
            Boolean::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Any::class.java,
        )
        val legacyGroupDefault = Class.forName(SHADCN_INPUT_GROUP_CLASS).getDeclaredMethod(
            "ShadcnInputGroup\$default",
            Composer::class.java,
            TextFieldState::class.java,
            Modifier::class.java,
            Boolean::class.javaPrimitiveType,
            String::class.java,
            Function2::class.java,
            Function2::class.java,
            ShadcnFocusRingMode::class.java,
            Function0::class.java,
            Int::class.javaPrimitiveType,
            Any::class.java,
        )
        val inputState = TextFieldState("alpha")
        val groupState = TextFieldState("bravo")
        val host = ComposeHost()
        val output = host.frame(FrameInput(viewportWidth = 480, viewportHeight = 240)) {
            provideShadcnTheme(theme) {
                Column {
                    invokeLegacyInput(legacyInputDefault, inputState)
                    invokeLegacyGroup(legacyGroupDefault, groupState)
                }
            }
        }
        assertNotNull(output.semantics.findByTag("legacy-input"))
        assertNotNull(output.semantics.findByTag("legacy-group"))
    }

    context(composer: Composer)
    private fun invokeLegacyInput(method: Method, state: TextFieldState) {
        // Context parameter `composer` occupies bit 0, `state` bit 1, `modifier` bit 2; bits 3..8 default.
        method.invoke(
            null,
            composer,
            state,
            Modifier.testTag("legacy-input"),
            false,
            null,
            null,
            null,
            null,
            false,
            0x1F8,
            null,
        )
    }

    context(composer: Composer)
    private fun invokeLegacyGroup(method: Method, state: TextFieldState) {
        // Context parameter `composer` occupies bit 0, `state` bit 1, `modifier` bit 2; bits 3..8 default.
        method.invoke(
            null,
            composer,
            state,
            Modifier.testTag("legacy-group"),
            false,
            null,
            null,
            null,
            null,
            null,
            0x1F8,
            null,
        )
    }

    private fun List<SemanticsNode>.findByTag(tag: String): SemanticsNode? =
        firstNotNullOfOrNull { it.findNodeByTag(tag) }

    private fun SemanticsNode.findNodeByTag(tag: String): SemanticsNode? =
        if (testTag == tag) {
            this
        } else {
            children.firstNotNullOfOrNull { it.findNodeByTag(tag) }
        }

    private fun Class<*>.publicStaticSignatures(): Set<String> =
        declaredMethods
            .filter { JvmModifier.isPublic(it.modifiers) && JvmModifier.isStatic(it.modifiers) }
            .mapTo(sortedSetOf()) { method ->
                buildString {
                    append(method.name)
                    append('(')
                    method.parameterTypes.forEach { append(it.jvmDescriptor()) }
                    append(')')
                    append(method.returnType.jvmDescriptor())
                }
            }

    private fun Class<*>.jvmDescriptor(): String =
        PRIMITIVE_DESCRIPTORS[name] ?: if (isArray) name.replace('.', '/') else "L${name.replace('.', '/')};"

    private companion object {
        const val BASIC_TEXT_FIELD_CLASS = "com.awakekt.awake.compose.foundation.text.BasicTextFieldKt"
        const val SHADCN_INPUT_CLASS = "com.awakekt.awake.ui.shadcn.components.ShadcnInputKt"
        const val SHADCN_INPUT_GROUP_CLASS = "com.awakekt.awake.ui.shadcn.components.ShadcnInputGroupKt"

        const val LEGACY_BASIC_TEXT_FIELD =
            "BasicTextField(Lcom/awakekt/awake/compose/runtime/Composer;" +
                "Lcom/awakekt/awake/compose/foundation/text/TextFieldState;" +
                "Lcom/awakekt/awake/compose/ui/Modifier;" +
                "Lcom/awakekt/awake/core/text/theme/TextStyle;" +
                "Lcom/awakekt/awake/compose/foundation/interaction/InteractionSource;" +
                "Ljava/lang/String;Lcom/awakekt/awake/core/color/Color;ZLkotlin/jvm/functions/Function0;" +
                "Ljava/lang/Character;ZLkotlin/jvm/functions/Function0;Ljava/util/Set;)V"
        const val LEGACY_BASIC_TEXT_FIELD_DEFAULT =
            "BasicTextField\$default(Lcom/awakekt/awake/compose/runtime/Composer;" +
                "Lcom/awakekt/awake/compose/foundation/text/TextFieldState;" +
                "Lcom/awakekt/awake/compose/ui/Modifier;" +
                "Lcom/awakekt/awake/core/text/theme/TextStyle;" +
                "Lcom/awakekt/awake/compose/foundation/interaction/InteractionSource;" +
                "Ljava/lang/String;Lcom/awakekt/awake/core/color/Color;ZLkotlin/jvm/functions/Function0;" +
                "Ljava/lang/Character;ZLkotlin/jvm/functions/Function0;Ljava/util/Set;ILjava/lang/Object;)V"
        const val ENABLED_BASIC_TEXT_FIELD =
            "BasicTextField(Lcom/awakekt/awake/compose/runtime/Composer;" +
                "Lcom/awakekt/awake/compose/foundation/text/TextFieldState;" +
                "Lcom/awakekt/awake/compose/ui/Modifier;" +
                "Lcom/awakekt/awake/core/text/theme/TextStyle;" +
                "Lcom/awakekt/awake/compose/foundation/interaction/InteractionSource;" +
                "Ljava/lang/String;Lcom/awakekt/awake/core/color/Color;ZLkotlin/jvm/functions/Function0;" +
                "Ljava/lang/Character;ZLkotlin/jvm/functions/Function0;Ljava/util/Set;Z)V"

        const val LEGACY_SHADCN_INPUT =
            "ShadcnInput(Lcom/awakekt/awake/compose/runtime/Composer;" +
                "Lcom/awakekt/awake/compose/foundation/text/TextFieldState;" +
                "Lcom/awakekt/awake/compose/ui/Modifier;ZLjava/lang/String;" +
                "Lcom/awakekt/awake/ui/shadcn/components/ShadcnFocusRingMode;" +
                "Lkotlin/jvm/functions/Function0;Ljava/lang/Character;Z)V"
        const val LEGACY_SHADCN_INPUT_DEFAULT =
            "ShadcnInput\$default(Lcom/awakekt/awake/compose/runtime/Composer;" +
                "Lcom/awakekt/awake/compose/foundation/text/TextFieldState;" +
                "Lcom/awakekt/awake/compose/ui/Modifier;ZLjava/lang/String;" +
                "Lcom/awakekt/awake/ui/shadcn/components/ShadcnFocusRingMode;" +
                "Lkotlin/jvm/functions/Function0;Ljava/lang/Character;ZILjava/lang/Object;)V"
        const val SUBMIT_SHADCN_INPUT =
            "ShadcnInput(Lcom/awakekt/awake/compose/runtime/Composer;" +
                "Lcom/awakekt/awake/compose/foundation/text/TextFieldState;" +
                "Lcom/awakekt/awake/compose/ui/Modifier;ZLjava/lang/String;" +
                "Lcom/awakekt/awake/ui/shadcn/components/ShadcnFocusRingMode;" +
                "Lkotlin/jvm/functions/Function0;Ljava/lang/Character;ZLkotlin/jvm/functions/Function0;)V"

        const val LEGACY_SHADCN_INPUT_GROUP =
            "ShadcnInputGroup(Lcom/awakekt/awake/compose/runtime/Composer;" +
                "Lcom/awakekt/awake/compose/foundation/text/TextFieldState;" +
                "Lcom/awakekt/awake/compose/ui/Modifier;ZLjava/lang/String;" +
                "Lkotlin/jvm/functions/Function2;Lkotlin/jvm/functions/Function2;" +
                "Lcom/awakekt/awake/ui/shadcn/components/ShadcnFocusRingMode;" +
                "Lkotlin/jvm/functions/Function0;)V"
        const val LEGACY_SHADCN_INPUT_GROUP_DEFAULT =
            "ShadcnInputGroup\$default(Lcom/awakekt/awake/compose/runtime/Composer;" +
                "Lcom/awakekt/awake/compose/foundation/text/TextFieldState;" +
                "Lcom/awakekt/awake/compose/ui/Modifier;ZLjava/lang/String;" +
                "Lkotlin/jvm/functions/Function2;Lkotlin/jvm/functions/Function2;" +
                "Lcom/awakekt/awake/ui/shadcn/components/ShadcnFocusRingMode;" +
                "Lkotlin/jvm/functions/Function0;ILjava/lang/Object;)V"
        const val SUBMIT_SHADCN_INPUT_GROUP =
            "ShadcnInputGroup(Lcom/awakekt/awake/compose/runtime/Composer;" +
                "Lcom/awakekt/awake/compose/foundation/text/TextFieldState;" +
                "Lcom/awakekt/awake/compose/ui/Modifier;ZLjava/lang/String;" +
                "Lkotlin/jvm/functions/Function2;Lkotlin/jvm/functions/Function2;" +
                "Lcom/awakekt/awake/ui/shadcn/components/ShadcnFocusRingMode;" +
                "Lkotlin/jvm/functions/Function0;Lkotlin/jvm/functions/Function0;)V"

        val PRIMITIVE_DESCRIPTORS = mapOf(
            "void" to "V",
            "boolean" to "Z",
            "byte" to "B",
            "char" to "C",
            "short" to "S",
            "int" to "I",
            "long" to "J",
            "float" to "F",
            "double" to "D",
        )
    }
}

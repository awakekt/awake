/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.scene.authoring.SceneAppDsl
import com.awakekt.awake.scene.authoring.SceneSystemsDsl
import com.awakekt.awake.scene.runtime.SceneSystemPhase
import com.awakekt.awake.scene.runtime.schedule.SceneSchedule
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies that adding [SceneSystemPhase.PreAnimation], [SceneSystemPlan.preAnimation],
 * [SceneSystemSet.preAnimation], and [SceneAppDsl.preAnimationSystem] keeps every pre-existing
 * public JVM method descriptor and enum ordinal intact for binary consumers.
 */
class SceneSystemPlanBinaryCompatibilityTest {
    @Test
    fun sceneSystemPlanKeepsExistingDescriptorsAndAddsPreAnimation() {
        assertEquals(
            setOf(
                "fixed(Ljava/lang/String;Lkotlin/jvm/functions/Function1;)V",
                "frame(Ljava/lang/String;Lkotlin/jvm/functions/Function1;)V",
                "getHasPhysics()Z",
                "getHasRenderer()Z",
                "physicsSystem(Lcom/awakekt/awake/project/runtime/SceneHostServices;)Lcom/awakekt/awake/scene/physics/PhysicsSystem;",
                "preAnimation(Ljava/lang/String;Lkotlin/jvm/functions/Function1;)V",
            ),
            SceneSystemPlan::class.java.publicMethodSignatures(),
        )
    }

    @Test
    fun sceneSystemSetKeepsExistingDescriptorsAndAddsPreAnimationGetter() {
        assertEquals(
            setOf(
                "close()V",
                "getFixed()Ljava/util/List;",
                "getFrame()Ljava/util/List;",
                "getPreAnimation()Ljava/util/List;",
                "interpolate(Lcom/awakekt/awake/ecs/World;F)V",
            ),
            SceneSystemSet::class.java.publicMethodSignatures(),
        )
        val topLevel = Class.forName("com.awakekt.awake.project.runtime.SceneSystemSetKt").declaredMethodSignatures()
        assertTrue(
            "sceneSystemsFor(Lcom/awakekt/awake/scene/document/SceneDocument;Lcom/awakekt/awake/project/runtime/SceneHostServices;Ljava/util/List;)Lcom/awakekt/awake/project/runtime/SceneSystemSet;" in topLevel,
            "sceneSystemsFor public binary entry point must remain unchanged: $topLevel",
        )
    }

    @Test
    fun sceneSystemPhaseKeepsFixedAndFrameOrdinalsBeforePreAnimation() {
        assertEquals(
            listOf(
                "Fixed" to 0,
                "Frame" to 1,
                "PreAnimation" to 2,
            ),
            SceneSystemPhase.entries.map { it.name to it.ordinal },
        )
    }

    @Test
    fun sceneAuthoringDslAndScheduleKeepBinarySurfaceIntact() {
        val expectedSystemBuilders = setOf(
            "fixedSystem(Ljava/lang/String;Lkotlin/jvm/functions/Function1;)Lcom/awakekt/awake/scene/runtime/SceneSystemHandle;",
            "frameSystem(Ljava/lang/String;Lkotlin/jvm/functions/Function1;)Lcom/awakekt/awake/scene/runtime/SceneSystemHandle;",
            "preAnimationSystem(Ljava/lang/String;Lkotlin/jvm/functions/Function1;)Lcom/awakekt/awake/scene/runtime/SceneSystemHandle;",
            "system(Ljava/lang/String;Lcom/awakekt/awake/scene/runtime/SceneSystemPhase;Lkotlin/jvm/functions/Function1;)Lcom/awakekt/awake/scene/runtime/SceneSystemHandle;",
        )
        assertTrue(SceneAppDsl::class.java.publicMethodSignatures().containsAll(expectedSystemBuilders))
        assertEquals(expectedSystemBuilders, SceneSystemsDsl::class.java.publicMethodSignatures())
        assertEquals(emptySet(), SceneSchedule::class.java.publicMethodSignatures())
    }

    private fun Class<*>.publicMethodSignatures(): Set<String> =
        declaredMethods
            .filter { it.isPublicApiMethod() }
            .mapTo(sortedSetOf()) { it.jvmSignature() }

    private fun Class<*>.declaredMethodSignatures(): Set<String> =
        declaredMethods
            .filter { Modifier.isPublic(it.modifiers) }
            .mapTo(sortedSetOf()) { it.jvmSignature() }

    private fun Method.isPublicApiMethod(): Boolean =
        Modifier.isPublic(modifiers) && !isSynthetic && '$' !in name

    private fun Method.jvmSignature(): String = buildString {
        append(name)
        append('(')
        parameterTypes.forEach { append(it.jvmDescriptor()) }
        append(')')
        append(returnType.jvmDescriptor())
    }

    private fun Class<*>.jvmDescriptor(): String =
        PRIMITIVE_DESCRIPTORS[name] ?: if (isArray) name.replace('.', '/') else "L${name.replace('.', '/')};"

    private companion object {
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

/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A resolver compiled against an older Core, such as a precompiled plugin, has none of the methods added
 * to [SceneAssetResolver] since. Each is a JVM default method, so the library's calls to them reach the
 * interface's body instead of throwing `AbstractMethodError`, and `DefaultImpls` keeps them too, for a
 * resolver compiled by an older Kotlin that calls through it.
 */
class SceneAssetResolverBinaryCompatibilityTest {
    @Test
    fun everyResolverMethodHasAJvmDefaultBody() {
        val methods = SceneAssetResolver::class.java.declaredMethods.filterNot { it.isSynthetic }

        assertTrue(methods.isNotEmpty())
        assertEquals(emptyList(), methods.filterNot { it.isDefault }.map { it.name }, "compiled without a JVM default body")
        assertEquals(emptyList(), methods.filter { Modifier.isAbstract(it.modifiers) }.map { it.name }, "abstract in the class file")
    }

    @Test
    fun defaultImplsStillHoldsEveryResolverMethod() {
        val defaults = Class.forName("${SceneAssetResolver::class.java.name}\$DefaultImpls").declaredMethods.map { it.name }.toSet()

        val missing = SceneAssetResolver::class.java.declaredMethods.filterNot { it.isSynthetic }.map { it.name }.filterNot { it in defaults }
        assertEquals(emptyList(), missing)
    }

    @Test
    fun aResolverWhoseClassHasNoTextureMethodsIsAskedAndForgottenWithoutThrowing() {
        val older = OlderResolver()
        assertEquals(
            emptyList(),
            older.javaClass.declaredMethods.map { it.name }.filter { it in TEXTURE_METHODS },
            "like a class compiled before these methods existed",
        )
        val library = SceneAssetLibrary(emptyMap(), emptyMap(), emptyMap(), dynamicResolvers = listOf(older))

        assertNull(library.texture("art/hero.png"))
        library.forgetTexture("art/hero.png")
        library.forgetTextures()
        library.requestForgetTexture("art/hero.png")
        library.applyRequestedForgets()
        library.requestForgetTextures()
        library.applyRequestedForgets()
        assertNull(library.texture("art/hero.png"))
    }

    /**
     * A mesh resolver of the kind written before resolvers supplied textures. Without the bridges Kotlin's
     * compatibility mode adds, its class file has only what it overrides, as an older one's does.
     */
    @JvmDefaultWithoutCompatibility
    private class OlderResolver : SceneAssetResolver {
        override fun canResolveMesh(name: String): Boolean = false
    }

    private companion object {
        val TEXTURE_METHODS = setOf("texture", "forgetTexture", "forgetTextures")
    }
}

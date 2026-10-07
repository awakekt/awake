/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderdocument

/** The shape of a value in a document: one to four numbers, or a condition. */
internal enum class ValueShape(val components: Int) {
    F32(1),
    Vec2(2),
    Vec3(3),
    Vec4(4),
    Bool(0),
    ;

    val isNumeric: Boolean get() = this != Bool

    val isVector: Boolean get() = components >= 2

    override fun toString(): String = when (this) {
        F32 -> "a scalar"
        Bool -> "a condition"
        else -> "a $components-component vector"
    }

    companion object {
        fun ofComponents(count: Int): ValueShape = when (count) {
            1 -> F32
            2 -> Vec2
            3 -> Vec3
            else -> Vec4
        }
    }
}

/** The shape a [ShaderValueType] holds. */
internal val ShaderValueType.shape: ValueShape
    get() = when (this) {
        ShaderValueType.Float -> ValueShape.F32
        ShaderValueType.Vec2 -> ValueShape.Vec2
        ShaderValueType.Vec3 -> ValueShape.Vec3
        ShaderValueType.Vec4, ShaderValueType.Color -> ValueShape.Vec4
    }

/** The shape a [ShaderInput] reads as. */
internal val ShaderInput.shape: ValueShape
    get() = when (this) {
        ShaderInput.Time, ShaderInput.DeltaTime -> ValueShape.F32
        ShaderInput.Uv, ShaderInput.ScreenUv, ShaderInput.Resolution -> ValueShape.Vec2
        else -> ValueShape.Vec3
    }

/** The stage an expression runs in. */
internal enum class Stage { Vertex, Fragment }

/** Names visible at one point of a stage, innermost block first. */
internal class Scope<T>(private val parent: Scope<T>? = null) {
    private val names = HashMap<String, T>()

    fun find(name: String): T? = names[name] ?: parent?.find(name)

    fun declare(name: String, value: T) {
        names[name] = value
    }

    fun child(): Scope<T> = Scope(this)
}

/** What a checked local is. */
internal enum class LocalKind { Let, Var, Counter }

/** A local as the checker sees it: its shape, and its value when that is known before the shader runs. */
internal class CheckedLocal(val shape: ValueShape, val kind: LocalKind, val constant: FloatArray?)

/** An expression's shape, and its value when that is known before the shader runs. */
internal class Typed(val shape: ValueShape, val constant: FloatArray? = null)

/** Which parameters and inputs one stage reads. Readers iterate in declaration order, not set order. */
internal class StageUsage {
    val parameters: MutableSet<Int> = mutableSetOf()
    val inputs: MutableSet<ShaderInput> = mutableSetOf()
}

/** Everything one check of a document records as it goes. */
internal class CheckContext(val document: ShaderDocument, val limits: ShaderDocumentLimits) {
    val issues: MutableList<ShaderDocumentIssue> = mutableListOf()
    val vertexUsage = StageUsage()
    val fragmentUsage = StageUsage()
    val sampledTextures: MutableSet<String> = mutableSetOf()
    var nodes: Int = 0
    var weightedCost: Long = 0
    var weightedSamples: Long = 0
    var locals: Int = 0

    /** The parameter index by name. */
    val parameterIndex: Map<String, Int> = document.parameters.withIndex().associate { (index, parameter) -> parameter.name to index }

    fun issue(path: String, message: String) {
        issues += ShaderDocumentIssue(path, message)
    }

    fun usage(stage: Stage): StageUsage = if (stage == Stage.Vertex) vertexUsage else fragmentUsage
}

/** A letter, then letters, digits or underscores, 32 characters at most. */
internal val DOCUMENT_NAME: Regex = Regex("[A-Za-z][A-Za-z0-9_]{0,31}")

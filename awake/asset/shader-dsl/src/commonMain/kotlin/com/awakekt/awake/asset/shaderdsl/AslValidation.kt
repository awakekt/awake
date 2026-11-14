/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderdsl

/** Dead-declaration lint: a const, function, texture, or storage binding nothing references,
 * or a varying the vertex stage writes and the fragment stage never reads, is authoring
 * waste that WGSL compiles silently. Uniform FIELDS are deliberately exempt -- declaring
 * fields a shader never reads is how a prefix struct binds another shader's buffer. */
internal fun validateNoDeadDeclarations(definition: AslShaderDefinition) {
    val used = mutableSetOf<String>()
    definition.vertexStage.statements.collectNames(used)
    definition.vertexStage.positionOnly?.collectNames(used)
    definition.fragmentStage.statements.collectNames(used)
    definition.fragmentStage.returnValue?.collectNames(used)
    definition.functions.forEach { it.body.collectNames(used) }
    definition.consts.forEach { it.value.collectNames(used) }
    val dead = buildList {
        definition.consts.filter { it.name !in used }.forEach { add("const '${it.name}'") }
        definition.functions.filter { it.name !in used }.forEach { add("fn '${it.name}'") }
        definition.textures.filter { it.name !in used }.forEach { add("texture '${it.name}'") }
        definition.storageBindings.filter { it.varName !in used }
            .forEach { add("storage '${it.varName}'") }
        val fragmentReads = mutableSetOf<String>()
        definition.fragmentStage.statements.collectVaryings(fragmentReads)
        definition.fragmentStage.returnValue?.collectVaryings(fragmentReads)
        definition.functions.forEach { it.body.collectVaryings(fragmentReads) }
        definition.varyings?.fields.orEmpty()
            .filter { it.name !in fragmentReads }
            .forEach { add("varying '${it.name}' (written, never read)") }
    }
    if (dead.isNotEmpty()) {
        throw AslDefinitionException("Shader '${definition.name}' has dead declarations: $dead.")
    }
}

/** Cross-stage structural checks -- the mistakes a Kotlin definition can make that emit
 * syntactically fine WGSL naga would reject late or, worse, accept with a silent gap. */
internal fun validateStages(definition: AslShaderDefinition) {
    val written = definition.vertexStage.statements
        .filterIsInstance<AslAssign>()
        .map { it.target.name }
        .toSet()
    validatePosition(definition, written)
    val read = mutableSetOf<String>()
    definition.fragmentStage.statements.collectVaryings(read)
    definition.fragmentStage.returnValue?.collectVaryings(read)
    definition.functions.forEach { it.body.collectVaryings(read) }
    val unwritten = read - written
    if (unwritten.isNotEmpty()) {
        throw AslDefinitionException(
            "Fragment stage of '${definition.name}' reads varyings never written: $unwritten.",
        )
    }
    validateNoDeadDeclarations(definition)
    validateUniformControlFlow(definition)
}

private val UNIFORM_FLOW_BUILTINS = setOf("dpdx", "dpdy", "fwidth", "textureSample")

/** WGSL's uniformity rule: implicit-derivative builtins (`dpdx`, `dpdy`, `fwidth`, `textureSample`)
 * must only be called from uniform control flow -- never inside an `if` or loop body, where naga
 * accepts them for SPIR-V while WebGPU's tint rejects the module at `createShaderModule`. */
private fun validateUniformControlFlow(definition: AslShaderDefinition) {
    val requiresUniformFlow = UNIFORM_FLOW_BUILTINS.toMutableSet()
    var changed = true
    while (changed) {
        changed = false
        for (fn in definition.functions) {
            if (fn.name in requiresUniformFlow) continue
            val calls = mutableSetOf<String>()
            fn.body.collectNames(calls)
            if (calls.any { it in requiresUniformFlow }) {
                requiresUniformFlow += fn.name
                changed = true
            }
        }
    }
    definition.functions.forEach { fn ->
        fn.body.findBranchCall(requiresUniformFlow)?.let { call ->
            throw AslDefinitionException(
                "Shader '${definition.name}' fn '${fn.name}' calls '$call' inside a branch or loop; " +
                    "WGSL requires uniform control flow.",
            )
        }
    }
    definition.fragmentStage.statements.findBranchCall(requiresUniformFlow)?.let { call ->
        throw AslDefinitionException(
            "Shader '${definition.name}' calls '$call' inside a branch or loop; WGSL requires uniform control flow.",
        )
    }
}

private fun List<AslStatement>.findBranchCall(restricted: Set<String>): String? {
    for (statement in this) {
        val body = when (statement) {
            is AslIf -> statement.body
            is AslForI32 -> statement.body
            is AslForU32 -> statement.body
            is AslAssign, is AslLet, is AslVar, is AslSet, is AslReturn, AslContinue, AslDiscard -> null
        } ?: continue
        val names = mutableSetOf<String>()
        body.collectNames(names)
        names.firstOrNull { it in restricted }?.let { return it }
    }
    return null
}

private fun validatePosition(definition: AslShaderDefinition, written: Set<String>) {
    if ("position" !in written && definition.vertexStage.positionOnly == null) {
        throw AslDefinitionException("Vertex stage of '${definition.name}' never set position.")
    }
    if (definition.varyings == null && definition.vertexStage.positionOnly == null) {
        throw AslDefinitionException(
            "Shader '${definition.name}' has no varyings struct; the vertex stage must use returnPosition.",
        )
    }
}

private fun List<AslStatement>.collectVaryings(into: MutableSet<String>): Unit = forEach {
    when (it) {
        is AslAssign -> it.value.collectVaryings(into)
        is AslLet -> it.value.collectVaryings(into)
        is AslVar -> it.value.collectVaryings(into)
        is AslSet -> it.value.collectVaryings(into)
        is AslIf -> {
            it.condition.collectVaryings(into)
            it.body.collectVaryings(into)
        }
        is AslForI32 -> {
            it.start.collectVaryings(into)
            it.endInclusive.collectVaryings(into)
            it.body.collectVaryings(into)
        }
        is AslForU32 -> {
            it.start.collectVaryings(into)
            it.endExclusive.collectVaryings(into)
            it.body.collectVaryings(into)
        }
        is AslReturn -> it.value?.collectVaryings(into)
        AslContinue, AslDiscard -> Unit
    }
}

private fun AslExpr.collectVaryings(into: MutableSet<String>) {
    when (this) {
        is AslVaryingRef -> into += name
        is AslSwizzle -> base.collectVaryings(into)
        is AslUnary -> operand.collectVaryings(into)
        is AslIndex -> index.collectVaryings(into)
        is AslChainIndex -> {
            outer.collectVaryings(into)
            inner.collectVaryings(into)
        }
        is AslBinary -> {
            left.collectVaryings(into)
            right.collectVaryings(into)
        }
        is AslArrayLiteral -> elements.forEach { it.collectVaryings(into) }
        is AslCall -> args.forEach { it.collectVaryings(into) }
        is AslConstruct -> args.forEach { it.collectVaryings(into) }
        is AslLiteral, is AslRef -> Unit
    }
}

private fun List<AslStatement>.collectNames(into: MutableSet<String>): Unit = forEach {
    when (it) {
        is AslAssign -> it.value.collectNames(into)
        is AslLet -> it.value.collectNames(into)
        is AslVar -> it.value.collectNames(into)
        is AslSet -> it.value.collectNames(into)
        is AslIf -> {
            it.condition.collectNames(into)
            it.body.collectNames(into)
        }
        is AslForI32 -> {
            it.start.collectNames(into)
            it.endInclusive.collectNames(into)
            it.body.collectNames(into)
        }
        is AslForU32 -> {
            it.start.collectNames(into)
            it.endExclusive.collectNames(into)
            it.body.collectNames(into)
        }
        is AslReturn -> it.value?.collectNames(into)
        AslContinue, AslDiscard -> Unit
    }
}

private fun AslExpr.collectNames(into: MutableSet<String>) {
    when (this) {
        is AslRef -> into += wgslName
        is AslVaryingRef -> into += name
        is AslIndex -> into += arrayName
        is AslChainIndex -> into += varName
        is AslCall -> into += function
        else -> Unit
    }
    childExprs().forEach { it.collectNames(into) }
}

private fun AslExpr.childExprs(): List<AslExpr> = when (this) {
    is AslSwizzle -> listOf(base)
    is AslUnary -> listOf(operand)
    is AslIndex -> listOf(index)
    is AslChainIndex -> listOf(outer, inner)
    is AslBinary -> listOf(left, right)
    is AslCall -> args
    is AslArrayLiteral -> elements
    is AslConstruct -> args
    is AslLiteral, is AslRef, is AslVaryingRef -> emptyList()
}

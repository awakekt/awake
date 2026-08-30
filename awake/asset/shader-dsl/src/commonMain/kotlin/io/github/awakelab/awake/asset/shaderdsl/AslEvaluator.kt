/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.shaderdsl

/**
 * Execution trace of a fragment shader for a single pixel.
 *
 * @property lets Map of local variable names (`let`) to their evaluated values.
 * @property color The final color output of the fragment shader.
 */
class AslFragmentTrace(val lets: Map<String, FloatArray>, val color: FloatArray)

internal class AslReturnSignal(val value: FloatArray?) : RuntimeException()

internal object AslContinueSignal : RuntimeException()

/**
 * CPU evaluation of a fragment stage -- the test oracle for emitted shader math and the engine
 * behind the headless terminal preview. Float scalar/vector math, control flow, and
 * module-scope functions; matrices and texture sampling throw. Values are [FloatArray]s keyed
 * by the names the emitter prints: varyings bare ("uv"), uniform fields dotted
 * ("uniforms.lightColor"), array fields flattened (`count * componentCount` floats). Bools
 * are single-element arrays holding 0/1; i32/u32 are floats holding whole numbers.
 */
class AslEvaluator private constructor(
    private val definition: AslShaderDefinition,
) {
    fun eval(expr: AslExpr, env: Map<String, FloatArray>): FloatArray = when (expr) {
        is AslLiteral -> floatArrayOf(expr.value)
        is AslRef -> lookup(env, expr.wgslName)
        is AslVaryingRef -> lookup(env, expr.name)
        is AslSwizzle -> evalSwizzle(expr, env)
        is AslUnary -> eval(expr.operand, env).let { v -> FloatArray(v.size) { -v[it] } }
        is AslIndex -> evalIndex(expr, env)
        is AslArrayLiteral -> concat(expr.elements, env)
        is AslChainIndex ->
            throw AslDefinitionException("Evaluator has no storage-buffer support.")
        is AslBinary -> evalBinary(expr, env)
        is AslCall -> evalCall(expr, env)
        is AslConstruct -> concat(constructValues(expr), env)
    }

    private fun lookup(env: Map<String, FloatArray>, name: String): FloatArray = env[name]
        ?: throw AslDefinitionException("Evaluator has no value for '$name'.")

    private fun concat(parts: List<AslExpr>, env: Map<String, FloatArray>): FloatArray =
        parts.flatMap { eval(it, env).toList() }.toFloatArray()

    private fun evalSwizzle(expr: AslSwizzle, env: Map<String, FloatArray>): FloatArray {
        val base = eval(expr.base, env)
        return FloatArray(expr.components.length) { base[swizzleIndex(expr.components[it])] }
    }

    private fun evalIndex(expr: AslIndex, env: Map<String, FloatArray>): FloatArray {
        val flat = env[expr.arrayName]
            ?: throw AslDefinitionException("Evaluator has no value for '${expr.arrayName}'.")
        val size = expr.elementShape.componentCount
        val slot = eval(expr.index, env)[0].toInt()
        return FloatArray(size) { flat[slot * size + it] }
    }

    private fun evalBinary(expr: AslBinary, env: Map<String, FloatArray>): FloatArray {
        val left = eval(expr.left, env)
        val right = eval(expr.right, env)
        if (expr.left.type.dataShapeOrNull()?.componentCount == 16 ||
            expr.right.type.dataShapeOrNull()?.componentCount == 16
        ) {
            throw AslDefinitionException("Evaluator is fragment-math only; no matrix ops.")
        }
        return binaryValues(expr.op, left, right)
    }

    private fun evalCall(expr: AslCall, env: Map<String, FloatArray>): FloatArray {
        val user = definition.functions.firstOrNull { it.name == expr.function }
        if (user == null) {
            return builtinCall(expr.function, expr.args.map { eval(it, env) })
        }
        val local = HashMap(env)
        user.params.zip(expr.args).forEach { (param, arg) -> local[param.name] = eval(arg, env) }
        return try {
            execute(user.body, local)
            throw AslDefinitionException("${user.name} finished without returning.")
        } catch (signal: AslReturnSignal) {
            signal.value ?: throw AslDefinitionException("${user.name} returned nothing.")
        }
    }

    private fun execute(statements: List<AslStatement>, env: MutableMap<String, FloatArray>) {
        statements.forEach { executeOne(it, env) }
    }

    private fun executeOne(statement: AslStatement, env: MutableMap<String, FloatArray>) {
        when (statement) {
            is AslLet -> env[statement.name] = eval(statement.value, env)
            is AslVar -> env[statement.name] = eval(statement.value, env)
            is AslSet -> env[statement.name] = eval(statement.value, env)
            is AslAssign -> env[statement.target.name] = eval(statement.value, env)
            is AslIf -> if (eval(statement.condition, env)[0] != 0f) execute(statement.body, env)
            is AslReturn -> throw AslReturnSignal(statement.value?.let { eval(it, env) })
            AslContinue -> throw AslContinueSignal
            is AslForI32 -> runLoop(
                statement.counter,
                eval(statement.start, env)[0].toInt(),
                eval(statement.endInclusive, env)[0].toInt(),
                statement.body,
                env,
            )
            is AslForU32 -> runLoop(
                statement.counter,
                eval(statement.start, env)[0].toInt(),
                eval(statement.endExclusive, env)[0].toInt() - 1,
                statement.body,
                env,
            )
        }
    }

    private fun runLoop(
        counter: String,
        first: Int,
        last: Int,
        body: List<AslStatement>,
        env: MutableMap<String, FloatArray>,
    ) {
        for (i in first..last) {
            env[counter] = floatArrayOf(i.toFloat())
            try {
                execute(body, env)
            } catch (@Suppress("SwallowedException") signal: AslContinueSignal) {
                continue
            }
        }
    }

    companion object {
        /** Run [definition]'s fragment stage for one pixel. [inputs] carries varyings and
         * uniform fields; module consts come from the definition itself. Returns RGBA. */
        fun evalFragment(definition: AslShaderDefinition, inputs: Map<String, FloatArray>): FloatArray =
            traceFragment(definition, inputs).color

        /** [evalFragment] plus every top-level `let` in declaration order -- a debugger's
         * variable view for one pixel, no GPU capture tool involved. */
        fun traceFragment(definition: AslShaderDefinition, inputs: Map<String, FloatArray>): AslFragmentTrace {
            val evaluator = AslEvaluator(definition)
            val env = HashMap<String, FloatArray>()
            definition.consts.forEach { env[it.name] = evaluator.eval(it.value, env) }
            env.putAll(inputs)
            val lets = LinkedHashMap<String, FloatArray>()
            val stage = definition.fragmentStage
            val returnValue = stage.returnValue
                ?: throw AslDefinitionException("'${definition.name}' has no color output to evaluate.")
            stage.statements.forEach { statement ->
                evaluator.executeOne(statement, env)
                if (statement is AslLet) lets[statement.name] = env.getValue(statement.name)
            }
            return AslFragmentTrace(lets, evaluator.eval(returnValue, env))
        }

        /** Builtin-only expression evaluation, for tests poking at single expressions. */
        fun eval(expr: AslExpr, env: Map<String, FloatArray>): FloatArray =
            AslEvaluator(EMPTY).eval(expr, env)

        private val EMPTY = shader("empty") {
            varyings("V")
            vertex { returnPosition(vec4(0f.lit)) }
            fragment { colorOutput(vec4(0f.lit)) }
        }
    }
}

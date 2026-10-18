/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.expression

/** What compiling a formula found: a checked tree, or the problems that stopped it. */
internal class Compilation(
    val number: NumberNode?,
    val boolean: BooleanNode?,
    val problems: List<FormulaProblem>,
    val usedVariables: List<FormulaVariable>,
    val usesRandom: Boolean,
)

/**
 * Parses [source], resolves its names against [variables], checks its types and arities, and folds every
 * part that does not depend on the inputs into a constant. It reports every problem it can find rather
 * than stopping at the first, except that a syntax error stops it. Without [allowRandom], a call to `random()`
 * is a problem too.
 */
@Suppress("TooManyFunctions") // One bind per syntax node, plus the checks they share.
internal class Compiler(
    private val source: String,
    private val variables: FormulaVariables,
    private val options: FormulaOptions,
    private val allowRandom: Boolean = true,
) {
    private val problems = ArrayList<FormulaProblem>()
    private val used = LinkedHashSet<FormulaVariable>()
    private var usesRandom = false
    private val constantInputs = FormulaInputs(FormulaVariables.Empty)

    private sealed class Typed {
        class Number(val node: NumberNode, val constant: kotlin.Boolean) : Typed()

        class Boolean(val node: BooleanNode, val constant: kotlin.Boolean) : Typed()

        /** Already reported; anything built on it is not checked further. */
        object Invalid : Typed()
    }

    @Suppress("ReturnCount") // Each stage can stop compilation.
    fun compile(expected: FormulaType): Compilation {
        if (source.length > options.maxLength) {
            problem(
                FormulaProblemKind.TooLong,
                "the formula has ${source.length} characters, more than the limit of ${options.maxLength}",
                options.maxLength,
                source.length,
            )
            return result(null, null)
        }
        val ast = try {
            Parser(tokenize(source), options.maxDepth, options.maxNodes).parse()
        } catch (e: SyntaxError) {
            problem(e.kind, e.message, e.start, e.end)
            return result(null, null)
        }
        val typed = bind(ast)
        return when {
            expected == FormulaType.Number && typed is Typed.Boolean -> {
                problem(FormulaProblemKind.TypeMismatch, "the formula gives true or false, but a number is needed", ast)
                result(null, null)
            }
            expected == FormulaType.Boolean && typed is Typed.Number -> {
                problem(FormulaProblemKind.TypeMismatch, "the formula gives a number, but a condition must give true or false", ast)
                result(null, null)
            }
            problems.isNotEmpty() -> result(null, null)
            else -> result((typed as? Typed.Number)?.node, (typed as? Typed.Boolean)?.node)
        }
    }

    private fun result(number: NumberNode?, boolean: BooleanNode?): Compilation =
        Compilation(number, boolean, problems.sortedBy { it.start }, used.toList(), usesRandom)

    private fun bind(ast: Ast): Typed = when (ast) {
        is Ast.Number -> Typed.Number(Constant(ast.value), constant = true)
        is Ast.Boolean -> Typed.Boolean(BooleanConstant(ast.value), constant = true)
        is Ast.Name -> bindName(ast)
        is Ast.Unary -> bindUnary(ast)
        is Ast.Binary -> bindBinary(ast)
        is Ast.Call -> bindCall(ast)
    }

    private fun bindName(ast: Ast.Name): Typed {
        val variable = variables.find(ast.name)
        if (variable == null) {
            val function = FormulaFunction.of(ast.name)
            val message = if (function != null) {
                "'${ast.name}' is a function; call it as ${function.signature}"
            } else {
                "unknown variable '${ast.name}'" + suggest(ast.name, variables.names, "variables")
            }
            problem(FormulaProblemKind.UnknownVariable, message, ast)
            return Typed.Invalid
        }
        used += variable
        return when (variable.type) {
            FormulaType.Number -> Typed.Number(Variable(variable.index), constant = false)
            FormulaType.Boolean -> Typed.Boolean(BooleanVariable(variable.index), constant = false)
        }
    }

    @Suppress("ReturnCount") // An invalid operand stops the check.
    private fun bindUnary(ast: Ast.Unary): Typed {
        val operand = bind(ast.operand)
        return when (ast.operator) {
            UnaryOperator.Negate -> {
                val x = number(operand, ast.operand, "'-'") ?: return Typed.Invalid
                fold(Negate(x), operand.isConstant)
            }
            UnaryOperator.Not -> {
                val x = boolean(operand, ast.operand, "'not'") ?: return Typed.Invalid
                fold(Not(x), operand.isConstant)
            }
        }
    }

    @Suppress("CyclomaticComplexMethod", "ReturnCount") // One branch per operator family.
    private fun bindBinary(ast: Ast.Binary): Typed {
        val left = bind(ast.left)
        val right = bind(ast.right)
        val constant = left.isConstant && right.isConstant
        val operator = ast.operator
        val what = "'${operator.symbol}'"
        when (operator) {
            BinaryOperator.And, BinaryOperator.Or -> {
                val a = boolean(left, ast.left, what)
                val b = boolean(right, ast.right, what)
                if (a == null || b == null) return Typed.Invalid
                return fold(if (operator == BinaryOperator.And) And(a, b) else Or(a, b), constant)
            }
            BinaryOperator.Equal, BinaryOperator.NotEqual -> {
                if (left is Typed.Boolean && right is Typed.Boolean) {
                    return fold(BooleanEqual(left.node, right.node, operator == BinaryOperator.Equal), constant)
                }
                if (left is Typed.Number && right is Typed.Number) {
                    return fold(Compare(operator, left.node, right.node), constant)
                }
                if (left !is Typed.Invalid && right !is Typed.Invalid) {
                    problem(FormulaProblemKind.TypeMismatch, "$what compares a number with true or false", ast)
                }
                return Typed.Invalid
            }
            else -> {
                val a = number(left, ast.left, what)
                val b = number(right, ast.right, what)
                if (a == null || b == null) return Typed.Invalid
                val site = site(what, ast.operatorStart, ast.operatorEnd)
                val node = when (operator) {
                    BinaryOperator.Add -> Add(a, b, site)
                    BinaryOperator.Subtract -> Subtract(a, b, site)
                    BinaryOperator.Multiply -> Multiply(a, b, site)
                    BinaryOperator.Divide -> Divide(a, b, site)
                    BinaryOperator.Remainder -> Remainder(a, b, site)
                    BinaryOperator.Power -> Power(a, b, site)
                    else -> return fold(Compare(operator, a, b), constant)
                }
                return fold(node, constant)
            }
        }
    }

    @Suppress("CyclomaticComplexMethod", "ReturnCount") // One branch per function.
    private fun bindCall(ast: Ast.Call): Typed {
        val function = FormulaFunction.of(ast.name)
        val arguments = ast.arguments.map(::bind)
        if (function == null) {
            val message = if (variables.find(ast.name) != null) {
                "'${ast.name}' is a variable, not a function"
            } else {
                "unknown function '${ast.name}'" + suggest(ast.name, FormulaFunction.entries.map { it.identifier }, "functions")
            }
            problem(FormulaProblemKind.UnknownFunction, message, ast.start, ast.nameEnd)
            return Typed.Invalid
        }
        if (function == FormulaFunction.Random && !allowRandom) return randomUnavailable(ast)
        if (!function.accepts(arguments.size)) {
            problem(
                FormulaProblemKind.ArgumentCount,
                "${function.identifier} takes ${arity(function)}, not ${arguments.size}: ${function.signature}",
                ast,
            )
            return Typed.Invalid
        }
        if (function == FormulaFunction.If) return bindIf(ast, arguments)
        val name = function.identifier
        val numbers = arrayOfNulls<NumberNode>(arguments.size)
        var valid = true
        for (i in arguments.indices) {
            numbers[i] = number(arguments[i], ast.arguments[i], name)
            if (numbers[i] == null) valid = false
        }
        if (!valid) return Typed.Invalid
        @Suppress("UNCHECKED_CAST")
        val args = numbers as Array<NumberNode>
        val site = site(name, ast.start, ast.end)
        val node = when (function) {
            FormulaFunction.Min -> Min(args)
            FormulaFunction.Max -> Max(args)
            FormulaFunction.Clamp -> Clamp(args[0], args[1], args[2])
            FormulaFunction.Floor -> Floor(args[0])
            FormulaFunction.Ceil -> Ceil(args[0])
            FormulaFunction.Round -> Round(args[0])
            FormulaFunction.Abs -> Abs(args[0])
            FormulaFunction.Sqrt -> Sqrt(args[0], site)
            FormulaFunction.Pow -> Power(args[0], args[1], site)
            FormulaFunction.Log -> Log(args[0], args.getOrNull(1), site)
            FormulaFunction.Lerp -> Lerp(args[0], args[1], args[2], site)
            FormulaFunction.Random -> {
                usesRandom = true
                return Typed.Number(RandomNumber(args.getOrNull(0), args.getOrNull(1), site), constant = false)
            }
            FormulaFunction.If -> error("if is bound by bindIf")
        }
        return fold(node, arguments.all { it.isConstant })
    }

    private fun randomUnavailable(ast: Ast.Call): Typed {
        problem(FormulaProblemKind.RandomUnavailable, "random() cannot be used here: nothing gives this formula a random source", ast)
        return Typed.Invalid
    }

    @Suppress("ReturnCount") // A number, a true-or-false value, or a mismatch.
    private fun bindIf(ast: Ast.Call, arguments: List<Typed>): Typed {
        val condition = boolean(arguments[0], ast.arguments[0], "if's condition")
        val then = arguments[1]
        val otherwise = arguments[2]
        if (condition == null || then is Typed.Invalid || otherwise is Typed.Invalid) return Typed.Invalid
        val constant = arguments.all { it.isConstant }
        if (then is Typed.Number && otherwise is Typed.Number) {
            return fold(NumberIf(condition, then.node, otherwise.node), constant)
        }
        if (then is Typed.Boolean && otherwise is Typed.Boolean) {
            return fold(BooleanIf(condition, then.node, otherwise.node), constant)
        }
        problem(
            FormulaProblemKind.TypeMismatch,
            "if's two results must both be numbers or both be true or false",
            ast.arguments[1].start,
            ast.arguments[2].end,
        )
        return Typed.Invalid
    }

    /** [node], or its value as a constant when [constant]. An undefined constant is reported under [UndefinedMath.Error]. */
    private fun fold(node: NumberNode, constant: Boolean): Typed {
        if (!constant || node is Constant) return Typed.Number(node, constant)
        return try {
            Typed.Number(Constant(node.evaluate(constantInputs)), constant = true)
        } catch (e: FormulaEvaluationException) {
            problem(FormulaProblemKind.Undefined, e.reason, e.start, e.end)
            Typed.Invalid
        }
    }

    private fun fold(node: BooleanNode, constant: Boolean): Typed {
        if (!constant || node is BooleanConstant) return Typed.Boolean(node, constant)
        return try {
            Typed.Boolean(BooleanConstant(node.test(constantInputs)), constant = true)
        } catch (e: FormulaEvaluationException) {
            problem(FormulaProblemKind.Undefined, e.reason, e.start, e.end)
            Typed.Invalid
        }
    }

    private fun number(typed: Typed, ast: Ast, what: String): NumberNode? = when (typed) {
        is Typed.Number -> typed.node
        is Typed.Boolean -> {
            problem(FormulaProblemKind.TypeMismatch, "$what needs a number, but '${snippet(ast)}' is true or false", ast)
            null
        }
        Typed.Invalid -> null
    }

    private fun boolean(typed: Typed, ast: Ast, what: String): BooleanNode? = when (typed) {
        is Typed.Boolean -> typed.node
        is Typed.Number -> {
            problem(FormulaProblemKind.TypeMismatch, "$what needs true or false, but '${snippet(ast)}' is a number", ast)
            null
        }
        Typed.Invalid -> null
    }

    private val Typed.isConstant: Boolean
        get() = when (this) {
            is Typed.Number -> constant
            is Typed.Boolean -> constant
            Typed.Invalid -> false
        }

    private fun site(operation: String, start: Int, end: Int): Site {
        val (line, column) = lineAndColumn(source, start)
        return Site(operation, start, end, line, column, options.undefinedMath)
    }

    private fun snippet(ast: Ast): String {
        val text = source.substring(ast.start, ast.end)
        return if (text.length > SNIPPET_LENGTH) text.take(SNIPPET_LENGTH) + "..." else text
    }

    private fun problem(kind: FormulaProblemKind, message: String, ast: Ast) {
        problem(kind, message, ast.start, ast.end)
    }

    private fun problem(kind: FormulaProblemKind, message: String, start: Int, end: Int) {
        val (line, column) = lineAndColumn(source, start)
        problems += FormulaProblem(kind, message, start, end, line, column)
    }

    private companion object {
        const val SNIPPET_LENGTH = 32
        const val MAX_LISTED_NAMES = 8
        const val MIN_PREFIX = 3

        fun arity(function: FormulaFunction): String = when {
            function == FormulaFunction.Random -> "no arguments or 2"
            function.maxArguments == Int.MAX_VALUE -> "at least ${function.minArguments} arguments"
            function.minArguments == function.maxArguments ->
                "${function.minArguments} argument${if (function.minArguments == 1) "" else "s"}"
            else -> "${function.minArguments} to ${function.maxArguments} arguments"
        }

        /** "; did you mean 'x'?" for the closest of [candidates], or a short list of them, or nothing. */
        fun suggest(name: String, candidates: List<String>, noun: String): String {
            val lower = name.lowercase()
            val closest = candidates
                .map { it to editDistance(lower, it.lowercase()) }
                .filter { (candidate, distance) -> distance <= maxOf(1, minOf(name.length, candidate.length) / 3) }
                .minByOrNull { it.second }
                ?.first
                ?: candidates.firstOrNull { candidate ->
                    val other = candidate.lowercase()
                    minOf(lower.length, other.length) >= MIN_PREFIX && (lower.startsWith(other) || other.startsWith(lower))
                }
            return when {
                closest != null -> "; did you mean '$closest'?"
                candidates.isEmpty() -> "; no $noun are declared"
                candidates.size <= MAX_LISTED_NAMES -> "; the $noun are ${candidates.joinToString()}"
                else -> ""
            }
        }

        fun editDistance(a: String, b: String): Int {
            var previous = IntArray(b.length + 1) { it }
            var current = IntArray(b.length + 1)
            for (i in 1..a.length) {
                current[0] = i
                for (j in 1..b.length) {
                    val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                    current[j] = minOf(substitution, previous[j] + 1, current[j - 1] + 1)
                }
                val swap = previous
                previous = current
                current = swap
            }
            return previous[b.length]
        }
    }
}

/** The 1-based line and column of [offset] in [source]. */
internal fun lineAndColumn(source: String, offset: Int): Pair<Int, Int> {
    var line = 1
    var lineStart = 0
    for (i in 0 until minOf(offset, source.length)) {
        if (source[i] == '\n') {
            line++
            lineStart = i + 1
        }
    }
    return line to offset - lineStart + 1
}

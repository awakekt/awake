/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.expression

/** A syntax tree, before names are resolved and types checked. Offsets are into the source. */
internal sealed class Ast(val start: Int, val end: Int) {
    class Number(val value: Double, start: Int, end: Int) : Ast(start, end)

    class Boolean(val value: kotlin.Boolean, start: Int, end: Int) : Ast(start, end)

    class Name(val name: String, start: Int, end: Int) : Ast(start, end)

    class Call(val name: String, val nameEnd: Int, val arguments: List<Ast>, start: Int, end: Int) : Ast(start, end)

    class Unary(val operator: UnaryOperator, val operand: Ast, start: Int) : Ast(start, operand.end)

    class Binary(val operator: BinaryOperator, val left: Ast, val right: Ast, val operatorStart: Int, val operatorEnd: Int) : Ast(left.start, right.end)
}

internal enum class UnaryOperator(val symbol: String) {
    Negate("-"),
    Not("not"),
}

internal enum class BinaryOperator(val symbol: String) {
    Add("+"),
    Subtract("-"),
    Multiply("*"),
    Divide("/"),
    Remainder("%"),
    Power("^"),
    Equal("=="),
    NotEqual("!="),
    Less("<"),
    LessOrEqual("<="),
    Greater(">"),
    GreaterOrEqual(">="),
    And("and"),
    Or("or"),
}

internal enum class TokenKind {
    Number,
    Name,
    OpenParen,
    CloseParen,
    Comma,
    Plus,
    Minus,
    Star,
    Slash,
    Percent,
    Caret,
    Equal,
    NotEqual,
    Less,
    LessOrEqual,
    Greater,
    GreaterOrEqual,
    End,
}

internal class Token(val kind: TokenKind, val start: Int, val end: Int, val text: String, val number: Double = 0.0)

/** A problem that stops parsing; [Compiler] turns it into a [FormulaProblem]. */
internal class SyntaxError(val kind: FormulaProblemKind, override val message: String, val start: Int, val end: Int) : Exception(message)

internal fun tokenize(source: String): List<Token> = Lexer(source).tokens()

private class Lexer(private val source: String) {
    private val tokens = ArrayList<Token>()
    private var i = 0

    fun tokens(): List<Token> {
        while (i < source.length) {
            val c = source[i]
            when {
                c == ' ' || c == '\t' || c == '\n' || c == '\r' -> i++
                isDigit(i) || (c == '.' && isDigit(i + 1)) -> number()
                isNameStart(c) -> name()
                else -> symbol(c)
            }
        }
        tokens += Token(TokenKind.End, source.length, source.length, "")
        return tokens
    }

    private fun number() {
        val start = i
        skipDigits()
        if (source.getOrNull(i) == '.' && isDigit(i + 1)) {
            i++
            skipDigits()
        }
        if (source.getOrNull(i) == 'e' || source.getOrNull(i) == 'E') {
            val mark = i
            i++
            if (source.getOrNull(i) == '+' || source.getOrNull(i) == '-') i++
            if (isDigit(i)) skipDigits() else i = mark
        }
        val text = source.substring(start, i)
        val value = (if (text[0] == '.') "0$text" else text).toDouble()
        if (!value.isFinite()) fail("the number $text is too large", start, i)
        tokens += Token(TokenKind.Number, start, i, text, value)
    }

    private fun name() {
        val start = i
        do {
            if (source[i] == '.') i++
            while (i < source.length && isNamePart(source[i])) i++
        } while (source.getOrNull(i) == '.' && source.getOrNull(i + 1)?.let(::isNameStart) == true)
        if (source.getOrNull(i) == '.') fail("a name cannot end with '.'", i)
        tokens += Token(TokenKind.Name, start, i, source.substring(start, i))
    }

    @Suppress("CyclomaticComplexMethod") // One branch per symbol, and one per symbol from another language.
    private fun symbol(c: Char) {
        val start = i
        val next = source.getOrNull(i + 1)
        val (kind, length) = when (c) {
            '(' -> TokenKind.OpenParen to 1
            ')' -> TokenKind.CloseParen to 1
            ',' -> TokenKind.Comma to 1
            '+' -> TokenKind.Plus to 1
            '-' -> TokenKind.Minus to 1
            '*' -> if (next == '*') fail("use ^ for a power", start, start + 2) else TokenKind.Star to 1
            '/' -> TokenKind.Slash to 1
            '%' -> TokenKind.Percent to 1
            '^' -> TokenKind.Caret to 1
            '=' -> if (next == '=') TokenKind.Equal to 2 else fail("use == to compare", start)
            '!' -> if (next == '=') TokenKind.NotEqual to 2 else fail("use 'not' instead of '!'", start)
            '<' -> if (next == '=') TokenKind.LessOrEqual to 2 else TokenKind.Less to 1
            '>' -> if (next == '=') TokenKind.GreaterOrEqual to 2 else TokenKind.Greater to 1
            '&' -> fail("use 'and' instead of '&'", start, if (next == '&') start + 2 else start + 1)
            '|' -> fail("use 'or' instead of '|'", start, if (next == '|') start + 2 else start + 1)
            '?', ':' -> fail("use if(condition, then, else) instead of '?' and ':'", start)
            else -> fail("unexpected character '$c'", start)
        }
        i += length
        tokens += Token(kind, start, i, source.substring(start, i))
    }

    private fun skipDigits() {
        while (isDigit(i)) i++
    }

    private fun isDigit(index: Int): Boolean = source.getOrNull(index)?.let { it in '0'..'9' } == true

    private fun fail(message: String, start: Int, end: Int = start + 1): Nothing =
        throw SyntaxError(FormulaProblemKind.Syntax, message, start, end)
}

/**
 * Recursive descent over [tokens]. Lowest to highest precedence: `or`, `and`, `not`, comparisons (which
 * do not chain), `+ -`, `* / %`, unary `-` and `+`, `^` (right-associative, so `-2 ^ 2` is -4 and
 * `2 ^ 3 ^ 2` is 512).
 */
@Suppress("TooManyFunctions") // One function per precedence level, plus the helpers they share.
internal class Parser(private val tokens: List<Token>, private val maxDepth: Int, private val maxNodes: Int) {
    private var position = 0
    private var depth = 0
    private var nodes = 0

    private val current: Token get() = tokens[position]

    fun parse(): Ast {
        if (current.kind == TokenKind.End) throw SyntaxError(FormulaProblemKind.Syntax, "the formula is empty", 0, 0)
        val result = parseOr()
        if (current.kind != TokenKind.End) {
            fail(
                when (current.kind) {
                    TokenKind.CloseParen -> "')' has no matching '('"
                    TokenKind.Comma -> "',' outside a function call"
                    else -> "expected an operator before ${describe(current)}"
                },
            )
        }
        return result
    }

    private fun parseOr(): Ast {
        var left = parseAnd()
        while (isKeyword("or")) {
            val op = advance()
            left = binary(BinaryOperator.Or, left, parseAnd(), op)
        }
        return left
    }

    private fun parseAnd(): Ast {
        var left = parseNot()
        while (isKeyword("and")) {
            val op = advance()
            left = binary(BinaryOperator.And, left, parseNot(), op)
        }
        return left
    }

    private fun parseNot(): Ast {
        if (!isKeyword("not")) return parseComparison()
        val op = advance()
        return nested { Ast.Unary(UnaryOperator.Not, parseNot(), op.start).counted() }
    }

    private fun parseComparison(): Ast {
        val left = parseAdditive()
        val operator = comparisonOperator(current.kind) ?: return left
        val op = advance()
        val result = binary(operator, left, parseAdditive(), op)
        if (comparisonOperator(current.kind) != null) {
            fail("comparisons do not chain; join them with 'and', as in a < b and b < c")
        }
        return result
    }

    private fun parseAdditive(): Ast {
        var left = parseMultiplicative()
        while (true) {
            val operator = when (current.kind) {
                TokenKind.Plus -> BinaryOperator.Add
                TokenKind.Minus -> BinaryOperator.Subtract
                else -> return left
            }
            val op = advance()
            left = binary(operator, left, parseMultiplicative(), op)
        }
    }

    private fun parseMultiplicative(): Ast {
        var left = parseUnary()
        while (true) {
            val operator = when (current.kind) {
                TokenKind.Star -> BinaryOperator.Multiply
                TokenKind.Slash -> BinaryOperator.Divide
                TokenKind.Percent -> BinaryOperator.Remainder
                else -> return left
            }
            val op = advance()
            left = binary(operator, left, parseUnary(), op)
        }
    }

    private fun parseUnary(): Ast = when (current.kind) {
        TokenKind.Minus -> {
            val op = advance()
            nested { Ast.Unary(UnaryOperator.Negate, parseUnary(), op.start).counted() }
        }
        TokenKind.Plus -> {
            advance()
            nested { parseUnary() }
        }
        else -> parsePower()
    }

    private fun parsePower(): Ast {
        val base = parsePrimary()
        if (current.kind != TokenKind.Caret) return base
        val op = advance()
        return nested { binary(BinaryOperator.Power, base, parseUnary(), op) }
    }

    private fun parsePrimary(): Ast {
        val token = current
        return when (token.kind) {
            TokenKind.Number -> {
                advance()
                Ast.Number(token.number, token.start, token.end).counted()
            }
            TokenKind.Name -> parseName(token)
            TokenKind.OpenParen -> {
                advance()
                val inner = nested { parseOr() }
                expect(TokenKind.CloseParen, "expected ')' to match the '(' before it")
                inner
            }
            TokenKind.End -> fail("the formula ends early; expected a value")
            else -> fail("expected a value, found ${describe(token)}")
        }
    }

    @Suppress("ReturnCount") // A literal, a variable or a call.
    private fun parseName(token: Token): Ast {
        when (token.text) {
            "true", "false" -> {
                advance()
                return Ast.Boolean(token.text == "true", token.start, token.end).counted()
            }
            "and", "or", "not" -> fail("expected a value, found '${token.text}'")
        }
        advance()
        if (current.kind != TokenKind.OpenParen) {
            if (token.text == "if") fail("'if' needs (condition, then, else)", token)
            return Ast.Name(token.text, token.start, token.end).counted()
        }
        advance()
        val arguments = ArrayList<Ast>()
        nested {
            if (current.kind != TokenKind.CloseParen) {
                while (true) {
                    arguments += parseOr()
                    if (current.kind != TokenKind.Comma) break
                    advance()
                }
            }
        }
        val close = current
        expect(TokenKind.CloseParen, "expected ',' or ')' in the call to ${token.text}")
        return Ast.Call(token.text, token.end, arguments, token.start, close.end).counted()
    }

    private fun binary(operator: BinaryOperator, left: Ast, right: Ast, op: Token): Ast =
        Ast.Binary(operator, left, right, op.start, op.end).counted()

    private fun comparisonOperator(kind: TokenKind): BinaryOperator? = when (kind) {
        TokenKind.Equal -> BinaryOperator.Equal
        TokenKind.NotEqual -> BinaryOperator.NotEqual
        TokenKind.Less -> BinaryOperator.Less
        TokenKind.LessOrEqual -> BinaryOperator.LessOrEqual
        TokenKind.Greater -> BinaryOperator.Greater
        TokenKind.GreaterOrEqual -> BinaryOperator.GreaterOrEqual
        else -> null
    }

    private inline fun <T> nested(block: () -> T): T {
        depth++
        if (depth > maxDepth) {
            throw SyntaxError(
                FormulaProblemKind.TooDeep,
                "the formula nests more than $maxDepth levels deep",
                current.start,
                current.end,
            )
        }
        val result = block()
        depth--
        return result
    }

    private fun Ast.counted(): Ast {
        nodes++
        if (nodes > maxNodes) {
            throw SyntaxError(FormulaProblemKind.TooLarge, "the formula has more than $maxNodes parts", start, end)
        }
        return this
    }

    private fun isKeyword(word: String): Boolean = current.kind == TokenKind.Name && current.text == word

    private fun advance(): Token = tokens[position].also { if (it.kind != TokenKind.End) position++ }

    private fun expect(kind: TokenKind, message: String) {
        if (current.kind != kind) fail(message)
        advance()
    }

    private fun fail(message: String, token: Token = current): Nothing =
        throw SyntaxError(FormulaProblemKind.Syntax, message, token.start, token.end)

    private fun describe(token: Token): String = when (token.kind) {
        TokenKind.End -> "the end of the formula"
        TokenKind.Number -> "the number ${token.text}"
        TokenKind.Name -> "'${token.text}'"
        else -> "'${token.text}'"
    }
}

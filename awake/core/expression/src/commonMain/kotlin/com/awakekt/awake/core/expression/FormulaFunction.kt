/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.expression

/**
 * Every function a formula may call. There are no others: a formula cannot reach anything but these and
 * the variables it is given. An editor lists them for completion and help.
 *
 * @property identifier What a formula calls it.
 * @property minArguments The fewest arguments it takes.
 * @property maxArguments The most arguments it takes; [Int.MAX_VALUE] when there is no limit.
 * @property signature How to call it, for help text.
 * @property description What it gives, in a sentence.
 */
enum class FormulaFunction(
    val identifier: String,
    val minArguments: Int,
    val maxArguments: Int,
    val signature: String,
    val description: String,
) {
    /** `if(condition, then, else)`. */
    If(
        "if",
        3,
        3,
        "if(condition, then, else)",
        "then when condition is true, else otherwise; only the chosen one is evaluated.",
    ),

    /** `min(a, b, ...)`. */
    Min("min", 2, Int.MAX_VALUE, "min(a, b, ...)", "The smallest argument."),

    /** `max(a, b, ...)`. */
    Max("max", 2, Int.MAX_VALUE, "max(a, b, ...)", "The largest argument."),

    /** `clamp(x, low, high)`. */
    Clamp("clamp", 3, 3, "clamp(x, low, high)", "x limited to low..high; low when low is above high."),

    /** `floor(x)`. */
    Floor("floor", 1, 1, "floor(x)", "The largest whole number not above x."),

    /** `ceil(x)`. */
    Ceil("ceil", 1, 1, "ceil(x)", "The smallest whole number not below x."),

    /** `round(x)`. */
    Round("round", 1, 1, "round(x)", "The nearest whole number; halves round away from zero."),

    /** `abs(x)`. */
    Abs("abs", 1, 1, "abs(x)", "x without its sign."),

    /** `sqrt(x)`. */
    Sqrt("sqrt", 1, 1, "sqrt(x)", "The square root of x; undefined below 0."),

    /** `pow(x, y)`. */
    Pow("pow", 2, 2, "pow(x, y)", "x to the power y, the same as x ^ y."),

    /** `log(x)` or `log(x, base)`. */
    Log("log", 1, 2, "log(x) or log(x, base)", "The natural logarithm of x, or its logarithm to base; undefined for x of 0 or less."),

    /** `lerp(a, b, t)`. */
    Lerp("lerp", 3, 3, "lerp(a, b, t)", "a + (b - a) * t; t is not clamped."),

    /** `random()` or `random(low, high)`. */
    Random(
        "random",
        0,
        2,
        "random() or random(low, high)",
        "A number from the inputs' random source: at least 0 and below 1, or at least low and below high.",
    ),
    ;

    internal fun accepts(count: Int): Boolean =
        count in minArguments..maxArguments && !(this == Random && count == 1)

    /** Looking functions up by what a formula calls them. */
    companion object {
        private val byIdentifier = entries.associateBy { it.identifier }

        /** The function a formula calls [identifier], or null when there is none. */
        fun of(identifier: String): FormulaFunction? = byIdentifier[identifier]
    }
}

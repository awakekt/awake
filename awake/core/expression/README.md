# `awake:core:expression`

A small, safe formula language for numbers a game keeps in data: damage from attack and defence, hit
chance, experience curves, drop weights, prices, cooldown scaling. A plugin takes a `Formula` wherever it
needs such a number; a game supplies it as a string in its data, or as Kotlin code implementing the same
interface. A project authored without code can then change any formula.

It depends on nothing in Awake. Formulas are parsed once, checked against the variables they may read,
and evaluated as many times as needed without allocating.

## The language

| Kind | Syntax |
|---|---|
| Numbers | `42`, `1.5`, `.5`, `2.5e-3` |
| Arithmetic | `+ - * / %` and `^` for powers |
| Comparisons | `== != < <= > >=`; they give true or false and do not chain |
| Logic | `and`, `or`, `not`, `true`, `false` |
| Choice | `if(condition, then, else)`; only the chosen branch is evaluated |
| Grouping | `( )` |
| Variables | names the caller declares, such as `atk` or `attacker.level` |
| Functions | `min`, `max`, `clamp`, `floor`, `ceil`, `round`, `abs`, `sqrt`, `pow`, `log`, `lerp`, `random` |

From lowest to highest precedence: `or`, `and`, `not`, comparisons, `+ -`, `* / %`, a sign (`-x`), `^`.
`^` is right-associative and binds tighter than a sign, so `-2 ^ 2` is -4 and `2 ^ 3 ^ 2` is 512. `%` takes
the sign of the dividend. `round` takes halves away from zero. `log(x)` is the natural logarithm and
`log(x, base)` any other. `FormulaFunction` lists every function with its signature and a description.

There are two types, numbers and true-or-false, and they never mix: `atk + critical` is rejected when the
formula is compiled, not when it runs. A formula has no loops, no assignment, and no way to reach
anything but its variables and the listed functions.

## Use

A plugin declares its variables once, in code:

```kotlin
val combat = formulaVariables {
    number("atk", "The attacker's attack power")
    number("def", "The defender's defence")
    number("level")
    boolean("critical")
}
val atk = combat["atk"]
val def = combat["def"]
```

Compile a formula once, then evaluate it with reusable inputs:

```kotlin
val damage = Formula.compile("max(1, atk * 2 - def) * if(critical, 1.75, 1)", combat)

val inputs = FormulaInputs(combat)
inputs[atk] = 40.0
inputs[def] = 25.0
damage.evaluate(inputs) // 55.0
```

Code can stand in for data, because a compiled formula is just a `Formula`:

```kotlin
val damage: Formula = Formula { maxOf(1.0, it[atk] * 2 - it[def]) }
```

A condition is a formula that gives true or false: `FormulaCondition.compile("level >= 10", combat)`.

## In data

`FormulaSerializer` writes a formula as its source string and compiles it when reading, so a bad formula
fails the load with its position. The variables come from the plugin, not the data, so the serializer is
built with them:

```kotlin
object DamageFormula : KSerializer<CompiledFormula> by FormulaSerializer(combat)

@Serializable
data class Skill(
    val name: String,
    @Serializable(with = DamageFormula::class) val damage: CompiledFormula,
)
```

```json
{ "name": "Strike", "damage": "max(1, atk * 2 - defence)" }
```

```text
Invalid formula 'max(1, atk * 2 - defence)': 1:18: unknown variable 'defence'; did you mean 'def'?
```

`FormulaConditionSerializer` does the same for conditions.

## Errors and editors

`Formula.check` and `FormulaCondition.check` return every problem without throwing, each with a kind, a
message, a start and end offset, and a line and column, for an editor to underline as the formula is
typed. They report unknown variables and functions (with a suggestion), wrong argument counts, type
mismatches and broken limits; a syntax error stops at the first one. `FormulaVariables.all` gives the
variables with their descriptions for completion, and `CompiledExpression.usedVariables` the ones a
formula reads.

`Formula.compile` throws `FormulaException` with the same problems.

## Limits

`FormulaOptions` bounds a formula's length (1024 characters), how deeply it nests (32 levels of
parentheses, calls, `not`, signs and powers) and how many parts it has (512). With no loops, every part
runs at most once, so the part limit is the step limit.

## Undefined results

Every value in a formula is finite: inputs must be finite when they are set, and an operation without a
finite answer (`x / 0`, `x % 0`, `sqrt` of a negative number, `log` of zero or less, a negative number to a
fractional power, an overflow) follows `FormulaOptions.undefinedMath`:

- `UndefinedMath.Zero`, the default, gives 0 and goes on, so a hit never stops a game.
- `UndefinedMath.Error` throws `FormulaEvaluationException` with the operation's position, and rejects at
  compile time a constant part that is undefined, such as `1 / 0`.

## Determinism

A server and its clients evaluate a formula to the same bits on the JVM, Android, iOS and wasm:

- `+ - * / %`, `sqrt`, `floor` and `ceil` are IEEE 754 operations every target rounds alike.
- `log` and `^` are computed here, not by the platform's math library, from those operations alone and
  with intermediates carried in two doubles, so they agree everywhere and an exact answer such as
  `100 ^ 1.5` or `log(1000, 10)` comes out exact. `min`, `max`, `clamp` and `round` are written here too.
- `random()` draws from `FormulaInputs.random`, which the caller supplies. `FormulaRandom.seeded` is a
  SplitMix64 generator built on 64-bit integer arithmetic; its `state` can be saved and restored.

The tests pin bit patterns that every target must reproduce.

## Cost

One evaluation of a typical damage formula, including setting its four inputs, takes a few hundred
nanoseconds on the JVM and in a browser; compiling one takes tens of microseconds. `FormulaCostTest`
measures both on each target and prints them.

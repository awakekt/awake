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

## A definition's formulas, and variables a game declares

A plugin's definition usually holds several formulas, each with its own variables, and some of those
variables come from the game: a combat formula reads `attacker.<stat>` for every stat the game names in its
data. A game declares them with `FormulaVariableDeclaration`, a `name`, a `type` (`"number"`, the default,
or `"boolean"`) and an optional `description`:

```json
{
  "stats": [
    { "name": "str", "description": "Strength: adds to damage" },
    { "name": "dex", "description": "Dexterity: adds to reach and speed" },
    { "name": "crit", "description": "Chance of a critical hit, 0 to 1" }
  ],
  "attackRange": "1.5 + attacker.dex * 0.01",
  "swingMillis": "max(400, 1200 - attacker.dex * 8)",
  "chances": { "miss": "clamp(0.05 + (target.dex - attacker.dex) / 200, 0, 0.5)" },
  "damage": "max(1, attacker.str * 2 - target.str) * if(random() < attacker.crit, 2, 1)",
  "threat": "damage * 1.5 + attacker.str"
}
```

The plugin declares the game's stats under each prefix it evaluates them for, then compiles every slot of
the definition together with a `FormulaSet.Builder`. Each slot has a path, its source, its variables and
whether the plugin gives it a random source:

```kotlin
@Serializable
class CombatRules(
    val stats: List<FormulaVariableDeclaration>,
    val attackRange: String,
    val swingMillis: String,
    val chances: Chances,
    val damage: String,
    val threat: String,
) {
    @Serializable
    class Chances(val miss: String)
}

class Combat(rules: CombatRules) {
    val sides = formulaVariables {
        declare(rules.stats, prefix = "attacker.")
        declare(rules.stats, prefix = "target.")
    }
    val hit = formulaVariables {
        declare(rules.stats, prefix = "attacker.")
        number("damage", "The damage the hit dealt")
    }

    private val slots = FormulaSet.Builder()
    val attackRange = slots.formula("attackRange", rules.attackRange, sides)
    val swingMillis = slots.formula("swingMillis", rules.swingMillis, sides)
    val miss = slots.formula("chances.miss", rules.chances.miss, sides)
    val damage = slots.formula("damage", rules.damage, sides, random = true) // the plugin rolls for it
    val threat = slots.formula("threat", rules.threat, hit)
    val formulas = slots.build()
}

combat.formulas[combat.damage].evaluate(inputs)
```

`build` fails once, listing every problem of every slot with where it is, rather than stopping at the first.
A slot without a random source rejects `random()` when it is compiled, not when it runs:

```text
Invalid formulas:
- attackRange: '1.5 + attacker.dx * 0.01': 1:7: unknown variable 'attacker.dx'; did you mean 'attacker.dex'?
- chances.miss: '0.05 + random() / 10': 1:8: random() cannot be used here: nothing gives this formula a random source
- threat: 'damage * 1.5 +': 1:15: the formula ends early; expected a value
```

`compile` gives the same without throwing: `FormulaSetResult.Compiled` with the set, or
`FormulaSetResult.Invalid` with every `FormulaSetProblem`, each carrying the slot's path and source and
the problem's kind, message and position, for an editor to underline both the slot and the formula. A
condition slot is added with `condition`, and `formulaSet { ... }` builds a set in one expression when the
slot handles are not needed; `formula(path)` and `condition(path)` then find a slot's result by path.

A bad stat name fails the load with its reason, the one `FormulaVariables.validateName` gives: an empty
name, a keyword (`and`, `or`, `not`, `if`, `true`, `false`) or a character other than letters, digits, `_`
and `.` between parts. `FormulaVariables.isValidName` is the same check as a yes or no. A function's name,
such as `max`, may name a variable, since a call is told apart by its parenthesis.

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

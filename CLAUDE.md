# Ghost Serializer — Project Conventions

Rules that always apply in this repository, without needing to be repeated in every session.

**Status:** the entire codebase is being migrated so that existing code complies with ALL the rules in this file — they are not merely guidelines for new code. The migration includes, at a minimum: reorganizing the constants objects (see the dedicated section below), moving every `companion object` to the end of its class, splitting files that contain several classes into one class per file, enforcing alphabetical ordering of methods and variables, auditing SOLID/DRY compliance outside the hot path, and extracting long methods into granular helpers. The project is large (~779 `.kt` files), so the work is executed in phases, and each phase is confirmed before any code is touched (the same pattern used for the KDoc cleanup: pilot → verify compilation and tests → next scope), never in a single pass. Progress is tracked in `REFACTOR_PLAN.md`.

## Design Rules

Ghost is a library that competes on throughput (SWAR, monomorphism, zero allocations on the hot path). The 7 rules below **always apply to surface code** (public API, contracts, codegen, tests, infrastructure). On the **parser/writer hot path** (see the next section), 3 of the rules have a documented exception. These exceptions are not arbitrary: they exist because measured evidence (real benchmarks, not theory) shows that applying the rule there costs speed.

1. **No magic strings or magic numbers.** Use named constants (`private const val ...`); never repeat bare literals in code. — *No exception; zero performance cost (they are compile-time constants).*
2. **Prefer easy-to-understand code to clever code.** Favor clarity over the shortest possible line. — *No exception. Where the code is inherently dense (SWAR bit tricks), clarity comes from a "why" comment, not from rewriting the algorithm.*
3. **Follow SOLID principles.** — *Applies to the contract layer (`GhostSerializer`, `GhostRegistry`, the Ktor/Retrofit/Spring integrations). See the hot-path exception below: the parsing/writing kernels intentionally use monomorphic lambdas instead of OOP abstractions.*
4. **Comments/KDoc only at the top of the class.** On methods or variables, only when strictly necessary (see the KDoc criteria below). — *No exception; zero performance cost.*
5. **Methods and variables are ordered alphabetically per section** (within each visibility/kind block). — *No exception; zero performance cost (the compiler does not care about source order). See the hot-path note below for the single case where logical order wins because the algorithm is sequential.*
6. **DRY.** Reuse code as much as possible; avoid duplicating the same logic or boilerplate across sibling files. — *See the hot-path exception below: there is one intentional, measured and documented duplication.*
7. **Reproducible, predictable architecture and class structure.** Each kind of file (constants object, reader/writer extension file, serializer, contract) always follows the same internal section order, so anyone can predict where something lives without reading the whole file — see "Reproducible Class Structure" below. — *No exception; zero performance cost.*

**Fully abstract interfaces (rule 3), decided 2026-09-22:** no contract interface has methods or properties with a default implementation — not even trivial defaults (`= false`, `{}`, `= 0`) that were previously considered acceptable. When default behavior must be shared among implementers, it goes in an **abstract class that implements the interface** (the same pattern as `AbstractList`/`AbstractMap` in Java), never in the interface itself: whoever wants full control implements the interface directly, and whoever wants the convenience extends the abstract class. Already applied to `GhostSerializer`/`AbstractGhostSerializer` (the interface defines the complete contract; `AbstractGhostSerializer` provides the defaults, including the compatibility bridges between reader/writer channels, which previously lived as inline defaults in the interface and mixed the contract with real adaptation logic) and to `GhostRegistry`/`AbstractGhostRegistry` (the interface keeps only `getSerializer` abstract; `AbstractGhostRegistry` provides the defaults for `getAllSerializers` [empty map], `prewarm` [no-op] and `registeredCount` [derived from `getAllSerializers`]). Apply the same criterion by default whenever any other contract interface in the project is touched.

**Clarification of "per section" (rule 5), decided 2026-09-21:** a section is a coherent logical or structural grouping of related members (e.g. "structural operations" vs. "value operations" in a reader/writer class; "parsing functions" vs. "formatting functions" in a codec) — not necessarily the entire public block of a class as one list. Alphabetical order applies **within each section**, never across sections. Do not force a single giant alphabetical list when that breaks the logical coherence of the code, but do not invent new sections as an excuse to avoid alphabetical order where it is not needed either. When in doubt about whether a grouping is a real section or an excuse, prefer whatever would already be obvious to someone reading the file for the first time.

**Exact scope of rule 5, decided 2026-09-21:**
- **It applies to `enum class` values** — they are sorted by constant name just like methods and properties (e.g. `RawJsonKind.kt` is reordered).
- **It does not apply to constructor parameters.** The order of constructor parameters (primary or secondary) follows other criteria (required before defaulted, logical order of use, grouping by relationship), not alphabetical order.

Example already applied with this criterion: `GhostJsonWriter`/`GhostJsonStringWriter`/`GhostYamlWriter` (writing) and `GhostJsonFlatReader`/`GhostJsonReader`/`GhostJsonStringReader` (reading) group their public API into sections by structural role (e.g. structural operations: `beginObject`/`endObject`/`beginArray`/`endArray`/`name`; value operations: `value`/`nullValue`/`rawValue`; raw operations: `write*ValueRaw`), alphabetical within each section. The same criterion applies to any file and is not a privilege of the hot path (e.g. the parsing/formatting functions of `proto/wkt/ProtoDurationSerializer.kt`/`ProtoTimestampSerializer.kt`, or `serializers/GhostFastPrimitiveArrayHelpers.kt`, are grouped the same way without needing the hot-path exception below).

## Measured Hot-Path Exceptions (parser/writer)

This covers code in `parser/*` and `writer/*` under `ghost-serialization/src/commonMain` — the shared kernels (`parseJsonFloatingBodyCore`, `tryParseEightDigitsAt`, `GhostJsonReader`/`GhostJsonFlatReader`/`GhostJsonStringReader`, `internalSelect`) and their three per-channel call sites (bytes/string/streaming). These exceptions are not a general license to write unclear code; they apply only where a measured reason already exists:

- **SOLID (rule 3) — monomorphism instead of polymorphism.** The kernels receive reader state through inlined lambdas (`getPosition: () -> Int`, `getByte: (Int) -> Int`, etc.) instead of a `Reader` interface with implementations. This is the established way to keep each call site monomorphic after inlining (see the KDoc of `GhostJsonNumberValidationHelpers.kt`/`GhostJsonFloatParseHelpers.kt`/`GhostJsonIntegerParseHelpers.kt`, previously unified in `GhostJsonNumberParseHelpers.kt`). Do not introduce a new interface or abstraction there without first measuring with `benchmarkTwitter`.
- **DRY (rule 6) — streaming and flat readers are intentionally kept separate.** `GhostJsonReader` (streaming) and `GhostJsonFlatReader` (in-memory) are NOT unified even though they share almost all of their logic: unification was attempted and cost real throughput on payloads with many arrays (documented in the KDoc of `GhostSerializer`). Any new duplication of this kind needs the same evidence (a real benchmark, not an isolated one) before it is accepted as an exception; without a measurement it is real duplication and must be eliminated.
- **Granular methods (style paragraph) — with a nuance.** Extracting small, self-explanatory functions remains the goal, but on the hot path they must be `inline` (as the existing pattern already is: `internal inline fun parseIntDigitsCore(...)`) so the compiler fuses them into the call site — a new non-inline function inside a parsing loop adds a real call per iteration. Extract, but inline.
- **Alphabetical order (rule 5) — with a nuance.** Inside a kernel where the algorithm is a true sequence (parse header → integer digits → decimal point → fraction → exponent), the algorithm's reading order wins over alphabetical order. Alphabetical order still applies between unrelated kernels within the same file.

Practical rule for deciding whether something qualifies as "hot path": is it on the `Ghost.serialize`/`Ghost.deserialize` path per element or field (executed once per object or per field, not once per process)? If so, and changing the design per the rules above could cost throughput, measure first with `benchmarkTwitter` before applying the rule blindly — do not assume, and do not use an isolated benchmark (see the policy below).

**Confirmed 2026-09-21: this practical rule is not limited literally to the `parser/`/`writer/` directories.** `proto/` runs per field during `Ghost.serialize`/`deserialize` of proto3 payloads, so any code unification there that touches that path (e.g. duplication between `ProtoJsonFlatReaderBase64` and `GhostBase64`, or between `writeLongToBytes`/`formatLong`) requires the same before/after `benchmarkTwitter` measurement as a `parser/`/`writer/` kernel — it is not treated as surface DRY/SOLID without measuring merely because the package has a different name. Any other code outside `parser/`/`writer/` that runs per element or field on the serialize/deserialize path is evaluated with the same criterion, case by case.

## Formatting Style

Function signatures: **one parameter per line**, always — even with a single parameter. Do not compress onto one line. This applies to `fun` (including `expect`/`actual`) and to constructors with real logic (`init` with validation or computation, derived properties, etc.).

**Exception decided 2026-09-21: trivial primary constructors are exempt.** The primary constructor of a `data class`/`value class`/`annotation class` that only declares properties, with no `init` and no logic (the parameter IS the property, with no transformation), may stay compact on a single line, whether it has one parameter or several. This is a different case from a function or a constructor with real logic. Examples: the fixture DTOs in `ghost-integration-test/model/`, and simple single-field annotations such as `GhostFlatten`/`GhostWrap`/`GhostName` in `ghost-api`.

Bad:
```kotlin
@InternalGhostApi
internal expect fun ghostUtf8BytesToString(bytes: ByteArray, offset: Int, length: Int): String
```

Good:
```kotlin
@InternalGhostApi
internal expect fun ghostUtf8BytesToString(
    bytes: ByteArray,
    offset: Int,
    length: Int
): String

override fun scanString(
    start: Int,
    limit: Int
): Long {
    val localData = data
    return scanStringImpl(start, limit) {
        localData[it].toInt() and BYTE_MASK
    }
}

override fun get(
    index: Int
): Int = data[index].toInt() and BYTE_MASK
```

Bad: very long methods without granular, self-explanatory helpers. Extract small private functions whose names already explain what they do, instead of one monolithic method.

Long or multi-line conditions in `if`/`when`: extract them into a well-named boolean variable (or a private function/property if reused in more than one place) instead of inlining the entire condition inside the `if`. This follows the same criterion as the granular methods above — on the hot path, if it is extracted as a function rather than a `val`, it must be `inline` (see the hot-path exception).

Bad:
```kotlin
if (project.plugins.hasPlugin(PLUGIN_ANDROID_APP) ||
    project.plugins.hasPlugin(PLUGIN_ANDROID_LIB) ||
    project.plugins.hasPlugin(PLUGIN_JVM)
) {
    setupAndroidOrJvmCore(project, ghostVersion)
}
```

Good:
```kotlin
val isAndroidOrJvmProject = project.plugins.hasPlugin(PLUGIN_ANDROID_APP) ||
    project.plugins.hasPlugin(PLUGIN_ANDROID_LIB) ||
    project.plugins.hasPlugin(PLUGIN_JVM)

if (isAndroidOrJvmProject) {
    setupAndroidOrJvmCore(project, ghostVersion)
}
```

Function calls: **always use named arguments** (`parameter = value`), and if a call has more than one argument, **one argument per line** (a line break after each comma), following the same criterion as signatures. **There is no exception for tests**: `assertEquals`/`assertTrue`/`assertNull`/etc. from `kotlin.test` are named like any other function (`assertEquals(expected = ..., actual = ...)`). There is no hot-path exception either — naming an argument has no runtime cost (it is resolved at compile time and the generated bytecode is identical to the positional version). The real exceptions, because the language does not allow named arguments there: operators (`a + b`), indexed access (`data[i]`), `infix` functions, the trailing lambda in trailing-lambda syntax (`list.map { it * 2 }` stays as is — converting it to `list.map(transform = { it * 2 })` would be less clear, not more), and **calls to Java platform functions/constructors without retained parameter names** — e.g. `StringBuilder` on the JVM target is `actual typealias StringBuilder = java.lang.StringBuilder`, and the compiler rejects named arguments against compiled Java code lacking that metadata (`Named arguments are prohibited for non-Kotlin functions`). This is not a style decision but a real compiler restriction: if Kotlin rejects it, the call is made positionally, with no need to document each case.

Bad:
```kotlin
val reader = GhostJsonFlatReader(data, offset, length)
```

Good:
```kotlin
val reader = GhostJsonFlatReader(
    data = data,
    offset = offset,
    length = length
)
```

**Verified 2026-09-21 with a real case: the Gradle API (`Project`, `PluginContainer`, `DependencyHandler`, `ConfigurationContainer`, etc.) is Java and falls under the same exception as `StringBuilder`.** `project.dependencies.add(configurationName = ..., dependencyNotation = ...)` **does not compile** (`Named arguments are prohibited for non-Kotlin functions`), so it is called positionally, like any other platform API without Kotlin metadata:
```kotlin
// Correct: DependencyHandler.add is Java (Gradle) and does not accept named arguments.
project.dependencies.add(CONFIG_IMPL, runtimeDep)
```
Before naming arguments against an external library (Gradle, OkHttp, Retrofit, Ktor, Spring, KotlinPoet/KSP, etc.), check whether the function is written in Kotlin or is Java interop. When in doubt, compile and let the compiler error confirm it; do not assume.

**Third real confirmation (2026-09-21/22): `org.junit.jupiter.api.Assertions.*` (JUnit 5) is Java — do not confuse it with `kotlin.test.*`.** Several files in `ghost-serialization/src/jvmTest` use `import org.junit.jupiter.api.Assertions.assertTrue` (not `kotlin.test.assertTrue`) for tests with `@TestFactory`/`DynamicTest`, and that variant rejects named arguments just like Gradle and `StringBuilder`. Before naming the arguments of an `assertTrue`/`assertEquals`/etc., check the import: if it comes from `org.junit.*` it is Java and the call is positional; if it comes from `kotlin.test.*`, the arguments are named.

**Second real exception confirmed 2026-09-21, systemic on the hot path: invoking a function-typed parameter (lambda) never accepts named arguments.** `getByte(position)`, where `getByte: (Int) -> Int` is a lambda parameter (not a named function), uses Kotlin's `invoke()` convention, which exposes its parameters as synthetic `p1`/`p2`/etc. — there is no way to name them, for any lambda, anywhere. This is precisely the established shape of the `parser/`/`writer/` kernels (they receive reader state via `getPosition: () -> Int`, `getByte: (Int) -> Int`, etc.), so the vast majority of calls there fall under this exception rather than the Java-interop one. The call is always positional whenever the receiver is a function-typed parameter, with no need to document each case (it is a language restriction, like the Java-interop one).

**Never use a fully qualified name (`package.Class.function(...)`) inline in code bodies, decided 2026-09-22.** Not even to avoid a name clash with a class member — always import (`import package.function`) and call it unqualified. Real case: `GhostJsonFlatReader.matchCoerceBooleanBytes()` (a private method with no arguments) clashes by name with the top-level function `matchCoerceBooleanBytes(start, length, onError, getByte)` in `parser/common/GhostParserUtils.kt`. It looked like the full prefix was needed to disambiguate, but it is not: Kotlin's overload resolver considers both visible candidates (member + import) and chooses by signature applicability, not by scope proximity, so a call with 4 named arguments resolves unambiguously to the top-level function even with the same-named private member in scope. Before assuming that full qualification is required because of a name clash, import and compile — the compiler either confirms or rejects it (`overload resolution ambiguity` only if there are genuinely equally applicable candidates). If the compiler DOES reject it due to real ambiguity, the fix is to rename one of the two symbols, not to leave the call qualified inline.

**`=` is never left alone at the end of a line (decided 2026-09-21).** When the body of a function or getter is `= expression` and that expression spans several lines (e.g. a call with named arguments), the opening of the expression stays on the same line as the `=` — the `=` is never the last token of the line with the call starting only on the next one.

Bad:
```kotlin
fun fromUtf8Bytes(
    bytes: ByteArray
): RawJson =
    RawJson(
        storage = bytes,
        storageOffset = 0,
        storageLength = bytes.size
    )
```

Good:
```kotlin
fun fromUtf8Bytes(
    bytes: ByteArray
): RawJson = RawJson(
    storage = bytes,
    storageOffset = 0,
    storageLength = bytes.size
)
```

**A single-line KDoc that grows too long is wrapped across several lines (decided 2026-09-21).** The practical line-length reference is 120 columns (the same limit already used by `GhostEmitterConstants`/`GeneratedCodeHygiene.MAX_GENERATED_LINE_LENGTH` in `ghost-compiler`). Each continuation line starts with `*`, aligned under the `/**` of the first line.

Bad:
```kotlin
/** Encodes [json] to UTF-8 bytes. Prefer [fromBufferSlice] or reader capture when decoding parsed wire bytes. */
```

Good:
```kotlin
/** Encodes [json] to UTF-8 bytes. Prefer [fromBufferSlice]
 * or reader capture when decoding parsed wire bytes. */
```

## Reproducible Class Structure

Rule 7. The goal: someone who has never seen a file should know, from the *kind* of file, in what order to look for something — without having to scan the whole file each time.

**Standard internal order** (sections that do not apply are omitted, but those that apply appear in this order):

1. Class/object KDoc (rule 4).
2. Imports (the order the IDE auto-formatter already imposes; do not reorder by hand — this project does not use ktlint and has no Gradle task to verify it).
3. Constants / instance properties.
4. `init` blocks.
5. Public API — alphabetical within the block (rule 5, with the hot-path nuance where applicable).
6. Private functions/helpers — alphabetical within the block, except on the hot path (same nuance).
7. `companion object` — **always the last element of the class**, never at the top or in the middle.

**Constants objects** (`GhostJsonConstants`, `GhostYamlConstants`, etc.) additionally follow their own fixed pattern, one per responsibility — see "Constants Object Organization" below.

**Reader/writer extension files** (pattern already established in `parser/*Numbers.kt`, `parser/*Strings.kt`): the public entry function(s) come first (`nextFloat`, `nextDouble`, ...), followed by their `private` helpers in the order in which the public function calls them (not alphabetical there — this is the "true algorithm sequence" exception from the hot-path section).

**Theme definition files** (e.g. `ghost-playground/ui/theme/PlaygroundTheme.kt`, decided 2026-09-30): the public palette `val` constants come first, grouped by role (canvas, accents, ink, card) — role order wins over alphabetical order, as in constants objects; then the private construction (`colors`, `typography`) that consumes them; and finally the public `@Composable` entry function. One-off color values inside `colors`/`typography` are extracted into a named constant in the first block.

**One class per file.** Prefer splitting each class into its own file; group several in one file only when strictly necessary (e.g. a `private`/sealed helper class that has no meaning or use outside the class that contains it).

**Scope of "companion object last" and "one class per file":** they apply to new code AND are part of the retroactive mass refactor announced at the top of this file — all existing code will be migrated to comply, and they are not merely future criteria. The retroactive migration is executed in confirmed phases, like the rest of the mass refactor.

When a new file does not clearly fit any of these molds, ask before inventing a new structure — the goal is repeatability, not for every file to have its own criteria.

## Constants Object Organization

Discussed and decided on 2026-09-21. **`GhostJsonConstants.kt` was executed on 2026-09-22** (pilot); `GhostYamlConstants.kt` (→ `GhostYamlTokens`/`GhostYamlScanConstants`/`GhostYamlWriterConstants`/`GhostYamlErrorMessages`) and `ghost-compiler/GhostEmitterConstants.kt` (→ `GhostCommonConstants`/`GhostAnalyzerConstants`/`GhostProcessorConstants`/`GhostCodegenConstants`/`GhostEmitterConstants` [emitter templates only]/`GhostPerfectHashConstants`) were executed on 2026-09-30 with the same criterion (see REFACTOR_PLAN.md, Phases 4c/4d).

**Rule:** a constants `object` is split by **who consumes it and what question it answers**, not by data type. One-line test: if two people looking for a constant arrive there with different questions (error message vs. byte/char), they are different groups even if both are `Int`.

There is no performance cost: `const val` is inlined at every call site at compile time, and non-const `val`s (LUTs, tables) are the same singleton whether they live in 1 object or 6. Splitting constants is purely organizational.

**Groups executed for `GhostJsonConstants.kt`** (744 lines / 427 constants / 108 importing files at the time of migration, 2026-09-22):

| Object | Question it answers |
|---|---|
| `GhostJsonErrorMessages` | JSON parse/decode error messages |
| `GhostJsonTokens` | Fixed bytes/chars of JSON syntax and their case-folded forms |
| `GhostJsonNumericLimits` | Overflow limits and numeric formatting tables/precision |
| `GhostJsonScanConstants` | SWAR bit tricks, perfect-hash dispatch, bitmask packing |
| `GhostJsonWriterConstants` | Writer fast-path literals, buffer sizes, UTF-8 packing |
| `GhostProtoConstants` (package `proto/`) | Everything specific to proto3: WKT type URLs, Hinnant timestamp/duration, base64, proto errors — its own domain, not spread across the 5 above. |

Decisions applied:
1. **Proto in its own object**, `GhostProtoConstants`, in the `proto/` package — it is not spread across the 5 JSON objects. `COLLISION_HASH_MULTIPLIER` was confirmed to be general-purpose (not proto-only) and stayed in `GhostJsonScanConstants`.
2. **Duplicates are consolidated only when type, value and destination object are all the same.** `ESC_B_INT`/`ESC_F_INT`/`ESC_N_INT`/`ESC_R_INT`/`ESC_T_INT` were merged into `B_BYTE_INT`/`F_BYTE_INT`/`N_BYTE_INT`/`R_BYTE_INT`/`T_BYTE_INT` (same `Int`, same purpose, same object). **`CHAR_N`/`CHAR_T`/etc. (`Char`) were NOT merged** with their `Int` equivalents — different types, and call sites that need the real `Char` (e.g. `FlatCharArrayWriter`); merging would have required changing the type at every call site, which is more risk than benefit. `N_BYTE_INT`/`N_LOWER_BYTE_INT` were not merged either (same value, 110, but generic JSON `null` vs. proto NaN/Infinity — different contexts and destination objects).
3. **"Parser Internals & Overflow Checks" was dismantled**: numeric overflow → `GhostJsonNumericLimits`; state sentinels (`MATCH_END`, `RESET_TOKEN_BYTE`, `MATCH_NONE`) and SWAR shifts (`SHIFT_56`..`SHIFT_4`) → `GhostJsonScanConstants`; Unicode surrogate pairs (`HIGH_SURROGATE_START`, etc.) → `GhostJsonTokens`.
4. **The internal order of each object follows relevance/usage, not strict alphabetical order** — grouping by consumer already satisfies the spirit of rule 5.
5. **Real finding from the migration**: the `ghost-compiler` codegen emitted a fully qualified reference to `GhostJsonConstants.RESET_TOKEN_BYTE` as literal text in generated code (`GhostEmitterConstants.STR_RESET_TOKEN_BYTE_CALL`), not via an import — the only case of this kind, corrected to `GhostJsonScanConstants`.

## Comment/KDoc Criteria

Validated in the September 2026 KDoc cleanup (applies together with rule 4 above):

- **KEEP** (optional tightening): comments that document a non-obvious "why" — a constraint, a platform quirk, a historical bug, a fuzzing/spec edge case, a design decision that is not evident from the code.
- **SHORTEN**: multi-paragraph KDoc blocks that only repeat what the code already says — condense to one sentence if it adds anything.
- **DELETE**: KDoc that merely restates the signature or the symbol name, identical boilerplate repeated across sibling files, and comments that narrate the next line without adding information.
- General rule: when in doubt, keep the comment (shortened if very long) — do not delete real information just to shorten.

**Reinforced preference 2026-09-21, from repeated real cases (`GhostExtension`, `GhostPlugin`):** even a comment that would qualify as KEEP (it documents a real "why") is **consolidated into the class KDoc** instead of remaining as a loose `//` comment or KDoc on an individual method or property, unless the method is so large or the note so specific to a single line that moving it to the class would lose its context. Before adding or leaving a method- or property-level comment, first try to solve the problem with a **more self-explanatory name** (see "granular methods" in Formatting Style) — only if the name cannot convey the "why" does the comment go in the class KDoc. Real example: the 3 property KDocs of `GhostExtension` were consolidated into `@property` tags in the interface KDoc; the two `//` comments in `GhostPlugin` (why the KSP wiring is reactive, why network injection runs in `afterEvaluate`) were moved to the class KDoc, and the method that previously needed a comment to explain what its `Boolean` returned was renamed (`addKspCompilerDependency` → `tryAddKspCompilerDependency`) instead of being documented.

## Verification — Gradle Task Names per Module

They are not uniform across modules; use exactly these:

| Module | Compile | Test |
|---|---|---|
| `ghost-serialization` (KMP) | `compileKotlinJvm`, `compileAndroidMain`, `compileKotlinIosArm64`, `compileKotlinWasmJs` | `jvmTest`, `testAndroidHostTest`, `wasmJsTest`* |
| `ghost-compiler` | `compileKotlin` | `test` |
| `ghost-compiler-plugin` | `compileKotlin` | `test` |
| `ghost-ktor` | — | `jvmTest` |
| `ghost-retrofit` | — | `test` |
| `ghost-spring-boot-starter` | — | `test` |
| `ghost-gradle-plugin` | — | `test` |
| `ghost-api` (KMP) | — | `jvmTest` |
| `ghost-integration-test` | — | `test` |
| `ghost-benchmark` (plain JVM) | `compileKotlin` | — |
| `ghost-playground` (KMP jvm+wasmJs) | `compileKotlinJvm`, `compileKotlinWasmJs` | — |

\* `wasmJsTest` runs `wasmJsBrowserTest` (karma + webpack). If it fails because of a JS tooling error (e.g. `karma-sourcemap-loader`) before running a single test, it is an environment problem, not a problem in the Kotlin code; it does not block verification if the remaining targets and tests pass.

Always invoke tasks with an explicit `:module:` prefix — a task name without a prefix runs in ALL subprojects that define it, which can pull in unwanted modules (e.g. `wasmJsTest` without a prefix also runs in `ghost-api`).

## Other Policies

- **Do not revert unexplained style or formatting changes without asking first.** The user often makes their own changes in parallel, in the background, while work is happening in the repository.
- **Measure performance only with a real benchmark** (`benchmarkTwitter` / the real `Ghost.serialize`/`Ghost.deserialize` path), never with an isolated or separately reimplemented benchmark — an isolated benchmark can predict an improvement that turns out to be a real regression on the actual path.

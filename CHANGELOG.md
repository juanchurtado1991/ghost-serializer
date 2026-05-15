# Changelog

## [1.1.14] - 2026-05-15
### Performance & Cold Start 
- **Lazy Registry Discovery (O(1))**: Rewrote the cross-platform discovery engine to be fully lazy. Ghost now loads the default module via `Class.forName` (fast-path) and stops before invoking the expensive `ServiceLoader` scan. **Reduces cold start by ~20-30% on JVM/Android.**
- **Automatic Fragmented Emission**: Models with more than 40 properties are now automatically serialized/deserialized using a "Chunked" strategy. This prevents JVM "method too large" errors and maintains peak JIT performance even for massive objects.
- **JIT-Friendly Inlining**: Applied `@Suppress("NOTHING_TO_INLINE")` and mandatory `inline` modifiers to fragmented chunks, guiding the JVM to optimize execution paths while bypassing bytecode limits.
- **Hot-Path Inlining**: Refactored `resolveSerializer` to be an `inline` function that checks the cache first, eliminating lambda allocations and `typeOf` calls during steady-state execution.
- **Manual Platform Iterators**: Replaced `sequence/yield` with specialized manual Iterators in `androidMain` and `jvmMain` to eliminate coroutine overhead and resolve IDE indexing "red" errors.
- **Zero-Collision Naming**: Standardized all platform-specific files with unique suffixes (`.jvm.kt`, `.android.kt`, `.ios.kt`, `.wasmJs.kt`) to prevent JVM class name collisions in multi-module projects.
- **Bitwise-Accelerated Parsing**: All internal validations (hex, whitespace, control characters) now use bitwise masks for maximum throughput.
- **Optimized Field Matching**: Refactored `selectNameAndConsume` to use an O(1) bitwise Trie lookup, reducing field identification overhead.
- **Writing Fusion**: Fused separator and header writes in `GhostJsonFlatWriter` to minimize branching and eliminate performance regressions.
- **Zero-Allocation Numbers**: Hardened the fast-path engine for `Double` and `Long` formatting to ensure zero heap allocation in 99% of use cases.

### Added
  - **Polymorphic Fallbacks**: Introduced `@GhostFallback` for sealed classes. Unknown discriminators now map to a safe default subclass instead of throwing an exception.
  - **Enum & Type Resilience**: New `@GhostResilient` annotation. Prevents crashes when receiving invalid enums or type mismatches by assigning `null` or default values.
  - **Custom Field Decoders/Encoders**: Support for `@GhostDecoder` and `@GhostEncoder` properties. Delegate specific field logic to manual functions while keeping the rest of the class automated.
  - **Flexible Type Coercion**: Added `coerceBooleans` (interpret 0/1 as bool) and `coerceNumbers` (parse numeric strings as Int/Long) flags to `GhostJsonReader`.
- **Modular Serialization**: Added `Ghost.addRegistry()` for manual, reflection-free registration of serializers in dynamic or plugin-based architectures.
- **Official Networking Adapters**: Shipped production-ready bridges for **Ktor 3.0** and **Retrofit 2.11**. Implemented explicit JSON `null` handling in `GhostConverterFactory` for total compatibility with Retrofit response bodies.
- **Reference Implementations**: Added official test app repositories and integration guides for **Android**, **Native iOS (Swift)**, and **Spring Boot**.

### Refactoring & Modularity (Zero Magic Strings)
- **Zero Magic Strings Architecture**: Achieved 100% literal-free compiler logic. Centralized all hardcoded templates, identifiers, and ProGuard rules into `GhostEmitterConstants.kt`.
- **Compiler Decomposition**: Surgically refactored the monolithic `DeserializeCodeEmitter` into specialized modules (`StandardEmitter`, `FragmentedEmitter`, `BaseDeserializeEmitter`, etc.) for maximum maintainability.
- **Encapsulated Generation Logic**: Introduced `GhostPropertyExtensions` and `TypeHelpers` to handle complex code generation via clean, type-safe helper methods.

### Fixed
- **Android Compatibility (API 21)**: Resolved a critical `NewApi` error. Replaced API 33+ `Arrays.equals` range comparison with a manual loop compatible with Android API 21.
- **KSP Format Integrity**: Resolved critical `UnknownFormatConversionException` and `IllegalArgumentException` by strictly segregating KotlinPoet templates (`%L`, `%T`) from standard Java format strings (`%s`).
- **Signature Mismatch**: Fixed a bug in standard mode where the `deserialize` function was omitted from the generated serializer object due to improper delegation.
- **Map Serialization Integrity**: Resolved a critical corruption bug in `MapSerializer` where the parser failed to consume separators correctly in complex nested maps.
- **Unresolved Processor References**: Fixed a build error where generated registry constants were missing the `C.` prefix after the massive constant migration.
- **Large String Flush**: Resolved a boundary condition in `FlatByteArrayWriter` when serializing very large escaped strings near the scratch buffer limit.
- **Depth Protection**: Improved JIT-friendliness of recursion depth checks in nested objects.

## [1.1.13] - 2026-04-24
### Fixed
- **Turbopack & Next.js 16 Resilience**: Applied architecture-grade fixes to the Kotlin-generated `.mjs` Wasm loader to prevent `TypeError: Cannot read properties of undefined (reading 'name')` and `ReferenceError: import.meta.resolve is not a function`.
- **Isomorphic Node.js Pathing**: Removed the experimental `import.meta.resolve()` and replaced it with standard isomorphic ES module path resolution (`fs.readFileSync(path.join(path.dirname(url.fileURLToPath(import.meta.url)), ...))`).
- **Memory Export Patch**: Fixed `ReferenceError: getGhostWasmInstance is not defined` by directly exporting and accessing the WebAssembly `memory` object from the generated bridge, removing the dependency on deprecated factory functions.

## [1.1.12] - 2026-04-24
### Added
- **Invisible Bridge (Auto-Tooling)**: Ghost now automatically downloads and installs OpenJDK 21 and Gradle 8.13 to `~/.ghost/` if they are missing. Developers no longer need to manually install Java or Gradle to use the Wasm engine.
- **Zero-Config UX**: Default models directory moved to `./src/ghost-models`. If the directory doesn't exist, Ghost creates it automatically.
- **Smart Project Discovery**: Improved logic to distinguish between pure web projects and KMP projects, ensuring Standalone Mode (Invisible Bridge) is triggered reliably in web environments.
- **Native Enum Support (JS/Wasm)**: The transpiler now generates Kotlin Enums and correctly links them with TypeScript, using `.name` for property mapping in the JS bridge.

### Fixed
- **API Visibility Hardening**: Internal Wasm interop functions are now protected with `@InternalGhostApi(Level.WARNING)`, preventing accidental usage by end-users while allowing the generated bridge to link correctly.
- **Standalone IR Collisions**: Fixed a critical compiler error where infrastructure files were colliding with library internals by moving generated code to a dedicated `com.ghost.serialization.standalone` package.
- **Transpiler Input Validation**: Fixed a bug where the transpiler would crash instead of guiding the user when the models directory was missing or empty.


## [1.1.11] - 2026-04-24
### Fixed
- **CLI Binary Execution**: Added missing node shebang to the transpiler to prevent syntax errors when running `ghost-sync` via NPM.

## [1.1.10] - 2026-04-24
### Added
- **Resilience Transpiler (v2.0)**: Complete rewrite of the Node.js transpiler with a robust parser supporting nested models, list of objects, and Kotlin keyword escaping (including soft keywords like `value`).
- **Single-Crossing Factory (Fast Path)**: Optimized the JS-to-Wasm bridge for small models (< 10 fields). It now generates a dedicated `@JsFun` factory that creates the entire JS object in a single crossing, reducing bridge latency.
- **Self-Validating Test Suite**: Shipped a comprehensive test suite with **250+ assertions** covering edge cases, stress payloads, and paranoid naming collisions.
- **Type-Safe Union Fallbacks**: Implemented smart fallback logic for TypeScript union types (`string | number`) and Literal types (`'a' | 'b'`) ensuring they map safely to Kotlin `String`.

### Fixed
- **Nested List Parsing**: Resolved a critical bug where lists of anonymous nested objects `{ val: string }[]` were incorrectly parsed as single objects.
- **Default Value Stability**: Fixed invalid Kotlin generation for `Long` (0L) and `Double` (0.0) default values.
- **Kotlin Keyword Shadowing**: Integrated an automatic escaping engine for all 70+ Kotlin keywords and soft-keywords to prevent compilation errors in generated models.

### Performance Results (Web/Next.js)
- **Memory Efficiency**: Confirmed a **~33% reduction in Heap Memory** usage compared to standard JSON.parse + Zod validation.
- **Trade-off Note**: While memory usage is significantly lower, raw deserialization latency (ms) is ~15% higher due to the Wasm boundary crossing. This makes Ghost ideal for memory-constrained environments or large state-management apps.


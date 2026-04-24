# Changelog

## [1.1.9] - 2026-04-23

### Changed
- **Advanced WASM Memory Profiling**: Enhanced the `ghost-sync` CLI transpiler template to automatically intercept and preserve the `WebAssembly.Instance` reference.
- **Benchmark Integration**: Exposed `getGhostWasmMemoryByteLength()` in the generated `ghost-bridge.ts`, allowing consuming web frameworks (like Next.js/Webpack) to accurately measure the raw WASM linear memory footprint bypassing JavaScript Garbage Collection noise and bundler encapsulation.
All notable changes to the Ghost Serialization project will be documented in this file.

## [1.1.8] - 2026-04-23
### Added
- **Ghost Gradle Plugin (Smart Auto-Configurator)**: Introduced `com.ghostserializer.ghost` Gradle plugin. Reduces installation to a single line `id("com.ghostserializer.ghost") version "1.1.8"`. Automatically detects Android, JVM, and KMP projects, configures KSP, and handles complex multiplatform dependency injection autonomously.
- **Custom Sealed Discriminators**: Added `discriminator` parameter to `@GhostSerialization(discriminator = "kind")`. Enables seamless integration with third-party polymorphic APIs (Stripe, JSON-LD `@type`, Kubernetes) without custom parsing logic.
- **Auto-Networking Injection**: The Gradle Plugin automatically detects `ktor-client` and `retrofit2` in the host project and injects `ghost-ktor` / `ghost-retrofit` bridges transparently.
- **R8 / ProGuard Resilience**: Shipped `consumerProguardFiles` within the `ghost-api` module to automatically protect `@GhostSerialization` annotated models from aggressive minification during Release builds.

### Fixed
- **Polymorphic Parent Resolution**: Hardened the KSP compiler to guarantee that subclass serializers strictly inherit dynamic discriminator keys declared in the parent interface/sealed class.
- **Test Infrastructure Safety**: Wrapped the plugin KSP application logic in a protective `try/catch` block to gracefully degrade (emitting warnings instead of crashes) when users have misconfigured Gradle Plugin Portal repositories.

## [1.1.7] - 2026-04-23
### Added
- **Collision-Safe Model Registry**: Added `val name: String = ""` parameter to `@GhostSerialization` annotation. When set, the KSP compiler uses this name as the unique registry key, allowing two models with the same class name in different packages to coexist without collision.
- **`typeName` Contract on `GhostSerializer`**: Added mandatory `val typeName: String` to the `GhostSerializer` interface. The registry now uses this static, compile-time-resolved string instead of runtime reflection (`KClass.simpleName`), ensuring O(1) lookup stability on all platforms including WasmJs where `qualifiedName` is unavailable.
- **Full `typeName` Coverage**: Implemented `typeName` across all built-in serializers: `PrimitiveSerializers`, `CollectionSerializers` (`List<T>`, `Map<String,V>`, `IntArray`, `LongArray`), and all test mock serializers.
- **Scientific Benchmark Engine (v2.0)**: Rewrote the Next.js benchmark engine with professional methodology: randomized engine order to eliminate JIT bias, median latency (most statistically robust metric), 100-iteration warmup per engine, GC yield between engines, and dead-code elimination prevention via `keepAlive` arrays.
- **Fair Field Coverage in Benchmark**: Expanded `GhostCharacter` model to match the full Zod schema field-for-field (`origin`, `location`, `episode[]`, `type`, `gender`, `url`, `created`). Previously Ghost was deserializing 5 fields while Zod validated 12 — an unfair comparison.
- **Honest Memory Reporting**: Replaced unreliable `performance.memory` heap-delta approach with engine-specific APIs: Ghost reports `WebAssembly.Memory.buffer.byteLength` (exact WASM linear memory), JS engines use `performance.measureUserAgentSpecificMemory()` (requires COOP/COEP, already configured).
- **Network/CPU Separation in Benchmark**: Real API fetch now happens before the benchmark timer starts. Network time is logged separately to the console. All engines compete only on CPU deserialization of identical in-memory payloads.
- **Fixed Transpiler Template Bug**: Corrected a nested template-literal syntax error in `ghost-transpiler.js` that produced a malformed `ghost-bridge.ts` with an extra closing brace, causing a Next.js/Turbopack parse failure.

### Fixed
- **WASM Graceful Failure (Falla Elegante)**: Hardened WASM boundaries with `try/catch(Throwable)` to prevent browser crashes (WASM Traps) during catastrophic engine errors or malformed JSON payloads.
- **Next.js & TypeScript Bridge Resilience**: The synchronous API (`deserializeModelSync`) now safely intercepts `null` engine states and throws standard JavaScript `Error` exceptions, acting as a robust drop-in replacement for `JSON.parse`.
- **Comprehensive E2E Integration Tests**: Added a suite of +200 cross-platform tests validating unicode, missing fields, malformed JSON, un-registered models, and VM stress edge cases directly inside Headless Chrome.
- **Industrial Performance Profiling**: Purged all heavy `console.log` / `println` operations from the fast-path execution loop to ensure zero-allocation I/O overhead, maintaining strict sub-millisecond execution times.
- **NPM Publication Automation**: Automated the inclusion of `README.md` and `LICENSE` files within the NPM Wasm production library bundle.
- **Descriptive Telemetry**: Enhanced WASM/JS bridge with critical hints for missing serializers.
- **Type-Safe Bridge (Next.js)**: Finalized the automatic TS-to-Kotlin synchronization flow.

## [1.1.6] - 2026-04-21
### Added
- **Security Hardening (Arithmetic)**: Implemented built-in overflow detection for `Long` and `Int` parsing to prevent silent data corruption.
- **DoS Protection (Resource Guarding)**: Integrated platform-aware `maxCollectionSize` limits (JVM, Android, Native, Web) to prevent memory exhaustion attacks.
- **Type-Safe Next.js Bridge**: Enhanced the transpiler to generate `ghost-bridge.ts`, providing `deserializeModel` with full TypeScript IntelliSense.
- **Memory Hygiene**: Automated string pool wiping during reader recycling to prevent sensitive data persistence.
- **Ghost CLI (Beta)**: Added automated TypeScript-to-Kotlin model transpiler for Next.js.
- **Wasm Auto-Registry**: Implemented an automated registry discovery hook in `ghostPrewarm()` for WASM targets.

### Fixed
- **Double Formatter Overflow**: Resolved a critical edge-case bug where rounding `Long.MAX_VALUE` could cause an arithmetic overflow in the JSON writer.
- **Depth Consistency**: Synchronized `MAX_DEPTH` (255) between `GhostJsonReader` and `GhostJsonWriter` for uniform structural support.
- **Gradle Stability**: Fixed case-sensitive task dependencies that caused intermittent `SourcesJar` failures.
- **WASM Interop**: Replaced unsafe `!!` assertions in the JS bridge with resilient null-handling.
- **Turbopack Compatibility**: Optimized WASM bridge visibility for Next.js development servers.

## [1.1.5] - 2026-04-21

### Added
- **Modular Discovery System:** Implemented `ServiceLoader` support on JVM/Android, enabling automatic discovery of generated registries across multi-module projects without manual configuration.
- **Smart Platform Abstraction:** Added internal `isJvm` utility for intelligent platform-aware logic in common code, improving test reliability and runtime performance.

### Fixed
- **Industrial Build Stabilization:** Resolved persistent KSP metadata conflicts and Gradle task dependency race conditions by enforcing a Single Source of Truth (SSOT) generation model in `commonMain`.
- **Registry Collision Errors:** Fixed "Redeclaration" errors in Multiplatform builds by isolating KSP outputs and linking them correctly to compile tasks.
- **Memory Audit Integrity:** Resolved false-positive failures in JS/Wasm memory tests by adapting identity checks to native JS engine behavior.
- **Android Runtime Discovery:** Fixed `UnresolvedReference` for generated registries in Android by aligning the runtime discovery with the new modular FQCN architecture.

### Optimized
- **Zero-Latency Core Startup:** Implemented a direct-load bypass for the core registry on JVM/Android, bypassing ServiceLoader overhead for the main library module.
- **Benchmark High-Fidelity:** Calibrated the Rick & Morty benchmark for a fair "Bytes-to-Object" comparison, revealing the true performance gap between Ghost and string-based serializers in Web environments.
- **JS/Wasm Bridge Visibility:** Restored missing `@JsExport` for `ghostDeserialize` and `ghostSerialize` in the WASM target.
- **Next.js Integration:** Adjusted WASM bridge return types to ensure full compatibility with TypeScript/Next.js client-side execution.

## [1.1.4] - 2026-04-20

### Added
- **Universal JS/Wasm Compatibility:** Enabled `js(IR)` and `wasmJs` targets across all core modules (`ghost-api`, `ghost-serialization`, `ghost-ktor`), providing full Node.js and Browser support.
- **High-Performance JS Bridge:** Implemented `GhostJsApi` with `@JsExport` for seamless Next.js/TS integration, enabling reflection-free deserialization via `ghostDeserialize`.
- **Universal Synchronization:** Implemented `__ghost_synchronized__` across all platforms (JVM, Android, iOS, JS, Wasm) ensuring thread-safety for multi-threaded environments.

### Fixed
- **Thread Safety:** Resolved potential race conditions in `Ghost.kt` by synchronizing global registry and cache access.
- **Web Build Collisions:** Resolved implicit dependency conflicts between JS and Wasm targets by assigning unique module names.

### Optimized
- **Zero-Allocation String Parsing (JS/Wasm):** Optimized String-to-ByteArray conversion in the JS bridge to minimize memory overhead.
- **O(1) Serializer Lookup:** Replaced String-based type comparisons with direct `KClass` lookups for maximum throughput.

## [1.1.3] - 2026-04-20

### Added
- **Adaptive Heuristics System:** ghost-serialization now automatically adjusts internal collection capacities and string pool limits based on the target platform (Android, JVM, iOS, Web).
- **Jank Performance Tracking:** Added frame-drop detection (Jank) to benchmarks to measure UI smoothness during deserialization.
- **UI Performance Insights:** Added real-time speed factor comparison (e.g., "1.18x faster") against baseline engines (KSER).
- **Dynamic Load Control:** Integrated a 1-10 page slider in sample apps for stress testing.

### Fixed
- **Coroutines Main Dispatcher:** Fixed a crash on Desktop JVM by adding missing `kotlinx-coroutines-swing` dependencies.
- **Benchmark Precision:** Updated UI to show results with 2 decimal places for more accurate comparisons.
- **Web UI Cleanup:** Automatically hide memory allocation metrics on platforms where measurement is not reliable (Wasm/JS).
- **Duplicate Declarations:** Resolved a build issue in `RickAndMortyRepository` caused by duplicate variable names.

### Optimized
- **JVM Throughput:** Increased initial capacity and pool sizes on Desktop/Server to maximize JIT performance.
- **Memory Footprint:** Further reduced baseline memory usage on Android to ~190KB for standard payloads.
- **Benchmark Warm-up:** Unified JIT warm-up cycles to 100 iterations across all platforms for scientific consistency.

## [1.1.2] - 2026-04-19
- Initial release with Dokka 2.x support, Maven Central, and NPM distribution.

# Changelog
All notable changes to the Ghost Serialization project will be documented in this file.

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

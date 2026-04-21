# Changelog
All notable changes to the Ghost Serialization project will be documented in this file.

## [1.1.5] - 2026-04-21

### Fixed
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

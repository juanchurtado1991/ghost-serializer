# Changelog
All notable changes to the Ghost Serialization project will be documented in this file.

## [1.1.2] - 2026-04-20

### Added
- **Adaptive Heuristics System (L6 Staff Level):** ghost-serialization now automatically adjusts internal collection capacities and string pool limits based on the target platform (Android, JVM, iOS, Web).
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

## [1.1.1] - 2026-04-19
- Initial industrial release with Dokka 2.x support and Maven Central publishing.

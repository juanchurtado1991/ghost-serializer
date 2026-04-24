# Ghost Transparency Report

## Performance Paradox: Memory vs. Latency (v1.1.10)

As of version 1.1.10, we have achieved a critical architectural milestone. Our benchmarks on Node.js and Headless Chrome reveal a deliberate performance tradeoff that defines Ghost's position in the ecosystem.

### 1. The Memory Triumph (33% Reduction)
Ghost consistently uses **33% less RAM** than standard `JSON.parse` + `Zod` validation. This is achieved by:
- **Linear Memory Deserialization**: We parse directly into WASM memory buffers, bypassing the initial JS object creation that `JSON.parse` forces.
- **Fast Path (Single-Crossing)**: For models with few fields, we cross the JS/Wasm boundary once to create the entire object, preventing thousands of intermediate JS object allocations.
- **Zero-Allocation Strings**: We share string references across the bridge where possible.

### 2. The Latency Tradeoff (+15% ms)
Due to the overhead of the WebAssembly-to-JavaScript boundary (Crossing the Bridge), raw execution time is roughly **15% higher (in ms)** than native browser parsers.
- **Why?**: Modern JS engines (V8) have highly optimized C++ intrinsics for `JSON.parse`. Any WASM-based solution that produces JS objects must pay the "bridge tax" for each property assigned.
- **Our Philosophy**: In many production scenarios (especially on mobile), **RAM is the bottleneck, not CPU**. A 15% latency increase is often imperceptible (e.g., 2ms vs 2.3ms), but a 33% RAM saving can prevent the browser from killing the application tab or causing severe UI jank due to Garbage Collection (GC) pauses.

### 3. Ideal Use Cases
- **Low-End Devices**: Android Go or older iPhones with limited RAM.
- **Data-Heavy Apps**: Dashboards, map providers, or large-scale Rick & Morty style APIs that stay in memory for long periods.
- **Background Workers**: Where memory quotas are strict.
- **Consistency**: When you need exact same serialization behavior between your Kotlin Backend and Next.js Frontend.

### 4. Comparison Summary

| Metric | JSON.parse + Zod | Ghost Serialization (Wasm) | Winner |
|---|---|---|---|
| **Heap Memory** | Baseline (100%) | **~67% (-33%)** | 👻 Ghost |
| **Latency (ms)** | **Baseline (100%)** | ~115% (+15%) | ⚡ Native |
| **Type Safety** | Runtime (Validation) | Compile-time (Transpilation) | 👻 Ghost |
| **Stability** | Manual Schema Sync | Auto-Sync (ghost-models) | 👻 Ghost |

---
*Verified on v1.1.10. Benchmarks conducted on Next.js 15.2 (Turbopack) & Node.js 24.14.*

# Benchmark Results

[![Speed](https://img.shields.io/badge/Speed-red.png?style=flat&logo=speedtest&logoColor=white)](benchmarks.md)

> **Methodology**: Independent Gradle JVM tasks (`benchmarkTwitter`, `benchmarkSynthetic`, …). Engines: **Ghost, KSER, Gson, Jackson**. **10 000-iteration warmup**, **500 sessions × 50 batched samples**. Per session: **Ghost+KSER measured first** (back-to-back), then Gson/Jackson. Regression uses **median** of per-session Ghost÷KSER ratios. LIST / SYNC / WRITING suites isolated with phase GC only.
>
> **`ghost.textChannel`**: default **false** per model. The default `deserialize(String)` path UTF-8-encodes once and parses via `GhostJsonFlatReader` — faster than native `GhostJsonStringReader` for typical DTOs (see [§ When to enable `textChannel`](advanced-features.md#5-native-string-reader-textchannel)). Opt in with `@GhostSerialization(textChannel = true)` only on **very large** String payloads (e.g. Twitter macro dataset). Legacy module flag `ghost.textChannel=true` still enables the string channel for every model in the module.

## Running the Benchmark Yourself

Independent JVM processes — run only what you need.

### Tests + benchmarks

| Task | What it runs |
|------|----------------|
| `./gradlew allTests` | **All tests** (JVM + Android + iOS on macOS) — alias of `ciTest` |
| `./gradlew ciTestJvm` | JVM modules only (faster local gate) |
| `./gradlew verifyAndBenchmarkFast` | `allTests` → `benchmarkRegressionFast` |
| `./gradlew verifyAndBenchmark` | `allTests` → `benchmarkRegression` (full) |

Benchmark tasks (`benchmarkRegression`, `benchmarkRegressionFast`, `benchmarkTwitter`, …) **run `allTests` first by default**. Skip the test gate with `-PskipTests`:

```bash
# Dev gate: tests + fast regression (~15–20 min with tests on Linux)
./gradlew :ghost-benchmark:benchmarkRegressionFast

# Bench only (~1–2 min)
./gradlew :ghost-benchmark:benchmarkRegressionFast -PskipTests

# CI regression gate — full profile (~9 min bench, +tests unless -PskipTests)
./gradlew :ghost-benchmark:benchmarkRegression -PskipTests

# Dev regression gate — fast profile (~1–2 min, ±10% tolerance)
./gradlew :ghost-benchmark:benchmarkRegressionFast -PskipTests

# Individual suites (add -PskipTests to skip allTests gate)
./gradlew :ghost-benchmark:benchmarkTwitter -PskipTests    # ~1–2 min
./gradlew :ghost-benchmark:benchmarkSynthetic -PskipTests  # ~4–6 min
./gradlew :ghost-benchmark:benchmarkSpecial -PskipTests
./gradlew :ghost-benchmark:benchmarkRawJson -PskipTests

# Full README suite (cold start + all tables)
./gradlew :ghost-benchmark:run -PskipTests
./gradlew :ghost-benchmark:run -Pjit -PskipTests  # JIT log for JITWatch
```

Exit code `1` = regression beyond ±10% tolerance vs baseline (twitter / synthetic tasks only).

| Task | Wall-clock | Regression gate |
|------|------------|-----------------|
| `benchmarkTwitter` / `benchmarkTwitterFast` | ~3 min / ~30s | ✅ 6 categories |
| `benchmarkSynthetic` / `benchmarkSyntheticFast` | ~6 min / ~60s | ✅ 9 categories |
| `benchmarkRegression` / `benchmarkRegressionFast` | ~9 min / ~1–2 min | ✅ all 15 |
| `run` (full) | ~8–10 min | ✅ all 15 |

---

## Twitter Macro Dataset

Results on [twitter_macro.json](../../ghost-benchmark/src/main/resources/twitter_macro.json). **🏆** = highest throughput · **💾** = leanest.

| Operation | Engine |        Throughput (ops/s)        | Mem (KB/op) |
| :--- | :---: |:--------------------------------:| :---: |
| **Decode (String)** | **👻 Ghost** | **1384.2** 🏆 *(+27.7% vs KSER)* | **361.2** 💾 *(-73.0% vs KSER)* |
| | KSER |              1084.3              | 1337.6 |
| **Decode (Bytes)** | **👻 Ghost** | **1144.5** 🏆 *(+74.0% vs KSER)* | **621.2** 💾 *(-85.5% vs KSER)* |
| | KSER |              657.8               | 4297.0 |
| **Decode (Streaming)** | **👻 Ghost** | **686.6** 🏆 *(+132.7% vs KSER)* | **1269.7** 💾 *(-33.3% vs KSER)* |
| | KSER |              295.1               | 1904.9 |
| **Encode (String)** | **👻 Ghost** | **4472.5** 🏆 *(+55.8% vs KSER)* | 1074.3 |
| | KSER |              2869.6              | **972.1** 💾 |
| **Encode (Bytes)** | **👻 Ghost** | **2341.8** 🏆 *(+90.9% vs KSER)* | **420.2** 💾 *(-81.0% vs KSER)* |
| | KSER |              1226.5              | 2206.8 |
| **Encode (Streaming)** | **👻 Ghost** | **2325.2** 🏆 *(+61.9% vs KSER)* | **426.9** 💾 *(-6.2% vs KSER)* |
| | KSER |              1436.1              | 455.0 |

---

## Multi-engine tables

Fixed row order: **Ghost → KSER → Gson → Jackson**. **🏆** = fastest (lowest ms / highest ops/s) · **💾** = leanest (lowest KB/op).

## Deserialization — 200 objects (LIST_MEDIUM)

| Engine | String (ms) | MEM (KB) | Bytes (ms) | MEM (KB) | Streaming (ms) | MEM (KB) |
|:---|:---:|:---:|:---:|:---:|:---:|:---:|
| **👻 Ghost** | **0.080 ±0.003** 🏆 | **157.7** 💾 | **0.045 ±0.001** 🏆 | **24.8** 💾 | **0.046 ±0.001** 🏆 | **24.8** 💾 |
| KSerialization | 0.096 ±0.004 | 189.7 | 0.095 ±0.002 | 189.7 | 0.163 ±0.005 | 189.7 |
| Gson | 0.092 ±0.003 | 164.0 | 0.092 ±0.004 | 164.0 | 0.094 ±0.002 | 173.5 |
| Jackson | 0.143 ±0.004 | 631.7 | 0.136 ±0.003 | 631.8 | 0.137 ±0.004 | 631.9 |

---

## Deserialization — 2000 objects (SYNC_FULL_LARGE)

| Engine | String (ms) | MEM (KB) | Bytes (ms) | MEM (KB) | Streaming (ms) | MEM (KB) |
|:---|:---:|:---:|:---:|:---:|:---:|:---:|
| **👻 Ghost** | 0.654 ±0.020 | **1173.7** 💾 | **0.370 ±0.005** 🏆 | **213.4** 💾 | **0.382 ±0.004** 🏆 | **334.2** 💾 |
| KSerialization | 0.767 ±0.009 | 1836.6 | 0.769 ±0.035 | 1836.6 | 1.413 ±0.014 | 1957.5 |
| Gson | **0.644 ±0.009** 🏆 | 1343.8 | 0.644 ±0.025 | 1343.8 | 0.652 ±0.008 | 1366.7 |
| Jackson | 1.380 ±0.073 | 6210.0 | 1.209 ±0.011 | 6210.1 | 1.217 ±0.014 | 6210.1 |

---

## Serialization — 1000 objects (WRITING)

| Engine | String (ms) | MEM (KB) | Bytes (ms) | MEM (KB) | Streaming (ms) | MEM (KB) |
|:---|:---:|:---:|:---:|:---:|:---:|:---:|
| **👻 Ghost** | 0.119 ±0.008 | **185.3** 💾 | **0.086 ±0.002** 🏆 | **92.6** 💾 | **0.084 ±0.001** 🏆 | **32.2** 💾 |
| KSerialization | **0.112 ±0.006** 🏆 | 264.9 | 0.116 ±0.005 | 326.3 | 0.210 ±0.005 | 203.5 |
| Gson | 0.353 ±0.012 | 731.0 | 0.359 ±0.013 | 823.6 | 0.941 ±0.055 | 3996.8 |
| Jackson | 0.201 ±0.009 | 458.6 | 0.168 ±0.007 | 312.1 | 0.158 ±0.001 | 123.6 |

---

## Stress Tests

Fixed column order: **Ghost → Gson → KSER → Jackson**. **🏆** = fastest.

| Test | Ghost | Gson | KSer | Jackson |
|:---|:---:|:---:|:---:|:---:|
| Deep Nesting — 20 levels (ms) | **0.183** 🏆 | 0.410 | 0.458 | 5.309 |
| Malformed JSON — resilience (ms) | 0.034 | **0.025** 🏆 | 0.077 | 0.087 |

---

## 👻 Ghost Special Features

| Feature | µs/op | B/op |
|:---|:---:|:---:|
| Polymorphism — Sealed Class Dispatch | **1.54** | 300 |
| Structural Flattening — `@GhostFlatten` (3 levels deep) | **0.34** | 32 |
| Resilience — `@GhostResilient` (type mismatch recovery) | **1.67** | 824 |
| Custom Decoders — `@GhostDecoder` (hex + nullable transform) | **0.72** | 80 |
| Polymorphic Fallback — `@GhostFallback` (unknown discriminator) | **0.95** | 264 |
| Opaque JSON — RawJson field capture (slice, bytes) | **0.33** | 48 |
| Opaque JSON — RawJson.kind() on captured slice | **0.08** | 0 |
| Opaque JSON — RawJson.decodeAs&lt;T&gt;() second stage | **0.76** | 128 |
| JsonEnvelope — parsePayload (SSE fat envelope) | **1.80** | 16664 |
| JsonEnvelope — parseTyped (cached serializer route) | **1.60** | 16671 |

---

## 👻 RawJson Capture (bytes vs string channels)

| Scenario | µs/op | B/op |
|:---|:---:|:---:|
| Decode `RawJson` field (bytes, small, slice capture) | **0.59** | **48** |
| Decode `RawJson` field (string, small, owned capture) | 0.84 | 536 |
| Decode `ByteArray` field (bytes, small, copy capture) | 0.70 | 280 |
| Decode `RawJson` field (bytes, large nested metadata) | **66.02** | **48** |
| Decode `RawJson` field (string, large nested metadata) | 97.21 | 175640 |
| Decode `ByteArray` field (bytes, large nested metadata) | 68.78 | 87808 |
| Encode `RawJson` payload (`encodeToBytes`, slice write) | **0.29** | 56 |
| Encode `RawJson` payload (`encodeToString`, UTF-8 decode) | 0.34 | 80 |
| Top-level `RawJson` decode (bytes) | 65.47 | 24 |
| Top-level `RawJson` decode (string) | 72.93 | 87808 |
| Top-level `RawJson` round-trip (bytes in/out) | 71.31 | 87808 |
| Top-level `RawJson` round-trip (string in/out) | 99.81 | 175616 |

---

← [Back to README](../../README.md)

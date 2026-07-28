# Benchmark Results

[![Speed](https://img.shields.io/badge/Speed-red.png?style=flat&logo=speedtest&logoColor=white)](benchmarks.md)

> **Methodology**: Independent Gradle JVM tasks (`benchmarkTwitter`, `benchmarkSynthetic`, …). Engines: **Ghost, KSER, Moshi (codegen adapters)**. **10 000-iteration warmup**, **500 sessions × 50 batched samples** on LIST / SYNC / WRITING. Per session: **Ghost+KSER measured first** (back-to-back), then Moshi. Throughput tables report **decimal GB/s** (`payload_bytes / seconds / 10⁹`, equivalent to `ops/s × payload / 10⁹`). Stress tests report **latency only** (single-shot or 100-iteration avg). Regression uses **median** of per-session Ghost÷KSER ratios. LIST / SYNC / WRITING suites isolated with phase GC only.
>
> **`ghost.textChannel`**: default **true** per model. String benchmarks use the generated `GhostJsonStringReader` / string writer; byte and streaming benchmarks use their dedicated readers. Byte-only applications can opt out with `@GhostSerialization(textChannel = false)` or the module flag `ghost.textChannel=false` to reduce generated code size. See [Native String Reader](advanced-features.md#5-native-string-reader-textchannel).

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
./gradlew :ghost-benchmark:benchmarkYaml -PskipTests
./gradlew :ghost-benchmark:benchmarkYamlFast -PskipTests

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

Results on [twitter_macro.json](../../ghost-benchmark/src/main/resources/twitter_macro.json) (**631 514 bytes**). Columns always read **throughput → latency → allocation**. Throughput is decimal GB/s (`ops/s × payload / 10⁹`). **🏆** = highest throughput · **⏱️** = lowest latency · **💾** = leanest.

| Operation | Engine | Throughput (GB/s) | Latency (µs/op) | Allocation (KB/op) |
|:---|:---:|---:|---:|---:|
| **Decode (String)** | **👻 Ghost** | **1.229** 🏆 *(+71.2% vs KSER)* | **514.0** ⏱️ | **361.1** 💾 *(-73.0%)* |
| | KSER | 0.718 | 879.2 | 1337.6 |
| | Moshi | 0.374 | 1688.2 | 1708.9 |
| **Decode (Bytes)** | **👻 Ghost** | **1.052** 🏆 *(+148.1% vs KSER)* | **600.2** ⏱️ | **621.2** 💾 *(-85.5%)* |
| | KSER | 0.424 | 1488.6 | 4297.0 |
| | Moshi | 0.275 | 2297.4 | 4668.4 |
| **Decode (Streaming)** | **👻 Ghost** | **0.529** 🏆 *(+174.1% vs KSER)* | **1193.7** ⏱️ | **1268.6** 💾 *(-33.4%)* |
| | KSER | 0.193 | 3269.5 | 1904.9 |
| | Moshi | 0.426 | 1481.9 | 1708.8 |
| **Encode (String)** | **👻 Ghost** | **2.807** 🏆 *(+41.9% vs KSER)* | **225.0** ⏱️ | 1074.3 |
| | KSER | 1.978 | 319.3 | **981.6** 💾 |
| | Moshi | 0.515 | 1226.8 | 2893.0 |
| **Encode (Bytes)** | **👻 Ghost** | **1.514** 🏆 *(+97.1% vs KSER)* | **417.0** ⏱️ | **420.2** 💾 *(-81.0%)* |
| | KSER | 0.768 | 822.7 | 2216.3 |
| | Moshi | 0.346 | 1822.7 | 4387.4 |
| **Encode (Streaming)** | **👻 Ghost** | **1.502** 🏆 *(+46.8% vs KSER)* | **420.5** ⏱️ | **426.9** 💾 *(-8.1%)* |
| | KSER | 1.023 | 617.3 | 464.5 |
| | Moshi | 0.798 | 791.1 | 560.5 |

---

## Multi-engine tables

Fixed row order: **Ghost → KSER → Moshi (codegen)**. Each mode reads **GB/s → µs/op → KB/op**. Throughput is decimal GB/s (`payload_bytes / seconds / 10⁹`). **🏆** = highest GB/s · **⏱️** = lowest latency · **💾** = leanest.

Payload sizes used for the conversion: LIST_MEDIUM **15 622 B**, SYNC_FULL_LARGE **123 822 B**, WRITING **62 822 B**.

## Deserialization — 200 objects (LIST_MEDIUM)

| Engine | String<br>GB/s · µs · KB/op | Bytes<br>GB/s · µs · KB/op | Streaming<br>GB/s · µs · KB/op |
|:---|:---:|:---:|:---:|
| **👻 Ghost** | **0.558** 🏆 · **28.0** ⏱️ · **33.9** 💾 | **0.497** 🏆 · **31.4** ⏱️ · **24.5** 💾 | **0.492** 🏆 · **31.8** ⏱️ · **24.5** 💾 |
| KSerialization | 0.215 · 72.8 · 113.9 | 0.216 · 72.3 · 113.9 | 0.124 · 125.6 · 113.9 |
| Moshi | 0.204 · 76.7 · 107.3 | 0.205 · 76.3 · 107.3 | 0.232 · 67.3 · 107.3 |

---

## Deserialization — 2000 objects (SYNC_FULL_LARGE)

| Engine | String<br>GB/s · µs · KB/op | Bytes<br>GB/s · µs · KB/op | Streaming<br>GB/s · µs · KB/op |
|:---|:---:|:---:|:---:|
| **👻 Ghost** | **0.606** 🏆 · **204.2** ⏱️ · **240.3** 💾 | **0.561** 🏆 · **220.7** ⏱️ · **158.3** 💾 | **0.538** 🏆 · **230.2** ⏱️ · **222.7** 💾 |
| KSerialization | 0.278 · 445.7 · 1009.3 | 0.277 · 446.6 · 1009.3 | 0.144 · 860.1 · 1073.8 |
| Moshi | 0.219 · 564.8 · 884.4 | 0.219 · 564.7 · 884.4 | 0.251 · 492.6 · 884.4 |

---

## Serialization — 1000 objects (WRITING)

| Engine | String<br>GB/s · µs · KB/op | Bytes<br>GB/s · µs · KB/op | Streaming<br>GB/s · µs · KB/op |
|:---|:---:|:---:|:---:|
| **👻 Ghost** | **0.909** 🏆 · **69.1** ⏱️ · **92.7** 💾 | **0.744** 🏆 · **84.5** ⏱️ · **92.7** 💾 | **0.767** 🏆 · **81.9** ⏱️ · **32.3** 💾 |
| KSerialization | 0.445 · 141.3 · 202.6 | 0.433 · 144.9 · 263.9 | 0.291 · 216.0 · 141.2 |
| Moshi | 0.210 · 299.2 · 397.4 | 0.206 · 305.3 · 488.7 | 0.218 · 287.8 · 210.8 |

---

## Stress Tests

Latency-only micro-benchmarks on tiny payloads (GB/s is omitted — it would be misleading at this scale). Deep nesting: **single-shot** parse per engine (**632 B**). Malformed JSON: **average of 100** failed parses per engine (**2 581 B**). Allocation is not measured. **⏱️** = lowest latency.

| Test | Ghost | KSer | Moshi |
|:---|:---:|:---:|:---:|
| Deep Nesting — 20 levels | 171.7 | 112.2 | **107.8** ⏱️ |
| Malformed JSON — resilience | **42.1** ⏱️ | 68.5 | 64.6 |

---

## 👻 Ghost Special Features

| Feature | Throughput (GB/s) | Latency (µs/op) | Allocation (KB/op) |
|:---|:---:|:---:|:---:|
| Polymorphism — Sealed Class Dispatch | **0.090** | 1.27 | 0.293 |
| Structural Flattening — `@GhostFlatten` (3 levels deep) | **0.191** | 0.40 | 0.031 |
| Resilience — `@GhostResilient` (type mismatch recovery) | **0.031** | 2.07 | 0.817 |
| Custom Decoders — `@GhostDecoder` (hex + nullable transform) | **0.121** | 0.34 | 0.078 |
| Polymorphic Fallback — `@GhostFallback` (unknown discriminator) | **0.273** | 0.34 | 0.258 |
| Opaque JSON — RawJson field capture (slice, bytes) | **0.081** | 0.69 | 0.047 |
| Opaque JSON — RawJson.kind() on captured slice | — | **0.08** | 0 |
| Opaque JSON — RawJson.decodeAs&lt;T&gt;() second stage | — | **1.07** | 0.125 |
| JsonEnvelope — parsePayload (SSE fat envelope) | **0.008** | 10.08 | 32.242 |
| JsonEnvelope — parseTyped (cached serializer route) | **0.007** | 11.47 | 32.281 |

---

## 👻 RawJson Capture (bytes vs string channels)

| Scenario | Throughput (GB/s) | Latency (µs/op) | Allocation (KB/op) |
|:---|:---:|:---:|:---:|
| Decode `RawJson` field (bytes, small, slice capture) | **0.245** | 0.98 | **0.047** |
| Decode `RawJson` field (string, small, owned capture) | 0.200 | 1.19 | 0.523 |
| Decode `ByteArray` field (bytes, small, copy capture) | 0.477 | 0.50 | 0.273 |
| Decode `RawJson` field (bytes, large nested metadata) | **1.341** | 65.46 | **0.047** |
| Decode `RawJson` field (string, large nested metadata) | 0.892 | 98.46 | 171.523 |
| Decode `ByteArray` field (bytes, large nested metadata) | 1.229 | 71.45 | 85.750 |
| Encode `RawJson` payload (`encodeToBytes`, slice write) | **0.077** | 0.50 | 0.055 |
| Encode `RawJson` payload (`encodeToString`, UTF-8 decode) | 0.062 | 0.63 | 0.078 |
| Top-level `RawJson` decode (bytes) | 1.273 | 68.96 | 0.023 |
| Top-level `RawJson` decode (string) | 1.167 | 75.23 | 85.750 |
| Top-level `RawJson` round-trip (bytes in/out) | 1.221 | 71.87 | 85.750 |
| Top-level `RawJson` round-trip (string in/out) | 0.827 | 106.06 | 171.500 |

---

## 👻 YAML Round-Trip (Ghost-only)

Ghost-only suite — there is no KSER/Moshi YAML equivalent. Exercises KSP-generated `GhostYamlSerializer` on the integration `BenchUser` fixture via `decodeFromYaml` / `encodeToYaml`.

| Task | Profile | Regression gate |
|:---|:---|:---:|
| `benchmarkYaml` | full | informational only (`regressionGate = false`) |
| `benchmarkYamlFast` | fast | informational only |
| `benchmarkYamlRegression` / `benchmarkYamlRegressionFast` | wired into `benchmarkRegression(Fast)` | no ±10% gate yet |

```bash
./gradlew :ghost-benchmark:benchmarkYamlFast -PskipTests
```

---

← [Back to README](../../README.md)

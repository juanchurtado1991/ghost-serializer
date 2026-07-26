# Benchmark Results

[![Speed](https://img.shields.io/badge/Speed-red.png?style=flat&logo=speedtest&logoColor=white)](benchmarks.md)

> **Methodology**: Independent Gradle JVM tasks (`benchmarkTwitter`, `benchmarkSynthetic`, …). Engines: **Ghost, KSER, Gson, Jackson**. **10 000-iteration warmup**, **500 sessions × 50 batched samples**. Per session: **Ghost+KSER measured first** (back-to-back), then Gson/Jackson. Throughput tables report **decimal GB/s** (`payload_bytes / seconds / 10⁹`, equivalent to `ops/s × payload / 10⁹`). Regression uses **median** of per-session Ghost÷KSER ratios. LIST / SYNC / WRITING suites isolated with phase GC only.
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
| **Decode (String)** | **👻 Ghost** | **1.219** 🏆 *(+71.0%)* | **518.2** ⏱️ | **361.2** 💾 *(-73.0%)* |
| | KSER | 0.713 | 886.1 | 1337.6 |
| **Decode (Bytes)** | **👻 Ghost** | **1.011** 🏆 *(+144.9%)* | **624.4** ⏱️ | **621.2** 💾 *(-85.5%)* |
| | KSER | 0.413 | 1529.0 | 4297.0 |
| **Decode (Streaming)** | **👻 Ghost** | **0.525** 🏆 *(+176.8%)* | **1203.7** ⏱️ | **1268.7** 💾 *(-33.4%)* |
| | KSER | 0.190 | 3331.6 | 1904.9 |
| **Encode (String)** | **👻 Ghost** | **2.824** 🏆 *(+55.8%)* | **223.6** ⏱️ | 1074.3 |
| | KSER | 1.812 | 348.5 | **972.1** 💾 |
| **Encode (Bytes)** | **👻 Ghost** | **1.479** 🏆 *(+90.9%)* | **427.0** ⏱️ | **420.2** 💾 *(-81.0%)* |
| | KSER | 0.775 | 815.3 | 2206.8 |
| **Encode (Streaming)** | **👻 Ghost** | **1.468** 🏆 *(+61.9%)* | **430.1** ⏱️ | **426.9** 💾 *(-6.2%)* |
| | KSER | 0.907 | 696.3 | 455.0 |

---

## Multi-engine tables

Fixed row order: **Ghost → KSER → Gson → Jackson**. Each mode reads **GB/s → µs/op → KB/op**. Throughput is decimal GB/s (`payload_bytes / seconds / 10⁹`). **🏆** = highest GB/s · **⏱️** = lowest latency · **💾** = leanest.

Payload sizes used for the conversion: LIST_MEDIUM **22 022 B**, SYNC_FULL_LARGE **187 822 B**, WRITING **94 822 B**.

## Deserialization — 200 objects (LIST_MEDIUM)

| Engine | String<br>GB/s · µs · KB/op | Bytes<br>GB/s · µs · KB/op | Streaming<br>GB/s · µs · KB/op |
|:---|:---:|:---:|:---:|
| **👻 Ghost** | **0.275** 🏆 · **80.0** ⏱️ · **157.7** 💾 | **0.489** 🏆 · **45.0** ⏱️ · **24.8** 💾 | **0.479** 🏆 · **46.0** ⏱️ · **24.8** 💾 |
| KSerialization | 0.229 · 96.0 · 189.7 | 0.232 · 95.0 · 189.7 | 0.135 · 163.0 · 189.7 |
| Gson | 0.239 · 92.0 · 164.0 | 0.239 · 92.0 · 164.0 | 0.234 · 94.0 · 173.5 |
| Jackson | 0.154 · 143.0 · 631.7 | 0.162 · 136.0 · 631.8 | 0.161 · 137.0 · 631.9 |

---

## Deserialization — 2000 objects (SYNC_FULL_LARGE)

| Engine | String<br>GB/s · µs · KB/op | Bytes<br>GB/s · µs · KB/op | Streaming<br>GB/s · µs · KB/op |
|:---|:---:|:---:|:---:|
| **👻 Ghost** | 0.287 · 654.0 · **1173.7** 💾 | **0.508** 🏆 · **370.0** ⏱️ · **213.4** 💾 | **0.492** 🏆 · **382.0** ⏱️ · **334.2** 💾 |
| KSerialization | 0.245 · 767.0 · 1836.6 | 0.244 · 769.0 · 1836.6 | 0.133 · 1413.0 · 1957.5 |
| Gson | **0.292** 🏆 · **644.0** ⏱️ · 1343.8 | 0.292 · 644.0 · 1343.8 | 0.288 · 652.0 · 1366.7 |
| Jackson | 0.136 · 1380.0 · 6210.0 | 0.155 · 1209.0 · 6210.1 | 0.154 · 1217.0 · 6210.1 |

---

## Serialization — 1000 objects (WRITING)

| Engine | String<br>GB/s · µs · KB/op | Bytes<br>GB/s · µs · KB/op | Streaming<br>GB/s · µs · KB/op |
|:---|:---:|:---:|:---:|
| **👻 Ghost** | 0.797 · 119.0 · **185.3** 💾 | **1.103** 🏆 · **86.0** ⏱️ · **92.6** 💾 | **1.129** 🏆 · **84.0** ⏱️ · **32.2** 💾 |
| KSerialization | **0.847** 🏆 · **112.0** ⏱️ · 264.9 | 0.817 · 116.0 · 326.3 | 0.452 · 210.0 · 203.5 |
| Gson | 0.269 · 353.0 · 731.0 | 0.264 · 359.0 · 823.6 | 0.101 · 941.0 · 3996.8 |
| Jackson | 0.472 · 201.0 · 458.6 | 0.564 · 168.0 · 312.1 | 0.600 · 158.0 · 123.6 |

---

## Stress Tests

Fixed column order: **Ghost → Gson → KSER → Jackson**. Each cell reads **GB/s · µs/op** (Deep Nesting payload **632 B**, Malformed **2 901 B**). **🏆** = highest GB/s · **⏱️** = lowest latency.

| Test | Ghost | Gson | KSer | Jackson |
|:---|:---:|:---:|:---:|:---:|
| Deep Nesting — 20 levels | **0.0035 · 183.0** 🏆⏱️ | 0.0015 · 410.0 | 0.0014 · 458.0 | 0.0001 · 5309.0 |
| Malformed JSON — resilience | 0.085 · 34.0 | **0.116 · 25.0** 🏆⏱️ | 0.038 · 77.0 | 0.033 · 87.0 |

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

← [Back to README](../../README.md)

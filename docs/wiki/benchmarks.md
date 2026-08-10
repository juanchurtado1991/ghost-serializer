# Benchmark Results

[![Speed](https://img.shields.io/badge/Speed-red.png?style=flat&logo=speedtest&logoColor=white)](benchmarks.md)

> **Methodology**: Independent Gradle JVM tasks (`benchmarkTwitter`, `benchmarkSynthetic`, …). Engines: **Ghost, KSER, Moshi (codegen adapters)**. **10 000-iteration warmup**, **500 sessions × 50 batched samples** on LIST / SYNC / WRITING. Per session: **Ghost+KSER measured first** (back-to-back), then Moshi. Throughput tables report **decimal GB/s** (`payload_bytes / seconds / 10⁹`, equivalent to `ops/s × payload / 10⁹`). Stress tests report **latency only** (single-shot or 100-iteration avg). Regression uses **median** of per-session Ghost÷KSER ratios. LIST / SYNC / WRITING suites isolated with phase GC only.
>
> **`ghost.textChannel`**: default **true** per model. String benchmarks use the generated `GhostJsonStringReader` / string writer; byte and streaming benchmarks use their dedicated readers. Byte-only applications can opt out with `@GhostSerialization(textChannel = false)` or the module flag `ghost.textChannel=false` to reduce generated code size. See [Native String Reader](advanced-features.md#5-native-string-reader-textchannel).
>
> **Wasm / playground:** Wasm uses scalar (non-SWAR) string/whitespace scans so Safari/WebKit stays competitive with Chrome ([#16](https://github.com/juanchurtado1991/ghost-serializer/issues/16)). JVM regression gates below remain the production performance source of truth.

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
./gradlew :ghost-benchmark:benchmarkSpecial -PskipTests    # JSON features + proto WKT micro-bench
./gradlew :ghost-benchmark:benchmarkRawJson -PskipTests
./gradlew :ghost-benchmark:benchmarkYaml -PskipTests       # YAML parser/writer (full profile)
./gradlew :ghost-benchmark:benchmarkYamlFast -PskipTests
./gradlew :ghost-benchmark:benchmarkProto -PskipTests      # Proto3 JSON via GhostProto (full profile)
./gradlew :ghost-benchmark:benchmarkProtoFast -PskipTests

# YAML spec-compliance reports (not perf benchmarks — see below), fully offline
./gradlew :ghost-serialization:yamlComplianceMatrix
./gradlew :ghost-serialization:yamlWriterComplianceMatrix

# Full README suite (cold start + all tables)
./gradlew :ghost-benchmark:run -PskipTests
./gradlew :ghost-benchmark:run -Pjit -PskipTests  # JIT log for JITWatch
```

Exit code `1` = regression beyond ±10% tolerance vs baseline (twitter / synthetic tasks only).

| Task | Wall-clock | Regression gate |
|------|------------|-----------------|
| `benchmarkTwitter` / `benchmarkTwitterFast` | ~3 min / ~30s | ✅ 6 categories |
| `benchmarkSynthetic` / `benchmarkSyntheticFast` | ~6 min / ~60s | ✅ 9 categories |
| `benchmarkRegression` / `benchmarkRegressionFast` | ~9 min / ~1–2 min | ✅ JSON only (15 categories) + yaml/proto informational |
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

Ghost-only suite — there is no KSER/Moshi YAML equivalent. Exercises KSP-generated `GhostYamlSerializer` on the integration [`YamlBenchUser`](../../ghost-integration-test/src/main/kotlin/com/ghost/serialization/integration/model/YamlBenchUser.kt) fixture via `Ghost.decodeFromYaml` / `encodeToYaml` / `encodeToYamlBytes`.

**Fixture:** block-style YAML document (**90 B** full profile, **49 B** minimal round-trip). Parser: `GhostYamlFlatReader` · Writer: `GhostYamlFlatWriter`.

| Task | Profile | Regression gate |
|:---|:---|:---:|
| `benchmarkYaml` | full | informational only (`regressionGate = false`) |
| `benchmarkYamlFast` | fast | informational only |
| `benchmarkYamlRegression` / `benchmarkYamlRegressionFast` | aliases | wired into `benchmarkRegression(Fast)` |

```bash
./gradlew :ghost-benchmark:benchmarkYaml -PskipTests
./gradlew :ghost-benchmark:benchmarkYamlFast -PskipTests
```

**Full profile** (5000 measurement runs, Linux JVM 17, July 2026). Fixtures are **~90 B** — at this scale **µs/op** is the meaningful metric; GB/s is shown for completeness but is dominated by payload size (a 1 µs parse of 90 B is only ~0.09 GB/s even if the parser is fast).

| Operation | Latency (µs/op) | Allocation (KB/op) | Throughput (GB/s) |
|:---|---:|---:|---:|
| Decode (`decodeFromYaml` bytes) | **1.27** ⏱️ | **1.211** 💾 | 0.071 |
| Encode (`encodeToYamlBytes`) | **0.65** | **0.773** | 0.138 |
| Encode (`encodeToYaml` string) | 1.29 | 0.837 | 0.070 |
| Decode (`decodeFromYaml` string) | 2.59 | 1.321 | 0.035 |
| Round-trip (minimal fixture, 49 B) | 1.78 | 1.852 | 0.027 |

Prefer **`ByteArray`** inputs when your client already exposes bytes — avoids an intermediate `String` and matches the flat reader hot path.

---

## 👻 YAML Spec Compliance (Ghost-only)

Not a performance benchmark — a **spec-conformance report** against the vendored
[yaml-test-suite](https://github.com/yaml/yaml-test-suite) snapshot (`ghost-serialization/src/jvmTest/resources/yaml-test-suite`,
pinned to commit `6ad3d2c6`, same tree as the official `data-2022-01-17` tag — see that directory's `README.md`). Runs entirely
offline; every case is a local resource, so the report is fully reproducible by anyone who clones the repo.

The **compliance %** is `value-pass / 279` — 279 is the count of valid (non-error) cases that ship an `in.json` fixture, the
same denominator [matrix.yaml.info](https://matrix.yaml.info)'s own "json" column uses, so this number is directly comparable
to that table.

| Task | What it does |
|:---|:---|
| `yamlComplianceMatrix` | Prints the report below; exits non-zero if any case falls outside the tracked deviations |

```bash
./gradlew :ghost-serialization:yamlComplianceMatrix
```

```
==============================================================================
Ghost YAML Spec Compliance — vendored yaml-test-suite snapshot
==============================================================================
Cases loaded: 402  (279 valid, with in.json — the matrix.yaml.info-comparable denominator)

Outcome (parses/rejects as the spec expects):
  371 pass, 31 known gap(s), 0 UNEXPECTED
Value (decoded tree matches in.json, of 275 checked):
  268 pass, 7 known gap(s), 0 UNEXPECTED

Compliance = value-pass / 279 valid cases:
  268 / 279 = 96.06%

Known gaps by category:
  TAB                      9 case(s)
  INDENTATION              9 case(s)
  MISC                     5 case(s)
  BLOCK_SCALAR             4 case(s)
  ANCHOR_ALIAS             3 case(s)
  FLOW_COLLECTION          3 case(s)
  COMMENT                  2 case(s)
  TAG                      1 case(s)
  EXPLICIT_KEY             1 case(s)
  EMPTY_MISSING            1 case(s)
==============================================================================
No unexpected deviations — every known gap is tracked in YamlTestSuiteDeviations.kt
```

Every known gap is tracked by case ID with a category and reason in
[`YamlTestSuiteDeviations.kt`](../../ghost-serialization/src/jvmTest/kotlin/com/ghost/serialization/yaml/testsuite/YamlTestSuiteDeviations.kt) —
nothing here is silently skipped. `GhostYamlTestSuiteConformanceTest` (the JUnit source of truth this report reads) fails the
build if a tracked case ever regresses or a stale entry survives a snapshot refresh, so the two can't quietly drift apart.

---

## 👻 YAML Writer Conformance (Ghost-only)

The reader-side report above says nothing about the **writer** (`GhostYamlFlatWriter`). This report runs every
vendored yaml-test-suite case the reader can decode through `decode -> encode -> decode` and checks two things:
does it reproduce the original tree (**round-trip**), and does a second, independent parser
([kaml](https://github.com/charleskorn/kaml)) accept Ghost's own re-encoded output (**kaml oracle**)? Same offline,
reproducible-by-anyone-who-clones-the-repo shape as the reader report.

| Task | What it does |
|:---|:---|
| `yamlWriterComplianceMatrix` | Prints the report below; exits non-zero if any case falls outside the tracked deviations |

```bash
./gradlew :ghost-serialization:yamlWriterComplianceMatrix
```

```
==============================================================================
Ghost YAML Writer Conformance — vendored yaml-test-suite snapshot
==============================================================================
Cases loaded: 321 reader-decodable (out of 402 total)

Round-trip (decode -> encode -> decode reproduces the original tree):
  321 pass, 0 known gap(s), 0 UNEXPECTED
  321 / 321 = 100.00%

kaml oracle (an independent second parser accepts Ghost's re-encoded output):
  309 pass, 12 known gap(s), 0 UNEXPECTED
  309 / 321 = 96.26%

Known gaps by category:
  KAML_COMPLEX_KEY_LIMITATION   12 case(s)
==============================================================================
No unexpected deviations — every known gap is tracked in YamlWriterDeviations.kt
```

`name(key: String)` used to write mapping keys as bare, unquoted plain-scalar text (unlike `value()`, which always
double-quotes) — a key containing structurally-significant bytes (`": "`, an embedded newline/tab, a leading
anchor/tag/alias/quote sigil, or a leading `[`/`{` from a stringified complex key) could round-trip to a different
structure, or fail to re-parse at all. Fixed via a fast, single-pass `keyNeedsQuoting` check that quotes only when
actually necessary (an ordinary key like `userId` stays bare) — round-trip is now 100%. `KAML_COMPLEX_KEY_LIMITATION`
is **not** a Ghost bug — kaml's own parser rejects any mapping key that isn't a simple scalar (flow-collection-shaped
or bare/empty implicit keys), a kotlinx.serialization-property-name design choice on kaml's side, not a YAML spec
violation; every one of those cases round-trips correctly through Ghost's own reader. Full detail, case-by-case, in
[`YamlWriterDeviations.kt`](../../ghost-serialization/src/jvmTest/kotlin/com/ghost/serialization/yaml/testsuite/YamlWriterDeviations.kt).

---

## 👻 Proto3 JSON Round-Trip (Ghost-only)

Ghost-only suite — there is no KSER/Moshi proto3-JSON equivalent. Exercises KSP-generated `@GhostProtoSerialization` serializers through **`GhostProto`** (`GhostProtoJsonFlatReader` on decode; proto quoting / default omission on encode) on [`ProtoBenchUser`](../../ghost-integration-test/src/main/kotlin/com/ghost/serialization/integration/model/ProtoBenchUser.kt).

**Fixture:** proto3 JSON with quoted `userId`, omitted default fields (**110 B** full profile, **65 B** minimal round-trip).

| Task | Profile | Regression gate |
|:---|:---|:---:|
| `benchmarkProto` | full | informational only |
| `benchmarkProtoFast` | fast | informational only |
| `benchmarkProtoRegression` / `benchmarkProtoRegressionFast` | aliases | wired into `benchmarkRegression(Fast)` |

```bash
./gradlew :ghost-benchmark:benchmarkProto -PskipTests
./gradlew :ghost-benchmark:benchmarkProtoFast -PskipTests
```

**Full profile** (5000 measurement runs, Linux JVM 17, July 2026). Fixtures are **~110 B** — use **µs/op** (and allocation) to judge parser/writer cost; GB/s is a secondary column and looks artificially low on tiny proto3 JSON documents.

| Operation | Latency (µs/op) | Allocation (KB/op) | Throughput (GB/s) |
|:---|---:|---:|---:|
| Decode (`GhostProto.deserialize` string) | **0.95** ⏱️ | 0.188 | 0.116 |
| Round-trip (minimal fixture, 65 B) | 1.04 | 0.469 | 0.063 |
| Decode (`GhostProto.deserialize` bytes) | 1.26 | **0.063** 💾 | 0.088 |
| Encode (`GhostProto.encodeToString`) | 1.03 | 0.352 | 0.107 |
| Encode (`GhostProto.encodeToBytes`) | 1.20 | 0.203 | 0.092 |

> **Reader pooling (1.3.0+)**  
> `GhostProto.deserialize(bytes)` and HTTP adapters now reuse a **`ThreadLocal` pooled `GhostProtoJsonFlatReader`** via `ghostProtoInternalUseFlatReader` — same pattern as `Ghost.deserialize(bytes)` and `Ghost.decodeFromYaml(bytes)`. The `String` overload still UTF-8-encodes per call; prefer **`ByteArray`** on hot paths.

**Well-Known Types** (`ProtoDuration`, `ProtoTimestamp`, `ProtoStruct`, …) are covered separately in `benchmarkSpecial` (micro-benchmarks, not the generated-model round-trip above).

---

← [Back to README](../../README.md)

# ⚡ Benchmark Results

> **Methodology**: Single JVM process. 20,000-iteration JIT warmup. 20,000 measured runs. Results are statistical averages ± standard deviation. Memory is measured via `ThreadMXBean.getThreadAllocatedBytes` (heap bytes allocated per call, not retained). Tested on JVM HotSpot with Kotlin 2.1.10 / KSP2.

## Running the Benchmark Yourself

```bash
# Full run: executes ./gradlew ciTest first, then the benchmark
./gradlew :ghost-benchmark:run --args="--runs 20000 --warmup 20000 --no-tests"

# Skip tests, benchmark only
./gradlew :ghost-benchmark:run -PskipTests --args="--runs 20000 --warmup 20000 --no-tests"

# With JIT compilation log for JITWatch analysis
./gradlew :ghost-benchmark:run -Pjit -PskipTests --args="--twitter-only --runs 20000 --warmup 20000"
```

> ⏱️ **Note on execution time:** The full benchmark suite with the recommended configuration takes approximately **15–18 minutes** to complete.

---

## 🐦 Twitter Macro Dataset

Results on the [twitter_macro.json](../../ghost-benchmark/src/main/resources/twitter_macro.json) dataset — a real-world payload with deeply nested objects and long string fields.

> **Note on Decode (String):** Ghost parses `String` inputs natively via `GhostJsonStringReader` (enabled with `ghost.textChannel=true`), bypassing `encodeToByteArray` entirely, allocating **3.3× less heap memory** compared to KSER on String inputs.

| Operation | Engine | Throughput (ops/s) | Mem (KB/op) |
| :--- | :---: | :---: | :---: |
| **Decode (String)** | **👻 Ghost** | **1465.5** *(+31.0% faster)* | **406.8** *(-69.6% memory)* |
| | KSER | 1118.7 | 1337.6 |
| **Decode (Bytes)** | **👻 Ghost** | **1174.1** *(+72.2% faster)* | **671.7** *(-84.4% memory)* |
| | KSER | 681.8 | 4297.0 |
| **Decode (Streaming)** | **👻 Ghost** | **512.5** *(+68.7% faster)* | **1320.1** *(-30.7% memory)* |
| | KSER | 303.8 | 1904.8 |
| **Encode (String)** | **👻 Ghost** | **4288.6** *(+48.8% faster)* | 1074.3 |
| | KSER | 2882.9 | **972.1** |
| **Encode (Bytes)** | **👻 Ghost** | **2292.1** *(+91.6% faster)* | **420.2** *(-81.0% memory)* |
| | KSER | 1196.1 | 2206.8 |
| **Encode (Streaming)** | **👻 Ghost** | **2289.2** *(+50.2% faster)* | **426.9** *(-6.2% memory)* |
| | KSER | 1524.1 | 455.0 |

---

## 📋 Deserialization — 200 objects (LIST_MEDIUM)

| Engine | String (ms) | MEM (KB) | Bytes (ms) | MEM (KB) | Streaming (ms) | MEM (KB) |
|:---|:---:|:---:|:---:|:---:|:---:|:---:|
| **👻 Ghost** | **0.089 ±0.006** | **63.5** | **0.046 ±0.008** | **29.8** | **0.047 ±0.009** | **24.8** |
| Gson | 0.092 ±0.011 | 164.0 | 0.092 ±0.011 | 164.0 | 0.094 ±0.010 | 173.5 |
| KSerialization | 0.104 ±0.006 | 194.4 | 0.104 ±0.006 | 194.4 | 0.168 ±0.018 | 194.5 |
| Moshi | 0.162 ±0.025 | 319.7 | 0.162 ±0.025 | 319.7 | 0.155 ±0.024 | 329.2 |
| Jackson | 0.219 ±0.031 | 696.0 | 0.219 ±0.031 | 696.0 | 0.234 ±0.035 | 705.5 |

---

## 📋 Deserialization — 2000 objects (SYNC_FULL_LARGE)

| Engine | String (ms) | MEM (KB) | Bytes (ms) | MEM (KB) | Streaming (ms) | MEM (KB) |
|:---|:---:|:---:|:---:|:---:|:---:|:---:|
| **👻 Ghost** | **0.675 ±0.042** | **431.8** | **0.374 ±0.023** | **207.5** | **0.395 ±0.038** | **334.2** |
| Gson | 0.603 ±0.056 | 1343.8 | 0.600 ±0.051 | 1343.8 | 0.609 ±0.050 | 1366.6 |
| KSerialization | 0.746 ±0.063 | 1836.6 | 0.746 ±0.062 | 1836.6 | 1.340 ±0.085 | 1957.5 |
| Moshi | 1.251 ±0.107 | 3131.4 | 1.247 ±0.106 | 3131.4 | 1.150 ±0.107 | 3131.4 |
| Jackson | 2.246 ±0.144 | 6944.6 | 2.145 ±0.147 | 6944.6 | 2.159 ±0.153 | 6944.6 |

---

## ✏️ Serialization — 1000 objects (WRITING)

| Engine | String (ms) | MEM (KB) | Bytes (ms) | MEM (KB) | Streaming (ms) | MEM (KB) |
|:---|:---:|:---:|:---:|:---:|:---:|:---:|
| **👻 Ghost** | **0.076 ±0.012** | **100.4** | **0.080 ±0.015** | **92.7** | **0.081 ±0.016** | **96.7** |
| KSerialization | 0.123 ±0.010 | 202.6 | 0.125 ±0.029 | 263.9 | 0.211 ±0.020 | 205.6 |
| Jackson | 0.189 ±0.024 | 396.2 | 0.151 ±0.019 | 249.7 | 0.150 ±0.014 | 303.5 |
| Gson | 0.330 ±0.028 | 551.3 | 0.329 ±0.033 | 643.9 | 0.756 ±0.084 | 3908.5 |
| Moshi | 0.389 ±0.034 | 630.7 | 0.389 ±0.033 | 723.3 | 0.377 ±0.035 | 445.5 |

---

## 💀 Stress Tests

| Test | Ghost | Gson | KSer | Moshi | Jackson |
|:---|:---:|:---:|:---:|:---:|:---:|
| Deep Nesting — 20 levels (ms) | **0.003 ±0.002** | 0.006 | 0.005 | 0.007 | 0.010 |
| Malformed JSON — resilience (ms) | **0.007 ±0.001** | 0.014 | 0.017 | 0.022 | 0.032 |

---

## 👻 Ghost Special Features

These features have **no equivalent** in Gson, Moshi, KSerialization, or Jackson.

| Feature | µs/op | B/op |
|:---|:---:|:---:|
| Polymorphism — Sealed Class Dispatch | **0.55** | 300 |
| Structural Flattening — `@GhostFlatten` (3 levels deep) | **0.31** | 32 |
| Resilience — `@GhostResilient` (type mismatch recovery) | **2.64** | 10612 |
| Custom Decoders — `@GhostDecoder` (hex + nullable transform) | **1.36** | 16840 |
| Polymorphic Fallback — `@GhostFallback` (unknown discriminator) | **0.23** | 264 |

> [!TIP]
> **Unified Validation**: The benchmark suite is designed to fail if any integration test doesn't pass. This ensures that performance results always reflect a stable and correct codebase.

---

← [Back to README](../../README.md)

# Safari / Wasm Speed Test — Mac handoff

**Audience:** LLM or human continuing [#16](https://github.com/juanchurtado1991/ghost-serializer/issues/16) on a **Mac with Safari** (JavaScriptCore). Ubuntu cannot validate Safari rankings.

**Repo:** `ghost-serializer` · branch usually `main`  
**Related chat (Ubuntu work):** search agent transcripts for “Safari SWAR” / issue `#16`.

---

## Goal

Measure whether Ghost’s Wasm **byte-channel** Speed Test (with **SWAR on**) is competitive with kotlinx.serialization on **Safari**, and decide if SWAR should stay on, be gated off only on JSC, or needs a different fix.

Do **not** reintroduce a playground SWAR on/off UI unless Mac data proves an A/B toggle is still needed.

---

## Current code state (what Ubuntu left)

| Piece | State |
|:---|:---|
| `ghostUseSwarScans` | `internal expect val` — **`true` on JVM, Android, Native, and Wasm** |
| Public `Ghost.useSwarScans` / mutable toggle | **Removed** (do not bring back unless Mac needs A/B) |
| Playground Speed Test — Ghost | **`Ghost.deserialize(utf8)` + `Ghost.encodeToBytes`** (flat / SWAR path) |
| Playground Speed Test — KSER / Moshi | Still **String** over the same UTF-8 payload |
| String-channel Ghost (`deserialize(String)`) | **Unaffected** by `ghostUseSwarScans` |

Key files:

- `ghost-serialization/.../parser/bytes/GhostSwar.wasmJs.kt` — `ghostUseSwarScans = true`
- `ghost-playground/.../bench/SpeedTestEngine.kt` — `SpeedTestPayload` + byte Ghost round-trip
- Gate sites: `GhostJsonFlatReader`, `GhostJsonFlatReaderSubsystem`, `GhostSourceCommon`, streaming readers

---

## Why previous “fix” looked like a no-op

1. Safari Speed Test scores (~**782 → 772**) with KSER still ~**1.3×** ahead after forcing Wasm SWAR **off**.
2. Root cause of wrong experiment: Speed Test used **`Ghost.deserialize(String)` / `encodeToString`**, so `ghostUseSwarScans` never ran on the measured path.
3. On Wasm, the “Moshi” phase is still kotlinx.serialization again (no real Moshi codegen).

That is why Ghost was switched to the **byte** channel and SWAR left **on by default** for Mac measurement.

---

## What to run on the Mac

### 1. Build & open playground (Wasm) in Safari

```bash
./gradlew :ghost-playground:wasmJsBrowserDevelopmentRun
```

Open the printed localhost URL in **Safari** (not Chrome). Use the **Speed test** tab. Wait for warmup + three ~15s phases (~48s total).

Record for each run:

- Ghost / KSER / Moshi **ops/sec** (and winner × from the result card)
- Safari version + macOS version
- Whether this is cold tab vs warmed tab (note it)

Optional compare: same build in **Chrome** on the same Mac (V8 vs JSC).

### 2. Unit / smoke (optional)

```bash
./gradlew :ghost-serialization:wasmJsBrowserTest
./gradlew :ghost-playground:jvmTest --tests 'com.ghost.playground.bench.SpeedTestEngineJvmTest'
```

### 3. If SWAR-on still loses badly on Safari

Only then consider an A/B:

1. Temporarily set `ghostUseSwarScans = false` in `GhostSwar.wasmJs.kt`, rebuild, re-run Safari Speed Test (still **byte** Ghost).
2. Or restore a short-lived mutable flag + UI — **not** the preferred first step.

Document numbers in [#16] before changing the default again.

---

## Decision guide

| Safari result (byte Ghost, SWAR on) | Action |
|:---|:---|
| Ghost competitive / ahead of KSER | Keep SWAR on; close or update #16 as “string-channel mis-measure; byte path OK” |
| Ghost still ~1.3× behind, SWAR off clearly faster | Set Wasm `ghostUseSwarScans = false` again; update CHANGELOG/README; note string path is separate |
| Both on/off lose | Look beyond SWAR (allocs, UTF-8, prediction, Wasm codegen) |

---

## Explicit non-goals

- Do not treat **fast** JVM bench (±20%) as Safari truth — full JVM bench is release SoT; Safari is browser Speed Test.
- Do not “fix” Safari by only changing the string channel.
- Do not commit Cursor `Co-authored-by` trailers if hooks add them.

---

## After Mac results

1. Paste ops/sec + Safari/macOS into issue **#16**.
2. Update `README.md` Wasm/Safari blurb and `CHANGELOG` Fixed/#16 note to match the decision.
3. If docs still say “SWAR off on Wasm” anywhere stale, sync them.

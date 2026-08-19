# Public Roadmap

[![Roadmap](https://img.shields.io/badge/Roadmap-purple.png?style=flat&logo=target&logoColor=white)](roadmap.md)

This page tracks **planned work** for Ghost Serializer. It is not a release commitment — priorities may shift based on community feedback and real-world adoption.

**Product principle:** Ghost is an **optimization layer** for hot DTOs. It coexists with kotlinx.serialization, Moshi, Gson, and Jackson — it does not aim to replace them app-wide.

Track progress in [GitHub Issues](https://github.com/juanchurtado1991/ghost-serializer/issues) and [Pull Requests](https://github.com/juanchurtado1991/ghost-serializer/pulls).

---

## Shipped in 1.3.0 (baseline)

- Kotlin Multiplatform: Android, iOS, JVM, **wasmJs**
- Framework adapters: Ktor, Retrofit, Spring Boot; **YAML** + Proto3 JSON in unified runtime
- Maven Central, reproducible benchmarks, [Ghost Playground](https://juanchurtado1991.github.io/ghost-serializer/), wiki docs
- `textChannel = true` by default; CI regression gates for benchmarks

---

## 1. OpenAPI → Ghost DTO codegen

**Goal:** Lower the cost of adopting Ghost in teams that already maintain an API contract (OpenAPI / Swagger), without replacing manual tuning for advanced Ghost features.

### Scope

| Phase | Deliverable | Status |
|:---|:---|:---:|
| **1a — Plain stubs** | Gradle task or CLI: OpenAPI (YAML/JSON) → Kotlin `data class` + `@GhostSerialization`, `@GhostName` when wire names differ | Planned |
| **1b — Schema mapping** | Map OpenAPI types to Ghost-supported types (`List`, `Map<String, V>`, sealed + `discriminator`, nullable/required) | Planned |
| **1c — Ghost extensions** | Optional `x-ghost-*` OpenAPI extensions for features that cannot be inferred (e.g. `x-ghost-resilient`, `x-ghost-flatten`) — documented convention | Planned |
| **1d — Sample JSON shortcut** | Paste example JSON → stub DTO (same output as 1a, for quick prototyping without a full OpenAPI file) | Planned |

### What codegen will **not** auto-decide

These stay **manual** (or opt-in via `x-ghost-*` extensions):

- `@GhostResilient`, `@GhostFallback`, `@GhostFlatten`, `@GhostWrap`
- `RawJson`, `@GhostJsonEnvelope`, custom `@GhostEncoder` / `@GhostDecoder`
- `@GhostProtoSerialization` vs plain `@GhostSerialization`
- Which DTOs are “hot path” vs left on kotlinx.serialization / Moshi

### Success criteria

- Generate a multi-model OpenAPI spec into compilable `commonMain` stubs in one command.
- Generated code passes `./gradlew ciTestJvm` when wired to existing integration tests.
- Wiki guide: *OpenAPI adoption* — workflow, extension reference, coexistence with other serializers.

---

## 2. Stronger tests + README badges

**Goal:** Make quality visible at a glance and close coverage gaps on adapters and edge paths.

### Badges (README)

| Badge | Purpose | Status |
|:---|:---|:---:|
| **CI** | Link to [GitHub Actions](https://github.com/juanchurtado1991/ghost-serializer/actions/workflows/ci.yml) — green/red signal on every push | Shipped |
| **Coverage** | Link to the [published Kover report](https://juanchurtado1991.github.io/ghost-serializer/coverage/) with line/branch % from the latest `main` build | Shipped (static "see report" badge) |

Both now live at the top of `README.md`. The Coverage badge is the static "see report" version — a dynamic %-badge (shields.io endpoint or a committed `coverage-badge.svg` updated by CI) is still a possible follow-up, not started.

### Test improvements

| Area | Work | Status |
|:---|:---|:---:|
| **Adapter gaps** | Retrofit/Ktor proto/YAML converters: `List<T>` / `Map` body unwrapping | Shipped in 1.3.0 |
| **Wasm / Native** | Documenting what Kover cannot measure is done (see [Contributing — Kover limits](contributing.md#verification-commands)). Safari string encode cliff: **done** — JSC uses UTF-8 + `TextDecoder` for `encodeToString`; Chrome keeps char writer (~2.7× KSER on Safari Speed Test) ([#16](https://github.com/juanchurtado1991/ghost-serializer/issues/16)). Expanding `wasmJsBrowserTest` still open — only `WasmPlatformActualsTest` exists today; no test exercises a real Ghost JSON/YAML round-trip on the actual Wasm target | Ongoing |
| **Regression visibility** | `RegressionCalculator.report` writes a JSON snapshot to `ghost-benchmark/build/reports/regression/regression-report.json` on every run (see [Contributing — Kover limits](contributing.md#verification-commands)). Deliberately local/pre-PR tooling only, not a CI artifact — regression checks stay opt-in (not a CI gate) since a full run takes 1–9 minutes | Shipped |
| **Fuzz / malformed JSON** | Every JSON/YAML reader and writer implementation (flat, Okio-streaming, string channel) plus typed decode (`GhostComplexObjectFuzzTest`) and proto3-JSON-specific decoders now have Jazzer fuzz coverage; found and fixed 3 real bugs (YAML key quoting on both writers, `GhostJsonStringReader` unicode-escape crash) and a gap where `.cifuzz-corpus` seeds were never replayed in CI | Shipped |
| **Playground** | Keep Speed Test + Studio presets aligned with real generated serializers on JVM | Ongoing |

### Success criteria

- README shows **CI** and **Coverage** badges above the fold.
- No module in `ciTestJvmModules` without a clear owner and at least smoke-level tests for public API.
- Coverage report on Pages updates automatically on every `main` push (already in CI; badge makes it discoverable).

---

## 3. Format & adapter gaps

Items intentionally deferred (parity across YAML and Proto3 JSON adapters):

| Gap | Notes | Status |
|:---|:---|:---:|
| **`List` / `Set` / `Map` HTTP bodies** | Top-level collection unwrap in Retrofit, Spring (MVC + WebFlux), and Ktor for JSON / YAML / Proto adapters. | Shipped (1.3.1) |
| **Binary protobuf wire** | Varint-encoded gRPC/protobuf binary — Ghost implements proto3 **JSON** mapping only (plus YAML documents for config/API). | Planned |
| **JSON-only structural features on YAML** | `@GhostResilient`, `@GhostFlatten`, sealed/`@GhostFallback`, `@GhostDecoder`/`@GhostEncoder` — compile-time blocked on `@GhostYamlSerialization`; no runtime fallback. | By design |

See also [YAML usage §7](usage-yaml.md#7-known-gaps-not-yet-implemented) and [Protobuf usage §8](usage-protobuf.md#8-known-gaps-not-yet-implemented).

---

## Non-goals

- Replacing kotlinx.serialization (or Moshi/Jackson) across an entire codebase
- Full parity with every Jackson/Gson feature
- Untyped `Map<String, Any>` trees — use `RawJson` or typed models
- Non-`String` map keys — reshape the model or use a custom serializer

---

## How to influence the roadmap

1. **+1 or comment** on an existing GitHub issue for the item you care about.
2. Open a **feature request** with your use case (OpenAPI sample, adapter, platform).
3. Send a **PR** — see [Contributing](contributing.md).

---

← [Back to README](../../README.md) | [Modules](modules.md) | [Benchmarks](benchmarks.md)

# Contributing to Ghost Serializer

[![Community](https://img.shields.io/badge/Community-blue.png?style=flat&logo=git&logoColor=white)](contributing.md)

Thank you for helping improve Ghost!

---

## Development Environment

| Tool | Version / Notes |
|:---|:---|
| **JDK** | **17** — required for all modules |
| **Gradle wrapper** | `./gradlew` — do not rely on a system Gradle installation |
| **Android SDK** | Needed for `:ghost-serialization:testDebugUnitTest` (compile SDK 36) |
| **Xcode** *(macOS only)* | Needed for `iosSimulatorArm64Test` |

```bash
git clone https://github.com/juanchurtado1991/GhostSerialization.git
cd GhostSerialization
./gradlew allTests          # full suite before a PR
./gradlew :ghost-serialization:compileKotlinJvm
```

---

## Verification Commands

| Command | What it runs |
|:---|:---|
| `./gradlew allTests` | **All tests** — alias of `ciTest` (JVM + Android + iOS on macOS) |
| `./gradlew ciTestJvm` | JVM test modules only (CI job **Tests (JVM)** on Ubuntu) |
| `./gradlew ciTest` | Same as `allTests` |
| `./gradlew verifyAndBenchmarkFast` | `allTests` then fast regression gate |
| `./gradlew verifyAndBenchmark` | `allTests` then full regression gate |
| `./gradlew :ghost-compiler:test` | KSP processor / emitter tests only |
| `./gradlew :ghost-benchmark:benchmarkRegressionFast` | `allTests` first, then fast regression |
| `./gradlew :ghost-benchmark:benchmarkRegressionFast -PskipTests` | Benchmark only — no test gate |
| `./gradlew :ghost-benchmark:run` | `allTests` first, then full JVM benchmark harness |
| `./gradlew :ghost-benchmark:run -PskipTests` | Benchmark only — no test gate |

On **Linux/Windows**, `ciTest` logs that iOS tests are skipped and runs **642** tests.
On **macOS** with Xcode, the full suite is **~874** (642 + `iosSimulatorArm64Test`).

---

## Adding a New Test Module

1. Create or extend a subproject under the repo root and add it to `settings.gradle.kts` if new.
2. Add tests under `src/test/` (JVM) or the appropriate KMP test source set.
3. Append the Gradle test task to **`ciTestJvmModules`** in the root `build.gradle.kts`:

   ```kotlin
   val ciTestJvmModules = listOf(
       // existing entries…
       ":your-module:test",
   )
   ```

4. Run `./gradlew ciTestJvm` locally.
5. Do **not** add a separate CI step in `.github/workflows/ci.yml` — the workflow already runs `./gradlew ciTestJvm`.

### KSP-Generated Models in Tests

Modules that deserialize annotated DTOs in tests need the Ghost compiler on test sources:

```kotlin
// build.gradle.kts
plugins { alias(libs.plugins.ksp) }
dependencies { kspTest(project(":ghost-compiler")) }
ksp { arg("ghost.moduleName", "your_module_test") }
```

See `ghost-spring-boot-starter` or `ghost-integration-test` for reference.

### Spring Boot Integration Tests

- Use **Spring Boot 3.4.x** (version catalog: `spring-boot` in `gradle/libs.versions.toml`).
- Prefer `@SpringBootTest` + `MockMvc` (or `WebTestClient` for WebFlux) with small `@GhostSerialization` fixtures in `src/test`.
- Keep `GhostWebMvcAutoConfiguration` / `GhostWebFluxAutoConfiguration` as `open` classes (Spring Boot 3.4+ requirement).

---

## Changing the Compiler (`ghost-compiler`)

- Add or update unit tests in `:ghost-compiler:test`.
- Run `:ghost-integration-test:test` for end-to-end generated serializer behavior.
- KSP processor errors **fail the build** by design — fix generated code or the processor, do not suppress failures.

---

## Changing Runtime (`ghost-serialization`) or Adapters

- **Retrofit / Ktor / Spring:** add adapter-specific tests in the corresponding module; register `:module:test` in `ciTestJvmModules` if not already listed.
- **Platform heuristics** (`GhostHeuristics`): update defaults in [Advanced Features → Platform Limits](advanced-features.md#7-platform-limits--memory) when values change.
- **HTTP body size:** enforce limits in OkHttp, Ktor, Spring codec, or reverse proxy — never in the core parser.

---

## Benchmarks

Performance-sensitive PRs should either:

- Run `./gradlew :ghost-benchmark:run` (includes `ciTest`), **or**
- Explain in the PR why benchmarks were not run (docs-only, comment-only, etc.).

Benchmark instructions: [benchmarks.md](benchmarks.md).

---

## Pull Request Checklist

1. Fork and branch from `main`.
2. `./gradlew ciTest` (or `ciTestJvm` + any platform tests you can run locally).
3. Update [CHANGELOG.md](../../CHANGELOG.md) under `[Unreleased]` or the target version for **all user-visible changes**.
4. One logical change per PR when possible.
5. No drive-by refactors unrelated to the issue.

We do not require a CLA for Apache 2.0 contributions. You retain copyright and license your work under the same [Apache 2.0](../../LICENSE) license as the project.

---

## Supported Platforms (Releases)

| Platform | Maven artifact | Notes |
|:---|:---|:---|
| Android | `ghost-serialization` (AAR) | KSP on `commonMain` / variant metadata |
| iOS | `ghost-serialization` (KMP) | XCFramework via consumer project |
| JVM | `ghost-serialization`, adapters | Server, desktop |
| Wasm | — | **Not published** in 1.2.x — do not document until the target ships |

---

## Getting Help

- [GitHub Issues](https://github.com/juanchurtado1991/GhostSerialization/issues) — bugs, features, questions
- [README](../../README.md) — landing page, Quick Start, all wiki links

---

← [Back to README](../../README.md)

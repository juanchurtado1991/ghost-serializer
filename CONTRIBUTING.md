# Contributing to Ghost Serialization

Thank you for helping improve Ghost. This document is the maintainer checklist; the README [Contributing](README.md#contributing) section is the short overview.

## Development environment

| Tool | Notes |
|:---|:---|
| **JDK 17** | Required for all modules |
| **Gradle wrapper** | `./gradlew` — do not rely on a system Gradle version |
| **Android SDK** | Needed for `testDebugUnitTest` (compile SDK 36 in `ghost-serialization`) |
| **Xcode (macOS)** | Needed for `iosSimulatorArm64Test` |

Clone and build:

```bash
git clone https://github.com/juanchurtado1991/GhostSerialization.git
cd GhostSerialization
./gradlew :ghost-serialization:compileKotlinJvm
```

## Verification commands

| Command | What it runs |
|:---|:---|
| `./gradlew ciTestJvm` | All JVM test modules (CI job **Tests (JVM)** on Ubuntu) |
| `./gradlew ciTest` | `ciTestJvm` + Android unit tests + iOS simulator tests on macOS only |
| `./gradlew :ghost-compiler:test` | KSP processor / emitter tests only |
| `./gradlew :ghost-benchmark:run` | **`ciTest` first**, then the JVM benchmark harness |
| `./gradlew :ghost-benchmark:run -PskipTests` | Benchmark only (no test gate) |

On Linux/Windows, `ciTest` logs that iOS tests are skipped and runs **642** tests. On macOS with Xcode, the full suite is **~874** (642 + `iosSimulatorArm64Test`).

## Adding a new test module

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

### KSP-generated models in tests

Modules that deserialize annotated DTOs in tests need the Ghost compiler on test sources, for example:

```kotlin
// build.gradle.kts
plugins { alias(libs.plugins.ksp) }
dependencies { kspTest(project(":ghost-compiler")) }
ksp { arg("ghost.moduleName", "your_module_test") }
```

See `ghost-spring-boot-starter` or `ghost-integration-test` for reference.

### Spring Boot integration tests

- Use **Spring Boot 3.4.x** (version catalog: `spring-boot` in `gradle/libs.versions.toml`).
- Prefer `@SpringBootTest` + `MockMvc` (or `WebTestClient` for WebFlux) with small `@GhostSerialization` fixtures in `src/test`.
- Keep `GhostWebMvcAutoConfiguration` / `GhostWebFluxAutoConfiguration` as `open` classes (Spring Boot 3.4+ enhancement).

## Changing the compiler (`ghost-compiler`)

- Add or update unit tests in `:ghost-compiler:test`.
- Run `:ghost-integration-test:test` for end-to-end generated serializer behavior.
- KSP processor errors **fail the build** by design — fix generated code or the processor, do not suppress failures.

## Changing runtime (`ghost-serialization`) or adapters

- **Retrofit / Ktor / Spring:** add adapter-specific tests in the corresponding module; register `:module:test` in `ciTestJvmModules` if not already listed.
- **Platform heuristics** (`GhostHeuristics`): document defaults in README *Platform limits and memory* when values change.
- **HTTP body size:** enforce limits in OkHttp, Ktor, Spring codec, or reverse proxy — not in the core parser (see README).

## Benchmarks

Performance-sensitive PRs should either:

- Run `./gradlew :ghost-benchmark:run` (includes `ciTest`), or
- Explain in the PR why benchmarks were not run (docs-only, comment-only, etc.).

Benchmark entry points are documented in README *Running the Benchmark Yourself*.

## Pull requests

1. Fork and branch from `main`.
2. `./gradlew ciTest` (or `ciTestJvm` + platform tests you can run).
3. Update [CHANGELOG.md](CHANGELOG.md) for user-visible changes.
4. One logical change per PR when possible.
5. No drive-by refactors unrelated to the issue.

We do not require a Contributor License Agreement for Apache 2.0 contributions; you retain copyright and license your work under the same [Apache 2.0](LICENSE) license as the project.

## Supported platforms (releases)

| Platform | Maven artifact | Notes |
|:---|:---|:---|
| Android | `ghost-serialization` (AAR) | KSP on `commonMain` / variant metadata |
| iOS | `ghost-serialization` (KMP) | XCFramework via consumer project |
| JVM | `ghost-serialization`, adapters | Server, desktop |
| Wasm | — | **Not published** in 1.1.x; do not document Wasm in README until the target ships |

## Getting help

- [GitHub Issues](https://github.com/juanchurtado1991/GhostSerialization/issues) — bugs, features, questions
- [README](README.md) — usage, limits, architecture

# Quality and conformance

How Ghost is tested beyond its own unit tests, and how to run each check yourself.

## JSON conformance

- **JSONTestSuite** ([nst/JSONTestSuite](https://github.com/nst/JSONTestSuite), vendored): all **318** cases behave correctly on the string, bytes and streaming channels, in lenient and in strict mode. All 95 `y_` cases are accepted, all 188 `n_` cases are rejected, and the 35 implementation-defined `i_` cases are pinned so a change is always deliberate.
- **json.org JSON_checker** (`pass1`-`pass3`, `fail1`-`fail33`, vendored): same channels and modes. Two files are accepted on purpose: `fail1` is a bare string at the root (valid since RFC 8259) and `fail18` nests arrays 20 deep (a depth limit the suite leaves optional).

```bash
./gradlew :ghost-serialization:jvmTest --tests '*GhostJsonTestSuiteTest*' --tests '*GhostJsonOrgTestSuiteTest*'
```

## Fuzzing

Coverage-guided fuzzing with [Jazzer](https://github.com/CodeIntelligenceTesting/jazzer) of every JSON reader and writer has executed more than **765 million cases with 0 crashes**. The accumulated corpus replays on every CI run.

Jazzer's JUnit mode fuzzes one test per JVM, so `scripts/jazzer.sh` runs each test in its own Gradle run for the duration you choose and writes a summary under `build/jazzer/`:

```bash
scripts/jazzer.sh 2m     # 10 JSON reader/writer tests, about 25 minutes
scripts/jazzer.sh 45m    # about 7.5 hours, for an overnight run
```

## Differential testing

`GhostDifferentialFuzzTest` checks that the three JSON channels agree with each other, that strict mode never accepts what kotlinx.serialization rejects, that strings round-trip, that decimals parse bit for bit like `Double.parseDouble` / `Float.parseFloat`, and that truncated or mutated input always fails with a clean `GhostJsonException`.

It runs on every build with a fixed seed. A scheduled workflow (`fuzz-nightly.yml`) runs it every night with a fresh seed and 300,000 iterations; a failure prints the seed, so it replays locally:

```bash
GHOST_FUZZ_SEED=<seed> GHOST_FUZZ_ITERATIONS=300000 ./gradlew :ghost-serialization:jvmTest --tests '*GhostDifferentialFuzzTest*'
```

## Real-world data

`GhostRealWorldCorpusTest` decodes every file listed in the file named by `GHOST_CORPUS_LIST` into a generic tree on every channel and compares it with kotlinx.serialization. Run against 8,934 JSON files from installed tools and packages, the trees are identical. The test is skipped when the variable is unset.

## YAML

- **Reader**: **96.06%** of [yaml-test-suite](https://github.com/yaml/yaml-test-suite)'s conformance cases decode exactly right (268/279). Every gap is tracked by case ID and reason in the repo; none is silently skipped.
- **Writer**: **100%** round-trip (decode, encode, decode returns the same tree) across every case the reader accepts; **96.25%** of the re-encoded output is also accepted by an independent second parser ([kaml](https://github.com/charleskorn/kaml)) as a cross-check, not just by Ghost's own reader.

```bash
./gradlew :ghost-serialization:yamlComplianceMatrix
```

Details and the full reports: [YAML Conformance](benchmarks.md#yaml-spec-compliance-ghost-only).

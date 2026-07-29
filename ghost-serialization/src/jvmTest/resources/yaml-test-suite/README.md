# Vendored yaml-test-suite snapshot

Source: https://github.com/yaml/yaml-test-suite

- Branch: `data` (directory-per-case layout — the `main` branch instead stores one YAML-wrapped
  file per case under `src/`)
- Pinned commit: `6ad3d2c62885d82fc349026c136ef560838fdf3d` (2022-01-17)
- Same tree as the official `data-2022-01-17` tag (dereferenced commit `6e6c296ae9c9d2d5c4134b4b64d01b29ac19ff6f`) — `git diff` between the two is empty
- No newer `data-*` tag exists upstream as of this vendoring

## What's excluded

`name/` and `tags/` — two directories of git symlinks (human-readable-name → ID, and
tag → ID) used for browsing upstream. Not used by `GhostYamlTestSuiteConformanceTest`, and
symlinks degrade to plain text on symlink-hostile checkouts, so they're stripped at vendor time.

## Refreshing this snapshot

```bash
rm -rf ghost-serialization/src/jvmTest/resources/yaml-test-suite/*
git -C <path-to-a-yaml-test-suite-clone> archive origin/data -- ':!name' ':!tags' \
  | tar -x -C ghost-serialization/src/jvmTest/resources/yaml-test-suite
```

Then re-run `GhostYamlTestSuiteConformanceTest` — the built-in deviation-staleness check will
fail loudly if any `DeviationCase` id in `YamlTestSuiteDeviations.kt` no longer matches a loaded
case, so stale entries can't silently go unnoticed after a refresh.

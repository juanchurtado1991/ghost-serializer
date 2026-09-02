# Support & Versioning

Ghost Serializer is maintained by one person, in spare time. This page says exactly what that means in practice, so you can decide whether to depend on it without guessing.

## Versioning

Ghost follows [Semantic Versioning](https://semver.org/) (`MAJOR.MINOR.PATCH`) on its public API — everything under `com.ghost.serialization` not marked `@InternalGhostApi`, plus the annotations (`@GhostSerialization`, `@GhostResilient`, `@GhostFallback`, `@GhostFlatten`, `@GhostDecoder`, `@GhostName`, …) and the Ktor/Retrofit/Spring integration modules.

- **PATCH** (`1.3.1` → `1.3.2`): bug fixes, performance work, new opt-in features. Safe to take without reading the changelog.
- **MINOR** (`1.3.x` → `1.4.0`): additive API changes. Old code keeps compiling; `@Deprecated` may appear on something being phased out.
- **MAJOR** (`1.x` → `2.0.0`): the only version that can remove or change the meaning of existing public API. Reserved for changes that can't be done any other way.

**Not covered by SemVer**: the exact shape of KSP-generated code (internal class/field names, dispatch tables), anything under `@InternalGhostApi`, and byte-for-byte wire output for edge cases the spec leaves undefined (e.g. YAML plain-scalar quoting heuristics) — these can change in a patch release if it makes Ghost faster or more correct, as long as valid input still decodes to the same value.

## Deprecation

Before anything is removed:

1. It's marked `@Deprecated` with a `replaceWith` pointing at the alternative, for at least one full minor version.
2. It's listed under a `### Deprecated` heading in [CHANGELOG.md](CHANGELOG.md) the version it started warning.
3. Removal itself only happens on a major version bump, and gets its own `### Removed` entry.

You will never see something disappear between two patch releases.

## Reporting a bug

Open a [GitHub Issue](https://github.com/juanchurtado1991/ghost-serializer/issues) with a minimal repro — the JSON/YAML input and the model class are almost always enough. Issues with a repro get triaged fastest; "it doesn't work" without one may sit longer.

**Realistic response time**: initial triage typically within a week. This is not a company with an SLA — if you need a guaranteed response window, that's worth knowing before you adopt.

## Reporting a security issue

Please don't open a public issue for anything that looks exploitable (crash on untrusted input, DoS via crafted payload, etc.) — email the maintainer address on the [GitHub profile](https://github.com/juanchurtado1991) instead, or use GitHub's [private vulnerability reporting](https://github.com/juanchurtado1991/ghost-serializer/security/advisories/new). Every reader and writer in the library already runs under continuous fuzz testing specifically to catch this class of bug before release — see [README § Tested against the spec](README.md#tested-against-the-spec-not-just-against-itself) — but fuzzing finds what it finds, not everything.

## What happens if this project goes quiet

If there's no maintainer activity (commits, releases, issue responses) for an extended period, that's the honest signal to fork or pin your dependency — the code is Apache-2.0 licensed specifically so that's always an option, for you or anyone else. There's no abandonment-detection magic beyond watching the repo.

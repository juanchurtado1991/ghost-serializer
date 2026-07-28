# Pendientes menores (post-1.3.0)

Lista de detalles no bloqueantes para revisar cuando convenga.

## Docs / artefactos generados

- [x] **`docs/coverage/`** — Regenerado localmente (`./gradlew ciTest koverHtmlReport` → `docs/coverage/`). Agregado 2026-07-27: merged **67.5% line / 49.2% branch**; sin refs a `com.ghost.protobuf.*`.

- [ ] **`docs/GHOST_MANUAL_EN.md` §23 (CI)** — drift menor vs `.github/workflows/ci.yml`:
  - Manual dice `testDebugUnitTest`; CI usa `:ghost-serialization:testAndroidHostTest`.
  - Manual dice `macos-14`; CI usa `macos-26`.
  - `ciTestJvmModules` incluye `:ghost-api:jvmTest` y `:ghost-playground:jvmTest`; la lista del manual no los nombra.

## CHANGELOG histórico

- [ ] Entradas **1.2.7 y anteriores** siguen nombrando `ghost-protobuf`, `ghost-yaml`, `ghost-sample` — correcto como contexto histórico; no reescribir salvo que se quiera un apéndice de migración separado.

## Limpieza local (hecho)

- [x] `ghost-yaml/build/` — eliminado (caché del módulo borrado).
- [x] `.yaml-staging/` — eliminado (copia untracked de migración).
- [x] `GhostEmitterConstants.OPTION_GENERATE_YAML` — constante muerta eliminada.

## Roadmap (intencional, no es deuda)

- `Set<T>` como body HTTP en adaptadores proto/YAML (Retrofit, Ktor, Spring MVC + WebFlux) — documentado en roadmap §3.
- Binary protobuf wire (gRPC).

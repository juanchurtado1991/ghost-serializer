# Changelog

## [Unreleased]

### Added
- **Test coverage for `ghost-api`**: the module had zero tests despite housing `RawJsonValueScanner` (the hand-written byte-level scanner behind `RawJson.kind()`/`asIntOrNull()`/`asLongOrNull()`/`asDoubleOrNull()`/`asStringOrNull()`) and `RawJson`'s slice-based `equals()`/`hashCode()`. It was previously only exercised indirectly through KSP-generated round-trips in other modules. Added `RawJsonNumberScanningTest`, `RawJsonStringEscapeTest`, `RawJsonSliceEqualityTest` (`ghost-api/src/commonTest`) covering number-grammar rejection (leading zeros, incomplete fraction/exponent), `Int`/`Long` overflow boundaries, Unicode escape decoding (surrogate pairs, truncated/invalid `\u` escapes), and `fromBufferSlice` equality/hashCode consistency against full-buffer instances. Wired `:ghost-api:jvmTest` into `ciTestJvmModules` (previously missing, so the module was never exercised in CI even after adding tests).
- **Test coverage for `ghost-ktor`'s bypass extensions**: `bodyGhost`/`respondGhost`/`respondGhostProto` (the direct, non-`ContentNegotiation` API most consumers actually call for hot paths) had no dedicated tests — only `bodyGhostProto`'s happy path was covered, transitively, by `GhostProtoKtorTest`. Added `GhostKtorBypassExtensionsTest` (`commonTest`, client-side: `bodyGhost` happy path and the "serializer not found" error message for both `bodyGhost`/`bodyGhostProto`) and `GhostKtorServerExtensionsTest` (new `jvmTest` source set, server-side: `respondGhost`/`respondGhostProto` happy paths, custom status codes, and the "serializer not found" error path via a real `testApplication` request/response cycle). Added `ktor-server-test-host` to the version catalog, scoped to `ghost-ktor`'s `jvmTest` only.
- **Test coverage for `ghost-gradle-plugin`'s classpath detection**: the plugin's entire value proposition — auto-injecting `ghost-ktor`/`ghost-retrofit`/`ghost-protobuf` when it detects a matching dependency, and respecting the `autoInjectKtor`/`autoInjectRetrofit`/`autoInjectProtobuf` opt-out flags — had zero coverage; the one existing dependency-injection test only checked the always-on core deps (`ghost-serialization`/`ghost-api`) on a bare JVM project. Added 7 tests to `GhostPluginTest` covering: ktor-client/retrofit/protobuf detection each injecting their adapter, each `autoInject*` flag disabling injection despite a matching dependency being present, the "nothing detected" baseline, and the KMP path (`commonMainImplementation` instead of `implementation`).
- **Kover coverage reporting**: added the `org.jetbrains.kotlinx.kover` Gradle plugin (0.9.9) to the 9 JVM/Android-testable modules (`ghost-api`, `ghost-serialization`, `ghost-ktor`, `ghost-compiler`, `ghost-integration-test`, `ghost-retrofit`, `ghost-spring-boot-starter`, `ghost-gradle-plugin`, `ghost-protobuf` — same set as `ciTestJvmModules`) plus a root-level merged report via `dependencies { kover(project(...)) }`. `./gradlew koverHtmlReport`/`koverXmlReport` now produce real line/branch/method/class coverage numbers instead of manual file-count guessing. First measured baseline: 70.0% line / 50.0% branch aggregate; `ghost-spring-boot-starter` (50.4% line / 37.9% branch) is the weakest module. Reporting-only for now — no `koverVerify` threshold gate wired into CI. Kover only measures JVM bytecode, so `iosSimulatorArm64Test`/Kotlin-Native code paths aren't reflected in these numbers; documented in `docs/wiki/contributing.md`.
- **Test coverage for `ghost-spring-boot-starter`'s WebFlux codecs**: `GhostReactiveDecoder`/`GhostReactiveEncoder` had *zero* test exercise (only `GhostSpringMvcIntegrationTest`/`GhostSpringProtoMvcIntegrationTest` existed; the only WebFlux-related assertion checked that `GhostWebFluxAutoConfiguration` was listed in `spring.factories`, never actually decoded/encoded anything). Added `GhostReactiveDecoderTest`/`GhostReactiveEncoderTest` (direct unit tests: `canDecode`/`canEncode` branches, `decode`/`decodeToMono`/`encode`, plain-JSON vs NDJSON framing, error wrapping) and `GhostSpringWebFluxIntegrationTest` (real reactive `@SpringBootTest` + `WebTestClient`, forcing `spring.main.web-application-type=reactive` since both MVC and WebFlux starters are on the test classpath). Module coverage moved from 50.4%/37.9% (line/branch) to 92.8%/78.9%. Added `spring-boot-starter-webflux` and `reactor-test` as test-only dependencies.
- **Test coverage for `ghost-serialization`'s `GhostJsonFlatWriter`**: the in-memory/`FlatByteArrayWriter`-backed writer used by every KSP-generated serializer's flat encode path sat at 0.5% branch coverage — the equivalent scenarios were only tested against its sibling, the Okio-streaming `GhostJsonWriter` (`GhostWriterEdgeCaseTest`), never against the flat writer itself. Added `GhostFlatWriterEdgeCaseTest` mirroring those scenarios (numeric fast paths incl. `Int`/`Long.MIN_VALUE`, double/float whole-number vs fractional vs `NaN`/`Infinity`, string escaping incl. long-string scratch-buffer-overflow branches, depth protection) plus the flat writer's own fused/raw APIs that `GhostJsonWriter` doesn't have (`writeField(header, value)`, `rawValue`, pre-encoded `ByteString` names). Branch coverage moved from 0.5% (1/182) to 64.3% (117/182).
- **Test coverage for `ghost-serialization`'s `StreamingGhostSource`**: the `GhostSource` backing every `GhostJsonReader` built from an `okio.BufferedSource` (used whenever Ghost parses from a streaming source — e.g. an OkHttp/Ktor response body — rather than an in-memory `ByteArray`) sat at 9.0% branch coverage; only one other test in the suite (`NextCharTest`) constructed a streaming reader at all, with a single-character payload. Added `StreamingGhostSourceTest`: direct `get`/`decodeToString`/`contentEquals`/`contentEqualsString` tests, plus `GhostJsonReader`-level tests using payloads larger than `STREAMING_BUFFER_SIZE` (8192 bytes) specifically to reach the segment-realignment (`getSlow`) and cross-segment continuation branches in `findNextNonWhitespace`/`findClosingQuote`/`scanString` that a small payload can never touch. Branch coverage moved from 9.0% (15/166) to 57.1% (97/170).
- **Test coverage for `ghost-serialization`'s `GhostJsonFlatReader`**: strict-mode trailing-comma rejection and `coerceBooleans` were already tested against sibling reader flavors (`GhostJsonStringReaderTest`, `GhostReaderSelectNameTest` via `GhostJsonReader`) but never against the flat reader itself, and `beginObject()`'s own depth-limit check had no dedicated test (only `beginArray()`'s did). Added `GhostFlatReaderTrailingCommaAndCoercionTest`, including a `hasNext()`/`consumeArraySeparator()` interaction test built from the real calling convention used by `CollectionSerializers.kt` (separator consumed *before* an element once the list is non-empty, never called twice in a row) after an initial version of that test — written against an assumed, incorrect convention — failed. Branch coverage moved from 43.6% (106/243) to 55.6% (135/243).
- **Test coverage for `ghost-compiler`'s `GhostAnalyzer` validation errors**: `validatePropertyVisibility` (private properties), `validateNames` (duplicate JSON names), and `validateMapKey` (non-`String` map keys) had no test anywhere in the module — only `validateClassKind`'s "not a data/sealed/value/enum class" error was covered (`GhostSerializationKspTest.failsForNonDataClass`). Each only surfaces via `KSPLogger.error` during a real KSP compile (it doesn't throw), so these can't be unit-tested without compiling source through `kotlin-compile-testing`. Added `GhostAnalyzerValidationKspTest` (3 rejection cases + 1 confirming `Map<String, V>` still compiles correctly).
- **Test coverage for `ghost-protobuf`'s dynamic-tree WKTs**: `ProtoStructSerializer` (the top-level `ProtoStruct = Map<String, ProtoValue>` entry point, distinct from `ProtoValue.Struct`'s nested variant) had no test anywhere in the module (16.7% branch). `ProtoAnySerializer`'s existing tests (`ProtoAnyTest`) only ever exercised the flat path, since `GhostProtobuf.deserialize<T>(String)` always builds a `GhostProtoJsonFlatReader` — the streaming (`GhostJsonWriter`/`GhostJsonReader`) overloads, the unrecognized-key `skipValue()` branch, and `"value"`-before-`"@type"` key order were all untested (40% branch). Added `ProtoStructSerializerTest` (direct calls to all 4 serialize/deserialize overloads, since `ProtoStruct` is a type alias — `ProtoStruct::class` erases to `Map::class` at runtime and doesn't reliably dispatch through the `KClass`-keyed registry) and `ProtoAnyExtraCoverageTest`. `ProtoStructSerializer` 16.7%→100%, `ProtoAnySerializer` 40%→95%, `ProtoValueSerializer` 50%→76.2% (bonus from the new round-trip tests exercising its `when` branches), module aggregate 59.5%→66.7%.
- **Test coverage for `ghost-ktor`'s `ContentNegotiation` converters**: `GhostContentConverter`/`GhostProtoContentConverter` were only ever exercised end-to-end through a real pipeline with small payloads (`GhostKtorTest`/`GhostProtoKtorTest`) — the `null`-return contract (`value == null`, unregistered type on both serialize and deserialize) and the scratch-buffer growth path (payload > the 512 KB initial buffer) had no direct coverage. Added `GhostContentConverterDirectTest`/`GhostProtoContentConverterDirectTest`, calling both converters directly as plain `ContentConverter` implementations (no client/server needed). `GhostContentConverter` 60%→80%, `GhostProtoContentConverter` 30%→70% branch, module aggregate 47.8%→73.9%.

### Fixed
- **`RawJson.asLongOrNull()`/`asIntOrNull()` silently corrupted sign on overflow**: `RawJsonValueScanner.parseIntegerOrNull()` accumulates digits into a negative `Long` and negates at the end for positive input. A positive token whose magnitude is exactly `-Long.MIN_VALUE` (i.e. `9223372036854775808`, one past `Long.MAX_VALUE`) accumulated to exactly `Long.MIN_VALUE`, and negating `Long.MIN_VALUE` overflows back to itself in two's complement — so `"9223372036854775808".asLongOrNull()` returned `-9223372036854775808` instead of `null`. Same root cause as the `Long.MIN_VALUE` formatting bugs fixed in 1.2.7 (`ProtoWrappers.kt`/`ProtoDuration.kt`), on the parse side instead of format side. Found by the new `RawJsonNumberScanningTest`. Now checks for this boundary explicitly before negating.
- **`GhostReactiveDecoder` silently dropped NDJSON records**: `decodeStreaming()` mapped each incoming `DataBuffer` 1:1 to one decoded object, assuming every buffer already aligned to exactly one NDJSON line. Over a real network/transport, a small multi-line NDJSON request body typically arrives as a *single* buffer (and a single record can just as easily split across two) — so a 2-record NDJSON POST body silently decoded to only 1 object, with the rest discarded and no error raised. Found by `GhostSpringWebFluxIntegrationTest`'s streaming-request test. Now re-frames the raw buffer stream on `\n` before decoding each line, carrying partial lines across buffer boundaries and flushing a final unterminated line at stream completion.
- **`StreamingGhostSource` silently corrupted data crossing a segment boundary — affects every streaming parse over ~8KB.** `getSlow()` refills its internal 8192-byte (`STREAMING_BUFFER_SIZE`) window via `tempBuffer.read(bufferBytes, offset, byteCount)`, assuming a single call always fills the full requested range. Okio's own `Buffer.read(ByteArray, Int, Int)` is documented as reading only **up to** `byteCount` bytes per call (same partial-read contract as `InputStream.read`) — when the requested range spans two of Okio's own internal segments (also 8192 bytes), a single call silently stops at that internal boundary, leaving the rest of `bufferBytes` at its previous/zeroed contents with **no exception thrown**. Any JSON payload parsed from a real `BufferedSource` (an HTTP response body via OkHttp/Ktor, a file, a socket — anything larger than ~8KB) where a value straddles this boundary got silently corrupted; depending on what landed in the zeroed bytes, this surfaced as anything from a confusing `"Unescaped control character"` parse error to *wrong data with no error at all*. Found by `StreamingGhostSourceTest`'s cross-segment string test. Now loops `read()` until the exact byte count is fully drained (bounded to at most 2 iterations, since a single refill can only ever span 2 segments) — negligible cost added to an operation that already only runs once per 8192 bytes of stream, and the hot per-byte scan loops are untouched.

## [1.2.7] - 2026-07-08

### Added
- **ProtoLab Benchmark (Sample App)**: New interactive UI in the sample app to compare Ghost, GhostProto, and KotlinX Serialization on Standard JSON vs Proto3 JSON payloads, using real OpenLibrary API data.
- **`ghost-protobuf` module**: New Kotlin Multiplatform module (`com.ghostserializer:ghost-protobuf`, package `com.ghost.protobuf`, targets Android / iOS arm64+simulator / JVM) that layers proto3 JSON mapping rules on top of the existing byte-first JSON engine. Entry point `GhostProtobuf.deserialize<T>()` accepts `ByteArray`, `String`, `BufferedSource`, or a pre-built `GhostProtoJsonFlatReader`. See the new [Usage — Protobuf](docs/wiki/usage-protobuf.md) wiki page.
- **Jazzer fuzzing suite**: Added `ProtoWktFuzzTest` leveraging coverage-guided fuzzing (Jazzer) on WKT parsers (`parseDuration`, `parseTimestamp`, `decodeBase64String`) and round-trip assertions.
- **`@GhostProtoSerialization` annotation** (`ghost-api`): Proto3-JSON-mapping variant of `@GhostSerialization` — lowerCamelCase field names, default/empty values omitted on serialize, 64-bit integers (`Long`) as quoted strings, enums as strings (already Ghost's default), `ByteArray` as Base64. Covers direct properties, `@JvmInline value class`-wrapped properties, and `List`/`Set`/`Map` elements. `GhostSerializationProcessor` scans for both annotations; `GhostAnalyzer` auto-converts `snake_case` field names to `lowerCamelCase` for `@GhostProtoSerialization` classes (unless an explicit `@GhostName`/`@SerialName` overrides it) and recognizes `@GhostProtoSerialization` types as valid nested Ghost types alongside `@GhostSerialization`.
- **oneof mapping**: proto3 `oneof` (variant field appears as a sibling of the message's other fields, no wrapper/discriminator) is supported by composing `@GhostWrappedKeys` with `@GhostSerialization(inferred = true)` sealed hierarchies — no dedicated annotation needed. Fixed `GhostAnalyzer.resolveWrappedUnwrapFields`/`BaseSerializeEmitter.emitWrappedKeysProperty` to resolve wire keys against a wrapped sealed type's *subclasses* (an `is`-check + smart-cast per resolved field) — previously the serialize direction silently dropped the payload (`resolveWrappedUnwrapFields` only looked at the wrapped type's own properties, and a sealed parent has none). See [Usage — Protobuf §3](docs/wiki/usage-protobuf.md#3-oneof-mapping).
- **`ProtoAnyRegistry`** (`ghost-protobuf/wkt`): `register<T>(typeUrl)` / `pack(value)` / `unpack<T>(any)` / `unpackDynamic(any)` — resolves a `ProtoAny`'s `typeUrl` to a concrete Kotlin type (via `Ghost`'s own serializer registry) instead of requiring callers to decode `ProtoAny.value` manually.
- **Full-range `uint64`**: `ProtoUInt64Value` is now backed by `ULong` instead of `Long` — `Long` can only represent about half of `uint64`'s range (0–`Long.MAX_VALUE`), silently failing/overflowing above that. `ULong.toString()` is unsigned-aware, so serialize no longer needs the `Long`-specific `formatLong()` helper. Also fixed `GhostProtoJsonFlatReader.nextProtoUInt64()`, previously dead code that just delegated to the `Long`-range-limited `nextProtoInt64()`.
- **`GhostProtoConverterFactory`** (`ghost-retrofit`): Retrofit `Converter.Factory` for proto3-JSON APIs — reads through `GhostProtoJsonFlatReader` instead of the plain flat reader. Direct (non-generic) types only, unlike `GhostConverterFactory`.
- **`GhostProtoContentConverter`, `Configuration.ghostProto()`, `bodyGhostProto<T>()`, `respondGhostProto<T>()`** (`ghost-ktor`): Ktor equivalents of the above, for both the `ContentNegotiation` pipeline and the bypass extensions.
- **`GhostHttpMessageConverter` auto-detects `@GhostProtoSerialization`** (`ghost-spring-boot-starter`): checks the resolved serializer's new `isProto` flag and routes through `GhostProtoJsonFlatReader` automatically — no separate converter/configuration needed, since Spring's `HttpMessageConverter` list is global (unlike a Retrofit/Ktor client, which is scoped per API).
- **`GhostSerializer.isProto: Boolean`** (`ghost-serialization`): Runtime-checkable flag mirroring the existing `isResilient` pattern. `@GhostProtoSerialization` has `BINARY` annotation retention (invisible to reflection), so this is the supported way for framework integrations to detect a proto3-flavored serializer at runtime. `GhostCodeGenerator` emits `override val isProto: Boolean = true` once per generated serializer object when applicable.
- **`GhostProtobuf.deserialize(bytes, KClass<T>)`, `.encodeToBytes(value)`, `.encodeToString(value)`**: Non-reified `KClass`-based deserialize (needed by the HTTP integrations above, where the target type is only known at runtime) and encode wrappers over `Ghost.encodeToBytes`/`Ghost.encodeToString`, so both directions live under one `GhostProtobuf.*` surface.
- **`GhostProtoJsonFlatReader`**: Subclass of `GhostJsonFlatReader` overriding `nextInt`/`nextLong`/`nextFloat`/`nextDouble` with proto3 JSON parsing rules — int64/uint64 accepted as quoted strings or plain numbers, int32 rejects non-zero fractional values (`1.5` throws, `1.0` is accepted), and float/double recognize the quoted literals `"NaN"`, `"Infinity"`, `"-Infinity"`. Adds `nextProtoUInt32`/`nextProtoUInt64` (range-checked against the uint32 max), `nextProtoBytes` (scratch-buffer-pooled Base64 decode), and `nextProtoEnum` (string-or-index enum decoding).
- **Well-Known Types** (`ghost-protobuf/wkt`): Hand-written zero-allocation `GhostSerializer` implementations — `ProtoDuration` / `ProtoTimestamp` (RFC3339 formatting/parsing via zero-allocation calendar math, nanosecond precision), `ProtoStruct` / `ProtoValue` / `ProtoEmpty` (dynamic JSON tree types), `ProtoFieldMask` (comma-separated path list ↔ camelCase conversion), `ProtoAny` (`@type` + raw `"value"` JSON bytes capture/round-trip), and scalar wrapper types (`ProtoBoolValue`, `ProtoStringValue`, `ProtoBytesValue`, `ProtoDoubleValue`, `ProtoFloatValue`, `ProtoInt32Value`, `ProtoInt64Value`, `ProtoUInt32Value`, `ProtoUInt64Value`) as `@JvmInline value class` wrappers wire-mapped per proto3 JSON rules.
- **`encodeBase64String()` / `decodeBase64String()`** (`ghost-serialization`, `com.ghost.serialization.parser`): Public, reader-agnostic Base64 codec functions (operate on a plain `String`/`ByteArray`, not tied to any specific reader implementation). Shared by `ProtoBytesValueSerializer` and by KSP-generated `@GhostProtoSerialization` `ByteArray` fields.
- **Benchmark coverage**: `GhostSpecialFeaturesBenchmark` and `BenchmarkEnvironment`'s manual `GhostRegistry` now exercise serialize/deserialize for all WKTs; `ghost-benchmark` now depends on `:ghost-protobuf` and includes these in the regression suites.
- **Test coverage**: `ProtoJsonNumericTest`, `ProtoValueTest`, `ProtoWktTest`, `ProtoWktStructsTest`, `ProtoWktEdgeCasesTest`, `ProtoWrappersTest`, `ProtoAnyTest`, `ProtoAnyRegistryTest`, `GhostProtobufEntryPointsTest` (`ghost-protobuf/src/commonTest`); `GhostProtoSerializationKspTest` (generated-code assertions for int64/`ByteArray`/collection/value-class quoting, Base64, zero-value omission, `isProto` flag) and an extended `GhostWrappedKeysKspTest` (sealed-subclass smart-cast) in `ghost-compiler/src/test`; `GhostProtoOneofIntegrationTest` (real KSP-generated round-trip) in `ghost-integration-test/src/test`; `GhostProtoRetrofitTest` (MockWebServer), `GhostProtoKtorTest` (Ktor `MockEngine`), and `GhostSpringProtoMvcIntegrationTest` (real Spring MVC + `MockMvc`) in the respective adapter modules.
- **`ProtoJsonConformanceTest`** (`ghost-protobuf/src/jvmTest`, JVM-only, part of the `ciTestJvm` gate): Cross-checks Ghost's proto3 JSON output against `protobuf-java`'s own `JsonFormat.Printer` — the real reference implementation, unlike every other test in the module, which only proves Ghost is internally consistent (round-trips what it wrote). Covers `Duration`, `Timestamp`, all 8 scalar wrapper types, `BytesValue`, `Struct`/`Value` (including nested struct/list), and `Empty`. Added `protobuf-java`/`protobuf-java-util` to `gradle/libs.versions.toml` as a test-only dependency (`ghost-protobuf/build.gradle.kts` `jvmTest.dependencies`) — not used by any shipped module. This test suite caught the `writeNanosFraction` bug below.

### Fixed
- **`ProtoDuration` sign loss**: Fixed a bug where a negative duration under one second (`seconds == 0` and `nanos < 0`) lost its sign on serialization.
- **Out-of-bounds in Base64 decoders**: Prevented `ArrayIndexOutOfBoundsException` when parsing malformed Base64 strings with non-Latin-1 characters.
- **`JvmInline` compiler import**: Fixed metadata compilation of `ProtoWrappers.kt` in KMP common compilations by adding the explicit import.
- **`ghost-protobuf` tests were never running in CI**: `:ghost-protobuf:jvmTest` was missing from `ciTestJvmModules` in the root `build.gradle.kts`, so `./gradlew ciTestJvm`/`ciTest`/`allTests` and the GitHub Actions `test-jvm` job silently skipped the entire module. Added to the gate.tire module. Added to the gate.
- **`Duration`/`Timestamp` fractional seconds violated proto3 JSON's 3/6/9-digit grouping rule**: `writeNanosFraction()` trimmed trailing zero digits to the last significant one (e.g. 450,000,000 ns rendered as `.45`) instead of rounding up to the nearest of 3, 6, or 9 fractional digits as the spec requires (`.450`) — mathematically equivalent but non-conformant with real protobuf JSON parsers, which expect exactly one of those three widths. Found by `ProtoJsonConformanceTest` against `protobuf-java`. Also corrected a pre-existing `ProtoWktTest` assertion (`"-120.5s"`) that had baked in the same non-conformant output.
- **`Long.MIN_VALUE` silently corrupted to `"-0"` on serialize**: `formatLong()` (`ProtoWrappers.kt`) and `writeLongToBytes()` (`ProtoDuration.kt`) negated the full magnitude of the input (`v = -v`), which overflows back to `Long.MIN_VALUE` in two's complement for that one boundary value — `ProtoInt64Value(Long.MIN_VALUE)` serialized to `"-0"` and `ProtoDuration(Long.MIN_VALUE, 0)` formatted to `"-0s"`, with no exception thrown. Both now work in negative space throughout and only ever negate a single digit (0–9), which can't overflow.
- **`ProtoAny` discarded its payload**: `serialize()` had a dead `if` branch that never wrote anything, and `deserialize()` always returned `ProtoAny(typeUrl, ByteArray(0))` regardless of wire content — the one existing test only asserted `typeUrl`. Now captures/round-trips the raw JSON bytes of the `"value"` sibling key via `captureRawJsonBytes()`. Also added explicit `equals()`/`hashCode()` to `ProtoAny` (the data-class-generated versions used `ByteArray` reference equality, which would silently fail to compare captured payloads).
- **`ProtoBytesValue` threw `UnsupportedOperationException` outside `GhostProtobuf.*`**: registering `ProtoBytesValue` in a `GhostRegistry` and then calling the more familiar `Ghost.deserialize()`/`Ghost.deserializeStreaming()` (instead of `GhostProtobuf.deserialize()`) compiled fine and threw at runtime, since Base64 decode was hard-tied to `GhostProtoJsonFlatReader`. Now reader-agnostic via `decodeBase64String(reader.nextString())`, working on every reader flavor.
- **KSP-generated `@GhostProtoSerialization` classes never actually applied proto3 wire rules**: despite the annotation's own KDoc, generated code previously routed `Long`/`ByteArray`/default-value fields through the exact same codegen as plain `@GhostSerialization` — verified by inspecting real generated output before this fix (`writer.writeField(H_USERID, value.user_id)`, unquoted; `writer.rawValue(value.payload)`, not Base64; zero-value fields always written). `BaseSerializeEmitter`/`BaseDeserializeEmitter` now special-case direct `Long` and `ByteArray` properties on proto classes (quoting + coercion, Base64 encode/decode) and wrap non-nullable scalar/collection properties in a "not the zero value" guard on serialize.

### Changed
- **`GhostJsonFlatReader` opened for subclassing**: Changed from `final class` to `open class`; `rawData`, `limit`, `position`, `nextTokenByte`, and `getByte()` widened from `internal`/`@PublishedApi internal` to `public`; `nextFloat`/`nextDouble`/`nextInt`/`nextLong` are now `open fun` delegating to renamed internal extension functions (`nextFloatExtension`/`nextDoubleExtension`/`nextIntExtension`/`nextLongExtension`). Required so `GhostProtoJsonFlatReader` can override numeric parsing without duplicating the core reader. **This widens `ghost-serialization`'s public API surface** — worth a deliberate look before the next release if API stability across modules matters.
- **`Ghost.getSerializer(KClass)` and `Ghost.resolveSerializer<T>()` made public** (previously `internal` / `@PublishedApi internal`): needed so `ghost-protobuf`'s `GhostProtobuf.deserialize<T>()` can resolve serializers from a separate module.
- **`JsonReaderOptions.rawBytes` / `.rawStrings` made public** (previously `@PublishedApi internal`): needed by `GhostProtoJsonFlatReader.readProtoEnum` for manual enum string/index matching outside the core module.

### Known limitations
- **`List<T>`/`Map<K,V>` body unwrapping not supported in the Retrofit/Ktor proto converters** — `GhostProtoConverterFactory`/`GhostProtoContentConverter` resolve direct (non-generic) types only, unlike their plain-JSON counterparts.
- **`Long`/`ByteArray` proto conversion doesn't cover value-class-wrapped collections or collections of value classes** — e.g. a value class wrapping `List<Long>`, or `List<AccountId>` where `AccountId` wraps a `Long`, fall back to plain (non-quoted / non-Base64) handling. Direct properties, single-level value-class wrapping, and `List`/`Set`/`Map` elements are covered.
- **No first-class `ULong` field type for user `@GhostProtoSerialization` messages** — only `ghost-protobuf`'s own `ProtoUInt64Value` WKT wrapper has full-range `uint64` support (core Ghost has no `ULong` field type at all). Model your own `uint64` fields as `ProtoUInt64Value` or a custom `@GhostEncoder`/`@GhostDecoder`.
- **`ghost-gradle-plugin` doesn't auto-inject `:ghost-protobuf`** the way it does for `ghost-ktor`/`ghost-retrofit` — add the dependency manually.

## [1.2.5] - 2026-07-06

### Distribution
- **GitHub Packages**: `1.2.5` is published to [GitHub Packages](docs/wiki/github-packages.md) while Maven Central monthly publishing limits are in effect. Maven Central remains at `1.2.4`; use the GitHub Packages repository (see wiki) for `1.2.5+`.

### Added
- **`@GhostWrappedKeys` structural transformation**: Collapses sibling JSON keys into a single Kotlin property (inverse of `@GhostWrap`, SmartThings `@WrappedKeys` parity). Deserialize captures each wire key as a zero-copy `RawJson` slice, assembles a synthetic wrapper object in a pooled scratch buffer, and parses the target type; serialize unwraps inner fields back to sibling keys. Supports `omitIfEmpty`, `omitIfAbsent`, repeated annotations on different properties, and nested hierarchy composition. Integration tests port `WrappedKeysTypeAdapterFactoryTest` scenarios.
- **`@GhostJsonEnvelope` external discriminator routing**: KSP generates `routePayload`, `parsePayload`, `routeTyped`, and `parseTyped` on envelope serializer companions. Supports fat multi-field SSE envelopes (`@GhostEnvelopePayload` per `RawJson?` field) and generic webhook shape (`type` + `data`). Zero-copy payload selection via captured `RawJson` slices; optional `@GhostEnvelopeFallback` for unknown types; typed second stage via `@GhostEnvelopePayload(target = …)` + `RawJsonDecode`.
- **`RawJson` opaque JSON type**: New type in `com.ghost.serialization.types.RawJson` for public API fields that hold arbitrary JSON (objects, arrays, primitives). Uses zero-copy `captureRawJson()` slice capture on flat byte readers (`storage` + `storageOffset` + `storageLength`), slice-aware `writer.rawValue(bytes, offset, length)` on serialize, and value-based `equals`/`hashCode`. `captureRawJsonBytes()` still returns an owned copy. Supports nullable fields, `List<RawJson>`, and `Map<String, RawJson>`. Added `RawJsonCaptureBenchmark` comparing slice vs `ByteArray` capture.
- **`RawJson` scalar access API** (`ghost-api`): `RawJsonKind`, `kind()`, `isJsonNull`, `asBooleanOrNull()`, `asIntOrNull()`, `asLongOrNull()`, `asDoubleOrNull()`, `asStringOrNull()`, `asDisplayString()` — zero-allocation classification and integer/boolean coercion on captured slices; ASCII string fast path without escape scanning when possible.
- **`RawJson.decodeAs<T>()`** (`ghost-serialization`): Typed second-stage parse via `GhostJsonFlatReader.resetSlice()` without copying parent response buffers.
- **String-native `@GhostDecoder`**: KSP detects `fun(GhostJsonStringReader): T` signatures and emits direct calls on the string deserialize path, avoiding per-field `encodeToByteArray()` bridging when `ghost.textChannel=true`. Legacy `fun(GhostJsonReader): T` decoders keep the compatibility bridge.
- **Per-model `textChannel` on `@GhostSerialization`**: Opt in native `GhostJsonStringReader` / `GhostJsonStringWriter` overloads per model (default `false`) with transitive propagation to nested `@GhostSerialization` types in the property graph. Module-wide `ghost.textChannel=true` remains supported as a legacy all-models switch.
- **UTF-8 cache on `GhostJsonStringReader`**: `ensureUtf8Bytes()`, char→byte offset table, and `sliceUtf8Bytes()` encode at most once per `reset` and avoid a full-document UTF-8 pass for `captureRawJsonBytes` on large envelopes.
- **Benchmark fast profile and regression gate**: `BenchmarkLauncher`, `benchmarkRegressionFast`, `benchmarkTwitterFast`, and `benchmarkSyntheticFast` with ±10% tolerance; benchmark tasks depend on `:allTests` unless `-PskipTests`.
- **Root verification tasks**: `allTests` (alias of `ciTest`), `verifyAndBenchmark`, and `verifyAndBenchmarkFast` for one-shot test-then-regression workflows.

### Fixed
- **Leaner generated serializers**: KSP now imports parser helpers conditionally (`captureRawJson` vs `captureRawJsonBytes`, `readList` vs `readSet`), gates native `GhostJsonStringWriter` serialize overloads behind `ghost.textChannel=true` (interface bridge remains), skips `HS_*` string header constants when the string channel is disabled, drops `@file:Suppress` from generated serializers, and trims redundant `kotlin.*` stdlib imports.
- **`@GhostWrappedKeys` materialize omits absent slots**: `GhostWrappedKeysCapture.materializeWrappedObject` no longer emits `"key":null` for uncaptured wire keys — fixes sealed-class discriminator parsing when nested objects precede `type` in the synthetic wrapper.
- **`GhostDiscriminatorPeeker` skips nested values before discriminator**: Peeking no longer aborts when nested JSON objects or arrays appear before the discriminator key; `GhostJsonStringReader.peekStringField` delegates to the same `peekInternal` scan (char channel, zero UTF-8 bridge).
- **`@GhostWrappedKeys` KSP metadata fallback**: `GhostAnalyzer` reads `keys` / `omitIfEmpty` / `omitIfAbsent` from `KSAnnotation.arguments` when `getAnnotationsByType` fails during `kspCommonMainKotlinMetadata` (KMP common metadata compilations).
- **String-bridge array serialization**: Default `serialize(GhostJsonStringWriter)` writes via `rawValue(bytes)` so comma separators are emitted between array elements when `textChannel=false`.
- **String-bridge `RawJson` capture**: `deserialize(GhostJsonStringReader)` sets `materializeRawJsonCaptures` on the delegated flat reader so `RawJson` fields get owned bytes (`storageOffset == 0`) without enabling per-model `textChannel`.
- **Enum wire-value hash collisions (issue #11)**: Enum serializers now use `PerfectHashFinder` for `ENUM_OPTIONS` (same as object field dispatch), instead of `JsonReaderOptions.of(*names)` with default shift/multiplier/table size. When the standard hash search fails (e.g. enums with shared 4-byte prefixes and different lengths like `w:locations:geo`), the compiler retries with extended key hashing and emits the matching runtime flag. Generated enum code no longer uses the bare `JsonReaderOptions.of(vararg)` factory described in issue #12.

### Changed
- **Benchmark harness refactor**: Replaced monolithic CLI flags with `BenchmarkProfile` constants and per-suite Gradle tasks; removed dead `ParsingTestBenchmark` / `TestResult` code.

## [1.2.4] - 2026-06-30

### Fixed
- **Perfect Hash Collision Resolution in KSP and Runtime (issue #10)**: Refactored `PerfectHashFinder.kt` and `JsonReaderOptions.kt` to use a zero-allocation `while` loop that hashes all remaining bytes of the field name when a collision is detected. This completely resolves KSP processing failures and runtime perfect hash collisions for classes containing fields with identical sizes and overlapping prefixes/suffixes (e.g., `modelCode` vs `modelName`, `dateCreated` vs `dateUpdated`, `inviterUsername` vs `inviteeUsername`).

## [1.2.3] - 2026-06-29

### Performance
- **Android ThreadLocal UI Thread Bypass**: Optimized `getLocalPool()` in `GhostPools.android.kt` by caching the main thread reference and performing a fast identity comparison (`===`) to completely bypass the `ThreadLocal.get()` map lookup on the UI thread.
- **String Pool Cache Locality**: Added a contiguous `IntArray` (`stringPoolHashes`) to `GhostJsonFlatReader` and `GhostJsonStringReader` to store string hashes. This avoids dereferencing `String` object references on pool misses, keeping the lookup in the L1 data cache and speeding up JSON parsing across all platforms.
- **Dynamic Perfect Hash Table Sizing**: Configured the compiler to search for a collision-free perfect hash starting at size `128` and scaling up (`256`, `512`, `1024`, etc.) on demand. This reduces dispatch table memory footprint by 50% to 87.5% at runtime for small and medium-sized models.

### Fixed
- **Perfect Hash Table Scaling for Large Models**: Refactored `PerfectHashFinder.kt` and `JsonReaderOptions.kt` to support dynamic table sizes up to `8192`. This resolves KSP processing failures for large models (like the 100-field `CollisionModel`) that could not find a perfect hash at the default size.
- **Android JVM Unit Test Looper Mocking**: Wrapped `Looper.getMainLooper()` in `GhostPools.android.kt` in a `try-catch` block to prevent `Method getMainLooper in android.os.Looper not mocked` crashes when executing Android unit tests in a pure JVM environment.
- **`classDeclaration` inaccessible in `GhostCodeGenerator`**: The `classDeclaration` constructor parameter was not declared as a `val`, making it invisible to annotation-reading helpers inside `buildSerializerObject()`. Changed to `private val`.
- **`@GhostFallback` support for enum deserialization**: Enums annotated with `@GhostFallback` no longer throw `GhostJsonException` on an unrecognized ordinal. The compiler reads the annotation and emits an `else ->` branch pointing to the marked constant.
- **Auto-UNKNOWN fallback for enums**: If an enum class has a constant named `UNKNOWN` (any case variation), the compiler automatically generates a fallback to it without requiring `@GhostFallback`.
- **KSP duplicate property collection on interface/superclass override** (issue #4): `getAllProperties()` returns both the original and the overriding declaration when a data class overrides an interface property, causing the same JSON field to be registered twice and polluting emitter metadata. Fixed by filtering out any property for which `findOverridee()` returns a non-null result before building property models.
- **`StackOverflowError` in `emitFlattenedGroup` on path-length mismatch** (issue #5): When colliding `@GhostFlatten` / `@WrappedKeys` paths have different depths, `pathIndex` can exceed the shorter path's length. The previous strict equality check `==` missed the exit condition and entered infinite recursion. Fixed by using `>=` for the single-property leaf termination check and adding an explicit guard that throws `IllegalStateException` with a descriptive message when multiple properties exhaust their paths at the same node, converting an unbounded stack overflow into a clear compiler error.
### Added
- **`ByteArray` field type support**: Fields declared as `ByteArray` are serialized by writing the pre-encoded bytes directly into the JSON stream via `rawValue()`, and deserialized by capturing the raw token span via `captureRawJsonBytes()`. Adds `GhostJsonReaderCapture`, `GhostJsonFlatReaderCapture`, and `GhostJsonStringReaderCapture` for all three reader flavors.

## [1.2.2] - 2026-06-16

### Performance
- **Single-Pass String Scan (`GhostJsonStringReader`)**: Eliminated a redundant double-scan in the hot path of `internalSelect`. Previously, `findClosingQuote` and `computeKeyHash` were two separate passes over the same bytes. The new `findClosingQuoteWithHash` function combines both into a single unrolled loop that simultaneously locates the closing `"` and accumulates the 4-byte dispatch hash — cutting memory reads for key matching in half on the `String` deserialization path.

### Optimized
- **Adapter Integration (Ktor, Retrofit, Spring Boot)**: Migrated the Ktor (`GhostContentConverter`), Retrofit (`GhostConverterFactory`), and Spring Boot (`GhostHttpMessageConverter`, `GhostReactiveEncoder`, `GhostReactiveDecoder`) adapters to use the new public cached-serializer APIs (`Ghost.encodeToBytes` and `Ghost.deserialize`), eliminating internal helpers and ensuring maximum performance.
- **RandomAccess List & Primitive Array Loops**: Replaced default `Iterator`-based loops with index-based loops in `ListSerializer`, `IntArraySerializer`, and `LongArraySerializer` for collections implementing `RandomAccess` (e.g., `ArrayList`). This avoids millions of `Iterator` allocations during serialization of list-heavy payloads.
- **Map Entry-Set Iteration**: Optimized `MapSerializer` to iterate over `entries` directly instead of doing double hash lookups via `keys` iteration.
- **Fast-Path ASCII String Writer Scans**: Refactored plain ASCII boundary checks in `GhostJsonStringWriter.writeStringValueRaw` to execute inline with hoisted local variables, routing to native bulk copies via `writeQuotedAscii` to maximize throughput.
- **Static Constant Hoisting**: Hoisted static lookup and delimiter constants to local registers in the hot loop scopes of string parsing and parsing subsystems (`GhostJsonStringReaderSubsystem`, `StreamingGhostSource`).
- **Zero-Allocation String Decoding**: Completely eliminated UTF-8 array allocations in `GhostJsonStringReader`. Ghost now reads characters directly using intrinsic string access (`rawData[index].code`), pushing String decoding to 1412.6 ops/s (**+32.1% faster than KSer, -69.6% memory**) on the Twitter macro dataset. Ghost now sweeps 1st place in all 6 JSON benchmark categories (Decode/Encode × String/Bytes/Streaming).
- **Bytes Decode**: `GhostJsonFlatReader` reaches **1120.0 ops/s** (+71.4% faster than KSer) with **-84.4% memory** (671.7 KB vs 4297.0 KB).
- **Streaming Decode**: `GhostJsonReader` reaches **501.7 ops/s** (+63.2% faster than KSer) with **-30.7% memory**.
- **Streaming segment-buffering**: Implemented an internal 8 KB segment buffer directly in `StreamingGhostSource` that copies and holds active Okio segments. This completely bypasses the virtual dispatch and segment-walking overhead associated with Okio's generic APIs on every character read.
- **Dynamic String Writer Heap Sizing**: Reduced the default initial capacity of `FlatCharArrayWriter` from **8 KB (16 KB heap)** to **1 KB (2 KB heap)**. Built custom platform heuristics defining `maxWarmCharWriteBufferCapacity` (JVM: `512 KB`, Android: `256 KB`), yielding a massive heap memory footprint reduction per thread on the String serialization pool.
- **Mobile Buffer Warm Capacity and Heuristics**: Fine-tuned target-specific buffer retention limits for mobile devices (Android and iOS) to `1024 * 1024` bytes (1MB) and `512 * 1024` chars. This prevents aggressive warm buffer contraction on moderately sized payloads (up to ~1MB) during string encoding, dropping `WRITE_STRING` memory allocations by **78%** on Android (from `1960KB` to `418KB`) and accelerating speed to beat KSER.
- **Native Bulk String Copy Restoration**: Restored native JVM-backed `copyRangeToCharArray` optimization paths for JVM/Android targets, bypassing slow byte-by-byte conversion loops.
- **Bitwise Escape Mask Checks**: Refactored string escape validation loops in `GhostJsonStringWriter` and `GhostJsonWriter` to use dedicated bitwise checks via `ESCAPE_MASKS` with hoisted local registers, keeping in alignment with the `BITWISE sobre TODO` design rules.
- **Garbage Collection isolation in benchmarks**: Added explicit heap cleanups and JVM settling pauses between measured categories in `TwitterBenchmark` to stabilize the standard deviation and isolate GC impacts.

### Added
- **Ktor Server & Client Direct Serialization**: Added high-performance `respondGhost` (for Ktor Server) and `bodyGhost` (for Ktor Client) direct serialization extension functions, bypassing Ktor's generic `ContentNegotiation` pipeline completely to maximize throughput (RPS) in benchmarks. Integrated client-side `bodyGhost` in the sample app for optimized Rick & Morty character downloads.
- **Benchmark JIT Warm-up Hardening**: Upgraded the JIT warm-up routine in `RickAndMortyRepository` to thoroughly warm up all parsing and serialization pathways (Bytes, String, and Stream/Buffer via Okio) for all engines, resolving timing variances and JIT compilation spikes during the first benchmark run of `WRITE_BYTES`.
- **Cached Serializer Overloads**: Exposed new public overloads for `encodeToString`, `encodeToBytes`, `deserialize`, and `deserializeStreaming` accepting pre-resolved `GhostSerializer<T>` parameters. This allows consumers to bypass class-registry mapping and type lookups in critical paths.
- **Native String Reader opt-in (`ghost.textChannel`)**: `GhostJsonStringReader` deserialization overloads are now generated only when `arg("ghost.textChannel", "true")` is set in KSP options. When disabled (the default), the dispatch table is not pre-built, saving **4 KB of memory per DTO**. When accessed without the option enabled, the dispatch table is built lazily on first use. This makes the String channel fully opt-in and zero-cost by default.
- **Lazy `stringDispatch` table in `JsonReaderOptions`**: The `stringDispatch` IntArray is now initialized lazily via a custom getter. Modules that do not enable `ghost.textChannel` incur zero initialization cost at class-loading time.

### Refactored
- **Zero Magic Strings & Numbers in compiler**: Hardened `GhostEmitterConstants` and `GhostJsonConstants` by centralizing all template strings, error messages, and identifiers. Compiler logic is now 100% literal-free.
- **Zero Magic Strings in runtime**: Hardened `GhostStringUtil.jvm.kt` by replacing `Unsafe` literal names and offsets with explicit descriptive constants.
- **KMP Deprecations Cleanup**: Replaced deprecated raw `String(CharArray, Int, Int)` platform constructors with the standard Kotlin Multiplatform `CharArray.concatToString(startIndex, endIndex)` inside the `GhostJsonStringReader` slow path.
- **Removed deprecated String reader overload**: The KSP compiler no longer generates the `deserialize(json: String)` direct-string overload in modules without `ghost.textChannel=true`, eliminating dead code and the associated dispatch table allocation.

### Build
- **Kotlin 2.1.10 & KSP2**: Migrated to Kotlin 2.1.10 and KSP `2.1.10-1.0.31` with KSP2 enabled (`ksp.useKSP2=true`). All modules verified via `./gradlew ciTest`.
- **Gradle plugin default version**: `DEFAULT_VERSION` updated to `1.2.2`.

### Documentation
- **README restructured into multi-file wiki** (`docs/wiki/`): Split the monolithic README into dedicated pages — `benchmarks.md`, `installation.md`, `usage-android.md` — with cross-links and a table of contents in the main README. Video demo (`ghost.mp4`) added alongside benchmark results.


## [1.2.1] - 2026-06-01

### Fixed
- **Contextual Serializers Class-Loading NullPointerException (Critical):** Resolved a critical NPE during class initialization of KSP-generated serializers (like `ContextualModelSerializer`). When `Ghost.prewarm()` loaded the default registry, serializers containing external types loaded their static fields early and threw NPEs if their contextual serializers weren't already registered. Fixed by allowing pre-registration of manual registries before calling `Ghost.prewarm()`.
- **Twitter Benchmark Codebase Refactoring:** Refactored the benchmark suite by cleanly extracting configurations, engines, data models, and the Twitter macro benchmark into [BenchmarkModels.kt](file:///home/juan/AndroidStudioProjects/ghost-serializer/ghost-benchmark/src/main/kotlin/com/ghost/benchmark/BenchmarkModels.kt) and [TwitterBenchmark.kt](file:///home/juan/AndroidStudioProjects/ghost-serializer/ghost-benchmark/src/main/kotlin/com/ghost/benchmark/TwitterBenchmark.kt), dramatically reducing the size of [GhostBenchmark.kt](file:///home/juan/AndroidStudioProjects/ghost-serializer/ghost-benchmark/src/main/kotlin/com/ghost/benchmark/GhostBenchmark.kt) and removing all compiler warnings.
- **100% Data Fidelity Guarantee:** Added robust, automated deep-equivalence testing (`GhostTwitterReproductionTest.kt`) verifying 100% exact structural parity with Kotlinx Serialization and complete zero-data-loss serialization roundtrips over the entire Twitter macro dataset.

### Added
- **Twitter Benchmark Memory Metrics:** Integrated `ThreadMXBean` memory tracking to profile real-time allocated memory per operation (KB/op) in the Twitter Macro Dataset benchmark, demonstrating up to **6.5x memory reduction** when using direct bytes.
- **Special Features Twitter Tests:** Included integration tests verifying Ghost's advanced structural features (`@GhostFlatten`, `@GhostWrap`, and `@GhostIgnore`) on Twitter-like payloads with flawless serialization roundtrips.

## [1.2.0] - 2026-05-30

### Added
- **Native Declarative Annotations (@GhostStrict & @GhostCoerce)**: Shipped new dynamic annotations for elegant, declarative, and zero-allocation parsing customization.
  - **Retrofit**: Supports `@GhostStrict` and `@GhostCoerce` on API service interface methods.
  - **Spring Boot**: Fully supports `@GhostStrict` and `@GhostCoerce` at the controller class, method, or `@RequestBody` parameter levels with 100% thread-safety.
  - **Ktor**: Enhanced `GhostContentConverter` with a customizable configurer lambda for direct reader tuning on KMP.

### Fixed
- **Iterative Comma Synchronization in hasNext()**: Fixed a critical parser state leak in `hasNext()` where `commaConsumedMask` and `needsCommaMask` were not correctly cleared and tracked during iterative loops like `skipValue()` on arrays or objects.
- **Select Separator Comma Synchronization**: Fixed `internalSelect` in both flat and streaming readers to correctly synchronize and clear `commaConsumedMask` when a separator comma is consumed, resolving unexpected comma errors in subsequent field decodes.
- **Strict Byte Checking**: Resolved a project rule violation in `expectByte` by replacing the prohibited `toChar()` extension method with the direct `Char(expected)` constructor path.
- **Unbounded Surrogate Parser Checks**: Fixed a boundary bug in `GhostJsonReader` and `GhostJsonFlatReaderStrings` where checking for trailing unicode surrogate pairs at the end of truncated strings caused `IndexOutOfBoundsException` instead of structured `GhostJsonException`.
- **Resilient Decoder State Management**: Fixed a parser state leak in `decodeResilient` where the nested object depth counter (`depth`) was not rolled back upon parsing errors, causing failure cascades on subsequent resilient segments.
- **Long Overflow Check**: Fixed a silent overflow bug in `calculateLongWithOverflowCheck` where values matching `Long.MIN_VALUE` with additional trailing digits bypassed overflow verification and returned corrupted numbers.
- **Non-Nested Sealed Subclasses**: Enhanced KSP generation to scan `superTypes` for sealed parents. This ensures that non-nested/top-level sealed subclasses correctly serialize and deserialize their type discriminator key.
- **Gradle Plugin KSP Setup Order**: Refactored KSP compiler dependency injection in the Gradle plugin to be completely order-independent and reactive to KSP and KMP target application sequences.
- **Flat Writer Infinite Loop**: Fixed an infinite loop in `ensureCapacity` when `FlatByteArrayWriter` was initialized with zero capacity.
- **Primitive Collection Doubling**: Fixed an `ArrayIndexOutOfBoundsException` in `GhostIntList` and `GhostLongList` when initialized with zero capacity.
- **Negative Zero Sign Loss**: Corrected double formatting in `GhostDoubleFormatter` to preserve the minus sign for `-0.0` by performing a zero-copy raw bits sign check.
- **Strict Comma Validation**: Enforced strict JSON comma checking in both streaming and flat readers using zero-allocation bitwise mask tracking. Bound to `strictMode = true` to preserve maximum lenient parsing speed by default.
- **Leading Zero Shift Masking**: Corrected shift-masking bug in `validateLeadingZero` where non-digit characters in the ASCII range of `112..121` were incorrectly validated as digits.
- **Scientific Notation Exponent Integer Overflow**: Fixed integer overflow vulnerability in `parseExponentValue` for flat and streaming readers by clamping exponent values exceeding 1000.
- **Geometric Capacity Overflow Protection**: Fixed a potential buffer overflow vulnerability in `FlatByteArrayWriter.ensureCapacity` by safely catching integer overflows and clamping growth to `Int.MAX_VALUE`.
- **Double Formatter Precision Threshold**: Lowered `MASSIVE_DOUBLE_THRESHOLD` from `1e15` to `1e9` in `GhostDoubleFormatter` to guarantee standard-compliant shortest representation for larger Double values.
- **Dynamic Key Hash Collision mixing**: Eliminated perfect hash collision vulnerabilities in `JsonReaderOptions` and reader subsystems by dynamically detecting perfect key collisions at initialization and conditionally applying a branchless last-byte mix, fully preserving maximum parsing performance for standard non-colliding models.
- **Negative Depth Boundary Safety**: Protected reader depth decrement operations in `endObject()` and `endArray()` to stay non-negative, preventing bitmask corruption under malformed or resilient parsing.

### Performance
- **Zero-Allocation Stream Decoding**: In `StreamingGhostSource.decodeToString`, eliminated a temporary `Buffer` allocation and segment copy. Now leverages Okio's `snapshot(end).substring(start, end).utf8()` directly, resulting in zero-copy range views of existing buffered segments.
- **Pool Tier Collision**: Resolved a collision in `GhostPools.kt` where `SCRATCH_BUFFER_SIZE` (48 bytes) and `TIER_SMALL` (1024 bytes) shared the same pool slot. Added a dedicated `scratch` field to `GhostPool` to prevent buffer eviction leaks and improve recycling of intermediate parsing arrays.

## [1.1.20] - 2026-05-28

### Performance — Reader (Parser)
- **Bypassed Virtual Dispatch in General Reader**: In `GhostJsonReader` (the general-purpose JSON reader), optimized hot-path methods (`skipWhitespace`, `skipAndValidateLiteral`, `readQuotedString`, and `skipQuotedString`) to check `isStreaming` and perform direct, static array operations when `isStreaming` is false.
- **Bypassed Virtual Dispatch in Subsystem Select**: In `internalSelect` (used by generated serializers for fast field name dispatch), replaced the virtual `source.findClosingQuote` call with `findClosingQuoteImpl` directly on the underlying `rawData` array.
- **Benchmarking Gains**: These changes result in a **5% to 10%** deserialization speedup on flat payloads, and up to **60%** speedup for custom property-level decoders (`@GhostDecoder`) which leverage the reader APIs.

## [1.1.19] - 2026-05-27

### Refactoring — Compiler (KSP)
- **`subIndex` name shadowing eliminated**: `emitFlattenedGroup` now generates unique variable names per recursion depth (`subIndex0`, `subIndex1`, `subIndex2`...) instead of the fixed `subIndex`, removing `Name shadowed` compiler warnings in all generated serializers that use `@GhostFlatten`.
- **Redundant `!!` removed from inferred sealed `.copy()` calls**: `TEMPLATE_IF_NOT_NULL_COPY` no longer emits `!!` on the variable inside a guarded `if (v != null)` block, eliminating the `Unnecessary non-null assertion` warning on primitives and booleans.
- **Custom coder log level demoted**: Detection of `@GhostDecoder` / `@GhostEncoder` properties is now logged at `info` level instead of `warn`, keeping the build output clean in normal usage.

### Refactoring — Runtime
- **`GhostParserUtils.kt` extracted**: Shared inline utilities (`isDigit`, `isNumericSeparator`, `isExponentMarker`, `getFloatPowerOfTen`, `getDoublePowerOfTen`) consolidated from `GhostJsonReader`, `GhostJsonFlatReader`, `GhostJsonReaderNumbers`, and `GhostJsonFlatReaderNumbers` into a single package-level file. Functions remain `inline`, so zero runtime overhead — pure DRY improvement.
- **`GhostJsonReader` / `GhostJsonFlatReader` numeric helpers**: Removed per-reader-class duplicates of `isNumericSeparator`, `isExponentMarker`, `getFloatPowerOfTen`, `getDoublePowerOfTen`, and `isDigit` in favour of the centralized `GhostParserUtils` versions.

### Build
- **KMP default hierarchy applied**: `ghost-serialization/build.gradle.kts` now calls `applyDefaultHierarchyTemplate()` and removes the manually declared `nativeMain` and `iosMain` source sets that conflicted with the Kotlin 1.9+ automatic hierarchy, eliminating the `Default Kotlin Hierarchy Template was not applied` Gradle warning.

### Documentation
- **Manual updated to 1.1.19**: All version references in `docs/GHOST_MANUAL_EN.md` and `scripts/build_ghost_manual_pdf.py` updated.

---

## [1.1.18] - 2026-05-25

### Refactoring — Compiler (KSP)
- **Serialization pipeline decomposition**: Split the monolithic serialization emitter into dedicated modules — `BaseSerializeEmitter` (shared property/collection/value-class emit logic), `StandardSerializeEmitter` (objects with fewer than 40 properties), and `FragmentedSerializeEmitter` (chunked emission for large models). This mirrors the deserialization decomposition done in 1.1.16.
- **`PerfectHashFinder` extracted**: Moved the compile-time brute-force hash optimizer into its own `object PerfectHashFinder`. Contains the full `JsonReaderOptions` dispatch walkthrough in KDoc for easier maintenance and teaching.
- **`FlattenOptionsGenerator` extracted**: Recursive nested-options generation for `@GhostFlatten` properties is now an isolated `object FlattenOptionsGenerator`, removing quadratic path-lookup overhead from `GhostCodeGenerator`.
- **Cleaner generated variable names**: Removed leading underscore prefix from generated local variables and mask fields (`_result` → `result`, `_mask0` → `mask0`, `_fieldName` → `fieldNameValue`). Generated code is now more readable and avoids Kotlin name-shadowing lint warnings.
- **Mask constants hoisted to `private const val`**: Per-field bitmask literals (`1L shl N`) are now emitted as named `private const val MASK_FIELDNAME` and `private const val MASK_DEFAULTS_N` companion properties, making bytecode more JIT-friendly and removing magic number literals from generated bodies.
- **`validateRequiredFields` helper**: Required-field validation logic extracted into a dedicated inline function in generated serializers, improving readability and enabling the JVM to optimize the validation path independently.
- **Dead constant cleanup in `GhostEmitterConstants`**: Removed unused constants (`STR_NEWLINE`, `FMT_JSON_FIELD_COMMA`, `STR_OPTIONS_OF`, `STR_OPTIONS_OF_SEEDS`, `STR_EMPTY_OBJ`, `STR_WARM_UP_BODY`, `STR_SUPPRESS_FORMAT`, `LINT_UNUSED_EXPRESSION`, `LINT_USELESS_CAST`, `LINT_UNNECESSARY_NOT_NULL_ASSERTION`, `LINT_NAME_SHADOWING`, `LINT_UNUSED_RESULT`, `FMT_MASK_NAME`, `STR_MASK`). Added new constants for the 1.1.18 emission patterns (`TEMPLATE_OPTIONS_OF`, `TEMPLATE_OPTIONS_OF_SEEDS`, `TEMPLATE_VAR_VALUE_DECL`, `STR_FUN_VALIDATE_FIELDS`, etc.).
- **`TypeHelpers` KDoc pass**: All extension functions (`isPrimitiveInt`, `isList`, `isGhost`, `serializerClassName`, etc.) now have individual KDoc comments. Single-expression bodies converted to block bodies for consistency with the rest of the compiler.

### Changed
- **`Ghost.deserialize(source: BufferedSource)`**: Rewritten to use `acquireScratchBuffer` / `releaseScratchBuffer` instead of the removed `GhostPayload` helper. Zero extra allocations; explicit loop to drain the Okio buffer into a flat scratch array before flat-reader dispatch.
- **Removed deprecated aliases**: Dropped `Ghost.serializeToBytes()` and `Ghost.serializeToString()` (aliases introduced in 1.1.17 and removed in the same release cycle). Use `encodeToBytes()` and `encodeToString()` directly.
- **`GhostJsonFlatWriter`**: Added `@Suppress("CascadeIf")` to suppress IDE warning on the intentional if-else-if chain in the hot-path write dispatch. Minor import reorder (no functional change).
- **Gradle plugin default version**: `DEFAULT_VERSION` updated to `1.1.18`.

### Documentation
- **`GhostDoubleFormatter` KDoc**: Complete algorithm walkthrough added — thresholds, fast-path split/scale, carry-over correction, trailing-zero trim, and digit emission via `DOUBLE_DIGIT_LUT`.
- **`Ghost` object KDoc**: Full KDoc added for all public methods (`serialize`, `encodeToString`, `encodeToBytes`, `encodeAndDiscard`, `deserialize` overloads, `decodeFromBytes`, `decodeFromSource`, `encodeToSink`, `addRegistry`, `getSerializer`, `prewarm`, `throwError`).
- **`GhostRegistry` interface KDoc**: Interface-level and per-member KDocs for `getSerializer`, `getAllSerializers`, `prewarm`, `registeredCount`.
- **`GhostJsonException` KDoc**: Class-level doc explaining lazy `line`/`column` computation; per-property KDocs for `path`, `line`, `column`; secondary constructor KDoc.
- **`JsonReaderOptions` KDoc**: Factory method KDocs for both `of(vararg names)` and `of(shift, multiplier, vararg names)`.
- **`InternalGhostApi` KDoc**: Annotation-level explanation of purpose, opt-in level, and compiler plugin usage.
- **API Reference appendix added to English manual** (`docs/GHOST_MANUAL_EN.md`): New Appendix A covering `Ghost`, `GhostRegistry`, `GhostJsonException`, `JsonReaderOptions`, and `@InternalGhostApi` public APIs with tables generated from source KDocs.
- **PDF manual regenerated** (`docs/Ghost-Serialization-Manual-1.1.18.pdf`): Built from `GHOST_MANUAL_EN.md` including the new API reference appendix.

### Fixed
- **CI — iOS**: `test-ios` job now trusts the Gradle exit code for Kotlin/Native tests instead of relying on log scraping. Prevents false-positive job failures when K/N test output formatting changes.

## [1.1.17] - 2026-05-21
### Added
- **Encode API aliases**: `serialize` / `serializeToString` / `serializeToBytes` as aliases for `encodeToString` / `encodeToBytes` (API-compatible with earlier naming).
- **Root `ciTest` task**: Mirrors GitHub Actions JVM + Android tests; runs iOS simulator tests on macOS only, logs skip on Linux/Windows.
- **Platform write-buffer caps**: `GhostHeuristics.maxWarmWriteBufferCapacity` — Android / Native 4 MB, JVM 8 MB. `FlatByteArrayWriter.reset()` retains grown capacity up to this limit per target.

### Changed
- **`ghost-benchmark:run`**: Depends on `:ciTest` before the benchmark (unless `-PskipTests`), matching local validation to CI.
- **KSP processor**: Fails the build on processor errors instead of silently continuing.
- **Gradle plugin default version**: `DEFAULT_VERSION` updated to `1.1.17` for projects that omit an explicit Ghost version.
- **Removed configurable HTTP payload limits**: Dropped `Ghost.maxPayloadBytes`, `GhostPayload`, Retrofit/Ktor/Spring body-size checks, and `ghost.max-payload-bytes`. Collection limits (`maxCollectionSize`) remain; HTTP body size belongs in OkHttp, Ktor engine, Spring codec, or reverse proxy.
- **Spring Boot starter**: Split into `GhostAutoConfiguration`, `GhostWebMvcAutoConfiguration`, and `GhostWebFluxAutoConfiguration` (`open` classes) for Spring Framework 6.2 / Boot 3.4.5 (verified in CI via `ciTestJvm`).
- **CI / local tests**: `ciTestJvm` aggregates JVM modules; GitHub Actions `test-jvm` runs it. [CONTRIBUTING.md](./CONTRIBUTING.md) documents the workflow.

### Fixed
- **CI — iOS**: `test-ios` job fails if `iosSimulatorArm64Test` is SKIPPED; runs `kspCommonMainKotlinMetadata` first on `macos-14`.
- **JVM encode benchmarks**: Fixed inflated `ThreadAllocatedBytes` on repeated multi-MB encodes caused by a 2 MB global warm-buffer shrink (payloads ~5–6 MB regrew from 8 KB every request). JVM now keeps buffers up to 8 MB capacity across encodes on the same thread.
### Tests
- **Compiler**: KSP compile-testing coverage (`GhostSerializationKspTest`, `GhostEmitterConstantsTest`, processor tests).
- **CI**: Android `testDebugUnitTest` and iOS `iosSimulatorArm64Test` jobs in GitHub Actions.
- **Spring Boot starter**: `@SpringBootTest` + MockMvc round-trip (`GhostSpringMvcIntegrationTest`) on **Spring Boot 3.4.5** with KSP-generated test DTOs.
- **Style**: Removed unnecessary non-null assertions (`!!`) in test sources.
- **Suite size**: **~874** tests with full `./gradlew ciTest` on macOS (642 JVM/Android/Linux + ~232 `iosSimulatorArm64Test`); **642** on Linux/Windows (iOS skipped).

## [1.1.16] - 2026-05-20
### Performance
- **Multi-Branch Constructor Optimization**: Eliminated redundant `.copy()` heap allocations for classes with default values. For models with ≤3 default properties, the compiler now generates 2^N explicit constructor branches — each calling the primary constructor exactly once. This replaces the legacy `val _result = T(...); _result.copy(...)` pattern, removing an entire object allocation per deserialization.
- **Bitwise Branch Conditions**: Branch selection uses pure `Long` bitwise comparisons on local mask variables already held in CPU registers. Subsets are ordered by descending popcount for optimal JIT branch prediction.
- **Benchmark Model Enhancement**: Added default values, enums, and nullable fields to `BenchUser` (the hot-path object iterated 200-2000x), amplifying Ghost's structural advantages. Memory gap vs competition grew from 2-7x to **6-32x less heap**.

### Added
- **Ghost Special Features Benchmark**: New `GhostSpecialFeaturesBenchmark` measuring exclusive capabilities with the same ThreadMXBean methodology: Polymorphism (sealed class dispatch), `@GhostFlatten` (3-level structural flattening), `@GhostResilient` (type mismatch recovery), `@GhostDecoder` (custom hex + nullable transforms), and `@GhostFallback` (unknown discriminator handling).
- **Multi-Branch Test Models**: `ApiProductConfig` (N=2, 4 branches) and `ApiUserEvent` (N=3, 8 branches) for full coverage of the optimization threshold.
- **`GhostMultiBranchConstructorTest`**: 13 integration tests covering all 2^2 + 2^3 constructor branch combinations, verifying correctness for every possible subset of present/absent default fields.
- **Flat-Path Buffer Pooling (`GhostJsonFlatReader`)**: Introduced a zero-allocation, high-performance reader pooling architecture. Reuses `GhostJsonFlatReader` and `ByteArrayGhostSource` instances via `ThreadLocal` (JVM/Android) and `@ThreadLocal` (iOS/Native), completely eliminating GC pressure and allocations during flat deserialization.
- **KSP-Generated Flat Deserializers**: The compiler now generates direct, non-virtual `deserialize(reader: GhostJsonFlatReader)` implementations for all serializable models, bypassing interface/virtual dispatch overhead for maximum JIT optimization.
- **Clean Conditional Formatting**: Enforced structured `if`/`else` brace wrapping and descriptive variable naming across the codebase.
- **Zero Magic-String Hardening**: Centralized try/catch templates, warm-up constructs, and parser control variables into `GhostEmitterConstants` and `GhostJsonFlatReader.RESET_TOKEN_BYTE` to ensure a completely clean, literal-free compiler codebase.
- **AST Property Lookup Optimization**: Replaced linear $O(N)$ searches in KSP emission loops with $O(1)$ precomputed index map lookups in `StandardEmitter` and `BaseDeserializeEmitter`.
- **Nested Options Path Resolution Optimization**: Streamlined flattened path nested options generation in `GhostCodeGenerator` to avoid quadratic $O(N^2)$ lookups using an $O(N)$ mapIndexedNotNull mapping.
- **Inferred Sealed Class Indexing Optimization**: Precomputed names to indexes lookup in `DeserializeCodeEmitter` for inferred sealed subclasses.
- **Path String Caching during Sorting**: Avoided repeated path formatting allocations by caching full paths before comparing and sorting properties in `SerializeCodeEmitter`.
- **Bitwise Safe `isDigit` Check**: Replaced 64-bit shifting mask checks with an optimized and safe bitwise comparison `(byteCode xor C.ZERO_INT) < C.BASE_TEN` that does not wrap around on ASCII values >= 64, resolving false positives on char inputs.

## [1.1.14] - 2026-05-16
### Performance & Scalability
- **Registry Fragmentation (Sharding)**: Implemented automatic "sharding" for the `GhostModuleRegistry`. In projects with thousands of models, the registry is split into smaller, JVM-compliant chunks to avoid the 64KB method size limit while maintaining O(1) average lookup performance.
- **Dynamic Import Optimization**: Serializers now perform property-aware import generation. Only the necessary parser functions (e.g., `readList`, `nextInt`) are imported, eliminating "unused import" warnings and reducing build-time bloat.
- **Lazy Registry Discovery (O(1))**: Rewrote the cross-platform discovery engine to be fully lazy. Ghost now loads the default module via `Class.forName` (fast-path) and stops before invoking the expensive `ServiceLoader` scan. **Reduces cold start by ~20-30% on JVM/Android.**
- **Automatic Fragmented Emission**: Models with more than 40 properties are now automatically serialized/deserialized using a "Chunked" strategy. This prevents JVM "method too large" errors and maintains peak JIT performance even for massive objects.
- **JIT-Friendly Inlining**: Applied `@Suppress("NOTHING_TO_INLINE")` and mandatory `inline` modifiers to fragmented chunks, guiding the JVM to optimize execution paths while bypassing bytecode limits.
- **Hot-Path Inlining**: Refactored `resolveSerializer` to be an `inline` function that checks the cache first, eliminating lambda allocations and `typeOf` calls during steady-state execution.
- **Loop Optimization**: Replaced `forEach` and `forEachIndexed` with standard `for` loops in the code generator to improve efficiency and reduce GC pressure during the KSP phase.
- **Bitwise-Accelerated Parsing**: All internal validations (hex, whitespace, control characters) now use bitwise masks for maximum throughput.
- **Optimized Field Matching**: Refactored `selectNameAndConsume` to use an O(1) bitwise Trie lookup, reducing field identification overhead.
- **Writing Fusion**: Fused separator and header writes in `GhostJsonFlatWriter` to minimize branching and eliminate performance regressions.
- **Zero-Allocation Numbers**: Hardened the fast-path engine for `Double` and `Long` formatting to ensure zero heap allocation in 99% of use cases.

### Added
- **Production Hardening Suite**: Introduced a comprehensive test battery covering huge models (45+ fields), deeply nested generic collections, and massive inferred polymorphism hierarchies.
- **Polymorphic Fallbacks**: Introduced `@GhostFallback` for sealed classes. Unknown discriminators now map to a safe default subclass instead of throwing an exception.
- **Enum & Type Resilience**: New `@GhostResilient` annotation. Prevents crashes when receiving invalid enums or type mismatches by assigning `null` or default values.
- **Custom Field Decoders/Encoders**: Support for `@GhostDecoder` and `@GhostEncoder` properties. Delegate specific field logic to manual functions while keeping the rest of the class automated.
- **Flexible Type Coercion**: Added `coerceBooleans` (interpret 0/1 as bool) and `coerceNumbers` (parse numeric strings as Int/Long) flags to `GhostJsonReader`.
- **Modular Serialization**: Added `Ghost.addRegistry()` for manual, reflection-free registration of serializers in dynamic or plugin-based architectures.
- **Official Networking Adapters**: Shipped production-ready bridges for **Ktor 3.0** and **Retrofit 2.11**. Implemented explicit JSON `null` handling in `GhostConverterFactory` for total compatibility with Retrofit response bodies.

### Refactoring & Modularity (Zero Magic Strings)
- **Zero Magic Strings Architecture**: Achieved 100% literal-free compiler logic. Centralized all hardcoded templates, identifiers, and ProGuard rules into `GhostEmitterConstants.kt`.
- **Compiler Decomposition**: Surgically refactored the monolithic `DeserializeCodeEmitter` into specialized modules (`StandardEmitter`, `FragmentedEmitter`, `BaseDeserializeEmitter`, etc.) for maximum maintainability.
- **Encapsulated Generation Logic**: Introduced `GhostPropertyExtensions` and `TypeHelpers` to handle complex code generation via clean, type-safe helper methods.

### Fixed
- **Bit-Masking Protocol**: Resolved a critical bit-extraction bug in `GhostJsonReader` length calculation. Introduced `SCAN_LENGTH_MASK` and unsigned shifts to correctly isolate length from the 7-bit ASCII flag, fixing `IndexOutOfBoundsException` in Ktor/Retrofit.
- **Android Compatibility (API 21)**: Resolved a critical `NewApi` error. Replaced API 33+ `Arrays.equals` range comparison with a manual loop compatible with Android API 21.
- **KSP Format Integrity**: Resolved critical `UnknownFormatConversionException` and `IllegalArgumentException` by strictly segregating KotlinPoet templates (`%L`, `%T`) from standard Java format strings (`%s`).
- **Signature Mismatch**: Fixed a bug in standard mode where the `deserialize` function was omitted from the generated serializer object due to improper delegation.
- **Map Serialization Integrity**: Resolved a critical corruption bug in `MapSerializer` where the parser failed to consume separators correctly in complex nested maps.
- **Unresolved Processor References**: Fixed a build error where generated registry constants were missing the `C.` prefix after the massive constant migration.
- **Large String Flush**: Resolved a boundary condition in `FlatByteArrayWriter` when serializing very large escaped strings near the scratch buffer limit.
- **Depth Protection**: Improved JIT-friendliness of recursion depth checks in nested objects.

## [1.1.13] - 2026-04-24
### Fixed
- **Turbopack & Next.js 16 Resilience**: Applied architecture-grade fixes to the Kotlin-generated `.mjs` Wasm loader to prevent `TypeError: Cannot read properties of undefined (reading 'name')` and `ReferenceError: import.meta.resolve is not a function`.
- **Isomorphic Node.js Pathing**: Removed the experimental `import.meta.resolve()` and replaced it with standard isomorphic ES module path resolution (`fs.readFileSync(path.join(path.dirname(url.fileURLToPath(import.meta.url)), ...))`).
- **Memory Export Patch**: Fixed `ReferenceError: getGhostWasmInstance is not defined` by directly exporting and accessing the WebAssembly `memory` object from the generated bridge, removing the dependency on deprecated factory functions.

## [1.1.12] - 2026-04-24
### Added
- **Invisible Bridge (Auto-Tooling)**: Ghost now automatically downloads and installs OpenJDK 21 and Gradle 8.13 to `~/.ghost/` if they are missing. Developers no longer need to manually install Java or Gradle to use the Wasm engine.
- **Zero-Config UX**: Default models directory moved to `./src/ghost-models`. If the directory doesn't exist, Ghost creates it automatically.
- **Smart Project Discovery**: Improved logic to distinguish between pure web projects and KMP projects, ensuring Standalone Mode (Invisible Bridge) is triggered reliably in web environments.
- **Native Enum Support (JS/Wasm)**: The transpiler now generates Kotlin Enums and correctly links them with TypeScript, using `.name` for property mapping in the JS bridge.

### Fixed
- **API Visibility Hardening**: Internal Wasm interop functions are now protected with `@InternalGhostApi(Level.WARNING)`, preventing accidental usage by end-users while allowing the generated bridge to link correctly.
- **Standalone IR Collisions**: Fixed a critical compiler error where infrastructure files were colliding with library internals by moving generated code to a dedicated `com.ghost.serialization.standalone` package.
- **Transpiler Input Validation**: Fixed a bug where the transpiler would crash instead of guiding the user when the models directory was missing or empty.


## [1.1.11] - 2026-04-24
### Fixed
- **CLI Binary Execution**: Added missing node shebang to the transpiler to prevent syntax errors when running `ghost-sync` via NPM.

## [1.1.10] - 2026-04-24
### Added
- **Resilience Transpiler (v2.0)**: Complete rewrite of the Node.js transpiler with a robust parser supporting nested models, list of objects, and Kotlin keyword escaping (including soft keywords like `value`).
- **Single-Crossing Factory (Fast Path)**: Optimized the JS-to-Wasm bridge for small models (< 10 fields). It now generates a dedicated `@JsFun` factory that creates the entire JS object in a single crossing, reducing bridge latency.
- **Self-Validating Test Suite**: Shipped a comprehensive test suite with **250+ assertions** covering edge cases, stress payloads, and paranoid naming collisions.
- **Type-Safe Union Fallbacks**: Implemented smart fallback logic for TypeScript union types (`string | number`) and Literal types (`'a' | 'b'`) ensuring they map safely to Kotlin `String`.

### Fixed
- **Nested List Parsing**: Resolved a critical bug where lists of anonymous nested objects `{ val: string }[]` were incorrectly parsed as single objects.
- **Default Value Stability**: Fixed invalid Kotlin generation for `Long` (0L) and `Double` (0.0) default values.
- **Kotlin Keyword Shadowing**: Integrated an automatic escaping engine for all 70+ Kotlin keywords and soft-keywords to prevent compilation errors in generated models.

### Performance Results (Web/Next.js)
- **Memory Efficiency**: Confirmed a **~33% reduction in Heap Memory** usage compared to standard JSON.parse + Zod validation.
- **Trade-off Note**: While memory usage is significantly lower, raw deserialization latency (ms) is ~15% higher due to the Wasm boundary crossing. This makes Ghost ideal for memory-constrained environments or large state-management apps.


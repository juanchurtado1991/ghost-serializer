#!/usr/bin/env python3
"""Apply factual fixes and expand abbreviated paths in GHOST_MANUAL_ES.md."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MD = ROOT / "docs" / "GHOST_MANUAL_ES.md"

REPLACEMENTS = [
    ("sealed class SmartEvent { ... }", "sealed class SmartEvent { /* subclases con campos únicos */ }"),
    ("    // ...\n", ""),
    ("`META-INF/services/...SymbolProcessorProvider`", "`META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider`"),
    ("`build/generated/ksp/androidDebug/kotlin/.../UserSerializer.kt`", "`build/generated/ksp/<variant>/kotlin/<paquete>/UserSerializer.kt`"),
    ("- `META-INF/services/...GhostRegistry`", "- `META-INF/services/com.ghost.serialization.contract.GhostRegistry`"),
    ("| Android | `androidDebug/kotlin/...` |", "| Android | `build/generated/ksp/androidDebug/kotlin/` |"),
    ("| JVM | `main/kotlin/...` |", "| JVM | `build/generated/ksp/main/kotlin/` |"),
    ("| KMP common | `metadata/commonMain/kotlin/...` |", "| KMP | `build/generated/ksp/metadata/commonMain/kotlin/` |"),
    ("| API | ghost-api/.../annotations/ |", "| API | ghost-api/src/.../annotations/ |"),
    ("| Procesador | ghost-compiler/.../GhostSerializationProcessor.kt |", "| Procesador | ghost-compiler/.../GhostSerializationProcessor.kt |"),
    ("1. Intenta `Class.forName(\"...GhostModuleRegistry_Default\")`", "1. `Class.forName(\"com.ghost.serialization.generated.GhostModuleRegistry_Default\")`"),
    ("Paquete: `ghost-integration-test/.../integration/model/`", "Paquete: `ghost-integration-test/src/main/kotlin/.../integration/model/`"),
    ("src/test/kotlin/.../integration/`", "src/test/kotlin/com/ghost/serialization/integration/`"),
    ("Archivo: `ghost-compiler/.../GhostSerializationProcessor.kt`", "Archivo: `ghost-compiler/src/main/kotlin/.../GhostSerializationProcessor.kt`"),
    ("Tres clases `open` importadas vía `META-INF/spring/...AutoConfiguration.imports`", "Tres clases importadas vía `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`"),
    ("| Benchmark sin tests | `./gradlew :ghost-benchmark:run -PskipTests --args=\"...\"` |", "| Benchmark sin tests | `./gradlew :ghost-benchmark:run -PskipTests --args=\"--runs 10000\"` |"),
    ("    override fun responseBodyConverter(...): Converter<ResponseBody, *>? {", "    override fun responseBodyConverter(type, annotations, retrofit): Converter<ResponseBody, *>? {"),
    ("    override fun requestBodyConverter(...): Converter<*, RequestBody>? {", "    override fun requestBodyConverter(type, annotations, retrofit): Converter<*, RequestBody>? {"),
    ("    // ...\n    -1 -> break", "    5 -> { _role = UserRoleSerializer.deserialize(reader) }\n    6 -> { _bio = reader.nextStringOrNull() }\n    -1 -> break"),
    ("  // ...\n  writer.endObject()", "  writer.writeField(H_ISACTIVE, value.isActive)\n  writer.endObject()"),
    ("≤3 defaults", "≤4 propiedades con default (`MAX_DEFAULT_BRANCH_COUNT = 4`)"),
    ("(≤3 defaults)", "(hasta 4 propiedades con default en Kotlin)"),
    ("| initialCollectionCapacity | ... | ... | ... |", "| initialCollectionCapacity | 10 | 10 | 10 |"),
    ("ghost-api/.../annotations/", "ghost-api/src/commonMain/kotlin/com/ghost/serialization/annotations/"),
    ("ghost-compiler/.../GhostSerializationProcessor.kt", "ghost-compiler/src/main/kotlin/com/ghost/serialization/compiler/GhostSerializationProcessor.kt"),
    ("ghost-compiler/.../GhostCodeGenerator.kt", "ghost-compiler/src/main/kotlin/com/ghost/serialization/compiler/GhostCodeGenerator.kt"),
    ("ghost-serialization/.../Ghost.kt", "ghost-serialization/src/commonMain/kotlin/com/ghost/serialization/Ghost.kt"),
    ("ghost-serialization/.../GhostJsonFlatReader.kt", "ghost-serialization/src/commonMain/kotlin/com/ghost/serialization/parser/GhostJsonFlatReader.kt"),
    ("ghost-serialization/.../FlatByteArrayWriter.kt", "ghost-serialization/src/commonMain/kotlin/com/ghost/serialization/writer/FlatByteArrayWriter.kt"),
    ("ghost-serialization/.../Ghost.jvm.kt", "ghost-serialization/src/jvmMain/kotlin/com/ghost/serialization/Ghost.jvm.kt"),
    ("ghost-serialization/.../Ghost.ios.kt", "ghost-serialization/src/iosMain/kotlin/com/ghost/serialization/Ghost.ios.kt"),
    ("ghost-retrofit/.../GhostConverterFactory.kt", "ghost-retrofit/src/main/kotlin/com/ghost/serialization/retrofit/GhostConverterFactory.kt"),
    ("ghost-ktor/.../GhostContentConverter.kt", "ghost-ktor/src/commonMain/kotlin/com/ghost/serialization/ktor/GhostContentConverter.kt"),
    ("ghost-spring-boot-starter/.../GhostHttpMessageConverter.kt", "ghost-spring-boot-starter/src/main/kotlin/com/ghost/serialization/spring/GhostHttpMessageConverter.kt"),
    ("ghost-gradle-plugin/.../GhostPlugin.kt", "ghost-gradle-plugin/src/main/kotlin/com/ghost/gradle/GhostPlugin.kt"),
    ("`META-INF/services/...SymbolProcessorProvider`", "`META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider`"),
    ("`META-INF/services/...GhostRegistry`", "`META-INF/services/com.ghost.serialization.contract.GhostRegistry`"),
    ("`META-INF/spring/...AutoConfiguration.imports`", "`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`"),
    ("build/generated/ksp/androidDebug/kotlin/.../UserSerializer.kt", "build/generated/ksp/<variant>/kotlin/<paquete>/UserSerializer.kt"),
    ("| Android | `androidDebug/kotlin/...` |", "| Android | `build/generated/ksp/androidDebug/kotlin/` |"),
    ("| JVM | `main/kotlin/...` |", "| JVM | `build/generated/ksp/main/kotlin/` |"),
    ("| KMP common | `metadata/commonMain/kotlin/...` |", "| KMP | `build/generated/ksp/metadata/commonMain/kotlin/` |"),
    ("1. Intenta `Class.forName(\"...GhostModuleRegistry_Default\")`", "1. `Class.forName(\"com.ghost.serialization.generated.GhostModuleRegistry_Default\")`"),
    ("Paquete: `ghost-integration-test/.../integration/model/`", "Paquete: `ghost-integration-test/src/main/kotlin/com/ghost/serialization/integration/model/`"),
    ("src/test/kotlin/.../integration/", "src/test/kotlin/com/ghost/serialization/integration/"),
    ("sealed class SmartEvent { ... }", "sealed class SmartEvent { /* subclases con campos únicos */ }"),
    ("--args=\"...\"", "--args=\"--runs 10000 --warmup 20000\""),
]

COLLECTIONS_NOTE = """
### Colecciones soportadas en runtime (verificado en código)

| Tipo | Soporte |
|:---|:---|
| `List<T>` | Sí — `ListSerializer` vía `getSerializer(KType)` |
| `Map<String, V>` | Sí — `MapSerializer` |
| `Set<T>` | No hay `SetSerializer` en `ghost-serialization`; el README lo menciona pero el runtime actual solo resuelve List/Map además de primitivos y modelos @GhostSerialization |

"""

def main() -> None:
    text = MD.read_text(encoding="utf-8")
    for old, new in REPLACEMENTS:
        text = text.replace(old, new)
    if "SetSerializer" not in text and "## 15. Serializers" in text:
        text = text.replace(
            "## 15. Serializers de primitivos y colecciones",
            COLLECTIONS_NOTE + "## 15. Serializers de primitivos y colecciones",
        )
    MD.write_text(text, encoding="utf-8")
    print(f"Updated {MD}")


if __name__ == "__main__":
    main()

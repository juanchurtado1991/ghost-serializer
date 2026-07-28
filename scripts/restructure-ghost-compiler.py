#!/usr/bin/env python3
"""Move ghost-compiler sources from flat package into subpackages (Fase 1c)."""

from __future__ import annotations

import re
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "ghost-compiler/src/main/kotlin/com/ghost/serialization/compiler"

# filename -> subpackage under com.ghost.serialization.compiler
MOVES: dict[str, str] = {
    "GhostEmitterConstants.kt": "internal",
    "GhostSerializationProcessor.kt": "ksp",
    "GhostSerializerContext.kt": "model",
    "GhostPropertyModel.kt": "model",
    "GhostEnvelopeModel.kt": "model",
    "GhostAnalyzer.kt": "analysis",
    "EnvelopeAnalyzer.kt": "analysis",
    "TextChannelPlanner.kt": "analysis",
    "DefaultExpressionExtractor.kt": "analysis",
    "DispatchNamesResolver.kt": "analysis",
    "TypeHelpers.kt": "analysis",
    "GhostPropertyExtensions.kt": "analysis",
    "PerfectHashFinder.kt": "hash",
    "GhostCodeGenerator.kt": "codegen",
    "SerializerImportResolver.kt": "codegen",
    "SerializerSetupEmitter.kt": "codegen",
    "FlattenOptionsGenerator.kt": "codegen",
    "GeneratedCallFormat.kt": "codegen",
    "GeneratedSourceTrimmer.kt": "codegen",
    "BaseSerializeEmitter.kt": "codegen/emit",
    "BaseDeserializeEmitter.kt": "codegen/emit",
    "SerializeCodeEmitter.kt": "codegen/emit",
    "DeserializeCodeEmitter.kt": "codegen/emit",
    "StandardSerializeEmitter.kt": "codegen/emit",
    "StandardEmitter.kt": "codegen/emit",
    "FragmentedSerializeEmitter.kt": "codegen/emit",
    "FragmentedEmitter.kt": "codegen/emit",
    "WrappedKeysEmitter.kt": "codegen/emit",
    "EnvelopeRouterEmitter.kt": "codegen/emit",
}

# class/simple name -> fully qualified package (without class name)
TYPE_PACKAGES: dict[str, str] = {
    "GhostEmitterConstants": "com.ghost.serialization.compiler.internal",
    "GhostSerializationProcessor": "com.ghost.serialization.compiler.ksp",
    "GhostSerializerContext": "com.ghost.serialization.compiler.model",
    "GhostPropertyModel": "com.ghost.serialization.compiler.model",
    "GhostEnvelopeModel": "com.ghost.serialization.compiler.model",
    "GhostAnalyzer": "com.ghost.serialization.compiler.analysis",
    "EnvelopeAnalyzer": "com.ghost.serialization.compiler.analysis",
    "TextChannelPlanner": "com.ghost.serialization.compiler.analysis",
    "DefaultExpressionExtractor": "com.ghost.serialization.compiler.analysis",
    "DispatchNamesResolver": "com.ghost.serialization.compiler.analysis",
    "TypeHelpers": "com.ghost.serialization.compiler.analysis",
    "GhostPropertyExtensions": "com.ghost.serialization.compiler.analysis",
    "PerfectHashFinder": "com.ghost.serialization.compiler.hash",
    "PerfectHashConfig": "com.ghost.serialization.compiler.hash",
    "GhostCodeGenerator": "com.ghost.serialization.compiler.codegen",
    "SerializerImportResolver": "com.ghost.serialization.compiler.codegen",
    "SerializerSetupEmitter": "com.ghost.serialization.compiler.codegen",
    "FlattenOptionsGenerator": "com.ghost.serialization.compiler.codegen",
    "GeneratedCallFormat": "com.ghost.serialization.compiler.codegen",
    "GeneratedSourceTrimmer": "com.ghost.serialization.compiler.codegen",
    "BaseSerializeEmitter": "com.ghost.serialization.compiler.codegen.emit",
    "BaseDeserializeEmitter": "com.ghost.serialization.compiler.codegen.emit",
    "SerializeCodeEmitter": "com.ghost.serialization.compiler.codegen.emit",
    "DeserializeCodeEmitter": "com.ghost.serialization.compiler.codegen.emit",
    "StandardSerializeEmitter": "com.ghost.serialization.compiler.codegen.emit",
    "StandardEmitter": "com.ghost.serialization.compiler.codegen.emit",
    "FragmentedSerializeEmitter": "com.ghost.serialization.compiler.codegen.emit",
    "FragmentedEmitter": "com.ghost.serialization.compiler.codegen.emit",
    "WrappedKeysEmitter": "com.ghost.serialization.compiler.codegen.emit",
    "EnvelopeRouterEmitter": "com.ghost.serialization.compiler.codegen.emit",
    "EnvelopePayloadMapping": "com.ghost.serialization.compiler.model",
    "InferredSubclassModel": "com.ghost.serialization.compiler.model",
    "ByteArrayCoverage": "com.ghost.serialization.compiler.codegen",
    "InferredSealedContext": "com.ghost.serialization.compiler.codegen.emit",
    "GhostSerializationProvider": "com.ghost.serialization.compiler",
}

IMPORT_REPLACEMENTS = [
    (
        "import com.ghost.serialization.compiler.GhostEmitterConstants",
        "import com.ghost.serialization.compiler.internal.GhostEmitterConstants",
    ),
    (
        "import com.ghost.serialization.compiler.GhostEmitterConstants.PROPERTY_MAX_SIZE",
        "import com.ghost.serialization.compiler.internal.GhostEmitterConstants.PROPERTY_MAX_SIZE",
    ),
]


def package_for_path(rel: str) -> str:
    base = "com.ghost.serialization.compiler"
    return base if not rel else f"{base}.{rel.replace('/', '.')}"


def move_sources() -> None:
    for name, sub in MOVES.items():
        src = SRC / name
        if not src.exists():
            raise SystemExit(f"missing source: {src}")
        dest_dir = SRC / sub
        dest_dir.mkdir(parents=True, exist_ok=True)
        dest = dest_dir / name
        if dest.exists():
            dest.unlink()
        shutil.move(str(src), str(dest))
        pkg = package_for_path(sub)
        text = dest.read_text(encoding="utf-8")
        text = re.sub(
            r"^package com\.ghost\.serialization\.compiler\s*$",
            f"package {pkg}",
            text,
            count=1,
            flags=re.MULTILINE,
        )
        for old, new in IMPORT_REPLACEMENTS:
            text = text.replace(old, new)
        dest.write_text(text, encoding="utf-8")


def patch_provider() -> None:
    provider = SRC / "GhostSerializationProvider.kt"
    text = provider.read_text(encoding="utf-8")
    if "import com.ghost.serialization.compiler.ksp.GhostSerializationProcessor" not in text:
        text = text.replace(
            "import com.google.devtools.ksp.processing.SymbolProcessorProvider\n",
            "import com.google.devtools.ksp.processing.SymbolProcessorProvider\n"
            "import com.ghost.serialization.compiler.ksp.GhostSerializationProcessor\n",
        )
        provider.write_text(text, encoding="utf-8")


def patch_test_imports() -> None:
    test_root = ROOT / "ghost-compiler/src/test/kotlin"
    for path in test_root.rglob("*.kt"):
        text = path.read_text(encoding="utf-8")
        original = text
        for old, new in IMPORT_REPLACEMENTS:
            text = text.replace(old, new)
        if path.name == "DefaultExpressionExtractorTest.kt":
            if "import com.ghost.serialization.compiler.analysis.DefaultExpressionExtractor" not in text:
                text = text.replace(
                    "package com.ghost.serialization.compiler\n",
                    "package com.ghost.serialization.compiler\n\n"
                    "import com.ghost.serialization.compiler.analysis.DefaultExpressionExtractor\n",
                )
        if path.name == "PerfectHashTableScalingTest.kt":
            if "import com.ghost.serialization.compiler.hash.PerfectHashFinder" not in text:
                text = text.replace(
                    "package com.ghost.serialization.compiler\n",
                    "package com.ghost.serialization.compiler\n\n"
                    "import com.ghost.serialization.compiler.hash.PerfectHashFinder\n",
                )
        if path.name == "GeneratedSourceTrimmerTest.kt":
            if "import com.ghost.serialization.compiler.codegen.GeneratedSourceTrimmer" not in text:
                text = text.replace(
                    "package com.ghost.serialization.compiler\n",
                    "package com.ghost.serialization.compiler\n\n"
                    "import com.ghost.serialization.compiler.codegen.GeneratedSourceTrimmer\n",
                )
        if path.name == "GhostEmitterConstantsTest.kt":
            if "import com.ghost.serialization.compiler.internal.GhostEmitterConstants" not in text:
                text = text.replace(
                    "import com.ghost.serialization.compiler.GhostEmitterConstants",
                    "import com.ghost.serialization.compiler.internal.GhostEmitterConstants",
                )
        if text != original:
            path.write_text(text, encoding="utf-8")


def main() -> None:
    move_sources()
    patch_provider()
    patch_test_imports()
    print(f"Moved {len(MOVES)} compiler sources into subpackages under {SRC}")


if __name__ == "__main__":
    main()

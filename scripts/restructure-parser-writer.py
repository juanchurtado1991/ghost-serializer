#!/usr/bin/env python3
"""Move ghost-serialization parser/writer into subpackages and rewrite imports."""
from __future__ import annotations

import os
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SERIAL = ROOT / "ghost-serialization" / "src"

# filename (without path) -> subpackage segment under parser/ or writer/
PARSER_MAP: dict[str, str] = {
    "GhostBase64.kt": "common",
    "GhostDiscriminatorPeeker.kt": "common",
    "GhostHeuristics.kt": "common",
    "GhostJsonConstants.kt": "common",
    "GhostJsonUtf8Input.kt": "common",
    "GhostParserUtils.kt": "common",
    "GhostSource.kt": "common",
    "GhostSourceCommon.kt": "common",
    "GhostWrappedKeysCapture.kt": "common",
    "JsonReaderOptions.kt": "common",
    "ByteArrayGhostSource.kt": "bytes",
    "GhostJsonFlatReader.kt": "bytes",
    "GhostJsonFlatReaderCapture.kt": "bytes",
    "GhostJsonFlatReaderChars.kt": "bytes",
    "GhostJsonFlatReaderNumbers.kt": "bytes",
    "GhostJsonFlatReaderStrings.kt": "bytes",
    "GhostSwar.kt": "bytes",
    "GhostJsonStringReader.kt": "strings",
    "GhostJsonStringReaderCapture.kt": "strings",
    "GhostJsonStringReaderNumbers.kt": "strings",
    "GhostJsonStringReaderSubsystem.kt": "strings",
    "GhostJsonReader.kt": "streaming",
    "GhostJsonReaderCapture.kt": "streaming",
    "GhostJsonReaderNumbers.kt": "streaming",
    "GhostJsonReaderSubsystem.kt": "streaming",
    "StreamingGhostSource.kt": "streaming",
}

WRITER_MAP: dict[str, str] = {
    "GhostDoubleFormatter.kt": "common",
    "FlatByteArrayWriter.kt": "bytes",
    "GhostJsonFlatWriter.kt": "bytes",
    "GhostJsonWriter.kt": "bytes",
    "GhostJsonWriterBmpChar.kt": "bytes",
    "WriterSinkPair.kt": "bytes",
    "CharArrayCopy.kt": "strings",
    "FlatCharArrayWriter.kt": "strings",
    "GhostJsonStringWriter.kt": "strings",
}

# Platform actuals: prefix before .platform.kt
PARSER_PLATFORM: dict[str, str] = {
    "GhostSwar": "bytes",
    "GhostHeuristics": "common",
    "GhostSource": "common",
}

WRITER_PLATFORM: dict[str, str] = {
    "CharArrayCopy": "strings",
}


def base_name(path: Path) -> str:
    name = path.name
    for suffix in (".jvm.kt", ".android.kt", ".ios.kt", ".native.kt", ".wasmJs.kt"):
        if name.endswith(suffix):
            return name[: -len(suffix)] + ".kt"
    return name


def move_parser_file(src: Path, sub: str) -> None:
    rel = src.relative_to(SERIAL)
    parts = list(rel.parts)
    # .../parser/File.kt -> .../parser/sub/File.kt
    idx = parts.index("parser")
    new_parts = parts[: idx + 1] + [sub] + parts[idx + 1 :]
    dst = SERIAL.joinpath(*new_parts)
    dst.parent.mkdir(parents=True, exist_ok=True)
    if src == dst:
        return
    subprocess.run(["git", "mv", str(src), str(dst)], cwd=ROOT, check=True)
    text = dst.read_text(encoding="utf-8")
    new_pkg = f"com.ghost.serialization.parser.{sub}"
    text = re.sub(
        r"^package com\.ghost\.serialization\.parser\b",
        f"package {new_pkg}",
        text,
        count=1,
        flags=re.MULTILINE,
    )
    dst.write_text(text, encoding="utf-8")


def move_writer_file(src: Path, sub: str) -> None:
    rel = src.relative_to(SERIAL)
    parts = list(rel.parts)
    idx = parts.index("writer")
    new_parts = parts[: idx + 1] + [sub] + parts[idx + 1 :]
    dst = SERIAL.joinpath(*new_parts)
    dst.parent.mkdir(parents=True, exist_ok=True)
    if src == dst:
        return
    subprocess.run(["git", "mv", str(src), str(dst)], cwd=ROOT, check=True)
    text = dst.read_text(encoding="utf-8")
    new_pkg = f"com.ghost.serialization.writer.{sub}"
    text = re.sub(
        r"^package com\.ghost\.serialization\.writer\b",
        f"package {new_pkg}",
        text,
        count=1,
        flags=re.MULTILINE,
    )
    dst.write_text(text, encoding="utf-8")


def collect_moves() -> None:
    for path in sorted(SERIAL.rglob("*/parser/*.kt")):
        if path.parent.name in ("common", "bytes", "strings", "streaming", "yaml", "proto"):
            continue
        bn = base_name(path)
        if bn in PARSER_MAP:
            move_parser_file(path, PARSER_MAP[bn])
            continue
        stem = path.stem.split(".")[0]  # GhostSwar from GhostSwar.jvm
        for prefix, sub in PARSER_PLATFORM.items():
            if stem == prefix or path.name.startswith(prefix + "."):
                move_parser_file(path, sub)
                break

    for path in sorted(SERIAL.rglob("*/writer/*.kt")):
        if path.parent.name in ("common", "bytes", "strings", "yaml"):
            continue
        bn = base_name(path)
        if bn in WRITER_MAP:
            move_writer_file(path, WRITER_MAP[bn])
            continue
        stem = path.stem.split(".")[0]
        for prefix, sub in WRITER_PLATFORM.items():
            if stem == prefix or path.name.startswith(prefix + "."):
                move_writer_file(path, sub)
                break


def build_type_subpackage() -> dict[str, tuple[str, str]]:
    """Map simple type name -> (area, sub) e.g. GhostJsonFlatReader -> (parser, bytes)."""
    out: dict[str, tuple[str, str]] = {}
    for fn, sub in PARSER_MAP.items():
        out[fn.removesuffix(".kt")] = ("parser", sub)
    for fn, sub in WRITER_MAP.items():
        out[fn.removesuffix(".kt")] = ("writer", sub)
    return out


TYPE_SUB = build_type_subpackage()

# Extension functions and extra top-level names used across modules
EXTRA: dict[str, tuple[str, str]] = {
    "createByteArraySource": ("parser", "common"),
    "createSourceBridge": ("parser", "common"),
    "createBufferedSource": ("parser", "streaming"),
    "beginObject": ("parser", "streaming"),  # also on strings - import streaming first; fix manually if needed
    "endObject": ("parser", "streaming"),
    "nextString": ("parser", "bytes"),
    "nextInt": ("parser", "bytes"),
    "nextLong": ("parser", "bytes"),
    "nextBoolean": ("parser", "bytes"),
    "nextDouble": ("parser", "bytes"),
    "nextFloat": ("parser", "bytes"),
    "skipValue": ("parser", "bytes"),
    "consumeNull": ("parser", "bytes"),
    "isNextNullValue": ("parser", "bytes"),
    "readList": ("parser", "bytes"),
    "readMap": ("parser", "bytes"),
    "captureRawJson": ("parser", "bytes"),
    "captureRawJsonBytes": ("parser", "bytes"),
    "consumeKeySeparator": ("parser", "bytes"),
    "consumeArraySeparator": ("parser", "bytes"),
    "decodeBase64String": ("parser", "common"),
    "encodeBase64String": ("parser", "common"),
    "charToBytePosition": ("parser", "common"),
    "byteToCharPosition": ("parser", "common"),
}


def rewrite_imports_in_file(path: Path) -> bool:
    text = path.read_text(encoding="utf-8")
    original = text

    def repl_parser(m: re.Match[str]) -> str:
        sym = m.group(1)
        if sym == "GhostJsonConstants" and " as " in m.group(0):
            return f"import com.ghost.serialization.parser.common.GhostJsonConstants as C"
        if sym in TYPE_SUB:
            area, sub = TYPE_SUB[sym]
            return f"import com.ghost.serialization.{area}.{sub}.{sym}"
        if sym in EXTRA:
            area, sub = EXTRA[sym]
            return f"import com.ghost.serialization.{area}.{sub}.{sym}"
        return m.group(0)

    text = re.sub(
        r"^import com\.ghost\.serialization\.parser\.(\w+)",
        repl_parser,
        text,
        flags=re.MULTILINE,
    )

    def repl_writer(m: re.Match[str]) -> str:
        sym = m.group(1)
        if sym in TYPE_SUB:
            area, sub = TYPE_SUB[sym]
            return f"import com.ghost.serialization.{area}.{sub}.{sym}"
        return m.group(0)

    text = re.sub(
        r"^import com\.ghost\.serialization\.writer\.(\w+)",
        repl_writer,
        text,
        flags=re.MULTILINE,
    )

    # Wildcard imports
    text = text.replace(
        "import com.ghost.serialization.parser.*",
        "import com.ghost.serialization.parser.bytes.*\nimport com.ghost.serialization.parser.common.*\nimport com.ghost.serialization.parser.streaming.*\nimport com.ghost.serialization.parser.strings.*",
    )
    text = text.replace(
        "import com.ghost.serialization.writer.*",
        "import com.ghost.serialization.writer.bytes.*\nimport com.ghost.serialization.writer.common.*\nimport com.ghost.serialization.writer.strings.*",
    )

    if text != original:
        path.write_text(text, encoding="utf-8")
        return True
    return False


def rewrite_all_imports() -> int:
    count = 0
    for root, _, files in os.walk(ROOT):
        if "build" in root.split(os.sep) or ".gradle" in root:
            continue
        for f in files:
            if f.endswith(".kt") or f.endswith(".java"):
                p = Path(root) / f
                if rewrite_imports_in_file(p):
                    count += 1
    return count


def add_internal_imports_in_moved_files() -> None:
    """Add cross-subpackage imports inside ghost-serialization moved sources."""
    # Run kotlin compile will guide fixes — placeholder for second pass
    pass


if __name__ == "__main__":
    collect_moves()
    n = rewrite_all_imports()
    print(f"Moved parser/writer files; updated imports in {n} files")

#!/usr/bin/env python3
"""Remove invalid parser/writer imports after subpackage restructure."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
VALID_SUB = {"common", "bytes", "strings", "streaming", "yaml", "proto"}
INVALID_TOP_LEVEL = {
    "nextString", "nextInt", "nextLong", "nextBoolean", "nextDouble", "nextFloat",
    "nextChar", "nextKey", "skipValue", "beginArray", "endArray", "hasNext",
    "consumeArraySeparator", "consumeKeySeparator", "consumeNull", "isNextNullValue",
    "readList", "readSet", "readMap", "decodeResilient", "selectString", "selectName",
    "captureRawJson", "captureRawJsonBytes", "beginObject", "endObject",
    "withPreparedUtf8Json", "prepareUtf8JsonSource", "nextIntExtension",
}
IMPORT_PARSER = re.compile(r"^import com\.ghost\.serialization\.parser\.(\w+)\.(\w+)(.*)$")
IMPORT_PARSER_FLAT = re.compile(r"^import com\.ghost\.serialization\.parser\.(\w+)(.*)$")
IMPORT_WRITER = re.compile(r"^import com\.ghost\.serialization\.writer\.(\w+)(.*)$")

WILDCARDS_SERIALIZATION = [
    "import com.ghost.serialization.parser.common.*",
    "import com.ghost.serialization.parser.bytes.*",
    "import com.ghost.serialization.parser.strings.*",
    "import com.ghost.serialization.parser.streaming.*",
]
WILDCARDS_PROTOBUF = WILDCARDS_SERIALIZATION + [
    "import com.ghost.serialization.parser.proto.*",
]


def should_drop_parser_line(line: str) -> bool:
    stripped = line.rstrip()
    m = IMPORT_PARSER.match(stripped)
    if m:
        sub, sym = m.group(1), m.group(2)
        if sub not in VALID_SUB:
            return True
        return sym in INVALID_TOP_LEVEL
    m = IMPORT_PARSER_FLAT.match(stripped)
    if m:
        sub, rest = m.group(1), m.group(2).strip()
        if sub not in VALID_SUB:
            return True
        if rest:
            sym = rest.split(" as ")[0].strip()
            return sym in INVALID_TOP_LEVEL
    return False


def should_drop_writer_line(line: str) -> bool:
    m = IMPORT_WRITER.match(line.rstrip())
    if not m:
        return False
    sub = m.group(1)
    if sub not in VALID_SUB:
        return True
    rest = m.group(2).strip()
    if rest:
        sym = rest.split(" as ")[0].strip()
        return sym in INVALID_TOP_LEVEL
    return False


def fix_file(path: Path) -> bool:
    text = path.read_text(encoding="utf-8")
    lines = text.splitlines(keepends=True)
    out: list[str] = []
    removed = False
    for line in lines:
        if should_drop_parser_line(line) or should_drop_writer_line(line):
            removed = True
            continue
        out.append(line)
    uses_parser = any(
        s in text
        for s in ("GhostJsonFlatReader", "GhostJsonReader", "GhostJsonStringReader", "GhostProtoJsonFlatReader")
    )
    if not removed and not uses_parser:
        return False
    text = "".join(out)
    if uses_parser:
        wildcards = WILDCARDS_PROTOBUF if "ghost-protobuf" in str(path) else WILDCARDS_SERIALIZATION
        is_compiler_main = "ghost-compiler/src/main" in str(path).replace("\\", "/")
        if (
            not is_compiler_main
            and "build" not in path.parts
            and any(
            mod in str(path) for mod in (
                "ghost-serialization", "ghost-protobuf", "ghost-compiler",
                "ghost-ktor", "ghost-integration-test", "ghost-spring-boot-starter",
                "ghost-retrofit",
            )
        ):
            pkg = re.search(r"^package .+\n", text, re.MULTILINE)
            if pkg:
                insert = pkg.end()
                to_add = [w for w in wildcards if w not in text]
                if to_add:
                    text = text[:insert] + "\n" + "\n".join(to_add) + "\n" + text[insert:].lstrip("\n")
    path.write_text(text, encoding="utf-8")
    return True


def main() -> None:
    n = 0
    for kt in ROOT.rglob("*.kt"):
        if "build" in kt.parts:
            continue
        if fix_file(kt):
            n += 1
    print(f"Cleaned bad imports in {n} files")


if __name__ == "__main__":
    main()

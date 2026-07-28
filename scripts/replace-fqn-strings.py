#!/usr/bin/env python3
"""Replace fully-qualified parser/writer references after subpackage move."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

REPLACEMENTS = [
    ("com.ghost.serialization.parser.GhostJsonFlatReader", "com.ghost.serialization.parser.bytes.GhostJsonFlatReader"),
    ("com.ghost.serialization.parser.GhostJsonReader", "com.ghost.serialization.parser.streaming.GhostJsonReader"),
    ("com.ghost.serialization.parser.GhostJsonStringReader", "com.ghost.serialization.parser.strings.GhostJsonStringReader"),
    ("com.ghost.serialization.parser.GhostSource", "com.ghost.serialization.parser.common.GhostSource"),
    ("com.ghost.serialization.parser.JsonReaderOptions", "com.ghost.serialization.parser.common.JsonReaderOptions"),
    ("com.ghost.serialization.parser.GhostJsonConstants", "com.ghost.serialization.parser.common.GhostJsonConstants"),
    ("com.ghost.serialization.parser.GhostProtoJsonFlatReader", "com.ghost.serialization.parser.proto.GhostProtoJsonFlatReader"),
    ("com.ghost.serialization.writer.GhostJsonFlatWriter", "com.ghost.serialization.writer.bytes.GhostJsonFlatWriter"),
    ("com.ghost.serialization.writer.FlatByteArrayWriter", "com.ghost.serialization.writer.bytes.FlatByteArrayWriter"),
    ("com.ghost.serialization.writer.GhostJsonWriter", "com.ghost.serialization.writer.bytes.GhostJsonWriter"),
    ("com.ghost.serialization.writer.GhostJsonStringWriter", "com.ghost.serialization.writer.strings.GhostJsonStringWriter"),
    ("com.ghost.serialization.writer.FlatCharArrayWriter", "com.ghost.serialization.writer.strings.FlatCharArrayWriter"),
    ("com.ghost.serialization.writer.WriterSinkPair", "com.ghost.serialization.writer.bytes.WriterSinkPair"),
    ("com.ghost.serialization.writer.GhostDoubleFormatter", "com.ghost.serialization.writer.common.GhostDoubleFormatter"),
]


def main() -> None:
    n = 0
    for path in ROOT.rglob("*"):
        if path.suffix not in (".kt", ".java", ".kts") or "build" in path.parts:
            continue
        text = path.read_text(encoding="utf-8")
        orig = text
        for old, new in REPLACEMENTS:
            text = text.replace(old, new)
        if text != orig:
            path.write_text(text, encoding="utf-8")
            n += 1
    print(f"Updated FQN strings in {n} files")


if __name__ == "__main__":
    main()

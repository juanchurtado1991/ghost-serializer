#!/usr/bin/env python3
"""Add parser.common imports to ghost-serialization top-level sources using UTF-8 helpers."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
SERIAL = ROOT / "ghost-serialization" / "src"

IMPORTS = [
    "import com.ghost.serialization.parser.common.*",
    "import com.ghost.serialization.parser.bytes.*",
    "import com.ghost.serialization.parser.strings.*",
    "import com.ghost.serialization.parser.streaming.*",
    "import com.ghost.serialization.writer.common.*",
    "import com.ghost.serialization.writer.bytes.*",
    "import com.ghost.serialization.writer.strings.*",
]

SYMBOLS = (
    "withPreparedUtf8Json",
    "prepareUtf8JsonSource",
    "GhostJsonFlatReader",
    "GhostJsonReader",
    "GhostJsonStringReader",
    "GhostSource",
    "GhostJsonFlatWriter",
    "GhostJsonWriter",
    "GhostJsonStringWriter",
    "FlatByteArrayWriter",
    "beginObject",
    "beginArray",
)


def needs_fix(text: str) -> bool:
    return any(s in text for s in SYMBOLS)


def fix_file(path: Path) -> bool:
    if "/parser/" in str(path) or "/writer/" in str(path):
        return False
    text = path.read_text(encoding="utf-8")
    if not needs_fix(text):
        return False
    pkg = re.search(r"^package .+\n", text, re.MULTILINE)
    if not pkg:
        return False
    end = pkg.end()
    added = [i for i in IMPORTS if i not in text]
    if not added:
        return False
    text = text[:end] + "\n" + "\n".join(added) + "\n" + text[end:].lstrip("\n")
    path.write_text(text, encoding="utf-8")
    return True


def main() -> None:
    n = 0
    for kt in SERIAL.rglob("*.kt"):
        if fix_file(kt):
            n += 1
    print(f"Fixed {n} top-level serialization files")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Add parser wildcard imports to ghost-protobuf sources."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "ghost-protobuf" / "src"
WILDCARDS = [
    "import com.ghost.serialization.parser.common.*",
    "import com.ghost.serialization.parser.bytes.*",
    "import com.ghost.serialization.parser.strings.*",
    "import com.ghost.serialization.parser.streaming.*",
    "import com.ghost.serialization.parser.proto.*",
]


def fix(path: Path) -> bool:
    text = path.read_text(encoding="utf-8")
    if "package com.ghost.protobuf" not in text and "package com.ghost.serialization.parser.proto" not in text:
        return False
    import re
    pkg = re.search(r"^package .+\n", text, re.MULTILINE)
    if not pkg:
        return False
    to_add = [w for w in WILDCARDS if w not in text]
    if not to_add:
        return False
    end = pkg.end()
    text = text[:end] + "\n" + "\n".join(to_add) + "\n" + text[end:].lstrip("\n")
    path.write_text(text, encoding="utf-8")
    return True


def main() -> None:
    n = 0
    for kt in ROOT.rglob("*.kt"):
        if fix(kt):
            n += 1
    print(f"Updated {n} ghost-protobuf files")


if __name__ == "__main__":
    main()

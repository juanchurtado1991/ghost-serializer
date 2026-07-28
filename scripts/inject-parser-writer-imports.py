#!/usr/bin/env python3
"""Add cross-subpackage wildcard imports to restructured parser/writer sources."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
SERIAL = ROOT / "ghost-serialization" / "src"

PARSER_IMPORTS = {
    "bytes": [
        "import com.ghost.serialization.parser.common.*",
    ],
    "strings": [
        "import com.ghost.serialization.parser.common.*",
        "import com.ghost.serialization.writer.strings.*",
    ],
    "streaming": [
        "import com.ghost.serialization.parser.common.*",
        "import com.ghost.serialization.parser.bytes.*",
    ],
    "common": [
        "import com.ghost.serialization.parser.bytes.*",
        "import com.ghost.serialization.parser.strings.*",
        "import com.ghost.serialization.parser.streaming.*",
    ],
}

WRITER_IMPORTS = {
    "bytes": [
        "import com.ghost.serialization.writer.common.*",
    ],
    "strings": [
        "import com.ghost.serialization.writer.common.*",
    ],
    "common": [],
}


def inject_imports(path: Path, area: str, sub: str) -> None:
    imports_map = PARSER_IMPORTS if area == "parser" else WRITER_IMPORTS
    to_add = imports_map.get(sub, [])
    if not to_add:
        return
    text = path.read_text(encoding="utf-8")
    pkg_match = re.search(r"^package .+\n", text, re.MULTILINE)
    if not pkg_match:
        return
    insert_at = pkg_match.end()
    existing = text[insert_at : insert_at + 500]
    new_lines = []
    for imp in to_add:
        if imp not in text:
            new_lines.append(imp)
    if not new_lines:
        return
    block = "\n".join(new_lines) + "\n"
    text = text[:insert_at] + "\n" + block + text[insert_at:].lstrip("\n")
    path.write_text(text, encoding="utf-8")


def main() -> None:
    for area in ("parser", "writer"):
        for sub in ("common", "bytes", "strings", "streaming"):
            base = SERIAL / "commonMain" / "kotlin" / "com" / "ghost" / "serialization" / area / sub
            if not base.exists():
                continue
            for kt in base.rglob("*.kt"):
                inject_imports(kt, area, sub)
        # platform source sets
        for src in SERIAL.glob(f"*/kotlin/com/ghost/serialization/{area}/*/*.kt"):
            sub = src.parent.name
            if sub in ("common", "bytes", "strings", "streaming"):
                inject_imports(src, area, sub)


if __name__ == "__main__":
    main()
    print("Injected cross-subpackage imports")

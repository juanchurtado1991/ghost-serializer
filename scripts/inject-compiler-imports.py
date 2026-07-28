#!/usr/bin/env python3
"""Inject cross-package imports after ghost-compiler subpackage split."""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "ghost-compiler/src/main/kotlin/com/ghost/serialization/compiler"

# Wildcard imports required per package after the flat split.
PACKAGE_IMPORTS: dict[str, list[str]] = {
    "com.ghost.serialization.compiler.analysis": [
        "com.ghost.serialization.compiler.model",
    ],
    "com.ghost.serialization.compiler.codegen": [
        "com.ghost.serialization.compiler.model",
        "com.ghost.serialization.compiler.analysis",
        "com.ghost.serialization.compiler.hash",
        "com.ghost.serialization.compiler.codegen.emit",
    ],
    "com.ghost.serialization.compiler.codegen.emit": [
        "com.ghost.serialization.compiler.model",
        "com.ghost.serialization.compiler.analysis",
        "com.ghost.serialization.compiler.hash",
        "com.ghost.serialization.compiler.codegen",
    ],
    "com.ghost.serialization.compiler.ksp": [
        "com.ghost.serialization.compiler.model",
        "com.ghost.serialization.compiler.analysis",
        "com.ghost.serialization.compiler.codegen",
        "com.ghost.serialization.compiler.hash",
    ],
}

PACKAGE_LINE = re.compile(r"^package (.+)$", re.MULTILINE)
IMPORT_LINE = re.compile(r"^import .+$", re.MULTILINE)


def inject_file(path: Path) -> bool:
    text = path.read_text(encoding="utf-8")
    match = PACKAGE_LINE.search(text)
    if not match:
        return False
    pkg = match.group(1)
    needed = PACKAGE_IMPORTS.get(pkg)
    if not needed:
        return False

    lines = text.splitlines(keepends=True)
    pkg_idx = next(i for i, line in enumerate(lines) if line.startswith("package "))
    existing = {line.strip() for line in lines if line.startswith("import ")}

    to_add: list[str] = []
    for imp in needed:
        stmt = f"import {imp}.*"
        if stmt not in existing:
            to_add.append(stmt + "\n")

    if not to_add:
        return False

    insert_at = pkg_idx + 1
    if insert_at < len(lines) and lines[insert_at].strip() == "":
        insert_at += 1

    new_lines = lines[:insert_at] + to_add + lines[insert_at:]
    path.write_text("".join(new_lines), encoding="utf-8")
    return True


def main() -> None:
    changed = 0
    for path in MAIN.rglob("*.kt"):
        if inject_file(path):
            changed += 1
            print(f"patched imports: {path.relative_to(ROOT)}")
    print(f"Updated {changed} files")


if __name__ == "__main__":
    main()

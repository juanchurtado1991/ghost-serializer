#!/usr/bin/env python3
"""Build mobile-friendly Ghost Serialization study manual (PDF) with TOC links and bookmarks."""
from __future__ import annotations

import re
import unicodedata
from pathlib import Path

from fpdf import FPDF

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "docs" / "GHOST_MANUAL_EN.md"
OUTPUT = ROOT / "docs" / "Ghost-Serialization-Manual-1.1.18.pdf"

FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
FONT_B = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_MONO = "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf"

PAGE_W, PAGE_H = 148, 210
MARGIN_L, MARGIN_R = 11, 11
MARGIN_T, MARGIN_B = 11, 13
CONTENT_W = PAGE_W - MARGIN_L - MARGIN_R
FOOTER_Y = PAGE_H - 10
BODY_BOTTOM = FOOTER_Y - 2

ANCHOR_RE = re.compile(r"\s*\{#([a-z0-9_-]+)\}\s*$", re.I)
LINK_RE = re.compile(r"\[([^\]]+)\]\(#([a-z0-9_-]+)\)", re.I)


def _strip_md_inline(text: str) -> str:
    text = re.sub(r"\*\*(.+?)\*\*", r"\1", text)
    text = re.sub(r"`(.+?)`", r"\1", text)
    return text


def _slug(text: str) -> str:
    text = re.sub(r"^#+\s*", "", text)
    text = ANCHOR_RE.sub("", text).strip().lower()
    text = unicodedata.normalize("NFKD", text)
    text = "".join(c for c in text if not unicodedata.combining(c))
    text = re.sub(r"[^a-z0-9]+", "-", text).strip("-")
    return text or "section"


def _parse_anchor_title(line: str) -> tuple[str, str | None]:
    m = ANCHOR_RE.search(line)
    if m:
        anchor = m.group(1).lower()
        title = ANCHOR_RE.sub("", line).strip()
        return title, anchor
    return line.strip(), None


class ManualPDF(FPDF):
    def __init__(self) -> None:
        super().__init__(format=(PAGE_W, PAGE_H), unit="mm")
        self.set_auto_page_break(auto=True, margin=MARGIN_B)
        self.set_margins(MARGIN_L, MARGIN_T, MARGIN_R)
        self.add_font("body", "", FONT)
        self.add_font("body", "B", FONT_B)
        self.add_font("mono", "", FONT_MONO)
        self._in_toc = False

    def header(self) -> None:
        if self.page_no() <= 1:
            return
        self.set_font("body", "", 6)
        self.set_text_color(130, 130, 130)
        self.set_y(6)
        self.cell(0, 4, "Ghost Serialization  1.1.18  —  API Manual", align="C")
        self.set_text_color(0, 0, 0)
        self.set_y(MARGIN_T)

    def footer(self) -> None:
        self.set_y(FOOTER_Y)
        self.set_font("body", "", 7)
        self.set_text_color(110, 110, 110)
        self.cell(0, 5, str(self.page_no()), align="C")

    def remaining(self) -> float:
        return BODY_BOTTOM - self.get_y()

    def ensure_space(self, h: float) -> None:
        if self.get_y() + h > BODY_BOTTOM:
            self.add_page()

    def _register_anchor(self, anchor: str) -> None:
        self.set_link(
            name=anchor,
            page=self.page_no(),
            y=self.get_y(),
        )

    def _bookmark(self, title: str, level: int) -> None:
        self.start_section(_strip_md_inline(title), level=level, strict=False)

    def section_rule(self) -> None:
        if self.remaining() < 6:
            self.add_page()
        self.ln(1.5)
        y = self.get_y()
        self.set_draw_color(200, 205, 220)
        self.set_line_width(0.15)
        self.line(MARGIN_L, y, PAGE_W - MARGIN_R, y)
        self.ln(2)

    def h1(self, text: str, anchor: str | None = None) -> None:
        if self.get_y() > MARGIN_T + 8 and not self._in_toc:
            self.section_rule()
        self.ensure_space(12)
        if anchor:
            self._register_anchor(anchor)
        self._bookmark(text, 0)
        self.set_font("body", "B", 14)
        self.set_text_color(20, 20, 80)
        self.multi_cell(CONTENT_W, 6, _strip_md_inline(text))
        self.ln(1.2)
        self.set_text_color(0, 0, 0)

    def h2(self, text: str, anchor: str | None = None) -> None:
        self.ensure_space(9)
        self.ln(0.8)
        if anchor:
            self._register_anchor(anchor)
        self._bookmark(text, 1)
        self.set_font("body", "B", 11)
        self.set_text_color(30, 30, 120)
        self.multi_cell(CONTENT_W, 5, _strip_md_inline(text))
        self.ln(0.8)
        self.set_text_color(0, 0, 0)

    def h3(self, text: str, anchor: str | None = None) -> None:
        self.ensure_space(7)
        if anchor:
            self._register_anchor(anchor)
        self._bookmark(text, 2)
        self.set_font("body", "B", 10)
        self.multi_cell(CONTENT_W, 4.8, _strip_md_inline(text))
        self.ln(0.5)

    def toc_part_header(self, text: str) -> None:
        """Bold PARTE header inside the navigation index."""
        self.ensure_space(6)
        self.ln(1.2)
        self.set_font("body", "B", 9.5)
        self.set_text_color(30, 30, 120)
        self.set_x(MARGIN_L)
        self.multi_cell(CONTENT_W, 5, text)
        self.ln(0.3)
        self.set_text_color(0, 0, 0)

    def toc_entry(self, text: str, anchor: str, level: int = 0) -> None:
        """Single TOC line with internal link — uses cell()+ln(), not write()."""
        line_h = 5.0
        self.ensure_space(line_h + 0.5)
        indent = 3 + level * 4
        self.set_font("body", "", 9)
        self.set_text_color(0, 0, 180)
        dest = self.get_named_destination(anchor)
        self.set_x(MARGIN_L + indent)
        w = CONTENT_W - indent
        self.cell(w, line_h, text, link=dest)
        self.ln(line_h + 0.3)
        self.set_text_color(0, 0, 0)

    def link_line(self, text: str, prefix: str = "") -> None:
        """Bullet or paragraph with a single markdown link — proper line break."""
        m = LINK_RE.search(text)
        if not m:
            self.p(prefix + text)
            return
        line_h = 5.0
        self.ensure_space(line_h + 0.5)
        label, anchor = m.group(1), m.group(2).lower()
        self.set_font("body", "", 9)
        self.set_text_color(0, 0, 180)
        dest = self.get_named_destination(anchor)
        self.set_x(MARGIN_L)
        self.cell(CONTENT_W, line_h, f"{prefix}{label}", link=dest)
        self.ln(line_h + 0.3)
        self.set_text_color(0, 0, 0)

    def p(self, text: str) -> None:
        self.set_font("body", "", 9.5)
        self.multi_cell(CONTENT_W, 4.5, _strip_md_inline(text))
        self.ln(0.8)

    def note(self, text: str) -> None:
        self.ensure_space(8)
        self.set_fill_color(245, 248, 255)
        self.set_font("body", "", 8.5)
        self.set_x(MARGIN_L)
        self.multi_cell(CONTENT_W, 4, _strip_md_inline(text), fill=True)
        self.ln(0.8)

    def bullet(self, text: str) -> None:
        if LINK_RE.search(text):
            self.link_line(text, prefix="• ")
            return
        self.set_font("body", "", 9.5)
        self.set_x(MARGIN_L)
        self.multi_cell(CONTENT_W, 4.5, f"• {_strip_md_inline(text)}")
        self.ln(0.5)

    def _wrap_lines(self, text: str, max_w: float, font_size: float) -> list[str]:
        self.set_font("body", "", font_size)
        words = text.split()
        if not words:
            return [""]
        lines: list[str] = []
        current = words[0]
        for word in words[1:]:
            trial = f"{current} {word}"
            if self.get_string_width(trial) <= max_w:
                current = trial
            else:
                lines.append(current)
                current = word
        lines.append(current)
        return lines

    def table(self, rows: list[list[str]]) -> None:
        if not rows:
            return
        ncols = max(len(r) for r in rows)
        if ncols == 0:
            return
        col_w = CONTENT_W / ncols
        line_h = 3.6
        font_size = 6.8
        data_rows: list[tuple[bool, list[str]]] = []
        for ri, row in enumerate(rows):
            is_sep = ri == 1 and all(
                set(c.strip()) <= set("-:") for c in row if c.strip()
            )
            if is_sep:
                continue
            is_header = ri == 0
            cells = [
                _strip_md_inline((row[ci] if ci < len(row) else "").strip())
                for ci in range(ncols)
            ]
            data_rows.append((is_header, cells))

        wrapped: list[tuple[bool, list[list[str]]]] = []
        for is_header, cells in data_rows:
            cell_lines = [
                self._wrap_lines(c, col_w - 1.5, font_size) for c in cells
            ]
            wrapped.append((is_header, cell_lines))

        for row_idx, (is_header, cell_lines) in enumerate(wrapped):
            row_h = line_h * max(len(cl) for cl in cell_lines)
            self.ensure_space(row_h + 0.5)
            y0 = self.get_y()
            fill = is_header or (row_idx % 2 == 0)
            for ci in range(ncols):
                lines = cell_lines[ci]
                x = MARGIN_L + ci * col_w
                self.set_fill_color(240, 242, 248)
                style = "B" if is_header else ""
                self.set_font("body", style, font_size)
                for li, ln in enumerate(lines):
                    self.set_xy(x, y0 + li * line_h)
                    self.cell(col_w, line_h, ln, border=0, fill=fill, align="L")
            self.set_y(y0 + row_h)
        self.ln(0.6)
        self.set_font("body", "", 9)

    def code(self, text: str) -> None:
        lines = text.rstrip().split("\n")
        if not lines:
            return
        line_h = 3.9
        block_h = len(lines) * line_h + 1.5
        if block_h < self.remaining() - 2:
            self.ensure_space(block_h)
        self.set_fill_color(248, 248, 248)
        self.set_font("mono", "", 6.4)
        for line in lines:
            safe = line.expandtabs(2)
            self.set_x(MARGIN_L)
            self.multi_cell(CONTENT_W, line_h, safe, fill=True)
        self.ln(0.8)
        self.set_font("body", "", 9)


def _is_table_row(line: str) -> bool:
    s = line.strip()
    return s.startswith("|") and s.endswith("|") and "|" in s[1:-1]


def _parse_table_row(line: str) -> list[str]:
    return [p.strip() for p in line.strip().strip("|").split("|")]


def parse_and_build(md: str, pdf: ManualPDF) -> None:
    lines = md.splitlines()
    i = 0
    in_code = False
    code_buf: list[str] = []
    table_buf: list[list[str]] = []

    def flush_table() -> None:
        nonlocal table_buf
        if table_buf:
            pdf.table(table_buf)
            table_buf = []

    while i < len(lines):
        line = lines[i]

        if line.strip().startswith("```"):
            flush_table()
            if in_code:
                pdf.code("\n".join(code_buf))
                code_buf = []
                in_code = False
            else:
                in_code = True
            i += 1
            continue

        if in_code:
            code_buf.append(line)
            i += 1
            continue

        if _is_table_row(line):
            flush_table()
            table_buf.append(_parse_table_row(line))
            i += 1
            continue

        flush_table()

        if line.startswith("# "):
            if pdf._in_toc:
                pdf._in_toc = False
            title, anchor = _parse_anchor_title(line[2:])
            pdf.h1(title, anchor or _slug(title))
        elif line.startswith("## "):
            title, anchor = _parse_anchor_title(line[3:])
            anchor = anchor or _slug(title)
            if anchor == "indice":
                pdf._in_toc = True
            elif pdf._in_toc and anchor != "indice":
                pdf._in_toc = False
            pdf.h2(title, anchor)
        elif line.startswith("### "):
            title, anchor = _parse_anchor_title(line[4:])
            pdf.h3(title, anchor or _slug(title))
        elif line.strip() == "---":
            if pdf._in_toc:
                pdf._in_toc = False
            else:
                pdf.section_rule()
        elif line.startswith("> "):
            pdf.note(line[2:].strip())
        elif line.startswith("- ") and LINK_RE.search(line):
            body = line[2:].strip()
            m = LINK_RE.search(body)
            if pdf._in_toc and m:
                pdf.toc_entry(m.group(1), m.group(2).lower())
            else:
                pdf.bullet(body)
        elif line.startswith("- "):
            pdf.bullet(line[2:].strip())
        elif line.strip().startswith("**") and pdf._in_toc:
            pdf.toc_part_header(_strip_md_inline(line.strip()))
        elif line.strip() and LINK_RE.search(line):
            pdf.link_line(line.strip())
        elif line.strip():
            pdf.p(line.strip())
        i += 1

    flush_table()
    if code_buf:
        pdf.code("\n".join(code_buf))
    pdf._in_toc = False


def main() -> None:
    if not SOURCE.exists():
        raise SystemExit(f"Missing source: {SOURCE}")
    md = SOURCE.read_text(encoding="utf-8")
    pdf = ManualPDF()
    pdf.add_page()
    parse_and_build(md, pdf)
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    pdf.output(str(OUTPUT))
    print(f"Generated: {OUTPUT}")
    print(f"Pages: {pdf.page_no()}")


if __name__ == "__main__":
    main()

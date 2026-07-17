#!/usr/bin/env python3
"""Render or verify the README terminal proof from the packaged CLI demo."""

from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path
from xml.sax.saxutils import escape


def render(jar: Path) -> str:
    process = subprocess.run(
        ["java", "-jar", str(jar), "--demo"],
        check=True,
        capture_output=True,
        text=True,
        encoding="utf-8",
    )
    lines = process.stdout.rstrip().splitlines()
    if not lines or lines[0] != "News Gatherer • deterministic offline demo":
        raise RuntimeError("packaged CLI did not emit the expected demo header")

    text_rows = []
    for index, line in enumerate(lines):
        css_class = "summary" if line == "=== SUMMARY ===" else "prompt" if index < 2 else "output"
        text_rows.append(
            f'  <text x="42" y="{112 + index * 25}" class="{css_class}">{escape(line)}</text>'
        )
    return "\n".join(
        [
            '<svg xmlns="http://www.w3.org/2000/svg" width="1280" height="640" viewBox="0 0 1280 640" role="img" aria-labelledby="title desc">',
            '  <title id="title">News Gatherer deterministic offline CLI demo</title>',
            '  <desc id="desc">The packaged CLI renders three synthetic Turkish news records and a fetched, new, and duplicate summary without network or database access.</desc>',
            '  <rect width="1280" height="640" rx="18" fill="#0d1117"/>',
            '  <rect width="1280" height="58" rx="18" fill="#161b22"/>',
            '  <rect y="40" width="1280" height="18" fill="#161b22"/>',
            '  <circle cx="28" cy="29" r="7" fill="#ff5f56"/>',
            '  <circle cx="52" cy="29" r="7" fill="#ffbd2e"/>',
            '  <circle cx="76" cy="29" r="7" fill="#27c93f"/>',
            '  <text x="640" y="35" text-anchor="middle" class="chrome">news-gatherer.jar --demo</text>',
            '  <style>text{font:17px ui-monospace,SFMono-Regular,Menlo,Consolas,monospace}.chrome{fill:#8b949e;font-size:15px}.prompt{fill:#58a6ff;font-weight:600}.output{fill:#e6edf3}.summary{fill:#3fb950;font-weight:700}</style>',
            *text_rows,
            '</svg>',
            '',
        ]
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", type=Path, default=Path("target/news-gatherer.jar"))
    parser.add_argument("--check", type=Path)
    args = parser.parse_args()
    output = render(args.jar)
    if args.check:
        if args.check.read_text(encoding="utf-8") != output:
            print(f"terminal proof differs from {args.check}", file=sys.stderr)
            return 1
        print(f"verified terminal proof: {args.check}")
        return 0
    sys.stdout.write(output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

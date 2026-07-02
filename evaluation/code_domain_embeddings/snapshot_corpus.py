"""Export the garden corpus to a frozen snapshot for reproducible evaluation."""

import json
import re
import sys
from datetime import datetime, timezone
from pathlib import Path

from .config import garden_path


def parse_garden_entry(text: str, relative_path: str) -> dict:
    """Parse a garden entry markdown file into structured data."""
    parts = re.split(r"^---\s*$", text, maxsplit=2, flags=re.MULTILINE)
    if len(parts) < 3:
        return {
            "id": Path(relative_path).stem,
            "domain": Path(relative_path).parent.name,
            "title": "",
            "content": text.strip(),
        }

    frontmatter_text = parts[1]
    body = parts[2].strip()

    fm = {}
    for line in frontmatter_text.strip().splitlines():
        match = re.match(r'^(\w[\w-]*)\s*:\s*(.+)$', line)
        if match:
            key = match.group(1)
            value = match.group(2).strip().strip('"').strip("'")
            fm[key] = value

    return {
        "id": fm.get("id", Path(relative_path).stem),
        "domain": fm.get("domain", Path(relative_path).parent.name),
        "title": fm.get("title", ""),
        "content": body,
    }


def snapshot_corpus(garden_dir: Path, output_dir: Path) -> dict:
    """Export all GE-*.md entries to a frozen snapshot directory."""
    output_dir.mkdir(parents=True, exist_ok=True)
    entries = []

    for md_file in sorted(garden_dir.rglob("GE-*.md")):
        rel = md_file.relative_to(garden_dir)
        if "_summaries" in str(rel) or "_index" in str(rel):
            continue
        text = md_file.read_text(encoding="utf-8")
        entry = parse_garden_entry(text, str(rel))
        entries.append(entry)

    manifest = {
        "entry_count": len(entries),
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "garden_source": str(garden_dir),
        "entries": entries,
    }

    (output_dir / "manifest.json").write_text(
        json.dumps(manifest, indent=2, ensure_ascii=False),
        encoding="utf-8",
    )
    return manifest


def load_snapshot(snapshot_dir: Path) -> dict:
    """Load a previously created corpus snapshot."""
    return json.loads((snapshot_dir / "manifest.json").read_text(encoding="utf-8"))


if __name__ == "__main__":
    garden = garden_path()
    output = Path(__file__).parent / "corpus-snapshot"
    print(f"Snapshotting garden at {garden}...")
    manifest = snapshot_corpus(garden, output)
    print(f"Exported {manifest['entry_count']} entries to {output}")

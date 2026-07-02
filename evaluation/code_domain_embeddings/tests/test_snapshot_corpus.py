import json
import tempfile
from pathlib import Path

import pytest

from evaluation.code_domain_embeddings.snapshot_corpus import (
    parse_garden_entry,
    snapshot_corpus,
    load_snapshot,
)


SAMPLE_ENTRY = """---
id: GE-20260515-da8abd
garden: discovery
title: "Maven submodule folder names — short, no repo-name prefix"
type: convention
domain: jvm
stack: "Maven"
tags: [maven, multi-module, naming]
score: 9
verified: true
submitted: 2026-05-15
---

## Maven submodule folder names — short, no repo-name prefix

Submodule folder names are short and descriptive.
"""


def test_parse_garden_entry_extracts_frontmatter():
    result = parse_garden_entry(SAMPLE_ENTRY, "jvm/GE-20260515-da8abd.md")
    assert result["id"] == "GE-20260515-da8abd"
    assert result["domain"] == "jvm"
    assert result["title"] == "Maven submodule folder names — short, no repo-name prefix"


def test_parse_garden_entry_extracts_body():
    result = parse_garden_entry(SAMPLE_ENTRY, "jvm/GE-20260515-da8abd.md")
    assert "Submodule folder names are short" in result["content"]


def test_parse_garden_entry_strips_frontmatter_from_content():
    result = parse_garden_entry(SAMPLE_ENTRY, "jvm/GE-20260515-da8abd.md")
    assert "---" not in result["content"]
    assert "garden: discovery" not in result["content"]


def test_snapshot_corpus_creates_manifest(tmp_path):
    garden = tmp_path / "garden" / "jvm"
    garden.mkdir(parents=True)
    (garden / "GE-20260515-da8abd.md").write_text(SAMPLE_ENTRY)

    output = tmp_path / "snapshot"
    manifest = snapshot_corpus(tmp_path / "garden", output)

    assert manifest["entry_count"] == 1
    assert "timestamp" in manifest
    assert len(manifest["entries"]) == 1
    assert manifest["entries"][0]["id"] == "GE-20260515-da8abd"


def test_snapshot_corpus_writes_manifest_json(tmp_path):
    garden = tmp_path / "garden" / "jvm"
    garden.mkdir(parents=True)
    (garden / "GE-20260515-da8abd.md").write_text(SAMPLE_ENTRY)

    output = tmp_path / "snapshot"
    snapshot_corpus(tmp_path / "garden", output)

    manifest_file = output / "manifest.json"
    assert manifest_file.exists()
    loaded = json.loads(manifest_file.read_text())
    assert loaded["entry_count"] == 1


def test_snapshot_corpus_skips_non_ge_files(tmp_path):
    garden = tmp_path / "garden" / "jvm"
    garden.mkdir(parents=True)
    (garden / "GE-20260515-da8abd.md").write_text(SAMPLE_ENTRY)
    (garden / "README.md").write_text("# Not a GE entry")
    (garden / "INDEX.md").write_text("# Index")

    output = tmp_path / "snapshot"
    manifest = snapshot_corpus(tmp_path / "garden", output)

    assert manifest["entry_count"] == 1


def test_snapshot_corpus_handles_multiple_domains(tmp_path):
    for domain in ("jvm", "tools", "quarkus"):
        d = tmp_path / "garden" / domain
        d.mkdir(parents=True)
        (d / f"GE-20260515-{domain[:6]}.md").write_text(
            SAMPLE_ENTRY.replace("da8abd", domain[:6]).replace("jvm", domain)
        )

    output = tmp_path / "snapshot"
    manifest = snapshot_corpus(tmp_path / "garden", output)

    assert manifest["entry_count"] == 3
    domains = {e["domain"] for e in manifest["entries"]}
    assert domains == {"jvm", "tools", "quarkus"}


def test_load_snapshot_reads_manifest(tmp_path):
    garden = tmp_path / "garden" / "jvm"
    garden.mkdir(parents=True)
    (garden / "GE-20260515-da8abd.md").write_text(SAMPLE_ENTRY)

    output = tmp_path / "snapshot"
    snapshot_corpus(tmp_path / "garden", output)
    loaded = load_snapshot(output)

    assert loaded["entry_count"] == 1
    assert loaded["entries"][0]["id"] == "GE-20260515-da8abd"

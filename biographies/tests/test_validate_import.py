"""Tests for validate_import.py — post-conversion validation."""

import json
import os
import tempfile

import pytest
import yaml

from validate_import import validate


def _write_yaml(tmpdir, filename, data):
    path = os.path.join(tmpdir, filename)
    with open(path, "w") as f:
        yaml.dump(data, f, default_flow_style=False)
    return path


def _corpus(nodes=None, edges=None, memories=None,
            entity_enrichments=None, disposition_profile=None):
    c = {"subject": "Test", "nodes": nodes or [], "edges": edges or [],
         "memories": memories or []}
    if entity_enrichments is not None:
        c["entity_enrichments"] = entity_enrichments
    if disposition_profile is not None:
        c["disposition_profile"] = disposition_profile
    return c


def _goal_node(name="Test Goal", **kw):
    n = {"name": name, "subgraph_type": "goal", "properties": {}}
    n.update(kw)
    return n


def _person_node(name="Frida Kahlo"):
    return {"name": name, "subgraph_type": "person", "properties": {}}


def _place_node(name="Studio"):
    return {"name": name, "subgraph_type": "place", "properties": {}}


def _memory(text="A memory", domain="experience", **kw):
    m = {"text": text, "domain": domain}
    m.update(kw)
    return m


class TestNodeCounts:

    def test_matching_counts_pass(self):
        corpus = _corpus(nodes=[_goal_node(), _person_node()])
        with tempfile.TemporaryDirectory() as d:
            _write_yaml(d, "goals.yaml", {"type": "goal", "entries": [{"name": "Test Goal"}]})
            _write_yaml(d, "relationships.yaml", {"type": "relationship", "entries": [{"name": "Frida Kahlo"}]})
            report = validate(corpus, d)
        check = next(c for c in report["checks"] if c["check"] == "node_count")
        assert check["status"] == "pass"

    def test_missing_entries_fail(self):
        corpus = _corpus(nodes=[_goal_node()] * 10)
        with tempfile.TemporaryDirectory() as d:
            _write_yaml(d, "goals.yaml", {"type": "goal", "entries": [{"name": "Only one"}]})
            report = validate(corpus, d)
        check = next(c for c in report["checks"] if c["check"] == "node_count")
        assert check["status"] == "fail"

    def test_empty_corpus_passes(self):
        with tempfile.TemporaryDirectory() as d:
            report = validate(_corpus(), d)
        check = next(c for c in report["checks"] if c["check"] == "node_count")
        assert check["status"] == "pass"


class TestEdgeCounts:

    def test_edges_mapped(self):
        corpus = _corpus(edges=[{"source": "A", "target": "B", "type": "knows"}])
        with tempfile.TemporaryDirectory() as d:
            _write_yaml(d, "relationships.yaml", {"type": "relationship", "entries": [
                {"name": "A", "associations": [{"entity_ref": "B", "edge_type": "knows"}]}
            ]})
            report = validate(corpus, d)
        check = next(c for c in report["checks"] if c["check"] == "edge_count")
        assert check["status"] == "pass"
        assert check["mapped_edges"] == 1

    def test_no_edges_no_output_passes(self):
        with tempfile.TemporaryDirectory() as d:
            report = validate(_corpus(), d)
        check = next(c for c in report["checks"] if c["check"] == "edge_count")
        assert check["status"] == "pass"

    def test_dependencies_counted(self):
        corpus = _corpus(edges=[{"source": "A", "target": "B", "type": "enables"}])
        with tempfile.TemporaryDirectory() as d:
            _write_yaml(d, "goals.yaml", {"type": "goal", "entries": [
                {"name": "A", "dependencies": [{"ref": "B", "edge_type": "enables"}]}
            ]})
            report = validate(corpus, d)
        check = next(c for c in report["checks"] if c["check"] == "edge_count")
        assert check["specific"] == 1

    def test_place_ref_counted(self):
        corpus = _corpus(edges=[{"source": "Visit", "target": "MOMA", "type": "at"}])
        with tempfile.TemporaryDirectory() as d:
            _write_yaml(d, "activities.yaml", {"type": "activity", "entries": [
                {"name": "Visit", "place_ref": "MOMA"}
            ]})
            report = validate(corpus, d)
        check = next(c for c in report["checks"] if c["check"] == "edge_count")
        assert check["specific"] == 1


class TestMemoryCounts:

    def test_exact_match_passes(self):
        corpus = _corpus(memories=[_memory(), _memory()])
        with tempfile.TemporaryDirectory() as d:
            _write_yaml(d, "life-events.yaml", {"type": "life-event", "entries": [
                {"description": "A"}, {"description": "B"}
            ]})
            report = validate(corpus, d)
        check = next(c for c in report["checks"] if c["check"] == "memory_count")
        assert check["status"] == "pass"

    def test_mood_snapshot_separated(self):
        corpus = _corpus(memories=[
            _memory(), _memory(domain="mood", event_type="mood-snapshot")
        ])
        with tempfile.TemporaryDirectory() as d:
            _write_yaml(d, "life-events.yaml", {"type": "life-event", "entries": [{"description": "A"}]})
            _write_yaml(d, "current-state.yaml", {"type": "current-state", "entries": [{"mood": {}}]})
            report = validate(corpus, d)
        check = next(c for c in report["checks"] if c["check"] == "memory_count")
        assert check["status"] == "pass"

    def test_count_mismatch_fails(self):
        corpus = _corpus(memories=[_memory(), _memory(), _memory()])
        with tempfile.TemporaryDirectory() as d:
            _write_yaml(d, "life-events.yaml", {"type": "life-event", "entries": [{"description": "A"}]})
            report = validate(corpus, d)
        check = next(c for c in report["checks"] if c["check"] == "memory_count")
        assert check["status"] == "fail"


class TestPadFidelity:

    def test_matching_pad_passes(self):
        corpus = _corpus(nodes=[_goal_node(pad={"pleasure": 0.5, "arousal": -0.3, "dominance": 0.7})])
        with tempfile.TemporaryDirectory() as d:
            _write_yaml(d, "goals.yaml", {"type": "goal", "entries": [
                {"name": "Test Goal", "pad": {"pleasure": 0.5, "arousal": -0.3, "dominance": 0.7}}
            ]})
            report = validate(corpus, d)
        check = next(c for c in report["checks"] if c["check"] == "pad_fidelity")
        assert check["status"] == "pass"

    def test_mismatched_pad_fails(self):
        corpus = _corpus(nodes=[_goal_node(pad={"pleasure": 0.5})])
        with tempfile.TemporaryDirectory() as d:
            _write_yaml(d, "goals.yaml", {"type": "goal", "entries": [
                {"name": "Test Goal", "pad": {"pleasure": 0.9}}
            ]})
            report = validate(corpus, d)
        check = next(c for c in report["checks"] if c["check"] == "pad_fidelity")
        assert check["status"] == "fail"

    def test_no_pad_passes(self):
        corpus = _corpus(nodes=[_goal_node()])
        with tempfile.TemporaryDirectory() as d:
            _write_yaml(d, "goals.yaml", {"type": "goal", "entries": [{"name": "Test Goal"}]})
            report = validate(corpus, d)
        check = next(c for c in report["checks"] if c["check"] == "pad_fidelity")
        assert check["status"] == "pass"


class TestVocabularyValues:

    def test_valid_vocabulary_passes(self):
        with tempfile.TemporaryDirectory() as d:
            _write_yaml(d, "goals.yaml", {"type": "goal", "entries": [
                {"name": "G", "content_domain": "health", "motivation_origin": "intrinsic"}
            ]})
            report = validate(_corpus(), d)
        check = next(c for c in report["checks"] if c["check"] == "vocabulary_values")
        assert check["status"] == "pass"

    def test_invalid_vocabulary_fails(self):
        with tempfile.TemporaryDirectory() as d:
            _write_yaml(d, "goals.yaml", {"type": "goal", "entries": [
                {"name": "G", "content_domain": "invalid-domain"}
            ]})
            report = validate(_corpus(), d)
        check = next(c for c in report["checks"] if c["check"] == "vocabulary_values")
        assert check["status"] == "fail"


class TestSubjectAlignment:

    def test_resolved_subjects_pass(self):
        corpus = _corpus(memories=[_memory(subject="Frida Kahlo")])
        with tempfile.TemporaryDirectory() as d:
            _write_yaml(d, "relationships.yaml", {"type": "relationship", "entries": [
                {"name": "Frida Kahlo"}
            ]})
            report = validate(corpus, d)
        check = next(c for c in report["checks"] if c["check"] == "subject_alignment")
        assert check["status"] == "pass"

    def test_unresolved_subject_warns(self):
        corpus = _corpus(memories=[_memory(subject="Unknown Person")])
        with tempfile.TemporaryDirectory() as d:
            _write_yaml(d, "relationships.yaml", {"type": "relationship", "entries": [
                {"name": "Frida Kahlo"}
            ]})
            report = validate(corpus, d)
        check = next(c for c in report["checks"] if c["check"] == "subject_alignment")
        assert check["status"] == "warn"


class TestGoalConnectivity:

    def test_goals_with_deps(self):
        with tempfile.TemporaryDirectory() as d:
            _write_yaml(d, "goals.yaml", {"type": "goal", "entries": [
                {"name": "A", "dependencies": [{"ref": "B", "edge_type": "enables"}]},
                {"name": "B"},
            ]})
            report = validate(_corpus(), d)
        check = next(c for c in report["checks"] if c["check"] == "goal_connectivity")
        assert check["status"] == "pass"
        assert check["goals"] == 2
        assert check["with_dependencies"] == 1


class TestEnrichmentPresence:

    def test_enrichments_present(self):
        corpus = _corpus(entity_enrichments={
            "Frida": {"drive_baselines": {"curiosity": 0.8}}
        })
        with tempfile.TemporaryDirectory() as d:
            _write_yaml(d, "entity-enrichments.yaml", {"type": "entity-enrichment", "entries": [
                {"entity_ref": "Frida", "drive_baselines": {"curiosity": {"baseline": 0.8}}}
            ]})
            report = validate(corpus, d)
        check = next(c for c in report["checks"] if c["check"] == "enrichment_presence")
        assert check["status"] == "pass"

    def test_missing_enrichments_fail(self):
        corpus = _corpus(entity_enrichments={"Frida": {"drive_baselines": {"curiosity": 0.8}}})
        with tempfile.TemporaryDirectory() as d:
            report = validate(corpus, d)
        check = next(c for c in report["checks"] if c["check"] == "enrichment_presence")
        assert check["status"] == "fail"

    def test_no_corpus_enrichments_pass(self):
        with tempfile.TemporaryDirectory() as d:
            report = validate(_corpus(), d)
        check = next(c for c in report["checks"] if c["check"] == "enrichment_presence")
        assert check["status"] == "pass"


class TestDispositionPresence:

    def test_valid_disposition_passes(self):
        corpus = _corpus(disposition_profile={"socialOrient": {"score": 0.8}})
        with tempfile.TemporaryDirectory() as d:
            _write_yaml(d, "disposition-profile.yaml", {"type": "disposition-profile", "entries": [
                {"entity_ref": "Test", "social_orient": "competitive", "rule_following": "flexible",
                 "risk_appetite": "bold", "autonomy": "autonomous", "conflict_mode": "diplomatic"}
            ]})
            report = validate(corpus, d)
        check = next(c for c in report["checks"] if c["check"] == "disposition_presence")
        assert check["status"] == "pass"

    def test_missing_axis_fails(self):
        corpus = _corpus(disposition_profile={"socialOrient": {"score": 0.8}})
        with tempfile.TemporaryDirectory() as d:
            _write_yaml(d, "disposition-profile.yaml", {"type": "disposition-profile", "entries": [
                {"entity_ref": "Test", "social_orient": "competitive"}
            ]})
            report = validate(corpus, d)
        check = next(c for c in report["checks"] if c["check"] == "disposition_presence")
        assert check["status"] == "fail"

    def test_invalid_category_fails(self):
        corpus = _corpus(disposition_profile={"socialOrient": {"score": 0.8}})
        with tempfile.TemporaryDirectory() as d:
            _write_yaml(d, "disposition-profile.yaml", {"type": "disposition-profile", "entries": [
                {"entity_ref": "Test", "social_orient": "invalid", "rule_following": "flexible",
                 "risk_appetite": "bold", "autonomy": "autonomous", "conflict_mode": "diplomatic"}
            ]})
            report = validate(corpus, d)
        check = next(c for c in report["checks"] if c["check"] == "disposition_presence")
        assert check["status"] == "fail"

    def test_no_corpus_disposition_passes(self):
        with tempfile.TemporaryDirectory() as d:
            report = validate(_corpus(), d)
        check = next(c for c in report["checks"] if c["check"] == "disposition_presence")
        assert check["status"] == "pass"


class TestReport:

    def test_report_format(self):
        with tempfile.TemporaryDirectory() as d:
            report = validate(_corpus(), d)
        assert report["format"] == "import-validation-report/v1"
        assert "summary" in report
        assert "checks" in report
        assert report["summary"]["total"] == 9

    def test_all_pass_on_empty(self):
        with tempfile.TemporaryDirectory() as d:
            report = validate(_corpus(), d)
        assert report["summary"]["failed"] == 0

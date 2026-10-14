"""Pipeline reproducibility test — deterministic stages produce identical output.

Runs assemble → verify → convert twice from the same inputs and asserts
structural equivalence. Uses output-test/ data when available, falls back
to synthetic data.

Epic #551 quality standard #1: re-running on the same source produces
structurally equivalent output.
"""

import copy
import json
import os
import tempfile

import pytest
import yaml


SCRIPTS_DIR = os.path.join(os.path.dirname(__file__), '..', 'scripts')
OUTPUT_TEST_DIR = os.path.join(os.path.dirname(__file__), '..', 'output-test')
SCHEMA_PATH = os.path.join(os.path.dirname(__file__), '..', 'docs', 'verify_schema.yaml')


def _load_schema():
    from verify_corpus import load_schema
    return load_schema(SCHEMA_PATH)


def _synthetic_enriched():
    return {
        "entities": [
            {"name": "Frida Kahlo", "type": "person", "properties": {"role": "artist"},
             "traits": ["Personable"],
             "confidence": {"value": 0.9, "origin": "STATED"},
             "pad": {"pleasure": 0.3, "arousal": 0.5, "dominance": 0.7}},
            {"name": "La Casa Azul", "type": "place", "properties": {"country": "Mexico"}},
            {"name": "Artistic mastery", "type": "goal",
             "goal_tier": "strategic", "goal_horizon": "long",
             "content_domain": "self-actualisation", "motivation_origin": "intrinsic",
             "pad": {"pleasure": 0.6, "arousal": 0.4, "dominance": 0.5}},
            {"name": "Pain as expression", "type": "concept"},
            {"name": "Los Cachuchas", "type": "organisation", "traits": ["Organisational"]},
        ],
        "relationships": [
            {"source": "Frida Kahlo", "target": "La Casa Azul", "type": "lives-at",
             "confidence_value": 0.9},
            {"source": "Artistic mastery", "target": "Pain as expression", "type": "contributes-to"},
        ],
        "memories": [
            {"text": "Frida contracted polio at age six", "domain": "experience",
             "event_type": "observation", "subject": "Frida Kahlo", "period": "1913",
             "confidence": {"origin": "STATED"},
             "pad": {"pleasure": -0.7, "arousal": 0.6, "dominance": -0.4},
             "occ_emotions": [{"emotion": "distress", "intensity": 0.8}]},
            {"text": "Current emotional state", "domain": "mood",
             "event_type": "mood-snapshot", "subject": "Frida Kahlo",
             "pad": {"pleasure": 0.2, "arousal": -0.1, "dominance": 0.3}},
        ],
        "entity_enrichments": {
            "Frida Kahlo": {
                "drive_baselines": {"curiosity": {"baseline": 0.85, "justification": "Artistic exploration"}},
            },
        },
        "disposition_profile": {
            "socialOrient": {"score": 0.5, "justification": "Cooperative nature"},
            "ruleFollowing": {"score": 0.2, "justification": "Defiant spirit"},
            "riskAppetite": {"score": 0.8, "justification": "Bold choices"},
            "autonomy": {"score": 0.9, "justification": "Fiercely independent"},
            "conflictMode": {"score": 0.6, "justification": "Diplomatic when needed"},
        },
    }


def _synthetic_corpus():
    """Minimal corpus.json matching what assemble would produce."""
    return {
        "format": "cognitive-workbench-corpus/v2",
        "subject": "Frida Kahlo",
        "nodes": [
            {"name": "Frida Kahlo", "subgraph_type": "person", "properties": {"role": "artist"},
             "traits": ["Personable"],
             "confidence": {"value": 0.9, "origin": "STATED"},
             "pad": {"pleasure": 0.3, "arousal": 0.5, "dominance": 0.7}},
            {"name": "La Casa Azul", "subgraph_type": "place", "properties": {"country": "Mexico"}},
            {"name": "Artistic mastery", "subgraph_type": "goal",
             "goal_tier": "strategic", "goal_horizon": "long",
             "content_domain": "self-actualisation", "motivation_origin": "intrinsic",
             "pad": {"pleasure": 0.6, "arousal": 0.4, "dominance": 0.5}},
            {"name": "Pain as expression", "subgraph_type": "concept"},
            {"name": "Los Cachuchas", "subgraph_type": "organisation", "traits": ["Organisational"]},
        ],
        "edges": [
            {"source": "Frida Kahlo", "target": "La Casa Azul", "type": "lives-at",
             "confidence_value": 0.9},
            {"source": "Artistic mastery", "target": "Pain as expression", "type": "contributes-to"},
        ],
        "memories": [
            {"text": "Frida contracted polio at age six", "domain": "experience",
             "event_type": "observation", "subject": "Frida Kahlo", "period": "1913",
             "confidence": {"origin": "STATED"},
             "pad": {"pleasure": -0.7, "arousal": 0.6, "dominance": -0.4},
             "occ_emotions": [{"emotion": "distress", "intensity": 0.8}]},
            {"text": "Current emotional state", "domain": "mood",
             "event_type": "mood-snapshot", "subject": "Frida Kahlo",
             "pad": {"pleasure": 0.2, "arousal": -0.1, "dominance": 0.3}},
        ],
        "entity_enrichments": {
            "Frida Kahlo": {
                "drive_baselines": {"curiosity": {"baseline": 0.85, "justification": "Artistic exploration"}},
            },
        },
        "disposition_profile": {
            "socialOrient": {"score": 0.5, "justification": "Cooperative nature"},
            "ruleFollowing": {"score": 0.2, "justification": "Defiant spirit"},
            "riskAppetite": {"score": 0.8, "justification": "Bold choices"},
            "autonomy": {"score": 0.9, "justification": "Fiercely independent"},
            "conflictMode": {"score": 0.6, "justification": "Diplomatic when needed"},
        },
    }


def _read_output_dir(path):
    """Read all files in a directory into a dict."""
    result = {}
    for fname in sorted(os.listdir(path)):
        fpath = os.path.join(path, fname)
        if fname.endswith(".yaml"):
            with open(fpath) as f:
                result[fname] = yaml.safe_load(f)
        elif fname.endswith(".json"):
            with open(fpath) as f:
                result[fname] = json.load(f)
    return result


def _strip_timestamps(obj):
    """Remove timestamp fields for structural comparison."""
    if isinstance(obj, dict):
        return {k: _strip_timestamps(v) for k, v in obj.items() if k != "timestamp"}
    if isinstance(obj, list):
        return [_strip_timestamps(item) for item in obj]
    return obj


# --- Convert reproducibility ---

class TestConvertReproducibility:

    def test_convert_identical_on_synthetic(self):
        from convert_corpus import convert
        corpus = _synthetic_corpus()
        schema = {"entity_aliases": {"Frida": "Frida Kahlo"}}

        with tempfile.TemporaryDirectory() as d1:
            convert(copy.deepcopy(corpus), schema, d1)
            out1 = _read_output_dir(d1)

        with tempfile.TemporaryDirectory() as d2:
            convert(copy.deepcopy(corpus), schema, d2)
            out2 = _read_output_dir(d2)

        assert set(out1.keys()) == set(out2.keys()), f"File sets differ: {out1.keys()} vs {out2.keys()}"
        for fname in out1:
            assert out1[fname] == out2[fname], f"Diff in {fname}"

    @pytest.mark.skipif(
        not os.path.exists(os.path.join(OUTPUT_TEST_DIR, 'corpus.json')),
        reason="output-test/corpus.json not available"
    )
    def test_convert_identical_on_real_corpus(self):
        from convert_corpus import convert

        with open(os.path.join(OUTPUT_TEST_DIR, 'corpus.json')) as f:
            corpus = json.load(f)

        schema = {}
        if os.path.exists(SCHEMA_PATH):
            with open(SCHEMA_PATH) as f:
                schema = yaml.safe_load(f)

        with tempfile.TemporaryDirectory() as d1:
            convert(copy.deepcopy(corpus), schema, d1)
            out1 = _read_output_dir(d1)

        with tempfile.TemporaryDirectory() as d2:
            convert(copy.deepcopy(corpus), schema, d2)
            out2 = _read_output_dir(d2)

        assert set(out1.keys()) == set(out2.keys())
        for fname in out1:
            assert out1[fname] == out2[fname], f"Diff in {fname}"


# --- Verify reproducibility ---

class TestVerifyReproducibility:

    def test_verify_identical_on_synthetic(self):
        from verify_corpus import verify
        corpus = _synthetic_corpus()
        schema = _load_schema()

        report1 = verify(copy.deepcopy(corpus), schema)
        report2 = verify(copy.deepcopy(corpus), schema)

        assert _strip_timestamps(report1) == _strip_timestamps(report2)

    @pytest.mark.skipif(
        not os.path.exists(os.path.join(OUTPUT_TEST_DIR, 'corpus.json')),
        reason="output-test/corpus.json not available"
    )
    def test_verify_identical_on_real_corpus(self):
        from verify_corpus import verify

        with open(os.path.join(OUTPUT_TEST_DIR, 'corpus.json')) as f:
            corpus = json.load(f)
        schema = _load_schema()

        report1 = verify(copy.deepcopy(corpus), schema)
        report2 = verify(copy.deepcopy(corpus), schema)

        assert _strip_timestamps(report1) == _strip_timestamps(report2)


# --- Assemble reproducibility ---

class TestAssembleReproducibility:

    def test_assemble_identical_on_synthetic(self):
        from assemble_corpus import assemble
        enriched = _synthetic_enriched()

        corpus1 = assemble(copy.deepcopy(enriched))
        corpus2 = assemble(copy.deepcopy(enriched))

        assert corpus1 == corpus2

    @pytest.mark.skipif(
        not os.path.exists(os.path.join(OUTPUT_TEST_DIR, 'enriched.json')),
        reason="output-test/enriched.json not available"
    )
    def test_assemble_identical_on_real_enriched(self):
        from assemble_corpus import assemble

        with open(os.path.join(OUTPUT_TEST_DIR, 'enriched.json')) as f:
            enriched = json.load(f)

        corpus1 = assemble(copy.deepcopy(enriched))
        corpus2 = assemble(copy.deepcopy(enriched))

        assert corpus1 == corpus2


# --- Full pipeline reproducibility ---

class TestFullPipelineReproducibility:

    def test_full_chain_synthetic(self):
        from assemble_corpus import assemble
        from verify_corpus import verify, apply_fixes
        from convert_corpus import convert

        enriched = _synthetic_enriched()
        schema = _load_schema()

        def run_pipeline(enriched_data):
            corpus = assemble(copy.deepcopy(enriched_data))
            report = verify(corpus, schema)
            fixed, _ = apply_fixes(corpus, report, schema)
            with tempfile.TemporaryDirectory() as d:
                convert(fixed, schema, d)
                return _read_output_dir(d)

        out1 = run_pipeline(enriched)
        out2 = run_pipeline(enriched)

        assert set(out1.keys()) == set(out2.keys())
        for fname in out1:
            assert out1[fname] == out2[fname], f"Full pipeline diff in {fname}"

    @pytest.mark.skipif(
        not os.path.exists(os.path.join(OUTPUT_TEST_DIR, 'enriched.json')),
        reason="output-test/enriched.json not available"
    )
    def test_full_chain_real_data(self):
        from assemble_corpus import assemble
        from verify_corpus import verify, apply_fixes
        from convert_corpus import convert

        with open(os.path.join(OUTPUT_TEST_DIR, 'enriched.json')) as f:
            enriched = json.load(f)
        schema = _load_schema()

        def run_pipeline(enriched_data):
            corpus = assemble(copy.deepcopy(enriched_data))
            report = verify(corpus, schema)
            fixed, _ = apply_fixes(corpus, report, schema)
            with tempfile.TemporaryDirectory() as d:
                convert(fixed, schema, d)
                return _read_output_dir(d)

        out1 = run_pipeline(enriched)
        out2 = run_pipeline(enriched)

        assert set(out1.keys()) == set(out2.keys())
        for fname in out1:
            assert out1[fname] == out2[fname], f"Full pipeline diff in {fname}"

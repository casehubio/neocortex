"""Tests for convert_corpus.py — corpus.json to biography YAML converter."""

import os
import json
import tempfile
import pytest
import yaml

from convert_corpus import slugify, make_id, convert, _score_to_category


# --- Synthetic data builders ---

def _node(name, subgraph_type, **overrides):
    node = {"name": name, "subgraph_type": subgraph_type, "properties": {}}
    node.update(overrides)
    return node


def _goal_node(name="Master painting", **overrides):
    return _node(name, "goal", goal_tier="strategic", goal_horizon="long", **overrides)


def _person_node(name="Diego Rivera", **overrides):
    return _node(name, "person", **overrides)


def _place_node(name="La Casa Azul", **overrides):
    return _node(name, "place", **overrides)


def _activity_node(name="Visit museum", **overrides):
    return _node(name, "activity", **overrides)


def _project_node(name="Las dos Fridas", **overrides):
    return _node(name, "project", **overrides)


def _organisation_node(name="Academy of San Carlos", **overrides):
    return _node(name, "organisation", **overrides)


def _concept_node(name="Pain as artistic fuel", **overrides):
    return _node(name, "concept", **overrides)


def _edge(source, target, edge_type="associated-with", **overrides):
    e = {"source": source, "target": target, "type": edge_type}
    e.update(overrides)
    return e


def _memory(text="A remembered experience", domain="experience", **overrides):
    m = {"text": text, "domain": domain}
    m.update(overrides)
    return m


def _corpus(nodes=None, edges=None, memories=None,
            entity_enrichments=None, disposition_profile=None):
    c = {
        "subject": "Test Subject",
        "nodes": nodes or [],
        "edges": edges or [],
        "memories": memories or [],
    }
    if entity_enrichments is not None:
        c["entity_enrichments"] = entity_enrichments
    if disposition_profile is not None:
        c["disposition_profile"] = disposition_profile
    return c


def _convert(corpus, schema=None):
    """Run convert() into a temp dir and return (report, written_files dict)."""
    schema = schema or {}
    with tempfile.TemporaryDirectory() as tmpdir:
        report = convert(corpus, schema, tmpdir)
        files = {}
        for fname in os.listdir(tmpdir):
            path = os.path.join(tmpdir, fname)
            if fname.endswith(".yaml"):
                with open(path) as f:
                    files[fname] = yaml.safe_load(f)
            elif fname.endswith(".json"):
                with open(path) as f:
                    files[fname] = json.load(f)
        return report, files


# --- slugify ---

def test_slugify_basic():
    assert slugify("Hello World") == "hello-world"


def test_slugify_special_chars():
    assert slugify("Frida's Art (1940)") == "frida-s-art-1940"


def test_slugify_truncation():
    assert len(slugify("a" * 100, max_len=20)) == 20


# --- make_id ---

def test_make_id_unique():
    seen = set()
    id1 = make_id("goal", "Master painting", seen)
    assert id1 == "goal-master-painting"
    assert id1 in seen


def test_make_id_collision_suffix():
    seen = set()
    id1 = make_id("goal", "test", seen)
    id2 = make_id("goal", "test", seen)
    assert id1 == "goal-test"
    assert id2 == "goal-test-2"
    assert id1 != id2


def test_make_id_multiple_collisions():
    seen = set()
    ids = [make_id("x", "same", seen) for _ in range(5)]
    assert len(set(ids)) == 5
    assert ids[0] == "x-same"
    assert ids[1] == "x-same-2"
    assert ids[4] == "x-same-5"


# --- Node-to-entry mapping per subgraph_type ---

class TestNodeMapping:

    def test_goal_entry(self):
        node = _goal_node(
            goal_status="active", goal_urgency="high",
            goal_need_tier="self-actualisation", goal_priority="1",
            goal_feasibility="achievable", content_domain="art",
            content_subdomain="painting", motivation_origin="intrinsic",
            clarity="clear", evidence_basis="strong",
            confidence={"origin": "STATED"},
            pad={"pleasure": 0.7, "arousal": 0.3, "dominance": 0.5},
            valid_from="1925", valid_until="1954",
        )
        report, files = _convert(_corpus(nodes=[node]))
        assert report["nodes_mapped"] == 1
        entry = files["goals.yaml"]["entries"][0]
        assert entry["name"] == "Master painting"
        assert entry["tier"] == "strategic"
        assert entry["horizon"] == "long"
        assert entry["status"] == "active"
        assert entry["urgency"] == "high"
        assert entry["content_domain"] == "art"
        assert entry["motivation_origin"] == "intrinsic"
        assert entry["confidence_origin"] == "STATED"
        assert entry["pad"] == {"pleasure": 0.7, "arousal": 0.3, "dominance": 0.5}
        assert entry["valid_from"] == "1925"

    def test_person_entry(self):
        node = _person_node(traits=["Personable"], properties={"role": "spouse"})
        report, files = _convert(_corpus(nodes=[node]))
        entry = files["relationships.yaml"]["entries"][0]
        assert entry["name"] == "Diego Rivera"
        assert entry["traits"] == ["Personable"]
        assert entry["properties"]["role"] == "spouse"

    def test_place_entry(self):
        node = _place_node(properties={"country": "Mexico", "lifecycle.founded": "1904"})
        report, files = _convert(_corpus(nodes=[node]))
        entry = files["places.yaml"]["entries"][0]
        assert entry["name"] == "La Casa Azul"
        assert "country" in entry["properties"]
        assert "lifecycle.founded" not in entry["properties"]

    def test_activity_entry(self):
        node = _activity_node(
            properties={"type": "exhibition"},
            trait_properties={"date": "1938-11"},
        )
        report, files = _convert(_corpus(nodes=[node]))
        entry = files["activities.yaml"]["entries"][0]
        assert entry["name"] == "Visit museum"
        assert entry["activity_type"] == "exhibition"
        assert entry["date"] == "1938-11"

    def test_project_entry(self):
        node = _project_node(properties={"description": "A double self-portrait", "medium": "oil"})
        report, files = _convert(_corpus(nodes=[node]))
        entry = files["projects.yaml"]["entries"][0]
        assert entry["name"] == "Las dos Fridas"
        assert entry["description"] == "A double self-portrait"
        assert entry["properties"]["medium"] == "oil"
        assert "description" not in entry["properties"]

    def test_organisation_entry(self):
        node = _organisation_node(traits=["Organisational"], properties={"founded": "1785"})
        report, files = _convert(_corpus(nodes=[node]))
        entry = files["organisations.yaml"]["entries"][0]
        assert entry["name"] == "Academy of San Carlos"
        assert entry["traits"] == ["Organisational"]
        assert entry["properties"]["founded"] == "1785"

    def test_concept_maps_to_belief(self):
        node = _concept_node()
        report, files = _convert(_corpus(nodes=[node]))
        entry = files["beliefs.yaml"]["entries"][0]
        assert "name" not in entry
        assert entry["description"] == "Pain as artistic fuel"

    def test_unknown_subgraph_type_warns(self):
        node = _node("Mystery", "unknown_type")
        report, _ = _convert(_corpus(nodes=[node]))
        assert report["nodes_mapped"] == 0
        assert any("Unknown subgraph_type" in w for w in report["warnings"])

    def test_pad_filters_none_values(self):
        node = _goal_node(pad={"pleasure": 0.5, "arousal": None, "dominance": 0.3})
        _, files = _convert(_corpus(nodes=[node]))
        pad = files["goals.yaml"]["entries"][0]["pad"]
        assert "arousal" not in pad
        assert pad["pleasure"] == 0.5


# --- Edge mapping: specific fields vs universal associations ---

class TestEdgeMapping:

    def test_goal_dependencies(self):
        nodes = [_goal_node("Learn anatomy"), _goal_node("Master painting")]
        edges = [_edge("Learn anatomy", "Master painting", "contributes-to")]
        report, files = _convert(_corpus(nodes=nodes, edges=edges))
        entry = next(e for e in files["goals.yaml"]["entries"] if e["name"] == "Learn anatomy")
        assert entry["dependencies"][0]["ref"] == "Master painting"
        assert entry["dependencies"][0]["edge_type"] == "contributes-to"
        assert report["edges_specific"] == 1

    def test_activity_place_ref(self):
        nodes = [_activity_node("Museum visit"), _place_node("MOMA")]
        edges = [_edge("Museum visit", "MOMA", "at")]
        _, files = _convert(_corpus(nodes=nodes, edges=edges))
        entry = files["activities.yaml"]["entries"][0]
        assert entry["place_ref"] == "MOMA"

    def test_activity_participants(self):
        nodes = [_activity_node("Dinner"), _person_node("Diego Rivera")]
        edges = [_edge("Dinner", "Diego Rivera", "participated")]
        _, files = _convert(_corpus(nodes=nodes, edges=edges))
        entry = files["activities.yaml"]["entries"][0]
        assert entry["participants"][0]["entity_ref"] == "Diego Rivera"

    def test_non_specific_edges_become_associations(self):
        nodes = [_person_node("Diego Rivera"), _place_node("La Casa Azul")]
        edges = [_edge("Diego Rivera", "La Casa Azul", "lives-at", confidence_value=0.9)]
        report, files = _convert(_corpus(nodes=nodes, edges=edges))
        entry = files["relationships.yaml"]["entries"][0]
        assert len(entry["associations"]) == 1
        assert entry["associations"][0]["entity_ref"] == "La Casa Azul"
        assert entry["associations"][0]["edge_type"] == "lives-at"
        assert entry["associations"][0]["confidence"] == 0.9
        assert report["edges_association"] == 1

    def test_mixed_specific_and_association_edges(self):
        nodes = [_goal_node("Freedom"), _goal_node("Independence")]
        edges = [
            _edge("Freedom", "Independence", "enables"),
            _edge("Freedom", "Independence", "inspired-by"),
        ]
        report, files = _convert(_corpus(nodes=nodes, edges=edges))
        freedom = next(e for e in files["goals.yaml"]["entries"] if e["name"] == "Freedom")
        assert len(freedom["dependencies"]) == 1
        assert len(freedom["associations"]) == 1
        assert report["edges_specific"] == 1
        assert report["edges_association"] == 1

    def test_unattributable_edges_counted(self):
        nodes = [_person_node("A")]
        edges = [_edge("A", "B", "knows"), _edge("C", "D", "related")]
        report, _ = _convert(_corpus(nodes=nodes, edges=edges))
        assert report["edges_unattributable"] >= 1


# --- Alias resolution ---

class TestAliasResolution:

    def test_edge_source_alias_resolved(self):
        schema = {"entity_aliases": {"Frida": "Frida Kahlo"}}
        nodes = [_person_node("Frida Kahlo"), _place_node("Studio")]
        edges = [_edge("Frida", "Studio", "works-at")]
        _, files = _convert(_corpus(nodes=nodes, edges=edges), schema)
        entry = files["relationships.yaml"]["entries"][0]
        assert entry["name"] == "Frida Kahlo"
        assert entry["associations"][0]["entity_ref"] == "Studio"

    def test_edge_target_alias_resolved(self):
        schema = {"entity_aliases": {"Diego": "Diego Rivera"}}
        nodes = [_person_node("Frida Kahlo"), _person_node("Diego Rivera")]
        edges = [_edge("Frida Kahlo", "Diego", "married-to")]
        _, files = _convert(_corpus(nodes=nodes, edges=edges), schema)
        entry = next(e for e in files["relationships.yaml"]["entries"]
                     if e["name"] == "Frida Kahlo")
        assert entry["associations"][0]["entity_ref"] == "Diego Rivera"

    def test_no_alias_passes_through(self):
        schema = {"entity_aliases": {}}
        nodes = [_person_node("Frida Kahlo")]
        edges = [_edge("Frida Kahlo", "Unknown", "knows")]
        _, files = _convert(_corpus(nodes=nodes, edges=edges), schema)
        entry = files["relationships.yaml"]["entries"][0]
        assert entry["associations"][0]["entity_ref"] == "Unknown"


# --- Memory-to-entry mapping ---

class TestMemoryMapping:

    def test_experience_to_life_event(self):
        mem = _memory(
            text="Frida was in a bus accident",
            domain="experience",
            event_type="traumatic",
            subject="Frida Kahlo",
            period="1925-09-17",
            confidence={"origin": "STATED"},
            related_entities=["Diego Rivera"],
            pad={"pleasure": -0.8, "arousal": 0.9, "dominance": -0.6},
        )
        report, files = _convert(_corpus(memories=[mem]))
        assert report["memories_mapped"] == 1
        entry = files["life-events.yaml"]["entries"][0]
        assert entry["description"] == "Frida was in a bus accident"
        assert entry["domain"] == "experience"
        assert entry["event_type"] == "traumatic"
        assert entry["subject"] == "Frida Kahlo"
        assert entry["confidence_origin"] == "STATED"
        assert entry["pad"]["pleasure"] == -0.8

    def test_mood_snapshot_to_current_state(self):
        mem = _memory(
            text="Feeling reflective after the exhibition",
            domain="mood",
            event_type="mood-snapshot",
            pad={"pleasure": 0.3, "arousal": -0.2, "dominance": 0.1},
            period="1938-11-15",
        )
        report, files = _convert(_corpus(memories=[mem]))
        assert report["memories_mapped"] == 1
        entry = files["current-state.yaml"]["entries"][0]
        assert entry["mood"]["pleasure"] == 0.3
        assert entry["recent_context"] == "Feeling reflective after the exhibition"
        assert entry["timestamp"] == "1938-11-15"

    def test_enrichment_fields_carried_through(self):
        mem = _memory(
            occ_emotions=["pride", "joy"],
            sec_appraisal={"relevance": 0.9},
            action_tendencies=["approach"],
            cognitive_distortions=[],
            drive_impact={"curiosity": 0.4},
            caps_inputs={"agency_granted": 0.7},
            goal_impacts=[{"goal": "Paint", "impact": "positive"}],
            habituation={"count": 3},
            gut_feeling={"valence": "APPROACH"},
        )
        _, files = _convert(_corpus(memories=[mem]))
        entry = files["life-events.yaml"]["entries"][0]
        assert entry["occ_emotions"] == ["pride", "joy"]
        assert entry["sec_appraisal"]["relevance"] == 0.9
        assert entry["action_tendencies"] == ["approach"]
        assert entry["drive_impact"]["curiosity"] == 0.4
        assert entry["caps_inputs"]["agency_granted"] == 0.7
        assert entry["goal_impacts"][0]["goal"] == "Paint"
        assert entry["habituation"]["count"] == 3
        assert entry["gut_feeling"]["valence"] == "APPROACH"

    def test_empty_enrichment_fields_omitted(self):
        mem = _memory(cognitive_distortions=[], caps_inputs={})
        _, files = _convert(_corpus(memories=[mem]))
        entry = files["life-events.yaml"]["entries"][0]
        assert "cognitive_distortions" not in entry
        assert "caps_inputs" not in entry

    def test_sub_thoughts_mapped(self):
        mem = _memory(sub_thoughts=[
            {"type": "causal-inference", "text": "because of the accident"},
            {"type": "evaluative", "text": "this changed everything"},
            {"type": "", "text": "incomplete"},
        ])
        _, files = _convert(_corpus(memories=[mem]))
        entry = files["life-events.yaml"]["entries"][0]
        assert len(entry["sub_thoughts"]) == 2
        assert entry["sub_thoughts"][0]["type"] == "causal-inference"

    def test_memory_pad_filters_none(self):
        mem = _memory(pad={"pleasure": 0.5, "arousal": None, "dominance": 0.3})
        _, files = _convert(_corpus(memories=[mem]))
        entry = files["life-events.yaml"]["entries"][0]
        assert "arousal" not in entry["pad"]

    def test_non_dict_pad_ignored(self):
        mem = _memory(pad="invalid")
        _, files = _convert(_corpus(memories=[mem]))
        entry = files["life-events.yaml"]["entries"][0]
        assert "pad" not in entry


# --- Entity enrichment mapping ---

class TestEntityEnrichment:

    def test_drive_baselines_dict_format(self):
        enrichments = {
            "Frida Kahlo": {
                "drive_baselines": {
                    "curiosity": {"baseline": 0.8, "justification": "Artistic exploration"},
                    "competence": {"baseline": 0.7, "justification": "Mastery drive"},
                },
            },
        }
        _, files = _convert(_corpus(entity_enrichments=enrichments))
        entry = files["entity-enrichments.yaml"]["entries"][0]
        assert entry["entity_ref"] == "Frida Kahlo"
        assert entry["drive_baselines"]["curiosity"]["baseline"] == 0.8
        assert entry["drive_baselines"]["curiosity"]["justification"] == "Artistic exploration"

    def test_drive_baselines_float_format(self):
        enrichments = {
            "Frida Kahlo": {
                "drive_baselines": {"curiosity": 0.8, "competence": 0.7},
            },
        }
        _, files = _convert(_corpus(entity_enrichments=enrichments))
        entry = files["entity-enrichments.yaml"]["entries"][0]
        assert entry["drive_baselines"]["curiosity"]["baseline"] == 0.8
        assert entry["drive_baselines"]["curiosity"]["justification"] is None

    def test_drive_baselines_mixed_format(self):
        enrichments = {
            "Frida Kahlo": {
                "drive_baselines": {
                    "curiosity": {"baseline": 0.8, "justification": "reason"},
                    "competence": 0.6,
                },
            },
        }
        _, files = _convert(_corpus(entity_enrichments=enrichments))
        entry = files["entity-enrichments.yaml"]["entries"][0]
        assert entry["drive_baselines"]["curiosity"]["baseline"] == 0.8
        assert entry["drive_baselines"]["curiosity"]["justification"] == "reason"
        assert entry["drive_baselines"]["competence"]["baseline"] == 0.6
        assert entry["drive_baselines"]["competence"]["justification"] is None

    def test_caps_patterns(self):
        enrichments = {
            "Frida Kahlo": {
                "caps_patterns": [
                    {"pattern": "threat-response", "frequency": "high"},
                ],
            },
        }
        _, files = _convert(_corpus(entity_enrichments=enrichments))
        entry = files["entity-enrichments.yaml"]["entries"][0]
        assert entry["caps_patterns"][0]["pattern"] == "threat-response"
        assert entry["caps_patterns"][0]["frequency"] == "high"

    def test_caps_patterns_with_input_activations(self):
        enrichments = {
            "Frida Kahlo": {
                "caps_patterns": [
                    {
                        "pattern": "betrayal → trust_violation → AUTONOMY_ASSERTION",
                        "frequency": 3,
                        "confidence": 0.87,
                        "input_activations": {"betrayal": 0.9, "abandonment": 0.6},
                    },
                ],
            },
        }
        _, files = _convert(_corpus(entity_enrichments=enrichments))
        entry = files["entity-enrichments.yaml"]["entries"][0]
        assert entry["caps_patterns"][0]["confidence"] == 0.87
        assert entry["caps_patterns"][0]["input_activations"] == {"betrayal": 0.9, "abandonment": 0.6}

    def test_behavioural_attractors(self):
        enrichments = {
            "Frida Kahlo": {
                "behavioural_attractors": [
                    {"name": "creative-defiance", "description": "Rebels through art"},
                ],
            },
        }
        _, files = _convert(_corpus(entity_enrichments=enrichments))
        entry = files["entity-enrichments.yaml"]["entries"][0]
        assert entry["behavioural_attractors"][0]["name"] == "creative-defiance"

    def test_relationship_stages(self):
        enrichments = {
            "Frida Kahlo": {
                "relationship_stages": {"Diego Rivera": "turbulent"},
            },
        }
        _, files = _convert(_corpus(entity_enrichments=enrichments))
        entry = files["entity-enrichments.yaml"]["entries"][0]
        assert entry["relationship_stages"]["Diego Rivera"] == "turbulent"


# --- Disposition profile ---

class TestDispositionProfile:

    def test_score_to_category_all_axes(self):
        assert _score_to_category("socialOrient", 0.0) == "independent"
        assert _score_to_category("socialOrient", 0.3) == "independent"
        assert _score_to_category("socialOrient", 0.4) == "cooperative"
        assert _score_to_category("socialOrient", 0.7) == "competitive"
        assert _score_to_category("socialOrient", 1.0) == "competitive"

        assert _score_to_category("ruleFollowing", 0.0) == "defiant"
        assert _score_to_category("ruleFollowing", 0.4) == "flexible"
        assert _score_to_category("ruleFollowing", 0.7) == "conformist"

        assert _score_to_category("riskAppetite", 0.0) == "conservative"
        assert _score_to_category("riskAppetite", 0.4) == "calculated"
        assert _score_to_category("riskAppetite", 0.7) == "bold"

        assert _score_to_category("autonomy", 0.0) == "dependent"
        assert _score_to_category("autonomy", 0.4) == "collaborative"
        assert _score_to_category("autonomy", 0.7) == "autonomous"

        assert _score_to_category("conflictMode", 0.0) == "avoidant"
        assert _score_to_category("conflictMode", 0.4) == "diplomatic"
        assert _score_to_category("conflictMode", 0.7) == "confrontational"

    def test_boundary_values(self):
        assert _score_to_category("socialOrient", 0.39) == "independent"
        assert _score_to_category("socialOrient", 0.40) == "cooperative"
        assert _score_to_category("socialOrient", 0.69) == "cooperative"
        assert _score_to_category("socialOrient", 0.70) == "competitive"

    def test_unknown_axis_returns_string(self):
        assert _score_to_category("unknownAxis", 0.5) == "0.5"

    def test_full_disposition_profile_mapping(self):
        disp = {
            "socialOrient": {"score": 0.8},
            "ruleFollowing": {"score": 0.2},
            "riskAppetite": {"score": 0.5},
            "autonomy": {"score": 0.9},
            "conflictMode": {"score": 0.1},
        }
        _, files = _convert(_corpus(disposition_profile=disp))
        entry = files["disposition-profile.yaml"]["entries"][0]
        assert entry["entity_ref"] == "Test Subject"
        assert entry["social_orient"] == "competitive"
        assert entry["rule_following"] == "defiant"
        assert entry["risk_appetite"] == "calculated"
        assert entry["autonomy"] == "autonomous"
        assert entry["conflict_mode"] == "avoidant"

    def test_disposition_subject_from_schema(self):
        schema = {"converter": {"subject_name": "Frida Kahlo"}}
        disp = {"socialOrient": {"score": 0.5}}
        _, files = _convert(
            _corpus(disposition_profile=disp), schema
        )
        entry = files["disposition-profile.yaml"]["entries"][0]
        assert entry["entity_ref"] == "Frida Kahlo"


# --- Report and file output ---

class TestReportAndOutput:

    def test_report_counts(self):
        corpus = _corpus(
            nodes=[_goal_node(), _person_node()],
            edges=[
                _edge("Master painting", "Diego Rivera", "contributes-to"),
                _edge("Diego Rivera", "Master painting", "associated-with"),
            ],
            memories=[_memory(), _memory(domain="mood", event_type="mood-snapshot")],
        )
        report, files = _convert(corpus)
        assert report["nodes_mapped"] == 2
        assert report["memories_mapped"] == 2
        assert report["edges_mapped"] == report["edges_specific"] + report["edges_association"]

    def test_conversion_report_json_written(self):
        _, files = _convert(_corpus(nodes=[_goal_node()]))
        assert "conversion-report.json" in files
        assert files["conversion-report.json"]["nodes_mapped"] == 1

    def test_yaml_files_have_type_field(self):
        corpus = _corpus(
            nodes=[_goal_node(), _person_node()],
            memories=[_memory()],
        )
        _, files = _convert(corpus)
        assert files["goals.yaml"]["type"] == "goal"
        assert files["relationships.yaml"]["type"] == "relationship"
        assert files["life-events.yaml"]["type"] == "life-event"

    def test_all_subgraph_types_produce_correct_files(self):
        corpus = _corpus(nodes=[
            _goal_node(), _person_node(), _place_node(),
            _activity_node(), _project_node(), _organisation_node(),
            _concept_node(),
        ])
        _, files = _convert(corpus)
        assert "goals.yaml" in files
        assert "relationships.yaml" in files
        assert "places.yaml" in files
        assert "activities.yaml" in files
        assert "projects.yaml" in files
        assert "organisations.yaml" in files
        assert "beliefs.yaml" in files

    def test_empty_corpus_produces_only_report(self):
        report, files = _convert(_corpus())
        assert report["nodes_mapped"] == 0
        assert report["memories_mapped"] == 0
        assert "conversion-report.json" in files
        assert len(files) == 1

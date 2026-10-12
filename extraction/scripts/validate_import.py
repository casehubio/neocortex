"""Post-conversion validation for the biography pipeline.

Compares original corpus.json against converter output YAML files
to verify structural completeness before Java import.

Usage:
  validate_import.py <corpus.json> <output-dir> [--schema <path>]
"""
import argparse
import json
import math
import os
import sys

import yaml


NODE_TYPE_MAP = {
    "goal": "goal",
    "person": "relationship",
    "place": "place",
    "activity": "activity",
    "project": "project",
    "organisation": "organisation",
    "concept": "belief",
}

TYPE_TO_FILE = {
    "goal": "goals.yaml",
    "relationship": "relationships.yaml",
    "place": "places.yaml",
    "activity": "activities.yaml",
    "project": "projects.yaml",
    "organisation": "organisations.yaml",
    "belief": "beliefs.yaml",
}

GOAL_VOCABULARY = {
    "content_domain": {
        "morality-and-virtue", "religion-and-spirituality", "self-actualisation",
        "avoidance", "social-relating", "health", "family",
        "mastery-and-competence", "financial-and-occupational",
    },
    "motivation_origin": {"intrinsic", "identified", "introjected", "external"},
}

DISPOSITION_AXES = {"social_orient", "rule_following", "risk_appetite", "autonomy", "conflict_mode"}
DISPOSITION_VALUES = {
    "social_orient": {"independent", "cooperative", "competitive"},
    "rule_following": {"defiant", "flexible", "conformist"},
    "risk_appetite": {"conservative", "calculated", "bold"},
    "autonomy": {"dependent", "collaborative", "autonomous"},
    "conflict_mode": {"avoidant", "diplomatic", "confrontational"},
}


def _load_yaml(path):
    if not os.path.exists(path):
        return None
    with open(path) as f:
        return yaml.safe_load(f)


def _check_node_counts(corpus, output_dir):
    corpus_counts = {}
    for node in corpus.get("nodes", []):
        sg = node.get("subgraph_type", "").lower()
        yaml_type = NODE_TYPE_MAP.get(sg)
        if yaml_type:
            corpus_counts[yaml_type] = corpus_counts.get(yaml_type, 0) + 1

    output_counts = {}
    for yaml_type, filename in TYPE_TO_FILE.items():
        data = _load_yaml(os.path.join(output_dir, filename))
        if data and "entries" in data:
            output_counts[yaml_type] = len(data["entries"])

    failures = []
    for yaml_type, expected in corpus_counts.items():
        actual = output_counts.get(yaml_type, 0)
        tolerance = max(1, int(expected * 0.05))
        if abs(actual - expected) > tolerance:
            failures.append(f"{yaml_type}: expected ~{expected}, got {actual}")

    return {
        "check": "node_count",
        "status": "pass" if not failures else "fail",
        "corpus_counts": corpus_counts,
        "output_counts": output_counts,
        "failures": failures,
    }


def _check_edge_counts(corpus, output_dir):
    corpus_edge_count = len(corpus.get("edges", []))

    total_associations = 0
    total_dependencies = 0
    for filename in os.listdir(output_dir):
        if not filename.endswith(".yaml"):
            continue
        data = _load_yaml(os.path.join(output_dir, filename))
        if not data or "entries" not in data:
            continue
        for entry in data["entries"]:
            total_associations += len(entry.get("associations", []))
            total_dependencies += len(entry.get("dependencies", []))
            if entry.get("place_ref"):
                total_dependencies += 1
            total_dependencies += len(entry.get("participants", []))

    mapped = total_associations + total_dependencies

    return {
        "check": "edge_count",
        "status": "pass" if mapped > 0 or corpus_edge_count == 0 else "fail",
        "corpus_edges": corpus_edge_count,
        "mapped_edges": mapped,
        "associations": total_associations,
        "specific": total_dependencies,
        "unattributable": corpus_edge_count - mapped,
    }


def _check_memory_counts(corpus, output_dir):
    corpus_experience = 0
    corpus_mood = 0
    for mem in corpus.get("memories", []):
        domain = mem.get("domain", "experience")
        if domain == "mood" and mem.get("event_type") == "mood-snapshot":
            corpus_mood += 1
        else:
            corpus_experience += 1

    life_events = _load_yaml(os.path.join(output_dir, "life-events.yaml"))
    current_state = _load_yaml(os.path.join(output_dir, "current-state.yaml"))

    output_experience = len(life_events["entries"]) if life_events and "entries" in life_events else 0
    output_mood = len(current_state["entries"]) if current_state and "entries" in current_state else 0

    match = (output_experience == corpus_experience and output_mood == corpus_mood)

    return {
        "check": "memory_count",
        "status": "pass" if match else "fail",
        "corpus": {"experience": corpus_experience, "mood_snapshot": corpus_mood},
        "output": {"life_events": output_experience, "current_state": output_mood},
    }


def _check_pad_fidelity(corpus, output_dir):
    failures = []
    epsilon = 0.001

    for node in corpus.get("nodes", []):
        pad = node.get("pad")
        if not pad or not isinstance(pad, dict):
            continue
        sg = node.get("subgraph_type", "").lower()
        yaml_type = NODE_TYPE_MAP.get(sg)
        if not yaml_type:
            continue

        filename = TYPE_TO_FILE.get(yaml_type)
        if not filename:
            continue
        data = _load_yaml(os.path.join(output_dir, filename))
        if not data or "entries" not in data:
            continue

        name = node["name"]
        entry = next((e for e in data["entries"] if e.get("name") == name or e.get("description") == name), None)
        if not entry:
            continue

        entry_pad = entry.get("pad", {})
        for dim in ("pleasure", "arousal", "dominance"):
            corpus_val = pad.get(dim)
            if corpus_val is None:
                continue
            entry_val = entry_pad.get(dim)
            if entry_val is None:
                failures.append(f"{name}: {dim} missing in output")
            elif abs(float(corpus_val) - float(entry_val)) > epsilon:
                failures.append(f"{name}: {dim} corpus={corpus_val} output={entry_val}")

    return {
        "check": "pad_fidelity",
        "status": "pass" if not failures else "fail",
        "failures": failures,
    }


def _check_vocabulary_values(output_dir):
    failures = []
    goals_data = _load_yaml(os.path.join(output_dir, "goals.yaml"))
    if not goals_data or "entries" not in goals_data:
        return {"check": "vocabulary_values", "status": "pass", "failures": []}

    for entry in goals_data["entries"]:
        for field, valid_set in GOAL_VOCABULARY.items():
            val = entry.get(field)
            if val and val not in valid_set:
                failures.append(f"Goal '{entry.get('name')}': {field}='{val}' not in valid set")

    return {
        "check": "vocabulary_values",
        "status": "pass" if not failures else "fail",
        "failures": failures,
    }


def _check_subject_alignment(corpus, output_dir):
    person_names = set()
    rels_data = _load_yaml(os.path.join(output_dir, "relationships.yaml"))
    if rels_data and "entries" in rels_data:
        for entry in rels_data["entries"]:
            person_names.add(entry.get("name"))

    unresolved = []
    for mem in corpus.get("memories", []):
        subj = mem.get("subject")
        if subj and subj not in person_names:
            if subj not in [u["subject"] for u in unresolved]:
                unresolved.append({"subject": subj})

    return {
        "check": "subject_alignment",
        "status": "pass" if not unresolved else "warn",
        "person_nodes": len(person_names),
        "unresolved_subjects": unresolved,
    }


def _check_goal_connectivity(output_dir):
    goals_data = _load_yaml(os.path.join(output_dir, "goals.yaml"))
    if not goals_data or "entries" not in goals_data:
        return {"check": "goal_connectivity", "status": "pass", "goals": 0, "with_deps": 0}

    total = len(goals_data["entries"])
    with_deps = sum(1 for e in goals_data["entries"] if e.get("dependencies"))

    return {
        "check": "goal_connectivity",
        "status": "pass",
        "goals": total,
        "with_dependencies": with_deps,
    }


def _check_enrichment_presence(corpus, output_dir):
    corpus_enrichments = corpus.get("entity_enrichments", {})
    enrichment_data = _load_yaml(os.path.join(output_dir, "entity-enrichments.yaml"))

    output_count = len(enrichment_data["entries"]) if enrichment_data and "entries" in enrichment_data else 0
    corpus_count = len(corpus_enrichments)

    failures = []
    if corpus_count > 0 and output_count == 0:
        failures.append("No enrichment entries in output despite corpus having entity_enrichments")
    elif output_count != corpus_count:
        failures.append(f"Count mismatch: corpus={corpus_count}, output={output_count}")

    if enrichment_data and "entries" in enrichment_data:
        for entry in enrichment_data["entries"]:
            if not entry.get("drive_baselines") and not entry.get("caps_patterns"):
                failures.append(f"Enrichment for '{entry.get('entity_ref')}' has no drive baselines or CAPS patterns")

    return {
        "check": "enrichment_presence",
        "status": "pass" if not failures else "fail",
        "corpus_count": corpus_count,
        "output_count": output_count,
        "failures": failures,
    }


def _check_disposition_presence(corpus, output_dir):
    corpus_disp = corpus.get("disposition_profile", {})
    disp_data = _load_yaml(os.path.join(output_dir, "disposition-profile.yaml"))

    if not corpus_disp:
        return {"check": "disposition_presence", "status": "pass", "detail": "No disposition in corpus"}

    if not disp_data or "entries" not in disp_data or not disp_data["entries"]:
        return {"check": "disposition_presence", "status": "fail",
                "failures": ["Corpus has disposition_profile but no output file"]}

    entry = disp_data["entries"][0]
    failures = []
    for axis in DISPOSITION_AXES:
        val = entry.get(axis)
        if not val:
            failures.append(f"Missing axis: {axis}")
        elif val not in DISPOSITION_VALUES.get(axis, set()):
            failures.append(f"{axis}='{val}' not a valid category")

    return {
        "check": "disposition_presence",
        "status": "pass" if not failures else "fail",
        "axes_found": [a for a in DISPOSITION_AXES if entry.get(a)],
        "failures": failures,
    }


def validate(corpus, output_dir, schema=None):
    checks = [
        _check_node_counts(corpus, output_dir),
        _check_edge_counts(corpus, output_dir),
        _check_memory_counts(corpus, output_dir),
        _check_pad_fidelity(corpus, output_dir),
        _check_vocabulary_values(output_dir),
        _check_subject_alignment(corpus, output_dir),
        _check_goal_connectivity(output_dir),
        _check_enrichment_presence(corpus, output_dir),
        _check_disposition_presence(corpus, output_dir),
    ]

    passed = sum(1 for c in checks if c["status"] == "pass")
    failed = sum(1 for c in checks if c["status"] == "fail")
    warned = sum(1 for c in checks if c["status"] == "warn")

    return {
        "format": "import-validation-report/v1",
        "summary": {"total": len(checks), "passed": passed, "failed": failed, "warned": warned},
        "checks": checks,
    }


def main():
    parser = argparse.ArgumentParser(description="Validate converter output against corpus")
    parser.add_argument("corpus", help="Path to corpus.json")
    parser.add_argument("output_dir", help="Path to converter output directory")
    parser.add_argument("--schema", default=None, help="Path to verify_schema.yaml")
    parser.add_argument("--json", dest="json_out", default=None, help="Write JSON report to file")
    args = parser.parse_args()

    with open(args.corpus) as f:
        corpus = json.load(f)

    schema = {}
    if args.schema:
        with open(args.schema) as f:
            schema = yaml.safe_load(f)

    report = validate(corpus, args.output_dir, schema)

    if args.json_out:
        with open(args.json_out, "w") as f:
            json.dump(report, f, indent=2)

    summary = report["summary"]
    print(f"Validation: {summary['passed']}/{summary['total']} passed, "
          f"{summary['failed']} failed, {summary['warned']} warned")
    for check in report["checks"]:
        status = "✓" if check["status"] == "pass" else ("⚠" if check["status"] == "warn" else "✗")
        print(f"  {status} {check['check']}")
        for f in check.get("failures", []):
            print(f"    - {f}")

    sys.exit(1 if summary["failed"] > 0 else 0)


if __name__ == "__main__":
    main()

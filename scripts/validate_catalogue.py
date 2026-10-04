#!/usr/bin/env python3
"""Validate experience-behaviour catalogue against CAPS topology.

Checks:
1. All trigger node IDs exist in topology input nodes
2. All expected_outcome node IDs exist in topology output or mediating nodes
3. All related cross-references resolve to existing entry IDs
4. All required fields present on every entry
5. All entry IDs are unique across the catalogue
6. All model values are valid enums
7. All enum fields use valid values
"""
import sys
import yaml
from pathlib import Path

CATALOGUE_DIR = Path(__file__).parent.parent / "docs" / "specs" / "experience-behaviour-catalogue"
TOPOLOGY_PATH = Path(__file__).parent.parent / "docs" / "specs" / "2026-10-02-caps-topology.yaml"

REQUIRED_FIELDS = {"id", "model", "clinical_name", "description", "triggers", "expected_outcomes", "sources"}
VALID_MODELS = {"attachment", "bis_bas", "cbt", "trauma", "operant", "bandura"}
VALID_DIRECTIONS = {"positive", "negative"}
VALID_REPETITIONS = {"high", "moderate", "low"}
VALID_PROVENANCES = {"empirical", "consensus", "estimated"}
TRIGGER_REQUIRED = {"node", "intensity", "repetition"}
OUTCOME_REQUIRED = {"node", "direction", "strength"}
SOURCE_REQUIRED = {"ref", "data", "provenance"}


def load_topology_nodes(path):
    with open(path) as f:
        topo = yaml.safe_load(f)
    input_nodes = set()
    for category in topo["nodes"]["input"].values():
        input_nodes.update(category)
    mediating_nodes = set()
    for category in topo["nodes"]["mediating"].values():
        for item in category:
            mediating_nodes.add(item["name"] if isinstance(item, dict) else item)
    output_nodes = set()
    for category in topo["nodes"]["output"].values():
        output_nodes.update(category)
    return input_nodes, mediating_nodes, output_nodes


def load_all_entries(catalogue_dir):
    entries = []
    for f in sorted(catalogue_dir.glob("*.yaml")):
        if f.name == "index.yaml":
            continue
        with open(f) as fh:
            data = yaml.safe_load(fh)
        if data and "entries" in data:
            for e in data["entries"]:
                e["_file"] = f.name
                entries.append(e)
    return entries


def validate(entries, input_nodes, mediating_nodes, output_nodes):
    errors = []
    all_ids = set()
    valid_outcome_nodes = mediating_nodes | output_nodes

    for e in entries:
        eid = e.get("id", "<missing>")
        src = e.get("_file", "?")

        missing = REQUIRED_FIELDS - set(e.keys())
        if missing:
            errors.append(f"{src}/{eid}: missing fields: {missing}")

        if eid in all_ids:
            errors.append(f"{src}/{eid}: duplicate ID")
        all_ids.add(eid)

        model = e.get("model")
        if model and model not in VALID_MODELS:
            errors.append(f"{src}/{eid}: invalid model '{model}'")

        for t in e.get("triggers", []):
            t_missing = TRIGGER_REQUIRED - set(t.keys())
            if t_missing:
                errors.append(f"{src}/{eid}: trigger missing {t_missing}")
            node = t.get("node")
            if node and node not in input_nodes:
                errors.append(f"{src}/{eid}: trigger node '{node}' not in topology inputs")
            rep = t.get("repetition")
            if rep and rep not in VALID_REPETITIONS:
                errors.append(f"{src}/{eid}: invalid repetition '{rep}'")
            intensity = t.get("intensity")
            if intensity and (not isinstance(intensity, list) or len(intensity) != 2):
                errors.append(f"{src}/{eid}: intensity must be [min, max]")
            elif intensity and (intensity[0] > intensity[1]):
                errors.append(f"{src}/{eid}: intensity min > max")

        for o in e.get("expected_outcomes", []):
            o_missing = OUTCOME_REQUIRED - set(o.keys())
            if o_missing:
                errors.append(f"{src}/{eid}: outcome missing {o_missing}")
            node = o.get("node")
            if node and node not in valid_outcome_nodes:
                errors.append(f"{src}/{eid}: outcome node '{node}' not in topology")
            direction = o.get("direction")
            if direction and direction not in VALID_DIRECTIONS:
                errors.append(f"{src}/{eid}: invalid direction '{direction}'")
            strength = o.get("strength")
            if strength and (not isinstance(strength, list) or len(strength) != 2):
                errors.append(f"{src}/{eid}: strength must be [min, max]")
            elif strength and (strength[0] > strength[1]):
                errors.append(f"{src}/{eid}: strength min > max")

        for s in e.get("sources", []):
            s_missing = SOURCE_REQUIRED - set(s.keys())
            if s_missing:
                errors.append(f"{src}/{eid}: source missing {s_missing}")
            prov = s.get("provenance")
            if prov and prov not in VALID_PROVENANCES:
                errors.append(f"{src}/{eid}: invalid provenance '{prov}'")

    for e in entries:
        eid = e.get("id", "<missing>")
        src = e.get("_file", "?")
        for ref in e.get("related", []):
            if ref not in all_ids:
                errors.append(f"{src}/{eid}: related '{ref}' not found")

    return errors


def main():
    if not TOPOLOGY_PATH.exists():
        print(f"ERROR: topology not found at {TOPOLOGY_PATH}")
        return 1
    if not CATALOGUE_DIR.exists():
        print(f"ERROR: catalogue dir not found at {CATALOGUE_DIR}")
        return 1

    input_nodes, mediating_nodes, output_nodes = load_topology_nodes(TOPOLOGY_PATH)
    entries = load_all_entries(CATALOGUE_DIR)
    print(f"Loaded {len(entries)} entries from {CATALOGUE_DIR}")
    print(f"Topology: {len(input_nodes)} input, {len(mediating_nodes)} mediating, {len(output_nodes)} output nodes")

    errors = validate(entries, input_nodes, mediating_nodes, output_nodes)
    if errors:
        print(f"\n{len(errors)} error(s):")
        for err in errors:
            print(f"  ERROR: {err}")
        return 1
    else:
        print("\nAll entries valid.")
        return 0


if __name__ == "__main__":
    sys.exit(main())

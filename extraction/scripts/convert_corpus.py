"""Corpus-to-YAML converter for BiographyLoader import.

Transforms corpus.json into typed YAML files ready for BiographyLoader.loadAll().

Usage:
  convert_corpus.py <corpus.json> [--schema <path>] [--output <dir>]
"""
import argparse
import json
import os
import re
import sys

import yaml


def slugify(name, max_len=60):
    slug = re.sub(r'[^a-z0-9]+', '-', name.lower()).strip('-')
    return slug[:max_len]


def make_id(prefix, name, seen_ids):
    base = f"{prefix}-{slugify(name)}"
    if base not in seen_ids:
        seen_ids.add(base)
        return base
    for i in range(2, 1000):
        candidate = f"{base}-{i}"
        if candidate not in seen_ids:
            seen_ids.add(candidate)
            return candidate
    raise ValueError(f"Too many duplicate IDs for {base}")


NODE_TYPE_MAP = {
    "goal": "goal",
    "person": "relationship",
    "place": "place",
    "activity": "activity",
    "project": "project",
    "organisation": "organisation",
    "concept": "belief",
}

GOAL_EDGE_TYPES = {"contributes-to", "enables", "blocks", "requires", "decomposes-into"}
ACTIVITY_PLACE_EDGES = {"at"}
ACTIVITY_PARTICIPANT_EDGES = {"participated"}

# Disposition axis score-to-category conversion (SR-R1-01).
DISPOSITION_CATEGORIES = {
    "socialOrient": [(0.0, "independent"), (0.4, "cooperative"), (0.7, "competitive")],
    "ruleFollowing": [(0.0, "defiant"), (0.4, "flexible"), (0.7, "conformist")],
    "riskAppetite": [(0.0, "conservative"), (0.4, "calculated"), (0.7, "bold")],
    "autonomy": [(0.0, "dependent"), (0.4, "collaborative"), (0.7, "autonomous")],
    "conflictMode": [(0.0, "avoidant"), (0.4, "diplomatic"), (0.7, "confrontational")],
}


def _score_to_category(axis, score):
    categories = DISPOSITION_CATEGORIES.get(axis)
    if not categories:
        return str(score)
    result = categories[0][1]
    for threshold, label in categories:
        if score >= threshold:
            result = label
    return result


def convert(corpus, schema, output_dir):
    aliases = schema.get("entity_aliases", {})
    seen_ids = set()
    report = {"nodes_mapped": 0, "edges_mapped": 0, "edges_specific": 0,
              "edges_association": 0, "edges_unattributable": 0,
              "memories_mapped": 0, "warnings": []}

    def resolve_alias(name):
        return aliases.get(name, name)

    edge_by_source = {}
    for edge in corpus.get("edges", []):
        src = resolve_alias(edge.get("source", ""))
        tgt = resolve_alias(edge.get("target", ""))
        edge_by_source.setdefault(src, []).append({**edge, "source": src, "target": tgt})

    entries_by_type = {}

    for node in corpus.get("nodes", []):
        sg_type = node.get("subgraph_type", "").lower()
        yaml_type = NODE_TYPE_MAP.get(sg_type)
        if not yaml_type:
            report["warnings"].append(f"Unknown subgraph_type: {sg_type} for node {node.get('name')}")
            continue

        entry = _map_node_to_entry(node, yaml_type, sg_type, seen_ids)

        node_edges = edge_by_source.get(node["name"], [])
        specific, assoc = _partition_edges(node_edges, yaml_type)
        if specific:
            _apply_specific_edges(entry, yaml_type, specific)
        if assoc:
            entry["associations"] = [
                {"entity_ref": e["target"], "edge_type": e["type"],
                 "confidence": e.get("confidence_value")}
                for e in assoc
            ]
            report["edges_association"] += len(assoc)
        report["edges_specific"] += len(specific)

        entries_by_type.setdefault(yaml_type, []).append(entry)
        report["nodes_mapped"] += 1

    report["edges_mapped"] = report["edges_specific"] + report["edges_association"]
    total_corpus_edges = len(corpus.get("edges", []))
    report["edges_unattributable"] = total_corpus_edges - report["edges_mapped"]

    life_events = []
    current_states = []
    for i, mem in enumerate(corpus.get("memories", [])):
        domain = mem.get("domain", "experience")
        if domain == "mood" and mem.get("event_type") == "mood-snapshot":
            current_states.append(_map_memory_to_current_state(mem, seen_ids, i))
        else:
            life_events.append(_map_memory_to_life_event(mem, seen_ids, i))
        report["memories_mapped"] += 1

    if life_events:
        entries_by_type["life-event"] = life_events
    if current_states:
        entries_by_type["current-state"] = current_states

    # Entity enrichments (from corpus.json top-level "entity_enrichments" dict)
    enrichments = []
    for entity_name, data in corpus.get("entity_enrichments", {}).items():
        enrichments.append(_map_entity_enrichment(entity_name, data, seen_ids))
    if enrichments:
        entries_by_type["entity-enrichment"] = enrichments

    # Disposition profile (from corpus.json top-level "disposition_profile" dict)
    disp = corpus.get("disposition_profile", {})
    if disp:
        subject = schema.get("converter", {}).get("subject_name", corpus.get("subject", "unknown"))
        entries_by_type["disposition-profile"] = [_map_disposition_profile(subject, disp, seen_ids)]

    type_to_filename = {
        "goal": "goals.yaml", "place": "places.yaml",
        "relationship": "relationships.yaml", "activity": "activities.yaml",
        "project": "projects.yaml", "organisation": "organisations.yaml",
        "belief": "beliefs.yaml", "life-event": "life-events.yaml",
        "current-state": "current-state.yaml",
        "entity-enrichment": "entity-enrichments.yaml",
        "disposition-profile": "disposition-profile.yaml",
    }

    for yaml_type, entries in entries_by_type.items():
        filename = type_to_filename.get(yaml_type, f"{yaml_type}.yaml")
        path = os.path.join(output_dir, filename)
        data = {"type": yaml_type, "entries": entries}
        with open(path, "w") as f:
            yaml.dump(data, f, default_flow_style=False, allow_unicode=True, sort_keys=False)

    report_path = os.path.join(output_dir, "conversion-report.json")
    with open(report_path, "w") as f:
        json.dump(report, f, indent=2)

    return report


def _map_node_to_entry(node, yaml_type, sg_type, seen_ids):
    entry = {"id": make_id(sg_type, node["name"], seen_ids), "name": node["name"]}

    conf = node.get("confidence", {})
    if conf.get("origin"):
        entry["confidence_origin"] = conf["origin"]

    pad = node.get("pad")
    if pad:
        entry["pad"] = {k: v for k, v in pad.items()
                        if k in ("pleasure", "arousal", "dominance") and v is not None}

    if node.get("valid_from"):
        entry["valid_from"] = str(node["valid_from"])
    if node.get("valid_until"):
        entry["valid_until"] = str(node["valid_until"])

    props = node.get("properties", {})
    traits = node.get("traits", [])

    if yaml_type == "goal":
        for corpus_field, yaml_field in [
            ("goal_tier", "tier"), ("goal_horizon", "horizon"),
        ]:
            val = node.get(corpus_field)
            if val is not None:
                entry[yaml_field] = val
        # Taxonomy fields as flat strings matching GoalEntry record
        for corpus_field, yaml_field in [
            ("goal_status", "status"), ("goal_urgency", "urgency"),
            ("goal_need_tier", "need_tier"), ("goal_priority", "priority"),
            ("goal_feasibility", "feasibility"), ("content_domain", "content_domain"),
            ("content_subdomain", "content_subdomain"),
            ("motivation_origin", "motivation_origin"), ("clarity", "clarity"),
            ("evidence_basis", "evidence_basis"),
        ]:
            val = node.get(corpus_field)
            if val is not None:
                entry[yaml_field] = str(val)

    elif yaml_type == "relationship":
        if traits:
            entry["traits"] = traits
        if props:
            entry["properties"] = {k: str(v) for k, v in props.items()}

    elif yaml_type == "place":
        if props:
            entry["properties"] = {k: str(v) for k, v in props.items()
                                   if not k.startswith("lifecycle.")}

    elif yaml_type == "activity":
        if props.get("type"):
            entry["activity_type"] = props["type"]
        trait_props = node.get("trait_properties", {})
        if trait_props.get("date"):
            entry["date"] = trait_props["date"]

    elif yaml_type == "belief":
        entry["description"] = node["name"]
        del entry["name"]

    elif yaml_type == "organisation":
        if traits:
            entry["traits"] = traits
        if props:
            entry["properties"] = {k: str(v) for k, v in props.items()}

    elif yaml_type == "project":
        desc = props.get("description")
        if desc:
            entry["description"] = desc
        remaining = {k: str(v) for k, v in props.items() if k != "description"}
        if remaining:
            entry["properties"] = remaining

    return entry


def _partition_edges(edges, yaml_type):
    specific = []
    associations = []
    for edge in edges:
        if yaml_type == "goal" and edge["type"] in GOAL_EDGE_TYPES:
            specific.append(edge)
        elif yaml_type == "activity" and edge["type"] in ACTIVITY_PLACE_EDGES:
            specific.append(edge)
        elif yaml_type == "activity" and edge["type"] in ACTIVITY_PARTICIPANT_EDGES:
            specific.append(edge)
        else:
            associations.append(edge)
    return specific, associations


def _apply_specific_edges(entry, yaml_type, edges):
    if yaml_type == "goal":
        deps = entry.get("dependencies", [])
        for e in edges:
            deps.append({"ref": e["target"], "edge_type": e["type"]})
        entry["dependencies"] = deps
    elif yaml_type == "activity":
        for e in edges:
            if e["type"] in ACTIVITY_PLACE_EDGES:
                entry["place_ref"] = e["target"]
            elif e["type"] in ACTIVITY_PARTICIPANT_EDGES:
                parts = entry.get("participants", [])
                parts.append({"entity_ref": e["target"]})
                entry["participants"] = parts


def _map_memory_to_life_event(mem, seen_ids, index):
    domain = mem.get("domain", "experience")
    entry = {
        "id": make_id(domain, f"{index:04d}", seen_ids),
        "description": mem["text"],
    }

    if mem.get("confidence", {}).get("origin"):
        entry["confidence_origin"] = mem["confidence"]["origin"]
    if mem.get("period"):
        entry["timestamp"] = str(mem["period"])

    entry["domain"] = domain
    if mem.get("event_type"):
        entry["event_type"] = mem["event_type"]
    if mem.get("subject"):
        entry["subject"] = mem["subject"]
    if mem.get("period"):
        entry["period"] = str(mem["period"])
    if mem.get("related_entities"):
        entry["related_entities"] = mem["related_entities"]

    pad = mem.get("pad")
    if pad and isinstance(pad, dict):
        entry["pad"] = {k: v for k, v in pad.items()
                        if k in ("pleasure", "arousal", "dominance") and v is not None}

    for field in ["occ_emotions", "sec_appraisal", "action_tendencies",
                  "cognitive_distortions", "drive_impact", "caps_inputs",
                  "goal_impacts", "habituation", "gut_feeling"]:
        val = mem.get(field)
        if val is not None and val != [] and val != {}:
            entry[field] = val

    if mem.get("sub_thoughts"):
        entry["sub_thoughts"] = [{"type": st["type"], "text": st["text"]}
                                 for st in mem["sub_thoughts"]
                                 if st.get("type") and st.get("text")]

    if mem.get("related_entities"):
        entry["entity_refs"] = mem["related_entities"]

    return entry


def _map_memory_to_current_state(mem, seen_ids, index):
    entry = {"id": make_id("mood", f"{index:04d}", seen_ids)}
    pad = mem.get("pad", {})
    if pad:
        entry["mood"] = {k: v for k, v in pad.items()
                         if k in ("pleasure", "arousal", "dominance") and v is not None}
    if mem.get("text"):
        entry["recent_context"] = mem["text"]
    if mem.get("period"):
        entry["timestamp"] = str(mem["period"])
    return entry


def _map_entity_enrichment(entity_name, data, seen_ids):
    entry = {
        "id": make_id("enrichment", entity_name, seen_ids),
        "entity_ref": entity_name,
    }
    if data.get("drive_baselines"):
        baselines = {}
        for k, v in data["drive_baselines"].items():
            if isinstance(v, dict):
                baselines[k] = {"baseline": v.get("baseline"), "justification": v.get("justification")}
            else:
                baselines[k] = {"baseline": v, "justification": None}
        entry["drive_baselines"] = baselines
    if data.get("caps_patterns"):
        entry["caps_patterns"] = [
            {"pattern": p.get("pattern", ""), "frequency": p.get("frequency")}
            for p in data["caps_patterns"]
        ]
    if data.get("behavioural_attractors"):
        entry["behavioural_attractors"] = [
            {"name": a.get("name", ""), "description": a.get("description")}
            for a in data["behavioural_attractors"]
        ]
    if data.get("relationship_stages"):
        entry["relationship_stages"] = data["relationship_stages"]
    return entry


def _map_disposition_profile(subject, disp, seen_ids):
    entry = {
        "id": make_id("disposition", subject, seen_ids),
        "entity_ref": subject,
    }
    axis_map = {
        "socialOrient": "social_orient",
        "ruleFollowing": "rule_following",
        "riskAppetite": "risk_appetite",
        "autonomy": "autonomy",
        "conflictMode": "conflict_mode",
    }
    for corpus_axis, yaml_field in axis_map.items():
        val = disp.get(corpus_axis)
        if isinstance(val, dict) and "score" in val:
            entry[yaml_field] = _score_to_category(corpus_axis, val["score"])
    return entry


def main():
    parser = argparse.ArgumentParser(description="Convert corpus.json to biography YAML")
    parser.add_argument("corpus", help="Path to corpus.json")
    parser.add_argument("--schema", default=None, help="Path to verify_schema.yaml")
    parser.add_argument("--output", default="biography-output", help="Output directory")
    args = parser.parse_args()

    with open(args.corpus) as f:
        corpus = json.load(f)

    schema = {}
    if args.schema:
        with open(args.schema) as f:
            schema = yaml.safe_load(f)

    os.makedirs(args.output, exist_ok=True)
    report = convert(corpus, schema, args.output)

    print(f"Nodes mapped: {report['nodes_mapped']}")
    print(f"Edges mapped: {report['edges_mapped']} "
          f"(specific: {report['edges_specific']}, association: {report['edges_association']})")
    print(f"Memories mapped: {report['memories_mapped']}")
    if report["warnings"]:
        print(f"Warnings: {len(report['warnings'])}")
        for w in report["warnings"][:5]:
            print(f"  - {w}")


if __name__ == "__main__":
    main()

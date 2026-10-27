#!/usr/bin/env python3
"""Compare two corpus files and produce a regression report.

Usage:
    python3 compare_corpus.py <baseline.json> <candidate.json> [--json report.json]
"""
import argparse
import json
import sys


def _count_by_type(nodes):
    counts = {}
    for n in nodes:
        t = n.get("subgraph_type", "unknown").lower()
        counts[t] = counts.get(t, 0) + 1
    return counts


def _domain_dist(memories):
    d = {}
    for m in memories:
        dom = m.get("domain", "experience")
        d[dom] = d.get(dom, 0) + 1
    return d


def _edge_type_dist(edges):
    d = {}
    for e in edges:
        t = e.get("type", "unknown")
        d[t] = d.get(t, 0) + 1
    return d


def _node_names(nodes):
    return {n.get("name") for n in nodes if n.get("name")}


def _field_population_rate(items, fields):
    if not items:
        return {}
    rates = {}
    for field in fields:
        count = sum(1 for item in items if _has_field(item, field))
        rates[field] = round(count / len(items), 3)
    return rates


def _has_field(item, field):
    if "." in field:
        parts = field.split(".", 1)
        sub = item.get(parts[0])
        if isinstance(sub, dict):
            return parts[1] in sub and sub[parts[1]] is not None
        return False
    val = item.get(field)
    return val is not None and val != [] and val != {}


# --- Check functions ---

def check_structural(baseline, candidate):
    b_nodes = _count_by_type(baseline.get("nodes", []))
    c_nodes = _count_by_type(candidate.get("nodes", []))
    b_domains = _domain_dist(baseline.get("memories", []))
    c_domains = _domain_dist(candidate.get("memories", []))
    b_edge_types = _edge_type_dist(baseline.get("edges", []))
    c_edge_types = _edge_type_dist(candidate.get("edges", []))

    diffs = []

    all_types = sorted(set(list(b_nodes.keys()) + list(c_nodes.keys())))
    for t in all_types:
        b_count = b_nodes.get(t, 0)
        c_count = c_nodes.get(t, 0)
        delta = c_count - b_count
        if delta != 0:
            status = "regressed" if delta < 0 else "improved"
            diffs.append({"category": "node_type", "key": t,
                          "baseline": b_count, "candidate": c_count,
                          "delta": delta, "status": status})

    b_mem = len(baseline.get("memories", []))
    c_mem = len(candidate.get("memories", []))
    if b_mem != c_mem:
        diffs.append({"category": "memory_count", "key": "total",
                      "baseline": b_mem, "candidate": c_mem,
                      "delta": c_mem - b_mem,
                      "status": "regressed" if c_mem < b_mem else "improved"})

    all_domains = sorted(set(list(b_domains.keys()) + list(c_domains.keys())))
    for dom in all_domains:
        bd = b_domains.get(dom, 0)
        cd = c_domains.get(dom, 0)
        if bd != cd:
            diffs.append({"category": "memory_domain", "key": dom,
                          "baseline": bd, "candidate": cd,
                          "delta": cd - bd,
                          "status": "regressed" if cd < bd else "improved"})

    b_edges = len(baseline.get("edges", []))
    c_edges = len(candidate.get("edges", []))
    if b_edges != c_edges:
        diffs.append({"category": "edge_count", "key": "total",
                      "baseline": b_edges, "candidate": c_edges,
                      "delta": c_edges - b_edges,
                      "status": "regressed" if c_edges < b_edges else "improved"})

    regressions = [d for d in diffs if d["status"] == "regressed"]
    status = "fail" if regressions else ("warn" if diffs else "pass")
    return {"check": "structural", "status": status, "diffs": diffs,
            "regressions": len(regressions)}


def check_field_coverage(baseline, candidate):
    node_fields = ["pad.pleasure", "pad.arousal", "pad.dominance",
                   "confidence", "traits", "valid_from", "valid_until"]
    memory_fields = ["pad", "occ_emotions", "sec_appraisal", "action_tendencies",
                     "sub_thoughts", "cognitive_distortions", "drive_impact",
                     "gut_feeling", "caps_inputs", "goal_impacts"]

    b_node_rates = _field_population_rate(baseline.get("nodes", []), node_fields)
    c_node_rates = _field_population_rate(candidate.get("nodes", []), node_fields)
    b_mem_rates = _field_population_rate(baseline.get("memories", []), memory_fields)
    c_mem_rates = _field_population_rate(candidate.get("memories", []), memory_fields)

    drops = []
    for field in node_fields:
        b_rate = b_node_rates.get(field, 0)
        c_rate = c_node_rates.get(field, 0)
        if c_rate < b_rate - 0.05:
            drops.append({"scope": "node", "field": field,
                          "baseline_rate": b_rate, "candidate_rate": c_rate,
                          "delta": round(c_rate - b_rate, 3)})

    for field in memory_fields:
        b_rate = b_mem_rates.get(field, 0)
        c_rate = c_mem_rates.get(field, 0)
        if c_rate < b_rate - 0.05:
            drops.append({"scope": "memory", "field": field,
                          "baseline_rate": b_rate, "candidate_rate": c_rate,
                          "delta": round(c_rate - b_rate, 3)})

    status = "fail" if drops else "pass"
    return {"check": "field_coverage", "status": status,
            "node_rates": {"baseline": b_node_rates, "candidate": c_node_rates},
            "memory_rates": {"baseline": b_mem_rates, "candidate": c_mem_rates},
            "coverage_drops": drops}


def check_content(baseline, candidate):
    b_names = _node_names(baseline.get("nodes", []))
    c_names = _node_names(candidate.get("nodes", []))

    added = sorted(c_names - b_names)
    removed = sorted(b_names - c_names)

    b_pad = {}
    for n in baseline.get("nodes", []):
        pad = n.get("pad")
        if pad and isinstance(pad, dict):
            b_pad[n["name"]] = pad
    c_pad = {}
    for n in candidate.get("nodes", []):
        pad = n.get("pad")
        if pad and isinstance(pad, dict):
            c_pad[n["name"]] = pad

    pad_changes = []
    for name in sorted(set(b_pad.keys()) & set(c_pad.keys())):
        for dim in ("pleasure", "arousal", "dominance"):
            bv = b_pad[name].get(dim)
            cv = c_pad[name].get(dim)
            if bv is not None and cv is not None and abs(float(bv) - float(cv)) > 0.01:
                pad_changes.append({"node": name, "dimension": dim,
                                    "baseline": bv, "candidate": cv})

    b_enrich = set((baseline.get("entity_enrichments") or {}).keys())
    c_enrich = set((candidate.get("entity_enrichments") or {}).keys())
    missing_enrichments = sorted(b_enrich - c_enrich)

    status = "fail" if removed or missing_enrichments else ("warn" if pad_changes else "pass")
    return {"check": "content", "status": status,
            "nodes_added": added, "nodes_removed": removed,
            "pad_changes": pad_changes,
            "missing_enrichments": missing_enrichments}


def compare(baseline, candidate):
    checks = [
        check_structural(baseline, candidate),
        check_field_coverage(baseline, candidate),
        check_content(baseline, candidate),
    ]

    passed = sum(1 for c in checks if c["status"] == "pass")
    failed = sum(1 for c in checks if c["status"] == "fail")
    warned = sum(1 for c in checks if c["status"] == "warn")

    return {
        "format": "regression-report/v1",
        "summary": {"total": len(checks), "passed": passed,
                     "failed": failed, "warned": warned},
        "checks": checks,
    }


def main():
    parser = argparse.ArgumentParser(description="Compare corpus baseline vs candidate")
    parser.add_argument("baseline", help="Path to baseline corpus.json")
    parser.add_argument("candidate", help="Path to candidate corpus.json")
    parser.add_argument("--json", dest="json_out", default=None,
                        help="Write JSON report to file")
    args = parser.parse_args()

    with open(args.baseline) as f:
        baseline = json.load(f)
    with open(args.candidate) as f:
        candidate = json.load(f)

    report = compare(baseline, candidate)

    if args.json_out:
        with open(args.json_out, "w") as f:
            json.dump(report, f, indent=2)

    summary = report["summary"]
    print(f"Regression check: {summary['passed']}/{summary['total']} passed, "
          f"{summary['failed']} failed, {summary['warned']} warned")
    for check in report["checks"]:
        icon = "✓" if check["status"] == "pass" else ("⚠" if check["status"] == "warn" else "✗")
        print(f"  {icon} {check['check']}")
        if check["check"] == "structural":
            for d in check.get("diffs", []):
                print(f"    {d['category']}/{d['key']}: {d['baseline']}→{d['candidate']} ({d['status']})")
        elif check["check"] == "field_coverage":
            for d in check.get("coverage_drops", []):
                print(f"    {d['scope']}/{d['field']}: {d['baseline_rate']}→{d['candidate_rate']}")
        elif check["check"] == "content":
            if check.get("nodes_removed"):
                print(f"    removed: {', '.join(check['nodes_removed'])}")
            if check.get("missing_enrichments"):
                print(f"    missing enrichments: {', '.join(check['missing_enrichments'])}")
            if check.get("pad_changes"):
                for p in check["pad_changes"][:5]:
                    print(f"    PAD {p['node']}.{p['dimension']}: {p['baseline']}→{p['candidate']}")

    sys.exit(1 if summary["failed"] > 0 else 0)


if __name__ == "__main__":
    main()

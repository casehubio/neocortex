#!/usr/bin/env python3
"""Compare two corpus files and report metrics with regression flags.

Usage:
    python3 compare_corpus.py <baseline.json> <candidate.json>
"""
import json
import sys

def count_by_type(nodes, type_name):
    return len([n for n in nodes if n.get('subgraph_type') == type_name])

def count_formative(memories):
    return len([m for m in memories if m.get('event_type') == 'formative-experience'])

def count_lifecycle(nodes):
    return len([n for n in nodes if any(k.startswith('lifecycle.') for k in n.get('properties', {}))])

def lifecycle_event_count(nodes):
    return sum(len([k for k in n.get('properties', {}) if k.startswith('lifecycle.')]) for n in nodes)

def domain_dist(memories):
    d = {}
    for m in memories:
        dom = m.get('domain', 'experience')
        d[dom] = d.get(dom, 0) + 1
    return d

def analyze(corpus):
    nodes = corpus.get('nodes', [])
    memories = corpus.get('memories', [])
    return {
        'nodes': len(nodes),
        'goals': count_by_type(nodes, 'goal'),
        'persons': count_by_type(nodes, 'person'),
        'places': count_by_type(nodes, 'place'),
        'projects': count_by_type(nodes, 'project'),
        'concepts': count_by_type(nodes, 'concept'),
        'organisations': count_by_type(nodes, 'organisation'),
        'activities': count_by_type(nodes, 'activity'),
        'edges': len(corpus.get('edges', [])),
        'memories': len(memories),
        'enriched': corpus['stats'].get('enriched_memories', 0),
        'formative': count_formative(memories),
        'lifecycle_nodes': count_lifecycle(nodes),
        'lifecycle_events': lifecycle_event_count(nodes),
        'domains': domain_dist(memories),
    }

def main():
    if len(sys.argv) < 3:
        print("Usage: compare_corpus.py <baseline.json> <candidate.json> [--passages N]")
        print("  --passages N: candidate used only N passages; scale targets proportionally")
        sys.exit(1)

    passages_flag = 0
    args = sys.argv[1:]
    if '--passages' in args:
        idx = args.index('--passages')
        passages_flag = int(args[idx + 1])
        args = args[:idx] + args[idx+2:]

    with open(args[0]) as f:
        baseline = json.load(f)
    with open(args[1]) as f:
        candidate = json.load(f)

    old = analyze(baseline)
    new = analyze(candidate)

    full_targets = {
        'nodes': 150, 'goals': 50, 'formative': 40,
        'lifecycle_nodes': 15, 'memories': 250, 'enriched': 180,
    }
    if passages_flag > 0:
        total_passages = 16
        scale = passages_flag / total_passages
        targets = {k: max(1, int(v * scale)) for k, v in full_targets.items()}
        print(f"(Scaled targets for {passages_flag}/{total_passages} passages)")
    else:
        targets = full_targets

    print("=== Corpus Comparison ===")
    print(f"{'Metric':<20} {'Baseline':>10} {'Candidate':>10} {'Delta':>8} {'Target':>8} {'Status'}")
    print("-" * 75)

    score = 0
    regressions = []
    for key in ['nodes', 'goals', 'formative', 'lifecycle_nodes', 'lifecycle_events',
                 'persons', 'places', 'projects', 'concepts', 'organisations', 'activities',
                 'edges', 'memories', 'enriched']:
        o = old.get(key, 0)
        n = new.get(key, 0)
        delta = n - o
        target = targets.get(key, '')
        target_str = str(target) if target else ''

        if delta > 0:
            status = 'IMPROVED'
            score += 1
        elif delta < 0:
            status = 'REGRESSED'
            score -= 2
            regressions.append(key)
        else:
            status = 'same'

        met = ''
        if target and n >= target:
            met = ' MET'
        elif target and n < target:
            met = ' MISS'

        print(f"{key:<20} {o:>10} {n:>10} {delta:>+8} {target_str:>8} {status}{met}")

    print(f"\nDomains (old): {old['domains']}")
    print(f"Domains (new): {new['domains']}")
    print(f"\nScore: {score} (gains - 2×regressions)")
    if regressions:
        print(f"Regressions: {', '.join(regressions)}")
    else:
        print("No regressions")

    all_met = all(new.get(k, 0) >= v for k, v in targets.items())
    print(f"All targets met: {'YES' if all_met else 'NO'}")


if __name__ == '__main__':
    main()

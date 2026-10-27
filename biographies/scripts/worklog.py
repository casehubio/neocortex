#!/usr/bin/env python3
"""Structured worklog for pipeline calibration — prevents loops and tracks progress.

Machine-readable JSON worklog alongside human-readable markdown logs.
Each orchestrator instance reads this to understand history and avoid repeats.

Usage:
    python3 worklog.py status                           # current state
    python3 worklog.py log --label "X" --change "Y" --file "Z" --score N [--regressions "a,b"]
    python3 worklog.py check-loop --proposed "change description"
    python3 worklog.py best                             # best scoring iteration
    python3 worklog.py trajectory                       # improving/stalling/regressing
    python3 worklog.py untried                          # categories not yet tried
"""
import argparse
import json
import os
import sys
from datetime import datetime

WORKBENCH = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WORKLOG_PATH = os.path.join(WORKBENCH, 'worklog.json')


def load():
    if os.path.exists(WORKLOG_PATH):
        with open(WORKLOG_PATH) as f:
            return json.load(f)
    return {
        'version': 1,
        'iteration': 0,
        'best_iteration': 0,
        'best_score': 0,
        'trajectory': 'unknown',
        'stall_count': 0,
        'entries': [],
        'tried_changes': [],
        'categories_tried': {},
    }


def save(data):
    with open(WORKLOG_PATH, 'w') as f:
        json.dump(data, f, indent=2)


def categorize(change_desc):
    """Infer category from change description."""
    desc = change_desc.lower()
    if any(w in desc for w in ['prompt', 'system', 'instruction', 'wording', 'example']):
        return 'prompt'
    if any(w in desc for w in ['chunk', 'split', 'passage', 'overlap']):
        return 'chunking'
    if any(w in desc for w in ['model', 'sonnet', 'haiku', 'opus']):
        return 'model'
    if any(w in desc for w in ['call', 'pass', 'merge', 'split call', 'structure']):
        return 'structure'
    if any(w in desc for w in ['few-shot', 'example', 'seed', 'inject']):
        return 'few-shot'
    if any(w in desc for w in ['temperature', 'top_p', 'param']):
        return 'parameters'
    return 'other'


def cmd_status(data):
    print(f"Iteration: {data['iteration']}")
    print(f"Best: iteration {data['best_iteration']} (score={data['best_score']})")
    print(f"Trajectory: {data['trajectory']}")
    print(f"Stall count: {data['stall_count']}")
    print(f"Categories tried: {data['categories_tried']}")
    if data['entries']:
        last = data['entries'][-1]
        print(f"Last: {last['label']} (score={last['score']}, regs={last.get('regressions', [])})")


def cmd_log(data, args):
    data['iteration'] += 1
    n = data['iteration']
    score = args.score
    regs = args.regressions.split(',') if args.regressions else []
    category = categorize(args.change)

    entry = {
        'iteration': n,
        'label': args.label,
        'change': args.change,
        'file': args.file,
        'category': category,
        'score': score,
        'regressions': regs,
        'timestamp': datetime.now().isoformat(),
    }
    data['entries'].append(entry)
    data['tried_changes'].append(args.change.lower().strip())
    data['categories_tried'][category] = data['categories_tried'].get(category, 0) + 1

    if score > data['best_score']:
        data['best_score'] = score
        data['best_iteration'] = n
        data['stall_count'] = 0
    else:
        data['stall_count'] += 1

    # Compute trajectory from last 3
    recent = [e['score'] for e in data['entries'][-3:]]
    if len(recent) >= 3:
        if recent[-1] > recent[-2] > recent[-3]:
            data['trajectory'] = 'improving'
        elif recent[-1] < recent[-2] < recent[-3]:
            data['trajectory'] = 'regressing'
        elif all(abs(recent[i] - recent[i-1]) <= 1 for i in range(1, len(recent))):
            data['trajectory'] = 'stalling'
        else:
            data['trajectory'] = 'mixed'
    elif len(recent) == 2:
        data['trajectory'] = 'improving' if recent[-1] > recent[-2] else 'regressing' if recent[-1] < recent[-2] else 'flat'
    else:
        data['trajectory'] = 'starting'

    save(data)
    print(f"Logged iteration {n}: {args.label} (score={score}, category={category}, trajectory={data['trajectory']})")


def cmd_check_loop(data, proposed):
    """Check if a proposed change has already been tried."""
    proposed_lower = proposed.lower().strip()
    for prev in data['tried_changes']:
        # Jaccard word overlap
        proposed_words = set(proposed_lower.split())
        prev_words = set(prev.split())
        if not proposed_words or not prev_words:
            continue
        overlap = len(proposed_words & prev_words) / len(proposed_words | prev_words)
        if overlap > 0.6:
            # Find the entry
            for e in data['entries']:
                if e['change'].lower().strip() == prev:
                    print(f"LOOP DETECTED: similar to iteration {e['iteration']} '{e['label']}' (overlap={overlap:.0%}, score={e['score']})")
                    sys.exit(1)
    print("OK: no similar change found in history")
    sys.exit(0)


def cmd_best(data):
    if not data['entries']:
        print("No iterations yet")
        return
    best = data['entries'][data['best_iteration'] - 1] if data['best_iteration'] > 0 else data['entries'][0]
    print(f"Best: iteration {best['iteration']} '{best['label']}' (score={best['score']})")
    print(f"Change: {best['change']}")
    print(f"File: {best['file']}")


def cmd_trajectory(data):
    print(data['trajectory'])
    print(f"Stall count: {data['stall_count']}")
    if data['stall_count'] >= 3:
        print("PIVOT RECOMMENDED: 3+ iterations without improvement")


ALL_CATEGORIES = {'prompt', 'chunking', 'model', 'structure', 'few-shot', 'multi-pass', 'parameters'}

def cmd_untried(data):
    tried = set(data['categories_tried'].keys())
    untried = ALL_CATEGORIES - tried
    print(f"Tried: {', '.join(sorted(tried))} ({sum(data['categories_tried'].values())} total)")
    print(f"Untried: {', '.join(sorted(untried)) if untried else 'none'}")
    if untried:
        print(f"Suggestion: try {sorted(untried)[0]} next")


def main():
    parser = argparse.ArgumentParser(description='Pipeline calibration worklog')
    sub = parser.add_subparsers(dest='cmd')

    sub.add_parser('status')

    log_p = sub.add_parser('log')
    log_p.add_argument('--label', required=True)
    log_p.add_argument('--change', required=True)
    log_p.add_argument('--file', required=True)
    log_p.add_argument('--score', type=int, required=True)
    log_p.add_argument('--regressions', default='')

    loop_p = sub.add_parser('check-loop')
    loop_p.add_argument('--proposed', required=True)

    sub.add_parser('best')
    sub.add_parser('trajectory')
    sub.add_parser('untried')

    args = parser.parse_args()
    data = load()

    if args.cmd == 'status':
        cmd_status(data)
    elif args.cmd == 'log':
        cmd_log(data, args)
    elif args.cmd == 'check-loop':
        cmd_check_loop(data, args.proposed)
    elif args.cmd == 'best':
        cmd_best(data)
    elif args.cmd == 'trajectory':
        cmd_trajectory(data)
    elif args.cmd == 'untried':
        cmd_untried(data)
    else:
        parser.print_help()


if __name__ == '__main__':
    main()

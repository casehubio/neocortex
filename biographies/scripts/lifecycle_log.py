#!/usr/bin/env python3
"""Lifecycle log for orchestrator agent management.

Logs agent creation, kills, self-replacement, and decisions.
Human-readable log file the user can review to see how the system performed.

Usage:
    python3 lifecycle_log.py spawn --name "executor-v1" --reason "apply entity-minimum change"
    python3 lifecycle_log.py kill --name "orchestrator-v1" --reason "context full after 5 iterations"
    python3 lifecycle_log.py replace --old "orchestrator-v1" --new "orchestrator-v2" --reason "context ~80% full, 5 iterations completed, trajectory improving"
    python3 lifecycle_log.py decision --what "pivot to chunking" --reason "3 prompt changes stalled, untried: chunking"
    python3 lifecycle_log.py show [--last N]
"""
import argparse
import json
import os
from datetime import datetime

WORKBENCH = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LOG_PATH = os.path.join(WORKBENCH, 'lifecycle-log.jsonl')
READABLE_PATH = os.path.join(WORKBENCH, 'lifecycle-log.md')


def append_entry(entry):
    entry['timestamp'] = datetime.now().isoformat()
    with open(LOG_PATH, 'a') as f:
        f.write(json.dumps(entry) + '\n')
    with open(READABLE_PATH, 'a') as f:
        ts = entry['timestamp'][:19].replace('T', ' ')
        event = entry['event']
        if event == 'spawn':
            f.write(f"- **{ts}** SPAWN `{entry['name']}` — {entry['reason']}\n")
        elif event == 'kill':
            f.write(f"- **{ts}** KILL `{entry['name']}` — {entry['reason']}\n")
        elif event == 'replace':
            f.write(f"- **{ts}** REPLACE `{entry['old']}` → `{entry['new']}` — {entry['reason']}\n")
        elif event == 'decision':
            f.write(f"- **{ts}** DECISION: {entry['what']} — {entry['reason']}\n")


def cmd_spawn(args):
    append_entry({'event': 'spawn', 'name': args.name, 'reason': args.reason})
    print(f"Logged: spawn {args.name}")


def cmd_kill(args):
    append_entry({'event': 'kill', 'name': args.name, 'reason': args.reason})
    print(f"Logged: kill {args.name}")


def cmd_replace(args):
    append_entry({'event': 'replace', 'old': args.old, 'new': args.new, 'reason': args.reason})
    print(f"Logged: replace {args.old} → {args.new}")


def cmd_decision(args):
    append_entry({'event': 'decision', 'what': args.what, 'reason': args.reason})
    print(f"Logged: decision — {args.what}")


def cmd_show(args):
    if not os.path.exists(READABLE_PATH):
        print("No lifecycle events yet.")
        return
    with open(READABLE_PATH) as f:
        lines = f.readlines()
    n = args.last if args.last else len(lines)
    for line in lines[-n:]:
        print(line.rstrip())


def main():
    parser = argparse.ArgumentParser(description='Agent lifecycle log')
    sub = parser.add_subparsers(dest='cmd')

    s = sub.add_parser('spawn')
    s.add_argument('--name', required=True)
    s.add_argument('--reason', required=True)

    k = sub.add_parser('kill')
    k.add_argument('--name', required=True)
    k.add_argument('--reason', required=True)

    r = sub.add_parser('replace')
    r.add_argument('--old', required=True)
    r.add_argument('--new', required=True)
    r.add_argument('--reason', required=True)

    d = sub.add_parser('decision')
    d.add_argument('--what', required=True)
    d.add_argument('--reason', required=True)

    sh = sub.add_parser('show')
    sh.add_argument('--last', type=int, default=0)

    args = parser.parse_args()
    if args.cmd == 'spawn': cmd_spawn(args)
    elif args.cmd == 'kill': cmd_kill(args)
    elif args.cmd == 'replace': cmd_replace(args)
    elif args.cmd == 'decision': cmd_decision(args)
    elif args.cmd == 'show': cmd_show(args)
    else: parser.print_help()


if __name__ == '__main__':
    main()

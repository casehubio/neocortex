#!/usr/bin/env python3
"""Launch parallel Claude Code sessions in tmux for pipeline calibration.

Each experiment gets its own tmux window, output directory, and full context.
The coordinator monitors results and picks the best variant.

Usage:
    python3 calibrate.py --experiments experiments.yaml
    python3 calibrate.py --check          # check results of running experiments
    python3 calibrate.py --summarize      # compare all completed experiments

Requires: tmux, claude CLI
"""
import argparse
import json
import os
import subprocess
import sys
import time
import yaml

WORKBENCH = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SCRIPTS = os.path.join(WORKBENCH, 'scripts')
BASELINE = os.path.join(WORKBENCH, 'output', 'corpus.json')
SESSION_NAME = 'calibrate'

VERTEX_ENV = {
    'CLAUDE_CODE_USE_VERTEX': os.environ.get('CLAUDE_CODE_USE_VERTEX', '1'),
    'ANTHROPIC_VERTEX_PROJECT_ID': os.environ.get('ANTHROPIC_VERTEX_PROJECT_ID', ''),
    'CLOUD_ML_REGION': os.environ.get('CLOUD_ML_REGION', 'us-east5'),
}


def load_experiments(path):
    with open(path) as f:
        return yaml.safe_load(f)


def create_experiment_prompt(exp, outdir):
    """Generate the prompt that a Claude session will execute."""
    name = exp['name']
    changes = exp.get('changes', '')
    limit = exp.get('limit', 3)

    return f"""You are running an autonomous pipeline calibration experiment.

EXPERIMENT: {name}
OUTPUT DIR: {outdir}
LIMIT: {limit} passages

TASK:
1. Apply these changes to the extraction pipeline:
{changes}

2. Run the pipeline:
   bash {SCRIPTS}/iterate.sh --limit {limit} --label "{name}"

3. Read the last entry in {WORKBENCH}/iteration-log.md

4. Write your results summary to {outdir}/result.json:
   {{"name": "{name}", "score": <score>, "regressions": [...], "notes": "..."}}

5. If the score is positive and no regressions, commit the change with:
   wip(neocortex#529): {name}

6. If the score is negative or has regressions, revert your changes.

Do NOT ask questions. Work autonomously. When done, just stop.
"""


def launch_experiment(exp, window_idx):
    name = exp['name']
    outdir = os.path.join(WORKBENCH, f'output-exp-{name.replace(" ", "-").lower()}')
    os.makedirs(outdir, exist_ok=True)

    prompt = create_experiment_prompt(exp, outdir)
    prompt_file = os.path.join(outdir, 'prompt.md')
    with open(prompt_file, 'w') as f:
        f.write(prompt)

    env_exports = ' '.join(f'{k}={v}' for k, v in VERTEX_ENV.items() if v)

    if window_idx == 0:
        subprocess.run([
            'tmux', 'new-session', '-d', '-s', SESSION_NAME,
            '-n', name,
        ])
    else:
        subprocess.run([
            'tmux', 'new-window', '-t', SESSION_NAME,
            '-n', name,
        ])

    cmd = f'{env_exports} claude --dangerously-skip-permissions -p "$(cat {prompt_file})"'
    subprocess.run([
        'tmux', 'send-keys', '-t', f'{SESSION_NAME}:{name}',
        f'cd {WORKBENCH} && {cmd}', 'Enter',
    ])

    print(f'  Launched: {name} (tmux window: {SESSION_NAME}:{name})')
    return outdir


def check_results():
    """Check which experiments have completed."""
    exp_dirs = [d for d in os.listdir(WORKBENCH) if d.startswith('output-exp-')]
    results = []
    for d in sorted(exp_dirs):
        result_path = os.path.join(WORKBENCH, d, 'result.json')
        if os.path.exists(result_path):
            with open(result_path) as f:
                results.append(json.load(f))
        else:
            print(f'  {d}: still running')
    return results


def summarize(results):
    """Compare all completed experiments."""
    if not results:
        print('No completed experiments yet.')
        return

    print(f'\n{"Name":<30} {"Score":>6} {"Regressions":<30} Notes')
    print('-' * 80)
    for r in sorted(results, key=lambda x: x.get('score', 0), reverse=True):
        regs = ', '.join(r.get('regressions', [])) or 'none'
        print(f'{r["name"]:<30} {r.get("score", "?"):>6} {regs:<30} {r.get("notes", "")[:30]}')

    best = max(results, key=lambda x: x.get('score', -999))
    print(f'\nBest: {best["name"]} (score={best.get("score", "?")})')


def main():
    parser = argparse.ArgumentParser(description='Pipeline calibration orchestrator')
    parser.add_argument('--experiments', help='YAML file defining experiments')
    parser.add_argument('--check', action='store_true', help='Check running experiments')
    parser.add_argument('--summarize', action='store_true', help='Summarize all results')
    parser.add_argument('--sequential', action='store_true', help='Run experiments one at a time')
    args = parser.parse_args()

    if args.check or args.summarize:
        results = check_results()
        if args.summarize:
            summarize(results)
        return

    if not args.experiments:
        parser.error('Provide --experiments or --check/--summarize')

    data = load_experiments(args.experiments)
    experiments = data.get('experiments', [])

    if not experiments:
        print('No experiments defined.')
        return

    print(f'Launching {len(experiments)} experiments...')
    outdirs = []
    for i, exp in enumerate(experiments):
        outdir = launch_experiment(exp, i)
        outdirs.append(outdir)
        if args.sequential:
            print(f'  Waiting for {exp["name"]} to complete...')
            while not os.path.exists(os.path.join(outdir, 'result.json')):
                time.sleep(30)

    print(f'\nAll launched. Monitor with:')
    print(f'  tmux attach -t {SESSION_NAME}')
    print(f'  python3 {__file__} --check')
    print(f'  python3 {__file__} --summarize')


if __name__ == '__main__':
    main()

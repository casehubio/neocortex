"""Pipeline convergence state — manifest tracking, confidence ceilings, atomic I/O."""

import json
import os
import tempfile

_CEILINGS = [1.0, 0.8, 0.6, 0.4]


def confidence_ceiling(pass_n: int) -> float:
    return _CEILINGS[min(pass_n - 1, len(_CEILINGS) - 1)]


def clamp_confidence(confidence, pass_n: int) -> dict:
    ceiling = confidence_ceiling(pass_n)
    if isinstance(confidence, (int, float)):
        confidence = {'origin': 'INFERRED', 'value': float(confidence)}
    return {**confidence, 'value': min(confidence.get('value', 1.0), ceiling)}


def atomic_write(path: str, data: dict):
    dir_name = os.path.dirname(path) or '.'
    fd, tmp = tempfile.mkstemp(dir=dir_name, suffix='.tmp')
    try:
        with os.fdopen(fd, 'w') as f:
            json.dump(data, f, indent=2)
        os.replace(tmp, path)
    except BaseException:
        try:
            os.unlink(tmp)
        except OSError:
            pass
        raise


class PipelineManifest:
    def __init__(self, path: str, mode: str = 'fixed-point', max_passes: int = 5):
        self.path = path
        if os.path.exists(path):
            with open(path) as f:
                self.data = json.load(f)
        else:
            self.data = {
                'mode': mode,
                'max_passes': max_passes,
                'passes': [],
                'converged': False,
            }

    def start_pass(self, pass_n: int):
        self.data['passes'].append({
            'pass': pass_n,
            'status': 'in_progress',
            'steps_complete': [],
            'step': None,
            'items_done': 0,
            'items_total': 0,
            'new_memories': 0,
            'new_entities': 0,
            'new_edges': 0,
        })
        self.save()

    def update_step(self, step: str, items_done: int, items_total: int):
        p = self.data['passes'][-1]
        p['step'] = step
        p['items_done'] = items_done
        p['items_total'] = items_total
        self.save()

    def complete_step(self, step: str):
        p = self.data['passes'][-1]
        if step not in p['steps_complete']:
            p['steps_complete'].append(step)
        p['step'] = None
        self.save()

    def complete_pass(self, new_memories: int, new_entities: int, new_edges: int):
        p = self.data['passes'][-1]
        p['status'] = 'complete'
        p['new_memories'] = new_memories
        p['new_entities'] = new_entities
        p['new_edges'] = new_edges
        self.save()

    def mark_converged(self):
        self.data['converged'] = True
        self.save()

    def current_pass(self) -> int:
        if not self.data['passes']:
            return 0
        return self.data['passes'][-1]['pass']

    def current_step(self) -> str | None:
        if not self.data['passes']:
            return None
        return self.data['passes'][-1].get('step')

    def is_step_complete(self, step: str) -> bool:
        if not self.data['passes']:
            return False
        return step in self.data['passes'][-1].get('steps_complete', [])

    def save(self):
        atomic_write(self.path, self.data)

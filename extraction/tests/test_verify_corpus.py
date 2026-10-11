"""Tests for import readiness verification."""

import copy
import os
import pytest

SCHEMA_PATH = os.path.join(os.path.dirname(__file__), '..', 'docs', 'verify_schema.yaml')


def _minimal_corpus(nodes=None, edges=None, memories=None):
    return {
        'format': 'cognitive-workbench-corpus/v2',
        'subject': 'Test Subject',
        'stats': {},
        'nodes': nodes or [],
        'edges': edges or [],
        'memories': memories or [],
    }


def _goal_node(name='Test Goal', **overrides):
    node = {
        'name': name,
        'subgraph_type': 'GOAL',
        'properties': {},
        'goal_tier': 'strategic',
        'goal_horizon': 'long',
        'content_domain': 'health',
        'motivation_origin': 'intrinsic',
    }
    node.update(overrides)
    return node


def _person_node(name='Frida Kahlo'):
    return {'name': name, 'subgraph_type': 'PERSON', 'properties': {}}


def _place_node(name='La Casa Azul'):
    return {'name': name, 'subgraph_type': 'PLACE', 'properties': {}}


def _edge(source, target, edge_type='associated-with'):
    return {'source': source, 'target': target, 'type': edge_type}


def _memory(text='A memory', domain='experience', subject='Frida Kahlo', **overrides):
    m = {'text': text, 'domain': domain, 'subject': subject}
    m.update(overrides)
    return m


def _load_schema():
    from verify_corpus import load_schema
    return load_schema(SCHEMA_PATH)


# --- Schema loading ---

def test_load_schema():
    from verify_corpus import load_schema
    schema = load_schema(SCHEMA_PATH)
    assert schema['format_version'] == 1
    assert 'goal' in schema['entity_types']
    assert 'entity_aliases' in schema


def test_load_schema_missing_file():
    from verify_corpus import load_schema
    with pytest.raises(FileNotFoundError):
        load_schema('/nonexistent/schema.yaml')


# --- Dangling edges ---

def test_dangling_edge_detected():
    from verify_corpus import verify
    schema = _load_schema()
    corpus = _minimal_corpus(
        nodes=[_person_node('Frida Kahlo')],
        edges=[_edge('Frida Kahlo', 'Nonexistent Place')],
    )
    report = verify(corpus, schema)
    dangling = [f for f in report['findings'] if f['category'] == 'dangling_edge']
    assert len(dangling) >= 1
    assert dangling[0]['severity'] == 'CRITICAL'


def test_dangling_edge_fixable_via_alias():
    from verify_corpus import verify
    schema = _load_schema()
    corpus = _minimal_corpus(
        nodes=[_person_node('Frida Kahlo'), _place_node('La Casa Azul')],
        edges=[_edge('Frida', 'La Casa Azul', 'lives-at')],
    )
    report = verify(corpus, schema)
    dangling = [f for f in report['findings'] if f['category'] == 'dangling_edge']
    assert len(dangling) >= 1
    assert dangling[0]['severity'] == 'FIXABLE'


def test_alias_resolution_fix():
    from verify_corpus import verify, apply_fixes
    schema = _load_schema()
    corpus = _minimal_corpus(
        nodes=[_person_node('Frida Kahlo'), _place_node('La Casa Azul')],
        edges=[_edge('Frida', 'La Casa Azul', 'lives-at')],
    )
    report = verify(corpus, schema)
    fixed_corpus, fixed_report = apply_fixes(corpus, report, schema)
    assert fixed_corpus['edges'][0]['source'] == 'Frida Kahlo'
    remaining = [f for f in fixed_report['findings'] if not f['fixed']]
    dangling = [f for f in remaining if f['category'] == 'dangling_edge']
    assert len(dangling) == 0


# --- Field alignment ---

def test_field_alignment_detected():
    from verify_corpus import verify
    schema = _load_schema()
    corpus = _minimal_corpus(nodes=[_goal_node()])
    report = verify(corpus, schema)
    alignment = [f for f in report['findings'] if f['category'] == 'field_alignment']
    assert len(alignment) >= 1
    assert alignment[0]['severity'] == 'CRITICAL'


def test_field_alignment_fix():
    from verify_corpus import verify, apply_fixes
    schema = _load_schema()
    goal = _goal_node()
    corpus = _minimal_corpus(nodes=[goal])
    report = verify(corpus, schema)
    fixed_corpus, _ = apply_fixes(corpus, report, schema)
    fixed_goal = fixed_corpus['nodes'][0]
    assert 'tier' in fixed_goal
    assert 'goal_tier' not in fixed_goal


# --- Vocabulary validation ---

def test_valid_vocabulary_passes():
    from verify_corpus import verify
    schema = _load_schema()
    corpus = _minimal_corpus(nodes=[_goal_node(content_domain='health')])
    report = verify(corpus, schema)
    vocab = [f for f in report['findings'] if f['category'] == 'vocabulary_violation']
    assert len(vocab) == 0


def test_invalid_vocabulary_detected():
    from verify_corpus import verify
    schema = _load_schema()
    corpus = _minimal_corpus(nodes=[_goal_node(content_domain='invalid-domain')])
    report = verify(corpus, schema)
    vocab = [f for f in report['findings'] if f['category'] == 'vocabulary_violation']
    assert len(vocab) >= 1
    assert vocab[0]['severity'] == 'WARNING'


# --- Range validation ---

def test_pad_in_range_passes():
    from verify_corpus import verify
    schema = _load_schema()
    goal = _goal_node(pad={'pleasure': 0.5, 'arousal': -0.3, 'dominance': 0.7})
    corpus = _minimal_corpus(nodes=[goal])
    report = verify(corpus, schema)
    range_v = [f for f in report['findings'] if f['category'] == 'range_violation']
    assert len(range_v) == 0


def test_pad_out_of_range_detected():
    from verify_corpus import verify
    schema = _load_schema()
    goal = _goal_node(pad={'pleasure': 1.5, 'arousal': -0.3, 'dominance': 0.7})
    corpus = _minimal_corpus(nodes=[goal])
    report = verify(corpus, schema)
    range_v = [f for f in report['findings'] if f['category'] == 'range_violation']
    assert len(range_v) >= 1
    assert range_v[0]['severity'] == 'WARNING'


def test_range_clamping_fix():
    from verify_corpus import verify, apply_fixes
    schema = _load_schema()
    goal = _goal_node(pad={'pleasure': 1.5, 'arousal': -2.0, 'dominance': 0.7})
    corpus = _minimal_corpus(nodes=[goal])
    report = verify(corpus, schema)
    fixed_corpus, _ = apply_fixes(corpus, report, schema)
    pad = fixed_corpus['nodes'][0]['pad']
    assert pad['pleasure'] == 1.0
    assert pad['arousal'] == -1.0
    assert pad['dominance'] == 0.7


# --- Subject alignment ---

def test_subject_alignment_fixable():
    from verify_corpus import verify
    schema = _load_schema()
    corpus = _minimal_corpus(
        nodes=[_person_node('Frida Kahlo')],
        memories=[_memory(subject='Frida')],
    )
    report = verify(corpus, schema)
    subj = [f for f in report['findings'] if f['category'] == 'subject_alignment']
    assert len(subj) >= 1
    assert subj[0]['severity'] == 'FIXABLE'


def test_subject_alignment_fix():
    from verify_corpus import verify, apply_fixes
    schema = _load_schema()
    corpus = _minimal_corpus(
        nodes=[_person_node('Frida Kahlo')],
        memories=[_memory(subject='Frida')],
    )
    report = verify(corpus, schema)
    fixed_corpus, _ = apply_fixes(corpus, report, schema)
    assert fixed_corpus['memories'][0]['subject'] == 'Frida Kahlo'


# --- Volume anomaly ---

def test_volume_anomaly_detected():
    from verify_corpus import verify
    schema = _load_schema()
    goals = [_goal_node(name=f'Goal {i}') for i in range(70)]
    corpus = _minimal_corpus(nodes=goals)
    report = verify(corpus, schema)
    vol = [f for f in report['findings'] if f['category'] == 'volume_anomaly']
    assert len(vol) >= 1
    assert vol[0]['severity'] == 'INFO'


# --- Enrichment gap ---

def test_enrichment_gap_detected():
    from verify_corpus import verify
    schema = _load_schema()
    memories = [_memory(text=f'Memory {i}') for i in range(10)]
    corpus = _minimal_corpus(nodes=[_person_node()], memories=memories)
    report = verify(corpus, schema)
    gaps = [f for f in report['findings'] if f['category'] == 'enrichment_gap']
    assert len(gaps) >= 1


# --- Memory domain validation (R1-04 fix) ---

def test_invalid_memory_domain_detected():
    from verify_corpus import verify
    schema = _load_schema()
    corpus = _minimal_corpus(
        nodes=[_person_node()],
        memories=[_memory(domain='invalid_domain')],
    )
    report = verify(corpus, schema)
    domain_findings = [f for f in report['findings'] if f['category'] == 'memory_domain_invalid']
    assert len(domain_findings) >= 1
    assert domain_findings[0]['severity'] == 'WARNING'


def test_valid_memory_domains_pass():
    from verify_corpus import verify
    schema = _load_schema()
    corpus = _minimal_corpus(
        nodes=[_person_node()],
        memories=[
            _memory(domain='experience'),
            _memory(domain='relationship'),
            _memory(domain='reflection'),
            _memory(domain='mood'),
        ],
    )
    report = verify(corpus, schema)
    domain_findings = [f for f in report['findings'] if f['category'] == 'memory_domain_invalid']
    assert len(domain_findings) == 0
    assert 'memory_domain_validity' in report['checks_passed']


# --- Report format ---

def test_report_has_required_fields():
    from verify_corpus import verify
    schema = _load_schema()
    corpus = _minimal_corpus()
    report = verify(corpus, schema)
    assert report['format'] == 'import-readiness-report/v1'
    assert 'summary' in report
    assert 'findings' in report
    assert 'checks_passed' in report
    summary = report['summary']
    for key in ('total_findings', 'critical', 'fixable', 'warning', 'info', 'gate'):
        assert key in summary


# --- Exit codes ---

def test_exit_code_pass():
    from verify_corpus import compute_exit_code
    report = {'summary': {'critical': 0, 'warning': 0, 'fixable': 0, 'info': 2}}
    assert compute_exit_code(report) == 0


def test_exit_code_critical():
    from verify_corpus import compute_exit_code
    report = {'summary': {'critical': 1, 'warning': 0, 'fixable': 0, 'info': 0}}
    assert compute_exit_code(report) == 1


def test_exit_code_warning():
    from verify_corpus import compute_exit_code
    report = {'summary': {'critical': 0, 'warning': 3, 'fixable': 0, 'info': 0}}
    assert compute_exit_code(report) == 2


# --- Fix idempotency ---

def test_fix_idempotent():
    from verify_corpus import verify, apply_fixes
    schema = _load_schema()
    corpus = _minimal_corpus(
        nodes=[_person_node('Frida Kahlo'), _goal_node(pad={'pleasure': 1.5})],
        edges=[_edge('Frida', 'Frida Kahlo')],
        memories=[_memory(subject='Frida')],
    )
    report1 = verify(corpus, schema)
    fixed1, _ = apply_fixes(corpus, report1, schema)
    report2 = verify(fixed1, schema)
    fixed2, _ = apply_fixes(fixed1, report2, schema)
    assert fixed1 == fixed2


# --- Edge type validation ---

def test_unknown_edge_type_detected():
    from verify_corpus import verify
    schema = _load_schema()
    corpus = _minimal_corpus(
        nodes=[_person_node(), _place_node()],
        edges=[_edge('Frida Kahlo', 'La Casa Azul', 'invented-edge-type')],
    )
    report = verify(corpus, schema)
    unknown = [f for f in report['findings'] if f['category'] == 'edge_type_unknown']
    assert len(unknown) >= 1


# --- Sub-thought type validation ---

def test_invalid_sub_thought_type_detected():
    from verify_corpus import verify
    schema = _load_schema()
    corpus = _minimal_corpus(
        nodes=[_person_node()],
        memories=[_memory(sub_thoughts=[{'type': 'invalid-type', 'text': 'test'}])],
    )
    report = verify(corpus, schema)
    st = [f for f in report['findings'] if f['category'] == 'sub_thought_type']
    assert len(st) >= 1
    assert st[0]['severity'] == 'WARNING'


def test_valid_sub_thought_types_pass():
    from verify_corpus import verify
    schema = _load_schema()
    corpus = _minimal_corpus(
        nodes=[_person_node()],
        memories=[_memory(sub_thoughts=[
            {'type': 'causal-inference', 'text': 'because X'},
            {'type': 'evaluative', 'text': 'this was good'},
        ])],
    )
    report = verify(corpus, schema)
    st = [f for f in report['findings'] if f['category'] == 'sub_thought_type']
    assert len(st) == 0

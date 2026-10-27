"""Tests for pipeline convergence state — manifest tracking + confidence ceilings."""

import json
import os
import pytest

from pipeline_state import confidence_ceiling, clamp_confidence, atomic_write, PipelineManifest


class TestConfidenceCeiling:
    def test_pass_1_is_1_0(self):
        assert confidence_ceiling(1) == 1.0

    def test_pass_2_is_0_8(self):
        assert confidence_ceiling(2) == 0.8

    def test_pass_3_is_0_6(self):
        assert confidence_ceiling(3) == 0.6

    def test_pass_4_is_0_4(self):
        assert confidence_ceiling(4) == 0.4

    def test_pass_5_is_0_4(self):
        assert confidence_ceiling(5) == 0.4

    def test_pass_100_is_0_4(self):
        assert confidence_ceiling(100) == 0.4


class TestClampConfidence:
    def test_pass_1_no_clamp(self):
        conf = {'origin': 'STATED', 'value': 0.9}
        result = clamp_confidence(conf, 1)
        assert result['value'] == 0.9
        assert result['origin'] == 'STATED'

    def test_pass_2_clamps_to_0_8(self):
        conf = {'origin': 'INFERRED', 'value': 0.95}
        result = clamp_confidence(conf, 2)
        assert result['value'] == 0.8

    def test_pass_2_no_clamp_when_below(self):
        conf = {'origin': 'INFERRED', 'value': 0.5}
        result = clamp_confidence(conf, 2)
        assert result['value'] == 0.5

    def test_missing_value_defaults_to_1(self):
        conf = {'origin': 'INFERRED'}
        result = clamp_confidence(conf, 3)
        assert result['value'] == 0.6

    def test_does_not_mutate_input(self):
        conf = {'origin': 'STATED', 'value': 0.95}
        clamp_confidence(conf, 2)
        assert conf['value'] == 0.95


class TestAtomicWrite:
    def test_writes_json(self, tmp_path):
        path = str(tmp_path / 'test.json')
        atomic_write(path, {'key': 'value'})
        with open(path) as f:
            assert json.load(f) == {'key': 'value'}

    def test_no_tmp_file_left(self, tmp_path):
        path = str(tmp_path / 'test.json')
        atomic_write(path, {'key': 'value'})
        files = os.listdir(tmp_path)
        assert files == ['test.json']

    def test_overwrites_existing(self, tmp_path):
        path = str(tmp_path / 'test.json')
        atomic_write(path, {'v': 1})
        atomic_write(path, {'v': 2})
        with open(path) as f:
            assert json.load(f)['v'] == 2


class TestPipelineManifest:
    def test_new_manifest_has_no_passes(self, tmp_path):
        m = PipelineManifest(str(tmp_path / 'manifest.json'))
        assert m.current_pass() == 0
        assert m.data['passes'] == []

    def test_start_pass_creates_entry(self, tmp_path):
        m = PipelineManifest(str(tmp_path / 'manifest.json'))
        m.start_pass(1)
        assert m.current_pass() == 1
        assert m.data['passes'][0]['status'] == 'in_progress'

    def test_complete_step_records(self, tmp_path):
        m = PipelineManifest(str(tmp_path / 'manifest.json'))
        m.start_pass(1)
        m.complete_step('chunk')
        assert m.is_step_complete('chunk')
        assert not m.is_step_complete('nlp_extract')

    def test_update_step_progress(self, tmp_path):
        m = PipelineManifest(str(tmp_path / 'manifest.json'))
        m.start_pass(1)
        m.update_step('extract_structure', 5, 30)
        assert m.current_step() == 'extract_structure'
        p = m.data['passes'][0]
        assert p['items_done'] == 5
        assert p['items_total'] == 30

    def test_complete_pass(self, tmp_path):
        m = PipelineManifest(str(tmp_path / 'manifest.json'))
        m.start_pass(1)
        m.complete_pass(new_memories=23, new_entities=3, new_edges=5)
        p = m.data['passes'][0]
        assert p['status'] == 'complete'
        assert p['new_memories'] == 23

    def test_mark_converged(self, tmp_path):
        m = PipelineManifest(str(tmp_path / 'manifest.json'))
        m.start_pass(1)
        m.complete_pass(0, 0, 0)
        m.mark_converged()
        assert m.data['converged'] is True

    def test_save_and_reload(self, tmp_path):
        path = str(tmp_path / 'manifest.json')
        m = PipelineManifest(path)
        m.start_pass(1)
        m.complete_step('chunk')
        m.save()
        m2 = PipelineManifest(path)
        assert m2.current_pass() == 1
        assert m2.is_step_complete('chunk')

    def test_mode_and_max_passes(self, tmp_path):
        m = PipelineManifest(str(tmp_path / 'manifest.json'),
                             mode='selective', max_passes=3)
        assert m.data['mode'] == 'selective'
        assert m.data['max_passes'] == 3

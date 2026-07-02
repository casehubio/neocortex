import numpy as np
import pytest

from evaluation.code_domain_embeddings.benchmark_runner import (
    compute_precision,
    score_retrieval,
    aggregate_by_failure_mode,
)


class TestComputePrecision:
    def test_all_relevant(self):
        scores = [2, 1, 2, 1, 2]
        assert compute_precision(scores, threshold=1) == pytest.approx(1.0)

    def test_none_relevant(self):
        scores = [0, 0, 0, 0]
        assert compute_precision(scores, threshold=1) == pytest.approx(0.0)

    def test_half_relevant(self):
        scores = [2, 0, 1, 0]
        assert compute_precision(scores, threshold=1) == pytest.approx(0.5)

    def test_empty_list(self):
        assert compute_precision([], threshold=1) == pytest.approx(0.0)

    def test_higher_threshold(self):
        scores = [2, 1, 1, 0]
        assert compute_precision(scores, threshold=2) == pytest.approx(0.25)


class TestScoreRetrieval:
    def test_scores_known_entries(self):
        baseline = {
            "GE-001": {"scenario-a": {"benchmark_score": 2, "methods": ["grep"]}},
            "GE-002": {"scenario-a": {"benchmark_score": 0, "methods": ["grep"]}},
        }
        retrieved_ids = ["GE-001", "GE-002", "GE-003"]
        scores = score_retrieval(retrieved_ids, "scenario-a", baseline)
        assert scores == [2, 0, 0]

    def test_unknown_entries_score_zero(self):
        baseline = {}
        retrieved_ids = ["GE-unknown"]
        scores = score_retrieval(retrieved_ids, "scenario-a", baseline)
        assert scores == [0]


class TestAggregateByFailureMode:
    def test_groups_scenarios_by_mode(self):
        scenario_precisions = {
            "issue-2-cdi-wiring": {"KW": 0.5, "NL": 0.8},
            "issue-5-ai-llm-inference": {"KW": 0.3, "NL": 0.6},
            "issue-1-reactive-async": {"KW": 0.9, "NL": 0.95},
        }
        failure_modes = {
            "issue-2-cdi-wiring": ["VOCABULARY_GAP"],
            "issue-5-ai-llm-inference": ["VOCABULARY_GAP", "DOMAIN_ABSENCE"],
            "issue-1-reactive-async": ["SEMANTIC_WIN"],
        }
        result = aggregate_by_failure_mode(scenario_precisions, failure_modes)
        assert "VOCABULARY_GAP" in result
        assert result["VOCABULARY_GAP"]["count"] == 2
        assert "SEMANTIC_WIN" in result
        assert result["SEMANTIC_WIN"]["count"] == 1

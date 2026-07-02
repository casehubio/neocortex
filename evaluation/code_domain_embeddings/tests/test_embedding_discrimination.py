# evaluation/code_domain_embeddings/tests/test_embedding_discrimination.py
import numpy as np
import pytest

from evaluation.code_domain_embeddings.embedding_discrimination import (
    compute_cosine_similarity,
    compute_discrimination_gap,
    calibrate_threshold,
    categorize_pairs_by_category,
)
from evaluation.code_domain_embeddings.config import DISCRIMINATION_PAIRS


class TestCosineSimilarity:
    def test_identical_vectors(self):
        v = np.array([1.0, 0.0, 0.0])
        assert compute_cosine_similarity(v, v) == pytest.approx(1.0)

    def test_orthogonal_vectors(self):
        a = np.array([1.0, 0.0, 0.0])
        b = np.array([0.0, 1.0, 0.0])
        assert compute_cosine_similarity(a, b) == pytest.approx(0.0, abs=1e-7)

    def test_opposite_vectors(self):
        a = np.array([1.0, 0.0])
        b = np.array([-1.0, 0.0])
        assert compute_cosine_similarity(a, b) == pytest.approx(-1.0)

    def test_similar_vectors(self):
        a = np.array([1.0, 1.0, 0.0])
        b = np.array([1.0, 0.9, 0.1])
        sim = compute_cosine_similarity(a, b)
        assert 0.9 < sim < 1.0


class TestDiscriminationGap:
    def test_perfect_discrimination(self):
        far_scores = [0.1, 0.2, 0.15]
        close_scores = [0.9, 0.95, 0.85]
        gap = compute_discrimination_gap(far_scores, close_scores)
        assert gap > 0.5

    def test_no_discrimination(self):
        far_scores = [0.5, 0.5, 0.5]
        close_scores = [0.5, 0.5, 0.5]
        gap = compute_discrimination_gap(far_scores, close_scores)
        assert gap == pytest.approx(0.0, abs=0.01)

    def test_inverted_discrimination(self):
        far_scores = [0.9, 0.95]
        close_scores = [0.1, 0.2]
        gap = compute_discrimination_gap(far_scores, close_scores)
        assert gap < 0


class TestCalibrateThreshold:
    def test_threshold_is_reference_plus_tolerance(self):
        nomic_far_scores = {"@DefaultBean vs default": 0.92}
        threshold = calibrate_threshold(nomic_far_scores, reference_pair="@DefaultBean vs default")
        assert threshold == pytest.approx(0.92 + 0.05, abs=0.001)

    def test_threshold_with_custom_tolerance(self):
        nomic_far_scores = {"@DefaultBean vs default": 0.80}
        threshold = calibrate_threshold(
            nomic_far_scores,
            reference_pair="@DefaultBean vs default",
            tolerance=0.10,
        )
        assert threshold == pytest.approx(0.90, abs=0.001)


class TestCategorizePairs:
    def test_separates_by_category(self):
        categorized = categorize_pairs_by_category(DISCRIMINATION_PAIRS)
        assert "should_be_far" in categorized
        assert "should_be_close" in categorized
        assert "polysemy" in categorized
        assert len(categorized["should_be_far"]) == 5
        assert len(categorized["should_be_close"]) == 3
        assert len(categorized["polysemy"]) == 2

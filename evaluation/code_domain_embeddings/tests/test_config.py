# evaluation/code_domain_embeddings/tests/test_config.py
import pytest
from evaluation.code_domain_embeddings.config import (
    MODEL_REGISTRY,
    TEST_VOCABULARY,
    DISCRIMINATION_PAIRS,
    SCENARIOS_WITH_FAILURE_MODES,
    garden_path,
    benchmark_path,
    results_dir,
)


def test_model_registry_contains_both_baselines():
    assert "nomic-embed-text" in MODEL_REGISTRY
    assert "BGE-M3" in MODEL_REGISTRY


def test_model_registry_contains_all_candidates():
    expected = {"CodeBERT", "UniXcoder", "jina-code", "nomic-v1.5"}
    assert expected.issubset(set(MODEL_REGISTRY.keys()))


def test_model_registry_contains_skyline():
    assert "nomic-embed-code" in MODEL_REGISTRY


def test_model_registry_entries_have_required_fields():
    for name, entry in MODEL_REGISTRY.items():
        assert "hf_id" in entry, f"{name} missing hf_id"
        assert "role" in entry, f"{name} missing role"
        assert entry["role"] in ("baseline", "candidate", "skyline"), (
            f"{name} has invalid role: {entry['role']}"
        )


def test_test_vocabulary_contains_class_names():
    assert "ConcurrentHashMap" in TEST_VOCABULARY
    assert "ExceptionMapper" in TEST_VOCABULARY


def test_test_vocabulary_contains_cdi_annotations():
    assert "@DefaultBean" in TEST_VOCABULARY
    assert "@ApplicationScoped" in TEST_VOCABULARY


def test_test_vocabulary_contains_framework_identifiers():
    assert "AmbiguousResolutionException" in TEST_VOCABULARY
    assert "quarkus.datasource.active" in TEST_VOCABULARY


def test_discrimination_pairs_have_three_categories():
    categories = {p["category"] for p in DISCRIMINATION_PAIRS}
    assert categories == {"should_be_far", "should_be_close", "polysemy"}


def test_discrimination_pairs_have_required_fields():
    for pair in DISCRIMINATION_PAIRS:
        assert "a" in pair, f"pair missing 'a': {pair}"
        assert "b" in pair, f"pair missing 'b': {pair}"
        assert "category" in pair, f"pair missing 'category': {pair}"
        assert "label" in pair, f"pair missing 'label': {pair}"


def test_scenarios_with_failure_modes_maps_to_known_modes():
    valid_modes = {"VOCABULARY_GAP", "POLYSEMY", "SEMANTIC_WIN",
                   "DOMAIN_ABSENCE", "UNAMBIGUOUS_TERM"}
    for scenario_id, modes in SCENARIOS_WITH_FAILURE_MODES.items():
        for mode in modes:
            assert mode in valid_modes, (
                f"{scenario_id} has unknown failure mode: {mode}"
            )


def test_garden_path_returns_path():
    p = garden_path()
    assert p.name == "garden"


def test_benchmark_path_returns_path():
    p = benchmark_path()
    assert p.name == "benchmark"


def test_results_dir_returns_path():
    d = results_dir()
    assert d.name == "results"

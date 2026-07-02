"""Layer 3: Full benchmark — 14-scenario retrieval evaluation."""

import json
import sys
from collections import defaultdict
from pathlib import Path

import numpy as np

from .config import MODEL_REGISTRY, SCENARIOS_WITH_FAILURE_MODES, benchmark_path, results_dir
from .snapshot_corpus import load_snapshot


def compute_precision(scores: list[int], threshold: int = 1) -> float:
    """Precision@k: fraction of retrieved entries with score >= threshold."""
    if not scores:
        return 0.0
    return sum(1 for s in scores if s >= threshold) / len(scores)


def score_retrieval(
    retrieved_ids: list[str],
    scenario_id: str,
    baseline: dict,
) -> list[int]:
    """Score retrieved entries against the human-judged baseline."""
    scores = []
    for ge_id in retrieved_ids:
        if ge_id in baseline and scenario_id in baseline[ge_id]:
            scores.append(baseline[ge_id][scenario_id]["benchmark_score"])
        else:
            scores.append(0)
    return scores


def aggregate_by_failure_mode(
    scenario_precisions: dict[str, dict[str, float]],
    failure_modes: dict[str, list[str]],
) -> dict:
    """Aggregate precision scores by failure mode category."""
    mode_data = defaultdict(lambda: {"scenarios": [], "kw_scores": [], "nl_scores": []})
    for scenario_id, precisions in scenario_precisions.items():
        modes = failure_modes.get(scenario_id, [])
        for mode in modes:
            mode_data[mode]["scenarios"].append(scenario_id)
            if "KW" in precisions:
                mode_data[mode]["kw_scores"].append(precisions["KW"])
            if "NL" in precisions:
                mode_data[mode]["nl_scores"].append(precisions["NL"])

    result = {}
    for mode, data in mode_data.items():
        result[mode] = {
            "count": len(data["scenarios"]),
            "scenarios": data["scenarios"],
            "mean_kw": round(float(np.mean(data["kw_scores"])), 4) if data["kw_scores"] else None,
            "mean_nl": round(float(np.mean(data["nl_scores"])), 4) if data["nl_scores"] else None,
        }
    return result


def load_scenarios():
    """Load scenarios from the engine benchmark harness."""
    bp = benchmark_path()
    sys.path.insert(0, str(bp.parent))
    from benchmark.queries import SCENARIOS
    return SCENARIOS


def load_baseline() -> dict:
    """Load baseline scores from the engine benchmark harness."""
    bp = benchmark_path()
    return json.loads((bp / "baseline_scores.json").read_text())


def embed_corpus_and_queries(model, entries: list[dict], queries: list[str]) -> tuple:
    """Embed corpus entries and queries, return (corpus_embeddings, query_embeddings)."""
    corpus_texts = [f"{e['title']}\n{e['content']}" for e in entries]
    all_texts = corpus_texts + queries
    all_embeddings = model.encode(all_texts, normalize_embeddings=True, show_progress_bar=True)
    corpus_embeddings = all_embeddings[:len(corpus_texts)]
    query_embeddings = all_embeddings[len(corpus_texts):]
    return corpus_embeddings, query_embeddings


def retrieve_top_k(
    query_embedding: np.ndarray,
    corpus_embeddings: np.ndarray,
    entry_ids: list[str],
    k: int = 20,
) -> list[str]:
    """Retrieve top-k entries by cosine similarity."""
    similarities = corpus_embeddings @ query_embedding
    top_indices = np.argsort(similarities)[::-1][:k]
    return [entry_ids[i] for i in top_indices]


def run_benchmark(
    model_names: list[str] | None = None,
    snapshot_dir: Path | None = None,
    top_k: int = 20,
) -> dict:
    """Run the full 14-scenario benchmark for specified models."""
    from sentence_transformers import SentenceTransformer

    if model_names is None:
        model_names = [n for n, e in MODEL_REGISTRY.items() if e["role"] != "skyline"]

    if snapshot_dir is None:
        snapshot_dir = Path(__file__).parent / "corpus-snapshot"
    snapshot = load_snapshot(snapshot_dir)
    entries = snapshot["entries"]
    entry_ids = [e["id"] for e in entries]

    scenarios = load_scenarios()
    baseline = load_baseline()

    results = {}
    for name in model_names:
        entry = MODEL_REGISTRY[name]
        hf_id = entry["hf_id"]
        print(f"\n{'='*60}")
        print(f"Benchmarking {name} ({hf_id})...")
        print(f"{'='*60}")

        try:
            model = SentenceTransformer(hf_id, trust_remote_code=True)
        except Exception as e:
            print(f"  SKIP: {e}")
            results[name] = {"error": str(e)}
            continue

        queries = []
        query_labels = []
        for scenario in scenarios:
            queries.append(scenario.kw_query)
            query_labels.append((scenario.id, "KW"))
            queries.append(scenario.nl_query)
            query_labels.append((scenario.id, "NL"))

        corpus_emb, query_emb = embed_corpus_and_queries(model, entries, queries)

        scenario_precisions = {}
        retrieval_details = []

        for i, (scenario_id, query_type) in enumerate(query_labels):
            retrieved = retrieve_top_k(query_emb[i], corpus_emb, entry_ids, k=top_k)
            scores = score_retrieval(retrieved, scenario_id, baseline)
            precision = compute_precision(scores, threshold=1)

            if scenario_id not in scenario_precisions:
                scenario_precisions[scenario_id] = {}
            scenario_precisions[scenario_id][query_type] = round(precision, 4)

            retrieval_details.append({
                "scenario_id": scenario_id,
                "query_type": query_type,
                "precision": round(precision, 4),
                "retrieved_ids": retrieved[:10],
                "scores": scores[:10],
            })

        by_mode = aggregate_by_failure_mode(scenario_precisions, SCENARIOS_WITH_FAILURE_MODES)

        all_kw = [p.get("KW", 0) for p in scenario_precisions.values()]
        all_nl = [p.get("NL", 0) for p in scenario_precisions.values()]

        results[name] = {
            "scenario_precisions": scenario_precisions,
            "failure_mode_breakdown": by_mode,
            "overall_kw_precision": round(float(np.mean(all_kw)), 4),
            "overall_nl_precision": round(float(np.mean(all_nl)), 4),
            "overall_precision": round(float(np.mean(all_kw + all_nl)), 4),
            "retrieval_details": retrieval_details,
            "corpus_size": len(entries),
        }
        print(f"  Overall: KW={results[name]['overall_kw_precision']:.1%} "
              f"NL={results[name]['overall_nl_precision']:.1%}")
        if "VOCABULARY_GAP" in by_mode:
            vg = by_mode["VOCABULARY_GAP"]
            if vg["mean_kw"] is not None and vg["mean_nl"] is not None:
                print(f"  VOCABULARY_GAP: KW={vg['mean_kw']:.1%} NL={vg['mean_nl']:.1%}")

    return results


def save_results(results: dict) -> Path:
    """Save results to JSON."""
    out = results_dir()
    out.mkdir(parents=True, exist_ok=True)
    path = out / "benchmark_precision.json"
    path.write_text(json.dumps(results, indent=2, ensure_ascii=False), encoding="utf-8")
    return path


if __name__ == "__main__":
    models = sys.argv[1:] if len(sys.argv) > 1 else None
    results = run_benchmark(models)
    path = save_results(results)
    print(f"\nResults saved to {path}")

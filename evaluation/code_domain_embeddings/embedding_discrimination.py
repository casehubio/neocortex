# evaluation/code_domain_embeddings/embedding_discrimination.py
"""Layer 2: Embedding discrimination — can models distinguish Java concepts?"""

import json
import sys
from collections import defaultdict
from pathlib import Path

import numpy as np

from .config import MODEL_REGISTRY, DISCRIMINATION_PAIRS, results_dir


def compute_cosine_similarity(vec_a: np.ndarray, vec_b: np.ndarray) -> float:
    """Compute cosine similarity between two vectors."""
    dot = np.dot(vec_a, vec_b)
    norm_a = np.linalg.norm(vec_a)
    norm_b = np.linalg.norm(vec_b)
    if norm_a == 0 or norm_b == 0:
        return 0.0
    return float(dot / (norm_a * norm_b))


def compute_discrimination_gap(far_scores: list[float], close_scores: list[float]) -> float:
    """Compute gap between mean close scores and mean far scores.

    Positive gap = good discrimination (close pairs score higher than far pairs).
    Negative gap = inverted discrimination (model can't distinguish concepts).
    """
    if not far_scores or not close_scores:
        return 0.0
    return float(np.mean(close_scores) - np.mean(far_scores))


def calibrate_threshold(
    reference_scores: dict[str, float],
    reference_pair: str,
    tolerance: float = 0.05,
) -> float:
    """Calibrate the vocabulary-gap detection threshold from nomic-embed-text's score.

    Models producing "should be far" scores within ±tolerance of nomic's score
    on the reference pair have an equivalent vocabulary gap.
    """
    ref_score = reference_scores[reference_pair]
    return ref_score + tolerance


def categorize_pairs_by_category(pairs: list[dict]) -> dict[str, list[dict]]:
    """Group discrimination pairs by category."""
    categorized = defaultdict(list)
    for pair in pairs:
        categorized[pair["category"]].append(pair)
    return dict(categorized)


def embed_texts(model, tokenizer, texts: list[str]) -> np.ndarray:
    """Embed texts using a sentence-transformers model or HF model."""
    if hasattr(model, 'encode'):
        return model.encode(texts, convert_to_numpy=True, normalize_embeddings=True)

    import torch
    inputs = tokenizer(texts, padding=True, truncation=True, return_tensors="pt")
    with torch.no_grad():
        outputs = model(**inputs)
    embeddings = outputs.last_hidden_state.mean(dim=1).numpy()
    norms = np.linalg.norm(embeddings, axis=1, keepdims=True)
    norms[norms == 0] = 1
    return embeddings / norms


def run_bgem3_multimodal(pairs: list[dict], all_texts: list[str]) -> dict:
    """Run BGE-M3 sparse and ColBERT discrimination (spec §Layer 2 multi-modal baseline)."""
    try:
        from FlagEmbedding import BGEM3FlagModel
    except ImportError:
        print("  FlagEmbedding not available — skipping BGE-M3 multi-modal")
        return {"error": "FlagEmbedding not installed"}

    print("  Loading BGE-M3 for multi-modal analysis...")
    model = BGEM3FlagModel("BAAI/bge-m3", use_fp16=False)
    output = model.encode(all_texts, return_dense=True, return_sparse=True, return_colbert_vecs=True)

    sparse_results = []
    colbert_results = []
    for i in range(0, len(all_texts), 2):
        pair = pairs[i // 2]

        # Sparse: check token activations
        sparse_a = output["lexical_weights"][i]
        sparse_b = output["lexical_weights"][i + 1]
        common_tokens = set(sparse_a.keys()) & set(sparse_b.keys())
        sparse_overlap = sum(min(sparse_a[t], sparse_b[t]) for t in common_tokens)
        sparse_results.append({
            "label": pair["label"],
            "category": pair["category"],
            "sparse_overlap": round(float(sparse_overlap), 4),
            "a_active_tokens": len(sparse_a),
            "b_active_tokens": len(sparse_b),
        })

        # ColBERT: MAX_SIM scoring
        col_a = np.array(output["colbert_vecs"][i])
        col_b = np.array(output["colbert_vecs"][i + 1])
        sim_matrix = col_a @ col_b.T
        max_sim = float(sim_matrix.max(axis=1).mean())
        colbert_results.append({
            "label": pair["label"],
            "category": pair["category"],
            "max_sim": round(max_sim, 4),
        })

    return {"sparse": sparse_results, "colbert": colbert_results}


def run_discrimination(model_names: list[str] | None = None) -> dict:
    """Run embedding discrimination on all specified models."""
    from sentence_transformers import SentenceTransformer
    from transformers import AutoModel, AutoTokenizer

    if model_names is None:
        model_names = [n for n, e in MODEL_REGISTRY.items() if e["role"] != "skyline"]

    categorized = categorize_pairs_by_category(DISCRIMINATION_PAIRS)
    all_texts = []
    for pair in DISCRIMINATION_PAIRS:
        all_texts.extend([pair["a"], pair["b"]])

    results = {}
    for name in model_names:
        entry = MODEL_REGISTRY[name]
        hf_id = entry["hf_id"]
        print(f"Loading {name} ({hf_id})...")

        try:
            model = SentenceTransformer(hf_id, trust_remote_code=True)
            embeddings = model.encode(all_texts, normalize_embeddings=True)
        except Exception:
            print(f"  SentenceTransformer failed, trying AutoModel...")
            try:
                tokenizer = AutoTokenizer.from_pretrained(hf_id, trust_remote_code=True)
                raw_model = AutoModel.from_pretrained(hf_id, trust_remote_code=True)
                embeddings = embed_texts(raw_model, tokenizer, all_texts)
            except Exception as e:
                print(f"  SKIP: {e}")
                results[name] = {"error": str(e)}
                continue

        text_to_embedding = {text: embeddings[i] for i, text in enumerate(all_texts)}

        pair_scores = []
        for pair in DISCRIMINATION_PAIRS:
            sim = compute_cosine_similarity(
                text_to_embedding[pair["a"]],
                text_to_embedding[pair["b"]],
            )
            pair_scores.append({
                "a": pair["a"],
                "b": pair["b"],
                "category": pair["category"],
                "label": pair["label"],
                "similarity": round(sim, 4),
            })

        far_scores = [p["similarity"] for p in pair_scores if p["category"] == "should_be_far"]
        close_scores = [p["similarity"] for p in pair_scores if p["category"] == "should_be_close"]
        polysemy_scores = [p["similarity"] for p in pair_scores if p["category"] == "polysemy"]

        results[name] = {
            "pairs": pair_scores,
            "mean_far": round(float(np.mean(far_scores)), 4) if far_scores else None,
            "mean_close": round(float(np.mean(close_scores)), 4) if close_scores else None,
            "mean_polysemy": round(float(np.mean(polysemy_scores)), 4) if polysemy_scores else None,
            "discrimination_gap": round(compute_discrimination_gap(far_scores, close_scores), 4),
        }
        print(f"  gap={results[name]['discrimination_gap']:.4f} "
              f"(far={results[name]['mean_far']:.4f}, close={results[name]['mean_close']:.4f})")

    # BGE-M3 multi-modal baseline (sparse + ColBERT)
    if "BGE-M3" in model_names or model_names is None:
        print("\nRunning BGE-M3 multi-modal analysis (sparse + ColBERT)...")
        results["BGE-M3-multimodal"] = run_bgem3_multimodal(DISCRIMINATION_PAIRS, all_texts)

    return results


def save_results(results: dict) -> Path:
    """Save results to JSON."""
    out = results_dir()
    out.mkdir(parents=True, exist_ok=True)
    path = out / "discrimination_scores.json"
    path.write_text(json.dumps(results, indent=2, ensure_ascii=False), encoding="utf-8")
    return path


if __name__ == "__main__":
    models = sys.argv[1:] if len(sys.argv) > 1 else None
    results = run_discrimination(models)
    path = save_results(results)
    print(f"\nResults saved to {path}")

"""Layer 1: Tokenizer analysis — how each model splits Java identifiers."""

import json
import re
import sys
from pathlib import Path

from .config import MODEL_REGISTRY, TEST_VOCABULARY, results_dir

MEANINGFUL_SUBWORD_MIN_LEN = 3

TOKENIZER_TYPE_MAP = {
    "BertTokenizer": "WordPiece",
    "BertTokenizerFast": "WordPiece",
    "GPT2Tokenizer": "BPE",
    "GPT2TokenizerFast": "BPE",
    "RobertaTokenizer": "BPE",
    "RobertaTokenizerFast": "BPE",
    "XLMRobertaTokenizer": "SentencePiece",
    "XLMRobertaTokenizerFast": "SentencePiece",
    "T5Tokenizer": "SentencePiece",
    "LlamaTokenizer": "BPE",
    "LlamaTokenizerFast": "BPE",
    "Qwen2Tokenizer": "BPE",
    "Qwen2TokenizerFast": "BPE",
    "PreTrainedTokenizerFast": "Unknown",
}


def classify_tokenizer_type(class_name: str) -> str:
    """Classify tokenizer type from its class name."""
    return TOKENIZER_TYPE_MAP.get(class_name, "Unknown")


def find_meaningful_subwords(tokens: list[str], original: str) -> list[str]:
    """Find tokens that represent meaningful subwords of the original identifier."""
    meaningful = []
    for token in tokens:
        cleaned = re.sub(r'^[#@▁Ġ]+', '', token)
        if len(cleaned) >= MEANINGFUL_SUBWORD_MIN_LEN:
            meaningful.append(token)
    return meaningful


def analyze_tokenizer_output(tokens: list[str], identifier: str) -> dict:
    """Analyze a single tokenization result."""
    cleaned_tokens = [re.sub(r'^[#@▁Ġ]+', '', t) for t in tokens]
    rejoined = "".join(cleaned_tokens)
    preserved = len(tokens) == 1

    return {
        "tokens": tokens,
        "count": len(tokens),
        "preserved": preserved,
        "meaningful_subwords": find_meaningful_subwords(tokens, identifier),
    }


def format_results_table(results: dict, vocabulary: list[str]) -> str:
    """Format results as a markdown table."""
    header = "| Model | " + " | ".join(vocabulary) + " |"
    separator = "|" + "|".join(["---"] * (len(vocabulary) + 1)) + "|"
    rows = [header, separator]

    for model_name, identifiers in results.items():
        cells = [model_name]
        for identifier in vocabulary:
            if identifier in identifiers:
                entry = identifiers[identifier]
                token_str = " ".join(entry["tokens"])
                cells.append(f"`{token_str}` ({entry['count']})")
            else:
                cells.append("—")
        rows.append("| " + " | ".join(cells) + " |")

    return "\n".join(rows)


def run_analysis(model_names: list[str] | None = None) -> dict:
    """Run tokenizer analysis on all specified models."""
    from transformers import AutoTokenizer

    if model_names is None:
        model_names = list(MODEL_REGISTRY.keys())

    results = {}
    for name in model_names:
        entry = MODEL_REGISTRY[name]
        hf_id = entry["hf_id"]
        print(f"Loading tokenizer for {name} ({hf_id})...")

        try:
            tokenizer = AutoTokenizer.from_pretrained(hf_id, trust_remote_code=True)
        except Exception as e:
            print(f"  SKIP: {e}")
            results[name] = {"error": str(e)}
            continue

        tok_type = classify_tokenizer_type(type(tokenizer).__name__)
        model_results = {}

        for identifier in TEST_VOCABULARY:
            tokens = tokenizer.tokenize(identifier)
            model_results[identifier] = analyze_tokenizer_output(tokens, identifier)

        results[name] = {
            "tokenizer_type": tok_type,
            "tokenizer_class": type(tokenizer).__name__,
            "identifiers": model_results,
        }
        total = sum(r["count"] for r in model_results.values())
        preserved = sum(1 for r in model_results.values() if r["preserved"])
        print(f"  {tok_type} | {total} total tokens | {preserved}/{len(TEST_VOCABULARY)} preserved")

    return results


def save_results(results: dict) -> Path:
    """Save results to JSON."""
    out = results_dir()
    out.mkdir(parents=True, exist_ok=True)
    path = out / "tokenizer_splits.json"
    path.write_text(json.dumps(results, indent=2, ensure_ascii=False), encoding="utf-8")
    return path


if __name__ == "__main__":
    models = sys.argv[1:] if len(sys.argv) > 1 else None
    results = run_analysis(models)
    path = save_results(results)
    print(f"\nResults saved to {path}")

    # Print summary table for identifiers that show the most variation
    print("\n--- Summary (token counts per identifier) ---")
    for name, data in results.items():
        if "error" in data:
            print(f"{name}: ERROR — {data['error']}")
            continue
        counts = {k: v["count"] for k, v in data["identifiers"].items()}
        avg = sum(counts.values()) / len(counts) if counts else 0
        print(f"{name} ({data['tokenizer_type']}): avg {avg:.1f} tokens/identifier")

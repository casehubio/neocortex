#!/usr/bin/env python3
"""
Export BAAI/bge-m3 to a three-head ONNX model (dense + sparse + ColBERT).

Usage:
    pip install -r scripts/requirements-export.txt
    python scripts/export_bge_m3.py

Output:
    ~/.hortora/models/bge-m3/model.onnx      (~2.2GB, external data)
    ~/.hortora/models/bge-m3/tokenizer.json

Idempotent: skips export if model exists and checksum matches.
Atomic: writes to temp dir, renames on success.
Validates: PyTorch vs ONNX allclose(atol=1e-4) on 7+ test cases including batch.
"""

from __future__ import annotations

import hashlib
import shutil
import sys
from pathlib import Path
from typing import Final

import numpy as np
import torch

SCRIPTS_DIR: Final = Path(__file__).resolve().parent
OUTPUT_DIR: Final = Path.home() / ".hortora" / "models" / "bge-m3"
TEMP_DIR: Final = OUTPUT_DIR / ".export-tmp"
CHECKSUM_FILE: Final = SCRIPTS_DIR / "bge-m3-checksums.sha256"
MODEL_FILE: Final = "model.onnx"
MODEL_DATA_FILE: Final = "model.onnx.data"
TOKENIZER_FILE: Final = "tokenizer.json"
OPSET_VERSION: Final = 16
ATOL: Final = 1e-4


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def check_idempotent() -> bool:
    """Return True if model already exported and verified."""
    model_path = OUTPUT_DIR / MODEL_FILE
    if not model_path.exists():
        return False
    if not CHECKSUM_FILE.exists():
        return False

    expected: dict[str, str] = {}
    for line in CHECKSUM_FILE.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        digest, name = line.split(None, 1)
        expected[name] = digest

    for name, digest in expected.items():
        file_path = OUTPUT_DIR / name
        if not file_path.exists():
            return False
        if sha256(file_path) != digest:
            return False

    return True


def export_onnx(model: torch.nn.Module, output_path: Path) -> None:
    """Export the three-head model to ONNX with dynamic axes."""
    import inspect

    vocab_size = model.config.vocab_size  # type: ignore[attr-defined]

    dummy_input = {
        "input_ids": torch.randint(0, vocab_size, (1, 32)),
        "attention_mask": torch.ones(1, 32, dtype=torch.long),
    }

    # Detect PyTorch version for external_data parameter name (GE-20260703-e0af92)
    export_params = inspect.signature(torch.onnx.export).parameters
    if "external_data" in export_params:
        ext_data_kwarg = {"external_data": True}
    else:
        ext_data_kwarg = {"use_external_data_format": True}

    print(f"Exporting to ONNX (opset {OPSET_VERSION})...")
    torch.onnx.export(
        model,
        (dummy_input,),
        str(output_path),
        opset_version=OPSET_VERSION,
        input_names=["input_ids", "attention_mask"],
        output_names=["dense", "sparse", "colbert"],
        dynamic_axes={
            "input_ids": {0: "batch_size", 1: "sequence"},
            "attention_mask": {0: "batch_size", 1: "sequence"},
            "dense": {0: "batch_size"},
            "sparse": {0: "batch_size"},
            "colbert": {0: "batch_size", 1: "sequence"},
        },
        **ext_data_kwarg,
    )
    print(f"  Exported: {output_path}")


def optimize_onnx(input_path: Path, output_path: Path) -> None:
    """Apply O2 optimization (basic + transformer fusions)."""
    from onnxruntime.transformers.optimizer import optimize_model

    print("Applying O2 optimization...")
    optimized = optimize_model(
        str(input_path),
        model_type="bert",
        opt_level=2,
        use_gpu=False,
    )
    optimized.save_model_to_file(str(output_path), use_external_data_format=True)
    print(f"  Optimized: {output_path}")


def validate(
    pytorch_model: torch.nn.Module,
    onnx_path: Path,
    tokenizer_path: Path,
) -> None:
    """Validate ONNX output against PyTorch for edge-case inputs."""
    import onnxruntime as ort
    from transformers import AutoTokenizer

    tokenizer = AutoTokenizer.from_pretrained(str(tokenizer_path.parent))
    session = ort.InferenceSession(
        str(onnx_path),
        providers=["CPUExecutionProvider"],
    )

    # Individual test cases (batch_size=1)
    test_cases: list[tuple[str, str]] = [
        ("standard_en_1", "The quick brown fox jumps over the lazy dog."),
        ("standard_en_2", "Machine learning models require careful evaluation."),
        ("standard_en_3", "Quarkus CDI beans are ApplicationScoped by default."),
        ("short_input", "Hi"),
        ("multilingual_cjk", "機械学習は複雑な問題を解決します。"),
        ("multilingual_arabic", "الذكاء الاصطناعي يغير العالم"),
        (
            "repeated_tokens",
            "the the the cat cat cat sat sat sat on the the the mat mat mat",
        ),
        (
            "near_max_length",
            # ~8192 tokens after tokenization — exercises truncation boundary
            " ".join(["embedding"] * 8000),
        ),
    ]

    print(f"\nValidating {len(test_cases)} individual test cases...")
    for name, text in test_cases:
        _validate_single(pytorch_model, session, tokenizer, name, text)

    # Batch test (batch_size=3)
    batch_texts = [
        "Dense embeddings capture semantic similarity.",
        "Sparse vectors enable keyword matching.",
        "ColBERT uses late interaction for reranking.",
    ]
    print(f"\nValidating batch test (batch_size={len(batch_texts)})...")
    _validate_batch(pytorch_model, session, tokenizer, batch_texts)

    print("\nAll validation passed.")


def _validate_single(
    pytorch_model: torch.nn.Module,
    session: "ort.InferenceSession",  # type: ignore[name-defined]
    tokenizer: "AutoTokenizer",  # type: ignore[name-defined]
    name: str,
    text: str,
) -> None:
    encoded = tokenizer(text, return_tensors="pt", padding=False, truncation=True)
    input_ids = encoded["input_ids"]
    attention_mask = encoded["attention_mask"]

    # PyTorch
    with torch.no_grad():
        pt_out = pytorch_model(input_ids=input_ids, attention_mask=attention_mask)

    # ONNX
    onnx_out = session.run(
        None,
        {
            "input_ids": input_ids.numpy(),
            "attention_mask": attention_mask.numpy(),
        },
    )
    onnx_dense, onnx_sparse, onnx_colbert = onnx_out

    pt_dense = pt_out["dense"].numpy()
    pt_sparse = pt_out["sparse"].numpy()
    pt_colbert = pt_out["colbert"].numpy()

    _assert_close(pt_dense, onnx_dense, name, "dense")
    _assert_close(pt_sparse, onnx_sparse, name, "sparse")
    _assert_close(pt_colbert, onnx_colbert, name, "colbert")

    token_count = int(attention_mask.sum())
    print(
        f"  {name}: OK ({token_count} tokens, "
        f"dense={pt_dense.shape}, sparse={pt_sparse.shape}, "
        f"colbert={pt_colbert.shape})"
    )


def _validate_batch(
    pytorch_model: torch.nn.Module,
    session: "ort.InferenceSession",  # type: ignore[name-defined]
    tokenizer: "AutoTokenizer",  # type: ignore[name-defined]
    texts: list[str],
) -> None:
    encoded = tokenizer(
        texts, return_tensors="pt", padding=True, truncation=True,
    )
    input_ids = encoded["input_ids"]
    attention_mask = encoded["attention_mask"]

    # PyTorch
    with torch.no_grad():
        pt_out = pytorch_model(input_ids=input_ids, attention_mask=attention_mask)

    # ONNX
    onnx_out = session.run(
        None,
        {
            "input_ids": input_ids.numpy(),
            "attention_mask": attention_mask.numpy(),
        },
    )
    onnx_dense, onnx_sparse, onnx_colbert = onnx_out

    pt_dense = pt_out["dense"].numpy()
    pt_sparse = pt_out["sparse"].numpy()
    pt_colbert = pt_out["colbert"].numpy()

    _assert_close(pt_dense, onnx_dense, "batch", "dense")
    _assert_close(pt_sparse, onnx_sparse, "batch", "sparse")
    _assert_close(pt_colbert, onnx_colbert, "batch", "colbert")

    print(
        f"  batch: OK (batch_size={len(texts)}, "
        f"dense={pt_dense.shape}, sparse={pt_sparse.shape}, "
        f"colbert={pt_colbert.shape})"
    )


def _assert_close(
    pt: np.ndarray, onnx: np.ndarray, case: str, output: str,
) -> None:
    if not np.allclose(pt, onnx, atol=ATOL):
        max_diff = float(np.max(np.abs(pt - onnx)))
        raise AssertionError(
            f"Validation failed for {case}/{output}: "
            f"max diff={max_diff:.6f}, atol={ATOL}"
        )


def write_checksums(output_dir: Path) -> None:
    """Write SHA-256 checksums to scripts/bge-m3-checksums.sha256."""
    lines: list[str] = []
    for name in [MODEL_FILE, MODEL_DATA_FILE, TOKENIZER_FILE]:
        file_path = output_dir / name
        if file_path.exists():
            digest = sha256(file_path)
            lines.append(f"{digest}  {name}")
            print(f"  {name}: {digest}")

    CHECKSUM_FILE.write_text("\n".join(lines) + "\n")
    print(f"Checksums written to {CHECKSUM_FILE}")


def main() -> None:
    # Idempotency check
    if check_idempotent():
        print("Model already exported and verified — skipping.")
        sys.exit(0)

    print("=== BGE-M3 Three-Head ONNX Export ===\n")

    # Load model
    print("Loading BAAI/bge-m3 (downloads ~2.2GB on first run)...")
    from bgem3_model import load_model
    pytorch_model, model_dir = load_model()
    print(f"  Model loaded from {model_dir}")
    print(f"  Vocab size: {pytorch_model.config.vocab_size}")

    # Prepare temp directory
    if TEMP_DIR.exists():
        shutil.rmtree(TEMP_DIR)
    TEMP_DIR.mkdir(parents=True)

    try:
        # Export
        raw_onnx = TEMP_DIR / "model_raw.onnx"
        export_onnx(pytorch_model, raw_onnx)

        # Optimize
        optimized_onnx = TEMP_DIR / MODEL_FILE
        optimize_onnx(raw_onnx, optimized_onnx)

        # Copy tokenizer
        src_tokenizer = model_dir / TOKENIZER_FILE
        dst_tokenizer = TEMP_DIR / TOKENIZER_FILE
        if src_tokenizer.exists():
            shutil.copy2(src_tokenizer, dst_tokenizer)
            print(f"  Tokenizer copied: {dst_tokenizer}")
        else:
            raise FileNotFoundError(
                f"tokenizer.json not found in {model_dir}"
            )

        # Clean up raw export
        raw_onnx.unlink(missing_ok=True)
        raw_data = TEMP_DIR / "model_raw.onnx.data"
        raw_data.unlink(missing_ok=True)

        # Validate against PyTorch (uses optimized model)
        validate(pytorch_model, optimized_onnx, dst_tokenizer)

        # Atomic rename to final location
        OUTPUT_DIR.parent.mkdir(parents=True, exist_ok=True)
        if OUTPUT_DIR.exists():
            shutil.rmtree(OUTPUT_DIR)
        TEMP_DIR.rename(OUTPUT_DIR)

        # Write checksums
        print("\nWriting checksums...")
        write_checksums(OUTPUT_DIR)

        print(f"\nExport complete: {OUTPUT_DIR}")
        print(f"  {MODEL_FILE}")
        print(f"  {MODEL_DATA_FILE}")
        print(f"  {TOKENIZER_FILE}")

    finally:
        if TEMP_DIR.exists():
            shutil.rmtree(TEMP_DIR, ignore_errors=True)


if __name__ == "__main__":
    main()

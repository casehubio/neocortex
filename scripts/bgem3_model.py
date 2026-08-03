"""
PyTorch nn.Module wrapper for BGE-M3 three-head ONNX export.

Loads BAAI/bge-m3 backbone + head weights and produces three named outputs:
  - dense:   [batch, 1024] — CLS pooling, L2-normalized
  - sparse:  [batch, 250002] — vocab-scattered weights (scatter baked in-graph)
  - colbert: [batch, seq_len, 1024] — per-token embeddings incl. CLS, L2-normalized

Adapted from aapot/bge-m3-onnx with three modifications:
  1. Sparse scatter baked into the graph (output is vocab-indexed, not token-indexed)
  2. ColBERT includes CLS token (required by OnnxInferenceModel batch padding logic)
  3. Output names match Java BgeM3Embedder expectations: "dense", "sparse", "colbert"
"""

from __future__ import annotations

from pathlib import Path
from typing import Any

import torch
import torch.nn as nn
import torch.nn.functional as F
from transformers import AutoModel, AutoConfig


class BgeM3ThreeHead(nn.Module):
    """Three-head wrapper around BAAI/bge-m3 for ONNX export."""

    def __init__(self, model_dir: str | Path) -> None:
        super().__init__()
        model_dir = Path(model_dir)

        config = AutoConfig.from_pretrained(model_dir)
        self.config = config
        self.model = AutoModel.from_pretrained(model_dir)

        hidden_size: int = config.hidden_size

        self.colbert_linear = nn.Linear(hidden_size, 1024)
        colbert_path = model_dir / "colbert_linear.pt"
        if colbert_path.exists():
            self.colbert_linear.load_state_dict(
                torch.load(colbert_path, map_location="cpu", weights_only=True)
            )

        self.sparse_linear = nn.Linear(hidden_size, 1)
        sparse_path = model_dir / "sparse_linear.pt"
        if sparse_path.exists():
            self.sparse_linear.load_state_dict(
                torch.load(sparse_path, map_location="cpu", weights_only=True)
            )

    def forward(
        self,
        input_ids: torch.Tensor,
        attention_mask: torch.Tensor,
    ) -> dict[str, torch.Tensor]:
        outputs = self.model(input_ids=input_ids, attention_mask=attention_mask)
        last_hidden_state: torch.Tensor = outputs.last_hidden_state
        batch_size = last_hidden_state.shape[0]

        # --- Dense: CLS pooling + L2-normalize ---
        dense = last_hidden_state[:, 0]
        dense = F.normalize(dense, dim=-1)

        # --- Sparse: linear → ReLU → scatter to vocab via input_ids ---
        token_weights = torch.relu(
            self.sparse_linear(last_hidden_state)
        ).squeeze(-1)  # [batch, seq_len]

        # Zero padding token weights
        token_weights = token_weights * attention_mask.float()

        # Scatter to vocab-sized output using non-in-place scatter_reduce
        sparse = torch.zeros(
            batch_size, self.config.vocab_size,
            device=token_weights.device, dtype=token_weights.dtype,
        )
        sparse = torch.scatter_reduce(
            sparse, 1, input_ids, token_weights, reduce="amax",
        )

        # --- ColBERT: linear on ALL tokens (incl. CLS) + mask + L2-normalize ---
        colbert = self.colbert_linear(last_hidden_state)
        colbert = colbert * attention_mask[:, :, None].float()
        colbert = F.normalize(colbert, dim=-1)
        # Re-zero padding positions after normalization (normalize maps 0→0/0=nan→0)
        colbert = colbert * attention_mask[:, :, None].float()

        return {"dense": dense, "sparse": sparse, "colbert": colbert}


def load_model(model_name_or_path: str = "BAAI/bge-m3") -> tuple[BgeM3ThreeHead, Path]:
    """Download (if needed) and load the BGE-M3 model.

    Returns the wrapper and the local model directory path.
    """
    from huggingface_hub import snapshot_download

    model_dir = snapshot_download(model_name_or_path)
    model = BgeM3ThreeHead(model_dir)
    model.eval()
    return model, Path(model_dir)

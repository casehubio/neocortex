import math
import torch
import torch.nn as nn
from evaluation.strategy_classifier.config import HyperParams


class StrategyClassifier(nn.Module):
    def __init__(self, f_temporal: int, f_map: int, num_classes: int, hp: HyperParams):
        super().__init__()
        c1, c2 = hp.conv_channels

        self.conv1 = nn.Conv1d(f_temporal, c1, kernel_size=3, padding=1)
        self.bn1 = nn.BatchNorm1d(c1)
        self.conv2 = nn.Conv1d(c1, c2, kernel_size=3, padding=1)
        self.bn2 = nn.BatchNorm1d(c2)

        self.pos_encoding = SinusoidalPositionalEncoding(c2, hp.max_windows)

        self.attn = nn.MultiheadAttention(
            embed_dim=c2, num_heads=1, batch_first=True
        )

        self.classifier = nn.Sequential(
            nn.Linear(c2 + f_map, hp.dense_hidden),
            nn.ReLU(),
            nn.Dropout(hp.dropout),
            nn.Linear(hp.dense_hidden, num_classes),
        )

    def forward(self, temporal: torch.Tensor, map_feat: torch.Tensor) -> torch.Tensor:
        x = temporal.permute(0, 2, 1)
        x = torch.relu(self.bn1(self.conv1(x)))
        x = torch.relu(self.bn2(self.conv2(x)))
        x = x.permute(0, 2, 1)

        padding_mask = (temporal.abs().sum(dim=-1) == 0)

        x = self.pos_encoding(x)

        x, _ = self.attn(x, x, x, key_padding_mask=padding_mask)

        valid_mask = (~padding_mask).unsqueeze(-1).float()
        valid_count = valid_mask.sum(dim=1).clamp(min=1)
        pooled = (x * valid_mask).sum(dim=1) / valid_count

        combined = torch.cat([pooled, map_feat], dim=-1)

        return self.classifier(combined)


class SinusoidalPositionalEncoding(nn.Module):
    def __init__(self, d_model: int, max_len: int):
        super().__init__()
        pe = torch.zeros(max_len, d_model)
        position = torch.arange(0, max_len, dtype=torch.float).unsqueeze(1)
        div_term = torch.exp(
            torch.arange(0, d_model, 2).float() * (-math.log(10000.0) / d_model)
        )
        pe[:, 0::2] = torch.sin(position * div_term)
        pe[:, 1::2] = torch.cos(position * div_term)
        self.register_buffer("pe", pe.unsqueeze(0))

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        return x + self.pe[:, :x.size(1), :]

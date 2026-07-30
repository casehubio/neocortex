import torch
import pytest
from evaluation.strategy_classifier.model import StrategyClassifier
from evaluation.strategy_classifier.config import HyperParams


class TestStrategyClassifier:
    def _make_model(self):
        hp = HyperParams()
        return StrategyClassifier(
            f_temporal=101, f_map=4, num_classes=8, hp=hp
        )

    def test_forward_shape(self):
        model = self._make_model()
        temporal = torch.randn(4, 10, 101)
        map_feat = torch.randn(4, 4)
        logits = model(temporal, map_feat)
        assert logits.shape == (4, 8)

    def test_single_sample(self):
        model = self._make_model()
        temporal = torch.randn(1, 10, 101)
        map_feat = torch.randn(1, 4)
        logits = model(temporal, map_feat)
        assert logits.shape == (1, 8)

    def test_output_is_finite(self):
        model = self._make_model()
        temporal = torch.randn(2, 10, 101)
        map_feat = torch.randn(2, 4)
        logits = model(temporal, map_feat)
        assert torch.isfinite(logits).all()

    def test_padding_mask_effect(self):
        model = self._make_model()
        model.eval()
        temporal = torch.randn(1, 10, 101)
        temporal[0, 4:, :] = 0.0
        map_feat = torch.randn(1, 4)
        logits_partial = model(temporal, map_feat)

        temporal2 = torch.randn(1, 10, 101)
        map_feat2 = map_feat.clone()
        logits_full = model(temporal2, map_feat2)

        assert not torch.allclose(logits_partial, logits_full)

    def test_parameter_count(self):
        model = self._make_model()
        n_params = sum(p.numel() for p in model.parameters())
        assert n_params < 1_000_000

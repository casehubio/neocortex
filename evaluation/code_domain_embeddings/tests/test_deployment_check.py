"""Tests for deployment feasibility checking."""

import pytest

from evaluation.code_domain_embeddings.deployment_check import (
    check_input_tensor_names,
    assess_onnx_jvm_compatibility,
    format_feasibility_table,
)


BERT_CONVENTION = ["input_ids", "attention_mask", "token_type_ids"]
ORIGINAL_BERT = ["input_ids", "input_mask", "segment_ids"]
MINIMAL = ["input_ids", "attention_mask"]


class TestCheckInputTensorNames:
    def test_hf_convention_compatible(self):
        result = check_input_tensor_names(BERT_CONVENTION)
        assert result["compatible"] is True
        assert result["convention"] == "huggingface"

    def test_original_bert_needs_fix(self):
        result = check_input_tensor_names(ORIGINAL_BERT)
        assert result["compatible"] is False
        assert result["convention"] == "original_bert"
        assert "#51" in result["blocker"]

    def test_minimal_inputs_compatible(self):
        result = check_input_tensor_names(MINIMAL)
        assert result["compatible"] is True


class TestAssessOnnxJvmCompatibility:
    def test_compatible_model(self):
        result = assess_onnx_jvm_compatibility(
            input_names=BERT_CONVENTION,
            opset_version=14,
            custom_ops=[],
        )
        assert result["jvm_ready"] is True
        assert len(result["blockers"]) == 0

    def test_old_opset_warning(self):
        result = assess_onnx_jvm_compatibility(
            input_names=BERT_CONVENTION,
            opset_version=10,
            custom_ops=[],
        )
        assert any("opset" in w.lower() for w in result["warnings"])

    def test_custom_ops_blocker(self):
        result = assess_onnx_jvm_compatibility(
            input_names=BERT_CONVENTION,
            opset_version=14,
            custom_ops=["CustomAttention"],
        )
        assert result["jvm_ready"] is False
        assert any("CustomAttention" in b for b in result["blockers"])

    def test_original_bert_names_blocker(self):
        result = assess_onnx_jvm_compatibility(
            input_names=ORIGINAL_BERT,
            opset_version=14,
            custom_ops=[],
        )
        assert result["jvm_ready"] is False


class TestFormatFeasibilityTable:
    def test_produces_markdown_table(self):
        data = {
            "CodeBERT": {
                "model_size_mb": 478,
                "latency_single_ms": 45,
                "latency_batch20_ms": 320,
                "jvm_compatible": True,
                "integration_path": "SeparateModelEmbedder",
                "blockers": [],
            }
        }
        table = format_feasibility_table(data)
        assert "CodeBERT" in table
        assert "478" in table

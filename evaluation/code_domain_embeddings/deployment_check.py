"""Layer 4: Deployment feasibility — ONNX export, size, latency, JVM compatibility."""

import json
import sys
import tempfile
import time
from pathlib import Path

from .config import MODEL_REGISTRY, results_dir

MIN_OPSET_VERSION = 11


def check_input_tensor_names(input_names: list[str]) -> dict:
    """Check whether ONNX input tensor names are compatible with OnnxInferenceModel."""
    hf_names = {"input_ids", "attention_mask", "token_type_ids"}
    original_bert_names = {"input_ids", "input_mask", "segment_ids"}

    name_set = set(input_names)
    if name_set.issubset(hf_names):
        return {"compatible": True, "convention": "huggingface", "blocker": None}
    if "input_mask" in name_set or "segment_ids" in name_set:
        return {
            "compatible": False,
            "convention": "original_bert",
            "blocker": "Uses original BERT input names (input_mask/segment_ids). "
                       "Requires #51 fix in OnnxInferenceModel.",
        }
    return {"compatible": True, "convention": "unknown", "blocker": None}


def assess_onnx_jvm_compatibility(
    input_names: list[str],
    opset_version: int,
    custom_ops: list[str],
) -> dict:
    """Assess overall ONNX Runtime JVM compatibility."""
    blockers = []
    warnings = []

    tensor_check = check_input_tensor_names(input_names)
    if not tensor_check["compatible"]:
        blockers.append(tensor_check["blocker"])

    if custom_ops:
        for op in custom_ops:
            blockers.append(f"Custom operator '{op}' may not be supported in ONNX Runtime JVM")

    if opset_version < MIN_OPSET_VERSION:
        warnings.append(f"Opset version {opset_version} is below minimum {MIN_OPSET_VERSION}")

    return {
        "jvm_ready": len(blockers) == 0,
        "blockers": blockers,
        "warnings": warnings,
        "input_convention": tensor_check["convention"],
        "opset_version": opset_version,
    }


def format_feasibility_table(data: dict) -> str:
    """Format feasibility data as a markdown table."""
    header = "| Model | Size (MB) | Latency 1x (ms) | Latency 20x (ms) | JVM Ready | Integration | Blockers |"
    separator = "|---|---|---|---|---|---|---|"
    rows = [header, separator]

    for name, info in data.items():
        blockers_str = ", ".join(info.get("blockers", [])) or "none"
        rows.append(
            f"| {name} "
            f"| {info.get('model_size_mb', '?')} "
            f"| {info.get('latency_single_ms', '?')} "
            f"| {info.get('latency_batch20_ms', '?')} "
            f"| {'yes' if info.get('jvm_compatible') else 'no'} "
            f"| {info.get('integration_path', '?')} "
            f"| {blockers_str} |"
        )

    return "\n".join(rows)


def export_and_check(hf_id: str, model_name: str) -> dict:
    """Export a model to ONNX and check compatibility."""
    import onnx
    import torch
    from transformers import AutoModel, AutoTokenizer

    print(f"  Loading model...")
    tokenizer = AutoTokenizer.from_pretrained(hf_id, trust_remote_code=True)
    model = AutoModel.from_pretrained(hf_id, trust_remote_code=True)
    model.eval()

    with tempfile.TemporaryDirectory() as tmpdir:
        onnx_path = Path(tmpdir) / "model.onnx"

        print(f"  Exporting to ONNX...")
        dummy = tokenizer("test input", return_tensors="pt")
        input_names = list(dummy.keys())

        try:
            torch.onnx.export(
                model,
                tuple(dummy.values()),
                str(onnx_path),
                input_names=input_names,
                output_names=["output"],
                dynamic_axes={name: {0: "batch", 1: "seq"} for name in input_names},
                opset_version=14,
            )
        except Exception as e:
            return {"export_success": False, "error": str(e)}

        onnx_model = onnx.load(str(onnx_path))
        opset = onnx_model.opset_import[0].version
        size_mb = round(onnx_path.stat().st_size / (1024 * 1024), 1)

        custom_ops = [
            node.op_type for node in onnx_model.graph.node
            if node.domain and node.domain != ""
        ]

        compat = assess_onnx_jvm_compatibility(input_names, opset, custom_ops)

        # Latency measurement
        print(f"  Measuring latency...")
        single_text = "ConcurrentHashMap thread-safe map"
        batch_texts = [single_text] * 20

        times_single = []
        for _ in range(5):
            start = time.perf_counter()
            with torch.no_grad():
                inputs = tokenizer(single_text, return_tensors="pt", padding=True, truncation=True)
                model(**inputs)
            times_single.append((time.perf_counter() - start) * 1000)

        times_batch = []
        for _ in range(3):
            start = time.perf_counter()
            with torch.no_grad():
                inputs = tokenizer(batch_texts, return_tensors="pt", padding=True, truncation=True)
                model(**inputs)
            times_batch.append((time.perf_counter() - start) * 1000)

        return {
            "export_success": True,
            "model_size_mb": size_mb,
            "opset_version": opset,
            "input_names": input_names,
            "custom_ops": custom_ops,
            "jvm_compatible": compat["jvm_ready"],
            "blockers": compat["blockers"],
            "warnings": compat["warnings"],
            "input_convention": compat["input_convention"],
            "latency_single_ms": round(min(times_single), 1),
            "latency_batch20_ms": round(min(times_batch), 1),
            "integration_path": "SeparateModelEmbedder (#61)",
        }


def run_deployment_check(model_names: list[str] | None = None) -> dict:
    """Run deployment feasibility check on specified models."""
    if model_names is None:
        model_names = [n for n, e in MODEL_REGISTRY.items()
                       if e["role"] == "candidate"]

    results = {}
    for name in model_names:
        entry = MODEL_REGISTRY[name]
        hf_id = entry["hf_id"]
        print(f"\nChecking {name} ({hf_id})...")
        result = export_and_check(hf_id, name)
        results[name] = result

        if result.get("export_success"):
            print(f"  Size: {result['model_size_mb']}MB | "
                  f"Latency: {result['latency_single_ms']}ms (1x), "
                  f"{result['latency_batch20_ms']}ms (20x) | "
                  f"JVM: {'ready' if result['jvm_compatible'] else 'BLOCKED'}")
        else:
            print(f"  EXPORT FAILED: {result.get('error')}")

    return results


def save_results(results: dict) -> Path:
    """Save results to JSON."""
    out = results_dir()
    out.mkdir(parents=True, exist_ok=True)
    path = out / "deployment_feasibility.json"
    path.write_text(json.dumps(results, indent=2, ensure_ascii=False), encoding="utf-8")
    return path


if __name__ == "__main__":
    models = sys.argv[1:] if len(sys.argv) > 1 else None
    results = run_deployment_check(models)
    path = save_results(results)
    print(f"\nResults saved to {path}")
    print(f"\n{format_feasibility_table(results)}")

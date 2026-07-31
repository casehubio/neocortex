"""Run the full pipeline: load data → train → calibrate → export → evaluate.

Usage: python3 -m evaluation.strategy_classifier.run_pipeline [--data synthetic]
"""
import sys
import torch
import numpy as np
from pathlib import Path
from evaluation.strategy_classifier.config import (
    MATCHUPS, HyperParams, Paths, archetypes_for_matchup,
)
from evaluation.strategy_classifier.model import StrategyClassifier
from evaluation.strategy_classifier.train import (
    train_model, evaluate, find_optimal_temperature, bake_temperature,
    compute_class_weights,
)
from evaluation.strategy_classifier.export_onnx import export_to_onnx, write_manifest
from evaluation.strategy_classifier.evaluate import (
    compute_metrics, benchmark_latency,
)
from evaluation.strategy_classifier.generate_synthetic import load_split
from evaluation.strategy_classifier.dataset import create_dataloaders
from evaluation.strategy_classifier.feature_engineering import F_MAP


def run(data_source: str = "synthetic", hp: HyperParams = HyperParams(), paths: Paths = Paths()):
    data_dir = paths.data / data_source
    output_dir = paths.output
    output_dir.mkdir(parents=True, exist_ok=True)
    models_dir = paths.models
    models_dir.mkdir(parents=True, exist_ok=True)

    for matchup in MATCHUPS:
        print(f"\n{'='*60}")
        print(f"  {matchup}")
        print(f"{'='*60}")

        archetypes = archetypes_for_matchup(matchup)
        num_classes = len(archetypes)

        matchup_dir = data_dir / matchup
        train_samples = load_split(matchup_dir / "train.npz")
        val_samples = load_split(matchup_dir / "val.npz")
        test_samples = load_split(matchup_dir / "test.npz")

        f_temporal = train_samples[0][0].shape[1]
        print(f"  Features: {f_temporal} temporal, {F_MAP} map, {num_classes} classes")
        print(f"  Samples: {len(train_samples)} train, {len(val_samples)} val, {len(test_samples)} test")

        train_loader, val_loader, test_loader = create_dataloaders(
            train_samples, val_samples, test_samples, hp
        )

        model = StrategyClassifier(
            f_temporal=f_temporal, f_map=F_MAP, num_classes=num_classes, hp=hp
        )
        n_params = sum(p.numel() for p in model.parameters())
        print(f"  Parameters: {n_params:,}")

        train_labels = np.array([s[2] for s in train_samples])
        weights = compute_class_weights(train_labels, num_classes)
        use_weights = any(w > 2.0 for w in weights)
        if use_weights:
            print(f"  Class weights: {dict(zip(archetypes, weights.numpy().round(2)))}")
        else:
            print(f"  Class balance: OK (no weighting needed)")
            weights = None

        print(f"\n  Training...")
        history = train_model(model, train_loader, val_loader, hp, class_weights=weights)

        print(f"\n  Calibrating temperature...")
        _, _, val_logits, val_labels = evaluate(model, val_loader)
        temperature = find_optimal_temperature(val_logits, val_labels)
        print(f"  Optimal temperature: {temperature:.3f}")
        bake_temperature(model, temperature)

        print(f"\n  Evaluating on test set...")
        _, test_acc, test_logits, test_labels = evaluate(model, test_loader)
        metrics = compute_metrics(test_logits, test_labels, archetypes)
        print(f"  Top-1 accuracy: {metrics['top1_accuracy']:.4f}")
        print(f"  Top-3 accuracy: {metrics['top3_accuracy']:.4f}")
        for name, info in metrics["per_class"].items():
            if info["accuracy"] is not None:
                print(f"    {name}: {info['accuracy']:.4f} ({info['count']} samples)")

        print(f"\n  Exporting to ONNX...")
        onnx_path = export_to_onnx(
            model, f_temporal=f_temporal, f_map=F_MAP,
            matchup=matchup, output_dir=output_dir,
            max_windows=hp.max_windows,
        )
        print(f"  Exported: {onnx_path} ({onnx_path.stat().st_size / 1024:.1f} KB)")

        print(f"\n  Benchmarking latency...")
        latency = benchmark_latency(onnx_path, f_temporal, F_MAP, hp.max_windows)
        print(f"  p50: {latency['p50_ms']:.2f}ms  p95: {latency['p95_ms']:.2f}ms  p99: {latency['p99_ms']:.2f}ms")

        write_manifest(
            output_dir, matchup, hp,
            f_temporal=f_temporal, f_map=F_MAP, num_classes=num_classes,
            accuracy=metrics, temperature=temperature,
        )

        checkpoint_path = models_dir / f"{matchup}.pt"
        torch.save(model.state_dict(), checkpoint_path)

    print(f"\n{'='*60}")
    print(f"  Pipeline complete. Output: {output_dir}")
    print(f"{'='*60}")


if __name__ == "__main__":
    data_source = "synthetic"
    if len(sys.argv) > 1 and sys.argv[1] == "--data":
        data_source = sys.argv[2]
    run(data_source=data_source)

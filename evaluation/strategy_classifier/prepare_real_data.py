"""Prepare real SC2EGSet data for training: extract → label → save.

Extracts replays from SC2EGSet ZIPs, derives build orders for labelling,
runs hybrid labelling (rule-based + LLM), builds windowed samples, and
saves as .npz files compatible with run_pipeline.py.

Usage: python3 -m evaluation.strategy_classifier.prepare_real_data \
         --zips <path1.zip> [<path2.zip> ...] \
         [--llm]  # enable LLM labelling for ambiguous replays
"""
import json
import sys
import numpy as np
from pathlib import Path
from typing import Dict, List, Optional, Tuple
from evaluation.strategy_classifier.config import (
    MATCHUPS, HyperParams, Paths, archetypes_for_matchup,
)
from evaluation.strategy_classifier.sc2egset_extractor import (
    extract_from_zip, ReplayData, LOOPS_PER_SECOND, BUILDING_IDX, BUILDINGS,
    build_samples_from_replays,
)
from evaluation.strategy_classifier.feature_engineering import (
    build_temporal_features, build_map_tensor, F_MAP,
)
from evaluation.strategy_classifier.fog_of_war import generate_scouting_mask
from evaluation.strategy_classifier.dataset import per_replay_split
from evaluation.strategy_classifier.labelling.rules import rule_based_label
from evaluation.strategy_classifier.labelling.label_pipeline import label_replay

MINUTES = [2, 3, 4, 5]


def extract_build_order(game_json: dict, player_id: int) -> List[Dict]:
    """Extract a player's build order from tracker events."""
    build_order = []
    for e in game_json.get("trackerEvents", []):
        etype = e.get("evtTypeName", "")
        pid = e.get("controlPlayerId", 0)
        if pid != player_id:
            continue

        loop = e.get("loop", 0)
        minute = loop / LOOPS_PER_SECOND / 60.0

        if etype == "UnitInit":
            unit_name = e.get("unitTypeName", "")
            if unit_name in BUILDING_IDX:
                build_order.append({
                    "type": "building", "name": unit_name, "minute": minute
                })

        elif etype == "UnitBorn":
            unit_name = e.get("unitTypeName", "")
            if unit_name not in BUILDING_IDX:
                build_order.append({
                    "type": "unit", "name": unit_name, "minute": minute
                })

    return sorted(build_order, key=lambda x: x["minute"])


def extract_build_orders_from_zip(zip_path: Path) -> List[Tuple[dict, Dict]]:
    """Extract raw game JSONs + metadata from a ZIP for labelling."""
    import zipfile, io
    outer = zipfile.ZipFile(zip_path)

    data_zip_name = None
    for name in outer.namelist():
        if name.endswith("_data.zip"):
            data_zip_name = name
            break

    if data_zip_name:
        inner_bytes = outer.read(data_zip_name)
        inner = zipfile.ZipFile(io.BytesIO(inner_bytes))
    else:
        inner = outer

    games = []
    for name in inner.namelist():
        if not name.endswith(".SC2Replay.json"):
            continue
        try:
            game = json.loads(inner.read(name))
            toon_map = game.get("ToonPlayerDescMap", {})
            if len(toon_map) != 2:
                continue

            players = {}
            for toon, desc in toon_map.items():
                pid = desc["playerID"]
                race_raw = desc.get("race", "")
                race = {"Terr": "Terran", "Prot": "Protoss", "Zerg": "Zerg"}.get(race_raw, race_raw)
                players[pid] = {"race": race, "result": desc.get("result", "")}

            if 1 in players and 2 in players:
                games.append((game, players))
        except Exception:
            continue

    return games


def label_all_replays(
    zip_paths: List[Path], use_llm: bool = False,
) -> Dict[str, List[Tuple[List[Dict], str, int]]]:
    """Label all replays across ZIPs.

    Returns dict[matchup] -> list of (build_order_for_labeller, label, opponent_player_id_in_game)
    Each game produces TWO entries (one per player's perspective as the opponent).
    """
    llm_client = None
    if use_llm:
        try:
            from evaluation.strategy_classifier.labelling.llm_labeller import create_client
            llm_client = create_client()
            print("LLM labelling enabled")
        except Exception as e:
            print(f"Warning: LLM labelling requested but client failed: {e}")
            print("Falling back to rule-based only")

    matchup_map = {
        ("Terran", "Terran"): "vs_terran",
        ("Terran", "Zerg"): "vs_zerg",
        ("Terran", "Protoss"): "vs_protoss",
        ("Zerg", "Terran"): "vs_terran",
        ("Zerg", "Zerg"): "vs_zerg",
        ("Zerg", "Protoss"): "vs_protoss",
        ("Protoss", "Terran"): "vs_terran",
        ("Protoss", "Zerg"): "vs_zerg",
        ("Protoss", "Protoss"): "vs_protoss",
    }

    labelled: Dict[str, list] = {m: [] for m in MATCHUPS}
    stats = {"rule": 0, "llm": 0, "excluded": 0, "total": 0}

    for zip_path in zip_paths:
        print(f"\nLabelling {zip_path.name}...")
        games = extract_build_orders_from_zip(zip_path)

        for game_json, players in games:
            for observer_id, opponent_id in [(1, 2), (2, 1)]:
                observer_race = players[observer_id]["race"]
                opponent_race = players[opponent_id]["race"]
                matchup_key = (observer_race, opponent_race)
                if matchup_key not in matchup_map:
                    continue
                matchup = matchup_map[matchup_key]

                opponent_build = extract_build_order(game_json, opponent_id)
                label, source = label_replay(opponent_build, opponent_race, llm_client)

                stats["total"] += 1
                stats[source] += 1

                if label is not None:
                    labelled[matchup].append((game_json, label, observer_id, opponent_id))

    print(f"\nLabelling stats: {stats['total']} total, "
          f"{stats['rule']} rule-based, {stats['llm']} LLM, "
          f"{stats['excluded']} excluded")
    for m in MATCHUPS:
        print(f"  {m}: {len(labelled[m])} labelled replays")

    return labelled


def build_and_save_dataset(
    labelled: Dict[str, list],
    zip_paths: List[Path],
    hp: HyperParams = HyperParams(),
    paths: Paths = Paths(),
    seed: int = 42,
):
    """Build windowed samples from labelled replays and save as .npz."""
    rng = np.random.default_rng(seed)
    output = paths.data / "sc2egset"
    output.mkdir(parents=True, exist_ok=True)

    all_replays = {}
    for zip_path in zip_paths:
        results = extract_from_zip(zip_path, hp)
        for matchup, replays in results.items():
            if matchup not in all_replays:
                all_replays[matchup] = []
            all_replays[matchup].extend(replays)

    for matchup in MATCHUPS:
        archetypes = archetypes_for_matchup(matchup)
        arch_to_idx = {a: i for i, a in enumerate(archetypes)}

        labelled_games = labelled.get(matchup, [])
        if not labelled_games:
            print(f"  {matchup}: no labelled replays, skipping")
            continue

        replay_features = all_replays.get(matchup, [])
        if not replay_features:
            print(f"  {matchup}: no extracted features, skipping")
            continue

        samples = []
        replay_ids = []
        replay_labels = []
        replay_id = 0

        for game_json, label, observer_id, opponent_id in labelled_games:
            if label not in arch_to_idx:
                continue
            label_idx = arch_to_idx[label]

            header = game_json.get("header", {})
            total_loops = header.get("elapsedGameLoops", 0)
            duration = min(int(total_loops / LOOPS_PER_SECOND), 600)

            from evaluation.strategy_classifier.sc2egset_extractor import extract_replay
            replay = extract_replay(game_json)
            if replay is None:
                continue

            if observer_id == 1:
                own_feat = replay.player1_features
                opp_feat = replay.player2_features
            else:
                own_feat = replay.player2_features
                opp_feat = replay.player1_features

            map_tensor = build_map_tensor(replay.map_name)
            mask = generate_scouting_mask(duration, rng)

            has_any = False
            for minute in MINUTES:
                if minute * 60 > duration:
                    continue
                temporal = build_temporal_features(own_feat, opp_feat, mask, minute, hp)
                samples.append((temporal, map_tensor, label_idx))
                has_any = True

            if has_any:
                replay_ids.append(replay_id)
                replay_labels.append(label_idx)
                replay_id += 1

        if not samples:
            print(f"  {matchup}: no valid samples after processing")
            continue

        train_ids, val_ids, test_ids = per_replay_split(replay_ids, replay_labels, seed=seed)
        train_set = set(train_ids)
        val_set = set(val_ids)

        train_samples, val_samples, test_samples = [], [], []
        samples_per_replay = {}
        idx = 0
        for rid in replay_ids:
            count = 0
            while idx < len(samples) and count < len(MINUTES):
                if rid in train_set:
                    train_samples.append(samples[idx])
                elif rid in val_set:
                    val_samples.append(samples[idx])
                else:
                    test_samples.append(samples[idx])
                idx += 1
                count += 1

        matchup_dir = output / matchup
        matchup_dir.mkdir(parents=True, exist_ok=True)
        _save_split(train_samples, matchup_dir / "train.npz")
        _save_split(val_samples, matchup_dir / "val.npz")
        _save_split(test_samples, matchup_dir / "test.npz")

        print(f"  {matchup}: {len(train_samples)} train, {len(val_samples)} val, "
              f"{len(test_samples)} test ({len(archetypes)} classes, "
              f"{len(replay_ids)} replays)")


def _save_split(samples, path: Path):
    if not samples:
        return
    temporal = np.array([s[0] for s in samples])
    map_feat = np.array([s[1] for s in samples])
    labels = np.array([s[2] for s in samples])
    np.savez_compressed(path, temporal=temporal, map_features=map_feat, labels=labels)


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--zips", nargs="+", required=True, type=Path)
    parser.add_argument("--llm", action="store_true", help="Enable LLM labelling for ambiguous replays")
    args = parser.parse_args()

    print("Step 1: Labelling replays...")
    labelled = label_all_replays(args.zips, use_llm=args.llm)

    print("\nStep 2: Building windowed samples and saving...")
    build_and_save_dataset(labelled, args.zips)

    print("\nDone. Run the training pipeline with:")
    print("  python3 -m evaluation.strategy_classifier.run_pipeline --data sc2egset")

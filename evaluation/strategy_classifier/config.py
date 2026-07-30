from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, List

MATCHUPS = ["vs_terran", "vs_zerg", "vs_protoss"]

ARCHETYPES: Dict[str, List[str]] = {
    "vs_terran": [
        "RUSH", "PROXY", "BANSHEE_HARASS", "AIR_SUPERIORITY",
        "MECH_PUSH", "BIO_TIMING", "MACRO_ECONOMY", "TECH_RUSH",
    ],
    "vs_zerg": [
        "RUSH", "ROACH_RUSH", "LING_BANE", "MUTA_HARASS",
        "HYDRA_PUSH", "MACRO_ECONOMY", "TECH_RUSH",
    ],
    "vs_protoss": [
        "RUSH", "PROXY", "CANNON_RUSH", "DT_RUSH",
        "BLINK_STALKER", "COLOSSUS_PUSH", "AIR_SUPERIORITY",
        "MACRO_ECONOMY", "TECH_RUSH",
    ],
}


def archetypes_for_matchup(matchup: str) -> List[str]:
    if matchup not in ARCHETYPES:
        raise ValueError(f"Unknown matchup: {matchup}. Expected one of {MATCHUPS}")
    return ARCHETYPES[matchup]


def all_archetype_names() -> List[str]:
    seen = []
    for archetypes in ARCHETYPES.values():
        for a in archetypes:
            if a not in seen:
                seen.append(a)
    return seen


@dataclass(frozen=True)
class HyperParams:
    lr: float = 1e-3
    batch_size: int = 128
    max_epochs: int = 50
    patience: int = 10
    focal_gamma: float = 2.0
    dropout: float = 0.3
    window_seconds: int = 30
    max_windows: int = 10
    conv_channels: list = field(default_factory=lambda: [64, 128])
    dense_hidden: int = 64
    seed: int = 42


@dataclass(frozen=True)
class Paths:
    base: Path = Path("evaluation/strategy_classifier")
    data: Path = Path("evaluation/strategy_classifier/data")
    models: Path = Path("evaluation/strategy_classifier/models")
    output: Path = Path("evaluation/strategy_classifier/output")

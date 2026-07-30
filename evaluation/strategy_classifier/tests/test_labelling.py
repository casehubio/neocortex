import pytest
from evaluation.strategy_classifier.labelling.rules import rule_based_label


class TestRuleBasedLabelling:
    def test_early_pool_is_zerg_rush(self):
        build = [
            {"type": "building", "name": "SpawningPool", "minute": 1.2},
            {"type": "unit", "name": "Zergling", "minute": 1.8},
        ]
        label = rule_based_label(build, opponent_race="Zerg")
        assert label == "RUSH"

    def test_early_forge_cannon_is_cannon_rush(self):
        build = [
            {"type": "building", "name": "Forge", "minute": 1.5},
            {"type": "building", "name": "PhotonCannon", "minute": 2.5},
        ]
        label = rule_based_label(build, opponent_race="Protoss")
        assert label == "CANNON_RUSH"

    def test_triple_cc_is_macro(self):
        build = [
            {"type": "building", "name": "CommandCenter", "minute": 0.0},
            {"type": "building", "name": "CommandCenter", "minute": 2.0},
            {"type": "building", "name": "CommandCenter", "minute": 4.0},
        ]
        label = rule_based_label(build, opponent_race="Terran")
        assert label == "MACRO_ECONOMY"

    def test_ambiguous_returns_none(self):
        build = [
            {"type": "building", "name": "Barracks", "minute": 1.5},
            {"type": "building", "name": "Factory", "minute": 3.0},
        ]
        label = rule_based_label(build, opponent_race="Terran")
        assert label is None

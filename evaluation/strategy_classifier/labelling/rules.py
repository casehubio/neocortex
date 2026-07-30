from typing import List, Dict, Optional


def rule_based_label(build_order: List[Dict], opponent_race: str) -> Optional[str]:
    buildings = [e for e in build_order if e["type"] == "building"]
    units = [e for e in build_order if e["type"] == "unit"]

    building_names = [b["name"] for b in buildings]
    early_buildings = [b for b in buildings if b["minute"] < 3.0]
    early_building_names = [b["name"] for b in early_buildings]

    if opponent_race == "Zerg":
        pools = [b for b in buildings if b["name"] == "SpawningPool" and b["minute"] < 1.5]
        if pools:
            return "RUSH"

        bane_nests = [b for b in buildings if b["name"] == "BanelingNest"]
        if bane_nests and any(b["minute"] < 4.0 for b in bane_nests):
            return "LING_BANE"

        spires = [b for b in buildings if b["name"] == "Spire"]
        if spires and any(b["minute"] < 6.0 for b in spires):
            return "MUTA_HARASS"

        hydra_dens = [b for b in buildings if b["name"] == "HydraliskDen"]
        if hydra_dens:
            return "HYDRA_PUSH"

        roach_warrens = [b for b in buildings if b["name"] == "RoachWarren" and b["minute"] < 4.0]
        if roach_warrens:
            return "ROACH_RUSH"

    if opponent_race == "Protoss":
        forges = [b for b in buildings if b["name"] == "Forge" and b["minute"] < 2.5]
        cannons = [b for b in buildings if b["name"] == "PhotonCannon" and b["minute"] < 4.0]
        if forges and cannons:
            return "CANNON_RUSH"

        dark_shrines = [b for b in buildings if b["name"] == "DarkShrine"]
        if dark_shrines and any(b["minute"] < 5.0 for b in dark_shrines):
            return "DT_RUSH"

        twilights = [b for b in buildings if b["name"] == "TwilightCouncil" and b["minute"] < 5.0]
        if twilights:
            return "BLINK_STALKER"

        robo_bays = [b for b in buildings if b["name"] == "RoboticsBay"]
        if robo_bays:
            return "COLOSSUS_PUSH"

    if opponent_race == "Terran":
        barracks = [b for b in early_buildings if b["name"] == "Barracks"]
        ccs = [b for b in buildings if b["name"] == "CommandCenter"]
        has_tech = any(b["name"] in ("Factory", "Starport") for b in buildings)
        if len(barracks) >= 1 and len(ccs) <= 1 and not has_tech:
            return "RUSH"

        starports = [b for b in buildings if b["name"] == "Starport"]
        tech_labs = [b for b in buildings if b["name"] == "TechLab"]
        if starports and tech_labs and not barracks:
            return "BANSHEE_HARASS"

        factories = [b for b in buildings if b["name"] == "Factory"]
        if len(factories) >= 2:
            return "MECH_PUSH"

        if len(barracks) >= 3:
            return "BIO_TIMING"

    expansion_names = {"CommandCenter", "Hatchery", "Nexus"}
    expansions = [b for b in buildings if b["name"] in expansion_names]
    if len(expansions) >= 3 and all(e["minute"] < 5.0 for e in expansions[:3]):
        return "MACRO_ECONOMY"

    return None

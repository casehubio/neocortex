from typing import List, Dict, Optional


def _first_time(buildings: List[Dict], name: str) -> float:
    for b in buildings:
        if b["name"] == name:
            return b["minute"]
    return 999.0


def _count_before(buildings: List[Dict], name: str, before_min: float) -> int:
    return sum(1 for b in buildings if b["name"] == name and b["minute"] < before_min)


def _order_before(buildings: List[Dict], first_name: str, second_name: str) -> bool:
    """True if first_name appears before second_name in the build."""
    t1 = _first_time(buildings, first_name)
    t2 = _first_time(buildings, second_name)
    return t1 < t2


def rule_based_label(build_order: List[Dict], opponent_race: str) -> Optional[str]:
    buildings = [e for e in build_order if e["type"] == "building"]

    if opponent_race == "Zerg":
        return _label_zerg(buildings)
    elif opponent_race == "Protoss":
        return _label_protoss(buildings)
    elif opponent_race == "Terran":
        return _label_terran(buildings)
    return None


def _label_zerg(buildings: List[Dict]) -> Optional[str]:
    pool_time = _first_time(buildings, "SpawningPool")
    hatch_times = sorted(b["minute"] for b in buildings if b["name"] == "Hatchery")
    second_hatch = hatch_times[1] if len(hatch_times) > 1 else 999.0

    # RUSH: pool-first (before 2nd hatchery) AND very early (< 1.0 min)
    if pool_time < 1.0 and pool_time < second_hatch:
        return "RUSH"

    bane_time = _first_time(buildings, "BanelingNest")
    roach_time = _first_time(buildings, "RoachWarren")
    spire_time = _first_time(buildings, "Spire")
    hydra_time = _first_time(buildings, "HydraliskDen")

    # ROACH_RUSH: RoachWarren before 5.5 min
    if roach_time < 5.5:
        return "ROACH_RUSH"

    # LING_BANE: BanelingNest before 7 min
    if bane_time < 7.0:
        return "LING_BANE"

    # MUTA_HARASS: Spire before 10 min
    if spire_time < 10.0:
        return "MUTA_HARASS"

    # HYDRA_PUSH: HydraliskDen present before 10 min
    if hydra_time < 10.0:
        return "HYDRA_PUSH"

    # MACRO_ECONOMY: 3+ hatches before 5 min, no early aggression tech
    if len(hatch_times) >= 3 and hatch_times[2] < 5.0:
        if bane_time > 7.0 and roach_time > 5.5:
            return "MACRO_ECONOMY"

    # TECH_RUSH: pool-first into fast tech (spire or hydra before hatch expansion)
    if pool_time < second_hatch and (spire_time < 12.0 or hydra_time < 12.0):
        return "TECH_RUSH"

    # Fallback: standard Zerg with some tech
    if bane_time < 999:
        return "LING_BANE"
    if roach_time < 999:
        return "ROACH_RUSH"
    if spire_time < 999:
        return "MUTA_HARASS"
    if hydra_time < 999:
        return "HYDRA_PUSH"

    # Default: macro if multiple hatches
    if len(hatch_times) >= 2:
        return "MACRO_ECONOMY"

    return None


def _label_terran(buildings: List[Dict]) -> Optional[str]:
    rax_time = _first_time(buildings, "Barracks")
    factory_time = _first_time(buildings, "Factory")
    starport_time = _first_time(buildings, "Starport")
    cc_times = sorted(b["minute"] for b in buildings if b["name"] == "CommandCenter")
    second_cc = cc_times[1] if len(cc_times) > 1 else 999.0
    armory_time = _first_time(buildings, "Armory")

    n_rax = sum(1 for b in buildings if b["name"] == "Barracks")
    n_factory = sum(1 for b in buildings if b["name"] == "Factory")
    has_starport_tl = any(b["name"] == "StarportTechLab" for b in buildings)
    has_rax_reactor = any(b["name"] == "BarracksReactor" for b in buildings)

    # RUSH: multiple barracks early, no expansion, no factory
    if n_rax >= 2 and _count_before(buildings, "Barracks", 3.0) >= 2 and factory_time > 4.0 and second_cc > 4.0:
        return "RUSH"

    # MACRO_ECONOMY: CC-first (2nd CC before factory)
    if second_cc < factory_time and second_cc < 3.0:
        return "MACRO_ECONOMY"

    # BANSHEE_HARASS: Starport + TechLab, starport before 4 min
    if starport_time < 4.0 and has_starport_tl and n_rax <= 2:
        return "BANSHEE_HARASS"

    # MECH_PUSH: Factory-heavy (2+ factories or armory before 10 min)
    if n_factory >= 2 or (armory_time < 10.0 and factory_time < 3.0):
        return "MECH_PUSH"

    # BIO_TIMING: 3+ barracks with reactor support
    if n_rax >= 3:
        return "BIO_TIMING"

    # AIR_SUPERIORITY: Starport-heavy without ground army
    if starport_time < 4.0 and n_rax <= 1 and n_factory <= 1:
        return "AIR_SUPERIORITY"

    # Standard 1-1-1: classify by what follows
    if factory_time < 3.0 and starport_time < 5.0:
        if has_starport_tl:
            return "BANSHEE_HARASS"
        if armory_time < 12.0:
            return "MECH_PUSH"
        return "BIO_TIMING"

    # TECH_RUSH: fast factory without expansion
    if factory_time < 2.5 and second_cc > 5.0:
        return "TECH_RUSH"

    # Default: bio if barracks exist
    if n_rax >= 1:
        return "BIO_TIMING"

    return None


def _label_protoss(buildings: List[Dict]) -> Optional[str]:
    gw_time = _first_time(buildings, "Gateway")
    cyber_time = _first_time(buildings, "CyberneticsCore")
    nexus_times = sorted(b["minute"] for b in buildings if b["name"] == "Nexus")
    second_nexus = nexus_times[1] if len(nexus_times) > 1 else 999.0
    forge_time = _first_time(buildings, "Forge")
    cannon_time = _first_time(buildings, "PhotonCannon")
    twilight_time = _first_time(buildings, "TwilightCouncil")
    robo_time = _first_time(buildings, "RoboticsFacility")
    robo_bay_time = _first_time(buildings, "RoboticsBay")
    stargate_time = _first_time(buildings, "Stargate")
    dark_shrine_time = _first_time(buildings, "DarkShrine")
    templar_archive_time = _first_time(buildings, "TemplarArchive")

    n_gateways = sum(1 for b in buildings if b["name"] in ("Gateway", "WarpGate"))

    # CANNON_RUSH: Forge before CyberneticsCore + early cannons
    if forge_time < cyber_time and cannon_time < 5.0:
        return "CANNON_RUSH"

    # RUSH: multiple gateways early, no expansion
    if n_gateways >= 3 and _count_before(buildings, "Gateway", 3.0) >= 2 and second_nexus > 5.0:
        return "RUSH"

    # PROXY: gateway before pylon timing would suggest proxy (very early)
    if gw_time < 0.7:
        return "PROXY"

    # MACRO_ECONOMY: Nexus-first (2nd Nexus before CyberneticsCore)
    if second_nexus < cyber_time and second_nexus < 2.5:
        return "MACRO_ECONOMY"

    # DT_RUSH: DarkShrine before 7 min
    if dark_shrine_time < 7.0:
        return "DT_RUSH"

    # BLINK_STALKER: TwilightCouncil before 5 min
    if twilight_time < 5.0:
        return "BLINK_STALKER"

    # AIR_SUPERIORITY: Stargate-first (before Robo and Twilight)
    if stargate_time < robo_time and stargate_time < twilight_time and stargate_time < 5.0:
        return "AIR_SUPERIORITY"

    # COLOSSUS_PUSH: RoboticsBay present
    if robo_bay_time < 15.0:
        return "COLOSSUS_PUSH"

    # TECH_RUSH: fast tech path without expansion
    if cyber_time < 2.0 and second_nexus > 4.0 and (twilight_time < 5.0 or stargate_time < 4.0 or robo_time < 4.0):
        return "TECH_RUSH"

    # Fallback classification by tech choice
    if twilight_time < robo_time and twilight_time < stargate_time:
        return "BLINK_STALKER"
    if robo_time < stargate_time:
        return "COLOSSUS_PUSH"
    if stargate_time < 999:
        return "AIR_SUPERIORITY"

    # Default: blink stalker for gateway-heavy
    if n_gateways >= 2:
        return "BLINK_STALKER"

    return None

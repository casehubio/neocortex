package io.casehub.neocortex.caps;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Detects competing drives via D1 activation overlap.
 * <p>
 * Two drives compete when they activate overlapping CAPS nodes — shared
 * activation pathways produce similar felt experience, causing the LLM
 * to conflate them. When competition is detected, a universal meta-
 * instruction helps the LLM attend to which quality of feeling is active.
 * <p>
 * D1 mappings sourced from research doc §2.3 (literature-backed).
 */
public final class DriveCompetitionDetector {

    private DriveCompetitionDetector() {}

    public static final String DIFFERENTIATION_INSTRUCTION =
            "Each of these pulls is a distinct felt state — a different quality of warmth, "
            + "tension, or urgency. When more than one activates in the same moment, attend to "
            + "which quality is at the front. The same situation can serve different pulls "
            + "differently. Notice which feeling is shaping your response, not just what you do.";

    public static final Map<String, List<String>> DRIVE_CAPS_MAPPINGS = Map.ofEntries(
            Map.entry("gallantry",         List.of("other_safety", "acceptance", "approach")),
            Map.entry("proving-worth",     List.of("mastery", "competence_recognition", "self_efficacy")),
            Map.entry("protection",        List.of("other_safety", "physical_threat", "approach")),
            Map.entry("scheming",          List.of("dominance", "distrust", "self_direct")),
            Map.entry("gloating",          List.of("dominance", "BAS_activation")),
            Map.entry("self-preservation", List.of("threat_sensitivity", "escape_assessment", "FFFS_activation")),
            Map.entry("recognition",       List.of("competence_recognition", "self_worth", "BAS_activation")),
            Map.entry("curiosity",         List.of("explore", "BAS_activation")),
            Map.entry("social-harmony",    List.of("other_safety", "acceptance", "proximity_seek")),
            Map.entry("adventure",         List.of("explore", "BAS_activation", "approach")),
            Map.entry("greed",             List.of("BAS_activation", "approach", "persist")),
            Map.entry("loyalty",           List.of("other_reliability", "proximity_seek", "trust")),
            Map.entry("suspicion",         List.of("threat_sensitivity", "distrust", "BIS_activation")),
            Map.entry("dominance",         List.of("dominance", "self_direct", "assert"))
    );

    /**
     * Returns true if any pair of the given drive types activates
     * overlapping CAPS nodes — indicating potential drive confusion.
     */
    public static boolean hasCompetingDrives(List<String> driveTypes) {
        for (int i = 0; i < driveTypes.size(); i++) {
            var a = Set.copyOf(DRIVE_CAPS_MAPPINGS.getOrDefault(driveTypes.get(i), List.of()));
            if (a.isEmpty()) continue;
            for (int j = i + 1; j < driveTypes.size(); j++) {
                var b = Set.copyOf(DRIVE_CAPS_MAPPINGS.getOrDefault(driveTypes.get(j), List.of()));
                if (!Collections.disjoint(a, b)) return true;
            }
        }
        return false;
    }
}

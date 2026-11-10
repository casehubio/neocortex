package io.casehub.neocortex.cognitive.observability;

import io.casehub.neocortex.cognitive.CognitiveCapacityConfig;
import io.casehub.neocortex.cognitive.index.CognitiveDefaults;

import java.util.ArrayList;
import java.util.List;

public record CapacityModulationReport(
        String agentId,
        CognitiveCapacityConfig capacity,
        List<SiteModulation> sites) {

    public record SiteModulation(
            String site,
            String capacityDimension,
            double capacityValue,
            String effect,
            String citation) {}

    public static CapacityModulationReport from(CognitiveDefaults defaults) {
        var capacity          = defaults.capacityConfig();
        var effectiveCapacity = capacity != null ? capacity : CognitiveCapacityConfig.defaults();

        double rc = capacity != null ? capacity.relationalComplexityOrDefault() : 1.0;
        double ar = capacity != null ? capacity.associativeReachOrDefault() : 1.0;
        double wm = capacity != null ? capacity.workingMemorySpanOrDefault() : 1.0;

        var sites = new ArrayList<SiteModulation>();

        sites.add(new SiteModulation(
                "caps-alpha", "associativeReach", ar,
                "multiplier", "Mednick 1962"));

        sites.add(new SiteModulation(
                "cbr-outcome-lr", "workingMemorySpan", wm,
                "multiplier", "Engle & Kane 2004"));

        sites.add(new SiteModulation(
                "graduation-score", "relationalComplexity", rc,
                "multiplier", "Halford 1998"));

        sites.add(new SiteModulation(
                "drive-adaptation-lr", "associativeReach", ar,
                "multiplier", "Mednick 1962"));

        sites.add(new SiteModulation(
                "strategy-delta-ceiling", "workingMemorySpan", wm,
                "ceiling", "Engle & Kane 2004"));

        sites.add(new SiteModulation(
                "personality-l2-ceiling", "relationalComplexity", rc,
                "ceiling", "Halford 1998"));

        return new CapacityModulationReport(defaults.agentId(), effectiveCapacity, List.copyOf(sites));
    }
}

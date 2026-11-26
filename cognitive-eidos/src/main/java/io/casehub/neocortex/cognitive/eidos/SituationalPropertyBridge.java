/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.neocortex.cognitive.eidos;

import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.AgentDisposition;
import io.casehub.eidos.api.AmpdFacetShift;
import io.casehub.eidos.api.SituationalProfile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SituationalPropertyBridge {

    private static final String PREFIX = "situational-";
    private static final String PROVENANCE_SUFFIX = "provenance";

    private SituationalPropertyBridge() {}

    public static List<SituationalProfile> extract(Map<String, String> properties) {
        if (properties == null || properties.isEmpty()) return List.of();

        var byMode = new LinkedHashMap<String, ModeAccumulator>();

        for (var entry : properties.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith(PREFIX)) continue;

            String remainder = key.substring(PREFIX.length());
            int sep = remainder.indexOf('-');
            if (sep < 0) continue;

            String mode = remainder.substring(0, sep);
            String field = remainder.substring(sep + 1);

            if (!SituationalProfile.VALID_MODES.contains(mode)) continue;

            var acc = byMode.computeIfAbsent(mode, k -> new ModeAccumulator());

            if (PROVENANCE_SUFFIX.equals(field)) {
                acc.provenance = entry.getValue();
            } else {
                try {
                    int delta = Integer.parseInt(entry.getValue().strip());
                    String facetName = field.replace('-', ' ');
                    acc.shifts.add(new AmpdFacetShift(facetName, delta));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        var result = new ArrayList<SituationalProfile>(byMode.size());
        for (var entry : byMode.entrySet()) {
            var acc = entry.getValue();
            if (acc.shifts.isEmpty()) continue;
            String provenance = acc.provenance != null ? acc.provenance : "biographical";
            result.add(new SituationalProfile(entry.getKey(), acc.shifts, provenance));
        }
        return List.copyOf(result);
    }

    public static AgentDescriptor enrich(AgentDescriptor descriptor,
                                          Map<String, String> nodeProperties) {
        var profiles = extract(nodeProperties);
        if (profiles.isEmpty()) return descriptor;
        var d = descriptor.disposition();
        var newDisposition = AgentDisposition.builder()
                .socialOrient(d.socialOrient())
                .ruleFollowing(d.ruleFollowing())
                .riskAppetite(d.riskAppetite())
                .autonomy(d.autonomy())
                .conflictMode(d.conflictMode())
                .delegation(d.delegation())
                .dispositionProfile(d.dispositionProfile())
                .styleProfile(d.styleProfile())
                .mbtiType(d.mbtiType())
                .enneagramType(d.enneagramType())
                .ampdFacets(d.ampdFacets())
                .situationalProfiles(profiles)
                .build();
        return descriptor.toBuilder().disposition(newDisposition).build();
    }

    private static class ModeAccumulator {
        final List<AmpdFacetShift> shifts = new ArrayList<>();
        String provenance;
    }
}

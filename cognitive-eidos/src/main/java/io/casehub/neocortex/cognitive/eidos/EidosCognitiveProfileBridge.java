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
import io.casehub.eidos.api.AgentGoal;
import io.casehub.eidos.api.DispositionAxis;
import io.casehub.eidos.api.DispositionValue;
import io.casehub.neocortex.cognitive.index.DescriptorView;
import io.casehub.neocortex.cognitive.index.DispositionAxes;
import io.casehub.neocortex.cognitive.index.LivedComposition;
import io.casehub.neocortex.cognitive.index.LivedProfile;
import io.casehub.neocortex.cognitive.index.PersonalityFacet;
import io.casehub.neocortex.cognitive.index.WeightedTerm;

import java.util.logging.Logger;

public class EidosCognitiveProfileBridge {

    private static final Logger LOG = Logger.getLogger(EidosCognitiveProfileBridge.class.getName());

    public EidosCognitiveProfileBridge() {}

    public static DescriptorView toDescriptorView(AgentDescriptor descriptor) {
        var disposition = descriptor.disposition();
        var axes = new DispositionAxes(
                disposition.primaryTerm(DispositionAxis.SOCIAL_ORIENTATION),
                disposition.primaryTerm(DispositionAxis.RULE_FOLLOWING),
                disposition.primaryTerm(DispositionAxis.RISK_APPETITE),
                disposition.primaryTerm(DispositionAxis.AUTONOMY),
                disposition.primaryTerm(DispositionAxis.CONFLICT_MODE)
        );
        var profile = disposition.dispositionProfile().stream()
                                 .map(dv -> new WeightedTerm(dv.term(), dv.weight()))
                                 .toList();
        var goals = descriptor.goals().stream()
                              .map(AgentGoal::name)
                              .toList();
        var facets = disposition.ampdFacets().stream()
                                .map(af -> new PersonalityFacet(titleCase(af.facet()), af.score() / 100.0, "eidos"))
                                .toList();
        return new DescriptorView(descriptor.agentId(), axes, profile, goals, null, facets);
    }

    public static AgentDescriptor livedDescriptor(AgentDescriptor authored, LivedProfile lived) {
        if (lived == null) {return authored;}
        var authoredProfile = authored.disposition().dispositionProfile().stream()
                                      .map(dv -> new WeightedTerm(dv.term(), dv.weight()))
                                      .toList();
        var effectiveTerms = LivedComposition.profile(authoredProfile, lived);
        if (effectiveTerms == authoredProfile) {return authored;}
        var effectiveValues = effectiveTerms.stream()
                                            .map(wt -> new DispositionValue(wt.term(), wt.weight()))
                                            .toList();
        var newDisposition = AgentDisposition.builder()
                                             .socialOrient(authored.disposition().socialOrient())
                                             .ruleFollowing(authored.disposition().ruleFollowing())
                                             .riskAppetite(authored.disposition().riskAppetite())
                                             .autonomy(authored.disposition().autonomy())
                                             .conflictMode(authored.disposition().conflictMode())
                                             .delegation(authored.disposition().delegation())
                                             .dispositionProfile(effectiveValues)
                                             .styleProfile(authored.disposition().styleProfile())
                                             .mbtiType(authored.disposition().mbtiType())
                                             .enneagramType(authored.disposition().enneagramType())
                                             .ampdFacets(authored.disposition().ampdFacets())
                                             .situationalProfiles(authored.disposition().situationalProfiles())
                                             .build();
        return authored.toBuilder().disposition(newDisposition).build();
    }


    public static AgentDisposition livedDisposition(AgentDisposition authored, LivedProfile lived) {
        if (lived == null) {return authored;}
        var authoredProfile = authored.dispositionProfile().stream()
                                      .map(dv -> new WeightedTerm(dv.term(), dv.weight()))
                                      .toList();
        var effectiveTerms = LivedComposition.profile(authoredProfile, lived);
        if (effectiveTerms == authoredProfile) {return authored;}
        var effectiveValues = effectiveTerms.stream()
                                            .map(wt -> new DispositionValue(wt.term(), wt.weight()))
                                            .toList();
        return AgentDisposition.builder()
                               .socialOrient(authored.socialOrient())
                               .ruleFollowing(authored.ruleFollowing())
                               .riskAppetite(authored.riskAppetite())
                               .autonomy(authored.autonomy())
                               .conflictMode(authored.conflictMode())
                               .delegation(authored.delegation())
                               .dispositionProfile(effectiveValues)
                               .styleProfile(authored.styleProfile())
                               .mbtiType(authored.mbtiType())
                               .enneagramType(authored.enneagramType())
                               .ampdFacets(authored.ampdFacets())
                               .situationalProfiles(authored.situationalProfiles())
                               .build();
    }

    public static AgentDisposition composeFacets(AgentDisposition disposition,
                                                 io.casehub.neocortex.cognitive.index.PersonalityStateSnapshot snapshot) {
        if (snapshot == null || snapshot.facets().isEmpty()) {return disposition;}
        var authored = disposition.ampdFacets();
        if (authored.isEmpty()) {return disposition;}

        boolean anyChanged = false;
        var     composed   = new java.util.ArrayList<io.casehub.eidos.api.AmpdFacet>(authored.size());
        for (var af : authored) {
            var fd = snapshot.facets().get(af.facet());
            if (fd == null) {
                composed.add(af);
                continue;
            }
            double resting       = Math.max(0.0, Math.min(1.0, af.score() / 100.0 + fd.homeOffset() + fd.promoted()));
            int    composedScore = (int) Math.round(resting * 100.0);
            if (composedScore != af.score()) {anyChanged = true;}
            composed.add(new io.casehub.eidos.api.AmpdFacet(af.facet(), composedScore));
        }
        if (!anyChanged) {return disposition;}

        return AgentDisposition.builder()
                               .socialOrient(disposition.socialOrient())
                               .ruleFollowing(disposition.ruleFollowing())
                               .riskAppetite(disposition.riskAppetite())
                               .autonomy(disposition.autonomy())
                               .conflictMode(disposition.conflictMode())
                               .delegation(disposition.delegation())
                               .dispositionProfile(disposition.dispositionProfile())
                               .styleProfile(disposition.styleProfile())
                               .mbtiType(disposition.mbtiType())
                               .enneagramType(disposition.enneagramType())
                               .ampdFacets(composed)
                               .situationalProfiles(disposition.situationalProfiles())
                               .build();
    }


    private static String titleCase(String s) {
        if (s == null || s.isEmpty()) {return s;}
        var     sb        = new StringBuilder();
        boolean nextUpper = true;
        for (char c : s.toCharArray()) {
            if (Character.isWhitespace(c)) {
                nextUpper = true;
                sb.append(c);
            } else if (nextUpper) {
                sb.append(Character.toUpperCase(c));
                nextUpper = false;
            } else {
                sb.append(Character.toLowerCase(c));
                nextUpper = false;
            }
        }
        return sb.toString();
    }
}

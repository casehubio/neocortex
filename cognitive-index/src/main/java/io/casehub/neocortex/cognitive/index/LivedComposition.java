package io.casehub.neocortex.cognitive.index;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

public final class LivedComposition {
    private static final double EPSILON_PUBLISH = 0.005;

    private LivedComposition() {}

    public static String fingerprint(List<WeightedTerm> authoredProfile) {
        if (authoredProfile == null || authoredProfile.isEmpty()) return "";
        var sorted = new ArrayList<>(authoredProfile);
        sorted.sort(Comparator.comparing(WeightedTerm::term));
        var sb = new StringBuilder();
        for (var wt : sorted) {
            if (!sb.isEmpty()) sb.append(';');
            sb.append(wt.term()).append('=').append(String.format(java.util.Locale.ROOT, "%.4f", wt.weight()));
        }
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var hash = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public static List<WeightedTerm> profile(List<WeightedTerm> authoredProfile, LivedProfile lived) {
        if (lived == null) return authoredProfile;
        if (fingerprint(authoredProfile).equals(lived.authoredFingerprint())) {
            return lived.profile();
        }
        return authoredProfile;
    }

    public static DescriptorView compose(DescriptorView authored, PersonalityStateSnapshot lived,
                                         boolean includeDisplacement) {
        if (lived == null || lived.equals(PersonalityStateSnapshot.empty())) return authored;

        var effectiveProfile = profile(authored.dispositionProfile(), lived.livedProfile());

        var composedFacets = new ArrayList<PersonalityFacet>();
        for (var facet : authored.personalityFacets()) {
            var fd = lived.facets().get(facet.facet());
            if (fd == null) {
                composedFacets.add(facet);
                continue;
            }
            double resting = clamp01(facet.score() + fd.promoted() + fd.homeOffset());
            double effective = includeDisplacement
                    ? clamp01(facet.score() + fd.promoted() + fd.homeOffset() + fd.displacement())
                    : resting;
            String origin = Math.abs(effective - facet.score()) >= EPSILON_PUBLISH
                    ? String.format(java.util.Locale.ROOT, "situational [usual %.2f]", facet.score()) : facet.origin();
            composedFacets.add(new PersonalityFacet(facet.facet(), effective, origin));
        }

        var result = new DescriptorView(authored.agentId(), authored.disposition(),
                effectiveProfile, authored.goals(), authored.formationPadSummary(), composedFacets);
        return result;
    }

    static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

}

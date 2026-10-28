package io.casehub.neocortex.cognitive.index;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class PersonalityProfileTest {

    @Test
    void compositeRecord_holdsAllFields() {
        var profile = new PersonalityProfile(
                null, null, null,
                new AttachmentProfile("dismissive-avoidant", 0.1, 0.8),
                List.of(new PersonalityFacet("Callousness", 0.9, null)));
        assertThat(profile.attachment()).isNotNull();
        assertThat(profile.attachment().avoidance()).isEqualTo(0.8);
        assertThat(profile.facets()).hasSize(1);
        assertThat(profile.facets().getFirst().facet()).isEqualTo("Callousness");
    }

    @Test
    void nullAttachmentAndEmptyFacets_areAllowed() {
        var profile = new PersonalityProfile(null, null, null, null, List.of());
        assertThat(profile.attachment()).isNull();
        assertThat(profile.facets()).isEmpty();
    }

    @Test
    void nullFacetsList_normalizesToEmpty() {
        var profile = new PersonalityProfile(null, null, null, null, null);
        assertThat(profile.facets()).isEmpty();
    }
}

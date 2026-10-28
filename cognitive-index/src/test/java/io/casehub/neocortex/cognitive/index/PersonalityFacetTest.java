package io.casehub.neocortex.cognitive.index;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PersonalityFacetTest {

    @Test
    void validFacet_createsSuccessfully() {
        var facet = new PersonalityFacet("Callousness", 0.9, "numbness where sympathy should be");
        assertThat(facet.facet()).isEqualTo("Callousness");
        assertThat(facet.score()).isEqualTo(0.9);
        assertThat(facet.origin()).isEqualTo("numbness where sympathy should be");
    }

    @Test
    void nullOrigin_isAllowed() {
        var facet = new PersonalityFacet("Grandiosity", 0.8, null);
        assertThat(facet.origin()).isNull();
    }

    @Test
    void scoreBelowZero_throws() {
        assertThatThrownBy(() -> new PersonalityFacet("X", -0.1, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void scoreAboveOne_throws() {
        assertThatThrownBy(() -> new PersonalityFacet("X", 1.1, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void boundaryValues_areAccepted() {
        assertThatCode(() -> new PersonalityFacet("X", 0.0, null)).doesNotThrowAnyException();
        assertThatCode(() -> new PersonalityFacet("X", 1.0, null)).doesNotThrowAnyException();
    }

    @Test
    void facetConstants_areAvailable() {
        assertThat(PersonalityFacet.CALLOUSNESS).isEqualTo("Callousness");
        assertThat(PersonalityFacet.MANIPULATIVENESS).isEqualTo("Manipulativeness");
        assertThat(PersonalityFacet.GRANDIOSITY).isEqualTo("Grandiosity");
    }
}

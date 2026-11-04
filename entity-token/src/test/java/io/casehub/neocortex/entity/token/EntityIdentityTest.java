package io.casehub.neocortex.entity.token;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EntityIdentityTest {

    @Test
    void surfaceFormsIncludesAllVariants() {
        var identity = EntityIdentity.of(
            "person_001", "Jane Doe",
            Map.of("canonicalFirst", "Jane", "canonicalLast", "Doe"),
            Map.of("pronounsSubject", "she", "pronounsObject", "her"),
            Map.of("preferredShort", "Janey"),
            Set.of("JD"));

        var forms = identity.surfaceForms();
        assertThat(forms).extracting(SurfaceForm::text)
            .contains("Jane Doe", "Jane", "Doe", "Janey", "JD");
        assertThat(forms).extracting(SurfaceForm::variant)
            .contains("full", "canonicalFirst", "canonicalLast", "preferredShort", "other");
    }

    @Test
    void surfaceFormsDeduplicatesIdenticalText() {
        var identity = EntityIdentity.of(
            "person_001", "Jane",
            Map.of("canonicalFirst", "Jane"),
            Map.of(), Map.of("preferredShort", "Jane"),
            Set.of());

        var forms = identity.surfaceForms();
        long janeCount = forms.stream()
            .filter(f -> "Jane".equals(f.text())).count();
        assertThat(janeCount).isEqualTo(1);
    }

    @Test
    void surfaceFormsAllCarryEntityId() {
        var identity = EntityIdentity.of(
            "p1", "Alice", Map.of(), Map.of(), Map.of(), Set.of("Al"));
        var forms = identity.surfaceForms();
        assertThat(forms).allMatch(f -> "p1".equals(f.entityId()));
    }

    @Test
    void resolveVariantFull() {
        var identity = EntityIdentity.of(
            "p1", "Jane Doe", Map.of(), Map.of(), Map.of(), Set.of());
        assertThat(identity.resolveVariant("full")).isEqualTo("Jane Doe");
    }

    @Test
    void resolveVariantFromNameParts() {
        var identity = EntityIdentity.of(
            "p1", "Jane Doe",
            Map.of("canonicalFirst", "Jane", "canonicalLast", "Doe"),
            Map.of(), Map.of(), Set.of());
        assertThat(identity.resolveVariant("canonicalFirst")).isEqualTo("Jane");
        assertThat(identity.resolveVariant("canonicalLast")).isEqualTo("Doe");
    }

    @Test
    void resolveVariantFromPreferred() {
        var identity = EntityIdentity.of(
            "p1", "Jane Doe", Map.of(), Map.of(),
            Map.of("preferredShort", "Janey"), Set.of());
        assertThat(identity.resolveVariant("preferredShort")).isEqualTo("Janey");
    }

    @Test
    void resolveVariantPronoun() {
        var identity = EntityIdentity.of(
            "p1", "Jane Doe", Map.of(),
            Map.of("pronounsSubject", "she", "pronounsObject", "her"),
            Map.of(), Set.of());
        assertThat(identity.resolveVariant("pronoun:subject")).isEqualTo("she");
        assertThat(identity.resolveVariant("pronoun:object")).isEqualTo("her");
    }

    @Test
    void resolveVariantUnknownReturnsNull() {
        var identity = EntityIdentity.of(
            "p1", "Jane Doe", Map.of(), Map.of(), Map.of(), Set.of());
        assertThat(identity.resolveVariant("nonexistent")).isNull();
    }

    @Test
    void immutableCollections() {
        var identity = EntityIdentity.of(
            "p1", "Jane Doe",
            Map.of("canonicalFirst", "Jane"),
            Map.of("pronounsSubject", "she"),
            Map.of("preferredShort", "Janey"),
            Set.of("JD"));
        assertThat(identity.nameParts()).isUnmodifiable();
        assertThat(identity.pronouns()).isUnmodifiable();
        assertThat(identity.preferred()).isUnmodifiable();
        assertThat(identity.aliases()).isUnmodifiable();
    }

    @Test
    void surfaceFormsIncludesAlterEgoForms() {
        var ziggy = new EntityIdentity(
                "bowie.ziggy", "Ziggy Stardust",
                Map.of("canonicalFirst", "Ziggy"),
                Map.of("pronounsSubject", "he"),
                Map.of("preferredShort", "Ziggy"),
                Set.of(), Map.of());
        var bowie = new EntityIdentity(
                "bowie", "David Bowie",
                Map.of("canonicalFirst", "David", "canonicalLast", "Bowie"),
                Map.of("pronounsSubject", "he"),
                Map.of(), Set.of("David Robert Jones"),
                Map.of("ziggy", ziggy));

        var forms = bowie.surfaceForms();
        assertThat(forms).extracting(SurfaceForm::text)
                         .contains("David Bowie", "David", "Bowie", "David Robert Jones",
                                   "Ziggy Stardust", "Ziggy");
        assertThat(forms).filteredOn(f -> "Ziggy Stardust".equals(f.text()))
                         .extracting(SurfaceForm::entityId)
                         .containsExactly("bowie.ziggy");
    }

    @Test
    void alterEgosMapIsImmutable() {
        var ziggy = new EntityIdentity(
                "bowie.ziggy", "Ziggy Stardust",
                Map.of(), Map.of(), Map.of(), Set.of(), Map.of());
        var bowie = new EntityIdentity(
                "bowie", "David Bowie",
                Map.of(), Map.of(), Map.of(), Set.of(),
                Map.of("ziggy", ziggy));
        assertThat(bowie.alterEgos()).isUnmodifiable();
    }

    @Test
    void backwardCompatFactoryDefaultsAlterEgosToEmpty() {
        var identity = EntityIdentity.of(
                "p1", "Jane Doe", Map.of(), Map.of(), Map.of(), Set.of());
        assertThat(identity.alterEgos()).isEmpty();
    }
}

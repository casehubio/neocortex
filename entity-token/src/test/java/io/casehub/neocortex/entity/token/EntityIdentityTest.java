package io.casehub.neocortex.entity.token;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EntityIdentityTest {

    @Test
    void surfaceFormsIncludesAllVariants() {
        var identity = new EntityIdentity(
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
        var identity = new EntityIdentity(
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
        var identity = new EntityIdentity(
            "p1", "Alice", Map.of(), Map.of(), Map.of(), Set.of("Al"));
        var forms = identity.surfaceForms();
        assertThat(forms).allMatch(f -> "p1".equals(f.entityId()));
    }

    @Test
    void resolveVariantFull() {
        var identity = new EntityIdentity(
            "p1", "Jane Doe", Map.of(), Map.of(), Map.of(), Set.of());
        assertThat(identity.resolveVariant("full")).isEqualTo("Jane Doe");
    }

    @Test
    void resolveVariantFromNameParts() {
        var identity = new EntityIdentity(
            "p1", "Jane Doe",
            Map.of("canonicalFirst", "Jane", "canonicalLast", "Doe"),
            Map.of(), Map.of(), Set.of());
        assertThat(identity.resolveVariant("canonicalFirst")).isEqualTo("Jane");
        assertThat(identity.resolveVariant("canonicalLast")).isEqualTo("Doe");
    }

    @Test
    void resolveVariantFromPreferred() {
        var identity = new EntityIdentity(
            "p1", "Jane Doe", Map.of(), Map.of(),
            Map.of("preferredShort", "Janey"), Set.of());
        assertThat(identity.resolveVariant("preferredShort")).isEqualTo("Janey");
    }

    @Test
    void resolveVariantPronoun() {
        var identity = new EntityIdentity(
            "p1", "Jane Doe", Map.of(),
            Map.of("pronounsSubject", "she", "pronounsObject", "her"),
            Map.of(), Set.of());
        assertThat(identity.resolveVariant("pronoun:subject")).isEqualTo("she");
        assertThat(identity.resolveVariant("pronoun:object")).isEqualTo("her");
    }

    @Test
    void resolveVariantUnknownReturnsNull() {
        var identity = new EntityIdentity(
            "p1", "Jane Doe", Map.of(), Map.of(), Map.of(), Set.of());
        assertThat(identity.resolveVariant("nonexistent")).isNull();
    }

    @Test
    void immutableCollections() {
        var identity = new EntityIdentity(
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
}

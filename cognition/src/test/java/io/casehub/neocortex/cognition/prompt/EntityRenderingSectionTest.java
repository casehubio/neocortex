package io.casehub.neocortex.cognition.prompt;

import io.casehub.neocortex.entity.token.EntityIdentity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;

class EntityRenderingSectionTest {

    private static final CognitionRenderContext CTX =
            new CognitionRenderContext("agent-1", "tenant-1", null, List.of());

    private static final EntityIdentity FRIDA = EntityIdentity.of(
            "p1", "Frida Kahlo",
            Map.of("canonicalFirst", "Frida", "canonicalLast", "Kahlo"),
            Map.of("pronounsSubject", "she", "pronounsObject", "her"),
            Map.of(), Set.of());

    private static final BiFunction<String, String, EntityIdentity> LOOKUP =
            (entityId, tenantId) -> "p1".equals(entityId) ? FRIDA : null;

    @Test
    void resolvesTokensViaIdentityLookup() {
        CognitionPromptRenderer delegate = ctx -> "{{p1:full:Frida}} painted self-portraits.";
        var section = new EntityRenderingSection(delegate, LOOKUP);

        String result = section.render(CTX);

        assertThat(result).isEqualTo("Frida Kahlo painted self-portraits.");
    }

    @Test
    void resolvesVariantToNamePart() {
        CognitionPromptRenderer delegate = ctx -> "{{p1:canonicalFirst:Frida}} is remembered.";
        var section = new EntityRenderingSection(delegate, LOOKUP);

        String result = section.render(CTX);

        assertThat(result).isEqualTo("Frida is remembered.");
    }

    @Test
    void unknownEntityFallsBackToSurfaceForm() {
        CognitionPromptRenderer delegate = ctx -> "{{unknown:full:Diego}} was a muralist.";
        var section = new EntityRenderingSection(delegate, LOOKUP);

        String result = section.render(CTX);

        assertThat(result).isEqualTo("Diego was a muralist.");
    }

    @Test
    void nullLookupStripsTokensToSurfaceForms() {
        CognitionPromptRenderer delegate = ctx -> "{{p1:full:Frida}} painted.";
        var section = new EntityRenderingSection(delegate, null);

        String result = section.render(CTX);

        assertThat(result).isEqualTo("Frida painted.");
    }

    @Test
    void nullRenderPassesThrough() {
        CognitionPromptRenderer delegate = ctx -> null;
        var section = new EntityRenderingSection(delegate, LOOKUP);

        String result = section.render(CTX);

        assertThat(result).isNull();
    }

    @Test
    void textWithoutTokensPassesThrough() {
        CognitionPromptRenderer delegate = ctx -> "Plain text with no tokens.";
        var section = new EntityRenderingSection(delegate, LOOKUP);

        String result = section.render(CTX);

        assertThat(result).isEqualTo("Plain text with no tokens.");
    }

    @Test
    void delegatesBlockTag() {
        CognitionPromptRenderer delegate = new CognitionPromptRenderer() {
            @Override
            public String render(CognitionRenderContext context) {
                return "content";
            }

            @Override
            public BlockTag blockTag() {
                return BlockTag.NARRATIVE;
            }
        };
        var section = new EntityRenderingSection(delegate, LOOKUP);

        assertThat(section.blockTag()).isEqualTo(BlockTag.NARRATIVE);
    }

    @Test
    void passesCorrectTenantIdToLookup() {
        var ctx = new CognitionRenderContext("agent-1", "tenant-42", null, List.of());
        String[] capturedTenant = {null};
        BiFunction<String, String, EntityIdentity> capturingLookup = (entityId, tenantId) -> {
            capturedTenant[0] = tenantId;
            return FRIDA;
        };
        CognitionPromptRenderer delegate = c -> "{{p1:full:Frida}} painted.";
        var section = new EntityRenderingSection(delegate, capturingLookup);

        section.render(ctx);

        assertThat(capturedTenant[0]).isEqualTo("tenant-42");
    }
}

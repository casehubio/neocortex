package io.casehub.neocortex.entity.token.cdi;

import io.casehub.neocortex.entity.token.EntityTokeniser;
import io.casehub.neocortex.entity.token.SurfaceFormTrie;
import io.casehub.neocortex.memory.CaseEnrichmentStep;
import io.casehub.neocortex.memory.MemoryInput;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class TokenisingEnrichmentStep implements CaseEnrichmentStep {

    private final SurfaceFormTrieManager trieManager;

    @Inject
    public TokenisingEnrichmentStep(SurfaceFormTrieManager trieManager) {
        this.trieManager = trieManager;
    }

    TokenisingEnrichmentStep(SurfaceFormTrieManager trieManager, @SuppressWarnings("unused") boolean forTest) {
        this.trieManager = trieManager;
    }

    @Override
    public boolean appliesTo(MemoryInput input) {
        return input.text() != null && !input.text().isBlank();
    }

    @Override
    public MemoryInput enrich(MemoryInput input) {
        SurfaceFormTrie trie = trieManager.getTrieForTenant(input.tenantId());
        String tokenised = EntityTokeniser.tokenise(input.text(), trie);
        if (tokenised.equals(input.text())) return input;
        return input.withText(tokenised);
    }

    @Override
    public int priority() {
        return 100;
    }
}

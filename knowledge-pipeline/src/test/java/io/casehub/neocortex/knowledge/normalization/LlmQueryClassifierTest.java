package io.casehub.neocortex.knowledge.normalization;

import io.casehub.neocortex.knowledge.KnowledgeDomain;
import io.casehub.neocortex.knowledge.KnowledgeQuery;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LlmQueryClassifierTest {

    @Test
    void parsesTextSearchResponse() {
        var classifier = new LlmQueryClassifier((io.casehub.platform.agent.AgentProvider) null, (java.util.List<io.casehub.connectors.location.spi.LocationPlatform>) null);
        var result = classifier.parseResponse(
            "{\"type\":\"TEXT\",\"query\":\"Italian food\"}",
            KnowledgeDomain.PLACE);
        assertThat(result).isPresent();
        assertThat(result.get()).isInstanceOf(KnowledgeQuery.TextSearch.class);
        var ts = (KnowledgeQuery.TextSearch) result.get();
        assertThat(ts.query()).isEqualTo("Italian food");
        assertThat(ts.domain()).isEqualTo(KnowledgeDomain.PLACE);
    }

    @Test
    void parsesCategorySearchResponse() {
        var classifier = new LlmQueryClassifier((io.casehub.platform.agent.AgentProvider) null, (java.util.List<io.casehub.connectors.location.spi.LocationPlatform>) null);
        var result = classifier.parseResponse(
            "{\"type\":\"CATEGORY\",\"category\":\"restaurant\",\"radius\":1000}",
            KnowledgeDomain.PLACE);
        assertThat(result).isPresent();
        assertThat(result.get()).isInstanceOf(KnowledgeQuery.TextSearch.class);
    }

    @Test
    void nearbyWithLocationButNoGeocodingFallsBackToTextSearch() {
        var classifier = new LlmQueryClassifier((io.casehub.platform.agent.AgentProvider) null, (java.util.List<io.casehub.connectors.location.spi.LocationPlatform>) null);
        var result = classifier.parseResponse(
            "{\"type\":\"NEARBY\",\"location\":\"King's Cross, London\",\"radius\":500}",
            KnowledgeDomain.PLACE);
        assertThat(result).isPresent();
        assertThat(result.get()).isInstanceOf(KnowledgeQuery.TextSearch.class);
        var ts = (KnowledgeQuery.TextSearch) result.get();
        assertThat(ts.query()).isEqualTo("King's Cross, London");
    }

    @Test
    void nearbyWithoutLocationReturnsEmpty() {
        var classifier = new LlmQueryClassifier((io.casehub.platform.agent.AgentProvider) null, (java.util.List<io.casehub.connectors.location.spi.LocationPlatform>) null);
        var result = classifier.parseResponse(
            "{\"type\":\"NEARBY\",\"radius\":500}",
            KnowledgeDomain.PLACE);
        assertThat(result).isEmpty();
    }

    @Test
    void invalidJsonReturnsEmpty() {
        var classifier = new LlmQueryClassifier((io.casehub.platform.agent.AgentProvider) null, (java.util.List<io.casehub.connectors.location.spi.LocationPlatform>) null);
        var result = classifier.parseResponse("not json", KnowledgeDomain.PLACE);
        assertThat(result).isEmpty();
    }

    @Test
    void blankResponseReturnsEmpty() {
        var classifier = new LlmQueryClassifier((io.casehub.platform.agent.AgentProvider) null, (java.util.List<io.casehub.connectors.location.spi.LocationPlatform>) null);
        var result = classifier.parseResponse("", KnowledgeDomain.PLACE);
        assertThat(result).isEmpty();
    }

    @Test
    void noAgentProviderReturnsEmpty() {
        var classifier = new LlmQueryClassifier((io.casehub.platform.agent.AgentProvider) null, (java.util.List<io.casehub.connectors.location.spi.LocationPlatform>) null);
        var result = classifier.classify("find pizza near me", KnowledgeDomain.PLACE);
        assertThat(result).isEmpty();
    }
}

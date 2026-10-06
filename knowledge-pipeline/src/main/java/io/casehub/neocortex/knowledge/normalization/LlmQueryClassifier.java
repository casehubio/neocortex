package io.casehub.neocortex.knowledge.normalization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.connectors.location.model.GeocodingResult;
import io.casehub.connectors.location.spi.LocationPlatform;
import io.casehub.neocortex.knowledge.KnowledgeQuery;
import io.casehub.neocortex.knowledge.QueryClassifier;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@ApplicationScoped
public class LlmQueryClassifier implements QueryClassifier {

    private static final Logger LOG = Logger.getLogger(LlmQueryClassifier.class.getName());
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private static final String SYSTEM_PROMPT = """
        You are a query classifier for a knowledge pipeline. Given natural language input, \
        classify it into one of three query types and extract parameters.

        Query types:
        1. TEXT — general text search. Use when no specific location or category is implied.
        2. CATEGORY — search by category near a location. Use when the user asks for a type \
           of place/thing near a location.
        3. NEARBY — search near a location without a category. Use when the user asks what's \
           near a location.

        Respond with valid JSON only:
        - TEXT: {"type":"TEXT","query":"<extracted search terms>"}
        - CATEGORY: {"type":"CATEGORY","category":"<category>","location":"<location name, city>","radius":<meters>}
        - NEARBY: {"type":"NEARBY","location":"<location name, city>","radius":<meters>}

        Include city/country qualifiers in location names for disambiguation.
        Default radius to 1000 if not specified.""";

    private final AgentProvider agentProvider;
    private final List<LocationPlatform> geocodingProviders;
    private final ObjectMapper mapper = new ObjectMapper();

    @Inject
    public LlmQueryClassifier(Instance<AgentProvider> agentProviderInstance,
                               Instance<LocationPlatform> platformInstance) {
        this.agentProvider = agentProviderInstance != null && agentProviderInstance.isResolvable()
            ? agentProviderInstance.get() : null;
        this.geocodingProviders = platformInstance != null
            ? platformInstance.stream()
                .filter(p -> p.supports(LocationPlatform.Geocoding.class))
                .toList()
            : List.of();
    }

    LlmQueryClassifier(AgentProvider agentProvider, List<LocationPlatform> geocodingProviders) {
        this.agentProvider = agentProvider;
        this.geocodingProviders = geocodingProviders != null ? geocodingProviders : List.of();
    }

    @Override
    public Optional<KnowledgeQuery> classify(String naturalLanguage, String domain) {
        if (agentProvider == null || naturalLanguage == null || naturalLanguage.isBlank()) {
            return Optional.empty();
        }

        try {
            var events = agentProvider.invoke(
                    AgentSessionConfig.of(SYSTEM_PROMPT, naturalLanguage, TIMEOUT))
                .collect().asList()
                .await().atMost(TIMEOUT.plusSeconds(5));

            String text = events.stream()
                .filter(AgentEvent.TextDelta.class::isInstance)
                .map(AgentEvent.TextDelta.class::cast)
                .map(AgentEvent.TextDelta::text)
                .collect(Collectors.joining());

            return parseResponse(text, domain);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "LLM query classification failed", e);
            return Optional.empty();
        }
    }

    Optional<KnowledgeQuery> parseResponse(String text, String domain) {
        if (text == null || text.isBlank()) return Optional.empty();

        String json = extractJson(text);
        if (json == null) return Optional.empty();

        try {
            JsonNode root = mapper.readTree(json);
            String type = root.path("type").asText(null);
            if (type == null) return Optional.empty();

            return switch (type) {
                case "TEXT" -> {
                    String query = root.path("query").asText(null);
                    yield query != null
                        ? Optional.of(new KnowledgeQuery.TextSearch(query, domain))
                        : Optional.empty();
                }
                case "CATEGORY" -> {
                    String category = root.path("category").asText(null);
                    String location = root.path("location").asText(null);
                    int radius = root.path("radius").asInt(1000);
                    if (category == null) yield Optional.empty();
                    if (location != null && !geocodingProviders.isEmpty()) {
                        var coords = geocode(location);
                        if (coords.isPresent()) {
                            yield Optional.of(new KnowledgeQuery.CategorySearch(
                                category, coords.get(), radius));
                        }
                    }
                    yield Optional.of(new KnowledgeQuery.TextSearch(category, domain));
                }
                case "NEARBY" -> {
                    String location = root.path("location").asText(null);
                    int radius = root.path("radius").asInt(1000);
                    if (location != null && !geocodingProviders.isEmpty()) {
                        var coords = geocode(location);
                        if (coords.isPresent()) {
                            yield Optional.of(new KnowledgeQuery.NearbySearch(
                                coords.get(), radius, null));
                        }
                    }
                    yield location != null
                        ? Optional.of(new KnowledgeQuery.TextSearch(location, domain))
                        : Optional.empty();
                }
                default -> Optional.empty();
            };
        } catch (JsonProcessingException e) {
            LOG.warning("Failed to parse LLM classifier response: " + e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<io.casehub.connectors.location.model.Coordinates> geocode(String location) {
        for (LocationPlatform provider : geocodingProviders) {
            try {
                List<GeocodingResult> results = provider.geocoding("pipeline")
                    .geocode(location);
                if (!results.isEmpty()) {
                    return Optional.of(results.get(0).location());
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Geocoding failed for: " + location, e);
            }
        }
        return Optional.empty();
    }

    private static String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) return null;
        return text.substring(start, end + 1);
    }
}

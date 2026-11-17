package io.casehub.neocortex.caps.llm.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.casehub.neocortex.caps.CapsTopology;
import io.casehub.neocortex.caps.engine.CapsTopologyLoader;
import io.casehub.platform.agent.AgentBackend;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.BackendInstanceRegistry;
import io.casehub.platform.agent.claude.ClaudeAgentClient;
import io.casehub.platform.agent.claude.ClaudeAgentProperties;
import io.casehub.platform.agent.claude.ClaudeAgentProvider;
import io.casehub.platform.agent.config.PoolDeclaration;
import io.casehub.platform.agent.gate.AimdAdmissionStrategy;
import io.casehub.platform.agent.session.SessionLifecycleManager;
import io.casehub.platform.agent.session.SessionPipeline;
import io.casehub.platform.agent.session.SessionPoolRegistry;
import io.smallrye.mutiny.Multi;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

final class EvalTestSupport {

    private static volatile ClaudeAgentClient sharedClient;
    private static volatile AgentBackend sharedBackend;

    static CapsTopology loadTopology() {
        return new CapsTopologyLoader().loadFromClasspath("caps-topology.yaml");
    }

    static AgentBackend backend() {
        if (sharedBackend == null) {
            synchronized (EvalTestSupport.class) {
                if (sharedBackend == null) {
                    ClaudeAgentProperties props = new ClaudeAgentProperties() {
                        @Override public java.util.Optional<String> binaryPath() { return java.util.Optional.empty(); }
                        @Override public java.time.Duration defaultTimeout() { return java.time.Duration.ofMinutes(5); }
                        @Override public int maxConcurrentSessions() { return 10; }
                    };
                    sharedClient = new ClaudeAgentClient(props);
                    sharedBackend = new ClaudeAgentProvider(sharedClient);
                    Runtime.getRuntime().addShutdownHook(new Thread(EvalTestSupport::shutdown, "eval-agent-cleanup"));
                }
            }
        }
        return sharedBackend;
    }

    static void shutdown() {
        var client = sharedClient;
        if (client != null) {
            client.shutdown();
        }
    }

    static AgentProvider agentProvider() {
        var backend = backend();
        return new AgentProvider() {
            @Override public Multi<AgentEvent> invoke(AgentSessionConfig config) { return backend.invoke(config); }
            @Override public AgentSession openSession(AgentSessionInit init) { return backend.openSession(init); }
        };
    }

    static SessionPipeline sessionPipeline() {
        var backend = backend();
        BackendInstanceRegistry registry = new BackendInstanceRegistry() {
            @Override public void register(AgentBackend b) {}
            @Override public Optional<AgentBackend> resolve(String key, String instanceId) { return Optional.of(backend); }
            @Override public List<AgentBackend> resolveByKey(String key) { return List.of(backend); }
        };
        var pool = new PoolDeclaration("eval", "eval", "claude", 0, 10, null, Map.of(), Map.of());
        var poolRegistry = new SessionPoolRegistry(List.of(pool), registry);
        var lifecycleManager = new SessionLifecycleManager(poolRegistry);
        return new SessionPipeline(lifecycleManager, "claude",
                new AimdAdmissionStrategy(10, 8, Duration.ofSeconds(120)), 50_000);
    }


    private static final int          MAX_RETRIES         = 3;
    private static final long         RETRY_BACKOFF_MS    = 5000;
    private static final String       JUDGE_SYSTEM_PROMPT = "You are a precise evaluation judge. Respond only with JSON.";
    private static final ObjectMapper JSON                = new ObjectMapper();
    private static final Path         OUTPUT_DIR          = Path.of("target/experiment-results");


    static String extractJson(String text) {
        text = text.strip();
        if (text.startsWith("{")) {return text.substring(0, text.lastIndexOf('}') + 1);}
        int start = text.indexOf('{');
        int end   = text.lastIndexOf('}');
        if (start >= 0 && end > start) {return text.substring(start, end + 1);}
        return text;
    }


    static void writeResult(String outputFilename, String testName, Map<String, Object> detail) {
        try {
            var outputFile = OUTPUT_DIR.resolve(outputFilename);
            Files.createDirectories(outputFile.getParent());
            LinkedHashMap<String, Object> results;
            if (Files.exists(outputFile)) {
                results = JSON.readValue(outputFile.toFile(),
                                         new com.fasterxml.jackson.core.type.TypeReference<>() {});
            } else {
                results = new LinkedHashMap<>();
            }
            results.put(testName, detail);
            JSON.copy().enable(SerializationFeature.INDENT_OUTPUT)
                .writeValue(outputFile.toFile(), results);
        } catch (Exception e) {
            System.err.printf("[%s] Failed to write result: %s%n", testName, e.getMessage());
        }
    }


    static int judge(AgentProvider agentProvider, String judgePrompt, String testName,
                     @org.jspecify.annotations.Nullable String outputFilename,
                     @org.jspecify.annotations.Nullable String persistedContent) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                var response = agentProvider.invoke(
                                                    AgentSessionConfig.of(JUDGE_SYSTEM_PROMPT, judgePrompt))
                                            .filter(e -> e instanceof AgentEvent.TextDelta)
                                            .map(e -> ((AgentEvent.TextDelta) e).text())
                                            .collect().with(Collectors.joining())
                                            .await().atMost(Duration.ofSeconds(60));

                var    json      = extractJson(response);
                var    node      = JSON.readTree(json);
                int    score     = node.get("score").asInt();
                String reasoning = node.has("reasoning") ? node.get("reasoning").asText() : "";
                System.out.printf("[judge:%s] score=%d reasoning=%s%n", testName, score, reasoning);

                if (outputFilename != null) {
                    var content = persistedContent != null
                                  ? persistedContent.substring(0, Math.min(persistedContent.length(), 2000))
                                  : "";
                    writeResult(outputFilename, testName, Map.of(
                            "score", score, "reasoning", reasoning, "content", content));
                }
                return score;
            } catch (Exception e) {
                System.err.printf("[judge:%s] attempt %d/%d failed: %s%n",
                                  testName, attempt, MAX_RETRIES, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try {Thread.sleep(RETRY_BACKOFF_MS * attempt);} catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return 0;
                    }
                }
            }
        }
        return 0;
    }

    private EvalTestSupport() {}
}

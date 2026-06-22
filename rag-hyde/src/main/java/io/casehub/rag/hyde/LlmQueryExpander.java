package io.casehub.rag.hyde;

import dev.langchain4j.model.chat.ChatModel;
import io.casehub.rag.QueryExpander;
import io.casehub.rag.RetrievalQuery;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
@IfBuildProperty(name = "casehub.rag.hyde.mode", stringValue = "llm", enableIfMissing = true)
public class LlmQueryExpander implements QueryExpander {

    static final String DEFAULT_PROMPT =
        "Given the question below, write a short passage (3-5 sentences) "
            + "that would directly answer it. Write as if the passage comes from "
            + "an authoritative document. Do not include the question itself.\n\n"
            + "Question: %s\n\nPassage:";

    private final ChatModel chatModel;
    private final HydeConfig config;

    @Inject
    public LlmQueryExpander(ChatModel chatModel, HydeConfig config) {
        this.chatModel = chatModel;
        this.config = config;
    }

    @Override
    public RetrievalQuery expand(RetrievalQuery query) {
        String prompt = config.promptTemplate().orElse(DEFAULT_PROMPT)
            .formatted(query.text());
        String hypothetical = chatModel.chat(prompt);
        return query.withExpansion(hypothetical);
    }
}

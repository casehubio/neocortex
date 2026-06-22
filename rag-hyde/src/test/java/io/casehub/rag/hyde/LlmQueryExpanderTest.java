package io.casehub.rag.hyde;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.casehub.rag.RetrievalQuery;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class LlmQueryExpanderTest {

    @Test
    void expandCallsChatModelWithDefaultPrompt() {
        var capturedPrompt = new String[1];
        ChatModel mockModel = stubChatModel(prompt -> {
            capturedPrompt[0] = prompt;
            return "Diabetes is a chronic condition characterized by elevated blood sugar.";
        });

        var expander = new LlmQueryExpander(mockModel, stubConfig(Optional.empty()));
        var result = expander.expand(RetrievalQuery.of("what is diabetes?"));

        assertThat(result.text()).isEqualTo("what is diabetes?");
        assertThat(result.expandedText()).isEqualTo(
            "Diabetes is a chronic condition characterized by elevated blood sugar.");
        assertThat(capturedPrompt[0]).contains("what is diabetes?");
        assertThat(capturedPrompt[0]).contains("write a short passage");
    }

    @Test
    void expandUsesCustomPromptTemplate() {
        ChatModel mockModel = stubChatModel(prompt -> "custom response");
        var expander = new LlmQueryExpander(mockModel,
            stubConfig(Optional.of("Custom: %s")));
        var result = expander.expand(RetrievalQuery.of("test query"));

        assertThat(result.expandedText()).isEqualTo("custom response");
    }

    @Test
    void expandPreservesOriginalText() {
        ChatModel mockModel = stubChatModel(prompt -> "hypothetical passage");
        var expander = new LlmQueryExpander(mockModel, stubConfig(Optional.empty()));

        var query = RetrievalQuery.of("my question");
        var result = expander.expand(query);

        assertThat(result.text()).isEqualTo("my question");
        assertThat(result.searchText()).isEqualTo("hypothetical passage");
    }

    /**
     * Creates a ChatModel stub that intercepts via doChat,
     * extracts the user message text, and delegates to the handler.
     */
    private static ChatModel stubChatModel(Function<String, String> handler) {
        return new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                String userText = ((UserMessage) request.messages().get(0)).singleText();
                String response = handler.apply(userText);
                return ChatResponse.builder()
                    .aiMessage(AiMessage.from(response))
                    .build();
            }
        };
    }

    private static HydeConfig stubConfig(Optional<String> promptTemplate) {
        return new HydeConfig() {
            @Override public boolean enabled() { return true; }
            @Override public String mode() { return "llm"; }
            @Override public Optional<String> promptTemplate() { return promptTemplate; }
            @Override public Optional<String> template() { return Optional.empty(); }
        };
    }
}

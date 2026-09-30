package io.casehub.neocortex.cognition.prompt;

import io.casehub.neocortex.cognition.core.CognitionConfig;
import io.casehub.eidos.api.AgentConstraint;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.AgentPromptContext;
import io.casehub.eidos.api.ConstraintSeverity;
import io.casehub.eidos.api.SystemPromptRenderer;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

public class CognitiveSystemPromptRenderer implements SystemPromptRenderer {

    private final CognitionConfig config;

    public CognitiveSystemPromptRenderer(CognitionConfig config) {
        this.config = config;
    }

    @Override
    public RenderedPrompt render(AgentDescriptor descriptor, AgentPromptContext context) {
        var sb = new StringBuilder();

        sb.append("# ").append(descriptor.name()).append("\n\n");

        var voice = descriptor.voice();
        if (voice != null) {
            if (descriptor.briefing() != null && !descriptor.briefing().isBlank()) {
                sb.append(descriptor.briefing()).append("\n\n");
            }

            if (descriptor.disposition() != null) {
                renderPersonality(sb, descriptor.disposition());
            }

            if (voice.personas() != null && !voice.personas().isEmpty()) {
                if (hasBaseVoiceContent(voice)) {
                    sb.append("## Voice (base)\n\n");
                    renderVoiceFields(sb, voice);
                }
                for (var entry : voice.personas().entrySet()) {
                    sb.append("## Voice: ").append(entry.getKey()).append("\n\n");
                    renderVoiceFields(sb, entry.getValue());
                }
            } else {
                sb.append("## Voice\n\n");
                renderVoiceFields(sb, voice);
            }
        } else {
            if (descriptor.briefing() != null && !descriptor.briefing().isBlank()) {
                sb.append(descriptor.briefing()).append("\n\n");
            }
        }

        var hardConstraints = descriptor.constraints() != null
                              ? descriptor.constraints().stream()
                                          .filter(c -> c.severity() == ConstraintSeverity.HARD)
                                          .toList()
                              : List.<AgentConstraint>of();
        if (!hardConstraints.isEmpty()) {
            sb.append("## Prime Directives\n\n");
            for (var c : hardConstraints) {
                sb.append("- ").append(c.description()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("## Your Mind\n\n");
        sb.append(CognitivePreambleGenerator.generate(config)).append("\n");

        var content = sb.toString().stripTrailing();
        var hash    = shortHash(content);
        return new RenderedPrompt(content, RenderFormat.MARKDOWN, hash, hash, false);
    }

    private static void renderPersonality(StringBuilder sb, io.casehub.eidos.api.AgentDisposition disposition) {
        sb.append("## Personality\n\n");
        if (disposition.dispositionProfile() != null && !disposition.dispositionProfile().isEmpty()) {
            var sorted = disposition.dispositionProfile().stream()
                                    .sorted(java.util.Comparator.comparingDouble(io.casehub.eidos.api.DispositionValue::weight).reversed())
                                    .toList();
            for (var trait : sorted) {
                sb.append("- ").append(trait.term())
                  .append(" (").append(String.format("%.1f", trait.weight())).append(")\n");
            }
        }
        sb.append("\n");
    }

    private static void renderVoiceFields(StringBuilder sb, io.casehub.eidos.api.AgentVoiceProfile voice) {
        if (voice.description() != null && !voice.description().isBlank()) {
            sb.append(voice.description()).append("\n");
        }
        if (voice.register() != null) {
            sb.append("Register: ").append(voice.register()).append("\n");
        }
        if (voice.accent() != null) {
            sb.append("Accent: ").append(voice.accent()).append("\n");
        }
        if (voice.catchphrases() != null && !voice.catchphrases().isEmpty()) {
            sb.append("Signature catchphrases: ").append(
                    voice.catchphrases().stream()
                         .map(c -> "\"" + c + "\"")
                         .collect(Collectors.joining(", "))
            ).append("\n");
        }
        if (voice.speechPatterns() != null && !voice.speechPatterns().isEmpty()) {
            sb.append("Speech patterns: ").append(String.join(", ", voice.speechPatterns())).append("\n");
        }
        if (voice.vocabularyUses() != null && !voice.vocabularyUses().isEmpty()) {
            sb.append("Vocabulary: ").append(
                    voice.vocabularyUses().stream()
                         .map(w -> "\"" + w + "\"")
                         .collect(Collectors.joining(", "))
            ).append("\n");
        }
        if (voice.vocabularyAvoids() != null && !voice.vocabularyAvoids().isEmpty()) {
            sb.append("Avoids: ").append(
                    voice.vocabularyAvoids().stream()
                         .map(w -> "\"" + w + "\"")
                         .collect(Collectors.joining(", "))
            ).append("\n");
        }
        if (voice.quirks() != null && !voice.quirks().isEmpty()) {
            sb.append("Quirks: ").append(String.join(", ", voice.quirks())).append("\n");
        }
        sb.append("\n");
    }

    private static boolean hasBaseVoiceContent(io.casehub.eidos.api.AgentVoiceProfile voice) {
        return voice.register() != null || voice.accent() != null
               || (voice.catchphrases() != null && !voice.catchphrases().isEmpty())
               || (voice.speechPatterns() != null && !voice.speechPatterns().isEmpty())
               || (voice.vocabularyUses() != null && !voice.vocabularyUses().isEmpty())
               || (voice.vocabularyAvoids() != null && !voice.vocabularyAvoids().isEmpty())
               || (voice.quirks() != null && !voice.quirks().isEmpty());
    }

    private static String shortHash(String input) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes())).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}

package io.casehub.neocortex.cognition.innerlife;

import org.jspecify.annotations.Nullable;

public sealed interface InnerLifeTick {
    record Silent(@Nullable String reason) implements InnerLifeTick {}
    record Initiated(String content, @Nullable String channelHint,
                     double motivationScore) implements InnerLifeTick {}
}

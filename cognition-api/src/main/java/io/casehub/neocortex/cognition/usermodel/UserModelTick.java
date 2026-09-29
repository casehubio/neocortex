package io.casehub.neocortex.cognition.usermodel;

import org.jspecify.annotations.Nullable;

public sealed interface UserModelTick {

    record Unchanged(@Nullable String reason) implements UserModelTick {}

    record Updated(UserProfile profile) implements UserModelTick {}

    record Synthesised(UserProfile profile, @Nullable UserProfile previousProfile)
            implements UserModelTick {}
}

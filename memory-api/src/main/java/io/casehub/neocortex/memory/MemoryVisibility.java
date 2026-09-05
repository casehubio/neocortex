package io.casehub.neocortex.memory;

import java.util.Set;

public final class MemoryVisibility {

    private MemoryVisibility() {}

    public static boolean isVisible(String callerPrincipalId,
                                    String memoryPrincipalId,
                                    Set<String> sharedWith) {
        if (callerPrincipalId == null) return true;
        if (memoryPrincipalId == null) return true;
        if (memoryPrincipalId.equals(callerPrincipalId)) return true;
        return sharedWith != null && sharedWith.contains(callerPrincipalId);
    }

    public static boolean isVisible(String callerPrincipalId, Memory memory) {
        return isVisible(callerPrincipalId, memory.principalId(), memory.sharedWith());
    }
}

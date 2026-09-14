package io.casehub.neocortex.mindmap;

public final class MutationContext {

    private static final ThreadLocal<String> SOURCE = ThreadLocal.withInitial(() -> "manual");

    private MutationContext() {}

    public static void set(String source) { SOURCE.set(source); }
    public static String get() { return SOURCE.get(); }
    public static void clear() { SOURCE.remove(); }
}

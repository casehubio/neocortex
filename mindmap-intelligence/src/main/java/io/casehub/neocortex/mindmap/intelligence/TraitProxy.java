package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.mindmap.MindMapNode;

import java.lang.reflect.Proxy;

public final class TraitProxy {

    private TraitProxy() {}

    @SuppressWarnings("unchecked")
    public static <T> T as(MindMapNode node, Class<T> traitInterface) {
        if (!traitInterface.isInterface()) {
            throw new IllegalArgumentException(
                "Trait must be an interface: " + traitInterface.getName());
        }
        return (T) Proxy.newProxyInstance(
            traitInterface.getClassLoader(),
            new Class<?>[] { traitInterface },
            new TraitInvocationHandler(node, traitInterface));
    }
}

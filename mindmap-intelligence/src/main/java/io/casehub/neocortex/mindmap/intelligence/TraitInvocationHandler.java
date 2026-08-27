package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.mindmap.MindMapNode;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Objects;
import java.util.Optional;

final class TraitInvocationHandler implements InvocationHandler {

    private final MindMapNode node;
    private final Class<?>    traitInterface;

    TraitInvocationHandler(MindMapNode node, Class<?> traitInterface) {
        this.node           = node;
        this.traitInterface = traitInterface;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        return switch (method.getName()) {
            case "toString" -> traitInterface.getSimpleName() + "[" + node.name() + "]";
            case "hashCode" -> Objects.hash(node.id(), traitInterface);
            case "equals"   -> args[0] != null
                && Proxy.isProxyClass(args[0].getClass())
                && Proxy.getInvocationHandler(args[0]) instanceof TraitInvocationHandler other
                && Objects.equals(node.id(), other.node.id())
                && Objects.equals(traitInterface, other.traitInterface);
            default -> {
                Class<?> returnType = method.getReturnType();
                Optional<String> value = node.property(method.getName());
                if (returnType == Optional.class) {
                    yield value;
                } else if (returnType == String.class) {
                    yield value.orElse(null);
                } else if (returnType == Integer.class) {
                    yield value.map(Integer::valueOf).orElse(null);
                } else if (returnType == Long.class) {
                    yield value.map(Long::valueOf).orElse(null);
                } else if (returnType == Double.class) {
                    yield value.map(Double::valueOf).orElse(null);
                } else {
                    yield value.orElse(null);
                }
            }
        };
    }
}

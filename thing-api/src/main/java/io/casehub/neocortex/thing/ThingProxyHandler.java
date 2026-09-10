package io.casehub.neocortex.thing;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Objects;
import java.util.Optional;

final class ThingProxyHandler implements InvocationHandler {

    private final Thing thing;
    private final Class<?> traitInterface;

    private ThingProxyHandler(Thing thing, Class<?> traitInterface) {
        this.thing = thing;
        this.traitInterface = traitInterface;
    }

    @SuppressWarnings("unchecked")
    static <T> T createProxy(Thing thing, Class<T> traitInterface) {
        return (T) Proxy.newProxyInstance(
            traitInterface.getClassLoader(),
            new Class<?>[] { traitInterface },
            new ThingProxyHandler(thing, traitInterface));
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        return switch (method.getName()) {
            case "toString" -> traitInterface.getSimpleName() + "[" + thing.id() + "]";
            case "hashCode" -> Objects.hash(thing.id(), traitInterface);
            case "equals"   -> args[0] != null
                && Proxy.isProxyClass(args[0].getClass())
                && Proxy.getInvocationHandler(args[0]) instanceof ThingProxyHandler other
                && Objects.equals(thing.id(), other.thing.id())
                && Objects.equals(traitInterface, other.traitInterface);
            default -> {
                Class<?> returnType = method.getReturnType();
                Optional<String> value = thing.property(method.getName());
                if (returnType == Optional.class) {
                    yield value;
                } else if (value.isEmpty()) {
                    yield primitiveDefault(returnType);
                } else {
                    yield coerce(value.get(), returnType);
                }
            }
        };
    }

    private static Object coerce(String value, Class<?> returnType) {
        if (returnType == String.class) return value;
        if (returnType == Integer.class || returnType == int.class) return Integer.parseInt(value);
        if (returnType == Long.class || returnType == long.class) return Long.parseLong(value);
        if (returnType == Double.class || returnType == double.class) return Double.parseDouble(value);
        if (returnType == Boolean.class || returnType == boolean.class) return Boolean.parseBoolean(value);
        return value;
    }

    private static Object primitiveDefault(Class<?> returnType) {
        if (returnType == int.class) return 0;
        if (returnType == long.class) return 0L;
        if (returnType == double.class) return 0.0;
        if (returnType == boolean.class) return false;
        return null;
    }
}

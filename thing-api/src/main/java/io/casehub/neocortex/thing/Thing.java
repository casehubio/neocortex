package io.casehub.neocortex.thing;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface Thing {

    String id();
    String name();
    String type();

    Optional<String> property(String key);
    Map<String, String> properties();

    Set<String> traits();

    default boolean is(String typeName) {
        return typeName.equals(type()) || traits().contains(typeName);
    }

    default <T> T as(Class<T> traitInterface) {
        if (!traitInterface.isInterface()) {
            throw new IllegalArgumentException(
                "Trait must be an interface: " + traitInterface.getName());
        }
        return ThingProxyHandler.createProxy(this, traitInterface);
    }
}

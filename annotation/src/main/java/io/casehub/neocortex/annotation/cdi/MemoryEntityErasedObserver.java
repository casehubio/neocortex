package io.casehub.neocortex.annotation.cdi;

import io.casehub.neocortex.annotation.EntityAnnotationIndex;
import io.casehub.neocortex.memory.MemoryEntityErased;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class MemoryEntityErasedObserver {

    private final EntityAnnotationIndex index;

    @Inject
    public MemoryEntityErasedObserver(EntityAnnotationIndex index) {
        this.index = index;
    }

    MemoryEntityErasedObserver() {
        this.index = null;
    }

    void onErased(@Observes MemoryEntityErased event) {
        switch (event) {
            case MemoryEntityErased.ByEntity e ->
                index.removeByOwnerSubject("memory", e.subject().id(), e.tenantId());
            case MemoryEntityErased.ByRequest e ->
                index.removeByOwnerSubject("memory", e.subject().id(), e.tenantId());
            case MemoryEntityErased.CrossTenant e ->
                e.tenantIds().forEach(tid ->
                    index.removeByOwnerSubject("memory", e.subject().id(), tid));
        }
    }
}

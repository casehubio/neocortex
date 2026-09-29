package io.casehub.neocortex.summarisation;

import java.util.List;

@FunctionalInterface
public interface LevelEventCompactor<E> {
    List<LevelEvent<E>> compact(List<LevelEvent<E>> events);
}

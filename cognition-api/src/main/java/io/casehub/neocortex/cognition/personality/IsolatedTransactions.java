package io.casehub.neocortex.cognition.personality;

@FunctionalInterface
public interface IsolatedTransactions {
    void requiresNew(Runnable work);
}

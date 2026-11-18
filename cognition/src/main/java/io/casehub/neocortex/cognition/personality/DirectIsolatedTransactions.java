package io.casehub.neocortex.cognition.personality;

public final class DirectIsolatedTransactions implements IsolatedTransactions {
    @Override
    public void requiresNew(Runnable work) {
        work.run();
    }
}

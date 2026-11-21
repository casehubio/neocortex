package io.casehub.neocortex.rag.tracking;

/** Test access to {@link RetentionScheduler#started()}. */
public final class RetentionSchedulerAccess {

    private RetentionSchedulerAccess() {}

    public static boolean started(RetentionScheduler scheduler) {
        return scheduler.started();
    }
}

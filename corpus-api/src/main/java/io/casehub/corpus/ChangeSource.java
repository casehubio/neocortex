package io.casehub.corpus;

public interface ChangeSource {
    ChangeSet changesSince(String cursor);
    ChangeSet fullScan();
}

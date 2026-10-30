package com.example.reconciliation.model;

import java.io.Serializable;

/**
 * The DBS and Quantifeed records for one correlationId. One side is null when the record exists in only one system.
 */
public record RecordMatch(SourceRecord dbs, SourceRecord quantifeed) implements Serializable {

    public boolean isPair() {
        return dbs != null && quantifeed != null;
    }

    /** Either side's record, for reading the shared key fields. */
    public SourceRecord any() {
        return dbs != null ? dbs : quantifeed;
    }
}

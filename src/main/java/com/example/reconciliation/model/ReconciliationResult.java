package com.example.reconciliation.model;

/**
 * Final result for one correlationId. One side is null when the record exists in only one system.
 *
 * @param type              the check that found the break; null when MATCHED or when a record is missing
 * @param breakType         null when MATCHED
 * @param differenceDetails every differing field in that check, e.g. "quantity: 100 vs 90"; empty when MATCHED
 */
public record ReconciliationResult(
        SourceRecord dbs,
        SourceRecord quantifeed,
        Status status,
        ReconciliationType type,
        String breakType,
        String differenceDetails) {

    public enum Status {
        MATCHED,
        BREAK
    }

    /** Either side's record, for reading the shared key fields. */
    public SourceRecord any() {
        return dbs != null ? dbs : quantifeed;
    }
}

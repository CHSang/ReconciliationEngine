package com.example.reconciliation.model;

import java.math.BigDecimal;

/**
 * Final result for one correlationId. One side is null when the record exists in only one system.
 *
 * @param dbsValue          the type's main value: quantity (POSITION), amount (TRANSACTION) or balance (CASH)
 * @param difference        dbsValue - quantifeedValue; null when one side is missing
 * @param breakType         null when MATCHED
 * @param differenceDetails every differing field, e.g. "quantity: 100 vs 90"; empty when MATCHED
 */
public record ReconciliationResult(
        SourceRecord dbs,
        SourceRecord quantifeed,
        Status status,
        String breakType,
        BigDecimal dbsValue,
        BigDecimal quantifeedValue,
        BigDecimal difference,
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

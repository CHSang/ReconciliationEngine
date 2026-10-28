package com.example.reconciliation.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/**
 * One CSV row after normalization. Blank fields are null.
 *
 * @param rowNumber row in the CSV file, counting the header as row 1
 * @param original  the row's values exactly as read, for tracing results back to the file
 */
public record SourceRecord(
        SourceSystem source,
        String fileName,
        long rowNumber,
        String sourceBatchId,
        LocalDate businessDate,
        String accountId,
        String transactionId,
        String correlationId,
        String executionId,
        String securityId,
        String side,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal amount,
        String currency,
        BigDecimal balance,
        LocalDate settlementDate,
        Instant sourceCreatedAt,
        Map<String, String> original) {
}

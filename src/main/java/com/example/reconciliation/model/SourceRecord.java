package com.example.reconciliation.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One CSV row after normalization. Blank fields are null.
 *
 * @param rowNumber row in the CSV file, counting the header as row 1
 */
public record SourceRecord(
        SourceSystem source,
        String fileName,
        long rowNumber,
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
        Instant sourceCreatedAt) implements Serializable {
}

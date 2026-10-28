package com.example.reconciliation.normalizer;

import com.example.reconciliation.model.ReconciliationType;
import com.example.reconciliation.model.RecordError;
import com.example.reconciliation.model.SourceRecord;
import com.example.reconciliation.model.SourceSystem;
import org.apache.commons.csv.CSVRecord;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Validates and normalizes one CSV row. Same rules for both sources:
 * text is trimmed (blank = no value); sourceSystem, type, side and currency are uppercased;
 * numbers are exact BigDecimal; dates are yyyy-MM-dd; timestamps are ISO-8601 with offset, converted to UTC.
 * A row with any problem is rejected as a whole, and every problem in it is reported.
 */
public class RecordNormalizer {

    private static final List<String> REQUIRED =
            List.of("sourceSystem", "reconciliationType", "sourceBatchId", "businessDate", "accountId", "correlationId");

    /** Extra fields each type needs. */
    private static final Map<ReconciliationType, List<String>> REQUIRED_BY_TYPE = Map.of(
            ReconciliationType.POSITION, List.of("securityId", "quantity"),
            ReconciliationType.TRANSACTION, List.of("securityId", "side", "quantity", "price", "amount", "currency", "settlementDate"),
            ReconciliationType.CASH, List.of("currency", "balance"));

    private final SourceSystem source;
    private final String fileName;

    public RecordNormalizer(SourceSystem source, String fileName) {
        this.source = source;
        this.fileName = fileName;
    }

    /** Returns the normalized record, or null after adding the row's problems to {@code errors}. */
    public SourceRecord normalize(CSVRecord csv, List<RecordError> errors) {
        Row f = new Row(csv, errors);
        int errorsBefore = errors.size();

        if (!csv.isConsistent()) {
            f.error(null, "wrong number of columns: " + csv.size());
            return null;
        }

        f.parse("sourceSystem", v -> upperMatching(v, source.name()), "expected " + source);
        ReconciliationType type = f.parse("reconciliationType",
                v -> ReconciliationType.valueOf(v.toUpperCase(Locale.ROOT)), "must be POSITION, TRANSACTION or CASH");

        SourceRecord record = new SourceRecord(
                source, fileName, f.rowNumber(),
                f.text("sourceBatchId"),
                f.parse("businessDate", LocalDate::parse, "not a date in yyyy-MM-dd format"),
                type,
                f.text("accountId"),
                f.text("transactionId"),
                f.text("correlationId"),
                f.text("executionId"),
                f.text("referenceId"),
                f.text("securityId"),
                f.parse("side", v -> upperMatching(v, "BUY|SELL"), "must be BUY or SELL"),
                f.parse("quantity", BigDecimal::new, "not a number"),
                f.parse("price", BigDecimal::new, "not a number"),
                f.parse("amount", BigDecimal::new, "not a number"),
                f.parse("currency", v -> upperMatching(v, "[A-Z]{3}"), "must be a 3-letter currency code"),
                f.parse("balance", BigDecimal::new, "not a number"),
                f.parse("settlementDate", LocalDate::parse, "not a date in yyyy-MM-dd format"),
                f.parse("sourceCreatedAt", v -> OffsetDateTime.parse(v).toInstant(),
                        "not an ISO-8601 timestamp with offset, e.g. 2026-10-28T09:30:00+08:00"),
                csv.toMap());

        // Report blank required fields. Filled-in but invalid values already have an error.
        for (String field : REQUIRED) {
            f.required(field, "required");
        }
        if (type != null) {
            for (String field : REQUIRED_BY_TYPE.get(type)) {
                f.required(field, "required for " + type);
            }
        }

        return errors.size() == errorsBefore ? record : null;
    }

    /** Uppercases the value and checks it against a regex; throws if it doesn't match. */
    private static String upperMatching(String value, String regex) {
        String upper = value.toUpperCase(Locale.ROOT);
        if (!upper.matches(regex)) {
            throw new IllegalArgumentException();
        }
        return upper;
    }

    /** One row's values, plus the error list to report problems to. */
    private final class Row {

        private final CSVRecord csv;
        private final List<RecordError> errors;

        Row(CSVRecord csv, List<RecordError> errors) {
            this.csv = csv;
            this.errors = errors;
        }

        /** Row number in the file, counting the header as row 1. */
        long rowNumber() {
            return csv.getRecordNumber() + 1;
        }

        /** Trimmed value, or null if blank. */
        String text(String field) {
            String value = csv.get(field).trim();
            return value.isEmpty() ? null : value;
        }

        /** Parses a non-blank value; on failure records {@code reason} and returns null. */
        <T> T parse(String field, Function<String, T> parser, String reason) {
            String value = text(field);
            if (value == null) {
                return null;
            }
            try {
                return parser.apply(value);
            } catch (RuntimeException e) {
                error(field, reason);
                return null;
            }
        }

        void required(String field, String reason) {
            if (text(field) == null) {
                error(field, reason);
            }
        }

        void error(String field, String reason) {
            errors.add(new RecordError(fileName, rowNumber(), field, field == null ? null : csv.get(field), reason));
        }
    }
}

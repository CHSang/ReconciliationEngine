package com.example.reconciliation.normalizer;

import com.example.reconciliation.model.ReconciliationType;
import com.example.reconciliation.model.SourceRecord;
import com.example.reconciliation.model.SourceSystem;
import org.apache.commons.csv.CSVRecord;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.function.Function;

/**
 * Validates and normalizes one CSV row. Same rules for both sources:
 * text is trimmed (blank = no value); sourceSystem, type, side and currency are uppercased;
 * numbers are exact BigDecimal; dates are yyyy-MM-dd; timestamps are ISO-8601 with offset, converted to UTC.
 * A row with any invalid value is skipped. Blank values are not checked: input files must fill in every field
 * a record's type uses.
 */
public class RecordNormalizer {

    private final SourceSystem source;
    private final String fileName;

    public RecordNormalizer(SourceSystem source, String fileName) {
        this.source = source;
        this.fileName = fileName;
    }

    /** Returns the normalized record, or null if the row is invalid. */
    public SourceRecord normalize(CSVRecord csv) {
        if (!csv.isConsistent()) {
            return null;
        }
        Row f = new Row(csv);

        f.parse("sourceSystem", v -> upperMatching(v, source.name()));
        ReconciliationType type = f.parse("reconciliationType", v -> ReconciliationType.valueOf(v.toUpperCase(Locale.ROOT)));

        SourceRecord record = new SourceRecord(
                source, fileName, csv.getRecordNumber() + 1, // header is row 1
                f.text("sourceBatchId"),
                f.parse("businessDate", LocalDate::parse),
                type,
                f.text("accountId"),
                f.text("transactionId"),
                f.text("correlationId"),
                f.text("executionId"),
                f.text("securityId"),
                f.parse("side", v -> upperMatching(v, "BUY|SELL")),
                f.parse("quantity", BigDecimal::new),
                f.parse("price", BigDecimal::new),
                f.parse("amount", BigDecimal::new),
                f.parse("currency", v -> upperMatching(v, "[A-Z]{3}")),
                f.parse("balance", BigDecimal::new),
                f.parse("settlementDate", LocalDate::parse),
                f.parse("sourceCreatedAt", v -> OffsetDateTime.parse(v).toInstant()),
                csv.toMap());

        return f.valid ? record : null;
    }

    /** Uppercases the value and checks it against a regex; throws if it doesn't match. */
    private static String upperMatching(String value, String regex) {
        String upper = value.toUpperCase(Locale.ROOT);
        if (!upper.matches(regex)) {
            throw new IllegalArgumentException();
        }
        return upper;
    }

    /** One row's values; {@code valid} turns false on the first value that doesn't parse. */
    private static final class Row {

        private final CSVRecord csv;
        private boolean valid = true;

        Row(CSVRecord csv) {
            this.csv = csv;
        }

        /** Trimmed value, or null if blank. */
        String text(String field) {
            String value = csv.get(field).trim();
            return value.isEmpty() ? null : value;
        }

        /** Parses a non-blank value; marks the row invalid if it doesn't parse. */
        <T> T parse(String field, Function<String, T> parser) {
            String value = text(field);
            if (value == null) {
                return null;
            }
            try {
                return parser.apply(value);
            } catch (RuntimeException e) {
                valid = false;
                return null;
            }
        }
    }
}

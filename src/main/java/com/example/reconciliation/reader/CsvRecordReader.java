package com.example.reconciliation.reader;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Reads a source CSV (UTF-8, comma-separated, with a header row). Values are not changed here. */
@Component
public class CsvRecordReader {

    public static final List<String> COLUMNS = List.of(
            "sourceSystem", "sourceBatchId", "businessDate", "accountId", "transactionId",
            "correlationId", "executionId", "securityId", "side", "quantity", "price", "amount",
            "currency", "balance", "settlementDate", "sourceCreatedAt");

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get();

    /** Returns all data rows. Fails (stopping the run) if the file can't be read or a column is missing. */
    public List<CSVRecord> read(Path path) throws IOException {
        try (CSVParser parser = FORMAT.parse(Files.newBufferedReader(path))) {
            List<String> missing = COLUMNS.stream().filter(c -> !parser.getHeaderNames().contains(c)).toList();
            if (!missing.isEmpty()) {
                throw new IllegalStateException(path.getFileName() + " is missing columns: " + missing);
            }
            return parser.getRecords();
        }
    }
}

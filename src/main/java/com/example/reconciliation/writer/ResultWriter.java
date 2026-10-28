package com.example.reconciliation.writer;

import com.example.reconciliation.model.ReconciliationResult;
import com.example.reconciliation.model.SourceRecord;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Writes reconciliation-results.csv. The file is written to a temporary file first and moved into place only
 * when complete, so a failed run never leaves a partial output. The old file is replaced, never appended to.
 */
@Component
public class ResultWriter {

    private static final String[] HEADER = {
            "runId", "businessDate", "reconciliationType", "correlationId", "accountId", "referenceId", "securityId",
            "currency", "dbsExecutionId", "quantifeedExecutionId", "dbsSourceRowNumber", "quantifeedSourceRowNumber",
            "dbsValue", "quantifeedValue", "difference", "status", "breakType", "differenceDetails"};

    public void write(String runId, List<ReconciliationResult> results, Path file) throws IOException {
        Path dir = file.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        Path tmp = dir.resolve(file.getFileName() + ".tmp");
        try {
            try (CSVPrinter csv = CSVFormat.DEFAULT.builder().setHeader(HEADER).get().print(Files.newBufferedWriter(tmp))) {
                for (ReconciliationResult r : results) {
                    SourceRecord any = r.any();
                    csv.printRecord(runId, any.businessDate(), any.type(), any.correlationId(), any.accountId(),
                            any.referenceId(), any.securityId(), any.currency(),
                            r.dbs() == null ? null : r.dbs().executionId(),
                            r.quantifeed() == null ? null : r.quantifeed().executionId(),
                            r.dbs() == null ? null : r.dbs().rowNumber(),
                            r.quantifeed() == null ? null : r.quantifeed().rowNumber(),
                            number(r.dbsValue()), number(r.quantifeedValue()), number(r.difference()),
                            r.status(), r.breakType(), r.differenceDetails());
                }
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /** Stable number format: no trailing zeros, no scientific notation (100.50 -> 100.5, 0.00 -> 0). */
    private static String number(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }
}

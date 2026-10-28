package com.example.reconciliation.matching;

import com.example.reconciliation.model.RecordMatch;
import com.example.reconciliation.model.SourceRecord;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Pairs DBS and Quantifeed records one-to-one by correlationId.
 * The business date is not part of the key: DBS may book a trade on a later date (at settlement) than Quantifeed.
 * executionId differs per system and is kept for tracing only.
 */
@Component
public class RecordMatcher {

    /** Returns one match per correlationId: DBS records first (file order), then Quantifeed-only records. */
    public List<RecordMatch> match(List<SourceRecord> dbs, List<SourceRecord> quantifeed) {
        Map<String, SourceRecord> dbsById = byId(dbs);
        Map<String, SourceRecord> quantifeedById = byId(quantifeed);

        List<RecordMatch> matches = new ArrayList<>();
        dbsById.forEach((id, record) -> matches.add(new RecordMatch(record, quantifeedById.get(id))));
        quantifeedById.forEach((id, record) -> {
            if (!dbsById.containsKey(id)) {
                matches.add(new RecordMatch(null, record));
            }
        });
        return matches;
    }

    /** correlationId must be unique within a file; a duplicate stops the run rather than overwriting a record. */
    private static Map<String, SourceRecord> byId(List<SourceRecord> records) {
        return records.stream().collect(Collectors.toMap(
                SourceRecord::correlationId,
                r -> r,
                (a, b) -> {
                    throw new IllegalStateException("Duplicate correlationId " + a.correlationId() + " in "
                            + a.fileName() + " rows " + a.rowNumber() + " and " + b.rowNumber());
                },
                LinkedHashMap::new));
    }
}

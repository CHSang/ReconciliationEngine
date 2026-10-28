package com.example.reconciliation.matching;

import com.example.reconciliation.model.RecordMatch;
import com.example.reconciliation.model.SourceRecord;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Pairs DBS and Quantifeed records one-to-one by correlationId, within the same business date.
 * Only correlationId is used to match: executionId differs per system and is kept for tracing only.
 */
@Component
public class RecordMatcher {

    private record Key(LocalDate businessDate, String correlationId) {
    }

    /** Returns one match per key: DBS records first (file order), then Quantifeed-only records. */
    public List<RecordMatch> match(List<SourceRecord> dbs, List<SourceRecord> quantifeed) {
        Map<Key, SourceRecord> dbsByKey = byKey(dbs);
        Map<Key, SourceRecord> quantifeedByKey = byKey(quantifeed);

        List<RecordMatch> matches = new ArrayList<>();
        dbsByKey.forEach((key, record) -> matches.add(new RecordMatch(record, quantifeedByKey.get(key))));
        quantifeedByKey.forEach((key, record) -> {
            if (!dbsByKey.containsKey(key)) {
                matches.add(new RecordMatch(null, record));
            }
        });
        return matches;
    }

    /** correlationId must be unique per business date; a duplicate stops the run rather than overwriting a record. */
    private static Map<Key, SourceRecord> byKey(List<SourceRecord> records) {
        return records.stream().collect(Collectors.toMap(
                r -> new Key(r.businessDate(), r.correlationId()),
                r -> r,
                (a, b) -> {
                    throw new IllegalStateException("Duplicate correlationId " + a.correlationId() + " in "
                            + a.fileName() + " rows " + a.rowNumber() + " and " + b.rowNumber());
                },
                LinkedHashMap::new));
    }
}

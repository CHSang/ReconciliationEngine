package com.example.reconciliation.batch;

import com.example.reconciliation.model.RecordMatch;
import com.example.reconciliation.model.ReconciliationResult;
import com.example.reconciliation.model.SourceRecord;
import com.example.reconciliation.model.SourceSystem;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Data passed between the job's steps during the single run in this process. */
@Component
public class RunData {

    private final Map<SourceSystem, List<SourceRecord>> records = new EnumMap<>(SourceSystem.class);
    private List<RecordMatch> matches = List.of();
    private List<ReconciliationResult> results = List.of();

    public void setRecords(SourceSystem source, List<SourceRecord> sourceRecords) {
        records.put(source, List.copyOf(sourceRecords));
    }

    public List<SourceRecord> records(SourceSystem source) {
        return records.get(source);
    }

    public List<RecordMatch> matches() {
        return matches;
    }

    public void setMatches(List<RecordMatch> matches) {
        this.matches = List.copyOf(matches);
    }

    public List<ReconciliationResult> results() {
        return results;
    }

    public void setResults(List<ReconciliationResult> results) {
        this.results = List.copyOf(results);
    }
}

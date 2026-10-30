package com.example.reconciliation.batch;

import com.example.reconciliation.model.RecordMatch;
import com.example.reconciliation.model.ReconciliationResult;
import com.example.reconciliation.model.SourceRecord;
import com.example.reconciliation.model.SourceSystem;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.item.ExecutionContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Data passed between the job's steps, kept in the job's execution context.
 * Spring Batch saves that context to the run history after each step and restores it when a failed run resumes,
 * so later steps still get the data from steps that finished before the failure.
 */
public final class RunData {

    private static final String RECORDS_KEY = "records.";
    private static final String MATCHES_KEY = "matches";
    private static final String RESULTS_KEY = "results";

    private final ExecutionContext context;

    private RunData(ExecutionContext context) {
        this.context = context;
    }

    public static RunData of(ChunkContext chunkContext) {
        return new RunData(chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext());
    }

    public void setRecords(SourceSystem source, List<SourceRecord> sourceRecords) {
        context.put(RECORDS_KEY + source, new ArrayList<>(sourceRecords));
    }

    public List<SourceRecord> records(SourceSystem source) {
        return get(RECORDS_KEY + source);
    }

    public void setMatches(List<RecordMatch> matches) {
        context.put(MATCHES_KEY, new ArrayList<>(matches));
    }

    public List<RecordMatch> matches() {
        return get(MATCHES_KEY);
    }

    public void setResults(List<ReconciliationResult> results) {
        context.put(RESULTS_KEY, new ArrayList<>(results));
    }

    public List<ReconciliationResult> results() {
        return get(RESULTS_KEY);
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> get(String key) {
        Object value = context.get(key);
        if (value == null) {
            throw new IllegalStateException("No '" + key + "' data from an earlier step");
        }
        return List.copyOf((List<T>) value);
    }
}

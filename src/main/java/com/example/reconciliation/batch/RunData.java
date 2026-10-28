package com.example.reconciliation.batch;

import com.example.reconciliation.model.RecordError;
import com.example.reconciliation.model.SourceRecord;
import com.example.reconciliation.model.SourceSystem;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Data passed between the job's steps during the single run in this process. */
@Component
public class RunData {

    /** One source after reading and normalizing. rowsRead = records + rejectedRows. */
    public record Loaded(String fileName, int rowsRead, List<SourceRecord> records, int rejectedRows, List<RecordError> errors) {
    }

    private final Map<SourceSystem, Loaded> sources = new EnumMap<>(SourceSystem.class);

    public void put(SourceSystem source, Loaded loaded) {
        sources.put(source, loaded);
    }

    public Loaded get(SourceSystem source) {
        return sources.get(source);
    }
}

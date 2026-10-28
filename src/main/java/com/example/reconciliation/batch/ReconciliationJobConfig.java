package com.example.reconciliation.batch;

import com.example.reconciliation.config.ReconciliationProperties;
import com.example.reconciliation.model.RecordError;
import com.example.reconciliation.model.SourceRecord;
import com.example.reconciliation.model.SourceSystem;
import com.example.reconciliation.normalizer.RecordNormalizer;
import com.example.reconciliation.reader.CsvRecordReader;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reconciliation job: check input files exist, then read and normalize both sources.
 * Matching, comparison and export steps come in later phases.
 */
@Configuration
public class ReconciliationJobConfig {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationJobConfig.class);

    @Bean
    public Job reconciliationJob(JobRepository jobRepository, Step validateInputStep, Step normalizeInputStep) {
        return new JobBuilder("reconciliationJob", jobRepository)
                .start(validateInputStep)
                .next(normalizeInputStep)
                .build();
    }

    @Bean
    public Step validateInputStep(JobRepository jobRepository,
                                  PlatformTransactionManager transactionManager,
                                  ReconciliationProperties properties) {
        return new StepBuilder("validateInputStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    requireReadableFile("DBS", properties.input().dbs());
                    requireReadableFile("Quantifeed", properties.input().quantifeed());
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Bean
    public Step normalizeInputStep(JobRepository jobRepository,
                                   PlatformTransactionManager transactionManager,
                                   ReconciliationProperties properties,
                                   CsvRecordReader reader,
                                   RunData runData) {
        return new StepBuilder("normalizeInputStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    runData.put(SourceSystem.DBS, load(SourceSystem.DBS, properties.input().dbs(), reader));
                    runData.put(SourceSystem.QUANTIFEED, load(SourceSystem.QUANTIFEED, properties.input().quantifeed(), reader));
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    private static RunData.Loaded load(SourceSystem source, Path path, CsvRecordReader reader) throws IOException {
        String fileName = path.getFileName().toString();
        RecordNormalizer normalizer = new RecordNormalizer(source, fileName);

        List<CSVRecord> rows = reader.read(path);
        List<SourceRecord> records = new ArrayList<>();
        List<RecordError> errors = new ArrayList<>();
        int rejectedRows = 0;

        for (CSVRecord row : rows) {
            SourceRecord record = normalizer.normalize(row, errors);
            if (record != null) {
                records.add(record);
            } else {
                rejectedRows++;
            }
        }

        log.info("{} ({}): rows read {}, normalized {}, rejected {}",
                source, fileName, rows.size(), records.size(), rejectedRows);
        for (RecordError e : errors) {
            if (e.field() == null) {
                log.warn("Rejected {} row {}: {}", e.fileName(), e.rowNumber(), e.reason());
            } else {
                log.warn("Rejected {} row {}: {} = '{}': {}", e.fileName(), e.rowNumber(), e.field(), e.value(), e.reason());
            }
        }
        return new RunData.Loaded(fileName, rows.size(), records, rejectedRows, errors);
    }

    private static void requireReadableFile(String source, Path path) {
        if (path == null) {
            throw new IllegalStateException(source + " input path is not configured");
        }
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IllegalStateException(source + " input file not found or not readable: " + path.toAbsolutePath());
        }
        log.info("{} input file: {}", source, path.toAbsolutePath());
    }
}

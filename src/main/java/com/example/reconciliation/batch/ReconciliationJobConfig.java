package com.example.reconciliation.batch;

import com.example.reconciliation.config.ReconciliationProperties;
import com.example.reconciliation.matching.RecordMatcher;
import com.example.reconciliation.model.SourceRecord;
import com.example.reconciliation.model.SourceSystem;
import com.example.reconciliation.normalizer.RecordNormalizer;
import com.example.reconciliation.reader.CsvRecordReader;
import com.example.reconciliation.service.RecordComparator;
import com.example.reconciliation.writer.ResultWriter;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reconciliation job, in order:
 * check input files -> read and normalize both sources -> match by correlationId -> compare -> write results CSV.
 * A failing step is retried a few times (see {@link #withRetry}). Each step's output is saved with the run
 * (see {@link RunData}), so a run that still fails can resume at the step that failed.
 */
@Configuration
public class ReconciliationJobConfig {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationJobConfig.class);

    private final int maxAttempts;
    private final RetryTemplate retryTemplate;

    public ReconciliationJobConfig(ReconciliationProperties properties) {
        this.maxAttempts = properties.retry().maxAttempts();
        this.retryTemplate = RetryTemplate.builder()
                .maxAttempts(maxAttempts)
                .fixedBackoff(properties.retry().delay())
                .build();
    }

    @Bean
    public Job reconciliationJob(JobRepository jobRepository, Step validateInputStep, Step normalizeInputStep,
                                 Step matchStep, Step compareStep, Step exportStep) {
        return new JobBuilder("reconciliationJob", jobRepository)
                .start(validateInputStep)
                .next(normalizeInputStep)
                .next(matchStep)
                .next(compareStep)
                .next(exportStep)
                .build();
    }

    @Bean
    public Step validateInputStep(JobRepository jobRepository,
                                  PlatformTransactionManager transactionManager,
                                  ReconciliationProperties properties) {
        return new StepBuilder("validateInputStep", jobRepository)
                .tasklet(withRetry((contribution, chunkContext) -> {
                    requireReadableFile("DBS", properties.input().dbs());
                    requireReadableFile("Quantifeed", properties.input().quantifeed());
                    return RepeatStatus.FINISHED;
                }), transactionManager)
                .build();
    }

    @Bean
    public Step normalizeInputStep(JobRepository jobRepository,
                                   PlatformTransactionManager transactionManager,
                                   ReconciliationProperties properties,
                                   CsvRecordReader reader) {
        return new StepBuilder("normalizeInputStep", jobRepository)
                .tasklet(withRetry((contribution, chunkContext) -> {
                    RunData runData = RunData.of(chunkContext);
                    runData.setRecords(SourceSystem.DBS, load(SourceSystem.DBS, properties.input().dbs(), reader));
                    runData.setRecords(SourceSystem.QUANTIFEED, load(SourceSystem.QUANTIFEED, properties.input().quantifeed(), reader));
                    return RepeatStatus.FINISHED;
                }), transactionManager)
                .build();
    }

    @Bean
    public Step matchStep(JobRepository jobRepository,
                          PlatformTransactionManager transactionManager,
                          RecordMatcher matcher) {
        return new StepBuilder("matchStep", jobRepository)
                .tasklet(withRetry((contribution, chunkContext) -> {
                    RunData runData = RunData.of(chunkContext);
                    runData.setMatches(matcher.match(
                            runData.records(SourceSystem.DBS), runData.records(SourceSystem.QUANTIFEED)));
                    return RepeatStatus.FINISHED;
                }), transactionManager)
                .build();
    }

    @Bean
    public Step compareStep(JobRepository jobRepository,
                            PlatformTransactionManager transactionManager) {
        return new StepBuilder("compareStep", jobRepository)
                .tasklet(withRetry((contribution, chunkContext) -> {
                    // Settings saved with the run, so a resumed run compares with the values it started with.
                    RunSettings settings = RunSettings.from(
                            chunkContext.getStepContext().getStepExecution().getJobParameters());
                    RecordComparator comparator = new RecordComparator(settings.settlementDays().intValue(), settings.runDate());
                    RunData runData = RunData.of(chunkContext);
                    runData.setResults(runData.matches().stream().map(comparator::compare).toList());
                    return RepeatStatus.FINISHED;
                }), transactionManager)
                .build();
    }

    @Bean
    public Step exportStep(JobRepository jobRepository,
                           PlatformTransactionManager transactionManager,
                           ReconciliationProperties properties,
                           ResultWriter writer) {
        return new StepBuilder("exportStep", jobRepository)
                .tasklet(withRetry((contribution, chunkContext) -> {
                    RunData runData = RunData.of(chunkContext);
                    String runId = (String) chunkContext.getStepContext().getJobParameters().get("runId");
                    Path file = properties.output().file();
                    writer.write(runId, runData.results(), file);
                    log.info("Wrote {} results to {}", runData.results().size(), file.toAbsolutePath().normalize());
                    return RepeatStatus.FINISHED;
                }), transactionManager)
                .build();
    }

    /** Runs the tasklet again when it throws, up to retry.max-attempts in total, then lets the step fail. */
    private Tasklet withRetry(Tasklet tasklet) {
        return (contribution, chunkContext) -> retryTemplate.execute(retry -> {
            if (retry.getRetryCount() > 0) {
                log.warn("Retrying {} (attempt {} of {}) after error: {}", chunkContext.getStepContext().getStepName(),
                        retry.getRetryCount() + 1, maxAttempts, retry.getLastThrowable().toString());
            }
            return tasklet.execute(contribution, chunkContext);
        });
    }

    /** Reads and normalizes one file. Invalid rows are skipped. */
    private static List<SourceRecord> load(SourceSystem source, Path path, CsvRecordReader reader) throws IOException {
        RecordNormalizer normalizer = new RecordNormalizer(source, path.getFileName().toString());
        List<SourceRecord> records = new ArrayList<>();
        for (CSVRecord row : reader.read(path)) {
            SourceRecord record = normalizer.normalize(row);
            if (record != null) {
                records.add(record);
            }
        }
        return records;
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

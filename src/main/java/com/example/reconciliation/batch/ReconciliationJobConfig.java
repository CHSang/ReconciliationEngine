package com.example.reconciliation.batch;

import com.example.reconciliation.config.ReconciliationProperties;
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

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reconciliation job definition.
 *
 * <p>Phase 1 has a single step that checks the configured input files exist. The read, normalize, match,
 * compare and export steps come in later phases.
 */
@Configuration
public class ReconciliationJobConfig {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationJobConfig.class);

    @Bean
    public Job reconciliationJob(JobRepository jobRepository, Step validateInputStep) {
        return new JobBuilder("reconciliationJob", jobRepository)
                .start(validateInputStep)
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

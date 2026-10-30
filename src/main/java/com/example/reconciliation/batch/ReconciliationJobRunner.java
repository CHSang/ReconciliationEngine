package com.example.reconciliation.batch;

import com.example.reconciliation.config.ReconciliationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Starts the reconciliation job once when the app starts and turns the result into the process exit code.
 * If the last run failed with the same settings, it is run again with the same parameters, and Spring Batch
 * continues at the step that failed. Otherwise a fresh run starts with a new runId.
 */
@Component
public class ReconciliationJobRunner implements CommandLineRunner, ExitCodeGenerator {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationJobRunner.class);

    private final JobLauncher jobLauncher;
    private final JobExplorer jobExplorer;
    private final Job reconciliationJob;
    private final ReconciliationProperties properties;
    private int exitCode = 1;

    public ReconciliationJobRunner(JobLauncher jobLauncher, JobExplorer jobExplorer, Job reconciliationJob,
                                   ReconciliationProperties properties) {
        this.jobLauncher = jobLauncher;
        this.jobExplorer = jobExplorer;
        this.reconciliationJob = reconciliationJob;
        this.properties = properties;
    }

    @Override
    public void run(String... args) throws Exception {
        RunSettings settings = RunSettings.from(properties);
        JobParameters parameters = failedRunToResume(settings);
        if (parameters == null) {
            parameters = settings.toJobParameters(UUID.randomUUID().toString());
        }

        String runId = parameters.getString("runId");
        log.info("Starting reconciliation run {}", runId);
        JobExecution execution = jobLauncher.run(reconciliationJob, parameters);
        log.info("Run {} finished with status {}", runId, execution.getStatus());

        execution.getAllFailureExceptions()
                .forEach(e -> log.error("Run failed: {}", e.getMessage()));

        exitCode = execution.getStatus() == BatchStatus.COMPLETED ? 0 : 1;
    }

    /** The last run's parameters if it failed with the same settings, otherwise null. */
    private JobParameters failedRunToResume(RunSettings settings) {
        JobInstance instance = jobExplorer.getLastJobInstance(reconciliationJob.getName());
        JobExecution last = instance == null ? null : jobExplorer.getLastJobExecution(instance);
        if (last == null || last.getStatus() != BatchStatus.FAILED) {
            return null;
        }

        RunSettings previous = RunSettings.from(last.getJobParameters());
        if (!settings.equals(previous)) {
            log.info("Not resuming the failed run because settings changed: was {}, now {}", previous, settings);
            return null;
        }
        log.info("Resuming failed run {}", last.getJobParameters().getString("runId"));
        return last.getJobParameters();
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}

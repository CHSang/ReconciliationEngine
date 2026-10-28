package com.example.reconciliation.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Starts the reconciliation job once when the app starts and turns the result into the process exit code.
 * Each run gets a new runId, so reruns never reuse an earlier execution.
 */
@Component
public class ReconciliationJobRunner implements CommandLineRunner, ExitCodeGenerator {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationJobRunner.class);

    private final JobLauncher jobLauncher;
    private final Job reconciliationJob;
    private int exitCode = 1;

    public ReconciliationJobRunner(JobLauncher jobLauncher, Job reconciliationJob) {
        this.jobLauncher = jobLauncher;
        this.reconciliationJob = reconciliationJob;
    }

    @Override
    public void run(String... args) throws Exception {
        String runId = UUID.randomUUID().toString();
        JobParameters parameters = new JobParametersBuilder()
                .addString("runId", runId)
                .toJobParameters();

        log.info("Starting reconciliation run {}", runId);
        JobExecution execution = jobLauncher.run(reconciliationJob, parameters);
        log.info("Run {} finished with status {}", runId, execution.getStatus());

        execution.getAllFailureExceptions()
                .forEach(e -> log.error("Run failed: {}", e.getMessage()));

        exitCode = execution.getStatus() == BatchStatus.COMPLETED ? 0 : 1;
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}

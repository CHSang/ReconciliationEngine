package com.example.reconciliation.batch;

import com.example.reconciliation.config.ReconciliationProperties;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;

import java.time.LocalDate;

/**
 * The settings that decide a run's results, saved with each run as job parameters.
 * A failed run is only resumed when these are the same as now.
 */
public record RunSettings(String dbsInput, String quantifeedInput, LocalDate runDate, Long settlementDays) {

    public static RunSettings from(ReconciliationProperties properties) {
        return new RunSettings(
                properties.input().dbs().toAbsolutePath().toString(),
                properties.input().quantifeed().toAbsolutePath().toString(),
                properties.runDateOrToday(),
                (long) properties.settlementDays());
    }

    public static RunSettings from(JobParameters parameters) {
        return new RunSettings(
                parameters.getString("dbsInput"),
                parameters.getString("quantifeedInput"),
                parameters.getLocalDate("runDate"),
                parameters.getLong("settlementDays"));
    }

    /** Job parameters for a new run. Only runId identifies the run; the settings are stored alongside it. */
    public JobParameters toJobParameters(String runId) {
        return new JobParametersBuilder()
                .addString("runId", runId)
                .addString("dbsInput", dbsInput, false)
                .addString("quantifeedInput", quantifeedInput, false)
                .addLocalDate("runDate", runDate, false)
                .addLong("settlementDays", settlementDays, false)
                .toJobParameters();
    }
}

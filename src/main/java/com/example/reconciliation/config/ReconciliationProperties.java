package com.example.reconciliation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.format.annotation.DateTimeFormat;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;

/**
 * Settings under {@code reconciliation.*} in application.yml; any of them can be overridden on the command line.
 *
 * @param settlementDays days a trade may appear in only one system before it is a break (T+2 = 2)
 * @param runDate        the date the reconciliation runs for; today when not set
 * @param retry          how often a failing step is tried before the run fails
 */
@ConfigurationProperties(prefix = "reconciliation")
public record ReconciliationProperties(
        Input input,
        Output output,
        @DefaultValue("2") int settlementDays,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate runDate,
        @DefaultValue Retry retry) {

    public record Input(Path dbs, Path quantifeed) {
    }

    public record Output(Path file) {
    }

    /** A failing step is tried {@code maxAttempts} times in total, waiting {@code delay} between attempts. */
    public record Retry(@DefaultValue("3") int maxAttempts, @DefaultValue("2s") Duration delay) {
    }

    public LocalDate runDateOrToday() {
        return runDate != null ? runDate : LocalDate.now();
    }
}

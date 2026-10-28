package com.example.reconciliation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.format.annotation.DateTimeFormat;

import java.nio.file.Path;
import java.time.LocalDate;

/**
 * Settings under {@code reconciliation.*} in application.yml; any of them can be overridden on the command line.
 *
 * @param settlementDays days a trade may appear in only one system before it is a break (T+2 = 2)
 * @param runDate        the date the reconciliation runs for; today when not set
 */
@ConfigurationProperties(prefix = "reconciliation")
public record ReconciliationProperties(
        Input input,
        Output output,
        @DefaultValue("2") int settlementDays,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate runDate) {

    public record Input(Path dbs, Path quantifeed) {
    }

    public record Output(Path file) {
    }

    public LocalDate runDateOrToday() {
        return runDate != null ? runDate : LocalDate.now();
    }
}

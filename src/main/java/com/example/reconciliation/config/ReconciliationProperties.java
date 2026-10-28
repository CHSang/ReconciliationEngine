package com.example.reconciliation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/** Settings under {@code reconciliation.*} in application.yml; any of them can be overridden on the command line. */
@ConfigurationProperties(prefix = "reconciliation")
public record ReconciliationProperties(Input input, Output output) {

    public record Input(Path dbs, Path quantifeed) {
    }

    public record Output(Path file, Path errorFile) {
    }
}

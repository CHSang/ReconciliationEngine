package com.example.reconciliation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ReconciliationApplication {

    public static void main(String[] args) {
        // Exit with the code chosen by ReconciliationJobRunner (0 = success, 1 = failure).
        System.exit(SpringApplication.exit(SpringApplication.run(ReconciliationApplication.class, args)));
    }
}

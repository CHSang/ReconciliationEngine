package com.example.reconciliation.model;

/** A row that could not be read or normalized. Reported, never silently dropped. */
public record RecordError(String fileName, long rowNumber, String field, String value, String reason) {
}

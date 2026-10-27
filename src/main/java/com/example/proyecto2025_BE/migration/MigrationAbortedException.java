package com.example.proyecto2025_BE.migration;

public class MigrationAbortedException extends RuntimeException {

    public MigrationAbortedException(String message) {
        super(message);
    }
}

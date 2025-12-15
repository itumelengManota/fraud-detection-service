package com.twenty9ine.frauddetection.infrastructure;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Utilities for efficient database management in tests.
 *
 * Performance Benefits:
 * - Transaction rollback is 10-100x faster than DELETE statements
 * - Batch truncation reduces overhead
 * - Minimal I/O operations
 */
public final class DatabaseTestUtils {

    private DatabaseTestUtils() {
        // Utility class
    }

    /**
     * Fast truncate of all test tables.
     * Use in @BeforeAll for class-level cleanup.
     */
    public static void truncateAllTables(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("TRUNCATE TABLE rule_evaluations, risk_assessments, transaction CASCADE");
    }

    /**
     * Disable foreign key checks for faster cleanup (PostgreSQL).
     */
    public static void disableForeignKeyChecks(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("SET session_replication_role = 'replica'");
    }

    /**
     * Enable foreign key checks after cleanup.
     */
    public static void enableForeignKeyChecks(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("SET session_replication_role = 'origin'");
    }

    /**
     * Fast cleanup with FK checks disabled.
     * Use this in @AfterAll for batch cleanup.
     */
    public static void fastCleanup(JdbcTemplate jdbcTemplate) {
        disableForeignKeyChecks(jdbcTemplate);
        truncateAllTables(jdbcTemplate);
        enableForeignKeyChecks(jdbcTemplate);
    }
}
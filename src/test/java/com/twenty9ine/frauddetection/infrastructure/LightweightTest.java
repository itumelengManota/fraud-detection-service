package com.twenty9ine.frauddetection.infrastructure;

import org.junit.jupiter.api.Order; /**
 * Custom test class ordering for optimal resource usage.
 *
 * Strategy:
 * 1. Run lightweight slice tests first (fastest feedback)
 * 2. Run integration tests in parallel
 * 3. Run heavy tests (with Keycloak) last
 *
 * Performance Benefits:
 * - Faster feedback loop for developers
 * - Better resource utilization
 * - Prevents resource contention
 */
@Order(1)  // Lightweight tests first
public interface LightweightTest {}

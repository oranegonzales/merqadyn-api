package dev.merqadyn.api.catalog

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProductConflictPolicyTests {
    @Test
    fun `matching versions can be applied`() {
        assertTrue(ProductConflictPolicy.canApply(7, 7))
    }

    @Test
    fun `stale and missing versions are rejected`() {
        assertFalse(ProductConflictPolicy.canApply(6, 7))
        assertFalse(ProductConflictPolicy.canApply(null, 7))
    }
}

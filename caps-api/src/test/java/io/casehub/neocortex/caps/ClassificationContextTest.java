package io.casehub.neocortex.caps;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClassificationContextTest {

    @Test
    void noneHasNullTenantId() {
        assertNull(ClassificationContext.NONE.tenantId());
    }

    @Test
    void ofCreateWithTenantId() {
        var ctx = ClassificationContext.of("tenant-1");
        assertEquals("tenant-1", ctx.tenantId());
    }

    @Test
    void ofRejectsNullTenantId() {
        assertThrows(NullPointerException.class,
            () -> ClassificationContext.of(null));
    }

    @Test
    void ofRejectsBlankTenantId() {
        assertThrows(IllegalArgumentException.class,
            () -> ClassificationContext.of("  "));
    }

    @Test
    void noneIsNotResolvable() {
        assertFalse(ClassificationContext.NONE.isResolvable());
    }

    @Test
    void ofIsResolvable() {
        assertTrue(ClassificationContext.of("t").isResolvable());
    }
}

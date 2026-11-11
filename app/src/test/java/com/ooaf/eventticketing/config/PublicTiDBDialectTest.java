package com.ooaf.eventticketing.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PublicTiDBDialectTest {
    @Test
    void keepsWriteLocksWithoutUnsupportedTableAliases() {
        PublicTiDBDialect dialect = new PublicTiDBDialect();
        String sql = dialect.getWriteLockString("e1_0", -1);
        assertEquals(" for update", sql);
        assertFalse(sql.contains("e1_0"));
        assertTrue(dialect.getForUpdateNowaitString("e1_0").startsWith(" for update"));
    }
}

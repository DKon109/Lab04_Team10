package com.ooaf.eventticketing.config;

import org.hibernate.dialect.MySQLDialect;

/** TiDB 8.5.3 accepts FOR UPDATE, but not Hibernate's alias in FOR UPDATE OF. */
public class PublicTiDBDialect extends MySQLDialect {
    @Override
    protected boolean supportsAliasLocks() {
        // Keep pessimistic locking; omit only the unsupported OF alias clause.
        return false;
    }
}

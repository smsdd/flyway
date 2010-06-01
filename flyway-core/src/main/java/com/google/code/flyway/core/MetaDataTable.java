/**
 * Copyright (C) 2009-2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.code.flyway.core;

import org.springframework.jdbc.core.simple.SimpleJdbcTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Supports reading and writing to the metadata table.
 */
public class MetaDataTable {
    /**
     * Database-specific functionality.
     */
    private DbSupport dbSupport;

    /**
     * The name of the schema metadata table used by flyway.
     */
    private final String tableName;

    /**
     * SimpleJdbcTemplate with ddl manipulation access to the database.
     */
    private SimpleJdbcTemplate jdbcTemplate;

    /**
     * The transaction template to use.
     */
    private TransactionTemplate transactionTemplate;

    /**
     * Creates a new instance of the metadata table support.
     *
     * @param transactionTemplate The transaction template to use.
     * @param jdbcTemplate        SimpleJdbcTemplate with ddl manipulation access to the database.
     * @param dbSupport           Database-specific functionality.
     * @param tableName           The name of the schema metadata table used by flyway.
     */
    public MetaDataTable(TransactionTemplate transactionTemplate, SimpleJdbcTemplate jdbcTemplate, DbSupport dbSupport,
                         String tableName) {
        this.transactionTemplate = transactionTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.dbSupport = dbSupport;
        this.tableName = tableName;
    }

    /**
     * Checks whether Flyway's metadata table is already present in the database.
     *
     * @return {@code true} if the table exists, {@code false} if it doesn't.
     * @throws java.sql.SQLException Thrown when the database metadata could not be read.
     */
    public boolean exists() throws SQLException {
        return dbSupport.metaDataTableExists(jdbcTemplate, tableName);
    }

    /**
     * Creates Flyway's metadata table.
     */
    public void create() {
        transactionTemplate.execute(new TransactionCallback<Void>() {
            @Override
            public Void doInTransaction(TransactionStatus status) {
                String[] statements = dbSupport.createSchemaMetaDataTableSql(tableName);
                for (String statement : statements) {
                    jdbcTemplate.update(statement);
                }
                return null;
            }
        });
    }

    /**
     * Marks this migration as succeeded.
     *
     * @param migration The migration that was run.
     */
    public void migrationFinished(final Migration migration) {
        jdbcTemplate.update("UPDATE " + tableName + " SET current_version=0");
        jdbcTemplate.update("INSERT INTO " + tableName
                + " (version, description, script, execution_time, state, current_version)"
                + " VALUES (?, ?, ?, ?, ?, 1)",
                migration.getVersion().getVersion(), migration.getVersion().getDescription(),
                migration.getScriptName(), migration.getExecutionTime(), migration.getState().name());
    }

    /**
     * @return The latest migration applied on the schema.
     */
    public Migration latestAppliedMigration() {
        final List<Map<String, Object>> result = jdbcTemplate.queryForList(
                "select VERSION, DESCRIPTION, SCRIPT, EXECUTION_TIME, STATE from " + tableName + " where current_version=1");
        if (result.isEmpty()) {
            return new Migration() {
                @Override
                public SchemaVersion getVersion() {
                    return SchemaVersion.EMPTY;
                }

                @Override
                public MigrationState getState() {
                    return MigrationState.UNKNOWN;
                }

                @Override
                public long getExecutionTime() {
                    return -1;
                }

                @Override
                public String getScriptName() {
                    return null;
                }

                @Override
                public void migrate(SimpleJdbcTemplate jdbcTemplate) {
                    // Do nothing.
                }
            };
        }

        return new Migration() {
            @Override
            public SchemaVersion getVersion() {
                return new SchemaVersion((String) result.get(0).get("VERSION"),
                        (String) result.get(0).get("DESCRIPTION"));
            }

            @Override
            public MigrationState getState() {
                return MigrationState.valueOf((String) result.get(0).get("STATE"));
            }

            @Override
            public long getExecutionTime() {
                return (Long) result.get(0).get("EXECUTION_TIME");
            }

            @Override
            public String getScriptName() {
                return (String) result.get(0).get("SCRIPT");
            }

            @Override
            public void migrate(SimpleJdbcTemplate jdbcTemplate) {
                // Do nothing.
            }
        };
    }
}

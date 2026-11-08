package com.syy.testplatform.ai.test.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FlywayLegacyUpgradeTest {
    @Test
    void upgradesLegacyHibernateTablesWithoutDeletingExistingRows() throws Exception {
        String url = "jdbc:h2:mem:legacy_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE api_definition (id BIGINT AUTO_INCREMENT PRIMARY KEY, path VARCHAR(255), method VARCHAR(20), description CLOB)");
            statement.execute("CREATE TABLE test_case (id BIGINT AUTO_INCREMENT PRIMARY KEY, api_id BIGINT, case_name VARCHAR(255), case_type VARCHAR(50), request_json CLOB, expect_result CLOB)");
            statement.execute("CREATE TABLE test_execution (id BIGINT AUTO_INCREMENT PRIMARY KEY, case_id BIGINT, actual_result CLOB, status VARCHAR(20), score INTEGER)");
            statement.execute("CREATE TABLE test_report (id BIGINT AUTO_INCREMENT PRIMARY KEY, total_count INTEGER, success_count INTEGER, fail_count INTEGER, pass_rate DOUBLE, ai_analysis CLOB, create_time TIMESTAMP)");
            statement.execute("CREATE TABLE test_task (id BIGINT AUTO_INCREMENT PRIMARY KEY, task_name VARCHAR(100), cron VARCHAR(100), status VARCHAR(20), create_time TIMESTAMP)");
            statement.execute("INSERT INTO api_definition(path, method, description) VALUES ('/legacy', 'GET', 'legacy API')");
            statement.execute("INSERT INTO test_case(api_id, case_name, case_type) VALUES (1, 'legacy case', 'POSITIVE')");
        }

        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            assertEquals(1, scalar(statement, "SELECT COUNT(*) FROM test_case"), "legacy test case must remain");
            assertEquals(1, scalar(statement, "SELECT COUNT(*) FROM api_definition"), "legacy API definition must remain");
            assertTrue(hasColumn(connection, "test_case", "fingerprint"));
            assertTrue(hasColumn(connection, "test_task", "document_id"));
            assertTrue(hasColumn(connection, "test_execution", "task_id"));
            assertTrue(hasColumn(connection, "test_report", "average_score"));
            assertTrue(hasColumn(connection, "test_task", "request_json"));
            assertEquals(1, scalar(statement, "SELECT COUNT(*) FROM information_schema.tables WHERE lower(table_name)='test_schedule'"));
            assertEquals(3, scalar(statement, "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE AND version IN ('1', '2', '3')"));
        }
    }

    private int scalar(Statement statement, String sql) throws Exception {
        try (ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private boolean hasColumn(Connection connection, String table, String column) throws Exception {
        try (ResultSet result = connection.getMetaData().getColumns(connection.getCatalog(), null,
                table.toLowerCase(Locale.ROOT), column.toLowerCase(Locale.ROOT))) {
            return result.next();
        }
    }
}

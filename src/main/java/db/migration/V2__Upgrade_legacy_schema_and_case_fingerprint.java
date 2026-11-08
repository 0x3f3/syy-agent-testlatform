package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Adds columns missing from earlier Hibernate/SQL-created installations without dropping user data. */
public class V2__Upgrade_legacy_schema_and_case_fingerprint extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        String product = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
        boolean mysql = product.contains("mysql") || product.contains("mariadb");
        String largeText = mysql ? "LONGTEXT" : "CLOB";
        String dateTime = mysql ? "DATETIME" : "TIMESTAMP";

        Map<String, Map<String, String>> additions = new LinkedHashMap<>();
        additions.put("api_document", columns(
            "name", "VARCHAR(255)", "title", "VARCHAR(255)", "version", "VARCHAR(64)",
            "server_url", "VARCHAR(1024)", "specification_version", "VARCHAR(32)",
            "raw_document", largeText, "imported_at", dateTime));
        additions.put("api_definition", columns(
            "document_id", "BIGINT", "path", "VARCHAR(512)", "method", "VARCHAR(12)",
            "operation_id", "VARCHAR(255)", "description", "VARCHAR(512)", "server_url", "VARCHAR(1024)",
            "tags", "VARCHAR(512)", "parameters_json", largeText, "request_body_schema_json", largeText,
            "request_example_json", largeText, "responses_json", largeText, "security_json", largeText,
            "raw_operation_json", largeText));
        additions.put("knowledge_chunk", columns(
            "source", "VARCHAR(512)", "content", largeText, "embedding_json", largeText, "created_at", dateTime));
        additions.put("test_case", columns(
            "api_id", "BIGINT", "case_name", "VARCHAR(255)", "case_type", "VARCHAR(40)",
            "priority", "VARCHAR(12)", "description", "VARCHAR(1000)", "request_json", largeText,
            "expect_result", largeText, "assertions_json", largeText, "enabled", "BOOLEAN DEFAULT TRUE",
            "created_at", dateTime, "fingerprint", "VARCHAR(64)"));
        additions.put("test_task", columns(
            "task_name", "VARCHAR(255)", "document_id", "BIGINT", "base_url", "VARCHAR(1024)",
            "status", "VARCHAR(24)", "total_count", "INTEGER DEFAULT 0", "passed_count", "INTEGER DEFAULT 0",
            "failed_count", "INTEGER DEFAULT 0", "error_count", "INTEGER DEFAULT 0", "created_at", dateTime,
            "started_at", dateTime, "finished_at", dateTime, "error_message", "VARCHAR(2000)"));
        additions.put("test_execution", columns(
            "task_id", "BIGINT", "case_id", "BIGINT", "api_id", "BIGINT", "request_url", "VARCHAR(1024)",
            "request_method", "VARCHAR(12)", "request_headers_json", largeText, "request_body", largeText,
            "response_status", "INTEGER", "response_headers_json", largeText, "actual_result", largeText,
            "duration_ms", "BIGINT", "status", "VARCHAR(16)", "score", "INTEGER", "judge_available", "BOOLEAN",
            "evaluation_reason", "VARCHAR(2000)", "suggestion", "VARCHAR(2000)", "execute_time", dateTime));
        additions.put("test_report", columns(
            "task_id", "BIGINT", "total_count", "INTEGER", "success_count", "INTEGER", "fail_count", "INTEGER",
            "error_count", "INTEGER", "pass_rate", "DOUBLE", "average_score", "DOUBLE", "ai_analysis", largeText,
            "create_time", dateTime));

        for (Map.Entry<String, Map<String, String>> table : additions.entrySet()) {
            for (Map.Entry<String, String> column : table.getValue().entrySet()) {
                addColumnIfMissing(connection, table.getKey(), column.getKey(), column.getValue());
            }
        }

        V1__Create_initial_schema.createIndexIfMissing(connection, "idx_api_document_id", "api_definition", "document_id", false);
        V1__Create_initial_schema.createIndexIfMissing(connection, "idx_api_method_path", "api_definition", "method, path", false);
        V1__Create_initial_schema.createIndexIfMissing(connection, "idx_knowledge_source", "knowledge_chunk", "source", false);
        V1__Create_initial_schema.createIndexIfMissing(connection, "idx_case_api_id", "test_case", "api_id", false);
        V1__Create_initial_schema.createIndexIfMissing(connection, "idx_execution_task_id", "test_execution", "task_id", false);
        V1__Create_initial_schema.createIndexIfMissing(connection, "idx_execution_case_id", "test_execution", "case_id", false);
        V1__Create_initial_schema.createIndexIfMissing(connection, "idx_execution_time", "test_execution", "execute_time", false);
        V1__Create_initial_schema.createIndexIfMissing(connection, "idx_report_task_id", "test_report", "task_id", false);
        V1__Create_initial_schema.createIndexIfMissing(connection, "idx_task_created_at", "test_task", "created_at", false);
        V1__Create_initial_schema.createIndexIfMissing(connection, "uk_test_case_api_fingerprint", "test_case", "api_id, fingerprint", true);
    }

    private static Map<String, String> columns(String... pairs) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put(pairs[i], pairs[i + 1]);
        return result;
    }

    private static void addColumnIfMissing(Connection connection, String table, String column, String definition) throws Exception {
        if (columnExists(connection, table, column)) return;
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    private static boolean columnExists(Connection connection, String table, String column) throws Exception {
        DatabaseMetaData metadata = connection.getMetaData();
        String catalog = connection.getCatalog();
        for (String candidate : new String[]{table, table.toUpperCase(Locale.ROOT), table.toLowerCase(Locale.ROOT)}) {
            try (ResultSet rs = metadata.getColumns(catalog, null, candidate, null)) {
                while (rs.next()) {
                    if (rs.getString("TABLE_NAME").equalsIgnoreCase(table)
                        && rs.getString("COLUMN_NAME").equalsIgnoreCase(column)) return true;
                }
            }
        }
        return false;
    }
}

package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Locale;

/** Creates the complete schema for fresh installations. Existing databases are upgraded by V2. */
public class V1__Create_initial_schema extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        String product = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
        boolean mysql = product.contains("mysql") || product.contains("mariadb");
        String largeText = mysql ? "LONGTEXT" : "CLOB";
        String dateTime = mysql ? "DATETIME" : "TIMESTAMP";

        String[] ddl = {
            "CREATE TABLE IF NOT EXISTS api_document (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255) NOT NULL, title VARCHAR(255), " +
                "version VARCHAR(64), server_url VARCHAR(1024), specification_version VARCHAR(32), " +
                "raw_document " + largeText + ", imported_at " + dateTime + ")",
            "CREATE TABLE IF NOT EXISTS api_definition (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, document_id BIGINT, path VARCHAR(512) NOT NULL, " +
                "method VARCHAR(12) NOT NULL, operation_id VARCHAR(255), description VARCHAR(512), " +
                "server_url VARCHAR(1024), tags VARCHAR(512), parameters_json " + largeText + ", " +
                "request_body_schema_json " + largeText + ", request_example_json " + largeText + ", " +
                "responses_json " + largeText + ", security_json " + largeText + ", raw_operation_json " + largeText + ")",
            "CREATE TABLE IF NOT EXISTS knowledge_chunk (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, source VARCHAR(512), content " + largeText + ", " +
                "embedding_json " + largeText + ", created_at " + dateTime + ")",
            "CREATE TABLE IF NOT EXISTS test_case (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, api_id BIGINT NOT NULL, case_name VARCHAR(255) NOT NULL, " +
                "case_type VARCHAR(40), priority VARCHAR(12), description VARCHAR(1000), request_json " + largeText + ", " +
                "expect_result " + largeText + ", assertions_json " + largeText + ", enabled BOOLEAN DEFAULT TRUE, " +
                "created_at " + dateTime + ", fingerprint VARCHAR(64))",
            "CREATE TABLE IF NOT EXISTS test_task (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, task_name VARCHAR(255), document_id BIGINT, " +
                "base_url VARCHAR(1024), status VARCHAR(24), total_count INTEGER DEFAULT 0, " +
                "passed_count INTEGER DEFAULT 0, failed_count INTEGER DEFAULT 0, error_count INTEGER DEFAULT 0, " +
                "created_at " + dateTime + ", started_at " + dateTime + ", finished_at " + dateTime + ", " +
                "error_message VARCHAR(2000))",
            "CREATE TABLE IF NOT EXISTS test_execution (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, task_id BIGINT, case_id BIGINT, api_id BIGINT, " +
                "request_url VARCHAR(1024), request_method VARCHAR(12), request_headers_json " + largeText + ", " +
                "request_body " + largeText + ", response_status INTEGER, response_headers_json " + largeText + ", " +
                "actual_result " + largeText + ", duration_ms BIGINT, status VARCHAR(16), score INTEGER, " +
                "judge_available BOOLEAN, evaluation_reason VARCHAR(2000), suggestion VARCHAR(2000), execute_time " + dateTime + ")",
            "CREATE TABLE IF NOT EXISTS test_report (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, task_id BIGINT, total_count INTEGER, success_count INTEGER, " +
                "fail_count INTEGER, error_count INTEGER, pass_rate DOUBLE, average_score DOUBLE, " +
                "ai_analysis " + largeText + ", create_time " + dateTime + ")"
        };

        try (Statement statement = connection.createStatement()) {
            for (String sql : ddl) statement.execute(sql);
        }

        // Indexes are created in V2 after legacy installations have had missing columns added.

    }

    static void createIndexIfMissing(Connection connection, String indexName, String table, String columns, boolean unique) throws Exception {
        if (indexExists(connection, table, indexName)) return;
        String sql = "CREATE " + (unique ? "UNIQUE " : "") + "INDEX " + indexName + " ON " + table + "(" + columns + ")";
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    static boolean indexExists(Connection connection, String table, String indexName) throws Exception {
        DatabaseMetaData metadata = connection.getMetaData();
        String catalog = connection.getCatalog();
        for (String candidate : new String[]{table, table.toUpperCase(Locale.ROOT), table.toLowerCase(Locale.ROOT)}) {
            try (ResultSet rs = metadata.getIndexInfo(catalog, null, candidate, false, false)) {
                while (rs.next()) {
                    String found = rs.getString("INDEX_NAME");
                    if (found != null && found.equalsIgnoreCase(indexName)) return true;
                }
            }
        }
        return false;
    }
}

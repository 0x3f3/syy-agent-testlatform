package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Locale;

/** Adds durable task payloads and cron-based schedule definitions without dropping existing rows. */
public class V3__Async_tasks_and_schedules extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        String product = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
        boolean mysql = product.contains("mysql") || product.contains("mariadb");
        String largeText = mysql ? "LONGTEXT" : "CLOB";
        String dateTime = mysql ? "DATETIME" : "TIMESTAMP";

        if (!columnExists(connection, "test_task", "request_json")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE test_task ADD COLUMN request_json " + largeText);
            }
        }

        String createSchedule = "CREATE TABLE IF NOT EXISTS test_schedule (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "schedule_name VARCHAR(255) NOT NULL," +
                "document_id BIGINT NOT NULL," +
                "task_name VARCHAR(255)," +
                "base_url VARCHAR(1024)," +
                "cron_expression VARCHAR(100) NOT NULL," +
                "enabled BOOLEAN NOT NULL DEFAULT TRUE," +
                "next_run_at " + dateTime + " NOT NULL," +
                "last_run_at " + dateTime + "," +
                "last_task_id BIGINT," +
                "created_at " + dateTime + " NOT NULL," +
                "updated_at " + dateTime + ")";
        try (Statement statement = connection.createStatement()) {
            statement.execute(createSchedule);
        }
        V1__Create_initial_schema.createIndexIfMissing(connection, "idx_schedule_due", "test_schedule", "enabled, next_run_at", false);
        V1__Create_initial_schema.createIndexIfMissing(connection, "idx_schedule_document", "test_schedule", "document_id", false);
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

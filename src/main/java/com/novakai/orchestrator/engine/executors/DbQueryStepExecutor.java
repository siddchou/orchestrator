package com.novakai.orchestrator.engine.executors;

import com.novakai.orchestrator.engine.JsonParser;
import com.novakai.orchestrator.engine.spi.FieldDefinition;
import com.novakai.orchestrator.engine.spi.FieldType;
import com.novakai.orchestrator.engine.spi.StepConfigSchema;
import com.novakai.orchestrator.engine.spi.StepContext;
import com.novakai.orchestrator.engine.spi.StepExecutor;
import com.novakai.orchestrator.engine.spi.StepResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class DbQueryStepExecutor implements StepExecutor {

    private final JdbcTemplate jdbcTemplate;
    private final JsonParser jsonParser;

    @Override
    public String getType() {
        return "DB_QUERY";
    }

    @Override
    public StepConfigSchema getConfigSchema() {
        return new StepConfigSchema(
                "DB_QUERY",
                "Database Query",
                List.of(
                        new FieldDefinition("sql", "SQL Statement", FieldType.STRING, true, null, null, "SQL query to execute"),
                        new FieldDefinition("params", "Parameters", FieldType.STRING, false, null, null, "JSON array of parameter values for ? placeholders"),
                        new FieldDefinition("expectRowCount", "Expected Row Count", FieldType.NUMBER, false, null, null, "Assert exact number of rows returned"),
                        new FieldDefinition("allowWrite", "Allow Write Operations", FieldType.BOOLEAN, false, false, null, "Set true to allow INSERT/UPDATE/DELETE/DROP/TRUNCATE")
                )
        );
    }

    @Override
    public StepResult execute(StepContext ctx) throws Exception {
        long startTime = System.nanoTime();

        if (ctx.getStepConfig() == null || ctx.getStepConfig().isBlank()) {
            return StepResult.failure("DB_QUERY config is null or empty", Duration.ofNanos(System.nanoTime() - startTime));
        }

        Map<String, Object> config = jsonParser.parse(ctx.getStepConfig(), Map.class);

        String sql = (String) config.get("sql");
        if (sql == null || sql.isBlank()) {
            return StepResult.failure("DB_QUERY: 'sql' is required", Duration.ofNanos(System.nanoTime() - startTime));
        }

        boolean allowWrite = config.containsKey("allowWrite") && Boolean.parseBoolean(config.get("allowWrite").toString());

        // Security check: reject write operations unless explicitly allowed
        String sqlTrimmed = sql.trim().toUpperCase();
        if (!allowWrite) {
            if (sqlTrimmed.startsWith("INSERT") || sqlTrimmed.startsWith("UPDATE") || sqlTrimmed.startsWith("DELETE")
                    || sqlTrimmed.startsWith("DROP") || sqlTrimmed.startsWith("TRUNCATE")
                    || sqlTrimmed.startsWith("ALTER") || sqlTrimmed.startsWith("CREATE")) {
                return StepResult.failure(
                        "DB_QUERY: write operations not allowed. Set 'allowWrite' to true for non-SELECT statements",
                        Duration.ofNanos(System.nanoTime() - startTime));
            }
        }

        // Parse params
        Object[] params;
        if (config.containsKey("params") && config.get("params") instanceof List<?> paramList) {
            params = paramList.toArray();
        } else {
            params = new Object[0];
        }

        try {
            int rowCount;
            List<Map<String, Object>> rows;

            if (sqlTrimmed.startsWith("SELECT")) {
                rows = jdbcTemplate.queryForList(sql, params);
                rowCount = rows.size();
            } else {
                rowCount = jdbcTemplate.update(sql, params);
                rows = List.of();
            }

            // Check expected row count
            if (config.containsKey("expectRowCount")) {
                int expected = Integer.parseInt(config.get("expectRowCount").toString());
                if (rowCount != expected) {
                    return StepResult.failure(
                            "DB_QUERY: expected " + expected + " rows but got " + rowCount,
                            Duration.ofNanos(System.nanoTime() - startTime));
                }
            }

            ctx.getLogSink().log("DB_QUERY executed: " + rowCount + " row(s) affected/returned");

            Map<String, Object> outputs = Map.of(
                    "rowCount", rowCount,
                    "rows", rows
            );

            return StepResult.success(outputs, "Query returned " + rowCount + " row(s)", Duration.ofNanos(System.nanoTime() - startTime));

        } catch (Exception ex) {
            ctx.getLogSink().log("ERROR: DB_QUERY failed: " + ex.getMessage());
            return StepResult.failure("DB_QUERY failed: " + ex.getMessage(), Duration.ofNanos(System.nanoTime() - startTime));
        }
    }
}

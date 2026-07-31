package com.novakai.orchestrator.engine.executors;

import com.novakai.orchestrator.engine.JsonParser;
import com.novakai.orchestrator.engine.spi.StepContext;
import com.novakai.orchestrator.engine.spi.StepConfigSchema;
import com.novakai.orchestrator.engine.spi.StepResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.*;

class DbQueryStepExecutorTest {

    private JdbcTemplate jdbcTemplate;
    private SingleConnectionDataSource dataSource;
    private DbQueryStepExecutor executor;

    @BeforeEach
    void setUp() throws Exception {
        Connection conn = DriverManager.getConnection("jdbc:h2:mem:testdb" + System.nanoTime() + ";DB_CLOSE_DELAY=-1", "sa", "");
        dataSource = new SingleConnectionDataSource(conn, true);
        jdbcTemplate = new JdbcTemplate(dataSource);

        try (Statement s = conn.createStatement()) {
            s.execute("CREATE TABLE test_items (id INT PRIMARY KEY, name VARCHAR(50))");
            s.execute("INSERT INTO test_items VALUES (1, 'alpha')");
            s.execute("INSERT INTO test_items VALUES (2, 'beta')");
        }

        executor = new DbQueryStepExecutor(jdbcTemplate, new JsonParser());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (dataSource != null) {
            dataSource.destroy();
        }
    }

    @Test
    void getType_returns_DB_QUERY() {
        assertEquals("DB_QUERY", executor.getType());
    }

    @Test
    void getConfigSchema_has_required_sql_field() {
        StepConfigSchema schema = executor.getConfigSchema();
        assertNotNull(schema);
        assertEquals("DB_QUERY", schema.stepType());
        assertTrue(schema.fields().stream().anyMatch(f -> f.name().equals("sql") && f.required()));
    }

    @Test
    void getConfigSchema_allowWrite_defaults_false() {
        StepConfigSchema schema = executor.getConfigSchema();
        var allowWriteField = schema.fields().stream()
            .filter(f -> f.name().equals("allowWrite"))
            .findFirst()
            .orElseThrow();
        assertEquals(false, allowWriteField.defaultValue());
    }

    @Test
    void execute_select_query_returns_rows() throws Exception {
        String config = "{\"sql\":\"SELECT * FROM test_items\"}";
        var ctx = StepContext.builder()
            .logSink(new StepContext.LogSink(new LinkedBlockingQueue<>()))
            .stepConfig(config)
            .build();

        StepResult result = executor.execute(ctx);

        assertTrue(result.isSuccess());
        assertEquals(2, result.outputs().get("rowCount"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.outputs().get("rows");
        assertEquals(2, rows.size());
    }

    @Test
    void execute_select_with_params() throws Exception {
        String config = "{\"sql\":\"SELECT * FROM test_items WHERE id = ?\",\"params\":[1]}";
        var ctx = StepContext.builder()
            .logSink(new StepContext.LogSink(new LinkedBlockingQueue<>()))
            .stepConfig(config)
            .build();

        StepResult result = executor.execute(ctx);

        assertTrue(result.isSuccess());
        assertEquals(1, result.outputs().get("rowCount"));
    }

    @Test
    void execute_rejects_insert_without_allowWrite() throws Exception {
        String config = "{\"sql\":\"INSERT INTO test_items VALUES (3, 'gamma')\"}";
        var ctx = StepContext.builder()
            .logSink(new StepContext.LogSink(new LinkedBlockingQueue<>()))
            .stepConfig(config)
            .build();

        StepResult result = executor.execute(ctx);

        assertFalse(result.isSuccess());
    }

    @Test
    void execute_allows_insert_with_allowWrite() throws Exception {
        String config = "{\"sql\":\"INSERT INTO test_items VALUES (3, 'gamma')\",\"allowWrite\":true}";
        var ctx = StepContext.builder()
            .logSink(new StepContext.LogSink(new LinkedBlockingQueue<>()))
            .stepConfig(config)
            .build();

        StepResult result = executor.execute(ctx);

        assertTrue(result.isSuccess());
        assertEquals(1, result.outputs().get("rowCount"));

        // verify the row was inserted
        int count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM test_items", Integer.class);
        assertEquals(3, count);
    }

    @Test
    void execute_rejects_delete_without_allowWrite() throws Exception {
        String config = "{\"sql\":\"DELETE FROM test_items WHERE id = 1\"}";
        var ctx = StepContext.builder()
            .logSink(new StepContext.LogSink(new LinkedBlockingQueue<>()))
            .stepConfig(config)
            .build();

        StepResult result = executor.execute(ctx);

        assertFalse(result.isSuccess());
    }

    @Test
    void execute_rejects_drop_without_allowWrite() throws Exception {
        String config = "{\"sql\":\"DROP TABLE test_items\"}";
        var ctx = StepContext.builder()
            .logSink(new StepContext.LogSink(new LinkedBlockingQueue<>()))
            .stepConfig(config)
            .build();

        StepResult result = executor.execute(ctx);

        assertFalse(result.isSuccess());
    }

    @Test
    void execute_returns_failure_when_config_null() throws Exception {
        var ctx = StepContext.builder()
            .logSink(new StepContext.LogSink(new LinkedBlockingQueue<>()))
            .stepConfig("")
            .build();

        StepResult result = executor.execute(ctx);

        assertFalse(result.isSuccess());
    }

    @Test
    void execute_returns_failure_when_sql_empty() throws Exception {
        var ctx = StepContext.builder()
            .logSink(new StepContext.LogSink(new LinkedBlockingQueue<>()))
            .stepConfig("{}")
            .build();

        StepResult result = executor.execute(ctx);

        assertFalse(result.isSuccess());
    }

    @Test
    void execute_respects_expect_row_count() throws Exception {
        String config = "{\"sql\":\"SELECT * FROM test_items\",\"expectRowCount\":5}";
        var ctx = StepContext.builder()
            .logSink(new StepContext.LogSink(new LinkedBlockingQueue<>()))
            .stepConfig(config)
            .build();

        StepResult result = executor.execute(ctx);

        assertFalse(result.isSuccess());
    }

    @Test
    void execute_expect_row_count_matches() throws Exception {
        String config = "{\"sql\":\"SELECT * FROM test_items\",\"expectRowCount\":2}";
        var ctx = StepContext.builder()
            .logSink(new StepContext.LogSink(new LinkedBlockingQueue<>()))
            .stepConfig(config)
            .build();

        StepResult result = executor.execute(ctx);

        assertTrue(result.isSuccess());
    }
}

package com.ai.coder.rag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SqlExecutionService {

    private final DataSource dataSource;

    public Map<String, Object> executeSql(String sql, String databaseName, String jdbcUrl, String dbUsername, String dbPassword) {
        String normalizedSql = sql.trim().toUpperCase();
        if (!normalizedSql.startsWith("SELECT") && !normalizedSql.startsWith("WITH") && !normalizedSql.startsWith("SHOW") && !normalizedSql.startsWith("DESCRIBE") && !normalizedSql.startsWith("EXPLAIN")) {
            throw new IllegalArgumentException("只允许执行 SELECT 查询语句");
        }

        Map<String, Object> result = new LinkedHashMap<>();

        Connection connection = null;
        boolean externalConnection = (jdbcUrl != null && !jdbcUrl.isBlank());

        try {
            if (externalConnection) {
                String user = dbUsername != null && !dbUsername.isBlank() ? dbUsername : "root";
                String pass = dbPassword != null ? dbPassword : "";
                connection = DriverManager.getConnection(jdbcUrl, user, pass);
            } else {
                connection = dataSource.getConnection();
                if (databaseName != null && !databaseName.isBlank()) {
                    try (Statement useStmt = connection.createStatement()) {
                        useStmt.execute("USE " + databaseName);
                    }
                }
            }

            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                // 收集原始列名，同时查询 comment
                List<String> rawColumns = new ArrayList<>();
                for (int i = 1; i <= columnCount; i++) {
                    rawColumns.add(metaData.getColumnLabel(i));
                }

                Map<String, String> commentMap = queryColumnComments(connection, metaData);
                List<String> displayColumns = new ArrayList<>();
                for (String col : rawColumns) {
                    displayColumns.add(commentMap.getOrDefault(col, col));
                }

                List<Map<String, Object>> rows = new ArrayList<>();
                int rowCount = 0;
                int maxRows = 1000;
                while (rs.next() && rowCount < maxRows) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        Object value = rs.getObject(i);
                        row.put(rawColumns.get(i - 1), value != null ? value.toString() : null);
                    }
                    rows.add(row);
                    rowCount++;
                }

                result.put("columns", rawColumns);
                result.put("columnLabels", displayColumns);
                result.put("rows", rows);
                result.put("totalRows", rowCount);
            }
        } catch (SQLException e) {
            log.error("SQL 执行失败: {}", e.getMessage(), e);
            throw new RuntimeException("SQL 执行失败: " + e.getMessage(), e);
        } finally {
            if (externalConnection && connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    log.warn("关闭外部数据库连接失败: {}", e.getMessage());
                }
            }
        }

        return result;
    }

    private Map<String, String> queryColumnComments(Connection connection, ResultSetMetaData metaData) throws SQLException {
        // 收集所有涉及的 catalog.table -> column 集合
        Map<String, Set<String>> tableColumns = new LinkedHashMap<>();
        int columnCount = metaData.getColumnCount();

        for (int i = 1; i <= columnCount; i++) {
            String tableName = metaData.getTableName(i);
            String colName = metaData.getColumnName(i);
            String catalog = metaData.getCatalogName(i);
            String label = metaData.getColumnLabel(i);

            // 只有当 label == colName 时才查 comment（别名的不查）
            if (tableName != null && !tableName.isEmpty() && label.equals(colName)) {
                String key = (catalog != null && !catalog.isEmpty() ? catalog : "") + "." + tableName;
                tableColumns.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(colName);
            }
        }

        if (tableColumns.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> commentMap = new LinkedHashMap<>();
        String currentDb = connection.getCatalog();

        for (Map.Entry<String, Set<String>> entry : tableColumns.entrySet()) {
            String[] parts = entry.getKey().split("\\.", 2);
            String catalog = parts[0].isEmpty() ? currentDb : parts[0];
            String table = parts[1];

            String colList = String.join("','", entry.getValue());
            String query = String.format(
                    "SELECT COLUMN_NAME, COLUMN_COMMENT FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = '%s' AND TABLE_NAME = '%s' AND COLUMN_NAME IN ('%s')",
                    catalog, table, colList
            );

            try (Statement stmt = connection.createStatement();
                 ResultSet crs = stmt.executeQuery(query)) {
                while (crs.next()) {
                    String col = crs.getString("COLUMN_NAME");
                    String comment = crs.getString("COLUMN_COMMENT");
                    if (comment != null && !comment.isEmpty()) {
                        commentMap.put(col, comment);
                    }
                }
            } catch (SQLException e) {
                log.debug("查询列注释失败: {}", e.getMessage());
            }
        }

        return commentMap;
    }
}

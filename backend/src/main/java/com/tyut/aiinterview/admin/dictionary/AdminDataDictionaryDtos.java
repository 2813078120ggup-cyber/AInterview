package com.tyut.aiinterview.admin.dictionary;

import java.time.Instant;
import java.util.List;

/** Read-only database data-dictionary responses used by the administrator operations console. */
public final class AdminDataDictionaryDtos {
    private AdminDataDictionaryDtos() {}

    public record TableQuery(Long pageNo, Long pageSize, String keyword, String sortBy, String sortOrder,
                             String tableType, Boolean sensitiveOnly, Boolean hasPrimaryKey,
                             Boolean hasForeignKey) {
        public TableQuery(Long pageNo, Long pageSize, String keyword, String sortBy, String sortOrder) {
            this(pageNo, pageSize, keyword, sortBy, sortOrder, null, null, null, null);
        }
    }

    public record Overview(
            String catalog,
            long tableCount,
            long columnCount,
            long sensitiveColumnCount,
            long indexCount,
            long foreignKeyCount,
            String schemaFingerprint,
            String latestFlywayVersion,
            Instant generatedAt) {}

    public record TableSummary(
            String tableName,
            String tableComment,
            String tableType,
            long columnCount,
            long sensitiveColumnCount,
            long indexCount,
            long foreignKeyCount,
            boolean hasPrimaryKey,
            boolean hasForeignKey,
            List<String> primaryKeyColumns) {
        public TableSummary(String tableName, String tableComment, String tableType, long columnCount,
                            long sensitiveColumnCount, List<String> primaryKeyColumns) {
            this(tableName, tableComment, tableType, columnCount, sensitiveColumnCount, 0, 0,
                    !primaryKeyColumns.isEmpty(), false, primaryKeyColumns);
        }
    }

    public record TableDetail(
            String catalog,
            String tableName,
            String tableComment,
            String tableType,
            List<Column> columns,
            List<Index> indexes,
            List<PrimaryKey> primaryKeys,
            List<UniqueConstraint> uniqueConstraints,
            List<ForeignKey> foreignKeys,
            String schemaFingerprint,
            Instant generatedAt) {}

    public record Column(
            int ordinalPosition,
            String columnName,
            String dataType,
            String nativeType,
            Integer length,
            Integer scale,
            boolean nullable,
            String defaultValue,
            String maskedDefaultValue,
            boolean defaultValueMasked,
            boolean autoIncrement,
            String comment,
            boolean sensitive,
            String maskingDefault) {}

    public record Index(String indexName, boolean unique, boolean primary, List<String> columns) {}

    public record PrimaryKey(String keyName, int keySequence, String columnName) {}

    public record UniqueConstraint(String constraintName, List<String> columns) {}

    public record ForeignKey(
            String constraintName,
            int keySequence,
            String columnName,
            String referencedCatalog,
            String referencedTable,
            String referencedColumn,
            String updateRule,
            String deleteRule) {}
}

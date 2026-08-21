package com.tyut.aiinterview.admin.dictionary;

import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.common.PageResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Read-only database metadata facade for the administrator data dictionary.
 *
 * <p>The current JDBC catalog is deliberately obtained from the connection and is never accepted
 * from a request. Table names are checked against the cached metadata allowlist before a detail
 * request is served. Metadata is cached briefly because this page is an operational read path and
 * should not open a connection for every table row rendered by the console.
 */
@Service
public class AdminDataDictionaryService {
    private static final long DEFAULT_CACHE_TTL_MILLIS = Duration.ofSeconds(30).toMillis();
    private static final long DEFAULT_PAGE_SIZE = 20;
    private static final long MAX_PAGE_SIZE = 100;
    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_$]{1,64}");
    private static final Set<String> SORT_FIELDS = Set.of(
            "tableName", "tableComment", "tableType", "columnCount", "sensitiveColumnCount");
    private static final Pattern SENSITIVE_NAME_PATTERN = Pattern.compile(
            "(?i)(password|passwd|pwd|secret|token|authorization|access[_-]?key|private[_-]?key"
                    + "|phone|mobile|email|mail|id[_-]?card|identity|address|resume|salary|bank|account)");
    private static final String MASKING_DEFAULT = "***";
    private static final int MAX_COMMENT_LENGTH = 2_000;

    private final DataSource dataSource;
    private final Clock clock;
    private final long cacheTtlMillis;
    private final AtomicReference<Snapshot> cache = new AtomicReference<>();

    @Autowired
    public AdminDataDictionaryService(DataSource dataSource) {
        this(dataSource, Clock.systemUTC(), DEFAULT_CACHE_TTL_MILLIS);
    }

    AdminDataDictionaryService(DataSource dataSource, Clock clock, long cacheTtlMillis) {
        this.dataSource = dataSource;
        this.clock = clock;
        this.cacheTtlMillis = Math.max(0, cacheTtlMillis);
    }

    public AdminDataDictionaryDtos.Overview overview() {
        Snapshot snapshot = snapshot();
        long columnCount = snapshot.tables.values().stream()
                .mapToLong(table -> table.columns().size()).sum();
        long sensitiveColumnCount = snapshot.tables.values().stream()
                .flatMap(table -> table.columns().stream())
                .filter(AdminDataDictionaryDtos.Column::sensitive)
                .count();
        long indexCount = snapshot.tables.values().stream()
                .mapToLong(table -> table.indexes().size()).sum();
        long foreignKeyCount = snapshot.tables.values().stream()
                .mapToLong(table -> table.foreignKeys().size()).sum();
        return new AdminDataDictionaryDtos.Overview(
                snapshot.catalog(), snapshot.tables.size(), columnCount, sensitiveColumnCount,
                indexCount, foreignKeyCount,
                snapshot.schemaFingerprint(), snapshot.latestFlywayVersion(), snapshot.generatedAt());
    }

    public PageResult<AdminDataDictionaryDtos.TableSummary> tables(AdminDataDictionaryDtos.TableQuery query) {
        Filter filter = Filter.from(query);
        List<AdminDataDictionaryDtos.TableSummary> records = snapshot().tables.values().stream()
                .map(TableMetadata::summary)
                .filter(item -> filter.matches(item))
                .sorted(filter.comparator())
                .toList();
        long total = records.size();
        long offset = filter.offset(total);
        int from = offset >= total ? records.size() : (int) offset;
        int to = (int) Math.min(total, offset + filter.pageSize());
        return PageResult.of(records.subList(from, to), total, filter.pageNo(), filter.pageSize());
    }

    public AdminDataDictionaryDtos.TableDetail table(String tableName) {
        if (tableName == null || !TABLE_NAME_PATTERN.matcher(tableName).matches()) {
            throw BusinessException.badRequest("数据表名称不合法");
        }
        Snapshot snapshot = snapshot();
        TableMetadata table = snapshot.tables().get(tableName);
        if (table == null) {
            throw BusinessException.notFound("数据表不存在");
        }
        return table.detail(snapshot.catalog(), snapshot.schemaFingerprint(), snapshot.generatedAt());
    }

    private Snapshot snapshot() {
        long now = clock.millis();
        Snapshot current = cache.get();
        if (current != null && now - current.loadedAtMillis() < cacheTtlMillis) return current;
        synchronized (cache) {
            current = cache.get();
            now = clock.millis();
            if (current != null && now - current.loadedAtMillis() < cacheTtlMillis) return current;
            Snapshot loaded = loadSnapshot(now);
            cache.set(loaded);
            return loaded;
        }
    }

    private Snapshot loadSnapshot(long loadedAtMillis) {
        try (Connection connection = dataSource.getConnection()) {
            String catalog = connection.getCatalog();
            if (catalog == null || catalog.isBlank()) {
                throw new SQLException("JDBC catalog is unavailable");
            }
            DatabaseMetaData metadata = connection.getMetaData();
            Map<String, TableMetadata> tables = readTables(metadata, catalog);
            String latestFlywayVersion = readLatestFlywayVersion(connection, tables.keySet());
            String fingerprint = fingerprint(catalog, tables);
            return new Snapshot(catalog, Map.copyOf(tables), fingerprint, latestFlywayVersion,
                    Instant.ofEpochMilli(loadedAtMillis), loadedAtMillis);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            // Never return JDBC driver messages, SQL, catalog credentials, or connection URLs to a caller.
            throw BusinessException.serviceUnavailable("数据库字典暂时不可用，请稍后重试");
        }
    }

    private Map<String, TableMetadata> readTables(DatabaseMetaData metadata, String catalog) throws SQLException {
        Map<String, TableMetadata> tables = new LinkedHashMap<>();
        try (ResultSet result = metadata.getTables(catalog, null, "%", new String[]{"TABLE", "VIEW"})) {
            while (result.next()) {
                String tableName = result.getString("TABLE_NAME");
                if (tableName == null || !TABLE_NAME_PATTERN.matcher(tableName).matches()) continue;
                String type = valueOrEmpty(result.getString("TABLE_TYPE"));
                String comment = cleanComment(result.getString("REMARKS"));
                tables.put(tableName, new TableMetadata(catalog, tableName, type, comment,
                        readColumns(metadata, catalog, tableName), readPrimaryKeys(metadata, catalog, tableName),
                        readIndexes(metadata, catalog, tableName), readForeignKeys(metadata, catalog, tableName)));
            }
        }
        return tables;
    }

    private List<AdminDataDictionaryDtos.Column> readColumns(DatabaseMetaData metadata, String catalog,
                                                               String tableName) throws SQLException {
        List<AdminDataDictionaryDtos.Column> columns = new ArrayList<>();
        try (ResultSet result = metadata.getColumns(catalog, null, tableName, "%")) {
            while (result.next()) {
                String name = result.getString("COLUMN_NAME");
                if (name == null || name.isBlank()) continue;
                String comment = cleanComment(result.getString("REMARKS"));
                boolean sensitive = isSensitive(name, comment);
                String rawDefault = result.getString("COLUMN_DEF");
                columns.add(new AdminDataDictionaryDtos.Column(
                        result.getInt("ORDINAL_POSITION"), name,
                        valueOrEmpty(result.getString("DATA_TYPE")),
                        valueOrEmpty(result.getString("TYPE_NAME")),
                        nullableInt(result, "COLUMN_SIZE"), nullableInt(result, "DECIMAL_DIGITS"),
                        result.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls,
                        sensitive ? null : rawDefault,
                        sensitive && rawDefault != null ? MASKING_DEFAULT : null,
                        sensitive && rawDefault != null,
                        "YES".equalsIgnoreCase(result.getString("IS_AUTOINCREMENT")),
                        comment, sensitive,
                        sensitive ? MASKING_DEFAULT : null));
            }
        }
        columns.sort(Comparator.comparingInt(AdminDataDictionaryDtos.Column::ordinalPosition)
                .thenComparing(AdminDataDictionaryDtos.Column::columnName));
        return List.copyOf(columns);
    }

    private List<AdminDataDictionaryDtos.PrimaryKey> readPrimaryKeys(DatabaseMetaData metadata, String catalog,
                                                                       String tableName) throws SQLException {
        List<AdminDataDictionaryDtos.PrimaryKey> keys = new ArrayList<>();
        try (ResultSet result = metadata.getPrimaryKeys(catalog, null, tableName)) {
            while (result.next()) {
                String column = result.getString("COLUMN_NAME");
                if (column == null || column.isBlank()) continue;
                keys.add(new AdminDataDictionaryDtos.PrimaryKey(
                        valueOrEmpty(result.getString("PK_NAME")), result.getShort("KEY_SEQ"), column));
            }
        }
        keys.sort(Comparator.comparingInt(AdminDataDictionaryDtos.PrimaryKey::keySequence)
                .thenComparing(AdminDataDictionaryDtos.PrimaryKey::columnName));
        return List.copyOf(keys);
    }

    private List<AdminDataDictionaryDtos.Index> readIndexes(DatabaseMetaData metadata, String catalog,
                                                              String tableName) throws SQLException {
        Map<String, IndexBuilder> builders = new LinkedHashMap<>();
        try (ResultSet result = metadata.getIndexInfo(catalog, null, tableName, false, false)) {
            while (result.next()) {
                String name = result.getString("INDEX_NAME");
                String column = result.getString("COLUMN_NAME");
                if (name == null || name.isBlank() || column == null || column.isBlank()) continue;
                boolean unique = !resultValueBoolean(result, "NON_UNIQUE");
                IndexBuilder builder = builders.computeIfAbsent(name,
                        ignored -> new IndexBuilder(name, unique));
                builder.columns.add(new OrderedColumn(result.getShort("ORDINAL_POSITION"), column));
            }
        }
        List<String> primaryColumns = readPrimaryKeys(metadata, catalog, tableName).stream()
                .map(AdminDataDictionaryDtos.PrimaryKey::columnName).toList();
        List<AdminDataDictionaryDtos.Index> indexes = new ArrayList<>();
        for (IndexBuilder builder : builders.values()) {
            builder.columns.sort(Comparator.comparingInt(OrderedColumn::order)
                    .thenComparing(OrderedColumn::name));
            List<String> columns = builder.columns.stream().map(OrderedColumn::name).toList();
            boolean primary = "PRIMARY".equalsIgnoreCase(builder.name)
                    || (!primaryColumns.isEmpty() && primaryColumns.equals(columns));
            indexes.add(new AdminDataDictionaryDtos.Index(builder.name, builder.unique, primary, columns));
        }
        indexes.sort(Comparator.comparing(AdminDataDictionaryDtos.Index::indexName));
        return List.copyOf(indexes);
    }

    private List<AdminDataDictionaryDtos.ForeignKey> readForeignKeys(DatabaseMetaData metadata, String catalog,
                                                                       String tableName) throws SQLException {
        List<AdminDataDictionaryDtos.ForeignKey> keys = new ArrayList<>();
        try (ResultSet result = metadata.getImportedKeys(catalog, null, tableName)) {
            while (result.next()) {
                String column = result.getString("FKCOLUMN_NAME");
                String referencedTable = result.getString("PKTABLE_NAME");
                String referencedColumn = result.getString("PKCOLUMN_NAME");
                if (column == null || referencedTable == null || referencedColumn == null) continue;
                String referencedCatalog = valueOrEmpty(result.getString("PKTABLE_CAT"));
                if (referencedCatalog.isBlank()) referencedCatalog = catalog;
                keys.add(new AdminDataDictionaryDtos.ForeignKey(
                        valueOrEmpty(result.getString("FK_NAME")), result.getShort("KEY_SEQ"), column,
                        referencedCatalog, referencedTable, referencedColumn,
                        rule(result.getShort("UPDATE_RULE")), rule(result.getShort("DELETE_RULE"))));
            }
        }
        keys.sort(Comparator.comparing(AdminDataDictionaryDtos.ForeignKey::constraintName)
                .thenComparingInt(AdminDataDictionaryDtos.ForeignKey::keySequence));
        return List.copyOf(keys);
    }

    private String readLatestFlywayVersion(Connection connection, Set<String> tableNames) {
        boolean present = tableNames.stream().anyMatch(name -> "flyway_schema_history".equalsIgnoreCase(name));
        if (!present) return null;
        String sql = "SELECT version FROM flyway_schema_history WHERE success = 1 "
                + "ORDER BY installed_rank DESC LIMIT 1";
        try (var statement = connection.prepareStatement(sql); var result = statement.executeQuery()) {
            return result.next() ? result.getString(1) : null;
        } catch (Exception ignored) {
            // The dictionary remains useful when a legacy Flyway table has an older layout.
            return null;
        }
    }

    private String fingerprint(String catalog, Map<String, TableMetadata> tables) {
        StringBuilder canonical = new StringBuilder(catalog);
        tables.values().stream().sorted(Comparator.comparing(TableMetadata::tableName)).forEach(table -> {
            canonical.append('|').append(table.tableName()).append('|').append(table.type())
                    .append('|').append(table.comment());
            table.columns().forEach(column -> canonical.append('|').append(column.ordinalPosition())
                    .append(':').append(column.columnName()).append(':').append(column.dataType())
                    .append(':').append(column.nativeType()).append(':').append(column.length())
                    .append(':').append(column.scale()).append(':').append(column.nullable())
                    .append(':').append(column.autoIncrement()).append(':').append(column.comment())
                    .append(':').append(column.sensitive()));
            table.indexes().forEach(index -> canonical.append('|').append(index.indexName())
                    .append(':').append(index.unique()).append(':').append(index.primary()).append(':')
                    .append(index.columns()));
            table.foreignKeys().forEach(key -> canonical.append('|').append(key.constraintName())
                    .append(':').append(key.columnName()).append(':').append(key.referencedTable())
                    .append(':').append(key.referencedColumn()));
        });
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) hex.append(String.format(Locale.ROOT, "%02x", value));
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private boolean isSensitive(String name, String comment) {
        return SENSITIVE_NAME_PATTERN.matcher(name).find()
                || (comment != null && SENSITIVE_NAME_PATTERN.matcher(comment).find());
    }

    private static Integer nullableInt(ResultSet result, String column) throws SQLException {
        int value = result.getInt(column);
        return result.wasNull() ? null : value;
    }

    private static boolean resultValueBoolean(ResultSet result, String column) throws SQLException {
        String value = result.getString(column);
        return "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value) || "1".equals(value)
                || result.getBoolean(column);
    }

    private static String rule(short rule) {
        return switch (rule) {
            case DatabaseMetaData.importedKeyCascade -> "CASCADE";
            case DatabaseMetaData.importedKeyRestrict -> "RESTRICT";
            case DatabaseMetaData.importedKeySetNull -> "SET NULL";
            case DatabaseMetaData.importedKeyNoAction -> "NO ACTION";
            case DatabaseMetaData.importedKeySetDefault -> "SET DEFAULT";
            default -> "UNKNOWN";
        };
    }

    private static String valueOrEmpty(String value) { return value == null ? "" : value; }

    /** Keep database remarks safe for UI rendering and bounded for cache/fingerprint size. */
    private static String cleanComment(String value) {
        if (value == null || value.isBlank()) return "";
        StringBuilder cleaned = new StringBuilder(Math.min(value.length(), MAX_COMMENT_LENGTH));
        for (int i = 0; i < value.length() && cleaned.length() < MAX_COMMENT_LENGTH; i++) {
            char character = value.charAt(i);
            if (character == '\n' || character == '\r' || character == '\t') {
                cleaned.append(' ');
            } else if (!Character.isISOControl(character)) {
                cleaned.append(character);
            }
        }
        return cleaned.toString().trim();
    }

    private record Snapshot(String catalog, Map<String, TableMetadata> tables, String schemaFingerprint,
                            String latestFlywayVersion, Instant generatedAt, long loadedAtMillis) {}

    private record OrderedColumn(short order, String name) {}

    private static final class IndexBuilder {
        private final String name;
        private final boolean unique;
        private final List<OrderedColumn> columns = new ArrayList<>();

        private IndexBuilder(String name, boolean unique) {
            this.name = name;
            this.unique = unique;
        }
    }

    private record TableMetadata(String catalog, String tableName, String type, String comment,
                                 List<AdminDataDictionaryDtos.Column> columns,
                                 List<AdminDataDictionaryDtos.PrimaryKey> primaryKeys,
                                 List<AdminDataDictionaryDtos.Index> indexes,
                                 List<AdminDataDictionaryDtos.ForeignKey> foreignKeys) {
        private AdminDataDictionaryDtos.TableSummary summary() {
            long sensitive = columns.stream().filter(AdminDataDictionaryDtos.Column::sensitive).count();
            return new AdminDataDictionaryDtos.TableSummary(tableName, comment, type, columns.size(), sensitive,
                    indexes.size(), foreignKeys.size(), !primaryKeys.isEmpty(), !foreignKeys.isEmpty(),
                    primaryKeys.stream().sorted(Comparator.comparingInt(AdminDataDictionaryDtos.PrimaryKey::keySequence))
                            .map(AdminDataDictionaryDtos.PrimaryKey::columnName).toList());
        }

        private AdminDataDictionaryDtos.TableDetail detail(String currentCatalog, String fingerprint,
                                                            Instant generatedAt) {
            List<AdminDataDictionaryDtos.UniqueConstraint> uniqueConstraints = indexes.stream()
                    .filter(index -> index.unique() && !index.primary())
                    .map(index -> new AdminDataDictionaryDtos.UniqueConstraint(index.indexName(), index.columns()))
                    .toList();
            return new AdminDataDictionaryDtos.TableDetail(currentCatalog, tableName, comment, type, columns,
                    indexes, primaryKeys, uniqueConstraints, foreignKeys, fingerprint, generatedAt);
        }
    }

    private record Filter(long pageNo, long pageSize, String keyword, String sortBy, String tableType,
                          Boolean sensitiveOnly, Boolean hasPrimaryKey, Boolean hasForeignKey,
                          boolean descending) {
        private static Filter from(AdminDataDictionaryDtos.TableQuery query) {
            long pageNo = query == null || query.pageNo() == null ? 1 : query.pageNo();
            long pageSize = query == null || query.pageSize() == null ? DEFAULT_PAGE_SIZE : query.pageSize();
            if (pageNo < 1) throw BusinessException.badRequest("页码必须大于 0");
            if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) throw BusinessException.badRequest("每页数量须在 1–100 之间");
            String sortBy = query == null || query.sortBy() == null || query.sortBy().isBlank()
                    ? "tableName" : query.sortBy().trim();
            if (!SORT_FIELDS.contains(sortBy)) throw BusinessException.badRequest("排序字段不合法");
            String sortOrder = query == null || query.sortOrder() == null || query.sortOrder().isBlank()
                    ? "asc" : query.sortOrder().trim().toLowerCase(Locale.ROOT);
            if (!"asc".equals(sortOrder) && !"desc".equals(sortOrder)) {
                throw BusinessException.badRequest("排序方向不合法");
            }
            String tableType = query == null ? "" : valueOrEmpty(query.tableType()).trim();
            if (tableType.length() > 32 || tableType.contains("%") || tableType.contains("_")) {
                throw BusinessException.badRequest("数据表类型筛选不合法");
            }
            return new Filter(pageNo, pageSize,
                    query == null ? "" : valueOrEmpty(query.keyword()).trim().toLowerCase(Locale.ROOT),
                    sortBy, tableType, query == null ? null : query.sensitiveOnly(),
                    query == null ? null : query.hasPrimaryKey(), query == null ? null : query.hasForeignKey(),
                    "desc".equals(sortOrder));
        }

        private boolean matches(AdminDataDictionaryDtos.TableSummary item) {
            boolean keywordMatch = keyword.isBlank() || item.tableName().toLowerCase(Locale.ROOT).contains(keyword)
                    || item.tableComment().toLowerCase(Locale.ROOT).contains(keyword);
            boolean typeMatch = tableType.isBlank() || tableType.equalsIgnoreCase(item.tableType());
            boolean sensitiveMatch = sensitiveOnly == null
                    || sensitiveOnly == (item.sensitiveColumnCount() > 0);
            boolean primaryMatch = hasPrimaryKey == null || hasPrimaryKey == item.hasPrimaryKey();
            boolean foreignMatch = hasForeignKey == null || hasForeignKey == item.hasForeignKey();
            return keywordMatch && typeMatch && sensitiveMatch && primaryMatch && foreignMatch;
        }

        private Comparator<AdminDataDictionaryDtos.TableSummary> comparator() {
            Comparator<AdminDataDictionaryDtos.TableSummary> comparator = switch (sortBy) {
                case "tableComment" -> Comparator.comparing(AdminDataDictionaryDtos.TableSummary::tableComment,
                        String.CASE_INSENSITIVE_ORDER);
                case "tableType" -> Comparator.comparing(AdminDataDictionaryDtos.TableSummary::tableType,
                        String.CASE_INSENSITIVE_ORDER);
                case "columnCount" -> Comparator.comparingLong(AdminDataDictionaryDtos.TableSummary::columnCount);
                case "sensitiveColumnCount" -> Comparator.comparingLong(AdminDataDictionaryDtos.TableSummary::sensitiveColumnCount);
                default -> Comparator.comparing(AdminDataDictionaryDtos.TableSummary::tableName,
                        String.CASE_INSENSITIVE_ORDER);
            };
            Comparator<AdminDataDictionaryDtos.TableSummary> tieBreaker = Comparator.comparing(
                    AdminDataDictionaryDtos.TableSummary::tableName, String.CASE_INSENSITIVE_ORDER);
            return (descending ? comparator.reversed() : comparator).thenComparing(tieBreaker);
        }

        private long offset(long total) {
            try {
                long offset = Math.multiplyExact(pageNo - 1, pageSize);
                return Math.min(offset, total);
            } catch (ArithmeticException exception) {
                return total;
            }
        }
    }
}

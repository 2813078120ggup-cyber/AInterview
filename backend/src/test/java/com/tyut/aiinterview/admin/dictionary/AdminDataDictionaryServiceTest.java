package com.tyut.aiinterview.admin.dictionary;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.tyut.aiinterview.common.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class AdminDataDictionaryServiceTest {
    private final DataSource dataSource = mock(DataSource.class);
    private final AdminDataDictionaryService service = new AdminDataDictionaryService(
            dataSource, Clock.fixed(Instant.parse("2026-08-21T00:00:00Z"), ZoneOffset.UTC), 30_000);

    @Test
    void rejectsPageSizeAboveOneHundredBeforeOpeningJdbcConnection() {
        assertThatThrownBy(() -> service.tables(new AdminDataDictionaryDtos.TableQuery(
                1L, 101L, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("每页数量须在 1–100 之间");
    }

    @Test
    void rejectsUnknownSortField() {
        assertThatThrownBy(() -> service.tables(new AdminDataDictionaryDtos.TableQuery(
                1L, 20L, null, "tableSql", "asc")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("排序字段不合法");
    }

    @Test
    void rejectsUnsafeTableNameBeforeOpeningJdbcConnection() {
        assertThatThrownBy(() -> service.table("users;drop table users"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("数据表名称不合法");
    }

    @Test
    void rejectsWildcardTableTypeFilter() {
        assertThatThrownBy(() -> service.tables(new AdminDataDictionaryDtos.TableQuery(
                1L, 20L, null, null, null, "%", null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("数据表类型筛选不合法");
    }
}

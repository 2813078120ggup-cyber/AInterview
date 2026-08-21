package com.tyut.aiinterview.admin.dictionary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tyut.aiinterview.common.ApiResponse;
import com.tyut.aiinterview.common.PageResult;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class AdminDataDictionaryControllerTest {
    private final AdminDataDictionaryService service = mock(AdminDataDictionaryService.class);
    private final AdminDataDictionaryController controller = new AdminDataDictionaryController(service);

    @Test
    void controllerIsAdminOnlyAndEndpointsAreReadOnly() throws NoSuchMethodException {
        PreAuthorize annotation = AdminDataDictionaryController.class.getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasRole('ADMIN')");
        assertThat(AdminDataDictionaryController.class.getDeclaredMethod("overview")
                .getAnnotation(org.springframework.web.bind.annotation.GetMapping.class)).isNotNull();
        for (Method method : AdminDataDictionaryController.class.getDeclaredMethods()) {
            if (method.getName().equals("overview") || method.getName().equals("tables")
                    || method.getName().equals("table")) {
                assertThat(method.getAnnotation(org.springframework.web.bind.annotation.GetMapping.class)).isNotNull();
            }
        }
    }

    @Test
    void tablesEndpointDelegatesQuery() {
        AdminDataDictionaryDtos.TableQuery query = new AdminDataDictionaryDtos.TableQuery(
                1L, 20L, "user", "tableName", "asc");
        PageResult<AdminDataDictionaryDtos.TableSummary> expected = PageResult.of(List.of(), 0, 1, 20);
        when(service.tables(query)).thenReturn(expected);

        ApiResponse<PageResult<AdminDataDictionaryDtos.TableSummary>> response = controller.tables(query);

        assertThat(response.data()).isSameAs(expected);
        verify(service).tables(query);
    }
}

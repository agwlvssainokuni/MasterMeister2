/*
 * Copyright 2026 agwlvssainokuni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cherry.mastermeister.audit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import cherry.mastermeister.common.PageRequest;
import cherry.mastermeister.common.PageResult;
import cherry.mastermeister.security.JwtTokenValidator;
import cherry.mastermeister.security.RestAccessDeniedHandler;
import cherry.mastermeister.security.RestAuthenticationEntryPoint;
import cherry.mastermeister.security.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * example-basedテスト: 正常系（絞り込みあり/なし、ページング）、管理者以外での403、
 * 未認証での401を検証する。P8（例外→HTTPステータスマッピング）は{@code GlobalExceptionHandlerTest}
 * （jqwikによるproperty-basedテスト）で別途検証する。
 */
@WebMvcTest(AuditLogController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class AuditLogControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @MockitoBean
    private AuditLogService auditLogService;

    @MockitoBean
    private JwtTokenValidator jwtTokenValidator;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void searchWithoutFiltersReturnsOkForAdmin() throws Exception {
        AuditLog log = new AuditLog(
                Instant.parse("2026-01-01T00:00:00Z"), 1L, null,
                EventCategory.ADMIN_OPERATION, EventType.USER_REGISTRATION_APPROVED,
                Result.SUCCESS, "target", "summary");
        when(auditLogService.search(any(), any()))
                .thenReturn(new PageResult<>(List.of(log), 1L, 0, 20));

        mockMvc.perform(get("/api/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].eventType").value("USER_REGISTRATION_APPROVED"))
                .andExpect(jsonPath("$.totalCount").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void searchWithFiltersPassesCriteriaToService() throws Exception {
        when(auditLogService.search(any(), any()))
                .thenReturn(new PageResult<>(List.of(), 0L, 0, 20));

        mockMvc.perform(get("/api/audit-logs")
                        .param("dateFrom", "2026-01-01T00:00:00Z")
                        .param("dateTo", "2026-01-31T00:00:00Z")
                        .param("userId", "42")
                        .param("eventCategory", "ADMIN_OPERATION")
                        .param("eventType", "USER_REGISTRATION_APPROVED"))
                .andExpect(status().isOk());

        ArgumentCaptor<AuditLogFilterCriteria> criteriaCaptor =
                ArgumentCaptor.forClass(AuditLogFilterCriteria.class);
        verify(auditLogService).search(criteriaCaptor.capture(), any());
        AuditLogFilterCriteria criteria = criteriaCaptor.getValue();
        org.assertj.core.api.Assertions.assertThat(criteria.dateFrom())
                .isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        org.assertj.core.api.Assertions.assertThat(criteria.dateTo())
                .isEqualTo(Instant.parse("2026-01-31T00:00:00Z"));
        org.assertj.core.api.Assertions.assertThat(criteria.userId()).isEqualTo(42L);
        org.assertj.core.api.Assertions.assertThat(criteria.eventCategory())
                .isEqualTo(EventCategory.ADMIN_OPERATION);
        org.assertj.core.api.Assertions.assertThat(criteria.eventType())
                .isEqualTo(EventType.USER_REGISTRATION_APPROVED);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void searchPassesPageAndPageSizeToService() throws Exception {
        when(auditLogService.search(any(), any()))
                .thenReturn(new PageResult<>(List.of(), 0L, 2, 50));

        mockMvc.perform(get("/api/audit-logs").param("page", "2").param("pageSize", "50"))
                .andExpect(status().isOk());

        ArgumentCaptor<PageRequest> pageRequestCaptor = ArgumentCaptor.forClass(PageRequest.class);
        verify(auditLogService).search(any(), pageRequestCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(pageRequestCaptor.getValue())
                .isEqualTo(new PageRequest(2, 50));
    }

    @Test
    @WithMockUser(roles = "USER")
    void searchReturnsForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(get("/api/audit-logs")).andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void searchReturnsUnauthorizedWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/audit-logs")).andExpect(status().isUnauthorized());
    }

}
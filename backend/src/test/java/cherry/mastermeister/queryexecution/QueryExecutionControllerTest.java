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

package cherry.mastermeister.queryexecution;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import cherry.mastermeister.common.exception.ValidationException;
import cherry.mastermeister.security.JwtTokenValidator;
import cherry.mastermeister.security.RestAccessDeniedHandler;
import cherry.mastermeister.security.RestAuthenticationEntryPoint;
import cherry.mastermeister.security.SecurityConfig;

/**
 * example-basedテスト: 2エンドポイント（手入力SQL実行・保存済みクエリ実行）それぞれについて
 * 認証済みユーザ成功系・未認証401・読み取り専用違反SQLでの400
 * （{@code ValidationException}、{@code GlobalExceptionHandler}既存マッピング）を検証する。
 * P4〜P8は{@code QueryExecutionServiceTest}（jqwikによるproperty-basedテスト）で別途検証する。
 */
@WebMvcTest(QueryExecutionController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class QueryExecutionControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @MockitoBean
    private QueryExecutionService queryExecutionService;

    @MockitoBean
    private JwtTokenValidator jwtTokenValidator;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    private static Authentication userAuthentication() {
        return new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Test
    void executeAdhocSqlReturnsOkForAuthenticatedUser() throws Exception {
        when(queryExecutionService.executeAdhocSql(
                eq(1L), eq(42L), eq("S1"), eq("SELECT 1"), eq(Map.of()), any(PagingOption.class)))
                .thenReturn(new QueryResult(
                        List.of(new ResultColumn("c1", "INTEGER")), List.of(List.of(1)), 1, false));

        mockMvc.perform(post("/api/query-execution/adhoc")
                        .with(authentication(userAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"connectionId":42,"schema":"S1","sql":"SELECT 1","params":{},
                                 "paging":{"enabled":false,"page":0,"pageSize":0}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(1));
    }

    @Test
    @WithAnonymousUser
    void executeAdhocSqlReturnsUnauthorizedWhenNotAuthenticated() throws Exception {
        mockMvc.perform(post("/api/query-execution/adhoc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"connectionId":42,"schema":"S1","sql":"SELECT 1","params":{},
                                 "paging":{"enabled":false,"page":0,"pageSize":0}}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void executeAdhocSqlReturnsBadRequestOnReadOnlyViolation() throws Exception {
        when(queryExecutionService.executeAdhocSql(
                eq(1L), eq(42L), eq("S1"), eq("DELETE FROM tbl"), eq(Map.of()), any(PagingOption.class)))
                .thenThrow(new ValidationException("読み取り専用SQL（SELECT文）1件のみ実行できます"));

        mockMvc.perform(post("/api/query-execution/adhoc")
                        .with(authentication(userAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"connectionId":42,"schema":"S1","sql":"DELETE FROM tbl","params":{},
                                 "paging":{"enabled":false,"page":0,"pageSize":0}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void executeSavedQueryReturnsOkForAuthenticatedUser() throws Exception {
        when(queryExecutionService.executeSavedQuery(
                eq(1L), eq(42L), eq("S1"), eq(10L), eq(Map.of()), any(PagingOption.class)))
                .thenReturn(new QueryResult(
                        List.of(new ResultColumn("c1", "INTEGER")), List.of(List.of(1)), 1, false));

        mockMvc.perform(post("/api/query-execution/saved/{savedQueryId}", 10L)
                        .with(authentication(userAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"connectionId":42,"schema":"S1","params":{},
                                 "paging":{"enabled":false,"page":0,"pageSize":0}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(1));
    }

    @Test
    @WithAnonymousUser
    void executeSavedQueryReturnsUnauthorizedWhenNotAuthenticated() throws Exception {
        mockMvc.perform(post("/api/query-execution/saved/{savedQueryId}", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"connectionId":42,"schema":"S1","params":{},
                                 "paging":{"enabled":false,"page":0,"pageSize":0}}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listAccessibleSchemasReturnsOkForAuthenticatedUser() throws Exception {
        when(queryExecutionService.listAccessibleSchemas(eq(1L), eq(42L))).thenReturn(List.of("S1", "S2"));

        mockMvc.perform(get("/api/query-execution/{connectionId}/schemas", 42L)
                        .with(authentication(userAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("S1"))
                .andExpect(jsonPath("$[1]").value("S2"));
    }

    @Test
    @WithAnonymousUser
    void listAccessibleSchemasReturnsUnauthorizedWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/query-execution/{connectionId}/schemas", 42L))
                .andExpect(status().isUnauthorized());
    }

}
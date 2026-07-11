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

package cherry.mastermeister.masterdata;

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

import cherry.mastermeister.common.PageRequest;
import cherry.mastermeister.common.PageResult;
import cherry.mastermeister.permission.Permission;
import cherry.mastermeister.schema.TableType;
import cherry.mastermeister.security.JwtTokenValidator;
import cherry.mastermeister.security.RestAccessDeniedHandler;
import cherry.mastermeister.security.RestAuthenticationEntryPoint;
import cherry.mastermeister.security.SecurityConfig;
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

/**
 * example-basedテスト: スキーマ/テーブル一覧・レコード検索・レコード更新の4エンドポイントを
 * {@code @WithMockUser}（ロール制約なし）での成功系、未認証401で検証する。本ユニットは
 * 管理者ロール制約を持たないため（{@code business-rules.md} 4節）403系テストは不要。
 * P1〜P10（業務ロジックの性質）は{@code MasterDataQueryServiceTest}/
 * {@code MasterDataMutationServiceTest}（jqwikによるproperty-basedテスト）で別途検証する。
 */
@WebMvcTest(MasterDataController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class MasterDataControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @MockitoBean
    private MasterDataQueryService masterDataQueryService;

    @MockitoBean
    private MasterDataMutationService masterDataMutationService;

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
    void listAccessibleSchemasReturnsOkForAuthenticatedUser() throws Exception {
        when(masterDataQueryService.listAccessibleSchemas(1L, 42L)).thenReturn(List.of("public"));

        mockMvc.perform(get("/api/master-data/{connectionId}/schemas", 42L)
                        .with(authentication(userAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("public"));
    }

    @Test
    @WithAnonymousUser
    void listAccessibleSchemasReturnsUnauthorizedWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/master-data/{connectionId}/schemas", 42L))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listAccessibleTablesReturnsOkForAuthenticatedUser() throws Exception {
        when(masterDataQueryService.listAccessibleTables(1L, 42L, "public")).thenReturn(List.of(
                new TableSummary("public", "employees", TableType.TABLE, "comment", Permission.READ, false, false)));

        mockMvc.perform(get("/api/master-data/{connectionId}/schemas/{schema}/tables", 42L, "public")
                        .with(authentication(userAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tableName").value("employees"));
    }

    @Test
    @WithAnonymousUser
    void listAccessibleTablesReturnsUnauthorizedWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/master-data/{connectionId}/schemas/{schema}/tables", 42L, "public"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listRecordsReturnsOkForAuthenticatedUser() throws Exception {
        FilterCriteria criteria = new FilterCriteria(FilterMode.RAW, List.of(), List.of(), null, null);
        RecordListResult result = new RecordListResult(
                List.of(new ColumnMetadata("id", "INTEGER", false, 1, Permission.READ)),
                new PageResult<>(List.of(List.of(1)), 1, 0, 50));
        when(masterDataQueryService.listRecords(
                eq(1L), eq(42L), eq("public"), eq("employees"), eq(criteria), eq(new PageRequest(0, 50))))
                .thenReturn(result);

        mockMvc.perform(post(
                        "/api/master-data/{connectionId}/schemas/{schema}/tables/{table}/records:search",
                        42L, "public", "employees")
                        .with(authentication(userAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"criteria":{"mode":"RAW","uiConditions":[],"uiSorts":[],
                                 "rawWhere":null,"rawOrderBy":null},"page":0,"pageSize":50}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns[0].columnName").value("id"))
                .andExpect(jsonPath("$.records.totalCount").value(1));
    }

    @Test
    @WithAnonymousUser
    void listRecordsReturnsUnauthorizedWhenNotAuthenticated() throws Exception {
        mockMvc.perform(post(
                        "/api/master-data/{connectionId}/schemas/{schema}/tables/{table}/records:search",
                        42L, "public", "employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"criteria":{"mode":"RAW","uiConditions":[],"uiSorts":[],
                                 "rawWhere":null,"rawOrderBy":null},"page":0,"pageSize":50}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void applyChangesReturnsOkForAuthenticatedUser() throws Exception {
        MutationRequest request = new MutationRequest(
                List.of(new RecordCreate(Map.of("id", 1, "name", "foo"))), List.of(), List.of());
        when(masterDataMutationService.applyChanges(eq(1L), eq(42L), eq("public"), eq("employees"), eq(request)))
                .thenReturn(new MutationResult(true, 1, 0, 0, null));

        mockMvc.perform(post(
                        "/api/master-data/{connectionId}/schemas/{schema}/tables/{table}/records:apply",
                        42L, "public", "employees")
                        .with(authentication(userAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"creates":[{"values":{"id":1,"name":"foo"}}],"updates":[],"deletes":[]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.createdCount").value(1));
    }

    @Test
    @WithAnonymousUser
    void applyChangesReturnsUnauthorizedWhenNotAuthenticated() throws Exception {
        mockMvc.perform(post(
                        "/api/master-data/{connectionId}/schemas/{schema}/tables/{table}/records:apply",
                        42L, "public", "employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"creates":[{"values":{"id":1,"name":"foo"}}],"updates":[],"deletes":[]}
                                """))
                .andExpect(status().isUnauthorized());
    }

}
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

package cherry.mastermeister.querybuilder;

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
import java.util.Optional;

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

import cherry.mastermeister.security.JwtTokenValidator;
import cherry.mastermeister.security.RestAccessDeniedHandler;
import cherry.mastermeister.security.RestAuthenticationEntryPoint;
import cherry.mastermeister.security.SecurityConfig;

/**
 * example-basedテスト: 5エンドポイント（スキーマ一覧・テーブル一覧・カラム一覧・
 * SQL生成・SQL解析）それぞれについて認証済みユーザ成功系・未認証401を検証する。
 * 本ユニットは管理者ロール制約を
 * 持たないため（{@code business-rules.md} 8節「認証済みユーザ全員」）403系テストは不要。
 * P1〜P10（業務ロジックの性質）は{@code QueryBuilderMetadataServiceTest}/
 * {@code SqlGenerationServiceTest}/{@code SqlParsingServiceTest}/{@code QueryBuilderRoundTripTest}
 * （jqwikによるproperty-basedテスト）で別途検証する。
 */
@WebMvcTest(QueryBuilderController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class QueryBuilderControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @MockitoBean
    private QueryBuilderMetadataService queryBuilderMetadataService;

    @MockitoBean
    private SqlGenerationService sqlGenerationService;

    @MockitoBean
    private SqlParsingService sqlParsingService;

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
    void listSelectableSchemasReturnsOkForAuthenticatedUser() throws Exception {
        when(queryBuilderMetadataService.listSelectableSchemas(1L, 42L)).thenReturn(List.of("public"));

        mockMvc.perform(get("/api/query-builder/{connectionId}/schemas", 42L)
                        .with(authentication(userAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("public"));
    }

    @Test
    @WithAnonymousUser
    void listSelectableSchemasReturnsUnauthorizedWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/query-builder/{connectionId}/schemas", 42L))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listSelectableTablesReturnsOkForAuthenticatedUser() throws Exception {
        when(queryBuilderMetadataService.listSelectableTables(1L, 42L, "public")).thenReturn(List.of(
                new TableRef("public", "employees", "comment")));

        mockMvc.perform(get("/api/query-builder/{connectionId}/schemas/{schema}/tables", 42L, "public")
                        .with(authentication(userAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].table").value("employees"));
    }

    @Test
    @WithAnonymousUser
    void listSelectableTablesReturnsUnauthorizedWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/query-builder/{connectionId}/schemas/{schema}/tables", 42L, "public"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listSelectableColumnsReturnsOkForAuthenticatedUser() throws Exception {
        when(queryBuilderMetadataService.listSelectableColumns(1L, 42L, "public", "employees")).thenReturn(List.of(
                new ColumnRef("id", "INTEGER", false)));

        mockMvc.perform(get(
                        "/api/query-builder/{connectionId}/schemas/{schema}/tables/{table}/columns",
                        42L, "public", "employees")
                        .with(authentication(userAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].columnName").value("id"));
    }

    @Test
    @WithAnonymousUser
    void listSelectableColumnsReturnsUnauthorizedWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get(
                        "/api/query-builder/{connectionId}/schemas/{schema}/tables/{table}/columns",
                        42L, "public", "employees"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void generateReturnsOkForAuthenticatedUser() throws Exception {
        when(sqlGenerationService.generate(eq(42L), any(QueryBuilderModel.class)))
                .thenReturn(new GeneratedSql("SELECT \"t0\".\"id\" FROM \"public\".\"employees\" AS \"t0\"", Map.of()));

        mockMvc.perform(post("/api/query-builder/{connectionId}/generate", 42L)
                        .with(authentication(userAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"selectItems":[{"tableAlias":"t0","columnName":"id",
                                 "aggregateFunction":"NONE","outputAlias":null}],
                                 "fromItem":{"schema":"public","table":"employees","alias":"t0"},
                                 "joinItems":[],"whereConditions":[],"groupByColumns":[],
                                 "havingConditions":[],"orderByItems":[],"limit":null,"offset":null}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sql").value("SELECT \"t0\".\"id\" FROM \"public\".\"employees\" AS \"t0\""));
    }

    @Test
    @WithAnonymousUser
    void generateReturnsUnauthorizedWhenNotAuthenticated() throws Exception {
        mockMvc.perform(post("/api/query-builder/{connectionId}/generate", 42L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"selectItems":[],"fromItem":{"schema":"public","table":"employees","alias":"t0"},
                                 "joinItems":[],"whereConditions":[],"groupByColumns":[],
                                 "havingConditions":[],"orderByItems":[],"limit":null,"offset":null}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void parseReturnsOkForAuthenticatedUser() throws Exception {
        QueryBuilderModel model = new QueryBuilderModel(
                List.of(new SelectItem("t0", "id", AggregateFunction.NONE, null)),
                new FromItem("public", "employees", "t0"),
                List.of(), List.of(), List.of(), List.of(), List.of(), null, null);
        when(sqlParsingService.parse(1L, 42L, "SELECT t0.id FROM public.employees t0"))
                .thenReturn(new ParseResult(true, Optional.of(model), Optional.empty()));

        mockMvc.perform(post("/api/query-builder/{connectionId}/parse", 42L)
                        .with(authentication(userAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rawSql":"SELECT t0.id FROM public.employees t0"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullyParsed").value(true));
    }

    @Test
    @WithAnonymousUser
    void parseReturnsUnauthorizedWhenNotAuthenticated() throws Exception {
        mockMvc.perform(post("/api/query-builder/{connectionId}/parse", 42L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rawSql":"SELECT t0.id FROM public.employees t0"}
                                """))
                .andExpect(status().isUnauthorized());
    }

}
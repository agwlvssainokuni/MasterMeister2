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

package cherry.mastermeister.schema;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import cherry.mastermeister.security.JwtTokenValidator;
import cherry.mastermeister.security.RestAccessDeniedHandler;
import cherry.mastermeister.security.RestAuthenticationEntryPoint;
import cherry.mastermeister.security.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * example-basedテスト: スキーマ取り込み・スキーマ/テーブル/カラム参照を
 * {@code @WithMockUser(roles = "ADMIN")}での成功系、管理者以外403・未認証401で検証する。
 * P7〜P9等（業務ロジックの性質）は{@code SchemaImportServiceTest}/{@code SchemaQueryServiceTest}
 * （jqwikによるproperty-basedテスト）で別途検証する。
 */
@WebMvcTest(SchemaController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class SchemaControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @MockitoBean
    private SchemaImportService schemaImportService;

    @MockitoBean
    private SchemaQueryService schemaQueryService;

    @MockitoBean
    private JwtTokenValidator jwtTokenValidator;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void importSchemaReturnsOkForAdminAndUsesPrincipalAsAdminUserId() throws Exception {
        Authentication adminAuthentication = new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(schemaImportService.importSchema(eq(42L), eq(1L)))
                .thenReturn(new SchemaImportResult(true, 5, "OK"));

        mockMvc.perform(post("/api/rdbms-connections/{connectionId}/schema-import", 42L)
                        .with(authentication(adminAuthentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.tableCount").value(5));
    }

    @Test
    @WithMockUser(roles = "USER")
    void importSchemaReturnsForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(post("/api/rdbms-connections/{connectionId}/schema-import", 42L))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void importSchemaReturnsUnauthorizedWhenNotAuthenticated() throws Exception {
        mockMvc.perform(post("/api/rdbms-connections/{connectionId}/schema-import", 42L))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listSchemasReturnsOkForAdmin() throws Exception {
        when(schemaQueryService.listSchemas(42L)).thenReturn(List.of("public"));

        mockMvc.perform(get("/api/rdbms-connections/{connectionId}/schemas", 42L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("public"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void listSchemasReturnsForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(get("/api/rdbms-connections/{connectionId}/schemas", 42L))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void listSchemasReturnsUnauthorizedWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/rdbms-connections/{connectionId}/schemas", 42L))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listTablesReturnsOkForAdmin() throws Exception {
        when(schemaQueryService.listTables(42L, "public"))
                .thenReturn(List.of(new TableMetadata("public", "users", TableType.TABLE, "comment")));

        mockMvc.perform(get("/api/rdbms-connections/{connectionId}/schemas/{schema}/tables", 42L, "public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tableName").value("users"))
                .andExpect(jsonPath("$[0].tableType").value("TABLE"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void listTablesReturnsForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(get("/api/rdbms-connections/{connectionId}/schemas/{schema}/tables", 42L, "public"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void listTablesReturnsUnauthorizedWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/rdbms-connections/{connectionId}/schemas/{schema}/tables", 42L, "public"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getTableDetailReturnsOkForAdmin() throws Exception {
        when(schemaQueryService.getTableDetail(42L, "public", "users"))
                .thenReturn(new TableDetail("public", "users", TableType.TABLE, "comment",
                        List.of(new ColumnDetail("id", "BIGINT", false, "primary key", 1, 1))));

        mockMvc.perform(get(
                        "/api/rdbms-connections/{connectionId}/schemas/{schema}/tables/{table}", 42L, "public", "users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableName").value("users"))
                .andExpect(jsonPath("$.columns[0].columnName").value("id"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getTableDetailReturnsForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(get(
                        "/api/rdbms-connections/{connectionId}/schemas/{schema}/tables/{table}", 42L, "public", "users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void getTableDetailReturnsUnauthorizedWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get(
                        "/api/rdbms-connections/{connectionId}/schemas/{schema}/tables/{table}", 42L, "public", "users"))
                .andExpect(status().isUnauthorized());
    }

}
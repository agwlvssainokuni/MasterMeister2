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

package cherry.mastermeister.rdbmsconnection;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import cherry.mastermeister.common.dialect.RdbmsType;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * example-basedテスト: 接続CRUD・接続テスト（保存前/既存接続）を
 * {@code @WithMockUser(roles = "ADMIN")}での成功系、管理者以外403・未認証401で検証する。
 * P1〜P6（業務ロジックの性質）は{@code RdbmsConnectionServiceTest}
 * （jqwikによるproperty-basedテスト）で別途検証する。
 */
@WebMvcTest(RdbmsConnectionController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class RdbmsConnectionControllerTest {

    private static final String CONFIG_JSON = """
            {"name":"test","rdbmsType":"MYSQL","host":"localhost","port":3306,
             "databaseName":"mastermeister","username":"user","password":"pass",
             "additionalParams":null}
            """;

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @MockitoBean
    private RdbmsConnectionService rdbmsConnectionService;

    @MockitoBean
    private JwtTokenValidator jwtTokenValidator;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    private static ConnectionConfig connectionConfig() {
        return new ConnectionConfig(
                "test", RdbmsType.MYSQL, "localhost", 3306, "mastermeister", "user", "pass", null);
    }

    @Test
    void createConnectionReturnsCreatedForAdminAndUsesPrincipalAsAdminUserId() throws Exception {
        Authentication adminAuthentication = new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(rdbmsConnectionService.createConnection(eq(1L), eq(connectionConfig()))).thenReturn(42L);

        mockMvc.perform(post("/api/rdbms-connections")
                        .with(authentication(adminAuthentication))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CONFIG_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$").value(42));

        verify(rdbmsConnectionService).createConnection(1L, connectionConfig());
    }

    @Test
    @WithMockUser(roles = "USER")
    void createConnectionReturnsForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(post("/api/rdbms-connections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CONFIG_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void createConnectionReturnsUnauthorizedWhenNotAuthenticated() throws Exception {
        mockMvc.perform(post("/api/rdbms-connections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CONFIG_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateConnectionReturnsNoContentForAdminAndUsesPrincipalAsAdminUserId() throws Exception {
        Authentication adminAuthentication = new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        doNothing().when(rdbmsConnectionService).updateConnection(eq(1L), eq(42L), eq(connectionConfig()));

        mockMvc.perform(put("/api/rdbms-connections/{id}", 42L)
                        .with(authentication(adminAuthentication))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CONFIG_JSON))
                .andExpect(status().isNoContent());

        verify(rdbmsConnectionService).updateConnection(1L, 42L, connectionConfig());
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateConnectionReturnsForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(put("/api/rdbms-connections/{id}", 42L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CONFIG_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void updateConnectionReturnsUnauthorizedWhenNotAuthenticated() throws Exception {
        mockMvc.perform(put("/api/rdbms-connections/{id}", 42L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CONFIG_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listConnectionsReturnsOkForAdmin() throws Exception {
        when(rdbmsConnectionService.listConnections())
                .thenReturn(List.of(new ConnectionSummary(42L, "test", RdbmsType.MYSQL, "localhost", "mastermeister")));

        mockMvc.perform(get("/api/rdbms-connections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(42))
                .andExpect(jsonPath("$[0].name").value("test"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void listConnectionsReturnsForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(get("/api/rdbms-connections")).andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void listConnectionsReturnsUnauthorizedWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/rdbms-connections")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getConnectionReturnsOkForAdmin() throws Exception {
        when(rdbmsConnectionService.getConnection(42L))
                .thenReturn(new ConnectionDetail(
                        42L, "test", RdbmsType.MYSQL, "localhost", 3306, "mastermeister", "user", null));

        mockMvc.perform(get("/api/rdbms-connections/{id}", 42L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.username").value("user"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getConnectionReturnsForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(get("/api/rdbms-connections/{id}", 42L)).andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void getConnectionReturnsUnauthorizedWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/rdbms-connections/{id}", 42L)).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testConnectionByConfigReturnsOkForAdmin() throws Exception {
        when(rdbmsConnectionService.testConnection(connectionConfig()))
                .thenReturn(new ConnectionTestResult(true, "OK"));

        mockMvc.perform(post("/api/rdbms-connections/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CONFIG_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "USER")
    void testConnectionByConfigReturnsForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(post("/api/rdbms-connections/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CONFIG_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void testConnectionByConfigReturnsUnauthorizedWhenNotAuthenticated() throws Exception {
        mockMvc.perform(post("/api/rdbms-connections/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CONFIG_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testConnectionByIdReturnsOkForAdmin() throws Exception {
        when(rdbmsConnectionService.testConnection(42L))
                .thenReturn(new ConnectionTestResult(true, "OK"));

        mockMvc.perform(post("/api/rdbms-connections/{id}/test", 42L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "USER")
    void testConnectionByIdReturnsForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(post("/api/rdbms-connections/{id}/test", 42L)).andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void testConnectionByIdReturnsUnauthorizedWhenNotAuthenticated() throws Exception {
        mockMvc.perform(post("/api/rdbms-connections/{id}/test", 42L)).andExpect(status().isUnauthorized());
    }

}
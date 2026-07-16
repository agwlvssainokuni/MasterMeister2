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

package cherry.mastermeister.permission;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

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
import org.springframework.mock.web.MockMultipartFile;
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
 * example-basedテスト: 権限更新（setPermission/setAuxPermission分岐）・エクスポート・インポートを
 * {@code @WithMockUser(roles = "ADMIN")}での成功系、管理者以外403・未認証401、
 * インポート形式不正時の400で検証する。
 */
@WebMvcTest(PermissionController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class PermissionControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @MockitoBean
    private PermissionAssignmentService permissionAssignmentService;

    @MockitoBean
    private JwtTokenValidator jwtTokenValidator;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    private static Authentication adminAuthentication() {
        return new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void lookupPermissionReturnsCurrentPermissionForAdmin() throws Exception {
        when(permissionAssignmentService.lookupPermission(
                eq(new PrincipalRef(PrincipalType.USER, 7L)), eq(42L), eq("public"),
                eq(Optional.of("employees")), eq(Optional.of("email"))))
                .thenReturn(new PermissionLookupResponse(Permission.UPDATE, true, false));

        mockMvc.perform(get("/api/rdbms-connections/{connectionId}/permissions", 42L)
                        .param("principalType", "USER")
                        .param("principalId", "7")
                        .param("schema", "public")
                        .param("table", "employees")
                        .param("column", "email")
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permission").value("UPDATE"))
                .andExpect(jsonPath("$.auxCreate").value(true))
                .andExpect(jsonPath("$.auxDelete").value(false));
    }

    @Test
    @WithMockUser(roles = "USER")
    void lookupPermissionReturnsForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(get("/api/rdbms-connections/{connectionId}/permissions", 42L)
                        .param("principalType", "USER")
                        .param("principalId", "7")
                        .param("schema", "public"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void lookupPermissionReturnsUnauthorizedWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/rdbms-connections/{connectionId}/permissions", 42L)
                        .param("principalType", "USER")
                        .param("principalId", "7")
                        .param("schema", "public"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updatePermissionDispatchesToSetPermissionWhenPermissionPresent() throws Exception {
        doNothing().when(permissionAssignmentService).setPermission(
                eq(1L), eq(new PrincipalRef(PrincipalType.USER, 7L)), eq(42L), eq("public"),
                eq(Optional.of("employees")), eq(Optional.empty()), eq(Permission.READ));

        mockMvc.perform(put("/api/rdbms-connections/{connectionId}/permissions", 42L)
                        .with(authentication(adminAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"principal":{"principalType":"USER","principalId":7},
                                 "schema":"public","table":"employees","column":null,
                                 "permission":"READ","auxType":null,"granted":null}
                                """))
                .andExpect(status().isNoContent());

        verify(permissionAssignmentService).setPermission(
                1L, new PrincipalRef(PrincipalType.USER, 7L), 42L, "public",
                Optional.of("employees"), Optional.empty(), Permission.READ);
    }

    @Test
    void updatePermissionDispatchesToSetAuxPermissionWhenPermissionAbsent() throws Exception {
        doNothing().when(permissionAssignmentService).setAuxPermission(
                eq(1L), eq(new PrincipalRef(PrincipalType.GROUP, 3L)), eq(42L), eq("public"),
                eq(Optional.of("employees")), eq(AuxPermissionType.DELETE), eq(true));

        mockMvc.perform(put("/api/rdbms-connections/{connectionId}/permissions", 42L)
                        .with(authentication(adminAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"principal":{"principalType":"GROUP","principalId":3},
                                 "schema":"public","table":"employees","column":null,
                                 "permission":null,"auxType":"DELETE","granted":true}
                                """))
                .andExpect(status().isNoContent());

        verify(permissionAssignmentService).setAuxPermission(
                1L, new PrincipalRef(PrincipalType.GROUP, 3L), 42L, "public",
                Optional.of("employees"), AuxPermissionType.DELETE, true);
    }

    @Test
    @WithMockUser(roles = "USER")
    void updatePermissionReturnsForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(put("/api/rdbms-connections/{connectionId}/permissions", 42L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"principal":{"principalType":"USER","principalId":7},
                                 "schema":"public","table":null,"column":null,
                                 "permission":"READ","auxType":null,"granted":null}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void updatePermissionReturnsUnauthorizedWhenNotAuthenticated() throws Exception {
        mockMvc.perform(put("/api/rdbms-connections/{connectionId}/permissions", 42L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"principal":{"principalType":"USER","principalId":7},
                                 "schema":"public","table":null,"column":null,
                                 "permission":"READ","auxType":null,"granted":null}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void exportPermissionsReturnsYamlForAdmin() throws Exception {
        byte[] yaml = "principals: []\n".getBytes();
        when(permissionAssignmentService.exportPermissionsAsYaml(1L, 42L)).thenReturn(yaml);

        mockMvc.perform(get("/api/rdbms-connections/{connectionId}/permissions/export", 42L)
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/x-yaml"))
                .andExpect(header().string(
                        "Content-Disposition", "attachment; filename=permissions-42.yaml"));

        verify(permissionAssignmentService).exportPermissionsAsYaml(1L, 42L);
    }

    @Test
    @WithMockUser(roles = "USER")
    void exportPermissionsReturnsForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(get("/api/rdbms-connections/{connectionId}/permissions/export", 42L))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void exportPermissionsReturnsUnauthorizedWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/rdbms-connections/{connectionId}/permissions/export", 42L))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void importPermissionsReturnsResultForAdmin() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "permissions.yaml", "application/x-yaml", "principals: []\n".getBytes());
        when(permissionAssignmentService.importPermissionsFromYaml(eq(1L), eq(42L), any()))
                .thenReturn(new ImportResult(true, "Import succeeded."));

        mockMvc.perform(multipart("/api/rdbms-connections/{connectionId}/permissions/import", 42L)
                        .file(file)
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Import succeeded."));
    }

    @Test
    void importPermissionsReturnsBadRequestOnMalformedYaml() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "permissions.yaml", "application/x-yaml", "not: valid: yaml:".getBytes());
        when(permissionAssignmentService.importPermissionsFromYaml(eq(1L), eq(42L), any()))
                .thenThrow(new PermissionYamlFormatException("YAML syntax error"));

        mockMvc.perform(multipart("/api/rdbms-connections/{connectionId}/permissions/import", 42L)
                        .file(file)
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("PERMISSION_YAML_FORMAT_ERROR"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void importPermissionsReturnsForbiddenForNonAdmin() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "permissions.yaml", "application/x-yaml", "principals: []\n".getBytes());

        mockMvc.perform(multipart("/api/rdbms-connections/{connectionId}/permissions/import", 42L)
                        .file(file))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void importPermissionsReturnsUnauthorizedWhenNotAuthenticated() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "permissions.yaml", "application/x-yaml", "principals: []\n".getBytes());

        mockMvc.perform(multipart("/api/rdbms-connections/{connectionId}/permissions/import", 42L)
                        .file(file))
                .andExpect(status().isUnauthorized());
    }

}
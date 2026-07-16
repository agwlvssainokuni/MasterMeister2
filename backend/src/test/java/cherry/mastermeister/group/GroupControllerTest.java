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

package cherry.mastermeister.group;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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
 * example-basedテスト: グループCRUD・メンバー追加/削除を
 * {@code @WithMockUser(roles = "ADMIN")}での成功系、管理者以外403・未認証401で検証する。
 */
@WebMvcTest(GroupController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class GroupControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @MockitoBean
    private GroupService groupService;

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
    void createGroupReturnsCreatedForAdminAndUsesPrincipalAsAdminUserId() throws Exception {
        when(groupService.createGroup(eq(1L), eq("team-a"))).thenReturn(42L);

        mockMvc.perform(post("/api/groups")
                        .with(authentication(adminAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"team-a\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$").value(42));

        verify(groupService).createGroup(1L, "team-a");
    }

    @Test
    @WithMockUser(roles = "USER")
    void createGroupReturnsForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"team-a\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void createGroupReturnsUnauthorizedWhenNotAuthenticated() throws Exception {
        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"team-a\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void renameGroupReturnsNoContentForAdminAndUsesPrincipalAsAdminUserId() throws Exception {
        doNothing().when(groupService).renameGroup(eq(1L), eq(42L), eq("team-b"));

        mockMvc.perform(put("/api/groups/{id}", 42L)
                        .with(authentication(adminAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"team-b\"}"))
                .andExpect(status().isNoContent());

        verify(groupService).renameGroup(1L, 42L, "team-b");
    }

    @Test
    @WithMockUser(roles = "USER")
    void renameGroupReturnsForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(put("/api/groups/{id}", 42L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"team-b\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void renameGroupReturnsUnauthorizedWhenNotAuthenticated() throws Exception {
        mockMvc.perform(put("/api/groups/{id}", 42L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"team-b\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteGroupReturnsNoContentForAdminAndUsesPrincipalAsAdminUserId() throws Exception {
        doNothing().when(groupService).deleteGroup(eq(1L), eq(42L));

        mockMvc.perform(delete("/api/groups/{id}", 42L)
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isNoContent());

        verify(groupService).deleteGroup(1L, 42L);
    }

    @Test
    @WithMockUser(roles = "USER")
    void deleteGroupReturnsForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(delete("/api/groups/{id}", 42L)).andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void deleteGroupReturnsUnauthorizedWhenNotAuthenticated() throws Exception {
        mockMvc.perform(delete("/api/groups/{id}", 42L)).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listGroupsReturnsOkForAdmin() throws Exception {
        when(groupService.listGroups())
                .thenReturn(List.of(new GroupSummary(42L, "team-a", Instant.parse("2026-07-11T00:00:00Z"))));

        mockMvc.perform(get("/api/groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(42))
                .andExpect(jsonPath("$[0].name").value("team-a"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void listGroupsReturnsForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(get("/api/groups")).andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void listGroupsReturnsUnauthorizedWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/groups")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listGroupMembersReturnsOkForAdmin() throws Exception {
        when(groupService.listGroupMembers(42L))
                .thenReturn(List.of(new UserSummary(7L, "user@example.com")));

        mockMvc.perform(get("/api/groups/{id}/members", 42L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].email").value("user@example.com"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void listGroupMembersReturnsForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(get("/api/groups/{id}/members", 42L)).andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void listGroupMembersReturnsUnauthorizedWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/groups/{id}/members", 42L)).andExpect(status().isUnauthorized());
    }

    @Test
    void addUserToGroupReturnsNoContentForAdminAndUsesPrincipalAsAdminUserId() throws Exception {
        doNothing().when(groupService).addUserToGroup(eq(1L), eq(42L), eq(7L));

        mockMvc.perform(post("/api/groups/{id}/members", 42L)
                        .with(authentication(adminAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":7}"))
                .andExpect(status().isNoContent());

        verify(groupService).addUserToGroup(1L, 42L, 7L);
    }

    @Test
    @WithMockUser(roles = "USER")
    void addUserToGroupReturnsForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(post("/api/groups/{id}/members", 42L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":7}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void addUserToGroupReturnsUnauthorizedWhenNotAuthenticated() throws Exception {
        mockMvc.perform(post("/api/groups/{id}/members", 42L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":7}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void removeUserFromGroupReturnsNoContentForAdminAndUsesPrincipalAsAdminUserId() throws Exception {
        doNothing().when(groupService).removeUserFromGroup(eq(1L), eq(42L), eq(7L));

        mockMvc.perform(delete("/api/groups/{id}/members/{userId}", 42L, 7L)
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isNoContent());

        verify(groupService).removeUserFromGroup(1L, 42L, 7L);
    }

    @Test
    @WithMockUser(roles = "USER")
    void removeUserFromGroupReturnsForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(delete("/api/groups/{id}/members/{userId}", 42L, 7L)).andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void removeUserFromGroupReturnsUnauthorizedWhenNotAuthenticated() throws Exception {
        mockMvc.perform(delete("/api/groups/{id}/members/{userId}", 42L, 7L)).andExpect(status().isUnauthorized());
    }

}
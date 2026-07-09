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

package cherry.mastermeister.userregistration;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * example-basedテスト: 登録申請・登録完了（permitAll、未認証で204）、承認待ち一覧・承認・却下
 * （管理者以外403、未認証401、管理者による成功系）を検証する。P1〜P6（業務ロジックの性質）は
 * {@code UserRegistrationServiceTest}/{@code RegistrationTokenServiceTest}
 * （jqwikによるproperty-basedテスト）で別途検証する。
 */
@WebMvcTest(RegistrationController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class RegistrationControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @MockitoBean
    private UserRegistrationService userRegistrationService;

    @MockitoBean
    private JwtTokenValidator jwtTokenValidator;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void requestRegistrationReturnsNoContentWithoutAuthentication() throws Exception {
        doNothing().when(userRegistrationService).requestRegistration(eq("user@example.com"));

        mockMvc.perform(post("/api/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.com"}
                                """))
                .andExpect(status().isNoContent());

        verify(userRegistrationService).requestRegistration("user@example.com");
    }

    @Test
    void completeRegistrationReturnsNoContentWithoutAuthentication() throws Exception {
        doNothing().when(userRegistrationService).completeRegistration(eq("token"), eq("password"));

        mockMvc.perform(post("/api/registrations/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"token","password":"password"}
                                """))
                .andExpect(status().isNoContent());

        verify(userRegistrationService).completeRegistration("token", "password");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listPendingUsersReturnsOkForAdmin() throws Exception {
        when(userRegistrationService.listPendingUsers())
                .thenReturn(List.of(new PendingUserSummary(1L, "pending@example.com", Instant.parse("2026-01-01T00:00:00Z"))));

        mockMvc.perform(get("/api/registrations/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("pending@example.com"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void listPendingUsersReturnsForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(get("/api/registrations/pending")).andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void listPendingUsersReturnsUnauthorizedWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/registrations/pending")).andExpect(status().isUnauthorized());
    }

    @Test
    void approveUserReturnsNoContentForAdminAndUsesPrincipalAsAdminUserId() throws Exception {
        Authentication adminAuthentication = new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        doNothing().when(userRegistrationService).approveUser(eq(1L), eq(42L));

        mockMvc.perform(post("/api/registrations/{userId}/approve", 42L)
                        .with(authentication(adminAuthentication)))
                .andExpect(status().isNoContent());

        verify(userRegistrationService).approveUser(1L, 42L);
    }

    @Test
    @WithMockUser(roles = "USER")
    void approveUserReturnsForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(post("/api/registrations/{userId}/approve", 42L))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectUserReturnsNoContentForAdminAndUsesPrincipalAsAdminUserId() throws Exception {
        Authentication adminAuthentication = new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        doNothing().when(userRegistrationService).rejectUser(eq(1L), eq(42L));

        mockMvc.perform(post("/api/registrations/{userId}/reject", 42L)
                        .with(authentication(adminAuthentication)))
                .andExpect(status().isNoContent());

        verify(userRegistrationService).rejectUser(1L, 42L);
    }

    @Test
    @WithAnonymousUser
    void rejectUserReturnsUnauthorizedWhenNotAuthenticated() throws Exception {
        mockMvc.perform(post("/api/registrations/{userId}/reject", 42L))
                .andExpect(status().isUnauthorized());
    }

}
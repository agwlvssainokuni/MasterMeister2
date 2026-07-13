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

package cherry.mastermeister.savedquery;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

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
 * example-basedテスト: 5エンドポイント（一覧・保存・取得・更新・廃止）それぞれについて
 * 認証済みユーザ成功系・未認証401を検証する。本ユニットは管理者ロール制約を持たないため
 * （{@code business-rules.md} 8節「認証済みユーザ全員」）403系テストは不要。P1〜P3は
 * {@code SavedQueryServiceTest}（jqwikによるproperty-basedテスト）で別途検証する。
 */
@WebMvcTest(SavedQueryController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class SavedQueryControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @MockitoBean
    private SavedQueryService savedQueryService;

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
    void listQueriesReturnsOkForAuthenticatedUser() throws Exception {
        when(savedQueryService.listQueries(1L, 42L, false)).thenReturn(List.of(
                new SavedQuerySummary(10L, "q1", Visibility.PUBLIC, false, 1L)));

        mockMvc.perform(get("/api/saved-queries")
                        .param("connectionId", "42")
                        .with(authentication(userAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("q1"));
    }

    @Test
    @WithAnonymousUser
    void listQueriesReturnsUnauthorizedWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/saved-queries").param("connectionId", "42"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void saveQueryReturnsCreatedForAuthenticatedUser() throws Exception {
        when(savedQueryService.saveQuery(eq(1L), eq(42L), eq("q1"), eq("SELECT 1"), eq(Visibility.PRIVATE)))
                .thenReturn(10L);

        mockMvc.perform(post("/api/saved-queries")
                        .with(authentication(userAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"connectionId":42,"name":"q1","sql":"SELECT 1","visibility":"PRIVATE"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$").value(10));
    }

    @Test
    @WithAnonymousUser
    void saveQueryReturnsUnauthorizedWhenNotAuthenticated() throws Exception {
        mockMvc.perform(post("/api/saved-queries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"connectionId":42,"name":"q1","sql":"SELECT 1","visibility":"PRIVATE"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getQueryReturnsOkForAuthenticatedUser() throws Exception {
        Instant now = Instant.now();
        when(savedQueryService.getQuery(1L, 10L)).thenReturn(new SavedQueryDetail(
                10L, 1L, 42L, "q1", "SELECT 1", Visibility.PRIVATE, false, 0, now, now));

        mockMvc.perform(get("/api/saved-queries/{savedQueryId}", 10L)
                        .with(authentication(userAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("q1"));
    }

    @Test
    @WithAnonymousUser
    void getQueryReturnsUnauthorizedWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/saved-queries/{savedQueryId}", 10L))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateQueryReturnsNoContentForAuthenticatedUser() throws Exception {
        mockMvc.perform(put("/api/saved-queries/{savedQueryId}", 10L)
                        .with(authentication(userAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"q2","sql":"SELECT 2","visibility":"PUBLIC"}
                                """))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithAnonymousUser
    void updateQueryReturnsUnauthorizedWhenNotAuthenticated() throws Exception {
        mockMvc.perform(put("/api/saved-queries/{savedQueryId}", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"q2","sql":"SELECT 2","visibility":"PUBLIC"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void retireQueryReturnsNoContentForAuthenticatedUser() throws Exception {
        mockMvc.perform(post("/api/saved-queries/{savedQueryId}/retire", 10L)
                        .with(authentication(userAuthentication())))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithAnonymousUser
    void retireQueryReturnsUnauthorizedWhenNotAuthenticated() throws Exception {
        mockMvc.perform(post("/api/saved-queries/{savedQueryId}/retire", 10L))
                .andExpect(status().isUnauthorized());
    }

}
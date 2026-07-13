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

package cherry.mastermeister.queryhistory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import cherry.mastermeister.common.PageRequest;
import cherry.mastermeister.common.PageResult;
import cherry.mastermeister.security.JwtTokenValidator;
import cherry.mastermeister.security.RestAccessDeniedHandler;
import cherry.mastermeister.security.RestAuthenticationEntryPoint;
import cherry.mastermeister.security.SecurityConfig;

/**
 * example-basedテスト: 1エンドポイント（実行履歴一覧）について認証済みユーザ成功系・未認証401を
 * 検証する。P9・P10は{@code QueryHistoryServiceTest}（jqwikによるproperty-basedテスト）で
 * 別途検証する。
 */
@WebMvcTest(QueryHistoryController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class QueryHistoryControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @MockitoBean
    private QueryHistoryService queryHistoryService;

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
    void listHistoryReturnsOkForAuthenticatedUser() throws Exception {
        Instant now = Instant.now();
        HistoryEntry entry = new HistoryEntry(
                100L, 1L, 42L, "SELECT 1", java.util.Map.of(), 1, 10L, now, null, null, null, false, false);
        when(queryHistoryService.listHistory(eq(1L), eq(42L), any(HistoryFilterCriteria.class), any(PageRequest.class)))
                .thenReturn(new PageResult<>(List.of(entry), 1, 0, 50));

        mockMvc.perform(get("/api/query-history")
                        .param("connectionId", "42")
                        .with(authentication(userAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sql").value("SELECT 1"));
    }

    @Test
    @WithAnonymousUser
    void listHistoryReturnsUnauthorizedWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/query-history").param("connectionId", "42"))
                .andExpect(status().isUnauthorized());
    }

}
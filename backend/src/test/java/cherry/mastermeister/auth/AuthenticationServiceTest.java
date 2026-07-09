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

package cherry.mastermeister.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;

import cherry.mastermeister.audit.AuditLogService;
import cherry.mastermeister.audit.EventCategory;
import cherry.mastermeister.audit.EventType;
import cherry.mastermeister.audit.Result;
import cherry.mastermeister.security.JwtTokenProvider;
import cherry.mastermeister.security.OpaqueTokenGenerator;
import cherry.mastermeister.userregistration.Role;
import cherry.mastermeister.userregistration.User;
import cherry.mastermeister.userregistration.UserRepository;
import cherry.mastermeister.userregistration.UserStatus;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.spring.JqwikSpringSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * P7〜P8（business-logic-model.md）を検証するプロパティテスト。
 * {@code UserRepository}はStep 8で生成されるため、本テストはStep 8完了後にのみ
 * コンパイル・実行可能（グリーン確認はBuild and Testステージで行う）。
 * userIdの自動採番を検証するP8のため、{@code UserRepository}のみ実DBを用いる。
 */
@JqwikSpringSupport
@DataJpaTest
class AuthenticationServiceTest {

    @Autowired
    private UserRepository userRepository;

    // P7: ログイン成否は status = APPROVED かつ パスワード一致の場合に限り真となる決定的関数である。
    @Property
    void loginSucceedsIfAndOnlyIfApprovedAndPasswordMatches(
            @ForAll("emails") String email,
            @ForAll("userStatuses") UserStatus status,
            @ForAll boolean passwordMatches
    ) {
        userRepository.deleteAll();
        userRepository.save(new User(email, "encoded-hash", Role.USER, status, Instant.now(), null));

        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.matches(anyString(), eq("encoded-hash"))).thenReturn(passwordMatches);
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
        when(jwtTokenProvider.generateToken(any(), any(), any())).thenReturn("access-token");
        RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
        when(refreshTokenService.issue(any())).thenReturn("refresh-token");
        RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
        OpaqueTokenGenerator opaqueTokenGenerator = mock(OpaqueTokenGenerator.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        AuthenticationService service = new AuthenticationService(
                userRepository, passwordEncoder, jwtTokenProvider, refreshTokenService,
                refreshTokenRepository, opaqueTokenGenerator, auditLogService, Duration.ofMinutes(10));

        boolean expectedSuccess = status == UserStatus.APPROVED && passwordMatches;
        if (expectedSuccess) {
            AuthToken token = service.login(email, "raw-password");
            assertThat(token.accessToken()).isEqualTo("access-token");
            assertThat(token.refreshToken()).isEqualTo("refresh-token");
        } else {
            assertThatThrownBy(() -> service.login(email, "raw-password"))
                    .isInstanceOf(AuthenticationFailedException.class);
        }
    }

    // P8: ログイン失敗時は必ずAuditLogService.record(LOGIN_FAILURE, ...)が呼ばれる。
    // 存在しないメールではuserId = null、存在するユーザのパスワード不一致ではuserIdが設定される。
    @Property
    void loginFailureAlwaysRecordsAuditLogWithCorrectUserId(
            @ForAll("emails") String email,
            @ForAll boolean userExists
    ) {
        userRepository.deleteAll();
        Long expectedUserId = null;
        if (userExists) {
            User saved = userRepository.save(
                    new User(email, "encoded-hash", Role.USER, UserStatus.APPROVED, Instant.now(), null));
            expectedUserId = saved.getId();
        }

        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.matches(any(), any())).thenReturn(false);
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
        RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
        RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
        OpaqueTokenGenerator opaqueTokenGenerator = mock(OpaqueTokenGenerator.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        AuthenticationService service = new AuthenticationService(
                userRepository, passwordEncoder, jwtTokenProvider, refreshTokenService,
                refreshTokenRepository, opaqueTokenGenerator, auditLogService, Duration.ofMinutes(10));

        assertThatThrownBy(() -> service.login(email, "raw-password"))
                .isInstanceOf(AuthenticationFailedException.class);

        verify(auditLogService).record(
                eq(EventCategory.AUTHENTICATION), eq(EventType.LOGIN_FAILURE), eq(expectedUserId),
                isNull(), eq(Result.FAILURE), eq(email), anyString());
    }

    @Provide
    Arbitrary<String> emails() {
        return Arbitraries.strings().withCharRange('a', 'z').ofMinLength(3).ofMaxLength(10)
                .map(local -> local + "@example.com");
    }

    @Provide
    Arbitrary<UserStatus> userStatuses() {
        return Arbitraries.of(UserStatus.class);
    }

}
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import cherry.mastermeister.audit.AuditLogService;
import cherry.mastermeister.mail.MailNotificationType;
import cherry.mastermeister.mail.MailService;
import cherry.mastermeister.security.OpaqueTokenGenerator;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * P1, P3〜P6（business-logic-model.md）を検証するプロパティテスト。
 * 依存先はすべてMockitoでモック化し、{@code UserRegistrationService}単体の振る舞いを検証する。
 */
class UserRegistrationServiceTest {

    private static final PasswordEncoder PASSWORD_ENCODER = new PasswordEncoder() {
        @Override
        public String encode(CharSequence rawPassword) {
            return "hash:" + rawPassword;
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            return encodedPassword.equals("hash:" + rawPassword);
        }
    };

    private enum ExistingState {
        NEW, PENDING_APPROVAL, APPROVED, REJECTED, VALID_TOKEN_RESEND;

        UserStatus status() {
            return switch (this) {
                case PENDING_APPROVAL -> UserStatus.PENDING_APPROVAL;
                case APPROVED -> UserStatus.APPROVED;
                case REJECTED -> UserStatus.REJECTED;
                default -> throw new IllegalStateException("no status for " + this);
            };
        }
    }

    private enum TokenOutcome {
        VALID, EXPIRED, NOT_FOUND
    }

    // P1: 任意の内部状態（新規/PENDING_APPROVAL/APPROVED/REJECTED/有効トークン再送信）に対し、
    // requestRegistrationは常に例外を投げず同一の成功結果となる（列挙攻撃対策）。
    @Property
    void requestRegistrationAlwaysSucceedsRegardlessOfExistingState(
            @ForAll("emails") String email,
            @ForAll("existingStates") ExistingState state
    ) {
        UserRepository userRepository = mock(UserRepository.class);
        RegistrationTokenRepository registrationTokenRepository = mock(RegistrationTokenRepository.class);
        RegistrationTokenService registrationTokenService =
                new RegistrationTokenService(registrationTokenRepository, new OpaqueTokenGenerator());
        MailService mailService = mock(MailService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        switch (state) {
            case NEW -> {
                when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
                when(registrationTokenRepository.findByEmailAndConsumedAtIsNullAndInvalidatedAtIsNull(email))
                        .thenReturn(Optional.empty());
            }
            case PENDING_APPROVAL, APPROVED, REJECTED -> when(userRepository.findByEmail(email))
                    .thenReturn(Optional.of(new User(
                            email, "hash", Role.USER, state.status(), Instant.now(), null)));
            case VALID_TOKEN_RESEND -> {
                when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
                when(registrationTokenRepository.findByEmailAndConsumedAtIsNullAndInvalidatedAtIsNull(email))
                        .thenReturn(Optional.of(new RegistrationToken(
                                email, "existing-hash", Instant.now().plusSeconds(3600),
                                null, null, Instant.now())));
            }
        }

        UserRegistrationService service = new UserRegistrationService(
                userRepository, registrationTokenRepository, registrationTokenService,
                PASSWORD_ENCODER, mailService, auditLogService,
                Duration.ofHours(3), "http://localhost:5173");

        assertThatCode(() -> service.requestRegistration(email)).doesNotThrowAnyException();
    }

    // P6: status = REJECTEDの既存ユーザに対しては、何度呼び出しても新規トークンが発行されない。
    @Property
    void requestRegistrationNeverIssuesTokenForRejectedUser(@ForAll("emails") String email) {
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(new User(
                email, "hash", Role.USER, UserStatus.REJECTED, Instant.now(), Instant.now())));
        RegistrationTokenRepository registrationTokenRepository = mock(RegistrationTokenRepository.class);
        RegistrationTokenService registrationTokenService =
                new RegistrationTokenService(registrationTokenRepository, new OpaqueTokenGenerator());
        MailService mailService = mock(MailService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        UserRegistrationService service = new UserRegistrationService(
                userRepository, registrationTokenRepository, registrationTokenService,
                PASSWORD_ENCODER, mailService, auditLogService,
                Duration.ofHours(3), "http://localhost:5173");

        service.requestRegistration(email);
        service.requestRegistration(email);

        verifyNoInteractions(mailService);
        verify(registrationTokenRepository, never()).save(any());
    }

    // P3: 同一メールアドレスへの再送信実行後、旧トークンは必ずEXPIRED、新トークンは必ずVALIDとなる。
    @Property
    void requestRegistrationResendInvalidatesOldTokenAndIssuesNewValidToken(@ForAll("emails") String email) {
        OpaqueTokenGenerator opaqueTokenGenerator = new OpaqueTokenGenerator();
        String oldPlainToken = "old-token-" + email;
        String oldHash = opaqueTokenGenerator.hash(oldPlainToken);
        RegistrationToken oldToken = new RegistrationToken(
                email, oldHash, Instant.now().plusSeconds(3600), null, null, Instant.now());

        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        RegistrationTokenRepository registrationTokenRepository = mock(RegistrationTokenRepository.class);
        when(registrationTokenRepository.findByEmailAndConsumedAtIsNullAndInvalidatedAtIsNull(email))
                .thenReturn(Optional.of(oldToken));

        AtomicReference<RegistrationToken> savedToken = new AtomicReference<>();
        when(registrationTokenRepository.save(any(RegistrationToken.class))).thenAnswer(invocation -> {
            RegistrationToken token = invocation.getArgument(0);
            savedToken.set(token);
            return token;
        });
        when(registrationTokenRepository.findByTokenHash(anyString())).thenAnswer(invocation -> {
            String hash = invocation.getArgument(0);
            if (hash.equals(oldHash)) {
                return Optional.of(oldToken);
            }
            RegistrationToken saved = savedToken.get();
            return saved != null && saved.getTokenHash().equals(hash) ? Optional.of(saved) : Optional.empty();
        });

        RegistrationTokenService registrationTokenService =
                new RegistrationTokenService(registrationTokenRepository, opaqueTokenGenerator);
        MailService mailService = mock(MailService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        UserRegistrationService service = new UserRegistrationService(
                userRepository, registrationTokenRepository, registrationTokenService,
                PASSWORD_ENCODER, mailService, auditLogService,
                Duration.ofHours(3), "http://localhost:5173");

        service.requestRegistration(email);

        assertThat(registrationTokenService.validate(oldPlainToken)).isEqualTo(RegistrationTokenStatus.EXPIRED);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mailService).send(
                eq(MailNotificationType.REGISTRATION_CONFIRMATION), eq(email), variablesCaptor.capture());
        String linkUrl = (String) variablesCaptor.getValue().get("linkUrl");
        String newPlainToken = linkUrl.substring(linkUrl.indexOf("token=") + "token=".length());

        assertThat(registrationTokenService.validate(newPlainToken)).isEqualTo(RegistrationTokenStatus.VALID);
    }

    // P4: VALIDなトークンでのみUserが1件作成される。EXPIRED/NOT_FOUNDでは作成されず例外のみとなる。
    @Property
    void completeRegistrationCreatesUserOnlyWhenTokenIsValid(
            @ForAll("emails") String email,
            @ForAll("rawPasswords") String rawPassword,
            @ForAll("tokenOutcomes") TokenOutcome outcome
    ) {
        UserRepository userRepository = mock(UserRepository.class);
        RegistrationTokenRepository registrationTokenRepository = mock(RegistrationTokenRepository.class);
        RegistrationTokenService registrationTokenService = mock(RegistrationTokenService.class);
        MailService mailService = mock(MailService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        String token = "token-" + email;

        switch (outcome) {
            case VALID -> when(registrationTokenService.resolveValid(token)).thenReturn(new RegistrationToken(
                    email, "hash", Instant.now().plusSeconds(3600), null, null, Instant.now()));
            case EXPIRED -> when(registrationTokenService.resolveValid(token))
                    .thenThrow(new TokenExpiredException("expired"));
            case NOT_FOUND -> when(registrationTokenService.resolveValid(token))
                    .thenThrow(new TokenNotFoundException("not found"));
        }

        UserRegistrationService service = new UserRegistrationService(
                userRepository, registrationTokenRepository, registrationTokenService,
                PASSWORD_ENCODER, mailService, auditLogService,
                Duration.ofHours(3), "http://localhost:5173");

        if (outcome == TokenOutcome.VALID) {
            service.completeRegistration(token, rawPassword);
            verify(userRepository).save(argThat(user ->
                    user.getEmail().equals(email) && user.getStatus() == UserStatus.PENDING_APPROVAL));
        } else {
            Class<? extends RuntimeException> expected =
                    outcome == TokenOutcome.EXPIRED ? TokenExpiredException.class : TokenNotFoundException.class;
            assertThatThrownBy(() -> service.completeRegistration(token, rawPassword)).isInstanceOf(expected);
            verify(userRepository, never()).save(any());
        }
    }

    // P5: status = PENDING_APPROVALの場合のみ承認/却下が可能で、必ずAPPROVED/REJECTEDへ遷移する。
    // 既に終端状態のUserへの再実行は必ずInvalidUserStateExceptionとなる。
    @Property
    void approveAndRejectOnlyTransitionFromPendingApprovalAndAreBlockedOtherwise(
            @ForAll("userStatuses") UserStatus initialStatus,
            @ForAll boolean approve
    ) {
        User user = new User("user@example.com", "hash", Role.USER, initialStatus, Instant.now(), null);
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        RegistrationTokenRepository registrationTokenRepository = mock(RegistrationTokenRepository.class);
        RegistrationTokenService registrationTokenService = mock(RegistrationTokenService.class);
        MailService mailService = mock(MailService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        UserRegistrationService service = new UserRegistrationService(
                userRepository, registrationTokenRepository, registrationTokenService,
                PASSWORD_ENCODER, mailService, auditLogService,
                Duration.ofHours(3), "http://localhost:5173");

        if (initialStatus == UserStatus.PENDING_APPROVAL) {
            if (approve) {
                service.approveUser(99L, 1L);
                assertThat(user.getStatus()).isEqualTo(UserStatus.APPROVED);
            } else {
                service.rejectUser(99L, 1L);
                assertThat(user.getStatus()).isEqualTo(UserStatus.REJECTED);
            }
            assertThatThrownBy(() -> {
                if (approve) {
                    service.approveUser(99L, 1L);
                } else {
                    service.rejectUser(99L, 1L);
                }
            }).isInstanceOf(InvalidUserStateException.class);
        } else {
            assertThatThrownBy(() -> {
                if (approve) {
                    service.approveUser(99L, 1L);
                } else {
                    service.rejectUser(99L, 1L);
                }
            }).isInstanceOf(InvalidUserStateException.class);
            assertThat(user.getStatus()).isEqualTo(initialStatus);
        }
    }

    @Provide
    Arbitrary<String> emails() {
        return Arbitraries.strings().withCharRange('a', 'z').ofMinLength(3).ofMaxLength(10)
                .map(local -> local + "@example.com");
    }

    @Provide
    Arbitrary<String> rawPasswords() {
        return Arbitraries.strings().withCharRange('a', 'z').ofMinLength(8).ofMaxLength(20);
    }

    @Provide
    Arbitrary<ExistingState> existingStates() {
        return Arbitraries.of(ExistingState.class);
    }

    @Provide
    Arbitrary<TokenOutcome> tokenOutcomes() {
        return Arbitraries.of(TokenOutcome.class);
    }

    @Provide
    Arbitrary<UserStatus> userStatuses() {
        return Arbitraries.of(UserStatus.class);
    }

}
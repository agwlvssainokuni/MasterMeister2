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

import java.time.Duration;

import cherry.mastermeister.audit.AuditLogService;
import cherry.mastermeister.audit.EventCategory;
import cherry.mastermeister.audit.EventType;
import cherry.mastermeister.audit.Result;
import cherry.mastermeister.security.JwtTokenProvider;
import cherry.mastermeister.security.OpaqueTokenGenerator;
import cherry.mastermeister.userregistration.User;
import cherry.mastermeister.userregistration.UserRepository;
import cherry.mastermeister.userregistration.UserStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OpaqueTokenGenerator opaqueTokenGenerator;
    private final AuditLogService auditLogService;
    private final Duration accessTokenExpiry;

    public AuthenticationService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            RefreshTokenService refreshTokenService,
            RefreshTokenRepository refreshTokenRepository,
            OpaqueTokenGenerator opaqueTokenGenerator,
            AuditLogService auditLogService,
            @Value("${mm.app.jwt.access-token-expiry}") Duration accessTokenExpiry
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenService = refreshTokenService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.opaqueTokenGenerator = opaqueTokenGenerator;
        this.auditLogService = auditLogService;
        this.accessTokenExpiry = accessTokenExpiry;
    }

    @Transactional
    public AuthToken login(String email, String rawPassword) {
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            auditLogService.record(
                    EventCategory.AUTHENTICATION, EventType.LOGIN_FAILURE, null, null,
                    Result.FAILURE, email, "Login failed: user not found");
            throw new AuthenticationFailedException("Invalid email or password");
        }

        if (user.getStatus() != UserStatus.APPROVED || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            auditLogService.record(
                    EventCategory.AUTHENTICATION, EventType.LOGIN_FAILURE, user.getId(), null,
                    Result.FAILURE, email, "Login failed: status or password mismatch");
            throw new AuthenticationFailedException("Invalid email or password");
        }

        String accessToken = jwtTokenProvider.generateToken(user.getId(), user.getRole(), accessTokenExpiry);
        String refreshToken = refreshTokenService.issue(user.getId());
        auditLogService.record(
                EventCategory.AUTHENTICATION, EventType.LOGIN_SUCCESS, user.getId(), null,
                Result.SUCCESS, email, "Login succeeded");
        return new AuthToken(accessToken, refreshToken);
    }

    @Transactional
    public AuthToken refresh(String rawRefreshToken) {
        RotationResult rotationResult = refreshTokenService.rotate(rawRefreshToken);
        User user = userRepository.findById(rotationResult.userId())
                .orElseThrow(() -> new InvalidTokenException("User not found for refresh token"));
        String accessToken = jwtTokenProvider.generateToken(user.getId(), user.getRole(), accessTokenExpiry);
        return new AuthToken(accessToken, rotationResult.newPlainToken());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        Long userId = refreshTokenRepository.findByTokenHash(opaqueTokenGenerator.hash(rawRefreshToken))
                .map(RefreshToken::getUserId)
                .orElse(null);
        refreshTokenService.revoke(rawRefreshToken);
        auditLogService.record(
                EventCategory.AUTHENTICATION, EventType.LOGOUT, userId, null,
                Result.SUCCESS, null, "Logout succeeded");
    }

}
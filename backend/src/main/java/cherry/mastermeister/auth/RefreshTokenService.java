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
import java.time.Instant;
import java.util.UUID;

import cherry.mastermeister.security.OpaqueTokenGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final OpaqueTokenGenerator opaqueTokenGenerator;
    private final Duration refreshTokenExpiry;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            OpaqueTokenGenerator opaqueTokenGenerator,
            @Value("${mm.app.jwt.refresh-token-expiry:24h}") Duration refreshTokenExpiry
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.opaqueTokenGenerator = opaqueTokenGenerator;
        this.refreshTokenExpiry = refreshTokenExpiry;
    }

    @Transactional
    public String issue(Long userId) {
        return issueWithFamily(userId, UUID.randomUUID().toString());
    }

    @Transactional
    public RotationResult rotate(String rawToken) {
        String tokenHash = opaqueTokenGenerator.hash(rawToken);
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidTokenException("Refresh token not found"));

        if (refreshToken.getRevokedAt() != null || refreshToken.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidTokenException("Refresh token is revoked or expired");
        }

        if (refreshToken.getRotatedAt() != null) {
            Instant now = Instant.now();
            for (RefreshToken sibling : refreshTokenRepository.findByFamilyId(refreshToken.getFamilyId())) {
                if (sibling.getRevokedAt() == null) {
                    sibling.revoke(now);
                }
            }
            throw new InvalidTokenException("Refresh token reuse detected");
        }

        refreshToken.rotate(Instant.now());
        String newPlainToken = issueWithFamily(refreshToken.getUserId(), refreshToken.getFamilyId());
        return new RotationResult(refreshToken.getUserId(), newPlainToken);
    }

    @Transactional
    public void revoke(String rawToken) {
        String tokenHash = opaqueTokenGenerator.hash(rawToken);
        refreshTokenRepository.findByTokenHash(tokenHash)
                .ifPresent(refreshToken -> refreshToken.revoke(Instant.now()));
    }

    private String issueWithFamily(Long userId, String familyId) {
        String plainToken = opaqueTokenGenerator.generate();
        String tokenHash = opaqueTokenGenerator.hash(plainToken);
        Instant now = Instant.now();
        RefreshToken refreshToken = new RefreshToken(
                userId,
                familyId,
                tokenHash,
                now.plus(refreshTokenExpiry),
                null,
                null,
                now
        );
        refreshTokenRepository.save(refreshToken);
        return plainToken;
    }

}
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

package cherry.mastermeister.security;

import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * アクセストークンのHS256署名・有効期限検証のみを行う（発行はU2の{@code JwtTokenProvider}）。
 */
@Component
public class JwtTokenValidator {

    private static final String ROLE_CLAIM = "role";

    private final SecretKey secretKey;

    public JwtTokenValidator(@Value("${mm.app.jwt.secret}") String secret) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public JwtClaims validate(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Long userId = Long.valueOf(claims.getSubject());
            String role = claims.get(ROLE_CLAIM, String.class);
            return new JwtClaims(userId, role);
        } catch (JwtException | IllegalArgumentException e) {
            throw new JwtValidationException("Invalid JWT token", e);
        }
    }

}
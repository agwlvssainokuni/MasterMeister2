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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import cherry.mastermeister.auth.InvalidTokenException;
import cherry.mastermeister.userregistration.Role;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * P13（business-logic-model.md、Code Generation計画で新規識別）を検証するプロパティテスト。
 */
class JwtTokenProviderTest {

    private final JwtTokenProvider provider =
            new JwtTokenProvider("test-secret-key-at-least-32-bytes-long-for-hs256-signing");

    // P13: parseAndValidate(generateToken(userId, role, expiry))は元のuserId・roleと一致するJwtClaimsを返す。
    @Property
    void roundTripsUserIdAndRole(@ForAll("userIds") long userId, @ForAll("roles") Role role) {
        String token = provider.generateToken(userId, role, Duration.ofMinutes(10));
        JwtClaims claims = provider.parseAndValidate(token);

        assertThat(claims.userId()).isEqualTo(userId);
        assertThat(claims.role()).isEqualTo(role.name());
    }

    // P13: 期限切れ（負のDuration）で生成したトークンはparseAndValidateで必ずInvalidTokenExceptionとなる。
    @Property
    void expiredTokenAlwaysFailsValidation(@ForAll("userIds") long userId, @ForAll("roles") Role role) {
        String token = provider.generateToken(userId, role, Duration.ofMinutes(-10));

        assertThatThrownBy(() -> provider.parseAndValidate(token)).isInstanceOf(InvalidTokenException.class);
    }

    @Provide
    Arbitrary<Long> userIds() {
        return Arbitraries.longs().between(1, 1_000_000);
    }

    @Provide
    Arbitrary<Role> roles() {
        return Arbitraries.of(Role.class);
    }

}
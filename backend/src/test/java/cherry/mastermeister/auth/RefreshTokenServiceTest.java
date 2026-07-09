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

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import cherry.mastermeister.security.OpaqueTokenGenerator;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.spring.JqwikSpringSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

/**
 * P9〜P11（business-logic-model.md）を検証するプロパティテスト。
 * {@code RefreshTokenRepository}はStep 8で生成されるため、本テストはStep 8完了後にのみ
 * コンパイル・実行可能（グリーン確認はBuild and Testステージで行う）。
 */
@JqwikSpringSupport
@DataJpaTest
class RefreshTokenServiceTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    // P9（reuse detection）+ P10（ローテーションのRound-trip）:
    // 有効な未消費トークンのローテーションは1回だけ成功し新トークンは同一familyIdで発行される。
    // ローテーション済みの元トークンを再度リフレッシュに使うと必ずreuse detectionが発動し、
    // 同一familyIdの全行のrevokedAtが設定された上でInvalidTokenExceptionとなる。
    @Property
    void rotationRoundTripsOnceThenDetectsReuseAndRevokesFamily(@ForAll("userIds") long userId) {
        refreshTokenRepository.deleteAll();
        OpaqueTokenGenerator opaqueTokenGenerator = new OpaqueTokenGenerator();
        RefreshTokenService service =
                new RefreshTokenService(refreshTokenRepository, opaqueTokenGenerator, Duration.ofHours(24));

        String original = service.issue(userId);

        RotationResult firstRotation = service.rotate(original);
        assertThat(firstRotation.userId()).isEqualTo(userId);
        assertThat(firstRotation.newPlainToken()).isNotEqualTo(original);

        RefreshToken originalRow = refreshTokenRepository
                .findByTokenHash(opaqueTokenGenerator.hash(original)).orElseThrow();
        assertThat(originalRow.getRotatedAt()).isNotNull();

        RefreshToken newRow = refreshTokenRepository
                .findByTokenHash(opaqueTokenGenerator.hash(firstRotation.newPlainToken())).orElseThrow();
        assertThat(newRow.getFamilyId()).isEqualTo(originalRow.getFamilyId());
        assertThat(newRow.getRevokedAt()).isNull();

        assertThatThrownBy(() -> service.rotate(original)).isInstanceOf(InvalidTokenException.class);

        List<RefreshToken> family = refreshTokenRepository.findByFamilyId(originalRow.getFamilyId());
        assertThat(family).allSatisfy(row -> assertThat(row.getRevokedAt()).isNotNull());
    }

    // P11: ログアウト（revoke）は指定したリフレッシュトークンの行のみを失効させ、
    // 同一familyIdの他の行（他デバイスのセッション等）のrevokedAtは変化しない。
    @Property
    void revokeAffectsOnlyTargetTokenNotSiblingsInSameFamily(@ForAll("userIds") long userId) {
        refreshTokenRepository.deleteAll();
        OpaqueTokenGenerator opaqueTokenGenerator = new OpaqueTokenGenerator();
        RefreshTokenService service =
                new RefreshTokenService(refreshTokenRepository, opaqueTokenGenerator, Duration.ofHours(24));

        String tokenA = service.issue(userId);
        RefreshToken rowA = refreshTokenRepository
                .findByTokenHash(opaqueTokenGenerator.hash(tokenA)).orElseThrow();

        String siblingHash = "sibling-hash-" + userId;
        RefreshToken sibling = new RefreshToken(
                userId, rowA.getFamilyId(), siblingHash, Instant.now().plusSeconds(3600),
                null, null, Instant.now());
        refreshTokenRepository.save(sibling);

        service.revoke(tokenA);

        RefreshToken reloadedA = refreshTokenRepository
                .findByTokenHash(opaqueTokenGenerator.hash(tokenA)).orElseThrow();
        RefreshToken reloadedSibling = refreshTokenRepository.findByTokenHash(siblingHash).orElseThrow();

        assertThat(reloadedA.getRevokedAt()).isNotNull();
        assertThat(reloadedSibling.getRevokedAt()).isNull();
    }

    @Provide
    Arbitrary<Long> userIds() {
        return Arbitraries.longs().between(1, 1_000_000);
    }

}
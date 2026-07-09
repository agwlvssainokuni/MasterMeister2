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

import java.time.Duration;
import java.time.Instant;

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
 * P2（business-logic-model.md）を検証するプロパティテスト。
 * {@code RegistrationTokenRepository}はStep 8で生成されるため、本テストはStep 8完了後にのみ
 * コンパイル・実行可能（グリーン確認はBuild and Testステージで行う）。
 */
@JqwikSpringSupport
@DataJpaTest
class RegistrationTokenServiceTest {

    @Autowired
    private RegistrationTokenRepository registrationTokenRepository;

    // P2: 発行直後のトークンはvalidate=VALID。consumedAt/invalidatedAtのいずれかを設定した後は必ずEXPIRED。
    @Property
    void issuedTokenIsValidThenExpiredAfterConsumptionOrInvalidation(
            @ForAll("emails") String email,
            @ForAll("expiries") Duration expiry,
            @ForAll boolean consumeInsteadOfInvalidate
    ) {
        registrationTokenRepository.deleteAll();
        OpaqueTokenGenerator opaqueTokenGenerator = new OpaqueTokenGenerator();
        RegistrationTokenService service =
                new RegistrationTokenService(registrationTokenRepository, opaqueTokenGenerator);

        String plainToken = service.issueToken(email, expiry);
        assertThat(service.validate(plainToken)).isEqualTo(RegistrationTokenStatus.VALID);

        RegistrationToken token = registrationTokenRepository
                .findByTokenHash(opaqueTokenGenerator.hash(plainToken))
                .orElseThrow();
        if (consumeInsteadOfInvalidate) {
            token.consume(Instant.now());
        } else {
            token.invalidate(Instant.now());
        }
        registrationTokenRepository.save(token);

        assertThat(service.validate(plainToken)).isEqualTo(RegistrationTokenStatus.EXPIRED);
    }

    @Provide
    Arbitrary<String> emails() {
        return Arbitraries.strings().withCharRange('a', 'z').ofMinLength(3).ofMaxLength(10)
                .map(local -> local + "@example.com");
    }

    @Provide
    Arbitrary<Duration> expiries() {
        return Arbitraries.longs().between(60, 86_400).map(Duration::ofSeconds);
    }

}
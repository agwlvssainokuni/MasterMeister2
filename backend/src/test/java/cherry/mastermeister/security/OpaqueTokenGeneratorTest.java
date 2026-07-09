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

import java.util.Base64;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * P12（business-logic-model.md、Code Generation計画で新規識別）を検証するプロパティテスト。
 */
class OpaqueTokenGeneratorTest {

    private final OpaqueTokenGenerator generator = new OpaqueTokenGenerator();

    // P12: hashは同一入力に対し常に同一出力を返す（決定的）。
    @Property
    void hashIsDeterministic(@ForAll("plainTokens") String input) {
        assertThat(generator.hash(input)).isEqualTo(generator.hash(input));
    }

    // P12: generate()はデコード可能な32バイトのURL-safe base64文字列を常に返す。
    @Property
    void generateReturnsDecodable32ByteUrlSafeBase64(@ForAll("iterations") int iteration) {
        String token = generator.generate();

        assertThat(token).doesNotContain("=", "+", "/");
        byte[] decoded = Base64.getUrlDecoder().decode(token);
        assertThat(decoded).hasSize(32);
    }

    @Provide
    Arbitrary<String> plainTokens() {
        return Arbitraries.strings().ofMinLength(0).ofMaxLength(50);
    }

    @Provide
    Arbitrary<Integer> iterations() {
        return Arbitraries.integers().between(0, 100);
    }

}
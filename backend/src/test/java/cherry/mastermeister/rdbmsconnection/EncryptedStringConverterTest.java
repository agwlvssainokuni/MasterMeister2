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

package cherry.mastermeister.rdbmsconnection;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.Base64;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * P1・P2（business-logic-model.md）を検証するプロパティテスト。
 */
class EncryptedStringConverterTest {

    private final EncryptedStringConverter converter = new EncryptedStringConverter(generateKey());

    // P1: convertToDatabaseColumnで暗号化した値をconvertToEntityAttributeで復号すると元の平文と一致する。
    @Property
    void encryptThenDecryptRoundTripsToOriginal(@ForAll("plainTexts") String plainText) {
        String encrypted = converter.convertToDatabaseColumn(plainText);
        String decrypted = converter.convertToEntityAttribute(encrypted);

        assertThat(decrypted).isEqualTo(plainText);
    }

    // P2: 暗号化後の値は（空文字を除き）常に平文と不一致である。
    @Property
    void encryptedValueDiffersFromPlainText(@ForAll("plainTexts") String plainText) {
        String encrypted = converter.convertToDatabaseColumn(plainText);

        assertThat(encrypted).isNotEqualTo(plainText);
    }

    @Provide
    Arbitrary<String> plainTexts() {
        return Arbitraries.strings().ofMinLength(0).ofMaxLength(200);
    }

    private static String generateKey() {
        byte[] keyBytes = new byte[32];
        new SecureRandom().nextBytes(keyBytes);
        return Base64.getEncoder().encodeToString(keyBytes);
    }

}
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

package cherry.mastermeister.queryexecution;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * P5（business-logic-model.md）を検証するプロパティテスト。
 */
class SqlParamDetectorTest {

    private final SqlParamDetector detector = new SqlParamDetector();

    // P5: 文字列リテラル外に出現する:paramName形式の識別子集合と、検出されるDetectedParam名の
    //     集合は常に一致する（リテラル内の同名出現は無視される）。
    @Property(tries = 30)
    void detectMatchesPlaceholdersOutsideStringLiterals(
            @ForAll("paramNameLists") List<String> outsideRaw, @ForAll("paramNameLists") List<String> insideRaw
    ) {
        List<String> outsideNames = outsideRaw.stream().map(s -> "o" + s).distinct().toList();
        List<String> insideNames = insideRaw.stream().map(s -> "i" + s).distinct().toList();

        StringBuilder sql = new StringBuilder("SELECT * FROM tbl WHERE 1 = 1");
        for (String name : outsideNames) {
            sql.append(" AND ").append(name).append(" = :").append(name);
        }
        for (String name : insideNames) {
            sql.append(" AND lit = 'contains :").append(name).append(" inside literal'");
        }

        List<DetectedParam> detected = detector.detect(sql.toString());
        Set<String> detectedNames = detected.stream().map(DetectedParam::name).collect(Collectors.toSet());

        assertThat(detectedNames).containsExactlyInAnyOrderElementsOf(outsideNames);
    }

    // PostgreSQLの::キャスト演算子はプレースホルダとして誤検知しない。
    @Property(tries = 1)
    void detectIgnoresPostgresCastOperator() {
        List<DetectedParam> detected = detector.detect("SELECT x::int FROM tbl WHERE y = :name");
        assertThat(detected).extracting(DetectedParam::name).containsExactly("name");
    }

    // ''によるエスケープを含む文字列リテラル内の:xxxは検出しない。
    @Property(tries = 1)
    void detectHandlesEscapedQuoteWithinLiteral() {
        List<DetectedParam> detected = detector.detect(
                "SELECT * FROM tbl WHERE note = 'it''s :notparam' AND col = :real");
        assertThat(detected).extracting(DetectedParam::name).containsExactly("real");
    }

    @Provide
    Arbitrary<List<String>> paramNameLists() {
        return Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(6)
                .list().ofMinSize(0).ofMaxSize(4).uniqueElements();
    }

}
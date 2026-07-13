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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import cherry.mastermeister.common.exception.ValidationException;

/**
 * P4（business-logic-model.md）を検証するプロパティテスト。
 */
class ReadOnlySqlValidatorTest {

    // P4: パース結果がSelect型でない、またはパース自体に失敗したSQLは常にValidationException。
    @Property(tries = 20)
    void validateRejectsNonSelectOrUnparsableSql(@ForAll("nonSelectSqlSamples") String sql) {
        ReadOnlySqlValidator validator = newValidator();
        try {
            assertThatThrownBy(() -> validator.validate(sql)).isInstanceOf(ValidationException.class);
        } finally {
            validator.shutdown();
        }
    }

    // P4: Select型として解析できるSQLは常に検証を通過する。
    @Property(tries = 10)
    void validateAcceptsSelectSql(@ForAll("selectSqlSamples") String sql) {
        ReadOnlySqlValidator validator = newValidator();
        try {
            assertThatCode(() -> validator.validate(sql)).doesNotThrowAnyException();
        } finally {
            validator.shutdown();
        }
    }

    // P4: sql-max-lengthを超えるSQLは、内容の妥当性によらず常にValidationException。
    @Property(tries = 5)
    void validateRejectsSqlExceedingMaxLength(@ForAll("selectSqlSamples") String baseSql) {
        ReadOnlySqlValidator validator = new ReadOnlySqlValidator(10, Duration.ofSeconds(5), 2);
        try {
            String longSql = baseSql + " ".repeat(50);
            assertThatThrownBy(() -> validator.validate(longSql)).isInstanceOf(ValidationException.class);
        } finally {
            validator.shutdown();
        }
    }

    @Provide
    Arbitrary<String> nonSelectSqlSamples() {
        return Arbitraries.of(
                "INSERT INTO tbl (id) VALUES (1)",
                "UPDATE tbl SET col = 1",
                "DELETE FROM tbl",
                "CREATE TABLE tbl (id INT)",
                "DROP TABLE tbl",
                "not a valid sql statement",
                "SELECT * FROM tbl; DROP TABLE tbl"
        );
    }

    @Provide
    Arbitrary<String> selectSqlSamples() {
        return Arbitraries.of(
                "SELECT * FROM tbl",
                "SELECT a.id FROM tbl a WHERE a.x = 1",
                "SELECT COUNT(*) FROM tbl",
                "SELECT a.id FROM tbl a JOIN tbl2 b ON a.id = b.id"
        );
    }

    private ReadOnlySqlValidator newValidator() {
        return new ReadOnlySqlValidator(10000, Duration.ofSeconds(5), 2);
    }

}
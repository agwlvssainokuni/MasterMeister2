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

package cherry.mastermeister.common.dialect;

/**
 * 対象RDBMS（MySQL/MariaDB/PostgreSQL/H2）の方言差異を吸収する操作を定義する。
 */
public interface DialectStrategy {

    RdbmsType getRdbmsType();

    String quoteIdentifier(String identifier);

    String buildPagingClause(int limit, int offset);

    String buildNullsOrderingClause(SortDirection direction, NullsOrder nullsOrder);

    SchemaResolutionMode getSchemaResolutionMode();

    /**
     * {@code getSchemaResolutionMode()}が{@code SCHEMA_BASED}の場合にのみ呼び出される、
     * 接続のデフォルトスキーマを切り替えるSQL文を返す（方言によってSET文の構文が異なる——
     * PostgreSQLは{@code SET search_path TO}、H2は{@code SET SCHEMA}）。{@code quotedSchema}は
     * 呼び出し側で{@code quoteIdentifier}済みの値。{@code CATALOG_BASED}方言では呼び出されない。
     */
    String buildSetSchemaStatement(String quotedSchema);

    String buildJdbcUrl(String host, int port, String databaseName);

}
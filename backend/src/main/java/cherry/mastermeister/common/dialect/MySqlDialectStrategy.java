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

import org.springframework.stereotype.Component;

@Component
public class MySqlDialectStrategy implements DialectStrategy {

    @Override
    public RdbmsType getRdbmsType() {
        return RdbmsType.MYSQL;
    }

    @Override
    public String quoteIdentifier(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    @Override
    public String buildPagingClause(int limit, int offset) {
        if (limit < 0 || offset < 0) {
            throw new IllegalArgumentException("limit and offset must be non-negative");
        }
        return "LIMIT " + limit + " OFFSET " + offset;
    }

    @Override
    public String buildNullsOrderingClause(SortDirection direction, NullsOrder nullsOrder) {
        // MySQL/MariaDBはNULLS FIRST/LAST構文を持たないため、方向指定のみ返す
        // （NULL値の並び順はエンジン既定値に従う。列単位のエミュレーションは呼び出し側の関心事）
        return direction.name();
    }

    @Override
    public SchemaResolutionMode getSchemaResolutionMode() {
        return SchemaResolutionMode.CATALOG_BASED;
    }

    @Override
    public String buildJdbcUrl(String host, int port, String databaseName) {
        return "jdbc:mysql://" + host + ":" + port + "/" + databaseName;
    }

}
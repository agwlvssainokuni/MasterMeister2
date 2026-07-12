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

import org.springframework.stereotype.Component;

import cherry.mastermeister.common.dialect.DialectStrategy;

/**
 * 入力SQLをサブクエリとしてラップし、外側にLIMIT/OFFSET相当句を付与する
 * （{@code business-rules.md} 4節）。入力SQL自体を構文解析してLIMIT句の有無を判定する
 * 必要がない。
 */
@Component
public class PagingSqlBuilder {

    public String wrapWithPaging(String sql, DialectStrategy dialect, int limit, int offset) {
        return "SELECT * FROM (" + sql + ") AS " + dialect.quoteIdentifier("subquery")
                + " " + dialect.buildPagingClause(limit, offset);
    }

}
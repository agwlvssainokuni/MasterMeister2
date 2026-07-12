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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * SQL文字列から{@code :paramName}形式のプレースホルダを検出する。文字列リテラル
 * （シングルクォート、{@code ''}によるエスケープ含む）内の{@code :xxx}や、PostgreSQLの
 * {@code ::type}キャスト演算子は誤検知しないよう1文字ずつスキャンする。
 */
@Component
public class SqlParamDetector {

    public List<DetectedParam> detect(String sql) {
        List<DetectedParam> params = new ArrayList<>();
        if (sql == null) {
            return params;
        }
        Set<String> seen = new LinkedHashSet<>();
        int len = sql.length();
        int i = 0;
        while (i < len) {
            char c = sql.charAt(i);
            if (c == '\'') {
                i = skipStringLiteral(sql, i, len);
                continue;
            }
            if (c == ':') {
                if (i + 1 < len && sql.charAt(i + 1) == ':') {
                    i += 2;
                    continue;
                }
                int start = i + 1;
                int j = start;
                while (j < len && (Character.isLetterOrDigit(sql.charAt(j)) || sql.charAt(j) == '_')) {
                    j++;
                }
                if (j > start) {
                    String name = sql.substring(start, j);
                    if (seen.add(name)) {
                        params.add(new DetectedParam(name));
                    }
                    i = j;
                    continue;
                }
            }
            i++;
        }
        return params;
    }

    private int skipStringLiteral(String sql, int start, int len) {
        int i = start + 1;
        while (i < len) {
            if (sql.charAt(i) == '\'') {
                if (i + 1 < len && sql.charAt(i + 1) == '\'') {
                    i += 2;
                    continue;
                }
                return i + 1;
            }
            i++;
        }
        return i;
    }

}
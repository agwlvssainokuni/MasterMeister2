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

import java.util.Map;

/**
 * {@code connectionId}は{@code SavedQuery.connectionId}との一致を
 * {@code QueryExecutionService}内で検証する防御的二重チェックに用いる
 * （Code Generation時点で確定する事項1）。
 */
public record SavedExecutionRequest(
        Long connectionId,
        Map<String, Object> params,
        PagingOption paging
) {
}
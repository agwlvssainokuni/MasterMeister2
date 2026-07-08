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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class DialectStrategyFactory {

    private final Map<RdbmsType, DialectStrategy> strategies;

    public DialectStrategyFactory(List<DialectStrategy> strategies) {
        this.strategies = strategies.stream()
                .collect(Collectors.toUnmodifiableMap(DialectStrategy::getRdbmsType, s -> s));
    }

    public DialectStrategy resolve(RdbmsType rdbmsType) {
        DialectStrategy strategy = strategies.get(rdbmsType);
        if (strategy == null) {
            throw new IllegalArgumentException("Unsupported RdbmsType: " + rdbmsType);
        }
        return strategy;
    }

}
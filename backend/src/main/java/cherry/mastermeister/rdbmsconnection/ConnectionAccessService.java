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

import java.util.List;

import org.springframework.stereotype.Service;

import cherry.mastermeister.permission.EffectivePermissionResolver;

@Service
public class ConnectionAccessService {

    private final RdbmsConnectionRepository rdbmsConnectionRepository;
    private final EffectivePermissionResolver effectivePermissionResolver;

    public ConnectionAccessService(
            RdbmsConnectionRepository rdbmsConnectionRepository,
            EffectivePermissionResolver effectivePermissionResolver
    ) {
        this.rdbmsConnectionRepository = rdbmsConnectionRepository;
        this.effectivePermissionResolver = effectivePermissionResolver;
    }

    public List<ConnectionSummary> listAccessibleConnections(Long userId) {
        return rdbmsConnectionRepository.findAll().stream()
                .filter(c -> !effectivePermissionResolver.listAccessibleSchemas(userId, c.getId()).isEmpty())
                .map(c -> new ConnectionSummary(c.getId(), c.getName(), c.getRdbmsType(), c.getHost(), c.getDatabaseName()))
                .toList();
    }

}